import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from backend.routers.community import get_deal_history
from backend.database.session import SessionLocal

db = SessionLocal()
res = get_deal_history(4984, db)
print("Deal 4984 history:", res)
