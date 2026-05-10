import sqlite3
import sys
sys.stdout.reconfigure(encoding='utf-8')
conn = sqlite3.connect('backend/insight_deal.db')
c = conn.cursor()
c.execute("SELECT id, title, source_community_id, honey_score, ai_summary FROM deals WHERE title LIKE '%삼양라면%'")
for row in c.fetchall():
    print(row)
conn.close()
