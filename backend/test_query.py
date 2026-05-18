from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from database import models
from datetime import datetime, timedelta
from sqlalchemy import or_, and_

engine = create_engine('sqlite:///insight_deal.db')
SessionLocal = sessionmaker(bind=engine)
db = SessionLocal()

query = db.query(models.Deal).join(models.Community)
query = query.filter(and_(models.Deal.category != "적립", models.Deal.category != "적립/이벤트", models.Deal.category != "이벤트"))

two_hours_ago = datetime.utcnow() - timedelta(hours=2)
query = query.filter(
    or_(
        models.Deal.indexed_at >= two_hours_ago, 
        models.Deal.honey_score >= 10
    )
)

deals = query.order_by(models.Deal.indexed_at.desc()).limit(300).all()
print("Deals found:", len(deals))
