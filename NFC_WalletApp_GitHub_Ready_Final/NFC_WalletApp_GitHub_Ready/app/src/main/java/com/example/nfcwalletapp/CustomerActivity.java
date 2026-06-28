package com.example.nfcwalletapp;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CustomerActivity extends AppCompatActivity {
    private TextView tvServerStatusCustomer, tvPendingDetails;
    private LinearLayout llApprovalContainer;
    private Button btnApprove, btnReject, btnCustomerReceipts, btnTimeoutTest;

    private SharedPreferences prefs;
    private String serverIp;
    private String currentPendingNonce = null;
    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;

    public static final String TX_STATUS_NONE = "NONE";
    public static final String TX_STATUS_PENDING = "PENDING";
    public static final String TX_STATUS_APPROVED = "APPROVED";
    public static final String TX_STATUS_REJECTED = "REJECTED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);

        prefs = getSharedPreferences("NFCWalletPrefs", MODE_PRIVATE);
        serverIp = getIntent().getStringExtra("SERVER_IP");
        if (serverIp == null || serverIp.isEmpty()) serverIp = prefs.getString("server_ip", "127.0.0.1");
        prefs.edit().putString("server_ip", serverIp).apply();

        tvServerStatusCustomer = findViewById(R.id.tvServerStatusCustomer);
        tvPendingDetails = findViewById(R.id.tvPendingDetails);
        llApprovalContainer = findViewById(R.id.llApprovalContainer);
        btnApprove = findViewById(R.id.btnApprove);
        btnReject = findViewById(R.id.btnReject);
        btnCustomerReceipts = findViewById(R.id.btnCustomerReceipts);
        btnTimeoutTest = findViewById(R.id.btnTimeoutTest);

        tvServerStatusCustomer.setText("Bağlı (Sunucu: " + serverIp + ")");

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        String nfcProblem = NfcSecurityUtils.getNfcProblem(this, true);
        if (nfcProblem != null) {
            tvServerStatusCustomer.setText("UYARI: " + nfcProblem + " Ayarları açmak için dokun.");
            tvServerStatusCustomer.setOnClickListener(v -> NfcSecurityUtils.openNfcSettings(this));
            SecurityLogger.log(this, "CUSTOMER_NFC_PRECHECK_FAILED", "LOCAL", "NFC", null, false, null, -1, -1, null, null, "ERROR", nfcProblem);
        } else {
            SecurityLogger.log(this, "CUSTOMER_HCE_READY", "LOCAL", "NFC_HCE", null, false, null, -1, -1, null, null, "SUCCESS", null);
        }

        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE);

        prefs.edit()
                .remove("Approved_Nonce")
                .putString("TX_STATUS", TX_STATUS_NONE)
                .apply();

        btnCustomerReceipts.setOnClickListener(v -> {
            SecurityLogger.log(this, "CUSTOMER_RECEIPTS_OPENED", "LOCAL", "APP", null, false, null, -1, -1, null, null, "SUCCESS", null);
            startActivity(new Intent(this, ReceiptsActivity.class));
        });

        btnApprove.setOnClickListener(v -> {
            if (currentPendingNonce != null) {
                prefs.edit()
                        .putString("Approved_Nonce", currentPendingNonce)
                        .putString("TX_STATUS", TX_STATUS_APPROVED)
                        .apply();
                SecurityLogger.log(this, "CUSTOMER_APPROVED", "LOCAL", "APP", currentPendingNonce, false, null, -1, -1, null, null, "APPROVED", null);
                Toast.makeText(this, "Ödeme Onaylandı! Cihazı POS telefonuna yakın tutun.", Toast.LENGTH_LONG).show();
                llApprovalContainer.setVisibility(View.GONE);
            }
        });

        btnReject.setOnClickListener(v -> {
            if (currentPendingNonce != null) {
                prefs.edit()
                        .remove("Approved_Nonce")
                        .putString("Rejected_Nonce", currentPendingNonce)
                        .putString("TX_STATUS", TX_STATUS_REJECTED)
                        .apply();
                SecurityLogger.log(this, "CUSTOMER_REJECTED", "LOCAL", "APP", currentPendingNonce, false, null, -1, -1, null, null, "REJECTED", "Customer manually rejected the bill");
            }
            currentPendingNonce = null;
            llApprovalContainer.setVisibility(View.GONE);
            Toast.makeText(this, "Ödeme Reddedildi.", Toast.LENGTH_SHORT).show();
        });

        btnTimeoutTest.setOnClickListener(v -> {
            if (currentPendingNonce != null) {
                prefs.edit()
                        .remove("Approved_Nonce")
                        .remove("Rejected_Nonce")
                        .putString("TX_STATUS", TX_STATUS_PENDING)
                        .apply();
                SecurityLogger.log(this, "CUSTOMER_TIMEOUT_TEST_ARMED", "LOCAL", "APP", currentPendingNonce, false,
                        null, -1, -1, null, null, "TEST", "Customer intentionally does not approve or reject to let POS polling timeout");
                Toast.makeText(this, "Zaman aşımı testi aktif. POS tarafı timeout olana kadar cevap gönderilmeyecek.", Toast.LENGTH_LONG).show();
                llApprovalContainer.setVisibility(View.GONE);
            }
        });

        handlePaymentIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            handlePaymentIntent(intent);
        }
    }

    private void handlePaymentIntent(Intent intent) {
        if ("com.example.nfcwalletapp.ACTION_PAYMENT_REQUEST".equals(intent.getAction())) {
            String amt = intent.getStringExtra("AMOUNT");
            String name = intent.getStringExtra("ITEM_NAME");
            String category = intent.getStringExtra("CATEGORY");
            String retailer = intent.getStringExtra("RETAILER");
            String nonce = intent.getStringExtra("NONCE");

            if (nonce != null && !nonce.equals(currentPendingNonce)) {
                currentPendingNonce = nonce;
                prefs.edit()
                        .putString("Pending_Nonce", nonce)
                        .putString("TX_STATUS", TX_STATUS_PENDING)
                        .remove("Approved_Nonce")
                        .remove("Rejected_Nonce")
                        .apply();

                String details = "Satıcı: " + (retailer == null ? "Unknown" : retailer)
                        + "\nKategori: " + (category == null ? "Unknown" : category)
                        + "\nÜrün: " + name
                        + "\nTutar: $" + amt
                        + "\nNonce: " + nonce
                        + "\n\nGüvenlik: AES şifreli NFC ödeme isteği alındı.";
                tvPendingDetails.setText(details);
                llApprovalContainer.setVisibility(View.VISIBLE);
                SecurityLogger.log(this, "PAYMENT_REQUEST_DISPLAYED", "INCOMING", "NFC_HCE", nonce, true,
                        "AES/CBC/PKCS5Padding", -1, -1, null, "9001", "PENDING", null);
            }
        }
    }
}
