import sqlite3

db_path = "c:/Users/kth00/StudioProjects/InsightDeal/backend/insight_deal.db"
conn = sqlite3.connect(db_path)
c = conn.cursor()

c.execute("SELECT id, title, price, currency FROM deals WHERE price <= 10000")
deals = c.fetchall()
count = 0
for d in deals:
    title = d[1] or ""
    price = int(d[2]) if d[2] else 0
    currency = d[3]
    if any(k in title for k in ['알리', '코인', '큐텐', '직구', '알익', 'JONR P20']) and price > 0 and price <= 10000:
        c.execute("UPDATE deals SET currency='USD', price=? WHERE id=?", (price * 100, d[0]))
        count += 1

conn.commit()
print(f"Fixed {count} deals")
