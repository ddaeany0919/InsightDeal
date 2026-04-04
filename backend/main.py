from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import JSONResponse
import os
import logging
from routers import wishlist, product, community, health

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

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

@app.get("/")
def read_root():
    return {"message": "Welcome to InsightDeal API"}

@app.exception_handler(Exception)
async def generic_exception_handler(request, exc):
    logging.error(f"Unhandled Error: {exc}")
    return JSONResponse(status_code=500, content={"message": "Internal Server Error - Please contact admin."})

# (선택적으로 lifespan, DB/스크래퍼 초기화 등도 여기에 추가 가능)
