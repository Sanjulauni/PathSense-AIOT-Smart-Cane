package com.example.smart_cane;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnOutdoorNavigation, btnIndoorNavigation;
    private Button btnSettings, btnLogout;

    private TextView tvWelcome;

    private TTSManager ttsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initializeViews();

        ttsManager = new TTSManager(this);

        setupListeners();

        startSmartCaneService();

        ttsManager.speak("Main menu. Double tap options to select.");

    }

    private void initializeViews() {

        btnOutdoorNavigation = findViewById(R.id.btnOutdoorNavigation);
        btnIndoorNavigation = findViewById(R.id.btnIndoorNavigation);
        btnSettings = findViewById(R.id.btnSettings);
        btnLogout = findViewById(R.id.btnLogout);

        tvWelcome = findViewById(R.id.tvWelcome);

        String email = getIntent().getStringExtra("USER_EMAIL");

        if (email != null) {

            tvWelcome.setText("Welcome " + email);

        }

    }

    private void setupListeners() {

        DoubleTapHandler.setupDoubleTap(btnOutdoorNavigation, () -> {

            ttsManager.speak("Opening outdoor navigation");

            startActivity(new Intent(MainActivity.this, OutdoorNavigationActivity.class));

        });

        DoubleTapHandler.setupDoubleTap(btnIndoorNavigation, () -> {

            ttsManager.speak("Opening indoor navigation");

            startActivity(new Intent(MainActivity.this, IndoorNavigationActivity.class));

        });

        DoubleTapHandler.setupDoubleTap(btnSettings, () -> {

            ttsManager.speak("Opening settings");

            startActivity(new Intent(MainActivity.this, SettingsActivity.class));

        });

        DoubleTapHandler.setupDoubleTap(btnLogout, () -> {

            ttsManager.speak("Logging out");

            startActivity(new Intent(MainActivity.this, LoginActivity.class));

            finish();

        });

    }

    private void startSmartCaneService() {

        Intent serviceIntent = new Intent(this, WifiUdpService.class);

        startForegroundService(serviceIntent);

    }

    @Override
    protected void onDestroy() {

        if (ttsManager != null) {

            ttsManager.shutdown();

        }

        super.onDestroy();

    }
}