package com.example.smart_cane;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.Locale;
import java.util.UUID;

public class BluetoothService extends Service {

    private final IBinder binder = new LocalBinder();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private TextToSpeech textToSpeech;

    // UUIDs for White Cane BLE characteristics (replace with your device's UUIDs)
    private static final UUID SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    // Connection state
    private int connectionState = STATE_DISCONNECTED;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // Device address
    private String deviceAddress;

    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeBluetooth();
        setupTextToSpeech();
        registerReceiver();
    }

    private void initializeBluetooth() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is not enabled", Toast.LENGTH_SHORT).show();
            speak("Please enable Bluetooth to connect to your white cane");
        }
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, filter);
    }

    public boolean connect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            Log.w("BluetoothService", "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Reuse existing connection
        if (deviceAddress != null && address.equals(deviceAddress) && bluetoothGatt != null) {
            if (bluetoothGatt.connect()) {
                connectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        BluetoothDevice device;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w("BluetoothService", "BLUETOOTH_CONNECT permission not granted");
                return false;
            }
        }
        device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w("BluetoothService", "Device not found. Unable to connect.");
            return false;
        }

        // Connect to GATT server
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        }
        deviceAddress = address;
        connectionState = STATE_CONNECTING;

        speak("Connecting to white cane device");
        return true;
    }

    public void disconnect() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.w("BluetoothService", "BluetoothAdapter not initialized");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w("BluetoothService", "BLUETOOTH_CONNECT permission not granted");
                return;
            }
        }
        bluetoothGatt.disconnect();
    }

    public void sendCommand(String command) {
        if (bluetoothGatt == null) {
            Log.e("BluetoothService", "BluetoothGatt not initialized");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e("BluetoothService", "BLUETOOTH_CONNECT permission not granted");
                return;
            }
        }

        BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
        if (service == null) {
            Log.e("BluetoothService", "Service not found");
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
        if (characteristic == null) {
            Log.e("BluetoothService", "Characteristic not found");
            return;
        }

        characteristic.setValue(command.getBytes());
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction = null;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = "ACTION_GATT_CONNECTED";
                connectionState = STATE_CONNECTED;
                speak("Connected to white cane");

                // Discover services
                bluetoothGatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = "ACTION_GATT_DISCONNECTED";
                connectionState = STATE_DISCONNECTED;
                speak("Disconnected from white cane");
            }
            
            if (intentAction != null) {
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate("ACTION_GATT_SERVICES_DISCOVERED");
                speak("White cane services discovered");
            } else {
                Log.w("BluetoothService", "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate("ACTION_DATA_AVAILABLE", characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate("ACTION_DATA_AVAILABLE", characteristic);

            // Process sensor data from white cane
            String data = new String(characteristic.getValue());
            processSensorData(data);
        }
    };

    private void processSensorData(String data) {
        // Parse sensor data from white cane
        // Format: "OBSTACLE:distance:direction" or "FALL:true" or "BUTTON:emergency"

        if (data.contains("OBSTACLE")) {
            String[] parts = data.split(":");
            if (parts.length >= 3) {
                String distance = parts[1];
                String direction = parts[2];
                speak("Obstacle detected " + distance + " centimeters " + direction);

                // Send broadcast to MainActivity
                Intent intent = new Intent("SENSOR_DATA");
                intent.putExtra("type", "OBSTACLE");
                intent.putExtra("distance", distance);
                intent.putExtra("direction", direction);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            }
        } else if (data.contains("FALL")) {
            speak("Fall detected! Sending emergency alert");

            Intent intent = new Intent("SENSOR_DATA");
            intent.putExtra("type", "FALL");
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        } else if (data.contains("BUTTON")) {
            speak("Emergency button pressed");

            Intent intent = new Intent("SENSOR_DATA");
            intent.putExtra("type", "EMERGENCY");
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        intent.putExtra("DATA", new String(characteristic.getValue()));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    speak("Bluetooth turned off");
                } else if (state == BluetoothAdapter.STATE_ON) {
                    speak("Bluetooth turned on");
                }
            }
        }
    };

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private void close() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        try {
            unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered
        }
        close();
    }
}