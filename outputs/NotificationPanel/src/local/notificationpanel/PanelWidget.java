package local.notificationpanel;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.widget.RemoteViews;
import android.view.View;
import java.util.*;

public final class PanelWidget extends AppWidgetProvider {
    static final String GROUP="widget_group";
    private static final class Entry implements Grouping.Entry {
        final Context c;final StatusBarNotification s;
        Entry(Context c,StatusBarNotification s){this.c=c;this.s=s;}
        public String key(){return s.getKey();}public String appKey(){return SmsSource.groupKey(s);}
        public String originalGroup(){return s.getGroupKey();}public boolean summary(){return (s.getNotification().flags&Notification.FLAG_GROUP_SUMMARY)!=0;}
        public boolean excluded(){return NotificationPolicy.excluded(c,s);}public long time(){return s.getPostTime();}
    }
    public void onUpdate(Context c,AppWidgetManager m,int[] ids){refresh(c);}
    public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle b){refresh(c);}
    static void refresh(Context c){
        if(Looper.myLooper()!=Looper.getMainLooper()){new Handler(Looper.getMainLooper()).post(()->refresh(c));return;}
        AppWidgetManager m=AppWidgetManager.getInstance(c);int[] ids=m.getAppWidgetIds(new ComponentName(c,PanelWidget.class));
        if(ids.length==0)return;
        try{m.updateAppWidget(ids,views(c));}catch(RuntimeException ignored){}
    }
    static PendingIntent open(Context c,String group){
        Intent i=new Intent(c,PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.setData(Uri.parse("panel-widget://open/"+Uri.encode(group==null?"":group)));
        if(group!=null)i.putExtra(GROUP,group);
        return PendingIntent.getActivity(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static RemoteViews views(Context c){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.panel_widget);
        v.setOnClickPendingIntent(R.id.widget_open,open(c,null));v.setOnClickPendingIntent(R.id.widget_footer,open(c,null));v.removeAllViews(R.id.widget_rows);
        if(Build.VERSION.SDK_INT>=31){
            v.setRemoteAdapter(R.id.widget_list,new RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1).build());
            Intent template=new Intent(c,PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            v.setPendingIntentTemplate(R.id.widget_list,PendingIntent.getActivity(c,700,template,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE));
        }else{v.setViewVisibility(R.id.widget_list,View.GONE);v.setViewVisibility(R.id.widget_rows,View.VISIBLE);}
        KeyguardManager k=c.getSystemService(KeyguardManager.class);
        if(k!=null&&k.isDeviceLocked()){v.setTextViewText(R.id.widget_status,"Разблокируйте телефон и откройте панель");return v;}
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(!nm.isNotificationListenerAccessGranted(new ComponentName(c,NotificationBridge.class))){v.setTextViewText(R.id.widget_status,"Нажмите, чтобы разрешить доступ к уведомлениям");return v;}
        if(!NotificationBridge.connected()&&!SpletaSource.enabled()){
            v.setTextViewText(R.id.widget_status,"Ожидаем подключения · нажмите, чтобы открыть панель");
            android.service.notification.NotificationListenerService.requestRebind(new ComponentName(c,NotificationBridge.class));return v;
        }
        List<Entry> source=new ArrayList<>();for(StatusBarNotification s:NotificationBridge.snapshot())source.add(new Entry(c,s));
        LinkedHashMap<String,List<Entry>> groups=Grouping.group(source);int total=0;for(List<Entry> es:groups.values())total+=es.size();
        String stamp=android.text.format.DateFormat.format("HH:mm",new Date()).toString();
        v.setTextViewText(R.id.widget_status,groups.isEmpty()?"Нет уведомлений · обновлено "+stamp:"Приложений: "+groups.size()+" · уведомлений: "+total+" · "+stamp);
        int shown=0;
        RemoteViews.RemoteCollectionItems.Builder items=Build.VERSION.SDK_INT>=31?new RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1):null;
        for(Map.Entry<String,List<Entry>> group:groups.entrySet()){
            if(shown++>=(Build.VERSION.SDK_INT>=31?60:5))break;
            StatusBarNotification s=group.getValue().get(0).s;RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_row);String label=s.getPackageName();
            try{
                Context pc=c.createPackageContext(s.getPackageName(),0);label=String.valueOf(pc.getApplicationInfo().loadLabel(pc.getPackageManager()));
                Drawable d=pc.getApplicationInfo().loadIcon(pc.getPackageManager());Bitmap b=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);d.setBounds(0,0,48,48);d.draw(canvas);row.setImageViewBitmap(R.id.widget_icon,b);
            }catch(Exception ignored){}
            if(SmsSource.owns(s)){label="SMS";row.setImageViewResource(R.id.widget_icon,R.drawable.ic_sms);}
            if(SpletaSource.owns(s)){label="Spleta";row.setImageViewResource(R.id.widget_icon,R.drawable.ic_spleta);}
            if(!s.getUser().equals(android.os.Process.myUserHandle()))label+=" · другой профиль";
            row.setTextViewText(R.id.widget_name,label);row.setTextViewText(R.id.widget_count,String.valueOf(group.getValue().size()));
            Bundle extras=s.getNotification().extras;CharSequence title=extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);if(title==null)title=extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence body=extras.getCharSequence(Notification.EXTRA_TEXT);if(body==null)body=extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            row.setTextViewText(R.id.widget_title,title==null?"Уведомление":title.toString());
            String preview=body==null?"":body.toString();
            row.setTextViewText(R.id.widget_more,"");row.setViewVisibility(R.id.widget_more,View.GONE);
            if(group.getValue().size()>1){
                LinkedHashSet<String> names=new LinkedHashSet<>();
                for(Entry item:group.getValue()){
                    Bundle other=item.s.getNotification().extras;CharSequence name=other.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);if(name==null)name=other.getCharSequence(Notification.EXTRA_TITLE);
                    if(name!=null&&(title==null||!name.toString().equals(title.toString())))names.add(name.toString());
                }
                StringBuilder more=new StringBuilder();int n=0;for(String name:names){if(n++==2)break;if(more.length()>0)more.append(" · ");more.append(name);}
                row.setTextViewText(R.id.widget_more,more.length()>0?"Ещё: "+more:"Ещё уведомлений: "+(group.getValue().size()-1));row.setViewVisibility(R.id.widget_more,View.VISIBLE);
            }
            if(preview.isEmpty())preview="Открыть уведомления";if(preview.length()>240)preview=preview.substring(0,240)+"…";
            row.setTextViewText(R.id.widget_preview,preview);if(Build.VERSION.SDK_INT>=31){row.setOnClickFillInIntent(R.id.widget_row,new Intent().putExtra(GROUP,group.getKey()));items.addItem(shown,row);}
            else{row.setOnClickPendingIntent(R.id.widget_row,open(c,group.getKey()));v.addView(R.id.widget_rows,row);}
        }
        if(Build.VERSION.SDK_INT>=31)v.setRemoteAdapter(R.id.widget_list,items.build());
        if(groups.size()>(Build.VERSION.SDK_INT>=31?60:5))v.setTextViewText(R.id.widget_footer,"Все приложения в панели  →");return v;
    }
}

