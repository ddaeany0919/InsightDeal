"""
🔨 AuctionScraper - 옥션 전용 스크래퍼

옥션(auction.co.kr) 가격 추적 및 상품 정보 수집
- eBay Korea 계열 쇼핑몰 (G마켓과 동일 체계)
- 옥션 특화: 경매/즈즉구매/고객만족샵 등
- 가격비교사이트의 4번째 플랫폼으로 마지막 완성
- 사용자 중심: 전체 4몰 중 최저가 찾기 완성
"""

import re
import json
import asyncio
from typing import List, Optional, Dict
from datetime import datetime
from bs4 import BeautifulSoup
from urllib.parse import urlencode, urlparse

from .base_scraper import BaseScraper, ProductInfo

class AuctionScraper(BaseScraper):
    """🔨 옥션 전용 스크래퍼"""
    
    def __init__(self):
        super().__init__("auction")
        self.base_url = "https://www.auction.co.kr"
        self.search_url = "https://browse.auction.co.kr/search"
        
    def _extract_product_id(self, url: str) -> Optional[str]:
        """🔍 옥션 URL에서 상품 ID 추출"""
        # 예: http://itempage3.auction.co.kr/DetailView.aspx?itemno=A123456789
        patterns = [
            r'itemno=([A-Z]?\d+)',
            r'ItemNo=([A-Z]?\d+)',
            r'/item/([A-Z]?\d+)',
            r'DetailView\.aspx\?.*?([A-Z]\d{8,})'
        ]
        
        for pattern in patterns:
            match = re.search(pattern, url)
            if match:
                return match.group(1)
        return None
    
    def _parse_auction_price(self, soup: BeautifulSoup) -> Dict[str, int]:
        """💰 옥션 페이지에서 가격 정보 추출"""
        prices = {'current': 0, 'original': 0, 'bid_price': 0}
        
        # 즉시구매가 (일반 판매가)
        current_selectors = [
            '.sale_price .price',
            '.item_price .now_price',
            '.price_area .sale_price strong',
            '.buynow_price',
            '.now_price strong'
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
            '.original_price',
            '.item_price .org_price', 
            '.price_area .original_price'
        ]
        
        for selector in original_selectors:
            element = soup.select_one(selector)
            if element:
                price = self._clean_price(element.get_text(strip=True))
                if price > prices['current']:  # 원가는 현재가보다 높아야 함
                    prices['original'] = price
                    break
        
        # 경매가 (옥션 특화)
        bid_selectors = [
            '.bid_price .price',
            '.current_bid_price',
            '.auction_price'
        ]
        
        for selector in bid_selectors:
            element = soup.select_one(selector)
            if element:
                bid_price = self._clean_price(element.get_text(strip=True))
                if bid_price > 0:
                    prices['bid_price'] = bid_price
                    # 경매가가 있으면 더 저렴한 가격 선택
                    if prices['current'] == 0 or bid_price < prices['current']:
                        prices['current'] = bid_price
                    break
        
        # 원가가 없으면 현재가로 설정
        if prices['original'] == 0:
            prices['original'] = prices['current']
            
        return prices
    
    def _detect_auction_shipping(self, soup: BeautifulSoup) -> Dict[str, any]:
        """🚚 옥션 배송 정보 (즘짙구매, 무료배송 등)"""
        info = {
            'shipping_fee': 2500,  # 기본 배송비
            'is_free_shipping': False,
            'is_quick_delivery': False,
            'is_satisfaction_shop': False,
            'delivery_type': '일반배송'
        }
        
        # 즘짙구매 확인
        quick_selectors = [
            '[alt*="즘짙구매"]',
            '.quick_delivery',
            ':contains("즘짙구매")',
            ':contains("즘짙배송")'
        ]
        
        for selector in quick_selectors:
            if soup.select(selector):
                info['is_quick_delivery'] = True
                info['delivery_type'] = '즘짙구매'
                # 즘짙구매는 보통 무료배송
                info['shipping_fee'] = 0
                info['is_free_shipping'] = True
                break
        
        # 무료배송 확인 (즘짙구매가 아닌 경우)
        if not info['is_quick_delivery']:
            free_shipping_selectors = [
                '[alt*="무료배송"]',
                '.free_delivery',
                ':contains("무료배송")',
                ':contains("무료")',
                '.delivery_free'
            ]
            
            for selector in free_shipping_selectors:
                if soup.select(selector):
                    info['shipping_fee'] = 0
                    info['is_free_shipping'] = True
                    info['delivery_type'] = '무룼배솨'
                    break
        
        # 고객만족샵 확인
        satisfaction_selectors = [
            '[alt*="고객만족"]',
            '.satisfaction_shop',
            ':contains("고객만족샵")'
        ]
        
        for selector in satisfaction_selectors:
            if soup.select(selector):
                info['is_satisfaction_shop'] = True
                break
                
        return info
    
    async def search_product(self, product_name: str, limit: int = 10) -> List[ProductInfo]:
        """🔍 옥션에서 상품 검색"""
        try:
            params = {
                'keyword': product_name,
                'itemno': '',
                'nickname': '',
                'frm': 'hometab',
                'dom': '1',
                'isSuggestion': 'No',
                'OrderingType': '2'  # 인기순
            }
            
            url = f"{self.search_url}?{urlencode(params)}"
            html = await self._make_request(url)
            
            if not html:
                return []
            
            soup = BeautifulSoup(html, 'html.parser')
            products = []
            
            # 상품 목록 파싱
            product_items = soup.select('.component--item_card, .item-wrap')[:limit]
            
            for item in product_items:
                try:
                    # 상품명
                    title_elem = item.select_one('.text--title, .item_title a')
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
                    prices = self._parse_auction_price(item)
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
                    shipping_info = self._detect_auction_shipping(item)
                    
                    # 평점
                    rating = 0.0
                    rating_elem = item.select_one('.rating, .grade')
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
                    seller_name = "옥션"
                    if shipping_info['is_satisfaction_shop']:
                        seller_name = "고객만족샵"
                    elif shipping_info['is_quick_delivery']:
                        seller_name = "즘짙구매"
                    
                    product = ProductInfo(
                        platform="auction",
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
                    self.logger.warning(f"⚠️ 옥션 상품 파싱 오류: {e}")
                    continue
            
            self.logger.info(f"✅ 옥션 검색 완료: {len(products)}개 상품")
            return products
            
        except Exception as e:
            self.logger.error(f"❌ 옥션 검색 실패: {e}")
            return []
    
    async def get_product_detail(self, product_url: str) -> Optional[ProductInfo]:
        """📋 옥션 상품 상세 정보"""
        try:
            if not self.is_valid_url(product_url):
                self.logger.warning(f"⚠️ 잘못된 옥션 URL: {product_url}")
                return None
                
            html = await self._make_request(product_url)
            if not html:
                return None
                
            soup = BeautifulSoup(html, 'html.parser')
            
            # 상품명
            title_selectors = [
                '.itemtitle',
                '.item_title h1',
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
                self.logger.warning("⚠️ 옥션 상품명 추출 실패")
                return None
            
            # 가격 정보
            prices = self._parse_auction_price(soup)
            if prices['current'] == 0:
                self.logger.warning("⚠️ 옥션 가격 추출 실패")
                return None
            
            # 할인율
            discount_rate = 0
            if prices['original'] > prices['current']:
                discount_rate = int((prices['original'] - prices['current']) / prices['original'] * 100)
            
            # 배송 정보
            shipping_info = self._detect_auction_shipping(soup)
            
            # 이미지
            img_elem = soup.select_one('.thumb img, .item_photo img')
            image_url = img_elem.get('src', '') if img_elem else ''
            if image_url.startswith('//'):
                image_url = 'https:' + image_url
            
            # 평점
            rating = 0.0
            rating_elem = soup.select_one('.score, .rating_score')
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
            seller_name = "옥션"
            if shipping_info['is_satisfaction_shop']:
                seller_name = "고객만족샵"
            elif shipping_info['is_quick_delivery']:
                seller_name = "즘짙구매"
            else:
                seller_elem = soup.select_one('.seller_info .name, .shop_name')
                if seller_elem:
                    seller_name = seller_elem.get_text(strip=True)
            
            product = ProductInfo(
                platform="auction",
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
            
            self.logger.info(f"✅ 옥션 상품 상세 조회: {title} - {prices['current']:,}원")
            return product
            
        except Exception as e:
            self.logger.error(f"❌ 옥션 상품 상세 조회 실패: {e}")
            return None