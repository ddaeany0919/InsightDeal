import sqlite3
import sys

sys.stdout.reconfigure(encoding='utf-8')
conn = sqlite3.connect('backend/insight_deal.db')
c = conn.cursor()

c.execute("SELECT id, title, honey_score, ai_summary FROM deals WHERE source_community_id = 9 AND honey_score = 100")
for row in c.fetchall():
    print(row)

conn.close()
