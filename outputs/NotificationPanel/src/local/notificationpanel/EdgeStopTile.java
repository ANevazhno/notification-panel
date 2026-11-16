package local.notificationpanel;
import android.service.quicksettings.*;
public final class EdgeStopTile extends TileService {
 public void onStartListening(){if(getQsTile()!=null){getQsTile().setLabel("Отключить боковую панель");getQsTile().setState(EdgePanelService.running()?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);getQsTile().updateTile();}}
 public void onClick(){EdgePanelService.stop(this);if(getQsTile()!=null){getQsTile().setState(Tile.STATE_INACTIVE);getQsTile().updateTile();}}
}
