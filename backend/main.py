from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import JSONResponse
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import logging
from routers import wishlist, product, community, health, push

import firebase_admin
from firebase_admin import credentials

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Firebase 초기화
try:
    cred = credentials.Certificate(os.path.join(os.path.dirname(__file__), "firebase-service-account.json"))
    firebase_admin.initialize_app(cred)
    logger.info("🔥 Firebase Admin SDK 성공적으로 초기화되었습니다.")
except Exception as e:
    logger.error(f"❌ Firebase 초기화 실패: {e}")

app = FastAPI(title="InsightDeal API")

# 이미지 캐시 디렉토리 설정
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_CACHE_DIR = os.path.join(BASE_DIR, "image_cache")
if not os.path.exists(IMAGE_CACHE_DIR):
    os.makedirs(IMAGE_CACHE_DIR)

# 정적 파일 마운트 (이미지 서빙)
app.mount("/images", StaticFiles(directory=IMAGE_CACHE_DIR), name="images")

# CORS 설정
ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "").split(",")
if not ALLOWED_ORIGINS or ALLOWED_ORIGINS == [""]:
    # 앱(안드로이드 에뮬/실기기)과 로컬 테스트 환경만 기본 허용
    ALLOWED_ORIGINS = ["http://localhost", "http://localhost:3000", "capacitor://localhost", "http://10.0.2.2"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],  # '*' 대신 명시적 선언
    allow_headers=["Authorization", "Content-Type", "Accept"], # 허용되는 헤더 제한
)

# 라우터 등록
app.include_router(wishlist.router, prefix="/api/wishlist", tags=["wishlist"])
app.include_router(product.router, prefix="/api/product", tags=["product"])
app.include_router(community.router, prefix="/api/community", tags=["community"])
app.include_router(health.router, prefix="/api/health", tags=["health"])
app.include_router(push.router, prefix="/api/push", tags=["push"])

# 관리자 페이지 라우터 등록
from routers import admin
app.include_router(admin.router, prefix="/admin", tags=["admin"])

from fastapi.responses import Response
import httpx

@app.get("/")
def read_root():
    return {"message": "Welcome to InsightDeal API"}

@app.get("/api/proxy-image")
async def proxy_image(url: str):
    with open("proxy_debug.log", "a", encoding="utf-8") as f:
        f.write(f"REQ: {url}\n")
    try:
        # Ppomppu 우회 시 이미지 URL 자체가 아닌 메인 사이트 도메인을 레퍼러로 주입
        referer = "https://m.ppomppu.co.kr/" if "ppomppu.co.kr" in url else url
        async with httpx.AsyncClient(headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Referer": referer}) as client:
            resp = await client.get(url, timeout=10.0)
            resp.raise_for_status()
            content_type = resp.headers.get("Content-Type", "image/jpeg")
            with open("proxy_debug.log", "a", encoding="utf-8") as f:
                f.write(f"SUCC: size {len(resp.content)} bytes\n")
            return Response(content=resp.content, media_type=content_type)
    except httpx.HTTPStatusError as e:
        logger.warning(f"Proxy Image 404/Error: {url} - {e}")
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Image not found or blocked")
    except Exception as e:
        logger.error(f"Proxy Image Failed Timeout/Other: {url} - {e}")
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Image fetch failed")

@app.exception_handler(Exception)
async def generic_exception_handler(request, exc):
    from fastapi import HTTPException
    if isinstance(exc, HTTPException):
        from fastapi.responses import JSONResponse
        return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})
    logging.error(f"Unhandled Error: {exc}")
    from fastapi.responses import JSONResponse
    return JSONResponse(status_code=500, content={"message": "Internal Server Error - Please contact admin."})

# (선택적으로 lifespan, DB/스크래퍼 초기화 등도 여기에 추가 가능)
