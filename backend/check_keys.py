import sys
sys.path.append('c:/Users/kth00/StudioProjects/InsightDeal')
from backend.routers.community import get_cluster_key
from backend.database.session import SessionLocal
from backend.database.models import Deal

db = SessionLocal()
deals = db.query(Deal).order_by(Deal.id.desc()).limit(20).all()
with open("check_keys.txt", "w", encoding="utf-8") as f:
    for d in deals:
        f.write(f"{d.id} | {get_cluster_key(d)} | {d.ecommerce_link}\n")
