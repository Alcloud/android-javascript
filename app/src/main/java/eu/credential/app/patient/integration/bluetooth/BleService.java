package eu.credential.app.patient.integration.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service that interacts with the GATT server on the BLE device
 */
public class BleService extends Service {
    // logging indicator
    private final static String TAG = BleService.class.getSimpleName();

    private LocalBroadcastManager localBroadcastManager;

    // bluetooth api stuff
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private Map<String, BluetoothGatt> connectedDevices;
    private Map<String, BluetoothGatt> disconnectedDevices;

    // connection status
    public final static int STATE_CONNECTING = BluetoothProfile.STATE_CONNECTING;
    public final static int STATE_CONNECTED = BluetoothProfile.STATE_CONNECTED;
    public final static int STATE_DISCONNECTING = BluetoothProfile.STATE_DISCONNECTING;
    public final static int STATE_DISCONNECTED = BluetoothProfile.STATE_DISCONNECTED;

    // status data that the service uses for broadcasts to its users
    public final static String ACTION_GATT_CONNECTED = "BleService.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "BleService.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "BleService.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "BleService.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITTEN = "BleService.ACTION_DATA_WRITTEN";
    public final static String ACTION_DESCRIPTOR_WRITTEN = "BleService.ACTION_DESCRIPTOR_WRITTEN";

    // the data field in the intent extra will be named like this
    public final static String EXTRA_CHARACTERISTIC_VALUE = "BleService.EXTRA_CHARACTERISTIC_VALUE";
    public final static String EXTRA_CHARACTERISTIC_UUID = "BleService.EXTRA_CHARACTERISTIC_UUID";
    public final static String CHARACTERISTIC_PROPERTIES = "BleService.CHARACTERISTIC_PROPERTIES";
    public final static String CHARACTERISTIC_PERMISSIONS = "BleService.CHARACTERISTIC_PERMISSIONS";

    public final static String EXTRA_DESCRIPTOR_VALUE = "BleService.EXTRA_DESCRIPTOR_VALUE";
    public final static String EXTRA_DESCRIPTOR_PERMISSIONS = "BleService.EXTRA_DESCRIPTOR_PERMISSIONS";
    public final static String EXTRA_DESCRIPTOR_UUID = "BleService.EXTRA_DESCRIPTOR_UUID";

    public final static String DEVICE_NAME = "BleService.DEVICE_NAME";
    public final static String DEVICE_ADDRESS = "BleService.DEVICE_ADDRESS";

    // UUID characteristic configuration used for descriptors
    public final static UUID UUID_CLIENT_CHARACTERISTIC_CONFIGURATION =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final BleGattCallback gattCallback = new BleGattCallback(this);

    /**
     * Default constructor which tries to initialize bluetooth.
     */
    public BleService() {
        // Creating a threadsafe map for deviceAddress to gatt service
        Map<String, BluetoothGatt> mapA = new HashMap<>();
        this.connectedDevices = Collections.synchronizedMap(mapA);
        Map<String, BluetoothGatt> mapB = new HashMap<>();
        this.disconnectedDevices = Collections.synchronizedMap(mapB);
    }

    @Override
    public void onCreate() {
        // Bluetooth has to be initialized at this late stage, so that the context is available
        initializeBluetooth();
        this.localBroadcastManager = LocalBroadcastManager.getInstance(this);
    }

    /**
     * Tries to get device name for the given address.
     * @param deviceAddress
     * @return empty string if not found
     */
    public String getDeviceName(String deviceAddress) {
        String result = "";
        if(this.connectedDevices.get(deviceAddress) != null) {
            result = this.connectedDevices.get(deviceAddress).getDevice().getName();
        } else if(this.disconnectedDevices.get(deviceAddress) != null) {
            result = this.disconnectedDevices.get(deviceAddress).getDevice().getName();
        }
        return result;
    }

