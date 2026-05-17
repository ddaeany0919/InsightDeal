import sys
import os
import re

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal

def main():
    db = SessionLocal()
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(5000).all()
    
    decimal_cases = []
    for d in deals:
        if not d.title: continue
        # Find something like 1.5L, 0.5kg, 1.2kg
        match = re.search(r'(\d+\.\d+)\s*(kg|l|g|ml)', d.title.lower())
        if match:
            decimal_cases.append(d.title)
            
    with open("c:/Users/kth00/StudioProjects/InsightDeal/backend/scratch/decimal_cases.txt", "w", encoding="utf-8") as f:
        for c in decimal_cases:
            f.write(c + "\n")

    db.close()

if __name__ == "__main__":
    main()
