package com.example.loanwise;

public class LoanModel {
    private String loanId;
    private String userId;
    private String loanType; // "New Loan" or "Existing Loan"
    private double loanAmount;
    private double outstandingPrincipal;
    private double interestRate;
    private double monthlyEMI;
    private int originalTenure;
    private int remainingTenure;
    private String tenureUnit; // "Months" or "Years"
    private double selectedPrepayment;
    private double estimatedInterestSaving;
    private int estimatedTenureReduction;
    private int revisedTenure;
    private long createdAt;
    private long updatedAt;

    public LoanModel() {
        // Required for Firestore
    }

    public LoanModel(String loanId, String userId, String loanType, double loanAmount,
                     double outstandingPrincipal, double interestRate, double monthlyEMI,
                     int originalTenure, int remainingTenure, String tenureUnit,
                     double selectedPrepayment, double estimatedInterestSaving,
                     int estimatedTenureReduction, int revisedTenure, long createdAt, long updatedAt) {
        this.loanId = loanId;
        this.userId = userId;
        this.loanType = loanType;
        this.loanAmount = loanAmount;
        this.outstandingPrincipal = outstandingPrincipal;
        this.interestRate = interestRate;
        this.monthlyEMI = monthlyEMI;
        this.originalTenure = originalTenure;
        this.remainingTenure = remainingTenure;
        this.tenureUnit = tenureUnit;
        this.selectedPrepayment = selectedPrepayment;
        this.estimatedInterestSaving = estimatedInterestSaving;
        this.estimatedTenureReduction = estimatedTenureReduction;
        this.revisedTenure = revisedTenure;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getLoanId() { return loanId; }
    public void setLoanId(String loanId) { this.loanId = loanId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }

    public double getLoanAmount() { return loanAmount; }
    public void setLoanAmount(double loanAmount) { this.loanAmount = loanAmount; }

    public double getOutstandingPrincipal() { return outstandingPrincipal; }
    public void setOutstandingPrincipal(double outstandingPrincipal) { this.outstandingPrincipal = outstandingPrincipal; }

    public double getInterestRate() { return interestRate; }
    public void setInterestRate(double interestRate) { this.interestRate = interestRate; }

    public double getMonthlyEMI() { return monthlyEMI; }
    public void setMonthlyEMI(double monthlyEMI) { this.monthlyEMI = monthlyEMI; }

    public int getOriginalTenure() { return originalTenure; }
    public void setOriginalTenure(int originalTenure) { this.originalTenure = originalTenure; }

    public int getRemainingTenure() { return remainingTenure; }
    public void setRemainingTenure(int remainingTenure) { this.remainingTenure = remainingTenure; }

    public String getTenureUnit() { return tenureUnit; }
    public void setTenureUnit(String tenureUnit) { this.tenureUnit = tenureUnit; }

    public double getSelectedPrepayment() { return selectedPrepayment; }
    public void setSelectedPrepayment(double selectedPrepayment) { this.selectedPrepayment = selectedPrepayment; }

    public double getEstimatedInterestSaving() { return estimatedInterestSaving; }
    public void setEstimatedInterestSaving(double estimatedInterestSaving) { this.estimatedInterestSaving = estimatedInterestSaving; }

    public int getEstimatedTenureReduction() { return estimatedTenureReduction; }
    public void setEstimatedTenureReduction(int estimatedTenureReduction) { this.estimatedTenureReduction = estimatedTenureReduction; }

    public int getRevisedTenure() { return revisedTenure; }
    public void setRevisedTenure(int revisedTenure) { this.revisedTenure = revisedTenure; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}