package eu.credential.app.patient.ui.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.administrator.credential_v020.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import eu.credential.app.patient.integration.model.GlucoseMeasurement;
import eu.credential.app.patient.integration.model.Measurement;
import eu.credential.app.patient.integration.model.WeightMeasurement;
import eu.credential.app.patient.integration.upload.UploadBroadcastReceiver;
import eu.credential.app.patient.orchestration.collection.CollectorBroadcastReceiver;
import eu.credential.app.patient.orchestration.collection.CollectorService;
import eu.credential.app.patient.orchestration.collection.CollectorServiceConnection;
import eu.credential.app.patient.orchestration.collection.WithCollectorService;

/**
 * View for the settings fragment
 */
public class DevicesActivity extends AppCompatActivity implements WithCollectorService {
    private LocalBroadcastManager localBroadcastManager;

    // Services the activity works with
    private CollectorService collectorService;
    private CollectorServiceConnection collectorServiceConnection;
    private CollectorBroadcastReceiver collectorBroadcastReceiver;
    private UploadBroadcastReceiver uploadBroadcastReceiver;

    private WebView myWebView;
    private String JAVASCRIPT_OBJ = "javascript_obj";
    private String BASE_URL = "file:///android_asset/webview.html";
    private EditText editTextToWeb;
    private TextView textWebView;
    private String glucoseValue;
    private int currentCounter;
    private static Map<Integer, Measurement> measurementMap;

