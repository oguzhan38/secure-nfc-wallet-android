package com.example.nfcwalletapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

import okhttp3.Response;

public class PosActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private NfcAdapter nfcAdapter;
    private EditText etAmount;
    private Spinner spItemType, spItemName;
    private Button btnReady, btnNewTransaction, btnPosReceipts, btnTamperTest, btnReplayLast;
    private TextView tvReceipt, tvAmountToPay, tvPosError;
    private LinearLayout llInvoiceForm, llNfcScanner;

    private String serverIp;
    private int serverPort = 5000;
    private String retailerUsername;
    private String retailerToken;
    private String currentNonce = null;
    private boolean isScanning = false;
    private boolean isPaymentProcessing = false;
    private long nfcRequestStartedNs = 0L;
    private double lastNfcRoundTripMs = -1;
    private boolean tamperNextAuthorization = false;
    private JSONObject lastAuthorizationRequest = null;

    private final String[] categories = {"Gıda", "Giyim", "Elektronik", "Eğlence", "Diğer"};
    private final String[] items = {"Kahve", "Tişört", "Kulaklık", "Sinema Bileti", "Market Alışverişi"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pos);

        etAmount = findViewById(R.id.etAmount);
        spItemType = findViewById(R.id.spItemType);
        spItemName = findViewById(R.id.spItemName);
        btnReady = findViewById(R.id.btnReadyToScan);
        btnNewTransaction = findViewById(R.id.btnNewTransaction);
        btnPosReceipts = findViewById(R.id.btnPosReceipts);
        btnTamperTest = findViewById(R.id.btnTamperTest);
        btnReplayLast = findViewById(R.id.btnReplayLast);
        tvReceipt = findViewById(R.id.tvReceipt);
        tvAmountToPay = findViewById(R.id.tvAmountToPay);
        tvPosError = findViewById(R.id.tvPosError);
        llInvoiceForm = findViewById(R.id.llInvoiceForm);
        llNfcScanner = findViewById(R.id.llNfcScanner);

        spItemType.setAdapter(makeAdapter(categories));
        spItemName.setAdapter(makeAdapter(items));

        SharedPreferences prefs = getSharedPreferences("NFCWalletPrefs", MODE_PRIVATE);
        serverIp = getIntent().getStringExtra("SERVER_IP");
        serverPort = getIntent().getIntExtra("SERVER_PORT", prefs.getInt("server_port", 5000));
        if (serverIp == null || serverIp.isEmpty()) serverIp = prefs.getString("server_ip", "127.0.0.1");
        prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply();

        retailerUsername = prefs.getString("retailer_username", prefs.getString("username", "Unknown"));
        retailerToken = prefs.getString("retailer_token", prefs.getString("jwt_token", null));
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        SecurityLogger.log(this, "POS_SCREEN_OPENED", "LOCAL", "APP", null, false, null, -1, -1, null, null, "SUCCESS", null);

        btnReady.setOnClickListener(v -> prepareTransaction());
        btnNewTransaction.setOnClickListener(v -> resetTransactionForm());
        btnPosReceipts.setOnClickListener(v -> {
            SecurityLogger.log(this, "RETAILER_RECEIPTS_OPENED", "LOCAL", "APP", null, false, null, -1, -1, null, null, "SUCCESS", null);
            startActivity(new Intent(this, ReceiptsActivity.class));
        });

        btnTamperTest.setOnClickListener(v -> {
            tamperNextAuthorization = true;
            tvPosError.setText("MITM/Tamper test modu aktif: sıradaki ödeme server tarafından hash mismatch ile reddedilecek.");
            SecurityLogger.log(this, "MITM_TAMPER_TEST_ARMED", "LOCAL", "APP", null, false, null, -1, -1, null, null, "INFO", "Next authorization will use an intentionally invalid payload hash");
        });

        btnReplayLast.setOnClickListener(v -> simulateReplayAttack());
    }

    private ArrayAdapter<String> makeAdapter(String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void prepareTransaction() {
        String amt = etAmount.getText().toString().trim();
        if (amt.isEmpty()) {
            tvPosError.setText("Lütfen tutarı girin.");
            return;
        }
        String nfcProblem = NfcSecurityUtils.getNfcProblem(this, false);
        if (nfcProblem != null) {
            tvPosError.setText(nfcProblem + " Ayarları açmak için buraya dokunabilirsiniz.");
            tvPosError.setOnClickListener(v -> NfcSecurityUtils.openNfcSettings(this));
            SecurityLogger.log(this, "NFC_PRECHECK_FAILED", "LOCAL", "NFC", null, false, null, -1, -1, null, null, "ERROR", nfcProblem);
            return;
        }

        currentNonce = UUID.randomUUID().toString();
        lastNfcRoundTripMs = -1;
        lastAuthorizationRequest = null;
        tvPosError.setText("");
        tvAmountToPay.setVisibility(View.VISIBLE);
        tvAmountToPay.setText("$" + amt + "\n(" + spItemName.getSelectedItem().toString() + ")");
        tvReceipt.setText("Müşteri bekleniyor...\nNonce: " + currentNonce);
        tvReceipt.setTextColor(Color.WHITE);

        llInvoiceForm.setVisibility(View.GONE);
        llNfcScanner.setVisibility(View.VISIBLE);
        btnNewTransaction.setText("İPTAL ET");

        isPaymentProcessing = false;
        SecurityLogger.log(this, "TRANSACTION_CREATED", "LOCAL", "APP", currentNonce, false, null, -1, -1, null, null, "PENDING", null);
        startNfcScanner();
    }

    private void resetTransactionForm() {
        stopNfcScanner();
        currentNonce = null;
        isPaymentProcessing = false;
        etAmount.setText("");
        tvPosError.setText("");
        tvAmountToPay.setVisibility(View.GONE);
        llNfcScanner.setVisibility(View.GONE);
        llInvoiceForm.setVisibility(View.VISIBLE);
    }

    private void startNfcScanner() {
        if (nfcAdapter != null && !isScanning) {
            Bundle options = new Bundle();
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000);
            nfcAdapter.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
            isScanning = true;
            SecurityLogger.log(this, "NFC_READER_MODE_ENABLED", "LOCAL", "NFC", currentNonce, false, null, -1, -1, null, null, "SUCCESS", null);
        }
    }

    private void stopNfcScanner() {
        if (nfcAdapter != null && isScanning) {
            nfcAdapter.disableReaderMode(this);
            isScanning = false;
            SecurityLogger.log(this, "NFC_READER_MODE_DISABLED", "LOCAL", "NFC", currentNonce, false, null, -1, -1, null, null, "SUCCESS", null);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (isPaymentProcessing || currentNonce == null) return;
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            SecurityLogger.log(this, "NFC_TAG_NOT_ISODEP", "INCOMING", "NFC_APDU", currentNonce, false, null, -1, -1, null, null, "ERROR", "Tag does not support IsoDep");
            return;
        }

        try {
            isoDep.setTimeout(10000);
            isoDep.connect();

            long selectStarted = System.nanoTime();
            byte[] aidResponse = isoDep.transceive(hexStringToByteArray("00A4040007F0010203040506"));
            String aidStatus = statusWord(aidResponse);
            SecurityLogger.log(this, "APDU_SELECT_SENT", "OUTGOING", "NFC_APDU", currentNonce, false, null,
                    7, SecurityLogger.elapsedMs(selectStarted), null, aidStatus, aidStatus.equals("9000") ? "SUCCESS" : "ERROR", null);

            if (!aidStatus.equals("9000")) {
                runOnUiThread(() -> tvReceipt.setText("HATA: Müşteri cihazında NFC Wallet HCE servisi hazır değil."));
                return;
            }

            JSONObject payloadObj = new JSONObject();
            payloadObj.put("amt", etAmount.getText().toString());
            payloadObj.put("name", spItemName.getSelectedItem().toString());
            payloadObj.put("category", spItemType.getSelectedItem().toString());
            payloadObj.put("retailer", retailerUsername);
            payloadObj.put("nonce", currentNonce);
            payloadObj.put("ts", System.currentTimeMillis());

            long encStarted = System.nanoTime();
            String encryptedReq = CryptoManager.encrypt(payloadObj.toString());
            double encMs = SecurityLogger.elapsedMs(encStarted);
            if (encryptedReq == null) throw new IllegalStateException("Payment request encryption failed");
            if (encryptedReq.getBytes(StandardCharsets.UTF_8).length > 240) {
                SecurityLogger.log(this, "APDU_PACKET_TOO_LARGE", "LOCAL", "NFC_APDU", currentNonce, true,
                        "AES/CBC/PKCS5Padding", encryptedReq.length(), encMs, null, null, "ERROR", "Encrypted NFC request is larger than safe short APDU payload size");
                runOnUiThread(() -> tvReceipt.setText("HATA: NFC paketi çok büyük. APDU güvenli boyut sınırı aşıldı."));
                return;
            }
            SecurityLogger.log(this, "PAYMENT_REQUEST_ENCRYPT_FINISHED", "LOCAL", "CRYPTO", currentNonce, true,
                    "AES/CBC/PKCS5Padding", encryptedReq.length(), encMs, null, null, "SUCCESS", null);

            byte[] reqBytes = encryptedReq.getBytes(StandardCharsets.UTF_8);
            byte[] reqCmd = new byte[5 + reqBytes.length];
            reqCmd[0] = (byte) 0x80;
            reqCmd[1] = 0x01;
            reqCmd[2] = 0x00;
            reqCmd[3] = 0x00;
            reqCmd[4] = (byte) (reqBytes.length & 0xFF);
            System.arraycopy(reqBytes, 0, reqCmd, 5, reqBytes.length);

            nfcRequestStartedNs = System.nanoTime();
            byte[] response = isoDep.transceive(reqCmd);
            SecurityLogger.log(this, "APDU_PAYMENT_REQUEST_SENT", "OUTGOING", "NFC_APDU", currentNonce, true,
                    "AES/CBC/PKCS5Padding", reqCmd.length, SecurityLogger.elapsedMs(nfcRequestStartedNs), null,
                    statusWord(response), "PENDING", null);

            int pollCount = 0;
            while (pollCount < 40) {
                String sw = statusWord(response);
                if (sw.equals("9001")) {
                    pollCount++;
                    runOnUiThread(() -> tvReceipt.setText("Müşteri Onayı Bekleniyor...\nNonce: " + currentNonce));
                    Thread.sleep(1500);
                    byte[] nonceBytes = currentNonce.getBytes(StandardCharsets.UTF_8);
                    byte[] statCmd = new byte[5 + nonceBytes.length];
                    statCmd[0] = (byte) 0x80;
                    statCmd[1] = 0x02;
                    statCmd[2] = 0x00;
                    statCmd[3] = 0x00;
                    statCmd[4] = (byte) (nonceBytes.length & 0xFF);
                    System.arraycopy(nonceBytes, 0, statCmd, 5, nonceBytes.length);
                    long pollStarted = System.nanoTime();
                    response = isoDep.transceive(statCmd);
                    SecurityLogger.log(this, "APDU_STATUS_POLL_SENT", "OUTGOING", "NFC_APDU", currentNonce, false,
                            null, statCmd.length, SecurityLogger.elapsedMs(pollStarted), null, statusWord(response), "PENDING", null);
                } else if (sw.equals("9000")) {
                    lastNfcRoundTripMs = SecurityLogger.elapsedMs(nfcRequestStartedNs);
                    processEncryptedResponse(new String(Arrays.copyOf(response, response.length - 2), StandardCharsets.UTF_8));
                    break;
                } else if (sw.equals("9002")) {
                    SecurityLogger.log(this, "CUSTOMER_REJECTED", "INCOMING", "NFC_APDU", currentNonce, false, null,
                            response.length, SecurityLogger.elapsedMs(nfcRequestStartedNs), null, sw, "REJECTED", "Customer rejected payment");
                    runOnUiThread(() -> {
                        tvReceipt.setTextColor(Color.parseColor("#E74C3C"));
                        tvReceipt.setText("ÖDEME REDDEDİLDİ\nMüşteri işlemi onaylamadı.");
                        btnNewTransaction.setText("GERİ DÖN");
                    });
                    break;
                } else {
                    SecurityLogger.log(this, "APDU_PAYMENT_FAILED", "INCOMING", "NFC_APDU", currentNonce, false, null,
                            response.length, SecurityLogger.elapsedMs(nfcRequestStartedNs), null, sw, "ERROR", "Unexpected APDU status word");
                    runOnUiThread(() -> tvReceipt.setText("İşlem Kesildi. APDU hata kodu: " + sw));
                    break;
                }
            }

            if (pollCount >= 40) {
                SecurityLogger.log(this, "CUSTOMER_APPROVAL_TIMEOUT", "INCOMING", "NFC_APDU", currentNonce, false, null,
                        -1, SecurityLogger.elapsedMs(nfcRequestStartedNs), null, "9001", "TIMEOUT", "Customer did not approve in time");
                runOnUiThread(() -> tvReceipt.setText("ZAMAN AŞIMI: Müşteri onayı alınamadı."));
            }
            isoDep.close();
        } catch (Exception e) {
            SecurityLogger.log(this, "NFC_COMMUNICATION_ERROR", "INCOMING", "NFC_APDU", currentNonce, false, null,
                    -1, nfcRequestStartedNs == 0 ? -1 : SecurityLogger.elapsedMs(nfcRequestStartedNs), null, null, "ERROR", e.getMessage());
            runOnUiThread(() -> tvReceipt.setText("NFC Hatası. Tekrar Deneyin.\n" + e.getMessage()));
        }
    }

    private void processEncryptedResponse(String encryptedText) {
        long decStarted = System.nanoTime();
        String dec = CryptoManager.decrypt(encryptedText);
        double decMs = SecurityLogger.elapsedMs(decStarted);
        if (dec == null) {
            SecurityLogger.log(this, "PAYMENT_RESPONSE_DECRYPT_FAILED", "INCOMING", "CRYPTO", currentNonce, true,
                    "AES/CBC/PKCS5Padding", encryptedText.length(), decMs, null, null, "ERROR", "Could not decrypt customer response");
            return;
        }
        try {
            JSONObject responseJson = new JSONObject(dec);
            if (currentNonce.equals(responseJson.getString("NONCE"))) {
                isPaymentProcessing = true;
                SecurityLogger.log(this, "PAYMENT_RESPONSE_DECRYPTED", "INCOMING", "CRYPTO", currentNonce, true,
                        "AES/CBC/PKCS5Padding", encryptedText.length(), decMs, null, "9000", "SUCCESS", null);
                runOnUiThread(() -> tvReceipt.setText("Jeton Alındı. Server Onayı Bekleniyor..."));
                sendPaymentToServer(responseJson.getString("TOKEN"));
            } else {
                SecurityLogger.log(this, "NONCE_MISMATCH_DETECTED", "INCOMING", "CRYPTO", currentNonce, true,
                        "AES/CBC/PKCS5Padding", encryptedText.length(), decMs, null, null, "REJECTED", "Nonce mismatch in customer response");
            }
        } catch (Exception e) {
            SecurityLogger.log(this, "PAYMENT_RESPONSE_PARSE_FAILED", "INCOMING", "APP", currentNonce, true,
                    "AES/CBC/PKCS5Padding", encryptedText.length(), decMs, null, null, "ERROR", e.getMessage());
        }
    }

    private void sendPaymentToServer(String customerToken) {
        new Thread(() -> {
            long started = System.nanoTime();
            try {
                JSONObject j = new JSONObject();
                j.put("retailer_token", retailerToken);
                j.put("retailer_username", retailerUsername);
                j.put("customer_token", customerToken);
                j.put("amount", etAmount.getText().toString());
                j.put("item_category", spItemType.getSelectedItem().toString());
                j.put("item_name", spItemName.getSelectedItem().toString());
                j.put("nonce", currentNonce);
                j.put("nfc_round_trip_ms", Math.round(lastNfcRoundTripMs * 1000.0) / 1000.0);
                j.put("encryption_algorithm", "AES/CBC/PKCS5Padding");

                JSONObject canonicalPayload = buildCanonicalPayload();
                String payloadHash = sha256Canonical(canonicalPayload);
                j.put("canonical_payload", canonicalPayload);
                if (tamperNextAuthorization) {
                    j.put("payload_hash", "tampered_" + payloadHash);
                    j.put("tamper_test", true);
                    SecurityLogger.log(this, "MITM_TAMPER_TEST_TRIGGERED", "OUTGOING", "HTTP", currentNonce, true,
                            "SHA-256", j.toString().length(), -1, null, null, "TEST", "Payload hash intentionally changed for academic MITM simulation");
                    tamperNextAuthorization = false;
                } else {
                    j.put("payload_hash", payloadHash);
                }
                lastAuthorizationRequest = new JSONObject(j.toString());

                SecurityLogger.log(this, "SERVER_AUTHORIZATION_REQUEST", "OUTGOING", "HTTP", currentNonce, true,
                        "AES/CBC/PKCS5Padding", j.toString().length(), -1, null, null, "PENDING", null);

                Response res = ApiClient.postJson(this, "/api/transactions/authorize", j);
                String body = res.body() != null ? res.body().string() : "{}";
                JSONObject rJson;
                try {
                    rJson = new JSONObject(body);
                } catch (Exception e) {
                    rJson = new JSONObject();
                    rJson.put("error", "Sunucu cevabı JSON formatında değil.");
                }

                double duration = SecurityLogger.elapsedMs(started);
                SecurityLogger.log(this, res.isSuccessful() ? "SERVER_AUTHORIZATION_SUCCESS" : "SERVER_AUTHORIZATION_REJECTED",
                        "INCOMING", "HTTP", currentNonce, true, "AES/CBC/PKCS5Padding", body.length(), duration,
                        String.valueOf(res.code()), null, res.isSuccessful() ? "SUCCESS" : "REJECTED", rJson.optString("error", null));

                final JSONObject finalJson = rJson;
                final boolean success = res.isSuccessful();
                runOnUiThread(() -> {
                    stopNfcScanner();
                    tvAmountToPay.setVisibility(View.GONE);
                    if (success) {
                        tvReceipt.setTextColor(Color.parseColor("#2ECC71"));
                        tvReceipt.setText("ÖDEME BAŞARILI!\n\nReceipt: " + finalJson.optString("receipt_id")
                                + "\nServer doğrulama: " + finalJson.optString("server_validation_ms") + " ms"
                                + "\nNFC süre: " + Math.round(lastNfcRoundTripMs * 1000.0) / 1000.0 + " ms");
                        btnNewTransaction.setText("TAMAM / YENİ ÖDEME");
                    } else {
                        tvReceipt.setTextColor(Color.parseColor("#E74C3C"));
                        tvReceipt.setText("ÖDEME BAŞARISIZ!\n\nHata: " + finalJson.optString("error"));
                        btnNewTransaction.setText("GERİ DÖN");
                        isPaymentProcessing = false;
                    }
                });
            } catch (Exception e) {
                SecurityLogger.log(this, "SERVER_UNREACHABLE", "OUTGOING", "HTTP", currentNonce, true,
                        "AES/CBC/PKCS5Padding", -1, SecurityLogger.elapsedMs(started), null, null, "ERROR", e.getMessage());
                runOnUiThread(() -> {
                    tvReceipt.setText("BAĞLANTI HATASI!\nSunucuya ulaşılamadı.");
                    btnNewTransaction.setText("GERİ DÖN");
                    isPaymentProcessing = false;
                });
            }
        }).start();
    }


    private JSONObject buildCanonicalPayload() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("amount", etAmount.getText().toString().trim());
        obj.put("item_category", spItemType.getSelectedItem().toString());
        obj.put("item_name", spItemName.getSelectedItem().toString());
        obj.put("nonce", currentNonce == null ? "" : currentNonce);
        return obj;
    }

    private String sha256Canonical(JSONObject obj) throws Exception {
        String canonical = "{"
                + "\"amount\":\"" + jsonEscape(obj.optString("amount")) + "\","
                + "\"item_category\":\"" + jsonEscape(obj.optString("item_category")) + "\","
                + "\"item_name\":\"" + jsonEscape(obj.optString("item_name")) + "\","
                + "\"nonce\":\"" + jsonEscape(obj.optString("nonce")) + "\""
                + "}";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void simulateReplayAttack() {
        if (lastAuthorizationRequest == null) {
            tvPosError.setText("Replay testi için önce başarılı veya reddedilmiş bir server authorization denemesi oluşmalı.");
            SecurityLogger.log(this, "REPLAY_TEST_NOT_READY", "LOCAL", "APP", null, false, null, -1, -1, null, null, "ERROR", "No previous authorization payload is available");
            return;
        }
        new Thread(() -> {
            long started = System.nanoTime();
            try {
                JSONObject replayPayload = new JSONObject(lastAuthorizationRequest.toString());
                SecurityLogger.log(this, "REPLAY_ATTACK_TEST_SENT", "OUTGOING", "HTTP", replayPayload.optString("nonce"), true,
                        "AES/CBC/PKCS5Padding", replayPayload.toString().length(), -1, null, null, "TEST", "Re-sending the same nonce and authorization payload");
                Response res = ApiClient.postJson(this, "/api/transactions/authorize", replayPayload);
                String body = res.body() != null ? res.body().string() : "{}";
                double duration = SecurityLogger.elapsedMs(started);
                SecurityLogger.log(this, res.code() == 409 ? "REPLAY_ATTACK_DETECTED" : "REPLAY_ATTACK_TEST_RESULT",
                        "INCOMING", "HTTP", replayPayload.optString("nonce"), true, "AES/CBC/PKCS5Padding",
                        body.length(), duration, String.valueOf(res.code()), null, res.isSuccessful() ? "UNEXPECTED_SUCCESS" : "REJECTED", body);
                runOnUiThread(() -> {
                    tvPosError.setText(res.code() == 409
                            ? "Replay attack testi başarılı: server aynı nonce'u reddetti."
                            : "Replay test sonucu HTTP " + res.code() + ": " + body);
                });
            } catch (Exception e) {
                SecurityLogger.log(this, "REPLAY_ATTACK_TEST_ERROR", "OUTGOING", "HTTP", null, true,
                        "AES/CBC/PKCS5Padding", -1, SecurityLogger.elapsedMs(started), null, null, "ERROR", e.getMessage());
                runOnUiThread(() -> tvPosError.setText("Replay test hatası: " + e.getMessage()));
            }
        }).start();
    }

    private String statusWord(byte[] response) {
        if (response == null || response.length < 2) return "0000";
        return String.format("%02X%02X", response[response.length - 2] & 0xFF, response[response.length - 1] & 0xFF);
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
