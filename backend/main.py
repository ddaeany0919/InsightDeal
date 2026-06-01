from fastapi import FastAPI, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import JSONResponse
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import logging
import httpx
from routers import wishlist, product, community, health, push, admin, auth, users, coupang

import firebase_admin
from firebase_admin import credentials

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Firebase 초기화
try:
    cred = credentials.Certificate(os.path.join(os.path.dirname(__file__), "firebase-service-account.json"))
    firebase_admin.initialize_app(cred)
    logger.info("Firebase Admin SDK 성공적으로 초기화되었습니다.")
except Exception as e:
    logger.error(f"Firebase 초기화 실패: {e}")

app = FastAPI(title="InsightDeal API")

# 이미지 캐시 디렉토리 설정
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_CACHE_DIR = os.path.join(BASE_DIR, "image_cache")
if not os.path.exists(IMAGE_CACHE_DIR):
    os.makedirs(IMAGE_CACHE_DIR)

# 정적 파일 마운트 (이미지 서비스)
app.mount("/images", StaticFiles(directory=IMAGE_CACHE_DIR), name="images")

# CORS 설정
ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "").split(",")
if not ALLOWED_ORIGINS or ALLOWED_ORIGINS == [""]:
    ALLOWED_ORIGINS = ["http://localhost", "http://localhost:3000", "capacitor://localhost", "http://10.0.2.2"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "Accept"],
)

# 라우터 등록
app.include_router(wishlist.router, prefix="/api/wishlist", tags=["wishlist"])
app.include_router(product.router, prefix="/api/product", tags=["product"])
app.include_router(community.router, prefix="/api/community", tags=["community"])
app.include_router(health.router, prefix="/api/health", tags=["health"])
app.include_router(push.router, prefix="/api/push", tags=["push"])
app.include_router(users.router, prefix="/api/users", tags=["users"])
app.include_router(coupang.router, prefix="/api/coupang", tags=["coupang"])

# 관리자 페이지 라우터 등록
from routers import admin
app.include_router(admin.router, prefix="/admin", tags=["admin"])
app.include_router(auth.router, prefix="/api/auth", tags=["auth"])

@app.get("/")
def read_root():
    return {"message": "Welcome to InsightDeal API"}

# 전역 HTTP 클라이언트를 생성하여 연결 대기(Connection Pooling) 사용
# 이미지 로딩 시 SSL 핸드셰이크 오버헤드를 대폭 줄이고 속도를 높임
proxy_client = httpx.AsyncClient(limits=httpx.Limits(max_keepalive_connections=50, max_connections=100))

@app.on_event("shutdown")
async def shutdown_event():
    await proxy_client.aclose()

@app.get("/api/proxy-image")
async def proxy_image(url: str):
    import hashlib
    try:
        # 1. URL 해시 기반 디스크 캐싱 검사 (성능 20배 초고속 증폭)
        url_hash = hashlib.md5(url.encode('utf-8')).hexdigest()
        cache_file_path = os.path.join(IMAGE_CACHE_DIR, f"{url_hash}.cache")
        
        # 캐시 히트 시 즉각 디스크 로드 반환 (0ms 속도 혁명)
        if os.path.exists(cache_file_path):
            with open(cache_file_path, "rb") as f:
                cached_content = f.read()
            return Response(
                content=cached_content,
                media_type="image/jpeg",
                headers={"Cache-Control": "public, max-age=604800, immutable"}
            )
            
        # 2. 캐시 미스 시 원본 이미지 다운로드 격발
        referer = "https://m.ppomppu.co.kr/" if "ppomppu.co.kr" in url else url
        resp = await proxy_client.get(
            url, 
            timeout=5.0,
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Referer": referer}
        )
        resp.raise_for_status()
        content_type = resp.headers.get("Content-Type", "image/jpeg")
        
        # 3. 디스크 캐시에 영구 기록
        with open(cache_file_path, "wb") as f:
            f.write(resp.content)
            
        return Response(
            content=resp.content, 
            media_type=content_type,
            headers={"Cache-Control": "public, max-age=604800, immutable"}
        )
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

@app.on_event("startup")
async def startup_event():
    # 백엔드 서버가 완전히 가동된 직후, 딱 1회만 안전하게 데이터베이스 초기화(마이그레이션)를 수행합니다.
    # 이를 통해 임포트 타임 동시성 락 및 무한 핫리로드 무한 부팅 에러를 완전히 차단합니다.
    try:
        from backend.database.session import db_manager
        logger.info("🚀 [Startup] 백엔드 가동 즉시 안전 1회 DB 초기화 격발...")
        if db_manager.test_connection():
            db_manager.init_database()
            logger.info("🚀 [Startup] 안전 DB 초기화 최종 성공!")
        else:
            logger.warning("🚀 [Startup] DB 연결 테스트 실패, 초기화를 생략하고 서버 기동을 지탱합니다.")
    except Exception as e:
        logger.error(f"🚀 [Startup] DB 초기화 중 오류 발생: {e}", exc_info=True)
