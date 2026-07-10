import logging
import sys
import os
import asyncio

# 현재 디렉토리를 path에 추가하여 모듈 임포트 가능하게 함
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from database.session import SessionLocal
from database.models import Community
from scrapers.ppomppu_scraper import PpomppuScraper
from scrapers.ruliweb_scraper import RuliwebScraper
from scrapers.clien_scraper import ClienScraper
from scrapers.quasarzone_scraper import QuasarzoneScraper
from scrapers.fmkorea_scraper import FmkoreaScraper
from scrapers.alippomppu_scraper import AlippomppuScraper
from scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from scrapers.bbasak_overseas_scraper import BbasakOverseasScraper

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

COMMUNITIES = [
    {"name": "뽐뿌", "url": "https://www.ppomppu.co.kr", "class": PpomppuScraper},
    {"name": "루리웹", "url": "https://bbs.ruliweb.com/market/board/1020", "class": RuliwebScraper},
    {"name": "클리앙", "url": "https://www.clien.net/service/board/jirum", "class": ClienScraper},
    {"name": "퀘이사존", "url": "https://quasarzone.com/bbs/qb_saleinfo", "class": QuasarzoneScraper},
    {"name": "펨코", "url": "https://www.fmkorea.com/hotdeal", "class": FmkoreaScraper},
    {"name": "알리뽐뿌", "url": "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4", "class": AlippomppuScraper},
    {"name": "빠삭국내", "url": "https://bbasak.com/bbs/board.php?bo_table=bbasak1", "class": BbasakDomesticScraper},
    {"name": "빠삭해외", "url": "https://bbasak.com/bbs/board.php?bo_table=bbasak2", "class": BbasakOverseasScraper},
]

def ensure_communities_exist(db):
    """모든 커뮤니티가 없으면 생성"""
    for comm_info in COMMUNITIES:
        community = db.query(Community).filter(Community.name == comm_info["name"]).first()
        if not community:
            logger.info(f"Creating '{comm_info['name']}' community...")
            community = Community(name=comm_info["name"], base_url=comm_info["url"])
            db.add(community)
    db.commit()

async def run_test_async():
    logger.info("🚀 Starting Comprehensive Scraper Test (Limit: 5 items per community)...")
    
    db = SessionLocal()
    ensure_communities_exist(db)
    
    for comm_info in COMMUNITIES:
        try:
            logger.info(f"\n--- Running Scraper: {comm_info['name']} ---")
            
            # DB에서 커뮤니티 ID 조회하여 생성자에 주입
            community = db.query(Community).filter(Community.name == comm_info["name"]).first()
            if not community:
                logger.error(f"Community '{comm_info['name']}' not found in database.")
                continue
                
            scraper = comm_info["class"](community_id=community.id)
            
            # 비동기 컨텍스트 매니저 진입
            async with scraper:
                # scraper.list_url을 가져와 수집 수행 (run은 limit 파라미터가 없으므로 생략)
                deals = await scraper.run(scraper.list_url)
                
                # 결과 슬라이싱으로 5개 데이터에 대해 간이 상세 정보 로깅
                test_deals = deals[:5]
                logger.info(f"Collected {len(deals)} items. Testing detail for first {len(test_deals)} items...")
                for d in test_deals:
                    # 상세 파싱 테스트
                    detail = await scraper.get_detail(d["url"])
                    logger.info(f" - Title: {d['title'][:30]}... | Price: {d['price']} | Detail URL: {detail.get('ecommerce_link')}")
                    
            logger.info(f"✅ {comm_info['name']} Scraper Completed.")
        except Exception as e:
            logger.error(f"❌ {comm_info['name']} Scraper Failed: {e}", exc_info=True)
            
    db.close()
    logger.info("\n🎉 Comprehensive Scraper Test Finished.")

def run_test():
    asyncio.run(run_test_async())

if __name__ == "__main__":
    run_test()
