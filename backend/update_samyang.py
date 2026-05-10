from backend.database.session import db_manager
from backend.database.models import Deal

session = db_manager.get_session()
deals = session.query(Deal).filter(Deal.title.like('%삼양%')).all()

count = 0
for deal in deals:
    deal.honey_score = 60
    deal.is_super_hotdeal = False
    count += 1

session.commit()
print(f"Updated {count} deals containing '삼양' to honey_score=60 and is_super_hotdeal=False")
session.close()
