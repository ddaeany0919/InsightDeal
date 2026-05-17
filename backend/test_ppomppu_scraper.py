import asyncio
import os
import sys

# Add backend directory to path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from scrapers.ppomppu_scraper import PpomppuScraper

async def test():
    scraper = PpomppuScraper(community_id=1)
    async with scraper:
        print("Testing Ppomppu Scraper...")
        deals = await scraper.scrape_list_page(1)
        print(f"Total deals found: {len(deals)}")
        for i, deal in enumerate(deals):
            print(f"[{i+1}] {deal.title}")
            print(f"  Price: {deal.price}")
            print(f"  URL: {deal.url}")

if __name__ == "__main__":
    asyncio.run(test())
