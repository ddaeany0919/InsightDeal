from database.session import SessionLocal
from database.models import Deal, Community

def get_latest_deals_all(limit=30):
    db = SessionLocal()
    try:
        deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(limit).all()
        
        print(f"--- 최신 수집된 딜 목록 (상위 {limit}개) ---")
        for i, deal in enumerate(deals):
            community_name = getattr(deal.community, 'name', 'Unknown')
            print(f"[{i+1}] [{community_name}] {deal.title}")
            print(f"    - 가격: {deal.price} | 배송비: {deal.shipping_fee}")
            print(f"    - 링크: {deal.post_link}")
            print("-" * 50)
            
    except Exception as e:
        print(f"오류 발생: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    get_latest_deals_all()
