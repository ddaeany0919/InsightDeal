import sqlite3
from datetime import datetime, timedelta

conn = sqlite3.connect('insight_deal.db')
c = conn.cursor()

two_hours_ago = (datetime.utcnow() - timedelta(hours=2)).isoformat()
print("2 hours ago:", two_hours_ago)

c.execute("SELECT COUNT(*) FROM deals")
print("Total deals:", c.fetchone()[0])

c.execute("SELECT COUNT(*) FROM deals WHERE indexed_at >= ?", (two_hours_ago,))
print("Deals >= 2 hours ago:", c.fetchone()[0])

c.execute("SELECT COUNT(*) FROM deals WHERE honey_score >= 10")
print("Deals with honey_score >= 10:", c.fetchone()[0])

c.execute("SELECT COUNT(*) FROM deals WHERE is_closed = 0")
print("Deals not closed:", c.fetchone()[0])

c.execute("SELECT COUNT(*) FROM deals WHERE (indexed_at >= ? OR honey_score >= 10) AND is_closed = 0", (two_hours_ago,))
print("Filter result:", c.fetchone()[0])

c.execute("SELECT indexed_at FROM deals LIMIT 5")
print("Sample indexed_at:", c.fetchall())
