import asyncio
from backend.database.session import AsyncSessionLocal
from sqlalchemy import text

async def get_db_time():
    async with AsyncSessionLocal() as db:
        res = await db.execute(text("SELECT platform_name, title, posted_at FROM deals WHERE platform_name='fmkorea' ORDER BY posted_at DESC LIMIT 5"))
        for row in res:
            print(f"Platform: {row[0]}, Title: {row[1][:15]}..., Time: {row[2]}, TZ: {row[2].tzinfo if hasattr(row[2], 'tzinfo') else 'None'}")

if __name__ == "__main__":
    asyncio.run(get_db_time())
