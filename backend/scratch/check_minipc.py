import sys
import os

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))
from backend.database.session import SessionLocal
from backend.database.models import Deal

def main():
    db = SessionLocal()
    deals = db.query(Deal).filter(Deal.title.like('%K10%')).all()
    for d in deals:
        if '미니' in d.title or 'PC' in d.title or 'pc' in d.title:
            print(f"[{d.community}] ID: {d.id} | Price: {d.price} | Title: {d.title}")
            print(f"URL: {d.post_link}")
            print("-" * 50)
    db.close()

if __name__ == "__main__":
    main()
