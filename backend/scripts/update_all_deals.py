import asyncio
import os
import sys
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

# 환경 셋업
root_dir = os.path.dirname(os.path.abspath(__file__))  # backend/scripts
backend_dir = os.path.dirname(root_dir)                 # backend
project_root = os.path.dirname(backend_dir)             # InsightDeal
sys.path.insert(0, project_root)

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
    
    # 🔍 [Self-Healing] 수집 공백시간(Gap) 분석을 통한 동적 수집 깊이 계산 (수동 실행/배치 공유)
    db = SessionLocal()
    pages_to_scrape = 3
    is_backfill_mode = False
    gap_hours = 0.0
    
    try:
        from backend.database.models import Deal
        last_deal = db.query(Deal).order_by(Deal.indexed_at.desc()).first()
        if last_deal and last_deal.indexed_at:
            from datetime import datetime, timedelta
            now = datetime.utcnow()
            last_time = last_deal.indexed_at
            if last_time.tzinfo is not None:
                now = datetime.now(last_time.tzinfo)
            
            gap_seconds = (now - last_time).total_seconds()
            gap_hours = gap_seconds / 3600.0
            
            # 수집 공백이 2시간을 초과한 경우 ➔ 자가 치유 복구 백필 모드 가동!
            if gap_hours > 2.0:
                is_backfill_mode = True
                # 누락 시간에 비례하여 탐색할 과거 페이지 깊이를 동적 결정 (최대 25페이지, 약 5일 치 분량 복구)
                pages_to_scrape = min(25, max(3, int(gap_hours * 1.5)))
    except Exception as e:
        print(f"[Self-Healing] 수집 공백 분석 실패: {e}")
    finally:
        db.close()
        
    if not is_backfill_mode:
        print(f'📢 평시 모드로 {pages_to_scrape}페이지 스크래핑을 실행합니다.')
    else:
        print(f'🔥 자가치유 백필 복구 모드 가동! (공백 {gap_hours:.1f}시간 감지 ➔ {pages_to_scrape}페이지 자동 추적 및 복구)')

    # 3페이지씩 긁어서 신규 추가 및 기존 데이터 업데이트(가격/상태 갱신)
    tasks = [
        scrape_community('ali_ppomppu', AlippomppuScraper, pages_to_scrape),
        scrape_community('fmkorea', FmkoreaScraper, pages_to_scrape),
        scrape_community('quasarzone', QuasarzoneScraper, pages_to_scrape),
        scrape_community('ruliweb', RuliwebScraper, pages_to_scrape),
        scrape_community('clien', ClienScraper, pages_to_scrape),
        scrape_community('ppomppu', PpomppuScraper, pages_to_scrape),
        scrape_community('bbasak_domestic', BbasakDomesticScraper, pages_to_scrape),
        scrape_community('bbasak_overseas', BbasakOverseasScraper, pages_to_scrape),
        scrape_community('bbasak_parenting', BbasakParentingScraper, pages_to_scrape),
    ]
    # 병렬 처리를 위한 세마포어 (SQLite DB의 Database is locked를 완벽 방지하면서 성능을 3배 높이기 위해 3개로 튜닝)
    sem = asyncio.Semaphore(3)

    async def run_with_semaphore(task_coro):
        async with sem:
            await task_coro

    print(f'🚀 {len(tasks)}개의 커뮤니티 스크래핑을 병렬로 시작합니다... (동시 실행 제한: 3)')
    
    # asyncio.gather를 사용하여 모든 스크래퍼 병렬 실행
    await asyncio.gather(*(run_with_semaphore(task) for task in tasks))
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
    
    # 작업 완료 시간 기록 (Admin UI 용)
    import json
    from datetime import datetime
    try:
        log_dir = os.path.join(project_root, 'backend', 'logs')
        os.makedirs(log_dir, exist_ok=True)
        log_file = os.path.join(log_dir, 'last_update.json')
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with open(log_file, 'w', encoding='utf-8') as f:
            json.dump({"last_updated": now_str, "status": "success"}, f)
    except Exception as e:
        print(f"로그 저장 실패: {e}")
