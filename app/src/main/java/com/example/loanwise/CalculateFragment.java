package com.example.loanwise;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
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

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalculateFragment extends Fragment {

    // Views
    private RadioGroup rgLoanType, rgTenureUnit;
    private RadioButton rbExistingLoan, rbYears;
    private TextInputLayout tilLoanAmount, tilInterestRate, tilCurrentEMI, tilTenure, tilEmiDate;
    private TextInputEditText etLoanAmount, etInterestRate, etCurrentEMI, etTenure, etEmiDate;
    private Button btnCalculate, btnSaveSelection;
    private MaterialCardView cardResults;
    private TextView tvResultEMI, tvResultTotalInterest, tvResultTotalRepayment;
    private LinearLayout layoutSuggestionsContainer;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Last calculated values (used when saving)
    private double principal, annualRate, emi;
    private int tenureMonths;
    private boolean isExistingLoan;
    private String tenureUnit = "Months";
    private final List<Suggestion> suggestions = new ArrayList<>();
    private Suggestion selectedSuggestion = null;
    private final List<MaterialCardView> suggestionCards = new ArrayList<>();

    // EMI debit date chosen by the user (noon of that day)
    private Calendar emiCal;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    private final NumberFormat inr = NumberFormat.getInstance(new Locale("en", "IN"));

    private static class Suggestion {
        double prepayment;
        double interestSaved;
        int monthsReduced;
        int newTenure;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calculate, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        inr.setMaximumFractionDigits(0);

        bindViews(view);

        // Default EMI date = same day next month (user can change it)
        emiCal = Calendar.getInstance();
        emiCal.add(Calendar.MONTH, 1);
        emiCal.set(Calendar.HOUR_OF_DAY, 12);
        emiCal.set(Calendar.MINUTE, 0);
        emiCal.set(Calendar.SECOND, 0);
        emiCal.set(Calendar.MILLISECOND, 0);
        etEmiDate.setText(dateFormat.format(emiCal.getTime()));

        etEmiDate.setOnClickListener(v -> showEmiDatePicker());

        // Show the "Current EMI" field only for existing loans
        rgLoanType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean existing = (checkedId == R.id.rbExistingLoan);
            tilCurrentEMI.setVisibility(existing ? View.VISIBLE : View.GONE);
            cardResults.setVisibility(View.GONE);
        });

        btnCalculate.setOnClickListener(v -> calculate());
        btnSaveSelection.setOnClickListener(v -> saveSelection());

        return view;
    }

    private void bindViews(View v) {
        rgLoanType = v.findViewById(R.id.rgLoanType);
        rgTenureUnit = v.findViewById(R.id.rgTenureUnit);
        rbExistingLoan = v.findViewById(R.id.rbExistingLoan);
        rbYears = v.findViewById(R.id.rbYears);

        tilLoanAmount = v.findViewById(R.id.tilLoanAmount);
        tilInterestRate = v.findViewById(R.id.tilInterestRate);
        tilCurrentEMI = v.findViewById(R.id.tilCurrentEMI);
        tilTenure = v.findViewById(R.id.tilTenure);
        tilEmiDate = v.findViewById(R.id.tilEmiDate);

        etLoanAmount = v.findViewById(R.id.etLoanAmount);
        etInterestRate = v.findViewById(R.id.etInterestRate);
        etCurrentEMI = v.findViewById(R.id.etCurrentEMI);
        etTenure = v.findViewById(R.id.etTenure);
        etEmiDate = v.findViewById(R.id.etEmiDate);

        btnCalculate = v.findViewById(R.id.btnCalculate);
        btnSaveSelection = v.findViewById(R.id.btnSaveSelection);
        cardResults = v.findViewById(R.id.cardResults);
        tvResultEMI = v.findViewById(R.id.tvResultEMI);
        tvResultTotalInterest = v.findViewById(R.id.tvResultTotalInterest);
        tvResultTotalRepayment = v.findViewById(R.id.tvResultTotalRepayment);
        layoutSuggestionsContainer = v.findViewById(R.id.layoutSuggestionsContainer);
    }

    private void showEmiDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                (picker, y, m, d) -> {
                    emiCal.set(y, m, d, 12, 0, 0);
                    emiCal.set(Calendar.MILLISECOND, 0);
                    etEmiDate.setText(dateFormat.format(emiCal.getTime()));
                },
                emiCal.get(Calendar.YEAR), emiCal.get(Calendar.MONTH),
                emiCal.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);  // today or later
        dialog.show();
    }

    // ------------------------------------------------------------------
    // 1. Read + validate input, then calculate
    // ------------------------------------------------------------------
    private void calculate() {
        tilLoanAmount.setError(null);
        tilInterestRate.setError(null);
        tilCurrentEMI.setError(null);
        tilTenure.setError(null);

        isExistingLoan = rbExistingLoan.isChecked();

        Double p = parseDouble(etLoanAmount);
        Double rate = parseDouble(etInterestRate);
        Double tenureVal = parseDouble(etTenure);

        if (p == null || p <= 0) {
            tilLoanAmount.setError("Enter a valid amount");
            return;
        }
        if (rate == null || rate < 0 || rate > 100) {
            tilInterestRate.setError("Enter a valid rate (0-100)");
            return;
        }
        if (tenureVal == null || tenureVal <= 0) {
            tilTenure.setError("Enter a valid tenure");
            return;
        }

        boolean years = rbYears.isChecked();
        tenureUnit = years ? "Years" : "Months";
        int months = (int) Math.round(years ? tenureVal * 12 : tenureVal);
        if (months <= 0) {
            tilTenure.setError("Tenure is too short");
            return;
        }

        double calculatedEmi;
        if (isExistingLoan) {
            Double userEmi = parseDouble(etCurrentEMI);
            if (userEmi == null || userEmi <= 0) {
                tilCurrentEMI.setError("Enter your current EMI");
                return;
            }
            // EMI must at least cover monthly interest or the loan never ends
            if (userEmi <= p * (rate / 12.0 / 100.0)) {
                tilCurrentEMI.setError("EMI is too low to cover the interest");
                return;
            }
            calculatedEmi = userEmi;
        } else {
            calculatedEmi = calcEmi(p, rate, months);
        }

        principal = p;
        annualRate = rate;
        tenureMonths = months;
        emi = calculatedEmi;

        double totalRepayment = emi * months;
        double totalInterest = totalRepayment - principal;

        tvResultEMI.setText("Monthly EMI: ₹" + inr.format(Math.round(emi)));
        tvResultTotalInterest.setText("Total Interest Payable: ₹" + inr.format(Math.round(Math.max(totalInterest, 0))));
        tvResultTotalRepayment.setText("Total Repayment: ₹" + inr.format(Math.round(totalRepayment)));

        buildSuggestions();
        cardResults.setVisibility(View.VISIBLE);
    }

    private Double parseDouble(TextInputEditText et) {
        if (et.getText() == null) return null;
        String s = et.getText().toString().trim().replace(",", "");
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 2. Math
    // ------------------------------------------------------------------

    /** Standard EMI formula: P * r * (1+r)^n / ((1+r)^n - 1) */
    private double calcEmi(double p, double annualRatePct, int n) {
        double r = annualRatePct / 12.0 / 100.0;
        if (r == 0) return p / n;
        double pow = Math.pow(1 + r, n);
        return p * r * pow / (pow - 1);
    }

    /** Months needed to clear principal p when paying a fixed EMI. Returns -1 if impossible. */
    private int monthsToClear(double p, double annualRatePct, double emiAmt) {
        if (p <= 0) return 0;
        double r = annualRatePct / 12.0 / 100.0;
        if (r == 0) return (int) Math.ceil(p / emiAmt);
        double x = 1 - (p * r / emiAmt);
        if (x <= 0) return -1;
        return (int) Math.ceil(-Math.log(x) / Math.log(1 + r));
    }

    // ------------------------------------------------------------------
    // 3. Prepayment suggestions (one-time part payment, EMI stays the same)
    // ------------------------------------------------------------------
    private void buildSuggestions() {
        suggestions.clear();
        suggestionCards.clear();
        selectedSuggestion = null;
        layoutSuggestionsContainer.removeAllViews();

        double originalInterest = emi * tenureMonths - principal;

        // Fixed ladder starting from a small minimum amount.
        // Only amounts up to half the principal are suggested.
        double[] ladder = {4000, 10000, 20000, 30000, 50000, 100000};
        double maxAllowed = principal * 0.5;

        List<Double> amounts = new ArrayList<>();
        for (double amt : ladder) {
            if (amt <= maxAllowed) amounts.add(amt);
        }

        // Fallback for very small loans: use 10% / 25% / 50% of the principal
        if (amounts.isEmpty()) {
            double[] pcts = {10, 25, 50};
            for (double pct : pcts) {
                double amt = Math.round(principal * pct / 100.0 / 100.0) * 100.0;
                if (amt >= 100 && amt < principal && !amounts.contains(amt)) amounts.add(amt);
            }
        }

        for (double amt : amounts) {
            double newPrincipal = principal - amt;
            int newMonths = monthsToClear(newPrincipal, annualRate, emi);
            if (newMonths < 0) continue;

            Suggestion s = new Suggestion();
            s.prepayment = amt;
            s.newTenure = Math.min(newMonths, tenureMonths);
            s.monthsReduced = Math.max(tenureMonths - s.newTenure, 0);
            double newInterest = emi * s.newTenure - newPrincipal;
            s.interestSaved = Math.max(originalInterest - newInterest, 0);
            suggestions.add(s);
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Suggestion s : suggestions) {
            View item = inflater.inflate(R.layout.item_prepayment_suggestion,
                    layoutSuggestionsContainer, false);
            MaterialCardView card = item.findViewById(R.id.cardPrepaymentItem);
            TextView tvAmount = item.findViewById(R.id.tvPrepaymentAmount);
            TextView tvSaved = item.findViewById(R.id.tvSavedInterest);
            TextView tvReduction = item.findViewById(R.id.tvTenureReduction);

            tvAmount.setText("Prepayment: ₹" + inr.format(Math.round(s.prepayment)));
            tvSaved.setText("Save ₹" + inr.format(Math.round(s.interestSaved)) + " interest");
            tvReduction.setText("Reduce tenure by " + s.monthsReduced
                    + " months (New: " + s.newTenure + " months)");

            suggestionCards.add(card);
            card.setOnClickListener(v -> selectSuggestion(s));
            layoutSuggestionsContainer.addView(item);
        }

        if (suggestions.isEmpty()) {
            TextView none = new TextView(requireContext());
            none.setText("No prepayment suggestions available for these values.");
            none.setTextColor(Color.parseColor("#5F6368"));
            layoutSuggestionsContainer.addView(none);
        }
    }

    private void selectSuggestion(Suggestion s) {
        selectedSuggestion = s;
        for (int i = 0; i < suggestionCards.size(); i++) {
            MaterialCardView card = suggestionCards.get(i);
            boolean selected = suggestions.get(i) == s;
            CheckBox cb = card.findViewById(R.id.cbSelected);
            cb.setChecked(selected);
            card.setStrokeColor(Color.parseColor(selected ? "#1A73E8" : "#E0E0E0"));
        }
    }

    // ------------------------------------------------------------------
    // 4. Save to Firestore -> HomeFragment / LoanDetailsFragment read "loans/{uid}"
    // ------------------------------------------------------------------
    private void saveSelection() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = mAuth.getCurrentUser().getUid();

        double prepay = selectedSuggestion != null ? selectedSuggestion.prepayment : 0;
        double saved = selectedSuggestion != null ? selectedSuggestion.interestSaved : 0;
        long reduced = selectedSuggestion != null ? selectedSuggestion.monthsReduced : 0;
        long revised = selectedSuggestion != null ? selectedSuggestion.newTenure : tenureMonths;
        long now = System.currentTimeMillis();

        Map<String, Object> loan = new HashMap<>();
        loan.put("userId", userId);
        loan.put("loanType", isExistingLoan ? "Existing Loan" : "New Loan");
        loan.put("loanAmount", principal);
        loan.put("outstandingPrincipal", principal);
        loan.put("interestRate", annualRate);
        loan.put("monthlyEMI", (double) Math.round(emi));
        loan.put("originalTenure", (long) tenureMonths);
        loan.put("remainingTenure", (long) tenureMonths);   // always stored in months
        loan.put("tenureUnit", tenureUnit);
        loan.put("selectedPrepayment", prepay);
        loan.put("estimatedInterestSaving", (double) Math.round(saved));
        loan.put("estimatedTenureReduction", reduced);
        loan.put("revisedTenure", revised);

        // EMI auto-debit schedule
        loan.put("emiDay", (long) emiCal.get(Calendar.DAY_OF_MONTH));
        loan.put("nextEmiDate", emiCal.getTimeInMillis());

        loan.put("createdAt", now);
        loan.put("updatedAt", now);

        btnSaveSelection.setEnabled(false);
        db.collection("loans").document(userId).set(loan)
                .addOnSuccessListener(a -> {
                    btnSaveSelection.setEnabled(true);
                    Toast.makeText(getContext(), "Saved! EMI will be added on "
                                    + dateFormat.format(emiCal.getTime()) + " each month.",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    btnSaveSelection.setEnabled(true);
                    Toast.makeText(getContext(), "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}