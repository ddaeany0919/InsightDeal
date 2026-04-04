import logging
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class BbasakBaseScraper(AsyncBaseScraper):
    def __init__(self, community_name: str, community_url: str, community_id: int = 0):
        super().__init__(community_name, max_concurrent_requests=5)
        self.community_id = community_id
        self.list_url = community_url

    async def parse_list(self, html: str) -> list[dict]:
        """빠삭 게시판 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        
        tables = soup.select('table.t1')
        post_rows = []
        # 빠삭은 보통 두 번째 테이블이 메인 리스트입니다.
        if len(tables) >= 2:
            post_rows = tables[1].select('tbody tr')
        elif len(tables) == 1:
             post_rows = tables[0].select('tbody tr')

        deals = []
        for row in post_rows:
            title_element = row.select_one('td.tit a')
            if not title_element: continue

            href = title_element.get('href')
            if not href: continue

            url = urljoin("https://bbasak.com", href)
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
