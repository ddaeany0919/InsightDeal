import sys
import os
from collections import defaultdict

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import build_dsu

def main():
    db = SessionLocal()
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(1000).all()
    
    # We will hijack build_dsu locally to log which condition met
    parent = {}
    def find(i):
        if parent[i] == i:
            return i
        parent[i] = find(parent[i])
        return parent[i]
        
    def union(i, j):
        root_i = find(i)
        root_j = find(j)
        if root_i != root_j:
            parent[root_i] = root_j

    from backend.routers.community import get_normalized_base_name, has_quantity_conflict, has_model_conflict
    
    items = []
    for d in deals:
        if not getattr(d, 'title', None): continue
        norm_res = get_normalized_base_name(d)
        if norm_res:
            words_set = set(norm_res.split('_'))
            items.append({
                'id': d.id,
                'title': d.title,
                'base': norm_res,
                'words': words_set,
                'sc': norm_res.replace('_', '')
            })

    for i in range(len(items)):
        parent[i] = i

    stats = {'jaccard': 0, 'sc': 0, 'subset': 0}
    
    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            w1 = items[i]['words']
            w2 = items[j]['words']
            if not w1 or not w2:
                continue
                
            intersection = len(w1.intersection(w2))
            if intersection == 0:
                continue
                
            union_len = len(w1.union(w2))
            jaccard = intersection / union_len
            
            matched = False
            reason = ""
            if jaccard >= 0.5:
                matched = True
                reason = "jaccard"
            elif items[i]['sc'] == items[j]['sc']:
                matched = True
                reason = "sc"
            else:
                subset_ratio = max(intersection / len(w1), intersection / len(w2))
                if subset_ratio >= 0.8 and intersection >= 3:
                    matched = True
                    reason = "subset"

            if matched:
                if has_quantity_conflict(w1, w2) or has_model_conflict(w1, w2):
                    pass
                else:
                    root_i = find(i)
                    root_j = find(j)
                    if root_i != root_j:
                        union(i, j)
                        stats[reason] += 1

    print("Round 1 (Jaccard >= 0.5):", stats['jaccard'])
    print("Round 2 (Super Clean ==):", stats['sc'])
    print("Round 3 (Subset Ratio >= 0.8):", stats['subset'])
    
    # Print some subset matched pairs to verify
    print("\n--- Subset Examples ---")
    subset_count = 0
    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            w1 = items[i]['words']
            w2 = items[j]['words']
            if not w1 or not w2: continue
            intersection = len(w1.intersection(w2))
            union_len = len(w1.union(w2))
            jaccard = intersection / union_len
            if jaccard < 0.5 and items[i]['sc'] != items[j]['sc']:
                subset_ratio = max(intersection / len(w1), intersection / len(w2))
                if subset_ratio >= 0.8 and intersection >= 3:
                    if not has_quantity_conflict(w1, w2) and not has_model_conflict(w1, w2):
                        print(f"[subset={subset_ratio:.2f}] {items[i]['title']}  VS  {items[j]['title']}")
                        subset_count += 1
                        if subset_count > 20:
                            break
        if subset_count > 20: break

if __name__ == "__main__":
    main()
