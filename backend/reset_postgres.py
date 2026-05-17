import asyncio
from database import async_session_maker
from sqlalchemy import text

async def reset_votes():
    async with async_session_maker() as session:
        await session.execute(text("UPDATE community_posts SET bounty_points = 0, like_count = 0"))
        await session.commit()
        print("✅ Postgres DB 투표수(bounty_points, like_count) 초기화 완료!")

if __name__ == "__main__":
    asyncio.run(reset_votes())
