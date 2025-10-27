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

class PpomppuOverseasScraper(BaseScraper):
    def __init__(self, db_session):
        """해외뽐뿌 스크래퍼 초기화"""
        super().__init__(
            db_session,
            community_name="해외뽐뿌",
            community_url="https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu3"
        )

    def scrape(self):
        """해외뽐뿌 목록 페이지에서 딜 정보를 수집"""
        logger.info(f"[{self.community_name}] 딜 목록 스크래핑 시작...")
        
        WebDriverWait(self.driver, 15).until(
            EC.presence_of_element_located((By.ID, "revolution_main_table"))
        )

        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_rows = soup.select('tr.baseList')

        if self.limit:
            post_rows = post_rows[:self.limit]

        temp_deals_info = []
        for row in post_rows:
            general_tag = row.select_one('small.baseList-small')
            if general_tag and '[일반]' in general_tag.get_text(strip=True):
                continue

            title_element = row.select_one('a.baseList-title')
            if not title_element:
                continue

            post_link = urljoin(self.community_url, title_element['href'])
            full_title_text = title_element.get_text(strip=True)

            # 목록 페이지에서 썸네일 이미지 URL 수집
            image_tag = row.select_one('img.thumb_border')
            original_image_url = urljoin(self.base_url, image_tag['src']) if image_tag else None

            temp_deals_info.append({
                'post_link': post_link,
                'full_title': full_title_text,
                'original_image_url': original_image_url
            })

        logger.info(f"Found {len(temp_deals_info)} potential deals from the list page.")
        return self._process_detail_pages(temp_deals_info)

    def get_post_details(self, post_url):
        """해외뽐뿌 전용 게시글 상세 정보 추출"""
        logger.info(f"[{self.community_name}] 게시글 상세 추출 시작: {post_url[:50]}...")
        
        try:
            # Selenium으로 페이지 로드
            if not self.driver:
                self._create_selenium_driver()
                
            self.driver.get(post_url)
            self.wait.until(EC.presence_of_element_located((By.TAG_NAME, 'body')))
            
            soup = BeautifulSoup(self.driver.page_source, 'html.parser')
            soup['data-url'] = post_url  # URL 정보 추가
            
            # 해외뽐뿌 전용 최적화 설정 (뽐뿌와 동일)
            site_config = {
                "content_selectors": ['td.board-contents', '.board-contents', '.view_content'],
                "time_selectors": ['li:contains("등록일")', 'li', '.date', '.post-date'],
                "time_patterns": [r'등록일\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2})'],
                "exclude_image_keywords": ['icon_expand_img.png', 'icon_', 'emoticon'],
                "text_selectors": ['p']  # 뽐뿌 계열은 p 태그만 사용
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
