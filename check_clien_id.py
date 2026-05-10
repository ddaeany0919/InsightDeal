import sqlite3
import sys

sys.stdout.reconfigure(encoding='utf-8')
conn = sqlite3.connect('backend/insight_deal.db')
c = conn.cursor()

c.execute("SELECT id, name FROM communities WHERE name = 'clien'")
print(c.fetchone())

conn.close()
