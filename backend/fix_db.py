import sqlite3

db_path = r"c:\Users\kth00\StudioProjects\InsightDeal\backend\insight_deal.db"
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# 뽐뿌의 경우 게시글 번호가 보통 70만, 80만 대입니다.
# 다른 커뮤니티에도 영향이 없도록, 비정상적으로 높은 조회수를 가진 레코드를 초기화합니다.
# 10만 이상의 조회수를 0으로 초기화하고, 허니 스코어도 0으로 초기화
cursor.execute("UPDATE deals SET view_count = 0, honey_score = 0 WHERE view_count > 100000")
affected = cursor.rowcount

conn.commit()
conn.close()

print(f"Fixed {affected} deals with corrupted view_counts.")
