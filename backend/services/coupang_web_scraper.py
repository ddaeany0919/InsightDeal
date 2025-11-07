"""
쿠팡 웹 페이지 크롤링으로 상품명 추출
개선사항:
- 에러 로깅 강화 (exc_info=True)
- 최신 User-Agent 적용
- 추가 선택자 패턴
"""
import logging
import re
from typing import Optional
import aiohttp
from bs4 import BeautifulSoup
from services.product_scraper_interface import ProductScraperInterface

class CoupangWebScraper(ProductScraperInterface):
    """
BeautifulSoup을 사용한 쿠팡 웹 크롤러"""
    
    # 최신 Chrome User-Agent (2025)
    USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    
    async def get_product_name(self, url: str) -> Optional[str]:
        """쿠팡 상품페이지에서 정확한 상품명 추출"""
        try:
            logging.info(f"[COUPANG_SCRAPER] 크롤링 시작: {url}")
            
            headers = {
                'User-Agent': self.USER_AGENT,
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
                'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7',
                'Accept-Encoding': 'gzip, deflate, br',
                'Connection': 'keep-alive',
                'Upgrade-Insecure-Requests': '1',
                'Sec-Fetch-Dest': 'document',
                'Sec-Fetch-Mode': 'navigate',
                'Sec-Fetch-Site': 'none',
                'Sec-Fetch-User': '?1',
                'Cache-Control': 'max-age=0',
                'Referer': 'https://www.coupang.com/'
            }
            
            async with aiohttp.ClientSession() as session:
                async with session.get(url, headers=headers, timeout=15) as response:
                    if response.status != 200:
                        logging.error(f"[COUPANG_SCRAPER] HTTP 에러: {response.status}")
                        return None
                    
                    html = await response.text()
                    soup = BeautifulSoup(html, 'html.parser')
                    
                    # 쿠팡 상품명 선택자 (여러 패턴 시도, 2025 기준)
                    selectors = [
                        'h1.prod-buy-header__title',
                        '.prod-buy-header__title',
                        'h2.prod-buy-header__title',
                        '.product-title h2',
                        '.product-title',
                        '#productTitle',
                        'h1[class*="title"]',
                        'div[class*="prod-buy"] h1',
                        'div[class*="prod-buy"] h2'
                    ]
                    
                    product_name = None
                    for selector in selectors:
                        element = soup.select_one(selector)
                        if element:
                            product_name = element.get_text(strip=True)
                            if product_name:  # 비어있지 않으면
                                logging.info(f"[COUPANG_SCRAPER] 선택자 '{selector}'로 찾음")
                                break
                    
                    if product_name:
                        # 불필요한 공백 제거 및 정제
                        product_name = re.sub(r'\s+', ' ', product_name).strip()
                        # 특수문자 제거
                        product_name = re.sub(r'[\n\r\t]', '', product_name)
                        logging.info(f"[COUPANG_SCRAPER] 성공: {product_name}")
                        return product_name
                    else:
                        logging.warning(f"[COUPANG_SCRAPER] 상품명을 찾을 수 없음")
                        # 디버깅: HTML 일부 출력
                        logging.debug(f"[COUPANG_SCRAPER] HTML 일부: {html[:500]}")
                        return None
                        
        except aiohttp.ClientError as e:
            logging.error(f"[COUPANG_SCRAPER] 네트워크 에러: {str(e)}", exc_info=True)
            return None
        except Exception as e:
            logging.error(f"[COUPANG_SCRAPER] 예상치 못한 에러: {str(e)}", exc_info=True)
            return None
    
    async def get_product_info(self, url: str) -> Optional[dict]:
        """상세 정보 추출 (확장용)"""
        try:
            headers = {
                'User-Agent': self.USER_AGENT,
                'Accept': 'text/html,application/xhtml+xml',
                'Accept-Language': 'ko-KR,ko;q=0.9'
            }
            
            async with aiohttp.ClientSession() as session:
                async with session.get(url, headers=headers, timeout=15) as response:
                    if response.status != 200:
                        return None
                    
                    html = await response.text()
                    soup = BeautifulSoup(html, 'html.parser')
                    
                    # 상품명
                    name_elem = soup.select_one('.prod-buy-header__title')
                    name = name_elem.get_text(strip=True) if name_elem else None
                    
                    # 가격
                    price_elem = soup.select_one('.total-price strong')
                    price_text = price_elem.get_text(strip=True) if price_elem else "0"
                    price = int(re.sub(r'[^0-9]', '', price_text))
                    
                    # 이미지
                    img_elem = soup.select_one('.prod-image__detail img')
                    image_url = img_elem.get('src') if img_elem else None
                    
                    return {
                        'name': name,
                        'price': price,
                        'brand': None,
                        'model': None,
                        'image_url': image_url
                    }
                    
        except Exception as e:
            logging.error(f"[COUPANG_SCRAPER] get_product_info 에러: {str(e)}", exc_info=True)
            return None
