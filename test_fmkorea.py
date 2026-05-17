import asyncio
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def main():
    scraper = FmkoreaScraper(community_id=1)
    detail = await scraper.get_detail("https://m.fmkorea.com/9832607753")
    print("Detail is_closed:", detail.get('is_closed'))
    print("Detail HTML:", detail.get('content_html')[:500])

if __name__ == "__main__":
    asyncio.run(main())
