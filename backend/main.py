from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import JSONResponse
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import logging
from routers import wishlist, product, community, health, push, admin, auth, users

import firebase_admin
from firebase_admin import credentials

# 濡쒓퉭 ?ㅼ젙
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Firebase 珥덇린??
try:
    cred = credentials.Certificate(os.path.join(os.path.dirname(__file__), "firebase-service-account.json"))
    firebase_admin.initialize_app(cred)
    logger.info("?뵦 Firebase Admin SDK ?깃났?곸쑝濡?珥덇린?붾릺?덉뒿?덈떎.")
except Exception as e:
    logger.error(f"??Firebase 珥덇린???ㅽ뙣: {e}")

app = FastAPI(title="InsightDeal API")

# ?대?吏 罹먯떆 ?붾젆?좊━ ?ㅼ젙
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_CACHE_DIR = os.path.join(BASE_DIR, "image_cache")
if not os.path.exists(IMAGE_CACHE_DIR):
    os.makedirs(IMAGE_CACHE_DIR)

# ?뺤쟻 ?뚯씪 留덉슫??(?대?吏 ?쒕튃)
app.mount("/images", StaticFiles(directory=IMAGE_CACHE_DIR), name="images")

# CORS ?ㅼ젙
ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "").split(",")
if not ALLOWED_ORIGINS or ALLOWED_ORIGINS == [""]:
    # ???덈뱶濡쒖씠???먮?/?ㅺ린湲?怨?濡쒖뺄 ?뚯뒪???섍꼍留?湲곕낯 ?덉슜
    ALLOWED_ORIGINS = ["http://localhost", "http://localhost:3000", "capacitor://localhost", "http://10.0.2.2"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],  # '*' ???紐낆떆???좎뼵
    allow_headers=["Authorization", "Content-Type", "Accept"], # ?덉슜?섎뒗 ?ㅻ뜑 ?쒗븳
)

# ?쇱슦???깅줉
app.include_router(wishlist.router, prefix="/api/wishlist", tags=["wishlist"])
app.include_router(product.router, prefix="/api/product", tags=["product"])
app.include_router(community.router, prefix="/api/community", tags=["community"])
app.include_router(health.router, prefix="/api/health", tags=["health"])
app.include_router(push.router, prefix="/api/push", tags=["push"])
app.include_router(users.router, prefix="/api/users", tags=["users"])

# 愿由ъ옄 ?섏씠吏 ?쇱슦???깅줉
from routers import admin
app.include_router(admin.router, prefix="/admin", tags=["admin"])
app.include_router(auth.router, prefix="/api/auth", tags=["auth"])

from fastapi.responses import Response
import httpx

@app.get("/")
def read_root():
    return {"message": "Welcome to InsightDeal API"}

# ?꾩뿭 HTTP ?대씪?댁뼵?몃? ?앹꽦?섏뿬 ?곌껐 ?留?Connection Pooling) ?ъ슜
# ?대?吏 濡쒕뵫 ??SSL ?몃뱶?곗씠???ㅻ쾭?ㅻ뱶瑜????以꾩씠怨??띾룄瑜??믪엫
proxy_client = httpx.AsyncClient(limits=httpx.Limits(max_keepalive_connections=50, max_connections=100))

@app.on_event("shutdown")
async def shutdown_event():
    await proxy_client.aclose()

@app.get("/api/proxy-image")
async def proxy_image(url: str):
    try:
        # Ppomppu ?고쉶 ???대?吏 URL ?먯껜媛 ?꾨땶 硫붿씤 ?ъ씠???꾨찓?몄쓣 ?덊띁?щ줈 二쇱엯
        referer = "https://m.ppomppu.co.kr/" if "ppomppu.co.kr" in url else url
        
        # ?꾩뿭 ?대씪?댁뼵???ъ궗?⑹쑝濡??깅뒫 理쒖쟻??
        resp = await proxy_client.get(
            url, 
            timeout=3.0,
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Referer": referer}
        )
        resp.raise_for_status()
        content_type = resp.headers.get("Content-Type", "image/jpeg")
        
        # Coil (Android) 罹먯떛???꾪븳 Cache-Control ?ㅻ뜑 異붽? (7??罹먯떆)
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
