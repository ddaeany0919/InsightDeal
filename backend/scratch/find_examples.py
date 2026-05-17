import sys
import os

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))
from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import build_dsu

def main():
    db = SessionLocal()
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(2000).all()
    
    dsu = build_dsu(deals, time_window_days=7)
    
    from collections import defaultdict
    clusters = defaultdict(list)
    for d in deals:
        root = dsu.find(d.id)
        clusters[root].append(d)
        
    for root, items in clusters.items():
        if len(items) >= 3:
            titles = [d.title for d in items if d.title]
            
            # Look for specific interesting cases
            has_decimal = any("1.5" in t or "1.2" in t for t in titles)
            has_parens = any("(" in t and ")" in t for t in titles)
            is_gpu = any("rtx" in t.lower() or "rx" in t.lower() for t in titles)
            
            if has_decimal or has_parens or is_gpu:
                print(f"--- Cluster ---")
                for t in titles:
                    print(t)
    
    db.close()

if __name__ == "__main__":
    main()
