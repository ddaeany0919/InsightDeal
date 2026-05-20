import sqlite3

db_path = r"C:\Users\kth00\StudioProjects\InsightDeal\backend\insight_deal.db"
conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# 올인원 키워드가 들어간 딜 조회
cursor.execute("SELECT id, title, category FROM deals WHERE title LIKE '%올인원%'")
rows = cursor.fetchall()

print(f"Total '올인원' deals found: {len(rows)}")
for row in rows[:15]:
    print(f"ID: {row['id']} | Cat: {row['category']} | Title: {row['title']}")

conn.close()
