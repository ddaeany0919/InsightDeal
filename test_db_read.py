import pymysql
conn = pymysql.connect(host='localhost', user='root', password='123', database='insightdeal', charset='utf8mb4')
with conn.cursor() as cur:
    cur.execute("SELECT title, base_product_name FROM deals WHERE title LIKE '%삼양라면%' ORDER BY id DESC LIMIT 15")
    for row in cur.fetchall():
        print('T:', row[0], ' | B:', row[1])
