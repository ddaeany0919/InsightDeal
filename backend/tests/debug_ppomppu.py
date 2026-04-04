
import time
from scrapers.ppomppu_scraper import PpomppuScraper
from database.session import SessionLocal

def main():
    db = SessionLocal()
    try:
        s = PpomppuScraper(db)
        s._create_selenium_driver()
        s.driver.get('https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu')
        time.sleep(5)
        content = s.driver.page_source
        with open('ppomppu_source.html', 'w', encoding='utf-8') as f:
            f.write(content)
        print("Successfully saved ppomppu_source.html")
    finally:
        if 's' in locals():
            s.driver.quit()
        db.close()

if __name__ == "__main__":
    main()
