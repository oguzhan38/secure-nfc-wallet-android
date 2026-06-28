@echo off
cd /d "%~dp0"
echo Running local server API smoke test...
echo Make sure Start_Server.bat is already running.
python api_smoke_test.py
pause
