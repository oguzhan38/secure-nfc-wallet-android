package com.example.nfcwalletapp;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoManager {
    // 32 chars = 256 bits for AES-256
    private static final String AES_KEY = "NFC_WALLET_SECURE_KEY_2026_DEU_C"; 
    
    public static String encrypt(String value) {
        try {
            // Generate random 16-byte IV
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES"), new IvParameterSpec(iv));
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            
            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception ex) { 
            ex.printStackTrace();
            return null; 
        }
    }

    public static String decrypt(String encryptedCombined) {
        try {
            byte[] combined = Base64.decode(encryptedCombined, Base64.NO_WRAP);
            if (combined.length < 16) return null;
            
            // Extract IV
            byte[] iv = Arrays.copyOfRange(combined, 0, 16);
            byte[] cipherText = Arrays.copyOfRange(combined, 16, combined.length);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES"), new IvParameterSpec(iv));
            
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception ex) { 
            ex.printStackTrace();
            return null; 
        }
    }
}