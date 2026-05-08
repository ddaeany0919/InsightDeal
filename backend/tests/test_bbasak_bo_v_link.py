import asyncio
import os
import sys

from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper

async def test():
    scraper = BbasakDomesticScraper(community_id=1)
    async with scraper:
        html = await scraper.fetch_html("https://bbasak.com/bbs/board.php?bo_table=bbasak1&wr_id=113822&device=pc")
        from bs4 import BeautifulSoup
        soup = BeautifulSoup(html, 'html.parser')
        
        # Print all links in bo_v_link
        for a in soup.select('.bo_v_link a'):
            print(f"bo_v_link: {a.get('href')}")
            
        print("Done")

if __name__ == "__main__":
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(test())
