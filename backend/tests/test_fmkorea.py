import asyncio
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def main():
    s = FmkoreaScraper(1)
    html = await s.fetch_html('https://www.fmkorea.com/hotdeal?listStyle=webzine')
    res = await s.parse_list(html)
    for r in res[:2]:
        print(r)

asyncio.run(main())
