import requests
from bs4 import BeautifulSoup
import re

res = requests.get('https://www.fmkorea.com/index.php?mid=hotdeal&search_keyword=풀무원&search_target=title', headers={'User-Agent': 'Mozilla/5.0'})
soup = BeautifulSoup(res.text, 'html.parser')

row = soup.select_one('li.li.li_best2_pop0.li_best2_hotdeal0')
if not row:
    row = soup.select_one('li.li') # just get the first one

info_div = row.select_one('.hotdeal_info')
extracted_price = 0
extracted_currency = 'KRW'

if info_div:
    spans = info_div.select('span')
    print("Found spans:", len(spans))
    for span in spans:
        text = span.get_text(strip=True)
        print(f"Span text: {text}")
        if '가격' in text:
            strong = span.select_one('.strong')
            if strong: 
                price_text = strong.get_text(strip=True)
                print(f"Price text: {price_text}")
                p_match = re.search(r'([0-9,]+(?:\.[0-9]+)?)', price_text)
                if p_match:
                    try:
                        extracted_price = int(float(p_match.group(1).replace(',', '')))
                        print(f"Extracted price: {extracted_price}")
                    except Exception as e: 
                        print("Exception:", e)
                else:
                    print("No regex match for price_text!")

print("Final Extracted Price:", extracted_price)
