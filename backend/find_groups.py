import asyncio
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker
from sqlalchemy import text
from routers.community import build_clusters

async def main():
    engine = create_async_engine("sqlite+aiosqlite:///insight_deal.db")
    async_session = sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    
    async with async_session() as session:
        result = await session.execute(text("""
            SELECT d.id, d.source_community_id, d.title, d.post_link, d.ecommerce_link, 
                   d.shop_name, d.price, d.shipping_fee, d.image_url, d.category, 
                   d.indexed_at, d.is_closed, d.deal_type, d.content_html, d.honey_score,
                   c.name as community_name
            FROM deals d
            JOIN communities c ON d.source_community_id = c.id
            WHERE d.indexed_at >= datetime('now', '-3 days')
        """))
        rows = result.fetchall()
        
    class DummyDeal:
        def __init__(self, **kwargs):
            for k, v in kwargs.items():
                setattr(self, k, v)
            
    deals = []
    for r in rows:
        deal = DummyDeal(
            id=r[0], source_community_id=r[1], title=r[2], post_link=r[3], 
            ecommerce_link=r[4], shop_name=r[5], price=r[6], shipping_fee=r[7], 
            image_url=r[8], category=r[9], indexed_at=r[10], is_closed=r[11], 
            deal_type=r[12], content_html=r[13], honey_score=r[14]
        )
        setattr(deal, 'community', DummyDeal(name=r[15]))
        deals.append(deal)
        
    clusters = build_clusters(deals)
    
    with open("grouped_deals.md", "w", encoding="utf-8") as f:
        f.write("# 클러스터링된 다중 커뮤니티 핫딜\n\n")
        count = 0
        for cluster in clusters:
            if len(cluster) > 1:
                # Check if they are from different communities
                comms = set(d.community.name for d in cluster)
                if len(comms) > 1:
                    count += 1
                    f.write(f"### 📦 그룹 {count}\n")
                    f.write(f"**대표 상품명**: {cluster[0].title}\n")
                    for d in cluster:
                        f.write(f"- [{d.community.name}] {d.price}원 | [링크]({d.post_link})\n")
                    f.write("\n---\n")
                    if count >= 20: break
                    
    if count == 0:
        with open("grouped_deals.md", "w", encoding="utf-8") as f:
            f.write("최근 3일 내에 여러 커뮤니티에서 묶인 핫딜이 없습니다.\n")
            
    print("Done")

if __name__ == "__main__":
    asyncio.run(main())
