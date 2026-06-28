package com.example.nfcwalletapp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.provider.Settings;

public class NfcSecurityUtils {
    public static String getNfcProblem(Context context, boolean requireHce) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
        if (adapter == null) {
            return "Bu cihaz NFC desteklemiyor.";
        }
        if (!adapter.isEnabled()) {
            return "NFC kapalı. Lütfen telefon ayarlarından NFC özelliğini açın.";
        }
        if (requireHce && !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            return "Bu cihaz Host Card Emulation (HCE) desteklemiyor.";
        }
        return null;
    }

    public static void openNfcSettings(Context context) {
        try {
            context.startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
        } catch (Exception e) {
            context.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
