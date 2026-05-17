import requests
from bs4 import BeautifulSoup
import sys

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
}

def check_hot_deals(page=1):
    url = f"https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu&page={page}"
    resp = requests.get(url, headers=headers)
    resp.encoding = 'euc-kr'
    soup = BeautifulSoup(resp.text, 'html.parser')

    rows = soup.select('tr.baseList, tr.list1, tr.list0')
    count = 0
    for row in rows:
        title_el = row.select_one('a.baseList-title') or row.select_one('.list_title') or row.select_one('font')
        if not title_el: continue
        title = title_el.get_text(strip=True)
        img = row.select_one('img[src*="hot_icon2.jpg"]')
        if img:
            print(f"[PAGE {page}] ACTUAL HOT DEAL: {title}")
            count += 1
    return count

total = 0
total += check_hot_deals(1)
total += check_hot_deals(2)
print(f"Total actual hot deals on first 2 pages: {total}")
