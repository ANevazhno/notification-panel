package local.notificationpanel;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.*;
import android.widget.*;
import java.util.*;

public class PanelActivity extends Activity {
    private static final int BG=PanelColors.BACKGROUND, CARD=PanelColors.SURFACE, TEXT=PanelColors.TEXT, MUTED=PanelColors.MUTED, BLUE=PanelColors.ACCENT;
    private SharedPreferences prefs;
    private LinearLayout root, list;
    private ScrollView scroll;
    private ListView groupList;
    private final GroupAdapter groupAdapter=new GroupAdapter();
    private int renderMode=-1;
    private int builtCards;
    private final android.util.LruCache<String,android.graphics.drawable.Drawable.ConstantState> icons=new android.util.LruCache<>(64);
    private final android.util.LruCache<String,String> labels=new android.util.LruCache<>(128);
    private TextView clockLabel,dateLabel,weatherLabel,weatherPlaceLabel;
    private void updateWeather(){if(weatherLabel==null)return;weatherLabel.setText(weather.label());weatherPlaceLabel.setText(weather.placeLabel());((View)weatherLabel.getParent()).setContentDescription("Погода: "+weather.label()+", "+weather.placeLabel()+". Открыть сведения");}
    private WeatherController weather;
    private PanelBackdrop backdrop;
    private MusicCard music;
    private final Set<String> expanded=new HashSet<>();
    private final Map<String,Integer> showAll=new HashMap<>();
    private final Map<String,Long> openedAt=new HashMap<>();
    private final Map<String,Long> missingSince=new HashMap<>();
    private final List<String> readingOrder=new ArrayList<>();
    private final Runnable expireMissing=()->{if(this.visible)render();};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean visible, demo;
    private String requestedGroup;
    private String motionGroup;
    private int motionHeight;
    private final Runnable observer=()-> { if(visible) render(); };
    private final Runnable tick=new Runnable(){ public void run(){
        if(!visible)return;
        if(clockLabel!=null)clockLabel.setText(android.text.format.DateFormat.format("HH:mm",new Date()).toString());
        SmsSource.sync();weather.refresh(false);SpletaSource.sync(false,null);updateWeather();if(dateLabel!=null)dateLabel.setText(new java.text.SimpleDateFormat("EEEE, d MMMM",new Locale("ru")).format(new Date()));
        handler.postDelayed(this,30000);
    }};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        weather=new WeatherController(this,this::updateWeather);
        requestedGroup=getIntent().getStringExtra(PanelWidget.GROUP);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("panel_settings",MODE_PRIVATE);
        root=column(); root.setBackground(new ShadeBackground());
        root.setPadding(dp(18),dp(8),dp(18),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());top=i.top;bottom=i.bottom;}
            else {top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            v.setPadding(dp(18),top+dp(8),dp(18),bottom); return insets;
        });
        setContentView(root); root.requestApplyInsets();
        backdrop=new PanelBackdrop(this,root);backdrop.attach();
    }
    @Override protected void onDestroy(){if(backdrop!=null)backdrop.detach();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);requestedGroup=intent.getStringExtra(PanelWidget.GROUP);demo=false;if(visible)render();}
    @Override public void onResume(){super.onResume();visible=true;readingOrder.clear();NotificationBridge.watch(observer);SpletaSource.sync(true,null);render();music.start();weather.resume();handler.post(tick);if(prefs.getBoolean("edge_pending",false)&&Settings.canDrawOverlays(this))handler.post(this::enableEdge);}
    @Override public void onPause(){visible=false;if(music!=null)music.stop();weather.pause();NotificationBridge.unwatch(observer);handler.removeCallbacks(tick);handler.removeCallbacks(expireMissing);super.onPause();}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==735&&results.length>0&&results[0]==0)startForegroundService(new Intent(this,SpletaPushService.class));if(code==421){prefs.edit().putBoolean("edge_pending",false).apply();if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)enableEdge();else toast("Боковая панель не включена: нужно разрешить служебное уведомление.");}if(code==420){if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)weather.refresh(true);else toast("Без местоположения температура недоступна. Разрешение можно выдать в настройках Android.");}}
    private boolean unlocked(){KeyguardManager k=getSystemService(KeyguardManager.class);return k!=null&&!k.isDeviceLocked();}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value,int sp,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setPadding(0,dp(3),0,dp(3));return t;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextColor(BLUE);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(14),0,dp(14),0);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(PanelColors.RIPPLE),shape(PanelColors.BUTTON,20),null));b.setOnClickListener(v->action.run());return b;}
    private void gap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private void note(LinearLayout parent,String message){parent.addView(text(message,13,MUTED));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private boolean access(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        return nm!=null&&nm.isNotificationListenerAccessGranted(new ComponentName(this,NotificationBridge.class));
    }
    private void render(){
        if(scroll==null){
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.TOP);
        LinearLayout clockBlock=column();
        TextView clock=text(android.text.format.DateFormat.format("HH:mm",new Date()).toString(),64,TEXT);clockLabel=clock;clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));clock.setLetterSpacing(-.03f);clock.setMaxLines(1);clock.setAutoSizeTextTypeUniformWithConfiguration(28,64,2,android.util.TypedValue.COMPLEX_UNIT_SP);clock.setPadding(0,0,0,0);clockBlock.addView(clock);
        TextView date=text(new java.text.SimpleDateFormat("EEEE, d MMMM",new Locale("ru")).format(new Date()),14,MUTED);dateLabel=date;LinearLayout.LayoutParams dateLp=new LinearLayout.LayoutParams(-1,-2);dateLp.topMargin=-dp(6);clockBlock.addView(date,dateLp);
        top.addView(clockBlock,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout weatherBox=column();weatherBox.setGravity(Gravity.CENTER);weatherBox.setPadding(dp(8),dp(16),dp(8),0);weatherBox.setMinimumWidth(dp(64));weatherBox.setMinimumHeight(dp(64));weatherBox.setFocusable(true);weatherBox.setOnClickListener(v->weather.details());
        weatherLabel=text(weather.label(),28,TEXT);weatherLabel.setContentDescription("Температура по местоположению. Нажмите для настройки");weatherLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);weatherBox.addView(weatherLabel);
        TextView source=text(weather.placeLabel(),11,MUTED);weatherPlaceLabel=source;source.setMaxLines(2);source.setMaxWidth(dp(96));source.setEllipsize(android.text.TextUtils.TruncateAt.END);source.setGravity(Gravity.CENTER);source.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);weatherBox.addView(source);top.addView(weatherBox,new LinearLayout.LayoutParams(-2,-2));
        ImageButton settings=iconButton(R.drawable.ic_more,"Настройки Шторки",this::settings);settings.setContentDescription("Настройки Шторки");LinearLayout.LayoutParams settingsLp=new LinearLayout.LayoutParams(dp(48),dp(48));settingsLp.topMargin=dp(15);top.addView(settings,settingsLp);root.addView(top);gap(root,12);
        music=new MusicCard(this);LinearLayout.LayoutParams musicLp=new LinearLayout.LayoutParams(-1,-2);musicLp.bottomMargin=dp(12);root.addView(music,musicLp);
        scroll=new ScrollView(this);scroll.setFillViewport(true);list=column();scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        groupList=new ListView(this);groupList.setDivider(null);groupList.setCacheColorHint(Color.TRANSPARENT);groupList.setItemsCanFocus(true);groupList.setAdapter(groupAdapter);groupList.setClipToPadding(false);
        root.addView(groupList,new LinearLayout.LayoutParams(-1,0,1));
        }
        if(weatherLabel!=null){View weatherBox=(View)weatherLabel.getParent();weatherBox.setContentDescription("Погода: "+weather.label()+", "+weather.placeLabel()+". Открыть сведения");}
        int mode=!unlocked()?0:demo?1:!access()?2:(!NotificationBridge.connected()&&!SpletaSource.enabled())?3:4;
        groupList.setVisibility(mode==4?View.VISIBLE:View.GONE);scroll.setVisibility(mode==4?View.GONE:View.VISIBLE);
        if(mode!=4){if(renderMode==mode)return;list.removeAllViews();groupAdapter.clear();}renderMode=mode;
        if(!unlocked()){note(list,"Разблокируйте телефон, чтобы увидеть уведомления.");return;}
        if(demo){renderDemo();return;}
        if(!access()){
            TextView h=text("Подключите уведомления",22,TEXT);list.addView(h);
            note(list,"Разрешите доступ для «Шторки». Панель читает активные уведомления и показывает их по приложениям. Данные не отправляются в сеть и не записываются в историю.");
            gap(list,12);list.addView(button("Открыть доступ к уведомлениям",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
            gap(list,12);list.addView(button("Посмотреть пример",()->{demo=true;render();}));return;
        }
        if(!NotificationBridge.connected()&&!SpletaSource.enabled()){
            note(list,"Ожидаем подключение к Android. Если доступ уже включён, попробуйте переподключиться.");
            list.addView(button("Переподключить",()->{android.service.notification.NotificationListenerService.requestRebind(new ComponentName(this,NotificationBridge.class));toast("Запрос отправлен Android");}));return;
        }
        List<Entry> entries=new ArrayList<>();
        for(StatusBarNotification s:NotificationBridge.snapshot()) entries.add(new Entry(s));
        LinkedHashMap<String,List<Entry>> groups=Grouping.group(entries);
        String focus=requestedGroup;
        if(focus!=null&&groups.containsKey(focus)){expanded.add(focus);openedAt.put(focus,SystemClock.elapsedRealtime());requestedGroup=null;}
        // Apps may cancel and repost all remaining notifications after a read action.
        // Keep UI state briefly, but never keep obsolete notification content or actions.
        Set<String> keys=groups.keySet();long now=SystemClock.elapsedRealtime();
        handler.removeCallbacks(expireMissing);long nextExpiry=Long.MAX_VALUE;
        for(String key:new HashSet<>(expanded)){
            if(keys.contains(key)){missingSince.remove(key);continue;}
            long since=missingSince.computeIfAbsent(key,k->now);
            if(now-since>=5000){expanded.remove(key);showAll.remove(key);openedAt.remove(key);missingSince.remove(key);}
            else nextExpiry=Math.min(nextExpiry,5000-(now-since));
        }
        missingSince.keySet().retainAll(expanded);
        if(nextExpiry!=Long.MAX_VALUE)handler.postDelayed(expireMissing,nextExpiry);
        if(expanded.isEmpty()){readingOrder.clear();readingOrder.addAll(keys);}
        else{
            readingOrder.removeIf(k->!keys.contains(k)&&!expanded.contains(k));
            for(String key:keys)if(!readingOrder.contains(key))readingOrder.add(key);
            LinkedHashMap<String,List<Entry>> stable=new LinkedHashMap<>();
            for(String key:readingOrder)if(groups.containsKey(key))stable.put(key,groups.get(key));
            groups=stable;
        }
        int count=0;for(List<Entry> es:groups.values())count+=es.size();
        groupList.setContentDescription(count+" "+plural(count,"уведомление","уведомления","уведомлений")+" · "+groups.size()+" "+plural(groups.size(),"приложение","приложения","приложений"));
        groupAdapter.update(groups,focus);
    }
    private final class GroupAdapter extends BaseAdapter {
        private final List<String> keys=new ArrayList<>();
        private String pendingAnchor;private int pendingAnchorY;
        private Map<String,List<Entry>> data=new LinkedHashMap<>();
        private final Map<String,Long> ids=new HashMap<>();private long nextId=1;
        private final LinkedHashMap<String,CachedCard> cache=new LinkedHashMap<>(32,.75f,true);
        private final class CachedCard {
            View view;List<Entry> entries;boolean open;int limit;
            boolean same(String key,List<Entry> now){if(open!=expanded.contains(key)||limit!=showAll.getOrDefault(key,prefs.getInt("rows",3))||entries.size()!=now.size())return false;for(int i=0;i<now.size();i++)if(entries.get(i).sbn!=now.get(i).sbn)return false;return true;}
        }
        public int getCount(){return keys.size();}public Object getItem(int p){return data.get(keys.get(p));}public long getItemId(int p){return ids.get(keys.get(p));}public boolean hasStableIds(){return true;}
        public View getView(int p,View recycled,ViewGroup parent){
            String key=keys.get(p);List<Entry> entries=data.get(key);CachedCard cached=cache.get(key);
            if(cached==null||!cached.same(key,entries)){
                int previousHeight=key.equals(motionGroup)?motionHeight:0;
                cached=new CachedCard();cached.entries=new ArrayList<>(entries);cached.open=expanded.contains(key);cached.limit=showAll.getOrDefault(key,prefs.getInt("rows",3));cached.view=appCard(key,entries);cache.put(key,cached);
                if(key.equals(motionGroup)){
                    motionGroup=null;
                    if(previousHeight>0&&android.animation.ValueAnimator.areAnimatorsEnabled())cached.view=new GroupMotionLayout(PanelActivity.this,cached.view,previousHeight,cached.open);
                }
                if(cache.size()>24){Iterator<String> it=cache.keySet().iterator();it.next();it.remove();}
            }return cached.view;
        }
        void prepareMotion(String key){
            CachedCard card=cache.get(key);
            motionGroup=key;
            motionHeight=card!=null&&card.view.isAttachedToWindow()?card.view.getHeight():0;
        }
        void clear(){data.clear();keys.clear();cache.clear();pendingAnchor=null;motionGroup=null;motionHeight=0;notifyDataSetChanged();}
        void update(LinkedHashMap<String,List<Entry>> groups,String focus){
            int first=groupList.getFirstVisiblePosition();String anchor=first<keys.size()?keys.get(first):null;int y=groupList.getChildCount()>0?groupList.getChildAt(0).getTop():0;
            if(pendingAnchor!=null){
                if(groups.containsKey(pendingAnchor)){anchor=pendingAnchor;y=pendingAnchorY;pendingAnchor=null;}
                else if(!expanded.contains(pendingAnchor))pendingAnchor=null;
            }
            if(pendingAnchor==null&&anchor!=null&&!groups.containsKey(anchor)&&expanded.contains(anchor)){pendingAnchor=anchor;pendingAnchorY=y;}
            View focused=findAccessFocus(groupList);String focusTag=focused!=null&&focused.getTag() instanceof String?(String)focused.getTag():null;
            data=groups;keys.clear();keys.addAll(groups.keySet());cache.keySet().retainAll(groups.keySet());
            for(String key:keys)if(!ids.containsKey(key))ids.put(key,nextId++);
            notifyDataSetChanged();
            if(focus!=null&&keys.contains(focus))groupList.setSelectionFromTop(keys.indexOf(focus),0);
            else if(anchor!=null&&keys.contains(anchor))groupList.setSelectionFromTop(keys.indexOf(anchor),y);
            if(focusTag!=null)groupList.post(()->{View target=groupList.findViewWithTag(focusTag);if(target!=null&&!target.isAccessibilityFocused())target.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null);});
        }
    }
    private View findAccessFocus(View v){if(v.isAccessibilityFocused())return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=findAccessFocus(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    private ImageButton iconButton(int icon,String label,Runnable action){ImageButton b=new ImageButton(this);b.setImageResource(icon);b.setColorFilter(BLUE);b.setContentDescription(label);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setMinimumHeight(dp(48));b.setMinimumWidth(dp(48));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(PanelColors.RIPPLE),null,shape(Color.WHITE,20)));b.setOnClickListener(v->action.run());return b;}
    private android.graphics.drawable.Drawable appIcon(String pkg)throws Exception{
        android.graphics.drawable.Drawable.ConstantState cached=icons.get(pkg);if(cached!=null)return cached.newDrawable(getResources());
        Context pc=createPackageContext(pkg,0);android.graphics.drawable.Drawable icon=pc.getApplicationInfo().loadIcon(pc.getPackageManager());if(icon.getConstantState()!=null)icons.put(pkg,icon.getConstantState());return icon;
    }

    private final class Entry implements Grouping.Entry {
        final StatusBarNotification sbn; final Notification n; final String app;
        Entry(StatusBarNotification s){sbn=s;n=s.getNotification();app=SmsSource.groupKey(s);}
        public String key(){return sbn.getKey();}public String appKey(){return app;}public String originalGroup(){return sbn.getGroupKey();}
        public boolean summary(){return (n.flags&Notification.FLAG_GROUP_SUMMARY)!=0;}
        public long time(){return sbn.getPostTime();}
        public boolean excluded(){
            return NotificationPolicy.excluded(PanelActivity.this,sbn);
        }
    }
    private String appName(StatusBarNotification s){
        if(SmsSource.owns(s))return "SMS";
        if(SpletaSource.owns(s))return "Spleta";
        String cached=labels.get(s.getUser()+":"+s.getPackageName());if(cached!=null)return cached;
        String label=s.getPackageName();
        try { Context c=createPackageContext(s.getPackageName(),0); ApplicationInfo ai=c.getApplicationInfo();label=String.valueOf(c.getPackageManager().getApplicationLabel(ai)); }
        catch(Exception ignored){}
        if(!s.getUser().equals(android.os.Process.myUserHandle()))label+=" · другой профиль";
        labels.put(s.getUser()+":"+s.getPackageName(),label);return label;
    }
    private String plural(int n,String one,String few,String many){int m=n%100;if(m>=11&&m<=14)return many;return n%10==1?one:(n%10>=2&&n%10<=4?few:many);}
    private GradientDrawable glass(){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{PanelColors.CARD_TOP,PanelColors.CARD_BOTTOM});d.setCornerRadius(dp(24));d.setStroke(dp(1),PanelColors.BORDER);return d;}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(16),dp(13),dp(16),dp(13));c.setBackground(glass());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);list.addView(c,lp);return c;}
    private TextView cardText(String value,int sp,int color){TextView t=text(value,sp,color);t.setIncludeFontPadding(false);t.setPadding(0,dp(1),0,dp(1));return t;}
    private View appCard(String key,List<Entry> entries){
        builtCards++;
        Entry first=entries.get(0);boolean open=expanded.contains(key);
        FrameLayout stack=new FrameLayout(this);stack.setTag(key);stack.setLayoutParams(new AbsListView.LayoutParams(-1,-2));stack.setPadding(0,0,0,dp(5));
        if(!open&&entries.size()>1){
            for(int i=2;i>=1;i--){View layer=new View(this);layer.setBackground(shape(i==2?PanelColors.STACK_BACK:PanelColors.STACK_FRONT,23));FrameLayout.LayoutParams layerLp=new FrameLayout.LayoutParams(-1,dp(24),Gravity.BOTTOM);layerLp.leftMargin=dp(i*7);layerLp.rightMargin=dp(i*7);layerLp.bottomMargin=dp((2-i)*5);stack.addView(layer,layerLp);}
        }
        LinearLayout c=column();c.setPadding(dp(12),dp(4),dp(12),dp(4));c.setBackground(glass());FrameLayout.LayoutParams contentLp=new FrameLayout.LayoutParams(-1,-2);contentLp.bottomMargin=dp(!open&&entries.size()>1?10:0);stack.addView(c,contentLp);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        try{ImageView icon=new ImageView(this);icon.setImageDrawable(SmsSource.owns(first.sbn)?getDrawable(R.drawable.ic_sms):SpletaSource.owns(first.sbn)?getDrawable(R.drawable.ic_spleta):appIcon(first.sbn.getPackageName()));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(22),dp(22));ip.rightMargin=dp(10);header.addView(icon,ip);}catch(Exception ignored){}
        LinearLayout captions=column();TextView label=cardText(appName(first.sbn),12,MUTED);label.setTypeface(Typeface.create("sans-serif-medium",0));label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);captions.addView(label);
        header.addView(captions,new LinearLayout.LayoutParams(0,-2,1));
        TextView time=cardText(android.text.format.DateFormat.getTimeFormat(this).format(new Date(first.time())),11,MUTED);time.setPadding(dp(8),0,dp(9),0);header.addView(time);
        TextView badge=cardText(String.valueOf(entries.size()),12,TEXT);badge.setGravity(Gravity.CENTER);badge.setMinWidth(dp(25));badge.setMinHeight(dp(25));badge.setPadding(dp(5),0,dp(5),0);header.addView(badge,new LinearLayout.LayoutParams(-2,-2));
        header.setMinimumHeight(dp(open?48:28));header.setFocusable(true);header.setTag("group:"+key);header.setPadding(0,dp(3),0,dp(3));header.setContentDescription(appName(first.sbn)+", "+entries.size()+" уведомлений, "+(open?"свернуть":"раскрыть"));
        header.setOnClickListener(v->{groupAdapter.prepareMotion(key);if(open){expanded.remove(key);showAll.remove(key);}else{expanded.add(key);openedAt.put(key,SystemClock.elapsedRealtime());}render();});c.addView(header);
        if(!open){TextView preview=cardText(title(first.n),15,TEXT);preview.setTypeface(Typeface.create("sans-serif-medium",0));preview.setMaxLines(1);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);c.addView(preview);TextView message=cardText(body(first.n),14,PanelColors.BODY);message.setMaxLines(2);message.setEllipsize(android.text.TextUtils.TruncateAt.END);c.addView(message);
            c.setOnClickListener(v->header.performClick());c.setFocusable(true);header.setClickable(false);header.setFocusable(false);header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);c.setContentDescription(appName(first.sbn)+", "+entries.size()+" уведомлений. "+title(first.n)+". "+body(first.n)+". Раскрыть");
            return swipeStack(stack,key,entries,null);}
        int max=showAll.getOrDefault(key,prefs.getInt("rows",3));
        for(int i=0;i<Math.min(entries.size(),max);i++) child(c,entries.get(i));
        if(entries.size()>max)c.addView(button("Ещё "+(entries.size()-max),()->{groupAdapter.prepareMotion(key);showAll.put(key,max+10);render();}));
        return swipeStack(stack,key,entries,header);
    }
    private View swipeStack(FrameLayout stack,String key,List<Entry> entries,View header){
            List<StatusBarNotification> clearable=new ArrayList<>();for(Entry item:entries)if(item.sbn.isClearable())clearable.add(item.sbn);
            if(clearable.isEmpty())return stack;
            SwipeDismissLayout swipe=new SwipeDismissLayout(this,()->{
                int minutes=prefs.getInt("stack_snooze_minutes",15);
                if(!unlocked()||!(minutes>0?NotificationBridge.snoozeBatch(clearable,minutes*60000L):NotificationBridge.dismissBatch(clearable))){toast("Не удалось убрать стопку. Уведомления могли измениться — повторите свайп.");return false;}
                if(clearable.size()==entries.size()){expanded.remove(key);showAll.remove(key);openedAt.remove(key);missingSince.remove(key);return true;}
                return false;
            });swipe.setTag("stack:"+key);swipe.setLayoutParams(new AbsListView.LayoutParams(-1,-2));stack.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));swipe.addView(stack);if(header!=null)swipe.setGestureRegion(header);return swipe;
    }
    private String title(Notification n){CharSequence t=n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);if(t==null||t.length()==0)t=n.extras.getCharSequence(Notification.EXTRA_TITLE);return t==null||t.length()==0?"Без заголовка":t.toString();}
    private String body(Notification n){
        CharSequence t=n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if(t==null||t.length()==0)t=n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if(t==null||t.length()==0){CharSequence[] lines=n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null)t=android.text.TextUtils.join("\n",lines);}
        if(t==null||t.length()==0){
            try{Parcelable[] messages=n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);if(messages!=null&&messages.length>0&&messages[messages.length-1] instanceof Bundle)t=((Bundle)messages[messages.length-1]).getCharSequence("text");}catch(Exception ignored){}
        }
        return t==null||t.length()==0?"Содержимое недоступно или скрыто Android":t.toString();
    }
    private void child(LinearLayout parent,Entry e){
        LinearLayout content=e.sbn.isClearable()?new SwipeDismissLayout(this,()->{
            return current(e)&&NotificationBridge.dismiss(e.key());
        }):column();
        parent.addView(content,new LinearLayout.LayoutParams(-1,-2));parent=content;
        View line=new View(this);line.setBackgroundColor(PanelColors.DIVIDER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.topMargin=dp(2);lp.bottomMargin=dp(2);parent.addView(line,lp);
        LinearLayout headingRow=new LinearLayout(this);headingRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView t=cardText(title(e.n),15,TEXT);t.setTypeface(Typeface.create("sans-serif-medium",0));t.setTag("title:"+e.key());t.setMaxLines(1);t.setEllipsize(android.text.TextUtils.TruncateAt.END);headingRow.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        TextView at=cardText(android.text.format.DateFormat.getTimeFormat(this).format(new Date(e.time())),11,MUTED);at.setPadding(dp(8),0,0,0);headingRow.addView(at);parent.addView(headingRow);
        TextView b=cardText(body(e.n),14,PanelColors.BODY);b.setMaxLines(2);b.setEllipsize(android.text.TextUtils.TruncateAt.END);parent.addView(b);
        
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);parent.addView(actions);
        if(e.n.contentIntent!=null||SmsSource.owns(e.sbn)||LocalNotifications.owns(e.sbn)){Button open=compactAction("Открыть",()->open(e));open.setTag("open:"+e.key());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.rightMargin=dp(7);actions.addView(open,p);}
        final Notification.Action readAction=findReadAction(e.n);
        Button read=compactAction("Прочитать",()->action(e,readAction));read.setTag("read:"+e.key());read.setEnabled(readAction!=null);read.setAlpha(readAction==null?.4f:1f);read.setContentDescription(readAction==null?"Прочитать: приложение не предоставило действие": "Пометить прочитанным");
        LinearLayout.LayoutParams readLp=new LinearLayout.LayoutParams(0,-2,1);readLp.rightMargin=dp(7);actions.addView(read,readLp);
        ImageButton more=iconButton(R.drawable.ic_more,"Другие действия: "+title(e.n),()->{
            List<String> labels=new ArrayList<>();List<Runnable> callbacks=new ArrayList<>();
            if(e.n.actions!=null)for(Notification.Action a:e.n.actions)if(a.actionIntent!=null&&a!=readAction){labels.add(String.valueOf(a.title));callbacks.add(()->action(e,a));}
            labels.add("Полный текст");callbacks.add(()->new PanelDialog.Builder(this).setTitle(title(e.n)).setMessage(body(e.n)).setPositiveButton("Закрыть",null).show());
            if(e.sbn.isClearable()){labels.add("Убрать уведомление…");callbacks.add(()->new PanelDialog.Builder(this).setTitle("Убрать из шторки?").setMessage(SmsSource.owns(e.sbn)?"SMS будет скрыто только здесь. В «Сообщениях» оно останется непрочитанным.":LocalNotifications.owns(e.sbn)?"Сохранённая карточка будет удалена с телефона.":"Это удалит уведомление и из системной шторки.").setNegativeButton("Отмена",null).setPositiveButton("Убрать",(d,w)->{if(!NotificationBridge.dismiss(e.key()))toast("Уведомление уже недоступно");}).show());}
            new PanelDialog.Builder(this).setTitle("Действия").setItems(labels.toArray(new String[0]),(d,index)->callbacks.get(index).run()).show();
        });more.setTag("more:"+e.key());more.setContentDescription("Другие действия: "+title(e.n));actions.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));
    }
    private Button compactAction(String label,Runnable action){
        Button b=button(label,action);b.setPadding(dp(10),dp(7),dp(10),dp(7));
        android.graphics.drawable.Drawable fill=new android.graphics.drawable.InsetDrawable(shape(PanelColors.BUTTON,12),0,dp(7),0,dp(7));
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(PanelColors.RIPPLE),fill,null));return b;
    }
    private boolean isReadAction(Notification.Action a){
        if(a==null||a.actionIntent==null||(a.getRemoteInputs()!=null&&a.getRemoteInputs().length>0))return false;
        if(a.getSemanticAction()==Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)return true;
        if(a.getSemanticAction()!=Notification.Action.SEMANTIC_ACTION_NONE)return false;
        String label=String.valueOf(a.title).trim().toLowerCase(Locale.ROOT).replace("ё","е");
        return Arrays.asList("прочитано","прочитать","пометить прочитанным","пометить как прочитанное","пометить как прочитанные","отметить как прочитанное","отметить прочитанным","mark as read","mark read").contains(label);
    }
    private Notification.Action findReadAction(Notification n){if(n.actions!=null)for(Notification.Action a:n.actions)if(isReadAction(a))return a;return null;}
    private boolean current(Entry e){if(!unlocked()||(!NotificationBridge.connected()&&!SpletaSource.owns(e.sbn)&&!SmsSource.owns(e.sbn)&&!LocalNotifications.owns(e.sbn))||!NotificationBridge.isCurrent(e.sbn)){toast("Уведомление изменилось или уже недоступно. Откройте его заново.");return false;}return true;}
    private boolean send(PendingIntent pi,Intent fill){
        try{
            Bundle opts=null;
            if(Build.VERSION.SDK_INT>=34)opts=ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle();
            pi.send(this,0,fill,null,null,null,opts);return true;
        }catch(PendingIntent.CanceledException|SecurityException ex){toast("Исходное действие больше недоступно. Откройте приложение вручную.");return false;}
        catch(RuntimeException ex){toast("Android не разрешил выполнить действие.");return false;}
    }
    private void open(Entry e){
        if(!current(e))return;
        if(LocalNotifications.owns(e.sbn)){LocalNotifications.open(this,e.sbn);return;}
        if(SmsSource.owns(e.sbn)){SmsOpener.open(this,e.n.extras.getString(Notification.EXTRA_TITLE));return;}
        if(send(e.n.contentIntent,null)&&(e.n.flags&Notification.FLAG_AUTO_CANCEL)!=0)NotificationBridge.dismiss(e.key());
    }
    private void action(Entry e,Notification.Action a){
        if(!current(e))return;
        if(isReadAction(a)){openedAt.put(e.app,SystemClock.elapsedRealtime());if(SpletaSource.owns(e.sbn))SpletaSource.read(e.sbn);else send(a.actionIntent,null);return;}
        android.app.RemoteInput[] inputs=a.getRemoteInputs();
        if(inputs==null||inputs.length==0){new PanelDialog.Builder(this).setTitle(String.valueOf(a.title)).setMessage("Выполнить действие исходного приложения для «"+title(e.n)+"»?").setNegativeButton("Отмена",null).setPositiveButton("Выполнить",(d,w)->{if(current(e))send(a.actionIntent,null);}).show();return;}
        android.app.RemoteInput target=null;
        for(android.app.RemoteInput r:inputs)if(r.getAllowFreeFormInput()){target=r;break;}
        if(target==null){toast("Это действие требует специального ввода. Используйте системное уведомление.");return;}
        final android.app.RemoteInput reply=target;
        EditText field=new EditText(this);field.setHint("Текст ответа");field.setMinLines(2);field.setPadding(dp(20),dp(12),dp(20),dp(12));
        PanelDialog dialog=new PanelDialog.Builder(this).setTitle("Ответ: "+title(e.n)).setView(field).setNegativeButton("Отмена",null).setPositiveButton("Отправить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(PanelDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=field.getText().toString().trim();if(value.isEmpty()){field.setError("Введите текст");return;}if(!current(e)){dialog.dismiss();return;}
            Bundle result=new Bundle();result.putCharSequence(reply.getResultKey(),value);Intent fill=new Intent();android.app.RemoteInput.addResultsToIntent(inputs,fill,result);
            android.app.RemoteInput.setResultsSource(fill,android.app.RemoteInput.SOURCE_FREE_FORM_INPUT);
            if(send(a.actionIntent,fill)){toast("Ответ передан приложению. Доставку проверьте в чате.");dialog.dismiss();}
        }));dialog.show();
    }
    private void enableEdge(){
        if(EdgePanelService.running()){prefs.edit().putBoolean("edge_pending",false).apply();return;}
        if(!Settings.canDrawOverlays(this)){new PanelDialog.Builder(this).setTitle("Панель у левого края").setMessage("Разрешите показ поверх других приложений и вернитесь сюда. Включение продолжится автоматически.").setNegativeButton("Отмена",null).setPositiveButton("Открыть разрешение",(d,w)->{prefs.edit().putBoolean("edge_pending",true).apply();startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));}).show();return;}
        prefs.edit().putBoolean("edge_pending",false).apply();
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},421);return;}
        try{startForegroundService(new Intent(this,EdgePanelService.class));handler.postDelayed(()->toast(EdgePanelService.running()?"Полоска включена. Нажмите Домой — она появится слева.":"Полоска не запустилась. Проверьте разрешение поверх приложений."),800);}catch(RuntimeException e){EdgePanelService.lastError="Android запретил запуск: "+e.getClass().getSimpleName();toast("Android не разрешил запуск. Повторите включение из открытой панели.");}
    }
    private void settings(){startActivity(new Intent(this,SettingsActivity.class));}
    private void renderDemo(){
        note(list,"ПРИМЕР · это вымышленные данные");gap(list,8);
        String[][] samples={{"Compass","3 уведомления","Инженерка · Портал Финансы · Другой чат"},{"Яндекс.Почта","2 уведомления","Рабочий аккаунт · Личный аккаунт"},{"Telegram","1 уведомление","Команда проекта"}};
        for(String[] sample:samples){LinearLayout c=card();TextView h=text(sample[0]+"  ⌄",18,TEXT);h.setTypeface(null,Typeface.BOLD);c.addView(h);c.addView(text(sample[1],13,BLUE));TextView detail=text(sample[2],14,MUTED);detail.setVisibility(View.GONE);c.addView(detail);c.setOnClickListener(v->detail.setVisibility(detail.getVisibility()==View.GONE?View.VISIBLE:View.GONE));}
        list.addView(button("Вернуться к уведомлениям",()->{demo=false;render();}));
    }
}

