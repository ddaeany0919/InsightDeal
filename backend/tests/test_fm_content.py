import asyncio
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

import logging
logging.basicConfig(level=logging.DEBUG)

async def test():
    async with FmkoreaScraper(1) as s:
        html = await s.fetch_html(s.list_url)
        print("HTML length:", len(html) if html else "None")
        if html:
            print("Title element test:", "hotdeal" in html)
            with open("test_fm.html", "w", encoding="utf-8") as f:
                f.write(html)
        res = await s.run(s.list_url)
        for r in res:
            print("TITLE:", r.get("title"))
            print("CONTENT_HTML_LEN:", len(r.get("content_html", "")))

asyncio.run(test())
