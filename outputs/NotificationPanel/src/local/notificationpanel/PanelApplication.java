package local.notificationpanel;
import android.app.Application;
import android.content.*;
import android.os.*;

public final class PanelApplication extends Application {
    static PanelApplication instance;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener settings=(p,k)->{PanelWidget.refresh(this);if(LocalNotifications.SETTING.equals(k))NotificationBridge.modeChanged();};
    public void onCreate(){super.onCreate();instance=this;LocalNotifications.init(this);SpletaSource.init(this);SmsSource.init(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            public void onActivityStarted(android.app.Activity a){}public void onActivityStopped(android.app.Activity a){}
            public void onActivityCreated(android.app.Activity a,Bundle b){}public void onActivityResumed(android.app.Activity a){EdgePanelService.activityVisible(true);EdgePanelService.restore(a);SpletaPushService.restore(a);SmsSource.sync();}public void onActivityPaused(android.app.Activity a){EdgePanelService.activityVisible(false);}public void onActivitySaveInstanceState(android.app.Activity a,Bundle b){}public void onActivityDestroyed(android.app.Activity a){}
        });
        getSharedPreferences("panel_settings",0).registerOnSharedPreferenceChangeListener(settings);
        IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_USER_PRESENT);
        BroadcastReceiver r=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))SmsSource.invalidate();else SmsSource.sync();PanelWidget.refresh(c);}};
        if(Build.VERSION.SDK_INT>=33)registerReceiver(r,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(r,f);
        new Handler(Looper.getMainLooper()).post(()->PanelWidget.refresh(this));
    }
}
