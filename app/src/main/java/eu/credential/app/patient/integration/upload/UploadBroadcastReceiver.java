package eu.credential.app.patient.integration.upload;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import eu.credential.app.patient.orchestration.collection.WithCollectorService;

/**
 * The main activity's broadcast receiver, which listens for news upload service.
 */
public class UploadBroadcastReceiver extends BroadcastReceiver {

    // actions this broadcast receiver is made for
    public final String[] ACTIONS = {
            UploadService.UPLOAD_EVENT
    };

    // Tag for logging purposes
    private final String TAG = UploadBroadcastReceiver.class.getSimpleName();

    // the main activity to which the data will be forwarded
    private WithCollectorService withCollectorService;

    public UploadBroadcastReceiver(WithCollectorService withCollectorService)  {
        this.withCollectorService = withCollectorService;
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
        final String action = intent.getAction();
        if(action == UploadService.UPLOAD_EVENT) {
            withCollectorService.listMessage(intent.getStringExtra(UploadService.MESSAGE));
        }
    }
}
