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
            community_name="퀘이사존",
            community_url="https://quasarzone.com/bbs/qb_saleinfo"
        )

    def scrape(self):
        """
        퀘이사존 목록 페이지에서 딜 정보를 수집합니다.
        """
        if not self.driver:
            self._create_selenium_driver()
            
        try:
            logger.info(f"[{self.community_name}] 페이지 접속 시도: {self.community_url}")
            self.driver.get(self.community_url)
            
            # 페이지 로딩 대기 (게시글 목록 컨테이너 대기)
            WebDriverWait(self.driver, 20).until(
                EC.presence_of_element_located((By.CSS_SELECTOR, "div.market-info-type-list table tbody tr"))
            )
            
        except Exception as e:
            logger.error(f"[{self.community_name}] 페이지 로딩 실패: {e}")
            return []

        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        
        # 목록 항목 선택
        post_rows = soup.select('div.market-info-type-list table tbody tr')
        logger.info(f"[{self.community_name}] Found {len(post_rows)} potential rows.")

        temp_deals_info = []
        for i, row in enumerate(post_rows):
            # 1. 공지사항 제외 (label 텍스트가 '공지'인 경우)
            label_tag = row.select_one('span.label')
            if label_tag and '공지' in label_tag.get_text():
                logger.info(f"Row {i}: Skipped (Notice)")
                continue

            # 2. 타이틀 및 링크 추출 (subject-link 클래스 사용)
            title_element = row.select_one('a.subject-link')
            if not title_element:
                continue

            href = title_element.get('href', '')
            if not href or href.startswith('javascript'):
                continue
            
            post_link = urljoin(self.base_url, href)
            
            # 실제 제목은 span.ellipsis-with-reply-cnt 내부에 있음 (댓글 수 제외를 위함)
            title_span = title_element.select_one('span.ellipsis-with-reply-cnt')
            if title_span:
                full_title_text = title_span.get_text(strip=True)
            else:
                full_title_text = title_element.get_text(strip=True)
            
            logger.info(f"[{self.community_name}] 게시글 발견: {full_title_text}")

            # 3. 추가 정보 (카테고리 등)
            category_tag = row.select_one('span.category')
            category_text = category_tag.get_text(strip=True) if category_tag else None

            temp_deals_info.append({
                'post_link': post_link,
                'full_title': full_title_text,
                'list_category': category_text
            })

            if self.limit and len(temp_deals_info) >= self.limit:
                break

        return self._process_detail_pages(temp_deals_info)

    def get_post_details(self, post_url):
        """퀘이사존 전용 게시글 상세 정보 추출"""
        logger.info(f"[{self.community_name}] 게시글 상세 추출 시작: {post_url[:50]}...")
        
        try:
            if not self.driver:
                self._create_selenium_driver()
                
            self.driver.get(post_url)
            self.wait.until(EC.presence_of_element_located((By.TAG_NAME, 'body')))
            
            soup = BeautifulSoup(self.driver.page_source, 'html.parser')
            soup['data-url'] = post_url
            
            # 퀘이사존 전용 최적화 설정
            site_config = {
            "content_selectors": ['.view-content', '.board-view-content', 'div.view-content', '.note-editor'],
            "time_selectors": ['.date', '.time', '.board-view-time'],
            "time_patterns": [
                r'(\d{4}\.\d{2}\.\d{2}\s+\d{2}:\d{2})',
                r'(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2})',
                r'(\d{2}\.\d{2}\.\d{2}\s+\d{2}:\d{2})'
            ],
            "exclude_image_keywords": ['btn_', 'icon_', 'button_', 'logo_', 'quasarzone_'],
            "text_selectors": ['p', 'div']
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
