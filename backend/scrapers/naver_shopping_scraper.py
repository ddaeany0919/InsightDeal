"""
네이버 쇼핑 API 스크래퍼
공식 API를 사용하여 안정적인 상품 검색 제공
"""

import os
import logging
import requests
import json
from typing import List, Dict, Optional, Any
from datetime import datetime
from dataclasses import dataclass

logger = logging.getLogger(__name__)

@dataclass
class NaverProduct:
    """네이버 쇼핑 상품 데이터 클래스"""
    title: str
    price: int
    mall: str
    image: str
    url: str
    category1: str = ""
    category2: str = ""
    category3: str = ""
    brand: str = ""
    product_id: str = ""

class NaverShoppingScraper:
    """네이버 쇼핑 API 스크래퍼"""
    
    def __init__(self):
        self.client_id = os.getenv('NAVER_CLIENT_ID')
        self.client_secret = os.getenv('NAVER_CLIENT_SECRET')
        self.base_url = "https://openapi.naver.com/v1/search/shop.json"
        
        self.is_mock_mode = False
        if not self.client_id or not self.client_secret:
            logger.warning("⚠️ NAVER_CLIENT_ID or NAVER_CLIENT_SECRET not set. Falling back to Mock mode for Naver API.")
            self.is_mock_mode = True
        
        self.headers = {
            "X-Naver-Client-Id": self.client_id or "",
            "X-Naver-Client-Secret": self.client_secret or "",
            "User-Agent": "InsightDeal/1.0 (compatible; price comparison service)"
        }
        
        logger.info(f"✅ Naver Shopping API scraper initialized (Mock mode: {self.is_mock_mode})")
    
    def search_products(self, query: str, display: int = 20, sort: str = "sim") -> List[NaverProduct]:
        """
        네이버 쇼핑에서 상품 검색
        
        Args:
            query: 검색 키워드
            display: 검색 결과 수 (1~100)
            sort: 정렬 방식
        """
        if self.is_mock_mode:
            logger.info(f"Mocking Naver API response for: {query}")
            return [
                NaverProduct(
                    title=f"[모의데이터] {query} 기본형",
                    price=50000,
                    mall="네이버 스마트스토어",
                    image="",
                    url="https://shopping.naver.com/"
                )
            ]

        try:
            params = {
                "query": query,
                "display": min(display, 100),  # 최대 100개
                "sort": sort
            }
            
            logger.info(f"🔍 Searching Naver Shopping: query='{query}', display={display}, sort={sort}")
            
            response = requests.get(
                self.base_url,
                headers=self.headers,
                params=params,
                timeout=10
            )
            
            if response.status_code == 200:
                data = response.json()
                products = self._parse_products(data.get('items', []))
                
                logger.info(f"✅ Found {len(products)} products from Naver Shopping")
                return products
                
            elif response.status_code == 400:
                logger.error(f"❌ Bad Request (400): Invalid parameters - {response.text}")
                return []
                
            elif response.status_code == 401:
                logger.error("❌ Unauthorized (401): Invalid API credentials")
                return []
                
            elif response.status_code == 403:
                logger.error("❌ Forbidden (403): API access denied or quota exceeded")
                return []
                
            elif response.status_code == 429:
                logger.error("❌ Rate Limited (429): Too many requests")
                return []
                
            else:
                logger.error(f"❌ Naver API error: {response.status_code} - {response.text}")
                return []
                
        except requests.exceptions.Timeout:
            logger.error("❌ Naver API request timeout")
            return []
            
        except requests.exceptions.ConnectionError:
            logger.error("❌ Naver API connection error")
            return []
            
        except Exception as e:
            logger.error(f"❌ Unexpected error in Naver Shopping search: {e}")
            return []
    
    def _parse_products(self, items: List[Dict]) -> List[NaverProduct]:
        """API 응답을 NaverProduct 객체로 변환"""
        products = []
        
        for item in items:
            try:
                # HTML 태그 제거
                title = self._clean_html(item.get('title', ''))
                
                # 가격 문자열을 정수로 변환
                price_str = item.get('lprice', '0')
                try:
                    price = int(price_str) if price_str else 0
                except (ValueError, TypeError):
                    price = 0
                
                product = NaverProduct(
                    title=title,
                    price=price,
                    mall=item.get('mallName', ''),
                    image=item.get('image', ''),
                    url=item.get('link', ''),
                    category1=item.get('category1', ''),
                    category2=item.get('category2', ''),
                    category3=item.get('category3', ''),
                    brand=item.get('brand', ''),
                    product_id=item.get('productId', '')
                )
                
                products.append(product)
                
            except Exception as e:
                logger.warning(f"⚠️ Failed to parse product item: {e}")
                continue
        
        return products
    
    def _clean_html(self, text: str) -> str:
        """HTML 태그 제거"""
        import re
        if not text:
            return ""
        
        # HTML 태그 제거
        clean_text = re.sub(r'<[^>]+>', '', text)
        # HTML 엔티티 변환
        clean_text = clean_text.replace('&lt;', '<').replace('&gt;', '>').replace('&amp;', '&')
        clean_text = clean_text.replace('&quot;', '"').replace('&#39;', "'")
        
        return clean_text.strip()
    
    def get_product_categories(self, query: str) -> Dict[str, int]:
        """검색 결과의 카테고리 분포 분석"""
        products = self.search_products(query, display=50)
        
        categories = {}
        for product in products:
            if product.category1:
                categories[product.category1] = categories.get(product.category1, 0) + 1
        
        return dict(sorted(categories.items(), key=lambda x: x[1], reverse=True))
    
    def get_price_range(self, query: str) -> Dict[str, int]:
        """검색 결과의 가격 범위 분석"""
        products = self.search_products(query, display=50)
        
        if not products:
            return {"min": 0, "max": 0, "avg": 0}
        
        prices = [p.price for p in products if p.price > 0]
        
        if not prices:
            return {"min": 0, "max": 0, "avg": 0}
        
        return {
            "min": min(prices),
            "max": max(prices),
            "avg": sum(prices) // len(prices)
        }
    
    def search_by_price_range(self, query: str, min_price: int = 0, max_price: int = 999999999) -> List[NaverProduct]:
        """가격 범위로 상품 검색"""
        all_products = self.search_products(query, display=100)
        
        filtered_products = [
            product for product in all_products
            if min_price <= product.price <= max_price
        ]
        
        logger.info(f"💰 Price filter: {len(filtered_products)}/{len(all_products)} products in range {min_price:,}~{max_price:,}원")
        
        return filtered_products

