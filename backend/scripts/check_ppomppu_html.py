import requests
from bs4 import BeautifulSoup

url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu"
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
}
resp = requests.get(url, headers=headers)
resp.encoding = 'euc-kr'
soup = BeautifulSoup(resp.text, 'html.parser')

rows = soup.select('tr.baseList, tr.list1, tr.list0')
for row in rows:
    title_el = row.select_one('a.baseList-title') or row.select_one('.list_title') or row.select_one('font')
    if not title_el: continue
    title = title_el.get_text(strip=True)
    if '육회' in title or '극세사' in title or 'G마켓' in title or '소소한' in title or '토스쇼핑' in title:
        print(f"FOUND: {title}")
        img = row.select_one('img[src*="hot_icon2.jpg"]')
        print(f"Has hot_icon2.jpg? {img is not None}")
        
        # Let's print all imgs in this row to see what icons are actually there
        imgs = row.select('img')
        for i in imgs:
            print(f"  Img: {i.get('src')}")
