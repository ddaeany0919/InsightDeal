import os
import sys
import re

# Set correct python path
sys.path.append(os.getcwd())

from backend.database.session import SessionLocal
from backend.database.models import Deal

def fix_shipping_fees():
    db = SessionLocal()
    try:
        deals = db.query(Deal).filter(Deal.shipping_fee.isnot(None)).all()
        fixed_count = 0
        for deal in deals:
            if re.search(r'\s{2,}|\n|\t', deal.shipping_fee):
                # Clean up multiple spaces, tabs, and newlines
                cleaned = re.sub(r'\s+', ' ', deal.shipping_fee).strip()
                if cleaned != deal.shipping_fee:
                    deal.shipping_fee = cleaned
                    fixed_count += 1
                    
        if fixed_count > 0:
            db.commit()
            print(f"✅ Fixed {fixed_count} deals with broken shipping_fee formatting.")
        else:
            print("✅ No broken shipping_fee strings found.")
            
    except Exception as e:
        print(f"Error: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    fix_shipping_fees()
