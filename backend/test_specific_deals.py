import sys
import os
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

import asyncio
from database.session import get_db
from routers.community import get_hot_deals, get_deal
import traceback

def main():
    db = next(get_db())
    res = asyncio.run(get_hot_deals(limit=100, offset=0, category=None, keyword='미니PC', platform=None, db=db))
    print(f"Found {len(res.get('deals', []))} deals for '미니PC'. Testing get_deal()...")
    for deal in res.get('deals', []):
        try:
            get_deal(deal['id'], db)
        except Exception as e:
            print(f"Exception on get_deal({deal['id']}):")
            traceback.print_exc()

if __name__ == "__main__":
    main()
