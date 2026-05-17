import os
import sys

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal

def main():
    db = SessionLocal()
    try:
        deals = db.query(Deal).filter(
            Deal.title.like('%소소한형제%') | 
            Deal.title.like('%빅스Day%') |
            Deal.title.like('%토스쇼핑%')
        ).all()
        
        for d in deals:
            print(f"Title: {d.title}")
            print(f"Score: {d.honey_score}")
            print(f"AI Summary: {d.ai_summary}")
            print(f"Closed: {d.is_closed}")
            print(f"Indexed at: {d.indexed_at}")
            print("-" * 40)
    finally:
        db.close()

if __name__ == "__main__":
    main()
