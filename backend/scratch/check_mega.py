import sys
import os
import json

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import build_dsu

def main():
    db = SessionLocal()
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(10000).all()
    
    dsu = build_dsu(deals, time_window_days=30)
    
    mega_coffee_deals = []
    for d in deals:
        if ("메가커피" in d.title or "메가MGC" in d.title) and ("아메리카노" in d.title):
            cluster_id = dsu.find(d.id)
            mega_coffee_deals.append({
                "id": d.id,
                "site": getattr(d, 'site_name', getattr(d, 'source_site', getattr(d, 'shop_name', 'Unknown'))),
                "title": d.title,
                "price": d.price,
                "cluster_id": cluster_id
            })
            
    with open("c:/Users/kth00/StudioProjects/InsightDeal/backend/scratch/mega_coffee.json", "w", encoding="utf-8") as f:
        json.dump(mega_coffee_deals, f, ensure_ascii=False, indent=2)

    db.close()

if __name__ == "__main__":
    main()
