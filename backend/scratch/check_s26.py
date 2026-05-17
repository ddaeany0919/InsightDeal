import sqlite3

conn = sqlite3.connect('../insight_deal.db')
cursor = conn.cursor()

cursor.execute("SELECT id, title, price, shop_name, indexed_at, is_closed FROM deals WHERE title LIKE '%S26%' ORDER BY indexed_at DESC LIMIT 20;")
rows = cursor.fetchall()
for r in rows:
    print(r)
