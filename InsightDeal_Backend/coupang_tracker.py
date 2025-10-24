import requests
from bs4 import BeautifulSoup
import re
import logging
from urllib.parse import urlparse, parse_qs
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium_stealth import stealth
from datetime import datetime
import time
import random
from typing import Optional, Dict

logger = logging.getLogger(__name__)

class CoupangTracker:
    """쿠팡 상품 가격 추적 시스템 (2025년 최신 버전)"""
    
    def __init__(self):
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
            'Accept-Language': 'ko-KR,ko;q=0.9,en;q=0.8',
            'Accept-Encoding': 'gzip, deflate, br',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1',
            'Sec-Fetch-Dest': 'document',
            'Sec-Fetch-Mode': 'navigate',
            'Sec-Fetch-Site': 'none'
        }
    
    def extract_product_info(self, product_url: str) -> Optional[Dict]:
        """쿠팡 상품 정보 추출 (차단 우회 포함)"""
        try:
            logger.info(f"🛍 [Coupang] Extracting info from: {product_url[:60]}...")
            
            # Chrome 옵션 설정 (쿠팡 차단 우회)
            chrome_options = Options()
            chrome_options.add_argument('--headless')
            chrome_options.add_argument('--no-sandbox')
            chrome_options.add_argument('--disable-dev-shm-usage')
            chrome_options.add_argument('--disable-gpu')
            chrome_options.add_argument('--disable-logging')
            chrome_options.add_argument('--log-level=3')
            chrome_options.add_argument('--window-size=1920,1080')
            chrome_options.add_argument(f'--user-agent={self.headers["User-Agent"]}')
            
            # 자동화 감지 방지
            chrome_options.add_experimental_option("excludeSwitches", ["enable-automation"])
            chrome_options.add_experimental_option('useAutomationExtension', False)
            
            driver = webdriver.Chrome(options=chrome_options)
            
            try:
                # Stealth 설정 (차단 방지)
                stealth(driver,
                    languages=["ko-KR", "ko", "en-US", "en"],
                    vendor="Google Inc.",
                    platform="Win32",
                    webgl_vendor="Intel Inc.",
                    renderer="Intel Iris OpenGL Engine",
                    fix_hairline=True
                )
                
                driver.set_page_load_timeout(15)
                driver.implicitly_wait(10)
                
                # 페이지 로드
                driver.get(product_url)
                
                # 동적 로딩 대기
                wait = WebDriverWait(driver, 15)
                wait.until(EC.presence_of_element_located((By.TAG_NAME, 'body')))
                
                # 추가 대기 (쿠팡 로딩 완료)
                time.sleep(random.uniform(2, 4))
                
                soup = BeautifulSoup(driver.page_source, 'html.parser')
                
                # 1. 상품명 추출 (2025년 최신 셀렉터)
                title_selectors = [
                    'h1.prod-buy-header__title',
                    '.prod-buy-header__title',
                    'h1[data-testid="product-title"]',
                    '.sdp-product__title',
                    'h1.prod-buy__title',
                    '.product-title h1'
                ]
                
                title = None
                for selector in title_selectors:
                    title_element = soup.select_one(selector)
                    if title_element:
                        title = title_element.get_text().strip()
                        logger.debug(f"✅ [Coupang] Title found with selector: {selector}")
                        break
                
                # 2. 가격 추출 (2025년 최신 셀렉터)
                price_selectors = [
                    '.total-price strong',
                    '.price-value strong',
                    '[data-testid="price-value"] strong',
                    '.prod-price .total-price',
                    '.prod-sale-price .total-price',
                    '.price .total-price',
                    '.price em',
                    'strong.price'
                ]
                
                price = None
                original_price = None
                
                for selector in price_selectors:
                    price_element = soup.select_one(selector)
                    if price_element:
                        price_text = price_element.get_text().strip()
                        # 숫자만 추출 (,원 제거)
                        price_digits = re.sub(r'[^0-9]', '', price_text)
                        if price_digits and len(price_digits) >= 3:
                            price = int(price_digits)
                            logger.debug(f"✅ [Coupang] Price found: {price:,}원 (selector: {selector})")
                            break
                
                # 3. 할인 전 가격 추출
                original_price_selectors = [
                    '.prod-origin-price',
                    '.price-original',
                    '.original-price',
                    'del.price',
                    '.discount-price'
                ]
                
                for selector in original_price_selectors:
                    orig_element = soup.select_one(selector)
                    if orig_element:
                        orig_text = orig_element.get_text().strip()
                        orig_digits = re.sub(r'[^0-9]', '', orig_text)
                        if orig_digits and len(orig_digits) >= 3:
                            original_price = int(orig_digits)
                            break
                
                # 4. 상품 이미지 추출
                image_url = None
                image_selectors = [
                    '.prod-image__detail img',
                    '.prod-image img',
                    '.product-image img',
                    'img.prod-image__main'
                ]
                
                for selector in image_selectors:
                    img_element = soup.select_one(selector)
                    if img_element:
                        img_src = img_element.get('src') or img_element.get('data-src')
                        if img_src:
                            # URL 정규화
                            if img_src.startswith('//'):
                                image_url = 'https:' + img_src
                            elif img_src.startswith('/'):
                                image_url = 'https://image.coupang.com' + img_src
                            else:
                                image_url = img_src
                            break
                
                # 5. 브랜드/판매자 정보
                brand = None
                brand_selectors = [
                    '.prod-brand-name',
                    '.vendor-name',
                    '.brand-name'
                ]
                
                for selector in brand_selectors:
                    brand_element = soup.select_one(selector)
                    if brand_element:
                        brand = brand_element.get_text().strip()
                        break
                
                # 6. 상품 ID 추출 (URL에서)
                product_id = self._extract_product_id(product_url)
                
                # 결과 데이터 구성
                result = {
                    'product_id': product_id,
                    'title': title or '상품명을 찾을 수 없음',
                    'price': price or 0,
                    'original_price': original_price,
                    'image_url': image_url,
                    'brand': brand,
                    'url': product_url,
                    'extracted_at': datetime.now().isoformat(),
                    'is_available': price is not None and price > 0
                }
                
                # 할인율 계산
                if price and original_price and original_price > price:
                    discount_rate = int(((original_price - price) / original_price) * 100)
                    result['discount_rate'] = discount_rate
                
                success_msg = f"✅ [Coupang] Success: {title[:40] if title else 'No title'}"
                if price:
                    success_msg += f" - {price:,}원"
                    if original_price:
                        success_msg += f" (할인전: {original_price:,}원)"
                
                logger.info(success_msg)
                return result
                
            finally:
                driver.quit()
                
        except Exception as e:
            logger.error(f"❌ [Coupang] Extraction failed for {product_url}: {e}")
            return None
    
    def _extract_product_id(self, url: str) -> Optional[str]:
        """쿠팡 상품 ID 추출"""
        try:
            # URL에서 상품 ID 추출 (여러 패턴 지원)
            patterns = [
                r'/products/(\d+)',  # /products/123456
                r'itemId=(\d+)',     # ?itemId=123456  
                r'vendorItemId=(\d+)' # ?vendorItemId=123456
            ]
            
            for pattern in patterns:
                match = re.search(pattern, url)
                if match:
                    return match.group(1)
            
            # 쿼리 파라미터에서 추출
            parsed_url = urlparse(url)
            query_params = parse_qs(parsed_url.query)
            
            for param in ['itemId', 'vendorItemId', 'productId']:
                if param in query_params:
                    return query_params[param][0]
            
            return None
            
        except Exception:
            return None
    
    def is_valid_coupang_url(self, url: str) -> bool:
        """유효한 쿠팡 URL 검증"""
        try:
            parsed = urlparse(url)
            domain_valid = 'coupang.com' in parsed.netloc.lower()
            path_valid = any(keyword in parsed.path for keyword in ['products', 'vp/products'])
            
            return domain_valid and (path_valid or 'itemId' in parsed.query)
        except:
            return False
    
    def get_clean_coupang_url(self, url: str) -> str:
        """쿠팡 URL 정리 (추적용)"""
        try:
            parsed = urlparse(url)
            product_id = self._extract_product_id(url)
            
            if product_id:
                # 깔끔한 쿠팡 URL 생성
                return f"https://www.coupang.com/vp/products/{product_id}"
            else:
                return url
        except:
            return url
    
    def check_price_change(self, current_data: Dict, previous_data: Dict) -> Dict:
        """가격 변동 분석"""
        current_price = current_data.get('price', 0)
        previous_price = previous_data.get('price', 0)
        
        if not current_price or not previous_price:
            return {'changed': False}
        
        price_diff = current_price - previous_price
        change_rate = (price_diff / previous_price) * 100
        
        result = {
            'changed': price_diff != 0,
            'price_diff': price_diff,
            'change_rate': round(change_rate, 2),
            'direction': 'up' if price_diff > 0 else 'down' if price_diff < 0 else 'same'
        }
        
        if result['changed']:
            logger.info(f"📈 [Coupang] Price changed: {previous_price:,}원 → {current_price:,}원 ({change_rate:+.1f}%)")
        
        return result
    
    def batch_check_products(self, product_urls: list) -> Dict:
        """여러 상품 일괄 가격 체크"""
        results = {
            'success': [],
            'failed': [],
            'total_checked': len(product_urls),
            'checked_at': datetime.now().isoformat()
        }
        
        for i, url in enumerate(product_urls):
            try:
                logger.info(f"🔄 [Coupang Batch] Checking {i+1}/{len(product_urls)}: {url[:40]}...")
                
                product_info = self.extract_product_info(url)
                if product_info:
                    results['success'].append(product_info)
                else:
                    results['failed'].append({'url': url, 'error': 'Failed to extract'})
                
                # 요청 간격 (쿠팡 서버 부하 방지)
                if i < len(product_urls) - 1:
                    delay = random.uniform(3, 7)
                    logger.debug(f"⏱️ [Coupang Batch] Waiting {delay:.1f}s before next request...")
                    time.sleep(delay)
                    
            except Exception as e:
                logger.error(f"❌ [Coupang Batch] Failed for {url}: {e}")
                results['failed'].append({'url': url, 'error': str(e)})
        
        logger.info(f"✅ [Coupang Batch] Completed: {len(results['success'])}/{results['total_checked']} successful")
        return results

# 전역 인스턴스
coupang_tracker = CoupangTracker()