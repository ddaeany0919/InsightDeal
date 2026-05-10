import asyncio
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def test():
    async with FmkoreaScraper(community_id=6) as scraper:
        html = await scraper.fetch_html(scraper.list_url)
        res = await scraper.parse_list(html)
        for r in res[:5]:
            print(f"[{r.category}] {r.title}")
            print(f"  Posted At (List): {r.posted_at}")
            print(f"  Deal Type: {r.deal_type}")
            detail_html = await scraper.fetch_html(r.post_link)
            detail = await scraper.get_detail(r, detail_html)
            print(f"  -> Detail posted_at: {detail.posted_at}")

asyncio.run(test())
