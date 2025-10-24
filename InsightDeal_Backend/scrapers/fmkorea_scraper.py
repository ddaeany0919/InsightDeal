import re
import time
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from .base import BaseScraper
from datetime import datetime
import logging

logger = logging.getLogger(__name__)

class FmkoreaScraper(BaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="펨코",
            community_url="https://www.fmkorea.com/hotdeal"
        )

    def scrape(self):
        """펨코 목록 페이지에서 딜 정보를 수집하고, 공통 처리 함수에 위임합니다."""
        # 무한스크롤 형태 대응: 간단 스크롤 3회
        for i in range(3):
            self.driver.execute_script(f"window.scrollBy(0, {1000 * (i + 1)});")
            time.sleep(1)

        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_items = soup.select('div.fm_best_widget._bd_pc > ul > li')
        if self.limit:
            post_items = post_items[:self.limit]

        temp_deals_info = []
        for li in post_items:
            title_tag = li.select_one('h3.title a')
            if not title_tag:
                continue
            post_link = urljoin(self.base_url, title_tag['href'])
            full_title = title_tag.get_text(strip=True)

            temp_deals_info.append({
                'post_link': post_link,
                'full_title': full_title
            })

        return self._process_detail_pages(temp_deals_info)

    # ✅ 새로 추가된 메서드: 펨코 전용 게시글 상세 정보 추출
    def get_post_details(self, post_url):
        """펨코 전용 게시글 상세 정보 추출"""
        logger.info(f"[{self.community_name}] 게시글 상세 추출 시작: {post_url[:50]}...")
        try:
            if not self.driver:
                self._create_selenium_driver()

            self.driver.get(post_url)
            self.wait.until(EC.presence_of_element_located((By.TAG_NAME, 'body')))

            soup = BeautifulSoup(self.driver.page_source, 'html.parser')
            soup['data-url'] = post_url

            site_config = {
                "content_selectors": ['.rd_body', '.xe_content', '.content'],
                "time_selectors": ['.date', '.regdate', '.rd_hd .time'],
                "time_patterns": [r'(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2})'],
                "exclude_image_keywords": ['icon_', 'btn_', 'fmkorea_', 'emoticon'],
                "text_selectors": ['p', 'div']
            }

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
