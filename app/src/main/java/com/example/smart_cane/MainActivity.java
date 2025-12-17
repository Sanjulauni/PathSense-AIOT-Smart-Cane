package com.example.smart_cane;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvDeviceName, tvBattery, tvObstacleStatus;
    private Button btnConnect, btnEmergency, btnSettings, btnLogout;
    private ProgressBar progressBar, batteryProgress;
    private LinearLayout layoutConnected, layoutDisconnected;
    private ImageView ivStatusIndicator;
    private TextToSpeech textToSpeech;

    private BluetoothAdapter bluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupTextToSpeech();
        checkPermissions();
        setupListeners();
        registerReceivers();

        // Check if user is logged in
        checkLogin();
    }

    private void initializeViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvDeviceName = findViewById(R.id.tvDeviceName);
        tvBattery = findViewById(R.id.tvBattery);
        tvObstacleStatus = findViewById(R.id.tvObstacleStatus);

        btnConnect = findViewById(R.id.btnConnect);
        btnEmergency = findViewById(R.id.btnEmergency);
        btnSettings = findViewById(R.id.btnSettings);
        btnLogout = findViewById(R.id.btnLogout);

        progressBar = findViewById(R.id.progressBar);
        batteryProgress = findViewById(R.id.batteryProgress);

        layoutConnected = findViewById(R.id.layoutConnected);
        layoutDisconnected = findViewById(R.id.layoutDisconnected);

        ivStatusIndicator = findViewById(R.id.ivStatusIndicator);
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(0.8f);
            }
        });
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };

            List<String> permissionsNeeded = new ArrayList<>();
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(permission);
                }
            }

            if (!permissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsNeeded.toArray(new String[0]),
                        REQUEST_PERMISSIONS);
            } else {
                initializeBluetooth();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_PERMISSIONS);
            } else {
                initializeBluetooth();
            }
        }
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device does not support Bluetooth", Toast.LENGTH_SHORT).show();
            speak("Device does not support Bluetooth");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            checkPairedDevices();
        }
    }

    private void checkPairedDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        Set<BluetoothDevice> pairedDevices;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pairedDevices = bluetoothAdapter.getBondedDevices();
        } else {
            pairedDevices = bluetoothAdapter.getBondedDevices();
        }
        deviceList.clear();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName() != null && device.getName().contains("Cane")) {
                    deviceList.add(device);
                }
            }
        }

        if (deviceList.isEmpty()) {
            tvStatus.setText("No cane device paired");
            speak("No white cane device found. Please pair your device in Bluetooth settings");
        } else {
            tvDeviceName.setText(deviceList.get(0).getName());
            speak("Found " + deviceList.size() + " cane devices");
        }
    }

    private void setupListeners() {
        btnConnect.setOnClickListener(v -> {
            if (isConnected) {
                disconnectDevice();
            } else {
                connectToDevice();
            }
        });

        btnEmergency.setOnClickListener(v -> {
            showEmergencyDialog();
        });

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        btnLogout.setOnClickListener(v -> {
            logoutUser();
        });
    }

    private void connectToDevice() {
        if (deviceList.isEmpty()) {
            Toast.makeText(this, "No device available", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnConnect.setEnabled(false);

        // Simulate connection
        new android.os.Handler().postDelayed(() -> {
            progressBar.setVisibility(View.GONE);
            btnConnect.setEnabled(true);

            isConnected = true;
            updateConnectionUI(true);
            speak("Connected to white cane device");

            // Start simulated sensor updates
            startSensorUpdates();
        }, 2000);
    }

    private void disconnectDevice() {
        isConnected = false;
        updateConnectionUI(false);
        speak("Disconnected from white cane");
    }

    private void updateConnectionUI(boolean connected) {
        if (connected) {
            tvStatus.setText("Connected");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            btnConnect.setText("Disconnect");
            layoutConnected.setVisibility(View.VISIBLE);
            layoutDisconnected.setVisibility(View.GONE);
            ivStatusIndicator.setImageResource(R.drawable.status_circle);
        } else {
            tvStatus.setText("Disconnected");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
            btnConnect.setText("Connect");
            layoutConnected.setVisibility(View.GONE);
            layoutDisconnected.setVisibility(View.VISIBLE);
        }
    }

    private void startSensorUpdates() {
        // Simulate sensor data updates
        new android.os.Handler().postDelayed(() -> {
            if (isConnected) {
                // Simulate battery level
                int batteryLevel = 85;
                tvBattery.setText("Battery: " + batteryLevel + "%");
                batteryProgress.setProgress(batteryLevel);

                // Simulate obstacle detection
                simulateObstacleDetection();

                // Continue updates
                startSensorUpdates();
            }
        }, 3000);
    }

    private void simulateObstacleDetection() {
        double random = Math.random();
        if (random > 0.7) {
            tvObstacleStatus.setText("Obstacle detected ahead");
            tvObstacleStatus.setTextColor(ContextCompat.getColor(this, R.color.status_danger));
            speak("Obstacle detected ahead");
        } else if (random > 0.5) {
            tvObstacleStatus.setText("Clear path");
            tvObstacleStatus.setTextColor(ContextCompat.getColor(this, R.color.status_safe));
        } else {
            tvObstacleStatus.setText("Minor obstacles");
            tvObstacleStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning));
            speak("Minor obstacles detected");
        }
    }

    private void showEmergencyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Emergency Alert")
                .setMessage("Send emergency alert to contacts?")
                .setPositiveButton("Send", (dialog, which) -> {
                    speak("Emergency alert sent to contacts");
                    Toast.makeText(this, "Emergency alert sent", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);

        LocalBroadcastManager.getInstance(this).registerReceiver(
                sensorReceiver,
                new IntentFilter("SENSOR_DATA")
        );
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    isConnected = false;
                    updateConnectionUI(false);
                    speak("Bluetooth turned off");
                }
            }
        }
    };

    private final BroadcastReceiver sensorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String type = intent.getStringExtra("type");
            if ("OBSTACLE".equals(type)) {
                String distance = intent.getStringExtra("distance");
                String direction = intent.getStringExtra("direction");
                speak("Obstacle detected " + distance + " centimeters " + direction);
            } else if ("FALL".equals(type)) {
                speak("Fall detected! Sending emergency alert");
            }
        }
    };

    private void checkLogin() {
        SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        if (!isLoggedIn) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        }
    }

    private void logoutUser() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("isLoggedIn", false);
                    editor.apply();

                    speak("Logged out successfully");
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeBluetooth();
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                checkPairedDevices();
            } else {
                Toast.makeText(this, "Bluetooth is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        try {
            unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered
        }
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(sensorReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered
        }
    }
}