    public DevicesActivity() {
        super();
        this.collectorService = null;
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        myWebView = (WebView) this.findViewById(R.id.myWebView);

        Button sendToWeb = (Button) findViewById(R.id.btn_send_to_web);
        Button generateValue = (Button) findViewById(R.id.btn_generate_value);
        editTextToWeb = (EditText) findViewById(R.id.edit_text_to_web);
        textWebView = (TextView) findViewById(R.id.txt_from_web);

        myWebView.getSettings().setJavaScriptEnabled(true);
        myWebView.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.equals(BASE_URL)) {
                    injectJavaScriptFunction();
                }
            }
        });

        currentCounter = 0;
        measurementMap = Collections.synchronizedMap(new TreeMap<Integer, Measurement>());

        myWebView.addJavascriptInterface(new JavaScriptInterface(), JAVASCRIPT_OBJ);
        WebView.setWebContentsDebuggingEnabled(true);

        myWebView.loadUrl(BASE_URL);

        this.localBroadcastManager = LocalBroadcastManager.getInstance(this);
        // Register the BLE service and bind it
        Intent collectorServiceIntent = new Intent(this, CollectorService.class);
        this.collectorServiceConnection = new CollectorServiceConnection(this);
        this.bindService(collectorServiceIntent, collectorServiceConnection, Context.BIND_AUTO_CREATE);

        sendToWeb.setOnClickListener(view -> myWebView.evaluateJavascript("javascript: " +
                "updateFromAndroid(\"" + editTextToWeb.getText() + "\")", null));

        generateValue.setOnClickListener(v1 -> {
            glucoseValue = String.valueOf(ThreadLocalRandom.current().nextInt(97, 119));
            editTextToWeb.setText(glucoseValue);
            Toast.makeText(this, "Last glucose value: " + glucoseValue, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // register tbe broadcast listener, to get collector events
        this.collectorBroadcastReceiver = new CollectorBroadcastReceiver(this);
        IntentFilter collectorActions = collectorBroadcastReceiver.getIntentFilter();
        localBroadcastManager.registerReceiver(collectorBroadcastReceiver, collectorActions);

        // register broadcast listener for upload events
        this.uploadBroadcastReceiver = new UploadBroadcastReceiver(this);
        IntentFilter uploadActions = uploadBroadcastReceiver.getIntentFilter();
        localBroadcastManager.registerReceiver(uploadBroadcastReceiver, uploadActions);

        // on first execution the collector service will not be bound
        if (this.collectorService != null) {
            refreshMeasurements();
        }
    }

    @Override
    protected void onPause() {
        localBroadcastManager.unregisterReceiver(collectorBroadcastReceiver);
        localBroadcastManager.unregisterReceiver(uploadBroadcastReceiver);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this.collectorServiceConnection);
        myWebView.removeJavascriptInterface(JAVASCRIPT_OBJ);
    }

    @Override
    public void listMessage(final String message) {
    }

    @Override
    public void setCollectorService(CollectorService collectorService) {
        this.collectorService = collectorService;
        if (this.collectorService != null) {
            refreshMeasurements();
        }
    }

    @Override
    public void refreshMeasurements() {
        // Check revision
        if (collectorService.getDataCount() == this.currentCounter) {
            return;
        }
        // When the versions differ receive the new data.
        Map<Integer, Measurement> there = collectorService.getMeasurementMap();
        Map<Integer, Measurement> here = measurementMap;

        // Search for new entries and collect them
        for (Integer id : there.keySet()) {
            if (!here.containsKey(id)) {
                Measurement newMeasurement = there.get(id);
                here.put(id, newMeasurement);
            }
        }
        //show toast message
        showToast(here);
        // update the current revision
        this.currentCounter = collectorService.getDataCount();
    }

    @Override
    public void refreshMessages() {
    }

    @Override
    public void displayConnectionStateChange(String deviceAddress, String deviceName, boolean hasConnected) {
        String statusText = hasConnected ? "connected" : "disconnected";
        if (!this.isFinishing()) {
            Toast.makeText(
                    this.getApplicationContext(),
                    deviceName + " " + statusText,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void injectJavaScriptFunction() {
        myWebView.loadUrl("javascript: " +
                "window.androidObj.textToAndroid = function(message) { " +
                JAVASCRIPT_OBJ + ".textFromWeb(message) }");
    }

    public void javascriptCallFinished(final String val){

        Toast.makeText(this, val, Toast.LENGTH_SHORT).show();

        // I need to run set operation of UI on the main thread.
        // therefore, the above parameter "val" must be final
        runOnUiThread(() -> textWebView.setText(val));
    }

    private class JavaScriptInterface {
        @JavascriptInterface
        public void textFromWeb(String fromWeb) {
            textWebView.setText(fromWeb);
        }
    }

    /**
     * This method create a new JSON element with glucose measurement values.
     */
    private String setGlucoseInPHRDocument(String sequenceNumber, String concentration, String unit,
                                           String fluidType, String sampleLocation, String sensorStatus,
                                           String deviceTime, String receiveTime) {
        return "{\n" +
                "  \"$schema\": \"http://json-schema.org/draft-04/schema#\",\n" +
                "  \"title\": \"Glucose Measurement\",\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"sequenceNumber\": {\n" +
                "      \"description\": \"internal measurement id given by the device\",\n" +
                "      \"value\": \"" + sequenceNumber + "\",\n" +
                "      \"type\": \"integer\",\n" +
                "      \"minimum\": 0\n" +
                "    },\n" +
                "    \"deviceTime\": {\n" +
                "      \"description\": \"ISO time string given by measurement device\",\n" +
                "      \"value\": \"" + deviceTime + "\",\n" +
                "      \"type\": \"string\"\n" +
                "    },\n" +
                "    \"receiveTime\": {\n" +
                "      \"description\": \"ISO time string, stating when the app received measurement from device\",\n" +
                "      \"value\": \"" + receiveTime + "\",\n" +
                "      \"type\": \"string\"\n" +
                "    },\n" +
                "    \"concentration\": {\n" +
                "      \"description\": \"how much glucose the device has measured in the given unit\",\n" +
                "      \"value\": \"" + concentration + "\",\n" +
                "      \"type\": \"number\",\n" +
                "      \"minimum\": 0\n" +
                "    },\n" +
                "    \"unit\": {\n" +
                "      \"description\": \"physical unit describing the glucose concentration\",\n" +
                "      \"value\": \"" + unit + "\",\n" +
                "      \"enum\": [\n" +
                "        \"kg/L\",\n" +
                "        \"mol/L\"\n" +
                "      ]\n" +
                "    },\n" +
                "    \"fluidType\": {\n" +
                "      \"description\": \"fluid type delivered to device\",\n" +
                "      \"value\": \"" + fluidType + "\",\n" +
                "      \"enum\": [\n" +
                "        \"Capillary Whole blood\",\n" +
                "        \"Capillary Plasma\",\n" +
                "        \"Venous Whole blood\",\n" +
                "        \"Venous Plasma\",\n" +
                "        \"Arterial Whole blood\",\n" +
                "        \"Arterial Plasma\",\n" +
                "        \"Undetermined Whole blood\",\n" +
                "        \"Undetermined Plasma\",\n" +
                "        \"Interstitial Fluid (ISF)\",\n" +
                "        \"Control Solution\"\n" +
                "      ]\n" +
                "    },\n" +
                "    \"sampleLocation\": {\n" +
                "      \"description\": \"body location the fluid was taken from\",\n" +
                "      \"value\": \"" + sampleLocation + "\",\n" +
                "      \"enum\": [\n" +
                "        \"Finger\",\n" +
                "        \"Alternate Site Test (AST)\",\n" +
                "        \"Earlobe\",\n" +
                "        \"Control solution\",\n" +
                "        \"Sample Location value not available\"\n" +
                "      ]\n" +
                "    },\n" +
                "    \"sensorStatus\": {\n" +
                "      \"description\": \"technical annunciations by the measurement device\",\n" +
                "      \"value\": \"" + sensorStatus + "\",\n" +
                "      \"type\": \"array\",\n" +
                "      \"uniqueItems\": true,\n" +
                "      \"minItems\": 0,\n" +
                "      \"items\": {\n" +
                "        \"enum\": [\n" +
                "          \"Device battery low at time of measurement\",\n" +
                "          \"Sensor malfunction or faulting at time of measurement\",\n" +
                "          \"Sample size for blood or control solution insufficient at time of measurement\",\n" +
                "          \"Strip insertion error\",\n" +
                "          \"Strip type incorrect for device\",\n" +
                "          \"Sensor result higher than the device can process\",\n" +
                "          \"Sensor result lower than the device can process\",\n" +
                "          \"Sensor temperature too high for valid test/result at time of measurement\",\n" +
                "          \"Sensor temperature too low for valid test/result at time of measurement\",\n" +
                "          \"Sensor read interrupted because strip was pulled too soon at time of measurement\",\n" +
                "          \"General device fault has occurred in the sensor\",\n" +
                "          \"Time fault has occurred in the sensor and time may be inaccurate\"\n" +
                "        ]\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n";
    }

    private void showToast(Map<Integer, Measurement> here) {
        StringBuilder builder = new StringBuilder();
        ArrayList<Number> toastSeries = new ArrayList<>();
        GlucoseMeasurement glucoseValue;
        WeightMeasurement weightValue;

        for (Integer key : here.keySet()) {
            Measurement meas = here.get(key);
            if (meas instanceof GlucoseMeasurement) {
                glucoseValue = (GlucoseMeasurement) meas;
                toastSeries.add(glucoseValue.getGlucoseConcentration() * 100000);
            } else if (meas instanceof WeightMeasurement) {
                weightValue = (WeightMeasurement) meas;
                toastSeries.add(weightValue.getWeight());
            }
        }
        //show toast message
        builder.append(toastSeries.get(toastSeries.size() - 1).toString());
        Toast.makeText(this.getApplicationContext(), "Last Value: " + builder.toString(),
                Toast.LENGTH_SHORT).show();
        editTextToWeb.setText(builder.toString());
    }
}