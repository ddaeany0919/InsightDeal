import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from .base import BaseScraper

class ClienScraper(BaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="클리앙",
            community_url="https://www.clien.net/service/board/jirum"
        )

    def scrape(self):
        """
        클리앙 목록 페이지에서 딜 정보를 수집하고,
        상세 분석은 BaseScraper의 공통 처리 함수에 위임합니다.
        """
        # 1. 목록 페이지 로딩 및 기본 정보 수집
        WebDriverWait(self.driver, 15).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "div.list_item.symph_row"))
        )
        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_rows = soup.select('div.list_item.symph_row')

        if self.limit:
            post_rows = post_rows[:self.limit]
        
        temp_deals_info = []
        for row in post_rows:
            # 품절 게시물 제외
            sold_out_tag = row.select_one('span.icon_info')
            if sold_out_tag and '품절' in sold_out_tag.get_text(strip=True):
                continue

            title_element = row.select_one('a[data-role="list-title-text"]')
            if not title_element: 
                continue
            
            post_link = urljoin(self.base_url, title_element['href'])
            full_title_text = title_element.get_text(strip=True)
            
            temp_deals_info.append({
                'post_link': post_link, 
                'full_title': full_title_text
            })

        # 2. 수집된 기본 정보를 바탕으로 공통 상세 페이지 처리 함수 호출
        return self._process_detail_pages(temp_deals_info)