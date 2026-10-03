package com.example.loanwise;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.loanwise.LoginActivity;
import com.example.loanwise.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    private TextView tvProfileName, tvProfilePhone;
    private Button btnProfileLogout;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvProfileName = view.findViewById(R.id.tvProfileName);
        tvProfilePhone = view.findViewById(R.id.tvProfilePhone);
        btnProfileLogout = view.findViewById(R.id.btnProfileLogout);

        loadProfileData();

        btnProfileLogout.setOnClickListener(v -> {
            if (getContext() != null) {
                SessionManager.clearSession(getContext());
            }
            mAuth.signOut();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        return view;
    }

    private void loadProfileData() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String username = documentSnapshot.getString("username");
                        String email = documentSnapshot.getString("email");
                        String phone = documentSnapshot.getString("phoneNumber");
                        tvProfileName.setText(username != null ? username : "User Profile");
                        if (email != null && !email.trim().isEmpty()) {
                            tvProfilePhone.setText("Email: " + email);
                        } else if (phone != null && !phone.trim().isEmpty()) {
                            tvProfilePhone.setText("Phone: " + phone);
                        } else {
                            tvProfilePhone.setText("Email: N/A");
                        }
                    }
                });
    }
}
