import sqlite3; conn=sqlite3.connect('insightdeal.db'); cursor=conn.cursor(); cursor.execute("SELECT id, title, price, is_hot, url FROM deals WHERE title LIKE '%MX Keys%'"); print(cursor.fetchall())
