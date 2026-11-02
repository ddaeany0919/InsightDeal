import asyncio
import logging
import sys
import httpx

sys.stdout.reconfigure(encoding="utf-8")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("CloudFeeder")

CLOUD_API = "https://34.59.208.14.sslip.io/api/community/ingest-batch"


async def scrape_and_push_once():
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
        for name, display_name, base_url, scraper_inst, max_items in targets:
            try:
                async with scraper_inst as sc:
                    items = await sc.run(sc.list_url)
                    items = items[:max_items]
                    enriched = []
                    for item in items:
                        try:
                            detail = await sc.get_detail(item["url"], item.get("title", ""))
                            if detail:
                                for k, v in detail.items():
                                    if k == "price" and (not v or v == 0) and item.get("price", 0) > 0:
                                        continue
                                    if v is not None and v != "":
                                        item[k] = v
                        except Exception as de:
                            logger.warning(f"[{display_name}] detail warning: {de}")
                        enriched.append(item)

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
