import sqlite3
import sys

sys.stdout.reconfigure(encoding='utf-8')
conn = sqlite3.connect('backend/insight_deal.db')
c = conn.cursor()

# Check current Samyang deals
c.execute("SELECT id, title, honey_score, ai_summary FROM deals WHERE title LIKE '%삼양라면%'")
print("Before update:")
for row in c.fetchall():
    print(row)

# Update score for Samyang deals that were incorrectly given 100 or 99
c.execute("""
    UPDATE deals 
    SET honey_score = 60, ai_summary = REPLACE(ai_summary, '🔥 [커뮤니티 인증 핫딜] ', '')
    WHERE title LIKE '%삼양라면%' AND honey_score >= 90
""")

conn.commit()

# Check after update
c.execute("SELECT id, title, honey_score, ai_summary FROM deals WHERE title LIKE '%삼양라면%'")
print("\nAfter update:")
for row in c.fetchall():
    print(row)

conn.close()
