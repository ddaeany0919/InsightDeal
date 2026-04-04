
import os
import sys
import logging
from database.session import SessionLocal
from scrapers.fmkorea_scraper import FmkoreaScraper

# 로그 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def test_fmkorea_scraper():
    logger.info("🚀 Running FMKorea Scraper Test...")
    db = SessionLocal()
    try:
        scraper = FmkoreaScraper(db)
        # 테스트를 위해 3개만 수집 시도
        success = scraper.run(limit=3)
        if success:
            logger.info("✅ FMKorea Scraper test PASSED (Deals saved).")
        else:
            logger.info("❌ FMKorea Scraper test FAILED (No deals saved).")
    except Exception as e:
        logger.error(f"💥 FMKorea Scraper test CRASHED: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    test_fmkorea_scraper()
