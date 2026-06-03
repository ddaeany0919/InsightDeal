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

    async def __aenter__(self):
        # 루리웹은 IP 차단 시 타임아웃이 길게 발생하므로, 빠른 실패를 위해 timeout 단축
        from curl_cffi.requests import AsyncSession
        self.client = AsyncSession(impersonate='chrome124', timeout=10.0)
        return self

    async def parse_list(self, html: str) -> list[dict]:
        """루리웹 핫딜/예판 게시판 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        post_rows = soup.select('tr.table_body.blocktarget:not(.notice):not(.best), a.board_list_item.deco:not(.notice)')
        
        import asyncio
        async def process_row(row):
            link_element = row.select_one('a.subject_link, a.board_list_item')
            if not link_element: return None

            from urllib.parse import urlparse, parse_qs, urlencode, urlunparse
            raw_url = urljoin("https://bbs.ruliweb.com", link_element['href'])
            parsed_href = urlparse(raw_url)
            qs = parse_qs(parsed_href.query)
            qs.pop('page', None)
            clean_query = urlencode(qs, doseq=True)
            url = urlunparse(parsed_href._replace(query=clean_query))

            title_element = row.select_one('a.subject_link, span.subject')
            if not title_element: return None

            reply_count_tag = title_element.find("strong", class_="reply_count")
            if reply_count_tag: reply_count_tag.decompose()

            full_title_raw = title_element.get_text(strip=True)
            full_title = re.sub(r'\s*\(\d+\)$', '', full_title_raw).strip()

            image_url = ""
            img_tag = row.select_one('img')
            if img_tag and img_tag.has_attr('src'):
                image_url = img_tag['src']
                if image_url.startswith('//'): image_url = "https:" + image_url
                elif not image_url.startswith('http'): image_url = urljoin("https://bbs.ruliweb.com", image_url)

            category = None
            cat_match = re.search(r'^\s*\[([^\]]+)\]', full_title)
            if cat_match:
                category = cat_match.group(1).strip()

            if any(k in full_title for k in ['적립', '출석', '출첵', '포인트']):
                category = "적립"

            is_closed = False
            style = title_element.get('style', '')
            if 'line-through' in style or title_element.select_one('del, s, strike'):
                is_closed = True
            
            if '종료' in full_title or '마감' in full_title or '품절' in full_title:
                is_closed = True

            is_super_hotdeal = False
            if 'best' in row.get('class', []):
                is_super_hotdeal = True

            # 실제 게시글 작성 시간 추출
            posted_at_iso = None
            time_td = row.select_one('td.time')
            if time_td:
                posted_at_iso = self.parse_time_str(time_td.get_text(strip=True))

            extracted_currency = "KRW"
            price = 0
            
            if price == 0:
                usd_match = re.search(r'(?:\$|USD|달러|유로|€)\s*([0-9,.]+)', full_title, re.IGNORECASE)
                if not usd_match:
                    usd_match = re.search(r'([0-9,.]+)\s*(?:\$|USD|달러|유로|€)', full_title, re.IGNORECASE)
                    
                price_match = re.search(r'([\d,]+(?:원|만원))', full_title)
                
                if usd_match:
                    try:
                        val = float(usd_match.group(1).replace(',', ''))
                        price = int(val * 100)
                        if '유로' in full_title or '€' in full_title:
                            extracted_currency = "EUR"
                        else:
                            extracted_currency = "USD"
                    except: pass
                elif price_match:
                    price_str = price_match.group(1).replace(',', '')
                    if '만원' in price_str:
                        try: price = int(float(price_str.replace('만원', '')) * 10000)
                        except: pass
                    else:
                        nums = re.findall(r'\d+', price_str)
                        if nums: price = int(''.join(nums))

            # 휴리스틱: 제목에 직구 관련 키워드가 있고 가격이 10000 이하면 USD로 간주
            if extracted_currency == "KRW" and price > 0 and price <= 10000:
                if any(kw in full_title for kw in ['알리', '코인', '큐텐', '직구', '알익']):
                    extracted_currency = "USD"
                    price = int(price * 100)

            return {
                "category": category,
                "title": full_title,
                "url": url,
                "price": price,
                "currency": extracted_currency,
                "shop_name": "",
                "image_url": image_url,
                "ecommerce_link": "",
                "is_closed": is_closed,
                "shipping_fee": "",
                "is_super_hotdeal": is_super_hotdeal,
                "posted_at": posted_at_iso,
                "content_html": ""
            }
            
        tasks = [process_row(row) for row in post_rows]
        results = await asyncio.gather(*tasks)
        return [r for r in results if r]

    async def get_detail(self, url: str, full_title: str = "") -> dict:
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
            
        price_fallback = 0
        shipping_fee = ""
        
        # 본문 영역 파싱
        content_html = ""
        content_area = soup.select_one('.board_main_view') or soup.select_one('.view_content')
        if content_area:
            content_html = content_area.get_text(separator=' ', strip=True)
            body_text = content_html
        else:
            body_text = soup.get_text(separator=' ')
        
        # 가격 휴리스틱 추출 (본문에서 추출 시 오류가 잦아 0으로 넘기고 Aggregator에서 제목 기반 추출)
        # 배송비 휴리스틱 추출
        search_text = full_title + " " + body_text
        if re.search(r'(무료배송|무배|배송비\s*무료|\(\s*무료\s*\)|/\s*무료|무료\s*/|무료\s*$)', search_text):
            shipping_fee = "무료배송"
        extracted_currency = "KRW"
        
        # [방어막 추가]: 본문 파싱 전 URL 소거 처리! (링크 내의 숫자 오인 차단)
        cleaned_body_text = re.sub(r'https?://[^\s]+', '', body_text)
        
        usd_match = re.search(r'(?:\$|USD|달러|유로|€)\s*([0-9,.]+)', cleaned_body_text, re.IGNORECASE)
        if not usd_match:
            usd_match = re.search(r'([0-9,.]+)\s*(?:\$|USD|달러|유로|€)', cleaned_body_text, re.IGNORECASE)
        
        price_matches = re.findall(r'([0-9]{1,3}(?:[,\.][0-9]{3})*|[0-9]+)\s*원', cleaned_body_text)
        
        if usd_match:
            try:
                val = float(usd_match.group(1).replace(',', ''))
                price_fallback = int(val * 100)
                if '유로' in body_text or '€' in body_text:
                    extracted_currency = "EUR"
                else:
                    extracted_currency = "USD"
            except: pass
        elif price_matches:
            try:
                price_fallback = int(price_matches[0].replace(',', '').replace('.', ''))
            except: pass

        # 📸 [고화질 썸네일 복구 가드]: 본문에 이미지가 없거나 cache 관련 썸네일인 경우, 아웃링크 og:image 추출
        if (not image_url or "cache" in image_url) and ecommerce_link:
            og_img = await self.fetch_og_image(ecommerce_link)
            if og_img:
                logger.info(f"✨ [루리웹 og:image] 외부 아웃링크에서 고화질 썸네일 확보: {og_img}")
                image_url = og_img

        return {"ecommerce_link": ecommerce_link, "image_url": image_url, "price": price_fallback, "currency": extracted_currency, "shipping_fee": shipping_fee, "content_html": content_html}
