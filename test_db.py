import sqlite3

conn = sqlite3.connect('backend/insight_deal.db')
cur = conn.cursor()
cur.execute("SELECT id, title, is_closed, post_link FROM deals WHERE post_link LIKE '%9832607753%'")
rows = cur.fetchall()
for r in rows:
    print(r)
