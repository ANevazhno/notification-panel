package local.notificationpanel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Hides a session until playback starts again; no commands are sent to the player. */
final class MediaDismissal {
    private final Map<Object,Boolean> hidden=new HashMap<>();

    void hide(Object session,boolean playing){hidden.put(session,playing);}

    boolean isHidden(Object session,boolean playing){
        Boolean previous=hidden.get(session);
        if(previous==null)return false;
        if(!previous&&playing){hidden.remove(session);return false;}
        hidden.put(session,playing);
        return true;
    }

    void retain(Collection<?> active){hidden.keySet().retainAll(active);}
}
