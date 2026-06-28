"""Small optional smoke test for the local security server.
Run after the server is already open:
    python api_smoke_test.py
It checks login, security logging, summary, and receipt-list endpoints without Android devices.
"""
import json
import sys
import urllib.error
import urllib.request

BASE_URL = "http://127.0.0.1:5000"


def request_json(method, path, payload=None, token=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(BASE_URL + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=5) as res:
            body = res.read().decode("utf-8")
            return res.status, json.loads(body or "{}")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8")
        try:
            parsed = json.loads(body or "{}")
        except Exception:
            parsed = {"raw": body}
        return exc.code, parsed


def main():
    print("NFC Wallet local server smoke test")
    status, health = request_json("GET", "/api/health")
    print("/api/health:", status, health)
    if status != 200:
        print("Server is not reachable. Start Start_Server.bat first.")
        sys.exit(1)

    status, login = request_json("POST", "/api/auth/login", {"username": "customer1", "password": "1234"})
    print("customer login:", status, login.get("role"), login.get("username"))
    if status != 200:
        sys.exit(1)
    token = login["token"]

    status, log_result = request_json("POST", "/api/security/log", {
        "event_type": "SMOKE_TEST_LOG",
        "actor_role": "CUSTOMER",
        "username": "customer1",
        "direction": "LOCAL",
        "protocol": "HTTP",
        "result": "SUCCESS",
        "error_reason": None
    }, token=token)
    print("security log:", status, log_result)

    status, receipts = request_json("GET", "/api/receipts", token=token)
    print("receipts:", status, "count=", len(receipts.get("receipts", [])))

    status, summary = request_json("GET", "/api/security/summary")
    print("summary:", status, summary)
    print("Smoke test finished.")


if __name__ == "__main__":
    main()
