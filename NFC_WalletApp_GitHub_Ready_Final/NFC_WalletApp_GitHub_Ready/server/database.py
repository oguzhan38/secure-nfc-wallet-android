import os
import sqlite3
import threading
from contextlib import contextmanager
from config import DB_PATH, DATA_DIR
from security_utils import hash_password

_db_lock = threading.RLock()

@contextmanager
def get_db():
    os.makedirs(DATA_DIR, exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=30, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

def init_db():
    with _db_lock, get_db() as conn:
        c = conn.cursor()
        c.execute('''
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('CUSTOMER', 'RETAILER', 'ADMIN')),
                balance REAL NOT NULL DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        ''')
        c.execute('''
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER,
                retailer_id INTEGER,
                amount REAL NOT NULL,
                item_category TEXT,
                item_name TEXT,
                status TEXT NOT NULL,
                reject_reason TEXT,
                nonce TEXT UNIQUE NOT NULL,
                nfc_round_trip_ms REAL,
                server_validation_ms REAL,
                encryption_algorithm TEXT,
                payload_hash TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(customer_id) REFERENCES users(id),
                FOREIGN KEY(retailer_id) REFERENCES users(id)
            )
        ''')
        c.execute('''
            CREATE TABLE IF NOT EXISTS security_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_type TEXT NOT NULL,
                actor_role TEXT,
                username TEXT,
                direction TEXT,
                protocol TEXT,
                endpoint TEXT,
                nonce TEXT,
                encrypted INTEGER NOT NULL DEFAULT 0,
                algorithm TEXT,
                payload_size_bytes INTEGER,
                duration_ms REAL,
                status_code TEXT,
                status_word TEXT,
                result TEXT,
                error_reason TEXT,
                raw_summary TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        ''')
        c.execute('''
            CREATE TABLE IF NOT EXISTS receipts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                transaction_id INTEGER UNIQUE NOT NULL,
                customer_id INTEGER NOT NULL,
                retailer_id INTEGER NOT NULL,
                receipt_no TEXT UNIQUE NOT NULL,
                amount REAL NOT NULL,
                item_category TEXT,
                item_name TEXT,
                nonce TEXT NOT NULL,
                security_summary TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(transaction_id) REFERENCES transactions(id),
                FOREIGN KEY(customer_id) REFERENCES users(id),
                FOREIGN KEY(retailer_id) REFERENCES users(id)
            )
        ''')
        c.execute('''
            CREATE TABLE IF NOT EXISTS attack_alerts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                alert_type TEXT NOT NULL,
                severity TEXT NOT NULL,
                nonce TEXT,
                username TEXT,
                description TEXT,
                related_log_id INTEGER,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(related_log_id) REFERENCES security_logs(id)
            )
        ''')
        c.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_nonce ON transactions(nonce)")
        c.execute("CREATE INDEX IF NOT EXISTS idx_logs_nonce ON security_logs(nonce)")
        c.execute("CREATE INDEX IF NOT EXISTS idx_logs_event_type ON security_logs(event_type)")
        c.execute("CREATE INDEX IF NOT EXISTS idx_receipts_customer ON receipts(customer_id)")
        c.execute("CREATE INDEX IF NOT EXISTS idx_receipts_retailer ON receipts(retailer_id)")

def seed_demo_data():
    demo_users = [
        ("customer1", "1234", "CUSTOMER", 1000.0),
        ("customer2", "1234", "CUSTOMER", 50.0),
        ("retailer1", "1234", "RETAILER", 0.0),
        ("retailer2", "1234", "RETAILER", 0.0),
        ("admin", "admin123", "ADMIN", 0.0),
    ]
    with _db_lock, get_db() as conn:
        for username, password, role, balance in demo_users:
            exists = conn.execute("SELECT id FROM users WHERE username=?", (username,)).fetchone()
            if not exists:
                conn.execute(
                    "INSERT INTO users(username, password_hash, role, balance) VALUES(?, ?, ?, ?)",
                    (username, hash_password(password), role, balance),
                )

def dict_rows(rows):
    return [dict(row) for row in rows]
