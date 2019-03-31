package eu.credential.app.patient.orchestration.collection;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;

import eu.credential.app.patient.integration.bluetooth.BleService;
import eu.credential.app.patient.integration.model.WeightMeasurement;

import java.util.UUID;

/**
 * This class ressembles a handler, for getting device data.
 * Created by ogr on 21.06.2016.
 */
public class WeightHandler extends CollectionHandler {
    private final static String TAG = WeightHandler.class.getSimpleName();

    // gatt Attributes which will be contacted
    private final static UUID UUID_WEIGHT_MEASUREMENT =
            UUID.fromString("00002a9d-0000-1000-8000-00805f9b34fb");
    // only for reading features
    private final static UUID UUID_WEIGHT_SCALE_FEATURE =
            UUID.fromString("00002a9e-0000-1000-8000-00805f9b34fb");
    // gatt ServiceID
    private final static UUID UUID_WEIGHT_SCALE_SERVICE =
            UUID.fromString("0000181d-0000-1000-8000-00805f9b34fb");

    public WeightHandler(String deviceAddress, BleService bleService, CollectorService collectorService) {
        super(deviceAddress, bleService, collectorService);
    }

    protected void connectedResultHook(Intent intent) {
        // Connected Result
    }

    @Override
    protected void descriptorWriteResultHook(Intent intent) {

    }

    protected void disconnectedResultHook(Intent intent) {
        //
    }

    /**
     * Enables the notification for glucose data and requests a record receive.
     *
     * @param intent
     */
    protected void serviceDiscoveryResultHook(Intent intent) {
        enableWeightIndication();
    }

    /**
     * Interprets data received from the health device and makes it human-readable.
     *
     * @param intent
     */
    protected void dataResultHook(Intent intent) {
        this.dataReceived = true;
        BluetoothGattCharacteristic characteristic = recreateCharacteristic(intent);
        String collectedData = "";
        if (UUID_WEIGHT_MEASUREMENT.equals(characteristic.getUuid())) {
            WeightMeasurement measurement = new WeightMeasurement(characteristic);
            collectorService.receiveMeasurement(measurement, deviceAddress);
        }
    }

    private void enableWeightIndication() {
        bleService.enableIndication(UUID_WEIGHT_SCALE_SERVICE, UUID_WEIGHT_MEASUREMENT, deviceAddress);
        notificationEnabled = true;
    }
}

