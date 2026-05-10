import sqlite3

db_path = r"c:\Users\kth00\StudioProjects\InsightDeal\backend\insight_deal.db"
conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

cursor.execute("SELECT post_link FROM deals WHERE title LIKE '%화평동%' ORDER BY id DESC LIMIT 1")
row = cursor.fetchone()

if row:
    print(dict(row))

conn.close()
