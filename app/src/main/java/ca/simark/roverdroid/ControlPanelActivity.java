package ca.simark.roverdroid;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

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

import ca.simark.roverdroid.proto.Controls;
import ca.simark.roverdroid.proto.Sensors;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

public class ControlPanelActivity extends AppCompatActivity implements MqttCallback {

    MqttAndroidClient fClient;
    String fHost;
    int fPort;

    public static final String TAG = ControlPanelActivity.class.getSimpleName();

    ProgressDialog fProgressDialog;

    DirectionSurfaceView fLeftSurface, fRightSurface;
    private Controls.RoverControls.Builder fControlsBuilder;

    class Motor {
        /* Power, from -100 to 100. */
        private int fPower;
        private String fName;

        Motor(String name) {
            fPower = 0;
            fName = name;
        }

        public int getPower() {
            return fPower;
        }

        public void setPower(int power) {
            this.fPower = power;
        }

        public String getName() {
            return fName;
        }
    };

    private Motor fLeftMotor, fRightMotor;

    private int lastLeft = Integer.MAX_VALUE;
    private int lastRight = Integer.MAX_VALUE;

    private void showProgressDialog() {
        fProgressDialog = new ProgressDialog(this);
        fProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        fProgressDialog.setMessage("Trying to connect to " + fHost + ":" + fPort + "...");
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
        String uri = String.format("tcp://%s:%d", fHost, fPort);

        fClient = new MqttAndroidClient(this, uri, MqttClient.generateClientId());
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

        Intent launchIntent = getIntent();

        fHost = launchIntent.getStringExtra("host");
        if (fHost == null) {
            Log.e(TAG, "Activity created without required host argument.");
            return;
        }

        fPort = launchIntent.getIntExtra("port", -1);
        if (fPort == -1) {
            Log.e(TAG, "Activity created without required port argument.");
            return;
        }

        Log.i(TAG, "onCreate " + fHost + " " + fPort);

        setContentView(R.layout.activity_control_panel);

        fControlsBuilder = Controls.RoverControls.newBuilder();

        fLeftMotor = new Motor("left");
        fRightMotor = new Motor("right");

        fLeftSurface = (DirectionSurfaceView) findViewById(R.id.left_surface);
        fRightSurface = (DirectionSurfaceView) findViewById(R.id.right_surface);

        View.OnTouchListener otl = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                DirectionSurfaceView v = (DirectionSurfaceView) view;

                switch (motionEvent.getAction()) {
                    case ACTION_DOWN:
                        v.setActive();
                    case ACTION_MOVE:
                        computeMotorPower(v, motionEvent.getX(), motionEvent.getY(), v == fLeftSurface ? fLeftMotor : fRightMotor);
                        return true;
                    case ACTION_UP:
                        v.setInactive();
                        stopAll();
                        return true;
                }

                return false;
            }
        };

        if (fLeftSurface != null) {
            fLeftSurface.setOnTouchListener(otl);
        }

        if (fRightSurface != null) {
            fRightSurface.setOnTouchListener(otl);
        }
    }

    private void computeMotorPower(DirectionSurfaceView v, float x, float y, Motor motor) {
        int width = v.getWidth();
        int height = v.getHeight();



        if (y < 0) {
            y = 0;
        }

        if (y > height) {
            y = height;
        }

        /* Translate so that the middle is at 0. */
        y -= height / 2.0;

        /* In the view, smaller y means higher and bigger y means lower.  We want the opposite. */
        y *= -1;

        /* Scale to -100 to 100. */
        y *= 200.0/height;
        motor.setPower((int) y);

        updateMotors();
    }

    private void updateMotors() {
        /* Check if we have moved enough such that a new command is worth it. */
        if (fLeftMotor.getPower() == lastLeft && fRightMotor.getPower() == lastRight) {
            return;
        }

        fControlsBuilder.setLeft(fLeftMotor.getPower());
        fControlsBuilder.setRight(fRightMotor.getPower());
        Controls.RoverControls message = fControlsBuilder.build();

        lastLeft = fLeftMotor.getPower();
        lastRight = fRightMotor.getPower();

        try {
            fClient.publish("/polarsys-rover/controls", message.toByteArray(), 0, false, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.v(TAG, "Controls sent successfully");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Error sending controls 1", exception);
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Error sending controls 2", e);
        }
    }

    private void stopAll() {
        fLeftMotor.setPower(0);
        fRightMotor.setPower(0);
        updateMotors();
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
        Log.v(TAG, "Message arrived, " + message.getPayload().length + " bytes.");
        try {
            Sensors.RoverSensors roverSensors = Sensors.RoverSensors.parseFrom(message.getPayload());
            Log.v(TAG, "Parsed message " + roverSensors);
            if (roverSensors.hasAccel() && roverSensors.getAccel().hasX()) {
            }
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "Protobuf parse exception: " + e);
            throw e;
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.v(TAG, "Delivery complete.");
    }
}
