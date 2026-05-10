import asyncio
import os
import sys
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

root_dir = os.path.dirname(os.path.abspath(__file__))
backend_dir = os.path.dirname(root_dir)
project_root = os.path.dirname(backend_dir)
sys.path.insert(0, project_root)

from backend.database.session import SessionLocal
from backend.scheduler.main import scrape_community
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def run_update():
    print('🔄 fmkorea 데이터 파싱 시작...')
    tasks = [
        scrape_community('fmkorea', FmkoreaScraper, 3),
    ]
    for task in tasks:
        await task
        await asyncio.sleep(2)
        
    print('✅ fmkorea 데이터 파싱 및 업데이트 완료!')

if __name__ == '__main__':
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(run_update())
