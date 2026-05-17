import sys, codecs
sys.stdout = codecs.getwriter('utf-8')(sys.stdout.detach())
from backend.database.session import SessionLocal
from backend.database.models import Deal

db = SessionLocal()
try:
    d = db.query(Deal).filter(Deal.title.like('%17[G마켓]%')).first()
    if d:
        print(f"Title: {d.title}, Link: {d.post_link}, Is_Closed: {d.is_closed}")
finally:
    db.close()
