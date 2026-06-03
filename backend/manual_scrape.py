import sys
import asyncio
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from backend.services.aggregator_service import AggregatorService
from backend.database.session import SessionLocal

async def update():
    print("Starting scraping process...")
    with SessionLocal() as db:
        agg = AggregatorService(db)
        await agg.scrape_and_aggregate()
    print("Scraping completed.")

if __name__ == "__main__":
    asyncio.run(update())
