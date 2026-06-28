package com.example.nfcwalletapp;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.Response;

public class ReceiptsActivity extends AppCompatActivity {
    private TextView tvTitle, tvStatus;
    private LinearLayout llReceiptList;
    private Button btnRefresh, btnBack;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipts);

        prefs = getSharedPreferences("NFCWalletPrefs", MODE_PRIVATE);
        tvTitle = findViewById(R.id.tvReceiptsTitle);
        tvStatus = findViewById(R.id.tvReceiptsStatus);
        llReceiptList = findViewById(R.id.llReceiptList);
        btnRefresh = findViewById(R.id.btnRefreshReceipts);
        btnBack = findViewById(R.id.btnBackFromReceipts);

        String role = prefs.getString("role", "UNKNOWN");
        tvTitle.setText(role.equals("RETAILER") ? "RETAILER RECEIPTS" : "CUSTOMER RECEIPTS");

        btnRefresh.setOnClickListener(v -> loadReceipts());
        btnBack.setOnClickListener(v -> finish());
        loadReceipts();
    }

    private void loadReceipts() {
        tvStatus.setText("Receipt kayıtları serverdan alınıyor...");
        tvStatus.setTextColor(Color.parseColor("#BDC3C7"));
        llReceiptList.removeAllViews();

        SecurityLogger.log(this, "RECEIPTS_REQUEST", "OUTGOING", "HTTP", null,
                false, null, -1, -1, null, null, "PENDING", null);

        new Thread(() -> {
            long started = System.nanoTime();
            try (Response response = ApiClient.get(this, "/api/receipts")) {
                String body = response.body() != null ? response.body().string() : "{}";
                JSONObject json = new JSONObject(body);
                double duration = SecurityLogger.elapsedMs(started);

                SecurityLogger.log(this, response.isSuccessful() ? "RECEIPTS_LOADED" : "RECEIPTS_LOAD_FAILED",
                        "INCOMING", "HTTP", null, false, null, body.length(), duration,
                        String.valueOf(response.code()), null, response.isSuccessful() ? "SUCCESS" : "ERROR",
                        json.optString("error", null));

                if (!response.isSuccessful()) {
                    runOnUiThread(() -> showError("Receipt listesi alınamadı: " + json.optString("error", "Server error")));
                    return;
                }

                JSONArray arr = json.optJSONArray("receipts");
                if (arr == null) arr = new JSONArray();
                JSONArray finalArr = arr;
                runOnUiThread(() -> renderReceipts(finalArr));
            } catch (Exception e) {
                SecurityLogger.log(this, "RECEIPTS_SERVER_UNREACHABLE", "OUTGOING", "HTTP", null,
                        false, null, -1, SecurityLogger.elapsedMs(started), null, null, "ERROR", e.getMessage());
                runOnUiThread(() -> showError("Server bağlantı hatası: " + e.getMessage()));
            }
        }).start();
    }

    private void renderReceipts(JSONArray arr) {
        llReceiptList.removeAllViews();
        String role = prefs.getString("role", "UNKNOWN");
        if (arr.length() == 0) {
            tvStatus.setText("Henüz başarılı receipt kaydı yok.");
            tvStatus.setTextColor(Color.parseColor("#FFCA28"));
            return;
        }

        tvStatus.setText(arr.length() + " başarılı receipt bulundu.");
        tvStatus.setTextColor(Color.parseColor("#2ECC71"));

        for (int i = 0; i < arr.length(); i++) {
            try {
                Receipt receipt = new Receipt(arr.getJSONObject(i));
                llReceiptList.addView(makeReceiptCard(receipt, role));
            } catch (Exception ignored) {
            }
        }
    }

    private TextView makeReceiptCard(Receipt receipt, String role) {
        TextView card = ReceiptAdapter.createReceiptCard(this, receipt, role);
        card.setOnClickListener(v -> showReceiptDetails(receipt));
        return card;
    }

    private void showReceiptDetails(Receipt receipt) {
        tvStatus.setText("Seçili Receipt: " + receipt.receiptNo + " | Nonce: " + receipt.nonce);
        tvStatus.setTextColor(Color.parseColor("#8ECAE6"));
    }

    private void showError(String message) {
        llReceiptList.removeAllViews();
        tvStatus.setText(message);
        tvStatus.setTextColor(Color.parseColor("#E74C3C"));
    }
}
