import sqlite3
import sys

sys.stdout.reconfigure(encoding='utf-8')
conn = sqlite3.connect('backend/insight_deal.db')
c = conn.cursor()

print("=== All Deals with honey_score = 100 ===")
c.execute("""
    SELECT d.id, c.name, d.title, d.post_link, d.ai_summary 
    FROM deals d 
    JOIN communities c ON d.source_community_id = c.id 
    WHERE d.honey_score = 100 
    ORDER BY d.id DESC
""")

for row in c.fetchall():
    print(row)

conn.close()
