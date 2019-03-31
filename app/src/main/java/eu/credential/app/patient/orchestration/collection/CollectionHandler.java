package eu.credential.app.patient.orchestration.collection;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Intent;
import android.util.Log;

import eu.credential.app.patient.integration.bluetooth.BleService;
import eu.credential.app.patient.integration.model.DeviceInformation;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Created by ogr on 27.06.2016.
 */
public abstract class CollectionHandler {

    private final static String TAG = CollectionHandler.class.getSimpleName();

    /**
     * UUID descriptors for getting device information
     */
    private enum InformationField {
        MANUFACTURER_NAME("2a29"), MODEL_NUMBER("2a24"), SERIAL_NUMBER("2a25"),
        HARDWARE_REVISION("2a27"), FIRMWARE_REVISION("2a26"), SOFTWARE_REVISION("2a28"),
        SYSTEM_ID("2a23"), REGULATORY_CERT_DATA("2a2a");

        InformationField(String id) {
            this.id = id;
            this.received = false;
        }

        private final String id;
        boolean received; // was it received?

        public UUID getUUID() {
            return UUID.fromString("0000"+ this.id +"-0000-1000-8000-00805f9b34fb");
        }

        public static InformationField find(UUID uuid) {
            for(InformationField field : values()) {
                if(field.getUUID().equals(uuid)) return field;
            }
            return null;
        }

        public static boolean allReceived()  {
            for(InformationField field : values()) {
                if(!field.received) return false;
            }
            return true;
        }
    }

    private final UUID UUID_DEVICE_INFORMATION =
            UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");

    // environment, needed to operate
    protected BleService bleService;
    protected CollectorService collectorService;
    protected String deviceAddress;

    // flags identifying the process' current state
    protected boolean done;
    protected boolean started;
    protected boolean notificationEnabled;
    protected boolean dataRequested;
    protected boolean dataReceived;
    protected boolean listening;

    // general information read from the device
    protected DeviceInformation deviceInformation;

    // Condition to signal, if a descriptor has been written
    protected final BlockingQueue<BluetoothGattDescriptor> descriptorQueue;
    private final BlockingQueue<BluetoothGattCharacteristic> informationQueue;

    /**
     * Constructor with environmental parameters.
     *
     * @param deviceAddress device address the process listens on
     * @param bleService    ble service the process should communicate with
     */
    public CollectionHandler(String deviceAddress, BleService bleService, CollectorService collectorService) {
        // init parameters
        this.deviceAddress = deviceAddress;
        this.bleService = bleService;
        this.collectorService = collectorService;
        this.deviceInformation = new DeviceInformation();
        this.informationQueue = new LinkedBlockingQueue<>();
        this.descriptorQueue = new LinkedBlockingQueue<>();

        // init flags
        this.started = false;
        this.done = false;
        this.listening = false;
        this.notificationEnabled = false;
        this.dataRequested = false;
        this.dataReceived = false;

    }

    /**
     * Starts the first step of the collection process, which is done asynchronously.
     */
    public void start() {
        this.started = true;
        requestConnection();
    }

    /**
     * Initiates asynchronously the creation of a new connection.
     */
    protected void requestConnection() {
        bleService.startConnect(deviceAddress);
    }

    public void stop() {
        bleService.startDisconnect(deviceAddress);
    }

    /**
     * Handles the result of an asynchronous action.
     *
     * @param intent
     */
    public final void processResult(Intent intent) {
        String action = intent.getAction();

        switch (action) {
            case BleService.ACTION_GATT_CONNECTED:
                processConnectedResult(intent);
                break;
            case BleService.ACTION_GATT_SERVICES_DISCOVERED:
                processServiceDiscoveryResult(intent);
                break;
            case BleService.ACTION_DATA_WRITTEN:
                publishActionStatus("Information written.");
                break;
            case BleService.ACTION_GATT_DISCONNECTED:
                processDisconnectedResult(intent);
                break;
            case BleService.ACTION_DATA_AVAILABLE:
                processDataResult(intent);
                break;
            case BleService.ACTION_DESCRIPTOR_WRITTEN:
                processDescriptorWriteResult(intent);
                break;
            default:
                break;
        }
    }

    /**
     * Sends a device status message to the CollectorService, which will be shown in the user
     * interface.
     *
     * @param message
     */
    protected void publishActionStatus(String message) {
        String complete = "(" + deviceAddress + ")> " + message;
        collectorService.broadcastDeviceMessage(complete);
    }

    protected void publishConnectionEstablished() {
        String deviceName = this.bleService.getDeviceName(deviceAddress);
        collectorService.broadcastConnectionEstablished(deviceAddress, deviceName);
    }

    protected void publishConnectionLost() {
        String deviceName = this.bleService.getDeviceName(deviceAddress);
        collectorService.broadcastConnectionLost(deviceAddress, deviceName);
    }

    private void processConnectedResult(Intent intent) {
        publishActionStatus("Connection established. Waiting for service readiness.");
        publishConnectionEstablished();

        try {
            Log.d(TAG, "Waiting 1000 before begin...");
            Thread.sleep(1000);
        } catch(InterruptedException ex) {
            Log.e(TAG, "Sleeping interrupted.");
        }
        bleService.startDeviceServiceDiscovery(deviceAddress);
        connectedResultHook(intent);
    }

