# NFC Wallet Project Architecture

## Overview

The project simulates a secure NFC wallet payment system in a controlled academic local-network environment. It uses two Android roles and a PC-based local security server.

```text
Retailer Android POS  <-- NFC APDU / HCE -->  Customer Android Wallet
          |                                      |
          +------------ HTTP/JSON --------------+
                         |
                         v
              Local Security Server
                         |
                         v
        SQLite database + Admin Security Console
```

## Main components

### Customer Android Wallet

- Runs Android Host Card Emulation service.
- Receives encrypted NFC payment requests.
- Shows payment information to the customer.
- Allows approve, reject, or timeout test behavior.
- Sends encrypted approval token through NFC.
- Displays receipt history.

### Retailer Android POS

- Creates a bill with category, item, and amount.
- Uses NFC reader mode to communicate with the customer phone.
- Generates a unique nonce for each payment attempt.
- Sends encrypted APDU payment request.
- Measures APDU and server round-trip durations.
- Sends authorization request to the local server.
- Supports replay and tamper academic tests.
- Displays receipt history.

### Local Security Server

- Runs on the PC/laptop in the same local network.
- Provides authentication, JWT issuance, transaction authorization, receipt generation, and security logging.
- Stores all records in SQLite.
- Provides an admin security console for demonstration.

## Security mechanisms

| Mechanism | Purpose |
|---|---|
| JWT tokens | Authenticated customer and retailer sessions |
| AES/CBC/PKCS5Padding | Encrypted NFC payload transport |
| Random IV | Prevents deterministic ciphertext for repeated plaintext |
| Nonce per transaction | Replay attack protection |
| SHA-256 payload hash | Academic integrity check for MITM/tamper simulation |
| Server-side authorization | Balance, token, role, nonce, and transaction validation |
| Security logs | Packet/event/timing visibility for network security evaluation |
| Attack alerts | Replay, tamper, invalid-token, and APDU error evidence |
| Receipts | Persistent successful transaction records for both parties |

## Important APDU status words

| Status Word | Meaning |
|---|---|
| 9000 | Success / customer approved |
| 9001 | Pending customer approval |
| 9002 | Customer rejected |
| 6985 | Token/session unavailable |
| 6D00 | Unknown APDU command |
| 6F00 | General error |

## Database tables

| Table | Purpose |
|---|---|
| users | Customer, retailer, and admin records |
| transactions | Successful and rejected transaction attempts |
| receipts | Successful receipt records |
| security_logs | APDU, HTTP, crypto, NFC, and timing logs |
| attack_alerts | Replay, MITM/tamper, token, and APDU alerts |

## Academic positioning

The project does not claim to replace a real banking/payment infrastructure. Instead, the local server simulates the key responsibilities of an authorization backend in a reproducible laboratory setting. This makes packet logging, timing analysis, attack simulation, and evidence collection visible for a network security course project.

## Visual References

- Main architecture figure: `docs/images/system-architecture.png`
- Payment authorization flow: `docs/images/payment-authorization-flow.png`
- Security log pipeline: `docs/images/security-log-pipeline.png`
