package local.panelbench;
import android.app.*;import android.os.*;import android.service.notification.StatusBarNotification;import java.util.*;import java.lang.reflect.*;
public final class SmsDedupInstrumentation extends Instrumentation {
 final long time=1700000000000L;Method filter;android.content.Context c;
 Notification.Builder base(String sender,String body){return new Notification.Builder(c,"test").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(sender).setContentText(body).setWhen(time);}
 StatusBarNotification wrap(String tag,Notification n){return new StatusBarNotification("test.messages","test.messages",tag.hashCode(),tag,android.os.Process.myUid(),0,0,n,android.os.Process.myUserHandle(),time);}
 void expect(int count,List<StatusBarNotification> active,List<StatusBarNotification> sms,String name)throws Exception{List<?> result=(List<?>)filter.invoke(null,active,sms,"test.messages");if(result.size()!=count)throw new AssertionError(name+": "+result.size());}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{c=getTargetContext();Class<?> type=c.getClassLoader().loadClass("local.notificationpanel.SmsDuplicates");filter=type.getDeclaredMethod("filter",List.class,List.class,String.class);filter.setAccessible(true);
 StatusBarNotification sms=wrap("sms",base("+12345678","fixture").build());List<StatusBarNotification> inbox=Collections.singletonList(sms);
 StatusBarNotification same=wrap("same",base("+1 (234) 5678","fixture").setGroup("g").build());expect(0,Arrays.asList(same),inbox,"matching SMS");expect(1,Arrays.asList(same),Collections.emptyList(),"disabled or empty inbox");
 StatusBarNotification rcs=wrap("rcs",base("+12345678","RCS-only").setGroup("g").build());expect(1,Arrays.asList(same,rcs),inbox,"RCS preserved");
 expect(1,Arrays.asList(wrap("old",base("+12345678","fixture").setWhen(time+60000).build())),inbox,"same text different time");expect(1,Arrays.asList(wrap("other",base("+87654321","fixture").build())),inbox,"different sender");
 StatusBarNotification summary=wrap("summary",base("Messages","2 messages").setGroup("g").setGroupSummary(true).build());expect(0,Arrays.asList(same,summary),inbox,"SMS-only summary");expect(2,Arrays.asList(same,rcs,summary),inbox,"mixed summary preserves RCS");expect(1,Arrays.asList(summary),inbox,"orphan summary");
 Person sender=new Person.Builder().setName("Contact").setUri("tel:+12345678").build();Person me=new Person.Builder().setName("Me").build();
 Notification.MessagingStyle style=new Notification.MessagingStyle(me).addMessage(new Notification.MessagingStyle.Message("fixture",time,sender));expect(0,Arrays.asList(wrap("style",base("Contact","fixture").setStyle(style).build())),inbox,"MessagingStyle exact");
 style.addMessage(new Notification.MessagingStyle.Message("RCS-only",time+1,sender));expect(1,Arrays.asList(wrap("mixed",base("Contact","fixture").setStyle(style).build())),inbox,"mixed conversation");
 expect(1,Arrays.asList(wrap("redacted",base("", "Sensitive notification content hidden").build())),inbox,"redacted retained");
 Notification media=base("+12345678","fixture").setStyle(new Notification.MessagingStyle(me).addMessage(new Notification.MessagingStyle.Message("fixture",time,sender).setData("image/png",android.net.Uri.parse("content://test/image")))).build();expect(1,Arrays.asList(wrap("attachment",media)),inbox,"attachment retained");
 StatusBarNotification profile=new StatusBarNotification("test.messages","test.messages",1,"work",1010000,0,0,same.getNotification(),UserHandle.getUserHandleForUid(1010000),time);expect(1,Arrays.asList(profile),inbox,"other profile retained");
 Class<?> opener=c.getClassLoader().loadClass("local.notificationpanel.SmsOpener"),launcher=c.getClassLoader().loadClass("local.notificationpanel.SmsOpener$Launcher");
 Method conversation=opener.getDeclaredMethod("conversation",String.class,String.class);conversation.setAccessible(true);
 android.content.Intent open=(android.content.Intent)conversation.invoke(null,"T-Bank","test.messages");
 if(!android.content.Intent.ACTION_SENDTO.equals(open.getAction())||!"smsto".equals(open.getData().getScheme())||!"T-Bank".equals(open.getData().getSchemeSpecificPart())||open.getExtras()!=null)throw new AssertionError("unsafe SMS intent");
 Method launch=opener.getDeclaredMethod("launch",android.content.Intent.class,android.content.Intent.class,launcher);launch.setAccessible(true);
 final int[] attempts={0};Object fake=Proxy.newProxyInstance(launcher.getClassLoader(),new Class[]{launcher},(o,m,args)->{attempts[0]++;if(attempts[0]==1)throw new SecurityException();return null;});
 if(!(Boolean)launch.invoke(null,open,new android.content.Intent(android.content.Intent.ACTION_MAIN),fake)||attempts[0]!=2)throw new AssertionError("fallback failed");
 Object denied=Proxy.newProxyInstance(launcher.getClassLoader(),new Class[]{launcher},(o,m,args)->{throw new android.content.ActivityNotFoundException();});
 if((Boolean)launch.invoke(null,open,new android.content.Intent(),denied))throw new AssertionError("failure reported as success");
 out.putString("result","PASS: SMS opener/no body/fallback/error, exact SMS, no inbox, RCS, sender/time mismatch, summaries, MessagingStyle, mixed conversation, redacted, attachments, work profile");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
