package ca.simark.roverdroid;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Timer;
import java.util.TimerTask;

public class ControlPanelActivity extends AppCompatActivity implements MqttCallback {

    MqttAndroidClient fClient;

    public static final String TAG = ControlPanelActivity.class.getSimpleName();

    ProgressDialog fProgressDialog;

    static final String SERVER_URI = "tcp://iot.eclipse.org:1883";

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

    private String generateClientId() {
        return "foo";
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "onCreate");

        setContentView(R.layout.activity_control_panel);

        fClient = new MqttAndroidClient(this, SERVER_URI, generateClientId());
        fClient.setCallback(this);
        try {
            fClient.connect(null, new IMqttActionListener() {
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
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.i(TAG, "Message arrived, " + message.getPayload().length + " bytes.");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.i(TAG, "Delivery complete.");
    }
}
