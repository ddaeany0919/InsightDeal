import asyncio
import os
import sys

# 환경 셋업
root_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.dirname(root_dir))

from backend.database.session import SessionLocal
from backend.scheduler.main import scrape_community
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.fmkorea_scraper import FmkoreaScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.scrapers.ruliweb_scraper import RuliwebScraper
from backend.scrapers.clien_scraper import ClienScraper
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from backend.scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from backend.scrapers.bbasak_parenting_scraper import BbasakParentingScraper

async def run_update():
    print('🔄 데이터 파싱 시작 (기존 DB 유지, Upsert 실행)...')
    # 3페이지씩 긁어서 신규 추가 및 기존 데이터 업데이트(가격/상태 갱신)
    tasks = [
        scrape_community('ali_ppomppu', AlippomppuScraper, 3),
        scrape_community('fmkorea', FmkoreaScraper, 3),
        scrape_community('quasarzone', QuasarzoneScraper, 3),
        scrape_community('ruliweb', RuliwebScraper, 3),
        scrape_community('clien', ClienScraper, 3),
        scrape_community('ppomppu', PpomppuScraper, 3),
        scrape_community('bbasak_domestic', BbasakDomesticScraper, 3),
        scrape_community('bbasak_overseas', BbasakOverseasScraper, 3),
        scrape_community('bbasak_parenting', BbasakParentingScraper, 3),
    ]
    for task in tasks:
        await task
        # 구글 Gemini 무료 티어 API Rate Limit(1분 15회) 방지를 위한 대기 시간 추가
        await asyncio.sleep(2)
        
    print('✅ 모든 커뮤니티 데이터 파싱 및 업데이트 완료!')

    print('🤖 AI Batch Worker 실행 중 (요약 생성)...')
    try:
        from backend.scheduler.batch_ai_worker import run_all_batches
        loop = asyncio.get_running_loop()
        processed = await loop.run_in_executor(None, run_all_batches)
        print(f'✅ AI Batch 요약 처리 완료! (총 {processed}개 항목)')
    except Exception as e:
        print(f'❌ AI Batch 처리 중 오류 발생: {e}')

if __name__ == '__main__':
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(run_update())
