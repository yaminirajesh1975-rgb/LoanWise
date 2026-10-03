package com.example.loanwise;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME = "loanwise_auth_session";
    private static final String KEY_AUTH_TOKEN = "jwt_token";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_LOGIN_TIME = "login_timestamp";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveSession(Context context, String token, String email) {
        getPrefs(context).edit()
                .putString(KEY_AUTH_TOKEN, token)
                .putString(KEY_USER_EMAIL, email)
                .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                .apply();
    }

    public static String getAuthToken(Context context) {
        return getPrefs(context).getString(KEY_AUTH_TOKEN, null);
    }

    public static String getUserEmail(Context context) {
        return getPrefs(context).getString(KEY_USER_EMAIL, null);
    }

    public static boolean isLoggedIn(Context context) {
        String token = getAuthToken(context);
        return token != null && !token.trim().isEmpty();
    }

    public static void clearSession(Context context) {
        getPrefs(context).edit().clear().apply();
    }
}
