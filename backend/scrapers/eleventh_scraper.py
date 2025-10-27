"""
🏪 EleventhScraper - 11번가 전용 스크래퍼

11번가(11st.co.kr) 가격 추적 및 상품 정보 수집
- 검색 API 우선, 실패시 웹 스크래핑으로 폴백
- 상품명/가격/이미지/배송비 정보 추출
- 11번가 특화: 글로벌샵/슈퍼딜 구분
- 사용자 중심: 정확한 가격 정보로 신뢰도 확보
"""

import re
import json
import asyncio
from typing import List, Optional, Dict
from datetime import datetime
from bs4 import BeautifulSoup
from urllib.parse import urlencode, urlparse

from .base_scraper import BaseScraper, ProductInfo

class EleventhScraper(BaseScraper):
    """🏪 11번가 전용 스크래퍼"""
    
    def __init__(self):
        super().__init__("eleventh")
        self.base_url = "https://www.11st.co.kr"
        self.search_url = "https://search.11st.co.kr/Search.tmall"
        self.api_url = "https://apis.11st.co.kr/rest/products/search"  # API 시도용
        
    def _extract_product_id(self, url: str) -> Optional[str]:
        """🔍 11번가 URL에서 상품 ID 추출"""
        # 예: https://www.11st.co.kr/products/123456789
        patterns = [
            r'/products/(\d+)',
            r'prdNo=(\d+)',
            r'productNo=(\d+)'
        ]
        
        for pattern in patterns:
            match = re.search(pattern, url)
            if match:
                return match.group(1)
        return None
    
    def _parse_11st_price(self, soup: BeautifulSoup) -> Dict[str, int]:
        """💰 11번가 페이지에서 가격 정보 추출"""
        prices = {'current': 0, 'original': 0}
        
        # 현재가격 (할인된 가격)
        current_selectors = [
            '.price_detail .sale_price strong',
            '.c_price .sale strong', 
            '.price_area .price_now',
            '.product_price .sale_price',
            '.price_detail strong'
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
            '.price_detail .market_price',
            '.c_price .market', 
            '.price_area .price_org',
            '.product_price .org_price'
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
    
    def _detect_shipping_info(self, soup: BeautifulSoup) -> Dict[str, any]:
        """🚚 11번가 배송 정보 (무료배송, 글로벌샵 등)"""
        info = {
            'shipping_fee': 2500,  # 기본 배송비
            'is_free_shipping': False,
            'is_global': False,
            'delivery_type': '일반배송'
        }
        
        # 무료배송 확인
        free_shipping_selectors = [
            '[alt*="무료배송"]',
            '.free_delivery',
            '.shipping_free',
            ':contains("무료배송")',
            ':contains("무료")'
        ]
        
        for selector in free_shipping_selectors:
            if soup.select(selector):
                info['shipping_fee'] = 0
                info['is_free_shipping'] = True
                break
        
        # 글로벌샵 확인
        global_selectors = [
            '[alt*="글로벌"]',
            '.global_shop',
            ':contains("글로벌샵")'
        ]
        
        for selector in global_selectors:
            if soup.select(selector):
                info['is_global'] = True
                info['delivery_type'] = '글로벌배송'
                break
        
        # 슈퍼딜 확인
        if soup.select('.super_deal, [alt*="슈퍼딜"]'):
            info['delivery_type'] = '슈퍼딜'
            
        return info
    
    async def _try_api_search(self, product_name: str, limit: int) -> List[ProductInfo]:
        """🔌 11번가 API를 통한 상품 검색 시도 (선택사항)"""
        try:
            # API 키가 필요한 경우 환경변수에서 가져오기
            # 현재는 공개 API가 제한적이므로 웹 스크래핑 우선
            return []
        except:
            return []
    
    async def search_product(self, product_name: str, limit: int = 10) -> List[ProductInfo]:
        """🔍 11번가에서 상품 검색"""
        try:
            # API 검색 먼저 시도 (현재는 스킵)
            api_results = await self._try_api_search(product_name, limit)
            if api_results:
                return api_results
            
            # 웹 스크래핑으로 검색
            params = {
                'kwd': product_name,
                'dispCtgrNo': '',
                'pageIdx': '1',
                'pageSize': str(min(limit, 40)),
                'sortCd': 'NP',  # 인기순
                'shopType': 'ALL'
            }
            
            url = f"{self.search_url}?{urlencode(params)}"
            html = await self._make_request(url)
            
            if not html:
                return []
            
            soup = BeautifulSoup(html, 'html.parser')
            products = []
            
            # 상품 목록 파싱
            product_items = soup.select('.c_product_item, .product_info_area, .c_prod')[:limit]
            
            for item in product_items:
                try:
                    # 상품명
                    title_selectors = [
                        '.c_product_name a',
                        '.product_name',
                        '.title a',
                        'dt a'
                    ]
                    
                    title = ""
                    product_url = ""
                    
                    for selector in title_selectors:
                        elem = item.select_one(selector)
                        if elem:
                            title = self._clean_product_name(elem.get_text(strip=True))
                            href = elem.get('href', '')
                            if href:
                                if href.startswith('/'):
                                    product_url = self.base_url + href
                                elif href.startswith('http'):
                                    product_url = href
                            break
                    
                    if not title or not product_url:
                        continue
                    
                    # 가격 정보
                    prices = self._parse_11st_price(item)
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
                    elif image_url.startswith('/'):
                        image_url = self.base_url + image_url
                    
                    # 배송 정보
                    shipping_info = self._detect_shipping_info(item)
                    
                    # 평점 (있는 경우)
                    rating = 0.0
                    rating_elem = item.select_one('.rating, .grade')
                    if rating_elem:
                        try:
                            rating_text = rating_elem.get_text(strip=True)
                            rating = float(re.findall(r'[\d.]+', rating_text)[0]) if re.findall(r'[\d.]+', rating_text) else 0.0
                        except:
                            pass
                    
                    # 리뷰 수
                    review_count = 0
                    review_elem = item.select_one('.review_count, .reply_cnt')
                    if review_elem:
                        review_count = self._clean_price(review_elem.get_text(strip=True))
                    
                    product = ProductInfo(
                        platform="eleventh",
                        product_name=title,
                        current_price=prices['current'],
                        original_price=prices['original'],
                        discount_rate=discount_rate,
                        product_url=product_url,
                        image_url=image_url,
                        shipping_fee=shipping_info['shipping_fee'],
                        rating=rating,
                        review_count=review_count,
                        seller_name="11번가" if not shipping_info['is_global'] else "글로벌샵",
                        is_available=True,
                        updated_at=datetime.now()
                    )
                    
                    products.append(product)
                    
                except Exception as e:
                    self.logger.warning(f"⚠️ 11번가 상품 파싱 오류: {e}")
                    continue
            
            self.logger.info(f"✅ 11번가 검색 완료: {len(products)}개 상품")
            return products
            
        except Exception as e:
            self.logger.error(f"❌ 11번가 검색 실패: {e}")
            return []
    
    async def get_product_detail(self, product_url: str) -> Optional[ProductInfo]:
        """📋 11번가 상품 상세 정보"""
        try:
            if not self.is_valid_url(product_url):
                self.logger.warning(f"⚠️ 잘못된 11번가 URL: {product_url}")
                return None
                
            html = await self._make_request(product_url)
            if not html:
                return None
                
            soup = BeautifulSoup(html, 'html.parser')
            
            # 상품명
            title_selectors = [
                '.b_product_name',
                '.product_name h1',
                'h1.product_title',
                '.product_info .name'
            ]
            
            title = ""
            for selector in title_selectors:
                elem = soup.select_one(selector)
                if elem:
                    title = self._clean_product_name(elem.get_text(strip=True))
                    break
                    
            if not title:
                self.logger.warning("⚠️ 11번가 상품명 추출 실패")
                return None
            
            # 가격 정보
            prices = self._parse_11st_price(soup)
            if prices['current'] == 0:
                self.logger.warning("⚠️ 11번가 가격 추출 실패")
                return None
            
            # 할인율
            discount_rate = 0
            if prices['original'] > prices['current']:
                discount_rate = int((prices['original'] - prices['current']) / prices['original'] * 100)
            
            # 배송 정보
            shipping_info = self._detect_shipping_info(soup)
            
            # 이미지
            img_elem = soup.select_one('.thumbnail_area img, .product_img img')
            image_url = img_elem.get('src', '') if img_elem else ''
            if image_url.startswith('//'):
                image_url = 'https:' + image_url
            elif image_url.startswith('/'):
                image_url = self.base_url + image_url
            
            # 평점
            rating = 0.0
            rating_elem = soup.select_one('.grade_area .grade, .rating strong')
            if rating_elem:
                try:
                    rating = float(rating_elem.get_text(strip=True).replace('점', ''))
                except:
                    pass
            
            # 리뷰 수
            review_count = 0
            review_elem = soup.select_one('.grade_area .count, .review_count')
            if review_elem:
                review_count = self._clean_price(review_elem.get_text(strip=True))
            
            # 판매자
            seller_name = "11번가"
            if shipping_info['is_global']:
                seller_name = "글로벌샵"
            else:
                seller_elem = soup.select_one('.seller_info .name, .shop_name')
                if seller_elem:
                    seller_name = seller_elem.get_text(strip=True)
            
            product = ProductInfo(
                platform="eleventh",
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
            
            self.logger.info(f"✅ 11번가 상품 상세 조회: {title} - {prices['current']:,}원")
            return product
            
        except Exception as e:
            self.logger.error(f"❌ 11번가 상품 상세 조회 실패: {e}")
            return None