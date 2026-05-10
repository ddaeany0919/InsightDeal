import asyncio
from ppomppu_scraper import PpomppuScraper

async def test():
    async with PpomppuScraper(community_id=1) as scraper:
        deals = await scraper.run("https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu")
        for i, deal in enumerate(deals):
            print(f"{i}. {deal.get('ecommerce_link')}")
            if not deal.get('ecommerce_link'):
                print(f"   => MISSING! {deal.get('url')}")

asyncio.run(test())
