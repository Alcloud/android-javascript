package eu.credential.app.patient.orchestration.collection;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import eu.credential.app.patient.integration.bluetooth.BleBroadcastReceiver;
import eu.credential.app.patient.integration.bluetooth.BleService;
import eu.credential.app.patient.integration.bluetooth.BleServiceConnection;
import eu.credential.app.patient.integration.model.DeviceInformation;
import eu.credential.app.patient.integration.model.Measurement;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Collects data from wireless health devices in order to hold them for the activity.
 */
public class CollectorService extends Service {

    // actions, that will be broadcasted
    public final static String ACTION_MEASUREMENT_COLLECTED =
            "CollectorService.ACTION_MEASUREMENT_COLLECTED";
    public final static String ACTION_DEVICE_INFO_COLLECTED =
            "CollectorService.ACTION_DEVICE_INFO_COLLECTED";
    public final static String NEW_MESSAGES =
            "CollectorService.NEW_MESSAGES";
    public final static String DEVICE_ADDR =
            "CollectorService.DEVICE_ADDR";
    public final static String COLLECTOR_STOPPED =
            "CollectorService.COLLECTOR_STOPPED";
    public final static String ACTION_DEVICE_CONNECTED =
            "CollectorService.ACTION_DEVICE_CONNECTED";
    public final static String ACTION_DEVICE_DISCONNECTED=
            "CollectorService.ACTION_DEVICE_DISCONNECTED";

    public enum Type {GLUCOSE_COLLECTION, WEIGHT_COLLECTION}

    // name of device address field
    public final static String DEVICE_ADDRESS = "CollectorService.DEVICE_ADDRESS";
    // name of device name field
    public final static String DEVICE_NAME = "CollectorService.DEVICE_NAME";

    // messages container which can be externally accessed by a user interface
    private Queue<String> messageQueue;

    // logging indicator
    private final static String TAG = CollectorService.class.getSimpleName();

    // BroadcastManager
    private LocalBroadcastManager localBroadcastManager;

    // message container where all received health data is stored
    private Map<Integer, Measurement> measurementMap;
    private int counter;
    private Map<String, DeviceInformation> deviceInformationMap;

    // broadcast receiver for incoming device data
    private BleBroadcastReceiver bleBroadcastReceiver;

    // service connection stuff
    private BleServiceConnection bleServiceConnection;
    private BleService bleService;

    // List of collection handlers, which manage device specific collection processes
    private Map<String, CollectionHandler> collectionHandlers;

    // Listener for getting information about changed devices to listen
    private CollectorServicePreferenceListener preferenceListener;
    private SharedPreferences preferences;

    /**
     * Default constructor
     */
    public CollectorService() {

        // initialize the data collection
        this.measurementMap = Collections.synchronizedMap(new TreeMap<Integer, Measurement>());
        this.counter = 0;
        this.bleService = null;
        this.collectionHandlers = Collections.synchronizedMap(new HashMap<String, CollectionHandler>());
        this.deviceInformationMap = Collections.synchronizedMap(new HashMap<String, DeviceInformation>());

        // create the broadcast receiver (needs to get registered in onCreate)
        this.bleBroadcastReceiver = new BleBroadcastReceiver(this);

        // create the ble service connection (this only holds callbacks)
        this.bleServiceConnection = new BleServiceConnection(this);

        // Instantiate the message queue
        this.messageQueue = new LinkedBlockingQueue<>();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.

        Log.i(TAG, "Collector Service successfully started.");
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        // Register the broadcast-receiver
        this.localBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter filter = bleBroadcastReceiver.getIntentFilter();
        localBroadcastManager.registerReceiver(this.bleBroadcastReceiver, filter);

        // Register the BLE service and start it
        Intent bleServiceIntent = new Intent(this, BleService.class);
        bindService(bleServiceIntent, bleServiceConnection, BIND_AUTO_CREATE);

        // Listen for change of settings
        this.preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.preferenceListener = new CollectorServicePreferenceListener(this);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        // preference listener does not get triggered on startup, but we do this later,
        // when the ble service has been bound
    }

