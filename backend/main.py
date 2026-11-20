from fastapi import FastAPI, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool
import sys
import os
import json
import socket
from urllib.parse import urljoin, urlparse
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
import asyncio
from collections import OrderedDict
import io
import logging
import ipaddress
import tempfile
import threading
from typing import Optional
import weakref
import httpx
from backend.core.url_utils import is_public_ip as _is_public_ip, is_safe_external_url as _is_safe_external_url
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

# Firebase 초기화 (환경변수 우선, 기본 경로 폴백)
try:
    if not firebase_admin._apps:
        firebase_source = (
            os.getenv("FIREBASE_SERVICE_ACCOUNT_PATH", "").strip()
            or os.getenv("FIREBASE_CREDENTIALS", "").strip()
        )
        if firebase_source:
            if firebase_source.startswith("{"):
                cred = credentials.Certificate(json.loads(firebase_source))
            else:
                cred = credentials.Certificate(firebase_source)
        else:
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

# CORS 설정 (신뢰 도메인 + 앱인토스 공식 도메인만 허용)
ALLOWED_ORIGINS = [o.strip() for o in os.getenv("ALLOWED_ORIGINS", "").split(",") if o.strip()]
if not ALLOWED_ORIGINS:
    ALLOWED_ORIGINS = [
        "http://localhost",
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "capacitor://localhost",
        "http://10.0.2.2",
        "https://toss.im",
        "https://apps-in-toss.toss.im",
        "https://insightdeal.vercel.app",
    ]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_origin_regex=r"^https://([a-z0-9-]+\.)*(toss\.im|tossmini\.com)$",
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=[
        "Authorization",
        "Content-Type",
        "Accept",
        "X-Admin-Key",
        "X-Device-Token",
        "X-Feeder-Token",
    ],
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

_PLACEHOLDER_VAPID_PUBLIC_KEYS = {
    "bel62iuyguivxikv69yvieuiBia-ib9-skvmeata3lfgdzkrxzjjsgsnfckjbjubkr3qbuyihbqflxyp5nksh8u".lower(),
}


@app.get("/api/push-public-key")
def get_push_public_key():
    key = os.getenv("VAPID_PUBLIC_KEY", "").strip()
    lowered = key.lower()
    if (
        not key
        or lowered in _PLACEHOLDER_VAPID_PUBLIC_KEYS
        or lowered.startswith(("your_", "placeholder", "change_me"))
        or "example" in lowered
    ):
        raise HTTPException(status_code=503, detail="VAPID_PUBLIC_KEY is not configured")
    return {"public_key": key, "publicKey": key}


@app.get("/api/meta/categories")
def get_meta_categories():
    from backend.core.categories import STANDARD_CATEGORIES, STANDARD_SOURCES
    return {"categories": STANDARD_CATEGORIES, "sources": STANDARD_SOURCES}


@app.get("/")
def read_root():
    return {"message": "Welcome to InsightDeal API"}

# 전역 HTTP 클라이언트 (Connection Pooling — 리다이렉트 수동 홉 검증 강제)
proxy_client = httpx.AsyncClient(
    limits=httpx.Limits(max_keepalive_connections=50, max_connections=100),
    follow_redirects=False,
)

@app.on_event("shutdown")
async def shutdown_event():
    await proxy_client.aclose()

FALLBACK_SVG_IMAGE = """<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300">
  <rect width="400" height="300" fill="#1E293B"/>
  <circle cx="200" cy="130" r="45" fill="#334155"/>
  <path d="M200 105 L215 125 L185 125 Z" fill="#F59E0B"/>
  <text x="200" y="210" font-family="sans-serif" font-size="15" font-weight="bold" fill="#94A3B8" text-anchor="middle">얼만데 이미지</text>
  <text x="200" y="235" font-family="sans-serif" font-size="12" fill="#64748B" text-anchor="middle">원본 이미지 만료 또는 준비 중</text>
</svg>""".encode('utf-8')

