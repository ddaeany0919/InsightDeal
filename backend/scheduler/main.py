import asyncio
import logging
import os
import sys
from datetime import datetime
from dotenv import load_dotenv

# 모듈 경로 및 환경 변수 로드 (API 서버와 동일한 DB를 바라보게 설정)
root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
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
from backend.scrapers.bbasak_parenting_scraper import BbasakParentingScraper
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

async def scrape_community(community_name: str, ScraperClass, pages: int = 1):
    """지정된 커뮤니티의 비동기 수집 파이프라인 태스크 (세션 독립 보장)"""
    db = SessionLocal()
    try:
        aggregator = AggregatorService(db)
        community = db.query(Community).filter(Community.name == community_name).first()
        if not community:
            name_map = {
                "ppomppu": "뽐뿌", "quasarzone": "퀘이사존", "fmkorea": "펨코",
                "ruliweb": "루리웹", "clien": "클리앙", "ali_ppomppu": "알리뽐뿌",
                "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외",
                "bbasak_parenting": "빠삭육아"
            }
            display_name = name_map.get(community_name, community_name)
            base_url = f"https://{community_name}.co.kr" if "ppomppu" in community_name else "https://알수없음"
            community = Community(name=community_name, display_name=display_name, base_url=base_url)
            db.add(community)
            db.commit()
            db.refresh(community)

        success_count = 0
        scraper = ScraperClass(community_id=community.id)
        async with scraper:
            logger.info(f"▶ [{community.display_name}] 스크래핑 시작 (pages={pages})")
            for page in range(1, pages + 1):
                if page > 1:
                    if "clien" in community_name:
                        sep = "&" if "?" in scraper.list_url else "?"
                        target_url = f"{scraper.list_url}{sep}po={page-1}"
                    else:
                        sep = "&" if "?" in scraper.list_url else "?"
                        target_url = f"{scraper.list_url}{sep}page={page}"
                else:
                    target_url = scraper.list_url
                    
                raw_html = await scraper.fetch_html(target_url)
                if not raw_html:
                    continue
                    
                items = await scraper.parse_list(raw_html)
                for item in items:
                    detail = await scraper.get_detail(item['url'])
                    if detail:
                        item.update(detail)
                    
                    try:
                        deal = await aggregator.process_scraped_deal(community.id, item)
                        if deal:
                            success_count += 1
                    except Exception as e:
                        db.rollback()
                        logger.error(f"[{community.display_name}] 데이터 처리 중 에러: {e}")
            
            logger.info(f"✅ [{community.display_name}] 스크래핑 성공 (총 수집건수: {success_count}건)")
            
        # 통계 JSON 쓰기 (동시성 보호를 위해 try/except 사용)
        import json
        stats_file = os.path.join(root_dir, "scraper_stats.json")
        try:
            stats = {}
            if os.path.exists(stats_file):
                with open(stats_file, 'r', encoding='utf-8') as f:
                    stats = json.load(f)
            stats[community.display_name] = {
                "last_count": success_count,
                "last_run": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                "status": "Success" if success_count > 0 else "Blocked/Failed"
            }
            with open(stats_file, 'w', encoding='utf-8') as f:
                json.dump(stats, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
            
        return success_count
    except Exception as e:
        logger.error(f"❌ [{community_name}] 파이프라인 크롤링 에러: {e}")
        return 0
    finally:
        db.close()

async def validate_closed_deals():
    """과거 핫딜(3일 이내) 품절 상태(Ping) 검증 데몬"""
    from datetime import datetime, timedelta
    from backend.database.models import Deal
    import httpx
    from bs4 import BeautifulSoup

    db = SessionLocal()
    try:
        logger.info("🔍 [Background] 과거 핫딜(3일 이내) 품절 상태 검증 데몬 가동")
        target_date = datetime.now() - timedelta(days=3)
        deals = db.query(Deal).filter(
            Deal.is_closed == False, 
            Deal.indexed_at >= target_date
        ).order_by(Deal.indexed_at.desc()).limit(150).all()
        
        closed_count = 0
        headers = {"User-Agent": "Mozilla/5.0"}
        
        async with httpx.AsyncClient(headers=headers, timeout=5.0) as client:
            for deal in deals:
                try:
                    req_headers = headers.copy()
                    if "ppomppu" in deal.post_link:
                        req_headers["Referer"] = "https://www.ppomppu.co.kr/"
                    elif "bbasak" in deal.post_link:
                        req_headers["Referer"] = "https://bbasak.com/"
                        
                    resp = await client.get(deal.post_link, headers=req_headers)
                    if resp.status_code == 404:
                        deal.is_closed = True
                        closed_count += 1
                        continue
                        
                    soup = BeautifulSoup(resp.content, 'html.parser')
                    title_tag = soup.title.string if soup.title else ""
                    
                    if any(kw in title_tag for kw in ["종료", "마감", "품절", "블라인드", "삭제"]):
                        deal.is_closed = True
                        closed_count += 1
                        continue
                        
                except Exception:
                    pass
                    
        if closed_count > 0:
            db.commit()
        logger.info(f"✅ 상태 검증 완료: 총 {len(deals)}개 핑(Ping) 테스트 수행 -> {closed_count}개 품절 처리")
    except Exception as e:
        logger.error(f"❌ 상태 검증 데몬 에러: {e}")
    finally:
        db.close()


async def run_pipeline_job():
    """
    투트랙(Two-Track) 오케스트레이션 엔진:
    - 매 사이클: 단기 수집 (1페이지)
    - 매 12번째 사이클 (약 1시간): 심층 수집 (1~3페이지)
    """
    logger.info("====================================")
    
    is_deep_scan = (SCHEDULER_STATE["total_run_count"] % 12 == 0)
    pages_to_scrape = 3 if is_deep_scan else 1
    
    mode_str = "심층 수사대(1~3페이지)" if is_deep_scan else "단기 감시조(1페이지)"
    logger.info(f"🚀 [Background] 정기 핫딜 동시 수집 파이프라인 가동 - 모드: {mode_str}")
    logger.info("====================================")
    
    SCHEDULER_STATE["last_status"] = f"Running ({mode_str})"
    
    try:
        # 각 스크래퍼가 독립적인 DB Session을 사용하므로, asyncio.gather 시 데드락 방지 완벽 보장
        tasks = [
            scrape_community("ppomppu", PpomppuScraper, pages_to_scrape),
            scrape_community("quasarzone", QuasarzoneScraper, pages_to_scrape),
            scrape_community("fmkorea", FmkoreaScraper, pages_to_scrape),
            scrape_community("ruliweb", RuliwebScraper, pages_to_scrape),
            scrape_community("clien", ClienScraper, pages_to_scrape),
            scrape_community("ali_ppomppu", AlippomppuScraper, pages_to_scrape),
            scrape_community("bbasak_domestic", BbasakDomesticScraper, pages_to_scrape),
            scrape_community("bbasak_overseas", BbasakOverseasScraper, pages_to_scrape),
            scrape_community("bbasak_parenting", BbasakParentingScraper, pages_to_scrape)
        ]
        
        results = await asyncio.gather(*tasks)
        total_collected = sum(results)
        
        SCHEDULER_STATE["last_run_time"] = datetime.now()
        SCHEDULER_STATE["last_status"] = f"Success ({total_collected} deals process)"
        SCHEDULER_STATE["total_run_count"] += 1
        
        logger.info(f"🎉 파이프라인 사이클 완료! 총 {total_collected}건의 Deal 데이터가 갱신되었습니다.")
        logger.info("====================================\n")
        
    except Exception as e:
        SCHEDULER_STATE["last_status"] = f"Error: {e}"
        logger.error(f"❌ 전체 파이프라인 사이클 붕괴 에러: {e}")

def start_scheduler():
    scheduler = AsyncIOScheduler()
    # 핫딜 수집 데몬 (5분 주기)
    scheduler.add_job(run_pipeline_job, 'interval', minutes=5, id='hotdeal_pipeline')
    # 과거 딜 품절 검증 데몬 (7분 주기)
    scheduler.add_job(validate_closed_deals, 'interval', minutes=7, id='hotdeal_validator')
    scheduler.start()
    logger.info("⏰ [System] 투트랙 스케줄러 & 상태 검증 데몬 시동 완료")
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
