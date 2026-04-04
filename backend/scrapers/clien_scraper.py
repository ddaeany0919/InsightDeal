import logging
import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class ClienScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        super().__init__("클리앙", max_concurrent_requests=5)
        self.community_id = community_id
        self.list_url = "https://www.clien.net/service/board/jirum"

    async def parse_list(self, html: str) -> list[dict]:
        """클리앙 알뜰구매 게시판 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        post_rows = soup.select('div.list_item:not(.notice)')

        deals = []
        for item in post_rows:
            # 광고글 제거
            if item.select_one('.label_ad'):
                continue

            # 품절 제거
            sold_out_tag = item.select_one('span.icon_info')
            if sold_out_tag and '품절' in sold_out_tag.get_text(strip=True):
                continue
            
            title_element = item.select_one('a[data-role="list-title-text"]')
            if not title_element:
                continue

            url = urljoin("https://www.clien.net", title_element['href'])
            full_title = title_element.get_text(strip=True)

            deals.append({
                "title": full_title,
                "url": url,
                "price": 0,
                "shop_name": ""
            })
            
        return deals

    async def get_detail(self, url: str) -> dict:
        """상세 페이지 데이터 파싱 로직 (추후 고도화)"""
        pass