_MAX_IMAGE_BYTES = 10 * 1024 * 1024
_MAX_IMAGE_REDIRECTS = 3
_IMAGE_WIDTH_BUCKETS = (120, 240, 400, 600, 800, 1200)
_AUTO_WEBP_MAX_WIDTH = 800
_AUTO_WEBP_SIZE_THRESHOLD = 40 * 1024

_IMAGE_MEM_LRU: OrderedDict[str, tuple[bytes, str, str, int, int]] = OrderedDict()
_IMAGE_MEM_LRU_BYTES = 0
_IMAGE_MEM_LRU_MAX_BYTES = 32 * 1024 * 1024
_IMAGE_MEM_LRU_MAX_ITEM_BYTES = 512 * 1024
_IMAGE_MEM_LRU_LOCK = threading.Lock()

_PROXY_LOOP_INFLIGHT: weakref.WeakKeyDictionary[asyncio.AbstractEventLoop, dict[str, asyncio.Task]] = (
    weakref.WeakKeyDictionary()
)


def _quantize_image_width(w: Optional[int], width: Optional[int]) -> Optional[int]:
    """요청 너비를 (120, 240, 400, 600, 800, 1200) 표준 버킷으로 양자화"""
    raw_w = w if (w is not None and w > 0) else (width if (width is not None and width > 0) else None)
    if raw_w is None:
        return None
    for bucket in _IMAGE_WIDTH_BUCKETS:
        if raw_w <= bucket:
            return bucket
    return _IMAGE_WIDTH_BUCKETS[-1]


def _mem_lru_get(mem_key: str) -> tuple[bytes, str] | None:
    """32MB 바이트 상한 인메모리 LRU 조회 (디스크 파일 변경/삭제 시 즉시 무효화)"""
    global _IMAGE_MEM_LRU_BYTES
    with _IMAGE_MEM_LRU_LOCK:
        entry = _IMAGE_MEM_LRU.get(mem_key)
        if entry is None:
            return None
        payload, mime, disk_path, expected_size, expected_mtime_ns = entry
    try:
        st = os.stat(disk_path)
    except OSError:
        st = None
    if st is None or st.st_size != expected_size or st.st_mtime_ns != expected_mtime_ns:
        with _IMAGE_MEM_LRU_LOCK:
            if _IMAGE_MEM_LRU.get(mem_key) is entry:
                _IMAGE_MEM_LRU.pop(mem_key, None)
                _IMAGE_MEM_LRU_BYTES = max(0, _IMAGE_MEM_LRU_BYTES - len(payload))
        return None
    with _IMAGE_MEM_LRU_LOCK:
        if _IMAGE_MEM_LRU.get(mem_key) is entry:
            _IMAGE_MEM_LRU.move_to_end(mem_key)
    return payload, mime


def _mem_lru_put(mem_key: str, payload: bytes, mime: str, disk_path: str) -> None:
    """단건 512KB·전체 32MB 상한을 준수해 핫 이미지 바이트를 인메모리 LRU에 적재"""
    global _IMAGE_MEM_LRU_BYTES
    if not payload or not mime or not disk_path or len(payload) > _IMAGE_MEM_LRU_MAX_ITEM_BYTES:
        return
    try:
        st = os.stat(disk_path)
    except OSError:
        return
    entry_size = len(payload)
    with _IMAGE_MEM_LRU_LOCK:
        old = _IMAGE_MEM_LRU.pop(mem_key, None)
        if old is not None:
            _IMAGE_MEM_LRU_BYTES = max(0, _IMAGE_MEM_LRU_BYTES - len(old[0]))
        while _IMAGE_MEM_LRU and (_IMAGE_MEM_LRU_BYTES + entry_size) > _IMAGE_MEM_LRU_MAX_BYTES:
            _, (evicted_payload, _, _, _, _) = _IMAGE_MEM_LRU.popitem(last=False)
            _IMAGE_MEM_LRU_BYTES = max(0, _IMAGE_MEM_LRU_BYTES - len(evicted_payload))
        _IMAGE_MEM_LRU[mem_key] = (payload, mime, disk_path, st.st_size, st.st_mtime_ns)
        _IMAGE_MEM_LRU_BYTES += entry_size


