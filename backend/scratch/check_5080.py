import sys
import os
from collections import defaultdict

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))
from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import build_dsu

def main():
    db = SessionLocal()
    # Get 3000 recent deals to build realistic clusters
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(3000).all()
    
    dsu = build_dsu(deals, time_window_days=7)
    
    clusters = defaultdict(list)
    for d in deals:
        root = dsu.find(d.id)
        clusters[root].append(d)
        
    print("=== 5080 Clusters ===")
    
    # Only keep clusters where at least one deal has '5080'
    for root, items in clusters.items():
        if any("5080" in d.title for d in items if d.title):
            print(f"\nCluster Root: {root}")
            for d in items:
                p = f"{d.price}원" if d.price else "가격없음"
                print(f" - [{d.community}] {d.title} ({p})")
                
    db.close()

if __name__ == "__main__":
    main()
