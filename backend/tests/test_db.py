import sqlite3
conn = sqlite3.connect('backend/insight_deal.db')
conn.row_factory = sqlite3.Row
c = conn.cursor()

print('--- DATA SANITY CHECK REPORT ---')

c.execute('SELECT category, COUNT(*) as cnt FROM deals GROUP BY category ORDER BY cnt DESC')
print('\n[1] 카테고리 분포:')
for row in c.fetchall():
    print(f"  - {row['category']}: {row['cnt']}건")

c.execute('SELECT title, price, category, source_community_id FROM deals WHERE price > 10000000 LIMIT 5')
high_prices = c.fetchall()
if high_prices:
    print('\n[2] 비정상적인 초고가 딜 (Price > 10,000,000):')
    for row in high_prices:
        print(f"  - [{row['source_community_id']}] {row['title']} : {row['price']}원")
else:
    print('\n[2] 초고가 이상치 없음 (No deals > 10,000,000원)')

c.execute('''
    SELECT 
        SUM(CASE WHEN image_url IS NULL OR image_url = '' THEN 1 ELSE 0 END) as missing_img,
        SUM(CASE WHEN content_html IS NULL OR content_html = '' THEN 1 ELSE 0 END) as missing_content,
        SUM(CASE WHEN ecommerce_link IS NULL OR ecommerce_link = '' THEN 1 ELSE 0 END) as missing_link
    FROM deals
''')
missing = c.fetchone()
print('\n[3] 누락된 핵심 데이터 수량:')
print(f"  - 이미지 누락: {missing['missing_img']}건")
print(f"  - 본문/설명 누락: {missing['missing_content']}건")
print(f"  - 구매링크 누락: {missing['missing_link']}건")

c.execute("SELECT title, price, source_community_id FROM deals WHERE category = '적립' AND price > 100")
wrong_points = c.fetchall()
if wrong_points:
    print('\n[4] 적립 카테고리 규칙 위반 (가격 100원 초과인데 적립으로 분류됨):')
    for row in wrong_points:
        print(f"  - [{row['source_community_id']}] {row['title']} : {row['price']}원")
else:
    print('\n[4] 적립 카테고리 규칙(100원 이하) 모두 정상 준수됨!')

c.execute('SELECT SUM(CASE WHEN is_closed = 1 THEN 1 ELSE 0 END) as closed_cnt, COUNT(*) as total FROM deals')
closed_stats = c.fetchone()
print(f"\n[5] 품절/종료 처리 현황: 전체 {closed_stats['total']}건 중 {closed_stats['closed_cnt']}건(약 {round(closed_stats['closed_cnt']/closed_stats['total']*100, 1)}%) 품절/종료 처리됨.")

conn.close()
