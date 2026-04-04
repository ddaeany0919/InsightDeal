import logging
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class PpomppuScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        super().__init__("뽐뿌", max_concurrent_requests=5)
        self.community_id = community_id
        self.list_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu"

    async def parse_list(self, html: str) -> list[dict]:
        """뽐뿌 게시판 리스트에서 제목과 URL (그리고 가능하면 가격) 추출"""
        soup = BeautifulSoup(html, 'html.parser')
        
        # 뽐뿌 핫딜 게시판 리스트 행
        post_rows = soup.select('tr.baseList, tr.list1, tr.list0')
        
        deals = []
        for row in post_rows:
            # 제목 추출
            title_el = row.select_one('a.baseList-title') or row.select_one('.list_title') or row.select_one('font')
            if not title_el:
                continue
                
            full_title = title_el.get_text(strip=True)
            if not full_title or "공지" in full_title:
                 continue
                 
            # 링크 추출
            link_el = row.select_one('a') if not title_el.has_attr('href') else title_el
            if not link_el or not link_el.get('href'):
                continue
                
            href = link_el.get('href')
            if href.startswith('javascript'):
                continue
                
            url = urljoin("https://www.ppomppu.co.kr/zboard/", href)
            
            deals.append({
                "title": full_title,
                "url": url,
                "price": 0, # 리스트에서는 추출불가하여 0으로 처리 (차후 상세 파싱 시 채움)
                "shop_name": ""
            })
            
        return deals

    async def get_detail(self, url: str) -> dict:
        """상세 페이지 데이터 파싱 로직 (추후 고도화)"""
        pass
