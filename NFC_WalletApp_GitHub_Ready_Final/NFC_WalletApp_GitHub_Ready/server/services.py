import json
import time
from typing import Any, Dict, Optional, Tuple
from database import get_db, dict_rows
from security_utils import create_token, decode_token, safe_float, sha256_json, verify_password

SUCCESS = "SUCCESS"
REJECTED = "REJECTED"

def write_security_log(event_type: str, **kwargs) -> int:
    allowed = {
        "actor_role", "username", "direction", "protocol", "endpoint", "nonce", "encrypted", "algorithm",
        "payload_size_bytes", "duration_ms", "status_code", "status_word", "result", "error_reason", "raw_summary"
    }
    values = {key: kwargs.get(key) for key in allowed}
    if isinstance(values.get("raw_summary"), (dict, list)):
        values["raw_summary"] = json.dumps(values["raw_summary"], ensure_ascii=False)
    with get_db() as conn:
        cur = conn.execute(
            """
            INSERT INTO security_logs(event_type, actor_role, username, direction, protocol, endpoint, nonce,
              encrypted, algorithm, payload_size_bytes, duration_ms, status_code, status_word, result, error_reason, raw_summary)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                event_type, values["actor_role"], values["username"], values["direction"], values["protocol"], values["endpoint"],
                values["nonce"], int(bool(values["encrypted"])), values["algorithm"], values["payload_size_bytes"], values["duration_ms"],
                values["status_code"], values["status_word"], values["result"], values["error_reason"], values["raw_summary"],
            ),
        )
        return int(cur.lastrowid)

def create_attack_alert(alert_type: str, severity: str, description: str, nonce: Optional[str] = None,
                        username: Optional[str] = None, related_log_id: Optional[int] = None) -> int:
    with get_db() as conn:
        cur = conn.execute(
            "INSERT INTO attack_alerts(alert_type, severity, nonce, username, description, related_log_id) VALUES(?, ?, ?, ?, ?, ?)",
            (alert_type, severity, nonce, username, description, related_log_id),
        )
        return int(cur.lastrowid)

def authenticate(username: str, password: str) -> Tuple[bool, Dict[str, Any], int]:
    with get_db() as conn:
        row = conn.execute("SELECT * FROM users WHERE username=? AND is_active=1", (username,)).fetchone()
    if not row or not verify_password(password, row["password_hash"]):
        log_id = write_security_log(
            "LOGIN_FAILED", username=username, endpoint="/api/auth/login", protocol="HTTP", direction="INCOMING",
            result="REJECTED", error_reason="Invalid username or password", status_code="401"
        )
        return False, {"error": "Invalid username or password"}, 401
    token = create_token(row["id"], row["username"], row["role"])
    write_security_log(
        "LOGIN_SUCCESS", actor_role=row["role"], username=row["username"], endpoint="/api/auth/login",
        protocol="HTTP", direction="INCOMING", result="SUCCESS", status_code="200"
    )
    return True, {
        "message": "Success",
        "token": token,
        "user_id": row["id"],
        "username": row["username"],
        "role": row["role"],
        "balance": row["balance"],
    }, 200

def get_user_by_token(token: str):
    payload = decode_token(token)
    with get_db() as conn:
        row = conn.execute("SELECT id, username, role, balance, is_active FROM users WHERE id=?", (payload["user_id"],)).fetchone()
    if not row or row["is_active"] != 1:
        raise ValueError("User not found or inactive")
    return dict(row)

def _reject_transaction(reason: str, nonce: str, retailer_user: Optional[dict], customer_user: Optional[dict], amount: float,
                        item_category: str, item_name: str, status_code: int = 400, alert_type: Optional[str] = None,
                        severity: str = "MEDIUM", nfc_round_trip_ms=None, server_validation_ms=None,
                        encryption_algorithm=None, payload_hash=None):
    retailer_id = retailer_user["id"] if retailer_user else None
    customer_id = customer_user["id"] if customer_user else None
    username = retailer_user["username"] if retailer_user else None
    inserted = False
    try:
        with get_db() as conn:
            conn.execute(
                """
                INSERT INTO transactions(customer_id, retailer_id, amount, item_category, item_name, status, reject_reason, nonce,
                  nfc_round_trip_ms, server_validation_ms, encryption_algorithm, payload_hash)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (customer_id, retailer_id, amount, item_category, item_name, REJECTED, reason, nonce,
                 nfc_round_trip_ms, server_validation_ms, encryption_algorithm, payload_hash),
            )
            inserted = True
    except Exception:
        pass
    log_id = write_security_log(
        "SERVER_AUTHORIZATION_REJECTED", actor_role="RETAILER", username=username, direction="INCOMING", protocol="HTTP",
        endpoint="/api/transactions/authorize", nonce=nonce, encrypted=True, algorithm=encryption_algorithm,
        duration_ms=server_validation_ms, result="REJECTED", error_reason=reason, status_code=str(status_code),
        raw_summary={"inserted_transaction_record": inserted}
    )
    if alert_type:
        create_attack_alert(alert_type, severity, reason, nonce=nonce, username=username, related_log_id=log_id)
    return {"status": "REJECTED", "error": reason, "nonce": nonce}, status_code

