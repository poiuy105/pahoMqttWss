package paho.android.mqtt_example;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MQTT Client";

    private TextInputEditText editBroker;
    private TextInputEditText editClientId;
    private TextInputEditText editUsername;
    private TextInputEditText editPassword;
    private TextInputEditText editSubscribeTopic;
    private TextInputEditText editPublishTopic;
    private TextInputEditText editPublishMessage;
    private TextView txtStatus;
    private TextView txtLog;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnSubscribe;
    private Button btnPublish;
    private Button btnClearLog;

    private MqttAndroidClient client;
    private MqttConnectOptions mqttOptions;
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(paho.android.mqtt_example.R.layout.activity_main);

        initViews();
        setupListeners();
    }

    private void initViews() {
        editBroker = findViewById(R.id.editBroker);
        editClientId = findViewById(R.id.editClientId);
        editUsername = findViewById(R.id.editUsername);
        editPassword = findViewById(R.id.editPassword);
        editSubscribeTopic = findViewById(R.id.editSubscribeTopic);
        editPublishTopic = findViewById(R.id.editPublishTopic);
        editPublishMessage = findViewById(R.id.editPublishMessage);
        txtStatus = findViewById(R.id.txtStatus);
        txtLog = findViewById(R.id.txtLog);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnSubscribe = findViewById(R.id.btnSubscribe);
        btnPublish = findViewById(R.id.btnPublish);
        btnClearLog = findViewById(R.id.btnClearLog);

        editBroker.setText("wss://broker.hivemq.com:443");
        editSubscribeTopic.setText("#");
    }

    private void setupListeners() {
        btnConnect.setOnClickListener(v -> connect());
        btnDisconnect.setOnClickListener(v -> disconnect());
        btnSubscribe.setOnClickListener(v -> subscribe());
        btnPublish.setOnClickListener(v -> publish());
        btnClearLog.setOnClickListener(v -> txtLog.setText(""));
    }

    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    private void log(String message) {
        String timestamp = getTimestamp();
        String logMessage = timestamp + " - " + message + "\n";
        txtLog.append(logMessage);
        Log.d(TAG, message);
    }

    private void updateStatus(String status, int color) {
        txtStatus.setText(status);
        txtStatus.setTextColor(color);
    }

    private void updateConnectionState(boolean connected) {
        isConnected = connected;
        btnConnect.setEnabled(!connected);
        btnDisconnect.setEnabled(connected);
        btnSubscribe.setEnabled(connected);
        btnPublish.setEnabled(connected);
    }

    private javax.net.ssl.SSLSocketFactory createTrustAllSSLSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }
            }}, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create SSL socket factory", e);
            return null;
        }
    }

    private javax.net.ssl.SSLSocketFactory createCustomSSLSocketFactory(Context context) {
        try {
            SocketFactory.SocketFactoryOptions socketFactoryOptions = new SocketFactory.SocketFactoryOptions();
            socketFactoryOptions.withCaInputStream(
                    context.getResources().openRawResource(paho.android.mqtt_example.R.raw.mosquitto_org));
            return new SocketFactory(socketFactoryOptions);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create custom SSL socket factory", e);
            return null;
        }
    }

    private void connect() {
        String broker = editBroker.getText().toString().trim();
        if (broker.isEmpty()) {
            editBroker.setError("Broker URL is required");
            return;
        }

        String clientId = editClientId.getText().toString().trim();
        if (clientId.isEmpty()) {
            clientId = MqttClient.generateClientId();
        }

        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString();

        updateStatus("Connecting...", 0xFFFF9800);
        log("Connecting to: " + broker);

        try {
            client = new MqttAndroidClient(getBaseContext(), broker, clientId);
            mqttOptions = new MqttConnectOptions();

            if (!username.isEmpty()) {
                mqttOptions.setUserName(username);
            }
            if (!password.isEmpty()) {
                mqttOptions.setPassword(password.toCharArray());
            }

            mqttOptions.setCleanSession(true);
            mqttOptions.setKeepAliveInterval(60);
            mqttOptions.setConnectionTimeout(30);

            if (broker.contains("ssl") || broker.contains("wss")) {
                if (broker.contains("test.mosquitto.org") || broker.contains("mosquitto.org")) {
                    javax.net.ssl.SSLSocketFactory sslFactory = createCustomSSLSocketFactory(this);
                    if (sslFactory != null) {
                        mqttOptions.setSocketFactory(sslFactory);
                        log("Using custom certificate (Mosquitto test server)");
                    }
                } else {
                    javax.net.ssl.SSLSocketFactory sslFactory = createTrustAllSSLSocketFactory();
                    if (sslFactory != null) {
                        mqttOptions.setSocketFactory(sslFactory);
                        log("Using trust-all SSL factory (for testing)");
                    }
                }
            }

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    runOnUiThread(() -> {
                        log("Connection lost: " + (cause != null ? cause.getMessage() : "unknown"));
                        updateStatus("Disconnected", 0xFFFF5722);
                        updateConnectionState(false);
                    });
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    runOnUiThread(() -> {
                        log("Topic: " + topic + " | Message: " + message.toString());
                    });
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    runOnUiThread(() -> {
                        log("Message delivered");
                    });
                }
            });

            IMqttToken token = client.connect(mqttOptions);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    runOnUiThread(() -> {
                        log("Connected successfully");
                        updateStatus("Connected", 0xFF4CAF50);
                        updateConnectionState(true);
                    });
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    runOnUiThread(() -> {
                        log("Connection failed: " + (exception != null ? exception.getMessage() : "unknown"));
                        updateStatus("Connection Failed", 0xFFFF5722);
                        updateConnectionState(false);
                    });
                }
            });

        } catch (MqttException e) {
            log("MqttException: " + e.getMessage());
            updateStatus("Connection Failed", 0xFFFF5722);
            e.printStackTrace();
        }
    }

    private void disconnect() {
        if (client != null && client.isConnected()) {
            try {
                IMqttToken token = client.disconnect();
                token.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        runOnUiThread(() -> {
                            log("Disconnected");
                            updateStatus("Disconnected", 0xFFFF5722);
                            updateConnectionState(false);
                        });
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        runOnUiThread(() -> {
                            log("Disconnect failed: " + (exception != null ? exception.getMessage() : "unknown"));
                        });
                    }
                });
            } catch (MqttException e) {
                log("Disconnect error: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            log("Not connected");
        }
    }

    private void subscribe() {
        String topic = editSubscribeTopic.getText().toString().trim();
        if (topic.isEmpty()) {
            editSubscribeTopic.setError("Topic is required");
            return;
        }

        if (client == null || !client.isConnected()) {
            log("Not connected. Please connect first.");
            return;
        }

        try {
            IMqttToken token = client.subscribe(topic, 1);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    runOnUiThread(() -> {
                        log("Subscribed to: " + topic);
                    });
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    runOnUiThread(() -> {
                        log("Subscribe failed: " + (exception != null ? exception.getMessage() : "unknown"));
                    });
                }
            });
        } catch (MqttException e) {
            log("Subscribe error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void publish() {
        String topic = editPublishTopic.getText().toString().trim();
        String message = editPublishMessage.getText().toString();

        if (topic.isEmpty()) {
            editPublishTopic.setError("Topic is required");
            return;
        }

        if (message.isEmpty()) {
            editPublishMessage.setError("Message is required");
            return;
        }

        if (client == null || !client.isConnected()) {
            log("Not connected. Please connect first.");
            return;
        }

        try {
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(message.getBytes());
            mqttMessage.setQos(1);

            client.publish(topic, mqttMessage, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    runOnUiThread(() -> {
                        log("Published to: " + topic + " | Message: " + message);
                    });
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    runOnUiThread(() -> {
                        log("Publish failed: " + (exception != null ? exception.getMessage() : "unknown"));
                    });
                }
            });
        } catch (MqttException e) {
            log("Publish error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }
}