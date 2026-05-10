import sqlite3
import sys

sys.stdout.reconfigure(encoding='utf-8')
conn = sqlite3.connect('backend/insight_deal.db')
c = conn.cursor()

c.execute("""
    SELECT c.name, COUNT(*)
    FROM deals d 
    JOIN communities c ON d.source_community_id = c.id 
    WHERE d.honey_score = 100 
    GROUP BY c.name
    ORDER BY COUNT(*) DESC
""")

print("=== 100 point deals by community ===")
for row in c.fetchall():
    print(f"{row[0]}: {row[1]} deals")

conn.close()
