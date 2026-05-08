import asyncio
import sys
sys.stdout.reconfigure(encoding='utf-8')

from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def run():
    scraper = FmkoreaScraper(community_id=1)
    async with scraper:
        html = await scraper.fetch_html(scraper.list_url)
        print("HTML Length:", len(html))
        
        deals = await scraper.parse_list(html)
        print(f"Scraped {len(deals)} deals.")
        for d in deals[:5]:
            print(d['title'], d['price'])

asyncio.run(run())
