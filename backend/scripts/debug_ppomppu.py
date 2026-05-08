import asyncio
import os
import sys
import traceback

sys.path.append(os.path.abspath(os.path.dirname(os.path.dirname(__file__))))

from backend.scrapers.ppomppu_scraper import PpomppuScraper
from bs4 import BeautifulSoup

async def main():
    try:
        async with PpomppuScraper(community_id=2) as scraper:
            html = await scraper.fetch_html('https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&page=1&divpage=23&no=87411')
            soup = BeautifulSoup(html, 'html.parser')
            content = soup.select_one('td.board-contents')
            print("Content HTML length:", len(html))
            if content:
                print("Extracted content text length:", len(content.get_text()))
            else:
                print("No td.board-contents found")
    except Exception as e:
        print("Error:")
        traceback.print_exc()

asyncio.run(main())
