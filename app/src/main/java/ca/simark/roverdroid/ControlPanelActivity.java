package ca.simark.roverdroid;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import ca.simark.roverdroid.proto.Sensors;

public class ControlPanelActivity extends AppCompatActivity implements MqttCallback {

    MqttAndroidClient fClient;
    TextView fBoiteDeTexte;

    public static final String TAG = ControlPanelActivity.class.getSimpleName();

    ProgressDialog fProgressDialog;

    static final String SERVER_URI = "tcp://10.0.0.11:1883";

    private void showProgressDialog() {
        fProgressDialog = new ProgressDialog(this);
        fProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        fProgressDialog.setMessage("Trying to connect to " + SERVER_URI + "...");
        fProgressDialog.setIndeterminate(true);
        fProgressDialog.setCanceledOnTouchOutside(false);
        fProgressDialog.setCancelable(true);
        fProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                Log.i(TAG, "ProgressDialog canceled.");
            }
        });
        fProgressDialog.show();
    }

    private void dismissProgressDialog() {
        if (fProgressDialog != null) {
            fProgressDialog.dismiss();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
        doConnect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
        doDisconnect();
    }

    private void doConnect() {
        fClient = new MqttAndroidClient(this, SERVER_URI, MqttClient.generateClientId());
        fClient.setCallback(this);

        try {
            MqttConnectOptions opt = new MqttConnectOptions();
            opt.setAutomaticReconnect(true);

            fClient.connect(opt, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "Connect success.");
                    doSubscribe();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "We failed to connect. " + exception);
                    dismissProgressDialog();
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Caught connect exception: " + e);
            dismissProgressDialog();
        }

        showProgressDialog();
    }

    private void doDisconnect() {
        if (fClient != null) {
            try {
                fClient.disconnect(null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.i(TAG, "Disconnect success");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e(TAG, "Disconnect failure", exception);
                    }
                });
                fClient.unregisterResources();
            } catch (MqttException e) {
                Log.e(TAG, "disconnect error", e);
            } finally {
                fClient = null;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "onCreate");

        setContentView(R.layout.activity_control_panel);

        fBoiteDeTexte = (TextView) findViewById(R.id.boitedetexte);
    }

    private void doSubscribe() {
        try {
            fClient.subscribe("/polarsys-rover/sensors", 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "Subscribe success.");
                    dismissProgressDialog();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "We failed to subscribe. " + exception);
                    dismissProgressDialog();
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Caught subscribe exception: " + e);
            dismissProgressDialog();
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.e(TAG, "Connection lost!");
        Log.e(TAG, "cause: " + cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.i(TAG, "Message arrived, " + message.getPayload().length + " bytes.");
        /*try {
            Sensors.RoverSensors roverSensors = Sensors.RoverSensors.parseFrom(message.getPayload());
            Log.i(TAG, "Parsed message " + roverSensors);
            if (roverSensors.hasAccel() && roverSensors.getAccel().hasX()) {
                fBoiteDeTexte.setText("Accel x: " + roverSensors.getAccel().getX());
            }
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "Protobuf parse exception: " + e);
            throw e;
        }*/
        fBoiteDeTexte.setText(new String(message.getPayload()));

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.i(TAG, "Delivery complete.");
    }
}
