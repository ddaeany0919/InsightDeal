import sys, os, asyncio
sys.path.append(os.path.abspath('.'))

from backend.scheduler.main import scrape_community
from backend.scrapers.ppomppu_scraper import PpomppuScraper

async def run():
    print("Scraping Ppomppu page 1...")
    await scrape_community('ppomppu', PpomppuScraper, 1)
    print("Done")

if sys.platform == 'win32':
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
asyncio.run(run())
