@echo off
cd /d "%~dp0"
echo Resetting NFC Wallet demo database...
python reset_database.py
pause
