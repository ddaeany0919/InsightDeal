import sys
import os
import json
import itertools

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import build_dsu, get_normalized_base_name

def main():
    db = SessionLocal()
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(1000).all()
    
    dsu = build_dsu(deals, time_window_days=30)
    
    deal_info = []
    for d in deals:
        if not d.title: continue
        n_name = get_normalized_base_name(d)
        if not n_name: continue
        words_set = set(n_name.split('_'))
        cid = dsu.find(d.id)
        deal_info.append({"id": d.id, "title": d.title, "words": words_set, "cid": cid})
        
    borderlines = []
    
    for i in range(len(deal_info)):
        for j in range(i+1, len(deal_info)):
            di = deal_info[i]
            dj = deal_info[j]
            if di["cid"] == dj["cid"]:
                continue
                
            intersection = len(di["words"].intersection(dj["words"]))
            union = len(di["words"].union(dj["words"]))
            
            if union == 0: continue
            
            jaccard = intersection / union
            
            # Find borderline cases: 0.3 <= jaccard < 0.5
            if 0.3 <= jaccard < 0.5:
                # To reduce noise, require at least 2 shared words
                if intersection >= 2:
                    borderlines.append({
                        "jaccard": round(jaccard, 3),
                        "shared_words": list(di["words"].intersection(dj["words"])),
                        "deal1": {"title": di["title"], "words": list(di["words"])},
                        "deal2": {"title": dj["title"], "words": list(dj["words"])}
                    })
                    
    # Sort by jaccard descending
    borderlines.sort(key=lambda x: x["jaccard"], reverse=True)
    
    with open("c:/Users/kth00/StudioProjects/InsightDeal/backend/scratch/borderline_clusters.json", "w", encoding="utf-8") as f:
        json.dump(borderlines[:100], f, ensure_ascii=False, indent=2)

    db.close()

if __name__ == "__main__":
    main()
