import sys
import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# DB Path: C:\Users\kth00\StudioProjects\InsightDeal\backend\insightdeal.db
db_path = r"C:\Users\kth00\StudioProjects\InsightDeal\backend\insightdeal.db"
engine = create_engine(f"sqlite:///{db_path}")
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
db = SessionLocal()

result = db.execute("SELECT id, title, indexed_at FROM deals ORDER BY indexed_at DESC LIMIT 10").fetchall()
for row in result:
    print(f"[{row[0]}] {row[1][:40]} - {row[2]}")
