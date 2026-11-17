package local.notificationpanel;
import android.app.job.*;
public final class SpletaSyncJob extends JobService {
    private final java.util.Set<JobParameters> active=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    public boolean onStartJob(JobParameters p){if(!SpletaSource.enabled())return false;active.add(p);SpletaSource.sync(true,ok->{if(active.remove(p))jobFinished(p,false);});return true;}
    public boolean onStopJob(JobParameters p){active.remove(p);return SpletaSource.enabled();}
}
