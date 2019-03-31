package eu.credential.app.patient.orchestration.collection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This BroadcastReceiver is used to start the CollectorService independently from the main
 * activity on bootup.
 */
public class CollectorServiceStarter extends BroadcastReceiver {

    private static final String TAG = CollectorService.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Starting GATT Collector service...");
        Intent startServiceIntent = new Intent(context, CollectorService.class);
        context.startService(startServiceIntent);
    }
}
