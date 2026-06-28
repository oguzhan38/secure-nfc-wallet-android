import json
import threading
import tkinter as tk
from tkinter import messagebox, scrolledtext, ttk

from config import DEFAULT_PORT
from database import get_db, init_db, seed_demo_data
from security_utils import hash_password
from server import get_local_ip, run_flask_server
from services import list_security_logs, list_transactions, security_summary

_server_started = False

def _clear_tree(tree):
    for item in tree.get_children():
        tree.delete(item)

def _insert_rows(tree, rows, columns):
    _clear_tree(tree)
    for row in rows:
        tree.insert("", "end", values=[row.get(col, "") for col in columns])

class AdminConsole:
    def __init__(self, root):
        self.root = root
        self.root.title("NFC Wallet Security Console")
        self.root.geometry("1180x760")
        self.root.minsize(1040, 680)
        self.port_var = tk.StringVar(value=str(DEFAULT_PORT))
        self.status_var = tk.StringVar(value="Server stopped")
        self._configure_style()
        self._build_ui()
        init_db()
        seed_demo_data()
        self.refresh_all()

    def _configure_style(self):
        self.root.configure(bg="#0f172a")
        style = ttk.Style()
        try:
            style.theme_use("clam")
        except Exception:
            pass
        style.configure("TFrame", background="#0f172a")
        style.configure("Panel.TFrame", background="#111827")
        style.configure("Card.TFrame", background="#1e293b", relief="flat")
        style.configure("TLabel", background="#0f172a", foreground="#e5e7eb", font=("Segoe UI", 10))
        style.configure("Title.TLabel", background="#0f172a", foreground="#f8fafc", font=("Segoe UI", 18, "bold"))
        style.configure("Sub.TLabel", background="#0f172a", foreground="#94a3b8", font=("Segoe UI", 10))
        style.configure("CardTitle.TLabel", background="#1e293b", foreground="#cbd5e1", font=("Segoe UI", 10, "bold"))
        style.configure("CardValue.TLabel", background="#1e293b", foreground="#38bdf8", font=("Segoe UI", 20, "bold"))
        style.configure("TButton", font=("Segoe UI", 10, "bold"), padding=8)
        style.configure("Accent.TButton", background="#2563eb", foreground="#ffffff")
        style.configure("Treeview", background="#111827", foreground="#e5e7eb", fieldbackground="#111827", rowheight=28, font=("Segoe UI", 9))
        style.configure("Treeview.Heading", background="#1e293b", foreground="#f8fafc", font=("Segoe UI", 9, "bold"))
        style.map("Treeview", background=[("selected", "#2563eb")])
        style.configure("TNotebook", background="#0f172a", borderwidth=0)
        style.configure("TNotebook.Tab", background="#1e293b", foreground="#cbd5e1", padding=(14, 8), font=("Segoe UI", 10, "bold"))
        style.map("TNotebook.Tab", background=[("selected", "#2563eb")], foreground=[("selected", "#ffffff")])

    def _build_ui(self):
        top = ttk.Frame(self.root, padding=14)
        top.pack(fill="x")
        title_box = ttk.Frame(top)
        title_box.pack(side="left", padx=(0, 28))
        ttk.Label(title_box, text="NFC Wallet Security Console", style="Title.TLabel").pack(anchor="w")
        ttk.Label(title_box, text="Local authorization server • packet logs • timing • attack alerts", style="Sub.TLabel").pack(anchor="w")
        ttk.Label(top, text="Port:").pack(side="left")
        ttk.Entry(top, textvariable=self.port_var, width=8).pack(side="left", padx=6)
        ttk.Button(top, text="Start Server", command=self.start_server, style="Accent.TButton").pack(side="left", padx=5)
        ttk.Button(top, text="Refresh", command=self.refresh_all).pack(side="left", padx=5)
        ttk.Label(top, textvariable=self.status_var, foreground="#22c55e").pack(side="left", padx=15)

        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill="both", expand=True, padx=10, pady=10)
        self.dashboard_tab = ttk.Frame(self.notebook, padding=10)
        self.users_tab = ttk.Frame(self.notebook, padding=10)
        self.tx_tab = ttk.Frame(self.notebook, padding=10)
        self.logs_tab = ttk.Frame(self.notebook, padding=10)
        self.alerts_tab = ttk.Frame(self.notebook, padding=10)
        self.notebook.add(self.dashboard_tab, text="Dashboard")
        self.notebook.add(self.users_tab, text="Users")
        self.notebook.add(self.tx_tab, text="Transactions / Receipts")
        self.notebook.add(self.logs_tab, text="Security Logs")
        self.notebook.add(self.alerts_tab, text="Attack Alerts")
        self._build_dashboard()
        self._build_users()
        self._build_transactions()
        self._build_logs()
        self._build_alerts()

    def _build_dashboard(self):
        self.kpi_frame = ttk.Frame(self.dashboard_tab)
        self.kpi_frame.pack(fill="x", pady=(0, 14))
        self.kpi_vars = {}
        cards = [
            ("total_users", "Users"),
            ("successful_transactions", "Success TX"),
            ("rejected_transactions", "Rejected TX"),
            ("attack_alerts", "Attack Alerts"),
            ("average_nfc_round_trip_ms", "Avg NFC ms"),
            ("average_server_validation_ms", "Avg Server ms"),
        ]
        for i, (key, label) in enumerate(cards):
            card = ttk.Frame(self.kpi_frame, style="Card.TFrame", padding=14)
            card.grid(row=0, column=i, sticky="nsew", padx=5)
            self.kpi_frame.columnconfigure(i, weight=1)
            ttk.Label(card, text=label, style="CardTitle.TLabel").pack(anchor="w")
            var = tk.StringVar(value="0")
            self.kpi_vars[key] = var
            ttk.Label(card, textvariable=var, style="CardValue.TLabel").pack(anchor="w", pady=(8, 0))

        guide = ttk.LabelFrame(self.dashboard_tab, text="Demonstration Checklist", padding=12)
        guide.pack(fill="x", pady=(0, 12))
        ttk.Label(guide, text=(
            "1) Start server and connect both phones to the same local network.\n"
            "2) Login as retailer1/customer1. Create a bill and tap phones for NFC.\n"
            "3) Inspect APDU events, nonce, encryption algorithm, packet size and timings in Security Logs.\n"
            "4) Use Replay and MITM/Tamper buttons to generate Attack Alerts for the report."
        ), justify="left").pack(anchor="w")

        self.summary_text = scrolledtext.ScrolledText(self.dashboard_tab, height=14, font=("Consolas", 10),
                                                     bg="#111827", fg="#e5e7eb", insertbackground="#e5e7eb")
        self.summary_text.pack(fill="both", expand=True)

    def _build_users(self):
        cols = ["id", "username", "role", "balance", "is_active", "created_at"]
        self.user_cols = cols
        self.user_tree = ttk.Treeview(self.users_tab, columns=cols, show="headings", height=15)
        for col in cols:
            self.user_tree.heading(col, text=col)
            self.user_tree.column(col, width=130)
        self.user_tree.pack(fill="both", expand=True)

        form = ttk.LabelFrame(self.users_tab, text="Add Demo User", padding=10)
        form.pack(fill="x", pady=10)
        self.new_username = tk.StringVar()
        self.new_password = tk.StringVar(value="1234")
        self.new_role = tk.StringVar(value="CUSTOMER")
        self.new_balance = tk.StringVar(value="1000")
        ttk.Label(form, text="Username").grid(row=0, column=0, sticky="w")
        ttk.Entry(form, textvariable=self.new_username, width=20).grid(row=0, column=1, padx=5)
        ttk.Label(form, text="Password").grid(row=0, column=2, sticky="w")
        ttk.Entry(form, textvariable=self.new_password, width=20).grid(row=0, column=3, padx=5)
        ttk.Label(form, text="Role").grid(row=0, column=4, sticky="w")
        ttk.Combobox(form, textvariable=self.new_role, values=["CUSTOMER", "RETAILER", "ADMIN"], state="readonly", width=12).grid(row=0, column=5, padx=5)
        ttk.Label(form, text="Balance").grid(row=0, column=6, sticky="w")
        ttk.Entry(form, textvariable=self.new_balance, width=10).grid(row=0, column=7, padx=5)
        ttk.Button(form, text="Add User", command=self.add_user).grid(row=0, column=8, padx=10)

    def _build_transactions(self):
        cols = ["id", "customer_username", "retailer_username", "amount", "item_category", "item_name", "status", "reject_reason", "nonce", "nfc_round_trip_ms", "server_validation_ms", "created_at"]
        self.tx_cols = cols
        self.tx_tree = ttk.Treeview(self.tx_tab, columns=cols, show="headings", height=22)
        for col in cols:
            self.tx_tree.heading(col, text=col)
            self.tx_tree.column(col, width=135)
        self.tx_tree.pack(fill="both", expand=True)

    def _build_logs(self):
        cols = ["id", "created_at", "event_type", "username", "actor_role", "direction", "protocol", "endpoint", "nonce", "encrypted", "algorithm", "payload_size_bytes", "duration_ms", "status_word", "result", "error_reason"]
        self.log_cols = cols
        self.log_tree = ttk.Treeview(self.logs_tab, columns=cols, show="headings", height=24)
        for col in cols:
            self.log_tree.heading(col, text=col)
            self.log_tree.column(col, width=130)
        self.log_tree.pack(fill="both", expand=True)

    def _build_alerts(self):
        cols = ["id", "created_at", "alert_type", "severity", "nonce", "username", "description"]
        self.alert_cols = cols
        self.alert_tree = ttk.Treeview(self.alerts_tab, columns=cols, show="headings", height=24)
        for col in cols:
            self.alert_tree.heading(col, text=col)
            self.alert_tree.column(col, width=160)
        self.alert_tree.pack(fill="both", expand=True)

    def start_server(self):
        global _server_started
        if _server_started:
            messagebox.showinfo("Server", "Server is already running.")
            return
        port = int(self.port_var.get())
        threading.Thread(target=run_flask_server, args=(port, True), daemon=True).start()
        _server_started = True
        self.status_var.set(f"Running at {get_local_ip()}:{port}")

    def add_user(self):
        try:
            with get_db() as conn:
                conn.execute(
                    "INSERT INTO users(username, password_hash, role, balance) VALUES(?, ?, ?, ?)",
                    (self.new_username.get(), hash_password(self.new_password.get()), self.new_role.get(), float(self.new_balance.get())),
                )
            self.refresh_users()
        except Exception as exc:
            messagebox.showerror("Add user failed", str(exc))

    def refresh_dashboard(self):
        data = security_summary()
        data["server_ip"] = get_local_ip()
        data["server_port"] = self.port_var.get()
        for key, var in getattr(self, "kpi_vars", {}).items():
            value = data.get(key)
            if isinstance(value, float):
                value = f"{value:.2f}"
            if value is None:
                value = "-"
            var.set(str(value))
        text = json.dumps(data, indent=2, ensure_ascii=False)
        self.summary_text.delete("1.0", tk.END)
        self.summary_text.insert(tk.END, text)

    def refresh_users(self):
        with get_db() as conn:
            rows = [dict(r) for r in conn.execute("SELECT id, username, role, balance, is_active, created_at FROM users ORDER BY id").fetchall()]
        _insert_rows(self.user_tree, rows, self.user_cols)

    def refresh_transactions(self):
        _insert_rows(self.tx_tree, list_transactions(500), self.tx_cols)

    def refresh_logs(self):
        _insert_rows(self.log_tree, list_security_logs(500), self.log_cols)

    def refresh_alerts(self):
        with get_db() as conn:
            rows = [dict(r) for r in conn.execute("SELECT * FROM attack_alerts ORDER BY id DESC LIMIT 500").fetchall()]
        _insert_rows(self.alert_tree, rows, self.alert_cols)

    def refresh_all(self):
        self.refresh_dashboard()
        self.refresh_users()
        self.refresh_transactions()
        self.refresh_logs()
        self.refresh_alerts()

if __name__ == "__main__":
    root = tk.Tk()
    app = AdminConsole(root)
    root.mainloop()
