import os
import requests

NAVER_CLIENT_ID = os.getenv('NAVER_CLIENT_ID')
NAVER_CLIENT_SECRET = os.getenv('NAVER_CLIENT_SECRET')

class NaverShoppingScraper:
    @staticmethod
    def search_products(keyword, display=5):
        url = "https://openapi.naver.com/v1/search/shop.json"
        headers = {
            "X-Naver-Client-Id": NAVER_CLIENT_ID,
            "X-Naver-Client-Secret": NAVER_CLIENT_SECRET,
        }
        params = {
            "query": keyword,
            "display": display,
            "sort": "asc"
        }
        response = requests.get(url, headers=headers, params=params)
        if response.status_code != 200:
            return []
        items = response.json().get("items", [])
        results = []
        for item in items:
            results.append({
                "title": item.get("title"),
                "link": item.get("link"),
                "price": int(item.get("lprice", 0)),
                "mall": item.get("mallName"),
                "image": item.get("image")
            })
        return results
