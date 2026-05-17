import asyncio
import sys
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def main():
    sys.stdout.reconfigure(encoding='utf-8')
    scraper = FmkoreaScraper(community_id=1)
    html = await scraper.fetch_html("https://www.fmkorea.com/hotdeal")
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(html, 'html.parser')
    for a in soup.select('a'):
        if '9832607753' in a.get('href', ''):
            print(a.get('class'))

if __name__ == "__main__":
    asyncio.run(main())
