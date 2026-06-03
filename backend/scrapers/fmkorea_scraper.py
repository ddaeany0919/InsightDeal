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

    async def fetch_html(self, url: str) -> str:
        """펨코리아 전용: Cloudflare WAF 430 차단 우회를 위해 selenium-stealth 헤드리스 크롬 수집기 가동"""
        logger.info(f"[{self.platform_name}] Selenium-Stealth 헤드리스 크롬 수집 가동 - {url}")
        import asyncio
        import time
        from selenium import webdriver
        from selenium.webdriver.chrome.options import Options
        from selenium_stealth import stealth
        
        def _sync_fetch():
            chrome_options = Options()
            chrome_options.add_argument("--headless=new")
            chrome_options.add_argument("--no-sandbox")
            chrome_options.add_argument("--disable-dev-shm-usage")
            chrome_options.add_argument("--disable-gpu")
            
            driver = webdriver.Chrome(options=chrome_options)
            try:
                stealth(driver,
                    languages=["ko-KR", "ko"],
                    vendor="Google Inc.",
                    platform="Win32",
                    webgl_vendor="Intel Inc.",
                    renderer="Intel Iris OpenGL Engine",
                    fix_hairline=True,
                )
                driver.get(url)
                
                # Cloudflare WAF 챌린지 통과를 위한 동적 폴링 대기 루프 (최대 12초)
                start_time = time.time()
                html = ""
                while time.time() - start_time < 12.0:
                    html = driver.page_source
                    # XE 게시판 테이블(bd_lst) 또는 모바일 핫딜 아이템(hotdeal_info), 혹은 글자수 3만자 이상 로드 시 성공 판정
                    if len(html) > 30000 or "bd_lst" in html or "hotdeal_info" in html:
                        logger.info(f"[{self.platform_name}] Cloudflare 챌린지 우회 통과 성공 (대기: {time.time() - start_time:.2f}초)")
                        break
                    time.sleep(0.5)
                return html
            finally:
                driver.quit()
                
        loop = asyncio.get_event_loop()
        try:
            return await loop.run_in_executor(None, _sync_fetch)
        except Exception as e:
            logger.error(f"[{self.platform_name}] Selenium-Stealth 수집 중 예외 발생: {e}")
            return ""

    async def parse_list(self, html: str) -> list[dict]:
        """펨코리아 게시판 리스트에서 타겟 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        
        # 포텐 핫딜 마크를 달기 위해 백그라운드에서 인기글(pop) 목록 조회하여 URL 수집
        if getattr(self, "hot_urls", None) is None:
            self.hot_urls = set()
            try:
                hot_html = await self.fetch_html("https://www.fmkorea.com/index.php?mid=hotdeal&sort_index=pop&order_type=desc&listStyle=webzine")
                if hot_html:
                    hot_soup = BeautifulSoup(hot_html, 'html.parser')
                    for r in hot_soup.select('li.li, div.list_item'):
                        # 인기글 중에서도 '포텐' 뱃지가 달린 것만 진짜 포텐 핫딜로 간주
                        is_poten = False
                        poten_span = r.select_one('span.STAR-BEST')
                        if poten_span and '포텐' in poten_span.get_text(strip=True):
                            is_poten = True
                            
                        if is_poten:
                            a = r.select_one('a.hotdeal_var8, a.hotdeal_var8Y, a.title, h3.title a')
                            if a and a.get('href'):
                                doc_match = re.search(r'document_srl=([0-9]+)', a.get('href'))
                                if doc_match:
                                    self.hot_urls.add(f"https://www.fmkorea.com/{doc_match.group(1)}")
                                else:
                                    clean_href = a.get('href').split('?')[0]
                                    self.hot_urls.add(urljoin("https://www.fmkorea.com/", clean_href))
                            else:
                                clean_href = a.get('href').split('?')[0]
                                self.hot_urls.add(urljoin("https://www.fmkorea.com/", clean_href))
            except Exception as e:
                logger.warning(f"[{self.platform_name}] 인기 핫딜 목록 조회 실패: {e}")

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
                            if '$' in price_text or '달러' in price_text or 'USD' in price_text.upper():
                                extracted_currency = "USD"
                            elif '€' in price_text or '유로' in price_text or 'EUR' in price_text.upper():
                                extracted_currency = "EUR"
                                
                            p_match = re.search(r'([0-9,]+(?:\.[0-9]+)?)', price_text)
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

    async def get_detail(self, url: str) -> dict:
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
        
        is_closed = False
        ecommerce_link = ""
        # 1. 펨코 리디렉터 링크 또는 hotdeal_info 내의 구매 링크를 최우선 수색!
        for a in soup.select('.hotdeal_info a, a'):
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
            content_area = soup.select_one('.xe_content') or soup.select_one('.rd_body')
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
        content_area = soup.select_one('.xe_content') or soup.select_one('.rd_body')
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
                                if '$' in td_text or '달러' in td_text or 'USD' in td_text.upper():
                                    currency_fallback = "USD"
                                elif '€' in td_text or '유로' in td_text or 'EUR' in td_text.upper():
                                    currency_fallback = "EUR"
                                
                                price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]+)?)\s*(?:원|달러|\$|€|유로)?', td_text)
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
                            if '$' in span_text or '달러' in span_text or 'USD' in span_text.upper():
                                currency_fallback = "USD"
                            elif '€' in span_text or '유로' in span_text or 'EUR' in span_text.upper():
                                currency_fallback = "EUR"
                                
                            price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]+)?)\s*(?:원|달러|\$|€|유로)?', span_text)
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
            body_parts.append(area.get_text(separator=' ', strip=True))
            
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

        # 📸 [고화질 썸네일 복구 가드]: 아웃링크(ecommerce_link)가 있으면 항상 고화질 og:image 조회를 시도하고 확보 시 우선 덮어쓰기
        if ecommerce_link:
            og_img = await self.fetch_og_image(ecommerce_link)
            if og_img:
                logger.info(f"✨ [펨코 og:image] 외부 아웃링크에서 고화질 썸네일 확보: {og_img}")
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
