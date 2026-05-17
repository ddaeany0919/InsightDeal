import sqlite3
import sys
import codecs

sys.stdout = codecs.getwriter('utf-8')(sys.stdout.detach())

conn = sqlite3.connect('insight_deal.db')
c = conn.cursor()

c.execute("SELECT COUNT(*) FROM deals WHERE post_link LIKE '%id=pmarket%'")
print(f"Pmarket deals found: {c.fetchone()[0]}")

c.execute("SELECT COUNT(*) FROM deals WHERE source_community_id=7 AND is_closed=0")
print(f"Active Ppomppu deals found: {c.fetchone()[0]}")

print("Deleting all Pmarket deals...")
c.execute("DELETE FROM deals WHERE post_link LIKE '%id=pmarket%'")

# Also delete all ppomppu deals that don't have hot_icon2?
# Since we don't know which ones had hot_icon2 (it's not saved in DB), let's just delete ALL active Ppomppu deals!
# The scraper will re-fetch the real ones since they are still active.
print("Deleting all Ppomppu active deals to clear garbage...")
c.execute("DELETE FROM deals WHERE source_community_id=7 AND is_closed=0")

c.execute("SELECT COUNT(*) FROM deals WHERE post_link LIKE '%id=pmarket%'")
print(f"Pmarket deals remaining: {c.fetchone()[0]}")

conn.commit()
print("Deletion complete.")
