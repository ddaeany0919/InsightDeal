
import sqlite3
conn = sqlite3.connect('backend/insight_deal.db')
c = conn.cursor()
c.execute('SELECT id, title, price, community_id FROM deals WHERE title LIKE \'%ÇÞ¹Ý%\' LIMIT 5;')
for row in c.fetchall():
    print(row)

