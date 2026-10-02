package com.example.loanwise.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.loanwise.LoanModel;
import com.example.loanwise.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class CalculateFragment extends Fragment {

    private RadioGroup rgLoanType;
    private RadioButton rbExistingLoan, rbYears, rbMonths;
    private TextInputLayout tilLoanAmount, tilCurrentEMI, tilTenure;
    private TextInputEditText etLoanAmount, etInterestRate, etCurrentEMI, etTenure;
    private Button btnCalculate, btnSaveSelection;
    private MaterialCardView cardResults;
    private TextView tvResultEMI, tvResultTotalInterest, tvResultTotalRepayment;
    private LinearLayout layoutSuggestionsContainer;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private double calculatedEMI = 0;
    private double calculatedTotalInterest = 0;
    private double calculatedTotalRepayment = 0;
    private int tenureMonths = 0;
    private double principalAmount = 0;
    private double annualInterestRate = 0;
    private String loanTypeStr = "New Loan";
    private String tenureUnitStr = "Months";

    private double selectedPrepaymentAmount = 0;
    private double selectedInterestSaving = 0;
    private int selectedTenureReduction = 0;
    private int selectedRevisedTenure = 0;

    private List<SuggestionItem> suggestionList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calculate, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        rgLoanType = view.findViewById(R.id.rgLoanType);
        rbExistingLoan = view.findViewById(R.id.rbExistingLoan);
        rbMonths = view.findViewById(R.id.rbMonths);
        rbYears = view.findViewById(R.id.rbYears);

        tilLoanAmount = view.findViewById(R.id.tilLoanAmount);
        tilCurrentEMI = view.findViewById(R.id.tilCurrentEMI);
        tilTenure = view.findViewById(R.id.tilTenure);

        etLoanAmount = view.findViewById(R.id.etLoanAmount);
        if (etLoanAmount == null && tilLoanAmount != null) {
            etLoanAmount = (TextInputEditText) tilLoanAmount.getEditText();
        }

        etInterestRate = view.findViewById(R.id.etInterestRate);

        etCurrentEMI = view.findViewById(R.id.etCurrentEMI);
        if (etCurrentEMI == null && tilCurrentEMI != null) {
            etCurrentEMI = (TextInputEditText) tilCurrentEMI.getEditText();
        }

        etTenure = view.findViewById(R.id.etTenure);
        if (etTenure == null && tilTenure != null) {
            etTenure = (TextInputEditText) tilTenure.getEditText();
        }

        btnCalculate = view.findViewById(R.id.btnCalculate);
        btnSaveSelection = view.findViewById(R.id.btnSaveSelection);
        cardResults = view.findViewById(R.id.cardResults);
        tvResultEMI = view.findViewById(R.id.tvResultEMI);
        tvResultTotalInterest = view.findViewById(R.id.tvResultTotalInterest);
        tvResultTotalRepayment = view.findViewById(R.id.tvResultTotalRepayment);
        layoutSuggestionsContainer = view.findViewById(R.id.layoutSuggestionsContainer);

        rgLoanType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbExistingLoan) {
                tilCurrentEMI.setVisibility(View.VISIBLE);
                tilLoanAmount.setHint("Outstanding Principal (₹)");
                loanTypeStr = "Existing Loan";
            } else {
                tilCurrentEMI.setVisibility(View.GONE);
                tilLoanAmount.setHint("Loan Amount (₹)");
                loanTypeStr = "New Loan";
            }
        });

        btnCalculate.setOnClickListener(v -> performCalculation());
        btnSaveSelection.setOnClickListener(v -> saveLoanAndPrepaymentToFirestore());

        return view;
    }

    private void performCalculation() {
        if (etLoanAmount == null && tilLoanAmount != null) {
            etLoanAmount = (TextInputEditText) tilLoanAmount.getEditText();
        }
        if (etTenure == null && tilTenure != null) {
            etTenure = (TextInputEditText) tilTenure.getEditText();
        }

        String amountStr = (etLoanAmount != null && etLoanAmount.getText() != null) ? etLoanAmount.getText().toString().trim().replace(",", "") : "";
        String rateStr = (etInterestRate != null && etInterestRate.getText() != null) ? etInterestRate.getText().toString().trim().replace(",", "") : "";
        String tenureStr = (etTenure != null && etTenure.getText() != null) ? etTenure.getText().toString().trim().replace(",", "") : "";

        tenureUnitStr = (rbYears != null && rbYears.isChecked()) ? "Years" : "Months";

        if (TextUtils.isEmpty(amountStr) || TextUtils.isEmpty(rateStr) || TextUtils.isEmpty(tenureStr)) {
            Toast.makeText(getContext(), "Please fill in all required fields (Amount, Rate, Tenure)", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            principalAmount = Double.parseDouble(amountStr);
            annualInterestRate = Double.parseDouble(rateStr);
            int rawTenure = Integer.parseInt(tenureStr);

            if (tenureUnitStr.equalsIgnoreCase("Years")) {
                tenureMonths = rawTenure * 12;
            } else {
                tenureMonths = rawTenure;
            }

            if (principalAmount <= 0 || annualInterestRate <= 0 || tenureMonths <= 0) {
                Toast.makeText(getContext(), "Values must be greater than zero", Toast.LENGTH_SHORT).show();
                return;
            }

            if (rbExistingLoan.isChecked()) {
                if (etCurrentEMI == null && tilCurrentEMI != null) {
                    etCurrentEMI = (TextInputEditText) tilCurrentEMI.getEditText();
                }
                String emiStr = (etCurrentEMI != null && etCurrentEMI.getText() != null) ? etCurrentEMI.getText().toString().trim().replace(",", "") : "";
                if (!TextUtils.isEmpty(emiStr)) {
                    calculatedEMI = Double.parseDouble(emiStr);
                } else {
                    calculatedEMI = calculateEMIFormula(principalAmount, annualInterestRate, tenureMonths);
                }
            } else {
                calculatedEMI = calculateEMIFormula(principalAmount, annualInterestRate, tenureMonths);
            }

            calculatedTotalRepayment = calculatedEMI * tenureMonths;
            calculatedTotalInterest = calculatedTotalRepayment - principalAmount;
            if (calculatedTotalInterest < 0) calculatedTotalInterest = 0;

            tvResultEMI.setText(String.format("Monthly EMI: ₹%.2f", calculatedEMI));
            tvResultTotalInterest.setText(String.format("Total Interest Payable: ₹%.2f", calculatedTotalInterest));
            tvResultTotalRepayment.setText(String.format("Total Repayment: ₹%.2f", calculatedTotalRepayment));

            generatePrepaymentSuggestions();

            cardResults.setVisibility(View.VISIBLE);
            if (cardResults.getParent() != null) {
                cardResults.post(() -> cardResults.getParent().requestChildFocus(cardResults, cardResults));
            }

        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Please enter valid numeric values", Toast.LENGTH_SHORT).show();
        }
    }

    private double calculateEMIFormula(double P, double annualRate, int nMonths) {
        double r = annualRate / (12.0 * 100.0);
        double pow = Math.pow(1 + r, nMonths);
        return (P * r * pow) / (pow - 1);
    }

    private void generatePrepaymentSuggestions() {
        layoutSuggestionsContainer.removeAllViews();
        suggestionList.clear();

        double[] fractions = {0.05, 0.10, 0.15, 0.20, 0.25};

        for (double fraction : fractions) {
            double prepayAmount = Math.round((principalAmount * fraction) / 1000.0) * 1000.0;
            if (prepayAmount <= 0 || prepayAmount >= principalAmount) continue;

            double remainingPrincipal = principalAmount - prepayAmount;
            int newTenureMonths = tenureMonths;

            double r = annualInterestRate / (12.0 * 100.0);
            if (r > 0) {
                double term = 1.0 - ((remainingPrincipal * r) / calculatedEMI);
                if (term > 0) {
                    double calculatedN = -Math.log(term) / Math.log(1 + r);
                    newTenureMonths = (int) Math.ceil(calculatedN);
                }
            }

            int tenureReduction = tenureMonths - newTenureMonths;
            if (tenureReduction < 0) tenureReduction = 0;

            double newTotalRepayment = calculatedEMI * newTenureMonths;
            double newTotalInterest = newTotalRepayment - remainingPrincipal;
            double interestSaved = calculatedTotalInterest - newTotalInterest;
            if (interestSaved < 0) interestSaved = 0;

            final double finalPrepayAmount = prepayAmount;
            final double finalInterestSaved = interestSaved;
            final int finalTenureReduction = tenureReduction;
            final int finalNewTenure = newTenureMonths;

            SuggestionItem item = new SuggestionItem(finalPrepayAmount, finalInterestSaved, finalTenureReduction, finalNewTenure);
            suggestionList.add(item);

            View cardView = getLayoutInflater().inflate(R.layout.item_prepayment_suggestion, layoutSuggestionsContainer, false);
            TextView tvPrepay = cardView.findViewById(R.id.tvPrepaymentAmount);
            TextView tvSaved = cardView.findViewById(R.id.tvSavedInterest);
            TextView tvReduction = cardView.findViewById(R.id.tvTenureReduction);
            CheckBox cbSelected = cardView.findViewById(R.id.cbSelected);
            MaterialCardView cardItem = cardView.findViewById(R.id.cardPrepaymentItem);

            tvPrepay.setText(String.format("Prepayment: ₹%,.0f", finalPrepayAmount));
            tvSaved.setText(String.format("Save ₹%,.0f interest", finalInterestSaved));
            tvReduction.setText(String.format("Reduce tenure by %d months (New: %d months)", finalTenureReduction, finalNewTenure));

            cardItem.setOnClickListener(v -> {
                for (int j = 0; j < layoutSuggestionsContainer.getChildCount(); j++) {
                    View child = layoutSuggestionsContainer.getChildAt(j);
                    if (child != null) {
                        MaterialCardView c = child.findViewById(R.id.cardPrepaymentItem);
                        CheckBox cb = child.findViewById(R.id.cbSelected);
                        if (c != null) c.setStrokeColor(0xFFE0E0E0);
                        if (cb != null) cb.setChecked(false);
                    }
                }
                cardItem.setStrokeColor(0xFF1A73E8);
                cbSelected.setChecked(true);

                selectedPrepaymentAmount = finalPrepayAmount;
                selectedInterestSaving = finalInterestSaved;
                selectedTenureReduction = finalTenureReduction;
                selectedRevisedTenure = finalNewTenure;
            });

            layoutSuggestionsContainer.addView(cardView);
        }
    }

    private void saveLoanAndPrepaymentToFirestore() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();
        String loanId = db.collection("loans").document().getId();

        long currentTime = System.currentTimeMillis();

        LoanModel loanModel = new LoanModel(
                loanId,
                userId,
                loanTypeStr,
                principalAmount,
                principalAmount,
                annualInterestRate,
                calculatedEMI,
                tenureMonths,
                selectedRevisedTenure > 0 ? selectedRevisedTenure : tenureMonths,
                tenureUnitStr,
                selectedPrepaymentAmount,
                selectedInterestSaving,
                selectedTenureReduction,
                selectedRevisedTenure > 0 ? selectedRevisedTenure : tenureMonths,
                currentTime,
                currentTime
        );

        db.collection("loans").document(userId)
                .set(loanModel)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Loan & Planned Prepayment saved successfully! Check Home screen.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private static class SuggestionItem {
        double prepaymentAmount;
        double interestSaved;
        int tenureReductionMonths;
        int revisedTenureMonths;

        public SuggestionItem(double prepaymentAmount, double interestSaved, int tenureReductionMonths, int revisedTenureMonths) {
            this.prepaymentAmount = prepaymentAmount;
            this.interestSaved = interestSaved;
            this.tenureReductionMonths = tenureReductionMonths;
            this.revisedTenureMonths = revisedTenureMonths;
        }
    }
}
