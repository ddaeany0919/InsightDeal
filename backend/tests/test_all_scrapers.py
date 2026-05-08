import asyncio
import logging
from backend.scrapers.fmkorea_scraper import FmkoreaScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.scrapers.ruliweb_scraper import RuliwebScraper

logging.basicConfig(level=logging.WARNING)

async def test_scrapers():
    scrapers = [
        (FmkoreaScraper(1), "FMKorea"),
        (QuasarzoneScraper(2), "Quasarzone"),
        (RuliwebScraper(3), "Ruliweb")
    ]
    
    for scraper_obj, name in scrapers:
        print(f"\n{'='*50}\nTesting {name}\n{'='*50}")
        try:
            async with scraper_obj as s:
                # Quasarzone might use a different method or run()
                res = await s.run(s.list_url)
                
                if not res:
                    print("No results found.")
                    continue
                    
                for idx, r in enumerate(res[:3]):
                    title = r.get("title", "")
                    price = r.get("price", 0)
                    content_html = r.get("content_html") or r.get("content") or ""
                    
                    print(f"[{idx+1}] Title : {title}")
                    print(f"    Price : {price}")
                    print(f"    Content Length: {len(content_html)}")
                    print(f"    Preview: {content_html[:150].replace(chr(10), ' ')}")
                    print("-" * 30)
        except Exception as e:
            print(f"Error testing {name}: {e}")

asyncio.run(test_scrapers())
