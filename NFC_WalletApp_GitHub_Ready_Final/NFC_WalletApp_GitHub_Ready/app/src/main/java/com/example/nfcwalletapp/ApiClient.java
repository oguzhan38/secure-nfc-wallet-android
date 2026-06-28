package com.example.nfcwalletapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int DEFAULT_PORT = 5000;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build();

    public static OkHttpClient client() {
        return CLIENT;
    }

    public static String baseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("NFCWalletPrefs", Context.MODE_PRIVATE);
        String ip = prefs.getString("server_ip", null);
        int port = prefs.getInt("server_port", DEFAULT_PORT);
        if (ip == null || ip.trim().isEmpty()) {
            ip = "127.0.0.1";
        }
        return "http://" + ip + ":" + port;
    }

    public static Response postJson(Context context, String path, JSONObject json) throws IOException {
        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request.Builder builder = new Request.Builder().url(baseUrl(context) + path).post(body);
        String token = getToken(context);
        if (token != null && !token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        return CLIENT.newCall(builder.build()).execute();
    }

    public static Response get(Context context, String path) throws IOException {
        Request.Builder builder = new Request.Builder().url(baseUrl(context) + path).get();
        String token = getToken(context);
        if (token != null && !token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        return CLIENT.newCall(builder.build()).execute();
    }

    public static String getToken(Context context) {
        return context.getSharedPreferences("NFCWalletPrefs", Context.MODE_PRIVATE).getString("jwt_token", null);
    }
}
