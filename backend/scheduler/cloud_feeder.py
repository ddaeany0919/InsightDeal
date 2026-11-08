import asyncio
import logging
import os
import re
import sys
import urllib.parse
import httpx

_backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_project_root = os.path.dirname(_backend_dir)
for _p in (_project_root, _backend_dir):
    if _p not in sys.path:
        sys.path.insert(0, _p)

# 클라우드 실행 환경 감지 (GitHub Actions, Cloud Run, Docker 서버 등)
IS_CLOUD_ENV = bool(
    os.environ.get("GITHUB_ACTIONS") == "true"
    or os.environ.get("IS_CLOUD") == "true"
    or os.environ.get("RUN_IN_CLOUD") == "true"
)

if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)
logger = logging.getLogger("CloudFeeder")


def _rotate_cron_log_if_needed(log_path: str = None, max_bytes: int = 3 * 1024 * 1024, keep_lines: int = 1500):
    """
    🧹 크론 실행 누적으로 인해 로그 파일이 max_bytes(기본 3MB)를 초과할 경우,
    파일 끝에서 역방향으로 64KB 청크씩 읽어 마지막 keep_lines(기본 1500줄)만 남기고
    기존 Inode를 보존(In-place Truncate)하며 축소합니다.
    """
    if log_path is None:
        log_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "logs",
            "cloud_feeder_cron.log"
        )
    if not os.path.exists(log_path):
        return
    try:
        size = os.path.getsize(log_path)
        if size <= max_bytes:
            return

        chunk_size = 64 * 1024
        chunks = []
        lines_count = 0

        with open(log_path, "rb") as f:
            f.seek(0, os.SEEK_END)
            file_len = f.tell()
            cur_pos = file_len

            while cur_pos > 0:
                read_size = min(chunk_size, cur_pos)
                cur_pos -= read_size
                f.seek(cur_pos)
                chunk = f.read(read_size)
                chunks.append(chunk)
                lines_count += chunk.count(b"\n")
                if lines_count > keep_lines:
                    break

        if chunks:
            data = b"".join(reversed(chunks))
            split_lines = data.splitlines(keepends=True)
            kept_data = b"".join(split_lines[-keep_lines:])

            # In-place Truncate (기존 Inode 및 >> 쉘 리다이렉션 디스크립터 보존)
            with open(log_path, "r+b") as f:
                f.seek(0)
                f.write(kept_data)
                f.truncate()
            logger.info(f"🔄 로그 로테이션 완료: {log_path} ({size} bytes -> {len(kept_data)} bytes, 최근 {keep_lines}줄 보존)")
    except Exception as e:
        logger.warning(f"로그 파일 로테이션 실패: {e}")


CLOUD_API = os.environ.get("CLOUD_API_URL", "https://34.59.208.14.sslip.io/api/community/ingest-batch")
CLOUD_DEALS_API = os.environ.get("CLOUD_DEALS_API_URL", "https://34.59.208.14.sslip.io/api/community/hot-deals")

# 이미 상세 페이지를 파싱했거나 클라우드 DB에 존재하는 게시글 URL 캐시 (커뮤니티별 분리 + 로컬 파일 영구 캐시 연동)
_SEEN_DETAIL_CACHE: dict[tuple[str, str], dict] = {}
_CACHE_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "logs", "feeder_seen_cache.json")


def _load_local_seen_cache():
    import json, os
    from backend.core.url_utils import is_search_or_community_url
    if os.path.exists(_CACHE_FILE):
        try:
            with open(_CACHE_FILE, "r", encoding="utf-8") as f:
                raw = json.load(f)
            for k_str, val in raw.items():
                if "::" in k_str and isinstance(val, dict):
                    pf, u = k_str.split("::", 1)
                    if is_search_or_community_url(val.get("ecommerce_link", "")):
                        val["ecommerce_link"] = ""
                    _SEEN_DETAIL_CACHE[(pf, u)] = val
        except Exception:
            pass


def _save_local_seen_cache():
    import json, os, tempfile
    try:
        cache_dir = os.path.dirname(_CACHE_FILE)
        os.makedirs(cache_dir, exist_ok=True)
        serializable = {f"{pf}::{u}": v for (pf, u), v in list(_SEEN_DETAIL_CACHE.items())[-4000:]}
        fd, tmp_file = tempfile.mkstemp(dir=cache_dir, prefix="feeder_cache_", suffix=".tmp")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(serializable, f, ensure_ascii=False)
        os.replace(tmp_file, _CACHE_FILE)
    except Exception as e:
        logger.warning(f"캐시 원자적 저장 실패: {e}")


