import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from .base import BaseScraper
from datetime import datetime
import logging

# 로거 설정
logger = logging.getLogger(__name__)

class QuasarzoneScraper(BaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="퀸이사존",
            community_url="https://quasarzone.com/bbs/qb_saleinfo"
        )

    def scrape(self):
        """
        퀸이사존 목록 페이지에서 딜 정보를 수집하고,
        상세 분석은 BaseScraper의 공통 처리 함수에 위임합니다.
        """
        # 1. 목록 페이지 로딩 및 기본 정보 수집
        WebDriverWait(self.driver, 15).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "div.market-info-list"))
        )

        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_rows = soup.select('div.market-info-list')

        if self.limit:
            post_rows = post_rows[:self.limit]

        temp_deals_info = []
        for row in post_rows:
            # 공지 게시물 제외
            if 'notice' in row.find_parent('tr').get('class', []):
                continue

            tit_element = row.select_one('p.tit a.subject-link')
            if not tit_element:
                continue

            post_link = urljoin(self.base_url, tit_element['href'])
            raw_title = tit_element.get_text(strip=True)
            full_title_text = re.sub(r'\\s*댓글\\s*\\[\\d+\\]$', '', raw_title).strip()

            price_span = row.select_one('span.text-orange')
            price = self._clean_price(price_span.text.strip() if price_span else '정보 없음')

            shipping_fee_span = row.find('span', string=re.compile(r'배송비'))
            shipping_fee = self._clean_shipping_fee(shipping_fee_span.text.replace('배송비', '').strip() if shipping_fee_span else '정보 없음')

            shop_name_span = row.select_one('span.brand')
            shop_name = self._clean_shop_name(shop_name_span.get_text(strip=True) if shop_name_span else '정보 없음')

            temp_deals_info.append({
                'post_link': post_link,
                'full_title': full_title_text,
                'list_price': price,
                'list_shipping_fee': shipping_fee,
                'list_shop_name': shop_name,
            })

        # 2. 수집된 기본 정보를 바탕으로 공통 상세 페이지 처리 함수 호출
        return self._process_detail_pages(temp_deals_info)

    # ✅ 새로 추가된 메서드: 퀸이사존 전용 게시글 상세 정보 추출
    def get_post_details(self, post_url):
        """퀸이사존 전용 게시글 상세 정보 추출"""
        logger.info(f"[{self.community_name}] 게시글 상세 추출 시작: {post_url[:50]}...")
        
        try:
            # Selenium으로 페이지 로드
            if not self.driver:
                self._create_selenium_driver()
                
            self.driver.get(post_url)
            self.wait.until(EC.presence_of_element_located((By.TAG_NAME, 'body')))
            
            soup = BeautifulSoup(self.driver.page_source, 'html.parser')
            soup['data-url'] = post_url  # URL 정보 추가
            
            # ✅ 퀸이사존 전용 최적화 설정
            site_config = {
                "content_selectors": ['.content', '.editor-content', '.post-content', '.market-content'],
                "time_selectors": ['.date', '.timestamp', '.post-date', '.view-date'],
                "time_patterns": [
                    r'(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})',   # 2024-10-23 21:10
                    r'(\\d{4}\\.\\d{2}\\.\\d{2}\\s+\\d{2}:\\d{2})', # 2024.10.23 21:10
                    r'(\\d{2}-\\d{2}-\\d{4}\\s+\\d{2}:\\d{2})'      # 23-10-2024 21:10
                ],
                "exclude_image_keywords": ['icon_', 'btn_', 'button_', 'logo_', 'qz_'],
                "text_selectors": ['p', 'div']  # 퀸이사존은 p, div 태그 사용
            }
            
            # base.py의 공통 메서드 활용
            result = self.extract_post_content_and_images(
                soup, 
                site_config["content_selectors"],
                self.base_url,
                site_config
            )
            
            logger.info(f"[{self.community_name}] 추출 완료! 이미지: {len(result['images'])}개, 텍스트: {len(result['content'])}자")
            return result
            
        except Exception as e:
            logger.error(f"[{self.community_name}] 추출 실패: {e}")
            return {
                "images": [],
                "content": "게시글 정보를 불러올 수 없습니다.",
                "posted_time": None,
                "error": str(e),
                "crawled_at": datetime.now().isoformat()
            }