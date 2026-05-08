import logging
import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class FmkoreaScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        # 펨코는 Cloudflare 방어가 강하므로 딜레이를 늘리고 동시성을 낮춥니다.
        super().__init__("펨코", max_concurrent_requests=2)
        self.community_id = community_id
        self.list_url = "https://www.fmkorea.com/hotdeal?listStyle=webzine"

    def _get_headers(self) -> dict:
        """펨코리아 전용 (Cloudflare 430 차단 우회를 위한 특수 헤더 추가)"""
        headers = super()._get_headers()
        headers.update({
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Sec-Ch-Ua": "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"",
            "Sec-Ch-Ua-Mobile": "?0",
            "Sec-Ch-Ua-Platform": "\"Windows\"",
            "Sec-Fetch-Dest": "document",
            "Sec-Fetch-Mode": "navigate",
            "Sec-Fetch-Site": "none",
            "Sec-Fetch-User": "?1",
            "Upgrade-Insecure-Requests": "1"
        })
        return headers

    async def fetch_html(self, url: str):
        """Selenium Stealth를 이용한 동적 렌더링 우회"""
        import asyncio
        
        def _selenium_fetch():
            from selenium import webdriver
            from selenium.webdriver.chrome.options import Options
            from selenium_stealth import stealth
            import time
            
            options = Options()
            options.add_argument('--headless')
            options.add_argument('--disable-gpu')
            options.add_argument('--no-sandbox')
            options.add_argument('--disable-dev-shm-usage')
            
            driver = webdriver.Chrome(options=options)
            
            stealth(driver,
                    languages=["ko-KR", "ko"],
                    vendor="Google Inc.",
                    platform="Win32",
                    webgl_vendor="Intel Inc.",
                    renderer="Intel Iris OpenGL Engine",
                    fix_hairline=True,
                    )
            try:
                driver.get(url)
                time.sleep(3) # CF 통과 및 렌더링 대기
                html = driver.page_source
                if '에펨코리아 보안 시스템' in html:
                    logger.warning(f"[펨코] Selenium Stealth로도 차단됨: {url}")
                    return ""
                return html
            except Exception as e:
                logger.error(f"[펨코] Selenium 에러: {e}")
                return ""
            finally:
                driver.quit()
                
        # 동기 함수인 Selenium 스크래퍼를 비동기 루프에서 안전하게 실행 (쓰레드 분리)
        return await asyncio.to_thread(_selenium_fetch)

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

            title_element = row.select_one('td.title a, h3.title a, a.title')
            if not title_element:
                if row.name == 'a' and 'title' in (row.get('class') or []):
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

            # 🖼️ 기본 썸네일 확인 (아이콘일 가능성이 큼)
            image_url = ""
            img_tag = row.select_one('img.thumb, .thumb img, .tmb img, img')
            if img_tag and img_tag.has_attr('src'):
                image_url = img_tag['src']
                if image_url.startswith('//'):
                    image_url = "https:" + image_url
                elif not image_url.startswith('http'):
                    image_url = urljoin("https://www.fmkorea.com/", image_url)

            # 상세 페이지에서 ecommerce_link와 본문 파싱
            detail_info = await self.get_detail(url)
            
            # 펨코리아 아이콘이 잡힌 경우, 상세 페이지의 이미지로 덮어쓰기
            if 'icons/fmkorea' in image_url or 'transparent.gif' in image_url or not image_url:
                image_url = detail_info.get("image_url", image_url)
                
            # 가격 및 배송비 파싱 (hotdeal_info)
            extracted_price = 0
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
                            p_match = re.search(r'([0-9,]+)', price_text)
                            if p_match:
                                extracted_price = int(p_match.group(1).replace(',', ''))
                    elif '배송비' in text:
                        strong = span.select_one('.strong')
                        if strong: shipping_fee = strong.get_text(strip=True)
            
            if "(0원)" in full_title and extracted_price == 0:
                extracted_price = 0
                
            if extracted_price == 0 and detail_info.get("price", 0) > 0:
                extracted_price = detail_info.get("price")
            if not shop_name and detail_info.get("shop_name", ""):
                shop_name = detail_info.get("shop_name")
            if not shipping_fee and detail_info.get("shipping_fee", ""):
                shipping_fee = detail_info.get("shipping_fee")

            # 종료 여부 확인 (취소선이 그어져 있는지 또는 종료 키워드)
            is_closed = detail_info.get("is_closed", False)
            # 펨코 전용: 핫딜 종료 시 <td class="title"> 태그에 'hotdeal_var8Y' 클래스가 붙음
            title_td = row.select_one('td.title')
            if title_td:
                td_classes = title_td.get('class', [])
                if isinstance(td_classes, list) and 'hotdeal_var8Y' in td_classes:
                    is_closed = True
                elif isinstance(td_classes, str) and 'hotdeal_var8Y' in td_classes:
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

            is_super_hotdeal = False
            html_str = str(row).lower()
            pc_voted = row.select_one('.pc_voted_count')
            like_count = 0
            if pc_voted:
                 v_match = re.search(r'\d+', pc_voted.get_text())
                 if v_match:
                      like_count = int(v_match.group())
                      if like_count >= 15:
                          is_super_hotdeal = True
            if 'poten' in html_str or 'li_best2_pop0' in html_str:
                 is_super_hotdeal = True
                 
            # 조회수 파싱
            view_count = 0
            m_no = row.select_one('td.m_no, .m_no_voted, td.hit')
            if m_no:
                v_txt = m_no.get_text(strip=True).replace(',', '')
                if v_txt.isdigit(): view_count = int(v_txt)

            # 실제 게시글 작성 시간 추출 (KST 기준을 UTC로 변환하여 저장)
            posted_at_iso = None
            time_td = row.select_one('td.time, .regdate')
            if time_td:
                posted_at_iso = self.parse_time_str(time_td.get_text(strip=True))

            return {
                "title": full_title,
                "url": url,
                "price": extracted_price,
                "shop_name": shop_name,
                "image_url": image_url,
                "ecommerce_link": detail_info.get("ecommerce_link", ""),
                "is_closed": is_closed,
                "shipping_fee": shipping_fee,
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
        """펨코리아 상세 페이지 데이터 파싱 로직"""
        html = await self.fetch_html(url)
        if not html: return {}
        soup = BeautifulSoup(html, 'html.parser')
        
        ecommerce_link = ""
        # 펨코 핫딜의 아웃링크는 보통 외부 도메인을 가리키는 a 태그입니다.
        for a in soup.select('a'):
            href = a.get('href', '')
            if href.startswith('http') and 'fmkorea.com' not in href and 'saedu.naver.com' not in href and 'ader.naver.com' not in href:
                if 'link.fmkorea.org' in href:
                    import urllib.parse
                    parsed_url = urllib.parse.urlparse(href)
                    query_params = urllib.parse.parse_qs(parsed_url.query)
                    target_url = query_params.get('url', [''])[0]
                    if target_url:
                        ecommerce_link = target_url
                        break
                else:
                    ecommerce_link = href
                    break
        
        # a 태그로 못 찾았을 경우, 텍스트(본문)에 포함된 URL 정규식 탐색
        if not ecommerce_link:
            content_area = soup.select_one('.xe_content') or soup.select_one('.rd_body')
            if content_area:
                text_content = content_area.get_text(separator=' ')
                # http(s) 링크 찾기
                url_matches = re.findall(r'(https?://[^\s]+)', text_content)
                for m in url_matches:
                    if 'fmkorea.com' not in m and 'saedu.naver.com' not in m:
                        ecommerce_link = m
                        break
                # http 없이 도메인만 적힌 경우 (예: m.smartstore.naver.com/...)
                if not ecommerce_link:
                    url_matches2 = re.findall(r'(m\.smartstore\.naver\.com/[^\s]+|smartstore\.naver\.com/[^\s]+|brand\.naver\.com/[^\s]+|coupang\.com/[^\s]+)', text_content)
                    if url_matches2:
                        ecommerce_link = "https://" + url_matches2[0]
                
        image_url = ""
        # 펨코 본문 이미지는 보통 files/attach/new 경로에 업로드됩니다.
        for img in soup.select('img'):
            src = img.get('src', '')
            if 'files/attach/new' in src:
                if src.startswith('//'):
                    image_url = "https:" + src
                elif not src.startswith('http'):
                    image_url = urljoin("https://www.fmkorea.com/", src)
                break

        # 혹시 image_url도 못 찾았지만 img 태그가 있다면 (외부 링크 이미지 등)
        if not image_url:
            for img in soup.select('img'):
                src = img.get('src', '')
                if src and 'fmkorealogo' not in src and 'icons/fmkorea' not in src and 'transparent' not in src:
                    if src.startswith('//'):
                        image_url = "https:" + src
                    elif not src.startswith('http'):
                        image_url = urljoin("https://www.fmkorea.com/", src)
                    break

        price_fallback = 0
        shipping_fee = ""
        shop_name = ""
        
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
                            price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\s*원?', td_text)
                            if price_matches:
                                try:
                                    price_fallback = int(price_matches[0].replace(',', ''))
                                except: pass
                        elif '배송' in th_text:
                            shipping_fee = td_text
                        elif '쇼핑몰' in th_text:
                            shop_name = td_text.split('[')[0].strip()
                break
                
        # 2. div.hotdeal_info 방식의 핫딜 정보 파싱
        if price_fallback == 0 and not shipping_fee:
            hotdeal_info = soup.select_one('.hotdeal_info')
            if hotdeal_info:
                for span in hotdeal_info.select('span'):
                    span_text = span.get_text(strip=True)
                    if '가격:' in span_text:
                        price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\s*원?', span_text)
                        if price_matches:
                            try:
                                price_fallback = int(price_matches[0].replace(',', ''))
                            except: pass
                    elif '배송:' in span_text:
                        shipping_fee = span_text.replace('배송:', '').replace('배송 :', '').strip()
                    elif '쇼핑몰:' in span_text:
                        shop_name = span_text.replace('쇼핑몰:', '').replace('쇼핑몰 :', '').strip()
        
        body_text = ""
        # 본문 콘텐츠 영역에서 탐색 (사이드바/광고 제외)
        content_area = soup.select_one('.xe_content') or soup.select_one('.rd_body')
        if content_area:
            body_text = content_area.get_text(separator=' ', strip=True)
            
            if price_fallback == 0:
                if "(0원)" in body_text or "나눔" in body_text:
                    price_fallback = 0
                else:
                    price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})\s*원', body_text)
                    if price_matches:
                        try:
                            price_fallback = int(price_matches[0].replace(',', ''))
                        except:
                            pass
                    
                if not shipping_fee:
                    if "무료배송" in body_text or "무배" in body_text:
                        shipping_fee = "무료배송"

        return {
            "ecommerce_link": ecommerce_link,
            "image_url": image_url,
            "price": price_fallback,
            "shop_name": shop_name,
            "shipping_fee": shipping_fee,
            "content_html": body_text
        }
