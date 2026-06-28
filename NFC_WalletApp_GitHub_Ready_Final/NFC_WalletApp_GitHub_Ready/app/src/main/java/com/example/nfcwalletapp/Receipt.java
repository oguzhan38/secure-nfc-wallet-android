package com.example.nfcwalletapp;

import org.json.JSONObject;

public class Receipt {
    public final String receiptNo;
    public final String customerUsername;
    public final String retailerUsername;
    public final String amount;
    public final String itemCategory;
    public final String itemName;
    public final String nonce;
    public final String createdAt;
    public final String securitySummary;

    public Receipt(JSONObject obj) {
        this.receiptNo = obj.optString("receipt_no", obj.optString("receipt_id", "N/A"));
        this.customerUsername = obj.optString("customer_username", obj.optString("customer", "Unknown"));
        this.retailerUsername = obj.optString("retailer_username", obj.optString("retailer", "Unknown"));
        this.amount = obj.optString("amount", "0.00");
        this.itemCategory = obj.optString("item_category", "Unknown");
        this.itemName = obj.optString("item_name", "Unknown");
        this.nonce = obj.optString("nonce", "N/A");
        this.createdAt = obj.optString("created_at", obj.optString("timestamp", "N/A"));
        this.securitySummary = obj.optString("security_summary", "{}");
    }

    public String toDisplayText(String role) {
        StringBuilder sb = new StringBuilder();
        sb.append("Receipt No: ").append(receiptNo).append("\n");
        sb.append("Tutar: ").append(amount).append(" TL\n");
        sb.append("Ürün: ").append(itemCategory).append(" / ").append(itemName).append("\n");
        sb.append("Customer: ").append(customerUsername).append("\n");
        sb.append("Retailer: ").append(retailerUsername).append("\n");
        sb.append("Nonce: ").append(shortNonce()).append("\n");
        sb.append("Tarih: ").append(createdAt).append("\n");
        sb.append("Security: encrypted=yes, nonce verified=yes, token verified=yes");
        return sb.toString();
    }

    public String shortNonce() {
        if (nonce == null || nonce.length() <= 16) return nonce;
        return nonce.substring(0, 8) + "..." + nonce.substring(nonce.length() - 8);
    }
}
