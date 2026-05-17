import sys, re
sys.path.append('c:/Users/kth00/StudioProjects/InsightDeal')
from backend.routers.community import get_cluster_key
from backend.database.session import SessionLocal
from backend.database.models import Deal

db = SessionLocal()
deals = db.query(Deal).order_by(Deal.id.desc()).limit(10).all()
for d in deals:
    k = get_cluster_key(d)
    print(f"{d.id} | Key: {k.encode('utf-8')} | Base: {d.base_product_name.encode('utf-8') if d.base_product_name else None} | URL: {d.ecommerce_link}")
