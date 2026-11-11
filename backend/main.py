from fastapi import FastAPI, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import JSONResponse
import sys
import os
try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass
_backend_dir = os.path.dirname(os.path.abspath(__file__))
_project_root = os.path.dirname(_backend_dir)
for _p in (_project_root, _backend_dir):
    if _p not in sys.path:
        sys.path.insert(0, _p)
if "pytest" not in sys.modules and "PYTEST_CURRENT_TEST" not in os.environ:
    try:
        from dotenv import load_dotenv
        load_dotenv(os.path.join(_backend_dir, ".env"), override=False)
        load_dotenv(os.path.join(_project_root, ".env"), override=False)
    except Exception:
        pass
import logging
import ipaddress
import httpx
from backend.routers import wishlist, product, community, health, push, admin, auth, users, coupang
for _router_mod_name, _router_mod in (
    ("wishlist", wishlist),
    ("product", product),
    ("community", community),
    ("health", health),
    ("push", push),
    ("admin", admin),
    ("auth", auth),
    ("users", users),
    ("coupang", coupang),
):
    sys.modules.setdefault(f"routers.{_router_mod_name}", _router_mod)

import firebase_admin
from firebase_admin import credentials

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Firebase 초기화
try:
    cred = credentials.Certificate(os.path.join(os.path.dirname(__file__), "firebase-service-account.json"))
    firebase_admin.initialize_app(cred)
    logger.info("Firebase Admin SDK가 초기화되었습니다.")
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

# CORS 설정 (로컬, 앱 WebView 및 앱인토스(Apps in Toss) 미니앱 CDN 도메인 허용)
ALLOWED_ORIGINS = [o.strip() for o in os.getenv("ALLOWED_ORIGINS", "").split(",") if o.strip()]
if not ALLOWED_ORIGINS:
    ALLOWED_ORIGINS = [
        "http://localhost",
        "http://localhost:3000",
        "capacitor://localhost",
        "http://10.0.2.2",
        "https://toss.im",
        "https://apps-in-toss.toss.im",
    ]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_origin_regex=r"https://.*\.(toss\.im|tossmini\.com|vercel\.app|sslip\.io|nip\.io|duckdns\.org|trycloudflare\.com)",
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "Accept", "X-Admin-Key"],
)

# 라우터 등록
app.include_router(wishlist.router, prefix="/api/wishlist", tags=["wishlist"])
app.include_router(product.router, prefix="/api/product", tags=["product"])
app.include_router(community.router, prefix="/api/community", tags=["community"])
app.include_router(health.router, prefix="/api/health", tags=["health"])
app.include_router(push.router, prefix="/api/push", tags=["push"])
app.include_router(users.router, prefix="/api/users", tags=["users"])
app.include_router(coupang.router, prefix="/api/coupang", tags=["coupang"])
app.include_router(admin.router, prefix="/admin", tags=["admin"])
app.include_router(auth.router, prefix="/api/auth", tags=["auth"])

@app.get("/")
def read_root():
    return {"message": "Welcome to InsightDeal API"}

# 전역 HTTP 클라이언트 (Connection Pooling)
proxy_client = httpx.AsyncClient(
    limits=httpx.Limits(max_keepalive_connections=50, max_connections=100),
    follow_redirects=True
)

@app.on_event("shutdown")
async def shutdown_event():
    await proxy_client.aclose()

FALLBACK_SVG_IMAGE = """<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300">
  <rect width="400" height="300" fill="#1E293B"/>
  <circle cx="200" cy="130" r="45" fill="#334155"/>
  <path d="M200 105 L215 125 L185 125 Z" fill="#F59E0B"/>
  <text x="200" y="210" font-family="sans-serif" font-size="15" font-weight="bold" fill="#94A3B8" text-anchor="middle">InsightDeal 이미지</text>
  <text x="200" y="235" font-family="sans-serif" font-size="12" fill="#64748B" text-anchor="middle">원본 이미지 만료 또는 준비 중</text>
</svg>""".encode('utf-8')

def _is_safe_external_url(url: str) -> bool:
    """SSRF 방어를 위한 외부 이미지 URL 검증 (사설망/루프백/메타데이터 IP 완벽 차단)"""
    from urllib.parse import urlparse
    try:
        parsed = urlparse(url)
        if parsed.scheme not in ("http", "https"):
            return False
        host = (parsed.hostname or "").lower().strip()
        if not host or host in ("localhost", "0.0.0.0", "169.254.169.254") or host.endswith(".internal") or host.endswith(".local"):
            return False
        try:
            ip = ipaddress.ip_address(host)
            if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast or ip.is_reserved:
                return False
        except ValueError:
            pass
        return True
    except Exception:
        return False

