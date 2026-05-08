import asyncio
import os
import sys

# Add the backend directory to sys.path so imports work correctly
backend_dir = os.path.dirname(os.path.abspath(__file__))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

from services.aggregator_service import AggregatorService
from database.session import SessionLocal
from models.deal import Deal

async def run_and_verify():
    print("Starting Aggregator Service...")
    service = AggregatorService()
    await service.aggregate_all()
    print("Aggregation complete!")

    print("\n--- Latest 15 Deals Verification ---")
    db = SessionLocal()
    deals = db.query(Deal).order_by(Deal.scraped_at.desc()).limit(15).all()
    
    for deal in deals:
        print("-" * 50)
        print(f"[{deal.community}] {deal.title}")
        print(f"  - 카테고리: {deal.category}")
        print(f"  - 가격: {deal.price}원")
        print(f"  - 배송비: {deal.shipping_fee}")
        print(f"  - 꿀딜점수: {deal.honey_score}")
        
        is_super_hot = deal.honey_score >= 85 or ("최저가" in str(deal.ai_summary)) or ("역대가" in str(deal.ai_summary))
        print(f"  - 초특가 배지 대상: {'🔥 초특가!' if is_super_hot else 'X'}")
        print(f"  - 이미지 존재 여부: {'O' if deal.image_url else 'X (No Image)'}")
        print(f"  - 링크: {deal.url}")
        print(f"  - 요약: {deal.ai_summary}")
        
    db.close()

if __name__ == "__main__":
    asyncio.run(run_and_verify())
