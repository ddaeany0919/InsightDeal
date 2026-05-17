import sys
import os
import json

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import build_dsu, get_normalized_base_name

def get_model_codes(words_set):
    codes = set()
    for w in words_set:
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
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(1500).all()
    
    dsu = build_dsu(deals, time_window_days=30)
    
    # Let's find pairs that have a subset relationship but are not in the same cluster
    deal_info = []
    for d in deals:
        if not d.title: continue
        n_name = get_normalized_base_name(d)
        if not n_name: continue
        words_set = set(n_name.split('_'))
        cid = dsu.find(d.id)
        deal_info.append({"id": d.id, "title": d.title, "words": words_set, "cid": cid})
        
    missed_subsets = []
    
    for i in range(len(deal_info)):
        for j in range(i+1, len(deal_info)):
            di = deal_info[i]
            dj = deal_info[j]
            if di["cid"] == dj["cid"]:
                continue # Already clustered together
                
            intersection = di["words"].intersection(dj["words"])
            if not intersection: continue
            
            min_len = min(len(di["words"]), len(dj["words"]))
            if min_len == 0: continue
            
            subset_ratio = len(intersection) / min_len
            
            # If one is a > 80% subset of the other
            if subset_ratio >= 0.8:
                # To prevent generic matches, require at least 3 matching words, OR a matching model code
                codes_i = get_model_codes(di["words"])
                codes_j = get_model_codes(dj["words"])
                shared_codes = codes_i.intersection(codes_j)
                
                if len(intersection) >= 3 or shared_codes:
                    missed_subsets.append({
                        "ratio": subset_ratio,
                        "shared_words": list(intersection),
                        "deal1": {"id": di["id"], "title": di["title"], "cid": di["cid"]},
                        "deal2": {"id": dj["id"], "title": dj["title"], "cid": dj["cid"]}
                    })
                    
    with open("c:/Users/kth00/StudioProjects/InsightDeal/backend/scratch/missed_subsets.json", "w", encoding="utf-8") as f:
        json.dump(missed_subsets[:50], f, ensure_ascii=False, indent=2)

    db.close()

if __name__ == "__main__":
    main()
