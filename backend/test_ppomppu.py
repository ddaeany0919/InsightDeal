import asyncio
import sys
import os
import json

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
from backend.scrapers.ppomppu_scraper import PpomppuScraper

async def test():
    async with PpomppuScraper(2) as scraper:
        deals = await scraper.run("https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu&hotlist_flag=999")
        for deal in deals[:5]:
            print(json.dumps({
                "title": deal["title"],
                "posted_at": deal.get("posted_at")
            }, ensure_ascii=False, indent=2))

asyncio.run(test())
