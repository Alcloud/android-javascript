package eu.credential.app.patient.ui.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import eu.credential.app.patient.orchestration.collection.CollectorServicePreferenceListener;
import eu.credential.app.patient.orchestration.collection.WithCollectorService;


/**
 * The main activity's broadcast receiver, which listens for news of the settings activity.
 */
public class SettingsBroadcastReceiver extends BroadcastReceiver {

    // actions this broadcast receiver is made for
    public final String[] ACTIONS = {
            CollectorServicePreferenceListener.SETTINGS_EVENT
    };

    // Tag for logging purposes
    private final String TAG = SettingsBroadcastReceiver.class.getSimpleName();

    // the main activity to which the data will be forwarded
    private WithCollectorService withCollectorService;

    public SettingsBroadcastReceiver(WithCollectorService withCollectorService) {
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
        if (action == CollectorServicePreferenceListener.SETTINGS_EVENT) {
            withCollectorService.listMessage(intent.getStringExtra(
                    CollectorServicePreferenceListener.MESSAGE));
        }
    }
}
