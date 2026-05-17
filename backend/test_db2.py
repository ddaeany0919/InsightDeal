import sqlite3
conn = sqlite3.connect('backend/insight_deal.db')
deals = conn.execute("SELECT id, title, indexed_at, price FROM deals WHERE title LIKE '%S26%'").fetchall()
print("Deals:", deals)
history = conn.execute("SELECT deal_id, price, checked_at FROM price_history WHERE deal_id IN (4984, 5856, 5866) ORDER BY checked_at").fetchall()
print("History:", history)