async def _preload_existing_urls(http_client: httpx.AsyncClient):
    if _SEEN_DETAIL_CACHE:
        return
    _load_local_seen_cache()
    try:
        from backend.core.url_utils import normalize_url, is_search_or_community_url
        platforms = ("펨코", "뽐뿌", "퀘이사존", "알리뽐뿌", "뽐뿌해외", "루리웹", "클리앙", "빠삭국내", "빠삭해외", "빠삭육아")
        for pf in platforms:
            for off in (0, 100, 200):
                res = await http_client.get(f"{CLOUD_DEALS_API}?limit=100&offset={off}&platform={pf}")
                if res.status_code == 200:
                    payload = res.json()
                    deals = payload if isinstance(payload, list) else payload.get("items", payload.get("deals", []))
                    if not deals:
                        break
                    for d in deals:
                        if not isinstance(d, dict):
                            continue
                        raw_u = d.get("post_url") or d.get("post_link") or d.get("source_url") or d.get("url")
                        if raw_u:
                            u = normalize_url(raw_u)
                            honey = int(d.get("honey_score") or 0)
                            is_hot = bool(d.get("is_super_hotdeal") or honey >= 100)
                            raw_ecom = d.get("ecommerce_url") or d.get("ecommerce_link") or ""
                            clean_ecom = "" if is_search_or_community_url(raw_ecom) else raw_ecom
                            _SEEN_DETAIL_CACHE[(pf, u)] = {
                                "title": d.get("title") or "",
                                "price": int(d.get("price") or 0),
                                "currency": d.get("currency") or "KRW",
                                "content_html": d.get("content_html") or "",
                                "ecommerce_link": clean_ecom,
                                "image_url": d.get("image_url") or "",
                                "shipping_fee": d.get("shipping_fee") or "",
                                "is_closed": bool(d.get("is_closed", False)),
                                "is_super_hotdeal": is_hot,
                                "like_count": int(d.get("like_count") or 0),
                                "view_count": int(d.get("view_count") or 0),
                                "comment_count": int(d.get("comment_count") or 0),
                                "created_at": d.get("created_at") or "",
                            }
                    if len(deals) < 100:
                        break
        logger.info(f"📦 기존 DB 게시글 {len(_SEEN_DETAIL_CACHE)}건 상세 캐시 로드 완료 (차등 업데이트 및 스킵 활성화)")
    except Exception as e:
        logger.warning(f"기존 DB URL 프리로드 생략: {e}")


