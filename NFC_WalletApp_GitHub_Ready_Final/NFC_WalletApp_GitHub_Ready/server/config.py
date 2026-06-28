import os

APP_NAME = "NFC Wallet Local Security Server"
SERVER_HOST = "0.0.0.0"
DEFAULT_PORT = 5000
BROADCAST_PORT = 9999
DISCOVERY_SERVICE_NAME = "NFC_WALLET_NODE"

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
DB_PATH = os.path.join(DATA_DIR, "wallet_security.db")

SECRET_KEY = os.environ.get("NFC_WALLET_SECRET_KEY", "NFC_WALLET_ACADEMIC_DEMO_SECRET_CHANGE_ME")
TOKEN_EXP_HOURS = 24

ACADEMIC_DEMO_MODE = True
