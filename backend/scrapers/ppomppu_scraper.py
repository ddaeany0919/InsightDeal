import logging
import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class PpomppuScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        super().__init__("뽐뿌", max_concurrent_requests=2)
        self.community_id = community_id
        # CEO 피드백: 뽐뿌 전체 핫딜을 수집하되, 추천수(like_count)로 인기 마크를 판별
        self.list_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu"

    async def parse_list(self, html: str) -> list[dict]:
        """뽐뿌 게시판 리스트에서 제목과 URL (그리고 가능하면 가격) 추출"""
        if "<rss" in html or "<item>" in html:
            from backend.core.deal_normalizer import normalize_shipping_fee, parse_title_price_and_shipping
            rss_blocks = re.findall(r"<item>(.*?)</item>", html, re.DOTALL)
            rss_deals = []
            for blk in rss_blocks:
                try:
                    m_t = re.search(r"<title>(.*?)</title>", blk, re.DOTALL)
                    full_title = BeautifulSoup(m_t.group(1), "html.parser").get_text(strip=True) if m_t else ""
                    if not full_title or any(k in full_title for k in ("공지", "질문", "문의")):
                        continue
                    m_link = re.search(r"<link>\s*(https?://[^\s<]+)", blk)
                    raw_link = m_link.group(1).replace("&amp;", "&") if m_link else ""
                    if not raw_link:
                        continue
                    url = raw_link.replace("http://", "https://")
                    m_desc = re.search(r"<description>(.*?)</description>", blk, re.DOTALL)
                    desc_txt = BeautifulSoup(m_desc.group(1), "html.parser").get_text(" ", strip=True) if m_desc else ""
                    ecom_m = re.search(r"(https?://[^\s\"'<>]+)", desc_txt)
                    ecom_url = ecom_m.group(1) if ecom_m and "ppomppu.co.kr" not in ecom_m.group(1) else ""
                    extracted_price, extracted_currency, extracted_ship, _ = parse_title_price_and_shipping(full_title)
                    shipping_fee = extracted_ship or normalize_shipping_fee("", title=full_title)
                    m_hits = re.search(r"<hits>(.*?)</hits>", blk, re.DOTALL)
                    hits_txt = m_hits.group(1).strip() if m_hits else ""
                    like_count, view_count = 0, 1500
                    hm = re.search(r"\[(\d+)\|(\d+)", hits_txt)
                    if hm:
                        like_count = int(hm.group(1))
                        view_count = max(int(hm.group(2)), 1500)
                    cat_m = re.match(r"^\[(.*?)\]", full_title)
                    rss_deals.append({
                        "title": full_title,
                        "url": url,
                        "price": extracted_price,
                        "currency": extracted_currency,
                        "shop_name": "",
                        "image_url": "",
                        "ecommerce_link": ecom_url,
                        "content_html": f"<p>{desc_txt}</p>" if desc_txt else "",
                        "is_closed": any(k in full_title for k in ("종료", "마감", "품절")),
                        "shipping_fee": shipping_fee,
                        "is_super_hotdeal": like_count >= 8 or view_count >= 2500,
                        "posted_at": None,
                        "view_count": view_count,
                        "like_count": like_count,
                        "comment_count": 0,
                        "category": cat_m.group(1).strip() if cat_m else None,
                    })
                except Exception as rss_err:
                    logger.warning(f"[{self.platform_name}] RSS 항목 파싱 오류로 건너뜀: {rss_err}")
                    continue
            if rss_deals:
                return rss_deals

        soup = BeautifulSoup(html, 'html.parser')
        
        # 쇼핑포럼 (관련 없는 게시글/광고) 제거
        forum_header = soup.find(lambda tag: tag.name == 'tr' and '더 많은 쇼핑 정보와' in tag.get_text())
        if forum_header:
            for sibling in forum_header.find_next_siblings():
                sibling.decompose()
            forum_header.decompose()

        # 뽐뿌 핫딜 게시판 리스트 행
        post_rows = soup.select('tr.baseList, tr.list1, tr.list0')
        
        import asyncio
        async def process_row(row):
            title_el = row.select_one('a.baseList-title') or row.select_one('.list_title') or row.select_one('font')
            if not title_el: return None
            
            if row.select_one('img[src*="icon_ppom_coupon"]'):
                return None

            # 뽐뿌 공식 인기 아이콘(pop_icon2.jpg) 또는 핫 아이콘(hot_icon2.jpg) 감지
            hot_icon = row.select_one('img[src*="hot_icon2.jpg"], img[src*="pop_icon2.jpg"]')
                
            full_title = title_el.get_text(strip=True)
            if not full_title or "공지" in full_title or "질문" in full_title or "문의" in full_title: return None
                 
            link_el = title_el if title_el.has_attr('href') else title_el.find_parent('a')
            if not link_el or not link_el.get('href'):
                link_el = row.select_one('a')
            if not link_el or not link_el.get('href'): return None
                
            href = link_el.get('href')
            if href.startswith('javascript'): return None
            
            from urllib.parse import urlparse, parse_qs, urlencode, urlunparse
            parsed_href = urlparse(href)
            qs = parse_qs(parsed_href.query)
            
            # [HOTFIX] 현재 스크래핑 중인 게시판 ID와 일치하는 게시글만 수집하도록 보정 (타 게시판 추천글/광고글 섞임 차단!)
            allowed_board_ids = []
            if hasattr(self, 'list_urls') and self.list_urls:
                for u in self.list_urls:
                    q = parse_qs(urlparse(u).query)
                    bid = q.get('id', [None])[0]
                    if bid: allowed_board_ids.append(bid)
            if hasattr(self, 'list_url') and self.list_url:
                q = parse_qs(urlparse(self.list_url).query)
                bid = q.get('id', [None])[0]
                if bid and bid not in allowed_board_ids: allowed_board_ids.append(bid)
                
            if not allowed_board_ids:
                allowed_board_ids = ['ppomppu']
                
            board_id = qs.get('id', [''])[0]
            if board_id not in allowed_board_ids:
                return None
                
            qs.pop('page', None)
            qs.pop('divpage', None)
            clean_query = urlencode(qs, doseq=True)
            clean_href = urlunparse(parsed_href._replace(query=clean_query))
                
            url = urljoin("https://www.ppomppu.co.kr/zboard/", clean_href)
            
            # 🖼️ 썸네일 추출 시도 
            image_url = ""
            img_td = None
            
            # 리스트 tr 내의 이미지 태그들 중 썸네일 확률이 높은 이미지를 정확하게 우선 스캐닝합니다.
            img_tags = row.select('img')
            for img in img_tags:
                src = img.get('src') or ''
                if any(x in src.lower() for x in ['_thumb', 'data3', 'noimage', 'data/']):
                    img_td = img
                    break
                    
            if not img_td:
                img_td = row.select_one('td img.thumb_border')
            if not img_td:
                img_td = row.select_one('img')
            
            if img_td and img_td.has_attr('src'):
                 image_url = img_td['src']
                 if image_url.startswith('//'): image_url = "https:" + image_url
                 elif not image_url.startswith('http'): image_url = urljoin("https://www.ppomppu.co.kr", image_url)
                 
                 # [E2E 과거 글 차단 2차 철벽 가드]: 썸네일 주소에 과거 년도가 포함된 경우 수집 원천 차단
                 # ⚠️ 현재 연도와 작년은 허용! (range 상한을 current_year - 1 로 설정하여 올해/작년 이미지 오차단 방지)
                 if "ppomppu" in image_url:
                     from datetime import datetime as dt_img
                     img_current_year = dt_img.now().year
                     if any(f"/{yr}/" in image_url for yr in [str(y) for y in range(2000, img_current_year - 1)]):
                         logger.info(f"[뽐뿌] 이미지 내 과거 연도 감지로 수집 스킵: {full_title} ({image_url})")
                         return None

            # 만약 썸네일 주소가 투명 이미지이거나 아이콘, 또는 노이미지 엑스박스면 비움 처리
            if image_url:
                img_url_lower = image_url.lower()
                if any(x in img_url_lower for x in ['transparent', 'blank', 'logo', 'icon', 'empty', 'noimage']) or image_url.startswith('data:') or 'base64' in img_url_lower:
                    image_url = ""

            # 제목에서 가격/통화 추출 (단일 진실 공급원 deal_normalizer 위임)
            import re
            from backend.core.deal_normalizer import parse_title_price_and_shipping
            extracted_price, extracted_currency, _, _ = parse_title_price_and_shipping(full_title)

            is_closed = False
            if '종료' in full_title or '마감' in full_title or '품절' in full_title:
                is_closed = True
            if title_el.select_one('del, s, strike, font[color="#999999"]'):
                is_closed = True
            if 'end' in title_el.get('class', []) or 'end2' in title_el.get('class', []):
                is_closed = True
            if row.select_one('img[src*="end_icon"]'):
                is_closed = True

            doc_id = ""
            is_super_hotdeal = False
            doc_id_match = re.search(r'no=([0-9]+)', href)
            if doc_id_match:
                doc_id = doc_id_match.group(1)
                
            view_count = 0
            like_count = 0
            for td in row.select('td.eng, td.baseList-space'):
                txt = td.get_text(strip=True).replace(',', '')
                if txt == doc_id:
                    continue # 게시글 번호(ID)는 건너뜀
                
                if '-' in txt and len(txt) < 10: # 추천수 포맷 (예: 11 - 0)
                    parts = txt.split('-')
                    if parts[0].strip().isdigit():
                        like_count = int(parts[0].strip())
                elif txt.isdigit(): # 조회수 (게시글 번호를 제외한 숫자)
                    view_count = int(txt)

            # 뽐뿌 공식 인기/핫 아이콘(pop_icon2.jpg / hot_icon2.jpg) 또는 추천 8개 이상 시 슈퍼핫딜 인정
            if hot_icon or like_count >= 8 or view_count >= 2500:
                is_super_hotdeal = True

            # 인기/HOT 여부(is_super_hotdeal)는 프론트엔드 UI의 뱃지(마크) 표시용으로 사용됨
            comment_count = 0
            cmt_span = row.select_one('.list_comment2 span, .list_comment2, .comment_count')
            if cmt_span:
                cmt_txt = cmt_span.get_text(strip=True).replace('[', '').replace(']', '')
                if cmt_txt.isdigit(): comment_count = int(cmt_txt)

            # 실제 게시글 작성 시간 추출
            posted_at_iso = None
            time_tds = row.select('nobr.eng, td.eng, td.baseList-time, time.baseList-time')
            time_str = ""
            for td in time_tds:
                txt = td.get_text(strip=True)
                # [버그 예방] 조회수나 큰 숫자가 든 열을 배제하여 2012년 오역 참사를 방어합니다.
                if len(txt) > 20 or txt.isdigit():
                    continue
                if ':' in txt or '/' in txt or '-' in txt:
                    # 추천수(11 - 0) 필터링
                    if re.search(r'\d+\s*-\s*\d+', txt) and not re.search(r'\d{4}-\d{2}-\d{2}', txt):
                        continue
                    time_str = txt
                    break

            if time_str:
                posted_at_iso = self.parse_time_str(time_str)

            # [과거 글 수집 원천 배제 가드]: 뽐뿌 하단의 아주 과거(2012년 등) 박제글 수집 배제
            # ⚠️ 현재 연도와 작년은 정상 게시글이므로 차단하면 안 됨! (range 상한을 current_year - 1 로 설정)
            if posted_at_iso:
                from datetime import datetime as dt
                current_year = dt.now().year
                if any(posted_at_iso.startswith(str(yr)) for yr in range(2000, current_year - 1)):
                    logger.info(f"[뽐뿌] 아주 과거의 딜 차단 (수집 스킵): {full_title} ({posted_at_iso})")
                    return None

            # 제목 앞 대괄호 분류 파싱
            scraped_category = None
            category_match = re.match(r'^\[(.*?)\]', full_title)
            if category_match:
                scraped_category = category_match.group(1).strip()

            return {
                "title": full_title,
                "url": url,
                "price": extracted_price,
                "currency": extracted_currency,
                "shop_name": "",
                "image_url": image_url,
                "ecommerce_link": "",
                "content_html": "",
                "is_closed": is_closed,
                "shipping_fee": "",
                "is_super_hotdeal": is_super_hotdeal,
                "posted_at": posted_at_iso,
                "view_count": view_count,
                "like_count": like_count,
                "comment_count": comment_count,
                "category": scraped_category
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
        """
        상세 페이지에 접속하여 본문(HTML)과 추가 추출 정보(예: 쿠폰, 배송비)를 가져옵니다.
        """
        headers = self._get_headers()
        headers["Referer"] = self.list_url  # 봇 차단 및 로그인 리다이렉트 방지를 위한 Referer 설정
        html = await self.fetch_html(url, headers=headers)
        if not html:
            return {}
        if any(k in html for k in ("해당 게시물이 존재하지 않거나 삭제", "존재하지 않는 게시물", "삭제된 게시물입니다")):
            return {"is_closed": True}
        soup = BeautifulSoup(html, 'html.parser')

        detail_title = ""
        og_t = soup.find("meta", property="og:title")
        if og_t and og_t.get("content"):
            detail_title = og_t.get("content").strip()
        if not detail_title:
            vt_el = soup.select_one("font.view_title2, .topTitle-mainbox h1, #topTitle h1")
            if vt_el:
                detail_title = vt_el.get_text(" ", strip=True)
        if detail_title and "�" not in detail_title:
            if not full_title or full_title.endswith("...") or len(detail_title) > len(full_title):
                full_title = detail_title

        ecommerce_link = ""
        # 1. 뽐뿌 개편 레이아웃 상단 구매 링크 (.topTitle-box, .topTitle-mainbox, .topTitle-link) 및 구형 레이아웃 (.word, .word_break a)
        # 닉네임(baseList-name), 프로필 등 오탐지 방지 위해 후보 a태그 중 실제 아웃링크(http)만 엄격 필터링
        # s.ppomppu.co.kr 은 리디렉터이므로 허용하고, 일반 뽐뿌 내부 도메인은 엄격 제외
        link_tags = soup.select('.topTitle-box a, .topTitle-mainbox a, .topTitle-link a, .word, .word_break a, .word a')
        for tag in link_tags:
            href = tag.get('href', '') or ''
            if href.startswith('http'):
                is_valid_outlink = 's.ppomppu.co.kr' in href or not any(x in href for x in ['ppomppu.co.kr', 'ppomppu4.co.kr', 'ppomppu8.co.kr', 'javascript:', '#'])
                if is_valid_outlink:
                    ecommerce_link = href
                    break
            
        # 2. 없으면 전체 a 태그 중 외부 쇼핑몰 직접 주소 또는 뽐뿌 리디렉터 수색
        if not ecommerce_link:
            for a in soup.find_all('a'):
                href = a.get('href', '') or ''
                if not href:
                    continue
                # 뽐뿌 리디렉터 또는 외부 쇼핑몰 링크 (ppomppu.co.kr, ppomppu8.co.kr 등 내부 링크 배제)
                if 's.ppomppu.co.kr' in href:
                    ecommerce_link = href
                    break
                elif href.startswith('http') and 'ppomppu.co.kr' not in href and 'ppomppu4.co.kr' not in href and 'ppomppu8.co.kr' not in href:
                    ecommerce_link = href
                    break
                    
        # 3. 뽐뿌 리디렉터 해독 (base64 target 디코딩)
        if "s.ppomppu.co.kr" in ecommerce_link:
            import urllib.parse
            import base64
            ecommerce_link_clean = ecommerce_link.replace("&amp;", "&").replace("&AMP;", "&")
            try:
                parsed_url = urllib.parse.urlparse(ecommerce_link_clean)
                query_params = urllib.parse.parse_qs(parsed_url.query)
                encoded_target = query_params.get("target", [None])[0] or query_params.get("amp;target", [None])[0]
                if encoded_target:
                    encoded_target = encoded_target.strip()
                    encoded_target += '=' * (-len(encoded_target) % 4)
                    decoded_url = base64.b64decode(encoded_target).decode('utf-8')
                    if decoded_url and decoded_url.startswith('http'):
                        ecommerce_link = decoded_url
            except Exception:
                pass
                
        # 가격 파싱 폴백 (게시글 본문에서 가격 추출 시도)
        price_fallback = 0
        import re

        # 1. 이미지 태그를 먼저 추출 (아래 a.string 치환 시 a 태그 내부 img가 소실되는 버그 원천 차단!)
        images = []
        image_url = ""
        for img in soup.select('td.board-contents img, td.han img, table.pic_bg img, .cont img'):
            src = (img.get('data-original') or img.get('data-src') or img.get('src') or '').strip()
            if src and src.startswith('//'):
                src = 'https:' + src
            if src and 'http' in src:
                src_lower = src.lower()
                # 이모티콘, 스티커, 로고, 프로필, 레이지로딩 플레이스홀더 등 배제
                if any(x in src_lower for x in [
                    'emoticon', 'sticker', 'transparent', 'logo', 'icon', 'reply',
                    'blank', 'avatar', 'profile', 'lazyloading', '/t.gif', 'drag_img'
                ]):
                    continue
                # 📸 [상세 고화질 변환 활성화]
                if "ppomppu" in src:
                    src = src.replace("_thumb/", "").replace("thumb/", "")
                    src = src.replace("/small_", "/").replace("small_", "")
                    src = src.replace("_thumb.", ".")
                if src not in images:
                    images.append(src)
                if not image_url:
                    image_url = src

        # 2. 본문 엘리먼트 정밀 탐색 (우선순위 셀렉터 순회 + 상단 헤더 .cont 오탐지 원천 차단)
        content_element = None
        for sel in ('td.board-contents', 'div.board-contents', 'td.han', 'table.pic_bg td'):
            for candidate in soup.select(sel):
                cand_txt = candidate.get_text(strip=True)
                if "뽐뿌 - 사람이 좋아 함께하는 곳" in cand_txt and len(cand_txt) < 80:
                    continue
                if len(cand_txt) > 0 or candidate.select('img, a'):
                    content_element = candidate
                    break
            if content_element is not None:
                break

        if content_element:
            import urllib.parse
            import base64
            # 뽐뿌 링크 프리뷰 박스(scrap_bx)의 중복 쇼핑몰 홍보 문구 제거 (본문 가독성 및 다중 옵션 파싱 정확도 향상)
            for scrap in content_element.select('a.scrap_bx_href, div.scrap_bx'):
                scrap.decompose()

            # 본문 내 이미지 태그의 DOM 위치를 [상품이미지: URL] 마커로 보존 (모음전 각 번호 상품과 1:1 이미지 매칭 보장)
            for img in content_element.find_all('img'):
                src = (img.get('data-original') or img.get('data-src') or img.get('src') or '').strip()
                if src.startswith('//'):
                    src = 'https:' + src
                if src and 'http' in src:
                    src_lower = src.lower()
                    if any(x in src_lower for x in [
                        'emoticon', 'sticker', 'transparent', 'logo', 'icon', 'reply',
                        'blank', 'avatar', 'profile', 'lazyloading', '/t.gif', 'drag_img'
                    ]):
                        continue
                    if "ppomppu" in src:
                        src = src.replace("_thumb/", "").replace("thumb/", "")
                        src = src.replace("/small_", "/").replace("small_", "")
                        src = src.replace("_thumb.", ".")
                    parent_a = img.find_parent('a')
                    if parent_a is not None:
                        parent_a.insert_before(f"\n[상품이미지: {src}]\n")
                        img.decompose()
                    else:
                        img.replace_with(f"\n[상품이미지: {src}]\n")

            # 링크 보존 및 본문 내 실제 구매 링크 추출 (상단 더미 링크 오탐 방어)
            body_outlinks = []
            for a in content_element.find_all('a'):
                href = a.get('href', '')
                if "s.ppomppu.co.kr" in href:
                    href_clean = href.replace("&amp;", "&").replace("&AMP;", "&")
                    try:
                        parsed_url = urllib.parse.urlparse(href_clean)
                        query_params = urllib.parse.parse_qs(parsed_url.query)
                        encoded_target = query_params.get("target", [None])[0] or query_params.get("amp;target", [None])[0]
                        if encoded_target:
                            encoded_target = encoded_target.strip()
                            encoded_target += '=' * (-len(encoded_target) % 4)
                            decoded = base64.b64decode(encoded_target).decode('utf-8')
                            if decoded and decoded.startswith('http'):
                                href = decoded
                    except Exception:
                        pass
                a_text = a.get_text(strip=True)
                if href and href.startswith('http'):
                    if not any(x in href for x in ['ppomppu.co.kr', 'ppomppu4.co.kr', 'ppomppu8.co.kr']):
                        body_outlinks.append(href)
                    if a_text and href not in a_text:
                        a.string = f"{a_text} (링크: {href})"
                    else:
                        a.string = f"(링크: {href})"
            # 본문에 실제 구매 링크가 존재하고, 상단 링크가 없거나 알리뽐뿌 더미 링크인 경우 본문 첫 번째 링크 우선 채택
            if body_outlinks:
                if not ecommerce_link or (self.platform_name == "알리뽐뿌" and ecommerce_link not in body_outlinks):
                    ecommerce_link = body_outlinks[0]
            body_text = content_element.get_text(separator='\n', strip=True)
        else:
            body_text = ""

        html_text = soup.get_text(separator=' ', strip=True)
        is_closed = False
        if ("종결" in html_text and "취소된 게시물" in html_text) or any(
            k in html_text for k in ("존재하지 않는 게시물", "삭제된 게시물", "게시물이 존재하지 않습니다", "블라인드 처리된")
        ):
            is_closed = True

        if not body_text and not images:
            if is_closed:
                return {"is_closed": True}
            return {}

        if images:
            body_text += f"\n[첨부된 이미지 링크들]: {', '.join(images)}"
            
        from backend.core.deal_normalizer import extract_body_fallback_price
        price_fallback, extracted_currency = extract_body_fallback_price(body_text, full_title or "")
                
        shipping_fee = ""
        # 1. 명시적인 테이블 정보(배송비/택배비) 확인
        # [HOTFIX] 본문 td.han이 th로 잘못 매칭되어 배송비 필드가 본문으로 오염되는 현상 원천 차단
        for tr in soup.select('table.board_table tr, table[class*="board_table"] tr, tr'):
            th = tr.select_one('th')
            td = tr.select_one('td')
            if th and td:
                th_text = th.get_text(strip=True)
                if len(th_text) < 15 and ('배송비' in th_text or '택배비' in th_text):
                    fee_text = td.get_text(strip=True)
                    if fee_text and len(fee_text) < 50:
                        shipping_fee = fee_text
                        break
        
        # 2. 본문 휴리스틱
        if not shipping_fee:
            search_text = full_title + " " + body_text
            import re
            if re.search(r'(무료배송|무배|배송비\s*무배|배송비\s*무료|\(\s*무료\s*\)|/\s*무료|무료\s*/|무료\s*$)', search_text):
                shipping_fee = "무료배송"
        
        if ("종결" in body_text and "취소된 게시물" in body_text) or any(
            k in body_text for k in ("존재하지 않는 게시물", "삭제된 게시물", "게시물이 존재하지 않습니다", "블라인드 처리된")
        ):
            is_closed = True
            
        posted_at_iso = None
        # 1차: 등록일 키워드 기반 상세 매칭
        dates = re.findall(r'등록일\s*[:\s]*(\d{4}-\d{2}-\d{2}\s*\d{2}:\d{2}(?::\d{2})?)', html_text)
        if dates:
            posted_at_iso = self.parse_time_str(dates[0])
        else:
            # 2차 폴백: 등록일 키워드가 없는 순수 날짜/시간 형식 추출 (글쓴이 옆 2026-06-01 22:19 포맷 방어)
            naive_dates = re.findall(r'(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}(?::\d{2})?)', html_text)
            if naive_dates:
                posted_at_iso = self.parse_time_str(naive_dates[0])
                
        # [과거 날짜 세컨드 가드] 상세 페이지에서 구한 시간이 과거 연도인 경우 무효화
        # ⚠️ 현재 연도와 작년은 정상 게시글이므로 무효화하면 안 됨!
        if posted_at_iso:
            from datetime import datetime as dt
            current_year = dt.now().year
            if any(posted_at_iso.startswith(str(yr)) for yr in range(2000, current_year - 1)):
                logger.info(f"[뽐뿌 상세] 과거 날짜 감지로 무효화: {posted_at_iso}")
                posted_at_iso = None
            
        # 📸 [고화질 썸네일 복구 가드]: 본문 첨부 고화질 이미지(data3, cdn.ppomppu 등)가 없을 때만 외부 아웃링크 og:image 및 상품명 폴백 사용
        has_valid_body_img = bool(
            image_url and any(
                x in image_url for x in ('/data3/', '/data2/', 'cdn.ppomppu', 'cdn2.ppomppu', 'cdn3.ppomppu', 'cdn4.ppomppu', 'files/attach')
            )
        )
        if not has_valid_body_img:
            og_img = await self.fetch_og_image(ecommerce_link, full_title)
            if og_img:
                logger.info(f"✨ [뽐뿌 og:image] 외부 아웃링크/상품명에서 고화질 썸네일 확보: {og_img}")
                image_url = og_img

        # [CEO 피드백: 모음전 분할을 위해 텍스트 길이 충분한 본문을 content_html로 반환]
        res_dict = {
            "ecommerce_link": ecommerce_link, 
            "price": price_fallback, 
            "currency": extracted_currency,
            "shipping_fee": shipping_fee, 
            "content_html": body_text, 
            "image_url": image_url,
            "is_closed": is_closed,
            "posted_at": posted_at_iso
        }
        if detail_title and "�" not in detail_title:
            res_dict["title"] = detail_title
        return res_dict
