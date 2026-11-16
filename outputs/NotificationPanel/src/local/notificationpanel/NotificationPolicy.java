package local.notificationpanel;
import android.app.Notification;
import android.content.*;
import android.service.notification.StatusBarNotification;
import java.util.Collections;

final class NotificationPolicy {
    static boolean excluded(Context c,StatusBarNotification s){
        SharedPreferences prefs=c.getSharedPreferences("panel_settings",0);Notification n=s.getNotification();
        String app=SmsSource.groupKey(s);
        if((s.getPackageName().equals(c.getPackageName())&&!SpletaSource.owns(s)&&!SmsSource.owns(s))||prefs.getStringSet("excluded",Collections.emptySet()).contains(app))return true;
        boolean ongoing=s.isOngoing()||(n.flags&(Notification.FLAG_FOREGROUND_SERVICE|Notification.FLAG_NO_CLEAR))!=0;
        if(prefs.getBoolean("hide_ongoing",true)&&ongoing)return true;
        boolean special=Notification.CATEGORY_CALL.equals(n.category)||Notification.CATEGORY_ALARM.equals(n.category)||Notification.CATEGORY_NAVIGATION.equals(n.category)||Notification.CATEGORY_TRANSPORT.equals(n.category)||n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION);
        boolean system=s.getPackageName().equals("android")||s.getPackageName().equals("com.android.systemui");
        return (prefs.getBoolean("hide_special",true)&&special)||(prefs.getBoolean("hide_system",true)&&system);
    }
}
