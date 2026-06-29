[README.md](https://github.com/user-attachments/files/29455709/README.md)
# Secure NFC Wallet Android App

An academic Android NFC wallet and payment prototype developed for a **Network Security** course. The project demonstrates how a **Customer Wallet**, a **Retailer POS**, and a **Local Security Server** can work together in a controlled laboratory environment. The implementation focuses on **NFC Host Card Emulation (HCE)**, **APDU-based communication**, **local API authorization**, **security monitoring**, and **measurable test scenarios**.

> **Important:** This repository is an educational prototype for demonstration and research. It is **not** a production-grade banking or real payment application.

---

## Project Overview

The system is built around three main components:

- **Customer Wallet App (Android):** receives the payment request through NFC, shows transaction details, and lets the user approve or reject the payment.
- **Retailer POS App (Android):** creates the invoice, starts reader mode, sends the encrypted request, and completes authorization with the server.
- **Local Security Server (PC):** validates users and transactions, checks JWT and nonce values, stores receipts, and records security logs.

This architecture was intentionally designed for a course project so that the communication flow, attack scenarios, and logging behavior can be observed clearly during testing and presentation.

---

## System Architecture

The figure below summarizes the interaction among the two Android devices and the local server.

![System Architecture](docs/images/system-architecture.png)

### General Logic

1. The **Retailer POS** creates a payment request.
2. The request is transferred to the **Customer Wallet** over **NFC / APDU**.
3. The customer approves or rejects the request on the second phone.
4. The POS forwards the authorization request to the **Local Security Server** over the local Wi-Fi network.
5. The server validates the request and writes the result to the database and log tables.
6. Both Android sides can display the resulting receipt.

---

## Payment and Authorization Flow

The following diagram presents the end-to-end transaction flow more clearly.

![Payment and Authorization Flow](docs/images/payment-authorization-flow.png)

A simplified sequence-oriented figure from the report is also included below for quick GitHub reading.

![Payment Sequence](docs/images/payment-sequence-simple.png)

### What the Server Checks

Before approving a payment, the server performs several control steps:

- **JWT validation** to verify session authenticity
- **Nonce uniqueness** to detect replay attempts
- **Balance check** to prevent invalid purchases
- **Payload hash verification** to detect tampering or MITM-style changes

If the request is valid, the server stores:

- transaction record
- receipt record
- security log
- alert record when an attack or abnormal event is detected

---

## Key Features

### Android / NFC Features

- NFC Host Card Emulation (HCE)
- APDU request/response flow
- Retailer-side reader mode
- Customer approval / rejection screen
- Receipt history for both customer and retailer
- NFC and HCE availability checks

### Security Features

- AES-protected NFC payload handling
- SHA-based payload integrity checking
- JWT-based login and authorization
- Nonce-based replay protection
- Tamper / MITM simulation detection
- Timeout, reject, and insufficient balance handling
- Detailed event logging for Android and server actions

### Local Server Features

- Flask-based local API
- SQLite storage
- Receipt persistence
- Security event logging
- Attack alert generation
- Simple admin console for demonstration

---

## Security Logging Pipeline

One important contribution of the project is the visibility of security events. The following figure shows how logs move from the Android side to the server-side database and finally to the console.

![Security Log Pipeline](docs/images/security-log-pipeline.png)

Typical logged fields include:

- event name
- role
- nonce
- protocol
- payload size
- duration
- result / status word

This makes the project especially suitable for a **Network Security** demonstration because both successful and abnormal operations can be discussed with evidence.

---

## Experimental Results

The repository also includes the main result visuals used in the report.

### Scenario Decision Accuracy

![Scenario Decision Accuracy](docs/images/scenario-decision-accuracy.png)

This result shows that the application behaved consistently across the tested scenarios. Rejection, replay detection, and balance-related responses were highly reliable in the controlled local test environment, while timeout handling showed slightly lower accuracy because it depends more heavily on user interaction timing.

### Successful Payment Timing

![Successful Payment Timing](docs/images/successful-payment-timing.png)

The timing chart shows that the **NFC round-trip time** is the dominant part of the transaction duration, while encryption, server validation, and local database logging introduce comparatively smaller overhead. This supports the project goal of keeping the design secure while still remaining practical for a local prototype.

---

## Repository Structure

```text
.
├── app/                         Android Studio application module
│   └── src/main/java/...        Android Java source code
├── server/                      Local Flask security server and admin console
│   ├── run_server.py
│   ├── server.py
│   ├── database.py
│   ├── services.py
│   ├── security_utils.py
│   ├── admin_console.py
│   └── Start_Server.bat
├── docs/                        Setup notes, demo notes, and GitHub visuals
│   ├── images/                  Figures used in the README
│   ├── API_ENDPOINTS.md
│   ├── DEMO_SCRIPT_TR.md
│   ├── GITHUB_UPLOAD_STEPS_TR.md
│   └── SETUP_TR.md
├── FINAL_TEST_GUIDE.md          Final testing checklist
├── PROJECT_ARCHITECTURE.md      Technical architecture notes
├── FINAL_DELIVERY_CHECKLIST.md  Submission checklist
├── build.gradle.kts             Project Gradle configuration
├── settings.gradle.kts          Gradle settings
└── gradlew / gradlew.bat        Gradle wrapper scripts
```

---

## Demo Accounts

The local database initializes several test users automatically.

| Username | Password | Role | Initial Balance |
|---|---|---|---:|
| customer1 | 1234 | CUSTOMER | 1000 |
| customer2 | 1234 | CUSTOMER | 50 |
| retailer1 | 1234 | RETAILER | 0 |
| retailer2 | 1234 | RETAILER | 0 |
| admin | admin123 | ADMIN | 0 |

---

## Requirements

### Android Side

- Android Studio
- JDK 17
- At least one NFC-capable Android phone for real testing
- HCE support on the customer phone
- Two phones recommended for full demo

### Server Side

- Python 3.10+
- Windows recommended for included `.bat` scripts
- Flask dependencies listed in `server/requirements.txt`
- PC and phones connected to the same local Wi-Fi network

---

## Quick Start

### 1) Start the Local Security Server

```bat
cd server
pip install -r requirements.txt
Start_Server.bat
```

The server runs at:

```text
http://<PC_LOCAL_IP>:5000
```

### 2) Open the Android Project

Open the **repository root folder** in Android Studio:

```text
NFC_WalletApp_GitHub_Ready/
```

Do **not** open only the `server/` folder as an Android project.

### 3) Build the APK

Using Android Studio:

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

Or from terminal:

```bat
.\gradlew assembleDebug
```

Output path:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 4) Install on Two Phones

