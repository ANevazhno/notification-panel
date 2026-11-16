package local.panelbench;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import java.lang.reflect.*;
public final class EdgeInstrumentation extends Instrumentation {
 Class<?> edge; final StringBuilder report=new StringBuilder();
 public void onCreate(Bundle b){super.onCreate(b);start();}
 Object call(String name,Class<?>[] types,Object... args)throws Exception{Method m=edge.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}
 Object field(String name,Object owner)throws Exception{Field f=edge.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 void check(boolean ok,String label){if(!ok)throw new AssertionError(label);report.append("PASS ").append(label).append('\n');}
 void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
 public void onStart(){Bundle result=new Bundle();try{
  edge=Class.forName("local.notificationpanel.EdgePanelService");Context c=getTargetContext();
  Activity a=startActivitySync(new Intent().setClassName(c,"local.notificationpanel.PanelActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  main(()->c.startForegroundService(new Intent().setClassName(c,"local.notificationpanel.EdgePanelService")));SystemClock.sleep(600);
  Service service=(Service)field("instance",null);check(service!=null,"Service starts");
  check((Boolean)call("enabled",new Class[]{Context.class},c),"Enabled choice persists");
  View handle=(View)field("handle",service);check(!handle.isLongClickable(),"Long press cannot disable handle");
  check(handle.getVisibility()==View.GONE,"Handle hidden inside panel");
  main(a::finish);SystemClock.sleep(400);
  check(handle.getVisibility()==View.VISIBLE,"Handle visible after leaving panel");
  main(()->handle.setVisibility(View.GONE));SystemClock.sleep(1400);
  check(handle.getVisibility()==View.VISIBLE,"Stale hidden handle recovers without an Activity or broadcast");
  android.os.Handler h=(android.os.Handler)field("visibilityHandler",service);
  Runnable reconcile=(Runnable)field("reconcileVisibility",service);
  android.content.BroadcastReceiver receiver=(android.content.BroadcastReceiver)field("screen",service);
  main(()->receiver.onReceive(c,new Intent(Intent.ACTION_SCREEN_OFF)));
  check(handle.getVisibility()==View.GONE&&!h.hasCallbacks(reconcile),"Screen-off hides handle and cancels polling");
  main(()->receiver.onReceive(c,new Intent(Intent.ACTION_SCREEN_ON)));SystemClock.sleep(100);
  check(handle.getVisibility()==View.VISIBLE&&h.hasCallbacks(reconcile),"Screen-on resumes reconciliation");
  Activity active=startActivitySync(new Intent().setClassName(c,"local.notificationpanel.PanelActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  SystemClock.sleep(1200);check(handle.getVisibility()==View.GONE,"Reconciliation never exposes handle over panel");
  int[] mode={0};main(()->mode[0]=service.onStartCommand(null,0,999));check(mode[0]==Service.START_STICKY,"System restart accepted as sticky");
  main(()->c.stopService(new Intent().setClassName(c,"local.notificationpanel.EdgePanelService")));SystemClock.sleep(300);
  check(!h.hasCallbacks(reconcile),"Service destruction cancels polling");
  check((Boolean)call("enabled",new Class[]{Context.class},c),"Service loss preserves enabled choice");
  main(()->{try{call("restore",new Class[]{Context.class},a);}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(500);
  check(field("instance",null)!=null,"Foreground resume restores lost service");
  main(()->{try{call("stop",new Class[]{Context.class},c);}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(300);
  check(!(Boolean)call("enabled",new Class[]{Context.class},c),"Explicit stop clears enabled choice");
  main(()->{try{call("restore",new Class[]{Context.class},a);}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(300);
  check(field("instance",null)==null,"Explicit stop is respected on resume");
  main(active::finish);result.putString("stream",report.toString());finish(-1,result);
 }catch(Throwable e){result.putString("stream",report+"FAIL "+e);finish(0,result);}}
}
