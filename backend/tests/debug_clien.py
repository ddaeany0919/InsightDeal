
import time
from scrapers.clien_scraper import ClienScraper
from database.session import SessionLocal

def main():
    db = SessionLocal()
    try:
        s = ClienScraper(db)
        s._create_selenium_driver()
        s.driver.get('https://www.clien.net/service/board/jirum')
        time.sleep(5)
        content = s.driver.page_source
        with open('clien_source.html', 'w', encoding='utf-8') as f:
            f.write(content)
        print("Successfully saved clien_source.html")
    finally:
        if 's' in locals():
            s.driver.quit()
        db.close()

if __name__ == "__main__":
    main()
