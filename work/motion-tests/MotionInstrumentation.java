package local.panelbench;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
public final class MotionInstrumentation extends Instrumentation {
 Activity panel; ListView list; final StringBuilder report=new StringBuilder();
 public void onCreate(Bundle b){super.onCreate(b);start();}
 String shell(String cmd)throws Exception{try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(cmd))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8").trim();}}
 void check(boolean b,String label){if(!b)throw new AssertionError(label);report.append("PASS ").append(label).append('\n');}
 View header(View v){if(v.getTag() instanceof String&&v.getTag().toString().startsWith("group:"))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=header(((ViewGroup)v).getChildAt(i));if(r!=null)return r;}return null;}
 void click(){runOnMainSync(()->{View v=header(list.getChildAt(0));if(!v.isClickable())v=(View)v.getParent();v.performClick();});}
 int height(){int[] h={0};runOnMainSync(()->h[0]=list.getChildAt(0).getHeight());return h[0];}
 Set<Integer> sample(){Set<Integer> sizes=new HashSet<>();for(int i=0;i<22;i++){sizes.add(height());SystemClock.sleep(16);}return sizes;}
 public void onStart(){Bundle result=new Bundle();String scale="1";try{
  scale=shell("settings get global animator_duration_scale");shell("settings put global animator_duration_scale 1");
  shell("cmd notification disallow_listener local.notificationpanel/.NotificationBridge");shell("cmd notification allow_listener local.notificationpanel/.NotificationBridge");
  for(String app:new String[]{"compass","mail","events","calendar","bank","tasks"})shell("am broadcast -n local.panelbench."+app+"/local.panelbench.FixtureReceiver --es command clear");
  shell("am broadcast -n local.panelbench.compass/local.panelbench.FixtureReceiver --es command compass");
  panel=startActivitySync(new Intent().setClassName("local.notificationpanel","local.notificationpanel.PanelActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1500);
  runOnMainSync(()->{try{Field f=panel.getClass().getDeclaredField("groupList");f.setAccessible(true);list=(ListView)f.get(panel);}catch(Exception e){throw new RuntimeException(e);}});
  check(list.getCount()==1,"Synthetic group available");int closed=height();
  shell("screencap -p /data/local/tmp/motion-closed.png");click();Set<Integer> opening=sample();int opened=height();
  check(opened>closed,"Expanded content visible");check(opening.size()>2,"Opening has intermediate heights: "+opening);
  shell("screencap -p /data/local/tmp/motion-open.png");click();Set<Integer> closing=sample();check(closing.size()>2,"Closing has intermediate heights: "+closing);check(height()==closed,"Collapse restores original height");
  click();SystemClock.sleep(60);click();sample();check(height()==closed,"Rapid reversal completes without a stuck height");
  shell("settings put global animator_duration_scale 0");SystemClock.sleep(400);click();SystemClock.sleep(80);check(height()==opened,"Disabled motion reaches final layout immediately");
  click();SystemClock.sleep(80);check(height()==closed,"Disabled motion collapses immediately");
  result.putString("stream",report.toString());
 }catch(Throwable e){result.putString("stream",report+"FAIL "+e);}finally{try{shell("settings put global animator_duration_scale "+scale);}catch(Exception ignored){}}
 finish(result.getString("stream").contains("FAIL")?0:-1,result);
 }
}
