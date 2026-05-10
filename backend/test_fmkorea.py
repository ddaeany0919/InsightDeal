import asyncio
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def test():
    scraper = FmkoreaScraper(community_id=2)
    async with scraper:
        html = await scraper.fetch_html("https://www.fmkorea.com/index.php?mid=hotdeal&listStyle=webzine")
        items = await scraper.parse_list(html)
        for item in items:
            if "삼양라면" in item['title']:
                print(item['title'])
                print(item['url'])
                print("is_super_hotdeal:", item.get('is_super_hotdeal'))

asyncio.run(test())
