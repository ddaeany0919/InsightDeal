
import requests
from bs4 import BeautifulSoup
import sys

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}

urls = [
    "https://quasarzone.com/bbs/qb_saleinfo",
    "https://quasarzone.com/bbs/qb_saleinfo/views/1922158",
    "https://www.fmkorea.com/hotdeal"
]

for url in urls:
    print(f"\n--- Testing: {url} ---")
    try:
        response = requests.get(url, headers=headers, timeout=10)
        print(f"Status Code: {response.status_code}")
        if response.status_code == 200:
            soup = BeautifulSoup(response.text, 'html.parser')
            # Quasarzone List
            if "qb_saleinfo" in url and "views" not in url:
                rows = soup.select('tbody tr')
                print(f"Rows found: {len(rows)}")
                for i, row in enumerate(rows):
                    num_val = row.select_one('td.num').get_text(strip=True) if row.select_one('td.num') else "N/A"
                    is_notice = "Yes" if row.select_one('td.num span') else "No"
                    print(f"Row {i}: Num={num_val}, IsNotice={is_notice}, HTML={str(row)[:200]}")
            # Quasarzone Detail
            elif "views" in url:
                print(f"Detail Title: {soup.title.string}")
                content = soup.select_one('.view-content') or soup.select_one('.board-view-content')
                if content:
                    print(f"Content found! Snippet: {str(content)[:500]}")
                else:
                    print("Content NOT found with current selectors.")
                    print(f"Body snippet: {str(soup.body)[:500]}")
        else:
            print(f"Response Content (first 500 chars): {response.text[:500]}")
    except Exception as e:
        print(f"Error: {e}")
