import sys, re
sys.path.append('c:/Users/kth00/StudioProjects/InsightDeal')
from backend.routers.community import get_cluster_key
from backend.database.session import SessionLocal
from backend.database.models import Deal

db = SessionLocal()
deals = db.query(Deal).order_by(Deal.id.desc()).limit(100).all()
groups = {}
for d in deals:
    k = get_cluster_key(d)
    groups.setdefault(k, []).append(d)

for k, group in groups.items():
    if len(group) > 1:
        print(f"\nGroup {k.encode('utf-8')}:")
        for d in group:
            print(f"  {d.id} | {d.title.encode('utf-8')} | {d.base_product_name.encode('utf-8') if d.base_product_name else None} | {d.ecommerce_link}")
