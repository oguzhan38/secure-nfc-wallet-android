@echo off
cd /d "%~dp0"
echo ================================================
echo   NFC Wallet Flask API Only
echo ================================================
echo.
echo Open another terminal and run Run_API_Smoke_Test.bat to verify the API.
echo.
python server.py
pause
