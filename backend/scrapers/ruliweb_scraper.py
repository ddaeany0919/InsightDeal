import logging
import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class RuliwebScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        super().__init__("루리웹", max_concurrent_requests=5)
        self.community_id = community_id
        self.list_url = "https://bbs.ruliweb.com/market/board/1020"

    async def parse_list(self, html: str) -> list[dict]:
        """루리웹 핫딜/예판 게시판 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        post_rows = soup.select('tr.table_body.blocktarget:not(.notice):not(.best), a.board_list_item.deco:not(.notice)')
        
        deals = []
        for row in post_rows:
            link_element = row.select_one('a.subject_link, a.board_list_item')
            if not link_element: continue

            url = urljoin("https://bbs.ruliweb.com", link_element['href'])

            title_element = row.select_one('a.subject_link, span.subject')
            if not title_element: continue

            # 댓글 카운트 제거
            reply_count_tag = title_element.find("strong", class_="reply_count")
            if reply_count_tag: reply_count_tag.decompose()

            full_title_raw = title_element.get_text(strip=True)
            full_title = re.sub(r'\s*\(\d+\)$', '', full_title_raw).strip()

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
