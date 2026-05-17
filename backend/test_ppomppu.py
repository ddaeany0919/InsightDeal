import asyncio
from backend.scheduler.main import scrape_community
from backend.scrapers.ppomppu_scraper import PpomppuScraper
import logging

logging.basicConfig(level=logging.INFO)

async def main():
    await scrape_community('ppomppu', PpomppuScraper, 3)

if __name__ == '__main__':
    import sys
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(main())
