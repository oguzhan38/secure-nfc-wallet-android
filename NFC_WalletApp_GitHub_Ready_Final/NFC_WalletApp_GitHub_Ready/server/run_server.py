import argparse
from config import DEFAULT_PORT

parser = argparse.ArgumentParser(description="NFC Wallet Local Security Server")
parser.add_argument("--port", type=int, default=DEFAULT_PORT)
parser.add_argument("--no-gui", action="store_true", help="Run Flask server without Tkinter admin console")
args = parser.parse_args()

if args.no_gui:
    from server import run_flask_server
    run_flask_server(args.port)
else:
    from admin_console import AdminConsole
    import tkinter as tk
    root = tk.Tk()
    AdminConsole(root)
    root.mainloop()
