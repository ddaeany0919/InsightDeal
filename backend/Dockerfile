FROM python:3.9-slim

WORKDIR /app

# System dependencies for scraping, OCR, and Chrome Driver
RUN apt-get update && apt-get install -y \
    gcc \
    build-essential \
    curl \
    tesseract-ocr \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxrender1 \
    libxext6 \
    chromium \
    chromium-driver \
    fonts-nanum \
    locales \
    tzdata \
    && rm -rf /var/lib/apt/lists/*

# Locale & Timezone (Korean)
RUN sed -i 's/# ko_KR.UTF-8 UTF-8/ko_KR.UTF-8 UTF-8/' /etc/locale.gen \
    && locale-gen \
    && ln -snf /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo 'Asia/Seoul' > /etc/timezone
ENV LANG=ko_KR.UTF-8 \
    LANGUAGE=ko_KR:ko \
    LC_ALL=ko_KR.UTF-8 \
    TZ=Asia/Seoul

# Python dependencies
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# Copy app
COPY . .

# Environment
ENV PYTHONUNBUFFERED=1 \
    OMP_THREAD_LIMIT=1 \
    PYTHONPATH=/app

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8000/api/health || exit 1

# Default command (overridden by docker-compose for dev)
CMD ["python", "-m", "uvicorn", "api.main:app", "--host", "0.0.0.0", "--port", "8000"]
