
import asyncio
import sys
import httpx
sys.path.insert(0, 'backend')
from scrapers.quasarzone_scraper import QuasarzoneScraper

async def main():
    s = QuasarzoneScraper(1)
    headers = {'User-Agent': 'Mozilla/5.0'}
    html = httpx.get('https://quasarzone.com/bbs/qb_saleinfo', headers=headers).text
    res = await s.parse_list(html)
    for r in res[:5]:
        print(r['title'], r['price'], r['currency'])

asyncio.run(main())

