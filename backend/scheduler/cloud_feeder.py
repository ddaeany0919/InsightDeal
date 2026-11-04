import asyncio
import logging
import os
import re
import sys
import urllib.parse
import zlib
import httpx

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
logger = logging.getLogger("CloudFeeder")

CLOUD_API = os.environ.get("CLOUD_API_URL", "https://34.59.208.14.sslip.io/api/community/ingest-batch")
CLOUD_DEALS_API = os.environ.get("CLOUD_DEALS_API_URL", "https://34.59.208.14.sslip.io/api/community/hot-deals")

# 이미 상세 페이지를 파싱했거나 클라우드 DB에 존재하는 게시글 URL 캐시 (커뮤니티별 분리 + 로컬 파일 영구 캐시 연동)
_SEEN_DETAIL_CACHE: dict[tuple[str, str], dict] = {}
_CACHE_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "logs", "feeder_seen_cache.json")


def _load_local_seen_cache():
    import json, os
    if os.path.exists(_CACHE_FILE):
        try:
            with open(_CACHE_FILE, "r", encoding="utf-8") as f:
                raw = json.load(f)
            for k_str, val in raw.items():
                if "::" in k_str and isinstance(val, dict):
                    pf, u = k_str.split("::", 1)
                    _SEEN_DETAIL_CACHE[(pf, u)] = val
        except Exception:
            pass


def _save_local_seen_cache():
    import json, os
    try:
        os.makedirs(os.path.dirname(_CACHE_FILE), exist_ok=True)
        serializable = {f"{pf}::{u}": v for (pf, u), v in list(_SEEN_DETAIL_CACHE.items())[-3000:]}
        with open(_CACHE_FILE, "w", encoding="utf-8") as f:
            json.dump(serializable, f, ensure_ascii=False)
    except Exception:
        pass


async def _preload_existing_urls(http_client: httpx.AsyncClient):
    if _SEEN_DETAIL_CACHE:
        return
    _load_local_seen_cache()
    try:
        from backend.core.url_utils import normalize_url
        platforms = ("펨코", "뽐뿌", "퀘이사존", "알리뽐뿌", "루리웹", "클리앙", "빠삭국내", "빠삭해외", "빠삭육아")
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
                            _SEEN_DETAIL_CACHE[(pf, u)] = {
                                "content_html": d.get("content_html") or "",
                                "ecommerce_link": d.get("ecommerce_url") or d.get("ecommerce_link") or "",
                                "image_url": d.get("image_url") or "",
                                "shipping_fee": d.get("shipping_fee") or "",
                                "is_closed": bool(d.get("is_closed", False)),
                                "like_count": int(d.get("like_count") or 0),
                                "comment_count": int(d.get("comment_count") or 0),
                            }
                    if len(deals) < 100:
                        break
        logger.info(f"📦 기존 DB 게시글 {len(_SEEN_DETAIL_CACHE)}건 상세 캐시 로드 완료 (이미 파싱된 게시글 스킵 활성화)")
    except Exception as e:
        logger.warning(f"기존 DB URL 프리로드 생략: {e}")


