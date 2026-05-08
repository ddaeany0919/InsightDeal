import asyncio
import logging
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper

logging.basicConfig(level=logging.DEBUG)

async def test_qz_detail():
    async with QuasarzoneScraper(2) as s:
        # fetch list to get one url
        res = await s.fetch_html(s.list_url)
        from bs4 import BeautifulSoup
        soup = BeautifulSoup(res, 'html.parser')
        first_a = soup.select_one('a.subject-link')
            
        if first_a:
            href = first_a.get('href', '')
            if href:
                url = "https://quasarzone.com" + href
                print("URL:", url)
                html = await s.fetch_html(url)
                if html:
                    s2 = BeautifulSoup(html, 'html.parser')
                    # Find body content
                    content = s2.select_one('.view-content')
                    print("Found .view-content:", bool(content))
                    if content:
                         print(content.get_text()[:200])
                    else:
                         # maybe .market-info-view-cont or something
                         print("Divs:", [d.get('class') for d in s2.select('div[class*=content]')][:10])
                         print("Views:", [d.get('class') for d in s2.select('div[class*=view]')][:10])

asyncio.run(test_qz_detail())
