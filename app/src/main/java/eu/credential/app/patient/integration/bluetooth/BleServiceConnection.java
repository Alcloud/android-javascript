package eu.credential.app.patient.integration.bluetooth;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import eu.credential.app.patient.orchestration.collection.CollectorService;


/**
 * Specialized service connection which is drawn between the parental CollectorService service
 * and the underlying ble service. Actually an outsourced callback in order to keep
 * the CollectorService class more clean.
 */
public class BleServiceConnection implements ServiceConnection {

    // the parent service, where the ble service will be registered at
    private CollectorService collectorService;
    private final String TAG = BleServiceConnection.class.getSimpleName();

    public BleServiceConnection(CollectorService collectorService) {
        this.collectorService = collectorService;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        // Assign the new BLE Service
        BleService bleService = ((BleService.LocalBinder) service).getService();
        collectorService.setBleService(bleService);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        collectorService.setBleService(null);
    }

}
