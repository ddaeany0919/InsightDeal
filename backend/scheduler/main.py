import asyncio
import logging
import os
import sys
from datetime import datetime

# 모듈 경로를 위해 추가 (상대경로 임포트 호환)
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.models.models_v2 import Base, Community
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.services.aggregator_service import AggregatorService

logger = logging.getLogger(__name__)

# 스케줄러 전역 상태 (API 헬스체크 및 대시보드 조회용)
SCHEDULER_STATE = {
    "last_run_time": None,
    "last_status": "Idle",
    "total_run_count": 0
}

# DB 설정 (공통 DB 설정 경로가 없다면 폴더 최상단에 sqlite 로컬 연결 생성)
db_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "insight_deal.db")
engine = create_engine(f"sqlite:///{db_path}", connect_args={"check_same_thread": False})
Base.metadata.create_all(bind=engine)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

async def scrape_community(community_name: str, ScraperClass, aggregator: AggregatorService, db):
    """지정된 커뮤니티의 비동기 수집 파이프라인 태스크"""
    community = db.query(Community).filter(Community.name == community_name).first()
    # 데모 편의용: DB에 해당 커뮤니티 데이터가 없으면 자동 생성
    if not community:
        display_name = "뽐뿌" if community_name == "ppomppu" else "퀘이사존"
        base_url = f"https://{community_name}.co.kr" if community_name == "ppomppu" else "https://quasarzone.com"
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
        
        # ⚡ 뽐뿌와 퀘이사존 동시 접속 (asyncio.gather를 통한 속도 극대화)
        tasks = [
            scrape_community("ppomppu", PpomppuScraper, aggregator, db),
            scrape_community("quasarzone", QuasarzoneScraper, aggregator, db)
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
    
    # 1. 켜자마자 바로 1회 돌려보기
    asyncio.run(run_pipeline_job())
    
    try:
        # 무한 대기
        asyncio.get_event_loop().run_forever()
    except (KeyboardInterrupt, SystemExit):
        pass
