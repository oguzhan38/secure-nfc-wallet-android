# API Endpoints

The local security server exposes the following main API endpoints.

## Health

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/health` | Server health check |

## Authentication

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/login` | Login customer, retailer, or admin |
| GET/POST | `/api/auth/me` | Resolve current JWT token |

## Transactions

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/transactions/authorize` | Validate and authorize payment transaction |
| GET | `/api/transactions` | List transaction records |
| POST | `/api/transaction` | Compatibility endpoint for older app flow |

## Receipts

| Method | Endpoint | Description |
|---|---|---|
| GET/POST | `/api/receipts` | List receipts for authenticated user |
| GET | `/api/receipts/customer` | List customer receipts |
| GET | `/api/receipts/retailer` | List retailer receipts |

## Security Logs

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/security/log` | Store security event from Android app |
| GET | `/api/security/logs` | List security event logs |
| GET | `/api/security/summary` | Security dashboard summary |
| GET | `/api/security/alerts` | List attack alerts |
