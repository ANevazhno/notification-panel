package local.notificationpanel;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.Telephony;
import android.service.notification.StatusBarNotification;
import java.util.*;
import java.util.concurrent.*;

/** Read-only local inbox. Never posted to Android, persisted, or sent to a server. */
final class SmsSource {
    static final String CHANNEL="local_sms";
    private static Context context;
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService io=Executors.newSingleThreadExecutor();
    private static final Map<String,StatusBarNotification> items=new LinkedHashMap<>();
    private static final Map<String,Long> hidden=new HashMap<>();
    private static boolean busy,again,registered;
    private static long generation;
    private static String status="Выключено";
    private static final ContentObserver observer=new ContentObserver(main){public void onChange(boolean self){sync();}};
    static void init(Context c){context=c.getApplicationContext();
        for(Map.Entry<String,?> e:context.getSharedPreferences("sms_hidden",0).getAll().entrySet())if(e.getValue() instanceof Long)hidden.put(e.getKey(),(Long)e.getValue());
    }
    static boolean granted(){return context!=null&&context.checkSelfPermission(android.Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED;}
    static boolean enabled(){return granted()&&context.getSharedPreferences("panel_settings",0).getBoolean("read_sms",false);}
    private static boolean unlocked(){return !context.getSystemService(KeyguardManager.class).isDeviceLocked();}
    static String status(){return !enabled()?(granted()?"Выключено":"Нужно разрешение"):status;}
    static void setEnabled(boolean on){context.getSharedPreferences("panel_settings",0).edit().putBoolean("read_sms",on).apply();invalidate();sync();}
    static void invalidate(){generation++;items.clear();NotificationBridge.sourceChanged();}
    static boolean owns(StatusBarNotification s){return CHANNEL.equals(s.getNotification().getChannelId())&&s.getPackageName().equals(context.getPackageName())&&s.getTag()!=null&&s.getTag().startsWith("sms:");}
    static String groupKey(StatusBarNotification s){return s.getUser()+":"+s.getPackageName()+(owns(s)?":sms":"");}
    static boolean current(StatusBarNotification s){return enabled()&&unlocked()&&owns(s)&&items.get(s.getTag())==s&&hidden.getOrDefault(s.getTag(),0L)<=System.currentTimeMillis();}
    static List<StatusBarNotification> snapshot(){List<StatusBarNotification> out=new ArrayList<>();if(enabled()&&unlocked())for(StatusBarNotification s:items.values())if(current(s))out.add(s);return out;}
    static List<StatusBarNotification> removeDuplicates(List<StatusBarNotification> active){
        if(!enabled()||!unlocked())return active;
        return SmsDuplicates.filter(active,new ArrayList<>(items.values()),Telephony.Sms.getDefaultSmsPackage(context));
    }
    static boolean hide(List<StatusBarNotification> rows,long duration){
        for(StatusBarNotification s:rows)if(!current(s))return false;
        SharedPreferences.Editor edit=context.getSharedPreferences("sms_hidden",0).edit();
        for(StatusBarNotification s:rows){long until=duration>0?System.currentTimeMillis()+duration:Long.MAX_VALUE;hidden.put(s.getTag(),until);edit.putLong(s.getTag(),until);}edit.apply();NotificationBridge.sourceChanged();
        if(duration>0)main.postDelayed(NotificationBridge::sourceChanged,duration);return true;
    }
    static void sync(){
        if(context==null)return;
        if(!enabled()){
            if(registered){context.getContentResolver().unregisterContentObserver(observer);registered=false;}
            if(!items.isEmpty())invalidate();return;
        }
        if(!unlocked()){if(!items.isEmpty())invalidate();return;}
        if(!registered)try{context.getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI,true,observer);registered=true;}catch(SecurityException ignored){}
        if(busy){again=true;return;}busy=true;final long token=generation;
        io.execute(()->{
            LinkedHashMap<String,StatusBarNotification> next=new LinkedHashMap<>();String result;
            try(Cursor c=context.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,new String[]{"_id","address","body","date","thread_id"},"read = 0 AND date >= ?",new String[]{String.valueOf(System.currentTimeMillis()-7L*24*60*60*1000)},"date DESC")){
                if(c==null)throw new IllegalStateException();
                while(next.size()<100&&c.moveToNext()){
                    String tag="sms:"+c.getLong(0),sender=c.getString(1),body=c.getString(2);long date=c.getLong(3),thread=c.getLong(4);
                    if(body==null||body.isEmpty())continue;
                    Notification n=new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_sms).setCategory(Notification.CATEGORY_MESSAGE).setContentTitle(sender==null||sender.isEmpty()?"SMS":sender).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setWhen(date).build();
                    next.put(tag,new StatusBarNotification(context.getPackageName(),context.getPackageName(),0,tag,android.os.Process.myUid(),0,0,n,android.os.Process.myUserHandle(),date));
                }
                result=next.isEmpty()?"Нет непрочитанных за 7 дней":"Непрочитанных: "+next.size();
            }catch(Exception e){next.clear();result="Не удалось прочитать SMS";}
            final String finalStatus=result;
            main.post(()->{busy=false;if(token==generation&&enabled()&&unlocked()){
                // Reuse unchanged objects so open actions and swipe captures stay valid.
                for(Map.Entry<String,StatusBarNotification> e:next.entrySet()){StatusBarNotification old=items.get(e.getKey());if(old!=null&&same(old,e.getValue()))e.setValue(old);}
                boolean changed=!items.equals(next);items.clear();items.putAll(next);status=finalStatus;if(changed)NotificationBridge.sourceChanged();
            }if(again){again=false;sync();}});
        });
    }
    private static boolean same(StatusBarNotification a,StatusBarNotification b){return a.getPostTime()==b.getPostTime()&&Objects.equals(a.getNotification().extras.getString(Notification.EXTRA_TEXT),b.getNotification().extras.getString(Notification.EXTRA_TEXT))&&Objects.equals(a.getNotification().extras.getString(Notification.EXTRA_TITLE),b.getNotification().extras.getString(Notification.EXTRA_TITLE));}
}