    @Override
    public void onDestroy() {
        // Unregister the broadcast receiver
        this.localBroadcastManager.unregisterReceiver(this.bleBroadcastReceiver);

        // Unregister the ble service
        unbindService(bleServiceConnection);

        // Unregister preference listener
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
    }

    /**
     * Gives information about what count the data collection has currently reached.
     * But does not represent what is the actual size of the data collection.
     *
     * @return
     */
    public int getDataCount() {
        return this.counter;
    }

    /**
     * Returns the map of the currently collected measurements with their integer ids.
     *
     * @return
     */
    public Map<Integer, Measurement> getMeasurementMap() {
        return this.measurementMap;
    }

    /**
     * Returns the map of the currently collected deviceInformations with their device addresses.
     *
     * @return
     */
    public Map<String, DeviceInformation> getDeviceInformationMap() {
        return this.deviceInformationMap;
    }

    /**
     * Creates a simple broadcast message without further context.
     */
    private void broadcastMeasurementCollected() {
        Intent intent = new Intent(ACTION_MEASUREMENT_COLLECTED);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Creates a simple broadcast message without further context.
     */
    private void broadcastDeviceInformationCollected() {
        Intent intent = new Intent(ACTION_DEVICE_INFO_COLLECTED);
        localBroadcastManager.sendBroadcast(intent);
    }


    public void broadcastConnectionLost(String deviceAddress, String deviceName) {
        broadcastConnectionChange(deviceAddress, deviceName, false);
    }

    public void broadcastConnectionEstablished(String deviceAddress, String deviceName) {
        broadcastConnectionChange(deviceAddress, deviceName, true);
    }

    private void broadcastConnectionChange(String deviceAddress, String deviceName, boolean hasConnected) {
        String type = hasConnected ? ACTION_DEVICE_CONNECTED : ACTION_DEVICE_DISCONNECTED;
        Intent intent = new Intent(type);

        intent.putExtra(DEVICE_NAME, deviceName);
        intent.putExtra(DEVICE_ADDRESS, deviceAddress);

        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Creates a broadcast message, that informs its receiver about a new device event.
     *
     * @param message
     */
    public void broadcastDeviceMessage(String message) {
        // Queue the message
        this.messageQueue.add(message);
        // Inform environment
        Intent intent = new Intent(NEW_MESSAGES);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Function to store new measurements by devices
     *
     * @param measurement
     */
    public void receiveMeasurement(Measurement measurement, String deviceAdress) {
        Log.d(TAG, "Received measurement from " + deviceAdress + ": \"" + measurement.toString() + "\"");

        // Take first the counter and then increment
        Integer id = this.counter;
        measurementMap.put(id, measurement);
        this.counter++;

        // broadcast the new status update
        broadcastMeasurementCollected();
    }

    /**
     * Function to store new information by devices
     *
     * @param deviceInformation
     */
    public void receiveDeviceInformation(DeviceInformation deviceInformation, String deviceAdress) {
        Log.d(TAG, "Received deviceInformation from " + deviceAdress + ": \"" + deviceInformation.toString() + "\"");

        deviceInformationMap.put(deviceAdress, deviceInformation);

        // broadcast the new status update
        broadcastDeviceInformationCollected();
    }

    /**
     * Registers the given BleService object at the data collector. this service has to be
     * initialized.
     *
     * @param bleService can be null
     */
    public void setBleService(BleService bleService) {
        // accepts nulls
        if (bleService == null) {
            this.bleService = null;
            return;
        }

        // take only initialized services
        if (bleService.isProperlyInitialized()) {
            this.bleService = bleService;
            // do a collector startup
            this.preferenceListener.trigger(this.preferences);
        } else {
            this.bleService = null;
            Log.e(TAG, "BLE service not correctly initialized.");
        }
    }

    /**
     * Returns the current Ble service
     *
     * @return null, if no ble service registered
     */
    public BleService getBleService() {
        return this.bleService;
    }

    public Queue<String> getMessageQueue() {
        return messageQueue;
    }

    /**
     * The binder allows to access the services methods from another class.
     */
    public class LocalBinder extends Binder {
        public CollectorService getService() {
            return CollectorService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service has been bound.");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Unbinding procedure started.");
        return super.onUnbind(intent);
    }

    /**
     * Initializes a new connection handler and starts it (if not already running). Other
     * collection handlers with same address but different type will be stopped.
     *
     * @param deviceAddress
     * @param wantedType
     */
    public void startCollection(String deviceAddress, Type wantedType) {
        // Start the collection handler
        CollectionHandler handler;
        switch (wantedType) {
            case WEIGHT_COLLECTION:
                handler = new WeightHandler(deviceAddress, this.bleService, this);
                break;
            case GLUCOSE_COLLECTION:
                handler = new GlucoseHandler(deviceAddress, this.bleService, this);
                break;
            default:
                handler = null;
                break;
        }

        if (handler != null) {
            Type currentType = getCollectionState(deviceAddress);

            if (currentType != wantedType) {
                if (currentType != null) stopCollection(deviceAddress);
                // start normally
                this.collectionHandlers.put(deviceAddress, handler);
                handler.start();
            }

        }
    }

    @Deprecated
    public void startGlucoseCollection(String deviceAddress) {
        startCollection(deviceAddress, Type.GLUCOSE_COLLECTION);
    }

    @Deprecated
    public void startWeightCollection(String deviceAddress) {
        startCollection(deviceAddress, Type.WEIGHT_COLLECTION);
    }

    /**
     * Returns a Map with all currently collected device addresses and their collection type.
     *
     * @return
     */
    public Map<String, Type> getCollectionSituation() {
        Map<String, Type> result = new HashMap<>();
        for (CollectionHandler handler : this.collectionHandlers.values()) {
            Type state = handler instanceof GlucoseHandler ?
                    Type.GLUCOSE_COLLECTION : Type.WEIGHT_COLLECTION;
            result.put(handler.getDeviceAddress(), state);
        }
        return result;
    }

    /**
     * Stops the collection handler for the given device.
     */
    public void stopCollection(String deviceAddress) {
        Log.d(TAG, "Sending stop signal to collection handler " + deviceAddress);
        CollectionHandler handler = this.collectionHandlers.get(deviceAddress);
        if (handler != null) handler.stop();
        unregisterCollector(deviceAddress);
    }

    /**
     * Gives information whether there is a collector listening on the given device address.
     *
     * @param deviceAddress
     * @return Type of the collection.
     */
    @Nullable
    public Type getCollectionState(String deviceAddress) {
        CollectionHandler handler = this.collectionHandlers.get(deviceAddress);
        if (handler != null) {
            return handler instanceof WeightHandler ?
                    Type.WEIGHT_COLLECTION : Type.GLUCOSE_COLLECTION;
        }
        return null;
    }

    /**
     * Removes the collector handler from the collector service, when it has done its work.
     *
     * @param deviceAddress device address the collector was registered on.
     */
    private void unregisterCollector(String deviceAddress) {
        this.collectionHandlers.remove(deviceAddress);
        broadcastDeviceMessage("Collector handler for " + deviceAddress + " unregistered.");
        Intent intent = new Intent(COLLECTOR_STOPPED);
        intent.putExtra(DEVICE_ADDR, deviceAddress);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * This method passes the device event to the corresponding collection handler.
     *
     * @param deviceAddress
     */
    public void forwardResultToCollector(String deviceAddress, Intent intent) {
        CollectionHandler handler = collectionHandlers.get(deviceAddress);
        if (handler == null) {
            Log.e(TAG, "Collection handler for address " + deviceAddress + " not found.");
        }
        Log.d(TAG, "Received result for " + deviceAddress + ", sending to " + handler.getClass().getSimpleName());
        handler.processResult(intent);
    }

}