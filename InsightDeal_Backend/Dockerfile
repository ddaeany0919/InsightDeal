# 1. 베이스 이미지 선택 (파이썬 3.9 슬림 버전)
FROM python:3.9-slim

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 시스템 패키지 업데이트 및 Tesseract OCR 엔진과 한국어 데이터 설치
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-kor \
    && rm -rf /var/lib/apt/lists/*

# 4. requirements.txt 파일을 컨테이너에 복사
COPY requirements.txt .

# 5. Python 라이브러리 설치
RUN pip install --no-cache-dir -r requirements.txt

# 6. 현재 폴더의 모든 소스 코드를 컨테이너의 작업 디렉토리로 복사
COPY . .

# 7. FastAPI 서버 실행 명령어 (8000번 포트로 실행)
CMD ["uvicorn", "start_server:app", "--host", "0.0.0.0", "--port", "8000"]