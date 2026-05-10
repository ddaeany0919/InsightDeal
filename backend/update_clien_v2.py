import sys
sys.stdout.reconfigure(encoding='utf-8')
import asyncio
from backend.database.session import SessionLocal
from backend.scrapers.clien_scraper import ClienScraper
from backend.services.aggregator_service import AggregatorService
import logging

logging.basicConfig(level=logging.INFO)

async def run():
    db = SessionLocal()
    agg_service = AggregatorService(db)
    
    scraper = ClienScraper(community_id=11)
    async with scraper:
        for page in [0, 1, 2]:
            url = f"https://www.clien.net/service/board/jirum?&po={page}"
            print(f"\n=== Scraping Clien Page {page + 1} ({url}) ===")
            try:
                html = await scraper.fetch_html(url)
                if html:
                    deals = await scraper.parse_list(html)
                    for deal in deals:
                        deal["source_community_id"] = 11
                        try:
                            # print(f"Processing: {deal['title']}, Closed: {deal['is_closed']}, HotDeal: {deal['is_super_hotdeal']}")
                            await agg_service.process_scraped_deal(11, deal)
                        except Exception as inner_e:
                            print(f"Error processing {deal.get('title')}: {inner_e}")
            except Exception as e:
                print(f"Error on page {page}: {e}")
            
            await asyncio.sleep(2)
            
    db.close()
    
    # Wait a bit before closing the event loop to allow any lingering futures to finish
    await asyncio.sleep(2)

if __name__ == "__main__":
    asyncio.run(run())
