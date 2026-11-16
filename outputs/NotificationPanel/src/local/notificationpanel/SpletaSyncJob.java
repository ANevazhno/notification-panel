package local.notificationpanel;
import android.app.job.*;
public final class SpletaSyncJob extends JobService {
    private boolean active;
    public boolean onStartJob(JobParameters p){active=true;SpletaSource.sync(true,ok->{if(active)jobFinished(p,false);active=false;});return true;}
    public boolean onStopJob(JobParameters p){active=false;return false;}
}