def _detect_image_mime(data: bytes) -> str | None:
    if not data or len(data) < 12:
        return None
    if data.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data.startswith(b"GIF87a") or data.startswith(b"GIF89a"):
        return "image/gif"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp"
    if data[4:8] == b"ftyp" and data[8:12] in (b"avif", b"avis", b"mif1", b"heic"):
        return "image/avif"
    return None

def _build_proxy_headers(target_url: str) -> dict:
    headers = {
        "User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36",
        "Accept": "image/jpeg,image/png,image/webp,*/*;q=0.8"
    }
    if "ppomppu.co.kr" in target_url:
        headers["Referer"] = "https://www.ppomppu.co.kr/"
    elif "fmkorea.com" in target_url or "fmkorea.org" in target_url:
        headers["Referer"] = "https://www.fmkorea.com/"
    elif "quasarzone.com" in target_url:
        headers["Referer"] = "https://quasarzone.com/"
    elif "ruliweb.com" in target_url:
        headers["Referer"] = "https://bbs.ruliweb.com/"
    elif "bbasak.com" in target_url:
        headers["Referer"] = "https://bbasak.com/"
    elif "gmarket.co.kr" in target_url:
        headers["Referer"] = "https://www.gmarket.co.kr/"
    elif "ohousecdn.com" in target_url or "ohou.se" in target_url:
        headers["Referer"] = "https://ohou.se/"
    elif "pstatic.net" in target_url or "naver.net" in target_url or "naver.com" in target_url:
        headers["Referer"] = "https://shopping.naver.com/"
    elif "coupangcdn.com" in target_url or "coupang.com" in target_url:
        headers["Referer"] = "https://www.coupang.com/"
    elif "11st.co.kr" in target_url or "11cdn.com" in target_url or "011st.com" in target_url:
        headers["Referer"] = "https://www.11st.co.kr/"
    elif "ssgcdn.com" in target_url or "ssg.com" in target_url:
        headers["Referer"] = "https://www.ssg.com/"
    elif "alicdn.com" in target_url or "aliexpress" in target_url:
        headers["Referer"] = "https://ko.aliexpress.com/"
    else:
        from urllib.parse import urlparse
        p = urlparse(target_url)
        headers["Referer"] = f"{p.scheme}://{p.netloc}/" if p.scheme and p.netloc else "https://google.com/"
    return headers

