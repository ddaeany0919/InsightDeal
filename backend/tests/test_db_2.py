import sqlite3
conn = sqlite3.connect('backend/insight_deal.db')
conn.row_factory = sqlite3.Row
c = conn.cursor()

c.execute('SELECT title, price, category, source_community_id FROM deals WHERE CAST(price AS INTEGER) > 10000000 LIMIT 5')
high_prices = c.fetchall()
if high_prices:
    for row in high_prices:
        print(f"HIGH PRICE: {row['title']} : {row['price']}원")
else:
    print('No high prices found.')

c.execute("SELECT title, price, source_community_id FROM deals WHERE category = '적립' AND CAST(price AS INTEGER) > 100")
wrong_points = c.fetchall()
if wrong_points:
    for row in wrong_points:
        print(f"WRONG POINT: {row['title']} : {row['price']}원")
else:
    print('All point categories are strictly <= 100원.')

conn.close()
