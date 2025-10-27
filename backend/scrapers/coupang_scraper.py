"""
🛒 CoupangScraper - 쿠팡 전용 스크래퍼

폴센트 방식 가격 추적을 위한 쿠팡 스크래핑
- 상품 검색: 키워드 → 상품 리스트
- 상품 상세: URL → 가격/정보 추출  
- 가격만 조회: 빠른 업데이트용
- 로켓배송/로켓프레시 구분
"""

import re
import json
import asyncio
from typing import List, Optional
from datetime import datetime
from bs4 import BeautifulSoup

from .base_scraper import BaseScraper, ProductInfo

class CoupangScraper(BaseScraper):
    """🛒 쿠팡 전용 스크래퍼"""
    
    def __init__(self):
        super().__init__("coupang")
        self.base_url = "https://www.coupang.com"
        self.search_url = "https://www.coupang.com/np/search"
        
    def _extract_product_id(self, url: str) -> Optional[str]:
        """🔍 쿠팡 URL에서 상품 ID 추출"""
        # 예: https://coupang.com/vp/products/123456789
        match = re.search(r'/products/(\d+)', url)
        return match.group(1) if match else None
    
    def _parse_price_element(self, soup: BeautifulSoup) -> int:
        """💰 쿠팡 페이지에서 가격 추출"""
        price_selectors = [
            '.total-price strong',
            '.price-value', 
            '.base-price',
            '.prod-price .total',
            '[data-product-price]'
        ]
        
        for selector in price_selectors:
            element = soup.select_one(selector)
            if element:
                price_text = element.get_text(strip=True)
                price = self._clean_price(price_text)
                if price > 0:
                    return price
                    
        return 0
    
    def _parse_original_price(self, soup: BeautifulSoup) -> int:
        """💸 원가 (할인 전 가격) 추출"""
        selectors = [
            '.origin-price',
            '.base-price.line-through',
            '.prod-origin-price'
        ]
        
        for selector in selectors:
            element = soup.select_one(selector)
            if element:
                return self._clean_price(element.get_text(strip=True))
        return 0
    
    def _parse_shipping_info(self, soup: BeautifulSoup) -> tuple:
        """🚚 배송비 정보 (무료배송 여부, 로켓배송 여부)"""
        shipping_fee = 0
        is_rocket = False
        
        # 로켓배송 확인
        rocket_elements = soup.select('.rocket, .badge-rocket, [alt*="로켓"]')
        is_rocket = len(rocket_elements) > 0
        
        # 배송비 확인
        shipping_elements = soup.select('.shipping, .delivery-fee')
        for elem in shipping_elements:
            text = elem.get_text(strip=True)
            if '무료' in text or 'FREE' in text.upper():
                shipping_fee = 0
                break
            else:
                fee = self._clean_price(text)
                if fee > 0:
                    shipping_fee = fee
                    
        return shipping_fee, is_rocket
    
    async def search_product(self, product_name: str, limit: int = 10) -> List[ProductInfo]:
        """🔍 쿠팡에서 상품 검색"""
        try:
            # 검색 파라미터
            params = {
                'q': product_name,
                'channel': 'user',
                'component': '',
                'eventCategory': 'SRP',
                'eventAction': 'search',
                'sorter': 'scoreDesc',  # 인기순
                'listSize': min(limit, 36)  # 쿠팡 최대 36개
            }
            
            # 검색 요청
            url = f"{self.search_url}?" + "&".join([f"{k}={v}" for k, v in params.items()])
            html = await self._make_request(url)
            
            if not html:
                return []
            
            soup = BeautifulSoup(html, 'html.parser')
            products = []
            
            # 상품 리스트 파싱
            product_items = soup.select('.search-product')[:limit]
            
            for item in product_items:
                try:
                    # 상품명
                    title_elem = item.select_one('.name')
                    if not title_elem:
                        continue
                    title = self._clean_product_name(title_elem.get_text(strip=True))
                    
                    # 상품 URL
                    link_elem = item.select_one('a')
                    if not link_elem:
                        continue
                    product_url = self.base_url + link_elem.get('href', '')
                    
                    # 현재 가격
                    price_elem = item.select_one('.price-value')
                    current_price = self._clean_price(price_elem.get_text(strip=True)) if price_elem else 0
                    
                    if current_price == 0:
                        continue
                    
                    # 원가 (할인 전)
                    original_elem = item.select_one('.base-price')
                    original_price = self._clean_price(original_elem.get_text(strip=True)) if original_elem else current_price
                    
                    # 할인율 계산
                    discount_rate = 0
                    if original_price > current_price:
                        discount_rate = int((original_price - current_price) / original_price * 100)
                    
                    # 이미지
                    img_elem = item.select_one('img')
                    image_url = img_elem.get('src', '') if img_elem else ''
                    if image_url.startswith('//'):
                        image_url = 'https:' + image_url
                    
                    # 로켓배송 여부
                    is_rocket = len(item.select('.badge-rocket, [alt*="로켓"]')) > 0
                    shipping_fee = 0 if is_rocket else 2500  # 로켓배송이면 무료
                    
                    # 평점 (옵션)
                    rating = 0.0
                    rating_elem = item.select_one('.rating')
                    if rating_elem:
                        rating_text = rating_elem.get('data-rating', '0')
                        try:
                            rating = float(rating_text)
                        except:
                            rating = 0.0
                    
                    # 리뷰 수
                    review_count = 0
                    review_elem = item.select_one('.rating-total-count')
                    if review_elem:
                        review_count = self._clean_price(review_elem.get_text(strip=True))
                    
                    product = ProductInfo(
                        platform="coupang",
                        product_name=title,
                        current_price=current_price,
                        original_price=original_price,
                        discount_rate=discount_rate,
                        product_url=product_url,
                        image_url=image_url,
                        shipping_fee=shipping_fee,
                        rating=rating,
                        review_count=review_count,
                        seller_name="쿠팡" if is_rocket else "마켓플레이스",
                        is_available=True,
                        updated_at=datetime.now()
                    )
                    
                    products.append(product)
                    
                except Exception as e:
                    self.logger.warning(f"⚠️ 쿠팡 상품 파싱 오류: {e}")
                    continue
            
            self.logger.info(f"✅ 쿠팡 검색 완료: {len(products)}개 상품")
            return products
            
        except Exception as e:
            self.logger.error(f"❌ 쿠팡 검색 실패: {e}")
            return []
    
    async def get_product_detail(self, product_url: str) -> Optional[ProductInfo]:
        """📋 쿠팡 상품 상세 정보"""
        try:
            if not self.is_valid_url(product_url):
                self.logger.warning(f"⚠️ 잘못된 쿠팡 URL: {product_url}")
                return None
                
            html = await self._make_request(product_url)
            if not html:
                return None
                
            soup = BeautifulSoup(html, 'html.parser')
            
            # 상품명
            title_selectors = [
                '.prod-buy-header__title',
                'h2.prod-title',
                '.product-title h1'
            ]
            title = ""
            for selector in title_selectors:
                elem = soup.select_one(selector)
                if elem:
                    title = self._clean_product_name(elem.get_text(strip=True))
                    break
                    
            if not title:
                self.logger.warning("⚠️ 쿠팡 상품명 추출 실패")
                return None
            
            # 가격 정보
            current_price = self._parse_price_element(soup)
            if current_price == 0:
                self.logger.warning("⚠️ 쿠팡 가격 추출 실패")
                return None
                
            original_price = self._parse_original_price(soup) or current_price
            
            # 할인율
            discount_rate = 0
            if original_price > current_price:
                discount_rate = int((original_price - current_price) / original_price * 100)
            
            # 배송 정보
            shipping_fee, is_rocket = self._parse_shipping_info(soup)
            
            # 이미지
            img_elem = soup.select_one('.prod-image__detail img, .product-image img')
            image_url = img_elem.get('src', '') if img_elem else ''
            if image_url and image_url.startswith('//'):
                image_url = 'https:' + image_url
            
            # 평점
            rating = 0.0
            rating_elem = soup.select_one('.rating-star-num, [data-rating]')
            if rating_elem:
                try:
                    rating = float(rating_elem.get_text(strip=True).replace('점', ''))
                except:
                    pass
            
            # 리뷰 수
            review_count = 0
            review_elem = soup.select_one('.rating-total-count')
            if review_elem:
                review_count = self._clean_price(review_elem.get_text(strip=True))
            
            # 판매자
            seller_name = "쿠팡" if is_rocket else "마켓플레이스"
            seller_elem = soup.select_one('.prod-sale-vendor-name')
            if seller_elem:
                seller_name = seller_elem.get_text(strip=True)
            
            product = ProductInfo(
                platform="coupang",
                product_name=title,
                current_price=current_price,
                original_price=original_price,
                discount_rate=discount_rate,
                product_url=product_url,
                image_url=image_url,
                shipping_fee=shipping_fee,
                rating=rating,
                review_count=review_count,
                seller_name=seller_name,
                is_available=True,
                updated_at=datetime.now()
            )
            
            self.logger.info(f"✅ 쿠팡 상품 상세 조회: {title} - {current_price:,}원")
            return product
            
        except Exception as e:
            self.logger.error(f"❌ 쿠팡 상품 상세 조회 실패: {e}")
            return None