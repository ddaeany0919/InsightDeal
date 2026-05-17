import sys
sys.path.append('c:/Users/kth00/StudioProjects/InsightDeal')
from backend.database.session import SessionLocal
from backend.database.models import Deal

db = SessionLocal()
deals = db.query(Deal).order_by(Deal.id.desc()).limit(150).all()

with open('same.txt', 'w', encoding='utf-8') as f:
    for d in deals:
        f.write(f"{d.id} | {d.title} | {d.base_product_name} | {d.ecommerce_link}\n")
