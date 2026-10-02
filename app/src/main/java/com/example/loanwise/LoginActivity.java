package com.example.loanwise;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.loanwise.models.UserModel;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilUsername, tilPhone, tilOtp;
    private Button btnSendOtp, btnVerifyOtp;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String verificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check if user is already logged in
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        tilUsername = findViewById(R.id.tilUsername);
        tilPhone = findViewById(R.id.tilPhone);
        tilOtp = findViewById(R.id.tilOtp);
        btnSendOtp = findViewById(R.id.btnSendOtp);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        progressBar = findViewById(R.id.progressBar);

        btnSendOtp.setOnClickListener(v -> sendVerificationOtp());
        btnVerifyOtp.setOnClickListener(v -> verifyOtpAndLogin());
    }

    private void sendVerificationOtp() {
        String username = tilUsername.getEditText().getText().toString().trim();
        String phoneNumber = tilPhone.getEditText().getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            tilUsername.setError("Please enter username");
            return;
        }
        tilUsername.setError(null);

        if (TextUtils.isEmpty(phoneNumber) || phoneNumber.length() < 10) {
            tilPhone.setError("Please enter a valid phone number with country code (e.g., +91...)");
            return;
        }
        tilPhone.setError(null);

        progressBar.setVisibility(View.VISIBLE);
        btnSendOtp.setEnabled(false);

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(mCallbacks)
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    signInWithPhoneAuthCredential(credential);
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {
                    progressBar.setVisibility(View.GONE);
                    btnSendOtp.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Verification Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onCodeSent(@NonNull String verId,
                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    progressBar.setVisibility(View.GONE);
                    verificationId = verId;

                    Toast.makeText(LoginActivity.this, "OTP Sent Successfully", Toast.LENGTH_SHORT).show();
                    tilOtp.setVisibility(View.VISIBLE);
                    btnVerifyOtp.setVisibility(View.VISIBLE);
                    btnSendOtp.setVisibility(View.GONE);
                    tilUsername.setEnabled(false);
                    tilPhone.setEnabled(false);
                }
            };

    private void verifyOtpAndLogin() {
        String otp = tilOtp.getEditText().getText().toString().trim();
        if (TextUtils.isEmpty(otp) || otp.length() < 6) {
            tilOtp.setError("Enter valid 6-digit OTP");
            return;
        }
        tilOtp.setError(null);

        progressBar.setVisibility(View.VISIBLE);
        btnVerifyOtp.setEnabled(false);

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        saveUserToFirestore();
                    } else {
                        btnVerifyOtp.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore() {
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();
        String username = tilUsername.getEditText().getText().toString().trim();
        String phoneNumber = mAuth.getCurrentUser().getPhoneNumber();
        if (phoneNumber == null) {
            phoneNumber = tilPhone.getEditText().getText().toString().trim();
        }

        UserModel userModel = new UserModel(userId, username, phoneNumber);

        db.collection("users").document(userId)
                .set(userModel)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(LoginActivity.this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
