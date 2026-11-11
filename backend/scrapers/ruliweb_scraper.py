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
            if not link_element or not link_element.get('href'):
                return None

            from urllib.parse import urlparse, parse_qs, urlencode, urlunparse
            raw_url = urljoin("https://bbs.ruliweb.com", link_element.get('href'))
            parsed_href = urlparse(raw_url)
            qs = parse_qs(parsed_href.query)
            qs.pop('page', None)
            clean_query = urlencode(qs, doseq=True)
            url = urlunparse(parsed_href._replace(query=clean_query))

            title_element = row.select_one('a.subject_link, span.subject')
            if not title_element: return None

            comment_count = 0
            reply_count_tag = title_element.find("strong", class_="reply_count") or row.select_one('.num_reply, .reply_count')
            if reply_count_tag:
                rc_txt = re.sub(r'[^\d]', '', reply_count_tag.get_text(strip=True))
                if rc_txt.isdigit():
                    comment_count = int(rc_txt)
                if reply_count_tag in title_element.descendants:
                    reply_count_tag.decompose()

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

            like_count = 0
            recomd_td = row.select_one('td.recomd, span.recomd')
            if recomd_td:
                r_txt = re.sub(r'[^\d]', '', recomd_td.get_text(strip=True))
                if r_txt.isdigit():
                    like_count = int(r_txt)

            view_count = 0
            hit_td = row.select_one('td.hit, span.hit')
            if hit_td:
                h_txt = re.sub(r'[^\d]', '', hit_td.get_text(strip=True))
                if h_txt.isdigit():
                    view_count = int(h_txt)

            is_super_hotdeal = False
            if 'best' in row.get('class', []) or like_count >= 5 or view_count >= 2500:
                is_super_hotdeal = True

            # 실제 게시글 작성 시간 추출
            posted_at_iso = None
            time_td = row.select_one('td.time')
            if time_td:
                posted_at_iso = self.parse_time_str(time_td.get_text(strip=True))

            from backend.core.deal_normalizer import parse_title_price_and_shipping
            price, extracted_currency, _, _ = parse_title_price_and_shipping(full_title)

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
                "view_count": view_count,
                "like_count": like_count,
                "comment_count": comment_count,
                "content_html": ""
            }
            
        async def safe_process_row(row):
            try:
                return await process_row(row)
            except Exception as row_err:
                logger.warning(f"[{self.platform_name}] 행 파싱 오류로 건너뜀: {row_err}")
                return None

        tasks = [safe_process_row(row) for row in post_rows]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        return [r for r in results if isinstance(r, dict)]

    async def get_detail(self, url: str, full_title: str = "") -> dict:
        """상세 페이지 데이터 파싱 로직"""
        html = await self.fetch_html(url)
        if not html: return {}
        if any(k in html for k in ("삭제된 게시물", "존재하지 않는 게시물", "권한이 없거나 존재하지 않는")):
            return {"is_closed": True}
        soup = BeautifulSoup(html, 'html.parser')

        from urllib.parse import urlparse, parse_qs, unquote

        def unwrap_ruliweb_link(raw_href: str) -> str:
            if not raw_href:
                return ""
            raw_href = raw_href.strip()
            if 'link.php' in raw_href and 'ol=' in raw_href:
                try:
                    qs = parse_qs(urlparse(raw_href).query)
                    if 'ol' in qs and qs['ol']:
                        return unquote(qs['ol'][0]).strip()
                except Exception:
                    pass
            return raw_href

        # 목록에서 긴 제목이 '...'으로 잘리는 경우 상세 페이지에서 원본 전체 제목 복원
        detail_title = ""
        subj_el = soup.select_one('span.subject_inner_text') or soup.select_one('.subject_text')
        if subj_el:
            detail_title = re.sub(r'\s*\(\d+\)\s*$', '', subj_el.get_text(strip=True)).strip()
        if not detail_title or detail_title.endswith('...'):
            og_t = soup.select_one('meta[property="og:title"]')
            if og_t and og_t.get('content'):
                cand_t = re.sub(r'\s*[|\-]\s*\ub8e8\ub9ac\uc6f9.*$', '', og_t.get('content', '').strip()).strip()
                if cand_t and not cand_t.endswith('...'):
                    detail_title = cand_t
        if detail_title:
            full_title = detail_title

        ecommerce_link = ""
        link_tag = soup.select_one('div.source_url a')
        if link_tag and link_tag.get('href'):
            ecommerce_link = unwrap_ruliweb_link(link_tag['href'])

        if not ecommerce_link:
            for a_tag in soup.select('.view_content a[href], .board_main_view a[href]'):
                cand_href = unwrap_ruliweb_link(a_tag.get('href', ''))
                if cand_href.startswith('http') and 'ruliweb.com' not in cand_href and 'daumcdn.net' not in cand_href:
                    ecommerce_link = cand_href
                    break

        image_url = ""
        for img_tag in soup.select('.view_content img, .board_main_view img'):
            src = img_tag.get('src') or img_tag.get('data-src') or ''
            if not src:
                continue
            if src.startswith('//'):
                src = "https:" + src
            elif not src.startswith('http'):
                src = urljoin("https://bbs.ruliweb.com", src)
            if any(bad in src.lower() for bad in ('icon', 'emoticon', 'btn_', 'logo', '.gif')):
                continue
            image_url = src
            break

        price_fallback = 0
        shipping_fee = ""

        # 본문 영역 파싱 (줄바꿈 및 링크 보존)
        content_html = ""
        content_area = soup.select_one('.view_content') or soup.select_one('.board_main_view')
        if content_area:
            for tag in content_area.select('script, style'):
                tag.decompose()
            for img in content_area.find_all('img'):
                src = (img.get('src') or img.get('data-src') or '').strip()
                if not src:
                    continue
                if src.startswith('//'):
                    src = "https:" + src
                elif not src.startswith('http'):
                    src = urljoin("https://bbs.ruliweb.com", src)
                if any(bad in src.lower() for bad in ('icon', 'emoticon', 'btn_', 'logo', '.gif')):
                    continue
                parent_a = img.find_parent('a')
                if parent_a is not None:
                    parent_a.insert_before(f"\n[상품이미지: {src}]\n")
                    img.decompose()
                else:
                    img.replace_with(f"\n[상품이미지: {src}]\n")
            for a_tag in content_area.find_all('a', href=True):
                real_href = unwrap_ruliweb_link(a_tag['href'])
                if real_href.startswith('http') and 'ruliweb.com' not in real_href:
                    txt = a_tag.get_text(strip=True)
                    if real_href not in txt:
                        a_tag.string = f"{txt} ({real_href})" if txt else real_href
            lines = [line.strip() for line in content_area.get_text(separator='\n').splitlines() if line.strip()]
            content_html = '\n'.join(lines)
            body_text = content_html
        else:
            body_text = soup.get_text(separator=' ')

        # 배송비 휴리스틱 추출
        search_text = (full_title or "") + " " + body_text
        if re.search(r'(무료배송|무배|배송비\s*무료|\(\s*무료\s*\)|/\s*무료|무료\s*/|무료\s*$)', search_text):
            shipping_fee = "무료배송"

        from backend.core.deal_normalizer import extract_body_fallback_price
        price_fallback, extracted_currency = extract_body_fallback_price(body_text, full_title or "")

        # 📸 [고화질 썸네일 복구 가드]: 본문에 이미지가 없거나 cache 관련 썸네일인 경우, 아웃링크 og:image 및 상품명 기반 폴백 추출
        if not image_url or "cache" in image_url:
            og_img = await self.fetch_og_image(ecommerce_link, full_title or detail_title)
            if og_img:
                logger.info(f"✨ [루리웹 og:image] 외부 아웃링크/상품명에서 고화질 썸네일 확보: {og_img}")
                image_url = og_img

        result = {
            "ecommerce_link": ecommerce_link,
            "image_url": image_url,
            "price": price_fallback,
            "currency": extracted_currency,
            "shipping_fee": shipping_fee,
            "content_html": content_html,
        }
        if detail_title:
            result["title"] = detail_title
        return result
