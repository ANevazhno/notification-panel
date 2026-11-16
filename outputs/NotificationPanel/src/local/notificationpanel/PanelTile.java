package local.notificationpanel;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class PanelTile extends TileService {
    @Override public void onStartListening() {
        if(getQsTile()!=null) {
            getQsTile().setState(Tile.STATE_INACTIVE);
            getQsTile().setLabel("Шторка");
            getQsTile().updateTile();
        }
    }
    @Override public void onClick() {
        Runnable open=()-> {
            Intent i=new Intent(this,PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if(Build.VERSION.SDK_INT>=34) startActivityAndCollapse(PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            else startActivityAndCollapse(i);
        };
        if(isLocked()) unlockAndRun(open); else open.run();
    }
}
