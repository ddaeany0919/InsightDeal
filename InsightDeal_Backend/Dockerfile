FROM python:3.9-slim

WORKDIR /app

# 필요한 시스템 패키지 설치
RUN apt-get update && apt-get install -y \
    gcc \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# ✅ 헬스체크 추가
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8000/health || exit 1

# ✅ 시작 스크립트 생성
RUN echo '#!/bin/sh\n\
python seed_db.py\n\
python start_server.py' > /app/entrypoint.sh && \
chmod +x /app/entrypoint.sh

CMD ["python", "start_server.py", "--reload"]
