
import logging
import sys
import os

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from database.session import SessionLocal
from database.models import Community
from scrapers.clien_scraper import ClienScraper

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

def run_test():
    db = SessionLocal()
    try:
        community = db.query(Community).filter(Community.name == "클리앙").first()
        if not community:
            community = Community(name="클리앙", base_url="https://www.clien.net/service/board/jirum")
            db.add(community)
            db.commit()

        logger.info("🚀 Running Clien Scraper Test...")
        scraper = ClienScraper(db)
        scraper.run(limit=5)
    except Exception as e:
        logger.error(f"❌ Failed: {e}", exc_info=True)
    finally:
        db.close()

if __name__ == "__main__":
    run_test()
