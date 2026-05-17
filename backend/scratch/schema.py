import sqlite3

conn = sqlite3.connect('../insight_deal.db')
cursor = conn.cursor()

cursor.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name='deals';")
print(cursor.fetchone()[0])
