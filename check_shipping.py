import sqlite3
import sys
sys.stdout.reconfigure(encoding='utf-8')
conn = sqlite3.connect('backend/insight_deal.db')
c = conn.cursor()
c.execute("SELECT id, title, shipping_fee FROM deals ORDER BY id DESC LIMIT 10")
for row in c.fetchall():
    print(row)
conn.close()
