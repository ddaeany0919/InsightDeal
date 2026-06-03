import logging
import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class PpomppuScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        super().__init__("뽐뿌", max_concurrent_requests=5)
        self.community_id = community_id
        # CEO 피드백: 뽐뿌 전체 핫딜을 수집하되, 추천수(like_count)로 인기 마크를 판별
        self.list_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu"

    async def parse_list(self, html: str) -> list[dict]:
        """뽐뿌 게시판 리스트에서 제목과 URL (그리고 가능하면 가격) 추출"""
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
            
            # [CEO 피드백] 뽐뿌의 핫딜 마크(is_super_hotdeal)는 hot_icon2.jpg 가 있을 때만 True로 설정
            hot_icon = row.select_one('img[src*="hot_icon2.jpg"]')
                
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

            # 제목에서 가격 추출 시도 (예: 15,900원, 3만9천원)
            import re
            extracted_price = 0
            extracted_currency = "KRW"
            
            is_overseas = (self.platform_name in ["뽐뿌해외", "알리뽐뿌"])
            
            usd_match = None
            if is_overseas:
                usd_match = re.search(r'(?:\$|USD|달러|유로|€)\s*([0-9,.]+)', full_title, re.IGNORECASE)
                if not usd_match:
                    usd_match = re.search(r'([0-9,.]+)\s*(?:\$|USD|달러|유로|€)', full_title, re.IGNORECASE)
                
            price_match = re.search(r'([\d,]+(?:원|만원))', full_title)
            
            if is_overseas and usd_match:
                try:
                    val = float(usd_match.group(1).replace(',', ''))
                    extracted_price = round(val * 100)
                    if '유로' in full_title or '€' in full_title:
                        extracted_currency = "EUR"
                    else:
                        extracted_currency = "USD"
                except: pass
            elif price_match:
                price_str = price_match.group(1).replace(',', '')
                if '만원' in price_str:
                    try: extracted_price = int(float(price_str.replace('만원', '')) * 10000)
                    except: pass
                else:
                    nums = re.findall(r'\d+', price_str)
                    if nums: extracted_price = int(''.join(nums))

            # 휴리스틱: 제목에 직구 관련 키워드가 있고 가격이 10000 이하면 USD로 간주
            if is_overseas and extracted_currency == "KRW" and extracted_price > 0 and extracted_price <= 10000:
                if any(kw in full_title for kw in ['알리', '코인', '큐텐', '직구', '알익']):
                    extracted_currency = "USD"
                    extracted_price = int(extracted_price * 100)

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

            # CEO 지시: 뽐뿌는 hot_icon2.jpg가 있는 경우에만 슈퍼핫딜로 인정
            if hot_icon:
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
            
        tasks = [process_row(row) for row in post_rows]
        results = await asyncio.gather(*tasks)
        return [r for r in results if r]

    async def get_detail(self, url: str, full_title: str = "") -> dict:
        """
        상세 페이지에 접속하여 본문(HTML)과 추가 추출 정보(예: 쿠폰, 배송비)를 가져옵니다.
        """
        headers = self._get_headers()
        headers["Referer"] = self.list_url  # 봇 차단 및 로그인 리다이렉트 방지를 위한 Referer 설정
        html = await self.fetch_html(url, headers=headers)
        if not html: return {}
        soup = BeautifulSoup(html, 'html.parser')
        
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
        
        content_element = soup.select_one('td.board-contents') or soup.select_one('table.pic_bg td') or soup.select_one('td.han') or soup.select_one('.cont')
        if content_element:
            import urllib.parse
            import base64
            # 링크 보존
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
                    except: pass
                # a 태그 내용을 "[텍스트](링크)" 형태로 변경
                new_text = f"{a.get_text()} (링크: {href})"
                a.string = new_text
            body_text = content_element.get_text(separator=' \n ', strip=True)
        else:
            body_text = soup.get_text(separator=' ')
        
        # 이미지 태그 추출해서 본문에 덧붙이기 (Gemini 매칭용)
        images = []
        image_url = ""
        for img in soup.select('.board-contents img, table.pic_bg img, .han img, .cont img'):
            src = img.get('src')
            if src and src.startswith('//'): src = 'https:' + src
            if src and 'http' in src:
                src_lower = src.lower()
                # 이모티콘, 스티커, 로고, 프로필 등 배제
                if any(x in src_lower for x in ['emoticon', 'sticker', 'transparent', 'logo', 'icon', 'reply', 'blank', 'avatar', 'profile']):
                    continue
                # 📸 [상세 고화질 변환 활성화]: 이제 프록시가 작동하므로 고해상도 이미지를 추출합니다.
                if "ppomppu" in src:
                    src = src.replace("_thumb/", "").replace("thumb/", "")
                    src = src.replace("/small_", "/").replace("small_", "")
                    src = src.replace("_thumb.", ".")
                images.append(src)
                if not image_url:
                    image_url = src
                    
        if images:
            body_text += f"\n[첨부된 이미지 링크들]: {', '.join(images)}"
            
        # 예: 15,000원, 23,500 원, 49900원 등
        extracted_currency = "KRW"
        
        is_overseas = (self.platform_name in ["뽐뿌해외", "알리뽐뿌"])
        
        # [방어막 추가]: 본문 파싱 전 URL 소거 처리! (링크 내의 숫자 오인 차단)
        cleaned_body_text = re.sub(r'https?://[^\s]+', '', body_text)
        
        usd_match = None
        if is_overseas:
            usd_match = re.search(r'(?:\$|USD|달러|유로|€)\s*([0-9,.]+)', cleaned_body_text, re.IGNORECASE)
            if not usd_match:
                usd_match = re.search(r'([0-9,.]+)\s*(?:\$|USD|달러|유로|€)', cleaned_body_text, re.IGNORECASE)
            
        price_matches = re.findall(r'([0-9]{1,3}(?:[,\.][0-9]{3})*|[0-9]+)\s*원', cleaned_body_text)
        
        if is_overseas and usd_match:
            try:
                val = float(usd_match.group(1).replace(',', ''))
                price_fallback = round(val * 100)
                if '유로' in body_text or '€' in body_text:
                    extracted_currency = "EUR"
                else:
                    extracted_currency = "USD"
            except: pass
        elif price_matches:
            try:
                # 본문에서 찾은 가격 중 가장 합리적인 첫 번째 금액
                price_fallback = int(price_matches[0].replace(',', '').replace('.', ''))
            except:
                pass
                
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
            if re.search(r'(무료배송|무배|배송비\s*mu배|배송비\s*무료|\(\s*무료\s*\)|/\s*무료|무료\s*/|무료\s*$)', search_text):
                shipping_fee = "무료배송"
        
        is_closed = False
        if "종결" in body_text and "취소된 게시물" in body_text:
            is_closed = True
            
        posted_at_iso = None
        html_text = soup.get_text(separator=' ', strip=True)
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
            
        # 📸 [고화질 썸네일 복구 가드]: 아웃링크(ecommerce_link)가 있으면 항상 고화질 og:image 조회를 시도하고 확보 시 우선 덮어쓰기
        if ecommerce_link:
            og_img = await self.fetch_og_image(ecommerce_link)
            if og_img:
                logger.info(f"✨ [뽐뿌 og:image] 외부 아웃링크에서 고화질 썸네일 확보: {og_img}")
                image_url = og_img

        # [CEO 피드백: 모음전 분할을 위해 텍스트 길이 충분한 본문을 content_html로 반환]
        return {
            "ecommerce_link": ecommerce_link, 
            "price": price_fallback, 
            "currency": extracted_currency,
            "shipping_fee": shipping_fee, 
            "content_html": body_text, 
            "image_url": image_url,
            "is_closed": is_closed,
            "posted_at": posted_at_iso
        }
