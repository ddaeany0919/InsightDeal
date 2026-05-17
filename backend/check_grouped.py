import sqlite3

conn = sqlite3.connect('insight_deal.db')
cursor = conn.cursor()

# Get table info
cursor.execute("PRAGMA table_info(deals);")
columns = cursor.fetchall()
col_names = [c[1] for c in columns]
print(f"Columns: {col_names}")

# Find deals that are grouped
query = """
SELECT id, group_id, title, price, is_closed
FROM deals
WHERE group_id IS NOT NULL
ORDER BY group_id;
"""

cursor.execute(query)
rows = cursor.fetchall()

print("Grouped deals in DB:")
groups = {}
for row in rows:
    gid = row[1]
    if gid not in groups:
        groups[gid] = []
    groups[gid].append(row)

for gid, items in groups.items():
    if len(items) > 1:
        print(f"Group {gid}:")
        for item in items:
            print(f"  {item}")
