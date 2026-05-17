import os
import sys

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal, Community

def main():
    db = SessionLocal()
    try:
        deals = db.query(Deal).join(Community).filter(
            Community.name == 'ppomppu',
            Deal.honey_score >= 100
        ).all()
        
        count = 0
        for d in deals:
            d.honey_score = 99
            if d.ai_summary and "🔥" in d.ai_summary:
                d.ai_summary = d.ai_summary.replace("🔥 [커뮤니티 인기] ", "").replace("🔥 [커뮤니티 인증 핫딜] ", "").replace("🔥", "").strip()
            count += 1
            
        db.commit()
        print(f"Successfully downgraded {count} Ppomppu deals from score 100 to 99.")
    except Exception as e:
        db.rollback()
        print(f"Error: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    main()
