import sqlite3
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse

def canonicalize_url(url):
    parsed = urlparse(url)
    qs = parse_qs(parsed.query)
    if 'page' in qs:
        qs.pop('page')
    if 'divpage' in qs:
        qs.pop('divpage')
    clean_query = urlencode(qs, doseq=True)
    return urlunparse(parsed._replace(query=clean_query))

conn = sqlite3.connect('backend/insight_deal.db')
c = conn.cursor()

c.execute("SELECT id, post_link, title, ai_summary FROM deals")
rows = c.fetchall()

url_title_map = {}
duplicates = []

for row in rows:
    deal_id, post_link, title, ai_summary = row
    clean_url = canonicalize_url(post_link)
    
    key = (clean_url, title)
    if key in url_title_map:
        duplicates.append(deal_id)
    else:
        url_title_map[key] = deal_id

print(f"Found {len(duplicates)} duplicates based on canonical URL and title.")

# Delete exact duplicates
for dup_id in duplicates:
    c.execute("DELETE FROM deals WHERE id = ?", (dup_id,))

# Update URLs to canonical ones
c.execute("SELECT id, post_link FROM deals")
remaining_rows = c.fetchall()
for r in remaining_rows:
    deal_id, post_link = r
    clean_url = canonicalize_url(post_link)
    if clean_url != post_link:
        c.execute("UPDATE deals SET post_link = ? WHERE id = ?", (clean_url, deal_id))

# Now, handle the AI split + fallback duplicate issue.
# For a given canonical URL, if there are multiple items, and one of them is the "original raw title" with `(AI 요약 대체)`, 
# we should probably delete it if there are valid split items for the same URL!
c.execute("SELECT post_link, COUNT(id) FROM deals GROUP BY post_link HAVING COUNT(id) > 1")
multi_item_urls = c.fetchall()

deleted_fallback_count = 0
for row in multi_item_urls:
    url = row[0]
    c.execute("SELECT id, title, ai_summary FROM deals WHERE post_link = ?", (url,))
    items = c.fetchall()
    
    has_split = any("✅ (AI 요약 대체)" not in (item[2] or "") for item in items)
    
    if has_split and len(items) > 1:
        for item in items:
            item_id, item_title, ai_summary = item
            if "✅ (AI 요약 대체)" in (ai_summary or ""):
                c.execute("DELETE FROM deals WHERE id = ?", (item_id,))
                deleted_fallback_count += 1

print(f"Deleted {deleted_fallback_count} raw fallback duplicates for split deals.")

conn.commit()
conn.close()
