import sqlite3
import re

db_path = "c:/Users/kth00/StudioProjects/InsightDeal/backend/insight_deal.db"
conn = sqlite3.connect(db_path)
c = conn.cursor()

c.execute("SELECT id, title, price, currency FROM deals WHERE (title LIKE '%알리%' OR title LIKE '%코인%' OR title LIKE '%큐텐%' OR title LIKE '%직구%' OR title LIKE '%알익%') AND price <= 10000 AND currency IN ('KRW', 'USD')")
deals = c.fetchall()
count = 0
for d in deals:
    c.execute("UPDATE deals SET currency = 'USD', price = ? WHERE id = ?", (int(d[2]) * 100, d[0]))
    count += 1
conn.commit()
print(f"Fixed {count} deals")
