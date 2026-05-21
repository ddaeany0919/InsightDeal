import sqlite3

db_path = "c:/Users/kth00/StudioProjects/InsightDeal/backend/insight_deal.db"
conn = sqlite3.connect(db_path)
c = conn.cursor()

c.execute("UPDATE deals SET currency='USD', price=13700 WHERE id=8610")
conn.commit()
print("Fixed ID 8610")