async def _is_safe_external_url_async(url: str) -> bool:
    """SSRF 방어를 위한 비동기 DNS 해석 검증 (IPv4/IPv6 전체 주소 중 비공인 IP 포함 시 즉시 차단)"""
    if not _is_safe_external_url(url):
        return False
    try:
        parsed = urlparse(url)
        host = (parsed.hostname or "").strip()
        try:
            ip = ipaddress.ip_address(host)
            return _is_public_ip(ip)
        except ValueError:
            pass
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        addr_infos = await run_in_threadpool(socket.getaddrinfo, host, port, 0, socket.SOCK_STREAM)
        if not addr_infos:
            return False
        for info in addr_infos:
            raw_ip = str(info[4][0]).split("%")[0]
            ip_obj = ipaddress.ip_address(raw_ip)
            if not _is_public_ip(ip_obj):
                return False
        return True
    except Exception as exc:
        logger.debug("SSRF DNS 검증 차단 (%s): %s", url, exc)
        return False


async def _fetch_external_image_safe(
    url: str,
    max_redirects: int = _MAX_IMAGE_REDIRECTS,
    max_bytes: int = _MAX_IMAGE_BYTES,
) -> httpx.Response:
    current_url = url
    for _ in range(max_redirects + 1):
        if not await _is_safe_external_url_async(current_url):
            raise ValueError(f"Blocked unsafe external URL: {current_url}")
        resp = await proxy_client.get(
            current_url,
            timeout=8.0,
            headers=_build_proxy_headers(current_url),
        )
        if resp.status_code in (301, 302, 303, 307, 308):
            location = (resp.headers.get("location") or "").strip()
            if not location:
                raise ValueError("Redirect response missing Location header")
            current_url = urljoin(current_url, location)
            continue
        resp.raise_for_status()
        content_len_hdr = resp.headers.get("content-length")
        if content_len_hdr and content_len_hdr.isdigit() and int(content_len_hdr) > max_bytes:
            raise ValueError(f"Content-Length exceeds limit: {content_len_hdr}")
        if len(resp.content) > max_bytes:
            raise ValueError(f"Image payload exceeds {max_bytes} bytes")
        return resp
    raise ValueError("Too many redirects")


def _read_cached_image_sync(cache_file_path: str) -> bytes | None:
    if not os.path.exists(cache_file_path):
        return None
    try:
        os.utime(cache_file_path, None)
    except OSError:
        pass
    with open(cache_file_path, "rb") as f:
        return f.read()


def _write_cached_image_sync(cache_file_path: str, content: bytes) -> None:
    """동일 디렉토리 내 임시 파일 기록 후 os.replace로 원자적 캐시 교체 (fsync 블로킹 제거, .tmp 잔류 차단)"""
    cache_dir = os.path.dirname(os.path.abspath(cache_file_path))
    os.makedirs(cache_dir, exist_ok=True)
    fd, tmp_path = tempfile.mkstemp(prefix=".cache_tmp_", suffix=".tmp", dir=cache_dir)
    try:
        try:
            with os.fdopen(fd, "wb") as f:
                f.write(content)
        except Exception:
            try:
                os.close(fd)
            except OSError:
                pass
            raise
        os.replace(tmp_path, cache_file_path)
    finally:
        if os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except OSError:
                pass


def _remove_file_quietly_sync(file_path: str) -> None:
    try:
        os.remove(file_path)
    except OSError:
        pass


def _get_deal_backup_candidates_sync(raw_input_url: str, url: str) -> tuple[int | None, list[str]]:
    import re
    from backend.database.session import SessionLocal
    from backend.database import models

    db = SessionLocal()
    try:
        deal = db.query(models.Deal).filter(models.Deal.image_url.in_([raw_input_url, url])).first()
        if not deal:
            return None, []
        backup_candidates: list[str] = []
        gm_match = re.search(
            r'(?:goodscode=|/vi/product/)(\d{8,12})',
            f"{deal.ecommerce_link or ''} {deal.content_html or ''} {raw_input_url}",
            re.I,
        )
        if gm_match:
            backup_candidates.append(f"https://gdimg.gmarket.co.kr/{gm_match.group(1)}/still/600")
        if deal.content_html:
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
        return deal.id, backup_candidates
    finally:
        db.close()


