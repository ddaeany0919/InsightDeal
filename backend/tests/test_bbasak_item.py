import httpx, re
from bs4 import BeautifulSoup
r = httpx.get("https://bbasak.com:443/bbs/board.php?bo_table=bbasak3&wr_id=12186&device=pc", verify=False)
soup = BeautifulSoup(r.text, 'html.parser')
body = soup.select_one('#board_view')
print("--- IMAGES ---")
for img in soup.select('#board_view img'):
    print(img.get('src'))
print("--- TEXT ---")
print(body.get_text(separator=' ')[:200] if body else "")
