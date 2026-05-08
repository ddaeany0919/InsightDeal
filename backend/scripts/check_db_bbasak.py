import sqlite3
import os

db_path = os.path.join(os.path.dirname(__file__), "insight_deal.db")
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# Get bbasak deals
cursor.execute("SELECT title, post_link, ecommerce_link FROM deals WHERE post_link LIKE '%bbasak%' LIMIT 5")
rows = cursor.fetchall()
for r in rows:
    print(r)
conn.close()
