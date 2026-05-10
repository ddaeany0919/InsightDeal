from backend.database.session import db_manager
from backend.database.models import Deal, Community

session = db_manager.get_session()
deals = session.query(Deal, Community).join(Community).filter(Deal.title.like('%삼양라면%')).all()

for deal, comm in deals:
    print(f"[{comm.name}] {deal.title} | Closed: {deal.is_closed} | Score: {deal.honey_score} | Indexed: {deal.indexed_at} | is_active: {deal.is_active}")
