package com.example.loanwise;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthApiClient {

    private static final String TAG = "AuthApiClient";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SendOtpListener {
        void onSuccess(String message);
        void onCooldown(String message, int secondsRemaining);
        void onError(String errorMessage);
    }

    public interface VerifyOtpListener {
        void onSuccess(String message, String token, String email);
        void onError(String errorMessage);
    }

    public static void sendOtp(Context context, String email, SendOtpListener listener) {
        final String baseUrl = BackendConfig.getBaseUrl(context);
        final String endpoint = baseUrl + "/auth/send-otp";

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("email", email);

                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                    os.flush();
                }

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                String responseBody = readStream(is);
                Log.d(TAG, "sendOtp response [" + statusCode + "]: " + responseBody);

                JSONObject json = new JSONObject(responseBody.isEmpty() ? "{}" : responseBody);
                boolean success = json.optBoolean("success", false);
                String message = json.optString("message", "Request completed with status " + statusCode);
                int cooldownRemaining = json.optInt("cooldownRemaining", 60);

                mainHandler.post(() -> {
                    if (statusCode == 200 && success) {
                        listener.onSuccess(message);
                    } else if (statusCode == 429) {
                        listener.onCooldown(message, cooldownRemaining);
                    } else {
                        listener.onError(message);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "sendOtp error: " + e.getMessage(), e);
                final String userMessage = "Could not connect to backend server (" + baseUrl + "). Please ensure the backend is running.";
                mainHandler.post(() -> listener.onError(userMessage));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public static void verifyOtp(Context context, String email, String otp, VerifyOtpListener listener) {
        final String baseUrl = BackendConfig.getBaseUrl(context);
        final String endpoint = baseUrl + "/auth/verify-otp";

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("otp", otp);

                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                    os.flush();
                }

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                String responseBody = readStream(is);
                Log.d(TAG, "verifyOtp response [" + statusCode + "]: " + responseBody);

                JSONObject json = new JSONObject(responseBody.isEmpty() ? "{}" : responseBody);
                boolean success = json.optBoolean("success", false);
                String message = json.optString("message", "Request completed with status " + statusCode);
                String token = json.optString("token", "");

                mainHandler.post(() -> {
                    if (statusCode == 200 && success) {
                        listener.onSuccess(message, token, email);
                    } else {
                        listener.onError(message);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "verifyOtp error: " + e.getMessage(), e);
                final String userMessage = "Could not connect to backend server (" + baseUrl + "). Please ensure the backend is running.";
                mainHandler.post(() -> listener.onError(userMessage));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
