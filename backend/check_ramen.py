import sqlite3
import sys
import io

def get_ramen():
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    conn = sqlite3.connect('insight_deal.db')
    cursor = conn.cursor()
    cursor.execute("SELECT id, title, community_id, price FROM deals WHERE title LIKE '%삼양라면%';")
    rows = cursor.fetchall()
    for row in rows:
        print(row)

if __name__ == "__main__":
    get_ramen()
