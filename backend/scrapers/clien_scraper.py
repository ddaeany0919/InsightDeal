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
            
            is_super_hotdeal = False
            symph_el = item.select_one('.list_symph')
            hit_el = item.select_one('.list_hit')
            symph = int(symph_el.get_text(strip=True)) if symph_el and symph_el.get_text(strip=True).isdigit() else 0
            hit_txt = hit_el.get_text(strip=True).replace(',', '') if hit_el else '0'
            hit = int(hit_txt) if hit_txt.isdigit() else 0
            if symph >= 10 or hit >= 10000:
                is_super_hotdeal = True

            image_url = ""
            img_tag = item.select_one('div.list_img img')
            if img_tag and img_tag.has_attr('src'):
                src = img_tag['src']
                if 'noimage' not in src:
                    image_url = src if src.startswith('http') else urljoin("https://www.clien.net", src)

            # 실제 게시글 작성 시간 추출
            posted_at_iso = None
            time_span = item.select_one('span.timestamp, span.time')
            if time_span:
                posted_at_iso = self.parse_time_str(time_span.get('title') or time_span.get_text(strip=True))

            deals.append({
                "title": full_title,
                "url": url,
                "price": 0,
                "shop_name": "",
                "image_url": image_url,
                "is_closed": is_closed,
                "is_super_hotdeal": is_super_hotdeal,
                "posted_at": posted_at_iso
            })
            
        import asyncio
        # Gather details to fetch ecommerce link (though mock for now)
        async def fetch_detail_mock(deal):
            detail = await self.get_detail(deal["url"])
            deal["ecommerce_link"] = detail.get("ecommerce_link", "")
            deal["price"] = detail.get("price", 0)
            deal["shipping_fee"] = detail.get("shipping_fee", "")
            if not deal.get("image_url") and detail.get("image_url"):
                deal["image_url"] = detail.get("image_url")
            deal["content_html"] = detail.get("content_html", "")
            deal["is_closed"] = deal.get("is_closed", False) or detail.get("is_closed", False)
            if "posted_at" not in deal:
                deal["posted_at"] = None
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
                
        # 구매링크 추출
        ecommerce_link = ""
        link_elem = soup.select_one('.outlink a')
        if not link_elem:
            link_elem = soup.select_one('.attached_link a')
        if link_elem and link_elem.get('href'):
            ecommerce_link = link_elem['href']
            
        price_fallback = 0
        shipping_fee = ""
        body_text = soup.get_text(separator=' ')
        
        # 가격 휴리스틱 추출
        import re
        price_matches = re.findall(r'([0-9]{1,3}(?:[,\.][0-9]{3})*|[0-9]+)\s*원', body_text)
        if price_matches:
            try:
                price_fallback = int(price_matches[0].replace(',', '').replace('.', ''))
            except:
                pass
                
        # 배송비 휴리스틱 추출
        if "무료배송" in body_text or "무배" in body_text:
            shipping_fee = "무료배송"
            
        content_html = ""
        content_area = soup.select_one('.post_article')
        if content_area:
            content_html = content_area.get_text(separator=' ', strip=True)
            
        return {"ecommerce_link": ecommerce_link, "image_url": image_url, "price": price_fallback, "shipping_fee": shipping_fee, "content_html": content_html}
