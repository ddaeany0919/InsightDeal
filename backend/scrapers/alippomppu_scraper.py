import logging
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class AlippomppuScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        super().__init__("알리뽐뿌", max_concurrent_requests=5)
        self.community_id = community_id
        self.list_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4"

    async def parse_list(self, html: str) -> list[dict]:
        """알리뽐뿌 게시판 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        post_rows = soup.select('tr.baseList')

        deals = []
        for row in post_rows:
            title_element = row.select_one('a.baseList-title')
            if not title_element: continue

            href = title_element.get('href', '')
            if not href: continue
            
            full_title = title_element.get_text(strip=True)

            url = urljoin("https://www.ppomppu.co.kr/zboard/", href)
            # 🖼️ 썸네일 추출 시도
            image_url = ""
            img_td = row.select_one('td img.thumb_border')
            if not img_td:
                 img_td = row.select_one('img')
            
            if img_td and img_td.has_attr('src'):
                 image_url = img_td['src']
                 if image_url.startswith('//'):
                      image_url = "https:" + image_url
                 elif not image_url.startswith('http'):
                      image_url = urljoin("https://www.ppomppu.co.kr", image_url)

            is_closed = False
            if '종료' in full_title or '마감' in full_title or '품절' in full_title:
                is_closed = True
            if title_element.select_one('del, s, strike, font[color="#999999"]'):
                is_closed = True

            deals.append({
                "title": full_title,
                "url": url,
                "price": 0,
                "shop_name": "",
                "image_url": image_url,
                "is_closed": is_closed
            })
            
        return deals

    async def get_detail(self, url: str) -> dict:
        """상세 페이지 데이터 파싱 로직 (추후 고도화)"""
        pass
