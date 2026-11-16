package local.notificationpanel;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.provider.Telephony;
import android.widget.Toast;

/** User-initiated navigation only: no body, send operation or default-role change. */
final class SmsOpener {
    interface Launcher { void launch(Intent intent); }
    static Intent conversation(String sender,String pkg){
        if(sender==null||sender.trim().isEmpty()||pkg==null||pkg.isEmpty())return null;
        return new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",sender.trim(),null)).setPackage(pkg);
    }
    static boolean launch(Intent conversation,Intent fallback,Launcher launcher){
        if(conversation!=null)try{launcher.launch(conversation);return true;}catch(ActivityNotFoundException|SecurityException ignored){}
        if(fallback!=null)try{launcher.launch(fallback);return true;}catch(ActivityNotFoundException|SecurityException ignored){}
        return false;
    }
    static void open(Activity activity,String sender){
        String pkg=Telephony.Sms.getDefaultSmsPackage(activity);
        Intent fallback=pkg==null?null:activity.getPackageManager().getLaunchIntentForPackage(pkg);
        if(!launch(conversation(sender,pkg),fallback,activity::startActivity))Toast.makeText(activity,"Не удалось открыть «Сообщения». Проверьте основное SMS-приложение в настройках Android.",Toast.LENGTH_LONG).show();
    }
}
