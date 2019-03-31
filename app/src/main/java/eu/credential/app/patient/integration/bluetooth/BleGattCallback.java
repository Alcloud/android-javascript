package eu.credential.app.patient.integration.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

/**
 * Implementation of Bluetooth callback methods which control the BLE service on Gatt-Events.
 */
public class BleGattCallback extends BluetoothGattCallback {

    // Tag used for logging
    private final static String TAG = BleGattCallback.class.getSimpleName();

    // Reference to the commanded ble service
    private BleService bleService;

    public BleGattCallback(BleService bleService) {
        this.bleService = bleService;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        bleService.processConnectionStateChange(newState, gatt);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        bleService.processServiceDiscoveryResult(status, gatt);
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic,
                                     int status) {
        bleService.processCharacteristicReadResult(status, characteristic, gatt);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt,
                                      BluetoothGattCharacteristic characteristic,
                                      int status) {
        bleService.processCharacteristicWriteResult(status, characteristic, gatt);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        bleService.processCharacteristicChangedResult(characteristic, gatt);
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt,
                                  BluetoothGattDescriptor descriptor,
                                  int status) {
        bleService.processDescriptorWriteResult(status, descriptor, gatt);
    }

}
