package eu.credential.app.patient.orchestration.collection;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;


/**
 * Specialized service connection, which is drawn between the user interface and the service wo
 * collects data from several devices
 */
public class CollectorServiceConnection implements ServiceConnection {

    // the parental service consumer
    private WithCollectorService withCollectorService;
    private final String TAG = CollectorServiceConnection.class.getSimpleName();

    public CollectorServiceConnection(WithCollectorService withCollectorService) {
        this.withCollectorService = withCollectorService;
    }
    /*public CollectorServiceConnection(DiaryFragment diaryFragment) {
        this.diaryFragment = diaryFragment;
    }*/

    /**
     * Passes the new data collector service instance to the parent consumer.
     * @param componentName
     * @param service
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        CollectorService collectorService = ((CollectorService.LocalBinder) service).getService();
        withCollectorService.setCollectorService(collectorService);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        withCollectorService.setCollectorService(null);
    }
}
