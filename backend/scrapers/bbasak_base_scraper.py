import re
import logging
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from .base import BaseScraper
from datetime import datetime

# 로거 설정
logger = logging.getLogger(__name__)

class BbasakBaseScraper(BaseScraper):
    def __init__(self, db_session, community_name, community_url):
        """빠삭 베이스 스크래퍼 초기화"""
        super().__init__(
            db_session,
            community_name=community_name,
            community_url=community_url
        )

    def scrape(self):
        """빠삭 목록 페이지에서 딜 정보를 수집"""
        logger.info(f"[{self.community_name}] 빠삭 목록 스크래핑 시작...")
        
        WebDriverWait(self.driver, 15).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "div.board-list"))
        )

        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_rows = soup.select('div.board-item')

        if self.limit:
            post_rows = post_rows[:self.limit]

        temp_deals_info = []
        for row in post_rows:
            title_element = row.select_one('a.title-link')
            if not title_element:
                continue

            post_link = urljoin(self.base_url, title_element['href'])
            full_title_text = title_element.get_text(strip=True)

            # 빠삭 목록에서 이미지 수집
            image_tag = row.select_one('img.thumbnail')
            original_image_url = urljoin(self.base_url, image_tag['src']) if image_tag else None

            temp_deals_info.append({
                'post_link': post_link,
                'full_title': full_title_text,
                'original_image_url': original_image_url
            })

        logger.info(f"Found {len(temp_deals_info)} potential deals from the list page.")
        return self._process_detail_pages(temp_deals_info)

    def get_post_details(self, post_url):
        """빠삭 전용 게시글 상세 정보 추출"""
        logger.info(f"[{self.community_name}] 게시글 상세 추출 시작: {post_url[:50]}...")
        
        try:
            # Selenium으로 페이지 로드
            if not self.driver:
                self._create_selenium_driver()
                
            self.driver.get(post_url)
            self.wait.until(EC.presence_of_element_located((By.TAG_NAME, 'body')))
            
            soup = BeautifulSoup(self.driver.page_source, 'html.parser')
            soup['data-url'] = post_url  # URL 정보 추가
            
            # 빠삭 전용 최적화 설정
            site_config = {
                "content_selectors": ['.content', '.post_content', '.article_content', '.board-content'],
                "time_selectors": ['.date', '.timestamp', '.post-date', '.view-date'],
                "time_patterns": [
                    r'(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2})',   # 2024-10-23 21:10
                    r'(\d{4}\.\d{2}\.\d{2}\s+\d{2}:\d{2})', # 2024.10.23 21:10
                    r'(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2})'      # 23/10/2024 21:10
                ],
                "exclude_image_keywords": ['icon_', 'btn_', 'button_', 'logo_', 'bbasak_'],
                "text_selectors": ['p', 'div']  # 빠삭은 p, div 태그 사용
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
