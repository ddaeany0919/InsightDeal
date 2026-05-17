import sys
import os
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from database.session import get_db
from routers.community import get_hot_deals
import traceback
import time

def main():
    db = next(get_db())
    print("Testing get_hot_deals()...")
    try:
        start_time = time.time()
        res = get_hot_deals(limit=20, offset=0, category=None, keyword=None, platform=None, db=db)
        print(f"OK. Returned {len(res.get('deals', []))} deals. Took {time.time() - start_time:.2f}s")
    except Exception as e:
        print("Exception in get_hot_deals:")
        traceback.print_exc()

if __name__ == "__main__":
    main()