# 사용 예시 및 테스트 함수
def test_naver_shopping_scraper():
    """네이버 쇼핑 API 스크래퍼 테스트"""
    try:
        scraper = NaverShoppingScraper()
        
        # 기본 검색 테스트
        products = scraper.search_products("아이폰", display=5)
        
        print(f"🔍 검색 결과: {len(products)}개 상품")
        
        for i, product in enumerate(products[:3], 1):
            print(f"\n{i}. {product.title}")
            print(f"   💰 가격: {product.price:,}원")
            print(f"   🏪 쇼핑몰: {product.mall}")
            print(f"   🔗 URL: {product.url[:50]}...")
        
        # 가격 범위 분석
        price_range = scraper.get_price_range("아이폰")
        print(f"\n💰 가격 범위 분석:")
        print(f"   최저가: {price_range['min']:,}원")
        print(f"   최고가: {price_range['max']:,}원")
        print(f"   평균가: {price_range['avg']:,}원")
        
        # 카테고리 분석
        categories = scraper.get_product_categories("아이폰")
        print(f"\n📂 카테고리 분포:")
        for category, count in list(categories.items())[:5]:
            print(f"   {category}: {count}개")
            
    except Exception as e:
        print(f"❌ 테스트 실패: {e}")

if __name__ == "__main__":
    test_naver_shopping_scraper()