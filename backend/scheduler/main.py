import asyncio
import logging
import os
import sys
from datetime import datetime
from dotenv import load_dotenv

# 모듈 경로 및 환경 변수 로드 (API 서버와 동일한 DB를 바라보게 설정)
root_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(root_dir)
load_dotenv(os.path.join(root_dir, '.env'))

from apscheduler.schedulers.asyncio import AsyncIOScheduler

from backend.database.models import Base, Community
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.scrapers.fmkorea_scraper import FmkoreaScraper
from backend.scrapers.ruliweb_scraper import RuliwebScraper
from backend.scrapers.clien_scraper import ClienScraper
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from backend.scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from backend.services.aggregator_service import AggregatorService

logger = logging.getLogger(__name__)

# 스케줄러 전역 상태 (API 헬스체크 및 대시보드 조회용)
SCHEDULER_STATE = {
    "last_run_time": None,
    "last_status": "Idle",
    "total_run_count": 0
}

# 🚀 메인 API 웹서버와 완전히 동일한 데이터베이스 파이프라인(session.py) 공유
from backend.database.session import SessionLocal, db_manager

# 메인 DB 연결 및 테이블 강제 동기화 (최초 1회)
db_manager.init_database()

async def scrape_community(community_name: str, ScraperClass, aggregator: AggregatorService, db):
    """지정된 커뮤니티의 비동기 수집 파이프라인 태스크"""
    community = db.query(Community).filter(Community.name == community_name).first()
    # 데모 편의용: DB에 해당 커뮤니티 데이터가 없으면 자동 생성
    if not community:
        name_map = {
            "ppomppu": "뽐뿌", "quasarzone": "퀘이사존", "fmkorea": "펨코",
            "ruliweb": "루리웹", "clien": "클리앙", "ali_ppomppu": "알리뽐뿌",
            "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외"
        }
        display_name = name_map.get(community_name, community_name)
        base_url = f"https://{community_name}.co.kr" if "ppomppu" in community_name else "https://알수없음"
        community = Community(name=community_name, display_name=display_name, base_url=base_url)
        db.add(community)
        db.commit()
        db.refresh(community)

    success_count = 0
    try:
        async with ScraperClass(community.id) as scraper:
            logger.info(f"🔄 [{community.display_name}] 스크래핑 파이프라인 가동...")
            html = await scraper.fetch_html(scraper.list_url)
            if html:
                deals = await scraper.parse_list(html)
                for deal_data in deals:
                    # 🚀 즉시 정규화 및 데이터 지능형 업서트(Upsert) 실행
                    await aggregator.process_scraped_deal(community.id, deal_data)
                    success_count += 1
                logger.info(f"✅ [{community.display_name}] 스크래핑 성공 (단일 파이프라인 수집건수: {success_count}건)")
    except Exception as e:
        logger.error(f"❌ [{community.name}] 파이프라인 크롤링 에러: {e}")
    
    # [에픽] UI 대시보드 상태 갱신을 위한 JSON 쓰기
    import json
    stats_file = os.path.join(root_dir, "backend", "scraper_stats.json")
    try:
        if os.path.exists(stats_file):
            with open(stats_file, 'r', encoding='utf-8') as f:
                stats = json.load(f)
        else:
            stats = {}
            
        stats[community.display_name] = {
            "last_count": success_count,
            "last_run": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "status": "Success" if success_count > 0 else "Blocked/Failed"
        }
        with open(stats_file, 'w', encoding='utf-8') as f:
            json.dump(stats, f, ensure_ascii=False, indent=2)
    except Exception as e:
        pass
        
    return success_count

async def run_pipeline_job():
    """뽐뿌와 퀘이사존을 병렬(gather)로 동시에 긁어오는 통합 엔진의 심장"""
    logger.info("====================================")
    logger.info("🚀 [Background] 정기 핫딜 동시 수집 파이프라인 가동")
    logger.info("====================================")
    
    SCHEDULER_STATE["last_status"] = "Running"
    db = SessionLocal()
    try:
        aggregator = AggregatorService(db)
        
        # ⚡ 모든 스크래핑 크롤러 동시 접속 (asyncio.gather를 통한 속도 극대화)
        tasks = [
            scrape_community("ppomppu", PpomppuScraper, aggregator, db),
            scrape_community("quasarzone", QuasarzoneScraper, aggregator, db),
            scrape_community("fmkorea", FmkoreaScraper, aggregator, db),
            scrape_community("ruliweb", RuliwebScraper, aggregator, db),
            scrape_community("clien", ClienScraper, aggregator, db),
            scrape_community("ali_ppomppu", AlippomppuScraper, aggregator, db),
            scrape_community("bbasak_domestic", BbasakDomesticScraper, aggregator, db),
            scrape_community("bbasak_overseas", BbasakOverseasScraper, aggregator, db)
        ]
        
        results = await asyncio.gather(*tasks)
        total_collected = sum(results)
        
        # 성공 후 헬스체크 상태 갱신
        SCHEDULER_STATE["last_run_time"] = datetime.now()
        SCHEDULER_STATE["last_status"] = f"Success ({total_collected} deals process)"
        SCHEDULER_STATE["total_run_count"] += 1
        
        logger.info(f"🎉 파이프라인 1회 사이클 완료! 총 {total_collected}건의 Deal 데이터가 갱신되었습니다.")
        logger.info("====================================\n")
        
    except Exception as e:
        SCHEDULER_STATE["last_status"] = f"Error: {e}"
        logger.error(f"❌ 전체 파이프라인 사이클 붕괴 에러: {e}")
    finally:
        db.close()

def start_scheduler():
    scheduler = AsyncIOScheduler()
    # 30분 단위 스케줄 (일단 데모 편의를 위해 매 15분으로 등록해도 됨)
    scheduler.add_job(run_pipeline_job, 'interval', minutes=30, id='hotdeal_pipeline')
    scheduler.start()
    logger.info("⏰ [System] Async Scheduler 엔진에 무한 동력 시동이 걸렸습니다. (30분 주기)")
    return scheduler

if __name__ == "__main__":
    # 이 파일을 직접 실행 시 엔진 즉시 가동용입니다.
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    
    scheduler = start_scheduler()
    
    import asyncio
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    
    # 1. 켜자마자 바로 1회 돌려보기
    loop.run_until_complete(run_pipeline_job())
    
    if "--one-shot" in sys.argv:
        logger.info("👋 [One-Shot Mode] 1 사이클 수집 완료. 백그라운드 프로세스를 종료합니다.")
        sys.exit(0)
    
    try:
        loop.run_forever()
    except (KeyboardInterrupt, SystemExit):
        pass
