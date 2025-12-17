package com.example.smart_cane;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private CheckBox cbVoiceFeedback, cbVibration, cbHighContrast;
    private SeekBar sbVoiceSpeed, sbVoiceVolume;
    private TextView tvVoiceSpeed, tvVoiceVolume;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initializeViews();
        loadSettings();
        setupListeners();
    }

    private void initializeViews() {
        cbVoiceFeedback = findViewById(R.id.cbVoiceFeedback);
        cbVibration = findViewById(R.id.cbVibration);
        cbHighContrast = findViewById(R.id.cbHighContrast);
        sbVoiceSpeed = findViewById(R.id.sbVoiceSpeed);
        sbVoiceVolume = findViewById(R.id.sbVoiceVolume);
        tvVoiceSpeed = findViewById(R.id.tvVoiceSpeed);
        tvVoiceVolume = findViewById(R.id.tvVoiceVolume);
        btnSave = findViewById(R.id.btnSave);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);

        cbVoiceFeedback.setChecked(prefs.getBoolean("voiceFeedback", true));
        cbVibration.setChecked(prefs.getBoolean("vibration", true));
        cbHighContrast.setChecked(prefs.getBoolean("highContrast", false));

        int speed = prefs.getInt("voiceSpeed", 50);
        int volume = prefs.getInt("voiceVolume", 80);

        sbVoiceSpeed.setProgress(speed);
        sbVoiceVolume.setProgress(volume);

        updateSpeedText(speed);
        updateVolumeText(volume);
    }

    private void setupListeners() {
        sbVoiceSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSpeedText(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbVoiceVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeText(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void updateSpeedText(int progress) {
        tvVoiceSpeed.setText("Voice Speed: " + progress + "%");
    }

    private void updateVolumeText(int progress) {
        tvVoiceVolume.setText("Voice Volume: " + progress + "%");
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("voiceFeedback", cbVoiceFeedback.isChecked());
        editor.putBoolean("vibration", cbVibration.isChecked());
        editor.putBoolean("highContrast", cbHighContrast.isChecked());
        editor.putInt("voiceSpeed", sbVoiceSpeed.getProgress());
        editor.putInt("voiceVolume", sbVoiceVolume.getProgress());

        editor.apply();
        finish();
    }
}