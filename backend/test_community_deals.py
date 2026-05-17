import sys
import os

sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from database.session import get_db
from database.models import Deal
from routers.community import get_deal
import traceback
import time

def main():
    db = next(get_db())
    deals = db.query(Deal).order_by(Deal.id.desc()).limit(20).all()
    print(f"Testing {len(deals)} recent deals...")
    error_count = 0
    for d in deals:
        try:
            start_time = time.time()
            get_deal(d.id, db=db)
            print(f"Deal {d.id} OK (took {time.time() - start_time:.2f}s)")
        except Exception as e:
            print(f"Exception on deal_id {d.id} ({d.title}):")
            traceback.print_exc()
            error_count += 1
            if error_count >= 5:
                break
    
    print(f"Finished testing. Total errors: {error_count}")

if __name__ == "__main__":
    main()
