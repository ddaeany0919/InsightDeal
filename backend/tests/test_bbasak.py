import asyncio
import os
import sys

from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper

async def test():
    scraper = BbasakDomesticScraper(community_id=1)
    async with scraper:
        print("Fetching html...")
        html = await scraper.fetch_html("https://bbasak.com/bbs/board.php?bo_table=bbasak1")
        print("Parsing...")
        deals = await scraper.parse_list(html)
        for d in deals[:3]:
            print(f"Title: {d['title']}")
            print(f"Post URL: {d['url']}")
            print(f"Ecom URL: {d['ecommerce_link']}")
            print("-" * 20)

if __name__ == "__main__":
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(test())
