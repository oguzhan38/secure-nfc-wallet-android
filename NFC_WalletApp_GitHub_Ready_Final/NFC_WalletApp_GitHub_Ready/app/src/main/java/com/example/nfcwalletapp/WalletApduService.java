package com.example.nfcwalletapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class WalletApduService extends HostApduService {
    private static final String TAG = "NFC_WALLET_HCE";

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (commandApdu == null || commandApdu.length < 4) {
            SecurityLogger.log(this, "APDU_INVALID_COMMAND", "INCOMING", "NFC_APDU", null, false, null,
                    commandApdu == null ? -1 : commandApdu.length, -1, null, "6F00", "ERROR", "APDU is null or too short");
            return hexStringToByteArray("6F00");
        }

        String hexCommand = bytesToHex(commandApdu);

        if (hexCommand.startsWith("00A40400")) {
            SecurityLogger.log(this, "APDU_SELECT_RECEIVED", "INCOMING", "NFC_APDU", null, false, null,
                    commandApdu.length, -1, null, "9000", "SUCCESS", null);
            return hexStringToByteArray("9000");
        }

        if (commandApdu[0] == (byte) 0x80 && commandApdu[1] == 0x01) {
            return handlePaymentRequest(commandApdu);
        }

        if (commandApdu[0] == (byte) 0x80 && commandApdu[1] == 0x02) {
            return handleStatusPoll(commandApdu);
        }

        SecurityLogger.log(this, "APDU_UNKNOWN_COMMAND", "INCOMING", "NFC_APDU", null, false, null,
                commandApdu.length, -1, null, "6D00", "ERROR", "Unknown APDU command");
        return hexStringToByteArray("6D00");
    }

    private byte[] handlePaymentRequest(byte[] commandApdu) {
        long started = System.nanoTime();
        try {
            int length = (commandApdu.length > 4) ? (commandApdu[4] & 0xFF) : 0;
            if (length <= 0 || commandApdu.length < 5 + length) {
                SecurityLogger.log(this, "APDU_PAYMENT_REQUEST_INVALID", "INCOMING", "NFC_APDU", null, true,
                        "AES/CBC/PKCS5Padding", commandApdu.length, SecurityLogger.elapsedMs(started), null, "6F00", "ERROR", "Invalid APDU Lc length");
                return hexStringToByteArray("6F00");
            }

            byte[] data = Arrays.copyOfRange(commandApdu, 5, 5 + length);
            String encryptedStr = new String(data, StandardCharsets.UTF_8);
            String dec = CryptoManager.decrypt(encryptedStr);

            if (dec != null) {
                JSONObject req = new JSONObject(dec);
                String amt = req.optString("amt");
                String name = req.optString("name");
                String category = req.optString("category", "Unknown");
                String retailer = req.optString("retailer", "Unknown");
                String nonce = req.optString("nonce");

                SharedPreferences prefs = getSharedPreferences("NFCWalletPrefs", MODE_PRIVATE);
                prefs.edit()
                        .putString("Pending_Nonce", nonce)
                        .putString("TX_STATUS", CustomerActivity.TX_STATUS_PENDING)
                        .remove("Approved_Nonce")
                        .remove("Rejected_Nonce")
                        .apply();

                SecurityLogger.log(this, "APDU_PAYMENT_REQUEST_RECEIVED", "INCOMING", "NFC_APDU", nonce, true,
                        "AES/CBC/PKCS5Padding", encryptedStr.length(), SecurityLogger.elapsedMs(started), null, "9001", "PENDING", null);

                Intent intent = new Intent(this, CustomerActivity.class);
                intent.setAction("com.example.nfcwalletapp.ACTION_PAYMENT_REQUEST");
                intent.putExtra("AMOUNT", amt);
                intent.putExtra("ITEM_NAME", name);
                intent.putExtra("CATEGORY", category);
                intent.putExtra("RETAILER", retailer);
                intent.putExtra("NONCE", nonce);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

                return hexStringToByteArray("9001");
            }

            SecurityLogger.log(this, "APDU_PAYMENT_REQUEST_DECRYPT_FAILED", "INCOMING", "NFC_APDU", null, true,
                    "AES/CBC/PKCS5Padding", encryptedStr.length(), SecurityLogger.elapsedMs(started), null, "6F00", "ERROR", "Could not decrypt payment request");
        } catch (Exception e) {
            Log.e(TAG, "Error processing REQ APDU", e);
            SecurityLogger.log(this, "APDU_PAYMENT_REQUEST_ERROR", "INCOMING", "NFC_APDU", null, true,
                    "AES/CBC/PKCS5Padding", commandApdu.length, SecurityLogger.elapsedMs(started), null, "6F00", "ERROR", e.getMessage());
        }
        return hexStringToByteArray("6F00");
    }

    private byte[] handleStatusPoll(byte[] commandApdu) {
        long started = System.nanoTime();
        String reqNonce = null;
        try {
            int length = (commandApdu.length > 4) ? (commandApdu[4] & 0xFF) : 0;
            if (length > 0 && commandApdu.length >= 5 + length) {
                byte[] nonceData = Arrays.copyOfRange(commandApdu, 5, 5 + length);
                reqNonce = new String(nonceData, StandardCharsets.UTF_8);

                SharedPreferences prefs = getSharedPreferences("NFCWalletPrefs", MODE_PRIVATE);
                String approvedNonce = prefs.getString("Approved_Nonce", "");
                String rejectedNonce = prefs.getString("Rejected_Nonce", "");
                String txStatus = prefs.getString("TX_STATUS", CustomerActivity.TX_STATUS_NONE);

                if (CustomerActivity.TX_STATUS_REJECTED.equals(txStatus) && rejectedNonce.equals(reqNonce)) {
                    prefs.edit().remove("Rejected_Nonce").putString("TX_STATUS", CustomerActivity.TX_STATUS_NONE).apply();
                    SecurityLogger.log(this, "APDU_STATUS_REJECTED_SENT", "OUTGOING", "NFC_APDU", reqNonce, false,
                            null, commandApdu.length, SecurityLogger.elapsedMs(started), null, "9002", "REJECTED", "Customer rejected payment");
                    return hexStringToByteArray("9002");
                }

                if (!approvedNonce.isEmpty() && approvedNonce.equals(reqNonce)) {
                    String token = prefs.getString("jwt_token", null);
                    if (token == null) {
                        SecurityLogger.log(this, "APDU_STATUS_TOKEN_MISSING", "OUTGOING", "NFC_APDU", reqNonce, false,
                                null, commandApdu.length, SecurityLogger.elapsedMs(started), null, "6985", "ERROR", "JWT token missing on customer device");
                        return hexStringToByteArray("6985");
                    }

                    JSONObject securePayload = new JSONObject();
                    securePayload.put("TOKEN", token);
                    securePayload.put("NONCE", reqNonce);
                    securePayload.put("TS", System.currentTimeMillis());

                    long encStarted = System.nanoTime();
                    String enc = CryptoManager.encrypt(securePayload.toString());
                    double encMs = SecurityLogger.elapsedMs(encStarted);
                    if (enc != null) {
                        byte[] responseBytes = enc.getBytes(StandardCharsets.UTF_8);
                        prefs.edit()
                                .remove("Approved_Nonce")
                                .remove("Pending_Nonce")
                                .putString("TX_STATUS", CustomerActivity.TX_STATUS_NONE)
                                .apply();

                        SecurityLogger.log(this, "APDU_STATUS_APPROVED_SENT", "OUTGOING", "NFC_APDU", reqNonce, true,
                                "AES/CBC/PKCS5Padding", responseBytes.length, encMs, null, "9000", "SUCCESS", null);
                        return concatArrays(responseBytes, hexStringToByteArray("9000"));
                    }
                }

                SecurityLogger.log(this, "APDU_STATUS_PENDING_SENT", "OUTGOING", "NFC_APDU", reqNonce, false,
                        null, commandApdu.length, SecurityLogger.elapsedMs(started), null, "9001", "PENDING", null);
                return hexStringToByteArray("9001");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing STATUS APDU", e);
            SecurityLogger.log(this, "APDU_STATUS_ERROR", "OUTGOING", "NFC_APDU", reqNonce, false,
                    null, commandApdu.length, SecurityLogger.elapsedMs(started), null, "6F00", "ERROR", e.getMessage());
        }
        return hexStringToByteArray("9001");
    }

    @Override
    public void onDeactivated(int reason) {
        SecurityLogger.log(this, "HCE_DEACTIVATED", "LOCAL", "NFC_HCE", null, false, null, -1, -1, null, null, "INFO", "reason=" + reason);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private byte[] concatArrays(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
