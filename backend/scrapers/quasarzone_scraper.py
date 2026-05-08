import logging
import re
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
        import asyncio
        async def process_row(row):
            is_closed = False
            label_tag = row.select_one('span.label')
            if label_tag:
                label_text = label_tag.get_text()
                if '공지' in label_text:
                    return None
                if '종료' in label_text or '마감' in label_text:
                    is_closed = True

            title_element = row.select_one('a.subject-link')
            if not title_element: return None

            href = title_element.get('href', '')
            if not href or href.startswith('javascript'): return None
            
            url = urljoin("https://quasarzone.com", href)
            
            title_span = title_element.select_one('span.ellipsis-with-reply-cnt')
            full_title = title_span.get_text(strip=True) if title_span else title_element.get_text(strip=True)

            if "게시판 규정" in full_title or "이용안내" in full_title:
                return None

            price = 0
            currency = "KRW"
            price_tag = row.select_one('span.text-orange')
            if price_tag:
                price_str = price_tag.get_text(strip=True)
                import re
                if '$' in price_str or 'USD' in price_str.upper() or '달러' in price_str or '불' in price_str:
                    match = re.search(r'([0-9.]+)', price_str)
                    if match:
                        usd_val = float(match.group(1))
                        price = int(usd_val * 100)
                        currency = "USD"
                else:
                    digits = re.sub(r'[^0-9]', '', price_str)
                    if digits:
                        price = int(digits)

            image_url = ""
            span_img = row.select_one('span.img-background-wrap')
            if span_img and span_img.has_attr('style'):
                import re
                match = re.search(r'url\((.*?)\)', span_img['style'])
                if match:
                    image_url = match.group(1).strip()
            
            if not image_url:
                img_tag = row.select_one('img')
                if img_tag and img_tag.has_attr('src'):
                    image_url = img_tag['src']

            if image_url and not image_url.startswith('http'):
                image_url = urljoin("https://quasarzone.com", image_url)
            
            if image_url.endswith('?'):
                image_url = image_url[:-1]
                
            category = None
            if any(k in full_title for k in ['적립', '출석', '출첵', '포인트']):
                category = "적립"

            if '종료' in full_title or '마감' in full_title or '품절' in full_title or '삭제' in full_title:
                is_closed = True

            is_super_hotdeal = False
            label_el = row.select_one('.label')
            if label_el and '인기' in label_el.get_text(strip=True):
                is_super_hotdeal = True

            # 실제 게시글 작성 시간 추출
            posted_at_iso = None
            time_span = row.select_one('span.date')
            if time_span:
                posted_at_iso = self.parse_time_str(time_span.get_text(strip=True))
                
            # 지표 추출
            view_count = 0
            like_count = 0
            comment_count = 0
            for count_span in row.select('span.count'):
                try:
                    icon = count_span.select_one('i')
                    if icon:
                        cls = icon.get('class', [])
                        txt = count_span.get_text(strip=True).replace(',', '')
                        if 'fa-eye' in cls: view_count = int(txt)
                        elif 'fa-thumbs-up' in cls: like_count = int(txt)
                        elif 'fa-comment' in cls or 'fa-comment-dots' in cls: comment_count = int(txt)
                except: pass

            detail_info = await self.get_detail(url)
            
            return {
                "category": category,
                "title": full_title,
                "url": url,
                "price": price,
                "currency": currency,
                "shop_name": "",
                "image_url": image_url,
                "ecommerce_link": detail_info.get("ecommerce_link", ""),
                "is_closed": is_closed or detail_info.get("is_closed", False),
                "shipping_fee": detail_info.get("shipping_fee", ""),
                "is_super_hotdeal": is_super_hotdeal,
                "posted_at": posted_at_iso,
                "view_count": view_count,
                "like_count": like_count,
                "comment_count": comment_count,
                "content_html": detail_info.get("content_html", "")
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
        import re
        import base64
        
        # 1. market-info-view-table 내의 <a> 태그를 찾아 goToLink 파싱
        for a in soup.select('table.market-info-view-table a'):
            href = a.get('href', '')
            if 'goToLink' in href:
                match = re.search(r"goToLink\('([^']+)'\)", href)
                if match:
                    try:
                        b64_str = match.group(1)
                        # base64 패딩 처리
                        b64_str += "=" * ((4 - len(b64_str) % 4) % 4)
                        decoded = base64.b64decode(b64_str).decode('utf-8')
                        if decoded.startswith('http'):
                            ecommerce_link = decoded
                            break
                    except Exception:
                        pass
            elif href.startswith('http'):
                ecommerce_link = href
                break
                
        # 2. 만약 없다면 일반 본문 내에서 http 링크 검색 (광고 배너 제외)
        if not ecommerce_link:
            for a in soup.select('.view-content a'):
                href = a.get('href', '')
                if href.startswith('http') and 'clickBanner' not in href:
                    ecommerce_link = href
                    break
        
        # 3. 배송비 파싱
        shipping_fee = ""
        for tr in soup.select('table.market-info-view-table tr'):
            th = tr.select_one('th')
            if th and '배송비' in th.get_text():
                td = tr.select_one('td')
                if td:
                    fee_text = td.get_text(strip=True)
                    shipping_fee = fee_text
                    break
                    
        # 4. 본문 내용 (content_html) 파싱
        content_html = ""
        org_content = soup.find(id='org_contents')
        if org_content:
            from bs4 import BeautifulSoup as BS
            # org_contents는 보통 textarea 등이며 텍스트로 HTML을 담고 있음
            inner_soup = BS(org_content.get_text(), 'html.parser')
            content_html = inner_soup.get_text(separator=' ', strip=True)
            
        if not content_html:
            content_area = soup.select_one('.view-content')
            if content_area:
                content_html = content_area.get_text(separator=' ', strip=True)

        return {"ecommerce_link": ecommerce_link, "shipping_fee": shipping_fee, "content_html": content_html}
