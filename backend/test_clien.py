import sys
sys.stdout.reconfigure(encoding='utf-8')
import asyncio
from backend.scrapers.clien_scraper import ClienScraper

async def run():
    scraper = ClienScraper(community_id=11)
    async with scraper:
        url = "https://www.clien.net/service/board/jirum?&po=1"
        html = await scraper.fetch_html(url)
        deals = await scraper.parse_list(html)
        for d in deals:
            if 'PDF' in d['title']:
                print(f"Title: {d['title']}")
                print(f"  Closed: {d['is_closed']}, HotDeal: {d['is_super_hotdeal']}")

if __name__ == "__main__":
    asyncio.run(run())
