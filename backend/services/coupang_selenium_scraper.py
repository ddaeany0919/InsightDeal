"""
Selenium 기반 쿠팡 크롤러
동적 페이지 렌더링을 위한 Selenium + BeautifulSoup 조합
"""
import logging
import re
from typing import Optional
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium_stealth import stealth
from bs4 import BeautifulSoup
import asyncio
from concurrent.futures import ThreadPoolExecutor
from services.product_scraper_interface import ProductScraperInterface

class CoupangSeleniumScraper(ProductScraperInterface):
    """Selenium을 사용한 쿠팡 동적 크롤러"""
    
    def __init__(self):
        self.executor = ThreadPoolExecutor(max_workers=2)
    
    def _create_driver(self) -> webdriver.Chrome:
        """반봇 우회 설정이 적용된 Chrome 드라이버 생성"""
        chrome_options = Options()
        chrome_options.add_argument('--headless')  # 헤드리스 모드
        chrome_options.add_argument('--no-sandbox')
        chrome_options.add_argument('--disable-dev-shm-usage')
        chrome_options.add_argument('--disable-blink-features=AutomationControlled')
        chrome_options.add_experimental_option('excludeSwitches', ['enable-automation'])
        chrome_options.add_experimental_option('useAutomationExtension', False)
        chrome_options.add_argument('--disable-gpu')
        chrome_options.add_argument('--window-size=1920,1080')
        
        # User-Agent 설정
        chrome_options.add_argument('user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36')
        
        driver = webdriver.Chrome(options=chrome_options)
        
        # Selenium-stealth 적용 (반봇 우회)
        stealth(driver,
                languages=["ko-KR", "ko"],
                vendor="Google Inc.",
                platform="Win32",
                webgl_vendor="Intel Inc.",
                renderer="Intel Iris OpenGL Engine",
                fix_hairline=True)
        
        return driver
    
    def _get_html_sync(self, url: str) -> Optional[str]:
        """동기식 HTML 가져오기 (쓰레드풀에서 실행)"""
        driver = None
        try:
            logging.info(f"[SELENIUM_HTML] HTML 가져오기 시작: {url}")
            
            driver = self._create_driver()
            driver.get(url)
            
            # 페이지 로드 대기
            wait = WebDriverWait(driver, 10)
            wait.until(EC.presence_of_element_located((By.TAG_NAME, "body")))
            
            # HTML 반환
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
        """비동기 HTML 가져오기"""
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(self.executor, self._get_html_sync, url)
        return result
    
    def _scrape_sync(self, url: str) -> Optional[str]:
        """동기식 Selenium 크롤링 (쓰레드풀에서 실행)"""
        driver = None
        try:
            logging.info(f"[SELENIUM_SCRAPER] 크롤링 시작: {url}")
            
            driver = self._create_driver()
            driver.get(url)
            
            # 상품명이 로드될 때까지 대기 (최대 10초)
            wait = WebDriverWait(driver, 10)
            wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "h1, h2, .prod-buy-header__title")))
            
            # 페이지 소스 가져오기
            html = driver.page_source
            soup = BeautifulSoup(html, 'html.parser')
            
            # 쿠팡 상품명 선택자
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
        """비동기 래퍼: 동기식 크롤링을 쓰레드풀에서 실행"""
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(self.executor, self._scrape_sync, url)
        return result
    
    async def get_product_info(self, url: str) -> Optional[dict]:
        """Selenium으로 상세 정보 추출 (확장용)"""
        driver = None
        try:
            driver = self._create_driver()
            driver.get(url)
            
            wait = WebDriverWait(driver, 10)
            wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, ".prod-buy-header__title")))
            
            html = driver.page_source
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
            logging.error(f"[SELENIUM_SCRAPER] get_product_info 에러: {str(e)}", exc_info=True)
            return None
        finally:
            if driver:
                driver.quit()