def _update_deal_image_url_sync(deal_id: int, backup_url: str) -> None:
    from backend.database.session import SessionLocal
    from backend.database import models

    db = SessionLocal()
    try:
        deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
        if deal:
            deal.image_url = backup_url
            db.commit()
    finally:
        db.close()

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

async def _fetch_and_cache_image_leader(
    raw_input_url: str,
    url: str,
    cache_file_path: str,
) -> tuple[int, bytes, str | None]:
    """Single-flight 리더 코루틴: 모든 예외를 포착해 (404, b'', None)으로 안전 변환"""
    import re

    try:
        cached_content = await run_in_threadpool(_read_cached_image_sync, cache_file_path)
        if cached_content is not None:
            detected_mime = _detect_image_mime(cached_content)
            if detected_mime:
                return 200, cached_content, detected_mime
            await run_in_threadpool(_remove_file_quietly_sync, cache_file_path)

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
                resp = await _fetch_external_image_safe(cand)
                detected_mime = _detect_image_mime(resp.content)
                if not detected_mime or len(resp.content) < 60:
                    raise ValueError(f"Non-image or tiny payload returned from {cand}")
                await run_in_threadpool(_write_cached_image_sync, cache_file_path, resp.content)
                return 200, resp.content, detected_mime
            except Exception as ce:
                last_err = ce
        raise last_err or ValueError("All primary candidates failed")
    except Exception as e:
        logger.warning(f"Proxy Image Fallback Triggered for {raw_input_url}: {e}")
        try:
            deal_id, backup_candidates = await run_in_threadpool(_get_deal_backup_candidates_sync, raw_input_url, url)
            for backup_url in backup_candidates[:6]:
                try:
                    b_resp = await _fetch_external_image_safe(backup_url)
                    b_mime = _detect_image_mime(b_resp.content)
                    if b_mime and len(b_resp.content) >= 60:
                        await run_in_threadpool(_write_cached_image_sync, cache_file_path, b_resp.content)
                        if deal_id is not None:
                            await run_in_threadpool(_update_deal_image_url_sync, deal_id, backup_url)
                        return 200, b_resp.content, b_mime
                except Exception as backup_err:
                    logger.debug("Proxy image backup candidate failed (%s): %s", backup_url, backup_err)
                    continue
        except Exception as fallback_err:
            logger.debug("Proxy image DB fallback failed for %s: %s", raw_input_url, fallback_err)
        return 404, b"", None


def _transform_image_to_webp_sync(
    orig: bytes,
    detected_mime: str,
    target_w: Optional[int],
) -> tuple[bytes, str, bool]:
    """Pillow 리사이즈/WebP 변환 (소형·GIF·애니메이션·오류 시 원본 바이트/MIME 안전 폴백)"""
    if not orig or not detected_mime or detected_mime == "image/gif":
        return orig, detected_mime, False
    if target_w is None and len(orig) <= _AUTO_WEBP_SIZE_THRESHOLD:
        return orig, detected_mime, False
    try:
        from PIL import Image, ImageOps

        with Image.open(io.BytesIO(orig)) as raw_img:
            is_animated = bool(
                getattr(raw_img, "is_animated", False) or getattr(raw_img, "n_frames", 1) > 1
            )
            if is_animated:
                return orig, detected_mime, False
            if target_w is None and detected_mime == "image/webp" and raw_img.width <= _AUTO_WEBP_MAX_WIDTH:
                return orig, detected_mime, False

            img = ImageOps.exif_transpose(raw_img)
            max_w = target_w if target_w is not None else _AUTO_WEBP_MAX_WIDTH
            if max_w > 0 and img.width > max_w and img.height > 0:
                new_h = max(1, round(img.height * (max_w / img.width)))
                resample = getattr(Image, "Resampling", Image).BILINEAR
                img = img.resize((max_w, new_h), resample)

            if img.mode in ("RGBA", "LA") or (img.mode == "P" and "transparency" in img.info):
                img = img.convert("RGBA")
            elif img.mode not in ("RGB", "RGBA"):
                img = img.convert("RGB")

            out_buf = io.BytesIO()
            img.save(out_buf, format="WEBP", quality=80, method=1)
            webp_bytes = out_buf.getvalue()
            if webp_bytes:
                return webp_bytes, "image/webp", True
    except Exception as exc:
        logger.debug("이미지 WebP 변환 스킵 -> 원본 폴백 (%s): %s", detected_mime, exc)
    return orig, detected_mime, False


