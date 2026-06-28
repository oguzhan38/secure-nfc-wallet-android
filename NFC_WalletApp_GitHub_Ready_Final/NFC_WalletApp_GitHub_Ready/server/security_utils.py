import base64
import datetime as _dt
import hashlib
import hmac
import json
import os
from typing import Any, Dict, Optional
from config import SECRET_KEY, TOKEN_EXP_HOURS

try:
    import jwt as _pyjwt
except Exception:  # PyJWT may not be installed on every lab PC.
    _pyjwt = None

PBKDF2_ITERATIONS = 120_000

def hash_password(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS)
    return f"pbkdf2_sha256${PBKDF2_ITERATIONS}${base64.b64encode(salt).decode()}${base64.b64encode(digest).decode()}"

def verify_password(password: str, password_hash: str) -> bool:
    try:
        scheme, iterations, salt_b64, digest_b64 = password_hash.split("$", 3)
        if scheme != "pbkdf2_sha256":
            return False
        salt = base64.b64decode(salt_b64)
        expected = base64.b64decode(digest_b64)
        actual = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, int(iterations))
        return hmac.compare_digest(actual, expected)
    except Exception:
        return False

def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")

def _b64url_decode(data: str) -> bytes:
    pad = "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode(data + pad)

def create_token(user_id: int, username: str, role: str) -> str:
    exp = _dt.datetime.utcnow() + _dt.timedelta(hours=TOKEN_EXP_HOURS)
    payload = {"user_id": user_id, "username": username, "role": role, "exp": int(exp.timestamp())}
    if _pyjwt is not None:
        token = _pyjwt.encode(payload, SECRET_KEY, algorithm="HS256")
        return token.decode("utf-8") if isinstance(token, bytes) else token
    header = {"alg": "HS256", "typ": "JWT"}
    signing_input = f"{_b64url(json.dumps(header, separators=(',', ':')).encode())}.{_b64url(json.dumps(payload, separators=(',', ':')).encode())}"
    sig = hmac.new(SECRET_KEY.encode(), signing_input.encode(), hashlib.sha256).digest()
    return f"{signing_input}.{_b64url(sig)}"

def decode_token(token: str) -> Dict[str, Any]:
    if not token:
        raise ValueError("Missing token")
    token = token.replace("Bearer ", "").strip()
    if _pyjwt is not None:
        return _pyjwt.decode(token, SECRET_KEY, algorithms=["HS256"])
    try:
        header_b64, payload_b64, sig_b64 = token.split(".")
        signing_input = f"{header_b64}.{payload_b64}"
        expected = hmac.new(SECRET_KEY.encode(), signing_input.encode(), hashlib.sha256).digest()
        received = _b64url_decode(sig_b64)
        if not hmac.compare_digest(expected, received):
            raise ValueError("Invalid token signature")
        payload = json.loads(_b64url_decode(payload_b64))
        if int(payload.get("exp", 0)) < int(_dt.datetime.utcnow().timestamp()):
            raise ValueError("Token expired")
        return payload
    except Exception as exc:
        raise ValueError(f"Invalid token: {exc}")

def sha256_json(data: Dict[str, Any]) -> str:
    canonical = json.dumps(data, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

def safe_float(value: Any, default: float = 0.0) -> float:
    try:
        return float(value)
    except Exception:
        return default

def extract_token_from_request(req) -> Optional[str]:
    auth = req.headers.get("Authorization", "")
    if auth.startswith("Bearer "):
        return auth[7:]
    data = req.get_json(silent=True) or {}
    return data.get("token") or req.args.get("token")
