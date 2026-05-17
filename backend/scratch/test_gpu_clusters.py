import sys
import os
import re

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))
from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import get_normalized_base_name

def get_model_codes(words):
    codes = set()
    for w in words:
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
    # Fetch recent graphics card deals
    deals = db.query(Deal).filter(
        Deal.title.like("%RTX%") | 
        Deal.title.like("%지포스%") | 
        Deal.title.like("%라데온%") | 
        Deal.title.like("%RX %")
    ).order_by(Deal.indexed_at.desc()).limit(100).all()
    
    print(f"Total GPU deals found: {len(deals)}")
    
    grouped = {}
    for d in deals:
        norm_res = get_normalized_base_name(d)
        if not norm_res: continue
        words = set(norm_res.split('_'))
        codes = get_model_codes(words)
        code_str = ", ".join(sorted(codes)) if codes else "NO_CODE"
        
        if code_str not in grouped:
            grouped[code_str] = []
        grouped[code_str].append(d.title)
        
    for code, titles in grouped.items():
        print(f"\n[Model Code: {code}]")
        for t in titles:
            print(f" - {t}")
            
    db.close()

if __name__ == "__main__":
    main()
