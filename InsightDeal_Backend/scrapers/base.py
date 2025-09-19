import os
import re
import sys
import hashlib
import requests
import logging
import time
import ai_parser
import base64
from abc import ABC, abstractmethod
from urllib.parse import urlparse, urljoin, parse_qs

from bs4 import BeautifulSoup
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium_stealth import stealth
from sqlalchemy.orm import Session
import easyocr

import models
import database

# --- 로거 설정 ---
logger = logging.getLogger(__name__)
if not logger.handlers:
    logger.setLevel(logging.INFO)
    handler = logging.StreamHandler()
    formatter = logging.Formatter('[%(levelname)s] %(asctime)s - %(message)s', '%Y-%m-%d %H:%M:%S')
    handler.setFormatter(formatter)
    logger.addHandler(handler)

# --- 전역 설정 ---
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_CACHE_DIR = os.path.join(os.path.dirname(SCRIPT_DIR), "image_cache")
if not os.path.exists(IMAGE_CACHE_DIR):
    os.makedirs(IMAGE_CACHE_DIR)

def parse_ecommerce_link(raw_link: str) -> str:
    """
    PPOMPPU 등 커뮤니티 사이트 URL에서 target 파라미터 Base64 디코딩 후 실제 링크 추출
    """
    try:
        parsed_url = urlparse(raw_link)
        query_params = parse_qs(parsed_url.query)
        encoded_target = query_params.get("target", [None])[0]
        if encoded_target:
            decoded_bytes = base64.b64decode(encoded_target)
            return decoded_bytes.decode('utf-8')
        else:
            # target 파라미터 없으면 원 링크 그대로 반환
            return raw_link
    except Exception as e:
        logger.warning(f"Failed to parse ecommerce link: {e}")
        return raw_link