async def _fetch_jsmon_and_hotgusa_enrichment(http_client: httpx.AsyncClient) -> dict[str, dict]:
    """
    🌐 [클라우드 Zero-403 초고속 아웃링크 & 상태 브릿지]
    1) https://t.me/s/jsmon (0.2s, 200 OK): 뽐뿌(id=ppomppu) 및 퀘이사존(qb_saleinfo)의
       실제 디코딩된 쇼핑몰 직접 링크(ecommerce_link), 본문 텍스트, 쇼핑몰명 추출
    2) https://www.hotgusa.com/api/products (0.2s, 200 OK): 뽐뿌(0_) 및 퀘이사존(3_)의
       실시간 마감 상태(is_end), 추천수(recm_cnt), 조회수(view_cnt), 댓글수(cmt_cnt), 고화질 썸네일 추출
    """
    from bs4 import BeautifulSoup
    from backend.core.url_utils import normalize_url

    enrichment_map: dict[str, dict] = {}

    # 1. t.me/s/jsmon 파싱 (최근 5페이지 = 100개 메시지)
    try:
        jsmon_urls = ["https://t.me/s/jsmon"]
        r0 = await http_client.get(jsmon_urls[0], headers={"User-Agent": "Mozilla/5.0"}, timeout=8.0)
        if r0.status_code == 200:
            befores = re.findall(r'href="/s/jsmon\?before=(\d+)"', r0.text)
            if befores:
                base_bf = int(befores[0])
                for step in (0, 20, 40, 60):
                    if base_bf - step > 0:
                        jsmon_urls.append(f"https://t.me/s/jsmon?before={base_bf - step}")

        for idx_u, j_url in enumerate(jsmon_urls):
            html_txt = r0.text if idx_u == 0 and r0.status_code == 200 else None
            if html_txt is None:
                rj = await http_client.get(j_url, headers={"User-Agent": "Mozilla/5.0"}, timeout=8.0)
                if rj.status_code == 200:
                    html_txt = rj.text
            if not html_txt:
                continue

            soup = BeautifulSoup(html_txt, "html.parser")
            for w in soup.select(".tgme_widget_message_wrap"):
                text_el = w.select_one(".tgme_widget_message_text")
                links = [
                    a.get("href", "").strip()
                    for a in w.select("a[href]")
                    if a.get("href") and a.get("href").startswith("http") and "t.me/" not in a.get("href") and "telegram.org" not in a.get("href")
                ]
                post_url = ""
                ecom_url = ""
                for lk in links:
                    if "ppomppu.co.kr" in lk or "quasarzone.com" in lk:
                        post_url = normalize_url(lk)
                    elif lk.startswith("http"):
                        ecom_url = lk

                if not post_url:
                    continue

                full_txt = text_el.get_text(" ", strip=True) if text_el else w.get_text(" ", strip=True)
                photo_el = w.select_one(".tgme_widget_message_photo_wrap")
                img_u = ""
                if photo_el and photo_el.get("style"):
                    m_img = re.search(r"background-image:url\('([^']+)'\)", photo_el["style"])
                    if m_img:
                        img_u = m_img.group(1)

                entry = enrichment_map.setdefault(post_url, {})
                if ecom_url and not entry.get("ecommerce_link"):
                    entry["ecommerce_link"] = ecom_url
                if img_u and not entry.get("image_url"):
                    entry["image_url"] = img_u
                if full_txt and not entry.get("content_html"):
                    entry["content_html"] = f"<p>{full_txt}</p>"
    except Exception as je:
        logger.warning(f"⚠️ [jsmon 브릿지] 보강 데이터 페칭 경고: {je}")

    # 2. hotgusa API 파싱 (뽐뿌 0_, 퀘이사존 3_ 실시간 마감/추천/조회수/썸네일 동기화)
    try:
        hg_url = (
            "https://www.hotgusa.com/api/products?communities=0,3&isOutOfStock=true"
            "&category=&sort=date&shop=all&timeRange=24h&hiddenKeywords="
            "&lastId=9_99999999999&lastValue=2099-12-31T23:59:59%2B09:00"
        )
        rh = await http_client.get(hg_url, headers={"User-Agent": "Mozilla/5.0"}, timeout=8.0)
        if rh.status_code == 200:
            payload = rh.json()
            rows = payload.get("products", []) if isinstance(payload, dict) else (payload if isinstance(payload, list) else [])
            if isinstance(rows, list):
                for row in rows:
                    if not isinstance(row, dict):
                        continue
                    cid = row.get("community_id")
                    pno = row.get("product_no")
                    if cid is None or not pno:
                        raw_id = str(row.get("id") or "")
                        if "_" in raw_id:
                            p_cid, p_no = raw_id.split("_", 1)
                            cid = int(p_cid) if p_cid.isdigit() else cid
                            pno = p_no or pno
                    if cid == 0 and pno:
                        p_url = normalize_url(f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no={pno}")
                    elif cid == 3 and pno:
                        p_url = normalize_url(f"https://quasarzone.com/bbs/qb_saleinfo/views/{pno}")
                    else:
                        continue

                    entry = enrichment_map.setdefault(p_url, {})
                    if row.get("is_end"):
                        entry["is_closed"] = True
                    recm = int(row.get("recm_cnt") or 0)
                    vcnt = int(row.get("view_cnt") or 0)
                    ccnt = int(row.get("cmt_cnt") or 0)
                    if recm > int(entry.get("like_count") or 0):
                        entry["like_count"] = recm
                    if vcnt > int(entry.get("view_count") or 0):
                        entry["view_count"] = vcnt
                    if ccnt > int(entry.get("comment_count") or 0):
                        entry["comment_count"] = ccnt
                    if recm >= 8 or vcnt >= 2500:
                        entry["is_super_hotdeal"] = True
                    if row.get("mall_name") and not entry.get("shop_name"):
                        entry["shop_name"] = str(row["mall_name"]).strip()
                    if row.get("has_thumb") and not entry.get("image_url"):
                        entry["image_url"] = f"https://d2w999htn2iva3.cloudfront.net/thumbs/{cid}/{pno}.jpg"
    except Exception as he:
        logger.warning(f"⚠️ [hotgusa API] 보강 데이터 페칭 경고: {he}")

    return enrichment_map


async def scrape_and_push_once():
    _rotate_cron_log_if_needed()
    from datetime import datetime, timedelta, timezone
    from backend.core.url_utils import normalize_url
    from backend.scrapers.ppomppu_scraper import PpomppuScraper
    from backend.scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
    from backend.scrapers.alippomppu_scraper import AlippomppuScraper
    from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
    from backend.scrapers.fmkorea_scraper import FmkoreaScraper
    from backend.scrapers.ruliweb_scraper import RuliwebScraper
    from backend.scrapers.clien_scraper import ClienScraper
    from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
    from backend.scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
    from backend.scrapers.bbasak_parenting_scraper import BbasakParentingScraper

    # KST 기준 오늘 00:00:00의 UTC ISO 문자열 계산
    kst = timezone(timedelta(hours=9))
    now_utc = datetime.now(timezone.utc)
    today_start_kst = datetime.now(kst).replace(hour=0, minute=0, second=0, microsecond=0)
    today_start_utc_iso = today_start_kst.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S")

    targets = [
        ("fmkorea", "펨코", "https://www.fmkorea.com/hotdeal", FmkoreaScraper, 6, 1 if IS_CLOUD_ENV else 12),
        ("ppomppu", "뽐뿌", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu", PpomppuScraper, 1, 1 if IS_CLOUD_ENV else 14),
        ("quasarzone", "퀘이사존", "https://quasarzone.com/bbs/qb_saleinfo", QuasarzoneScraper, 5, 1 if IS_CLOUD_ENV else 3),
        ("ali_ppomppu", "알리뽐뿌", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu8", AlippomppuScraper, 4, 1 if IS_CLOUD_ENV else 5),
        ("ppomppu_overseas", "뽐뿌해외", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4", PpomppuOverseasScraper, 10, 1 if IS_CLOUD_ENV else 2),
        ("ruliweb", "루리웹", "https://bbs.ruliweb.com/market/board/1020", RuliwebScraper, 2, 1 if IS_CLOUD_ENV else 2),
        ("clien", "클리앙", "https://www.clien.net/service/board/jirum", ClienScraper, 3, 1 if IS_CLOUD_ENV else 2),
        ("bbasak_domestic", "빠삭국내", "https://bbasak.com/bbs/board.php?bo_table=bbasak1", BbasakDomesticScraper, 7, 1 if IS_CLOUD_ENV else 2),
        ("bbasak_overseas", "빠삭해외", "https://bbasak.com/bbs/board.php?bo_table=bbasak2", BbasakOverseasScraper, 8, 1),
        ("bbasak_parenting", "빠삭육아", "https://bbasak.com/bbs/board.php?bo_table=bbasak3", BbasakParentingScraper, 9, 1),
    ]

    community_sem = asyncio.Semaphore(4)

    async with httpx.AsyncClient(timeout=30.0 if IS_CLOUD_ENV else 60.0) as http_client:
        await _preload_existing_urls(http_client)
        enrichment_map = await _fetch_jsmon_and_hotgusa_enrichment(http_client)

        async def process_single_target(target):
            name, display_name, base_url, scraper_cls, concurrency, max_pages = target
            async with community_sem:
                try:
                    sc = scraper_cls(concurrency)
                    async with sc:
                        cache_pf = display_name
                        items = []
                        seen_page_urls = set()

                        # 1. 뽐뿌 계열 클라우드 모드 시 Vercel 서울 릴레이 RSS 1-RTT 일괄 수집
                        if "ppomppu" in name and IS_CLOUD_ENV:
                            board_id = "ppomppu8" if name == "ali_ppomppu" else ("ppomppu4" if name == "ppomppu_overseas" else "ppomppu")
                            rss_target = f"https://www.ppomppu.co.kr/rss.php?id={board_id}"
                            rss_relay_url = f"https://insightdeal.vercel.app/api/scraper-relay?url={urllib.parse.quote(rss_target, safe='')}"
                            try:
                                res = await http_client.get(rss_relay_url, timeout=10.0)
                                if res.status_code == 200 and "<item>" in res.text:
                                    rss_items = await sc.parse_list(res.text)
                                    if rss_items:
                                        logger.info(f"⚡ [{display_name}] 클라우드 RSS 릴레이로 {len(rss_items)}건 초고속 1-RTT 수집 완료 (0.5s)")
                                        for pit in rss_items:
                                            norm_u = normalize_url(pit["url"])
                                            if norm_u not in seen_page_urls:
                                                seen_page_urls.add(norm_u)
                                                pit["url"] = norm_u
                                                pit["_page_no"] = 1
                                                items.append(pit)
                            except Exception as rss_err:
                                logger.warning(f"⚠️ [{display_name}] 클라우드 RSS 수집 실패 ({rss_err}), 웹 크롤링 폴백")

                        # 2. 일반 목록 페이지네이션 수집 (RSS로 수집되지 않았거나 타 커뮤니티인 경우)
                        if not items:
                            for page_no in range(1, max_pages + 1):
                                if page_no == 1:
                                    page_url = sc.list_url
                                elif "clien" in name:
                                    sep = "&" if "?" in sc.list_url else "?"
                                    page_url = f"{sc.list_url}{sep}po={page_no - 1}"
                                else:
                                    sep = "&" if "?" in sc.list_url else "?"
                                    page_url = f"{sc.list_url}{sep}page={page_no}"

                                page_items = await sc.run(page_url)
                                if not page_items:
                                    break

                                unseen_today_on_page = 0
                                today_on_page = 0
                                for pit in page_items:
                                    norm_u = normalize_url(pit["url"])
                                    if norm_u in seen_page_urls:
                                        continue
                                    seen_page_urls.add(norm_u)
                                    pit["url"] = norm_u
                                    pit["_page_no"] = page_no
                                    items.append(pit)
                                    posted_iso = pit.get("posted_at") or ""
                                    is_today = (not posted_iso) or (posted_iso >= today_start_utc_iso)
                                    if is_today:
                                        today_on_page += 1
                                        if (cache_pf, norm_u) not in _SEEN_DETAIL_CACHE:
                                            unseen_today_on_page += 1

                                if page_no >= 1 and (unseen_today_on_page == 0 or today_on_page == 0):
                                    break

                        # 2.5 외부 브릿지(jsmon + hotgusa) 보강 데이터 즉시 병합
                        for pit in items:
                            norm_u = normalize_url(pit["url"])
                            ext = enrichment_map.get(norm_u)
                            if ext:
                                if ext.get("ecommerce_link") and not pit.get("ecommerce_link"):
                                    pit["ecommerce_link"] = ext["ecommerce_link"]
                                if ext.get("image_url") and not pit.get("image_url"):
                                    pit["image_url"] = ext["image_url"]
                                if ext.get("shop_name") and not pit.get("shop_name"):
                                    pit["shop_name"] = ext["shop_name"]
                                if ext.get("content_html") and (not pit.get("content_html") or len(pit.get("content_html", "")) < 40):
                                    pit["content_html"] = ext["content_html"]
                                if ext.get("is_closed"):
                                    pit["is_closed"] = True
                                if ext.get("is_super_hotdeal"):
                                    pit["is_super_hotdeal"] = True
                                if int(ext.get("like_count") or 0) > int(pit.get("like_count") or 0):
                                    pit["like_count"] = int(ext["like_count"])
                                if int(ext.get("view_count") or 0) > int(pit.get("view_count") or 0):
                                    pit["view_count"] = int(ext["view_count"])
                                if int(ext.get("comment_count") or 0) > int(pit.get("comment_count") or 0):
                                    pit["comment_count"] = int(ext["comment_count"])

                        # 2.6 [게시글 삭제(Post Vanished) 감지 - 고유 Post ID 단조 증가 범위 기반]:
                        # 각 게시판의 고유 게시글 번호(Post ID)는 단조 증가하므로, 현재 1페이지 목록의 (최소 ID ~ 최대 ID) 범위 사이에
                        # 위치한 활성 게시글(is_closed=False)이 1페이지 목록에서 증발했다면 2페이지로 밀려난 것이 아니라
                        # 작성자/관리자에 의해 실제 삭제된 게시글임이 수학적으로 보장됩니다.
                        def _extract_board_and_post_id(u: str) -> tuple[str, int]:
                            if not u:
                                return ("", 0)
                            if "ppomppu.co.kr" in u:
                                m_b = re.search(r"id=(ppomppu\d*)", u)
                                m_n = re.search(r"no=(\d+)", u)
                                if m_b and m_n:
                                    return (m_b.group(1), int(m_n.group(1)))
                            elif "fmkorea.com/" in u:
                                m_n = re.search(r"fmkorea\.com/(?:index\.php\?.*?document_srl=)?(\d{7,12})", u)
                                if m_n:
                                    return ("fmkorea", int(m_n.group(1)))
                            elif "quasarzone.com" in u:
                                m_n = re.search(r"/views/(\d+)", u)
                                if m_n:
                                    return ("quasarzone", int(m_n.group(1)))
                            elif "ruliweb.com" in u:
                                m_n = re.search(r"/read/(\d+)", u)
                                if m_n:
                                    return ("ruliweb", int(m_n.group(1)))
                            elif "clien.net" in u:
                                m_n = re.search(r"/jirum/(\d+)", u)
                                if m_n:
                                    return ("clien", int(m_n.group(1)))
                            elif "bbasak.com" in u:
                                m_b = re.search(r"bo_table=(bbasak\d+)", u)
                                m_n = re.search(r"wr_id=(\d+)", u)
                                if m_b and m_n:
                                    return (m_b.group(1), int(m_n.group(1)))
                            return ("", 0)

                        page_board_ids: dict[str, list[int]] = {}
                        for p_u in seen_page_urls:
                            b_key, p_id = _extract_board_and_post_id(p_u)
                            if b_key and p_id > 0:
                                page_board_ids.setdefault(b_key, []).append(p_id)

                        for b_key, id_list in page_board_ids.items():
                            # 텔레그램 큐레이션 브릿지(퀘이사존 클라우드 9~11건 등)처럼 일부 게시글만 발췌하는 소스는
                            # 전체 1페이지 전수 목록이 아니므로 증발 감지 대상에서 제외하고, 최소 18건 이상의 정식 1페이지 목록에만 적용!
                            if len(id_list) < 18 or (b_key == "quasarzone" and IS_CLOUD_ENV):
                                continue
                            sorted_ids = sorted(id_list)
                            # 하단 경계 흔들림 방지를 위해 하위 2번째 ID를 바닥(floor)으로 설정
                            floor_id = sorted_ids[1]
                            ceil_id = sorted_ids[-1]
                            for (c_pf, c_url), c_val in list(_SEEN_DETAIL_CACHE.items()):
                                if c_pf != cache_pf or c_val.get("is_closed") or not c_val.get("title"):
                                    continue
                                cb_key, cp_id = _extract_board_and_post_id(c_url)
                                if cb_key == b_key and (floor_id < cp_id < ceil_id) and c_url not in seen_page_urls:
                                    logger.info(f"🗑️ [{display_name}] 1페이지 범위({floor_id}~{ceil_id}) 내 게시글 삭제 감지(ID={cp_id}) -> 자동 마감 처리: {c_val.get('title')} ({c_url})")
                                    items.append({
                                        "title": c_val["title"],
                                        "url": c_url,
                                        "price": int(c_val.get("price") or 0),
                                        "currency": c_val.get("currency") or "KRW",
                                        "content_html": c_val.get("content_html") or "",
                                        "ecommerce_link": c_val.get("ecommerce_link") or "",
                                        "image_url": c_val.get("image_url") or "",
                                        "shipping_fee": c_val.get("shipping_fee") or "",
                                        "is_closed": True,
                                        "is_super_hotdeal": bool(c_val.get("is_super_hotdeal", False)),
                                        "like_count": int(c_val.get("like_count") or 0),
                                        "view_count": int(c_val.get("view_count") or 1500),
                                        "comment_count": int(c_val.get("comment_count") or 0),
                                        "_page_no": 1,
                                    })

                        enriched = []
                        skipped_count = 0
                        items_needing_detail = []

                        for item in items:
                            item_url = normalize_url(item["url"])
                            item["url"] = item_url
                            item_page_no = item.pop("_page_no", 1)
                            cache_key = (cache_pf, item_url)

                            if cache_key in _SEEN_DETAIL_CACHE:
                                cached_detail = _SEEN_DETAIL_CACHE[cache_key]
                                new_closed = bool(item.get("is_closed", False))
                                old_closed = bool(cached_detail.get("is_closed", False))
                                new_hot = bool(item.get("is_super_hotdeal", False))
                                old_hot = bool(cached_detail.get("is_super_hotdeal", False))
                                new_likes = int(item.get("like_count") or 0)
                                old_likes = int(cached_detail.get("like_count") or 0)
                                new_ecom = item.get("ecommerce_link") or ""
                                old_ecom = cached_detail.get("ecommerce_link") or ""

                                # 1) 마감 상태 변경(False->True), 2) 포텐/인기 핫딜 승격(False->True),
                                # 3) 추천수 유의미 증가(+3 이상 또는 8개 돌파), 4) 신규 쇼핑몰 직접 링크 확보 시에만
                                # 상세 페이지 재요청(0-RTT) 없이 캐시된 본문과 합쳐 서버로 차등 업데이트 전송!
                                from backend.core.url_utils import is_search_or_community_url

                                is_old_ecom_empty_or_search = (
                                    not old_ecom
                                    or is_search_or_community_url(old_ecom)
                                )
                                is_new_ecom_valid_direct = bool(new_ecom) and not is_search_or_community_url(new_ecom)

                                is_missing_cached_img = not (cached_detail.get("image_url") or item.get("image_url"))
                                status_changed = (
                                    (new_closed and not old_closed)
                                    or (new_hot and not old_hot)
                                    or (new_likes >= 8 and old_likes < 8)
                                    or (new_likes - old_likes >= 3)
                                    or (is_new_ecom_valid_direct and is_old_ecom_empty_or_search)
                                    or is_missing_cached_img
                                )
                                if not status_changed:
                                    skipped_count += 1
                                    continue

                                for k in ("content_html", "image_url", "shipping_fee", "options_data", "has_options"):
                                    v = cached_detail.get(k)
                                    if v and not item.get(k):
                                        item[k] = v

                                if not item.get("image_url"):
                                    try:
                                        img_fb = await sc.fetch_og_image(item.get("ecommerce_link", ""), item.get("title", ""))
                                        if img_fb:
                                            item["image_url"] = img_fb
                                            cached_detail["image_url"] = img_fb
                                    except Exception:
                                        pass

                                # 직링크 캐시 갱신 및 오염 방지 (검색 링크/커뮤니티 링크 역복사 차단)
                                if is_new_ecom_valid_direct:
                                    item["ecommerce_link"] = new_ecom
                                    cached_detail["ecommerce_link"] = new_ecom
                                elif not item.get("ecommerce_link") and not is_search_or_community_url(old_ecom):
                                    item["ecommerce_link"] = old_ecom
                                elif is_search_or_community_url(item.get("ecommerce_link", "")):
                                    item["ecommerce_link"] = ""

                                cached_detail["is_closed"] = new_closed
                                cached_detail["is_super_hotdeal"] = new_hot or old_hot or (new_likes >= 8)
                                cached_detail["like_count"] = max(new_likes, old_likes)
                                enriched.append(item)
                                continue

                            # 신규 게시글 상세 필요 여부 판별
                            # 외부 브릿지(Telegram/jsmon 등)에서 이미 완전한 본문과 링크를 확보한 경우에만 상세 페칭 스킵
                            is_bridge_item = (
                                bool(item.pop("_from_bridge", False))
                                or "bridge_id=" in item_url
                                or ("quasarzone.com" in item_url and item.get("content_html"))
                                or ("fmkorea.com" in item_url and item.get("content_html"))
                            )
                            if is_bridge_item:
                                need_detail = False
                                if not item.get("image_url"):
                                    try:
                                        img_fb = await sc.fetch_og_image(item.get("ecommerce_link", ""), item.get("title", ""))
                                        if img_fb:
                                            item["image_url"] = img_fb
                                    except Exception:
                                        pass
                            else:
                                is_ppomppu = "ppomppu.co.kr" in item_url
                                content_txt = item.get("content_html") or ""
                                # 뽐뿌 계열(ppomppu, ali_ppomppu, ppomppu_overseas)은 클라우드 환경에서도
                                # Vercel 서울 릴레이(/api/scraper-relay)를 통해 온전한 상세 본문(150자 이상),
                                # 직링크(ecommerce_link), 옵션 데이터를 정상 수집해야 합니다.
                                # RSS 피드로 가져온 80~100자 요약문은 본문으로 인정하지 않고 get_detail()을 호출합니다.
                                has_full_content = bool(content_txt) and len(content_txt) >= 150
                                from backend.core.url_utils import is_search_or_community_url
                                has_direct_ecom = bool(item.get("ecommerce_link")) and not is_search_or_community_url(item.get("ecommerce_link", ""))

                                if is_ppomppu:
                                    need_detail = (item_page_no == 1) or (not has_full_content) or (not has_direct_ecom) or (not item.get("image_url"))
                                else:
                                    need_detail = (item_page_no == 1) or (not item.get("image_url"))
                                    if item.get("content_html") and item.get("image_url"):
                                        need_detail = False

                            # 다중 상품/모음전 패턴 게시글 감지 (모음, 골라담기, 외 \d+, 공구, 총정리, \d+종 등)
                            # 모음전의 경우 본문 내부의 [상품이미지: URL] 및 번호별 옵션을 온전히 수집하기 위해 상세 페칭 강제 활성화
                            item_title = item.get("title", "") or ""
                            is_multi_title = bool(
                                re.search(r'(?:외\s*\d+|골라담기|모음|선택|공구|총정리|\d+\s*종|다양|특가\s*모음)', item_title)
                                or (re.search(r'/[^/]+(?:원|만|\$|달러)', item_title) and re.search(r'(?:원|만|\$|달러).*/', item_title))
                            )
                            if is_multi_title:
                                need_detail = True

                            if need_detail:
                                items_needing_detail.append(item)
                            else:
                                enriched.append(item)

                        # 3. 비동기 병렬 상세 페칭 (신규 게시글만 대상, Vercel 릴레이 및 뽐뿌 부하 방지를 위해 클라우드 동시 3개 제한)
                        if items_needing_detail:
                            target_details = items_needing_detail[:10] if IS_CLOUD_ENV else items_needing_detail
                            detail_sem = asyncio.Semaphore(3 if IS_CLOUD_ENV else 5)

                            async def fetch_one_detail(it):
                                async with detail_sem:
                                    try:
                                        det = await sc.get_detail(it["url"], it.get("title", ""))
                                        if det:
                                            for k, v in det.items():
                                                if k == "price" and (not v or v == 0) and it.get("price", 0) > 0:
                                                    continue
                                                if v is not None and v != "":
                                                    it[k] = v
                                            # 멀티 옵션 추출 (본문 및 타이틀 기반 정밀 옵션 추출)
                                            try:
                                                from backend.services.aggregator_service import extract_structured_multi_options
                                                opts = extract_structured_multi_options(
                                                    raw_title=it.get("title", ""),
                                                    content_html=it.get("content_html", ""),
                                                    default_currency=it.get("currency") or "KRW",
                                                    default_url=it.get("ecommerce_link") or ""
                                                )
                                                if opts and len(opts) >= 2:
                                                    it["options_data"] = opts
                                                    it["has_options"] = True
                                            except Exception:
                                                pass
                                    except Exception as de:
                                        logger.warning(f"[{display_name}] detail warning: {de}")
                                return it

                            detail_results = await asyncio.gather(*[fetch_one_detail(it) for it in target_details], return_exceptions=True)
                            for r in detail_results:
                                if isinstance(r, dict):
                                    enriched.append(r)
                            for it in items_needing_detail[len(target_details):]:
                                enriched.append(it)

                        # 4. 내용 및 메타데이터 정규화, 캐시 등록
                        final_enriched = []
                        for item in enriched:
                            cache_key = (cache_pf, item["url"])
                            if not item.get("content_html"):
                                shop_txt = item.get("shop_name") or display_name
                                ship_txt = item.get("shipping_fee") or ""
                                img_tag = f'<p><img src="{item["image_url"]}" alt="thumbnail" /></p>' if item.get("image_url") else ""
                                item["content_html"] = f"{img_tag}<p><strong>{item.get('title', '')}</strong></p><p>출처: {shop_txt} {ship_txt}</p>"

                            raw_t = item.get("title") or ""
                            p_val = int(item.get("price") or 0)
                            if "적립" in raw_t and (p_val > 100 or any(k in raw_t for k in ("슈퍼적립", "즉시적립", "캐시적립", "만 적립", "만원 적립"))):
                                raw_t = raw_t.replace("슈퍼적립", "슈퍼혜택").replace("즉시적립", "즉시페이백").replace("캐시적립", "캐시백").replace("적립", "페이백")

                            item["title"] = raw_t
                            item["view_count"] = max(int(item.get("view_count") or 0), 1500)

                            _SEEN_DETAIL_CACHE[cache_key] = {
                                "title": item["title"],
                                "price": p_val,
                                "currency": item.get("currency") or "KRW",
                                "content_html": item.get("content_html") or "",
                                "ecommerce_link": item.get("ecommerce_link") or "",
                                "image_url": item.get("image_url") or "",
                                "shipping_fee": item.get("shipping_fee") or "",
                                "is_closed": bool(item.get("is_closed", False)),
                                "is_super_hotdeal": bool(item.get("is_super_hotdeal", False)),
                                "like_count": int(item.get("like_count") or 0),
                                "view_count": int(item.get("view_count") or 1500),
                                "comment_count": int(item.get("comment_count") or 0),
                                "created_at": item.get("posted_at") or now_utc.strftime("%Y-%m-%dT%H:%M:%SZ"),
                                "options_data": item.get("options_data"),
                                "has_options": bool(item.get("has_options", False)),
                            }
                            final_enriched.append(item)

                        if skipped_count > 0 and not final_enriched:
                            logger.info(f"⏭️ [{display_name}] 신규/상태변경 게시글 없음 (기존 캐시 {skipped_count}건 전체 스킵)")

                        # 5. 클라우드 서버 전송
                        if final_enriched:
                            chunk_size = 5
                            total_processed = 0
                            for idx in range(0, len(final_enriched), chunk_size):
                                chunk = final_enriched[idx:idx + chunk_size]
                                for attempt in range(2):
                                    try:
                                        res = await http_client.post(
                                            CLOUD_API,
                                            json={
                                                "community_name": name,
                                                "display_name": display_name,
                                                "base_url": base_url,
                                                "deals": chunk,
                                            },
                                        )
                                        if res.status_code == 200:
                                            total_processed += res.json().get("processed", len(chunk))
                                            break
                                    except Exception:
                                        pass
                                    await asyncio.sleep(0.5 if IS_CLOUD_ENV else 1.5)
                            logger.info(f"✅ [{display_name}] 클라우드 서버 전송 완료 (신규/상태갱신 {len(final_enriched)}건 중 {total_processed}건 반영)")
                except Exception as e:
                    logger.error(f"❌ [{display_name}] 수집/전송 오류: {e}")

        # 10개 커뮤니티 타겟을 asyncio.Semaphore(4) 기반 asyncio.gather 병렬 수집
        await asyncio.gather(*[process_single_target(t) for t in targets], return_exceptions=True)
        _save_local_seen_cache()
        _rotate_cron_log_if_needed()


async def main():
    one_shot = "--one-shot" in sys.argv
    try:
        while True:
            await scrape_and_push_once()
            if one_shot:
                break
            await asyncio.sleep(60)
    finally:
        _rotate_cron_log_if_needed()


if __name__ == "__main__":
    asyncio.run(main())