def authorize_transaction(data: Dict[str, Any]) -> Tuple[Dict[str, Any], int]:
    started = time.perf_counter()
    nonce = data.get("nonce") or data.get("transaction_id")
    amount = safe_float(data.get("amount"), -1)
    item_category = data.get("item_category") or data.get("item_type") or "Unknown"
    item_name = data.get("item_name") or "Unknown"
    encryption_algorithm = data.get("encryption_algorithm") or data.get("algorithm") or "AES/CBC/PKCS5Padding"
    payload_hash = data.get("payload_hash")
    nfc_round_trip_ms = data.get("nfc_round_trip_ms")

    if not nonce:
        return {"status": "REJECTED", "error": "Missing nonce"}, 400
    if amount <= 0:
        return {"status": "REJECTED", "error": "Invalid amount"}, 400

    try:
        retailer_token = data.get("retailer_token")
        customer_token = data.get("customer_token")
        retailer_user = get_user_by_token(retailer_token) if retailer_token else None
        customer_user = get_user_by_token(customer_token) if customer_token else None
    except Exception as exc:
        validation_ms = (time.perf_counter() - started) * 1000
        return _reject_transaction(
            f"Invalid token: {exc}", nonce, None, None, amount, item_category, item_name, 401,
            alert_type="INVALID_TOKEN", severity="HIGH", server_validation_ms=validation_ms,
            encryption_algorithm=encryption_algorithm, payload_hash=payload_hash
        )

    if retailer_user["role"] != "RETAILER":
        validation_ms = (time.perf_counter() - started) * 1000
        return _reject_transaction("Retailer token does not belong to a retailer", nonce, retailer_user, customer_user, amount,
                                   item_category, item_name, 403, "INVALID_ROLE", "HIGH", nfc_round_trip_ms,
                                   validation_ms, encryption_algorithm, payload_hash)
    if customer_user["role"] != "CUSTOMER":
        validation_ms = (time.perf_counter() - started) * 1000
        return _reject_transaction("Customer token does not belong to a customer", nonce, retailer_user, customer_user, amount,
                                   item_category, item_name, 403, "INVALID_ROLE", "HIGH", nfc_round_trip_ms,
                                   validation_ms, encryption_algorithm, payload_hash)

    # Optional academic tamper check: if the app sends a canonical_payload and payload_hash, verify it.
    canonical_payload = data.get("canonical_payload")
    if isinstance(canonical_payload, dict) and payload_hash:
        expected_hash = sha256_json(canonical_payload)
        if expected_hash != payload_hash:
            validation_ms = (time.perf_counter() - started) * 1000
            return _reject_transaction("MITM/tamper detected: payload hash mismatch", nonce, retailer_user, customer_user, amount,
                                       item_category, item_name, 400, "MITM_TAMPER_DETECTED", "CRITICAL", nfc_round_trip_ms,
                                       validation_ms, encryption_algorithm, payload_hash)

    with get_db() as conn:
        replay = conn.execute("SELECT id FROM transactions WHERE nonce=?", (nonce,)).fetchone()
    if replay:
        validation_ms = (time.perf_counter() - started) * 1000
        return _reject_transaction("Replay attack detected: nonce was already used", nonce, retailer_user, customer_user, amount,
                                   item_category, item_name, 409, "REPLAY_ATTACK_DETECTED", "CRITICAL", nfc_round_trip_ms,
                                   validation_ms, encryption_algorithm, payload_hash)

    if customer_user["balance"] < amount:
        validation_ms = (time.perf_counter() - started) * 1000
        return _reject_transaction("Insufficient balance", nonce, retailer_user, customer_user, amount, item_category,
                                   item_name, 400, "INSUFFICIENT_BALANCE", "LOW", nfc_round_trip_ms, validation_ms,
                                   encryption_algorithm, payload_hash)

    validation_ms = (time.perf_counter() - started) * 1000
    with get_db() as conn:
        conn.execute("UPDATE users SET balance = balance - ? WHERE id=?", (amount, customer_user["id"]))
        conn.execute("UPDATE users SET balance = balance + ? WHERE id=?", (amount, retailer_user["id"]))
        cur = conn.execute(
            """
            INSERT INTO transactions(customer_id, retailer_id, amount, item_category, item_name, status, nonce,
              nfc_round_trip_ms, server_validation_ms, encryption_algorithm, payload_hash)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (customer_user["id"], retailer_user["id"], amount, item_category, item_name, SUCCESS, nonce,
             nfc_round_trip_ms, validation_ms, encryption_algorithm, payload_hash),
        )
        tx_id = int(cur.lastrowid)
        receipt_no = f"REC-{int(time.time())}-{tx_id:04d}"
        security_summary = json.dumps({
            "encrypted_payload": True,
            "algorithm": encryption_algorithm,
            "nonce_verified": True,
            "token_verified": True,
            "replay_check": "PASSED",
            "server_validation_ms": round(validation_ms, 3),
            "nfc_round_trip_ms": nfc_round_trip_ms,
        }, ensure_ascii=False)
        conn.execute(
            """
            INSERT INTO receipts(transaction_id, customer_id, retailer_id, receipt_no, amount, item_category, item_name, nonce, security_summary)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (tx_id, customer_user["id"], retailer_user["id"], receipt_no, amount, item_category, item_name, nonce, security_summary),
        )
    write_security_log(
        "SERVER_AUTHORIZATION_SUCCESS", actor_role="RETAILER", username=retailer_user["username"], direction="INCOMING",
        protocol="HTTP", endpoint="/api/transactions/authorize", nonce=nonce, encrypted=True, algorithm=encryption_algorithm,
        duration_ms=validation_ms, result="SUCCESS", status_code="200",
        raw_summary={"transaction_id": tx_id, "receipt_no": receipt_no, "customer": customer_user["username"]}
    )
    return {
        "status": "SUCCESS",
        "transaction_id": tx_id,
        "receipt_id": receipt_no,
        "amount_deducted": amount,
        "server_validation_ms": round(validation_ms, 3),
    }, 200

