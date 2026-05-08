import asyncio
import os
import sys

sys.path.append(os.path.abspath(os.path.dirname(os.path.dirname(__file__))))

from backend.scrapers.ppomppu_scraper import PpomppuScraper
from bs4 import BeautifulSoup

async def main():
    async with PpomppuScraper(community_id=2) as scraper:
        html = await scraper.fetch_html('https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&page=1&divpage=23&no=87411')
        soup = BeautifulSoup(html, 'html.parser')
        content = soup.select_one('td.board-contents')
        if content:
            with open('ppomppu_content.txt', 'w', encoding='utf-8') as f:
                f.write(content.get_text(separator='\n', strip=True))

asyncio.run(main())
