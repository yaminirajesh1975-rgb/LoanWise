package com.example.loanwise;

import android.content.Context;
import android.content.SharedPreferences;

public class BackendConfig {

    private static final String PREF_NAME = "loanwise_backend_config";
    private static final String KEY_BASE_URL = "backend_base_url";

    // Default for Android emulator to access host machine localhost:5001.
    // For physical devices, user can tap the Server Settings button to input their PC's LAN IP (e.g. http://192.168.1.10:5001)
    public static final String DEFAULT_EMULATOR_URL = "http://10.0.2.2:5001";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getBaseUrl(Context context) {
        String saved = getPrefs(context).getString(KEY_BASE_URL, null);
        if (saved != null && !saved.trim().isEmpty()) {
            return normalizeUrl(saved);
        }
        return DEFAULT_EMULATOR_URL;
    }

    public static void setBaseUrl(Context context, String url) {
        if (url == null) return;
        getPrefs(context).edit().putString(KEY_BASE_URL, normalizeUrl(url)).apply();
    }

    public static void resetToDefault(Context context) {
        getPrefs(context).edit().remove(KEY_BASE_URL).apply();
    }

    private static String normalizeUrl(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        return trimmed;
    }
}
