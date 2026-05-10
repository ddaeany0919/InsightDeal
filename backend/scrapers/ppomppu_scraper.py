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
        
        # 핫딜 마크를 정확히 달기 위해 1페이지의 hotlist_flag=999 를 조회하여 게시글 ID 수집
        if getattr(self, "hot_ids", None) is None:
            self.hot_ids = set()
            try:
                import urllib.parse
                parsed = urllib.parse.urlparse(self.list_url)
                qs = urllib.parse.parse_qs(parsed.query)
                board_id = qs.get("id", ["ppomppu"])[0]
                
                hot_html = await self.fetch_html(f"https://www.ppomppu.co.kr/zboard/zboard.php?id={board_id}&hotlist_flag=999")
                if hot_html:
                    hot_soup = BeautifulSoup(hot_html, 'html.parser')
                    for a in hot_soup.select('tr.baseList a.baseList-title, tr.list1 a, tr.list0 a'):
                        if a.get('href'):
                            match = re.search(r'no=([0-9]+)', a.get('href'))
                            if match:
                                self.hot_ids.add(match.group(1))
            except Exception as e:
                logger.warning(f"[{self.platform_name}] 핫딜 목록 조회 실패: {e}")
                
        import asyncio
        async def process_row(row):
            title_el = row.select_one('a.baseList-title') or row.select_one('.list_title') or row.select_one('font')
            if not title_el: return None
                
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
            qs.pop('page', None)
            qs.pop('divpage', None)
            clean_query = urlencode(qs, doseq=True)
            clean_href = urlunparse(parsed_href._replace(query=clean_query))
                
            url = urljoin("https://www.ppomppu.co.kr/zboard/", clean_href)
            
            # 🖼️ 썸네일 추출 시도 
            image_url = ""
            img_td = row.select_one('td img.thumb_border')
            if not img_td: img_td = row.select_one('img')
            
            if img_td and img_td.has_attr('src'):
                 image_url = img_td['src']
                 if image_url.startswith('//'): image_url = "https:" + image_url
                 elif not image_url.startswith('http'): image_url = urljoin("https://www.ppomppu.co.kr", image_url)

            # 제목에서 가격 추출 시도 (예: 15,900원, 3만9천원)
            import re
            extracted_price = 0
            price_match = re.search(r'([\d,]+(?:원|만원))', full_title)
            if price_match:
                price_str = price_match.group(1).replace(',', '')
                if '만원' in price_str:
                    try: extracted_price = int(float(price_str.replace('만원', '')) * 10000)
                    except: pass
                else:
                    nums = re.findall(r'\d+', price_str)
                    if nums: extracted_price = int(''.join(nums))

            detail_info = await self.get_detail(url)
            
            if extracted_price == 0 and detail_info.get("price", 0) > 0:
                extracted_price = detail_info.get("price")

            is_closed = detail_info.get("is_closed", False)
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
                if getattr(self, "hot_ids", None) is not None:
                    is_super_hotdeal = doc_id in self.hot_ids
            
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
                if ':' in txt or '/' in txt or '-' in txt:
                    # 추천수(11 - 0) 필터링
                    if re.search(r'\d+\s*-\s*\d+', txt) and not re.search(r'\d{4}-\d{2}-\d{2}', txt):
                        continue
                    time_str = txt
                    break

            if time_str:
                posted_at_iso = self.parse_time_str(time_str)

            detail_posted_at = detail_info.get("posted_at")
            if detail_posted_at:
                posted_at_iso = detail_posted_at


            return {
                "title": full_title,
                "url": url,
                "price": extracted_price,
                "shop_name": "",
                "image_url": image_url,
                "ecommerce_link": detail_info.get("ecommerce_link", ""),
                "content_html": detail_info.get("content_html", ""),
                "is_closed": is_closed,
                "shipping_fee": detail_info.get("shipping_fee", ""),
                "is_super_hotdeal": is_super_hotdeal,
                "posted_at": posted_at_iso,
                "view_count": view_count,
                "like_count": like_count,
                "comment_count": comment_count
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
        link_tag = soup.select_one('.word')
        if link_tag and link_tag.get('href') and 'http' in link_tag['href']:
            ecommerce_link = link_tag['href']
        else:
            for a in soup.find_all('a'):
                href = a.get('href', '')
                if 's.ppomppu.co.kr' in href:
                    ecommerce_link = href
                    break
                    
        if "s.ppomppu.co.kr" in ecommerce_link:
            import urllib.parse
            import base64
            try:
                parsed_url = urllib.parse.urlparse(ecommerce_link)
                if 'target' in parsed_url.query:
                    query_params = urllib.parse.parse_qs(parsed_url.query)
                    encoded_target = query_params.get("target", [None])[0]
                    if encoded_target:
                        encoded_target += '=' * (-len(encoded_target) % 4)
                        ecommerce_link = base64.b64decode(encoded_target).decode('utf-8')
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
                    try:
                        parsed_url = urllib.parse.urlparse(href)
                        if 'target' in parsed_url.query:
                            query_params = urllib.parse.parse_qs(parsed_url.query)
                            encoded_target = query_params.get("target", [None])[0]
                            if encoded_target:
                                encoded_target += '=' * (-len(encoded_target) % 4)
                                decoded = base64.b64decode(encoded_target).decode('utf-8')
                                if decoded: href = decoded
                    except: pass
                # a 태그 내용을 "[텍스트](링크)" 형태로 변경
                new_text = f"{a.get_text()} (링크: {href})"
                a.string = new_text
            body_text = content_element.get_text(separator=' \n ', strip=True)
        else:
            body_text = soup.get_text(separator=' ')
        
        # 이미지 태그 추출해서 본문에 덧붙이기 (Gemini 매칭용)
        images = []
        for img in soup.select('.board-contents img, table.pic_bg img, .han img, .cont img'):
            src = img.get('src')
            if src and src.startswith('//'): src = 'https:' + src
            if src and 'http' in src and 'icon' not in src:
                images.append(src)
        if images:
            body_text += f"\n[첨부된 이미지 링크들]: {', '.join(images)}"
            
        # 예: 15,000원, 23,500 원, 49900원 등
        price_matches = re.findall(r'([0-9]{1,3}(?:[,\.][0-9]{3})*|[0-9]+)\s*원', body_text)
        if price_matches:
            try:
                # 본문에서 찾은 가격 중 가장 합리적인 첫 번째 금액
                price_fallback = int(price_matches[0].replace(',', '').replace('.', ''))
            except:
                pass
                
        shipping_fee = ""
        # 1. 명시적인 테이블 정보(배송비/택배비) 확인
        for tr in soup.select('table tr'):
            th = tr.select_one('th, td.han')
            td = tr.select_one('td:not(.han)')
            if th and td:
                th_text = th.get_text(strip=True)
                if '배송비' in th_text or '택배비' in th_text:
                    fee_text = td.get_text(strip=True)
                    if fee_text:
                        shipping_fee = fee_text
                        break
        
        # 2. 본문 휴리스틱
        if not shipping_fee:
            if "무료배송" in body_text or "무배" in body_text:
                shipping_fee = "무료배송"
        
        is_closed = False
        if "종결" in body_text and "취소된 게시물" in body_text:
            is_closed = True
            
        posted_at_iso = None
        html_text = soup.get_text(separator=' ', strip=True)
        dates = re.findall(r'등록일\s*[:\s]*(\d{4}-\d{2}-\d{2}\s*\d{2}:\d{2}(?::\d{2})?)', html_text)
        if dates:
            posted_at_iso = self.parse_time_str(dates[0])
            
        # [CEO 피드백: 모음전 분할을 위해 텍스트 길이 충분한 본문을 content_html로 반환]
        return {
            "ecommerce_link": ecommerce_link, 
            "price": price_fallback, 
            "shipping_fee": shipping_fee, 
            "content_html": body_text, 
            "is_closed": is_closed,
            "posted_at": posted_at_iso
        }
