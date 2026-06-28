import os
from config import DB_PATH, DATA_DIR
from database import init_db, seed_demo_data


def main():
    os.makedirs(DATA_DIR, exist_ok=True)
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
        print(f"Removed old database: {DB_PATH}")
    init_db()
    seed_demo_data()
    print("Database recreated successfully.")
    print("Demo accounts:")
    print("  customer1 / 1234")
    print("  customer2 / 1234")
    print("  retailer1 / 1234")
    print("  retailer2 / 1234")
    print("  admin / admin123")


if __name__ == "__main__":
    main()
