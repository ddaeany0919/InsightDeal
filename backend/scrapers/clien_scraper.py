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

            is_closed = False
            sold_out_tag = item.select_one('span.icon_info')
            if sold_out_tag and '품절' in sold_out_tag.get_text(strip=True):
                is_closed = True
            
            title_element = item.select_one('span.list_subject a')
            if not title_element:
                title_element = item.select_one('a[data-role="list-title-text"]')
            if not title_element:
                continue

            url = urljoin("https://www.clien.net", title_element.get('href', ''))
            full_title = title_element.get_text(strip=True)
            
            if '종료' in full_title or '마감' in full_title or '품절' in full_title:
                is_closed = True

            style = title_element.get('style', '')
            if 'line-through' in style or title_element.select_one('del, s, strike'):
                is_closed = True
            
            image_url = ""
            img_tag = item.select_one('div.list_img img')
            if img_tag and img_tag.has_attr('src'):
                src = img_tag['src']
                if 'noimage' not in src:
                    image_url = src if src.startswith('http') else urljoin("https://www.clien.net", src)

            deals.append({
                "title": full_title,
                "url": url,
                "price": 0,
                "shop_name": "",
                "image_url": image_url,
                "is_closed": is_closed
            })
            
        import asyncio
        # Gather details to fetch ecommerce link (though mock for now)
        async def fetch_detail_mock(deal):
            detail = await self.get_detail(deal["url"])
            deal["ecommerce_link"] = detail.get("ecommerce_link", "")
            return deal
            
        tasks = [fetch_detail_mock(d) for d in deals]
        return await asyncio.gather(*tasks)

    async def get_detail(self, url: str) -> dict:
        """상세 페이지 데이터 파싱 로직"""
        html = await self.fetch_html(url)
        if not html: return {}
        soup = BeautifulSoup(html, 'html.parser')
        
        image_url = ""
        # 클리앙 본문 이미지
        img_element = soup.select_one('.post_article img')
        if img_element and img_element.has_attr('src'):
            src = img_element['src']
            if not src.startswith('http'):
                if src.startswith('//'):
                    src = "https:" + src
                else:
                    src = urljoin("https://www.clien.net", src)
            if 'attachment' in src or 'image' in src or '.jpg' in src or '.png' in src:
                image_url = src
                
        return {"image_url": image_url}
