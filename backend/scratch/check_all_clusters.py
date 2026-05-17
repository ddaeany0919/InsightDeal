import sys
import os
from collections import defaultdict

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))
from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import build_dsu

def main():
    db = SessionLocal()
    # 5000개 로드
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(5000).all()
    print(f"Total deals loaded: {len(deals)}")
    dsu = build_dsu(deals, time_window_days=7)
    
    clusters = defaultdict(list)
    for d in deals:
        root = dsu.find(d.id)
        clusters[root].append(d)
        
    print(f"Total clusters formed: {len(clusters)}")
    
    anomalies = []
    
    for root, items in clusters.items():
        if len(items) <= 1:
            continue
            
        prices = []
        for d in items:
            if d.price is not None:
                try:
                    p = int(d.price)
                    if p > 100:
                        prices.append(p)
                except ValueError:
                    pass
                    
        if not prices:
            continue
            
        min_p = min(prices)
        max_p = max(prices)
        
        if min_p > 0 and max_p / min_p >= 2.0 and (max_p - min_p) >= 20000:
            anomalies.append({
                'root': root,
                'items': items,
                'min_p': min_p,
                'max_p': max_p
            })
            
    print(f"\n--- 가격 불일치 의심 클러스터 (총 {len(anomalies)}개) ---")
    for a in anomalies:
        print(f"\n[Price Diff: {a['min_p']}원 ~ {a['max_p']}원]")
        for d in a['items']:
            p_str = f"{d.price}원" if d.price else "가격없음"
            print(f" - [{p_str}] {d.title}")
            
    db.close()

if __name__ == "__main__":
    main()
