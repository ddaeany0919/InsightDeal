import asyncio
import sys
import codecs
sys.stdout = codecs.getwriter('utf-8')(sys.stdout.detach())
sys.path.append('c:\\Users\\kth00\\StudioProjects\\InsightDeal\\backend')
from scrapers.fmkorea_scraper import FmkoreaScraper
from scrapers.ppomppu_scraper import PpomppuScraper
from scrapers.quasarzone_scraper import QuasarzoneScraper

async def run():
    print("Testing Fmkorea...")
    async with FmkoreaScraper(community_id=5) as scraper:
        html = await scraper.fetch_html(scraper.list_url)
        deals = await scraper.parse_list(html)
        for d in deals[:5]:
            print(f"Title: {d['title'][:30]} - Super: {d.get('is_super_hotdeal')}")

    print("\nTesting Ppomppu...")
    async with PpomppuScraper(community_id=1) as scraper:
        html = await scraper.fetch_html(scraper.list_url)
        deals = await scraper.parse_list(html)
        for d in deals[:5]:
            print(f"Title: {d['title'][:30]} - Super: {d.get('is_super_hotdeal')}")

    print("\nTesting Quasarzone...")
    async with QuasarzoneScraper(community_id=4) as scraper:
        html = await scraper.fetch_html(scraper.list_url)
        deals = await scraper.parse_list(html)
        for d in deals[:5]:
            print(f"Title: {d['title'][:30]} - Super: {d.get('is_super_hotdeal')}")

asyncio.run(run())