def list_security_logs(limit: int = 200):
    with get_db() as conn:
        rows = conn.execute("SELECT * FROM security_logs ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    return dict_rows(rows)

def list_transactions(limit: int = 200):
    with get_db() as conn:
        rows = conn.execute(
            """
            SELECT t.*, cu.username AS customer_username, ru.username AS retailer_username
            FROM transactions t
            LEFT JOIN users cu ON cu.id=t.customer_id
            LEFT JOIN users ru ON ru.id=t.retailer_id
            ORDER BY t.id DESC LIMIT ?
            """, (limit,)
        ).fetchall()
    return dict_rows(rows)

def list_receipts_for_user(user: dict, limit: int = 100):
    column = "customer_id" if user["role"] == "CUSTOMER" else "retailer_id"
    with get_db() as conn:
        rows = conn.execute(
            f"""
            SELECT r.*, cu.username AS customer_username, ru.username AS retailer_username
            FROM receipts r
            LEFT JOIN users cu ON cu.id=r.customer_id
            LEFT JOIN users ru ON ru.id=r.retailer_id
            WHERE r.{column}=?
            ORDER BY r.id DESC LIMIT ?
            """, (user["id"], limit)
        ).fetchall()
    return dict_rows(rows)

def list_attack_alerts(limit: int = 200):
    with get_db() as conn:
        rows = conn.execute("SELECT * FROM attack_alerts ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    return dict_rows(rows)

def security_summary():
    with get_db() as conn:
        total_users = conn.execute("SELECT COUNT(*) AS c FROM users").fetchone()["c"]
        success = conn.execute("SELECT COUNT(*) AS c FROM transactions WHERE status='SUCCESS'").fetchone()["c"]
        rejected = conn.execute("SELECT COUNT(*) AS c FROM transactions WHERE status='REJECTED'").fetchone()["c"]
        alerts = conn.execute("SELECT COUNT(*) AS c FROM attack_alerts").fetchone()["c"]
        avg_nfc = conn.execute("SELECT AVG(nfc_round_trip_ms) AS v FROM transactions WHERE nfc_round_trip_ms IS NOT NULL").fetchone()["v"]
        avg_srv = conn.execute("SELECT AVG(server_validation_ms) AS v FROM transactions WHERE server_validation_ms IS NOT NULL").fetchone()["v"]
    return {
        "total_users": total_users,
        "successful_transactions": success,
        "rejected_transactions": rejected,
        "attack_alerts": alerts,
        "average_nfc_round_trip_ms": avg_nfc,
        "average_server_validation_ms": avg_srv,
    }
