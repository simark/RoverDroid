package ca.simark.roverdroid;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.MemoryFile;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
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

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

import ca.simark.roverdroid.proto.Controls;
import ca.simark.roverdroid.proto.Sensors;

public class ControlPanelActivity extends AppCompatActivity implements MqttCallback {

    MqttAndroidClient fClient;
    String fHost;
    int fPort;

    public static final String TAG = ControlPanelActivity.class.getSimpleName();

    SeekBar steer_seek_bar, power_seek_bar;
    TextView power_level_text_view;
    Button stop_button, straight_button;
    TextView text_view_accel_x, text_view_accel_y, text_view_accel_z;
    TextView text_view_gyro_x, text_view_gyro_y, text_view_gyro_z;
    TextView text_view_compass_x, text_view_compass_y, text_view_compass_z;
    TextView text_view_battery;

    ImageView image_view;

    Button button_toggle_sub_sensors;
    boolean subscribed_to_sensors = false;
    Button button_toggle_camera;
    boolean subscribed_to_camera = false;


    ProgressDialog fProgressDialog;
    private Controls.RoverControls.Builder fControlsBuilder;


    private int lastLeft = Integer.MAX_VALUE;
    private int lastRight = Integer.MAX_VALUE;

    private static final String SENSORS_TOPIC = "/polarsys-rover/sensors";
    private static final String FRONT_CAMERA_TOPIC = "/polarsys-rover/front-camera";

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
                    dismissProgressDialog();
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

