package com.example.loanwise.models;

public class PrepaymentSuggestionModel {
    private double prepaymentAmount;
    private double interestSaved;
    private int tenureReductionMonths;
    private int revisedTenureMonths;
    private boolean isSelected;

    public PrepaymentSuggestionModel(double prepaymentAmount, double interestSaved, int tenureReductionMonths, int revisedTenureMonths, boolean isSelected) {
        this.prepaymentAmount = prepaymentAmount;
        this.interestSaved = interestSaved;
        this.tenureReductionMonths = tenureReductionMonths;
        this.revisedTenureMonths = revisedTenureMonths;
        this.isSelected = isSelected;
    }

    public double getPrepaymentAmount() {
        return prepaymentAmount;
    }

    public double getInterestSaved() {
        return interestSaved;
    }

    public int getTenureReductionMonths() {
        return tenureReductionMonths;
    }

    public int getRevisedTenureMonths() {
        return revisedTenureMonths;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