- Phone 1: login as **retailer1 / 1234**
- Phone 2: login as **customer1 / 1234**

If necessary, enter the PC local IP and port `5000` manually.

---

## Main Demo Flow

1. Start the local server.
2. Open the admin console.
3. Login as retailer on one Android phone.
4. Login as customer on the second Android phone.
5. Create an invoice from the POS side.
6. Start the NFC payment process.
7. Approve or reject the request on the customer device.
8. Let the POS send authorization to the server.
9. Observe logs, receipts, and alerts.
10. Review stored data from the admin console.

---

## Security Scenarios Included

The project supports demonstration of the following scenarios:

- successful payment
- user rejection
- timeout / no response
- replay detection
- tamper detection
- invalid token rejection
- insufficient balance rejection
- NFC disabled / unsupported device checks

---

## Documentation

Additional documentation is included in the repository:

- [`docs/SETUP_TR.md`](docs/SETUP_TR.md) – Turkish setup guide
- [`docs/DEMO_SCRIPT_TR.md`](docs/DEMO_SCRIPT_TR.md) – Turkish demo script
- [`docs/API_ENDPOINTS.md`](docs/API_ENDPOINTS.md) – API endpoint summary
- [`docs/GITHUB_UPLOAD_STEPS_TR.md`](docs/GITHUB_UPLOAD_STEPS_TR.md) – Turkish GitHub upload guide
- [`PROJECT_ARCHITECTURE.md`](PROJECT_ARCHITECTURE.md) – technical notes
- [`FINAL_TEST_GUIDE.md`](FINAL_TEST_GUIDE.md) – final test instructions

---

## Academic Scope and Limitations

This repository was prepared as a **course project**. Therefore, it prioritizes **clarity, security demonstration, visibility of internal steps, and measurable results** rather than production deployment. It uses a local server and controlled devices so that authentication, APDU exchange, timing behavior, and security events can be presented in a transparent way.

The system does **not** implement production-grade banking controls such as:

- certified EMV payment infrastructure
- production key management
- PCI-DSS compliance
- hardened remote backend deployment
- commercial mobile banking security standards

---

## Safety Notice

Please do **not** use this project for real financial transactions. It is a controlled academic prototype prepared only for learning, demonstration, testing, and reporting purposes.
