package local.panelbench;
import android.app.*;import android.os.*;import android.service.notification.StatusBarNotification;import java.util.*;import java.lang.reflect.*;
public final class LocalNotificationInstrumentation extends Instrumentation {
 Bundle args;android.content.Context c;Class<?> type;long now=System.currentTimeMillis();
 Object call(String name,Class<?>[] args,Object... values)throws Exception{Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m.invoke(null,values);}
 boolean check(String name,StatusBarNotification s)throws Exception{return (Boolean)call(name,new Class[]{StatusBarNotification.class},s);}
 void expect(boolean v,String name){if(!v)throw new AssertionError(name);}
 Notification.Builder base(){return new Notification.Builder(c,"fixture").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Fixture").setContentText("Durable text").setWhen(now);}
 StatusBarNotification wrap(String tag,Notification n){return new StatusBarNotification("local.panelbench.transferfixtures","local.panelbench.transferfixtures",tag.hashCode(),tag,10123,0,0,n,android.os.Process.myUserHandle(),now);}
 List<StatusBarNotification> snapshot()throws Exception{return (List<StatusBarNotification>)call("snapshot",new Class[]{});}
 void reload()throws Exception{Field r=type.getDeclaredField("records");r.setAccessible(true);((Map)r.get(null)).clear();Field l=type.getDeclaredField("loaded");l.setAccessible(true);l.set(null,false);call("init",new Class[]{android.content.Context.class},c);}
 public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 public void onStart(){Bundle out=new Bundle();try{c=getTargetContext();type=c.getClassLoader().loadClass("local.notificationpanel.LocalNotifications");
 if(args.containsKey("mode")){String mode=args.getString("mode");runOnMainSync(()->{c.getSharedPreferences("panel_settings",0).edit().putBoolean("local_notifications",mode.equals("on")).commit();c.startActivity(new android.content.Intent().setClassName(c,"local.notificationpanel.SettingsActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));});out.putString("result","mode="+mode);finish(Activity.RESULT_OK,out);return;}
 if(args.containsKey("inspect")){runOnMainSync(()->{try{out.putInt("local_count",snapshot().size());for(StatusBarNotification s:snapshot())if(s.getPackageName().equals("local.panelbench.transferfixtures"))out.putString("fixture_"+s.getId(),s.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString());}catch(Exception e){out.putString("error",e.toString());}});finish(Activity.RESULT_OK,out);return;}
 runOnMainSync(()->{try{runChecks();}catch(Exception e){throw new RuntimeException(e);}});
 out.putString("result","PASS: protected categories/media/ongoing/profile, durable reload, actions stripped, snooze, dismiss, write failure, summaries, attachment retained");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}}
 void runChecks()throws Exception{
 for(String cat:new String[]{Notification.CATEGORY_CALL,Notification.CATEGORY_ALARM,Notification.CATEGORY_NAVIGATION,Notification.CATEGORY_TRANSPORT})expect(check("protectedNotification",wrap(cat,base().setCategory(cat).build())),cat);
 Notification media=base().build();media.extras.putString(Notification.EXTRA_MEDIA_SESSION,"fixture");expect(check("protectedNotification",wrap("media",media)),"media session");
 expect(check("protectedNotification",wrap("ongoing",base().setOngoing(true).build())),"ongoing");
 StatusBarNotification work=new StatusBarNotification("fixture","fixture",1,"work",1010123,0,0,base().build(),UserHandle.getUserHandleForUid(1010123),now);expect(check("protectedNotification",work),"work profile");
 StatusBarNotification ordinary=wrap("durable",base().addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,android.R.drawable.ic_dialog_info),"Read",null).build()).build());
 expect(!check("protectedNotification",ordinary),"ordinary candidate");expect(check("capture",ordinary),"durable write");reload();StatusBarNotification stored=null;for(StatusBarNotification s:snapshot())if(s.getKey().equals(ordinary.getKey()))stored=s;expect(stored!=null,"reload");
 expect("Durable text".contentEquals(stored.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT)),"body");expect(stored.getNotification().actions==null&&stored.getNotification().contentIntent==null,"no persisted tokens");
 expect((Boolean)call("hide",new Class[]{List.class,long.class},Collections.singletonList(stored),50L),"snooze");expect(!snapshot().contains(stored),"snoozed hidden");Thread.sleep(70);expect(snapshot().contains(stored),"snooze returns");
 expect((Boolean)call("hide",new Class[]{List.class,long.class},Collections.singletonList(stored),0L),"dismiss");reload();expect(!(Boolean)call("contains",new Class[]{String.class},ordinary.getKey()),"dismiss persisted");
 StatusBarNotification summary=wrap("summary",base().setGroup("g").setGroupSummary(true).build());summary.setOverrideGroupKey("override");expect(check("capture",summary),"summary key");reload();expect((Boolean)call("contains",new Class[]{String.class},summary.getKey()),"summary reload");call("remove",new Class[]{String.class},summary.getKey());
 Notification picture=base().build();picture.extras.putString(Notification.EXTRA_PICTURE,"fixture");expect(!check("capture",wrap("picture",picture)),"unsupported kept system");
 Field f=type.getDeclaredField("file");f.setAccessible(true);Object original=f.get(null);java.io.File blocker=new java.io.File(c.getFilesDir(),"write-failure");new java.io.FileOutputStream(blocker).close();f.set(null,new android.util.AtomicFile(new java.io.File(blocker,"impossible")));expect(!check("capture",ordinary),"write failure prevents cancellation");f.set(null,original);blocker.delete();
 }
}
