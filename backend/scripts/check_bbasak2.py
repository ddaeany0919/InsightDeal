import sqlite3
import os
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

db_path = os.path.join(os.path.dirname(__file__), "insight_deal.db")
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# Get bbasak deals
cursor.execute("SELECT title, post_link, ecommerce_link FROM deals WHERE post_link LIKE '%bbasak2%' LIMIT 10")
rows = cursor.fetchall()
for r in rows:
    print(r)
conn.close()