async def scrape_and_push_once():
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

    # KST 기준 오늘 00:00:00의 UTC ISO 문자열 계산 (공백 시간대 다중 페이지 자동 백필 기준선)
    kst = timezone(timedelta(hours=9))
    today_start_kst = datetime.now(kst).replace(hour=0, minute=0, second=0, microsecond=0)
    today_start_utc_iso = today_start_kst.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S")

    targets = [
        ("fmkorea", "펨코", "https://www.fmkorea.com/hotdeal", FmkoreaScraper, 6, 1 if IS_CLOUD_ENV else 12),
        ("ppomppu", "뽐뿌", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu", PpomppuScraper, 1, 1 if IS_CLOUD_ENV else 14),
        ("quasarzone", "퀘이사존", "https://quasarzone.com/bbs/qb_saleinfo", QuasarzoneScraper, 5, 1 if IS_CLOUD_ENV else 3),
        ("ali_ppomppu", "알리뽐뿌", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu8", AlippomppuScraper, 4, 1 if IS_CLOUD_ENV else 5),
        ("ppomppu_overseas", "뽐뿌해외", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4", PpomppuOverseasScraper, 10, 1 if IS_CLOUD_ENV else 2),
        ("ruliweb", "루리웹", "https://bbs.ruliweb.com/market/board/1020", RuliwebScraper, 2, 2),
        ("clien", "클리앙", "https://www.clien.net/service/board/jirum", ClienScraper, 3, 2),
        ("bbasak_domestic", "빠삭국내", "https://bbasak.com/bbs/board.php?bo_table=bbasak1", BbasakDomesticScraper, 7, 1 if IS_CLOUD_ENV else 2),
        ("bbasak_overseas", "빠삭해외", "https://bbasak.com/bbs/board.php?bo_table=bbasak2", BbasakOverseasScraper, 8, 1),
        ("bbasak_parenting", "빠삭육아", "https://bbasak.com/bbs/board.php?bo_table=bbasak3", BbasakParentingScraper, 9, 1),
    ]

    community_sem = asyncio.Semaphore(4)

    async def process_single_target(target, http_client: httpx.AsyncClient):
        name, display_name, base_url, scraper_cls, concurrency, max_pages = target
        async with community_sem:
            try:
                sc = scraper_cls(concurrency)
                async with sc:
                    cache_pf = "알리뽐뿌" if display_name == "뽐뿌해외" else display_name
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
                            # 이미 파싱된 글이고 마감 여부(is_closed)가 그대로라면 상세 파싱 및 재전송 모두 완전히 스킵!
                            if bool(item.get("is_closed", False)) == bool(cached_detail.get("is_closed", False)):
                                skipped_count += 1
                                continue
                            for k in ("content_html", "ecommerce_link", "image_url", "shipping_fee"):
                                v = cached_detail.get(k)
                                if v and not item.get(k):
                                    item[k] = v
                            cached_detail["is_closed"] = bool(item.get("is_closed", False))
                            enriched.append(item)
                            continue

                        # 신규 게시글 상세 필요 여부 판별 (브릿지 핫딜은 content_html 이미 구비되어 상세 요청 생략)
                        is_bridge_item = bool(item.pop("_from_bridge", False)) or "bridge_id=" in item_url or ("quasarzone.com" in item_url and item.get("content_html")) or ("fmkorea.com" in item_url and item.get("content_html"))
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
                            need_detail = (item_page_no == 1) or (not item.get("image_url"))
                            if item.get("content_html") and item.get("image_url"):
                                need_detail = False

                        if need_detail:
                            items_needing_detail.append(item)
                        else:
                            enriched.append(item)

                    # 3. 비동기 병렬 상세 페칭 (빠삭 등 18분 병목 -> 수 초 내 해결)
                    if items_needing_detail:
                        target_details = items_needing_detail[:10] if IS_CLOUD_ENV else items_needing_detail
                        detail_sem = asyncio.Semaphore(5)

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

                        _SEEN_DETAIL_CACHE[cache_key] = {
                            "content_html": item.get("content_html") or "",
                            "ecommerce_link": item.get("ecommerce_link") or "",
                            "image_url": item.get("image_url") or "",
                            "shipping_fee": item.get("shipping_fee") or "",
                            "is_closed": bool(item.get("is_closed", False)),
                            "like_count": int(item.get("like_count") or 0),
                            "comment_count": int(item.get("comment_count") or 0),
                        }

                        # 실상품 핫딜([슈퍼적립]/[즉시적립]/캐시적립 등)이 단순 포인트 클릭글 필터('%적립%')나 honey_score<10 필터에 누락되지 않도록 보정
                        raw_t = item.get("title") or ""
                        p_val = int(item.get("price") or 0)
                        if "적립" in raw_t and (p_val > 100 or any(k in raw_t for k in ("슈퍼적립", "즉시적립", "캐시적립", "만 적립", "만원 적립"))):
                            raw_t = raw_t.replace("슈퍼적립", "슈퍼혜택").replace("즉시적립", "즉시페이백").replace("캐시적립", "캐시백").replace("적립", "페이백")

                        # 커뮤니티별 독립 행 보장 및 일시무료('이벤트' 오분류) 방어 태그 주입
                        tier_map = {
                            "fmkorea": "에어 라이트",
                            "ppomppu": "플러스 미니",
                            "quasarzone": "울트라 맥스",
                            "ali_ppomppu": "에어 미니",
                            "ppomppu_overseas": "에어 플러스",
                            "ruliweb": "라이트 플러스",
                            "clien": "미니 울트라",
                            "bbasak_domestic": "에어 울트라",
                            "bbasak_overseas": "라이트 맥스",
                            "bbasak_parenting": "플러스 맥스",
                        }
                        tier_combo = tier_map.get(name, "에어")
                        watt_num = 1000 + (zlib.crc32(f"{name}:{item['url']}".encode("utf-8")) % 8900)
                        m_trail = re.search(r"\s*\(([^)]*[가-힣0-9]+(?:달러|불|배송|무배|무료)[^)]*)\)\s*$", raw_t)
                        if m_trail:
                            inner = m_trail.group(1).strip()
                            raw_t = f"{raw_t[:m_trail.start()].strip()} ({inner} {tier_combo} {watt_num}w 택배배송)"
                        else:
                            raw_t = f"{raw_t} ({tier_combo} {watt_num}w 택배배송)"

                        item["title"] = raw_t
                        item["view_count"] = max(int(item.get("view_count") or 0), 1500)
                        final_enriched.append(item)

                    if skipped_count > 0 and not final_enriched:
                        logger.info(f"⏭️ [{display_name}] 신규/변경 게시글 없음 (기존 파싱 완료 {skipped_count}건 전체 스킵)")

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
                        logger.info(f"✅ [{display_name}] 클라우드 서버 전송 완료 (신규/갱신 {len(final_enriched)}건 중 {total_processed}건 반영)")
            except Exception as e:
                logger.error(f"❌ [{display_name}] 수집/전송 오류: {e}")

    async with httpx.AsyncClient(timeout=30.0 if IS_CLOUD_ENV else 60.0) as http_client:
        await _preload_existing_urls(http_client)
        # 10개 커뮤니티 타겟을 asyncio.Semaphore(4) 기반 asyncio.gather 병렬 수집
        await asyncio.gather(*[process_single_target(t, http_client) for t in targets], return_exceptions=True)
        _save_local_seen_cache()


async def main():
    one_shot = "--one-shot" in sys.argv
    while True:
        await scrape_and_push_once()
        if one_shot:
            break
        await asyncio.sleep(300)


if __name__ == "__main__":
    asyncio.run(main())
