import asyncio
import sys
import os

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from scrapers.fmkorea_scraper import FmkoreaScraper
from services.aggregator_service import AggregatorService
from database.session import create_db_session

from database.models import Community

async def main():
    print("Starting FMKorea update...")
    
    with create_db_session() as db:
        community = db.query(Community).filter(Community.name == 'fmkorea').first()
        community_id = community.id if community else 1
        
    scraper = FmkoreaScraper(community_id=community_id)
    # 1. 일반 게시판 수집
    print("Scraping normal pages...")
    scraper.parsing_pop = False
    deals_normal = await scraper.run(scraper.list_url)
    
    # 2. 포텐(인기) 게시판 수집
    print("Scraping popular pages...")
    scraper.parsing_pop = True
    deals_pop = await scraper.run(scraper.pop_url)
    
    all_deals = deals_normal + deals_pop
    
    count = 0
    with create_db_session() as db:
        agg_service = AggregatorService(db)
        for deal in all_deals:
            await agg_service.process_scraped_deal(community_id=community_id, scraped_data=deal)
            count += 1
    print(f"Total {count} fmkorea deals processed and DB updated (closed status updated).")

if __name__ == "__main__":
    asyncio.run(main())
