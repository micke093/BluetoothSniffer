package com.example.bluetoothsniffer;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

/**
 * This is where we manage the BLE device and the corresponding services, characteristics et c.
 * <p>
 * NB: In this simple example there is no other way to turn off notifications than to
 * leave the activity (the BluetoothGatt is disconnected and closed in activity.onStop).
 */
public class DeviceActivity extends AppCompatActivity {

    /**
     * Documentation on UUID:s and such for services on a BBC Micro:bit.
     * Characteristics et c. are found at
     * https://lancaster-university.github.io/microbit-docs/resources/bluetooth/bluetooth_profile.html
     */
    // Below: gui stuff...
    private TextView mDeviceView;
    private TextView mDataView;

    public static final UUID ACCELEROMETER_SERVICE_UUID =
            UUID.fromString("E95D0753-251D-470A-A062-FA1922DFA9A8");
    public static final UUID ACCELEROMETER_DATA_CHARACTERISTIC_UUID =
            UUID.fromString("E95DCA4B-251D-470A-A062-FA1922DFA9A8");
    public static final UUID ACCELEROMETER_PERIOD_CHARACTERISTIC_UUID =
            UUID.fromString("E95DFB24-251D-470A-A062-FA1922DFA9A8");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothDevice mConnectedDevice = null;
    private BluetoothGatt mBluetoothGatt = null;
    private BluetoothGattService mAccelerometerService = null;

    private Handler mHandler;

    @Override
    protected void onStart() {
        super.onStart();
        mConnectedDevice = ConnectedDevice.getInstance();
        if (mConnectedDevice != null) {
            mDeviceView.setText(mConnectedDevice.toString());
            connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
        }
        ConnectedDevice.removeInstance();
        mConnectedDevice = null;

        finish();
    }

    private void connect() {
        if (mConnectedDevice != null) {
            // register call backs for bluetooth gatt
            mBluetoothGatt = mConnectedDevice.connectGatt(this, false, mBtGattCallback);
            Log.i("connect", "connctGatt called");
        }
    }

    /**
     * Callbacks for bluetooth gatt changes/updates
     * The documentation is not clear, but (some of?) the callback methods seems to
     * be executed on a worker thread - hence use a Handler when updating the ui.
     */
    private BluetoothGattCallback mBtGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mBluetoothGatt = gatt;
                gatt.discoverServices();
                mHandler.post(new Runnable() {
                    public void run() {
                        mDataView.setText("Connected");
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // close connection and display info in ui
                mBluetoothGatt = null;
                mHandler.post(new Runnable() {
                    public void run() {
                        mDataView.setText("Disconnected");
                    }
                });
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                // debug, list services
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    String uuid = service.getUuid().toString();
                    Log.i("service", uuid);
                }

                /*
                 * Get the accelerometer sensor service
                 */
                mAccelerometerService = gatt.getService(ACCELEROMETER_SERVICE_UUID);
                // debug, list characteristics for detected service
                if (mAccelerometerService != null) {
                    List<BluetoothGattCharacteristic> characteristics =
                            mAccelerometerService.getCharacteristics();
                    for (BluetoothGattCharacteristic chara : characteristics) {
                        Log.i("characteristic", chara.getUuid().toString());
                    }

                    //BTDeviceArrayAdapter btDeviceArrayAdapter = new BTDeviceArrayAdapter(this, );

                    /*
                     * Enable notifications on accelerometer data
                     * First: call setCharacteristicNotification
                     */
                    BluetoothGattCharacteristic accelerometerDataChara =
                            mAccelerometerService.getCharacteristic(ACCELEROMETER_DATA_CHARACTERISTIC_UUID);
                    gatt.setCharacteristicNotification(accelerometerDataChara, true);

                    /*
                     * Second: set enable notification
                     * (why isn't this done by setCharacteristicNotification - a flaw in the API?)
                     */
                    BluetoothGattDescriptor descriptor =
                            accelerometerDataChara.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor); // callback: onDescriptorWrite

                } else {
                    mHandler.post(new Runnable() {
                        public void run() {
                            showToast("Acc-data charcterisitc not found");
                        }
                    });
                }
            }
        }

        @Override
        public void onDescriptorWrite(final BluetoothGatt gatt, BluetoothGattDescriptor
                descriptor, int status) {
            Log.i("onDescriptorWrite", "descriptor " + descriptor.getUuid());
            Log.i("onDescriptorWrite", "status " + status);
//            mHandler.post(new Runnable() {
//                public void run() {
//                    enableAccelerometerDataNotifications(gatt);
//                }
//            });
            if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid()) &&
                    status == BluetoothGatt.GATT_SUCCESS) {

                mHandler.post(new Runnable() {
                    public void run() {
                        showToast("Acc-data notifications enabled");
                        mDeviceView.setText("Accelerometer service");
                    }
                });
            }
        }

        /**
         * Call back called on characteristic changes, e.g. when a data value is changed.
         * This is where we receive notifications on updates of accelerometer data.
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic) {
            Log.i("onCharacteristicChanged", characteristic.getUuid().toString());
            // TODO: check which service and characteristic caused this call (in this simple
            // example we assume it's the accelerometer sensor)
            BluetoothGattCharacteristic irDataConfig =
                    mAccelerometerService.getCharacteristic(ACCELEROMETER_DATA_CHARACTERISTIC_UUID);
            final byte[] value = irDataConfig.getValue();
            // update ui
            mHandler.post(new Runnable() {
                public void run() {
                   /// float[] acc = getAccXyz(value);
                ///    mDataView.setText(String.format("( %.2f, %.2f, %.2f )", acc[0], acc[1], acc[2]));
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            Log.i("onCharacteristicWrite", characteristic.getUuid().toString());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.getUuid().toString());
        }
    };

    // raw acc data to value in "G"
    // refer to  documentation on Micro:bit
    /*
    private float[] getAccXyz(byte[] sensorValue) {
        int x = (sensorValue[1] << 8) + sensorValue[0]; // usinged 16 bit int, little endian
        int y = (sensorValue[3] << 8) + sensorValue[2];
        int z = (sensorValue[5] << 8) + sensorValue[4];
        return new float[]{
                convertAccRawToG(x),
                convertAccRawToG(y),
                convertAccRawToG(z)
        };
    }
    */

    // raw acc data to value in "G"
    private float convertAccRawToG(int value) {
        return value * 1.0F / (1000.0F); // scale factor, refer to  documentation on Micro:bit
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        mDeviceView = findViewById(R.id.deviceView);
        mDataView = findViewById(R.id.dataView);
        mHandler = new Handler();
    }


    protected void showToast(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        toast.show();
    }
}
