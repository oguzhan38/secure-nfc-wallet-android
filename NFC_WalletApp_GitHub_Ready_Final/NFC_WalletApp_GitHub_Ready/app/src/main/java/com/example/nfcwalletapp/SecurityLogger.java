package com.example.nfcwalletapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import okhttp3.Response;

public class SecurityLogger {
    private static final String TAG = "NFC_SECURITY_LOG";

    public static void log(Context context, String eventType, String direction, String protocol,
                           String nonce, boolean encrypted, String algorithm, int payloadSizeBytes,
                           double durationMs, String statusCode, String statusWord,
                           String result, String errorReason) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("NFCWalletPrefs", Context.MODE_PRIVATE);
            JSONObject json = new JSONObject();
            json.put("event_type", eventType);
            json.put("username", prefs.getString("username", "Unknown"));
            json.put("actor_role", prefs.getString("role", "Unknown"));
            json.put("direction", direction);
            json.put("protocol", protocol);
            json.put("nonce", nonce == null ? JSONObject.NULL : nonce);
            json.put("encrypted", encrypted);
            json.put("algorithm", algorithm == null ? JSONObject.NULL : algorithm);
            if (payloadSizeBytes >= 0) json.put("payload_size_bytes", payloadSizeBytes);
            if (durationMs >= 0) json.put("duration_ms", Math.round(durationMs * 1000.0) / 1000.0);
            json.put("status_code", statusCode == null ? JSONObject.NULL : statusCode);
            json.put("status_word", statusWord == null ? JSONObject.NULL : statusWord);
            json.put("result", result == null ? JSONObject.NULL : result);
            json.put("error_reason", errorReason == null ? JSONObject.NULL : errorReason);
            logAsync(context, json);
        } catch (Exception e) {
            Log.w(TAG, "Could not prepare security log", e);
        }
    }

    public static void logAsync(Context context, JSONObject json) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try (Response ignored = ApiClient.postJson(appContext, "/api/security/log", json)) {
                // Fire-and-forget logging. The payment flow must not fail just because a log could not be sent.
            } catch (Exception e) {
                Log.w(TAG, "Security log could not be sent", e);
            }
        }).start();
    }

    public static double elapsedMs(long startedNs) {
        return (System.nanoTime() - startedNs) / 1_000_000.0;
    }
}
