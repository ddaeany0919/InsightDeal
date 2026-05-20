import sqlite3
import sys

sys.stdout.reconfigure(encoding='utf-8')

conn = sqlite3.connect('backend/insight_deal.db')
cursor = conn.cursor()

# 오분류 의심 ID들 상세 조회
ids = [7965, 8337, 8194, 7910, 8274]
cursor.execute(f"SELECT id, title, category, price FROM deals WHERE id IN ({','.join(map(str, ids))})")
rows = cursor.fetchall()
print("=== 오분류 상세 항목 ===")
for r in rows:
    print(f"ID: {r[0]}, Title: {r[1]}, Category: {r[2]}, Price: {r[3]}")

conn.close()

