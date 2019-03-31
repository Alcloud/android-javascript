package eu.credential.app.patient.orchestration.collection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * The main activity's broadcast receiver, which listens for news by the collector
 * service.
 */
public class CollectorBroadcastReceiver extends BroadcastReceiver {

    // actions this broadcast receiver is made for
    public final String[] ACTIONS = {
            CollectorService.ACTION_MEASUREMENT_COLLECTED,
            CollectorService.ACTION_DEVICE_INFO_COLLECTED,
            CollectorService.COLLECTOR_STOPPED,
            CollectorService.NEW_MESSAGES,
            CollectorService.ACTION_DEVICE_CONNECTED,
            CollectorService.ACTION_DEVICE_DISCONNECTED
    };

    // Tag for logging purposes
    private final String TAG = CollectorBroadcastReceiver.class.getSimpleName();

    // the main activity to which the data will be forwarded
    private WithCollectorService withCollectorService;

    public CollectorBroadcastReceiver(WithCollectorService withCollectorService) {
        this.withCollectorService = withCollectorService;
    }

    /**
     * Create an intent filter this receiver works with.
     *
     * @return
     */
    public IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        for (String action : ACTIONS) {
            filter.addAction(action);
        }
        return filter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        String[] addrAndName;
        switch (action) {
            case CollectorService.ACTION_MEASUREMENT_COLLECTED:
                withCollectorService.refreshMeasurements();
                break;
            case CollectorService.ACTION_DEVICE_INFO_COLLECTED:
                withCollectorService.refreshMessages();
                break;
            case CollectorService.COLLECTOR_STOPPED:
                withCollectorService.refreshMessages();
                break;
            case CollectorService.NEW_MESSAGES:
                withCollectorService.refreshMessages();
                break;
            case CollectorService.ACTION_DEVICE_CONNECTED:
                addrAndName = readDeviceAddressAndName(intent);
                withCollectorService.displayConnectionStateChange(addrAndName[0], addrAndName[1], true);
                break;
            case CollectorService.ACTION_DEVICE_DISCONNECTED:
                addrAndName = readDeviceAddressAndName(intent);
                withCollectorService.displayConnectionStateChange(addrAndName[0], addrAndName[1], false);
                break;
            default:
                break;
        }

    }

    private String[] readDeviceAddressAndName(Intent intent) {
        String deviceAddress = intent.getStringExtra(CollectorService.DEVICE_ADDRESS);
        String deviceName = intent.getStringExtra(CollectorService.DEVICE_NAME);
        String[] result = {deviceAddress, deviceName};
        return result;
    }
}