    /* Convert the value of a slider [0, 200] to [-100, 100].  */
    int seekbarToValue(SeekBar seekbar) {
        return seekbar.getProgress() - seekbar.getMax() / 2;
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

        power_seek_bar = (SeekBar) findViewById(R.id.power_seek_bar);
        steer_seek_bar = (SeekBar) findViewById(R.id.steer_seek_bar);
        power_level_text_view = (TextView) findViewById(R.id.power_level_text_view);

        power_seek_bar.setOnSeekBarChangeListener(new DefaultOnSeeKBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                power_level_text_view.setText(String.format(Locale.getDefault(), "%d %%",
                        seekbarToValue(power_seek_bar)));

                computerMotorLevels();
            }
        });

        steer_seek_bar.setOnSeekBarChangeListener(new DefaultOnSeeKBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                computerMotorLevels();
            }
        });

        stop_button = (Button) findViewById(R.id.stop_button);
        straight_button = (Button) findViewById(R.id.straight_button);

        stop_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                power_seek_bar.setProgress(power_seek_bar.getMax() / 2);
            }
        });

        straight_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                steer_seek_bar.setProgress(steer_seek_bar.getMax() / 2);
            }
        });

        text_view_accel_x = (TextView) findViewById(R.id.text_view_accel_x);
        text_view_accel_y = (TextView) findViewById(R.id.text_view_accel_y);
        text_view_accel_z = (TextView) findViewById(R.id.text_view_accel_z);

        text_view_gyro_x = (TextView) findViewById(R.id.text_view_gyro_x);
        text_view_gyro_y = (TextView) findViewById(R.id.text_view_gyro_y);
        text_view_gyro_z = (TextView) findViewById(R.id.text_view_gyro_z);

        text_view_compass_x = (TextView) findViewById(R.id.text_view_compass_x);
        text_view_compass_y = (TextView) findViewById(R.id.text_view_compass_y);
        text_view_compass_z = (TextView) findViewById(R.id.text_view_compass_z);

        text_view_battery = (TextView) findViewById(R.id.text_view_battery);

        button_toggle_sub_sensors = (Button) findViewById(R.id.button_toggle_sub_sensors);
        button_toggle_sub_sensors.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (subscribed_to_sensors) {
                    doUnsubscribeSensors();
                    button_toggle_sub_sensors.setText("Subscribe to sensors");
                } else {
                    doSubscribeSensors();
                    button_toggle_sub_sensors.setText("Unsubscribe from sensors");
                }
                subscribed_to_sensors = !subscribed_to_sensors;
            }
        });

        image_view = (ImageView) findViewById(R.id.front_camera);
        button_toggle_camera = (Button) findViewById(R.id.button_toggle_camera);
        button_toggle_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (subscribed_to_camera) {
                    doUnsubscribeCamera();
                    button_toggle_camera.setText("Subscribe to camera");
                } else {
                    doSubscribeCamera();
                    button_toggle_camera.setText("Unsubscribe from camera");
                }
                subscribed_to_camera = !subscribed_to_camera;
            }
        });

    }

    private void computerMotorLevels() {
        int steer = seekbarToValue(steer_seek_bar);
        int power = seekbarToValue(power_seek_bar);

        int abs_max_steer = steer_seek_bar.getMax() / 2;

        // Steer ration on a scale of 1 (no steering) to 0 (max steering)
        double steer_ratio = 1 - (Math.abs(steer) / (double) abs_max_steer);

        // Double the scale, so it's from 2 (no steering) to 0 (max steering)
        steer_ratio *= 2;

        // Move the scale, so it's from 1 (no steering) to -1 (max steering)
        steer_ratio -= 1;

        int left = power, right = power;

        if (power > 0) {
            if (steer < 0) {
                // Turning left
                left = (int) (right * steer_ratio);
            } else if (steer > 0) {
                right = (int) (left * steer_ratio);
            }
        } else if (power < 0) {
            if (steer < 0) {
                // Turning left
                left = (int) (right * steer_ratio);
            } else if (steer > 0) {
                // Turning right
                right = (int) (left * steer_ratio);
            }
        } else {
            // Straight, keep both sides at "power".
        }

        Log.d(TAG, "steer = " + steer + " power = " + power);
        Log.d(TAG, "left = " + left + " right = " + right);
        Log.d(TAG, "Ratio = " + steer_ratio);

        sendMotorLevels(left, right);
    }

    private void sendMotorLevels(int left, int right) {
        if (left < -100 || left > 100) {
            Log.e(TAG, "Internal error: invalid value for left motor: " + left);
            return;
        }

        if (right < -100 || right > 100) {
            Log.e(TAG, "Internal error: invalid value for right motor: " + right);
            return;
        }

        if (left == lastLeft && right == lastRight) {
            return;
        }

        lastLeft = left;
        lastRight = right;

        fControlsBuilder.setLeft(left);
        fControlsBuilder.setRight(right);
        Controls.RoverControls msg = fControlsBuilder.build();

        try {
            fClient.publish("/polarsys-rover/controls", msg.toByteArray(), 0, false, null, new IMqttActionListener() {
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

    private void doSubscribe(final String topic) {
        try {
            fClient.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "Subscribe success to " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "We failed to subscribe to " + topic + ": " + exception);
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Caught exception while subscribing to " + topic + ": " + e);
        }
    }

    private void doUnsubscribe(final String topic) {
        try {
            fClient.unsubscribe(topic, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "Unsubscribe success from " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "We failed to unsubscribe from " + topic + ": " + exception);
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Caught exception while unsubscribing from " + topic + ": " + e);
        }
    }

    private void doSubscribeSensors() {
        doSubscribe(SENSORS_TOPIC);
    }

    private void doUnsubscribeSensors() {
        doUnsubscribe(SENSORS_TOPIC);
    }

    private void doSubscribeCamera() {
        doSubscribe(FRONT_CAMERA_TOPIC);
    }

    private void doUnsubscribeCamera() {
        doUnsubscribe(FRONT_CAMERA_TOPIC);
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.e(TAG, "Connection lost!");
        Log.e(TAG, "cause: " + cause);
    }

    private void setTextViewVal(TextView view, float value) {
        view.setText(String.format(Locale.getDefault(), "%.2f", value));
    }

    private void processSensorsMessage(MqttMessage message) throws InvalidProtocolBufferException {
        try {
            Sensors.RoverSensors roverSensors = Sensors.RoverSensors.parseFrom(message.getPayload());
            Log.v(TAG, "Parsed message " + roverSensors);
            if (roverSensors.hasAccel()) {
                Sensors.Acceleration accel = roverSensors.getAccel();

                if (accel.hasX()) {
                    setTextViewVal(text_view_accel_x, accel.getX());
                }

                if (accel.hasY()) {
                    setTextViewVal(text_view_accel_y, accel.getY());
                }

                if (accel.hasZ()) {
                    setTextViewVal(text_view_accel_z, accel.getZ());
                }
            }

            if (roverSensors.hasGyro()) {
                Sensors.Gyro gyro = roverSensors.getGyro();

                if (gyro.hasX()) {
                    setTextViewVal(text_view_gyro_x, gyro.getX());
                }

                if (gyro.hasY()) {
                    setTextViewVal(text_view_gyro_y, gyro.getY());
                }

                if (gyro.hasZ()) {
                    setTextViewVal(text_view_gyro_z, gyro.getZ());
                }
            }

            if (roverSensors.hasCompass()) {
                Sensors.Compass compass = roverSensors.getCompass();

                if (compass.hasX()) {
                    setTextViewVal(text_view_compass_x, compass.getX());
                }

                if (compass.hasY()) {
                    setTextViewVal(text_view_compass_y, compass.getY());
                }

                if (compass.hasZ()) {
                    setTextViewVal(text_view_compass_z, compass.getZ());
                }
            }

            if (roverSensors.hasBatteryVoltage()) {
                setTextViewVal(text_view_battery, roverSensors.getBatteryVoltage() / 1000.0f);
            }
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "Protobuf parse exception: " + e);
            throw e;
        }
    }

    File tempFile;
    int n = 0;

    private void processFrontCameraMessage(MqttMessage message) throws IOException {
        Log.d(TAG, "New image " + message.getPayload().length);
        n++;

        Log.d(TAG, "Number " + n);

        if (tempFile == null) {
            File outputDir = this.getCacheDir();
            tempFile = File.createTempFile("frontcam", "jpg", outputDir);
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(message.getPayload(), 0, message.getPayload().length);
        image_view.setImageBitmap(bitmap);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.v(TAG, "Message arrived on topic " + topic + ", " + message.getPayload().length + " bytes.");

        switch (topic) {
            case SENSORS_TOPIC:
                processSensorsMessage(message);
                break;
            case FRONT_CAMERA_TOPIC:
                processFrontCameraMessage(message);
                break;
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.v(TAG, "Delivery complete.");
    }
}