async def _resolve_image_variant_leader(
    raw_input_url: str,
    url: str,
    url_hash: str,
    orig_cache_path: str,
    variant_cache_path: str,
    target_w: Optional[int],
    inflight_map: dict[str, asyncio.Task],
) -> tuple[int, bytes, str | None, str]:
    """파생 WebP 캐시 확인 -> 원본 Single-flight 확보 -> 필요 시 Pillow WebP 변환 및 캐싱"""
    try:
        cached_var = await run_in_threadpool(_read_cached_image_sync, variant_cache_path)
        if cached_var is not None:
            if _detect_image_mime(cached_var) == "image/webp":
                return 200, cached_var, "image/webp", variant_cache_path
            await run_in_threadpool(_remove_file_quietly_sync, variant_cache_path)

        orig_key = f"orig:{IMAGE_CACHE_DIR}:{url_hash}"
        orig_task = inflight_map.get(orig_key)
        if orig_task is None:
            orig_task = asyncio.create_task(
                _fetch_and_cache_image_leader(raw_input_url, url, orig_cache_path)
            )
            inflight_map[orig_key] = orig_task

            def _cleanup_orig(finished_task: asyncio.Task, m: dict = inflight_map, k: str = orig_key) -> None:
                if m.get(k) is finished_task:
                    m.pop(k, None)

            orig_task.add_done_callback(_cleanup_orig)

        status_code, orig_bytes, orig_mime = await asyncio.shield(orig_task)
        if status_code != 200 or not orig_bytes or not orig_mime:
            return 404, b"", None, ""

        # 계약 보존 가드: target_w 미지정 + 소형(<=40KB) 또는 GIF는 변환 없이 원본 즉시 반환
        if target_w is None and (len(orig_bytes) <= _AUTO_WEBP_SIZE_THRESHOLD or orig_mime == "image/gif"):
            return 200, orig_bytes, orig_mime, orig_cache_path

        out_bytes, out_mime, is_webp = await run_in_threadpool(
            _transform_image_to_webp_sync, orig_bytes, orig_mime, target_w
        )
        if is_webp:
            await run_in_threadpool(_write_cached_image_sync, variant_cache_path, out_bytes)
            return 200, out_bytes, out_mime, variant_cache_path
        return 200, orig_bytes, orig_mime, orig_cache_path
    except Exception as exc:
        logger.debug("이미지 파생 변환 리더 예외 (%s): %s", raw_input_url, exc)
        return 404, b"", None, ""


