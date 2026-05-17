import os
import sys

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal

def main():
    db = SessionLocal()
    try:
        deals = db.query(Deal).filter(
            Deal.title.like('%Q9000%') | Deal.title.like('%빅세일LIVE%삼성전자%')
        ).all()
        
        for d in deals:
            print(f"Title: {d.title}")
            print(f"URL: {d.url}")
            print(f"Score: {d.honey_score}")
            print(f"Closed: {d.is_closed}")
            print(f"Indexed at: {d.indexed_at}")
            print(f"View Count: {d.view_count}, Like Count: {d.like_count}")
            
            ai_summary = d.ai_summary or ""
            safe_summary = ai_summary.encode('cp949', 'ignore').decode('cp949')
            print(f"AI Summary: {safe_summary}")
            print("-" * 40)
            
    finally:
        db.close()

if __name__ == "__main__":
    main()
