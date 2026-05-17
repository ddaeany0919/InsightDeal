import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
import asyncio
from backend.database.session import create_db_session
from backend.routers.community import get_top_hot_deals

async def test():
    with create_db_session() as session:
        res = await get_top_hot_deals(session)
        deals = res.get('deals', [])
        print(f'Count: {len(deals)}')
        for item in deals[:10]:
            print(f"[{item['site_name']}] {item['title']} / score: {item['honey_score']} / likes: {item['like_count']}")

if __name__ == "__main__":
    asyncio.run(test())