@app.get("/api/proxy-image")
@app.get("/api/image-proxy")
async def proxy_image(
    url: str,
    w: Optional[int] = None,
    width: Optional[int] = None,
    v: Optional[str] = None,
):
    import hashlib
    raw_input_url = url
    url = (url or "").replace("&amp;", "&").strip().rstrip(",)")
    if url.startswith("//"):
        url = "https:" + url
    if ("gmarket.co.kr" in url or "esmplus.com" in url or "auction.co.kr" in url) and "?ver=" in url:
        url = url.split("?ver=")[0]
    from backend.core.deal_normalizer import upgrade_lowres_image_url
    url = upgrade_lowres_image_url(url)
    url_hash = hashlib.md5(url.encode('utf-8')).hexdigest()
    orig_cache_path = os.path.join(IMAGE_CACHE_DIR, f"{url_hash}.cache")
    target_w = _quantize_image_width(w, width)
    variant_tag = f"w{target_w}" if target_w is not None else "auto800"
    variant_cache_path = os.path.join(IMAGE_CACHE_DIR, f"{url_hash}_{variant_tag}.webp.cache")
    mem_key = f"{IMAGE_CACHE_DIR}:{url_hash}:{variant_tag}"

    try:
        if not await _is_safe_external_url_async(url):
            raise ValueError("Unsafe or non-http external URL")
        lower_u = url.lower()
        if any(bad in lower_u for bad in ("no_image.svg", "thumb_no_image.svg", "noimage", "dot03.gif", "logo_f.svg", "49841804f3d132d75a6c11b1510af812.png")):
            raise ValueError("Known placeholder image URL")
    except Exception:
        return Response(
            status_code=404,
            headers={"Cache-Control": "no-store"}
        )

    mem_hit = _mem_lru_get(mem_key)
    if mem_hit is not None:
        mem_payload, mem_mime = mem_hit
        return Response(
            content=mem_payload,
            media_type=mem_mime,
            headers={
                "Cache-Control": "public, max-age=604800, immutable",
                "CDN-Cache-Control": "public, max-age=604800, s-maxage=604800, stale-while-revalidate=86400",
                "Vercel-CDN-Cache-Control": "public, s-maxage=604800, stale-while-revalidate=86400",
            },
        )

    loop = asyncio.get_running_loop()
    inflight_map = _PROXY_LOOP_INFLIGHT.get(loop)
    if inflight_map is None:
        inflight_map = {}
        _PROXY_LOOP_INFLIGHT[loop] = inflight_map

    var_key = f"var:{IMAGE_CACHE_DIR}:{url_hash}:{variant_tag}"
    task = inflight_map.get(var_key)
    if task is None:
        task = asyncio.create_task(
            _resolve_image_variant_leader(
                raw_input_url,
                url,
                url_hash,
                orig_cache_path,
                variant_cache_path,
                target_w,
                inflight_map,
            )
        )
        inflight_map[var_key] = task

        def _cleanup_inflight(finished_task: asyncio.Task, m: dict = inflight_map, k: str = var_key) -> None:
            if m.get(k) is finished_task:
                m.pop(k, None)

        task.add_done_callback(_cleanup_inflight)

    status_code, payload, detected_mime, actual_disk_path = await asyncio.shield(task)
    if status_code == 200 and payload and detected_mime:
        _mem_lru_put(mem_key, payload, detected_mime, actual_disk_path)
        return Response(
            content=payload,
            media_type=detected_mime,
            headers={
                "Cache-Control": "public, max-age=604800, immutable",
                "CDN-Cache-Control": "public, max-age=604800, s-maxage=604800, stale-while-revalidate=86400",
                "Vercel-CDN-Cache-Control": "public, s-maxage=604800, stale-while-revalidate=86400",
            },
        )
    return Response(
        status_code=404,
        headers={"Cache-Control": "no-store"}
    )


_PREWARM_BLOCKED_DOMAINS = (
    "ppomppu.co.kr",
    "fmkorea.com",
    "fmkorea.org",
    "quasarzone.com",
    "ruliweb.com",
    "bbasak.com",
    "clien.net",
    "coolenjoy.net",
    "damoang.net",
)


def _is_proxy_prewarm_candidate(raw_url: str | None) -> bool:
    if not raw_url or not isinstance(raw_url, str):
        return False
    u = raw_url.strip()
    if not (u.startswith("http://") or u.startswith("https://")):
        return False
    if u.startswith("http://"):
        return True
    low = u.lower()
    return any(dom in low for dom in _PREWARM_BLOCKED_DOMAINS)


