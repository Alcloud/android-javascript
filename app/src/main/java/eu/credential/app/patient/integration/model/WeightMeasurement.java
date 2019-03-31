package eu.credential.app.patient.integration.model;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Created by ogr on 28.04.2016.
 */
public class WeightMeasurement extends Measurement{

    // offsets in bytes
    private int weightOffset;
    private int timestampOffset;
    private int userIdOffset;
    private int bmiOffset;
    private int heightOffset;

    private Date receiveTime;

    public static final String UNIT_SCI_WEIGHT = "kg";
    public static final int FACTOR_SCI_WEIGHT = 200;
    public static final String UNIT_SCI_HEIGHT = "m";
    public static final int FACTOR_SCI_HEIGHT = 1000;
    public static final String UNIT_IMP_WEIGHT = "lb";
    public static final int FACTOR_IMP_WEIGHT = 100;
    public static final String UNIT_IMP_HEIGHT = "in";
    public static final int FACTOR_IMP_HEIGHT = 10;
    public static final int FACTOR_BMI = 10;

    // flag positions
    private static final int FLAG_UNIT = 0;
    private static final int FLAG_TIMESTAMP = 1;
    private static final int FLAG_USERID = 2;
    private static final int FLAG_BMI_HEIGHT = 3;

    public WeightMeasurement(BluetoothGattCharacteristic characteristic) {
        super(characteristic);

        // calculate the offsets depending on the flags
        weightOffset = flagOffset + 1; // 16 bits for weight indication
        timestampOffset = weightOffset + 2; // 56 bits for time date field
        userIdOffset = isFlagSet(FLAG_TIMESTAMP) ? timestampOffset + 7 : timestampOffset;
        bmiOffset = isFlagSet(FLAG_USERID) ? userIdOffset + 1 : userIdOffset;
        heightOffset = isFlagSet(FLAG_BMI_HEIGHT) ? bmiOffset + 2 : bmiOffset;
        this.receiveTime = Calendar.getInstance().getTime();
    }

    /**
     * Prints the weight unit depending on the flag.
     *
     * @return
     */
    public String getWeightUnit() {
        return isFlagSet(FLAG_UNIT) ? UNIT_IMP_WEIGHT : UNIT_SCI_WEIGHT;
    }

    /**
     * Prints the height unit depending on the flag
     *
     * @return
     */
    public String getHeightUnit() {
        return isFlagSet(FLAG_UNIT) ? UNIT_IMP_HEIGHT : UNIT_SCI_HEIGHT;
    }

    /**
     * Returns the base time or null if not given.
     */
    public Date getBaseTime() {
            return receiveTime;
    }

    public void setReceiveTime(Date receiveTime) {
        this.receiveTime = receiveTime;
    }

    public Date getReceiveTime() {
        return receiveTime;
    }

    /**
     * Returns the parsed weight in units.
     *
     * @return
     */
    public double getWeight() {
        double baseVal = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, weightOffset);
        double converted;
        if (isFlagSet(FLAG_UNIT)) {
            converted = baseVal / FACTOR_IMP_WEIGHT;
        } else {
            converted = baseVal / FACTOR_SCI_WEIGHT;
        }
        return converted;
    }

    /**
     * Returns the parsed weight in units.
     *
     * @return
     */
    public boolean weightFailed() {
        int baseVal = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, weightOffset);
        return baseVal == 0xFFFF;
    }

    public int getUserId() {
        if(isFlagSet(FLAG_USERID)) {
            return characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, userIdOffset);
        }
        return -1;
    }

    public double getBmi() {
        if(isFlagSet(FLAG_BMI_HEIGHT)) {
            int base = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, bmiOffset);
            return base / 10;
        }
        return -1;
    }

    public double getHeight() {
        if(!isFlagSet(FLAG_BMI_HEIGHT)) {
            return -1;
        }

        int base = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, heightOffset);
        double converted;
        if(isFlagSet(FLAG_UNIT)) {
            converted = base / FACTOR_IMP_WEIGHT;
        } else {
            converted = base / FACTOR_SCI_HEIGHT;
        }
        return converted;

    }

    public String toInfluxLine() {
        StringBuilder builder = new StringBuilder();

        // Name of the measurement
        builder.append("Weight");

        // tags
        if(isFlagSet(FLAG_BMI_HEIGHT)) {
            builder.append(",height_unit=").append(getHeightUnit());
        }
        if(isFlagSet(FLAG_USERID)) {
            builder.append(",user_id=").append(getUserId());
        }
        builder.append(",weight_unit=").append(getWeightUnit());

        // separator
        builder.append(" ");

        // measurements
        builder.append("weight=").append(getWeight());
        if(isFlagSet(FLAG_BMI_HEIGHT)) {
            builder.append(",height=").append(getHeight());
            builder.append(",bmi=").append(getBmi());
        }

        if(isFlagSet(FLAG_TIMESTAMP)) {
            // separator
            builder.append(" ");

            // time in unix format
            builder.append(getBaseTime().getTime());
            // by default, influx db wants the time in nano-seconds, not milliseconds
            builder.append("000000");
        }

        return builder.toString();
    }

    /**
     * Converts glucose measurement data into a human-readable string.
     * @return
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        // Weight
        builder.append("Weight: ");
        if(weightFailed()) {
            builder.append("Measurement failed");
        } else {
            builder.append(getWeight()).append(" ").append(getWeightUnit());
        }

        // Base time and if existing, time offset
        if(isFlagSet(FLAG_TIMESTAMP)) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy HH:mm:ss Z", Locale.US);
            builder.append("\nTime: ").append(dateFormat.format(getBaseTime()));
        }

        // User id, if set
        if(isFlagSet(FLAG_USERID)) {
            int userId = getUserId();
            builder.append("\nUser ID: ").append(userId == 0xFF ? "unknown" : userId);
        }

        // BMI and height
        if(isFlagSet(FLAG_BMI_HEIGHT)) {
            builder.append("\nBMI: ").append(getBmi());
            builder.append("\nHeight: ").append(getHeight()).append(" ").append(getHeightUnit());
        }

        return builder.toString();
    }
    public void writeJSON(Context context){
        JSONObject weightJson = new JSONObject();
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        Date baseTime = getBaseTime();
        Date receiveTime = getReceiveTime();
        String fileNameSpace = getReceiveTime().toString();
        String fileName = fileNameSpace.replaceAll("\\W", "");
        String jsonBaseTime;
        String jsonReceiveTime;
        try {
            //Convert Date to json with timeStamp
            jsonBaseTime = objectMapper.writeValueAsString(baseTime);
            jsonReceiveTime = objectMapper.writeValueAsString(receiveTime);

            FileWriter file = new FileWriter(context.getFilesDir()+"/"+fileName+".json");
            weightJson.put("deviceTime", jsonBaseTime);
            weightJson.put("receiveTime", jsonReceiveTime);
            weightJson.put("weight", getWeight());
            weightJson.put("weightUnit", getWeightUnit());
            file.write(weightJson.toString());
            file.flush();
            file.close();
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }
}
