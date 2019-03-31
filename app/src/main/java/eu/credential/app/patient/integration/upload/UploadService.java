package eu.credential.app.patient.integration.upload;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;


import com.example.administrator.credential_v020.R;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;


/**
 * Uploads measurements to the measurement influx database.
 */
public class UploadService extends IntentService {

    private static final String NIFI_URL =
            "https://ehealth-credential.fokus.fraunhofer.de/credential/write/oliver/ble";
    private final static String TAG = UploadService.class.getSimpleName();

    // Constants concerning the key material
    private static final String CLIENT_CERT_PW = "password";

    // Indicators used for broadcasts
    public static final String UPLOAD_EVENT = "UploadService.UPLOAD_EVENT";
    public static final String MESSAGE = "UploadService.MESSAGE";
    public static final String UPLOAD_CONTENT = "UploadService.UPLOAD_CONTENT";

    // variables used when handling a request
    private LocalBroadcastManager localBroadcastManager;
    private ConnectivityManager connManager;

    // Key management
    private SSLContext sslContext;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public UploadService(String name) {
        super(name);
    }

    public UploadService() {
        super("UploadService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.localBroadcastManager = LocalBroadcastManager.getInstance(this);
        this.connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        loadCertificates();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // network connection mandatory
        if (!isNetworkConnected()) {
            broadcastMessage("Error: Not connected to any network.");
            return;
        }

        // content to upload
        String content = intent.getStringExtra(UPLOAD_CONTENT);
        if (content == null) {
            broadcastMessage("Error: Nothing to upload.");
            return;
        }

        // synchronous upload
        try {
            uploadContent(content);
            broadcastMessage("Upload successful.");
        } catch (IOException ex) {
            broadcastMessage("Error: " + ex.getMessage());
        }
    }

    /**
     * Initializes Key Management system, imports server and client certificates.
     */
    private void loadCertificates() {
        try {
            KeyStore keyStore = buildKeyStore();
            KeyStore trustStore = buildTrustStore();
            this.sslContext = buildSSLContext(keyStore, trustStore);
        } catch (IOException | GeneralSecurityException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            broadcastMessage("Error: " + ex.getMessage());
        }
    }

    /**
     * Creates a key generator which will be used by HTTPs.
     *
     * @param keyStore Key store with loaded client certificate.
     */
    private SSLContext buildSSLContext(
            @NonNull KeyStore keyStore, @NonNull KeyStore trustStore)
            throws GeneralSecurityException {

        // Loading client key
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
        kmf.init(keyStore, CLIENT_CERT_PW.toCharArray());
        KeyManager[] keyManagers = kmf.getKeyManagers();

        // Loading trust store with server's certificate
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(trustStore);
        TrustManager[] trustManagers = tmf.getTrustManagers();

        // Initializing SSL context
        SSLContext result = SSLContext.getInstance("TLS");
        result.init(keyManagers, trustManagers, null);
        return result;
    }

    private KeyStore buildTrustStore() throws IOException, GeneralSecurityException {
        // load server's cert chain
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate cert;
        String alias;
        try (InputStream input = getResources().openRawResource(R.raw.server_public)) {
            ByteArrayInputStream bytes = new ByteArrayInputStream(decodePEMCertificate(input));
            cert = (X509Certificate) certFactory.generateCertificate(bytes);
            alias = cert.getSubjectX500Principal().getName();
        }

        // integrate server cert in trust store
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);
        trustStore.setCertificateEntry(alias, cert);
        return trustStore;
    }

    /**
     * Decodes the Contents of a PEM file, by cutting its beginnings and endings and decoding
     * base64.
     */
    private byte[] decodePEMCertificate(@NonNull InputStream input) {
        Scanner scanner = new Scanner(input);
        StringBuilder encoded = new StringBuilder();

        while(scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if(line.contains("-----END CERTIFICATE-----")) {
                break;
            } else if(!line.contains("-----BEGIN CERTIFICATE-----")) {
                encoded.append(line);
            }
        }

        byte[] result = Base64.decode(encoded.toString(), Base64.DEFAULT);
        return result;
    }

    /**
     * Initializes a PKCS12 keyStore with the client certificate.
     *
     * @return Keystore with opened client certificate.
     * @throws IOException              Client cert file not loaded.
     * @throws GeneralSecurityException Key store not buildable.
     */
    private KeyStore buildKeyStore() throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (InputStream input = getResources().openRawResource(R.raw.client_private)) {
            keyStore.load(input, CLIENT_CERT_PW.toCharArray());
        }

        return keyStore;
    }

    /**
     * Blocking HTTP-Post action.
     *
     * @param content
     * @throws IOException
     */
    private void uploadContent(String content) throws IOException {
        // Prepare request body
        byte[] contentBytes = content.getBytes("UTF-8");

        InputStream input = null;
        OutputStream output = null;
        HttpsURLConnection conn = null;
        try {
            // Init
            URL url = new URL(NIFI_URL);
            conn = (HttpsURLConnection) url.openConnection();
            if(this.sslContext != null) conn.setSSLSocketFactory(this.sslContext.getSocketFactory());
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(12000);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setChunkedStreamingMode(contentBytes.length);
            conn.setRequestMethod("POST");
            conn.connect();

            // Write
            broadcastMessage("Starting upload...");
            output = conn.getOutputStream();
            output.write(contentBytes);

            // Response
            int responseCode = conn.getResponseCode();
            input = conn.getInputStream();
            String response = readStream(input);
            if (responseCode != HttpsURLConnection.HTTP_OK) {
                throw new IOException(response);
            }

        } finally {
            if (input != null) input.close();
            if (output != null) output.close();
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream stream) throws IOException {
        Scanner scanner = new Scanner(stream, "UTF-8");
        String result = scanner.next();
        return result;
    }

    private void broadcastMessage(String message) {
        Intent intent = new Intent(UPLOAD_EVENT);
        intent.putExtra(MESSAGE, message);
        localBroadcastManager.sendBroadcast(intent);
    }

    private boolean isNetworkConnected() {
        NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
}
