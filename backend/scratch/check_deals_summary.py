import sqlite3
import os

db_path = os.path.join(os.path.dirname(__file__), "..", "insight_deal.db")
print("DB Path:", os.path.abspath(db_path))

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# 1. 커뮤니티 테이블 조회
print("\n--- Communities ---")
cursor.execute("SELECT id, name, display_name, base_url FROM communities")
communities = cursor.fetchall()
for c in communities:
    print(c)

# 2. 최근 딜 개수와 커뮤니티별 개수
print("\n--- Deal count by community ---")
cursor.execute("""
    SELECT c.name, c.display_name, COUNT(d.id), MIN(d.indexed_at), MAX(d.indexed_at)
    FROM deals d
    JOIN communities c ON d.source_community_id = c.id
    GROUP BY c.name
""")
rows = cursor.fetchall()
for row in rows:
    print(row)

# 3. 최근 10개 딜의 상세 정보
print("\n--- Recent 10 Deals ---")
cursor.execute("""
    SELECT d.id, c.display_name, d.title, d.indexed_at, d.post_link
    FROM deals d
    JOIN communities c ON d.source_community_id = c.id
    ORDER BY d.indexed_at DESC
    LIMIT 10
""")
for row in cursor.fetchall():
    print(row)

conn.close()
