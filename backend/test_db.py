
import sqlite3
conn = sqlite3.connect('insight_deal.db')
c = conn.cursor()
c.execute('UPDATE deals SET is_super_hotdeal=0 WHERE title LIKE \'%삼성 인버터 1등급 제습기%\'')
conn.commit()
print('Updated items:', c.rowcount)

