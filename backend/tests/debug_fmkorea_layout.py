
import logging
import time
import random
from database.session import SessionLocal
from scrapers.fmkorea_scraper import FmkoreaScraper

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def main():
    db = SessionLocal()
    try:
        scraper = FmkoreaScraper(db)
        if not scraper.driver:
            scraper._create_selenium_driver()
            
        # 신뢰 구축 단계 거침
        scraper.driver.get("https://www.fmkorea.com/")
        time.sleep(random.uniform(2, 4))
        scraper.driver.get("https://www.fmkorea.com/free")
        time.sleep(random.uniform(2, 3))
        
        # 핫딜 페이지 접속 (listStyle=list)
        url = "https://www.fmkorea.com/index.php?mid=hotdeal&listStyle=list"
        scraper.driver.get(url)
        time.sleep(5)
        
        with open("fmkorea_list_layout.html", "w", encoding="utf-8") as f:
            f.write(scraper.driver.page_source)
        logger.info("Saved FMKorea list layout to fmkorea_list_layout.html")
        
    except Exception as e:
        logger.error(f"Error: {e}")
    finally:
        if scraper.driver:
            scraper.driver.quit()
        db.close()

if __name__ == "__main__":
    main()
