import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from backend.routers.community import build_dsu
from backend.database.session import SessionLocal
from backend.database.models import Deal
from datetime import datetime, timedelta

db = SessionLocal()
deal_id = 4984
deal = db.query(Deal).filter(Deal.id == deal_id).first()

end_time = datetime.utcnow()
time_limit = end_time - timedelta(days=7)

recent_deals = db.query(Deal).filter(
    Deal.indexed_at >= time_limit,
    Deal.indexed_at <= end_time
).all()

if not any(d.id == deal.id for d in recent_deals):
    recent_deals.append(deal)

dsu = build_dsu(recent_deals)
cluster_key = dsu.find(deal.id)
cluster_deal_ids = [d.id for d in recent_deals if dsu.find(d.id) == cluster_key]

print("cluster_deal_ids for 4984:", cluster_deal_ids)
