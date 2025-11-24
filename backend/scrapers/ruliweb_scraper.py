import re
import time
import logging
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from .base import BaseScraper
from datetime import datetime
from database import models
from core import ai_parser

# 로거 설정
logger = logging.getLogger(__name__)

class RuliwebScraper(BaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="루리웹",
            community_url="https://bbs.ruliweb.com/market/board/1020"
        )

    def _parse_list_page(self, soup):
        """루리웹 목록 페이지의 신/구 UI를 모두 파싱합니다."""
        post_rows = soup.select('tr.table_body.blocktarget:not(.best), a.board_list_item.deco:not(.notice)')
        if not post_rows: return []

        logger.info("Parsing ruliweb list page...")
        temp_deals_info = []
        processed_links = set()

        for row in post_rows:
            link_element = row.select_one('a.subject_link, a.board_list_item')
            if not link_element: continue

            post_link = urljoin(self.base_url, link_element['href'])
            if post_link in processed_links: continue
            processed_links.add(post_link)

            title_element = row.select_one('a.subject_link, span.subject')
            if not title_element: continue

            reply_count_tag = title_element.find("strong", class_="reply_count")
            if reply_count_tag: reply_count_tag.decompose()

            full_title_raw = title_element.get_text(strip=True)
            full_title = re.sub(r'\s*\(\d+\)$', '', full_title_raw).strip()

            category_tag = row.select_one('td.divsn a, span.divsn')
            category_text = category_tag.get_text(strip=True) if category_tag else None

            temp_deals_info.append({
                'post_link': post_link,
                'full_title': full_title,
                'list_category': category_text
            })

        return temp_deals_info

    def scrape(self):
        """루리웹 목록 페이지에서 딜 정보를 수집하고, 공통 처리 함수를 호출합니다."""
        WebDriverWait(self.driver, 15).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "table.board_list_table, a.board_list_item"))
        )

        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        temp_deals_info = self._parse_list_page(soup)

        if self.limit:
            temp_deals_info = temp_deals_info[:self.limit]

        # 상세 페이지 분석 및 DB 저장을 위한 공통 함수 호출
        return self._process_detail_pages(temp_deals_info)

    def get_post_details(self, post_url):
        """루리웹 전용 게시글 상세 정보 추출"""
        logger.info(f"[{self.community_name}] 게시글 상세 추출 시작: {post_url[:50]}...")
        
        try:
            # Selenium으로 페이지 로드
            if not self.driver:
                self._create_selenium_driver()
                
            self.driver.get(post_url)
            self.wait.until(EC.presence_of_element_located((By.TAG_NAME, 'body')))
            
            soup = BeautifulSoup(self.driver.page_source, 'html.parser')
            soup['data-url'] = post_url  # URL 정보 추가
            
            # ✅ 루리웹 전용 최적화 설정
            site_config = {
                "content_selectors": ['.view_content', '.article_content', '.post_content', '.board_content'],
                "time_selectors": ['.date', '.time', '.post-info', '.view_date'],
                "time_patterns": [
                    r'(\d{4}\.\d{2}\.\d{2}\s+\d{2}:\d{2})',  # 2024.10.23 21:10
                    r'(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2})',   # 2024-10-23 21:10
                    r'(\d{2}\.\d{2}\.\d{2}\s+\d{2}:\d{2})'  # 24.10.23 21:10
                ],
                "exclude_image_keywords": ['btn_', 'icon_', 'button_', 'logo_', 'ruliweb_'],
                "text_selectors": ['p', 'div']  # 루리웹은 p, div 태그 사용
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
