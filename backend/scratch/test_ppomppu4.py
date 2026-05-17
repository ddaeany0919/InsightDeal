import requests
from bs4 import BeautifulSoup
import sys, codecs
sys.stdout = codecs.getwriter('utf-8')(sys.stdout.detach())
url = 'https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu'
headers = {'User-Agent': 'Mozilla/5.0'}
res = requests.get(url, headers=headers)
soup = BeautifulSoup(res.text, 'html.parser')
for img in soup.select('img'):
    src = img.get('src')
    if src and 'hot' in src.lower():
        print(src)
