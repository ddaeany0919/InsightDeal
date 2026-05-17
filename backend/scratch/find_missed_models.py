import sys
import os
import json
import re
import collections

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import build_dsu, get_normalized_base_name

def get_model_codes(title):
    codes = set()
    if not title: return codes
    words = get_normalized_base_name(title)
    if not words: return codes
    for w in words.split('_'):
        if (len(w) >= 5
                and any(c.isdigit() for c in w)
                and any(c.isalpha() for c in w)
                and not w.endswith('gb')
                and not w.endswith('mb')
                and not w.endswith('kg')
                and not w.endswith('ml')):
            codes.add(w.lower())
    return codes

def main():
    db = SessionLocal()
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(5000).all()
    
    # We create a dummy object to pass to get_normalized_base_name
    class DummyDeal:
        def __init__(self, t):
            self.title = t
            
    # Re-run build_dsu (which we just updated)
    dsu = build_dsu(deals, time_window_days=30)
    
    # Map model code to clusters
    model_to_clusters = collections.defaultdict(list)
    
    for d in deals:
        if not d.title: continue
        dummy = DummyDeal(d.title)
        words_str = get_normalized_base_name(dummy)
        if not words_str: continue
        
        codes = get_model_codes(dummy)
        cid = dsu.find(d.id)
        
        for code in codes:
            model_to_clusters[code].append((cid, d))
            
    missed_models = []
    
    for code, items in model_to_clusters.items():
        unique_cids = set(cid for cid, d in items)
        if len(unique_cids) > 1:
            missed_models.append({
                "model_code": code,
                "deals": [
                    {"id": d.id, "title": d.title, "cluster_id": cid} for cid, d in items
                ]
            })
            
    with open("c:/Users/kth00/StudioProjects/InsightDeal/backend/scratch/missed_models.json", "w", encoding="utf-8") as f:
        json.dump(missed_models[:20], f, ensure_ascii=False, indent=2)

    db.close()

if __name__ == "__main__":
    main()
