"""
쿠팡 스크래퍼 통합 모듈
API, Web(BS4), Selenium 기반 스크래퍼를 하나의 파일에서 관리합니다.
"""
import logging
import os
import re
import asyncio
import aiohttp
from typing import Optional
from bs4 import BeautifulSoup
from concurrent.futures import ThreadPoolExecutor

from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium_stealth import stealth

from services.product_scraper_interface import ProductScraperInterface

class CoupangAPIClient(ProductScraperInterface):
    """쿠팡 파트너스 API 클라이언트"""
    def __init__(self):
        self.api_key = os.getenv('COUPANG_API_KEY')
        self.api_secret = os.getenv('COUPANG_API_SECRET')
        if not self.api_key or not self.api_secret:
            logging.warning("[COUPANG_API] API 키가 설정되지 않았습니다.")
    
    async def get_product_name(self, url: str) -> Optional[str]:
        if not self.api_key:
            logging.error("[COUPANG_API] API 키가 없어 호출 불가")
            return None
        try:
            product_id = self._extract_product_id(url)
            if not product_id:
                return None
            logging.info(f"[COUPANG_API] 상품 ID: {product_id}")
            logging.warning("[COUPANG_API] 아직 구현되지 않았습니다.")
            return None
        except Exception as e:
            logging.error(f"[COUPANG_API] 에러: {e}")
            return None
            
    async def get_product_info(self, url: str) -> Optional[dict]:
        logging.warning("[COUPANG_API] get_product_info 아직 구현되지 않음")
        return None
        
    def _extract_product_id(self, url: str) -> Optional[str]:
        match = re.search(r'/products/(\d+)', url)
        return match.group(1) if match else None

class CoupangWebScraper(ProductScraperInterface):
    """BeautifulSoup을 사용한 쿠팡 웹 크롤러"""
    USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    
    async def get_product_name(self, url: str) -> Optional[str]:
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
                    selectors = [
                        'h1.prod-buy-header__title', '.prod-buy-header__title',
                        'h2.prod-buy-header__title', '.product-title h2',
                        '.product-title', '#productTitle', 'h1[class*="title"]',
                        'div[class*="prod-buy"] h1', 'div[class*="prod-buy"] h2'
                    ]
                    product_name = None
                    for selector in selectors:
                        element = soup.select_one(selector)
                        if element:
                            product_name = element.get_text(strip=True)
                            if product_name:
                                logging.info(f"[COUPANG_SCRAPER] 선택자 '{selector}'로 찾음")
                                break
                    if product_name:
                        product_name = re.sub(r'\s+', ' ', product_name).strip()
                        product_name = re.sub(r'[\n\r\t]', '', product_name)
                        logging.info(f"[COUPANG_SCRAPER] 성공: {product_name}")
                        return product_name
                    else:
                        logging.warning(f"[COUPANG_SCRAPER] 상품명을 찾을 수 없음")
                        return None
        except Exception as e:
            logging.error(f"[COUPANG_SCRAPER] 에러: {str(e)}", exc_info=True)
            return None
            
    async def get_product_info(self, url: str) -> Optional[dict]:
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
                    name_elem = soup.select_one('.prod-buy-header__title')
                    name = name_elem.get_text(strip=True) if name_elem else None
                    price_elem = soup.select_one('.total-price strong')
                    price_text = price_elem.get_text(strip=True) if price_elem else "0"
                    price = int(re.sub(r'[^0-9]', '', price_text))
                    img_elem = soup.select_one('.prod-image__detail img')
                    image_url = img_elem.get('src') if img_elem else None
                    return {
                        'name': name, 'price': price, 'brand': None,
                        'model': None, 'image_url': image_url
                    }
        except Exception as e:
            logging.error(f"[COUPANG_SCRAPER] get_product_info 에러: {str(e)}", exc_info=True)
            return None