def _query_recent_deal_image_urls_sync(limit: int = 36) -> list[str]:
    from backend.database.session import SessionLocal
    from backend.database import models

    db = SessionLocal()
    try:
        rows = (
            db.query(models.Deal.image_url)
            .filter(models.Deal.is_closed == False, models.Deal.image_url.isnot(None))
            .order_by(models.Deal.indexed_at.desc(), models.Deal.id.desc())
            .limit(limit * 2)
            .all()
        )
        urls: list[str] = []
        seen: set[str] = set()
        for (img_u,) in rows:
            if not img_u or img_u in seen or not _is_proxy_prewarm_candidate(img_u):
                continue
            seen.add(img_u)
            urls.append(img_u)
            if len(urls) >= limit:
                break
        return urls
    finally:
        db.close()


async def prewarm_recent_deal_thumbnails(urls: Optional[list[str]] = None, limit: int = 36) -> None:
    """상위 활성 딜의 프록시 썸네일(auto800 WebP 및 원본 디스크 캐시)을 백그라운드에서 선적재하여 첫 화면 이미지 스켈레톤 지연을 제거합니다."""
    if (
        os.getenv("ENV") == "test"
        or "pytest" in sys.modules
        or "memory" in os.getenv("DATABASE_URL", "").lower()
    ):
        return

    try:
        if urls is None:
            await asyncio.sleep(2.0)
            candidate_urls = await run_in_threadpool(_query_recent_deal_image_urls_sync, limit)
        else:
            seen: set[str] = set()
            candidate_urls = []
            for raw_u in urls:
                if not raw_u or raw_u in seen or not _is_proxy_prewarm_candidate(raw_u):
                    continue
                seen.add(raw_u)
                candidate_urls.append(raw_u)
                if len(candidate_urls) >= limit:
                    break

        if not candidate_urls:
            return

        sem = asyncio.Semaphore(2)

        async def _warm_one(target_u: str) -> None:
            async with sem:
                try:
                    await asyncio.wait_for(proxy_image(url=target_u), timeout=8.0)
                except Exception:
                    pass

        await asyncio.gather(*[_warm_one(u) for u in candidate_urls], return_exceptions=True)
        logger.info("[Prewarm] 썸네일 auto800 WebP 선적재 완료 (%d건)", len(candidate_urls))
    except Exception as exc:
        logger.debug("[Prewarm] 썸네일 선적재 스킵: %s", exc)


def _warm_default_feeds_sync() -> None:
    from backend.database.session import SessionLocal
    from backend.routers import community as community_router

    db = SessionLocal()
    try:
        community_router.get_popular_keywords(limit=10, db=db)
        for sort_key in ("latest", "popular"):
            community_router.get_hot_deals(
                limit=20,
                offset=0,
                category="전체",
                keyword=None,
                platform=None,
                source=None,
                sort=sort_key,
                popular_only=False,
                cursor_indexed_at=None,
                cursor_id=None,
                include_closed=False,
                only_hot=False,
                hide_closed=False,
                db=db,
            )
    finally:
        db.close()


async def _prewarm_hot_feeds_and_thumbs() -> None:
    if (
        os.getenv("ENV") == "test"
        or "pytest" in sys.modules
        or "memory" in os.getenv("DATABASE_URL", "").lower()
    ):
        return
    try:
        await asyncio.sleep(1.0)
        await run_in_threadpool(_warm_default_feeds_sync)
        logger.info("[Prewarm] 기본 피드(latest/popular/popular-keywords) 인메모리 캐시 워밍 완료")
    except Exception as exc:
        logger.debug("[Prewarm] 기본 피드 워밍 스킵: %s", exc)
    await prewarm_recent_deal_thumbnails(urls=None, limit=36)


def schedule_thumbnail_prewarm(urls: Optional[list[str]] = None) -> None:
    if (
        os.getenv("ENV") == "test"
        or "pytest" in sys.modules
        or "memory" in os.getenv("DATABASE_URL", "").lower()
    ):
        return
    try:
        loop = asyncio.get_running_loop()
        if urls is None:
            loop.create_task(_prewarm_hot_feeds_and_thumbs())
        else:
            loop.create_task(prewarm_recent_deal_thumbnails(urls=urls, limit=36))
    except RuntimeError:
        pass

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

    schedule_thumbnail_prewarm(urls=None)
