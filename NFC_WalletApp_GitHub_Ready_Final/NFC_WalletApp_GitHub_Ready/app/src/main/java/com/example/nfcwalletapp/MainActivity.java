package com.example.nfcwalletapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;

import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private TextView tvServerStatus;
    private EditText etUsername, etPassword, etServerIp;
    private Button btnLogin;
    private String serverIp = null;
    private int serverPort = 5000;
    private boolean isServerFound = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("NFCWalletPrefs", MODE_PRIVATE);
        tvServerStatus = findViewById(R.id.tvServerStatus);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etServerIp = findViewById(R.id.etServerIp);
        btnLogin = findViewById(R.id.btnLogin);

        serverIp = prefs.getString("server_ip", null);
        serverPort = prefs.getInt("server_port", 5000);
        if (serverIp != null && !serverIp.isEmpty()) {
            isServerFound = true;
            tvServerStatus.setText("Son Sunucu: " + serverIp + " (Manuel değiştirmek için tıkla)");
            tvServerStatus.setBackgroundColor(Color.parseColor("#607D8B"));
        }

        tvServerStatus.setOnClickListener(v -> {
            if (etServerIp.getVisibility() == View.GONE) {
                etServerIp.setVisibility(View.VISIBLE);
                etServerIp.setText(serverIp == null ? "" : serverIp);
                etServerIp.requestFocus();
                tvServerStatus.setText("Manuel Mod: PC server IP adresini girin");
                tvServerStatus.setBackgroundColor(Color.parseColor("#FFC107"));
            } else {
                etServerIp.setVisibility(View.GONE);
                tvServerStatus.setText(isServerFound ? "Bağlandı: " + serverIp : "Sunucu Aranıyor... (Manuel IP için Tıkla)");
                tvServerStatus.setBackgroundColor(isServerFound ? Color.parseColor("#4CAF50") : Color.parseColor("#9E9E9E"));
            }
        });

        listenForUDP();
        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void listenForUDP() {
        new Thread(() -> {
            try {
                java.net.DatagramSocket socket = new java.net.DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(9999));
                byte[] buf = new byte[1024];
                while (!isServerFound) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength()).trim();
                    JSONObject j = new JSONObject(message);
                    if (j.optString("service").equals("NFC_WALLET_NODE")) {
                        serverIp = j.getString("ip");
                        serverPort = j.optInt("port", 5000);
                        isServerFound = true;
                        socket.close();
                        prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply();
                        runOnUiThread(() -> {
                            tvServerStatus.setText("Bağlandı: " + serverIp);
                            tvServerStatus.setBackgroundColor(Color.parseColor("#4CAF50"));
                            etServerIp.setVisibility(View.GONE);
                        });
                    }
                }
            } catch (Exception ignored) { }
        }).start();
    }

    private void attemptLogin() {
        String u = etUsername.getText().toString().trim();
        String p = etPassword.getText().toString().trim();

        if (etServerIp.getVisibility() == View.VISIBLE && !etServerIp.getText().toString().trim().isEmpty()) {
            serverIp = etServerIp.getText().toString().trim();
            serverPort = 5000;
            isServerFound = true;
        }

        if (u.isEmpty() || p.isEmpty()) {
            Toast.makeText(this, "Bilgileri girin", Toast.LENGTH_SHORT).show();
            return;
        }
        if (serverIp == null || serverIp.isEmpty()) {
            Toast.makeText(this, "Bağlantı bekleniyor veya manuel IP girin", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply();
        SecurityLogger.log(this, "LOGIN_REQUEST", "OUTGOING", "HTTP", null, false, null, -1, -1, null, null, "PENDING", null);

        btnLogin.setText("BAĞLANILIYOR...");
        btnLogin.setEnabled(false);

        new Thread(() -> {
            long started = System.nanoTime();
            try {
                JSONObject json = new JSONObject();
                json.put("username", u);
                json.put("password", p);
                RequestBody body = RequestBody.create(json.toString(), ApiClient.JSON);
                Request req = new Request.Builder().url("http://" + serverIp + ":" + serverPort + "/api/auth/login").post(body).build();
                Response res = ApiClient.client().newCall(req).execute();
                String responseBody = res.body() != null ? res.body().string() : "{}";
                double duration = SecurityLogger.elapsedMs(started);

                if (res.isSuccessful()) {
                    JSONObject rJson = new JSONObject(responseBody);
                    String role = rJson.getString("role");
                    prefs.edit()
                            .putString("jwt_token", rJson.getString("token"))
                            .putString("username", rJson.optString("username", u))
                            .putString("role", role)
                            .putString("server_ip", serverIp)
                            .putInt("server_port", serverPort)
                            .apply();

                    SecurityLogger.log(this, "LOGIN_SUCCESS", "INCOMING", "HTTP", null, false, null,
                            responseBody.length(), duration, String.valueOf(res.code()), null, "SUCCESS", null);

                    Intent i;
                    if (role.equals("RETAILER")) {
                        prefs.edit().putString("retailer_username", u).putString("retailer_token", rJson.getString("token")).apply();
                        i = new Intent(MainActivity.this, PosActivity.class);
                    } else {
                        i = new Intent(MainActivity.this, CustomerActivity.class);
                    }
                    i.putExtra("SERVER_IP", serverIp);
                    i.putExtra("SERVER_PORT", serverPort);
                    startActivity(i);
                    finish();
                } else {
                    SecurityLogger.log(this, "LOGIN_FAILED", "INCOMING", "HTTP", null, false, null,
                            responseBody.length(), duration, String.valueOf(res.code()), null, "REJECTED", responseBody);
                    runOnUiThread(() -> {
                        btnLogin.setText("GİRİŞ BAŞARISIZ");
                        btnLogin.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                SecurityLogger.log(this, "LOGIN_NETWORK_ERROR", "OUTGOING", "HTTP", null, false, null,
                        -1, SecurityLogger.elapsedMs(started), null, null, "ERROR", e.getMessage());
                runOnUiThread(() -> {
                    btnLogin.setText("AĞ HATASI - TEKRAR DENE");
                    btnLogin.setEnabled(true);
                });
            }
        }).start();
    }
}
