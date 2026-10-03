package com.example.loanwise;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.loanwise.models.UserModel;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilOtp;
    private Button btnSendOtp, btnVerifyOtp;
    private TextView tvCooldown, tvServerConfig;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private CountDownTimer countDownTimer;
    private String pendingEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check if user already has an active session
        if (SessionManager.isLoggedIn(this)) {
            proceedToMain();
            return;
        }

        tilEmail = findViewById(R.id.tilEmail);
        tilOtp = findViewById(R.id.tilOtp);
        btnSendOtp = findViewById(R.id.btnSendOtp);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        tvCooldown = findViewById(R.id.tvCooldown);
        tvServerConfig = findViewById(R.id.tvServerConfig);
        progressBar = findViewById(R.id.progressBar);

        btnSendOtp.setOnClickListener(v -> handleSendOtp());
        btnVerifyOtp.setOnClickListener(v -> handleVerifyOtp());
        tvServerConfig.setOnClickListener(v -> showServerConfigDialog());
    }

    private void handleSendOtp() {
        String email = (tilEmail.getEditText() != null)
                ? tilEmail.getEditText().getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid email address");
            return;
        }
        tilEmail.setError(null);
        pendingEmail = email;

        progressBar.setVisibility(View.VISIBLE);
        btnSendOtp.setEnabled(false);

        AuthApiClient.sendOtp(this, email, new AuthApiClient.SendOtpListener() {
            @Override
            public void onSuccess(String message) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, "OTP sent successfully", Toast.LENGTH_SHORT).show();

                // Reveal OTP input and verification controls
                tilOtp.setVisibility(View.VISIBLE);
                btnVerifyOtp.setVisibility(View.VISIBLE);
                btnVerifyOtp.setEnabled(true);
                tilEmail.setEnabled(false);

                // Start 60-second cooldown timer
                startCooldownTimer(60);
            }

            @Override
            public void onCooldown(String message, int secondsRemaining) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, "Please wait before requesting another OTP", Toast.LENGTH_SHORT).show();
                startCooldownTimer(secondsRemaining > 0 ? secondsRemaining : 60);
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                btnSendOtp.setEnabled(true);
                Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleVerifyOtp() {
        String otp = (tilOtp.getEditText() != null)
                ? tilOtp.getEditText().getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(otp) || otp.length() != 6) {
            tilOtp.setError("Enter the OTP");
            Toast.makeText(this, "Enter the OTP", Toast.LENGTH_SHORT).show();
            return;
        }
        tilOtp.setError(null);

        progressBar.setVisibility(View.VISIBLE);
        btnVerifyOtp.setEnabled(false);

        AuthApiClient.verifyOtp(this, pendingEmail, otp, new AuthApiClient.VerifyOtpListener() {
            @Override
            public void onSuccess(String message, String token, String email) {
                Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();

                // Save secure JWT session
                SessionManager.saveSession(LoginActivity.this, token, email);

                // Connect to Firebase session to preserve existing Firestore functionality
                syncWithFirebaseAndProceed(email);
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                btnVerifyOtp.setEnabled(true);

                if (errorMessage.toLowerCase().contains("expired")) {
                    tilOtp.setError("OTP expired");
                    Toast.makeText(LoginActivity.this, "OTP expired", Toast.LENGTH_SHORT).show();
                } else if (errorMessage.toLowerCase().contains("too many attempts") ||
                           errorMessage.toLowerCase().contains("attempts")) {
                    tilOtp.setError("Too many attempts");
                    btnVerifyOtp.setEnabled(false);
                    Toast.makeText(LoginActivity.this, "Too many attempts", Toast.LENGTH_LONG).show();
                } else {
                    tilOtp.setError("Invalid OTP");
                    Toast.makeText(LoginActivity.this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void syncWithFirebaseAndProceed(String email) {
        String stableUserId = SessionManager.getUserId(this);
        if (mAuth.getCurrentUser() != null) {
            saveUserToFirestore(mAuth.getCurrentUser().getUid(), email);
            return;
        }

        // Establish Firebase session in background if supported; fallback to stable session user ID
        mAuth.signInAnonymously().addOnCompleteListener(task -> {
            String uid = (task.isSuccessful() && mAuth.getCurrentUser() != null)
                    ? mAuth.getCurrentUser().getUid()
                    : stableUserId;
            SessionManager.saveSession(this, SessionManager.getAuthToken(this), email, uid);
            saveUserToFirestore(uid, email);
        });
    }

    private void saveUserToFirestore(String userId, String email) {
        db.collection("users").document(userId).get().addOnSuccessListener(documentSnapshot -> {
            String username = (documentSnapshot.exists() && documentSnapshot.getString("username") != null)
                    ? documentSnapshot.getString("username")
                    : email.split("@")[0];

            UserModel userModel = new UserModel(userId, username, email, "");
            db.collection("users").document(userId).set(userModel)
                    .addOnCompleteListener(t -> {
                        progressBar.setVisibility(View.GONE);
                        proceedToMain();
                    });
        }).addOnFailureListener(e -> {
            UserModel userModel = new UserModel(userId, email.split("@")[0], email, "");
            db.collection("users").document(userId).set(userModel)
                    .addOnCompleteListener(t -> {
                        progressBar.setVisibility(View.GONE);
                        proceedToMain();
                    });
        });
    }

    private void startCooldownTimer(int seconds) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        btnSendOtp.setEnabled(false);
        tvCooldown.setVisibility(View.VISIBLE);

        countDownTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000;
                tvCooldown.setText("Resend OTP in " + sec + "s");
            }

            @Override
            public void onFinish() {
                tvCooldown.setVisibility(View.GONE);
                btnSendOtp.setEnabled(true);
                btnSendOtp.setText("Resend OTP");
            }
        }.start();
    }

    private void showServerConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Backend Server Configuration");
        builder.setMessage("Configure the authentication server URL.\nDefault production: https://loan-wise-sigma.vercel.app");

        final EditText input = new EditText(this);
        input.setText(BackendConfig.getBaseUrl(this));
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newUrl = input.getText().toString().trim();
            if (!TextUtils.isEmpty(newUrl)) {
                BackendConfig.setBaseUrl(this, newUrl);
                Toast.makeText(this, "Server URL updated", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNeutralButton("Reset Default", (dialog, which) -> {
            BackendConfig.resetToDefault(this);
            Toast.makeText(this, "Reset to default: " + BackendConfig.DEFAULT_PRODUCTION_URL, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void proceedToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