    /**
     * Sets the connection state (see BluetoothProfile).
     *
     * @param newState STATE_CONNECTED, STATE_DISCONNECTED, STATE_DISCONNECTING or STATE_CONNECTING
     */
    public void processConnectionStateChange(int newState, BluetoothGatt gatt) {
        BluetoothDevice device = gatt.getDevice();
        if (newState == STATE_CONNECTED) {
            // register the new device
            if (!isConnected(device.getAddress())) {
                Log.i(TAG, "Connected to GATT server on device " + device.getAddress());
                this.disconnectedDevices.remove(device.getAddress());
                this.connectedDevices.put(device.getAddress(), gatt);
                broadcastDeviceUpdate(ACTION_GATT_CONNECTED, device.getAddress());
            }
        } else if (newState == STATE_DISCONNECTED) {
            // inform about the lost connection and unregister
            Log.i(TAG, "Disconnected from GATT server");
            this.disconnectedDevices.put(device.getAddress(), gatt);
            this.connectedDevices.remove(device.getAddress());
            broadcastDeviceUpdate(ACTION_GATT_DISCONNECTED, device.getAddress());
        }
    }

    /**
     * Determines if the given characteristic is supported by the device.
     *
     * @param serviceId
     * @param characteristicId
     * @return
     */
    public boolean supportsCharacteristic(String deviceAddress, UUID serviceId, UUID characteristicId) {
        BluetoothGatt gatt = connectedDevices.get(deviceAddress);
        return gatt != null && findCharacteristic(serviceId, characteristicId, gatt) != null;

    }

    /**
     * Starts an asynchronous service discovery on the given device
     */
    public void startDeviceServiceDiscovery(String deviceAddress) {
        BluetoothGatt gatt = connectedDevices.get(deviceAddress);
        if (gatt == null) return;

        if (gatt.getServices().isEmpty()) {
            // start service discovery in order to get the service ready for further steps
            Log.i(TAG, "Starting service discovery");
            boolean startResult = gatt.discoverServices();
            if (startResult == false) {
                Log.e(TAG, "Service discovery could not have been started.");
            }
        } else {
            Log.d(TAG, "Services have already been discovered for " + deviceAddress);
            processServiceDiscoveryResult(BluetoothGatt.GATT_SUCCESS, gatt);
        }

    }

