package eu.credential.app.patient.integration.model;

import android.bluetooth.BluetoothGattCharacteristic;

import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Created by ogr on 04.07.2016.
 */
public abstract class Measurement {

    private Date receiveTime;

    // underlying byte array
    protected BluetoothGattCharacteristic characteristic;
    protected int flags;

    // offset
    protected int flagOffset;

    public Measurement(BluetoothGattCharacteristic characteristic) {
        this.receiveTime = Calendar.getInstance().getTime();

        // take the GATT coded data
        this.characteristic = characteristic;

        // take the flags
        this.flags = characteristic.getValue()[0];
        this.flagOffset = 0;
    }

    /**
     * Checks, if the bit at the given position is set.
     *
     * @param data word to be checked
     * @param pos  position of the bit
     * @return true, if the bit on the position is checked, else false.
     */
    protected boolean isBitSet(int data, int pos) {
        int mask = 1 << pos;
        return (data & mask) == mask;
    }

    /**
     * Gives the status of the flags in the first byte of the underlying characteristic-
     *
     * @param pos position of the bit starting with zero
     * @return true if the flag is set to 1
     */
    protected boolean isFlagSet(int pos) {
        return isBitSet(flags, pos);
    }

    /**
     * Creates a pseudo unsigned int out of the given value.
     *
     * @return
     */
    protected int readUnsignedInt(BitSet bits) {
        int result = 0;
        for (int i = 0; i < bits.length(); i++) {
            if (bits.get(i)) result += Math.pow(2, i);
        }
        return result;
    }
    public void setReceiveTime(Date receiveTime) {
        this.receiveTime = receiveTime;
    }

    public Date getReceiveTime() {
        return receiveTime;
    }
    /**
     * Returns the 7-bit time by reading data till the given offset
     */
    protected Date readTime(int startOffset) {
        int yearOffset = startOffset;
        int monthOffset = yearOffset + 2;
        int dayOffset = monthOffset + 1;
        int hourOffset = dayOffset + 1;
        int minuteOffset = hourOffset + 1;
        int secondOffset = minuteOffset + 1;

        int year = characteristic.getIntValue(
                BluetoothGattCharacteristic.FORMAT_UINT16, yearOffset);
        int month = characteristic.getIntValue(
                BluetoothGattCharacteristic.FORMAT_UINT8, monthOffset);
        int day = characteristic.getIntValue(
                BluetoothGattCharacteristic.FORMAT_UINT8, dayOffset);
        int hour = characteristic.getIntValue(
                BluetoothGattCharacteristic.FORMAT_UINT8, hourOffset);
        int minute = characteristic.getIntValue(
                BluetoothGattCharacteristic.FORMAT_UINT8, minuteOffset);
        int seconds = characteristic.getIntValue(
                BluetoothGattCharacteristic.FORMAT_UINT8, secondOffset);

        Calendar cal = GregorianCalendar.getInstance();
        // Attention: MONTH field in Calendar starts with 0
        cal.set(year, month - 1, day, hour, minute, seconds);
        cal.set(Calendar.MILLISECOND, 0);

        return cal.getTime();
    }

    /**
     * Serializes the measurement data (tags and fields) to the line protocol.
     * The line protocol is a space-efficient single line format used by InfluxDB.
     * @return
     */
    public abstract String toInfluxLine();

    @Override
    public abstract String toString();

}
