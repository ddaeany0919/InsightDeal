import asyncio
import logging
import sys
import httpx

if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

sys.stdout.reconfigure(encoding="utf-8")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("CloudFeeder")

CLOUD_API = "https://34.59.208.14.sslip.io/api/community/ingest-batch"
CLOUD_DEALS_API = "https://34.59.208.14.sslip.io/api/community/hot-deals"

# 이미 상세 페이지를 파싱했거나 클라우드 DB에 존재하는 게시글 URL 캐시 (펨코 430 과호출 방지)
_SEEN_DETAIL_CACHE: dict[str, dict] = {}


async def _preload_existing_urls(http_client: httpx.AsyncClient):
    if _SEEN_DETAIL_CACHE:
        return
    try:
        from backend.core.url_utils import normalize_url
        for page in (1, 2, 3, 4):
            res = await http_client.get(f"{CLOUD_DEALS_API}?page={page}&limit=100")
            if res.status_code == 200:
                payload = res.json()
                deals = payload if isinstance(payload, list) else payload.get("items", payload.get("deals", []))
                for d in deals:
                    if not isinstance(d, dict):
                        continue
                    raw_u = d.get("post_link") or d.get("source_url") or d.get("url")
                    if raw_u:
                        u = normalize_url(raw_u)
                        _SEEN_DETAIL_CACHE[u] = {
                            "content_html": d.get("content_html") or "",
                            "ecommerce_link": d.get("ecommerce_link") or "",
                            "image_url": d.get("image_url") or "",
                            "shipping_fee": d.get("shipping_fee") or "",
                            "is_closed": bool(d.get("is_closed", False)),
                            "like_count": int(d.get("like_count") or 0),
                            "comment_count": int(d.get("comment_count") or 0),
                        }
        logger.info(f"📦 기존 DB 게시글 {len(_SEEN_DETAIL_CACHE)}건 상세 캐시 로드 완료 (이미 파싱된 게시글 스킵 활성화)")
    except Exception as e:
        logger.warning(f"기존 DB URL 프리로드 생략: {e}")


async def scrape_and_push_once():
    from backend.core.url_utils import normalize_url
    from backend.scrapers.ppomppu_scraper import PpomppuScraper
    from backend.scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
    from backend.scrapers.alippomppu_scraper import AlippomppuScraper
    from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
    from backend.scrapers.fmkorea_scraper import FmkoreaScraper

    targets = [
        ("fmkorea", "펨코", "https://www.fmkorea.com/hotdeal", FmkoreaScraper(6), 12),
        ("ppomppu", "뽐뿌", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu", PpomppuScraper(1), 15),
        ("quasarzone", "퀘이사존", "https://quasarzone.com/bbs/qb_saleinfo", QuasarzoneScraper(5), 15),
        ("ali_ppomppu", "알리뽐뿌", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4", AlippomppuScraper(4), 12),
        ("ppomppu_overseas", "뽐뿌해외", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4", PpomppuOverseasScraper(10), 12),
    ]

    async with httpx.AsyncClient(timeout=30.0) as http_client:
        await _preload_existing_urls(http_client)
        for name, display_name, base_url, scraper_inst, max_items in targets:
            try:
                async with scraper_inst as sc:
                    items = await sc.run(sc.list_url)
                    items = items[:max_items]
                    enriched = []
                    skipped_count = 0
                    for item in items:
                        item_url = normalize_url(item["url"])
                        item["url"] = item_url
                        if item_url in _SEEN_DETAIL_CACHE:
                            cached_detail = _SEEN_DETAIL_CACHE[item_url]
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
                            detail = await sc.get_detail(item_url, item.get("title", ""))
                            if detail:
                                for k, v in detail.items():
                                    if k == "price" and (not v or v == 0) and item.get("price", 0) > 0:
                                        continue
                                    if v is not None and v != "":
                                        item[k] = v
                            _SEEN_DETAIL_CACHE[item_url] = {
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
                        enriched.append(item)

                    if skipped_count > 0 and not enriched:
                        logger.info(f"⏭️ [{display_name}] 신규/변경 게시글 없음 (기존 파싱 완료 {skipped_count}건 전체 스킵)")

                    if enriched:
                        res = await http_client.post(
                            CLOUD_API,
                            json={
                                "community_name": name,
                                "display_name": display_name,
                                "base_url": base_url,
                                "deals": enriched,
                            },
                        )
                        logger.info(f"✅ [{display_name}] 클라우드 서버 전송 완료 ({len(enriched)}건): {res.status_code} {res.text}")
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
