import requests
from bs4 import BeautifulSoup
import re

res = requests.get('https://bbasak.com:443/bbs/board.php?bo_table=bbasak2&wr_id=28658')
soup = BeautifulSoup(res.text, 'html.parser')

print('--- ALL A TAGS ---')
for a in soup.select('a'):
    href = a.get('href')
    if href and href.startswith('http'):
        print("TAG:", href)

print('--- TEXT URLS ---')
board_view = soup.select_one('#board_view')
body_text_raw = board_view.get_text(separator=' ') if board_view else ''
urls = re.findall(r'https?://[^\s\"\'<>]+', body_text_raw)
for u in urls:
    print("TEXT:", u)
