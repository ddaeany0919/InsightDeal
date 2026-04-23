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
        
        import asyncio
        async def process_row(row):
            link_element = row.select_one('a.subject_link, a.board_list_item')
            if not link_element: return None

            url = urljoin("https://bbs.ruliweb.com", link_element['href'])

            title_element = row.select_one('a.subject_link, span.subject')
            if not title_element: return None

            reply_count_tag = title_element.find("strong", class_="reply_count")
            if reply_count_tag: reply_count_tag.decompose()

            full_title_raw = title_element.get_text(strip=True)
            full_title = re.sub(r'\s*\(\d+\)$', '', full_title_raw).strip()

            detail_info = await self.get_detail(url)
            
            image_url = detail_info.get("image_url", "")
            if not image_url:
                img_tag = row.select_one('img')
                if img_tag and img_tag.has_attr('src'):
                    image_url = img_tag['src']
                    if image_url.startswith('//'): image_url = "https:" + image_url
                    elif not image_url.startswith('http'): image_url = urljoin("https://bbs.ruliweb.com", image_url)

            category = None
            if any(k in full_title for k in ['적립', '출석', '출첵', '포인트']):
                category = "적립"

            is_closed = False
            style = title_element.get('style', '')
            if 'line-through' in style or title_element.select_one('del, s, strike'):
                is_closed = True
            
            if '종료' in full_title or '마감' in full_title or '품절' in full_title:
                is_closed = True

            return {
                "category": category,
                "title": full_title,
                "url": url,
                "price": 0,
                "shop_name": "",
                "image_url": image_url,
                "ecommerce_link": detail_info.get("ecommerce_link", ""),
                "is_closed": is_closed
            }
            
        tasks = [process_row(row) for row in post_rows]
        results = await asyncio.gather(*tasks)
        return [r for r in results if r]

    async def get_detail(self, url: str) -> dict:
        """상세 페이지 데이터 파싱 로직"""
        html = await self.fetch_html(url)
        if not html: return {}
        soup = BeautifulSoup(html, 'html.parser')
        
        ecommerce_link = ""
        link_tag = soup.select_one('div.source_url a')
        if link_tag and link_tag.get('href'):
            ecommerce_link = link_tag['href']
            
        image_url = ""
        img_tag = soup.select_one('.board_main_view img') or soup.select_one('.view_content img')
        if img_tag and img_tag.get('src'):
            image_url = img_tag['src']
            if image_url.startswith('//'): image_url = "https:" + image_url
            
        return {"ecommerce_link": ecommerce_link, "image_url": image_url}
