package com.example.loanwise.fragments;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.loanwise.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LoanDetailsFragment extends Fragment {

    private TextView tabLoanDetails, tabHistory, tvRepaidPercent, tvDetailLoanType, tvDetailBank,
            tvDetailRepaidText, tvDetailLoanAmount, tvDetailInterestRate, tvDetailTenure,
            tvDetailEMI, tvDetailTotalInterest, tvDetailTotalAmount, tvDetailOutstanding,
            tvDetailPrepayment, tvHistoryEmpty;
    private ProgressBar progressRepaid;
    private View layoutDetailsTab, layoutHistoryTab;
    private LinearLayout layoutHistoryItems;
    private Button btnUpdateLoan, btnDeleteLoan;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private final NumberFormat inr = NumberFormat.getInstance(new Locale("en", "IN"));
    private final DecimalFormat rateFormat = new DecimalFormat("0.##");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_loan_details, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        inr.setMaximumFractionDigits(0);

        tabLoanDetails = v.findViewById(R.id.tabLoanDetails);
        tabHistory = v.findViewById(R.id.tabHistory);
        layoutDetailsTab = v.findViewById(R.id.layoutDetailsTab);
        layoutHistoryTab = v.findViewById(R.id.layoutHistoryTab);
        layoutHistoryItems = v.findViewById(R.id.layoutHistoryItems);
        tvHistoryEmpty = v.findViewById(R.id.tvHistoryEmpty);

        progressRepaid = v.findViewById(R.id.progressRepaid);
        tvRepaidPercent = v.findViewById(R.id.tvRepaidPercent);
        tvDetailLoanType = v.findViewById(R.id.tvDetailLoanType);
        tvDetailBank = v.findViewById(R.id.tvDetailBank);
        tvDetailRepaidText = v.findViewById(R.id.tvDetailRepaidText);

        tvDetailLoanAmount = v.findViewById(R.id.tvDetailLoanAmount);
        tvDetailInterestRate = v.findViewById(R.id.tvDetailInterestRate);
        tvDetailTenure = v.findViewById(R.id.tvDetailTenure);
        tvDetailEMI = v.findViewById(R.id.tvDetailEMI);
        tvDetailTotalInterest = v.findViewById(R.id.tvDetailTotalInterest);
        tvDetailTotalAmount = v.findViewById(R.id.tvDetailTotalAmount);
        tvDetailOutstanding = v.findViewById(R.id.tvDetailOutstanding);
        tvDetailPrepayment = v.findViewById(R.id.tvDetailPrepayment);

        btnUpdateLoan = v.findViewById(R.id.btnUpdateLoan);
        btnDeleteLoan = v.findViewById(R.id.btnDeleteLoan);

        // Tabs
        tabLoanDetails.setOnClickListener(view -> showTab(true));
        tabHistory.setOnClickListener(view -> showTab(false));

        // Actions
        btnUpdateLoan.setOnClickListener(view -> {
            BottomNavigationView nav = requireActivity().findViewById(R.id.bottomNavigationView);
            nav.setSelectedItemId(R.id.nav_calculate);
        });

        btnDeleteLoan.setOnClickListener(view -> new AlertDialog.Builder(requireContext())
                .setTitle("Delete loan record?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteLoan())
                .setNegativeButton("Cancel", null)
                .show());

        showTab(true);
        loadLoan();
        return v;
    }

    // ------------------------------------------------------------------
    // Tab switching
    // ------------------------------------------------------------------
    private void showTab(boolean details) {
        layoutDetailsTab.setVisibility(details ? View.VISIBLE : View.GONE);
        layoutHistoryTab.setVisibility(details ? View.GONE : View.VISIBLE);

        styleTab(tabLoanDetails, details);
        styleTab(tabHistory, !details);
    }

    private void styleTab(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_segment_selected);
            tab.setTextColor(Color.parseColor("#2563EB"));
        } else {
            tab.setBackground(null);
            tab.setTextColor(Color.parseColor("#64748B"));
        }
    }

    // ------------------------------------------------------------------
    // Load from Firestore
    // ------------------------------------------------------------------
    private void loadLoan() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("loans").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            if (!doc.exists()) {
                showEmpty();
                return;
            }

            String loanType = doc.getString("loanType");
            Double loanAmount = doc.getDouble("loanAmount");
            Double outstandingP = doc.getDouble("outstandingPrincipal");
            Double rate = doc.getDouble("interestRate");
            Double emi = doc.getDouble("monthlyEMI");
            Long originalTenure = doc.getLong("originalTenure");
            Long remainingTenure = doc.getLong("remainingTenure");
            Double prepay = doc.getDouble("selectedPrepayment");
            Double saving = doc.getDouble("estimatedInterestSaving");
            Long updatedAt = doc.getLong("updatedAt");
            Long createdAt = doc.getLong("createdAt");

            double amount = loanAmount != null ? loanAmount : 0;
            double outstanding = outstandingP != null ? outstandingP : amount;
            double emiValue = emi != null ? emi : 0;
            long tenure = originalTenure != null ? originalTenure
                    : (remainingTenure != null ? remainingTenure : 0);

            double totalAmount = emiValue * tenure;
            double totalInterest = Math.max(totalAmount - amount, 0);

            // Repaid % = part of the loan amount already paid off
            int repaid = 0;
            if (amount > 0) {
                repaid = (int) Math.round((amount - outstanding) / amount * 100.0);
                repaid = Math.max(0, Math.min(100, repaid));
            }

            // Summary card
            progressRepaid.setProgress(repaid);
            tvRepaidPercent.setText(repaid + "%");
            tvDetailRepaidText.setText("Loan Repaid: " + repaid + "%");
            tvDetailLoanType.setText(loanType != null ? loanType : "Active Loan");
            tvDetailBank.setText(updatedAt != null
                    ? "Last updated: " + dateFormat.format(new Date(updatedAt)) : "—");

            // Details card
            tvDetailLoanAmount.setText("₹" + inr.format(amount));
            tvDetailInterestRate.setText(rateFormat.format(rate != null ? rate : 0) + "% p.a.");
            tvDetailTenure.setText(tenureText(tenure));
            tvDetailEMI.setText("₹" + inr.format(emiValue));
            tvDetailTotalInterest.setText("₹" + inr.format(totalInterest));
            tvDetailTotalAmount.setText("₹" + inr.format(totalAmount));
            tvDetailOutstanding.setText("₹" + inr.format(outstanding));
            tvDetailPrepayment.setText("₹" + inr.format(prepay != null ? prepay : 0));

            // History tab (built only from data we actually have)
            layoutHistoryItems.removeAllViews();
            if (createdAt != null) {
                addHistoryItem("Loan saved",
                        dateFormat.format(new Date(createdAt)) + " • EMI ₹" + inr.format(emiValue));
            }
            if (prepay != null && prepay > 0) {
                addHistoryItem("Prepayment plan selected",
                        "₹" + inr.format(prepay) + " • est. interest saving ₹"
                                + inr.format(saving != null ? saving : 0));
            }
            tvHistoryEmpty.setVisibility(layoutHistoryItems.getChildCount() == 0
                    ? View.VISIBLE : View.GONE);

        }).addOnFailureListener(e -> {
            if (isAdded()) {
                Toast.makeText(getContext(), "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteLoan() {
        if (mAuth.getCurrentUser() == null) return;
        db.collection("loans").document(mAuth.getCurrentUser().getUid()).delete()
                .addOnSuccessListener(a -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Loan deleted", Toast.LENGTH_SHORT).show();
                    showEmpty();
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Delete failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showEmpty() {
        progressRepaid.setProgress(0);
        tvRepaidPercent.setText("0%");
        tvDetailRepaidText.setText("Loan Repaid: 0%");
        tvDetailLoanType.setText("No Active Loan");
        tvDetailBank.setText("—");
        tvDetailLoanAmount.setText("₹0");
        tvDetailInterestRate.setText("0% p.a.");
        tvDetailTenure.setText("0 months");
        tvDetailEMI.setText("₹0");
        tvDetailTotalInterest.setText("₹0");
        tvDetailTotalAmount.setText("₹0");
        tvDetailOutstanding.setText("₹0");
        tvDetailPrepayment.setText("₹0");
        layoutHistoryItems.removeAllViews();
        tvHistoryEmpty.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private void addHistoryItem(String title, String subtitle) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextSize(15);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(Color.parseColor("#0F172A"));

        TextView s = new TextView(requireContext());
        s.setText(subtitle);
        s.setTextSize(13);
        s.setTextColor(Color.parseColor("#64748B"));

        row.addView(t);
        row.addView(s);
        layoutHistoryItems.addView(row);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** 240 -> "20 years", 18 -> "18 months" */
    private String tenureText(long months) {
        if (months >= 12 && months % 12 == 0) {
            long years = months / 12;
            return years + (years == 1 ? " year" : " years");
        }
        return months + " months";
    }
}