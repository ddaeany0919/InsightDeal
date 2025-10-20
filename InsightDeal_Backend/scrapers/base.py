import os
import re
import sys
import hashlib
import requests
import logging
import time
import json
import ai_parser
import base64
from abc import ABC, abstractmethod
from urllib.parse import urlparse, urljoin, unquote, parse_qs

from bs4 import BeautifulSoup
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium_stealth import stealth
from sqlalchemy.orm import Session
import easyocr
from pathlib import Path
import random
# --- 👇 [추가] WebDriverWait을 위한 import ---
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By
# --- [추가 완료] ---

import models
import database

# 환경변수 설정
SELENIUM_TIMEOUT = int(os.getenv("SELENIUM_TIMEOUT", "5"))
SCRAPER_DELAY = int(os.getenv("SCRAPER_DELAY", "0"))
MAX_RETRY_COUNT = int(os.getenv("MAX_RETRY_COUNT", "2"))
HEADLESS = os.getenv("HEADLESS", "true").lower() == "true"
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")

# User-Agent 로테이션
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
]

# --- 로거 설정 ---
def setup_logger(name: str = "BaseScraper"):
    """UTF-8 인코딩을 강제하는 로거 설정"""
    logger = logging.getLogger(name)
    
    if logger.handlers:
        return logger
    
    logger.setLevel(getattr(logging, LOG_LEVEL))
    
    # 로그 디렉토리 생성
    log_dir = Path(__file__).parent.parent / "logs"
    log_dir.mkdir(exist_ok=True)
    
    # 포맷터
    formatter = logging.Formatter(
        fmt='%(asctime)s | %(levelname)-8s | %(name)s:%(funcName)s:%(lineno)d | %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )
    
    # 파일 핸들러 (UTF-8 인코딩 강제)
    from logging.handlers import RotatingFileHandler
    file_handler = RotatingFileHandler(
        log_dir / f"scraper_{time.strftime('%Y%m%d')}.log",
        maxBytes=10*1024*1024,  # 10MB
        backupCount=5,
        encoding='utf-8'  # ✅ UTF-8 인코딩 강제 설정
    )
    file_handler.setFormatter(formatter)
    file_handler.setLevel(logging.DEBUG)
    
    # 콘솔 핸들러 (UTF-8 설정)
    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)
    console_handler.setLevel(logging.INFO)
    
    logger.addHandler(file_handler)
    logger.addHandler(console_handler)
    
    return logger

logger = setup_logger()

# --- 전역 설정 ---
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_CACHE_DIR = os.path.join(os.path.dirname(SCRIPT_DIR), "image_cache")
if not os.path.exists(IMAGE_CACHE_DIR):
    os.makedirs(IMAGE_CACHE_DIR)

