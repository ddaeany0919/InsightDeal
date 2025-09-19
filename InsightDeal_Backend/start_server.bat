@echo off
REM 현재 배치 파일이 위치한 디렉토리로 이동
cd /d %~dp0

REM 가상환경 활성화 (필요하면 경로 수정)
REM call venv\Scripts\activate.bat

REM uvicorn 실행
uvicorn start_server:app --host 0.0.0.0 --port 8000 --reload

pause