package local.notificationpanel;
import java.util.Arrays;
import java.util.Collections;

public class MediaDismissalTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        MediaDismissal sessions=new MediaDismissal();
        Object first=new Object(),second=new Object();
        check(!sessions.isHidden(first,true),"new playback visible");
        sessions.hide(first,true);
        check(sessions.isHidden(first,true),"closing active playback hides it");
        check(!sessions.isHidden(second,true),"other player remains visible");
        check(sessions.isHidden(first,false),"pause does not restore dismissed card");
        check(!sessions.isHidden(first,true),"next play restores card");
        sessions.hide(first,false);
        check(sessions.isHidden(first,false),"closing paused playback hides it");
        check(!sessions.isHidden(first,true),"resuming a paused player restores card");
        sessions.hide(first,true);
        sessions.retain(Arrays.asList(first,second));
        check(sessions.isHidden(first,true),"rebind retains current session dismissal");
        sessions.retain(Collections.singletonList(second));
        check(!sessions.isHidden(first,true),"ended session no longer dismissed");
        System.out.println("PASS MediaDismissal: close, pause, restart, multiple sessions, rebind");
    }
}
