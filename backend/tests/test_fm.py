import asyncio
import sys
sys.stdout.reconfigure(encoding='utf-8')

from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def run():
    async with FmkoreaScraper() as scraper:
        deals = await scraper.scrape_recent_deals()
        print(f"Scraped {len(deals)} deals.")
        for d in deals[:5]:
            print(d.title, d.price)

asyncio.run(run())
