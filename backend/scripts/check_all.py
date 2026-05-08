import asyncio
import os
import sys

# 환경 셋업
root_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(root_dir)

from backend.database.session import SessionLocal, engine
from backend.database.models import Deal
from backend.scheduler.main import scrape_community
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from backend.scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from backend.scrapers.bbasak_parenting_scraper import BbasakParentingScraper

async def test_all():
    db = SessionLocal()
    try:
        # DB 초기화
        print("🧹 DB 'deals' 테이블 데이터 초기화 중...")
        db.query(Deal).delete()
        db.commit()
        print("✅ DB 초기화 완료!")

        print("\n🚀 스크래퍼 3페이지 단위 테스트 시작...")
        tasks = [
            scrape_community("ali_ppomppu", AlippomppuScraper, 3),
            scrape_community("bbasak_domestic", BbasakDomesticScraper, 3),
            scrape_community("bbasak_overseas", BbasakOverseasScraper, 3),
            scrape_community("bbasak_parenting", BbasakParentingScraper, 3)
        ]
        
        await asyncio.gather(*tasks)
        print("\n🎉 모든 스크래퍼 테스트 완료!")
        
        # 검증 출력
        deals = db.query(Deal).order_by(Deal.id.desc()).all()
        print(f"\n총 저장된 딜 수: {len(deals)}")
        
        # 각 커뮤니티별 3개씩 샘플 출력
        communities = ["알리뽐뿌", "빠삭국내", "빠삭해외", "빠삭육아"]
        for comm in communities:
            print(f"\n--- {comm} 샘플 ---")
            samples = [d for d in deals if d.community.display_name == comm][:3]
            for s in samples:
                print(f"[{s.category}] {s.title}")
                print(f"  가격: {s.price}원 | 배송비: {s.shipping_fee}")
                print(f"  원본 URL: {s.post_link}")
                print(f"  상품 URL: {s.ecommerce_link}")
                
    finally:
        db.close()

if __name__ == "__main__":
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(test_all())
