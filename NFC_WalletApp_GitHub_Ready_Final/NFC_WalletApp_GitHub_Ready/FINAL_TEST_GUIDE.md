# NFC Wallet Final Test Guide

This guide explains how to demonstrate the final NFC Wallet academic project after the Android app is installed on two NFC-capable Android phones.

## 1. Required devices

- Windows PC/laptop connected to the same Wi-Fi network as the phones.
- Phone A: Retailer POS device.
- Phone B: Customer Wallet device with NFC and HCE support.
- Both phones must have NFC enabled.

## 2. Start the local security server

1. Open `NFC_WalletApp/server`.
2. Double-click `Start_Server.bat`.
3. If Python dependencies are missing, run:

```bat
pip install -r requirements.txt
```

4. The PC console should show the Flask server running on port `5000`.
5. The admin security console opens and shows dashboard, users, transactions, receipts, logs, and alerts.

Optional checks:

- `Reset_Database.bat`: recreates a clean demo database.
- `Run_API_Smoke_Test.bat`: checks `/api/health`, login, security log, receipts, and summary endpoints.

## 3. Demo accounts

| Role | Username | Password |
|---|---|---|
| Customer | customer1 | 1234 |
| Customer with low balance | customer2 | 1234 |
| Retailer | retailer1 | 1234 |
| Retailer | retailer2 | 1234 |
| Admin | admin | admin123 |

## 4. Happy-path NFC payment

1. On Phone A, log in as `retailer1 / 1234`.
2. On Phone B, log in as `customer1 / 1234`.
3. On the retailer POS, choose item category, item name, and amount.
4. Press the NFC payment button.
5. Touch the two phones back-to-back.
6. On the customer phone, approve the payment.
7. Wait for the retailer phone to display successful payment and receipt information.
8. Open the server security console and check:
   - `Security Logs`: NFC APDU, encryption, nonce, and HTTP events.
   - `Transactions / Receipts`: successful transaction and generated receipt.
   - `Dashboard`: success counter and timing values.

## 5. Customer rejection scenario

1. Start a payment from the retailer phone.
2. Touch the devices.
3. On the customer phone, press Reject.
4. The retailer phone should display rejected payment.
5. The server logs should contain customer rejection / APDU status information.

## 6. Timeout scenario

1. Start a payment from the retailer phone.
2. Touch the devices.
3. On the customer phone, press the timeout test button or do not approve/reject.
4. The retailer phone waits for approval and then shows timeout.
5. The server security logs should contain `CUSTOMER_APPROVAL_TIMEOUT`.

## 7. Replay attack scenario

1. Complete a normal payment or authorization attempt first.
2. On the retailer phone, press `TEST: SON ISLEMI REPLAY GONDER`.
3. The server should reject the duplicate nonce.
4. The server attack alerts should show `REPLAY_ATTACK_DETECTED`.

## 8. MITM / tampered payload scenario

1. On the retailer phone, press `TEST: MITM / TAMPER PAYLOAD`.
2. Start the next payment normally.
3. The retailer app intentionally sends a wrong SHA-256 payload hash to the server.
4. The server rejects the transaction.
5. The admin console should show `MITM_TAMPER_DETECTED` in attack alerts.

## 9. Insufficient balance scenario

1. Log in customer phone as `customer2 / 1234`.
2. Start a transaction higher than customer2's balance.
3. Approve from the customer phone.
4. The server should reject the transaction as `INSUFFICIENT_BALANCE`.

## 10. NFC disabled scenario

1. Disable NFC on one phone.
2. Try to start or receive an NFC transaction.
3. The app should display an NFC warning and allow opening NFC settings.
4. The server logs should contain an NFC precheck/security event if the app can report it.

## 11. What to capture for the report

Take screenshots of:

- Login screen.
- Retailer POS screen.
- Customer approval screen.
- Successful payment receipt.
- Receipt history screen on customer and retailer.
- Server dashboard.
- Security logs table with APDU/status/timing rows.
- Attack alerts table for replay and tamper tests.
- Failed payment screen for insufficient balance or rejection.

## 12. Troubleshooting

- If phones cannot find the server automatically, enter the PC's local IPv4 address manually in the login screen.
- Make sure the PC firewall allows Python/Flask on port `5000`.
- Make sure all devices are on the same Wi-Fi network.
- If the database becomes messy during testing, run `Reset_Database.bat`.
- If app login works but receipt loading fails, verify the JWT token was created by logging in again.
