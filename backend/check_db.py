from database.session import db_manager
from database.models import Deal, Community

session = db_manager.get_session()
deals = session.query(Deal, Community).join(Community).filter(Deal.title.like('%삼양%')).all()

for deal, comm in deals:
    if deal.honey_score >= 100:
        print(f"[{comm.name}] {deal.title} | Closed: {deal.is_closed} | Score: {deal.honey_score} | Indexed: {deal.indexed_at}")
