package com.example.nfcwalletapp;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ReceiptAdapter {
    public static TextView createReceiptCard(Context context, Receipt receipt, String role) {
        TextView card = new TextView(context);
        card.setText("🧾  " + receipt.toDisplayText(role));
        card.setTextColor(context.getResources().getColor(R.color.text_primary));
        card.setTextSize(14f);
        card.setTypeface(Typeface.MONOSPACE);
        card.setPadding(26, 22, 26, 22);
        card.setGravity(Gravity.START);
        card.setBackgroundResource(R.drawable.bg_card_dark);
        card.setElevation(6f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 18);
        card.setLayoutParams(params);
        return card;
    }
}
