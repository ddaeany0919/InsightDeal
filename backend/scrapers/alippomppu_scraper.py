import logging
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.ppomppu_scraper import PpomppuScraper

logger = logging.getLogger(__name__)

class AlippomppuScraper(PpomppuScraper):
    def __init__(self, community_id: int):
        super().__init__(community_id)
        self.platform_name = "알리뽐뿌"
        self.list_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu8"

    async def parse_list(self, html: str) -> list[dict]:
        """알리뽐뿌 게시판 특화 무료배송 처리 (나머지는 부모 클래스 로직 재사용)"""
        # PpomppuScraper의 parse_list를 호출하여 기본적인 파싱 및 get_detail 실행
        deals = await super().parse_list(html)
        
        import re
        for deal in deals:
            full_title = deal.get("title", "")
            # 'FS' or 'Free' in title means Free Shipping
            if re.search(r'\b(?:FS|Free|무배|무료배송|무료)\b', full_title, re.IGNORECASE):
                deal["shipping_fee"] = "무료배송"
                
        return deals
