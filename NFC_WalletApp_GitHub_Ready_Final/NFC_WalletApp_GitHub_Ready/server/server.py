import json
import socket
import threading
import time
from flask import Flask, jsonify, request
from config import BROADCAST_PORT, DEFAULT_PORT, DISCOVERY_SERVICE_NAME, SERVER_HOST
from database import dict_rows, get_db, init_db, seed_demo_data
from security_utils import decode_token, extract_token_from_request
from services import (
    authenticate,
    authorize_transaction,
    get_user_by_token,
    list_receipts_for_user,
    list_security_logs,
    list_transactions,
    list_attack_alerts,
    create_attack_alert,
    security_summary,
    write_security_log,
)

app = Flask(__name__)
_broadcast_running = False

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def udp_broadcaster(port: int):
    global _broadcast_running
    _broadcast_running = True
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    while _broadcast_running:
        try:
            msg = json.dumps({"service": DISCOVERY_SERVICE_NAME, "ip": get_local_ip(), "port": port}).encode("utf-8")
            sock.sendto(msg, ("255.255.255.255", BROADCAST_PORT))
        except Exception:
            pass
        time.sleep(2)

def create_app():
    init_db()
    seed_demo_data()
    return app

@app.route("/api/health", methods=["GET"])
def health():
    return jsonify({"status": "OK", "service": "NFC Wallet Local Security Server", "ip": get_local_ip()}), 200

@app.route("/api/auth/login", methods=["POST"])
def login():
    data = request.get_json(silent=True) or {}
    ok, payload, code = authenticate(data.get("username", ""), data.get("password", ""))
    return jsonify(payload), code

@app.route("/api/auth/me", methods=["GET", "POST"])
def me():
    try:
        token = extract_token_from_request(request)
        user = get_user_by_token(token)
        return jsonify(user), 200
    except Exception as exc:
        return jsonify({"error": str(exc)}), 401

@app.route("/api/security/log", methods=["POST"])
def security_log():
    data = request.get_json(silent=True) or {}
    event_type = data.get("event_type", "UNKNOWN_EVENT")
    log_id = write_security_log(
        event_type,
        actor_role=data.get("actor_role") or data.get("role"),
        username=data.get("username"),
        direction=data.get("direction"),
        protocol=data.get("protocol"),
        endpoint=data.get("endpoint"),
        nonce=data.get("nonce"),
        encrypted=data.get("encrypted", False),
        algorithm=data.get("algorithm"),
        payload_size_bytes=data.get("payload_size_bytes"),
        duration_ms=data.get("duration_ms"),
        status_code=data.get("status_code"),
        status_word=data.get("status_word"),
        result=data.get("result"),
        error_reason=data.get("error_reason"),
        raw_summary=data.get("raw_summary") or data,
    )
    alert_events = {
        "REPLAY_ATTACK_DETECTED": "CRITICAL",
        "MITM_TAMPER_DETECTED": "CRITICAL",
        "APDU_PAYMENT_REQUEST_DECRYPT_FAILED": "HIGH",
        "PAYMENT_RESPONSE_DECRYPT_FAILED": "HIGH",
        "NONCE_MISMATCH_DETECTED": "HIGH",
        "APDU_UNKNOWN_COMMAND": "MEDIUM",
        "APDU_PACKET_TOO_LARGE": "MEDIUM",
        "INVALID_TOKEN": "HIGH",
    }
    if event_type in alert_events:
        create_attack_alert(
            event_type,
            alert_events[event_type],
            data.get("error_reason") or f"Security event reported by Android app: {event_type}",
            nonce=data.get("nonce"),
            username=data.get("username"),
            related_log_id=log_id,
        )
    return jsonify({"status": "LOGGED", "log_id": log_id}), 201

@app.route("/api/security/logs", methods=["GET"])
def security_logs():
    limit = min(int(request.args.get("limit", 200)), 1000)
    return jsonify({"logs": list_security_logs(limit)}), 200


@app.route("/api/security/alerts", methods=["GET"])
def security_alerts():
    limit = min(int(request.args.get("limit", 200)), 1000)
    return jsonify({"alerts": list_attack_alerts(limit)}), 200

@app.route("/api/security/summary", methods=["GET"])
def summary():
    return jsonify(security_summary()), 200

@app.route("/api/transactions/authorize", methods=["POST"])
def transactions_authorize():
    data = request.get_json(silent=True) or {}
    payload, code = authorize_transaction(data)
    return jsonify(payload), code

# Backward-compatible endpoint for the current Android project.
@app.route("/api/transaction", methods=["POST"])
def legacy_transaction():
    data = request.get_json(silent=True) or {}
    # Current app sends retailer_username instead of retailer_token. To preserve compatibility during migration,
    # we allow this only for the old endpoint by resolving the retailer directly.
    if not data.get("retailer_token") and data.get("retailer_username"):
        # Create a short internal token equivalent by reading user and generating failure if not found.
        from security_utils import create_token
        with get_db() as conn:
            row = conn.execute("SELECT id, username, role FROM users WHERE username=? AND role='RETAILER' AND is_active=1", (data.get("retailer_username"),)).fetchone()
        if row:
            data["retailer_token"] = create_token(row["id"], row["username"], row["role"])
    payload, code = authorize_transaction(data)
    return jsonify(payload), code

@app.route("/api/transactions", methods=["GET"])
def transactions_list():
    limit = min(int(request.args.get("limit", 200)), 1000)
    return jsonify({"transactions": list_transactions(limit)}), 200

@app.route("/api/receipts", methods=["GET", "POST"])
def receipts():
    try:
        token = extract_token_from_request(request)
        user = get_user_by_token(token)
        return jsonify({"receipts": list_receipts_for_user(user)}), 200
    except Exception as exc:
        return jsonify({"error": str(exc)}), 401

@app.route("/api/receipts/customer", methods=["GET", "POST"])
def customer_receipts():
    return receipts()

@app.route("/api/receipts/retailer", methods=["GET", "POST"])
def retailer_receipts():
    return receipts()

@app.route("/api/check_notification", methods=["POST"])
def check_notification():
    try:
        token = (request.get_json(silent=True) or {}).get("token")
        user = get_user_by_token(token)
        with get_db() as conn:
            row = conn.execute(
                """
                SELECT t.*, ru.username AS retailer_username
                FROM transactions t
                LEFT JOIN users ru ON ru.id=t.retailer_id
                WHERE t.customer_id=? ORDER BY t.id DESC LIMIT 1
                """, (user["id"],)
            ).fetchone()
        if not row:
            return jsonify({"message": "No transactions"}), 404
        return jsonify({
            "retailer": row["retailer_username"],
            "amount": row["amount"],
            "item_type": row["item_category"],
            "item_name": row["item_name"],
            "status": row["status"],
        }), 200
    except Exception as exc:
        return jsonify({"error": str(exc)}), 401

@app.route("/api/admin/users", methods=["GET", "POST"])
def admin_users():
    if request.method == "GET":
        with get_db() as conn:
            rows = conn.execute("SELECT id, username, role, balance, is_active, created_at FROM users ORDER BY id").fetchall()
        return jsonify({"users": dict_rows(rows)}), 200
    return jsonify({"error": "Use the Admin Console to add users in this academic build."}), 405

def run_flask_server(port: int = DEFAULT_PORT, with_broadcast: bool = True):
    create_app()
    if with_broadcast:
        threading.Thread(target=udp_broadcaster, args=(port,), daemon=True).start()
    app.run(host=SERVER_HOST, port=port, debug=False, use_reloader=False, threaded=True)

if __name__ == "__main__":
    run_flask_server(DEFAULT_PORT)
