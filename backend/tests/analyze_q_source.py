
from bs4 import BeautifulSoup
import os

source_file = "quasarzone_source.html"
if not os.path.exists(source_file):
    print(f"File {source_file} not found.")
    exit(1)

with open(source_file, "r", encoding="utf-8") as f:
    soup = BeautifulSoup(f.read(), "html.parser")

rows = soup.select('tbody tr')
print(f"Total rows found: {len(rows)}")

for i, row in enumerate(rows):
    notice_tag = row.select_one('td.num span.icon-notice')
    if not notice_tag:
        print(f"--- First Non-Notice Row (Index {i}) ---")
        print(row.prettify())
        break
