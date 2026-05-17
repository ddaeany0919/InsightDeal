import os
import sys

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal, Community

def main():
    db = SessionLocal()
    try:
        deals = db.query(Deal, Community).join(Community).filter(
            Community.name == 'ppomppu',
            Deal.honey_score >= 100,
            Deal.is_closed == False
        ).all()
        
        print(f"Total active Ppomppu hot deals: {len(deals)}")
        
        for d, c in deals:
            try:
                title = d.title.encode('cp949', 'ignore').decode('cp949')
                print(f"Title: {title}")
                print(f"URL: {d.url}")
                print(f"Score: {d.honey_score}")
                print(f"Indexed at: {d.indexed_at}")
                
                # Print AI summary safely
                ai_summary = d.ai_summary or ""
                safe_summary = ai_summary.encode('cp949', 'ignore').decode('cp949')
                print(f"AI Summary: {safe_summary}")
                print("-" * 40)
            except Exception as e:
                print(f"Error printing deal {d.id}: {e}")
            
    finally:
        db.close()

if __name__ == "__main__":
    main()
