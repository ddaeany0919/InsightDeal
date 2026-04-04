
import time
from scrapers.ruliweb_scraper import RuliwebScraper
from database.session import SessionLocal

def main():
    db = SessionLocal()
    try:
        s = RuliwebScraper(db)
        s._create_selenium_driver()
        s.driver.get('https://bbs.ruliweb.com/market/board/1020')
        time.sleep(5)
        content = s.driver.page_source
        with open('ruliweb_source.html', 'w', encoding='utf-8') as f:
            f.write(content)
        print("Successfully saved ruliweb_source.html")
    finally:
        if 's' in locals():
            s.driver.quit()
        db.close()

if __name__ == "__main__":
    main()
