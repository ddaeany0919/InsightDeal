import os
import requests
import logging

NAVER_CLIENT_ID = os.getenv('NAVER_CLIENT_ID')
NAVER_CLIENT_SECRET = os.getenv('NAVER_CLIENT_SECRET')

class NaverShoppingScraper:
    @staticmethod
    def search_products(keyword, display=30):
        """
        네이버 쇼핑 API로 상품 검색
        
        개선사항:
        - 정확도순 정렬 (sort=sim)
        - 30개 가져오기
        - 가격순 정렬은 우리가 직접 처리
        """
        url = "https://openapi.naver.com/v1/search/shop.json"
        headers = {
            "X-Naver-Client-Id": NAVER_CLIENT_ID,
            "X-Naver-Client-Secret": NAVER_CLIENT_SECRET,
        }
        params = {
            "query": keyword,
            "display": display,  # 30개 가져오기
            "sort": "sim"  # 정확도순
        }
        
        try:
            response = requests.get(url, headers=headers, params=params, timeout=10)
            if response.status_code != 200:
                logging.error(f"[NAVER_API] HTTP 에러: {response.status_code}")
                return []
            
            items = response.json().get("items", [])
            logging.info(f"[NAVER_API] {len(items)}개 상품 검색 성공")
            
            results = []
            for item in items:
                results.append({
                    "title": item.get("title"),
                    "link": item.get("link"),
                    "price": int(item.get("lprice", 0)),
                    "mall": item.get("mallName"),
                    "image": item.get("image")
                })
            
            # 가격순 정렬 (정확도 높은 것들 중에서)
            results_sorted = sorted(results, key=lambda x: x['price'])
            logging.info(f"[NAVER_API] 가격순 정렬 완료, 최저가={results_sorted[0]['price']}원")
            
            return results_sorted
            
        except Exception as e:
            logging.error(f"[NAVER_API] 예외 발생: {str(e)}", exc_info=True)
            return []
