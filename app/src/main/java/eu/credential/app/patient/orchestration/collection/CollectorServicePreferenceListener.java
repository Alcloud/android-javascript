package eu.credential.app.patient.orchestration.collection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * This Listener starts and stops collector handlers depending on the preferences made by the user.
 */
public class CollectorServicePreferenceListener
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = CollectorServicePreferenceListener.class.getSimpleName();

    // variables used for getting preferences concerning device names
    private static final String GLUCOSE_DEVICE_NAMES = "pref_glucose_device_names";
    private static final String WEIGHT_DEVICE_NAMES = "pref_weight_device_names";

    // used for propagating status information
    public static final String SETTINGS_EVENT = "PreferenceListener.SETTINGS_EVENT";
    public static final String MESSAGE = "PreferenceListener.MESSAGE";
    private LocalBroadcastManager localBroadcastManager;

    private BluetoothAdapter btAdapter;

    // binding of the controlled collector service
    private CollectorService collectorService;

    public CollectorServicePreferenceListener(CollectorService collectorService) {
        this.collectorService = collectorService;
        this.localBroadcastManager = LocalBroadcastManager.getInstance(collectorService);
        btInit();
    }

    /**
     * Broadcasts messages to the local world.
     *
     * @param message
     */
    private void broadcastMessage(String message) {
        Intent intent = new Intent(SETTINGS_EVENT);
        intent.putExtra(MESSAGE, message);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Entry Point for the bluetooth initialization process.
     */
    private void btInit() {
        // find the bluetooth adapter
        BluetoothManager btManager = (BluetoothManager) collectorService.getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();

        if (btAdapter == null) {
            // No Bluetooth interface on the mobile device
            broadcastMessage("Error: Missing Bluetooth adapter");
            return;
        }
    }

    /**
     * Starts the preference changed procedure for all device types.
     */
    public void trigger(SharedPreferences sharedPref) {
        onSharedPreferenceChanged(sharedPref, GLUCOSE_DEVICE_NAMES);
        onSharedPreferenceChanged(sharedPref, WEIGHT_DEVICE_NAMES);
    }

    /**
     * Gets called, whenever a bluetooth name is entered in the settings activity.
     * @param sharedPref
     * @param key
     */
    public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key) {

        if (!GLUCOSE_DEVICE_NAMES.equals(key) && !WEIGHT_DEVICE_NAMES.equals(key)) {
            return;
        }

        // Collect the entered device names, which are line-separated
        String input = sharedPref.getString(key, "");
        Set<String> deviceNames = parseDeviceNames(input);

        // try to get the corresponding bluetooth devices from the pairing list
        Set<BluetoothDevice> wantedDevices = findDevicesByName(deviceNames);
        Set<String> wantedAddresses = collectDeviceAddresses(wantedDevices);

        // start the wanted collector handlers and stops non-wanted
        CollectorService.Type wantedType = translateKeyToCollectionType(key);
        if (wantedType == null) {
            Log.e(TAG, "No corresponding collector type for " + key + " found.");
            return;
        }
        updateDeviceCollection(wantedAddresses, wantedType);
        Log.d(TAG, "Collector for " + key + " successfully updated.");
    }

    /**
     * Starts wanted collectors and the stops non-wanted ones in the collector service.
     *
     * @param wantedType
     * @param wantedAddresses
     */
    private void updateDeviceCollection(Set<String> wantedAddresses,
                                        CollectorService.Type wantedType) {
        Map<String, CollectorService.Type> situation = collectorService.getCollectionSituation();

        // Stop unwanted services of the given type
        for(String runningAddress :  situation.keySet()) {
            if(!wantedAddresses.contains(runningAddress) && (wantedType == situation.get(runningAddress))) {
                collectorService.stopCollection(runningAddress);
            }
        }

        // Call a start command for all wanted devices
        for(String wantedAddress : wantedAddresses) {
            collectorService.startCollection(wantedAddress, wantedType);
        }
    }

    /**
     * Checks the pairing status of the diabetes device and starts a le scan for it.
     */
    private Set<BluetoothDevice> findDevicesByName(Set<String> deviceNames) {
        Set<BluetoothDevice> result = new LinkedHashSet<>();

        for (String deviceName : deviceNames) {
            BluetoothDevice device = findDeviceByName(deviceName);

            if (device != null) {
                broadcastMessage("Device \"" + deviceName + "\" found.");
                broadcastMessage("Device address " + device.getAddress() + " for collection.");
                result.add(device);
            } else {
                broadcastMessage("Error: Device \"" + deviceName + "\" not paired.");
            }
        }

        return result;
    }

    /**
     * Returns for the given settings id the corresponding collection type.
     *
     * @param key
     * @return null, if no corresponding.
     */
    @Nullable
    private CollectorService.Type translateKeyToCollectionType(String key) {
        if (WEIGHT_DEVICE_NAMES.equals(key)) {
            return CollectorService.Type.WEIGHT_COLLECTION;
        } else if (GLUCOSE_DEVICE_NAMES.equals(key)) {
            return CollectorService.Type.GLUCOSE_COLLECTION;
        } else {
            return null;
        }
    }

    /**
     * Collects all hardware addresses from the given set of bluetooth device handles.
     *
     * @param devices
     * @return
     */
    private Set<String> collectDeviceAddresses(Set<BluetoothDevice> devices) {
        HashSet<String> result = new HashSet<>();
        for (BluetoothDevice device : devices) {
            result.add(device.getAddress());
        }
        return result;
    }

    /**
     * Searches the list of paired devices for the diabetes device.
     */
    private BluetoothDevice findDeviceByName(String deviceName) {
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();

        // check if the device is in the list of paired devices
        Log.d(TAG, "Currently " + pairedDevices.size() + " paired devices");
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().startsWith(deviceName)) {
                return device;
            }
        }
        return null;
    }

    /**
     * Splits a list of space separated device names and puts them in a set.
     * @param input
     * @return
     */
    private Set<String> parseDeviceNames(String input) {
        Set<String> result = new LinkedHashSet<>();
        String[] values = input.trim().split("\\n");
        for (String deviceName : values) {
            String trimmed = deviceName.trim();
            if (trimmed.length() > 0) result.add(trimmed);
        }
        return result;
    }

}