    private void processDescriptorWriteResult(Intent intent) {
        String uuid = intent.getStringExtra(BleService.EXTRA_DESCRIPTOR_UUID);
        byte[] value = intent.getByteArrayExtra(BleService.EXTRA_DESCRIPTOR_VALUE);
        int permissions = intent.getIntExtra(BleService.EXTRA_DESCRIPTOR_PERMISSIONS, 0);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString(uuid), permissions);
        this.descriptorQueue.offer(descriptor);

        descriptorWriteResultHook(intent);
    }

    private void processDisconnectedResult(Intent intent) {
        publishActionStatus("Connection lost.");
        publishConnectionLost();
        this.listening = false;
        this.done = true;
        disconnectedResultHook(intent);
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    private void processServiceDiscoveryResult(Intent intent) {
        Log.d(TAG, "Device Services successfully discovered.");
        publishActionStatus("Services discovered.");
        requestDeviceInformation();
    }

    private void processDataResult(Intent intent) {
        BluetoothGattCharacteristic characteristic = recreateCharacteristic(intent);
        UUID uuid = characteristic.getUuid();
        if(InformationField.find(uuid) != null) {
            publishActionStatus("Device Information Part received.");
            try {
                informationQueue.put(characteristic);
            } catch(InterruptedException ex) {
                Log.e(TAG, "Filling the information queue was interrupted.");
            }
        } else {
            publishActionStatus("Data received.");
            dataResultHook(intent);
        }
    }

    protected abstract void dataResultHook(Intent intent);

    protected abstract void connectedResultHook(Intent intent);

    protected abstract void disconnectedResultHook(Intent intent);

    protected abstract void serviceDiscoveryResultHook(Intent intent);

    protected abstract void descriptorWriteResultHook(Intent intent);

    /**
     * Takes received device information and puts it into the belonging field
     * @param characteristic
     */
    private void feedDeviceInformation(BluetoothGattCharacteristic characteristic) {
        InformationField field = InformationField.find(characteristic.getUuid());
        field.received = true;
        switch(field) {
            case MANUFACTURER_NAME:
                deviceInformation.setManufacturerName(characteristic.getStringValue(0));
                break;
            case MODEL_NUMBER:
                deviceInformation.setModelNumber(characteristic.getStringValue(0));
                break;
            case SERIAL_NUMBER:
                deviceInformation.setSerialNumber(characteristic.getStringValue(0));
                break;
            case HARDWARE_REVISION:
                deviceInformation.setHardwareRevision(characteristic.getStringValue(0));
                break;
            case FIRMWARE_REVISION:
                deviceInformation.setFirmwareRevision(characteristic.getStringValue(0));
                break;
            case SOFTWARE_REVISION:
                deviceInformation.setSoftwareRevision(characteristic.getStringValue(0));
                break;
            case SYSTEM_ID:
                deviceInformation.setSystemId(characteristic.getValue());
                break;
            case REGULATORY_CERT_DATA:
                deviceInformation.setRegulatoryCertData(characteristic.getValue());
                break;
            default:
                break;
        }
    }

    /**
     * Builds a hex string from a given byte array. Looks at the end like a MAC address.
     *
     * @param bytes
     * @return
     */
    protected String byteArrayToHexString(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder builder = new StringBuilder();
        for (byte element : bytes) {
            builder.append(String.format("%02x", element)).append(":");
        }
        return builder.toString();
    }

    /**
     * Creates a GATT characteristic from given fields, since the object cannot be broadcast.
     *
     * @param intent
     */
    protected BluetoothGattCharacteristic recreateCharacteristic(Intent intent) {
        byte[] data = intent.getByteArrayExtra(BleService.EXTRA_CHARACTERISTIC_VALUE);
        String uuid = intent.getStringExtra(BleService.EXTRA_CHARACTERISTIC_UUID);
        int properties = intent.getIntExtra(BleService.CHARACTERISTIC_PROPERTIES, 0);
        int permissions = intent.getIntExtra(BleService.CHARACTERISTIC_PERMISSIONS, 0);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                UUID.fromString(uuid), properties, permissions);
        characteristic.setValue(data);
        return characteristic;
    }

    /**
     * Requests the fields of the device Information of the device.
     * They will be returned asynchronously.
     */
    protected void requestDeviceInformation() {
        DeviceInformationProcess runner = new DeviceInformationProcess();
        Thread requester = new Thread(runner);
        requester.start();
    }

    /**
     * Requests the device information fields one after the other.
     */
    private class DeviceInformationProcess implements Runnable {
        public void run() {
            UUID serviceId = UUID_DEVICE_INFORMATION;
            for(InformationField field : InformationField.values().clone()) {
                UUID characteristicId = field.getUUID();
                if(!bleService.supportsCharacteristic(deviceAddress, serviceId, characteristicId)) {
                    continue; // Non-supported characteristics will be ignored
                }

                bleService.readCharacteristic(serviceId, characteristicId, deviceAddress);
                // wait for the result
                try {
                    // info queue will be filled on each receive by the main thread.
                    BluetoothGattCharacteristic result = informationQueue.take();
                    if(result != null) feedDeviceInformation(result);
                } catch (InterruptedException iex) {
                    Log.e(TAG, "Device Information Process was interrupted");
                }
            }
            // Inform the collector when everything is done
            collectorService.receiveDeviceInformation(deviceInformation, deviceAddress);

            serviceDiscoveryResultHook(null);
        }
    }

}
