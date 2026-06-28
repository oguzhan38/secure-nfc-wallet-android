# Security Policy

This repository is an academic network-security prototype. It is not designed for real financial transactions.

## Intended Scope

The project demonstrates:

- NFC/HCE communication
- APDU command/response logging
- local JWT-based authorization
- nonce-based replay protection
- payload hash validation
- attack scenario logging

## Not Intended For

- real banking or payment processing
- PCI-DSS environments
- production EMV infrastructure
- storing real user credentials or financial data

## Secret Management

Do not commit real keys, keystores, production credentials, or real database files. The `.gitignore` file excludes common Android, Python, database, and keystore outputs.
