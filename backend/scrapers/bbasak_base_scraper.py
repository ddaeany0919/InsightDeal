import logging
import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class BbasakBaseScraper(AsyncBaseScraper):
    def __init__(self, community_name: str, community_url: str, community_id: int = 0, default_category: str = None):
        super().__init__(community_name, max_concurrent_requests=5)
        self.community_id = community_id
        self.list_url = community_url
        self.default_category = default_category

    async def parse_list(self, html: str) -> list[dict]:
        """빠삭 게시판 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        
        tables = soup.select('table.t1')
        post_rows = []
        if len(tables) >= 2:
            post_rows = tables[1].select('tbody tr')
        elif len(tables) == 1:
             post_rows = tables[0].select('tbody tr')

        deals = []
        import asyncio
        
        async def process_row(row):
            title_element = row.select_one('td.tit a')
            if not title_element: return None

            href = title_element.get('href')
            if not href: return None

            url = urljoin("https://bbasak.com", href).replace("bbasak.com:443", "bbasak.com")
            from urllib.parse import urlparse, parse_qs, urlencode, urlunparse
            parsed_u = urlparse(url)
            qs = parse_qs(parsed_u.query)
            qs.pop("page", None)
            qs["device"] = ["pc"]
            url = urlunparse(parsed_u._replace(netloc="bbasak.com", query=urlencode(qs, doseq=True)))
                
            full_title = title_element.get_text(strip=True)
            
            price = 0
            extracted_currency = "KRW"
            import re
            usd_match = re.search(r'(?:\$|USD|달러|유로|€)\s*([0-9,.]+)', full_title, re.IGNORECASE)
            if not usd_match:
                usd_match = re.search(r'([0-9,.]+)\s*(?:\$|USD|달러|유로|€)', full_title, re.IGNORECASE)
                
            if usd_match:
                try:
                    val = float(usd_match.group(1).replace(',', ''))
                    price = int(val * 100)
                    if '유로' in full_title or '€' in full_title:
                        extracted_currency = "EUR"
                    else:
                        extracted_currency = "USD"
                except: pass
            else:
                match = re.search(r'([\d,]+)\s*원', full_title)
                if match:
                    try:
                        price = int(match.group(1).replace(',', ''))
                    except: pass
                
                if price == 0:
                    plan_match = re.search(r'(\d{2,3})\s*요금제', full_title)
                    if plan_match:
                        try:
                            price = int(plan_match.group(1)) * 1000
                        except: pass
                
            is_closed = False
            if '종료' in full_title or '마감' in full_title or '품절' in full_title:
                is_closed = True
            style = title_element.get('style', '')
            if 'line-through' in style or title_element.select_one('del, s, strike'):
                is_closed = True
                
            is_super_hotdeal = False
            view_count = 0
            like_count = 0
            tds = row.select('td')
            if len(tds) > 4:
                hit_txt = tds[-1].get_text(strip=True).replace(',', '')
                view_count = int(hit_txt) if hit_txt.isdigit() else 0
                if len(tds) >= 7:
                    rec_txt = tds[-2].get_text(strip=True).replace(',', '')
                    if rec_txt.isdigit():
                        like_count = int(rec_txt)
                view_threshold = 1500 if "국내" in getattr(self, "platform_name", "") else 2500
                if view_count >= view_threshold or like_count >= 3:
                    is_super_hotdeal = True
                    
            # 댓글수 추출 (보통 제목 옆에 [3] 또는 span 태그)
            comment_count = 0
            comment_span = title_element.find_next_sibling('span')
            if comment_span:
                cmt_txt = comment_span.get_text(strip=True).replace('[', '').replace(']', '')
                if cmt_txt.isdigit(): comment_count = int(cmt_txt)
            else:
                cmt_match = re.search(r'\[(\d+)\]$', full_title)
                if cmt_match:
                    comment_count = int(cmt_match.group(1))
                    full_title = re.sub(r'\s*\[\d+\]$', '', full_title).strip()
                
            # 휴리스틱: 제목에 직구 관련 키워드가 있고 가격이 10000 이하면 USD로 간주
            if extracted_currency == "KRW" and price > 0 and price <= 10000:
                if any(kw in full_title for kw in ['알리', '코인', '큐텐', '직구', '알익']):
                    extracted_currency = "USD"
                    price = int(price * 100)
                
            # 실제 게시글 작성 시간 추출
            posted_at_iso = None
            time_str = ""
            for td in tds:
                txt = td.get_text(strip=True)
                if ':' in txt or '/' in txt or '-' in txt:
                    if re.match(r'^\d{1,2}:\d{1,2}(:\d{1,2})?$', txt) or re.match(r'^\d{2,4}[/.-]\d{1,2}[/.-]\d{1,2}', txt):
                        time_str = txt
                        break

            if time_str:
                posted_at_iso = self.parse_time_str(time_str)
                
            # 빠삭 제목 앞 대괄호([쿠팡], [슈퍼적립], [브랜드쿠폰] 등)는 쇼핑몰/프로모션 태그이므로 default_category 사용
            category_extracted = self.default_category

            return {
                "title": full_title,
                "url": url,
                "price": price,
                "currency": extracted_currency,
                "shop_name": "",
                "image_url": "",
                "ecommerce_link": "",
                "is_closed": is_closed,
                "shipping_fee": "",
                "is_super_hotdeal": is_super_hotdeal,
                "posted_at": posted_at_iso,
                "view_count": view_count,
                "like_count": like_count,
                "comment_count": comment_count,
                "category": category_extracted
            }

        tasks = [process_row(row) for row in post_rows]
        results = await asyncio.gather(*tasks)
        for r in results:
            if r: deals.append(r)
            
        return deals

    async def get_detail(self, url: str, full_title: str = "") -> dict:
        """상세 페이지 데이터 파싱 로직 (추후 고도화)"""
        html = await self.fetch_html(url)
        if not html: return {}
        if any(k in html for k in ("글이 존재하지 않습니다", "삭제되었거나 이동된", "존재하지 않는 게시물")):
            return {"is_closed": True}
        soup = BeautifulSoup(html, 'html.parser')
        
        import re
        # 이미지 필터 정의 (기존 필터와 동일)
        def is_valid_image(img_url):
            if not img_url:
                return False
            img_url = urljoin("https://bbasak.com", img_url)
            return not re.search(r'icon|emoticon|expand|beautifulLine|util/|skin/|share/|btn_|footer|logo|layout|bg\.|mypage|member|board|222E38587E38581', img_url, re.IGNORECASE)

        # 1. 상품정보 이미지 (div.alignC) 내에서 유효한 이미지 우선 추출
        product_images = []
        for img in soup.select('div.alignC img'):
            src = img.get('src')
            if src and is_valid_image(src):
                product_images.append(urljoin("https://bbasak.com", src))

        if product_images:
            # 상품정보에 이미지가 있으면 해당 이미지 사용! (여러 개면 첫 번째)
            image_url = product_images[0]
        else:
            # 2. 상품정보에 이미지가 없으면 내용 탭 및 전체 본문 이미지 활용 (기존 Fallback 로직)
            valid_images = [urljoin("https://bbasak.com", img.get('src') or '') for img in soup.select('#board_view img') if img.get('src')]
            valid_images = [img for img in valid_images if is_valid_image(img)]
            
            def image_score(url):
                url_lower = url.lower()
                if 'kakao' in url_lower or 'screenshot' in url_lower or 'capture' in url_lower:
                    return 10  # 아주 낮음
                if 'ckeditor' in url_lower:
                    return 1   # 본문 삽입 이미지 (보통 상품 이미지 확률이 가장 높음)
                return 5       # 일반 첨부파일
                
            valid_images.sort(key=image_score)
            image_url = valid_images[0] if valid_images else ""
        
        # 외부 쇼핑몰 링크 추출 (본문 영역 내에서만 추출)
        ecommerce_link = ""
        for a in soup.select('#board_view a'):
            href = a.get('href')
            if href:
                href = href.replace('\xa0', '').replace('\u00A0', '').strip()
                if href.startswith('http') and 'sns' not in href:
                    if 'bbasak.com' not in href:
                        ecommerce_link = href
                        break
                    elif 'link.php' in href:
                        # link.php는 모바일 브라우저에서 막히므로, 파이썬 백엔드에서 미리 최종 리다이렉트 URL을 추적합니다.
                        try:
                            import httpx
                            client = httpx.AsyncClient(verify=False, follow_redirects=True, timeout=5.0)
                            headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0'}
                            res = await client.head(href, headers=headers)
                            if str(res.url) != href and "bbasak.com" not in str(res.url):
                                ecommerce_link = str(res.url)
                                await client.aclose()
                                break
                            else:
                                res = await client.get(href, headers=headers)
                                if str(res.url) != href and "bbasak.com" not in str(res.url):
                                    ecommerce_link = str(res.url)
                                    await client.aclose()
                                    break
                            await client.aclose()
                        except Exception as e:
                            logger.error(f"[빠삭] 리다이렉트 실패: {e}")
                            
        # a 태그에서 추출 실패했거나 link.php 우회 실패 시 본문 텍스트 정규식으로 직접 추출
        if not ecommerce_link:
            import re
            board_view = soup.select_one('#board_view')
            body_text_raw = board_view.get_text(separator=' ') if board_view else ""
            urls = re.findall(r'https?://[^\s"\'<>]+', body_text_raw)
            for u in urls:
                if 'bbasak.com' not in u and 'sns' not in u:
                    ecommerce_link = u
                    break
        price_fallback = 0
        extracted_currency = "KRW"
        shipping_fee = ""
        view_content_el = soup.select_one('#view_content') or soup.select_one('#board_view')
        body_text = view_content_el.get_text(separator=' ') if view_content_el else soup.get_text(separator=' ')

        # 0. 빠삭 상품정보 영역(.detailTxt ul li)의 명시적 가격/배송비/링크 추출
        for li in soup.select('#view_content .detailTxt li, .detailTxt li'):
            tit_span = li.select_one('span.titTxt')
            if not tit_span:
                continue
            label = tit_span.get_text(strip=True)
            full_li_text = li.get_text(strip=True)
            val_text = full_li_text[len(label):].strip() if full_li_text.startswith(label) else full_li_text.replace(label, '', 1).strip()
            if '가격' in label and val_text:
                usd_m = re.search(r'(?:\$|USD|달러|유로|€)\s*([0-9,.]+)|([0-9,.]+)\s*(?:\$|USD|달러|유로|€)', val_text, re.IGNORECASE)
                if usd_m:
                    try:
                        raw_v = usd_m.group(1) or usd_m.group(2)
                        price_fallback = int(float(raw_v.replace(',', '')) * 100)
                        extracted_currency = "EUR" if ('유로' in val_text or '€' in val_text) else "USD"
                    except Exception:
                        pass
                else:
                    num_m = re.search(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,8})', val_text)
                    if num_m:
                        try:
                            cand = int(num_m.group(1).replace(',', ''))
                            if 100 <= cand <= 15000000:
                                price_fallback = cand
                        except Exception:
                            pass
            elif ('배송' in label or '택배' in label) and val_text:
                if val_text in ('0', '0원') or '무료' in val_text or '무배' in val_text:
                    shipping_fee = "무료배송"
                else:
                    shipping_fee = val_text
            elif '링크' in label and not ecommerce_link:
                a_tag = li.select_one('a[href]')
                if a_tag and a_tag['href'].startswith('http') and 'bbasak.com' not in a_tag['href']:
                    ecommerce_link = a_tag['href'].strip()

        # 가격 휴리스틱 추출 (상단 .detailTxt에 가격이 없을 때만)
        cleaned_body_text = re.sub(r'https?://[^\s]+', '', body_text)

        if price_fallback == 0:
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
                except Exception:
                    pass
            elif price_matches:
                for pm in price_matches:
                    try:
                        cand = int(pm.replace(',', '').replace('.', ''))
                        if cand >= 100:
                            price_fallback = cand
                            break
                    except Exception:
                        pass

        # 1. 명시적인 테이블 정보(배송비/택배비) 확인
        if not shipping_fee:
            for tr in soup.select('table tr'):
                th = tr.select_one('th')
                td = tr.select_one('td')
                if th and td:
                    th_text = th.get_text(strip=True)
                    if '배송비' in th_text or '택배비' in th_text:
                        fee_text = td.get_text(strip=True)
                        if fee_text:
                            shipping_fee = "무료배송" if fee_text in ('0', '0원') else fee_text
                            break

        # 2. 본문 휴리스틱
        if not shipping_fee:
            if re.search(r'(무료배송|무배|택배비\s*무료|배송비\s*무료|\(\s*무료\s*\)|/\s*무료|무료\s*/|무료\s*$)', body_text):
                shipping_fee = "무료배송"

        # 📸 [고화질 썸네일 복구 가드]: 본문에 이미지가 없거나 cache 관련 썸네일인 경우, 아웃링크 og:image 및 상품명 폴백 추출
        if not image_url or "cache" in image_url:
            og_img = await self.fetch_og_image(ecommerce_link, full_title)
            if og_img:
                logger.info(f"✨ [빠삭 og:image] 외부 아웃링크/상품명에서 고화질 썸네일 확보: {og_img}")
                image_url = og_img

        content_html = ""
        board_view_el = soup.select_one('#view_content') or soup.select_one('#board_view') or soup.select_one('div.board_view')
        if board_view_el:
            for tag in board_view_el.select('script, style, iframe, button, .Singoinfo, #s_layer'):
                tag.decompose()
            raw_lines = [line.strip() for line in board_view_el.get_text(separator='\n').splitlines() if line.strip()]
            filtered_lines = [
                ln for ln in raw_lines
                if not any(noise in ln for noise in ('게시글 신고 기능 안내', '신고버튼을 누르면', '신고사유를 선택후', '빠삭 운영진에게 전달'))
            ]
            content_html = '\n'.join(filtered_lines)

        return {
            "image_url": image_url,
            "ecommerce_link": ecommerce_link,
            "price": price_fallback,
            "currency": extracted_currency,
            "shipping_fee": shipping_fee,
            "content_html": content_html,
        }

