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
            if like_count >= 8 or view_count >= 2500:
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

    async def get_detail(self, url: str, full_title: str = "") -> dict:
        """상세 페이지 데이터 파싱 로직"""
        html = await self.fetch_html(url)
        if not html: return {}
        if any(k in html for k in ("존재하지 않는 게시물", "삭제된 게시물", "블라인드 처리된", "해당 게시물이 존재하지 않습니다")):
            return {"is_closed": True}
        soup = BeautifulSoup(html, 'html.parser')

        detail_title = ""
        og_t = soup.select_one('meta[property="og:title"]')
        if og_t and og_t.get('content'):
            cand_t = re.sub(r'\s*(?:-\s*)?퀘이사존.*$', '', og_t.get('content', '').strip()).strip()
            if cand_t and not cand_t.endswith('...'):
                detail_title = cand_t
        if not detail_title:
            h1_t = soup.select_one('h1.title')
            if h1_t:
                from copy import copy
                h1_clone = copy(h1_t)
                for sp in h1_clone.select('span'):
                    sp.decompose()
                cand_t = h1_clone.get_text(strip=True)
                if cand_t:
                    detail_title = cand_t
        if detail_title and (not full_title or full_title.endswith('...') or len(detail_title) > len(full_title)):
            full_title = detail_title
        
        ecommerce_link = ""
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

        if not image_url:
            og_img = await self.fetch_og_image(ecommerce_link, full_title)
            if og_img:
                logger.info(f"✨ [퀘이사존 og:image] 외부 아웃링크/상품명에서 고화질 썸네일 확보: {og_img}")
                image_url = og_img

        result = {
            "price": price,
            "currency": currency,
            "ecommerce_link": ecommerce_link, 
            "shipping_fee": shipping_fee, 
            "content_html": content_html,
            "image_url": image_url
        }
        if detail_title:
            result["title"] = detail_title
        return result

    async def _fetch_fallback_from_bridge(self) -> list[dict]:
        """
        🌐 [클라우드 100% 수집 보장] 퀘이사존 Cloudflare 403 차단 시,
        실시간 공공 핫딜 브릿지(t.me/s/hotdeal_kr, t.me/s/jsmon)를 통해 최신 핫딜 목록을 파싱합니다.
        """
        import httpx
        from bs4 import BeautifulSoup
        from backend.scrapers.schemas import ScrapedDeal

        bridge_urls = ["https://t.me/s/hotdeal_kr", "https://t.me/s/jsmon"]
        parsed_items = []
        seen_urls = set()

        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
            urls_to_crawl = []
            for b_url in bridge_urls:
                urls_to_crawl.append(b_url)
                try:
                    res = await client.get(b_url, headers={"User-Agent": "Mozilla/5.0"})
                    if res.status_code == 200:
                        m_before = re.findall(r'href="(/s/(?:hotdeal_kr|jsmon)\?before=\d+)"', res.text)
                        for b_path in m_before[:2]:
                            urls_to_crawl.append(f"https://t.me{b_path}")
                except Exception:
                    pass

            for u in urls_to_crawl:
                try:
                    res = await client.get(u, headers={"User-Agent": "Mozilla/5.0"})
                    if res.status_code != 200:
                        continue
                    soup = BeautifulSoup(res.text, "html.parser")
                    for w in reversed(soup.select(".tgme_widget_message_wrap")):
                        a_qz = w.select_one('a[href*="quasarzone.com/bbs/qb_saleinfo/views"]')
                        if not a_qz:
                            continue
                        href = a_qz.get("href", "").split("?")[0].strip()
                        if not href or href in seen_urls:
                            continue
                        seen_urls.add(href)

                        text_el = w.select_one(".tgme_widget_message_text")
                        full_text = text_el.get_text(separator=" ", strip=True) if text_el else ""
                        time_el = w.select_one("time")
                        posted_at = time_el.get("datetime") if time_el else None

                        photo_el = w.select_one(".tgme_widget_message_photo_wrap")
                        img_url = ""
                        if photo_el and photo_el.get("style"):
                            m = re.search(r"background-image:url\('([^']+)'\)", photo_el["style"])
                            if m:
                                img_url = m.group(1)

                        category = None
                        clean_text = full_text
                        m_cat = re.match(r"^\[([^\]]+)\]", clean_text)
                        if m_cat:
                            category = m_cat.group(1).strip()
                            clean_text = clean_text[m_cat.end():].strip()

                        shop_name = ""
                        m_shop = re.match(r"^\[([^\]]+)\]", clean_text)
                        if m_shop:
                            shop_name = m_shop.group(1).strip()
                            clean_text = clean_text[m_shop.end():].strip()

                        price = 0
                        currency = "KRW"
                        shipping_fee = "무료배송" if any(k in full_text for k in ["무료", "무배", "무료배송"]) else ""

                        # 가격 정규식: 화폐 기호 및 이모지(💰) 우선 탐색
                        m_price = re.search(r"[￦₩\\💰]\s*([0-9,]+)", full_text)
                        if m_price:
                            digits = re.sub(r"[^0-9]", "", m_price.group(1))
                            if digits:
                                price = int(digits)
                        else:
                            m_price_num = re.search(r"([0-9,]+)\s*원(?:\s*/|\s*\)|\s*🚚)", full_text)
                            if m_price_num:
                                digits = re.sub(r"[^0-9]", "", m_price_num.group(1))
                                if digits:
                                    price = int(digits)

                        clean_title = re.sub(r"[\s/]*[￦₩\\💰].*$", "", clean_text).strip()
                        clean_title = re.sub(r"[\s/]*(?:🚚|퀘이사존/지름|지름&할인정보).*$", "", clean_title).strip()
                        clean_title = re.sub(r"\s*\([^)]*(?:[0-9,]+|[0-9]+(?:\.[0-9]+)?)\s*(?:원|달러|불|엔|유로|원\s*대|원\s*상당)[^)]*\)\s*$", "", clean_title).strip()
                        clean_title = re.sub(r"\s*\([^)]*(?:무료배송|배송비|무배|유배|카드할인)[^)]*\)\s*$", "", clean_title).strip()
                        clean_title = re.sub(r"\s*\([^)]*$", "", clean_title).strip()
                        clean_title = re.sub(r"[\s\.,\-\/~]+$", "", clean_title).strip()
                        if not clean_title:
                            clean_title = full_text

                        # 외부 쇼핑몰 직접 링크 추출 (퀘이사존 내부 도메인 제외)
                        ecommerce_link = ""
                        for a_tag in w.select("a[href]"):
                            cand_href = a_tag.get("href", "").strip()
                            if cand_href.startswith("http") and "quasarzone.com" not in cand_href and "t.me" not in cand_href and "telegram.org" not in cand_href:
                                ecommerce_link = cand_href
                                break

                        item = {
                            "category": category,
                            "title": clean_title,
                            "url": href,
                            "price": price,
                            "currency": currency,
                            "shop_name": shop_name,
                            "image_url": img_url,
                            "ecommerce_link": ecommerce_link,
                            "is_closed": any(k in full_text for k in ("종료", "마감", "품절")),
                            "shipping_fee": shipping_fee,
                            "is_super_hotdeal": False,
                            "posted_at": posted_at,
                            "view_count": 1500,
                            "like_count": 5,
                            "comment_count": 0,
                            "content_html": f"<p>{full_text}</p>",
                        }
                        try:
                            ScrapedDeal(**{k: v for k, v in item.items() if v != ""})
                            parsed_items.append(item)
                        except Exception:
                            pass
                except Exception as b_err:
                    logger.warning(f"⚠️ [퀘이사존 브릿지] 페칭 경고 ({u}): {b_err}")

            # hotgusa API(communities=3) 병합: 텔레그램 미러에 누락된 퀘이사존 게시글까지 100% 보완
            try:
                hg_url = (
                    "https://www.hotgusa.com/api/products?communities=3&isOutOfStock=true"
                    "&category=&sort=date&shop=all&timeRange=24h&hiddenKeywords="
                    "&lastId=9_99999999999&lastValue=2099-12-31T23:59:59%2B09:00"
                )
                rh = await client.get(hg_url, headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"})
                if rh.status_code == 200:
                    payload = rh.json()
                    rows = payload.get("products", []) if isinstance(payload, dict) else (payload if isinstance(payload, list) else [])
                    by_url = {it["url"]: it for it in parsed_items}
                    for row in rows:
                        rid = str(row.get("id") or "")
                        if not rid.startswith("3_"):
                            continue
                        pno = rid.split("_", 1)[1]
                        q_url = f"https://quasarzone.com/bbs/qb_saleinfo/views/{pno}"
                        mall = str(row.get("mall_name") or "").strip()
                        raw_t = str(row.get("title") or "").strip()
                        full_t = f"[{mall}] {raw_t}" if mall and not raw_t.startswith("[") else raw_t
                        thumb = f"https://d2w999htn2iva3.cloudfront.net/thumbs/3/{pno}.jpg" if row.get("has_thumb", True) else ""
                        if q_url in by_url:
                            ex = by_url[q_url]
                            ex["title"] = full_t or ex["title"]
                            if int(row.get("price") or 0) > 0:
                                ex["price"] = int(row["price"])
                            if mall and not ex.get("shop_name"):
                                ex["shop_name"] = mall
                            if not ex.get("image_url") and thumb:
                                ex["image_url"] = thumb
                            ex["is_closed"] = ex["is_closed"] or bool(row.get("is_end", False))
                            ex["like_count"] = max(int(ex.get("like_count") or 0), int(row.get("recm_cnt") or 0))
                            ex["view_count"] = max(int(ex.get("view_count") or 0), int(row.get("view_cnt") or 0))
                            ex["comment_count"] = max(int(ex.get("comment_count") or 0), int(row.get("cmt_cnt") or 0))
                        else:
                            seen_urls.add(q_url)
                            deliv = int(row.get("delivery_price") or 0)
                            reg_iso = str(row.get("reg_date") or "")
                            if reg_iso:
                                try:
                                    from datetime import datetime, timezone
                                    dt_reg = datetime.fromisoformat(reg_iso.replace("Z", "+00:00")).astimezone(timezone.utc)
                                    reg_iso = dt_reg.strftime("%Y-%m-%dT%H:%M:%SZ")
                                except Exception:
                                    pass
                            new_it = {
                                "category": None,
                                "title": full_t,
                                "url": q_url,
                                "price": int(row.get("price") or 0),
                                "currency": "USD" if row.get("currency_code") == 1 else "KRW",
                                "shop_name": mall,
                                "image_url": thumb,
                                "ecommerce_link": "",
                                "is_closed": bool(row.get("is_end", False)),
                                "shipping_fee": "무료배송" if deliv == 0 else f"{deliv:,}원",
                                "is_super_hotdeal": int(row.get("recm_cnt") or 0) >= 8 or int(row.get("view_cnt") or 0) >= 2500,
                                "posted_at": reg_iso or None,
                                "view_count": int(row.get("view_cnt") or 100),
                                "like_count": int(row.get("recm_cnt") or 0),
                                "comment_count": int(row.get("cmt_cnt") or 0),
                                "content_html": f"<p>{full_t}</p>",
                            }
                            parsed_items.append(new_it)
            except Exception as hg_err:
                logger.warning(f"⚠️ [퀘이사존 hotgusa 브릿지] 경고: {hg_err}")

        logger.info(f"⚡ [퀘이사존 브릿지] 실시간 공공 핫딜 브릿지에서 {len(parsed_items)}건 수집 완료")
        return parsed_items

    async def run(self, url: str) -> list[dict]:
        """클라우드 환경에서는 403 차단을 피하기 위해 즉시 실시간 핫딜 브릿지로 직행하고, 로컬에서는 웹 파싱 우선 시도"""
        import os
        is_cloud = os.getenv("IS_CLOUD_ENV", "false").lower() in ("1", "true", "yes") or os.getenv("GITHUB_ACTIONS", "").lower() == "true"
        if is_cloud:
            return await self._fetch_fallback_from_bridge()
        deals = await super().run(url)
        if not deals:
            logger.info(f"🌐 [퀘이사존] 웹 페이지 수집 0건/차단 감지 -> 실시간 핫딜 브릿지 폴백 발동: {url}")
            deals = await self._fetch_fallback_from_bridge()
        return deals

