# check_saved_deals_fixed.py
from dotenv import load_dotenv
load_dotenv()

from database import SessionLocal
from models import Deal, Community
from datetime import datetime, timedelta

def check_recent_deals():
    print("=== 최근 저장된 딜 확인 ===\n")
    
    with SessionLocal() as db:
        # 알리뽐뿌 커뮤니티 찾기
        alippomppu = db.query(Community).filter(Community.name == "알리뽐뿌").first()
        
        if not alippomppu:
            print("❌ 알리뽐뿌 커뮤니티를 찾을 수 없습니다.")
            return
        
        print(f"✅ 알리뽐뿌 커뮤니티 ID: {alippomppu.id}\n")
        
        # 최근 30분 내 저장된 딜 조회 (올바른 컬럼명 사용)
        recent_time = datetime.now() - timedelta(minutes=30)
        recent_deals = db.query(Deal).filter(
            Deal.source_community_id == alippomppu.id,  # 수정: community_id → source_community_id
            Deal.indexed_at >= recent_time
        ).order_by(Deal.indexed_at.desc()).all()
        
        print(f"📊 최근 30분 내 저장된 딜: {len(recent_deals)}개\n")
        
        if recent_deals:
            for i, deal in enumerate(recent_deals, 1):
                print(f"{i}. {deal.title}")
                print(f"   - ID: {deal.id}")
                print(f"   - 가격: {deal.price}")
                print(f"   - 배송비: {deal.shipping_fee}")
                print(f"   - 쇼핑몰: {deal.shop_name}")
                print(f"   - 등록시간: {deal.indexed_at}")
                print(f"   - 커뮤니티 ID: {deal.source_community_id}")
                print(f"   - 링크: {deal.post_link}")
                print(f"   - 이커머스 링크: {deal.ecommerce_link}")
                print()
        else:
            print("❌ 최근 저장된 딜이 없습니다.")
            print("💡 더 넓은 범위로 확인해보겠습니다...\n")
            
            # 오늘 저장된 딜 확인
            today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
            today_deals = db.query(Deal).filter(
                Deal.source_community_id == alippomppu.id,
                Deal.indexed_at >= today
            ).order_by(Deal.indexed_at.desc()).all()
            
            print(f"📅 오늘 저장된 딜: {len(today_deals)}개")
            
            if today_deals:
                print("오늘의 딜 5개:")
                for deal in today_deals[:5]:
                    print(f"   - {deal.title} (등록: {deal.indexed_at})")
        
        # 전체 알리뽐뿌 딜 개수
        total_deals = db.query(Deal).filter(Deal.source_community_id == alippomppu.id).count()
        print(f"\n📈 알리뽐뿌 전체 딜 개수: {total_deals}개")
        
        # 전체 딜 개수 (모든 커뮤니티)
        all_deals = db.query(Deal).count()
        print(f"🌟 전체 딜 개수: {all_deals}개")
        
        # 커뮤니티별 딜 개수
        print(f"\n📋 커뮤니티별 딜 개수:")
        communities = db.query(Community).all()
        for comm in communities:
            count = db.query(Deal).filter(Deal.source_community_id == comm.id).count()
            print(f"   - {comm.name}: {count}개")

if __name__ == "__main__":
    check_recent_deals()
