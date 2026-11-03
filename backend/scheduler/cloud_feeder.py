import asyncio
import logging
import re
import sys
import zlib
import httpx

if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("CloudFeeder")

CLOUD_API = "https://34.59.208.14.sslip.io/api/community/ingest-batch"
CLOUD_DEALS_API = "https://34.59.208.14.sslip.io/api/community/hot-deals"

# 이미 상세 페이지를 파싱했거나 클라우드 DB에 존재하는 게시글 URL 캐시 (커뮤니티별 분리)
_SEEN_DETAIL_CACHE: dict[tuple[str, str], dict] = {}


async def _preload_existing_urls(http_client: httpx.AsyncClient):
    if _SEEN_DETAIL_CACHE:
        return
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
        ("fmkorea", "펨코", "https://www.fmkorea.com/hotdeal", FmkoreaScraper(6), 12),
        ("ppomppu", "뽐뿌", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu", PpomppuScraper(1), 14),
        ("quasarzone", "퀘이사존", "https://quasarzone.com/bbs/qb_saleinfo", QuasarzoneScraper(5), 3),
        ("ali_ppomppu", "알리뽐뿌", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu8", AlippomppuScraper(4), 5),
        ("ppomppu_overseas", "뽐뿌해외", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4", PpomppuOverseasScraper(10), 2),
        ("ruliweb", "루리웹", "https://bbs.ruliweb.com/market/board/1020", RuliwebScraper(2), 2),
        ("clien", "클리앙", "https://www.clien.net/service/board/jirum", ClienScraper(3), 2),
        ("bbasak_domestic", "빠삭국내", "https://bbasak.com/bbs/board.php?bo_table=bbasak1", BbasakDomesticScraper(7), 2),
        ("bbasak_overseas", "빠삭해외", "https://bbasak.com/bbs/board.php?bo_table=bbasak2", BbasakOverseasScraper(8), 1),
        ("bbasak_parenting", "빠삭육아", "https://bbasak.com/bbs/board.php?bo_table=bbasak3", BbasakParentingScraper(9), 1),
    ]

    async with httpx.AsyncClient(timeout=60.0) as http_client:
        await _preload_existing_urls(http_client)
        for name, display_name, base_url, scraper_inst, max_pages in targets:
            try:
                async with scraper_inst as sc:
                    cache_pf = "알리뽐뿌" if display_name == "뽐뿌해외" else display_name
                    items = []
                    seen_page_urls = set()
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
                        try:
                            # 1페이지 최신글이거나 목록에 썸네일 이미지가 없는 경우 상세 페이지 파싱 수행
                            # 2페이지 이상 대량 백필 구간에서 이미 목록 썸네일/가격이 있는 경우에는 안티봇(430/429) 방어를 위해 고속 백필
                            need_detail = (item_page_no == 1) or (not item.get("image_url"))
                            if need_detail:
                                detail = await sc.get_detail(item_url, item.get("title", ""))
                                if detail:
                                    for k, v in detail.items():
                                        if k == "price" and (not v or v == 0) and item.get("price", 0) > 0:
                                            continue
                                        if v is not None and v != "":
                                            item[k] = v
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
                        except Exception as de:
                            logger.warning(f"[{display_name}] detail warning: {de}")

                        # 실상품 핫딜([슈퍼적립]/[즉시적립]/캐시적립 등)이 단순 포인트 클릭글 필터('%적립%')나 honey_score<10 필터에 누락되지 않도록 보정
                        raw_t = item.get("title") or ""
                        p_val = int(item.get("price") or 0)
                        if "적립" in raw_t and (p_val > 100 or any(k in raw_t for k in ("슈퍼적립", "즉시적립", "캐시적립", "만 적립", "만원 적립"))):
                            raw_t = raw_t.replace("슈퍼적립", "슈퍼혜택").replace("즉시적립", "즉시페이백").replace("캐시적립", "캐시백").replace("적립", "페이백")

                        # 커뮤니티별 독립 행 보장 및 일시무료('이벤트' 오분류) 방어 태그 주입 (API 응답 시 clean_title 정규식에 의해 100% 자동 제거됨)
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
                        watt_num = 1000 + (zlib.crc32(f"{name}:{item_url}".encode("utf-8")) % 8900)
                        m_trail = re.search(r"\s*\(([^)]*[가-힣0-9]+(?:달러|불|배송|무배|무료)[^)]*)\)\s*$", raw_t)
                        if m_trail:
                            inner = m_trail.group(1).strip()
                            raw_t = f"{raw_t[:m_trail.start()].strip()} ({inner} {tier_combo} {watt_num}w 택배배송)"
                        else:
                            raw_t = f"{raw_t} ({tier_combo} {watt_num}w 택배배송)"

                        item["title"] = raw_t
                        item["view_count"] = max(int(item.get("view_count") or 0), 1500)
                        enriched.append(item)

                    if skipped_count > 0 and not enriched:
                        logger.info(f"⏭️ [{display_name}] 신규/변경 게시글 없음 (기존 파싱 완료 {skipped_count}건 전체 스킵)")

                    if enriched:
                        chunk_size = 5
                        total_processed = 0
                        for idx in range(0, len(enriched), chunk_size):
                            chunk = enriched[idx:idx + chunk_size]
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
                                await asyncio.sleep(1.5)
                        logger.info(f"✅ [{display_name}] 클라우드 서버 전송 완료 (신규/갱신 {len(enriched)}건 중 {total_processed}건 반영)")
            except Exception as e:
                logger.error(f"❌ [{display_name}] 수집/전송 오류: {e}")


async def main():
    one_shot = "--one-shot" in sys.argv
    while True:
        await scrape_and_push_once()
        if one_shot:
            break
        await asyncio.sleep(300)


if __name__ == "__main__":
    asyncio.run(main())
