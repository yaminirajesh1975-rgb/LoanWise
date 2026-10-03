package com.example.loanwise;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LoanDetailsFragment extends Fragment {

    private TextView tabLoanDetails, tabHistory, tvRepaidPercent, tvDetailLoanType, tvDetailBank,
            tvDetailRepaidText, tvDetailLoanAmount, tvDetailInterestRate, tvDetailTenure,
            tvDetailEMI, tvDetailTotalInterest, tvDetailTotalAmount, tvDetailOutstanding,
            tvDetailPrepayment, tvHistoryEmpty;
    private ProgressBar progressRepaid;
    private View layoutDetailsTab, layoutHistoryTab;
    private LinearLayout layoutHistoryItems;
    private Button btnRecordPayment, btnUpdateLoan, btnDeleteLoan;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Latest loan values (used by the Record Payment dialog)
    private boolean hasLoan = false;
    private double curOutstanding = 0, curEmi = 0;

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

        btnRecordPayment = v.findViewById(R.id.btnRecordPayment);
        btnUpdateLoan = v.findViewById(R.id.btnUpdateLoan);
        btnDeleteLoan = v.findViewById(R.id.btnDeleteLoan);

        tabLoanDetails.setOnClickListener(view -> showTab(true));
        tabHistory.setOnClickListener(view -> showTab(false));

        btnRecordPayment.setOnClickListener(view -> showRecordPaymentDialog());

        btnUpdateLoan.setOnClickListener(view -> {
            BottomNavigationView nav = requireActivity().findViewById(R.id.bottomNavigationView);
            nav.setSelectedItemId(R.id.nav_calculate);
        });

        btnDeleteLoan.setOnClickListener(view -> new AlertDialog.Builder(requireContext())
                .setTitle("Delete loan record?")
                .setMessage("This also deletes your payment history. It cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteLoan())
                .setNegativeButton("Cancel", null)
                .show());

        showTab(true);

        // Add any EMIs whose debit date has arrived, then load the screen
        String resolvedUid = SessionManager.getUserId(getContext());
        if (resolvedUid != null) {
            EmiScheduler.postDueEmis(db, resolvedUid,
                    count -> { if (isAdded()) loadLoan(); });
        }
        return v;
    }

    // ------------------------------------------------------------------
    // Tabs
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
    // Load loan + history
    // ------------------------------------------------------------------
    private void loadLoan() {
        final Context context = (getContext() != null) ? getContext() : getActivity();
        String uid = SessionManager.getUserId(context);
        if (uid == null) return;

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
            Long updatedAt = doc.getLong("updatedAt");

            double amount = loanAmount != null ? loanAmount : 0;
            double outstanding = outstandingP != null ? outstandingP : amount;
            double emiValue = emi != null ? emi : 0;
            long tenure = originalTenure != null ? originalTenure
                    : (remainingTenure != null ? remainingTenure : 0);

            hasLoan = true;
            curOutstanding = outstanding;
            curEmi = emiValue;

            double totalAmount = emiValue * tenure;
            double totalInterest = Math.max(totalAmount - amount, 0);

            int repaid = 0;
            if (amount > 0) {
                repaid = (int) Math.round((amount - outstanding) / amount * 100.0);
                repaid = Math.max(0, Math.min(100, repaid));
            }

            progressRepaid.setProgress(repaid);
            tvRepaidPercent.setText(repaid + "%");
            tvDetailRepaidText.setText("Loan Repaid: " + repaid + "%");
            tvDetailLoanType.setText(loanType != null ? loanType : "Active Loan");
            tvDetailBank.setText(updatedAt != null
                    ? "Last updated: " + dateFormat.format(new Date(updatedAt)) : "—");

            tvDetailLoanAmount.setText("₹" + inr.format(amount));
            tvDetailInterestRate.setText(rateFormat.format(rate != null ? rate : 0) + "% p.a.");
            tvDetailTenure.setText(tenureText(tenure));
            tvDetailEMI.setText("₹" + inr.format(emiValue));
            tvDetailTotalInterest.setText("₹" + inr.format(totalInterest));
            tvDetailTotalAmount.setText("₹" + inr.format(totalAmount));
            tvDetailOutstanding.setText("₹" + inr.format(outstanding));
            tvDetailPrepayment.setText("₹" + inr.format(prepay != null ? prepay : 0));

            loadHistory(uid);

        }).addOnFailureListener(e -> {
            if (isAdded()) {
                Toast.makeText(getContext(), "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadHistory(String uid) {
        db.collection("loans").document(uid).collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    layoutHistoryItems.removeAllViews();
                    LayoutInflater inflater = LayoutInflater.from(requireContext());

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String type = d.getString("type");
                        Double amt = d.getDouble("amount");
                        Double bal = d.getDouble("balanceAfter");
                        Long date = d.getLong("date");
                        boolean isPrepay = "Prepayment".equals(type);

                        View row = inflater.inflate(R.layout.item_history_transaction,
                                layoutHistoryItems, false);

                        View iconBox = row.findViewById(R.id.flHistIcon);
                        ImageView icon = row.findViewById(R.id.ivHistIcon);
                        TextView title = row.findViewById(R.id.tvHistTitle);
                        TextView dateTv = row.findViewById(R.id.tvHistDate);
                        TextView amount = row.findViewById(R.id.tvHistAmount);
                        TextView balance = row.findViewById(R.id.tvHistBalance);

                        if (isPrepay) {
                            iconBox.setBackgroundResource(R.drawable.bg_icon_green);
                            icon.setImageResource(R.drawable.ic_history_prepay);
                            amount.setTextColor(Color.parseColor("#0F9D58"));
                            title.setText("Prepayment");
                        } else {
                            iconBox.setBackgroundResource(R.drawable.bg_icon_blue);
                            icon.setImageResource(R.drawable.ic_history_card);
                            amount.setTextColor(Color.parseColor("#0F172A"));
                            title.setText("EMI Payment");
                        }

                        dateTv.setText(date != null ? dateFormat.format(new Date(date)) : "—");
                        amount.setText("₹" + inr.format(amt != null ? amt : 0));
                        balance.setText("Bal: ₹" + inr.format(bal != null ? bal : 0));

                        layoutHistoryItems.addView(row);
                    }

                    tvHistoryEmpty.setVisibility(layoutHistoryItems.getChildCount() == 0
                            ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "History failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Record Payment dialog
    // ------------------------------------------------------------------
    private void showRecordPaymentDialog() {
        if (!hasLoan) {
            Toast.makeText(getContext(), "Save a loan from the Calculator tab first",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (curOutstanding <= 0) {
            Toast.makeText(getContext(), "This loan is already fully repaid",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_record_payment, null);
        RadioGroup rgType = dialogView.findViewById(R.id.rgPayType);
        TextInputLayout tilAmount = dialogView.findViewById(R.id.tilPayAmount);
        TextInputEditText etAmount = dialogView.findViewById(R.id.etPayAmount);
        TextInputEditText etDate = dialogView.findViewById(R.id.etPayDate);

        final Calendar cal = Calendar.getInstance();
        etDate.setText(dateFormat.format(cal.getTime()));
        etAmount.setText(String.valueOf(Math.round(curEmi)));   // EMI is pre-filled

        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            tilAmount.setError(null);
            if (checkedId == R.id.rbPayEmi) {
                etAmount.setText(String.valueOf(Math.round(curEmi)));
            } else {
                etAmount.setText("");
            }
        });

        etDate.setOnClickListener(view -> new DatePickerDialog(requireContext(),
                (picker, y, m, d) -> {
                    cal.set(y, m, d, 12, 0, 0);
                    etDate.setText(dateFormat.format(cal.getTime()));
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                .show());

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Record Payment")
                .setView(dialogView)
                .setPositiveButton("Save", null)      // set below so errors don't close the dialog
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            boolean isEmi = rgType.getCheckedRadioButtonId() == R.id.rbPayEmi;
            Double amount = null;
            try {
                String s = etAmount.getText() == null ? "" : etAmount.getText().toString().trim();
                if (!s.isEmpty()) amount = Double.parseDouble(s.replace(",", ""));
            } catch (NumberFormatException ignored) { }

            if (amount == null || amount <= 0) {
                tilAmount.setError("Enter a valid amount");
                return;
            }
            if (!isEmi && amount > curOutstanding) {
                tilAmount.setError("Cannot exceed outstanding ₹" + inr.format(curOutstanding));
                return;
            }
            dialog.dismiss();
            savePayment(isEmi, amount, cal.getTimeInMillis());
        });
    }

    // ------------------------------------------------------------------
    // Save the payment + update the loan balance (one atomic transaction)
    // ------------------------------------------------------------------
    private void savePayment(boolean isEmi, double amount, long dateMillis) {
        final Context context = (getContext() != null) ? getContext() : getActivity();
        String uid = SessionManager.getUserId(context);
        if (uid == null) return;

        DocumentReference loanRef = db.collection("loans").document(uid);
        DocumentReference txRef = loanRef.collection("transactions").document();

        btnRecordPayment.setEnabled(false);

        db.runTransaction((Transaction.Function<Double>) transaction -> {
            DocumentSnapshot snap = transaction.get(loanRef);
            if (!snap.exists()) {
                throw new FirebaseFirestoreException("No loan found",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            Double outObj = snap.getDouble("outstandingPrincipal");
            Double rateObj = snap.getDouble("interestRate");
            Double emiObj = snap.getDouble("monthlyEMI");
            Long remObj = snap.getLong("remainingTenure");

            double outstanding = outObj != null ? outObj : 0;
            double rate = rateObj != null ? rateObj : 0;
            double emi = emiObj != null ? emiObj : 0;
            long remaining = remObj != null ? remObj : 0;

            if (outstanding <= 0) {
                throw new FirebaseFirestoreException("Loan already repaid",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            double newBalance;
            long newRemaining;
            if (isEmi) {
                // Part of each EMI is interest; only the rest reduces the principal
                double interestPart = outstanding * rate / 12.0 / 100.0;
                double principalPart = Math.max(amount - interestPart, 0);
                newBalance = Math.max(outstanding - principalPart, 0);
                newRemaining = Math.max(remaining - 1, 0);
            } else {
                newBalance = Math.max(outstanding - amount, 0);
                newRemaining = monthsToClear(newBalance, rate, emi, remaining);
            }
            if (newBalance <= 0) newRemaining = 0;

            long now = System.currentTimeMillis();

            Map<String, Object> tx = new HashMap<>();
            tx.put("type", isEmi ? "EMI" : "Prepayment");
            tx.put("amount", amount);
            tx.put("balanceAfter", (double) Math.round(newBalance));
            tx.put("date", dateMillis);
            tx.put("createdAt", now);

            transaction.update(loanRef,
                    "outstandingPrincipal", (double) Math.round(newBalance),
                    "remainingTenure", newRemaining,
                    "updatedAt", now);
            transaction.set(txRef, tx);

            return newBalance;
        }).addOnSuccessListener(newBalance -> {
            if (!isAdded()) return;
            btnRecordPayment.setEnabled(true);
            Toast.makeText(getContext(), "Payment recorded", Toast.LENGTH_SHORT).show();
            loadLoan();     // refreshes ring, details and history
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            btnRecordPayment.setEnabled(true);
            Toast.makeText(getContext(), "Could not save: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    /** Months needed to clear principal p with a fixed EMI (falls back to old value if impossible). */
    private long monthsToClear(double p, double annualRate, double emi, long fallback) {
        if (p <= 0) return 0;
        if (emi <= 0) return fallback;
        double r = annualRate / 12.0 / 100.0;
        if (r == 0) return (long) Math.ceil(p / emi);
        double x = 1 - (p * r / emi);
        if (x <= 0) return fallback;
        return (long) Math.ceil(-Math.log(x) / Math.log(1 + r));
    }

    // ------------------------------------------------------------------
    // Delete loan + its transactions
    // ------------------------------------------------------------------
    private void deleteLoan() {
        final Context context = (getContext() != null) ? getContext() : getActivity();
        String uid = SessionManager.getUserId(context);
        if (uid == null) return;
        DocumentReference loanRef = db.collection("loans").document(uid);

        loanRef.collection("transactions").get().addOnSuccessListener(snap -> {
            WriteBatch batch = db.batch();
            for (DocumentSnapshot d : snap.getDocuments()) batch.delete(d.getReference());
            batch.delete(loanRef);
            batch.commit()
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
        }).addOnFailureListener(e -> {
            if (isAdded()) {
                Toast.makeText(getContext(), "Delete failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showEmpty() {
        hasLoan = false;
        curOutstanding = 0;
        curEmi = 0;
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

    /** 240 -> "20 years", 18 -> "18 months" */
    private String tenureText(long months) {
        if (months >= 12 && months % 12 == 0) {
            long years = months / 12;
            return years + (years == 1 ? " year" : " years");
        }
        return months + " months";
    }
}