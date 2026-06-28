@echo off
cd /d "%~dp0"
echo ================================================
echo   NFC Wallet Local Security Server
echo ================================================
echo.
echo This starts the Flask API and the Admin Security Console.
echo If dependencies are missing, run:
echo   pip install -r requirements.txt
echo.
python run_server.py
pause
