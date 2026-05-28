import sqlite3
import sys

sys.stdout.reconfigure(encoding='utf-8')

conn = sqlite3.connect('backend/insight_deal.db')
cursor = conn.cursor()

# 1. 스프라이트가 포함된 모든 딜 조회
print("=== 1. 스프라이트 검색 결과 ===")
cursor.execute("SELECT id, title, price, indexed_at FROM deals WHERE title LIKE '%스프라이트%' ORDER BY indexed_at DESC")
rows = cursor.fetchall()
for r in rows:
    print(f"ID: {r[0]} | Title: {r[1]} | Price: {r[2]} | Date: {r[3]}")

# 2. 게이밍 완본체 관련 딜 상세 조회
print("\n=== 2. 게이밍 완본체 검색 결과 ===")
cursor.execute("SELECT id, title, price, shipping_fee, post_link FROM deals WHERE title LIKE '%완본체%' OR price > 100000000 ORDER BY id DESC LIMIT 10")
rows_pc = cursor.fetchall()
for r in rows_pc:
    print(f"ID: {r[0]} | Title: {r[1]} | Price: {r[2]} | Shipping: {r[3]} | Link: {r[4]}")

conn.close()