@app.get("/api/proxy-image")
@app.get("/api/image-proxy")
async def proxy_image(url: str):
    import hashlib
    import re
    raw_input_url = url
    url = (url or "").replace("&amp;", "&").strip().rstrip(",)")
    if url.startswith("//"):
        url = "https:" + url
    if ("gmarket.co.kr" in url or "esmplus.com" in url or "auction.co.kr" in url) and "?ver=" in url:
        url = url.split("?ver=")[0]
    url_hash = hashlib.md5(raw_input_url.encode('utf-8')).hexdigest()
    cache_file_path = os.path.join(IMAGE_CACHE_DIR, f"{url_hash}.cache")

    try:
        if not _is_safe_external_url(url):
            raise ValueError("Unsafe or non-http external URL")
        lower_u = url.lower()
        if any(bad in lower_u for bad in ("no_image.svg", "thumb_no_image.svg", "noimage", "dot03.gif", "logo_f.svg", "49841804f3d132d75a6c11b1510af812.png")):
            raise ValueError("Known placeholder image URL")

        if os.path.exists(cache_file_path):
            # [LRU 수명 연장] 최근 조회 시 mtime을 현재 시각으로 갱신하여 7일 청소 데몬으로부터 보호
            try:
                os.utime(cache_file_path, None)
            except OSError:
                pass

            with open(cache_file_path, "rb") as f:
                cached_content = f.read()
            detected_mime = _detect_image_mime(cached_content)
            if detected_mime:
                return Response(
                    content=cached_content,
                    media_type=detected_mime,
                    headers={"Cache-Control": "public, max-age=604800, immutable"}
                )
            else:
                try:
                    os.remove(cache_file_path)
                except OSError:
                    pass

        candidates = [url]
        if "ppomppu.co.kr" in url:
            no_900w = url.replace("/900w_", "/")
            if no_900w not in candidates:
                candidates.append(no_900w)
            for cdn in ("cdn4.ppomppu.co.kr", "cdn2.ppomppu.co.kr", "cdn3.ppomppu.co.kr"):
                alt = re.sub(r"cdn\d\.ppomppu\.co\.kr", cdn, no_900w)
                if alt not in candidates:
                    candidates.append(alt)

        last_err = None
        for cand in candidates:
            try:
                resp = await proxy_client.get(
                    cand,
                    timeout=8.0,
                    headers=_build_proxy_headers(cand)
                )
                resp.raise_for_status()
                detected_mime = _detect_image_mime(resp.content)
                if not detected_mime or len(resp.content) < 60:
                    raise ValueError(f"Non-image or tiny payload returned from {cand}")
                with open(cache_file_path, "wb") as f:
                    f.write(resp.content)
                return Response(
                    content=resp.content,
                    media_type=detected_mime,
                    headers={"Cache-Control": "public, max-age=604800, immutable"}
                )
            except Exception as ce:
                last_err = ce
        raise last_err or ValueError("All primary candidates failed")
    except Exception as e:
        logger.warning(f"Proxy Image Fallback Triggered for {raw_input_url}: {e}")
        try:
            from backend.database.session import SessionLocal
            from backend.database import models
            db = SessionLocal()
            try:
                deal = db.query(models.Deal).filter(models.Deal.image_url.in_([raw_input_url, url])).first()
                backup_candidates = []
                if deal:
                    gm_match = re.search(
                        r'(?:goodscode=|/vi/product/)(\d{8,12})',
                        f"{deal.ecommerce_link or ''} {deal.content_html or ''} {raw_input_url}",
                        re.I
                    )
                    if gm_match:
                        backup_candidates.append(f"https://gdimg.gmarket.co.kr/{gm_match.group(1)}/still/600")
                if deal and deal.content_html:
                    idx = deal.content_html.find("[첨부된 이미지 링크들]")
                    search_zone = deal.content_html[idx:] if idx != -1 else deal.content_html
                    for raw_m in re.findall(r'https?://[^\s,\)\]\'\"<>]+', search_zone):
                        clean_m = raw_m.replace("&amp;", "&").strip().rstrip(",)")
                        if clean_m and clean_m not in backup_candidates and clean_m != url:
                            backup_candidates.append(clean_m)
                            if "ppomppu.co.kr" in clean_m:
                                no_900 = clean_m.replace("/900w_", "/")
                                if no_900 not in backup_candidates:
                                    backup_candidates.append(no_900)
                                cdn4_u = re.sub(r"cdn\d\.ppomppu\.co\.kr", "cdn4.ppomppu.co.kr", no_900)
                                if cdn4_u not in backup_candidates:
                                    backup_candidates.append(cdn4_u)
                for backup_url in backup_candidates[:6]:
                    if not _is_safe_external_url(backup_url):
                        continue
                    try:
                        b_resp = await proxy_client.get(backup_url, timeout=8.0, headers=_build_proxy_headers(backup_url))
                        b_resp.raise_for_status()
                        b_mime = _detect_image_mime(b_resp.content)
                        if b_mime and len(b_resp.content) >= 60:
                            with open(cache_file_path, "wb") as f:
                                f.write(b_resp.content)
                            deal.image_url = backup_url
                            db.commit()
                            return Response(
                                content=b_resp.content,
                                media_type=b_mime,
                                headers={"Cache-Control": "public, max-age=604800, immutable"}
                            )
                    except Exception:
                        continue
            finally:
                db.close()
        except Exception:
            pass
        return Response(
            status_code=404,
            headers={"Cache-Control": "no-store"}
        )

@app.exception_handler(Exception)
async def generic_exception_handler(request, exc):
    from fastapi import HTTPException
    if isinstance(exc, HTTPException):
        return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})
    logging.error(f"Unhandled Error: {exc}")
    return JSONResponse(status_code=500, content={"message": "Internal Server Error - Please contact admin."})

@app.on_event("startup")
async def startup_event():
    try:
        from backend.database.session import db_manager
        logger.info("[Startup] 데이터베이스 및 복합 인덱스 초기화 시작...")
        if db_manager.test_connection():
            db_manager.init_database()
            logger.info("[Startup] 데이터베이스 및 복합 인덱스 초기화 성공")
        else:
            logger.warning("[Startup] DB 연결 테스트 실패")
    except Exception as e:
        logger.error(f"[Startup] DB 초기화 중 오류 발생: {e}", exc_info=True)

    # ENABLE_SCHEDULER 환경변수 가드 (멀티 워커/도커 분리 구동 시 중복 크롤링 및 IP 차단 방지)
    enable_scheduler = os.getenv("ENABLE_SCHEDULER", "true").lower() in ("true", "1", "yes")
    if enable_scheduler:
        try:
            import asyncio
            from backend.scheduler.main import start_scheduler, run_pipeline_job
            app.state.scheduler = start_scheduler()
            asyncio.create_task(run_pipeline_job())
            logger.info("[Startup] 실시간 핫딜 통합 파싱 스케줄러(3분 주기 + 일일 자동 백업) 가동 완료")
        except Exception as e:
            logger.error(f"[Startup] 스케줄러 연동 중 오류 발생: {e}", exc_info=True)
    else:
        logger.info("[Startup] ENABLE_SCHEDULER=false 설정에 따라 내장 스케줄러 기동을 생략합니다 (전용 스케줄러 컨테이너 사용).")
