package local.notificationpanel;

import android.app.Notification;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;

public final class NotificationBridge extends NotificationListenerService {
    private static NotificationBridge instance;
    private final Set<String> transferring=new HashSet<>();
    private final Runnable transfer=this::transferVisible;
    static void modeChanged(){if(instance!=null){main.removeCallbacks(instance.transfer);main.postDelayed(instance.transfer,300);}changed();}
    private void transferVisible(){
        if(!LocalNotifications.enabled())return;
        for(StatusBarNotification s:new ArrayList<>(active.values())){
            if(transferring.contains(s.getKey())||!LocalNotifications.candidate(s))continue;
            // A summary can dismiss its entire group. Wait until ALL children are gone.
            if(LocalNotifications.summary(s)){
                boolean child=false;for(StatusBarNotification other:active.values())if(!LocalNotifications.summary(other)&&sameGroup(s,other)){child=true;break;}
                if(child)continue;
            }
            if(!LocalNotifications.capture(s))continue;
            try{transferring.add(s.getKey());cancelNotification(s.getKey());}
            catch(SecurityException e){transferring.remove(s.getKey());}
        }
        changed();
    }
    private static final Map<String,StatusBarNotification> active = new LinkedHashMap<>();
    private static final Set<Runnable> observers = new HashSet<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable notifyObservers = () -> {
        for (Runnable r : new ArrayList<>(observers)) r.run();
        if(PanelApplication.instance!=null)PanelWidget.refresh(PanelApplication.instance);
    };
    static void sourceChanged(){changed();}
    private static void changed() { main.removeCallbacks(notifyObservers); main.postDelayed(notifyObservers,150); }
    public static boolean connected() { return instance != null; }
    public static void watch(Runnable r) { observers.add(r); }
    public static void unwatch(Runnable r) { observers.remove(r); }
    public static List<StatusBarNotification> snapshot() { Map<String,StatusBarNotification> merged=new LinkedHashMap<>();for(StatusBarNotification s:LocalNotifications.snapshot())merged.put(s.getKey(),s);merged.putAll(active);List<StatusBarNotification> all=SmsSource.removeDuplicates(new ArrayList<>(merged.values()));all.addAll(SpletaSource.snapshot());all.addAll(SmsSource.snapshot());return all; }
    /** A live source notification, not a local copy or a resumable SystemUI history card. */
    static boolean hasMediaNotification(MediaController controller) {
        if(controller==null)return false;
        MediaSession.Token token=controller.getSessionToken();
        for(StatusBarNotification s:active.values()){
            if(!s.getUser().equals(android.os.Process.myUserHandle())||!s.getPackageName().equals(controller.getPackageName()))continue;
            Object media=s.getNotification().extras.get(Notification.EXTRA_MEDIA_SESSION);
            if(token.equals(media))return true;
        }
        return false;
    }
    @Override public void onListenerConnected() {
        instance=this;SmsSource.sync();transferring.clear();
        active.clear();
        try { for(StatusBarNotification s : getActiveNotifications()){active.put(s.getKey(),s);LocalNotifications.remove(s.getKey());} }
        catch(SecurityException ignored) { }
        modeChanged();
    }
    @Override public void onListenerDisconnected() { main.removeCallbacks(transfer);instance=null; active.clear(); transferring.clear();changed(); }
    @Override public void onDestroy() {
        if(instance==this) { main.removeCallbacks(transfer);instance=null; active.clear(); transferring.clear();changed(); }
        super.onDestroy();
    }
    @Override public void onNotificationPosted(StatusBarNotification s) {
        active.put(s.getKey(),s);transferring.remove(s.getKey());LocalNotifications.remove(s.getKey());SmsSource.sync();modeChanged();
    }
    @Override public void onNotificationRemoved(StatusBarNotification s) {
        active.remove(s.getKey());transferring.remove(s.getKey());modeChanged();
    }
    public static boolean dismiss(String key) {
        for(StatusBarNotification s:SmsSource.snapshot())if(s.getKey().equals(key))return SmsSource.hide(Collections.singletonList(s),0);
        for(StatusBarNotification s:SpletaSource.snapshot())if(s.getKey().equals(key))return SpletaSource.hide(Collections.singletonList(s),0);
        if(!active.containsKey(key)){for(StatusBarNotification s:LocalNotifications.snapshot())if(s.getKey().equals(key))return handleBatch(Collections.singletonList(s),0);return false;}
        LocalNotifications.remove(key);
        if(instance==null) return false;
        try {
            StatusBarNotification removed=active.get(key);
            instance.cancelNotification(key);
            // Remove an orphan summary only when this was its last active child.
            if((removed.getNotification().flags&android.app.Notification.FLAG_GROUP_SUMMARY)==0&&removed.getGroupKey()!=null){
                boolean sibling=false;
                for(StatusBarNotification s:active.values())if(!s.getKey().equals(key)&&sameGroup(removed,s)&&(s.getNotification().flags&android.app.Notification.FLAG_GROUP_SUMMARY)==0){sibling=true;break;}
                if(!sibling)for(StatusBarNotification s:new ArrayList<>(active.values()))if(sameGroup(removed,s)&&s.isClearable()&&(s.getNotification().flags&android.app.Notification.FLAG_GROUP_SUMMARY)!=0)instance.cancelNotification(s.getKey());
            }
            return true;
        }
        catch(SecurityException e) { return false; }
    }
    public static boolean dismissBatch(List<StatusBarNotification> expected) {return handleBatch(expected,0);}
    public static boolean snoozeBatch(List<StatusBarNotification> expected,long durationMs) {if(durationMs<=0)return false;return handleBatch(expected,durationMs);}
    private static boolean handleBatch(List<StatusBarNotification> expected,long durationMs) {
        if(expected.isEmpty())return false;
        if(SmsSource.owns(expected.get(0)))return SmsSource.hide(expected,durationMs);
        if(SpletaSource.owns(expected.get(0)))return SpletaSource.hide(expected,durationMs);
        for(StatusBarNotification s:expected)if(!s.isClearable()||!isCurrent(s))return false;
        List<StatusBarNotification> local=new ArrayList<>(),system=new ArrayList<>();
        for(StatusBarNotification s:expected)if(LocalNotifications.owns(s))local.add(s);else system.add(s);
        if(!local.isEmpty()){
            List<StatusBarNotification> all=snapshot();Set<String> keys=new HashSet<>();for(StatusBarNotification s:expected)keys.add(s.getKey());
            for(StatusBarNotification summary:all)if(LocalNotifications.owns(summary)&&LocalNotifications.summary(summary)&&!keys.contains(summary.getKey())){
                boolean related=false,survivor=false;for(StatusBarNotification s:local)if(sameGroup(s,summary))related=true;
                for(StatusBarNotification s:all)if(!LocalNotifications.summary(s)&&sameGroup(s,summary)&&!keys.contains(s.getKey()))survivor=true;
                if(related&&!survivor)local.add(summary);
            }
            if(!LocalNotifications.hide(local,durationMs))return false;
        }
        return system.isEmpty()||handleSystemBatch(system,durationMs);
    }
    private static boolean handleSystemBatch(List<StatusBarNotification> expected,long durationMs) {
        if(!expected.isEmpty()&&SmsSource.owns(expected.get(0)))return SmsSource.hide(expected,durationMs);
        if(!expected.isEmpty()&&SpletaSource.owns(expected.get(0)))return SpletaSource.hide(expected,durationMs);
        if(instance==null||expected.isEmpty())return false;
        Set<String> keys=new HashSet<>();
        for(StatusBarNotification s:expected){if(!s.isClearable()||!isCurrent(s))return false;keys.add(s.getKey());}
        // Include summaries only when no active child would be left behind.
        for(StatusBarNotification summary:active.values()){
            if(!summary.isClearable()||(summary.getNotification().flags&android.app.Notification.FLAG_GROUP_SUMMARY)==0)continue;
            boolean related=false,survivor=false;
            for(StatusBarNotification target:expected)if(sameGroup(summary,target)){related=true;break;}
            if(!related)continue;
            for(StatusBarNotification child:active.values())if(sameGroup(summary,child)&&(child.getNotification().flags&android.app.Notification.FLAG_GROUP_SUMMARY)==0&&!keys.contains(child.getKey())){survivor=true;break;}
            if(!survivor)keys.add(summary.getKey());else keys.remove(summary.getKey());
        }
        if(keys.isEmpty())return false;
        for(String key:keys)LocalNotifications.remove(key);
        try{if(durationMs>0){for(String key:keys)instance.snoozeNotification(key,durationMs);}else instance.cancelNotifications(keys.toArray(new String[0]));return true;}catch(SecurityException e){return false;}
    }
    private static boolean sameGroup(StatusBarNotification a,StatusBarNotification b){return a.getPackageName().equals(b.getPackageName())&&a.getUser().equals(b.getUser())&&Objects.equals(a.getGroupKey(),b.getGroupKey());}
    public static boolean exists(String key) { return active.containsKey(key)||LocalNotifications.contains(key); }
    public static boolean isCurrent(StatusBarNotification s) { return SmsSource.owns(s)?SmsSource.current(s):SpletaSource.owns(s)?SpletaSource.current(s):(LocalNotifications.owns(s)||active.get(s.getKey())==s); }
}
