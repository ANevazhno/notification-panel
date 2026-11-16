package local.notificationpanel;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native grouped preferences. Values save immediately; child lists share one navigation stack. */
public final class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout root,body,group;
    private ScrollView scroll;
    private String page="main";
    private int mainScroll;
    private Switch edge,sms;
    private TextView portal,accessValue;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        if(sms!=null)sms.setChecked(SmsSource.enabled());
        if(edge!=null)edge.setChecked(EdgePanelService.running());
        if(portal!=null){String state=SpletaSource.status();if(!state.equals(portal.getText().toString()))portal.setText(state);}
        if(accessValue!=null)accessValue.setText(hasAccess()?"Разрешён":"Разрешить");
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);prefs=getSharedPreferences("panel_settings",0);
        if(saved!=null){page=saved.getString("page","main");mainScroll=saved.getInt("scroll",0);}showPage(page);
    }
    @Override public void onResume(){super.onResume();if(prefs.getBoolean("edge_pending",false)&&Settings.canDrawOverlays(this))enableEdge();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onSaveInstanceState(Bundle out){out.putString("page",page);out.putInt("scroll",page.equals("main")?scroll.getScrollY():mainScroll);super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(page.equals("main"))super.onBackPressed();else showPage("main");}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void showPage(String next){
        if(scroll!=null&&page.equals("main"))mainScroll=scroll.getScrollY();page=next;edge=null;sms=null;portal=null;accessValue=null;
        root=column();root.setBackgroundColor(PanelColors.BACKGROUND);
        root.setOnApplyWindowInsetsListener((v,i)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());top=b.top;bottom=b.bottom;}else{top=i.getSystemWindowInsetTop();bottom=i.getSystemWindowInsetBottom();}v.setPadding(0,top,0,bottom);return i;});
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);nav.setMinimumHeight(dp(56));nav.setPadding(dp(4),0,dp(16),0);
        ImageButton back=new ImageButton(this);back.setImageDrawable(new Mark(0));back.setContentDescription(page.equals("main")?"Назад к Шторке":"Назад к настройкам");back.setBackground(new RippleDrawable(ColorStateList.valueOf(PanelColors.RIPPLE),null,null));back.setOnClickListener(v->onBackPressed());nav.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        String title=page.equals("main")?"Настройки":page.equals("snooze")?"Свайп стопки":page.equals("rows")?"Карточек при раскрытии":"Исключённые приложения";
        TextView heading=text(title,20,PanelColors.TEXT);heading.setTypeface(Typeface.create("sans-serif-medium",0));heading.setAccessibilityHeading(true);nav.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(nav);
        scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setPadding(dp(16),dp(12),dp(16),dp(24));scroll.setFillViewport(false);
        body=column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.requestApplyInsets();
        if(page.equals("main")){mainPage();scroll.post(()->scroll.scrollTo(0,mainScroll));}
        else if(page.equals("snooze"))choices("stack_snooze_minutes",new int[]{1,15,30,60,120,0},new String[]{"Вернуть через 1 минуту","Вернуть через 15 минут","Вернуть через 30 минут","Вернуть через 1 час","Вернуть через 2 часа","Удалять без возврата"},15);
        else if(page.equals("rows"))choices("rows",new int[]{3,5,10},new String[]{"3 карточки","5 карточек","10 карточек"},3);
        else excluded();
    }
    private void section(String title){
        if(!title.isEmpty()){TextView h=text(title,13,PanelColors.MUTED);h.setAccessibilityHeading(true);h.setPadding(dp(16),body.getChildCount()==0?dp(4):dp(24),0,dp(8));body.addView(h);}
        group=column();group.setBackground(surface(0xff22364b,14));group.setClipToOutline(true);body.addView(group,new LinearLayout.LayoutParams(-1,-2));
    }
    private void add(View v){if(group.getChildCount()>0){View line=new View(this);line.setBackgroundColor(PanelColors.DIVIDER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(0.5f));lp.leftMargin=dp(16);group.addView(line,lp);}group.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private LinearLayout row(String title){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(52));row.setPadding(dp(16),dp(12),dp(16),dp(12));TextView label=text(title,16,PanelColors.TEXT);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));return row;}
    private TextView link(String title,String value,Runnable action){
        LinearLayout r=row(title);TextView detail=text(value,15,PanelColors.MUTED);detail.setGravity(Gravity.END);detail.setPadding(dp(12),0,dp(8),0);detail.setMaxWidth(dp(136*Math.min(getResources().getConfiguration().fontScale,1.5f)));r.addView(detail);
        ImageView arrow=new ImageView(this);arrow.setImageDrawable(new Mark(1));arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);r.addView(arrow,new LinearLayout.LayoutParams(dp(12),dp(18)));
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(PanelColors.RIPPLE),null,null));r.setFocusable(true);r.setOnClickListener(v->action.run());if(Build.VERSION.SDK_INT>=28)r.setScreenReaderFocusable(true);add(r);return detail;
    }
    private Switch toggle(String title,boolean checked,java.util.function.Consumer<Boolean> change){
        Switch sw=new Switch(this);sw.setText(title);sw.setTextColor(PanelColors.TEXT);sw.setTextSize(16);sw.setMinHeight(dp(52));sw.setPadding(dp(16),dp(10),dp(16),dp(10));sw.setSwitchMinWidth(dp(46));sw.setSwitchPadding(dp(16));sw.setShowText(false);
        sw.setThumbTintList(ColorStateList.valueOf(0xfff5f8fd));sw.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff55b6aa,0xff6b7c90}));sw.setChecked(checked);sw.setOnClickListener(v->change.accept(sw.isChecked()));add(sw);return sw;
    }
    private String snooze(){int n=prefs.getInt("stack_snooze_minutes",15);return n==0?"Удалять":n==60?"1 час":n==120?"2 часа":n+" мин";}
    private boolean hasAccess(){return getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(this,NotificationBridge.class));}
    private void mainPage(){
        section("Панель");edge=toggle("Боковая кнопка",EdgePanelService.running(),on->{if(on)enableEdge();else EdgePanelService.stop(this);edge.setChecked(EdgePanelService.running());});
        link("Свайп стопки",snooze(),()->showPage("snooze"));
        link("Карточек при раскрытии",String.valueOf(prefs.getInt("rows",3)),()->showPage("rows"));
        section("Заводская шторка");
        toggle("Только в нашей Шторке",LocalNotifications.enabled(),on->prefs.edit().putBoolean(LocalNotifications.SETTING,on).apply());
        section("Скрывать уведомления");
        toggle("Постоянные и служебные",prefs.getBoolean("hide_ongoing",true),on->prefs.edit().putBoolean("hide_ongoing",on).apply());
        toggle("Android и System UI",prefs.getBoolean("hide_system",true),on->prefs.edit().putBoolean("hide_system",on).apply());
        int excluded=prefs.getStringSet("excluded",Collections.emptySet()).size();link("Исключённые приложения",excluded==0?"Нет":String.valueOf(excluded),()->showPage("excluded"));
        section("SMS");
        sms=toggle("Читать SMS напрямую",SmsSource.enabled(),on->{if(!on)SmsSource.setEnabled(false);else if(SmsSource.granted())SmsSource.setEnabled(true);else requestPermissions(new String[]{android.Manifest.permission.READ_SMS},422);sms.setChecked(SmsSource.enabled());});
        TextView smsHelp=text("Непрочитанные за 7 дней. Только на телефоне; свайп скрывает карточку здесь.",13,PanelColors.MUTED);smsHelp.setPadding(dp(16),dp(8),dp(16),0);body.addView(smsHelp);
        section("Портал");
        link("Spleta",SpletaSource.enabled()?"Подключение":"Подключить",()->startActivity(new Intent(this,SpletaActivity.class)));
        portal=text(SpletaSource.status(),13,PanelColors.MUTED);portal.setPadding(dp(16),dp(8),dp(16),0);body.addView(portal);
        section("Приложение");
        accessValue=link("Доступ к уведомлениям",hasAccess()?"Разрешён":"Разрешить",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        link("Виджет на рабочий стол","",()->{android.appwidget.AppWidgetManager wm=android.appwidget.AppWidgetManager.getInstance(this);if(wm.isRequestPinAppWidgetSupported())wm.requestPinAppWidget(new ComponentName(this,PanelWidget.class),null,null);else Toast.makeText(this,"Удерживайте рабочий стол → Виджеты → Шторка",Toast.LENGTH_LONG).show();});
        link("Обновления","",()->startActivity(new Intent(this,UpdateActivity.class)));
    }
    private void choices(String key,int[] values,String[] labels,int fallback){section("");int selected=prefs.getInt(key,fallback);
        for(int i=0;i<values.length;i++){final int value=values[i];LinearLayout r=row(labels[i]);ImageView check=new ImageView(this);check.setImageDrawable(new Mark(2));check.setVisibility(value==selected?View.VISIBLE:View.INVISIBLE);r.addView(check,new LinearLayout.LayoutParams(dp(22),dp(22)));r.setSelected(value==selected);r.setContentDescription(labels[i]+(value==selected?", выбрано":""));r.setFocusable(true);r.setBackground(new RippleDrawable(ColorStateList.valueOf(PanelColors.RIPPLE),null,null));r.setOnClickListener(v->{prefs.edit().putInt(key,value).apply();showPage("main");});add(r);}
    }
    private void excluded(){
        Map<String,String> names=new HashMap<>();Set<String> selected=new HashSet<>(prefs.getStringSet("excluded",Collections.emptySet()));
        for(StatusBarNotification s:NotificationBridge.snapshot()){if(SmsSource.owns(s)){names.put(SmsSource.groupKey(s),"SMS");continue;}if(s.getPackageName().equals(getPackageName()))continue;String label=s.getPackageName();try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(label,0)).toString();}catch(Exception ignored){}names.put(s.getUser().toString()+":"+s.getPackageName(),label);}
        for(String key:selected)names.putIfAbsent(key,key.substring(key.indexOf(':')+1));
        if(names.isEmpty()){TextView empty=text("Здесь появятся приложения, от которых получены уведомления.",16,PanelColors.MUTED);empty.setPadding(dp(16),dp(16),dp(16),0);body.addView(empty);return;}
        section("Не показывать в Шторке");List<String> keys=new ArrayList<>(names.keySet());Collections.sort(keys,(a,b)->names.get(a).compareToIgnoreCase(names.get(b)));
        for(String key:keys)toggle(names.get(key),selected.contains(key),on->{if(on)selected.add(key);else selected.remove(key);prefs.edit().putStringSet("excluded",new HashSet<>(selected)).apply();});
    }
    private void enableEdge(){
        if(!Settings.canDrawOverlays(this)){prefs.edit().putBoolean("edge_pending",true).apply();startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));return;}
        prefs.edit().putBoolean("edge_pending",false).apply();
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},421);return;}
        try{startForegroundService(new Intent(this,EdgePanelService.class));}catch(RuntimeException ex){Toast.makeText(this,"Не удалось включить боковую кнопку. Попробуйте ещё раз.",Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] result){super.onRequestPermissionsResult(code,p,result);if(code==422){if(result.length>0&&result[0]==0)SmsSource.setEnabled(true);else Toast.makeText(this,"Чтение SMS не разрешено",Toast.LENGTH_LONG).show();}if(code==421){if(result.length>0&&result[0]==0)enableEdge();else Toast.makeText(this,"Для боковой кнопки разрешите уведомления Шторки в настройках Android.",Toast.LENGTH_LONG).show();}}
    /** Small geometric navigation marks, drawn at native density. */
    private final class Mark extends Drawable {
        private final int type;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);Mark(int t){type=t;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.8f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(t==1?PanelColors.MUTED:PanelColors.ACCENT);}
        public void draw(Canvas c){Rect b=getBounds();float x=b.exactCenterX(),y=b.exactCenterY();Path p=new Path();if(type==2){p.moveTo(x-dp(7),y);p.lineTo(x-dp(2),y+dp(5));p.lineTo(x+dp(8),y-dp(5));}else{float d=dp(type==0?6:4),h=dp(type==0?8:5);p.moveTo(x+(type==0?d:-d),y-h);p.lineTo(x+(type==0?-d:d),y);p.lineTo(x+(type==0?d:-d),y+h);}c.drawPath(p,paint);}
        public void setAlpha(int a){paint.setAlpha(a);}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}public int getIntrinsicWidth(){return dp(24);}public int getIntrinsicHeight(){return dp(24);}
    }
}
