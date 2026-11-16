package local.notificationpanel;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private, bounded text copies. A successful durable write MUST precede cancellation. */
final class LocalNotifications {
    static final String SETTING="local_notifications";
    private static final long TTL=7*24*60*60*1000L;
    private static final int LIMIT=200;
    private static final Map<String,Record> records=new LinkedHashMap<>();
    private static Context context;
    private static AtomicFile file;
    private static boolean loaded;
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final Runnable wake=()->NotificationBridge.sourceChanged();
    private static final class Record {
        StatusBarNotification sbn; JSONObject json; long until,hidden;
        Record(StatusBarNotification s,JSONObject j,long expiry,long snooze){sbn=s;json=j;until=expiry;hidden=snooze;}
    }
    static void init(Context c){context=c.getApplicationContext();file=new AtomicFile(new File(context.getFilesDir(),"local-notifications.json"));load();}
    static boolean enabled(){return context!=null&&context.getSharedPreferences("panel_settings",0).getBoolean(SETTING,false);}
    static boolean protectedNotification(StatusBarNotification s){
        Notification n=s.getNotification();String cat=n.category;
        return !s.getUser().equals(android.os.Process.myUserHandle())||!s.isClearable()||s.isOngoing()
            ||(n.flags&(Notification.FLAG_FOREGROUND_SERVICE|Notification.FLAG_NO_CLEAR))!=0
            ||n.fullScreenIntent!=null||Notification.CATEGORY_CALL.equals(cat)||Notification.CATEGORY_ALARM.equals(cat)
            ||Notification.CATEGORY_NAVIGATION.equals(cat)||Notification.CATEGORY_TRANSPORT.equals(cat)
            ||n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
            ||s.getPackageName().equals("android")||s.getPackageName().equals("com.android.systemui")
            ||s.getPackageName().equals("local.notificationpanel");
    }
    static boolean candidate(StatusBarNotification s){return !protectedNotification(s)&&!NotificationPolicy.excluded(context,s);}
    static boolean summary(StatusBarNotification s){return (s.getNotification().flags&Notification.FLAG_GROUP_SUMMARY)!=0;}
    private static String value(CharSequence s){return s==null?"":s.toString();}
    private static JSONObject encode(StatusBarNotification s)throws JSONException{
        Notification n=s.getNotification();Bundle e=n.extras;
        String title=value(e.getCharSequence(Notification.EXTRA_TITLE));
        String body=value(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if(body.isEmpty())body=value(e.getCharSequence(Notification.EXTRA_TEXT));
        CharSequence[] lines=e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if(lines!=null&&lines.length>0){StringBuilder b=new StringBuilder();for(CharSequence line:lines){if(b.length()>0)b.append('\n');b.append(value(line));}body=b.toString();}
        Parcelable[] messages=e.getParcelableArray(Notification.EXTRA_MESSAGES);
        if(messages!=null&&messages.length>0){
            List<Notification.MessagingStyle.Message> parsed=Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages);if(parsed.size()!=messages.length)throw new JSONException("Invalid messages");
            StringBuilder b=new StringBuilder();for(Notification.MessagingStyle.Message message:parsed){
                if(message.getDataUri()!=null)throw new JSONException("Attachment needs original notification");
                if(b.length()>0)b.append('\n');if(message.getSenderPerson()!=null)b.append(value(message.getSenderPerson().getName())).append(": ");b.append(value(message.getText()));
            }body=b.toString();
        }
        // Never cancel content we cannot represent completely in a text card.
        if((title.isEmpty()&&body.isEmpty())||title.length()>2048||body.length()>32768||n.contentView!=null||n.bigContentView!=null||e.containsKey(Notification.EXTRA_PICTURE))throw new JSONException("Unsupported content");
        return new JSONObject().put("key",s.getKey()).put("pkg",s.getPackageName()).put("id",s.getId()).put("tag",s.getTag())
            .put("uid",s.getUid()).put("post",s.getPostTime()).put("when",n.when).put("title",title).put("body",body)
            .put("group",n.getGroup()).put("override",s.getOverrideGroupKey()).put("summary",summary(s)).put("visibility",n.visibility);
    }
    private static StatusBarNotification decode(JSONObject j)throws JSONException{
        Notification.Builder b=new Notification.Builder(context,"local_copy").setSmallIcon(R.drawable.ic_panel)
            .setContentTitle(j.getString("title")).setContentText(j.getString("body"))
            .setStyle(new Notification.BigTextStyle().bigText(j.getString("body"))).setWhen(j.getLong("when"))
            .setVisibility(j.optInt("visibility",Notification.VISIBILITY_PRIVATE)).setGroupSummary(j.optBoolean("summary"));
        if(j.has("group"))b.setGroup(j.getString("group"));
        StatusBarNotification s=new StatusBarNotification(j.getString("pkg"),j.getString("pkg"),j.getInt("id"),j.optString("tag",null),j.getInt("uid"),0,0,b.build(),android.os.Process.myUserHandle(),j.getLong("post"));
        s.setOverrideGroupKey(j.optString("override",null));
        if(!s.getKey().equals(j.getString("key")))throw new JSONException("Key mismatch");return s;
    }
    private static void load(){
        if(loaded||file==null)return;loaded=true;
        if(!file.getBaseFile().exists())return;
        try{JSONArray a=new JSONArray(new String(file.readFully(),StandardCharsets.UTF_8));long now=System.currentTimeMillis();
            for(int i=0;i<a.length()&&records.size()<LIMIT;i++){JSONObject j=a.getJSONObject(i);long until=j.getLong("until");if(until<=now)continue;StatusBarNotification s=decode(j);records.put(s.getKey(),new Record(s,j,until,j.optLong("hidden")));}
        }catch(Exception e){records.clear();loaded=false; /* Fail closed: never overwrite unreadable copies. */}
    }
    private static boolean save(Map<String,Record> next){
        if(!loaded||file==null)return false;FileOutputStream stream=null;
        try{JSONArray a=new JSONArray();for(Record r:next.values()){JSONObject j=new JSONObject(r.json.toString());j.put("until",r.until).put("hidden",r.hidden);a.put(j);}stream=file.startWrite();stream.write(a.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(stream);records.clear();records.putAll(next);schedule();return true;}
        catch(Exception e){if(stream!=null)file.failWrite(stream);return false;}
    }
    static boolean capture(StatusBarNotification s){
        load();if(!loaded)return false;
        try{JSONObject j=encode(s);StatusBarNotification copy=decode(j);
            // Keep only the open action while its token is alive; no stale reply/read actions.
            copy.getNotification().contentIntent=s.getNotification().contentIntent;
            Map<String,Record> next=new LinkedHashMap<>(records);long now=System.currentTimeMillis();next.values().removeIf(r->r.until<=now);
            if(!next.containsKey(s.getKey())&&next.size()>=LIMIT)return false;
            next.put(s.getKey(),new Record(copy,j,now+TTL,0));return save(next);
        }catch(Exception e){return false;}
    }
    static List<StatusBarNotification> snapshot(){
        load();long now=System.currentTimeMillis();List<StatusBarNotification> result=new ArrayList<>();
        Map<String,Record> live=new LinkedHashMap<>(records);live.values().removeIf(r->r.until<=now);if(live.size()!=records.size())save(live);
        for(Record r:records.values())if(r.until>now&&r.hidden<=now)result.add(r.sbn);schedule();return result;
    }
    private static void schedule(){main.removeCallbacks(wake);long now=System.currentTimeMillis(),next=Long.MAX_VALUE;for(Record r:records.values()){if(r.until>now)next=Math.min(next,r.until);if(r.hidden>now)next=Math.min(next,r.hidden);}if(next!=Long.MAX_VALUE)main.postDelayed(wake,Math.max(1,next-now));}
    static boolean owns(StatusBarNotification s){Record r=records.get(s.getKey());return r!=null&&r.sbn==s;}
    static boolean contains(String key){return records.containsKey(key);}
    static boolean remove(String key){if(!records.containsKey(key))return true;Map<String,Record> next=new LinkedHashMap<>(records);next.remove(key);return save(next);}
    static boolean hide(List<StatusBarNotification> items,long duration){
        Map<String,Record> next=new LinkedHashMap<>(records);for(StatusBarNotification s:items){Record r=next.get(s.getKey());if(r==null||r.sbn!=s)return false;if(duration==0)next.remove(s.getKey());else next.put(s.getKey(),new Record(r.sbn,r.json,r.until,System.currentTimeMillis()+duration));}
        boolean ok=save(next);if(ok)NotificationBridge.sourceChanged();return ok;
    }
    static void open(Activity activity,StatusBarNotification s){
        try{PendingIntent pi=s.getNotification().contentIntent;if(pi!=null){Bundle options=Build.VERSION.SDK_INT>=34?ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle():null;pi.send(activity,0,null,null,null,null,options);return;}}catch(Exception ignored){}
        try{Intent launch=activity.getPackageManager().getLaunchIntentForPackage(s.getPackageName());if(launch!=null){activity.startActivity(launch);return;}}catch(RuntimeException ignored){}
        android.widget.Toast.makeText(activity,"Не удалось открыть приложение",android.widget.Toast.LENGTH_SHORT).show();
    }
}
