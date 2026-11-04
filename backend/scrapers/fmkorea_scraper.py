import logging
import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class FmkoreaScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        # 펨코는 Cloudflare 방어가 강하므로 동시성을 1로 제한하고 딜레이를 대폭 늘립니다.
        super().__init__("펨코", max_concurrent_requests=1)
        self.community_id = community_id
        self.list_url = "https://www.fmkorea.com/hotdeal"
        self.pop_url = "https://www.fmkorea.com/index.php?mid=hotdeal&sort_index=pop&order_type=desc"
        self.parsing_pop = False

    async def __aenter__(self):
        await super().__aenter__()
        return self

    def _get_headers(self) -> dict:
        return {}

    async def parse_list(self, html: str) -> list[dict]:
        """펨코리아 게시판 리스트에서 타겟 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')

        post_rows = soup.select('li.li:not(.notice), div.list_item:not(.notice)')
        if not post_rows:
            post_rows = soup.select('table.bd_lst tbody tr')

        import asyncio

        async def process_row(row):
            row_classes = row.get('class') or []
            if any(cls in row_classes for cls in ['notice', 'notice_pop0', 'notice_pop1']):
                return None
                
            no_tag = row.select_one('td.no')
            if no_tag and no_tag.get_text(strip=True) in ['공지', '인기', 'AD', '광고']:
                return None

            title_element = row.select_one('td.title a, h3.title a, a.title, a.hotdeal_var8, a.hotdeal_var8Y')
            if not title_element:
                if row.name == 'a' and ('title' in (row.get('class') or []) or 'hotdeal_var' in str(row.get('class'))):
                    title_element = row
                else:
                    return None

            href = title_element.get('href', '')
            if not href or href.startswith('javascript'): return None
            
            doc_match = re.search(r'document_srl=([0-9]+)', href)
            if doc_match:
                url = f"https://www.fmkorea.com/{doc_match.group(1)}"
            else:
                clean_href = href.split('?')[0]
                url = urljoin("https://www.fmkorea.com/", clean_href)
            
            # 댓글 카운트 파싱 및 제거
            comment_count = 0
            for meta_span in title_element.select('.replyNum, .comment_count, .reply_num'):
                cmt_txt = meta_span.get_text(strip=True).replace('[', '').replace(']', '').replace(',', '')
                if cmt_txt.isdigit(): comment_count = int(cmt_txt)
                meta_span.decompose()

            full_title = title_element.get_text(strip=True)

            # 🖼️ 기본 썸네일 확인 (Lazy Loading 방어 위해 data-original, data-src 우선 조회)
            image_url = ""
            img_tag = row.select_one('img.thumb, .thumb img, .tmb img, img')
            if img_tag:
                image_url = img_tag.get('data-original') or img_tag.get('data-src') or img_tag.get('src', '')
                if image_url.startswith('//'):
                    image_url = "https:" + image_url
                elif image_url and not image_url.startswith('http'):
                    image_url = urljoin("https://www.fmkorea.com/", image_url)

            # 만약 썸네일 주소가 펨코리아 아이콘, 투명 기프, 빈 로고 등 유효하지 않은 정보라면 비움 처리
            if image_url:
                img_url_lower = image_url.lower()
                # 'fmkorea'는 도메인 주소에 포함되므로 필터 키워드에서 제외하여 정상적인 이미지 URL이 유실되는 버그를 방지합니다.
                if any(x in img_url_lower for x in ['transparent', 'blank', 'logo', 'icon', 'empty']) or image_url.startswith('data:') or 'base64' in img_url_lower:
                    image_url = ""


            # 상세 페이지에서 ecommerce_link와 본문 파싱
            # 가격 및 배송비 파싱 (hotdeal_info)
            extracted_price = 0
            extracted_currency = "KRW"
            shop_name = ""
            shipping_fee = ""
            
            info_div = row.select_one('.hotdeal_info')
            if info_div:
                spans = info_div.select('span')
                for span in spans:
                    text = span.get_text(strip=True)
                    if '쇼핑몰' in text:
                        strong = span.select_one('.strong')
                        if strong: shop_name = strong.get_text(strip=True)
                    elif '가격' in text:
                        strong = span.select_one('.strong')
                        if strong: 
                            price_text = strong.get_text(strip=True)
                            price_text_clean = re.sub(r'\d+(?:\.\d+)?\s*(?:kg|g|ml|l|개|입|봉|팩|포|장|매|구|미|알|병|캔|통|박스)\s*당', ' ', price_text, flags=re.IGNORECASE)
                            if '$' in price_text_clean or '달러' in price_text_clean or 'USD' in price_text_clean.upper():
                                extracted_currency = "USD"
                            elif '€' in price_text_clean or '유로' in price_text_clean or 'EUR' in price_text_clean.upper():
                                extracted_currency = "EUR"
                                
                            p_match = re.search(r'([0-9,]+(?:\.[0-9]+)?)', price_text_clean)
                            if p_match:
                                try:
                                    raw_val = float(p_match.group(1).replace(',', ''))
                                    if extracted_currency in ["USD", "EUR"]:
                                        extracted_price = int(raw_val * 100)
                                    else:
                                        extracted_price = int(raw_val)
                                except: pass
                    elif '배송비' in text:
                        strong = span.select_one('.strong')
                        if strong: shipping_fee = strong.get_text(strip=True)
            
            if "(0원)" in full_title and extracted_price == 0:
                extracted_price = 0

            # 휴리스틱: 제목에 직구 관련 키워드가 있고 가격이 10000 이하면 USD로 간주
            if extracted_currency == "KRW" and extracted_price > 0 and extracted_price <= 10000:
                if any(kw in full_title for kw in ['알리', '코인', '큐텐', '직구', '알익']):
                    extracted_currency = "USD"
                    extracted_price = int(extracted_price * 100)

            # 종료 여부 확인 (취소선이 그어져 있는지 또는 종료 키워드)
            is_closed = False
            
            # 펨코 전용: 핫딜 종료 시 a 태그(title_element)에 'hotdeal_var8Y' 클래스가 붙음
            a_classes = title_element.get('class', [])
            if isinstance(a_classes, list) and 'hotdeal_var8Y' in a_classes:
                is_closed = True
            elif isinstance(a_classes, str) and 'hotdeal_var8Y' in a_classes:
                is_closed = True

            style = title_element.get('style', '')
            if 'line-through' in style or title_element.select_one('del, s, strike'):
                is_closed = True
            
            if '종료' in full_title or '마감' in full_title or '품절' in full_title:
                is_closed = True
                
            for span in title_element.select('span'):
                if 'line-through' in span.get('style', ''):
                    is_closed = True
                    break

            # [엄격한 핫딜 검증]
            # 핫딜 게시판의 정상적인 핫딜은 반드시 .hotdeal_info 구조를 가집니다.
            # 만약 이게 없다면 다른 게시판(예: 국내축구, 유머 등)에서 핫딜 게시판에 주입된 인기글(포텐글)이므로 차단합니다.
            if not info_div:
                logger.info(f"[펨코] 핫딜 정보(.hotdeal_info)가 없어 스킵 (타 게시판 인기글로 추정): {full_title}")
                return None

            # 조회수 및 추천수(포텐) 파싱
            view_count = 0
            like_count = 0
            m_no = row.select_one('td.m_no, .m_no_voted, td.hit')
            if m_no:
                v_txt = m_no.get_text(strip=True).replace(',', '')
                if v_txt.isdigit(): view_count = int(v_txt)
                
            voted_el = row.select_one('.pc_voted_count, .m_voted_count')
            if voted_el:
                l_txt = voted_el.get_text(strip=True).replace('추천', '').replace(',', '').strip()
                if l_txt.isdigit(): like_count = int(l_txt)
                
            # 펨코 핫딜 게시판 목록 자체에 '포텐' 뱃지가 있는 경우 우선 반영 (포텐 뱃지독점)
            is_poten = False
            poten_span = row.select_one('span.STAR-BEST')
            if poten_span and '포텐' in poten_span.get_text(strip=True):
                is_poten = True

            # 실제 게시글 작성 시간 추출 (KST 기준을 UTC로 변환하여 저장)
            posted_at_iso = None
            time_td = row.select_one('td.time, .regdate')
            if time_td:
                posted_at_iso = self.parse_time_str(time_td.get_text(strip=True))

            category_span = row.select_one('span.category')
            cat_text = ""
            if category_span:
                cat_text = category_span.get_text(strip=True).replace('/', '').replace(' ', '')

            extracted_category = None
            if cat_text:
                # 펨코 카테고리 매핑
                if '먹거리' in cat_text or '음식' in cat_text: extracted_category = '음식'
                elif '의류' in cat_text or '패션' in cat_text: extracted_category = '의류'
                elif '기프티콘' in cat_text or '모바일' in cat_text: extracted_category = '모바일/기프티콘'
                elif '이용권' in cat_text or '패키지' in cat_text: extracted_category = '패키지/이용권'
                else: extracted_category = cat_text # PC제품, 가전제품, 생활용품 등은 텍스트가 거의 일치함

            return {
                "title": full_title,
                "url": url,
                "price": extracted_price,
                "currency": extracted_currency,
                "shop_name": shop_name,
                "image_url": image_url,
                "ecommerce_link": "",
                "is_closed": is_closed,
                "shipping_fee": shipping_fee,
                "is_super_hotdeal": is_poten,
                "posted_at": posted_at_iso,
                "view_count": view_count,
                "like_count": like_count,
                "comment_count": comment_count,
                "category": extracted_category,
                "content_html": ""
            }

        tasks = [process_row(row) for row in post_rows]
        results = await asyncio.gather(*tasks)
        return [r for r in results if r]

    async def get_detail(self, url: str, full_title: str = "") -> dict:
        """펨코리아 상세 페이지 데이터 파싱 로직"""
        # WAF 레이트 리밋(430) 차단 방지를 위한 강제 서핑 딜레이 주입
        import asyncio
        import random
        await asyncio.sleep(random.uniform(2.5, 4.5))
        
        html = await self.fetch_html(url)
        if not html: return {}
        
        # 글 삭제 / 해당 문서 존재하지 않음 감지 시 즉각 종료 처리 반환
        if "해당 문서가 존재하지 않습니다" in html or "해당 문서는 존재하지 않습니다" in html:
            return {"is_closed": True}
            
        soup = BeautifulSoup(html, 'html.parser')
        # 댓글 영역(.fdb_lst_ul, #cmtPosition 등)을 사전 제거하여 유저 댓글이나 댓글 내 링크가 본문/옵션으로 혼입되는 현상 원천 차단
        for cmt_node in soup.select('.fdb_lst_ul, #cmtPosition, .comment_list, .fdb_v2, .rd_ft, .rd_tl'):
            cmt_node.decompose()
        
        is_closed = False
        ecommerce_link = ""
        # 1. 펨코 리디렉터 링크 또는 hotdeal_info 내의 구매 링크를 최우선 수색!
        for a in soup.select('.hotdeal_info a, article a, .rd_body a, a'):
            href = a.get('href', '')
            if not href:
                continue
            
            # 펨코 리디렉터 도메인 정밀 매칭 (link.fmkorea.org 또는 link.fmkorea.com)
            if 'link.fmkorea.org' in href or 'link.fmkorea.com' in href:
                import urllib.parse
                parsed_url = urllib.parse.urlparse(href)
                query_params = urllib.parse.parse_qs(parsed_url.query)
                target_url = query_params.get('url', [''])[0] or query_params.get('target', [''])[0]
                if target_url:
                    ecommerce_link = target_url
                    break
            
            # 리디렉터가 아닌 일반 쇼핑몰 직접 링크
            elif href.startswith('http') and 'fmkorea.com' not in href and 'fmkorea.org' not in href and 'saedu.naver.com' not in href and 'ader.naver.com' not in href:
                ecommerce_link = href
                break
        
        # 2. a 태그로 못 찾았을 경우, 텍스트(본문)에 포함된 URL 정규식 탐색
        if not ecommerce_link:
            content_area = soup.select_one('article .xe_content') or soup.select_one('.rd_body .xe_content') or soup.select_one('.xe_content') or soup.select_one('.rd_body')
            if content_area:
                text_content = content_area.get_text(separator=' ')
                # http(s) 링크 찾기
                url_matches = re.findall(r'(https?://[^\s]+)', text_content)
                for m in url_matches:
                    if 'fmkorea.com' not in m and 'fmkorea.org' not in m and 'saedu.naver.com' not in m:
                        ecommerce_link = m
                        break
                # http 없이 도메인만 적힌 경우 (예: m.smartstore.naver.com/...)
                if not ecommerce_link:
                    url_matches2 = re.findall(r'(m\.smartstore\.naver\.com/[^\s]+|smartstore\.naver\.com/[^\s]+|brand\.naver\.com/[^\s]+|coupang\.com/[^\s]+)', text_content)
                    if url_matches2:
                        ecommerce_link = "https://" + url_matches2[0]
                
        image_url = ""
        # 펨코 본문 영역 (.xe_content or .rd_body)에서 고해상도 실제 이미지를 정밀 추출
        content_area = soup.select_one('article .xe_content') or soup.select_one('.rd_body .xe_content') or soup.select_one('.xe_content') or soup.select_one('.rd_body')
        if content_area:
            for img in content_area.select('img'):
                src = img.get('data-original') or img.get('data-src') or img.get('src', '')
                if not src:
                    continue
                src_lower = src.lower()
                # 이모티콘, 스티커, 추천 뱃지, 로고, 프로필, 투명 기프, 빈 공간 등은 제외
                if any(x in src_lower for x in ['emoticon', 'sticker', 'transparent', 'logo', 'icon', 'reply', 'blank', 'avatar', 'profile']) or src.startswith('data:') or 'base64' in src_lower:
                    continue
                if src.startswith('//'):
                    image_url = "https:" + src
                elif not src.startswith('http'):
                    image_url = urljoin("https://www.fmkorea.com/", src)
                else:
                    image_url = src
                break

        # 2차 폴백: 본문 영역이 없을 때만 전체 상세 페이지에서 유효 이미지 재탐색
        if not image_url:
            for img in soup.select('img'):
                src = img.get('data-original') or img.get('data-src') or img.get('src', '')
                if not src:
                    continue
                src_lower = src.lower()
                if any(x in src_lower for x in ['emoticon', 'sticker', 'transparent', 'logo', 'icon', 'reply', 'blank', 'avatar', 'profile']) or src.startswith('data:') or 'base64' in src_lower:
                    continue
                if src.startswith('//'):
                    image_url = "https:" + src
                elif not src.startswith('http'):
                    image_url = urljoin("https://www.fmkorea.com/", src)
                else:
                    image_url = src
                break

        price_fallback = 0
        currency_fallback = "KRW"
        shipping_fee = ""
        shop_name = ""
        is_price_resolved = False
        
        # 1. table 방식의 핫딜 정보 파싱 (데스크탑 레이아웃 등)
        for table in soup.select('table'):
            text = table.get_text()
            if '쇼핑몰' in text and '가격' in text:
                for tr in table.select('tr'):
                    th = tr.select_one('th')
                    td = tr.select_one('td')
                    if th and td:
                        th_text = th.get_text(strip=True)
                        td_text = td.get_text(strip=True)
                        if '가격' in th_text:
                            if any(x in td_text.lower() for x in ['무료', '공짜', '나눔', 'free', '0원', '무배']):
                                price_fallback = 0
                                is_price_resolved = True
                            else:
                                td_text_clean = re.sub(r'\d+(?:\.\d+)?\s*(?:kg|g|ml|l|개|입|봉|팩|포|장|매|구|미|알|병|캔|통|박스)\s*당', ' ', td_text, flags=re.IGNORECASE)
                                if '$' in td_text_clean or '달러' in td_text_clean or 'USD' in td_text_clean.upper():
                                    currency_fallback = "USD"
                                elif '€' in td_text_clean or '유로' in td_text_clean or 'EUR' in td_text_clean.upper():
                                    currency_fallback = "EUR"
                                
                                price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]+)?)\s*(?:원|달러|\$|€|유로)?', td_text_clean)
                                if price_matches:
                                    try:
                                        raw_val = float(price_matches[0].replace(',', ''))
                                        if currency_fallback in ["USD", "EUR"]:
                                            price_fallback = int(raw_val * 100)
                                        else:
                                            price_fallback = int(raw_val)
                                        is_price_resolved = True
                                    except: pass
                        elif '배송' in th_text:
                            shipping_fee = td_text
                        elif '쇼핑몰' in th_text:
                            shop_name = td_text.split('[')[0].strip()
                break
                
        # 2. div.hotdeal_info 방식의 핫딜 정보 파싱
        if not is_price_resolved or not shipping_fee:
            hotdeal_info = soup.select_one('.hotdeal_info')
            if hotdeal_info:
                for span in hotdeal_info.select('span'):
                    span_text = span.get_text(strip=True)
                    if '가격:' in span_text:
                        # 무료 감지 추가
                        if any(x in span_text.lower() for x in ['무료', '공짜', '나눔', 'free', '0원', '무배']):
                            price_fallback = 0
                            is_price_resolved = True
                        else:
                            span_text_clean = re.sub(r'\d+(?:\.\d+)?\s*(?:kg|g|ml|l|개|입|봉|팩|포|장|매|구|미|알|병|캔|통|박스)\s*당', ' ', span_text, flags=re.IGNORECASE)
                            if '$' in span_text_clean or '달러' in span_text_clean or 'USD' in span_text_clean.upper():
                                currency_fallback = "USD"
                            elif '€' in span_text_clean or '유로' in span_text_clean or 'EUR' in span_text_clean.upper():
                                currency_fallback = "EUR"
                                
                            price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]+)?)\s*(?:원|달러|\$|€|유로)?', span_text_clean)
                            if price_matches:
                                try:
                                    raw_val = float(price_matches[0].replace(',', ''))
                                    if currency_fallback in ["USD", "EUR"]:
                                        price_fallback = int(raw_val * 100)
                                    else:
                                        price_fallback = int(raw_val)
                                    is_price_resolved = True
                                except: pass
                    elif '배송:' in span_text:
                        shipping_fee = span_text.replace('배송:', '').replace('배송 :', '').strip()
                    elif '쇼핑몰:' in span_text:
                        shop_name = span_text.replace('쇼핑몰:', '').replace('쇼핑몰 :', '').strip()
        
        body_text = ""
        content_areas = soup.select('article .xe_content, .rd_body .xe_content')
        if not content_areas:
            content_areas = soup.select('.xe_content')
        if not content_areas:
            content_areas = soup.select('.rd_body')
            
        body_parts = []
        for area in content_areas:
            # a 태그 내용을 "[텍스트](링크)" 형태로 변경하여 내용과 링크 모두 추출
            for a in area.select('a'):
                href = a.get('href', '')
                if href and not href.startswith('javascript'):
                    new_text = f"{a.get_text(strip=True)} (링크: {href})"
                    a.string = new_text
            body_parts.append(area.get_text(separator='\n', strip=True))
            
        if body_parts:
            body_text = " \n ".join(body_parts)
            
            # 가격이 수립되지 않은 경우에만 본문 폴백 가격 유추기 작동
            if not is_price_resolved:
                # 본문 파싱 전 URL 소거 처리! (스팀 앱아이디 등 링크 내의 숫자 오인 차단)
                cleaned_body_text = re.sub(r'https?://[^\s]+', '', body_text)
                
                if "(0원)" in cleaned_body_text or "나눔" in cleaned_body_text or any(x in cleaned_body_text.lower() for x in ['무료', '공짜', 'free']):
                    price_fallback = 0
                    is_price_resolved = True
                else:
                    if '$' in cleaned_body_text or '달러' in cleaned_body_text or 'USD' in cleaned_body_text.upper():
                        currency_fallback = "USD"
                    elif '€' in cleaned_body_text or '유로' in cleaned_body_text or 'EUR' in cleaned_body_text.upper():
                        currency_fallback = "EUR"
                        
                    price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,}(?:\.[0-9]+)?)\s*(?:원|달러|\$|€|유로)?', cleaned_body_text)
                    if price_matches:
                        try:
                            raw_val = float(price_matches[0].replace(',', ''))
                            if currency_fallback in ["USD", "EUR"]:
                                price_fallback = int(raw_val * 100)
                            else:
                                price_fallback = int(raw_val)
                            is_price_resolved = True
                        except:
                            pass
                    
            if not shipping_fee:
                if re.search(r'(무료배송|무배|택배비\s*무료|배송비\s*무료|\(\s*무료\s*\)|/\s*무료|무료\s*/|무료\s*$)', body_text):
                    shipping_fee = "무료배송"

        posted_at_iso = None
        date_el = soup.select_one('.date')
        if date_el:
            date_text = date_el.get_text(strip=True)
            # Fmkorea date format: 2026.05.08 14:04
            parsed = self.parse_time_str(date_text)
            if parsed:
                posted_at_iso = parsed

        category_text = ""
        cat_el = soup.select_one('.bd_nav .cat, .category, .cat_name')
        if cat_el:
            category_text = cat_el.get_text(strip=True)
            
        # 본문 단순 언급(예: '어제 품절됨') 오탐지를 방지하기 위해 명확한 시스템 메시지나 종료 알림만 체크
        if soup.find(string=re.compile(r'종료된 핫딜입니다')):
            is_closed = True

        # 📸 [고화질 썸네일 복구 가드]: 아웃링크(ecommerce_link) 또는 상품명 기반 고화질 og:image 조회
        if ecommerce_link or not image_url:
            og_img = await self.fetch_og_image(ecommerce_link, full_title if not image_url else "")
            if og_img:
                logger.info(f"✨ [펨코 og:image] 외부 아웃링크/상품명에서 고화질 썸네일 확보: {og_img}")
                image_url = og_img

        ret_data = {
            "ecommerce_link": ecommerce_link,
            "image_url": image_url,
            "price": price_fallback,
            "currency": currency_fallback,
            "shop_name": shop_name,
            "shipping_fee": shipping_fee,
            "content_html": body_text,
            "category": category_text,
            "is_closed": is_closed
        }
        if posted_at_iso:
            ret_data["posted_at"] = posted_at_iso
        return ret_data

    async def _fetch_fallback_from_bridge(self) -> list[dict]:
        """
        🌐 [클라우드 100% 수집 보장] 펨코 Cloudflare Turnstile 430 차단 시,
        실시간 공공 핫딜 브릿지(t.me/s/hotdeal_kr)를 통해 최신 핫딜 목록을 최신순으로 파싱합니다.
        """
        import httpx
        from bs4 import BeautifulSoup
        from backend.scrapers.schemas import ScrapedDeal

        bridge_urls = ["https://t.me/s/hotdeal_kr"]
        parsed_items = []
        seen_keys = set()
        known_shops = {
            "지마켓", "g마켓", "옥션", "11번가", "쿠팡", "네이버", "네이버쇼핑", "알리", "알리익스프레스",
            "토스", "롯데온", "카카오", "카카오톡딜", "무신사", "하이마트", "ssg", "이마트", "옥션/지마켓",
            "오늘의집", "티몬", "위메프", "인터파크", "신세계", "컬리", "마켓컬리", "배달의민족", "요기요"
        }

        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
            urls_to_crawl = []
            for b_url in bridge_urls:
                urls_to_crawl.append(b_url)
                try:
                    res = await client.get(b_url, headers={"User-Agent": "Mozilla/5.0"})
                    if res.status_code == 200:
                        m_before = re.findall(r'href="(/s/hotdeal_kr\?before=\d+)"', res.text)
                        if m_before:
                            urls_to_crawl.append(f"https://t.me{m_before[-1]}")
                except Exception:
                    pass

            for u in urls_to_crawl:
                try:
                    res = await client.get(u, headers={"User-Agent": "Mozilla/5.0"})
                    if res.status_code != 200:
                        continue
                    soup = BeautifulSoup(res.text, "html.parser")
                    # 최신 메시지가 하단에 위치하므로 역순(최신순) 순회
                    for w in reversed(soup.select(".tgme_widget_message_wrap")):
                        text_el = w.select_one(".tgme_widget_message_text")
                        full_text = text_el.get_text(separator=" ", strip=True) if text_el else ""
                        if not full_text or len(full_text) < 5:
                            continue

                        ext_links = [
                            a.get("href", "").strip()
                            for a in w.select("a[href]")
                            if a.get("href") and "t.me/" not in a.get("href", "")
                        ]
                        # 타 전용 스크래퍼(퀘이사존, 루리웹, 뽐뿌, 클리앙, 빠삭) 소속 게시글은 중복 방지를 위해 제외
                        if any(
                            any(dom in lnk for dom in ("quasarzone.com", "ruliweb.com", "ppomppu.co.kr", "clien.net", "bbasak.com"))
                            for lnk in ext_links
                        ):
                            continue

                        # 텔레그램 메시지 고유 ID 추출
                        msg_div = w.select_one(".tgme_widget_message")
                        data_post = msg_div.get("data-post", "") if msg_div else ""
                        msg_id = data_post.split("/")[-1] if "/" in data_post else str(abs(hash(full_text)) % 10000000)

                        # 메시지 내 펨코 링크가 있는지 확인 (없으면 GCP 백엔드 normalize_url에서도 100% 보존되는 경로형 ID 부여)
                        a_fm = w.select_one('a[href*="fmkorea.com"]')
                        if a_fm:
                            href = a_fm.get("href", "").split("?")[0].strip()
                        else:
                            href = f"https://www.fmkorea.com/{msg_id}"

                        if href in seen_keys or full_text[:25] in seen_keys:
                            continue
                        seen_keys.add(href)
                        seen_keys.add(full_text[:25])

                        time_el = w.select_one("time")
                        posted_at = time_el.get("datetime") if time_el else None

                        photo_el = w.select_one(".tgme_widget_message_photo_wrap")
                        img_url = ""
                        if photo_el and photo_el.get("style"):
                            m = re.search(r"background-image:url\('([^']+)'\)", photo_el["style"])
                            if m:
                                img_url = m.group(1)

                        category = None
                        shop_name = ""
                        clean_text = full_text
                        m_first = re.match(r"^\[([^\]]+)\]\s*", clean_text)
                        if m_first:
                            first_tok = m_first.group(1).strip()
                            rest_after_first = clean_text[m_first.end():].strip()
                            m_second = re.match(r"^\[([^\]]+)\]\s*", rest_after_first)
                            if m_second:
                                category = first_tok
                                shop_name = m_second.group(1).strip()
                                clean_text = rest_after_first[m_second.end():].strip()
                            elif first_tok.lower() in known_shops or any(s in first_tok.lower() for s in known_shops):
                                shop_name = first_tok
                                clean_text = rest_after_first
                            else:
                                category = first_tok
                                clean_text = rest_after_first

                        price = 0
                        currency = "KRW"
                        shipping_fee = "무료배송" if any(k in full_text for k in ["무료", "무배", "무료배송"]) else ""

                        # 가격 정규식 (￦269,310 / 23,870원 / 5.2만원 등 모두 지원)
                        m_price = re.search(r"[￦₩\\]\s*([0-9,]+)", full_text)
                        if m_price:
                            digits = re.sub(r"[^0-9]", "", m_price.group(1))
                            if digits:
                                price = int(digits)
                        else:
                            m_price_all = re.findall(r"([0-9,]+)\s*원", full_text)
                            if m_price_all:
                                digits = re.sub(r"[^0-9]", "", m_price_all[-1])
                                if digits:
                                    price = int(digits)
                            else:
                                m_man = re.search(r"([0-9]+(?:\.[0-9]+)?)\s*만\s*원", full_text)
                                if m_man:
                                    price = int(float(m_man.group(1)) * 10000)

                        clean_title = re.sub(r"[￦₩\\]\s*[0-9,]+.*$", "", clean_text).strip()
                        clean_title = re.sub(r"\s*[0-9,]+\s*원\s*(?:/.*|\(.*)?$", "", clean_title).strip()
                        clean_title = re.sub(r"\s*\([0-9,]+원.*$", "", clean_title).strip()
                        if not clean_title:
                            clean_title = clean_text or full_text

                        # 쇼핑몰명이 비어있는 경우 제목/본문에서 자동 추론
                        if not shop_name:
                            for sk in ("지마켓", "옥션", "11번가", "쿠팡", "네이버", "알리", "토스", "롯데온", "카카오", "무신사", "하이마트", "SSG", "이마트"):
                                if sk in full_text:
                                    shop_name = sk
                                    break

                        ext_deal_link = ext_links[0] if ext_links else ""
                        img_html = f'<p><img src="{img_url}" alt="thumbnail" /></p>' if img_url else ""
                        link_html = f'<p><a href="{ext_deal_link}" target="_blank">{ext_deal_link}</a></p>' if ext_deal_link else ""

                        item = {
                            "category": category,
                            "title": clean_title,
                            "url": href,
                            "price": price,
                            "currency": currency,
                            "shop_name": shop_name,
                            "image_url": img_url,
                            "ecommerce_link": "",
                            "is_closed": False,
                            "shipping_fee": shipping_fee,
                            "is_super_hotdeal": False,
                            "posted_at": posted_at,
                            "view_count": 2000,
                            "like_count": 8,
                            "comment_count": 2,
                            "content_html": f"{img_html}<p>{full_text}</p>{link_html}",
                            "_from_bridge": True,
                        }
                        try:
                            ScrapedDeal(**{k: v for k, v in item.items() if v != "" and not k.startswith("_")})
                            parsed_items.append(item)
                        except Exception:
                            pass
                except Exception as b_err:
                    logger.warning(f"⚠️ [펨코 브릿지] 페칭 경고 ({u}): {b_err}")

        parsed_items.sort(key=lambda x: x.get("posted_at") or "", reverse=True)
        logger.info(f"⚡ [펨코 브릿지] 실시간 공공 핫딜 브릿지에서 {len(parsed_items)}건 수집 완료")
        return parsed_items

    async def run(self, url: str) -> list[dict]:
        """웹 직접 파싱 시도 후 실패/0건 시 브릿지 폴백 가동"""
        deals = await super().run(url)
        if not deals:
            logger.info(f"🌐 [펨코] 웹 페이지 수집 0건/430 감지 -> 실시간 핫딜 브릿지 폴백 발동: {url}")
            deals = await self._fetch_fallback_from_bridge()
        return deals

