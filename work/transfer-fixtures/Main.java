package local.panelbench.transferfixtures;
import android.app.*;import android.os.*;import android.content.*;
public final class Main extends Activity {
 public void onCreate(Bundle saved){super.onCreate(saved);NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("fixture","Transfer test",NotificationManager.IMPORTANCE_LOW));
 if(getIntent().getBooleanExtra("clear",false)){nm.cancelAll();finish();return;}
 String[] cats={Notification.CATEGORY_EMAIL,Notification.CATEGORY_CALL,Notification.CATEGORY_ALARM,Notification.CATEGORY_NAVIGATION,Notification.CATEGORY_TRANSPORT};
 for(int i=0;i<cats.length;i++){Notification.Builder b=new Notification.Builder(this,"fixture").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Test "+cats[i]).setContentText("Synthetic fixture "+i).setCategory(cats[i]);if(i<2)b.setGroup("mixed");nm.notify(100+i,b.build());}
 nm.notify(200,new Notification.Builder(this,"fixture").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Mixed group").setContentText("Email and call").setGroup("mixed").setGroupSummary(true).build());finish();
 }
}
