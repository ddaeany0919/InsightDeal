import asyncio
import logging
import sys
import os

# 모듈 경로 설정을 위해 추가
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from backend.models.models_v2 import Base, Community, Product, Deal
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.services.aggregator_service import AggregatorService

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

# 테스트용 SQLite 메모리 DB 설정
engine = create_engine("sqlite:///:memory:", echo=False)
Base.metadata.create_all(bind=engine)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

async def run_test():
    db = SessionLocal()
    
    # 1. 커뮤니티 데이터 강제 셋업
    c_ppomppu = Community(name="ppomppu", display_name="뽐뿌", base_url="https://ppomppu.co.kr")
    c_quasarzone = Community(name="quasarzone", display_name="퀘이사존", base_url="https://quasarzone.com")
    db.add(c_ppomppu)
    db.add(c_quasarzone)
    db.commit()
    
    aggregator = AggregatorService(db)
    
    print("\n=======================================================")
    print(" 🌐 [Task 1 & 2] 라이브 웹 스크래핑 (비동기 v2.0 엔진 테스트) ")
    print("=======================================================\n")
    
    # [Task 1] 뽐뿌 라이브 스크래핑
    try:
        async with PpomppuScraper(c_ppomppu.id) as pp_scraper:
            pp_html = await pp_scraper.fetch_html(pp_scraper.list_url)
            if pp_html:
                pp_deals = await pp_scraper.parse_list(pp_html)
                print(f"✅ 뽐뿌에서 {len(pp_deals)}개의 최신 라이브 딜 수집 완료!")
                for i, d in enumerate(pp_deals[:3]): # 3개만 테스트
                    print(f" -> 뽐뿌 딜 #{i+1}: {d['title']}")
                    await aggregator.process_scraped_deal(c_ppomppu.id, d)
    except Exception as e:
        print(f"❌ 뽐뿌 라이브 에러: {e}")

    print("\n-------------------------------------------------------")

    # [Task 2] 퀘이사존 라이브 스크래핑
    try:
        async with QuasarzoneScraper(c_quasarzone.id) as qz_scraper:
            qz_html = await qz_scraper.fetch_html(qz_scraper.list_url)
            if qz_html:
                qz_deals = await qz_scraper.parse_list(qz_html)
                print(f"✅ 퀘이사존에서 {len(qz_deals)}개의 최신 라이브 딜 수집 완료!")
                for i, d in enumerate(qz_deals[:3]): # 3개만 테스트
                    print(f" -> 퀘이사존 딜 #{i+1}: {d['title']}")
                    await aggregator.process_scraped_deal(c_quasarzone.id, d)
    except Exception as e:
        print(f"❌ 퀘이사존 라이브 에러: {e}")
        

    print("\n=======================================================")
    print(" 🧪 [Task 3] 가상 데이터 기반 동일제품 '통합(Aggregation)' 검증 ")
    print("=======================================================\n")
    
    # 의도적으로 동일 상품을 다른 이름으로 주입하여 병합되는지 확인
    mock_scraped_data = [
        (c_ppomppu.id, {
            "title": "[11마존] 삼성 갤럭시 S24 울트라 512GB 역대급 떴다! (1,230,000/무료)",
            "url": "https://ppomppu.co.kr/mock_1",
            "price": 1230000,
        }),
        (c_quasarzone.id, {
            "title": "삼성전자 갤럭시 S24 울트라 자급제 512GB 최저가 특가 등장 ㄷㄷ",
            "url": "https://quasarzone.com/mock_2",
            "price": 1220000,  # 더 싼 최저가!
        })
    ]
    
    for comm_id, deal_data in mock_scraped_data:
        print(f" -> DB 인입 시뮬레이션: {deal_data['title']}")
        await aggregator.process_scraped_deal(comm_id, deal_data)

    print("\n=======================================================")
    print(" 📊 최종 DB 적재 상태 요약 (와우 포인트 확인!) ")
    print("=======================================================\n")
    
    products = db.query(Product).all()
    print(f"▶ 총 생성된 고유 상품(Product 엔티티): {len(products)}개\n")
    
    for p in products:
        deals = db.query(Deal).filter(Deal.product_id == p.id).all()
        print(f"📦 [카테고리: {p.category}] 브랜드:{p.brand} | 모델명(정규화): {p.name}")
        print(f"   => 현재 최방어선 최저가: {p.current_lowest_price:,}원")
        print(f"   => 서로 다른 원본 병합 딜 개수: {len(deals)}개")
        for d in deals:
            comm_name = "뽐뿌" if d.community_id == c_ppomppu.id else "퀘이사존"
            print(f"      └ [{comm_name} 게시글] {d.title} (기록가: {d.price:,}원)")
        print()
            
if __name__ == "__main__":
    asyncio.run(run_test())
