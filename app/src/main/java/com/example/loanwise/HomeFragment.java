package com.example.loanwise;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView tvWelcomeHome, tvAvatar, tvViewAll, tvLoanTitle, tvLoanSubtitle,
            tvStatActiveLoan, tvStatEmi, tvStatOutstanding, tvTip, tvNoLoan;
    private View btnCalculateEmi, cardLoan, layoutLoanSection, flBell;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private final NumberFormat inr = NumberFormat.getInstance(new Locale("en", "IN"));
    private final DecimalFormat rateFormat = new DecimalFormat("0.##");

    private static final String DEFAULT_TIP =
            "You could save on interest with a smart prepayment. Check the Loans tab for a suggestion.";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        inr.setMaximumFractionDigits(0);

        tvWelcomeHome = view.findViewById(R.id.tvWelcomeHome);
        tvAvatar = view.findViewById(R.id.tvAvatar);
        tvViewAll = view.findViewById(R.id.tvViewAll);
        tvLoanTitle = view.findViewById(R.id.tvLoanTitle);
        tvLoanSubtitle = view.findViewById(R.id.tvLoanSubtitle);
        tvStatActiveLoan = view.findViewById(R.id.tvStatActiveLoan);
        tvStatEmi = view.findViewById(R.id.tvStatEmi);
        tvStatOutstanding = view.findViewById(R.id.tvStatOutstanding);
        tvTip = view.findViewById(R.id.tvTip);
        tvNoLoan = view.findViewById(R.id.tvNoLoan);
        btnCalculateEmi = view.findViewById(R.id.btnCalculateEmi);
        cardLoan = view.findViewById(R.id.cardLoan);
        layoutLoanSection = view.findViewById(R.id.layoutLoanSection);
        flBell = view.findViewById(R.id.flBell);

        // ----- Click actions -----
        btnCalculateEmi.setOnClickListener(v -> goToTab(R.id.nav_calculate));
        tvViewAll.setOnClickListener(v -> goToTab(R.id.nav_loan_details));
        cardLoan.setOnClickListener(v -> goToTab(R.id.nav_loan_details));
        tvAvatar.setOnClickListener(v -> goToTab(R.id.nav_profile));
        flBell.setOnClickListener(v ->
                Toast.makeText(getContext(), "No new notifications", Toast.LENGTH_SHORT).show());

        loadUserData();

        // Add any EMIs whose debit date has arrived, then load the loan card
        String resolvedUserId = SessionManager.getUserId(getContext());
        if (resolvedUserId != null) {
            EmiScheduler.postDueEmis(db, resolvedUserId,
                    count -> { if (isAdded()) loadLoanData(); });
        }

        return view;
    }

    /** Switches the bottom navigation tab (which also swaps the fragment). */
    private void goToTab(int menuItemId) {
        if (getActivity() == null) return;
        BottomNavigationView nav = getActivity().findViewById(R.id.bottomNavigationView);
        if (nav != null) nav.setSelectedItemId(menuItemId);
    }

    private void loadUserData() {
        final Context context = (getContext() != null) ? getContext() : getActivity();
        String userId = SessionManager.getUserId(context);
        if (userId == null) return;

        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    if (doc.exists()) {
                        String username = doc.getString("username");
                        if (username != null && !username.trim().isEmpty()) {
                            tvWelcomeHome.setText(username.trim());
                            tvAvatar.setText(initials(username));
                        }
                    } else {
                        String email = SessionManager.getUserEmail(context);
                        if (email != null && !email.trim().isEmpty()) {
                            String defaultName = email.split("@")[0];
                            tvWelcomeHome.setText(defaultName);
                            tvAvatar.setText(initials(defaultName));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    String email = SessionManager.getUserEmail(context);
                    if (email != null && !email.trim().isEmpty()) {
                        String defaultName = email.split("@")[0];
                        tvWelcomeHome.setText(defaultName);
                        tvAvatar.setText(initials(defaultName));
                    }
                });
    }

    private void loadLoanData() {
        final Context context = (getContext() != null) ? getContext() : getActivity();
        String userId = SessionManager.getUserId(context);
        if (userId == null) return;

        db.collection("loans").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;

                    if (!doc.exists()) {
                        layoutLoanSection.setVisibility(View.GONE);
                        tvNoLoan.setVisibility(View.VISIBLE);
                        tvTip.setText(DEFAULT_TIP);
                        return;
                    }

                    String loanType = doc.getString("loanType");
                    Double loanAmount = doc.getDouble("loanAmount");
                    Double outstanding = doc.getDouble("outstandingPrincipal");
                    Double rate = doc.getDouble("interestRate");
                    Double emi = doc.getDouble("monthlyEMI");
                    Long originalTenure = doc.getLong("originalTenure");
                    Long remainingTenure = doc.getLong("remainingTenure");
                    Double prepayment = doc.getDouble("selectedPrepayment");
                    Double saving = doc.getDouble("estimatedInterestSaving");
                    Long reduction = doc.getLong("estimatedTenureReduction");

                    String title = loanType != null ? loanType : "Active Loan";
                    long tenure = originalTenure != null ? originalTenure
                            : (remainingTenure != null ? remainingTenure : 0);
                    double outstandingValue = outstanding != null ? outstanding
                            : (loanAmount != null ? loanAmount : 0);

                    tvLoanTitle.setText(title);
                    tvLoanSubtitle.setText(rateFormat.format(rate != null ? rate : 0)
                            + "% p.a. · " + tenureText(tenure) + " tenure");
                    tvStatActiveLoan.setText(title);
                    tvStatEmi.setText("₹" + inr.format(emi != null ? emi : 0));
                    tvStatOutstanding.setText("₹" + inr.format(outstandingValue));

                    layoutLoanSection.setVisibility(View.VISIBLE);
                    tvNoLoan.setVisibility(View.GONE);

                    // Tip card: personalised if a prepayment was saved, otherwise generic
                    if (prepayment != null && prepayment > 0) {
                        tvTip.setText("Your planned prepayment of ₹" + inr.format(prepayment)
                                + " could save about ₹" + inr.format(saving != null ? saving : 0)
                                + " in interest and cut your tenure by "
                                + (reduction != null ? reduction : 0) + " months.");
                    } else {
                        tvTip.setText(DEFAULT_TIP);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Could not load loan: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** 240 -> "20 yrs", 18 -> "18 months" */
    private String tenureText(long months) {
        if (months >= 12 && months % 12 == 0) {
            long years = months / 12;
            return years + (years == 1 ? " yr" : " yrs");
        }
        return months + " months";
    }

    /** "Riya Sharma" -> "RS", "Riya" -> "R" */
    private String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.length() > 0 ? sb.toString() : "U";
    }
}