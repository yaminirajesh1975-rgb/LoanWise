package com.example.loanwise.models;

public class UserModel {
    private String userId;
    private String username;
    private String phoneNumber;

    public UserModel() {
        // Required for Firestore public no-arg constructor
    }

    public UserModel(String userId, String username, String phoneNumber) {
        this.userId = userId;
        this.username = username;
        this.phoneNumber = phoneNumber;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
