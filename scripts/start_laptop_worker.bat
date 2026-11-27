@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0\.."

if exist ".venv-laptop\Scripts\python.exe" (
    ".venv-laptop\Scripts\python.exe" "backend\scripts\laptop_kr_worker.py"
) else (
    python "backend\scripts\laptop_kr_worker.py"
)

if errorlevel 1 (
    echo.
    echo [경고] 워커가 오류로 종료되었습니다. 처음 실행이라면 scripts\setup_laptop_worker.ps1 을 먼저 실행하세요.
    pause
)
