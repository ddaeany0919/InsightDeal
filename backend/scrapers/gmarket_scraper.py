"""
🎁 GmarketScraper - G마켓 전용 스크래퍼

G마켓(gmarket.co.kr) 가격 추적 및 상품 정보 수집
- eBay Korea 계열 쇼핑몰 (옥션과 동일 체계)
- 스마일배송/스마일클럽 기능 지원
- G마켓 특화: 킹샵/글로벌샵/일반샵 구분
- 사용자 중심: 정확한 배송비와 할인 정보
"""

import re
import json
import asyncio
from typing import List, Optional, Dict
from datetime import datetime
from bs4 import BeautifulSoup
from urllib.parse import urlencode, urlparse

from .base_scraper import BaseScraper, ProductInfo

class GmarketScraper(BaseScraper):
    """🎁 G마켓 전용 스크래퍼"""
    
    def __init__(self):
        super().__init__("gmarket")
        self.base_url = "https://www.gmarket.co.kr"
        self.search_url = "https://browse.gmarket.co.kr/search"
        
    def _extract_product_id(self, url: str) -> Optional[str]:
        """🔍 G마켓 URL에서 상품 ID 추출"""
        # 예: https://item.gmarket.co.kr/Item?goodscode=123456789
        patterns = [
            r'goodscode=(\d+)',
            r'itemid=(\d+)', 
            r'/Item\?.*?(\d{8,})',
            r'/item/(\d+)'
        ]
        
        for pattern in patterns:
            match = re.search(pattern, url)
            if match:
                return match.group(1)
        return None
    
    def _parse_gmarket_price(self, soup: BeautifulSoup) -> Dict[str, int]:
        """💰 G마켓 페이지에서 가격 정보 추출"""
        prices = {'current': 0, 'original': 0}
        
        # 현재가격 (할인된 가격)
        current_selectors = [
            '.item_price .price_innerwrap .price_real',
            '.price .now_price strong',
            '.item_price .price',
            '.price_area .sale_price',
            '.now_price'
        ]
        
        for selector in current_selectors:
            element = soup.select_one(selector)
            if element:
                price = self._clean_price(element.get_text(strip=True))
                if price > 0:
                    prices['current'] = price
                    break
        
        # 원가 (정가)
        original_selectors = [
            '.item_price .price_innerwrap .price_original',
            '.price .org_price',
            '.item_price .original_price',
            '.price_area .original_price'
        ]
        
        for selector in original_selectors:
            element = soup.select_one(selector)
            if element:
                price = self._clean_price(element.get_text(strip=True))
                if price > prices['current']:  # 원가는 현재가보다 높아야 함
                    prices['original'] = price
                    break
        
        # 원가가 없으면 현재가로 설정
        if prices['original'] == 0:
            prices['original'] = prices['current']
            
        return prices
    
    def _detect_gmarket_shipping(self, soup: BeautifulSoup) -> Dict[str, any]:
        """🚚 G마켓 배송 정보 (스마일배송, 무료배송 등)"""
        info = {
            'shipping_fee': 2500,  # 기본 배송비
            'is_free_shipping': False,
            'is_smile_delivery': False,
            'is_king_shop': False,
            'delivery_type': '일반배송'
        }
        
        # 스마일배송 확인 (무료배송)
        smile_selectors = [
            '[alt*="스마일배송"]',
            '.smile_delivery',
            '.smile_club',
            ':contains("스마일배송")',
            ':contains("스마일클럽")'
        ]
        
        for selector in smile_selectors:
            if soup.select(selector):
                info['shipping_fee'] = 0
                info['is_free_shipping'] = True
                info['is_smile_delivery'] = True
                info['delivery_type'] = '스마일배송'
                break
        
        # 무료배송 확인
        free_shipping_selectors = [
            '[alt*="무료배송"]',
            '.free_delivery',
            ':contains("무룼배송")',
            ':contains("무료")'
        ]
        
        if not info['is_smile_delivery']:  # 스마일배송이 아닌 경우만 차세
            for selector in free_shipping_selectors:
                if soup.select(selector):
                    info['shipping_fee'] = 0
                    info['is_free_shipping'] = True
                    info['delivery_type'] = '무료배송'
                    break
        
        # 킹샵 확인
        king_selectors = [
            '[alt*="킹샵"]',
            '.king_shop',
            ':contains("킹샵")'
        ]
        
        for selector in king_selectors:
            if soup.select(selector):
                info['is_king_shop'] = True
                break
                
        return info
    
    async def search_product(self, product_name: str, limit: int = 10) -> List[ProductInfo]:
        """🔍 G마켓에서 상품 검색"""
        try:
            params = {
                'keyword': product_name,
                'k': '30',  # 카테고리 (전체)
                's': '8',   # 정렬 (인기순)
                'p': '1',   # 페이지
                'n': str(min(limit, 40))  # 개수
            }
            
            url = f"{self.search_url}?{urlencode(params)}"
            html = await self._make_request(url)
            
            if not html:
                return []
            
            soup = BeautifulSoup(html, 'html.parser')
            products = []
            
            # 상품 목록 파싱
            product_items = soup.select('.box__item-container, .item_box')[:limit]
            
            for item in product_items:
                try:
                    # 상품명
                    title_elem = item.select_one('.text__item, .itemname a')
                    if not title_elem:
                        continue
                    title = self._clean_product_name(title_elem.get_text(strip=True))
                    
                    # 상품 URL
                    link_elem = item.select_one('a') or title_elem
                    if not link_elem:
                        continue
                    href = link_elem.get('href', '')
                    if href.startswith('/'):
                        product_url = self.base_url + href
                    elif href.startswith('http'):
                        product_url = href
                    else:
                        continue
                    
                    # 가격 정보
                    prices = self._parse_gmarket_price(item)
                    if prices['current'] == 0:
                        continue
                    
                    # 할인율 계산
                    discount_rate = 0
                    if prices['original'] > prices['current']:
                        discount_rate = int((prices['original'] - prices['current']) / prices['original'] * 100)
                    
                    # 이미지
                    img_elem = item.select_one('img')
                    image_url = img_elem.get('src', '') if img_elem else ''
                    if image_url.startswith('//'):
                        image_url = 'https:' + image_url
                    
                    # 배송 정보
                    shipping_info = self._detect_gmarket_shipping(item)
                    
                    # 평점
                    rating = 0.0
                    rating_elem = item.select_one('.rating, .star')
                    if rating_elem:
                        try:
                            rating_text = rating_elem.get_text(strip=True)
                            rating_match = re.findall(r'[\d.]+', rating_text)
                            if rating_match:
                                rating = float(rating_match[0])
                        except:
                            pass
                    
                    # 리뷰 수
                    review_count = 0
                    review_elem = item.select_one('.review_count')
                    if review_elem:
                        review_count = self._clean_price(review_elem.get_text(strip=True))
                    
                    # 판매자 구분
                    seller_name = "G마켓"
                    if shipping_info['is_king_shop']:
                        seller_name = "킹샵"
                    elif shipping_info['is_smile_delivery']:
                        seller_name = "스마일배송"
                    
                    product = ProductInfo(
                        platform="gmarket",
                        product_name=title,
                        current_price=prices['current'],
                        original_price=prices['original'],
                        discount_rate=discount_rate,
                        product_url=product_url,
                        image_url=image_url,
                        shipping_fee=shipping_info['shipping_fee'],
                        rating=rating,
                        review_count=review_count,
                        seller_name=seller_name,
                        is_available=True,
                        updated_at=datetime.now()
                    )
                    
                    products.append(product)
                    
                except Exception as e:
                    self.logger.warning(f"⚠️ G마켓 상품 파싱 오류: {e}")
                    continue
            
            self.logger.info(f"✅ G마켓 검색 완료: {len(products)}개 상품")
            return products
            
        except Exception as e:
            self.logger.error(f"❌ G마켓 검색 실패: {e}")
            return []
    
    async def get_product_detail(self, product_url: str) -> Optional[ProductInfo]:
        """📋 G마켓 상품 상세 정보"""
        try:
            if not self.is_valid_url(product_url):
                self.logger.warning(f"⚠️ 잘못된 G마켓 URL: {product_url}")
                return None
                
            html = await self._make_request(product_url)
            if not html:
                return None
                
            soup = BeautifulSoup(html, 'html.parser')
            
            # 상품명
            title_selectors = [
                '.itemtit',
                '.item_name h1',
                '.product_name',
                'h1.title'
            ]
            
            title = ""
            for selector in title_selectors:
                elem = soup.select_one(selector)
                if elem:
                    title = self._clean_product_name(elem.get_text(strip=True))
                    break
                    
            if not title:
                self.logger.warning("⚠️ G마켓 상품명 추출 실패")
                return None
            
            # 가격 정보
            prices = self._parse_gmarket_price(soup)
            if prices['current'] == 0:
                self.logger.warning("⚠️ G마켓 가격 추출 실패")
                return None
            
            # 할인율
            discount_rate = 0
            if prices['original'] > prices['current']:
                discount_rate = int((prices['original'] - prices['current']) / prices['original'] * 100)
            
            # 배송 정보
            shipping_info = self._detect_gmarket_shipping(soup)
            
            # 이미지
            img_elem = soup.select_one('.thumb_area img, .item_photo_big img')
            image_url = img_elem.get('src', '') if img_elem else ''
            if image_url.startswith('//'):
                image_url = 'https:' + image_url
            
            # 평점
            rating = 0.0
            rating_elem = soup.select_one('.score strong, .rating_score')
            if rating_elem:
                try:
                    rating = float(rating_elem.get_text(strip=True).replace('점', ''))
                except:
                    pass
            
            # 리뷰 수
            review_count = 0
            review_elem = soup.select_one('.review_count, .score .count')
            if review_elem:
                review_count = self._clean_price(review_elem.get_text(strip=True))
            
            # 판매자
            seller_name = "G마켓"
            if shipping_info['is_king_shop']:
                seller_name = "킹샵"
            elif shipping_info['is_smile_delivery']:
                seller_name = "스마일배송"
            else:
                seller_elem = soup.select_one('.seller_info .name, .shop_name')
                if seller_elem:
                    seller_name = seller_elem.get_text(strip=True)
            
            product = ProductInfo(
                platform="gmarket",
                product_name=title,
                current_price=prices['current'],
                original_price=prices['original'],
                discount_rate=discount_rate,
                product_url=product_url,
                image_url=image_url,
                shipping_fee=shipping_info['shipping_fee'],
                rating=rating,
                review_count=review_count,
                seller_name=seller_name,
                is_available=True,
                updated_at=datetime.now()
            )
            
            self.logger.info(f"✅ G마켓 상품 상세 조회: {title} - {prices['current']:,}원")
            return product
            
        except Exception as e:
            self.logger.error(f"❌ G마켓 상품 상세 조회 실패: {e}")
            return None