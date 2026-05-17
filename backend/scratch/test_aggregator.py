import sys, os, asyncio
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))
from backend.services.aggregator_service import AggregatorService
from backend.database.session import SessionLocal

async def main():
    db = SessionLocal()
    svc = AggregatorService(db)
    
    # 1. 퀘이사존 데이터
    qz_data = {
        "title": "완전 랜덤한 특가 테스트 ZZZ123XYZ456 상품명입니다 10.61만",
        "price": 154911,
        "currency": "KRW",
        "url": "https://quasarzone.com/bbs/qb_saleinfo/views/1951194_TEST8"
    }
    # community_id = 8 for 퀘이사존
    deal1 = await svc.process_scraped_deal(8, qz_data)
    if deal1:
        print("--- 퀘이사존 ---")
        print(f"Final Price: {deal1.price} {deal1.currency}")
        print(f"Options Data: {deal1.options_data}")
        print(f"Category: {deal1.category}")

    db.close()

if __name__ == "__main__":
    asyncio.run(main())