class BaseScraper(ABC):
    """
    모든 스크래퍼의 기반이 되는 추상 클래스.
    Selenium 드라이버 생성, DB 세션 관리, 공통 유틸리티 함수 등 공통 로직 포함.
    """

    ocr_reader = None

    def __init__(self, db_session: Session, community_name: str, community_url: str):
        self.db = db_session
        self.community_name = community_name
        self.community_url = community_url
        self.base_url = f"{urlparse(community_url).scheme}://{urlparse(community_url).netloc}"
        self.driver = None
        self.cookies = {}
        self.limit = None

        # 커뮤니티 엔트리 조회
        self.community_entry = (
            self.db.query(models.Community)
            .filter(models.Community.name == self.community_name)
            .first()
        )
        if not self.community_entry:
            raise ValueError(f"'{self.community_name}' 커뮤니티를 DB에서 찾을 수 없습니다.")

        # EasyOCR 리더 초기화 (클래스 변수로 공유)
        if BaseScraper.ocr_reader is None:
            logger.info("Initializing EasyOCR model... (This may take a moment on first run)")
            BaseScraper.ocr_reader = easyocr.Reader(['ko', 'en'])
            logger.info("EasyOCR model loaded successfully.")

    @abstractmethod
    def scrape(self):
        """
        커뮤니티 목록 페이지 스크래핑 메소드.
        하위 클래스에서 반드시 구현.
        """
        pass

    def run(self, limit=None):
        """
        전체 스크래핑 프로세스 실행
        :param limit: 처리할 최대 아이템 수 (Optional)
        """
        self.limit = limit
        logger.info(f"[{self.community_name}] Scraping process started.")
        try:
            self._create_selenium_driver()
            deals_data = self.scrape()

            if not deals_data:
                logger.info(f"[{self.community_name}] No new deals found.")
                return

            # 최신 순서로 저장
            deals_data.reverse()
            new_deals_count = 0
            for item in deals_data:
                deal_obj = item['deal']

                # 중복 확인
                exists = self.db.query(models.Deal).filter(
                    models.Deal.post_link == deal_obj.post_link,
                    models.Deal.title == deal_obj.title
                ).first()
                if exists:
                    continue

                # 이미지 다운로드 및 OCR 적용 판단
                original_image_url = item.get('original_image_url')
                ocr_needed = (deal_obj.shipping_fee == '정보 없음' and deal_obj.deal_type == '일반')
                web_path, _, ocr_text = self._download_and_get_local_path(
                    original_image_url, -1, perform_ocr=ocr_needed
                )
                deal_obj.image_url = web_path  # 이미지 경로 할당

                if ocr_needed and any(term in ocr_text for term in ("무료배송", "무료 배송")):
                    deal_obj.shipping_fee = "무료"

                self.db.add(deal_obj)
                self.db.flush()

                # 가격히스토리 기록 추가
                new_price_history = models.PriceHistory(deal_id=deal_obj.id, price=deal_obj.price)
                self.db.add(new_price_history)
                new_deals_count += 1

            self.db.commit()
            if new_deals_count > 0:
                logger.info(f"[{self.community_name}] Saved {new_deals_count} new deals to DB.")
            else:
                logger.info(f"[{self.community_name}] No new deals to save.")

        except Exception as e:
            logger.error(f"[{self.community_name}] An error occurred during scraping: {e}", exc_info=True)
            self.db.rollback()
        finally:
            if self.driver:
                self.driver.quit()

    def _create_selenium_driver(self):
        """
        셀레니움 크롬 드라이버 초기화 및 stealth 적용
        """
        chrome_options = Options()
        chrome_options.page_load_strategy = 'eager'
        chrome_options.add_argument("--headless")
        chrome_options.add_argument("--window-size=1920,1080")
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--disable-gpu")
        chrome_options.add_argument(
            'user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
            'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36'
        )
        chrome_options.add_experimental_option("excludeSwitches", ["enable-automation", "enable-logging"])
        chrome_options.add_experimental_option('useAutomationExtension', False)

        self.driver = webdriver.Chrome(options=chrome_options)
        stealth(
            self.driver,
            languages=["ko-KR", "ko"],
            vendor="Google Inc.",
            platform="Win32",
            webgl_vendor="Intel Inc.",
            renderer="Intel Iris OpenGL Engine",
            fix_hairline=True
        )
        self.driver.get(self.community_url)
        self.cookies = {c['name']: c['value'] for c in self.driver.get_cookies()}

    def _download_and_get_local_path(self, image_url, deal_id: int, perform_ocr: bool = True):
        """
        이미지 URL을 로컬에 저장하고 OCR 결과를 반환합니다.
        """
        placeholder = 'https://placehold.co/400x400/E9E2FD/333?text=Deal'
        if not isinstance(image_url, str) or not image_url or 'placehold.co' in image_url:
            return placeholder, None, ""
        try:
            url_hash = hashlib.md5(image_url.encode()).hexdigest()
            match = re.search(r'\.(jpg|jpeg|png|gif|webp)', urlparse(image_url).path, re.IGNORECASE)
            ext = match.group(0) if match else '.jpg'
            filename = f"{url_hash}{ext}"
            save_path = os.path.join(IMAGE_CACHE_DIR, filename)

            if not os.path.exists(save_path):
                headers = {
                    'Referer': f"{urlparse(image_url).scheme}://{urlparse(image_url).netloc}/",
                    'User-Agent': self.driver.execute_script("return navigator.userAgent;")
                }
                resp = requests.get(image_url, headers=headers, cookies=self.cookies, stream=True, timeout=15)
                resp.raise_for_status()
                with open(save_path, 'wb') as f:
                    for chunk in resp.iter_content(chunk_size=8192):
                        f.write(chunk)

            ocr_text = self._ocr_image(save_path) if perform_ocr else ""
            return f"/images/{filename}", save_path, ocr_text

        except Exception as e:
            logger.warning(f"[ImageProcessing] Failed for URL: {image_url}, Error: {e}")
            return placeholder, None, ""

    @staticmethod
    def _clean_price(price_input):
        """
        가격 문자열 정제 함수
        """
        price_str = str(price_input).strip()
        if not price_str or "정보 없음" in price_str or "상이" in price_str:
            return "가격 상이"

        # 달러 가격 처리
        is_dollar = ('$' in price_str or '.' in price_str) and '원' not in price_str
        if is_dollar:
            try:
                num = re.sub(r'[^\d.]', '', price_str)
                return f"${float(num):.2f}"
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
        """
        배송비 문자열 정제 함수
        """
        shipping_str = str(shipping_input).strip()
        if not shipping_str or "정보 없음" in shipping_str:
            return "정보 없음"
        for part in re.split(r'[,()]', shipping_str):
            p = part.lower().strip()
            if '포함' in p:
                return "배송비 포함"
            if any(k in p for k in ['무료', '무배', 'free']):
                return "무료"
        if any(k in shipping_str for k in ["배송비", "착불"]) and any(c.isdigit() for c in shipping_str):
            return shipping_str
        return "정보 없음"

    @staticmethod
    def _extract_shipping_from_title(title: str) -> str:
        """
        제목에서 배송비 정보 추출
        """
        match = re.search(r'\(([^)]+)\)$', title)
        if match:
            for part in match.group(1).split('/'):
                fee = BaseScraper._clean_shipping_fee(part)
                if fee != '정보 없음':
                    return fee
        if any(k in title for k in ['무배', '무료배송', '택배비 포함', '무료']):
            return "무료"
        return "정보 없음"

    @staticmethod
    def _extract_price_from_title(title: str) -> str:
        """
        제목에서 가격 정보 추출
        """
        match = re.search(r'\(([^)]+)\)$', title)
        if match:
            for part in match.group(1).split('/'):
                price = BaseScraper._clean_price(part)
                if price not in ["가격 상이", "정보 없음"]:
                    return price
        return "정보 없음"

    @staticmethod
    def _clean_shop_name(shop_name_input):
        """
        상점 이름 표준화
        """
        shop_name_str = str(shop_name_input).strip()
        if not shop_name_str:
            return "정보 없음"
        lower = shop_name_str.lower()
        shop_map = {
            "알리익스프레스": "알리", "aliexpress": "알리", "알리": "알리",
            "네이버 스마트스토어": "네이버", "naver smartstore": "네이버", "스마트스토어": "네이버", "naver": "네이버",
            "g마켓": "G마켓", "gmarket": "G마켓",
            "옥션": "옥션", "auction": "옥션",
            "11번가": "11번가", "11st": "11번가",
            "쿠팡": "쿠팡", "coupang": "쿠팡",
            "롯데온": "롯데온", "lotteon": "롯데온",
            "티몬": "티몬", "tmon": "티몬",
            "costco": "Costco", "코스트코": "Costco",
            "wish": "Wish",
        }
        for alias, name in shop_map.items():
            if alias in lower:
                return name
        return shop_name_str

    def _process_detail_pages(self, temp_deals_info: list) -> list:
        deals_data = []
        for deal_info in temp_deals_info:
            post_link = deal_info.get('post_link', '')
            try:
                logger.info(f"Processing post_link: {post_link}")  # 상세보기 링크 로그

                full_title_raw = deal_info['full_title']
                full_title = re.sub(r'\s*\(\d+\)$', '', full_title_raw).strip()
                logger.info(f"Processing deal: {full_title[:50]}...")

                self.driver.get(post_link)
                time.sleep(0.5)
                detail_soup = BeautifulSoup(self.driver.page_source, 'html.parser')
                content_element = detail_soup.select_one('td.board-contents, div.view_content')
                content_text = content_element.get_text(strip=True, separator='\n') if content_element else ""

                category_ai_result = ai_parser.parse_title_with_ai(full_title) or {}
                category = self._clean_text(category_ai_result.get('category', '기타'))

                shop_name_code = self._clean_shop_name(re.search(r'^\[(.*?)\]', full_title).group(1).strip() if re.search(r'^\[(.*?)\]', full_title) else '정보 없음')
                price_code = self._extract_price_from_title(full_title)
                shipping_fee_from_title = self._extract_shipping_from_title(full_title)

                ai_result = ai_parser.parse_content_with_ai(
                    content_text=content_text, post_link=post_link,
                    original_title=full_title
                )
                if not ai_result or not ai_result.get('deals'):
                    logger.warning(f"AI parsing failed for link: {post_link}")
                    continue

                logger.info(f"AI parsed ecommerce links:")
                for idx, deal_item in enumerate(ai_result['deals']):
                    logger.info(f"  Deal {idx} ecommerce_link: {deal_item.get('ecommerce_link')}")

                valid_images = [urljoin(self.base_url, img.get('src') or '') for img in (content_element.select('img') if content_element else []) if img.get('src')]

                group_id = hashlib.md5(post_link.encode()).hexdigest()

                for idx, deal_item in enumerate(ai_result['deals']):
                    product_title = self._clean_text(deal_item.get('product_title'))
                    ai_price_raw = deal_item.get('price', '정보 없음')
                    ai_shipping_raw = deal_item.get('shipping_fee', '정보 없음')
                    deal_type = deal_item.get('deal_type', '일반')

                    final_shipping_fee = shipping_fee_from_title
                    assigned_image_url = valid_images[idx] if idx < len(valid_images) else None

                    ocr_needed = (final_shipping_fee == '정보 없음' and deal_type == '일반')
                    web_path, _, image_text = self._download_and_get_local_path(assigned_image_url, -1, perform_ocr=ocr_needed)

                    if ocr_needed and any(term in image_text for term in ("무료배송", "무료 배송")):
                        final_shipping_fee = "무료"

                    if final_shipping_fee == '정보 없음':
                        final_shipping_fee = self._clean_shipping_fee(ai_shipping_raw)

                    final_price = self._clean_price(price_code)
                    if final_price in ["정보 없음", "가격 상이"] or any(kw in ai_price_raw for kw in ['할인', '쿠폰', '~', 'N/A', '적립']):
                        final_price = self._clean_price(ai_price_raw)
                    if final_price in ["정보 없음", "가격 상이"]:
                        final_price = self._clean_price(price_code)

                    shop_name = deal_info.get('list_shop_name') or shop_name_code
                    if shop_name == '정보 없음':
                        shop_name = self._clean_shop_name(ai_result.get('shop_name'))

                    raw_ecommerce_link = deal_item.get('ecommerce_link', '')
                    decoded_ecommerce_link = parse_ecommerce_link(raw_ecommerce_link)
                    logger.info(f"Decoded ecommerce_link: {decoded_ecommerce_link}")

                    new_deal = models.Deal(
                        source_community_id=self.community_entry.id,
                        title=product_title,
                        post_link=post_link,
                        ecommerce_link=decoded_ecommerce_link,
                        shop_name=shop_name,
                        price=final_price,
                        shipping_fee=final_shipping_fee,
                        category=category,
                        is_closed=deal_item.get('is_closed', False),
                        deal_type=deal_type,
                        image_url=web_path,
                        content_html=str(content_element),
                        group_id=group_id
                    )
                    deals_data.append({'deal': new_deal, 'original_image_url': assigned_image_url})

            except Exception as e:
                logger.error(f"Error processing {post_link}: {e}", exc_info=True)
        return deals_data


    def _clean_text(self, text_input):
        """텍스트 입력을 정리하여 반환"""
        if not text_input or not str(text_input).strip():
            return "정보 없음"
        return str(text_input).strip()

    def _ocr_image(self, image_path: str) -> str:
        """EasyOCR로 이미지 내 텍스트 추출"""
        if not image_path or not os.path.exists(image_path):
            return ""
        try:
            result = BaseScraper.ocr_reader.readtext(image_path)
            image_text = ' '.join([text for _, text, _ in result])
            if image_text.strip():
                logger.debug(f"[OCR] Extracted: '{image_text.strip()[:100]}...'")
            return image_text
        except Exception as e:
            logger.warning(f"[OCR] Failed for image {image_path}: {e}")
            return ""
