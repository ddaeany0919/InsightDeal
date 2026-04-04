import logging
import sys
import os

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from database.session import SessionLocal
from database.models import Community
from scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from scrapers.bbasak_overseas_scraper import BbasakOverseasScraper

# Logging setup
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

def run_test():
    logger.info("🚀 Starting Bbasak Scraper Test...")
    
    db = SessionLocal()
    
    scrapers = [
        {"name": "빠삭국내", "class": BbasakDomesticScraper},
        {"name": "빠삭해외", "class": BbasakOverseasScraper}
    ]
    
    for item in scrapers:
        try:
            logger.info(f"\n--- Running Scraper: {item['name']} ---")
            scraper = item["class"](db)
            scraper.run(limit=5)
            logger.info(f"✅ {item['name']} Scraper Completed.")
        except Exception as e:
            logger.error(f"❌ {item['name']} Scraper Failed: {e}", exc_info=True)
            
    db.close()
    logger.info("\n🎉 Bbasak Scraper Test Finished.")

if __name__ == "__main__":
    run_test()
