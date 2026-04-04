
import logging
import sys
import os

# 현재 디렉토리를 path에 추가하여 모듈 임포트 가능하게 함
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from database.session import SessionLocal
from database.models import Community
from scrapers.ruliweb_scraper import RuliwebScraper

# 로깅 설정 (콘솔 출력)
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

def run_test():
    db = SessionLocal()
    try:
        community = db.query(Community).filter(Community.name == "루리웹").first()
        if not community:
            community = Community(name="루리웹", base_url="https://bbs.ruliweb.com/market/board/1020")
            db.add(community)
            db.commit()

        logger.info("🚀 Running Ruliweb Scraper Test...")
        scraper = RuliwebScraper(db)
        scraper.run(limit=5)
    except Exception as e:
        logger.error(f"❌ Failed: {e}", exc_info=True)
    finally:
        db.close()

if __name__ == "__main__":
    run_test()
