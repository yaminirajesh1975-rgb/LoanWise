package com.example.loanwise;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;

public class SessionManager {

    private static final String PREF_NAME = "loanwise_auth_session";
    private static final String KEY_AUTH_TOKEN = "jwt_token";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_LOGIN_TIME = "login_timestamp";

    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveSession(Context context, String token, String email) {
        if (context == null) return;
        String uid = "user_" + Math.abs((email != null ? email.trim().toLowerCase() : "").hashCode());
        saveSession(context, token, email, uid);
    }

    public static void saveSession(Context context, String token, String email, String userId) {
        SharedPreferences p = getPrefs(context);
        if (p == null) return;
        p.edit()
                .putString(KEY_AUTH_TOKEN, token)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_ID, userId)
                .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                .apply();
    }

    public static String getAuthToken(Context context) {
        SharedPreferences p = getPrefs(context);
        return p != null ? p.getString(KEY_AUTH_TOKEN, null) : null;
    }

    public static String getUserEmail(Context context) {
        SharedPreferences p = getPrefs(context);
        return p != null ? p.getString(KEY_USER_EMAIL, null) : null;
    }

    public static String getUserId(Context context) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null && auth.getCurrentUser().getUid() != null) {
            return auth.getCurrentUser().getUid();
        }
        SharedPreferences p = getPrefs(context);
        String savedUid = p != null ? p.getString(KEY_USER_ID, null) : null;
        if (savedUid != null && !savedUid.trim().isEmpty()) {
            return savedUid;
        }
        String email = getUserEmail(context);
        if (email != null && !email.trim().isEmpty()) {
            return "user_" + Math.abs(email.trim().toLowerCase().hashCode());
        }
        return "user_default";
    }

    public static boolean isLoggedIn(Context context) {
        String token = getAuthToken(context);
        return token != null && !token.trim().isEmpty();
    }

    public static void clearSession(Context context) {
        SharedPreferences p = getPrefs(context);
        if (p != null) {
            p.edit().clear().apply();
        }
    }
}
