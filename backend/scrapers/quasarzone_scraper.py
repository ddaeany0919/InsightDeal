import logging
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class QuasarzoneScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        super().__init__("퀘이사존", max_concurrent_requests=5)
        self.community_id = community_id
        self.list_url = "https://quasarzone.com/bbs/qb_saleinfo"

    async def parse_list(self, html: str) -> list[dict]:
        """퀘이사존 게시판 리스트에서 타겟 데이터 추출"""
        soup = BeautifulSoup(html, 'html.parser')
        
        post_rows = soup.select('div.market-info-type-list table tbody tr')
        
        deals = []
        for row in post_rows:
            # 공지사항 제외
            label_tag = row.select_one('span.label')
            if label_tag and '공지' in label_tag.get_text():
                continue

            title_element = row.select_one('a.subject-link')
            if not title_element:
                continue

            href = title_element.get('href', '')
            if not href or href.startswith('javascript'):
                continue
            
            url = urljoin("https://quasarzone.com", href)
            
            # 실제 제목 추출
            title_span = title_element.select_one('span.ellipsis-with-reply-cnt')
            if title_span:
                full_title = title_span.get_text(strip=True)
            else:
                full_title = title_element.get_text(strip=True)

            # 가격 추출 시도
            price = 0
            price_tag = row.select_one('span.text-orange')
            if price_tag:
                price_str = price_tag.get_text(strip=True).replace(',', '').replace('원', '')
                try:
                    price = int(price_str)
                except ValueError:
                    pass

            deals.append({
                "title": full_title,
                "url": url,
                "price": price,
                "shop_name": ""
            })
            
        return deals

    async def get_detail(self, url: str) -> dict:
        """상세 페이지 데이터 파싱 로직 (추후 고도화)"""
        pass
