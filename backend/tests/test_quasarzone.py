import logging
import sys
import os
import asyncio

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from database.session import SessionLocal
from database.models import Community
from scrapers.quasarzone_scraper import QuasarzoneScraper

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

async def run_test_async():
    db = SessionLocal()
    try:
        community = db.query(Community).filter(Community.name == "퀘이사존").first()
        if not community:
            community = Community(name="퀘이사존", base_url="https://quasarzone.com/bbs/qb_saleinfo")
            db.add(community)
            db.commit()

        logger.info("🚀 Running Quasarzone Scraper Test...")
        scraper = QuasarzoneScraper(community_id=community.id)
        async with scraper:
            deals = await scraper.run(scraper.list_url)
            for deal in deals[:5]:
                detail = await scraper.get_detail(deal["url"])
                logger.info(f"Title: {deal['title']} | Price: {deal['price']} | Ecommerce Link: {detail.get('ecommerce_link')}")
    except Exception as e:
        logger.error(f"❌ Failed: {e}", exc_info=True)
    finally:
        db.close()

if __name__ == "__main__":
    asyncio.run(run_test_async())
