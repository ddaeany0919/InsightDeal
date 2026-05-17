import sys
import os
import json

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import build_dsu

def main():
    db = SessionLocal()
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(1000).all()
    
    dsu = build_dsu(deals, time_window_days=30)
    
    clusters = {}
    for d in deals:
        cluster_id = dsu.find(d.id)
        if cluster_id not in clusters:
            clusters[cluster_id] = []
        clusters[cluster_id].append(d)
        
    interesting_clusters = []
    for cid, c_deals in clusters.items():
        if len(c_deals) > 1:
            titles = set(d.title for d in c_deals)
            if len(titles) > 1:
                interesting_clusters.append(c_deals)
                
    results = []
    for i, c_deals in enumerate(interesting_clusters[:10]):
        cluster_info = {"cluster_id": i + 1, "deals": []}
        for d in c_deals:
            site = getattr(d, 'site_name', getattr(d, 'source_site', getattr(d, 'shop_name', 'Unknown')))
            if not site:
                site = "Unknown"
            cluster_info["deals"].append({"site": site, "title": d.title, "price": d.price})
        results.append(cluster_info)

    with open("c:/Users/kth00/StudioProjects/InsightDeal/backend/scratch/result.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    db.close()

if __name__ == "__main__":
    main()
