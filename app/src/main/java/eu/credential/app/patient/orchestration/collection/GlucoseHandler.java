package eu.credential.app.patient.orchestration.collection;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Intent;
import android.util.Log;

import eu.credential.app.patient.integration.bluetooth.BleService;
import eu.credential.app.patient.integration.model.GlucoseMeasurement;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * This class ressembles a handler, for getting device data.
 * Created by ogr on 21.06.2016.
 */
public class GlucoseHandler extends CollectionHandler {
    private final static String TAG = GlucoseHandler.class.getSimpleName();

    // gatt Attributes which will be contacted
    private final static UUID UUID_GLUCOSE_MEASUREMENT =
            UUID.fromString("00002a18-0000-1000-8000-00805f9b34fb");
    private final static UUID UUID_GLUCOSE_MEASUREMENT_CONTEXT =
            UUID.fromString("00002a34-0000-1000-8000-00805f9b34fb");

    private final static UUID UUID_RECORD_ACCESS_CONTROL_POINT =
            UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb");
    // gatt ServiceID
    private final static UUID UUID_GLUCOSE_SERVICE =
            UUID.fromString("00001808-0000-1000-8000-00805f9b34fb");

    // states, if notifications have been enabled
    private boolean glucMeasEnabled;
    private boolean glucMeasCtxEnabled;
    // states, if indidcation have been enabled
    private boolean racpEnabled;

    public GlucoseHandler(String deviceAddress, BleService bleService, CollectorService collectorService) {
        super(deviceAddress, bleService, collectorService);
        this.glucMeasCtxEnabled = false;
        this.glucMeasEnabled = false;
        this.racpEnabled = false;
    }

    protected void connectedResultHook(Intent intent) {
        this.glucMeasCtxEnabled = false;
        this.glucMeasEnabled = false;
        this.racpEnabled = false;
    }

    /**
     * Losing the connection to a device is the endpoint for the process, since there is no
     * possibility get any data.
     *
     * @param intent
     */
    protected void disconnectedResultHook(Intent intent) {
        //
    }

    /**
     * Enables the notification for glucose data and requests a record receive.
     *
     * @param intent
     */
    protected void serviceDiscoveryResultHook(Intent intent) {
        Runnable runnable = () -> {

            glucMeasCtxEnabled = enableGlucoseMeasurementNotification();
            glucMeasEnabled = enableGlucoseNotification();
            racpEnabled = enableRACPIndication();

            if (glucMeasCtxEnabled && (glucMeasEnabled && racpEnabled)) {
                requestRecordReceive();
            }

        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    /**
     * Contacts the Record Access Control Point of the device in order to get notifications about
     * glucose data.
     */
    private void requestRecordReceive() {
        dataRequested = bleService.requestAllRecords(
                UUID_GLUCOSE_SERVICE, UUID_RECORD_ACCESS_CONTROL_POINT, deviceAddress);
        if (!dataRequested) {
            Log.e(TAG, "Could not request records (request not sent).");
        }
    }

    /**
     * Interprets data received from the health device and makes it human-readable.
     *
     * @param intent
     */
    protected void dataResultHook(Intent intent) {
        this.dataReceived = true;
        BluetoothGattCharacteristic characteristic = recreateCharacteristic(intent);
        GlucoseMeasurement measurement = null;
        if (UUID_GLUCOSE_MEASUREMENT.equals(characteristic.getUuid())) {
            measurement = new GlucoseMeasurement(characteristic);
            collectorService.receiveMeasurement(measurement, deviceAddress);
        }
    }

    @Override
    protected void descriptorWriteResultHook(Intent intent) {

    }

    private boolean enableGlucoseNotification() {
        boolean result = false;
        boolean sendingOk = bleService.enableNotification(
                UUID_GLUCOSE_SERVICE, UUID_GLUCOSE_MEASUREMENT, deviceAddress);

        if (sendingOk) {
            try {
                BluetoothGattDescriptor temp = descriptorQueue.poll(10, TimeUnit.SECONDS);
                result = temp != null;
            } catch (InterruptedException ex) {
                Log.e(TAG, "Could not enable notification for glucose (interrupted).");
            }
        } else {
            Log.e(TAG, "Could not enable notification for glucose (returned false).");
        }
        return result;
    }

    private boolean enableGlucoseMeasurementNotification() {
        boolean result = false;
        boolean sendingOk = bleService.enableNotification(
                UUID_GLUCOSE_SERVICE, UUID_GLUCOSE_MEASUREMENT_CONTEXT, deviceAddress);

        if (sendingOk) {
            try {
                BluetoothGattDescriptor temp = descriptorQueue.poll(10, TimeUnit.SECONDS);
                result = temp != null;
            } catch (InterruptedException ex) {
                Log.e(TAG, "Could not enable notification for glucose measurement (interrupted).");
            }
        } else {
            Log.e(TAG, "Could not enable notification for glucose measurement (returned false).");
        }
        return result;
    }

    private boolean enableRACPIndication() {
        boolean sendingOk = bleService.enableIndication(
                UUID_GLUCOSE_SERVICE, UUID_RECORD_ACCESS_CONTROL_POINT, deviceAddress);
        boolean result = false;

        if (sendingOk) {
            try {
                BluetoothGattDescriptor temp = descriptorQueue.poll(10, TimeUnit.SECONDS);
                result = temp != null;
            } catch (InterruptedException ex) {
                Log.e(TAG, "Could not enable notification for RACP (interrupted).");
            }
        } else {
            Log.e(TAG, "Could not enable notification for RACP (returned false).");
        }
        return result;
    }
}

