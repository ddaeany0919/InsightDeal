import asyncio
from database.session import SessionLocal
from routers.community import get_hot_deals

db = SessionLocal()
try:
    result = asyncio.run(get_hot_deals(limit=5, offset=0, category=None, keyword=None, platform=None, db=db))
    print(result)
except Exception as e:
    import traceback
    traceback.print_exc()
