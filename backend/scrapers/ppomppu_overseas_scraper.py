import logging
import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class PpomppuOverseasScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        super().__init__("뽐뿌 해외", max_concurrent_requests=5)
        self.community_id = community_id
        self.list_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4"

    async def parse_list(self, html: str) -> list[dict]:
        """뽐뿌 해외 게시판 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        post_rows = soup.select('tr.baseList')

        deals = []
        for row in post_rows:
            title_element = row.select_one('a.baseList-title')
            if not title_element: continue

            href = title_element.get('href', '')
            if not href: continue

            url = urljoin("https://www.ppomppu.co.kr/zboard/", href)
            full_title = title_element.get_text(strip=True)

            # 실제 게시글 작성 시간 추출
            posted_at_iso = None
            time_tds = row.select('nobr.eng, td.eng, td.baseList-time, time.baseList-time')
            time_str = ""
            for td in time_tds:
                txt = td.get_text(strip=True)
                if ':' in txt or '/' in txt or '-' in txt:
                    time_str = txt
                    break

            if time_str:
                posted_at_iso = self.parse_time_str(time_str)

            deals.append({
                "title": full_title,
                "url": url,
                "price": 0,
                "shop_name": "",
                "posted_at": posted_at_iso
            })
            
        return deals

    async def get_detail(self, url: str) -> dict:
        """상세 페이지 데이터 파싱 로직 (추후 고도화)"""
        pass
