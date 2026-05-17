import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
import asyncio
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def test_fm():
    async with FmkoreaScraper(6) as scraper:
        results = await scraper.run('https://www.fmkorea.com/index.php?mid=hotdeal&sort_index=pop&order_type=desc')
        print(f'Scraped {len(results)} items from pop')
        for res in results[:15]:
            print(f"[{res.get('is_super_hotdeal')}] {res.get('title')} / likes: {res.get('like_count')} / view: {res.get('view_count')}")

asyncio.run(test_fm())