class CoupangSeleniumScraper(ProductScraperInterface):
    """Selenium을 사용한 쿠팡 동적 크롤러"""
    def __init__(self):
        self.executor = ThreadPoolExecutor(max_workers=2)
    
    def _create_driver(self) -> webdriver.Chrome:
        chrome_options = Options()
        chrome_options.add_argument('--headless')
        chrome_options.add_argument('--no-sandbox')
        chrome_options.add_argument('--disable-dev-shm-usage')
        chrome_options.add_argument('--disable-blink-features=AutomationControlled')
        chrome_options.add_experimental_option('excludeSwitches', ['enable-automation'])
        chrome_options.add_experimental_option('useAutomationExtension', False)
        chrome_options.add_argument('--disable-gpu')
        chrome_options.add_argument('--window-size=1920,1080')
        chrome_options.add_argument('user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36')
        driver = webdriver.Chrome(options=chrome_options)
        stealth(driver, languages=["ko-KR", "ko"], vendor="Google Inc.", platform="Win32",
                webgl_vendor="Intel Inc.", renderer="Intel Iris OpenGL Engine", fix_hairline=True)
        return driver
    
    def _get_html_sync(self, url: str) -> Optional[str]:
        driver = None
        try:
            logging.info(f"[SELENIUM_HTML] HTML 가져오기 시작: {url}")
            driver = self._create_driver()
            driver.get(url)
            wait = WebDriverWait(driver, 10)
            wait.until(EC.presence_of_element_located((By.TAG_NAME, "body")))
            html = driver.page_source
            logging.info(f"[SELENIUM_HTML] HTML 크기: {len(html)} bytes")
            return html
        except Exception as e:
            logging.error(f"[SELENIUM_HTML] 에러: {str(e)}", exc_info=True)
            return None
        finally:
            if driver:
                driver.quit()
    
    async def get_html(self, url: str) -> Optional[str]:
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(self.executor, self._get_html_sync, url)
    
    def _scrape_sync(self, url: str) -> Optional[str]:
        driver = None
        try:
            logging.info(f"[SELENIUM_SCRAPER] 크롤링 시작: {url}")
            driver = self._create_driver()
            driver.get(url)
            wait = WebDriverWait(driver, 10)
            wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "h1, h2, .prod-buy-header__title")))
            html = driver.page_source
            soup = BeautifulSoup(html, 'html.parser')
            selectors = [
                'h1.prod-buy-header__title', '.prod-buy-header__title',
                'h2.prod-buy-header__title', '.product-title h2',
                '.product-title', '#productTitle', 'h1[class*="title"]',
                'div[class*="prod-buy"] h1', 'div[class*="prod-buy"] h2'
            ]
            product_name = None
            for selector in selectors:
                element = soup.select_one(selector)
                if element:
                    product_name = element.get_text(strip=True)
                    if product_name:
                        logging.info(f"[SELENIUM_SCRAPER] 선택자 '{selector}'로 찾음")
                        break
            if product_name:
                product_name = re.sub(r'\s+', ' ', product_name).strip()
                product_name = re.sub(r'[\n\r\t]', '', product_name)
                logging.info(f"[SELENIUM_SCRAPER] 성공: {product_name}")
                return product_name
            else:
                logging.warning(f"[SELENIUM_SCRAPER] 상품명을 찾을 수 없음")
                return None
        except Exception as e:
            logging.error(f"[SELENIUM_SCRAPER] 에러: {str(e)}", exc_info=True)
            return None
        finally:
            if driver:
                driver.quit()
    
    async def get_product_name(self, url: str) -> Optional[str]:
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(self.executor, self._scrape_sync, url)
    
    async def get_product_info(self, url: str) -> Optional[dict]:
        driver = None
        try:
            driver = self._create_driver()
            driver.get(url)
            wait = WebDriverWait(driver, 10)
            wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, ".prod-buy-header__title")))
            html = driver.page_source
            soup = BeautifulSoup(html, 'html.parser')
            name_elem = soup.select_one('.prod-buy-header__title')
            name = name_elem.get_text(strip=True) if name_elem else None
            price_elem = soup.select_one('.total-price strong')
            price_text = price_elem.get_text(strip=True) if price_elem else "0"
            price = int(re.sub(r'[^0-9]', '', price_text))
            img_elem = soup.select_one('.prod-image__detail img')
            image_url = img_elem.get('src') if img_elem else None
            return {
                'name': name, 'price': price, 'brand': None,
                'model': None, 'image_url': image_url
            }
        except Exception as e:
            logging.error(f"[SELENIUM_SCRAPER] get_product_info 에러: {str(e)}", exc_info=True)
            return None
        finally:
            if driver:
                driver.quit()

class CoupangScraperFacade:
    """
    쿠팡 스크래퍼 통합 파사드 패턴
    호출자(product_scraper_factory)는 내부 구현을 몰라도 이 파사드를 통해 스크래핑을 수행할 수 있습니다.
    """
    @staticmethod
    def get_scraper(use_selenium: bool = True) -> ProductScraperInterface:
        if os.getenv('COUPANG_API_KEY'):
            logging.info("[FACTORY] 쿠팡 API 클라이언트 사용")
            return CoupangAPIClient()
        if use_selenium:
            logging.info("[FACTORY] 쿠팡 Selenium 크롤러 사용")
            return CoupangSeleniumScraper()
        else:
            logging.info("[FACTORY] 쿠팡 웹 크롤러 사용")
            return CoupangWebScraper()