    /**
     * Tells the world, that the device's services have been discovered. The device will now be
     * ready to operate with.
     *
     * @param status
     */
    public void processServiceDiscoveryResult(int status, BluetoothGatt gatt) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            broadcastDeviceUpdate(ACTION_GATT_SERVICES_DISCOVERED, gatt.getDevice().getAddress());
            Log.w(TAG, "onServicesDiscovered OK: " + gatt.getDevice().getAddress());
        } else {
            Log.w(TAG, "onServicesDiscovered received: " + status);
        }
    }


    /**
     * Takes data which has been read and broadcasts it, so that the data collecting service
     * can deal with it.
     *
     * @param status
     * @param characteristic
     */
    public void processCharacteristicReadResult(
            int status,
            BluetoothGattCharacteristic characteristic,
            BluetoothGatt gatt) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            String deviceAddress = gatt.getDevice().getAddress();
            broadcastCharacteristicUpdate(ACTION_DATA_AVAILABLE, characteristic, deviceAddress);
            Log.i(TAG, "New characteristic read data available on "
                    + deviceAddress + ": " + characteristic.getUuid());
        } else {
            Log.w(TAG, "Characteristic read callback was not successful.");
        }
    }

    /**
     * Informs the environment, that a new characteristic has been written.
     *
     * @param status
     * @param characteristic
     * @param gatt
     */
    public void processCharacteristicWriteResult(
            int status,
            BluetoothGattCharacteristic characteristic,
            BluetoothGatt gatt) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            String deviceAddress = gatt.getDevice().getAddress();
            broadcastCharacteristicUpdate(ACTION_DATA_WRITTEN, characteristic, deviceAddress);
            Log.i(TAG, "Characteristic has been written " + characteristic);
        } else {
            Log.w(TAG, "Characteristic  write was not successful:\n" +
                    "UUID: " + characteristic.getUuid() + "\nStatuscode: " + status +
                    "\nValue: " + characteristic.getValue());
        }
    }

    /**
     * Informs the environment, that a characteristic on the device has changed.
     *
     * @param characteristic
     */
    public void processCharacteristicChangedResult(
            BluetoothGattCharacteristic characteristic,
            BluetoothGatt gatt) {
        String deviceAddress = gatt.getDevice().getAddress();
        broadcastCharacteristicUpdate(ACTION_DATA_AVAILABLE, characteristic, deviceAddress);
        Log.i(TAG, "New characteristic read data available on " + characteristic);
    }

    /**
     * Broadcasts the information, that a action with a certain device has happened (like new
     * connection).
     *
     * @param action        predefined action identifier
     * @param deviceAddress device address
     */
    private void broadcastDeviceUpdate(final String action,
                                       final String deviceAddress) {
        final Intent intent = new Intent(action);
        intent.putExtra(DEVICE_ADDRESS, deviceAddress);
        this.localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Broadcast method which provides information about a finished action and the related
     * gatt characteristic.
     *
     * @param action         descriptor which should be statically defined
     * @param characteristic gatt characteristic which has the new action
     * @param deviceAddress
     */
    private void broadcastCharacteristicUpdate(final String action,
                                               final BluetoothGattCharacteristic characteristic,
                                               final String deviceAddress) {
        Intent intent = new Intent(action);
        intent = embedCharacteristicInIntent(characteristic, intent);
        intent.putExtra(DEVICE_ADDRESS, deviceAddress);
        this.localBroadcastManager.sendBroadcast(intent);
    }


    /**
     * Broadcast method which provides information about a finished action and the related
     * gatt characteristic.
     *
     * @param action         descriptor which should be statically defined
     * @param descriptor gatt descriptor
     * @param deviceAddress
     */
    private void broadcastDescriptorUpdate(final String action,
                                           final BluetoothGattDescriptor descriptor,
                                           final String deviceAddress) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DESCRIPTOR_UUID, descriptor.getUuid().toString());
        intent.putExtra(EXTRA_DESCRIPTOR_VALUE, descriptor.getValue());
        intent.putExtra(EXTRA_DESCRIPTOR_PERMISSIONS, descriptor.getPermissions());
        intent.putExtra(DEVICE_ADDRESS, deviceAddress);
        this.localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Saves the core properties of the given characteristic into the given intent object.
     * Unfortunately BluetoothGattCharacteristic is only parcelable since API 24 (Android 7.0)...
     *
     * @param characteristic
     * @param intent
     */
    private Intent embedCharacteristicInIntent(
            BluetoothGattCharacteristic characteristic, Intent intent) {

        byte[] characteristicValue = characteristic.getValue();
        String characteristicId = characteristic.getUuid().toString();
        int characteristicProp = characteristic.getProperties();
        int characteristicPerm = characteristic.getPermissions();

        intent.putExtra(EXTRA_CHARACTERISTIC_VALUE, characteristicValue);
        intent.putExtra(EXTRA_CHARACTERISTIC_UUID, characteristicId);
        intent.putExtra(CHARACTERISTIC_PROPERTIES, characteristicProp);
        intent.putExtra(CHARACTERISTIC_PERMISSIONS, characteristicPerm);

        return intent;
    }

    public class LocalBinder extends Binder {
        BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        /* After using a given device BluetoothGatt close has to be called, so that the resources
        are cleaned properly. This will be called, when the UI disconnects from the service. */
        close();
        return super.onUnbind(intent);
    }

    private final IBinder binder = new LocalBinder();

    /**
     * Initializes a reference to the local bluetooth adapter.
     */
    private boolean initializeBluetooth() {
        // Get the bluetooth manager
        if (btManager == null) {

            btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (btManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        // Get the default adapter
        btAdapter = btManager.getAdapter();
        if (btAdapter == null) {
            Log.e(TAG, "Unable to obtain bluetooth adapter");
            return false;
        }

        return true;
    }

    /**
     * Gives Information about the bluetooth initialization status.
     *
     * @return true, if bluetooth adapter found and bluetooth activated on the device
     */
    public boolean isProperlyInitialized() {
        return btManager != null && btAdapter != null;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address
     * @return
     */
    public boolean startConnect(String address) {
        // check for initialization
        if (!isProperlyInitialized() || address == null) {
            Log.w(TAG, "Bluetooth adapter not initialized or unspecified address");
            return false;
        }

        if (this.connectedDevices.containsKey(address)) return false;

        // previously connected device try to reconnect
        BluetoothGatt existing = this.disconnectedDevices.get(address);
        if (existing != null) {
            Log.d(TAG, "Trying to use existing bluetooth gatt for connection");
            return existing.connect();
        }

        // no previous connection, make a new connection
        final BluetoothDevice device = btAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device " + address + " not found. Unable to startConnect.");
            return false;
        }

        // startConnect directly, no auto-startConnect
        BleGattCallback gattCallback = new BleGattCallback(this);
        device.connectGatt(this, true, gattCallback);
        Log.d(TAG, "Waiting for a connection to device " + address + " ...");
        return true;
    }

    /**
     * Disconnects existing connection or cancels a pending connection. The disconnection result
     * is reported asynchronously through the BluetoothGattCallback.onConnectionStateChange
     * callback.
     */
    public void startDisconnect(String deviceAddress) {
        if (isProperlyInitialized() && connectedDevices.containsKey(deviceAddress)) {
            BluetoothGatt gatt = connectedDevices.get(deviceAddress);
            gatt.disconnect();
        }
    }

    /**
     * Releases resources for the BLE device after it has been used.
     */
    public void close() {
        for (String deviceAddress : this.connectedDevices.keySet()) {
            BluetoothGatt gatt = this.connectedDevices.get(deviceAddress);
            gatt.close();
        }
        for (String deviceAddress : this.disconnectedDevices.keySet()) {
            BluetoothGatt gatt = this.disconnectedDevices.get(deviceAddress);
            gatt.close();
        }
    }

    /**
     * Requests a read on a given characteristic. The result will be returned asynchronously through
     * the BluetoothGattCallback.onCharacteristicRead callback.
     *
     * @param characteristicId
     * @param serviceId
     * @param deviceAddress
     */
    public void readCharacteristic(UUID serviceId, UUID characteristicId, String deviceAddress) {
        BluetoothGatt gatt = this.connectedDevices.get(deviceAddress);
        if (gatt != null) {
            BluetoothGattCharacteristic characteristic =
                    findCharacteristic(serviceId, characteristicId, gatt);
            if (characteristic != null) gatt.readCharacteristic(characteristic);
        }
    }

    /**
     * Requests a read on a given characteristic. The result will be returned asynchronously through
     * the BluetoothGattCallback.onCharacteristicRead callback.
     *
     * @param characteristic
     * @param deviceAddress
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic, String deviceAddress) {
        BluetoothGatt gatt = this.connectedDevices.get(deviceAddress);
        if (gatt != null) gatt.readCharacteristic(characteristic);
    }

    /**
     * Writes onto a given characteristic. The result will be returned asynchronously through
     * BluetoothGattCallback.onCharacteristicWrite callback.
     */
    public void writeCharacteristic(UUID serviceId, UUID characteristicId, String deviceAddress) {
        BluetoothGatt gatt = this.connectedDevices.get(deviceAddress);
        if (gatt != null) {
            BluetoothGattCharacteristic characteristic =
                    findCharacteristic(serviceId, characteristicId, gatt);
            if (characteristic != null) gatt.writeCharacteristic(characteristic);
        }
    }

    /**
     * Writes onto a given characteristic. The result will be returned asynchronously through
     * BluetoothGattCallback.onCharacteristicWrite callback.
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, String deviceAddress) {
        BluetoothGatt gatt = this.connectedDevices.get(deviceAddress);
        if (gatt != null) gatt.writeCharacteristic(characteristic);
    }

    /**
     * Contacts the record access control point in order trigger the submission of the service
     * data (like glucose measurements).
     */
    public boolean requestAllRecords(UUID serviceId, UUID characteristicId, String deviceAddress) {
        BluetoothGatt gatt = this.connectedDevices.get(deviceAddress);
        boolean sendingOk = false;
        if (gatt != null) {
            BluetoothGattCharacteristic characteristic = findCharacteristic(serviceId, characteristicId, gatt);
            // set the parameters of the characteristic and write it
            if (characteristic != null) {
                byte[] value = {0x01, 0x01}; // get all records (01) unfiltered (01)
                characteristic.setValue(value);
                sendingOk = gatt.writeCharacteristic(characteristic);
            }
        }
        return sendingOk;
    }

    /**
     * Waits for the service list to come up, because it is not instantly instantiated.
     *
     * @param tries number of tries to wait for in 100 ms
     * @return true if service list came up, else false
     */
    private boolean waitForServiceList(int tries, BluetoothGatt gatt) {
        List<BluetoothGattService> services = null;
        boolean result = false;

        // the gatt service list is not instantly available
        try {
            while (tries > 0 && services == null) {
                services = gatt.getServices();
                if (services != null) {
                    result = true;
                } else {
                    Thread.sleep(10);
                    tries--;
                }
            }
        } catch (InterruptedException ex) {
            Log.w(TAG, "Waiting for service list has been interrupted");
            result = false;
        }

        return result;
    }

    /**
     * Enables the notification for a given characteristic.
     */
    public boolean enableNotification(UUID serviceId, UUID characteristicId, String deviceAddress) {
        return enableIndicationOrNotification(serviceId, characteristicId, deviceAddress, false);
    }

    /**
     * Enables the indication for a given characteristic.
     */
    public boolean enableIndication(UUID serviceId, UUID characteristicId, String deviceAddress) {
        return enableIndicationOrNotification(serviceId, characteristicId, deviceAddress, true);
    }

    private boolean enableIndicationOrNotification(
            UUID serviceId, UUID characteristicId, String deviceAddress, boolean isIndication) {
        byte[] value = isIndication ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE :
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        boolean success = false;

        // enable the notification, finally
        BluetoothGatt gatt = this.connectedDevices.get(deviceAddress);
        if (gatt != null) {
            BluetoothGattCharacteristic characteristic = findCharacteristic(
                    serviceId, characteristicId, gatt);
            if (characteristic != null) {
                Log.d(TAG, "Activating " + (isIndication ? "indications" : "notifications") +
                        " for characteristic " + characteristic.getUuid());

                // enable it locally
                success = gatt.setCharacteristicNotification(characteristic, true);

                // write the new configuration to the ble device
                if (success) {
                    Log.d(TAG, "Writing indication descriptor");
                    BluetoothGattDescriptor descriptor =
                            characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIGURATION);
                    descriptor.setValue(value);
                    success = gatt.writeDescriptor(descriptor);
                }
            }
        }
        return success;
    }

    /**
     * Retrieves the wished characteristic from the bluetooth gatt interface.
     *
     * @param serviceId
     * @param characteristicId
     * @return null if not found
     */
    private BluetoothGattCharacteristic findCharacteristic(
            UUID serviceId, UUID characteristicId, BluetoothGatt gatt) {
        BluetoothGattCharacteristic result = null;
        // pick the right service
        boolean servicesReady = waitForServiceList(15, gatt);
        if (servicesReady) {
            BluetoothGattService service = gatt.getService(serviceId);
            if (service != null) result = service.getCharacteristic(characteristicId);
            else Log.w(TAG, "Service " + serviceId + " not found.");
        } else Log.w(TAG, "No GATT services found");
        if (result == null) Log.w(TAG, "Characteristic " + characteristicId + " not found.");
        return result;
    }

    /**
     * Informs about the connection state.
     *
     * @return
     */
    public boolean isConnected(String deviceAddress) {
        BluetoothGatt gatt = this.connectedDevices.get(deviceAddress);
        return gatt != null;
    }

    /**
     * Will get called, when an indication or notification has successfully been written.
     * @param status
     * @param gatt
     */
    public void processDescriptorWriteResult(
            int status, BluetoothGattDescriptor descriptor, BluetoothGatt gatt) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            String deviceAddress = gatt.getDevice().getAddress();
            broadcastDescriptorUpdate(ACTION_DESCRIPTOR_WRITTEN, descriptor, deviceAddress);
            Log.i(TAG, "Descriptor has been written " + descriptor);
        } else {
            Log.w(TAG, "Descriptor write was not successful:\n" +
                    "UUID: " + descriptor.getUuid() + "\nStatuscode: " + status +
                    "\nValue: " + descriptor.getValue());
        }
    }

}
