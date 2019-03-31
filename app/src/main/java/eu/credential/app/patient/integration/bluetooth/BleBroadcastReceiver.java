package eu.credential.app.patient.integration.bluetooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import eu.credential.app.patient.orchestration.collection.CollectorService;


/**
 * Broadcast Receiver which is used by the data collectorService service in order to receive data
 * from the underlying device services.
 */
public class BleBroadcastReceiver extends BroadcastReceiver {

    // actions this broadcast receiver is made for
    public final String[] ACTIONS = {
            BleService.ACTION_GATT_CONNECTED,
            BleService.ACTION_GATT_DISCONNECTED,
            BleService.ACTION_DATA_AVAILABLE,
            BleService.ACTION_DATA_WRITTEN,
            BleService.ACTION_GATT_SERVICES_DISCOVERED,
            BleService.ACTION_DESCRIPTOR_WRITTEN
    };

    // Tag for logging purposes
    private final String TAG = BleBroadcastReceiver.class.getSimpleName();

    // parent service
    private CollectorService collectorService;

    public BleBroadcastReceiver(CollectorService collectorService)  {
        this.collectorService = collectorService;
    }

    /**
     * Create an intent filter this receiver works with.
     * @return
     */
    public IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        for(String action : ACTIONS) {
            filter.addAction(action);
        }
        return filter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // forward the device action to the collector service
        String deviceAddress = intent.getStringExtra(BleService.DEVICE_ADDRESS);
        if(deviceAddress != null) {
            collectorService.forwardResultToCollector(deviceAddress, intent);
        }
    }
}