class BaseScraper(ABC):
    """
    모든 스크래퍼의 기반이 되는 추상 클래스.
    """
    ocr_reader = None

    def __init__(self, db_session: Session, community_name: str, community_url: str):
        self.db = db_session
        self.community_name = community_name
        self.community_url = community_url
        self.base_url = f"{urlparse(community_url).scheme}://{urlparse(community_url).netloc}"
        self.driver = None
        self.wait = None  # WebDriverWait 객체를 저장할 변수 추가
        self.cookies = {}
        self.limit = None
        self.retry_count = 0
        self.start_time = None
        self.scraped_count = 0
        self.community_entry = (
            self.db.query(models.Community)
            .filter(models.Community.name == self.community_name)
            .first()
        )
        if not self.community_entry:
            raise ValueError(f"'{self.community_name}' 커뮤니티를 DB에서 찾을 수 없습니다.")

        if BaseScraper.ocr_reader is None:
            logger.info("Initializing EasyOCR model... (This may take a moment on first run)")
            BaseScraper.ocr_reader = easyocr.Reader(['ko', 'en'])
            logger.info("EasyOCR model loaded successfully.")
    def run_with_retry(self, limit=None) -> bool:
        """재시도 로직이 포함된 실행"""
        for attempt in range(MAX_RETRY_COUNT):
            try:
                self.start_time = time.time()
                self.scraped_count = 0
                
                result = self.run(limit)
                
                # 성능 로그
                elapsed = time.time() - self.start_time
                rate = self.scraped_count / elapsed if elapsed > 0 else 0
                
                logger.info(
                    f"[{self.community_name}] 스크래핑 완료 - "
                    f"처리: {self.scraped_count}건, "
                    f"소요시간: {elapsed:.2f}초, "
                    f"속도: {rate:.2f}건/초"
                )
                
                return True
                
            except Exception as e:
                self.retry_count = attempt + 1
                logger.warning(
                    f"[{self.community_name}] 스크래핑 실패 (시도 {attempt + 1}/{MAX_RETRY_COUNT}): {e}"
                )
                
                if attempt < MAX_RETRY_COUNT - 1:
                    delay = (2 ** attempt) + random.uniform(0, 1)  # 지수 백오프 + 랜덤
                    logger.info(f"[{self.community_name}] {delay:.1f}초 후 재시도...")
                    time.sleep(delay)
                    continue
                else:
                    logger.error(f"[{self.community_name}] 최대 재시도 횟수 초과")
                    return False
        
        return False

    def __enter__(self):
        """컨텍스트 매니저 지원"""
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """리소스 정리"""
        if self.driver:
            try:
                self.driver.quit()
                logger.debug(f"[{self.community_name}] 드라이버 정리 완료")
            except Exception as e:
                logger.warning(f"[{self.community_name}] 드라이버 정리 중 오류: {e}")
        
        if self.db:
            try:
                self.db.close()
                logger.debug(f"[{self.community_name}] DB 세션 정리 완료")
            except Exception as e:
                logger.warning(f"[{self.community_name}] DB 세션 정리 중 오류: {e}")
    @staticmethod
    def _parse_ecommerce_link(raw_link: str) -> str:
        if not raw_link:
            return ""
        try:
            parsed_url = urlparse(raw_link)
            if 'target' in parsed_url.query:
                query_params = parse_qs(parsed_url.query)
                encoded_target = query_params.get("target", [None])[0]
                if encoded_target:
                    encoded_target += '=' * (-len(encoded_target) % 4)
                    decoded_bytes = base64.b64decode(encoded_target)
                    return decoded_bytes.decode('utf-8')
            return unquote(raw_link)
        except Exception as e:
            logger.warning(f"Failed to parse ecommerce link '{raw_link}': {e}")
            return raw_link

    @abstractmethod
    def scrape(self):
        pass

    def run(self, limit=None):
        self.limit = limit
        self.scraped_count = 0
        
        logger.info(f"[{self.community_name}] 스크래핑 프로세스 시작")
        
        try:
            self._create_selenium_driver()
            deals_data = self.scrape()
            
            if not deals_data:
                logger.info(f"[{self.community_name}] 새로운 딜을 찾지 못했습니다")
                return False
            
            deals_data.reverse()
            new_deals_count = 0
            
            # ✅ 배치 리스트 선언
            batch_deals = []
            batch_histories = []
            
            for item in deals_data:
                self.scraped_count += 1
                
                deal_obj = item['deal']
                exists = self.db.query(models.Deal.id).filter(  # ✅ 수정
                    models.Deal.post_link == deal_obj.post_link
                ).first()

                if exists:
                    continue
                
                # 기존 OCR 로직
                if deal_obj.shipping_fee == '정보 없음' and deal_obj.deal_type == '일반':
                    original_image_url = item.get('original_image_url')
                    if original_image_url:
                        _, _, ocr_text = self._download_and_get_local_path(
                            original_image_url, -1, perform_ocr=True
                        )
                        
                        if any(term in ocr_text for term in ("무료배송", "무료 배송")):
                            logger.info(f"[OCR] '{deal_obj.title}'에서 '무료배송' 발견. 배송비 업데이트")
                            deal_obj.shipping_fee = "무료"
                
                batch_deals.append(deal_obj)
                new_deals_count += 1
            
            # ✅ 배치 저장
            if batch_deals:
                self.db.add_all(batch_deals)    # ✅ 수정
                self.db.flush()                 # ✅ 수정
                
                for deal in batch_deals:
                    batch_histories.append(models.PriceHistory(deal_id=deal.id, price=deal.price))
                
                self.db.add_all(batch_histories)  # ✅ 수정
                self.db.commit()                  # ✅ 수정
                
                logger.info(f"[{self.community_name}] {new_deals_count}개의 새로운 딜을 DB에 저장했습니다")
            else:
                logger.info(f"[{self.community_name}] 저장할 새로운 딜이 없습니다")
            
            return new_deals_count > 0
        
        except Exception as e:
            logger.error(f"[{self.community_name}] 스크래핑 중 오류 발생: {e}", exc_info=True)
            self.db.rollback()  # ✅ 수정
            return False
        finally:
            pass

    def _create_selenium_driver(self):
        """최적화된 Chrome 드라이버 생성"""
        chrome_options = Options()
        
        # ✅ 핵심 성능 최적화 (품질 유지)
        chrome_options.page_load_strategy = 'eager'  # DOM 로드되면 즉시 진행
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--disable-gpu")
        chrome_options.add_argument("--disable-software-rasterizer")
        chrome_options.add_argument("--disable-logging")
        chrome_options.add_argument("--log-level=3")
        chrome_options.add_argument("--silent")
        chrome_options.add_argument("--disable-web-security")
        chrome_options.add_argument("--disable-features=VizDisplayCompositor")
        chrome_options.add_argument("--disable-ipc-flooding-protection")
        
        # ✅ 불필요한 기능 비활성화 (속도 향상)
        chrome_options.add_argument("--disable-plugins")
        chrome_options.add_argument("--no-first-run")
        chrome_options.add_argument("--disable-default-apps")
        chrome_options.add_argument("--disable-background-timer-throttling")
        chrome_options.add_argument("--disable-renderer-backgrounding")
        chrome_options.add_argument("--disable-backgrounding-occluded-windows")
        chrome_options.add_argument("--disable-client-side-phishing-detection")
        chrome_options.add_argument("--disable-sync")
        chrome_options.add_argument("--metrics-recording-only")
        chrome_options.add_argument("--no-default-browser-check")
        
        # ✅ 메모리 최적화
        chrome_options.add_argument("--memory-pressure-off")
        chrome_options.add_argument("--max_old_space_size=4096")
        
        # ✅ 네트워크 최적화  
        chrome_options.add_argument("--aggressive-cache-discard")
        chrome_options.add_argument("--disable-background-networking")
        
        # 윈도우 크기 설정
        chrome_options.add_argument("--window-size=1920,1080")
        
        # 헤드리스 모드
        if HEADLESS:
            chrome_options.add_argument("--headless")
        
        # 랜덤 User-Agent
        user_agent = random.choice(USER_AGENTS)
        chrome_options.add_argument(f'user-agent={user_agent}')
        
        # 자동화 감지 방지
        chrome_options.add_experimental_option("excludeSwitches", ["enable-automation", "enable-logging"])
        chrome_options.add_experimental_option('useAutomationExtension', False)
        
        try:
            self.driver = webdriver.Chrome(options=chrome_options)
            self.driver.set_page_load_timeout(3)
            self.driver.implicitly_wait(5)
            self.wait = WebDriverWait(self.driver, 3)
            
            # Stealth 설정
            stealth(
                self.driver,
                languages=["ko-KR", "ko"],
                vendor="Google Inc.",
                platform="Win32",
                webgl_vendor="Intel Inc.",
                renderer="Intel Iris OpenGL Engine",
                fix_hairline=True
            )
            
            # 페이지 로드
            self.driver.get(self.community_url)
            self.cookies = {c['name']: c['value'] for c in self.driver.get_cookies()}
            
            logger.info(f"[{self.community_name}] 드라이버 초기화 완료")
            
        except Exception as e:
            logger.error(f"[{self.community_name}] 드라이버 초기화 실패: {e}")
            raise

    def _download_and_get_local_path(self, image_url, deal_id: int, perform_ocr: bool = True):
        """최적화된 이미지 다운로드 및 캐싱"""
        placeholder = 'https://placehold.co/400x400/E9E2FD/333?text=Deal'
        
        if not isinstance(image_url, str) or not image_url or 'placehold.co' in image_url:
            return placeholder, None, ""

        try:
            url_hash = hashlib.md5(image_url.encode()).hexdigest()
            match = re.search(r'\.(jpg|jpeg|png|gif|webp)', urlparse(image_url).path, re.IGNORECASE)
            ext = match.group(0) if match else '.jpg'
            filename = f"{url_hash}{ext}"
            save_path = os.path.join(IMAGE_CACHE_DIR, filename)
            
            # 캐시 확인
            if os.path.exists(save_path):
                file_age = time.time() - os.path.getmtime(save_path)
                if file_age < 7 * 24 * 3600:  # 7일 이내면 캐시 사용
                    logger.debug(f"[ImageCache] Using cached image: {filename}")
                    ocr_text = self._ocr_image(save_path) if perform_ocr else ""
                    return f"/images/{filename}", save_path, ocr_text

            # 새로 다운로드
            headers = {
                'Referer': f"{urlparse(image_url).scheme}://{urlparse(image_url).netloc}/",
                'User-Agent': self.driver.execute_script("return navigator.userAgent;") if self.driver else random.choice(USER_AGENTS)
            }
            
            resp = requests.get(
                image_url, 
                headers=headers, 
                cookies=self.cookies, 
                stream=True, 
                timeout=15
            )
            resp.raise_for_status()
            
            with open(save_path, 'wb') as f:
                for chunk in resp.iter_content(chunk_size=8192):
                    f.write(chunk)
            
            logger.debug(f"[ImageCache] Downloaded new image: {filename}")
            ocr_text = self._ocr_image(save_path) if perform_ocr else ""
            
            return f"/images/{filename}", save_path, ocr_text

        except Exception as e:
            logger.warning(f"[ImageProcessing] Failed for URL: {image_url}, Error: {e}")
            return placeholder, None, ""

    @staticmethod
    def _clean_price(price_input):
        price_str = str(price_input).strip()
        if not price_str or "정보" in price_str or "없음" in price_str:
            return "정보 없음"

        is_dollar = ("$" in price_str or "달러" in price_str or "dollar" in price_str.lower()) and "원" not in price_str
        
        if is_dollar:
            try:
                num_match = re.search(r'[\d,]+\.?\d*', price_str)
                if num_match:
                    num_str = num_match.group().replace(',', '')
                    dollar_amount = float(num_str)
                    return f"${dollar_amount:.2f}"
            except (ValueError, TypeError):
                pass
        digits = re.sub(r'[^0-9]', '', price_str)
        if not digits:
            return "정보 없음"
        try:
            return f"{int(digits):,}원"
        except (ValueError, TypeError):
            return "정보 없음"

    @staticmethod
    def _clean_shipping_fee(shipping_input):
        shipping_str = str(shipping_input).strip()
        if not shipping_str or "정보 없음" in shipping_str: return "정보 없음"
        for part in re.split(r'[,()]', shipping_str):
            p = part.lower().strip()
            if '포함' in p: return "배송비 포함"
            if any(k in p for k in ['무료', '무배', 'free']): return "무료"
        if any(k in shipping_str for k in ["배송비", "착불"]) and any(c.isdigit() for c in shipping_str): return shipping_str
        return "정보 없음"
    
    @staticmethod
    def _extract_shipping_from_title(title: str) -> str:
        match = re.search(r'\(([^)]+)\)$', title)
        if match:
            for part in match.group(1).split('/'):
                fee = BaseScraper._clean_shipping_fee(part)
                if fee != '정보 없음': return fee
        if any(k in title for k in ['무배', '무료배송', '택배비 포함', '무료']): return "무료"
        return "정보 없음"

    @staticmethod
    def _extract_price_from_title(title: str) -> str:
        match = re.search(r'\(([^)]+)\)$', title)
        if match:
            for part in match.group(1).split('/'):
                price = BaseScraper._clean_price(part)
                if price not in ["가격 상이", "정보 없음"]: return price
        return "정보 없음"
    
    @staticmethod
    def _clean_shop_name(shop_name_input):
        shop_name_str = str(shop_name_input).strip()
        if not shop_name_str: return "정보 없음"
        lower = shop_name_str.lower()
        shop_map = {
            # 해외 쇼핑몰
            "알리익스프레스": "알리", "aliexpress": "알리", "알리": "알리",
            "alibaba": "알리바바", "알리바바": "알리바바",
            "amazon": "Amazon", "아마존": "Amazon",
            "ebay": "eBay", "이베이": "eBay",
            "qoo10": "Qoo10", "큐텐": "Qoo10",
            "wish": "Wish",

            # 국내 대형 쇼핑몰
            "g마켓": "G마켓", "gmarket": "G마켓",
            "옥션": "옥션", "auction": "옥션",
            "11번가": "11번가", "11st": "11번가",
            "쿠팡": "쿠팡", "coupang": "쿠팡",
            "티몬": "티몬", "tmon": "티몬",
            "위메프": "위메프", "wemakeprice": "위메프",
            "인터파크": "인터파크", "interpark": "인터파크",
            
            # 네이버 관련
            "네이버 스마트스토어": "네이버", "naver smartstore": "네이버", "스마트스토어": "네이버",
            "naver": "네이버", "네이버쇼핑": "네이버",

            # 유통/리테일
            "롯데온": "롯데ON", "lotteon": "롯데ON", "롯데마트": "롯데ON",
            "ssg": "SSG", "쓱": "SSG", "신세계": "SSG",
            "costco": "Costco", "코스트코": "Costco",
            "하이마트": "하이마트", "hi-mart": "하이마트",
            "홈플러스": "홈플러스", "homeplus": "홈플러스",
            "이마트": "이마트", "emart": "이마트",
            "올리브영": "올리브영", "oliveyoung": "올리브영",
            
            # 패션/뷰티 전문몰
            "무신사": "무신사", "musinsa": "무신사",
            "29cm": "29CM",
            "브랜디": "브랜디", "brandi": "브랜디",

            # 게임/콘텐츠
            "ps스토어": "PlayStation Store", "플레이스테이션": "PlayStation Store",
            
            # 가격비교
            "다나와": "다나와", "danawa": "다나와",

            # 음식/프랜차이즈
            "스타벅스": "스타벅스", "starbucks": "스타벅스",
            "맥도날드": "맥도날드", "mcdonald": "맥도날드",
            "버거킹": "버거킹", "burgerking": "버거킹",
            "롯데리아": "롯데리아",
            "kfc": "KFC",
            "피자헛": "피자헛",
            "도미노피자": "도미노피자",

            # 카드사
            "nh농협카드": "NH농협카드", "농협카드": "NH농협카드",
            "신한카드": "신한카드", "shinhan": "신한카드",
            "삼성카드": "삼성카드", "samsung": "삼성카드",
            "현대카드": "현대카드", "hyundai": "현대카드",
            "kb국민카드": "KB국민카드", "국민카드": "KB국민카드",
            "하나카드": "하나카드", "hanacard": "하나카드",

            # 페이 서비스
            "카카오페이": "카카오페이", "kakaopay": "카카오페이",
            "네이버페이": "네이버페이", "naverpay": "네이버페이",
            "토스": "토스", "toss": "토스",
            "페이코": "페이코", "payco": "페이코",
        }
        for alias, name in shop_map.items():
            if alias in lower: return name
        return shop_name_str

    @staticmethod
    def _infer_shop_name_from_link(ecommerce_link: str) -> str:
        """쇼핑몰 링크에서 쇼핑몰 이름을 추론합니다."""
        if not ecommerce_link:
            return "정보 없음"
            
        try:
            domain = urlparse(ecommerce_link).netloc.lower()
            logger.info(f" - Inferring shop name from domain: {domain}")
            
            # 도메인별 쇼핑몰 매핑
            domain_map = {
                # 해외 쇼핑몰
                "aliexpress.com": "알리",
                "alibaba.com": "알리바바", 
                "amazon.com": "Amazon",
                "amazon.co.kr": "Amazon",
                "ebay.com": "eBay",
                "wish.com": "Wish",
                
                # 국내 대형 쇼핑몰
                "coupang.com": "쿠팡",
                "gmarket.co.kr": "G마켓",
                "auction.co.kr": "옥션",
                "11st.co.kr": "11번가",
                "lotteon.com": "롯데ON",
                "tmon.co.kr": "티몬",
                "wemakeprice.com": "위메프", # .co.kr -> .com 변경 가능성
                "ssg.com": "SSG",
                "qoo10.com": "Qoo10", # .co.kr -> .com 변경 가능성
                
                # 네이버 관련
                "smartstore.naver.com": "네이버",
                "shopping.naver.com": "네이버",
                "m.naver.com": "네이버",
                
                # 브랜드/서비스
                "musinsa.com": "무신사",
                "oliveyoung.co.kr": "올리브영",
                "29cm.co.kr": "29CM",  
                "brandi.co.kr": "브랜디",
                "interpark.com": "인터파크",
                "e-himart.co.kr": "하이마트", # 하이마트 도메인 변경 가능성
                "danawa.com": "다나와",
                "homeplus.co.kr": "홈플러스",
                "emart.ssg.com": "이마트", # 이마트 도메인
                
                # 카드/페이 서비스
                "kakaopay.com": "카카오페이",
                "pay.naver.com": "네이버페이",
                "toss.im": "토스",
                "payco.com": "페이코",
                
                # 음식점/프랜차이즈
                "starbucks.co.kr": "스타벅스",
                "mcdonalds.co.kr": "맥도날드", 
                "burgerking.co.kr": "버거킹",
            }
            
            # 정확한 도메인 매칭
            if domain in domain_map:
                shop_name = domain_map[domain]
                logger.info(f" - Matched exact domain '{domain}' -> '{shop_name}'")
                return shop_name
            
            # 서브도메인 포함 매칭
            for domain_key, shop_name in domain_map.items():
                if domain.endswith(domain_key):
                    logger.info(f" - Matched subdomain '{domain_key}' -> '{shop_name}'")
                    return shop_name
            
            logger.info(f" - No matching shop found for domain: {domain}")
            return "정보 없음"
            
        except Exception as e:
            logger.warning(f" - Failed to infer shop name from link: {e}")
            return "정보 없음"

    @staticmethod
    def _infer_shop_name_from_content(full_title: str, content_html: str) -> str:
        """본문이나 타이틀에서 실제 상품을 파는 곳을 찾아 쇼핑몰 이름을 추론합니다."""
        logger.info(" - Inferring shop name from title/content...")
        
        # 타이틀과 본문 텍스트 결합
        content_text = ""
        if content_html:
            try:
                soup = BeautifulSoup(content_html, 'html.parser')
                content_text = soup.get_text().lower()
            except:
                content_text = ""
        
        combined_text = (full_title + " " + content_text).lower()
        
        # 1. 쿠폰/적립 관련 패턴 확인 (더 높은 우선순위)
        coupon_patterns = [
            # 카드사
            (r'(nh농협카드|농협카드)', "NH농협카드"),
            (r'(신한카드)', "신한카드"),
            (r'(삼성카드)', "삼성카드"),
            (r'(현대카드)', "현대카드"),
            (r'(kb국민카드|국민카드)', "KB국민카드"),
            (r'(하나카드)', "하나카드"),
            
            # 페이 서비스
            (r'(카카오페이)', "카카오페이"),
            (r'(네이버페이)', "네이버페이"),
            (r'(토스)', "토스"),
            (r'(페이코)', "페이코"),
            
            # 프랜차이즈
            (r'(스타벅스)', "스타벅스"),
            (r'(맥도날드)', "맥도날드"),
            (r'(버거킹)', "버거킹"),
            (r'(롯데리아)', "롯데리아"),
            (r'(kfc)', "KFC"),
            (r'(피자헛)', "피자헛"),
            (r'(도미노피자)', "도미노피자"),
        ]
        
        for pattern, shop_name in coupon_patterns:
            if re.search(pattern, combined_text):
                logger.info(f" - Found coupon/reward pattern: '{shop_name}'")
                return shop_name
        
        # 2. 일반 쇼핑몰 패턴 확인
        shop_patterns = [
            (r'알리익스프레스|aliexpress', "알리"),
            (r'쿠팡|coupang', "쿠팡"),
            (r'지마켓|g마켓|gmarket', "G마켓"),
            (r'옥션|auction', "옥션"),
            (r'11번가|11st', "11번가"),
            (r'네이버.*?스토어|스마트스토어', "네이버"),
            (r'롯데온|lotteon', "롯데ON"),
            (r'티몬|tmon', "티몬"),
            (r'위메프|wemakeprice', "위메프"),
            (r'ssg|쓱', "SSG"),
            (r'큐텐|qoo10', "Qoo10"),
            (r'아마존|amazon', "Amazon"),
            (r'이베이|ebay', "eBay"),
        ]
        
        for pattern, shop_name in shop_patterns:
            if re.search(pattern, combined_text):
                logger.info(f" - Found shop pattern: '{shop_name}' from content")
                return shop_name
        
        # 3. 타이틀에서 대괄호 안의 내용 추출
        bracket_match = re.search(r'\[([^\]]+)\]', full_title)
        if bracket_match:
            bracket_content = bracket_match.group(1).strip()
            # 대괄호 안의 내용을 다시 clean_shop_name으로 확인
            cleaned_name = BaseScraper._clean_shop_name(bracket_content)
            if cleaned_name != '정보 없음' and cleaned_name != bracket_content:
                logger.info(f" - Using bracket content as shop name: '{cleaned_name}'")
                return cleaned_name
        
        logger.info(" - Could not infer shop name from content")
        return "정보 없음"
                
    def _process_detail_pages(self, temp_deals_info: list) -> list:
        deals_data = []
        for deal_info in temp_deals_info:
            post_link = deal_info.get('post_link', '')
            try:
                logger.info(f"Processing post_link: {post_link}")
                full_title_raw = deal_info['full_title']
                full_title = re.sub(r'\s*\(\d+\)$', '', full_title_raw).strip()
                logger.info(f"Processing deal: {full_title[:50]}...")
                self.driver.get(post_link)
                self.wait.until(EC.presence_of_element_located((By.TAG_NAME, 'body')))
                detail_soup = BeautifulSoup(self.driver.page_source, 'html.parser')
                
                possible_selectors = ['.view_content article', '#bo_v_atc', '.bo_v_con', 'div.view-content', 'td.board-contents', '.view_content']
                
                content_element = None
                for selector in possible_selectors:
                    content_element = detail_soup.select_one(selector)
                    if content_element:
                        logger.info(f"콘텐츠 요소를 찾았습니다 (selector: '{selector}')")
                        break
                
                if not content_element:
                    logger.warning(f"지정된 콘텐츠 선택자를 찾지 못했습니다. body 전체를 사용합니다: {post_link}")
                    content_element = detail_soup.select_one('body')

                if not content_element:
                    logger.error(f"Body 태그조차 찾을 수 없습니다. 이 게시물을 건너뜁니다: {post_link}")
                    continue
                    
                content_html = str(content_element)

                category_from_list = deal_info.get('list_category')
                shop_name_from_list = deal_info.get('list_shop_name', '정보 없음')
                price_from_list = deal_info.get('list_price') or self._extract_price_from_title(full_title)
                shipping_fee_from_list = deal_info.get('list_shipping_fee') or self._extract_shipping_from_title(full_title)
                
                category_ai_result = ai_parser.parse_title_with_ai(full_title) or {}
                category = self._clean_text(category_from_list or category_ai_result.get('category', '기타'))
                
                ai_result = ai_parser.parse_content_with_ai(content_html=content_html, post_link=post_link, original_title=full_title)
                if not ai_result or not ai_result.get('deals'):
                    logger.warning(f"AI parsing failed for link: {post_link}")
                    continue
                
                logger.info(f"  [AI Multi-Deal Analysis] Found {len(ai_result['deals'])} deals.")
                
                # --- ✨ [수정] 이미지 대표 이미지 결정 로직 ---
                list_image_url = deal_info.get('original_image_url')
                valid_images_from_content = [urljoin(self.base_url, img.get('src') or '') for img in content_element.select('img') if img.get('src')]
                
                # 유효하지 않은 이미지(placeholder, icon 등) 필터링
                valid_images_from_content = [img for img in valid_images_from_content if not re.search(r'icon|emoticon|expand', img)]

                # 대표 이미지를 하나만 선택
                post_representative_image = None
                if list_image_url:
                    post_representative_image = list_image_url
                elif valid_images_from_content:
                    post_representative_image = valid_images_from_content[0]

                logger.info(f"  [Image Debug] Post Representative Image: {post_representative_image}")
                # --- 수정 완료 ---

                group_id = hashlib.md5(post_link.encode()).hexdigest()

                for idx, deal_item in enumerate(ai_result['deals']):
                    product_title = self._clean_text(deal_item.get('product_title'))
                    logger.info(f"    [Deal {idx+1}] AI Product: {product_title}")
                    
                    final_price = self._get_final_price(price_from_list=price_from_list, price_from_ai=deal_item.get('price', '정보 없음'))
                    final_shipping_fee = self._get_final_shipping_fee(shipping_fee_from_list=shipping_fee_from_list, shipping_fee_from_ai=deal_item.get('shipping_fee', '정보 없음'))
                    
                    shop_name = shop_name_from_list
                    if shop_name == '정보 없음': shop_name = self._clean_shop_name(ai_result.get('shop_name'))

                    raw_ecommerce_link = deal_item.get('ecommerce_link', '')
                    final_ecommerce_link = self._resolve_redirect(raw_ecommerce_link)
                    
                    if final_ecommerce_link:
                        inferred_shop_from_link = self._infer_shop_name_from_link(final_ecommerce_link)
                        if inferred_shop_from_link != '정보 없음':
                            shop_name = inferred_shop_from_link
                    
                    if shop_name == '정보 없음':
                        inferred_shop_from_content = self._infer_shop_name_from_content(full_title, content_html)
                        if inferred_shop_from_content != '정보 없음':
                            shop_name = inferred_shop_from_content

                    # 모든 딜에 동일한 대표 이미지를 할당
                    web_path, _, _ = self._download_and_get_local_path(post_representative_image, -1, perform_ocr=False)
                    
                    is_options_deal = deal_item.get('deal_type') == 'Type A: Options Deal'
                    options_data_json = json.dumps(deal_item.get('options', [])) if is_options_deal else None
                    base_product_name = deal_item.get('base_product_name') if is_options_deal else product_title
                    
                    new_deal = models.Deal(
                        source_community_id=self.community_entry.id, title=product_title, post_link=post_link,
                        ecommerce_link=final_ecommerce_link, shop_name=shop_name, price=final_price,
                        shipping_fee=final_shipping_fee, category=category, is_closed=deal_item.get('is_closed', False),
                        deal_type=deal_item.get('deal_type', '일반'), image_url=web_path, content_html=content_html,
                        group_id=group_id, has_options=is_options_deal, options_data=options_data_json,
                        base_product_name=base_product_name
                    )
                    deals_data.append({'deal': new_deal, 'original_image_url': post_representative_image})
            except Exception as e:
                logger.error(f"Error processing {post_link}: {e}", exc_info=True)
        return deals_data

    def _clean_text(self, text_input):
        if not text_input or not str(text_input).strip(): return "정보 없음"
        return str(text_input).strip()

    def _ocr_image(self, image_path: str) -> str:
        if not image_path or not os.path.exists(image_path): return ""
        try:
            result = BaseScraper.ocr_reader.readtext(image_path)
            image_text = ' '.join([text for _, text, _ in result])
            if image_text.strip(): logger.debug(f"[OCR] Extracted: '{image_text.strip()[:100]}...'")
            return image_text
        except Exception as e:
            logger.warning(f"[OCR] Failed for image {image_path}: {e}")
            return ""

    def _resolve_redirect(self, url: str) -> str:
        if not url or not isinstance(url, str):
            logger.info("   - No ecommerce_link provided by AI.")
            return ''
        original_url = url

        # --- ✨ [수정] 비정상적인 URL을 처리하는 로직 보강 ---
        if url.startswith('http:s'):
            url = url.replace('http:s', 'https', 1)
        elif url.startswith('https.'):
            url = url.replace('https.', 'https://', 1)
        elif url.startswith('http.'):
            url = url.replace('http.', 'http://', 1)
        # 'http:'로 시작하지만 'http://'가 아닌 경우를 처리하는 로직 추가
        elif url.startswith('http:') and not url.startswith('http://'):
            url = url.replace('http:', 'http://', 1)
        elif not url.startswith(('http://', 'https://')):
            url = 'https://' + url
        # --- 수정 완료 ---

        logger.info(f"   - [Redirect] 1. AI raw link: {original_url}")
        if original_url != url:
            logger.info(f"   - [Redirect] 1.1. Fixed URL: {url}")

        try:
            decoded_url = BaseScraper._parse_ecommerce_link(url)
            logger.info(f"   - [Redirect] 2. Decoded link: {decoded_url}")

            if not decoded_url.startswith(('http://', 'https://')):
                logger.warning(f"   - [Redirect] 3. Invalid URL after decoding. Returning as-is: {decoded_url}")
                return decoded_url

            self.driver.get(decoded_url)
            try:
                WebDriverWait(self.driver, 2).until(
                    lambda driver: driver.execute_script("return document.readyState") == "complete"
                )
            except Exception:
                logger.warning(f"   - [Redirect] Page load timeout for {decoded_url}. Proceeding anyway.")
            
            final_url = self.driver.current_url
            logger.info(f"   - [Redirect] 3. Final resolved URL: {final_url}")
            return final_url

        except Exception as e:
            logger.error(f"   - [Redirect] 3. Failed to resolve redirect for {url}: {e}", exc_info=True)
            try:
                current_url_on_fail = self.driver.current_url
                logger.warning(f"   - [Redirect] Error occurred. Returning current URL: {current_url_on_fail}")
                return current_url_on_fail
            except:
                return decoded_url if 'decoded_url' in locals() else url

    def _get_final_price(self, price_from_list: str, price_from_ai: str) -> str:
        # --- ✨ [수정] 가격 결정 로직 변경 ---
        list_price = self._clean_price(price_from_list)
        # 목록(제목)에서 가져온 가격이 유효하면 AI 결과와 상관없이 최우선으로 사용
        if list_price not in ["정보 없음", "가격 상이"]:
            return list_price
        
        # 목록 정보가 없을 때만 AI 가격 사용
        ai_price = self._clean_price(price_from_ai)
        if ai_price not in ["정보 없음", "가격 상이"] and not any(kw in price_from_ai for kw in ['할인', '쿠폰', '~', 'N/A', '적립']):
            return ai_price
            
        return ai_price
        # --- 수정 완료 ---

    def _get_final_shipping_fee(self, shipping_fee_from_list: str, shipping_fee_from_ai: str) -> str:
        if shipping_fee_from_list != '정보 없음':
            return shipping_fee_from_list
        cleaned_ai_fee = self._clean_shipping_fee(shipping_fee_from_ai)
        if cleaned_ai_fee != '정보 없음':
            return cleaned_ai_fee
        return "정보 없음"