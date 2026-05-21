import sqlite3
import re

db_path = "c:/Users/kth00/StudioProjects/InsightDeal/backend/insight_deal.db"
conn = sqlite3.connect(db_path)
c = conn.cursor()

c.execute("SELECT id, title, price, currency FROM deals WHERE (title LIKE '%알리%' OR title LIKE '%코인%' OR title LIKE '%직구%' OR title LIKE '%큐텐%') AND (currency = 'KRW' OR currency IS NULL OR currency = '원')")
rows = c.fetchall()

updated_count = 0
for r in rows:
    deal_id, title, price, currency = r
    if not price: continue
    
    try:
        p_val = float(price)
        if 0 < p_val < 10000:
            print(f"Updating deal {deal_id}: {title} (Price: {price} -> USD)")
            c.execute("UPDATE deals SET currency = 'USD' WHERE id = ?", (deal_id,))
            updated_count += 1
    except Exception as e:
        pass

conn.commit()
conn.close()
print(f"Updated {updated_count} deals.")
