# NFC Wallet Local Security Server

This folder contains the PC-side local backend for the NFC Wallet academic project. It replaces the older single-file demo server with a local authorization and security logging server designed for network-security demonstration.

## Purpose

The server simulates the main responsibilities of a payment authorization backend inside a controlled laboratory network:

- user authentication and JWT creation,
- customer/retailer role validation,
- NFC transaction authorization,
- nonce-based replay protection,
- payload hash validation for MITM/tamper demonstration,
- transaction and receipt storage,
- packet/security/timing logs,
- attack alert collection,
- PC-side admin security console.

## How to run on Windows

1. Open this `server` folder.
2. Install requirements once if needed:

```bat
pip install -r requirements.txt
```

3. Double-click:

```text
Start_Server.bat
```

4. Keep the server window open during Android testing.
5. Make sure the PC and both phones are on the same Wi-Fi network.

## Optional utility scripts

| File | Purpose |
|---|---|
| `Start_Server.bat` | Starts Flask server + admin security console. |
| `Start_Server_Console_Only.bat` | Starts only the Flask API server. |
| `Reset_Database.bat` | Deletes and recreates the demo SQLite database. |
| `Run_API_Smoke_Test.bat` | Runs a small API test after the server is already running. |

## Demo accounts

| Username | Password | Role | Balance |
|---|---:|---|---:|
| customer1 | 1234 | CUSTOMER | 1000 |
| customer2 | 1234 | CUSTOMER | 50 |
| retailer1 | 1234 | RETAILER | 0 |
| retailer2 | 1234 | RETAILER | 0 |
| admin | admin123 | ADMIN | 0 |

## Main API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Server health check. |
| POST | `/api/auth/login` | Login and JWT creation. |
| GET/POST | `/api/auth/me` | Resolve current token. |
| POST | `/api/security/log` | Store Android/server security event. |
| GET | `/api/security/logs` | List packet/security logs. |
| GET | `/api/security/summary` | Dashboard metrics. |
| GET | `/api/security/alerts` | List attack alerts. |
| POST | `/api/transactions/authorize` | Authorize payment. |
| GET | `/api/transactions` | List transaction attempts. |
| GET/POST | `/api/receipts` | List receipts for current token. |
| POST | `/api/transaction` | Legacy compatibility transaction endpoint. |
| POST | `/api/check_notification` | Legacy compatibility notification endpoint. |

## Security events promoted to attack alerts

The server automatically promotes these Android-reported events to `attack_alerts`:

- `REPLAY_ATTACK_DETECTED`
- `MITM_TAMPER_DETECTED`
- `APDU_PAYMENT_REQUEST_DECRYPT_FAILED`
- `PAYMENT_RESPONSE_DECRYPT_FAILED`
- `NONCE_MISMATCH_DETECTED`
- `APDU_UNKNOWN_COMMAND`
- `APDU_PACKET_TOO_LARGE`
- `INVALID_TOKEN`

## Demonstration scenarios

1. Successful NFC payment.
2. Customer reject.
3. Customer approval timeout.
4. Insufficient balance.
5. Replay attack using reused nonce.
6. MITM/tampered payload using invalid SHA-256 hash.
7. NFC disabled / unsupported warning.
8. Receipt history for customer and retailer.

## Troubleshooting

- If Android cannot discover the server, manually enter the PC's IPv4 address in the login screen.
- If the PC firewall asks for permission, allow Python/Flask on private networks.
- If the database contains old tests, run `Reset_Database.bat`.
- If `ModuleNotFoundError: flask` appears, run `pip install -r requirements.txt`.
- If server runs but receipts are unauthorized, log out/in again on the Android app to refresh JWT token storage.
