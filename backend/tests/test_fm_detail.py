import asyncio
import logging
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

logging.basicConfig(level=logging.DEBUG)

async def test_fm_detail():
    async with FmkoreaScraper(1) as s:
        # fetch list to get one url
        res = await s.fetch_html(s.list_url)
        from bs4 import BeautifulSoup
        soup = BeautifulSoup(res, 'html.parser')
        first_a = soup.select_one('li.li:not(.notice) td.title a, li.li:not(.notice) h3.title a, li.li:not(.notice) a.title')
        if not first_a:
            first_a = soup.select_one('table.bd_lst tbody tr td.title a')
            
        if first_a:
            url = "https://www.fmkorea.com" + first_a.get('href')
            print("URL:", url)
            
            detail = await s.get_detail(url)
            print("Detail:", detail)

asyncio.run(test_fm_detail())
