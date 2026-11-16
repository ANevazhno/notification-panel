package local.panelbench;
import android.app.*;import android.os.*;import android.content.*;import android.database.*;import android.provider.Telephony;import android.service.notification.StatusBarNotification;import java.util.*;import java.lang.reflect.*;
public final class SmsInstrumentation extends Instrumentation {
 Class<?> source;final String marker="Panel_SMS_integration_fixture_0617";
 Object call(String name,Class<?>[] types,Object...args)throws Exception {Method m=source.getDeclaredMethod(name,types);m.setAccessible(true);final Object[] result={null};final Throwable[] error={null};runOnMainSync(()->{try{result[0]=m.invoke(null,args);}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);return result[0];}
 Object call(String name)throws Exception{return call(name,new Class<?>[0]);}
 StatusBarNotification fixture()throws Exception {for(Object o:(List<?>)call("snapshot")){StatusBarNotification s=(StatusBarNotification)o;if(marker.equals(s.getNotification().extras.getString(Notification.EXTRA_TEXT)))return s;}return null;}
 void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{
 source=getTargetContext().getClassLoader().loadClass("local.notificationpanel.SmsSource");String original=Telephony.Sms.getDefaultSmsPackage(getTargetContext());
 call("setEnabled",new Class[]{boolean.class},true);StatusBarNotification s=null;for(int i=0;i<30&&s==null;i++){Thread.sleep(200);s=fixture();}check(s!=null,"new unread SMS missing; enabled="+call("enabled")+" status="+call("status")+" locked="+getTargetContext().getSystemService(android.app.KeyguardManager.class).isDeviceLocked());
 check(!(Boolean)call("groupKey",new Class[]{StatusBarNotification.class},s).equals(s.getUser()+":"+s.getPackageName()),"SMS merges with portal");
 call("sync");Thread.sleep(500);check(fixture()==s,"unchanged item identity lost");
 check((Boolean)call("hide",new Class[]{List.class,long.class},Collections.singletonList(s),300L),"snooze failed");check(fixture()==null,"snooze not hidden");Thread.sleep(400);check(fixture()!=null,"snooze did not return");
 call("setEnabled",new Class[]{boolean.class},false);check(((List<?>)call("snapshot")).isEmpty(),"disabled SMS leaked");call("setEnabled",new Class[]{boolean.class},true);Thread.sleep(700);s=fixture();check(s!=null,"enable failed");call("hide",new Class[]{List.class,long.class},Collections.singletonList(s),0L);check(fixture()==null,"dismiss failed");
 try(Cursor c=getTargetContext().getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,new String[]{"read"},"body = ?",new String[]{marker},null)){check(c!=null&&c.moveToFirst()&&c.getInt(0)==0,"SMS read state changed");}
 check(Objects.equals(original,Telephony.Sms.getDefaultSmsPackage(getTargetContext())),"default SMS changed");out.putString("result","PASS: inbox read, grouping, stable updates, snooze/return, disable/re-enable, dismiss without marking read, default SMS unchanged");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
