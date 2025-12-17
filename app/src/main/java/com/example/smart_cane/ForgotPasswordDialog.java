package com.example.smart_cane;

import android.app.Dialog;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.smart_cane.R;

public class ForgotPasswordDialog extends Dialog {

    private EditText etEmail;
    private Button btnSubmit, btnCancel;
    private ProgressBar progressBar;
    private TextToSpeech textToSpeech;
    private ImageView ivClose;
    private TextView tvDialogTitle;

    public ForgotPasswordDialog(Context context, TextToSpeech tts) {
        super(context);
        this.textToSpeech = tts;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_forgot_password);

        initializeViews();
        setupListeners();

        speak("Forgot password dialog. Enter your email to reset password");
    }

    private void initializeViews() {
        etEmail = findViewById(R.id.etDialogEmail);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnCancel = findViewById(R.id.btnCancel);
        progressBar = findViewById(R.id.dialogProgressBar);
        ivClose = findViewById(R.id.ivClose);
        tvDialogTitle = findViewById(R.id.tvDialogTitle);

        // Accessibility
        etEmail.setContentDescription("Enter your registered email address");
        btnSubmit.setContentDescription("Submit button to send reset link");
    }

    private void setupListeners() {
        btnSubmit.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                speak("Please enter your email address");
                etEmail.setError("Email required");
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            speak("Sending reset instructions to your email");

            // Simulate API call
            new android.os.Handler().postDelayed(() -> {
                progressBar.setVisibility(View.GONE);
                speak("Password reset instructions sent to your email");
                Toast.makeText(getContext(), "Reset email sent", Toast.LENGTH_SHORT).show();
                dismiss();
            }, 1500);
        });

        btnCancel.setOnClickListener(v -> {
            speak("Closing forgot password dialog");
            dismiss();
        });

        ivClose.setOnClickListener(v -> dismiss());

        etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                speak("Email field selected for password reset");
            }
        });
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
}