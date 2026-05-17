import sys
import asyncio
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from backend.scheduler.main import scrape_community
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.scrapers.fmkorea_scraper import FmkoreaScraper
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.database.session import SessionLocal

async def update():
    print("Starting scraping process for Pages 1~3...")
    db = SessionLocal()
    
    scrapers = [
        ("ppomppu", PpomppuScraper),
        ("quasarzone", QuasarzoneScraper),
        ("fmkorea", FmkoreaScraper)
    ]
    
    for name, scraper in scrapers:
        print(f"Scraping {name}...")
        await scrape_community(name, scraper, pages=3)
        
    db.close()
    print("Scraping completed.")

if __name__ == "__main__":
    asyncio.run(update())
