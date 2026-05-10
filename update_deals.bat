@echo off
set PYTHONPATH=%~dp0
set PYTHONUTF8=1
echo Starting InsightDeal Scrapers...
echo.
"%~dp0backend\.venv\Scripts\python.exe" "%~dp0backend\scripts\update_all_deals.py"
echo.
echo Update Complete!
pause
