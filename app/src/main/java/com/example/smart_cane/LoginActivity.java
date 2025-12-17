package com.example.smart_cane;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnSignup;
    private ProgressBar progressBar;
    private TextToSpeech textToSpeech;
    private CheckBox cbRememberMe;
    private TextView tvForgotPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initializeViews();
        setupTextToSpeech();
        setupListeners();
        loadSavedCredentials();
    }

    private void initializeViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnSignup = findViewById(R.id.btnSignup);
        progressBar = findViewById(R.id.progressBar);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(0.8f);
            }
        });
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        btnSignup.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });

        tvForgotPassword.setOnClickListener(v -> {
            Toast.makeText(this, "Reset password feature coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadSavedCredentials() {
        SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);
        boolean rememberMe = prefs.getBoolean("rememberMe", false);

        if (rememberMe) {
            String email = prefs.getString("email", "");
            String password = prefs.getString("password", "");

            etEmail.setText(email);
            etPassword.setText(password);
            cbRememberMe.setChecked(true);
        }
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email required");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password required");
            etPassword.requestFocus();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        // Simulate login process
        new android.os.Handler().postDelayed(() -> {
            progressBar.setVisibility(View.GONE);
            btnLogin.setEnabled(true);

            if (isValidCredentials(email, password)) {
                saveLoginState(email, password);
                speak("Login successful");

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("USER_EMAIL", email);
                startActivity(intent);
                finish();
            } else {
                speak("Invalid credentials");
                Toast.makeText(LoginActivity.this, "Invalid email or password", Toast.LENGTH_SHORT).show();
            }
        }, 1500);
    }

    private boolean isValidCredentials(String email, String password) {
        // For demo - accept any valid email and password >= 6 chars
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                && password.length() >= 6;
    }

    private void saveLoginState(String email, String password) {
        SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("isLoggedIn", true);
        editor.putString("email", email);

        if (cbRememberMe.isChecked()) {
            editor.putBoolean("rememberMe", true);
            editor.putString("password", password);
        } else {
            editor.putBoolean("rememberMe", false);
            editor.remove("password");
        }

        editor.apply();
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}