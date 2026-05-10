import sqlite3

conn = sqlite3.connect('backend/insight_deal.db')
cursor = conn.cursor()
cursor.execute("DELETE FROM deals WHERE post_link LIKE '%id=oversea%'")
cursor.execute("DELETE FROM deals WHERE post_link LIKE '%id=freeboard%'")
conn.commit()
print('Deleted garbage deals successfully.')
