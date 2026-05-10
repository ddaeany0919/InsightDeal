import sys, os
sys.path.append(os.path.abspath('.'))
from backend.database.session import SessionLocal
from backend.database.models import Deal, PriceHistory

db = SessionLocal()

def delete_deal_with_history(d):
    print(f'Deleting: {d.title}')
    histories = db.query(PriceHistory).filter(PriceHistory.deal_id == d.id).all()
    for h in histories:
        db.delete(h)
    db.delete(d)

deals = db.query(Deal).filter(Deal.title.like('%태국망고 대과 2.5kg%')).all()
for d in deals:
    delete_deal_with_history(d)

deals2 = db.query(Deal).filter(Deal.title.like('%제스프리 골드키위 슈퍼점보과 3.4kg%')).all()
for d in deals2:
    delete_deal_with_history(d)

db.commit()
print('Deletion Done!')
