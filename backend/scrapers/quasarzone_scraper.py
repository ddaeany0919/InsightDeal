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
        """퀘이사존 게시판 리스트에서 타겟 데이터 추출 (v2-list-row 신형 레이아웃 및 구형 테이블 완벽 호환)"""
        soup = BeautifulSoup(html, 'html.parser')
        
        post_rows = soup.select('div.v2-list-row, div.market-info-type-list table tbody tr')
        import asyncio
        async def process_row(row):
            row_classes = " ".join(row.get('class', [])) if row.get('class') else ""
            if 'notice' in row_classes.lower():
                return None

            is_closed = any(x in row_classes.lower() for x in ['end', 'closed', 'blind'])
            for badge_el in row.select('span.label, span.v2-badge'):
                badge_text = badge_el.get_text(strip=True)
                if '공지' in badge_text:
                    return None
                if '종료' in badge_text or '마감' in badge_text or '품절' in badge_text:
                    is_closed = True

            title_element = row.select_one('a.subject-link')
            if not title_element: return None

            href = title_element.get('href', '')
            if not href or href.startswith('javascript'): return None
            
            url = urljoin("https://quasarzone.com", href)
            
            title_span = title_element.select_one('span.ellipsis-with-reply-cnt')
            full_title = title_span.get_text(strip=True) if title_span else title_element.get_text(strip=True)

            if not full_title or "게시판 규정" in full_title or "이용안내" in full_title or "블라인드" in full_title:
                return None

            price = 0
            currency = "KRW"
            price_tag = row.select_one('span.text-orange, span.v2-list-row__price')
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
                    # 첫 번째로 발견되는 숫자 그룹(쉼표 포함)만 추출하여 파싱하여 병합 버그 방지
                    match = re.search(r'([0-9,]+)', price_str)
                    if match:
                        digits = re.sub(r'[^0-9]', '', match.group(1))
                        if digits:
                            price = int(digits)

            shop_name = ""
            shop_logo = row.select_one('img.v2-shop-logo')
            if shop_logo and shop_logo.get('alt'):
                shop_name = shop_logo.get('alt').strip()

            shipping_fee = ""
            for ship_el in row.select('span.v2-list-row__ship'):
                ship_txt = ship_el.get_text(strip=True)
                if not ship_txt:
                    continue
                if '배송' in ship_txt or '무료' in ship_txt or '무배' in ship_txt:
                    raw_ship = ship_txt.replace('배송비', '').strip()
                    if raw_ship in ['0', '0원', '무료', '무배']:
                        shipping_fee = "무료배송"
                    else:
                        shipping_fee = raw_ship
                elif not shop_name:
                    shop_name = ship_txt

            image_url = (row.get('data-preview') or "").strip()
            if not image_url:
                span_img = row.select_one('span.img-background-wrap, a.img-background-wrap')
                if span_img and span_img.has_attr('style'):
                    import re
                    match = re.search(r"url\(['\"]?(.*?)['\"]?\)", span_img['style'])
                    if match:
                        image_url = match.group(1).strip()
            
            if not image_url:
                img_tag = row.select_one('img:not(.v2-shop-logo):not(.qz-planet-ic)')
                if img_tag and img_tag.has_attr('src'):
                    image_url = img_tag['src']

            if image_url and not image_url.startswith('http'):
                image_url = urljoin("https://quasarzone.com", image_url)
            
            if image_url.endswith('?'):
                image_url = image_url[:-1]
                
            if image_url and "thumb_" in image_url:
                image_url = image_url.replace("thumb_", "")
                
            category = None
            category_span = row.select_one('span.category, span.v2-badge')
            if category_span:
                cat_raw = category_span.get_text(strip=True)
                if cat_raw not in ['인기', '종료', '마감', '공지']:
                    category = cat_raw

            if any(k in full_title for k in ['적립', '출석', '출첵', '포인트']):
                category = "적립"

            row_classes = row.get('class') or []
            if 'is-done' in row_classes or row.select_one('.v2-thumb-done'):
                is_closed = True
            if '종료' in full_title or '마감' in full_title or '품절' in full_title or '삭제' in full_title:
                is_closed = True

            # 실제 게시글 작성 시간 추출
            posted_at_iso = None
            time_span = row.select_one('span.date, span.v2-list-row__time')
            if time_span:
                posted_at_iso = self.parse_time_str(time_span.get_text(strip=True))
                
            # 지표 추출 (v2-list-row 및 구형 호환)
            view_count = 0
            like_count = 0
            comment_count = 0
            
            good_el = row.select_one('span.qc-count-good')
            if good_el:
                g_txt = re.sub(r'[^0-9]', '', good_el.get_text(strip=True))
                if g_txt.isdigit():
                    like_count = int(g_txt)

            hit_el = row.select_one('span.qc-count-hit')
            if hit_el:
                raw_hit = hit_el.get_text(strip=True).replace(',', '')
                try:
                    if '만' in raw_hit:
                        view_count = int(float(re.sub(r'[^0-9.]', '', raw_hit)) * 10000)
                    elif '천' in raw_hit or 'k' in raw_hit.lower():
                        view_count = int(float(re.sub(r'[^0-9.]', '', raw_hit)) * 1000)
                    else:
                        h_digits = re.sub(r'[^0-9]', '', raw_hit)
                        if h_digits.isdigit():
                            view_count = int(h_digits)
                except Exception:
                    pass

            cmt_el = row.select_one('span.qc-count-comment')
            if cmt_el:
                c_txt = re.sub(r'[^0-9]', '', cmt_el.get_text(strip=True))
                if c_txt.isdigit():
                    comment_count = int(c_txt)

            # 구형 퀘이사존 UI 귤(추천) 아이콘 파싱 폴백
            if like_count == 0:
                tangerine_img = row.select_one('img.tangerine_icon')
                if tangerine_img:
                    next_num = tangerine_img.find_next_sibling('span', class_='num')
                    if next_num:
                        try:
                            like_count = int(next_num.get_text(strip=True))
                        except:
                            pass
                            
            for count_span in row.select('span.count'):
                try:
                    icon = count_span.select_one('i')
                    if icon:
                        cls = icon.get('class', [])
                        txt = count_span.get_text(strip=True).replace(',', '')
                        if 'fa-eye' in cls and view_count == 0: view_count = int(txt)
                        elif 'fa-thumbs-up' in cls and like_count == 0: like_count = int(txt)
                        elif ('fa-comment' in cls or 'fa-comment-dots' in cls) and comment_count == 0: comment_count = int(txt)
                except: pass

            is_super_hotdeal = False
            for b_el in row.select('.label, .v2-badge, .v2-badge--hot'):
                if '인기' in b_el.get_text(strip=True):
                    is_super_hotdeal = True
            if like_count >= 10:
                is_super_hotdeal = True

            return {
                "category": category,
                "title": full_title,
                "url": url,
                "price": price,
                "currency": currency,
                "shop_name": shop_name,
                "image_url": image_url,
                "ecommerce_link": "",
                "is_closed": is_closed,
                "shipping_fee": shipping_fee,
                "is_super_hotdeal": is_super_hotdeal,
                "posted_at": posted_at_iso,
                "view_count": view_count,
                "like_count": like_count,
                "comment_count": comment_count,
                "content_html": ""
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
        
        # 1. market-info-view-table 내의 <a> 태그를 찾아 goToLink 파싱 (onclick 속성도 함께 검사)
        for a in soup.select('table.market-info-view-table a'):
            href = a.get('href', '') or ''
            onclick = a.get('onclick', '') or ''
            combined = href + " " + onclick
            
            if 'goToLink' in combined:
                match = re.search(r"goToLink\('([^']+)'\)", combined)
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
            elif href.startswith('http') and 'quasarzone.com' not in href:
                ecommerce_link = href
                break
                
        # 2. 만약 없다면 일반 본문 내에서 http 링크 검색 (광고 배너 제외)
        if not ecommerce_link:
            for a in soup.select('.view-content a'):
                href = a.get('href', '')
                if href.startswith('http') and 'clickBanner' not in href:
                    ecommerce_link = href
                    break
        
        # 3. 가격 및 배송비 파싱
        shipping_fee = ""
        price = 0
        currency = "KRW"
        for tr in soup.select('table.market-info-view-table tr'):
            th = tr.select_one('th')
            td = tr.select_one('td')
            if not th or not td:
                continue
            th_text = th.get_text(strip=True)
            if '배송비' in th_text:
                shipping_fee = td.get_text(strip=True)
            elif '가격' in th_text:
                price_str = td.get_text(strip=True)
                if '$' in price_str or 'USD' in price_str.upper() or '달러' in price_str:
                    m_p = re.search(r'([0-9,]+(?:\.[0-9]+)?)', price_str)
                    if m_p:
                        price = int(float(m_p.group(1).replace(',', '')) * 100)
                        currency = "USD"
                else:
                    m_p = re.search(r'([0-9,]+)', price_str)
                    if m_p:
                        digits = re.sub(r'[^0-9]', '', m_p.group(1))
                        if digits:
                            price = int(digits)
        
        if not shipping_fee:
            body_text = soup.get_text(separator=' ')
            if re.search(r'(무료배송|무배|택배비\s*무료|배송비\s*무료|\(\s*무료\s*\)|/\s*무료|무료\s*/|무료\s*$)', body_text):
                shipping_fee = "무료배송"
                    
        # 4. 본문 내용 (content_html) 파싱 및 고화질 이미지 추출
        content_html = ""
        image_url = ""
        org_content = soup.find(id='org_contents')
        if org_content:
            from bs4 import BeautifulSoup as BS
            # org_contents는 보통 textarea 등이며 텍스트로 HTML을 담고 있음
            inner_soup = BS(org_content.get_text(), 'html.parser')
            content_html = inner_soup.get_text(separator='\n', strip=True)
            img_tag = inner_soup.select_one('img')
            if img_tag and img_tag.get('src'):
                image_url = img_tag['src']
            
        if not content_html:
            content_area = soup.select_one('.view-content')
            if content_area:
                content_html = content_area.get_text(separator='\n', strip=True)
                if not image_url:
                    img_tag = content_area.select_one('img')
                    if img_tag and img_tag.get('src'):
                        image_url = img_tag['src']

        if image_url:
            if image_url.startswith('//'):
                image_url = "https:" + image_url
            elif not image_url.startswith('http'):
                image_url = urljoin("https://quasarzone.com", image_url)
            if "thumb_" in image_url:
                image_url = image_url.replace("thumb_", "")

        return {
            "price": price,
            "currency": currency,
            "ecommerce_link": ecommerce_link, 
            "shipping_fee": shipping_fee, 
            "content_html": content_html,
            "image_url": image_url
        }
