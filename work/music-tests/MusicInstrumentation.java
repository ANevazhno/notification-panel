package local.panelbench;
import android.app.*;import android.os.*;import android.media.*;import android.media.session.*;import android.content.*;import android.view.*;import java.lang.reflect.*;import java.util.*;
public class MusicInstrumentation extends Instrumentation {
 Object card;Class<?> type;MediaSession session;int plays,pauses,nexts;Activity activity;
 Object call(String name,Class<?>[] types,Object... args)throws Exception{Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(card,args);}
 void expect(boolean value,String name){if(!value)throw new AssertionError(name);}
 void state(int state){session.setPlaybackState(new PlaybackState.Builder().setState(state,0,1).setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT).build());}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
 activity=startActivitySync(new Intent().setClassName(getTargetContext(),"local.notificationpanel.PanelActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
 runOnMainSync(()->{try{
 type=Class.forName("local.notificationpanel.MusicCard",true,getTargetContext().getClassLoader());Field field=activity.getClass().getDeclaredField("music");field.setAccessible(true);card=field.get(activity);
 session=new MediaSession(getTargetContext(),"music-test");session.setCallback(new MediaSession.Callback(){public void onPlay(){plays++;state(PlaybackState.STATE_PLAYING);}public void onPause(){pauses++;state(PlaybackState.STATE_PAUSED);}public void onSkipToNext(){nexts++;}},new Handler(Looper.getMainLooper()));
 session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Тестовый трек").putString(MediaMetadata.METADATA_KEY_ARTIST,"Проверка управления").build());state(PlaybackState.STATE_PLAYING);session.setActive(true);
 call("bind",new Class[]{List.class},Collections.singletonList(session.getController()));expect(((View)card).getVisibility()==View.VISIBLE,"playing visible");call("command",new Class[]{int.class},1);
 }catch(Exception e){throw new RuntimeException(e);}});
 Thread.sleep(600);
 runOnMainSync(()->{try{expect(pauses==1,"pause delivered");expect(((View)card).getVisibility()==View.VISIBLE,"paused retained");call("command",new Class[]{int.class},1);call("command",new Class[]{int.class},3);call("command",new Class[]{int.class},0);}catch(Exception e){throw new RuntimeException(e);}});
 Thread.sleep(600);
 runOnMainSync(()->{expect(plays==1&&nexts==1,"play and next delivered");});
 Thread.sleep(15000);
 runOnMainSync(()->{try{state(PlaybackState.STATE_STOPPED);call("update",new Class[]{});expect(((View)card).getVisibility()==View.GONE,"stopped hidden");session.release();call("stop",new Class[]{});expect(((View)card).getVisibility()==View.GONE,"cleanup");}catch(Exception e){throw new RuntimeException(e);}});
 result.putString("stream","PASS music: visible, pause retained, play/pause/next transport, unsupported previous, stopped hidden, cleanup");finish(-1,result);
 }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}}
}
