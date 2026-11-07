from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy.orm import Session
from backend.database.session import get_db_session
from fastapi.responses import StreamingResponse
import httpx
from backend.database import models
import logging
import os
import re
import requests
from datetime import datetime, timedelta, timezone

import json
from pydantic import BaseModel
from typing import List, Optional

router = APIRouter()
logger = logging.getLogger(__name__)

_COMMUNITY_ID_CACHE: dict[int, str] = {}

def get_community_display_name_by_id(cid: int, db=None) -> Optional[str]:
    if cid in _COMMUNITY_ID_CACHE:
        return _COMMUNITY_ID_CACHE[cid]
    COMMUNITY_MAP = {
        "fmkorea": "펨코", "ppomppu": "뽐뿌", "ruliweb": "루리웹",
        "clien": "클리앙", "quasarzone": "퀘이사존", "ali_ppomppu": "알리뽐뿌",
        "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외",
        "bbasak_parenting": "빠삭육아"
    }
    if db:
        try:
            all_comms = db.query(models.Community).all()
            for c in all_comms:
                _COMMUNITY_ID_CACHE[c.id] = c.display_name or COMMUNITY_MAP.get(c.name, c.name)
            if cid in _COMMUNITY_ID_CACHE:
                return _COMMUNITY_ID_CACHE[cid]
        except Exception:
            pass
    fallback_id_map = {
        1: "뽐뿌", 2: "루리웹", 3: "클리앙", 4: "알리뽐뿌",
        5: "퀘이사존", 6: "펨코", 7: "빠삭국내", 8: "빠삭해외", 9: "빠삭육아"
    }
    return fallback_id_map.get(cid)

def _build_direct_mall_search_url(shop_name: str, raw_title: str, post_url: str = "", comp_name: str = "") -> str:
    """
    뽐뿌 등 커뮤니티 본문 내 외부 링크가 누락되었거나 모음전으로 생략되었을 때,
    제목의 [쇼핑몰] 태그 및 상품명을 기반으로 해당 쇼핑몰의 다이렉트 상품 페이지로 즉시 연결합니다.
    (사용자가 '쇼핑몰 바로가기'나 카드를 눌렀을 때 다시 커뮤니티 게시글로 튕기는 현상 원천 차단!)
    """
    import urllib.parse
    from backend.services.aggregator_service import infer_shop_name

    inferred_shop = infer_shop_name(raw_title or "", "", shop_name or "")
    if not inferred_shop:
        if "id=ppomppu8" in (post_url or "") or comp_name == "알리뽐뿌" or (raw_title and any(k in raw_title.lower() for k in ("$", "달러", "알리", "코인", "초이스", "광군제"))):
            inferred_shop = "알리익스프레스"
        elif "id=ppomppu4" in (post_url or ""):
            inferred_shop = "아마존"
        elif raw_title:
            m_b = re.match(r"^\s*\[([^\]]+)\]", raw_title)
            if m_b:
                b_txt = m_b.group(1).strip().lower()
                if "지그재그" in b_txt or "zigzag" in b_txt:
                    inferred_shop = "지그재그"
                elif "kream" in b_txt or "크림" in b_txt:
                    inferred_shop = "KREAM"
    if not inferred_shop:
        inferred_shop = "네이버"

    # 상품명에서 대괄호 쇼핑몰명, 가격/배송비 괄호, 노이즈 제거 후 핵심 모델/상품명 추출
    clean_q = re.sub(r"^\s*\[[^\]]+\]\s*", "", raw_title or "").strip()
    clean_q = re.sub(r"^\s*\[[^\]]+\]\s*", "", clean_q).strip()
    clean_q = re.sub(r"\([^)]*(?:원|무료|무배|배송|쿠폰|할인|적립|최대|\$|달러|불|유로|€)[^)]*\)", " ", clean_q)
    clean_q = re.sub(r"(?:\$\s*[0-9,.]+|[0-9,.]+\s*(?:원|만원|만|달러|불|유로|€)|무료배송|무배|택배비|특가|할인|핫딜|끌올|역대급)", " ", clean_q, flags=re.IGNORECASE)
    clean_q = re.sub(r"[/|·~()]+", " ", clean_q)
    clean_q = re.sub(r"\s+", " ", clean_q).strip(" -.,()")
    if len(clean_q) < 2:
        clean_q = (raw_title or "").strip()
    if len(clean_q) < 2:
        return ""
    tokens = clean_q.split()
    query_str = " ".join(tokens[:6])
    q_enc = urllib.parse.quote(query_str)

    mall_search_templates = {
        "G마켓": f"https://www.gmarket.co.kr/n/search?keyword={q_enc}",
        "지마켓": f"https://www.gmarket.co.kr/n/search?keyword={q_enc}",
        "옥션": f"https://browse.auction.co.kr/search?keyword={q_enc}",
        "11번가": f"https://search.11st.co.kr/pc/total-search?kwd={q_enc}",
        "쿠팡": f"https://www.coupang.com/np/search?q={q_enc}",
        "롯데온": f"https://www.lotteon.com/search/search/search.ecn?render=search&platform=pc&q={q_enc}",
        "네이버": f"https://search.shopping.naver.com/search/all?query={q_enc}",
        "SSG": f"https://www.ssg.com/search.ssg?target=all&query={q_enc}",
        "신세계몰": f"https://www.ssg.com/search.ssg?target=all&query={q_enc}",
        "이마트몰": f"https://emart.ssg.com/search.ssg?target=all&query={q_enc}",
        "오늘의집": f"https://ohou.se/productions/feed?query={q_enc}",
        "무신사": f"https://www.musinsa.com/search/goods?keyword={q_enc}",
        "29CM": f"https://search.29cm.co.kr/?keyword={q_enc}",
        "지그재그": f"https://zigzag.kr/search?keyword={q_enc}",
        "KREAM": f"https://kream.co.kr/search?keyword={q_enc}",
        "톡딜": f"https://store.kakao.com/search/result/product?q={q_enc}",
        "카카오": f"https://store.kakao.com/search/result/product?q={q_enc}",
        "알리익스프레스": f"https://ko.aliexpress.com/w/wholesale-{urllib.parse.quote(query_str.replace(' ', '-'))}.html?SearchText={q_enc}",
        "알리": f"https://ko.aliexpress.com/w/wholesale-{urllib.parse.quote(query_str.replace(' ', '-'))}.html?SearchText={q_enc}",
        "우체국쇼핑": f"https://mall.epost.go.kr/fo/search/search.do?searchTerm={q_enc}",
        "하이마트": f"https://www.e-himart.co.kr/app/search/totalSearch?query={q_enc}",
        "CJ온스타일": f"https://display.cjonstyle.com/p/search/searchAllList?k={q_enc}",
        "GS샵": f"https://www.gsshop.com/shop/search/main.gs?tq={q_enc}",
        "현대Hmall": f"https://www.hmall.com/md/pde/search.do?searchTerm={q_enc}",
        "컬리": f"https://www.kurly.com/search?sword={q_enc}",
        "아마존": f"https://www.amazon.com/s?k={q_enc}",
        "큐텐": f"https://www.qoo10.com/s/?keyword={q_enc}",
        "스팀": f"https://store.steampowered.com/search/?term={q_enc}",
        "올리브영": f"https://www.oliveyoung.co.kr/store/search/getSearchMain.do?query={q_enc}",
        "다이소몰": f"https://www.daisomall.co.kr/ds/search?searchTerm={q_enc}",
        "홈플러스": f"https://front.homeplus.co.kr/search?entry=direct&keyword={q_enc}",
        "LF몰": f"https://www.lfmall.co.kr/app/search/letSearch?searchTerm={q_enc}",
        "하프클럽": f"https://www.halfclub.com/search?keyword={q_enc}",
        "보리보리": f"https://www.boribori.co.kr/search?keyword={q_enc}",
    }
    return mall_search_templates.get(inferred_shop, f"https://search.shopping.naver.com/search/all?query={q_enc}")


def get_deal_sources(deal, comp_name, parsed_price_int, db=None):
    deal_sources = []
    currency = getattr(deal, 'currency', 'KRW') or 'KRW'
    content_html = getattr(deal, 'content_html', '') or ''
    deal_id = getattr(deal, 'id', None)
    
    # 모음전/다중 딜 여부 판별 (동일한 뽐뿌 상세 글 주소이거나 제목에 다중 기호가 있는 경우)
    is_multi = False
    ecommerce_link = getattr(deal, 'ecommerce_link', None)
    post_link = getattr(deal, 'post_link', None)
    title = getattr(deal, 'title', '') or ''
    ai_summary = getattr(deal, 'ai_summary', '') or ''
    shop_name = getattr(deal, 'shop_name', '') or ''
    
    # 핫딜 복원 실패 시의 동일 주소 매칭 조건은 모음전 판단에서 전격 배제함!
    if (title and ("," in title or "모음" in title or "선택" in title or "외/" in title or "($86)" in title or "($26)" in title or "($195)" in title or "($324)" in title)) or ("[모음전]" in ai_summary):
        is_multi = True
        
    cleaned_ecom_url = get_clean_ecommerce_url(deal.ecommerce_link or deal.post_link or "", content_html, deal_id, db, is_multi_item=is_multi)
    if not cleaned_ecom_url:
        cleaned_ecom_url = _build_direct_mall_search_url(shop_name, title, post_link or "", comp_name)

    if hasattr(deal, 'options_data') and deal.options_data:
        try:
            options = json.loads(deal.options_data)
            for opt in options:
                opt_url = opt.get("url")
                opt_name = opt.get("name", "옵션")
                if opt_url:
                    opt_ecom_url = get_clean_ecommerce_url(opt_url, "", deal_id, None, is_multi_item=False)
                else:
                    opt_ecom_url = ""
                if not opt_ecom_url:
                    opt_ecom_url = _build_direct_mall_search_url(shop_name, f"[{shop_name}] {opt_name}" if shop_name else opt_name, post_link or "", comp_name) or cleaned_ecom_url
                deal_sources.append({
                    "site_name": f"{comp_name} - {opt_name}",
                    "post_url": deal.post_link or "",
                    "ecommerce_url": opt_ecom_url,
                    "price": int(opt.get("price", parsed_price_int)),
                    "currency": opt.get("currency", currency),
                    "code": opt.get("code") or "",
                    "image_url": opt.get("image_url") or "",
                    "sub_urls": opt.get("sub_urls") or opt.get("urls") or [],
                })
        except Exception:
            deal_sources.append({
                "site_name": comp_name, 
                "post_url": deal.post_link or "",
                "ecommerce_url": cleaned_ecom_url,
                "price": parsed_price_int,
                "currency": currency,
                "code": "",
                "image_url": getattr(deal, 'image_url', '') or "",
                "sub_urls": [],
            })
    else:
        deal_sources.append({
            "site_name": comp_name, 
            "post_url": deal.post_link or "",
            "ecommerce_url": cleaned_ecom_url,
            "price": parsed_price_int,
            "currency": currency,
            "code": "",
            "image_url": getattr(deal, 'image_url', '') or "",
            "sub_urls": [],
        })
        
    # [백그라운드 병합 딜(merged_communities) 소스 복원 로직 추가 - 인메모리 캐시로 N+1 DB 쿼리 완전 제거]
    if hasattr(deal, 'merged_communities') and deal.merged_communities:
        for merge_item in deal.merged_communities.split(','):
            merge_item = merge_item.strip()
            if not merge_item: continue
            
            parts = merge_item.split("::")
            try:
                cid = int(parts[0])
                merge_post_url = parts[1] if len(parts) > 1 else deal.post_link
            except:
                continue
            
            m_name = get_community_display_name_by_id(cid, db)
            if m_name:
                existing_src = next((s for s in deal_sources if s.get("site_name") == m_name), None)
                if existing_src is not None:
                    if merge_post_url:
                        existing_src["post_url"] = merge_post_url
                else:
                    deal_sources.append({
                        "site_name": m_name,
                        "post_url": merge_post_url or "",
                        "ecommerce_url": cleaned_ecom_url,
                        "price": parsed_price_int,
                        "currency": currency,
                        "code": "",
                        "image_url": getattr(deal, 'image_url', '') or "",
                        "sub_urls": [],
                    })
    
    return deal_sources

BASE_URL = os.getenv("BASE_URL", "http://10.0.2.2:8080")

@router.get("/scraper-stats")
def get_scraper_stats():
    import os
    stats_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scraper_stats.json")
    if os.path.exists(stats_file):
        try:
            with open(stats_file, 'r', encoding='utf-8') as f:
                return json.load(f)
        except:
            return {}
    return {}

@router.get("/proxy-image")
async def proxy_image(url: str):
    from backend.main import proxy_image as main_proxy_image
    return await main_proxy_image(url)

def get_clean_ecommerce_url(url: str, content_html: str = "", deal_id: int = None, db = None, is_multi_item: bool = False) -> str:
    if not url:
        return ""
    url = re.split(r'[가-힣\s"\'<>]|(?:\$\d)', str(url).strip())[0].strip().rstrip(".,;)>]")
    if not url:
        return ""
        
    # [보증 가드]: 이미 진짜 쇼핑몰 아웃링크로 온전하게 변환/복원 완료된 주소라면 커뮤니티 경유 디코딩을 생략함
    if url.startswith("http") and not any(x in url.lower() for x in ["ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "quasarzone.com", "ruliweb.com", "ruliweb.daum.net", "bbasak.com", "clien.net"]):
        from backend.core.url_utils import apply_affiliate_monetization
        return apply_affiliate_monetization(url)
    
    if is_multi_item:
        # 모음전이거나 여러 개가 묶인 상품이라도 이미 외부 쇼핑몰 주소라면 그대로 반환하고, 커뮤니티 글 주소라면 본문에서 첫 번째 쇼핑몰 링크를 추출 시도!
        if any(x in url.lower() for x in ["ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "quasarzone.com", "ruliweb.com", "ruliweb.daum.net", "bbasak.com", "clien.net"]) and not any(k in url.lower() for k in ["s.ppomppu.co.kr", "out_link.php", "view_gourl_show.php", "link.php", "popup/outlink"]):
            if not content_html:
                return ""
        else:
            return url
    
    import urllib.parse
    import base64
    url_str = str(url).strip()
    
    # 0. 즉시 Base64 디코딩 시도 (만약 이미 s.ppomppu.co.kr 이거나 target 파라미터가 들어있는 리디렉션 주소인 경우)
    if "s.ppomppu.co.kr" in url_str:
        match = re.search(r'target=([^&]+)', url_str)
        if match:
            try:
                base64_str = match.group(1)
                missing_padding = len(base64_str) % 4
                if missing_padding:
                    base64_str += '=' * (4 - missing_padding)
                decoded = base64.b64decode(base64_str).decode('utf-8')
                if decoded.startswith("http"):
                    url_str = decoded
            except Exception as e:
                logger.error(f"Failed to decode base64 target in url_str: {e}")

    # [백필 방어막]: 만약 들어온 URL이 뽐뿌 글 상세주소이거나 펨코 글 상세주소라면 진짜 아웃링크를 복원한다.
    is_fmkorea_post = "fmkorea.com/" in url_str or "fmkorea.org/" in url_str
    is_ppomppu_post = "ppomppu.co.kr/" in url_str
    
    if (is_fmkorea_post and "link.php" not in url_str) or (is_ppomppu_post and "out_link.php" not in url_str and "view_gourl_show.php" not in url_str and "s.ppomppu.co.kr" not in url_str):
        # 뽐뿌/펨코 상세 글 주소인 경우 진짜 쇼핑몰 링크 복원 시도
        real_url = None
        
        # 1차: content_html 내에서 쇼핑몰 도메인 및 s.ppomppu.co.kr 찾기
        if content_html:
            patterns = [
                r'href="([^"]*?s\.ppomppu\.co\.kr[^"]*?)"',
                r'https?://[^\s"\'<>]*?(?:gmarket|auction|11st|coupang|coupa\.ng|naver|ssg|lotteon|tmon|wemakeprice|aliexpress|taobao)[^\s"\'<>]*'
            ]
            for pat in patterns:
                matches = re.findall(pat, content_html, re.IGNORECASE)
                if matches:
                    for m in matches:
                        if "s.ppomppu.co.kr" in m:
                            t_match = re.search(r'target=([^&]+)', m)
                            if t_match:
                                try:
                                    b64 = t_match.group(1)
                                    missing_padding = len(b64) % 4
                                    if missing_padding:
                                        b64 += '=' * (4 - missing_padding)
                                    decoded = base64.b64decode(b64).decode('utf-8')
                                    if decoded.startswith("http"):
                                        real_url = decoded
                                        break
                                except:
                                    pass
                        elif "fmkorea" not in m and "ppomppu" not in m:
                            if "naver.com" in m and any(x in m for x in ["saedu", "log", "adbiz", "ad.naver", "click.link"]):
                                continue
                            real_url = m
                            break
                if real_url:
                    break
                    
        # 2차 실시간 파싱(requests.get)은 API 블로킹(응답 지연 및 새로고침 무한루프)을 유발하므로 제거함.
        if real_url:
            url_str = real_url
            if "aliexpress" in url_str and "af=" not in url_str:
                url_str += "&af=insightdeal" if "?" in url_str else "?af=insightdeal"
                
            if db and deal_id:
                try:
                    db.execute(
                        models.Deal.__table__.update()
                        .where(models.Deal.id == deal_id)
                        .values(ecommerce_link=url_str)
                    )
                    db.commit()
                    logger.info(f"Successfully backfilled ecommerce_link in DB for deal {deal_id} -> {url_str}")
                except Exception as db_err:
                    logger.error(f"Failed to commit backfilled ecommerce_link for deal {deal_id}: {db_err}")

    # 1. 펨코 (link.fmkorea.org/link.php?url=...)
    if "fmkorea.org/link.php" in url_str or "fmkorea.com/link.php" in url_str:
        match = re.search(r'url=([^&]+)', url_str)
        if match:
            try:
                decoded = urllib.parse.unquote(match.group(1))
                if decoded.startswith("http"):
                    url_str = decoded
            except:
                pass
                
    # 2. 뽐뿌 (m.ppomppu.co.kr/new/out_link.php?link=...)
    elif "out_link.php" in url_str:
        match = re.search(r'link=([^&]+)', url_str)
        if match:
            try:
                decoded = urllib.parse.unquote(match.group(1))
                if decoded.startswith("http"):
                    url_str = decoded
            except:
                pass
                
    # 3. 뽐뿌 PC (view_gourl_show.php?gourl=...)
    elif "view_gourl_show.php" in url_str:
        match = re.search(r'gourl=([^&]+)', url_str)
        if match:
            try:
                decoded = urllib.parse.unquote(match.group(1))
                if decoded.startswith("http"):
                    url_str = decoded
            except:
                pass
                
    # 4. 루리웹 (ruliweb.com/link.php?ol=...)
    elif "ruliweb.com/link.php" in url_str or "ruliweb.daum.net/link.php" in url_str:
        match = re.search(r'ol=([^&]+)', url_str)
        if match:
            try:
                decoded = urllib.parse.unquote(match.group(1))
                if decoded.startswith("http"):
                    url_str = decoded
            except:
                pass
                
    # 5. 클리앙 (clien.net/service/popup/outlink?url=...)
    elif "popup/outlink" in url_str:
        match = re.search(r'url=([^&]+)', url_str)
        if match:
            try:
                decoded = urllib.parse.unquote(match.group(1))
                if decoded.startswith("http"):
                    url_str = decoded
            except:
                pass
                
    # 모바일 도메인을 표준 PC/표준 주소 도메인으로 규격 다듬기
    url_str = url_str.replace("m.gmarket.co.kr", "item.gmarket.co.kr")
    url_str = url_str.replace("m.auction.co.kr", "itempage3.auction.co.kr")
    url_str = url_str.replace("m.11st.co.kr", "www.11st.co.kr")
    
    # [대표님이 명시한 특수 규격 보정]: G마켓 모바일 혹은 간소화 주소가 넘어올 때 Item?goodscode 로 규격 강제 일원화!
    # 예: http://item.gmarket.co.kr/Item?goodscode=4701840459
    if "gmarket.co.kr" in url_str:
        match = re.search(r'goodscode=([0-9]+)', url_str, re.IGNORECASE)
        if match:
            url_str = f"https://item.gmarket.co.kr/Item?goodscode={match.group(1)}"
            
    if "auction.co.kr" in url_str:
        match = re.search(r'itemno=([a-zA-Z0-9]+)', url_str, re.IGNORECASE)
        if match:
            url_str = f"https://itempage3.auction.co.kr/DetailView.aspx?itemno={match.group(1)}"
            
    # 모든 /./ 오염 주소를 원천 격파
    url_str = url_str.replace("/./", "/")

    if url_str.startswith("http://"):
        url_str = "https://" + url_str[7:]
    elif url_str.startswith("//"):
        url_str = "https:" + url_str
        
    # [아웃링크 오염 원천 봉쇄 가드]: 복원에 최종 실패하여 여전히 커뮤니티 글 주소라면 빈 문자열 반환
    if any(x in url_str for x in ["ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "quasarzone.com", "ruliweb.com", "bbasak.com", "clien.net"]):
        return ""

    from backend.core.url_utils import apply_affiliate_monetization
    return apply_affiliate_monetization(url_str)


def extract_price(price_str):
    if not price_str: return 0
    price_str_str = str(price_str)
    # 제휴 링크나 URL 내의 상품번호 숫자가 가격으로 오역되는 현상 영구 차단
    if "http://" in price_str_str or "https://" in price_str_str or "products/" in price_str_str:
        return 0
    nums = re.findall(r'\d+', price_str_str)
    if nums:
        parsed_val = int(''.join(nums))
        # 천오백만 원(15,000,000원) 이상의 비현실적 가격은 크롤링 오류이므로 0(정보 없음 / 본문 참조)으로 강제 정화
        if parsed_val > 15000000:
            return 0
        return parsed_val
    return 0


def resolve_deal_price_and_currency(deal) -> tuple[int, str]:
    parsed_price = extract_price(getattr(deal, 'price', 0))
    currency = getattr(deal, 'currency', 'KRW') or 'KRW'
    if parsed_price > 0:
        return parsed_price, currency
    title = getattr(deal, 'title', '') or ''
    if not title:
        return 0, currency
    m_usd = re.search(r'(?:\$\s*([0-9]+(?:\.[0-9]+)?)|([0-9]+(?:\.[0-9]+)?)\s*(?:달러|불))', title, re.IGNORECASE)
    if m_usd:
        val = float(m_usd.group(1) or m_usd.group(2))
        if 0 < val < 100000:
            return int(round(val * 100)), 'USD'
    won_matches = list(
        re.finditer(
            r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,7})\s*원(?!\s*(?:권|이상|할인|적립|페이백|캐시백|청구|한도|증정))',
            title
        )
    )
    if won_matches:
        val = int(won_matches[-1].group(1).replace(',', ''))
        if 100 <= val <= 15000000:
            return val, 'KRW'
    m_chun = re.search(
        r'([0-9]+(?:\.[0-9]+)?)\s*천\s*원(?!\s*(?:권|이상|할인|적립|페이백|캐시백|청구|한도|증정))',
        title
    )
    if m_chun:
        return int(round(float(m_chun.group(1)) * 1000)), 'KRW'
    return 0, currency


def extend_unique_sources(existing_sources: list, new_sources: list):
    for ns in new_sources:
        ns_site = ns.get("site_name") or ""
        if " - " in ns_site:
            opt_name = re.sub(r'\s+', ' ', ns_site.split(" - ", 1)[1].strip()).lower()
            if any(
                " - " in (es.get("site_name") or "")
                and re.sub(r'\s+', ' ', (es.get("site_name") or "").split(" - ", 1)[1].strip()).lower() == opt_name
                for es in existing_sources
            ):
                continue
        existing_sources.append(ns)

def extract_shipping_fee(fee_str):
    if not fee_str:
        return 0
    fee_str_str = str(fee_str).strip()
    if len(fee_str_str) > 25 or any(x in fee_str_str for x in ['무료', '무배', '무료배송', '포함', '정보 없음', '확인 필요']):
        return 0
    if '$' in fee_str_str or '달러' in fee_str_str:
        m_usd = re.search(r'([0-9]+(?:\.[0-9]+)?)', fee_str_str)
        return round(float(m_usd.group(1)) * 100) if m_usd else 0
    m_won = re.search(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,6})', fee_str_str)
    if m_won:
        val = int(m_won.group(1).replace(',', ''))
        return val if val <= 100000 else 0
    return 0


_BENEFIT_TAG_RULES = [
    (["삼성카드", "삼카"], "삼성카드"),
    (["신한카드", "신카"], "신한카드"),
    (["국민카드", "kb카드", "국민kb"], "국민카드"),
    (["현대카드", "현카"], "현대카드"),
    (["농협카드", "nh카드"], "농협카드"),
    (["롯데카드", "롯카"], "롯데카드"),
    (["하나카드"], "하나카드"),
    (["우리카드"], "우리카드"),
    (["비씨카드", "bc카드"], "BC카드"),
    (["카드할인", "카드결제", "청구할인"], "카드할인"),
    (["네이버페이", "네페"], "네이버페이"),
    (["카카오페이", "카페"], "카카오페이"),
    (["토스페이", "토스결제"], "토스페이"),
    (["페이코", "payco"], "페이코"),
    (["와우회원", "와우할인", "로켓와우"], "와우할인"),
    (["우주패스"], "우주패스"),
    (["네멤", "네이버멤버십", "플러스멤버십"], "네이버멤버십"),
    (["쿠폰", "중복쿠폰", "할인코드", "프로모션코드"], "쿠폰적용"),
    (["사은품", "증정", "+증정"], "사은품"),
    (["선착순", "타임딜", "한정수량"], "타임특가"),
    (["관부가세", "직구", "초이스데이"], "해외직구"),
]

_BENEFIT_TAGS_MEMO: dict[str, list[str]] = {}
_NORM_NAME_MEMO: dict[str, Optional[str]] = {}
_NORM_URL_MEMO: dict[str, Optional[str]] = {}

def extract_deal_benefit_tags(title: str, content_html: str = "") -> list[str]:
    """제목 및 본문에서 결제 수단/멤버십/쿠폰 혜택을 자동 추출하여 최대 3개 태그로 반환"""
    cache_k = f"{title or ''}|{(content_html or '')[:120]}"
    if cache_k in _BENEFIT_TAGS_MEMO:
        return _BENEFIT_TAGS_MEMO[cache_k]
    text = f"{title or ''} {(content_html or '')[:600]}".lower()
    tags = []
    for keywords, badge in _BENEFIT_TAG_RULES:
        if any(kw in text for kw in keywords):
            if badge not in tags:
                tags.append(badge)
            if len(tags) >= 3:
                break
    if len(_BENEFIT_TAGS_MEMO) > 4000:
        _BENEFIT_TAGS_MEMO.clear()
    _BENEFIT_TAGS_MEMO[cache_k] = tags
    return tags



def normalize_units(text: str) -> str:
    t = text.lower()
    t = re.sub(r'(\d+)\s*\+\s*(\d+)', r'\1 \2', t)
    t = re.sub(r'(\d+)\s*기가바이트', r'\1gb', t)
    t = re.sub(r'(\d+)\s*기가', r'\1gb', t)
    t = re.sub(r'(\d+)\s*tb', r'\1000gb', t)
    t = re.sub(r'(\d+)\s*gb', r'\1gb', t)
    t = re.sub(r'(\d+\.\d+)\s*kg', lambda m: str(int(float(m.group(1)) * 1000)) + 'g', t)
    t = re.sub(r'(\d+\.\d+)\s*l', lambda m: str(int(float(m.group(1)) * 1000)) + 'ml', t)
    t = re.sub(r'(?<!\d)(\d+)\s*kg', lambda m: str(int(m.group(1)) * 1000) + 'g', t)
    t = re.sub(r'(?<!\d)(\d+)\s*l', lambda m: str(int(m.group(1)) * 1000) + 'ml', t)
    t = re.sub(r'(\d+)\s*킬로그램', r'\1kg', t)
    return t
def get_normalized_base_name(deal):
    raw_title = getattr(deal, 'title', None)
    if not raw_title:
        return None
    if raw_title in _NORM_NAME_MEMO:
        return _NORM_NAME_MEMO[raw_title]
    clean = raw_title
    clean = re.sub(r'\([^)]*(?:달러|배송|무배|무료|체감|할인|쿠폰|최저가|특가)[^)]*\)', '', clean).strip()
    clean = re.sub(r'\s*\([\d,\s/]+\)\s*$', '', clean).strip()
    clean = re.sub(r'\[[^\]]*\]', '', clean).strip()
    clean = re.sub(r'\d+(?:,\d+)*\s*원', '', clean)
    clean = normalize_units(clean)
    clean = re.sub(r'아이(?:스)?\s*아메리카노', '아이스아메리카노', clean)
    base_str = re.sub(r'[^가-힣a-zA-Z0-9]', ' ', clean)
    malls = r'(쿠팡|g마켓|지마켓|11번가|십일번가|옥션|위메프|티몬|ssg|쓱|이마트|홈플러스|롯데마트|하이마트|오늘의집|집꾸미기|알리|알리익스프레스|테무|큐텐|아마존|무신사|올리브영|에이블리|지그재그|아이디어스)'
    modifiers = r'(역대급|대박|미친|오늘만|단하루|품절|임박|로켓|스마일|새벽|무료|배송|무배|체감|추가|할인|반값|특가|쿠폰|카드|핫딜|최저가|이벤트|세일|오픈|론칭|한정|수량|마감|긴급|선착순|단독|예약|할인가|공식|인증|행사|사은품|증정|패키지|초특가|투데이|반짝|게릴라|종결|우주패스|빅스마일데이|광군제|블프|블랙프라이데이|크리스마스|명절|설날|추석|옵션|행상\d+종|행상\d+가지|재입고|출시|추천|종료)'
    clean = re.sub(malls, '', base_str, flags=re.IGNORECASE)
    clean = re.sub(modifiers, '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\s+', ' ', clean).strip()
    res = clean if len(clean) > 1 else base_str.strip()
    if len(_NORM_NAME_MEMO) > 4000:
        _NORM_NAME_MEMO.clear()
    _NORM_NAME_MEMO[raw_title] = res
    return res
def get_normalized_url(deal):
    raw_url = getattr(deal, 'ecommerce_link', None) or ""
    if not raw_url:
        return None
    if raw_url in _NORM_URL_MEMO:
        return _NORM_URL_MEMO[raw_url]
    from backend.core.url_utils import extract_canonical_product_key, normalize_url
    canonical_key = extract_canonical_product_key(raw_url)
    if canonical_key:
        res = canonical_key
    elif "link.php" in raw_url or "out.php" in raw_url:
        res = None
    else:
        res = normalize_url(raw_url)
    if len(_NORM_URL_MEMO) > 4000:
        _NORM_URL_MEMO.clear()
    _NORM_URL_MEMO[raw_url] = res
    return res

class DSU:
    def __init__(self):
        self.parent = {}
    def find(self, i):
        if self.parent.setdefault(i, i) != i:
            self.parent[i] = self.find(self.parent[i])
        return self.parent[i]
    def union(self, i, j):
        root_i = self.find(i)
        root_j = self.find(j)
        if root_i != root_j:
            self.parent[root_i] = root_j

def has_quantity_conflict(words_a, words_b):
    """
    용량(g/kg, ml/l), 수량(개/입/캔/병/팩/봉/포/매/박스), 전력/용량(w, gb/tb), 화면크기(인치/호)가
    양쪽에 모두 존재하는데 수치가 다르면 절대 병합하지 않도록 방어합니다.
    """
    def get_quants(words):
        q = {}
        joined = words.lower() if isinstance(words, str) else " ".join(words).lower()
        # 복합 수량(예: 500ml x 24캔, 200g*10개)까지 모두 개별 단위로 정밀 추출
        matches = re.findall(
            r'(\d+(?:\.\d+)?)\s*(kg|g|ml|l|gb|tb|w|인치|inch|개입|개|입|캔|병|팩|봉|포|매|판|박스|세트|롤|정|캡슐)(?![a-zA-Z])',
            joined
        )
        for val_str, unit in matches:
            val = float(val_str)
            if unit in ['개', '개입', '입', '판', '팩', '봉', '포', '세트', '매', '캔', '병', '박스', '롤', '정', '캡슐']:
                u_key = 'count'
            elif unit == 'kg':
                val *= 1000
                u_key = 'weight'
            elif unit == 'g':
                u_key = 'weight'
            elif unit == 'l':
                val *= 1000
                u_key = 'volume'
            elif unit == 'ml':
                u_key = 'volume'
            elif unit == 'tb':
                val *= 1000
                u_key = 'storage'
            elif unit == 'gb':
                u_key = 'storage'
            elif unit == 'w':
                u_key = 'watt'
            elif unit in ['인치', 'inch']:
                u_key = 'inch'
            else:
                u_key = unit
            q.setdefault(u_key, set()).add(int(round(val)))
        return q
        
    qa = get_quants(words_a)
    qb = get_quants(words_b)
    for unit, vals_a in qa.items():
        if unit in qb:
            if not vals_a.intersection(qb[unit]):
                return True
    return False

def extract_true_model_codes(words) -> set[str]:
    if isinstance(words, str):
        words = set(re.split(r'[\s,\[\]\(\)/]+', words))
    codes = set()
    for w in words:
        w_low = w.lower().strip()
        if (len(w_low) >= 4
                and re.match(r'^[a-zA-Z0-9\-]+$', w_low)
                and any(c.isdigit() for c in w_low)
                and any(c.isascii() and c.isalpha() for c in w_low)
                and not re.match(r'^\d+(gb|tb|mb|kg|g|ml|l|w|v|a|hz|fps|pcs|ea|mah|ah|gsm|in1|p|구|미|쪽|봉|팩|개|캔)$', w_low)
                and not re.match(r'^\d+x\d+$', w_low)):
            codes.add(w_low.replace('-', ''))
    return codes

def has_model_conflict(words_a, words_b):
    if isinstance(words_a, str):
        words_a = set(re.split(r'[\s,\[\]\(\)/]+', words_a))
    if isinstance(words_b, str):
        words_b = set(re.split(r'[\s,\[\]\(\)/]+', words_b))

    codes_a = extract_true_model_codes(words_a)
    codes_b = extract_true_model_codes(words_b)

    if codes_a and codes_b:
        if not codes_a.intersection(codes_b):
            return True
        else:
            # 고유 제조사 모델코드(예: WIZ040, LS32FM701 등)가 명확히 일치하면 동일 제품으로 인정
            return False

    tier_tokens = {"pro", "max", "plus", "ultra", "mini", "se", "fe", "lite", "air", "ti", "super", "oled", "qled", "프로", "맥스", "플러스", "울트라", "미니", "라이트", "에어"}
    tiers_a = {w.lower() for w in words_a if w.lower() in tier_tokens}
    tiers_b = {w.lower() for w in words_b if w.lower() in tier_tokens}
    if tiers_a != tiers_b:
        return True

    return False

def has_brand_or_keyword_conflict(words_a, words_b):
    if isinstance(words_a, str):
        words_a = set(re.split(r'[\s,\[\]\(\)/]+', words_a))
    if isinstance(words_b, str):
        words_b = set(re.split(r'[\s,\[\]\(\)/]+', words_b))
    brands = {
        "펩시", "코카콜라", "코카", "칠성", "스프라이트", "가래떡", "만두", "피자", "치킨", "라면", "신라면", 
        "불닭", "햇반", "오뚜기밥", "아디다스", "나이키", "뉴발란스", "푸마", "울트라기어", "아이맥", "imac", 
        "맥북", "그램", "갤럭시", "아이폰", "아이패드", "에어팟", "버즈", "닌텐도", "플레이스테이션", "ps5", 
        "xbox", "엑스박스", "조청", "떡", "갑오징어", "오징어", "삼다수", "아이시스", "백산수", "스파클",
        "비비고", "하림", "마니커", "종가집", "동원", "오뚜기", "농심", "삼양", "청정원", "풀무원",
        "로지텍", "레이저", "레노버", "아수스", "asus", "msi", "기가바이트", "한성", "주연테크"
    }
    
    words_a_lower = {w.lower() for w in words_a}
    words_b_lower = {w.lower() for w in words_b}
    
    brands_a = {w for w in words_a_lower if w in brands or any(b in w for b in brands)}
    brands_b = {w for w in words_b_lower if w in brands or any(b in w for b in brands)}
    
    if brands_a and brands_b:
        has_overlap = False
        for ba in brands_a:
            for bb in brands_b:
                if ba in bb or bb in ba:
                    has_overlap = True
                    break
            if has_overlap:
                break
        if not has_overlap:
            return True
    return False

def has_price_conflict(price_a: int, price_b: int) -> bool:
    """
    동일 상품인데 가격이 45% 이상 차이 나면 본품 vs 액세서리/단품 vs 박스 구성일 확률이 높으므로 병합을 차단합니다.
    """
    if price_a > 0 and price_b > 0:
        hi = max(price_a, price_b)
        lo = min(price_a, price_b)
        if (hi - lo) / hi > 0.45:
            return True
    return False

def build_dsu(deals, time_window_days=1):
    dsu = DSU()
    url_to_deal = {}
    model_to_deal = {}
    name_clusters = []

    unit_or_generic_re = re.compile(
        r'^(?:\d+(?:\.\d+)?(?:kg|g|ml|l|gb|tb|mb|w|v|a|hz|mah|ah|gsm|in1|인치|inch|개입|개|입|캔|병|팩|봉|포|매|판|박스|세트|롤|정|캡슐|구|미|쪽|호|p|ea|pcs|년|개월|일|시간|원|만원|천원)?|국내산|국산|해외|정품|무료|무배|특가|할인|증정|사은품|단품|혼합|선택|외|모음|한정|출시|신제품|신모델|리뉴얼|끌올)$',
        re.IGNORECASE
    )

    def get_core_words(words_set: set[str]) -> set[str]:
        return {w.lower() for w in words_set if len(w.strip()) >= 2 and not unit_or_generic_re.match(w.strip())}

    for deal in deals:
        dsu.find(deal.id)
        n_url = get_normalized_url(deal)
        n_name = get_normalized_base_name(deal)
        deal_time = deal.indexed_at
        curr_price = extract_price(getattr(deal, 'price', 0))
        curr_model = (getattr(deal, 'model_code', None) or "").strip().lower()

        curr_words_set = set(n_name.split(' ')) if n_name else set()
        curr_core_set = get_core_words(curr_words_set)
        curr_sc = "".join(sorted(curr_core_set)) if curr_core_set else (n_name.replace(' ', '') if n_name else "")

        # 1단계: 쇼핑몰 고유 상품 키(Canonical Product Key) 또는 정규화 URL 기반 매칭
        if n_url and not any(x in n_url for x in ["ppomppu", "fmkorea", "ruliweb", "quasarzone", "clien", "bbasak"]):
            n_url_key = n_url
            if n_url_key in url_to_deal:
                for past_id, past_words_set, past_core_set, past_price in url_to_deal[n_url_key]:
                    if curr_words_set and past_words_set:
                        intersection = len(curr_words_set.intersection(past_words_set))
                        union = len(curr_words_set.union(past_words_set))
                        core_inter = len(curr_core_set.intersection(past_core_set))

                        is_exact_id = any(
                            n_url_key.startswith(prefix)
                            for prefix in [
                                "gmarket_", "auction_", "11st_", "coupang_", "aliexpress_",
                                "naver_", "lotteon_", "ssg_", "musinsa_", "ohouse_", "kakao_", "himart_"
                            ]
                        )

                        if is_exact_id:
                            # 고유 쇼핑몰 상품번호가 100% 일치하면 옵션/수량/가격 충돌만 없을 때 즉시 동일 상품으로 병합!
                            if not has_quantity_conflict(curr_words_set, past_words_set) and not has_model_conflict(curr_words_set, past_words_set) and not has_brand_or_keyword_conflict(curr_words_set, past_words_set) and not has_price_conflict(curr_price, past_price):
                                if core_inter >= 1 and (union == 0 or (intersection / union) >= 0.25):
                                    dsu.union(deal.id, past_id)
                                    break
                        else:
                            if union > 0 and (intersection / union) >= 0.8 and core_inter >= 2:
                                if not has_quantity_conflict(curr_words_set, past_words_set) and not has_model_conflict(curr_words_set, past_words_set) and not has_brand_or_keyword_conflict(curr_words_set, past_words_set) and not has_price_conflict(curr_price, past_price):
                                    dsu.union(deal.id, past_id)
                                    break
            url_to_deal.setdefault(n_url_key, []).append((deal.id, curr_words_set, curr_core_set, curr_price))

        # 2단계: 고유 제조사 모델코드 일치 시 교차 병합
        true_codes = extract_true_model_codes(curr_words_set)
        if curr_model and len(curr_model) >= 5:
            true_codes.update(extract_true_model_codes({curr_model}))
        for code_key in true_codes:
            if code_key in model_to_deal:
                for past_id, past_words_set, past_core_set, past_price in model_to_deal[code_key]:
                    if (
                        len(curr_core_set.intersection(past_core_set)) >= 2
                        and not has_quantity_conflict(curr_words_set, past_words_set)
                        and not has_model_conflict(curr_words_set, past_words_set)
                        and not has_brand_or_keyword_conflict(curr_words_set, past_words_set)
                        and not has_price_conflict(curr_price, past_price)
                    ):
                        dsu.union(deal.id, past_id)
                        break
            model_to_deal.setdefault(code_key, []).append((deal.id, curr_words_set, curr_core_set, curr_price))

        # 3단계: 정규화 상품명 및 핵심 토큰 유사도 기반 매칭
        if n_name and curr_core_set:
            words_set = curr_words_set
            matched = False
            for cluster in name_clusters:
                time_diff_ok = False
                if deal_time:
                    for c_time in cluster.get('times', []):
                        if c_time and abs((deal_time - c_time).total_seconds()) <= time_window_days * 24 * 3600:
                            time_diff_ok = True
                            break
                    if not cluster.get('times'):
                        time_diff_ok = True
                else:
                    time_diff_ok = True

                if not time_diff_ok:
                    continue

                core_inter = len(curr_core_set.intersection(cluster['core_words']))
                core_union = len(curr_core_set.union(cluster['core_words']))
                core_jaccard = core_inter / core_union if core_union > 0 else 0

                core_min_len = min(len(curr_core_set), len(cluster['core_words']))
                core_subset_ratio = core_inter / core_min_len if core_min_len > 0 else 0

                sc_match = bool(curr_sc and cluster.get('sc') and curr_sc == cluster['sc'] and core_inter >= 2)

                if (core_min_len == 1 and core_jaccard == 1.0) or (
                    core_inter >= 2 and (core_jaccard >= 0.68 or sc_match or (core_subset_ratio >= 0.85 and core_inter >= 3))
                ):
                    if (
                        not has_quantity_conflict(words_set, cluster['words'])
                        and not has_model_conflict(words_set, cluster['words'])
                        and not has_brand_or_keyword_conflict(words_set, cluster['words'])
                        and not has_price_conflict(curr_price, cluster.get('price', 0))
                    ):
                        dsu.union(deal.id, cluster['id'])
                        if deal_time:
                            cluster.setdefault('times', []).append(deal_time)
                        matched = True
                        break

            if not matched:
                name_clusters.append({
                    'id': deal.id,
                    'words': words_set,
                    'core_words': curr_core_set,
                    'sc': curr_sc,
                    'price': curr_price,
                    'times': [deal_time] if deal_time else []
                })

    return dsu

import time as _feed_time

# 자체 프리파싱(Pre-parsed) 인메모리 JSON API 캐시 (페이지 이동·탭 전환 시 재파싱 없이 0.5ms 내 즉시 응답)
_TOP_DEALS_API_CACHE = {"ts": 0.0, "max_id": 0, "data": None}
_HOT_DEALS_API_CACHE = {}  # key: (category, keyword, platform) -> {"ts": float, "max_id": int, "grouped": list}
_FEED_CACHE_TTL_SEC = 20.0

def invalidate_feed_api_cache():
    _TOP_DEALS_API_CACHE["ts"] = 0.0
    _HOT_DEALS_API_CACHE.clear()

def _clean_content_text(raw_text: str, title: str = "") -> str:
    if not raw_text:
        return ""
    text = raw_text
    # 스크래퍼 아티팩트 제거: CDATA/인라인 JS, 댓글 영역 누수, (링크: ...), [첨부된 이미지 링크들]: ..., 비디오 태그 폴백 문구 등
    text = re.sub(r'//<!\[CDATA\[[\s\S]*?//\]\]>', '', text, flags=re.IGNORECASE)
    text = re.sub(r'댓글\s*작성\s*하려면\s*로그인이\s*필요합니다[\s\S]*$', '', text, flags=re.IGNORECASE)
    text = re.sub(r'추천수\s*/\s*비추천수[\s\S]*$', '', text, flags=re.IGNORECASE)
    text = re.sub(r'\[상품이미지\s*:\s*https?://[^\]]+\]', '', text, flags=re.IGNORECASE)
    text = re.sub(r'\[상품이미지\s*:[^\]]*\]', '', text, flags=re.IGNORECASE)
    text = re.sub(r'\(\s*링크\s*:\s*https?://[^\)]+\)', '', text, flags=re.IGNORECASE)
    text = re.sub(r'\[첨부된 이미지 링크들\]\s*:?[\s\S]*$', '', text, flags=re.IGNORECASE)
    text = re.sub(r'Your browser does not support the video tag\.?', '', text, flags=re.IGNORECASE)
    text = re.sub(r'<[^>]+>', ' ', text)
    text = (
        text.replace('&nbsp;', ' ')
        .replace('&amp;', '&')
        .replace('&lt;', '<')
        .replace('&gt;', '>')
        .replace('&quot;', '"')
        .replace('&#039;', "'")
        .replace('&#39;', "'")
    )
    # 단독 URL 라인 및 본문 내 날것의 URL 제거
    text = re.sub(r'https?://[^\s\)\]]+', '', text)
    # 각 줄 양끝 공백 정리 및 빈 줄 압축
    lines = [ln.strip() for ln in text.splitlines()]
    cleaned_lines = []
    for ln in lines:
        if not ln:
            if cleaned_lines and cleaned_lines[-1] != "":
                cleaned_lines.append("")
            continue
        # 의미 없는 구분선/기호만 있는 줄 제거
        if re.match(r'^[\-\=\*\_\~\.\,\:\;\(\)\[\]\/\\]+$', ln):
            continue
        cleaned_lines.append(ln)
    result = "\n".join(cleaned_lines).strip()
    if len(result) < 4:
        return ""
    if title and result.replace(" ", "") == title.replace(" ", ""):
        return ""
    return result

def _clean_ai_summary(raw_summary: str) -> str:
    if not raw_summary:
        return ""
    if any(bad in raw_summary for bad in (
        "여러 상품이 포함된 핫딜입니다",
        "AI가 자동 분리해낸 핫딜입니다",
        "묻지도 따지지도 않고 쟁여두면",
        "카드 할인/쿠폰 최대한 끌어모으면",
        "가격이 0원이거나 역동적 할인이 적용되어",
    )):
        return "[커뮤니티 인증 핫딜]" if ("인기" in raw_summary or "인증" in raw_summary) else ""
    cleaned = re.sub(r'[🔥✨🤖💡⚡🎉📦✅⚠️]+', '', raw_summary).strip()
    cleaned = re.sub(r'^\s*\[커뮤니티 인기\]\s*', '[커뮤니티 인증 핫딜] ', cleaned).strip()
    return re.sub(r'\s{2,}', ' ', cleaned)


def clean_deal_title(raw_title: str) -> str:
    """
    🧹 게시글 제목에서 지름 꼬리표(💰, 🚚, 퀘이사존/지름 등), 울트라 맥스 태그,
    가격/배송비 절삭 태그 및 미완성 닫히지 않은 괄호(r"\s*\([^)]*$")를 완벽하게 정제합니다.
    (수원, 강원 등 숫자가 없는 한글 지명 괄호는 안전하게 보존합니다)
    """
    if not raw_title:
        return ""
    t = str(raw_title).strip()
    if len(t) > 500:
        t = t[:500]
    t = re.sub(r'[\s/]*[￦₩\\💰].*$', '', t)
    t = re.sub(r'[\s/]*(?:🚚|퀘이사존/지름|지름&할인정보).*$', '', t)
    t = re.sub(r'\s*\([^)]*울트라\s*맥스[^)]*\)', '', t)
    # 1. 가격 표기 괄호 절삭: 화폐단위(원, 달러, 불 등) 앞에 반드시 숫자가 존재하는 경우만 정제하여 한글 지명(수원, 강원 등) 보존
    t = re.sub(r'\s*\([^)]*(?:[0-9,]+|[0-9]+(?:\.[0-9]+)?)\s*(?:원|달러|불|엔|유로|원\s*대|원\s*상당)[^)]*\)\s*$', '', t)
    # 2. 배송비 및 카드할인 안내 괄호 절삭
    t = re.sub(r'\s*\([^)]*(?:무료배송|배송비|무배|유배|카드할인)[^)]*\)\s*$', '', t)
    # 3. 가격 절삭 후 끝에 남은 미완성 열린 괄호 제거
    t = re.sub(r'\s*\([^)]*$', '', t)
    t = re.sub(r'[\s\.,\-\/~]+$', '', t).strip()
    return t or raw_title.strip()


def _resolve_best_image_url(deal_obj, base_url: str) -> Optional[str]:
    img = (getattr(deal_obj, "image_url", None) or "").replace("&amp;", "&").strip().rstrip(",)")
    if img.startswith("//"):
        img = "https:" + img
    if img:
        lower_img = img.lower()
        is_invalid_img = any(
            bad in lower_img
            for bad in (
                "no_image.svg",
                "thumb_no_image.svg",
                "noimage",
                "dot03.gif",
                "logo_f.svg",
                "49841804f3d132d75a6c11b1510af812.png",
                "https:undefined",
                "<a href=",
                "/web/upload/",
                "coupang.com/vp/products/",
                "item.gmarket.co.kr/item",
                "mobile.auction.co.kr/ego.aspx",
            )
        )
        if not is_invalid_img:
            if img.startswith("/images/"):
                return f"{base_url}{img}"
            if not img.startswith("http"):
                return f"{base_url}/images/{img}"
            return img
    content = getattr(deal_obj, "content_html", None) or ""
    if content:
        m = re.search(r'\[첨부된 이미지 링크들\]\s*:?\s*(https?://[^\s,\)\]]+)', content)
        if m:
            cand = m.group(1).strip().rstrip(",)")
            if cand and "no_image" not in cand.lower():
                return cand
        m2 = re.search(r'(https?://[^\s"\'<>\)\]]+?\.(?:jpg|jpeg|png|webp))', content, re.IGNORECASE)
        if m2:
            cand2 = m2.group(1).strip().rstrip(",)")
            if cand2 and "dot03.gif" not in cand2.lower():
                return cand2
    title = getattr(deal_obj, "title", None) or ""
    lower_title = title.lower()

    if any(kw in lower_title for kw in ["롯데시네마", "롯시"]):
        return "https://play-lh.googleusercontent.com/WUVm0Q6FQnqTZ33julTaMJx7oJCqYANWArhv92Sin8Bix4dBvgWxPn1YkCogC6BSihWS2dDz2evjq-FSIhPFIA=s300"
    if "메가박스" in lower_title:
        return "https://play-lh.googleusercontent.com/Np5x0-5pstR3-j9DsuIzeMLbCcvvdVAIqwfLlJY2XdJkjQWXUfdJCBF0nTvJr9iBhnSPPxuSJeHK2gJFP68vkac=s300"
    if any(kw in lower_title for kw in ["cgv", "씨지브이"]):
        return "https://play-lh.googleusercontent.com/3S9LNVDqVQCm-QRwvpECpJJBKmRhQ9LNuvPAc14occs2d69IM49_OCOyp8i0cTVmH3DUZvXGSYqvm7R7Pbxa7fY=s300"
    if any(kw in lower_title for kw in ["스타벅스", "스벅"]):
        return "https://play-lh.googleusercontent.com/vD7K2him82DhJFyfPjw9WbPIXTqLoS8_LoC4GPpfBKKsc2apjowqvcCw2l9zNRrI_YbortUyLhfKW14ePgL0pA=s300"
    if any(kw in lower_title for kw in ["네이버페이", "네이버하이", "네이버적립", "일일적립"]):
        return "https://campaign-cdn.pstatic.net/0/campaign2/2025/03/click-point/img/npay_og_260x260.png"
    if any(kw in lower_title for kw in [
        "기프트카드", "기프트코드", "문화상품권", "해피머니", "컬쳐랜드", 
        "도서문화상품권", "모바일상품권", "백화점상품권", "금액권", "교환권"
    ]):
        return "https://cdn-icons-png.flaticon.com/512/8633/8633496.png"
    return None

@router.get("/top-hot-deals")
async def get_top_hot_deals(db: Session = Depends(get_db_session)):
    try:
        from sqlalchemy import and_, or_, not_
        from sqlalchemy.orm import joinedload
        now_ts = _feed_time.time()
        if (
            _TOP_DEALS_API_CACHE["data"] is not None
            and (now_ts - _TOP_DEALS_API_CACHE["ts"]) < _FEED_CACHE_TTL_SEC
        ):
            return {"deals": _TOP_DEALS_API_CACHE["data"]}

        query = db.query(models.Deal).join(models.Community).options(
            joinedload(models.Deal.community),
            joinedload(models.Deal.deal_reactions)
        )
        # ⏰ 24시간 제한을 48시간으로 완화하여 어제 올라온 고품질 핫딜도 함께 구제
        time_limit = datetime.utcnow() - timedelta(hours=48)
        
        deals = query.filter(
            and_(
                # 🎯 대표님의 정책 결정에 따라 오늘의 핫딜 신뢰성 보존을 위해 100점 컷오프 유지!
                models.Deal.honey_score >= 100,
                models.Deal.indexed_at >= time_limit,
                models.Deal.is_closed == False,
                # 🧹 적립/이벤트 카테고리 제외
                models.Deal.category != "적립",
                models.Deal.category != "이벤트",
                models.Deal.category != "적립/이벤트",
                not_(models.Deal.title.like("%블라인드%"))
            )
        ).order_by(models.Deal.indexed_at.desc()).limit(600).all()

        # 48시간 내 신규 수집이 멈춘 상태라면 최신 고득점 딜로 폴백하여 빈 화면 방지
        use_time_cutoff = True
        if not deals:
            use_time_cutoff = False
            deals = query.filter(
                and_(
                    models.Deal.honey_score >= 100,
                    models.Deal.is_closed == False,
                    models.Deal.category != "적립",
                    models.Deal.category != "이벤트",
                    models.Deal.category != "적립/이벤트",
                    not_(models.Deal.title.like("%블라인드%"))
                )
            ).order_by(models.Deal.indexed_at.desc()).limit(200).all()

        COMMUNITY_MAP = {
            "fmkorea": "펨코", "ppomppu": "뽐뿌", "ruliweb": "루리웹",
            "clien": "클리앙", "quasarzone": "퀘이사존", "ali_ppomppu": "알리뽐뿌",
            "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외",
            "bbasak_parenting": "빠삭육아"
        }

        cluster_map = {}
        grouped_result = []
        
        dsu = build_dsu(deals)

        for deal in deals:
            comp_name = getattr(deal.community, 'display_name', None) or COMMUNITY_MAP.get(deal.community.name, deal.community.name)
            parsed_price_int, resolved_currency = resolve_deal_price_and_currency(deal)
            parsed_shipping_fee = extract_shipping_fee(deal.shipping_fee)
            total_price = parsed_price_int + parsed_shipping_fee if parsed_price_int > 0 else 0
            
            # 모음전/다중 딜 여부 판별
            is_multi = False
            ecommerce_link = getattr(deal, 'ecommerce_link', None)
            post_link = getattr(deal, 'post_link', None)
            title = getattr(deal, 'title', '') or ''
            ai_summary = getattr(deal, 'ai_summary', '') or ''
            if (ecommerce_link == post_link) or (title and ("," in title or "모음" in title or "선택" in title or "외/" in title or "($86)" in title or "($26)" in title or "($195)" in title or "($324)" in title)) or ("[모음전]" in ai_summary):
                is_multi = True
            
            cluster_key = dsu.find(deal.id)
            image_url = _resolve_best_image_url(deal, BASE_URL)

            if cluster_key in cluster_map:
                existing = cluster_map[cluster_key]
                if total_price > 0:
                    if existing.get("total_price", 0) == 0 or total_price < existing["total_price"]:
                        # 더 저렴한 딜이 발견되면 대표 가격, 이미지, 제목 갱신
                        existing["price"] = parsed_price_int
                        existing["shipping_fee"] = deal.shipping_fee or ""
                        existing["total_price"] = total_price
                        existing["currency"] = resolved_currency
                        clean_title = clean_deal_title(deal.title)
                        existing["title"] = clean_title
                        
                        # 더 저렴한 최저가에 유효한 이미지가 있거나 기존 딜에 이미지가 없는 경우에만 덮어씀
                        if image_url or not existing.get("image_url") or "ui-avatars.com" in (existing.get("image_url", "") or ""):
                            existing["image_url"] = image_url or existing.get("image_url")
                            
                        new_ecom_url = get_clean_ecommerce_url(deal.ecommerce_link or deal.post_link or "", deal.content_html, deal.id, db, is_multi_item=is_multi)
                        if not new_ecom_url:
                            new_ecom_url = _build_direct_mall_search_url(getattr(deal, 'shop_name', '') or '', deal.title or '', deal.post_link or '', comp_name)
                        if new_ecom_url or not existing.get("ecommerce_url"):
                            existing["ecommerce_url"] = new_ecom_url
                        existing["post_url"] = deal.post_link or ""
                    else:
                        if image_url and (not existing.get("image_url") or "ui-avatars.com" in (existing.get("image_url", "") or "")):
                            existing["image_url"] = image_url
                    
                    # 출처 추가 (post_url 기준 중복 방지)
                    if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                        if comp_name not in existing.setdefault("site_names", []):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                        extend_unique_sources(existing["sources"], get_deal_sources(deal, comp_name, parsed_price_int, db))
                else:
                    if existing.get("total_price", 0) == 0:
                        if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                            if comp_name not in existing.setdefault("site_names", []):
                                existing["site_names"].append(comp_name)
                                existing["site_name"] = ", ".join(existing["site_names"])
                            extend_unique_sources(existing["sources"], get_deal_sources(deal, comp_name, parsed_price_int, db))
                
                # 하나라도 살아있다면 전체 딜을 진행중인 것으로 처리
                existing["is_closed"] = existing.get("is_closed", True) and getattr(deal, 'is_closed', False)
                existing["has_options"] = existing.get("has_options", False) or bool(getattr(deal, "has_options", False))
                    
                # 다수의 중복 딜 중 하나라도 핫딜이면 핫딜 상태 병합
                if getattr(deal, 'honey_score', 0) >= 100:
                    existing["honey_score"] = max(existing.get("honey_score", 0), 100)
                if deal.ai_summary and ("🔥" in deal.ai_summary or "인증 핫딜" in deal.ai_summary):
                    if not existing.get("ai_summary"):
                        existing["ai_summary"] = "[커뮤니티 인증 핫딜]"
                    elif "인증 핫딜" not in existing["ai_summary"]:
                        existing["ai_summary"] = "[커뮤니티 인증 핫딜] " + existing["ai_summary"]
            else:
                ai_sum = _clean_ai_summary(deal.ai_summary)
                # Clean title for display
                clean_title = clean_deal_title(deal.title)
                
                buy_count = deal.like_count or 0
                save_count = 0
                if hasattr(deal, 'deal_reactions') and deal.deal_reactions:
                    reaction = deal.deal_reactions[0]
                    buy_count += reaction.positive_reactions
                    save_count = reaction.negative_reactions

                deal_dict = {
                    "id": deal.id,
                    "title": clean_title,
                    "price": parsed_price_int,
                    "shipping_fee": deal.shipping_fee or "",
                    "total_price": total_price,
                    "currency": resolved_currency,
                    "original_price": None,
                    "discount_rate": 0,
                    "image_url": image_url,
                    "ecommerce_url": get_clean_ecommerce_url(deal.ecommerce_link or deal.post_link or "", deal.content_html, deal.id, db, is_multi_item=is_multi) or _build_direct_mall_search_url(getattr(deal, 'shop_name', '') or '', deal.title or '', deal.post_link or '', comp_name),
                    "post_url": deal.post_link or "",
                    "site_name": comp_name,
                    "site_names": [comp_name],
                    "sources": get_deal_sources(deal, comp_name, parsed_price_int, db),
                    "category": deal.category or "기타",
                    "created_at": (deal.indexed_at.strftime('%Y-%m-%dT%H:%M:%SZ')) if deal.indexed_at else None,
                    "view_count": deal.view_count or 0,
                    "like_count": buy_count,
                    "comment_count": deal.comment_count or 0,
                    "dislike_count": save_count,
                    "tags": extract_deal_benefit_tags(deal.title, deal.content_html),
                    "is_closed": deal.is_closed,
                    "honey_score": deal.honey_score or 0,
                    "ai_summary": ai_sum,
                    "content_html": _clean_content_text(deal.content_html or "", clean_title),
                    "has_options": bool(getattr(deal, "has_options", False)),
                }
                cluster_map[cluster_key] = deal_dict
                grouped_result.append(deal_dict)
        if use_time_cutoff:
            # 48시간 이내에 올라온 딜을 포함한 클러스터링 필터링
            cutoff = datetime.utcnow() - timedelta(hours=48)
            filtered_result = []
            for deal_dict in grouped_result:
                if deal_dict.get("created_at"):
                    dt = datetime.fromisoformat(deal_dict["created_at"].replace("Z", "+00:00"))
                    if dt.tzinfo:
                        dt = dt.replace(tzinfo=None)
                    if dt >= cutoff:
                        filtered_result.append(deal_dict)
                else:
                    filtered_result.append(deal_dict)
        else:
            filtered_result = grouped_result
                
        # 필터링된 결과 중 앞에서 200개만 반환 (시간순 정렬 유지)
        final_top_result = filtered_result[:200]
        _TOP_DEALS_API_CACHE["ts"] = now_ts
        _TOP_DEALS_API_CACHE["data"] = final_top_result
        
        return {"deals": final_top_result}
    except Exception as e:
        logger.error(f"Error fetching top hot deals: {e}")
        raise HTTPException(status_code=500, detail="Internal Server Error")

@router.get("/hot-deals")
async def get_hot_deals(
    limit: int = 20,
    offset: int = 0,
    category: str = Query(None, description="분류별 카테고리명(ex: 전자기기, 패션 등)"),
    keyword: str = Query(None, description="검색어 (공백 AND, -제외단어, :상한가격 지원)"),
    platform: str = Query(None, description="커뮤니티/사이트 필터"),
    source: Optional[str] = Query(None, description="커뮤니티/사이트 필터 (alias for platform)"),
    sort: str = Query("latest", description="정렬 기준: latest | popular | price_asc"),
    db: Session = Depends(get_db_session)
):
    try:
        from sqlalchemy.orm import joinedload
        target_platform = platform or source
        now_ts = _feed_time.time()
        sort_mode = (sort or "latest").strip().lower()
        cache_key = ((category or "").strip(), (keyword or "").strip(), (target_platform or "").strip(), sort_mode)
        cached_entry = _HOT_DEALS_API_CACHE.get(cache_key)
        if (
            cached_entry is not None
            and (now_ts - cached_entry["ts"]) < _FEED_CACHE_TTL_SEC
        ):
            return {"deals": cached_entry["grouped"][offset:offset+limit]}

        query = db.query(models.Deal).join(models.Community).options(
            joinedload(models.Deal.community),
            joinedload(models.Deal.deal_reactions)
        )
        
        # 블라인드 처리된 게시글 및 종료된 딜은 기본 목록에서 제외 (신뢰성 보존)
        from sqlalchemy import not_
        query = query.filter(
            not_(models.Deal.title.like("%블라인드%")),
            models.Deal.is_closed == False
        )
        
        if category and category not in ["전체", ""]:
            # 탭 카테고리명을 스크래퍼 카테고리/키워드 배열로 매핑
            target_keywords = []
            if category == "음식":
                target_keywords = ["음식", "식품", "먹거리", "간식", "음료수", "건강"]
            elif category == "SW/게임":
                target_keywords = ["게임", "sw", "소프트웨어", "콘솔"]
            elif category == "PC용품":
                target_keywords = ["pc", "하드웨어", "노트북", "컴퓨터", "데스크탑", "모니터"]
            elif category == "가전제품":
                target_keywords = ["가전", "냉장고", "tv", "음향"]
            elif category == "생활용품":
                target_keywords = ["생활", "생필품", "주방", "가구", "인테리어"]
            elif category == "패션":
                target_keywords = ["패션", "의류", "신발", "잡화", "가방", "화장품"]
            elif category == "뷰티":
                target_keywords = ["뷰티", "미용", "화장품"]
            elif category == "모바일/기프티콘":
                target_keywords = ["모바일", "스마트폰", "태블릿", "기프티콘", "쿠폰", "e쿠폰", "e-쿠폰"]
            elif category == "상품권":
                target_keywords = ["상품권", "이용권", "패키지", "기프티콘", "커피", "이커머스"]
            elif category == "적립":
                target_keywords = ["적립", "사인업", "하이패스", "리퀘스트"]
            elif category == "이벤트":
                target_keywords = ["이벤트", "추석", "명절", "무료증정", "리뷰", "체험단", "선착순", "이벤트", "무료배포"]
            elif category == "육아/유아용품":
                target_keywords = ["유아용품", "육아"]
            elif category == "여행.해외핫딜":
                target_keywords = ["여행", "해외", "호텔", "프리미엄", "항공", "직구"]
            elif category == "핫딜모음":
                target_keywords = [] # 핫딜모음은 아래에서 별도로 필터링 (honey_score >= 100 or 🔥)
            else:
                target_keywords = [category]

            from sqlalchemy import or_, and_
            if category == "핫딜모음":
                query = query.filter(models.Deal.honey_score >= 100)
            elif category == "육아/유아용품":
                parenting_title_kws = ["기저귀", "분유", "아기", "유아", "키즈", "하기스", "팸퍼스", "젖병", "카시트", "유모차", "베베", "아동"]
                query = query.filter(
                    or_(
                        models.Community.name == "bbasak_parenting",
                        models.Deal.category.ilike("%육아%"),
                        models.Deal.category.ilike("%유아%"),
                        *[models.Deal.title.ilike(f"%{kw}%") for kw in parenting_title_kws]
                    )
                )
            elif category == "적립":
                point_title_kws = ["네이버페이", "적립", "네이버포인트", "페이포인트", "라방", "클릭", "출석"]
                query = query.filter(
                    or_(
                        *[models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords],
                        *[models.Deal.title.ilike(f"%{kw}%") for kw in point_title_kws]
                    )
                )
            elif category == "이벤트":
                event_title_kws = ["이벤트", "선착순", "무료배포", "일시무료", "프로모션", "체험단", "응모"]
                query = query.filter(
                    or_(
                        *[models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords],
                        *[models.Deal.title.ilike(f"%{kw}%") for kw in event_title_kws]
                    )
                )
            elif category == "뷰티":
                beauty_title_kws = ["선크림", "바디워시", "샴푸", "트리트먼트", "클렌징", "토너", "세럼", "로션", "올리브영", "이니스프리", "닥터지", "달바"]
                query = query.filter(
                    or_(
                        *[models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords],
                        *[models.Deal.title.ilike(f"%{kw}%") for kw in beauty_title_kws]
                    )
                )
            elif category == "가전제품":
                appliance_title_kws = [
                    "선풍기", "에어컨", "청소기", "로봇청소기", "냉장고", "세탁기", "건조기", "공기청정기",
                    "제습기", "가습기", "전자레인지", "오븐", "에어프라이어", "밥솥", "식기세척기", "블렌더",
                    "믹서기", "다리미", "안마", "드라이기", "면도기", "전동칫솔", "이어폰", "헤드폰", "스피커",
                    "사운드바", "버즈", "에어팟", "빔프로젝터", "TV", "티비"
                ]
                query = query.filter(
                    or_(
                        *[models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords],
                        *[models.Deal.title.ilike(f"%{kw}%") for kw in appliance_title_kws]
                    )
                )
            else:
                filter_conditions = [models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords]
                query = query.filter(or_(*filter_conditions))
        else:
            from sqlalchemy import and_, not_
            # 전체 탭일 경우 순수 포인트 클릭형 '적립' 및 공지성 '정보 안내'만 제외하고, [슈퍼적립]/[즉시적립]/무료배포 핫딜은 모두 정상 노출
            query = query.filter(
                and_(
                    models.Deal.category != "적립",
                    models.Deal.category != "적립/이벤트",
                    not_(models.Deal.title.like("%일일적립%")),
                    not_(models.Deal.title.like("%정보 안내%")),
                    not_(models.Deal.title.like("%불가%"))
                )
            )

        search_max_price: Optional[int] = None
        if keyword and keyword.strip():
            from sqlalchemy import or_, not_
            positive_tokens = []
            exclude_tokens = []
            for raw_tok in keyword.strip().split():
                if raw_tok.startswith(":") and len(raw_tok) > 1:
                    digits = "".join(ch for ch in raw_tok[1:] if ch.isdigit())
                    if digits:
                        try:
                            val = int(digits)
                            if val > 0:
                                search_max_price = val
                        except ValueError:
                            pass
                elif raw_tok.startswith("-") and len(raw_tok) > 1:
                    exclude_tokens.append(raw_tok[1:].strip())
                else:
                    tok = raw_tok.strip()
                    # 단일 영문 알파벳(예: 'f')이나 단일 한글 자모(예: 'ㄱ')는 무의미한 노이즈 매칭을 유발하므로 차단
                    if len(tok) == 1 and (tok.isascii() or re.match(r'^[ㄱ-ㅎㅏ-ㅣ]$', tok)):
                        return {"deals": []}
                    positive_tokens.append(tok)

            for pt in positive_tokens:
                pt_like = f"%{pt}%"
                if len(pt) == 1:
                    # 1글자 완성형 한글(예: 쌀, 꿀, 배)은 제목(title) 및 기본 상품명, 다중 옵션 데이터에서 검색
                    query = query.filter(
                        or_(
                            models.Deal.title.ilike(pt_like),
                            models.Deal.base_product_name.ilike(pt_like),
                            models.Deal.options_data.ilike(pt_like)
                        )
                    )
                else:
                    query = query.filter(
                        or_(
                            models.Deal.title.ilike(pt_like),
                            models.Deal.search_keywords.ilike(pt_like),
                            models.Deal.base_product_name.ilike(pt_like),
                            models.Deal.options_data.ilike(pt_like)
                        )
                    )
            for et in exclude_tokens:
                et_like = f"%{et}%"
                query = query.filter(not_(models.Deal.title.ilike(et_like)))
            
        if target_platform and target_platform not in ["전체", ""]:
            # DB의 communities.name / display_name 정확 매칭 + 다중 출처 병합(merged_communities) 포함
            REVERSE_COMMUNITY_MAP = {
                "펨코": "fmkorea", "에펨": "fmkorea", "뽐뿌": "ppomppu", "루리웹": "ruliweb",
                "클리앙": "clien", "퀘이사존": "quasarzone", "알리뽐뿌": "ali_ppomppu",
                "빠삭국내": "bbasak_domestic", "빠삭해외": "bbasak_overseas",
                "빠삭육아": "bbasak_parenting", "뽐뿌해외": "ppomppu_overseas"
            }
            platforms = [p.strip() for p in target_platform.split(',') if p.strip()]
            from sqlalchemy import or_
            platform_conditions = []
            target_names = set()
            for p in platforms:
                target_names.add(p)
                eng_name = REVERSE_COMMUNITY_MAP.get(p)
                if eng_name:
                    target_names.add(eng_name)
                    platform_conditions.append(models.Community.name == eng_name)
                    platform_conditions.append(models.Community.display_name == p)
                else:
                    platform_conditions.append(models.Community.name == p)
                    platform_conditions.append(models.Community.display_name == p)
            matched_comms = db.query(models.Community.id).filter(
                or_(
                    models.Community.name.in_(list(target_names)),
                    models.Community.display_name.in_(list(target_names))
                )
            ).all()
            for (cid,) in matched_comms:
                platform_conditions.append(models.Deal.merged_communities.like(f"{cid}::%"))
                platform_conditions.append(models.Deal.merged_communities.like(f"%,{cid}::%"))
                platform_conditions.append(models.Deal.merged_communities == str(cid))
            if platform_conditions:
                query = query.filter(or_(*platform_conditions))
            
        # 넉넉하게 가져와서 파이썬 단에서 클러스터링
        deals = query.order_by(models.Deal.indexed_at.desc()).limit(300).all()

        # [스마트 DSU 백필 가드 (DSU Backfill Guard)]
        # 검색(keyword)이나 플랫폼 필터링 시 병합되어야 할 다른 커뮤니티 딜이 누락되는 현상 방지
        _GENERIC_BROKEN_LINKS = {
            "https://item.gmarket.co.kr/item",
            "https://itempage3.auction.co.kr/detailview.aspx",
            "https://bbs.ruliweb.com/link.php",
            "https://www.11st.co.kr/product/sellerproductdetail.tmall",
            "https://item.gmarket.co.kr/detailview/item.asp",
            "https://www.hyundaihmall.com/front/shitemdetailr.do",
            "https://click.linkprice.com/click.php",
            "https://www.lotteimall.com/goods/viewgoodsdetail.lotte",
        }
        if deals and (keyword or (platform and platform not in ["전체", ""])):
            ecom_links = []
            for d in deals:
                if d.ecommerce_link and len(d.ecommerce_link) > 15:
                    link = d.ecommerce_link.strip()
                    if link.lower() in _GENERIC_BROKEN_LINKS:
                        continue
                    if any(c in link.lower() for c in ["fmkorea.", "ppomppu.", "ruliweb.", "quasarzone.", "clien.", "bbasak."]):
                        continue
                    ecom_links.append(link)
                    if link.endswith('/'):
                        ecom_links.append(link[:-1])
                    else:
                        ecom_links.append(link + '/')
            if ecom_links:
                existing_ids = {d.id for d in deals}
                additional_deals = db.query(models.Deal).join(models.Community).options(
                    joinedload(models.Deal.community),
                    joinedload(models.Deal.deal_reactions)
                ).filter(
                    models.Deal.ecommerce_link.in_(ecom_links),
                    ~models.Deal.id.in_(existing_ids),
                    models.Deal.is_closed == False
                ).limit(100).all()
                if additional_deals:
                    deals.extend(additional_deals)

        COMMUNITY_MAP = {
            "fmkorea": "펨코", "ppomppu": "뽐뿌", "ruliweb": "루리웹",
            "clien": "클리앙", "quasarzone": "퀘이사존", "ali_ppomppu": "알리뽐뿌",
            "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외",
            "bbasak_parenting": "빠삭육아"
        }

        cluster_map = {}
        grouped_result = []
        dsu = build_dsu(deals)

        for deal in deals:
            # 모음전/다중 딜 여부 판별
            is_multi = False
            ecommerce_link = getattr(deal, 'ecommerce_link', None)
            post_link = getattr(deal, 'post_link', None)
            title = getattr(deal, 'title', '') or ''
            ai_summary = getattr(deal, 'ai_summary', '') or ''
            if (ecommerce_link == post_link) or (title and ("," in title or "모음" in title or "선택" in title or "외/" in title or "($86)" in title or "($26)" in title or "($195)" in title or "($324)" in title)) or ("[모음전]" in ai_summary):
                is_multi = True

            cluster_key = dsu.find(deal.id)
            comp_name = getattr(deal.community, 'display_name', None) or COMMUNITY_MAP.get(deal.community.name, deal.community.name)

            image_url = _resolve_best_image_url(deal, BASE_URL)

            # UI를 위해 제목에서 가격 태그 날리기
            clean_title = clean_deal_title(deal.title)
            
            parsed_price_int, resolved_currency = resolve_deal_price_and_currency(deal)
            parsed_shipping_fee = extract_shipping_fee(deal.shipping_fee)
            total_price = parsed_price_int + parsed_shipping_fee if parsed_price_int > 0 else 0

            if cluster_key in cluster_map:
                existing = cluster_map[cluster_key]
                
                if total_price > 0:
                    if existing.get("total_price", 0) == 0 or total_price < existing["total_price"]:
                        # 더 싼 딜이 발견되면 대표 가격, 이미지, 제목 갱신
                        existing["price"] = parsed_price_int
                        existing["shipping_fee"] = deal.shipping_fee or ""
                        existing["total_price"] = total_price
                        existing["currency"] = resolved_currency
                        
                        if image_url or not existing.get("image_url") or "ui-avatars.com" in (existing.get("image_url", "") or ""):
                            existing["image_url"] = image_url or existing.get("image_url")
                            
                        existing["title"] = clean_title
                        new_ecom_url = get_clean_ecommerce_url(deal.ecommerce_link or deal.post_link or "", deal.content_html, deal.id, db, is_multi_item=is_multi)
                        if not new_ecom_url:
                            new_ecom_url = _build_direct_mall_search_url(getattr(deal, 'shop_name', '') or '', deal.title or '', deal.post_link or '', comp_name)
                        if new_ecom_url or not existing.get("ecommerce_url"):
                            existing["ecommerce_url"] = new_ecom_url
                        
                        # 사이트 이름(메인 뱃지)도 더 싼 곳이 앞으로 오도록 조정
                        if comp_name in existing.setdefault("site_names", []):
                            existing["site_names"].remove(comp_name)
                        existing["site_names"].insert(0, comp_name)
                        existing["site_name"] = ", ".join(existing["site_names"])
                    else:
                        # 더 비싼 딜이라도 실제 이미지가 있고, 기존 이미지가 없거나 아바타라면 실제 이미지로 보강
                        if image_url and (not existing.get("image_url") or "ui-avatars.com" in (existing.get("image_url", "") or "")):
                            existing["image_url"] = image_url

                    
                    # 출처 배지 추가 (post_url 중복 방지)
                    if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                        if comp_name not in existing.setdefault("site_names", []):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                        extend_unique_sources(existing["sources"], get_deal_sources(deal, comp_name, parsed_price_int, db))
                else:
                    if existing.get("total_price", 0) == 0:
                        if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                            if comp_name not in existing.setdefault("site_names", []):
                                existing["site_names"].append(comp_name)
                                existing["site_name"] = ", ".join(existing["site_names"])
                            extend_unique_sources(existing["sources"], get_deal_sources(deal, comp_name, parsed_price_int, db))
                            
                # 하나라도 살아있다면 전체 딜을 진행중인 것으로 처리
                existing["is_closed"] = existing.get("is_closed", True) and getattr(deal, 'is_closed', False)
                existing["has_options"] = existing.get("has_options", False) or bool(getattr(deal, "has_options", False))
                
                # 다수의 중복 딜 중 하나라도 핫딜이면 핫딜 상태 병합
                if getattr(deal, 'honey_score', 0) >= 100:
                    existing["honey_score"] = max(existing.get("honey_score", 0), 100)
                if deal.ai_summary and ("🔥" in deal.ai_summary or "인증 핫딜" in deal.ai_summary):
                    if not existing.get("ai_summary"):
                        existing["ai_summary"] = "[커뮤니티 인증 핫딜]"
                    elif "인증 핫딜" not in existing["ai_summary"]:
                        existing["ai_summary"] = "[커뮤니티 인증 핫딜] " + existing["ai_summary"]
            else:

                buy_count = deal.like_count or 0
                save_count = 0
                if hasattr(deal, 'deal_reactions') and deal.deal_reactions:
                    reaction = deal.deal_reactions[0]
                    buy_count += reaction.positive_reactions
                    save_count = reaction.negative_reactions

                site_names_list = [comp_name]
                if getattr(deal, 'merged_communities', None):
                    for cid_str in deal.merged_communities.split(','):
                        try:
                            cid = int(cid_str.split('::')[0].strip())
                            m_name = get_community_display_name_by_id(cid, db)
                            if m_name and m_name not in site_names_list:
                                site_names_list.append(m_name)
                        except:
                            pass

                resolved_ecom_url = get_clean_ecommerce_url(deal.ecommerce_link or deal.post_link or "", deal.content_html, deal.id, db, is_multi_item=is_multi)
                if not resolved_ecom_url:
                    resolved_ecom_url = _build_direct_mall_search_url(getattr(deal, 'shop_name', '') or '', deal.title or '', deal.post_link or '', comp_name)
                is_hot = bool(getattr(deal, 'is_super_hotdeal', False) or (deal.honey_score or 0) >= 100 or (buy_count >= 8))
                final_honey_score = 100 if is_hot else (deal.honey_score or 0)

                deal_dict = {
                    "id": deal.id,
                    "title": clean_title,
                    "price": parsed_price_int,
                    "shipping_fee": deal.shipping_fee or "",
                    "total_price": total_price,
                    "currency": resolved_currency,
                    "image_url": image_url,
                    "ecommerce_url": resolved_ecom_url,
                    "post_url": deal.post_link or "",
                    "site_name": ", ".join(site_names_list),
                    "site_names": site_names_list,
                    "sources": get_deal_sources(deal, comp_name, parsed_price_int, db),
                    "category": deal.category or "기타",
                    "honey_score": final_honey_score,
                    "is_super_hotdeal": is_hot,
                    "ai_summary": _clean_ai_summary(deal.ai_summary or ""),
                    "content_html": _clean_content_text(deal.content_html or "", clean_title),
                    "is_closed": getattr(deal, 'is_closed', False),
                    "created_at": (deal.indexed_at.strftime('%Y-%m-%dT%H:%M:%SZ')) if deal.indexed_at else None,
                    "view_count": deal.view_count or 0,
                    "comment_count": deal.comment_count or 0,
                    "like_count": buy_count,
                    "dislike_count": save_count,
                    "tags": extract_deal_benefit_tags(deal.title, deal.content_html),
                    "has_options": bool(getattr(deal, "has_options", False)),
                }
                cluster_map[cluster_key] = deal_dict
                grouped_result.append(deal_dict)

        if search_max_price is not None and search_max_price > 0:
            grouped_result = [
                g for g in grouped_result
                if 0 < int(g.get("price") or 0) <= search_max_price
            ]

        if sort_mode == "popular":
            grouped_result.sort(
                key=lambda x: (
                    int(x.get("honey_score") or 0),
                    int(x.get("like_count") or 0),
                    int(x.get("comment_count") or 0)
                ),
                reverse=True
            )
        elif sort_mode == "price_asc":
            grouped_result.sort(
                key=lambda x: (
                    0 if int(x.get("price") or 0) > 0 else 1,
                    int(x.get("price") or 0)
                )
            )

        if len(_HOT_DEALS_API_CACHE) > 50:
            _HOT_DEALS_API_CACHE.clear()
        _HOT_DEALS_API_CACHE[cache_key] = {
            "ts": now_ts,
            "grouped": grouped_result,
        }

        # 그룹핑된 결과에서 페이지네이션 수행
        final_result = grouped_result[offset:offset+limit]
        return {"deals": final_result}

    except Exception as e:
        logger.error(f"Error fetching hot deals: {e}", exc_info=True)
        return {"deals": []}

from fastapi import BackgroundTasks
import time as _time

_LAST_SCRAPE_TRIGGER_TS: float = 0.0
_SCRAPE_RUNNING_LOCK: bool = False

def run_scraper_script():
    global _SCRAPE_RUNNING_LOCK
    if _SCRAPE_RUNNING_LOCK:
        return
    _SCRAPE_RUNNING_LOCK = True
    try:
        import subprocess
        import sys
        root_dir = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
        fetch_script = os.path.join(root_dir, 'backend', 'scheduler', 'main.py')
        subprocess.run([sys.executable, fetch_script, "--one-shot"], cwd=root_dir, timeout=240)
        invalidate_feed_api_cache()
    except Exception as e:
        logger.warning(f"Manual scrape background task error: {e}")
    finally:
        _SCRAPE_RUNNING_LOCK = False

@router.post("/force-scrape")
def force_scrape_deals(background_tasks: BackgroundTasks):
    global _LAST_SCRAPE_TRIGGER_TS
    try:
        invalidate_feed_api_cache()
        now = _time.time()
        if _SCRAPE_RUNNING_LOCK or (now - _LAST_SCRAPE_TRIGGER_TS < 60):
            return {"status": "cooldown", "message": "이미 실시간 스크래핑이 진행 중이거나 최근 동기화되었습니다."}
        _LAST_SCRAPE_TRIGGER_TS = now
        background_tasks.add_task(run_scraper_script)
        return {"status": "success", "message": "Manual scraping is now running in the background."}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@router.get("/popular-keywords")
def get_popular_keywords(db: Session = Depends(get_db_session)):
    try:
        noise_keywords = {
            "네이버", "쿠팡", "지마켓", "옥션", "11번가", "알리", "알리익스프레스", "롯데온", "티몬", "위메프",
            "오늘의집", "ssg", "신세계", "이마트", "홈플러스", "카카오", "토스", "큐텐", "아마존",
            "쿠폰", "할인", "특가", "무료", "무배", "무료배송", "넾다", "넾다세일", "메가", "빅스마일",
            "광군제", "선착순", "공동구매", "총정리", "이벤트", "적립", "포인트", "기타", "품절", "종료",
            "pro", "max", "plus", "mini", "ultra", "usb", "pc", "tv", "led", "유튜버", "공구", "링크", "코드",
            "프로", "미니", "플러스", "맥스", "울트라", "자켓", "게이밍", "멤버십", "다운", "헤비", "브랜드",
            "일시", "일시품절", "스토어", "프로모션", "결제", "판매가", "최종가", "기본가", "청구할인", "즉시할인"
        }
        modifier_noise = {
            "국내산", "무선", "유선", "세트", "골라담기", "정품", "새상품", "미개봉", "리퍼", "증정", "포함",
            "전용", "공식", "출시", "다양", "선택", "모음", "라이브", "추가", "최대", "이상", "결제시", "체감",
            "초특가", "할인가", "쿠폰가", "사은품", "무료증정", "한정수량", "당일발송", "빠른배송", "박스", "팩",
            "남성", "여성", "공용", "겨울", "여름", "구분", "기준", "옵션", "대체", "제품별", "상품권",
            "게이밍", "멤버십", "다운", "가정용", "업소용", "대용량", "소용량", "제주", "프리미엄", "슈퍼",
            "울트라", "스페셜", "오리지널", "클래식", "베이직", "스탠다드", "슬림", "와이드", "멀티", "고속", "초고속",
            "일시", "일시품절", "스토어쿠폰", "결제할인", "공구코드", "슈퍼적립", "브랜드쿠폰", "전원증정"
        }
        stop_words = {"특가", "할인", "무료배송", "무배", "체감가", "역대급", "최저가", "핫딜", "쿠폰", "카드", "적립", "오늘마켓", "추천", "일시"}

        # 최근 핫딜 제목 분석 및 실제 DB 매칭 검증용 목록 로드
        deals_rows = db.query(models.Deal.title, models.Deal.indexed_at).filter(
            models.Deal.is_closed == False
        ).order_by(models.Deal.indexed_at.desc()).limit(1000).all()

        def count_matching_deals(kw_str: str) -> tuple[int, int]:
            tokens = [t.lower() for t in kw_str.split() if t]
            if not tokens:
                return 0, 0
            d_cnt = 0
            r_cnt = 0
            now_u = datetime.utcnow()
            r_cut = now_u - timedelta(hours=12)
            for t_val, idx_val in deals_rows:
                if not t_val:
                    continue
                t_low = t_val.lower()
                if all(tok in t_low for tok in tokens):
                    d_cnt += 1
                    if idx_val and idx_val >= r_cut:
                        r_cnt += 1
            return d_cnt, r_cnt

        filtered_keywords = []
        # 1. 펨코 실시간 급상승 검색어 JSON 확인 (실제 DB에 매칭되는 핫딜이 최소 1건 이상 존재하는 키워드만 채택!)
        data_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data")
        file_path = os.path.join(data_dir, "fmkorea_trending.json")
        if os.path.exists(file_path):
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                for kw in data.get("keywords", []):
                    kw_clean = kw.strip()
                    if kw_clean == "제로":
                        kw_clean = "제로콜라"
                    elif kw_clean.upper() == "메가MGC커피":
                        kw_clean = "메가커피"
                    elif kw_clean == "폴드":
                        kw_clean = "갤럭시 폴드"
                    is_valid_len = len(kw_clean) >= 2 or (len(kw_clean) == 1 and '가' <= kw_clean <= '힣')
                    if is_valid_len and kw_clean.lower() not in noise_keywords and kw_clean.lower() not in modifier_noise and not kw_clean.isdigit():
                        d_cnt, _ = count_matching_deals(kw_clean)
                        if d_cnt > 0 and kw_clean not in filtered_keywords:
                            filtered_keywords.append(kw_clean)

        # 2. 최근 핫딜 제목 분석 및 최근 12시간 vs 이전 구간 급상승 집계
        words_count: dict[str, int] = {}
        recent_count: dict[str, int] = {}
        now_utc = datetime.utcnow()
        recent_cutoff = now_utc - timedelta(hours=12)

        for title_val, indexed_at_val in deals_rows:
            if not title_val:
                continue
            clean_title = re.sub(r'\[.*?\]|\(.*?\)', '', title_val)
            words = re.findall(r'[가-힣a-zA-Z0-9]{2,}', clean_title)
            is_recent = bool(indexed_at_val and indexed_at_val >= recent_cutoff)
            for w in words:
                if w not in stop_words and not w.isdigit():
                    w_norm = "제로콜라" if w == "제로" else ("메가커피" if w.upper() == "메가MGC커피" else w)
                    words_count[w_norm] = words_count.get(w_norm, 0) + 1
                    if is_recent:
                        recent_count[w_norm] = recent_count.get(w_norm, 0) + 1

        sorted_words = sorted(words_count.items(), key=lambda x: (recent_count.get(x[0], 0) * 2 + x[1]), reverse=True)
        top_keywords = list(filtered_keywords)
        for w_norm, _ in sorted_words:
            w_low = w_norm.lower()
            if w_low in noise_keywords or w_low in modifier_noise:
                continue
            if re.match(r'^\d+[a-zA-Z가-힣]*$', w_norm) or re.match(r'^[a-zA-Z가-힣]\d+$', w_norm):
                continue
            if len(w_norm) >= 2 and w_norm not in top_keywords:
                d_cnt, _ = count_matching_deals(w_norm)
                if d_cnt > 0:
                    top_keywords.append(w_norm)
            if len(top_keywords) >= 10:
                break

        base_keywords = ["아이폰", "갤럭시", "로봇청소기", "다이슨", "노트북", "에어팟", "갈비탕", "제로콜라", "모니터", "생수"]
        if len(top_keywords) < 10:
            for bk in base_keywords:
                if bk not in top_keywords:
                    d_cnt, _ = count_matching_deals(bk)
                    if d_cnt > 0:
                        top_keywords.append(bk)
                if len(top_keywords) >= 10:
                    break

        final_keywords = top_keywords[:10]
        rankings = []
        for idx, kw in enumerate(final_keywords):
            deal_cnt, recent_cnt = count_matching_deals(kw)
            if deal_cnt == 0:
                deal_cnt = words_count.get(kw, 1)

            if idx == 0 or (idx < 3 and recent_cnt >= 3):
                status = "HOT"
            elif idx < 5 or recent_cnt >= 2:
                status = "UP"
            elif idx in (6, 8) and recent_cnt >= 1:
                status = "NEW"
            else:
                status = "SAME"

            rankings.append({
                "rank": idx + 1,
                "keyword": kw,
                "deal_count": max(1, deal_cnt),
                "recent_count": recent_cnt,
                "status": status
            })

        return {
            "keywords": final_keywords,
            "rankings": rankings,
            "updated_at": datetime.now(timezone(timedelta(hours=9))).strftime("%H:%M 기준")
        }
    except Exception as e:
        logger.error(f"Error fetching keywords: {e}", exc_info=True)
        fallback = ["아이폰", "갤럭시", "다이슨", "제로콜라", "노트북", "로봇청소기", "모니터", "에어팟", "스탠리", "보조배터리"]
        return {
            "keywords": fallback,
            "rankings": [
                {"rank": i + 1, "keyword": k, "deal_count": 5, "recent_count": 1, "status": "HOT" if i == 0 else ("UP" if i < 3 else "SAME")}
                for i, k in enumerate(fallback)
            ],
            "updated_at": "방금 전"
        }

@router.get("/deals/{deal_id}")
def get_deal(deal_id: int, db: Session = Depends(get_db_session)):
    from sqlalchemy.orm import joinedload
    deal = db.query(models.Deal).options(
        joinedload(models.Deal.community),
        joinedload(models.Deal.deal_reactions)
    ).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    COMMUNITY_MAP = {
        "fmkorea": "펨코", "ppomppu": "뽐뿌", "ruliweb": "루리웹",
        "clien": "클리앙", "quasarzone": "퀘이사존", "ali_ppomppu": "알리뽐뿌",
        "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외",
        "bbasak_parenting": "빠삭육아"
    }
    
    # 현재 딜과 cluster_key가 동일한 딜들을 모아서 출처(sources) 병합 (최신 250건 범위로 제한하여 응답속도 최적화)
    if deal.indexed_at:
        end_time = deal.indexed_at + timedelta(hours=12)
        time_limit = deal.indexed_at - timedelta(days=3)
    else:
        end_time = datetime.utcnow()
        time_limit = end_time - timedelta(days=3)
        
    similar_deals = db.query(models.Deal).join(models.Community).options(
        joinedload(models.Deal.community),
        joinedload(models.Deal.deal_reactions)
    ).filter(
        models.Deal.indexed_at >= time_limit,
        models.Deal.indexed_at <= end_time
    ).order_by(models.Deal.indexed_at.desc()).limit(450).all()
    
    if not any(d.id == deal.id for d in similar_deals):
        similar_deals.append(deal)
        
    dsu = build_dsu(similar_deals, time_window_days=3)
    cluster_key = dsu.find(deal.id)
    
    site_names = []
    sources = []
    
    best_deal = deal
    min_total_price = float('inf')
    
    for d in similar_deals:
        if dsu.find(d.id) == cluster_key:
            # 현재 딜이 진행 중(is_closed=False)일 때, 이미 종료된 과거 딜(is_closed=True)은
            # 시세 차트(히스토리)에만 남기고 실시간 판매처 비교(sources) 및 현재가 계산에서는 제외
            if d.id != deal.id and getattr(d, 'is_closed', False) and not getattr(deal, 'is_closed', False):
                continue

            c_name = d.community.display_name if d.community and hasattr(d.community, 'display_name') and d.community.display_name else (COMMUNITY_MAP.get(d.community.name, d.community.name) if d.community else "Unknown")
            
            p_val = extract_price(d.price) if isinstance(d.price, str) else (d.price or 0)
            s_val = extract_shipping_fee(d.shipping_fee)
            tot_val = p_val + s_val if p_val > 0 else 0
            
            if tot_val > 0 and tot_val < min_total_price:
                min_total_price = tot_val
                best_deal = d
            elif tot_val == 0 and min_total_price == float('inf'):
                best_deal = d
            
            if not any(s.get('post_url') == (d.post_link or "") for s in sources):
                if c_name not in site_names:
                    site_names.append(c_name)
                for new_src in get_deal_sources(d, c_name, p_val, db):
                    if not any(
                        s.get("site_name") == new_src.get("site_name") and s.get("price") == new_src.get("price")
                        for s in sources
                    ):
                        sources.append(new_src)
                
    if not sources:
        c_name = deal.community.display_name if deal.community and hasattr(deal.community, 'display_name') and deal.community.display_name else (COMMUNITY_MAP.get(deal.community.name, deal.community.name) if deal.community else "Unknown")
        site_names = [c_name]
        p_val = extract_price(deal.price) if isinstance(deal.price, str) else (deal.price or 0)
        sources = get_deal_sources(deal, c_name, p_val, db)
        best_deal = deal

    community_name = ", ".join(site_names)
    
    # 클러스터 전체가 종료되었는지 확인 (하나라도 열려있으면 False)
    is_closed = True
    for d in similar_deals:
        if dsu.find(d.id) == cluster_key:
            if not getattr(d, 'is_closed', False):
                is_closed = False
                break
    
    # 최저가 계산
    min_price = min([s["price"] for s in sources if s.get("price", 0) > 0], default=0)
    original_price = extract_price(deal.price) if isinstance(deal.price, str) else (deal.price or 0)
    final_price_int = min_price if min_price > 0 and min_price < original_price else original_price
    
    curr = getattr(best_deal, 'currency', 'KRW') or 'KRW'
                     
    detail_ecom_url = get_clean_ecommerce_url(
        best_deal.ecommerce_link or best_deal.post_link, 
        getattr(best_deal, "content_html", "") or "", 
        best_deal.id, 
        db, 
        is_multi_item=(
            (getattr(best_deal, 'ecommerce_link', None) == getattr(best_deal, 'post_link', None)) or 
            (getattr(best_deal, 'title', '') and ("," in best_deal.title or "모음" in best_deal.title or "선택" in best_deal.title or "외/" in best_deal.title or "($86)" in best_deal.title or "($26)" in best_deal.title)) or 
            ("[모음전]" in (getattr(best_deal, 'ai_summary', '') or ''))
        )
    )
    if not detail_ecom_url:
        detail_ecom_url = _build_direct_mall_search_url(getattr(best_deal, "shop_name", "") or "", getattr(best_deal, "title", "") or deal.title or "", getattr(best_deal, "post_link", "") or "", community_name)

    return {
        "id": deal.id,
        "title": deal.title, # 본래 사용자가 클릭한 딜의 제목 유지
        "price": final_price_int,
        "shipping_fee": getattr(best_deal, "shipping_fee", None),
        "post_url": best_deal.post_link,
        "ecommerce_url": detail_ecom_url,
        "image_url": _resolve_best_image_url(best_deal, BASE_URL) or _resolve_best_image_url(deal, BASE_URL),
        "site_name": community_name,
        "site_names": site_names,
        "currency": curr,
        "sources": sources,
        "category": getattr(best_deal, "category", "기타") or "기타",
        "author": getattr(best_deal, "author", "익명") or "익명",
        "views": getattr(best_deal, "view_count", 0) or 0,
        "recommendations": getattr(best_deal, "like_count", 0) or 0,
        "comments": getattr(best_deal, "comment_count", 0) or 0,
        "indexed_at": deal.indexed_at.isoformat() if deal.indexed_at else None,
        "is_closed": is_closed,
        "score": getattr(best_deal, "honey_score", 0) or getattr(deal, "honey_score", 0),
        "ai_summary": _clean_ai_summary(getattr(best_deal, "ai_summary", "") or getattr(deal, "ai_summary", "") or ""),
        "content_html": _clean_content_text(getattr(best_deal, "content_html", "") or getattr(deal, "content_html", "") or "", deal.title or ""),
        "has_options": bool(getattr(best_deal, "has_options", False) or getattr(deal, "has_options", False)),
    }

GENERIC_CATEGORY_STOPWORDS = {
    # 가전 / 디지털
    "청소기", "로봇청소기", "로청", "무선청소기", "물걸레", "스틱청소기", "창문로봇청소기",
    "모니터", "포터블모니터", "휴대용모니터", "tv", "티비", "텔레비전", "oled", "qled",
    "노트북", "랩탑", "컴퓨터", "데스크탑", "본체", "완본체", "태블릿", "패드", "아이패드", "갤럭시탭",
    "스마트폰", "핸드폰", "휴대폰", "자급제", "이어폰", "헤드폰", "헤드셋", "스피커", "사운드바",
    "마우스", "키보드", "기계식키보드", "충전기", "고속충전기", "보조배터리", "보배", "케이블", "거치대",
    # 생활 / 계절
    "선풍기", "서큘레이터", "가습기", "제습기", "에어컨", "냉장고", "김치냉장고", "세탁기", "건조기",
    "공기청정기", "공청기", "밥솥", "에어프라이어", "전자레인지", "식기세척기", "식세기", "인덕션",
    "의자", "게이밍의자", "책상", "모션데스크", "침대", "매트리스", "토퍼", "전기장판", "온수매트", "카본매트",
    # 식품 / 패션 / 뷰티
    "영양제", "비타민", "유산균", "오메가3", "밀크씨슬", "루테인", "단백질", "프로틴",
    "생수", "음료", "탄산수", "사이다", "콜라", "커피", "원두", "라면", "햇반", "쌀",
    "신발", "운동화", "슬리퍼", "티셔츠", "바지", "패딩", "코트", "자켓"
}

@router.get("/deals/{deal_id}/history")
def get_deal_history(
    deal_id: int, 
    period: str = "7d", 
    option_name: Optional[str] = Query(None, description="선택된 멀티옵션 상품명"),
    query: Optional[str] = Query(None, description="선택된 옵션/검색어 쿼리 (안드로이드 호환)"),
    currency: Optional[str] = Query(None, description="선택된 통화 (KRW, USD)"),
    db: Session = Depends(get_db_session)
):
    opt_query = option_name or query
    auto_fallback = (period == "7d_auto")
    effective_period = "7d" if auto_fallback else period
    res = _compute_deal_history(deal_id, effective_period, db, option_name=opt_query, target_currency=currency)
    if auto_fallback and len(res) <= 1:
        res_3m = _compute_deal_history(deal_id, "3m", db, option_name=opt_query, target_currency=currency)
        if len(res_3m) >= 2:
            return res_3m
    return res

def _compute_deal_history(
    deal_id: int, 
    period: str = "7d", 
    db: Session = None,
    option_name: Optional[str] = None,
    target_currency: Optional[str] = None
):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")

    target_opt_name = (option_name or "").strip()
    target_opt_price = 0
    effective_currency = target_currency or getattr(deal, "currency", "KRW")

    if target_opt_name and deal.options_data:
        try:
            opts = json.loads(deal.options_data) if isinstance(deal.options_data, str) else deal.options_data
            if isinstance(opts, list):
                for opt in opts:
                    o_name = (opt.get("name") or "").strip()
                    if o_name and (o_name == target_opt_name or target_opt_name in o_name or o_name in target_opt_name):
                        target_opt_price = int(opt.get("price", 0) or 0)
                        if not target_currency and opt.get("currency"):
                            effective_currency = opt.get("currency")
                        break
        except Exception as e:
            logger.debug(f"Error parsing options_data in history: {e}")

    now_kst = datetime.utcnow() + timedelta(hours=10)
    ref_base_time = deal.indexed_at if deal.indexed_at else now_kst
    end_time = max(now_kst, ref_base_time)

    days_map = {
        "7d": 7,
        "1m": 30,
        "3m": 90,
        "6m": 180,
        "1y": 365
    }
    days = days_map.get(period, 7)
    time_limit = ref_base_time - timedelta(days=days)

    unit_or_generic_re = re.compile(
        r'^(?:\d+(?:\.\d+)?(?:kg|g|ml|l|gb|tb|mb|w|v|a|hz|mah|ah|gsm|in1|인치|inch|개입|개|입|캔|병|팩|봉|포|매|판|박스|세트|롤|정|캡슐|구|미|쪽|호|p|ea|pcs|년|개월|일|시간|원|만원|천원)?|국내산|국산|해외|정품|무료|무배|특가|할인|증정|사은품|단품|혼합|선택|외|모음|한정|출시|신제품|신모델|리뉴얼|끌올)$',
        re.IGNORECASE
    )

    def get_core_product_tokens(d_obj) -> set[str]:
        if isinstance(d_obj, str):
            norm_str = d_obj
        else:
            norm_str = get_normalized_base_name(d_obj) or ""
        tokens = set()
        for w in norm_str.split():
            w_low = w.strip().lower()
            if len(w_low) < 2 or unit_or_generic_re.match(w_low) or w_low in GENERIC_CATEGORY_STOPWORDS:
                continue
            tokens.add(w_low)
        return tokens

    if target_opt_name:
        curr_norm_name = target_opt_name
        curr_all_words = set(re.findall(r'[가-힣a-zA-Z0-9]+', target_opt_name))
        curr_true_codes = extract_true_model_codes(curr_all_words)
        curr_model = ""
        core_tokens = get_core_product_tokens(target_opt_name)
    else:
        curr_model = (getattr(deal, "model_code", None) or "").strip()
        curr_norm_name = get_normalized_base_name(deal) or ""
        curr_all_words = set(curr_norm_name.split()) if curr_norm_name else set()
        curr_true_codes = extract_true_model_codes(curr_all_words)
        if curr_model and len(curr_model) >= 4:
            curr_true_codes.add(curr_model.lower().replace("-", ""))
        core_tokens = get_core_product_tokens(deal)

    n_url = get_normalized_url(deal)
    target_price_val = target_opt_price if target_opt_price > 0 else extract_price(getattr(deal, "price", 0))

    from sqlalchemy import or_
    search_clauses = []
    if curr_model and len(curr_model) >= 3:
        search_clauses.append(models.Deal.model_code.ilike(f"%{curr_model}%"))
        search_clauses.append(models.Deal.title.ilike(f"%{curr_model}%"))
    for code_tok in list(curr_true_codes)[:2]:
        search_clauses.append(models.Deal.title.ilike(f"%{code_tok}%"))

    sorted_core = sorted(core_tokens, key=lambda x: len(x), reverse=True)
    for cw in sorted_core[:3]:
        if len(cw) >= 2 and cw not in GENERIC_CATEGORY_STOPWORDS:
            search_clauses.append(models.Deal.title.ilike(f"%{cw}%"))

    query = db.query(models.Deal).filter(
        models.Deal.indexed_at >= time_limit,
        models.Deal.indexed_at <= end_time
    )
    if effective_currency:
        query = query.filter(models.Deal.currency == effective_currency)

    # 옵션명이 지정되었으나 모델코드나 식별 토큰이 부족하면 타 딜 교차 매칭을 제한하여 노이즈 방어
    if target_opt_name and not curr_true_codes and len(core_tokens) <= 1:
        candidate_deals = [deal]
    elif search_clauses:
        candidate_deals = query.filter(or_(*search_clauses)).order_by(models.Deal.indexed_at.asc()).limit(450).all()
    else:
        candidate_deals = [deal]

    target_is_collection = bool(re.search(r'(?:외\s*\d+종|모음전|골라담기)', deal.title or ""))

    matched_deals = []
    for d in candidate_deals:
        if d.id == deal.id:
            matched_deals.append(d)
            continue

        if effective_currency and getattr(d, "currency", "KRW") != effective_currency:
            continue

        d_price = extract_price(getattr(d, "price", 0))
        if d_price <= 0:
            continue

        if not target_is_collection and re.search(r'(?:외\s*\d+종|모음전|골라담기)', d.title or ""):
            continue

        if target_price_val > 0:
            if d_price < target_price_val * 0.40 or d_price > target_price_val * 2.20:
                continue

        d_norm_name = get_normalized_base_name(d) or ""
        d_all_words = set(d_norm_name.split()) if d_norm_name else set()
        d_core_tokens = get_core_product_tokens(d)

        if has_quantity_conflict(curr_all_words, d_all_words):
            continue
        if has_model_conflict(curr_all_words, d_all_words):
            continue
        if has_brand_or_keyword_conflict(curr_all_words, d_all_words):
            continue

        # 옵션 검색 시 고유 식별자(모델코드 또는 2개 이상의 핵심 토큰) 일치 강제
        if target_opt_name:
            d_true_codes = extract_true_model_codes(d_all_words)
            d_model = (getattr(d, "model_code", None) or "").strip()
            if d_model and len(d_model) >= 4:
                d_true_codes.add(d_model.lower().replace("-", ""))
            has_model_match = bool(curr_true_codes and d_true_codes and curr_true_codes.intersection(d_true_codes))
            has_core_match = len(core_tokens.intersection(d_core_tokens)) >= 2
            if has_model_match or has_core_match:
                matched_deals.append(d)
            continue

        # 1) 쇼핑몰 고유 상품 키(Canonical URL Key) 일치
        d_url = get_normalized_url(d)
        if n_url and d_url and n_url == d_url and not any(x in n_url for x in ["ppomppu", "fmkorea", "ruliweb", "quasarzone", "clien", "bbasak"]):
            if len(core_tokens.intersection(d_core_tokens)) >= 1:
                matched_deals.append(d)
                continue

        # 2) 고유 제조사 모델코드(예: WIZ040, LS32FM701 등) 일치
        d_true_codes = extract_true_model_codes(d_all_words)
        d_model = (getattr(d, "model_code", None) or "").strip()
        if d_model and len(d_model) >= 4:
            d_true_codes.add(d_model.lower().replace("-", ""))
        if curr_true_codes and d_true_codes and curr_true_codes.intersection(d_true_codes):
            if len(core_tokens.intersection(d_core_tokens)) >= 2:
                matched_deals.append(d)
                continue

        # 3) 순수 상품명 핵심 토큰(단위/수량 제외) 유사도 검사
        if not core_tokens or not d_core_tokens:
            continue

        core_inter = core_tokens.intersection(d_core_tokens)
        core_union = core_tokens.union(d_core_tokens)
        core_jaccard = len(core_inter) / len(core_union) if core_union else 0
        core_min_len = min(len(core_tokens), len(d_core_tokens))
        core_subset = len(core_inter) / core_min_len if core_min_len else 0

        if (core_min_len == 1 and core_jaccard == 1.0) or (
            len(core_inter) >= 2 and (core_jaccard >= 0.55 or (core_subset >= 0.80 and len(core_inter) >= 2))
        ):
            matched_deals.append(d)

    matched_deal_ids = [d.id for d in matched_deals]
    if deal.id not in matched_deal_ids:
        matched_deals.append(deal)
        matched_deal_ids.append(deal.id)

    def normalize_kst_dt(dt_val):
        if not dt_val:
            return now_kst
        # UTC 자정(15:00:00)으로 저장된 날짜형 타임스탬프를 KST 날짜 기준으로 보정
        if dt_val.hour == 15 and dt_val.minute == 0 and dt_val.second == 0:
            return dt_val + timedelta(hours=9)
        return dt_val

    deal_price_map = {d.id: extract_price(getattr(d, "price", 0)) for d in matched_deals}

    # 2. 커뮤니티 핫딜 가격 히스토리(PriceHistory) 조회
    history = db.query(models.PriceHistory).filter(
        models.PriceHistory.deal_id.in_(matched_deal_ids),
        models.PriceHistory.checked_at >= time_limit,
        models.PriceHistory.checked_at <= end_time
    ).order_by(models.PriceHistory.checked_at.asc()).all()

    time_format = "%m.%d %H:%M" if days <= 7 else "%y.%m.%d"

    community_points = []
    comm_map = {}

    # 2-1. PriceHistory 데이터 반영
    for h in history:
        try:
            p_val = int(re.sub(r'[^\d]', '', str(h.price)))
            if p_val > 0:
                if target_price_val > 0 and (p_val < target_price_val * 0.40 or p_val > target_price_val * 2.20):
                    continue
                if p_val == deal_price_map.get(h.deal_id, 0) and not (target_opt_name and h.deal_id == deal.id and p_val == target_opt_price):
                    if not (target_opt_price > 0 and p_val == target_opt_price):
                        continue
                dt = normalize_kst_dt(h.checked_at)
                time_key = dt.strftime(time_format)
                if time_key not in comm_map or p_val < comm_map[time_key]["price"]:
                    comm_map[time_key] = {"price": p_val, "dt": dt, "source": "community"}
        except Exception:
            pass

    # 2-2. 매칭된 각 Deal의 수집 시점 가격 반영
    for d in matched_deals:
        try:
            is_current = (d.id == deal.id)
            if is_current and target_opt_price > 0:
                p_val = target_opt_price
            else:
                p_val = deal_price_map.get(d.id, 0)

            if p_val > 0:
                if not is_current and target_price_val > 0:
                    if p_val < target_price_val * 0.40 or p_val > target_price_val * 2.20:
                        continue
                dt = normalize_kst_dt(d.indexed_at)
                base_time_key = dt.strftime(time_format)

                if is_current:
                    if base_time_key in comm_map and comm_map[base_time_key]["price"] == p_val:
                        comm_map[base_time_key]["dt"] = dt
                    elif base_time_key not in comm_map:
                        comm_map[base_time_key] = {"price": p_val, "dt": dt, "source": "community"}
                    else:
                        alt_label = dt.strftime("%m.%d %H:%M") if dt.strftime("%H:%M") != "00:00" else base_time_key
                        comm_map[base_time_key + "_current"] = {"price": p_val, "dt": dt, "source": "community", "clean_label": alt_label}
                else:
                    if base_time_key not in comm_map or p_val < comm_map[base_time_key]["price"]:
                        comm_map[base_time_key] = {"price": p_val, "dt": dt, "source": "community"}
        except Exception:
            pass

    # 2-3. 실제 매칭된 동일 상품 딜이 2건 이상인데 같은 날짜에 올라와 날짜 키(comm_map)가 1개로 뭉개진 경우:
    if len(comm_map) == 1 and len(matched_deals) >= 2:
        distinct_prices = {deal_price_map.get(d.id, 0) for d in matched_deals if deal_price_map.get(d.id, 0) > 0}
        distinct_times = {normalize_kst_dt(d.indexed_at).strftime("%m.%d %H:%M") for d in matched_deals}
        if len(distinct_prices) >= 2 or (len(distinct_times) >= 2 and not any(t.endswith("00:00") for t in distinct_times)):
            comm_map.clear()
            sorted_matched = sorted(matched_deals, key=lambda x: (normalize_kst_dt(x.indexed_at), x.id))
            seen_label_price = set()
            for idx, d in enumerate(sorted_matched):
                p_val = target_opt_price if (d.id == deal.id and target_opt_price > 0) else deal_price_map.get(d.id, 0)
                if p_val > 0:
                    dt = normalize_kst_dt(d.indexed_at)
                    label = dt.strftime("%m.%d %H:%M") if dt.strftime("%H:%M") != "00:00" else dt.strftime("%y.%m.%d")
                    if (label, p_val) in seen_label_price:
                        continue
                    seen_label_price.add((label, p_val))
                    comm_map[f"{label}#{idx}"] = {"price": p_val, "dt": dt, "source": "community", "clean_label": label}

    for k, v in comm_map.items():
        clean_recorded_at = v.get("clean_label") or k.replace(" 00:00", "").replace("_current", "").split("#")[0]
        community_points.append({
            "price": v["price"],
            "originalPrice": None,
            "discountRate": None,
            "recordedAt": clean_recorded_at,
            "source": "community",
            "dt": v["dt"]
        })

    # 3. 네이버 쇼핑 최저가 히스토리(NaverPriceHistory) 조회 및 병합
    naver_points = []
    if not target_opt_name:
        target_model_code = deal.model_code if deal.model_code else (deal.base_product_name if deal.base_product_name else None)
        if deal.brand and target_model_code:
            naver_history = db.query(models.NaverPriceHistory).filter(
                models.NaverPriceHistory.brand == deal.brand,
                models.NaverPriceHistory.model_code == target_model_code,
                models.NaverPriceHistory.checked_at >= time_limit,
                models.NaverPriceHistory.checked_at <= end_time
            ).order_by(models.NaverPriceHistory.checked_at.asc()).all()

            naver_map = {}
            for nh in naver_history:
                try:
                    dt = normalize_kst_dt(nh.checked_at)
                    time_key = dt.strftime(time_format)
                    p_val = int(nh.price)
                    if time_key not in naver_map or p_val < naver_map[time_key]["price"]:
                        naver_map[time_key] = {"price": p_val, "dt": dt, "source": "naver"}
                except Exception:
                    pass

            for k, v in naver_map.items():
                naver_points.append({
                    "price": v["price"],
                    "originalPrice": None,
                    "discountRate": None,
                    "recordedAt": k.replace(" 00:00", ""),
                    "source": "naver",
                    "dt": v["dt"]
                })

    # 4. 두 최저가 스트림을 통합하여 날짜(dt) 순으로 오름차순 정렬
    all_points = community_points + naver_points
    all_points.sort(key=lambda x: x["dt"] or datetime.min)

    # 5. 최종 반환 형태 정제 (dt 제외)
    result = []
    for p in all_points:
        result.append({
            "price": p["price"],
            "originalPrice": p["originalPrice"],
            "discountRate": p["discountRate"],
            "recordedAt": p["recordedAt"],
            "source": p["source"]
        })

    return result

# --- 소비 참견 거지방 API ---

class CommentCreate(BaseModel):
    user_id: str
    nickname: str = "익명의찰떡이"
    content: str

class VoteCreate(BaseModel):
    user_id: str
    vote_type: str # "buy" or "save"

@router.get("/deals/{deal_id}/comments")
def get_deal_comments(deal_id: int, db: Session = Depends(get_db_session)):
    comments = db.query(models.DealComment).filter(
        models.DealComment.deal_id == deal_id,
        models.DealComment.is_deleted == False
    ).order_by(models.DealComment.created_at.desc()).all()
    
    return [
        {
            "id": c.id,
            "userId": c.user_id,
            "nickname": c.nickname,
            "content": c.content,
            "createdAt": c.created_at.isoformat() if c.created_at else None
        }
        for c in comments
    ]

@router.post("/deals/{deal_id}/comments")
def add_deal_comment(deal_id: int, comment: CommentCreate, db: Session = Depends(get_db_session)):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    new_comment = models.DealComment(
        deal_id=deal_id,
        user_id=comment.user_id,
        nickname=comment.nickname,
        content=comment.content
    )
    db.add(new_comment)
    
    # 딜 반응 업데이트
    reaction = db.query(models.DealReaction).filter(models.DealReaction.deal_id == deal_id).first()
    if not reaction:
        reaction = models.DealReaction(deal_id=deal_id, comment_count=1)
        db.add(reaction)
    else:
        reaction.comment_count += 1
        
    deal.comment_count += 1
    
    db.commit()
    db.refresh(new_comment)
    
    return {
        "id": new_comment.id,
        "userId": new_comment.user_id,
        "nickname": new_comment.nickname,
        "content": new_comment.content,
        "createdAt": new_comment.created_at.isoformat() if new_comment.created_at else None
    }

@router.get("/deals/{deal_id}/votes")
def get_deal_votes(deal_id: int, user_id: str = None, db: Session = Depends(get_db_session)):
    votes = db.query(models.DealVote).filter(models.DealVote.deal_id == deal_id).all()
    
    buy_count = sum(1 for v in votes if v.vote_type == "buy")
    save_count = sum(1 for v in votes if v.vote_type == "save")
    
    user_vote = None
    if user_id:
        uv = next((v for v in votes if v.user_id == user_id), None)
        if uv:
            user_vote = uv.vote_type
            
    return {
        "buyCount": buy_count,
        "saveCount": save_count,
        "userVote": user_vote
    }

@router.post("/deals/{deal_id}/votes")
def add_deal_vote(deal_id: int, vote: VoteCreate, db: Session = Depends(get_db_session)):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    if vote.vote_type not in ["buy", "save"]:
        raise HTTPException(status_code=400, detail="Invalid vote_type. Must be 'buy' or 'save'")
        
    existing_vote = db.query(models.DealVote).filter(
        models.DealVote.deal_id == deal_id,
        models.DealVote.user_id == vote.user_id
    ).first()
    
    if existing_vote:
        # 기존 투표 변경 또는 취소
        if existing_vote.vote_type == vote.vote_type:
            # 같은 투표 한 번 더 누르면 취소
            db.delete(existing_vote)
        else:
            # 다른 투표로 변경
            existing_vote.vote_type = vote.vote_type
    else:
        # 새로 투표
        new_vote = models.DealVote(
            deal_id=deal_id,
            user_id=vote.user_id,
            vote_type=vote.vote_type
        )
        db.add(new_vote)
        
    db.commit()
    
    # 갱신된 투표 수 계산
    all_votes = db.query(models.DealVote).filter(models.DealVote.deal_id == deal_id).all()
    buy_count = sum(1 for v in all_votes if v.vote_type == "buy")
    save_count = sum(1 for v in all_votes if v.vote_type == "save")
    
    # 딜 반응 업데이트
    reaction = db.query(models.DealReaction).filter(models.DealReaction.deal_id == deal_id).first()
    if not reaction:
        reaction = models.DealReaction(
            deal_id=deal_id, 
            positive_reactions=buy_count,
            negative_reactions=save_count
        )
        db.add(reaction)
    else:
        reaction.positive_reactions = buy_count
        reaction.negative_reactions = save_count
        
    db.commit()
    
    uv = db.query(models.DealVote).filter(
        models.DealVote.deal_id == deal_id,
        models.DealVote.user_id == vote.user_id
    ).first()
    
    return {
        "buyCount": buy_count,
        "saveCount": save_count,
        "userVote": uv.vote_type if uv else None
    }

# --- UGC Community Features (역경매 & 오프라인 핫딜) ---

class CommunityPostCreate(BaseModel):
    post_type: str
    user_id: str
    nickname: str
    title: str
    content: str
    target_price: Optional[int] = None
    bounty_points: Optional[int] = 0
    like_count: Optional[int] = 0
    location: Optional[str] = None

class CommunityPostCommentCreate(BaseModel):
    user_id: str
    nickname: str
    content: str
    deal_url: Optional[str] = None
    parent_id: Optional[int] = None



@router.post("/posts")
def create_community_post(post: CommunityPostCreate, db: Session = Depends(get_db_session)):
    new_post = models.CommunityPost(
        post_type=post.post_type,
        user_id=post.user_id,
        nickname=post.nickname,
        title=post.title,
        content=post.content,
        target_price=post.target_price,
        bounty_points=post.bounty_points,
        like_count=post.like_count if post.like_count is not None else 0,
        location=post.location
    )
    db.add(new_post)
    db.commit()
    db.refresh(new_post)
    return new_post

@router.put("/posts/{post_id}")
def update_community_post(
    post_id: int, 
    post_data: CommunityPostCreate, 
    db: Session = Depends(get_db_session)
):
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")
        
    if post.user_id != post_data.user_id:
        raise HTTPException(status_code=403, detail="Only the post creator can edit")
        
    post.title = post_data.title
    post.content = post_data.content
    post.target_price = post_data.target_price
    post.bounty_points = post_data.bounty_points
    if post_data.like_count is not None:
        post.like_count = post_data.like_count
    post.location = post_data.location
    post.post_type = post_data.post_type
    
    db.commit()
    db.refresh(post)
    return post


@router.get("/posts")
def get_community_posts(
    post_type: Optional[str] = None,
    skip: int = 0,
    limit: int = 20,
    db: Session = Depends(get_db_session)
):
    query = db.query(models.CommunityPost)
    if post_type:
        query = query.filter(models.CommunityPost.post_type == post_type)
        
    posts = query.order_by(models.CommunityPost.created_at.desc()).offset(skip).limit(limit).all()
    
    result = []
    for p in posts:
        result.append({
            "id": p.id,
            "post_type": p.post_type,
            "user_id": p.user_id,
            "nickname": p.nickname,
            "title": p.title,
            "content": p.content,
            "target_price": p.target_price,
            "bounty_points": p.bounty_points,
            "is_resolved": p.is_resolved,
            "location": p.location,
            "view_count": p.view_count,
            "like_count": p.like_count,
            "created_at": p.created_at.isoformat() if p.created_at else None,
            "comment_count": len(p.comments)
        })
    return result

@router.get("/posts/{post_id}")
def get_community_post(post_id: int, db: Session = Depends(get_db_session)):
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")
        
    # 조회수 증가
    post.view_count += 1
    db.commit()
    db.refresh(post)
    
    # 댓글 정렬: 먼저 COALESCE(parent_id, id) 로 그룹화하고, 
    # parent_id가 없는(부모) 댓글이 먼저 오고 그 다음 자식 댓글들이 작성순으로 오도록 정렬
    sorted_comments = sorted(
        post.comments, 
        key=lambda c: (c.parent_id if c.parent_id is not None else c.id, c.parent_id is not None, c.created_at)
    )
    
    comments = []
    for c in sorted_comments:
        comments.append({
            "id": c.id,
            "user_id": c.user_id,
            "nickname": c.nickname,
            "content": c.content,
            "deal_url": c.deal_url,
            "is_accepted": c.is_accepted,
            "parent_id": c.parent_id,
            "created_at": c.created_at.isoformat() if c.created_at else None
        })
        
    return {
        "id": post.id,
        "post_type": post.post_type,
        "user_id": post.user_id,
        "nickname": post.nickname,
        "title": post.title,
        "content": post.content,
        "target_price": post.target_price,
        "bounty_points": post.bounty_points,
        "is_resolved": post.is_resolved,
        "location": post.location,
        "view_count": post.view_count,
        "like_count": post.like_count,
        "created_at": post.created_at.isoformat() if post.created_at else None,
        "comments": comments
    }

@router.post("/posts/{post_id}/comments")
def create_community_post_comment(
    post_id: int, 
    comment: CommunityPostCommentCreate, 
    db: Session = Depends(get_db_session)
):
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")
        
    new_comment = models.CommunityPostComment(
        post_id=post_id,
        user_id=comment.user_id,
        nickname=comment.nickname,
        content=comment.content,
        deal_url=comment.deal_url,
        parent_id=comment.parent_id
    )
    db.add(new_comment)
    db.commit()
    db.refresh(new_comment)
    
    return {
        "id": new_comment.id,
        "user_id": new_comment.user_id,
        "nickname": new_comment.nickname,
        "content": new_comment.content,
        "deal_url": new_comment.deal_url,
        "is_accepted": new_comment.is_accepted,
        "parent_id": new_comment.parent_id,
        "created_at": new_comment.created_at.isoformat() if new_comment.created_at else None
    }

@router.put("/posts/{post_id}/comments/{comment_id}")
def update_community_post_comment(
    post_id: int, 
    comment_id: int,
    comment_data: CommunityPostCommentCreate, 
    db: Session = Depends(get_db_session)
):
    comment = db.query(models.CommunityPostComment).filter(
        models.CommunityPostComment.id == comment_id,
        models.CommunityPostComment.post_id == post_id
    ).first()
    
    if not comment:
        raise HTTPException(status_code=404, detail="Comment not found")
        
    if comment.user_id != comment_data.user_id:
        raise HTTPException(status_code=403, detail="Only the comment creator can edit")
        
    comment.content = comment_data.content
    if comment_data.deal_url is not None:
        comment.deal_url = comment_data.deal_url
        
    db.commit()
    db.refresh(comment)
    
    return {
        "id": comment.id,
        "user_id": comment.user_id,
        "nickname": comment.nickname,
        "content": comment.content,
        "deal_url": comment.deal_url,
        "is_accepted": comment.is_accepted,
        "parent_id": comment.parent_id,
        "created_at": comment.created_at.isoformat() if comment.created_at else None
    }

@router.delete("/posts/{post_id}/comments/{comment_id}")
def delete_community_post_comment(
    post_id: int, 
    comment_id: int,
    user_id: str = Query(...),
    db: Session = Depends(get_db_session)
):
    comment = db.query(models.CommunityPostComment).filter(
        models.CommunityPostComment.id == comment_id,
        models.CommunityPostComment.post_id == post_id
    ).first()
    
    if not comment:
        raise HTTPException(status_code=404, detail="Comment not found")
        
    if comment.user_id != user_id:
        raise HTTPException(status_code=403, detail="Only the comment creator can delete")
        
    # 만약 자식 댓글이 있으면 삭제 상태로만 마킹 (또는 내용만 변경)
    # 여기서는 간단히 삭제 처리
    db.delete(comment)
    db.commit()
    
    return {"message": "Comment deleted successfully"}

@router.post("/posts/{post_id}/comments/{comment_id}/accept")
def accept_community_post_comment(
    post_id: int, 
    comment_id: int,
    user_id: str = Query(...),
    db: Session = Depends(get_db_session)
):
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")
        
    if post.user_id != user_id:
        raise HTTPException(status_code=403, detail="Only the post creator can accept comments")
        
    if post.is_resolved:
        raise HTTPException(status_code=400, detail="Post is already resolved")
        
    comment = db.query(models.CommunityPostComment).filter(
        models.CommunityPostComment.id == comment_id,
        models.CommunityPostComment.post_id == post_id
    ).first()
    
    if not comment:
        raise HTTPException(status_code=404, detail="Comment not found")
        
    comment.is_accepted = True
    post.is_resolved = True
    
    db.commit()
    
    return {"message": "Comment accepted successfully", "bounty_points_awarded": post.bounty_points}

@router.get("/users/{user_id}/comments")
def get_user_comments(
    user_id: str,
    skip: int = 0,
    limit: int = 20,
    db: Session = Depends(get_db_session)
):
    comments = db.query(models.CommunityPostComment)\
        .filter(models.CommunityPostComment.user_id == user_id)\
        .order_by(models.CommunityPostComment.created_at.desc())\
        .offset(skip).limit(limit).all()
        
    result = []
    for c in comments:
        # Get parent post for context
        post = db.query(models.CommunityPost).filter(models.CommunityPost.id == c.post_id).first()
        
        result.append({
            "id": c.id,
            "post_id": c.post_id,
            "post_title": post.title if post else "삭제된 게시글",
            "user_id": c.user_id,
            "nickname": c.nickname,
            "content": c.content,
            "deal_url": c.deal_url,
            "is_accepted": c.is_accepted,
            "parent_id": c.parent_id,
            "created_at": c.created_at.isoformat() if c.created_at else None
        })
        
    return result


class IngestBatchPayload(BaseModel):
    community_name: str
    display_name: str
    base_url: str
    deals: List[dict]


@router.post("/ingest-batch")
async def ingest_scraped_deals_batch(
    payload: IngestBatchPayload,
    db: Session = Depends(get_db_session),
):
    """한국 리전 릴레이/피더로부터 수집된 핫딜 배치를 서버 DB에 즉시 병합하고 통계를 갱신합니다."""
    from backend.services.aggregator_service import AggregatorService

    community = db.query(models.Community).filter(models.Community.name == payload.community_name).first()
    if not community:
        community = models.Community(
            name=payload.community_name,
            display_name=payload.display_name,
            base_url=payload.base_url,
        )
        db.add(community)
        db.commit()
        db.refresh(community)

    aggregator = AggregatorService(db)
    inserted = 0
    for item in payload.deals:
        try:
            deal = await aggregator.process_scraped_deal(community.id, item)
            if deal:
                inserted += 1
        except Exception as e:
            db.rollback()
            logger.warning(f"[ingest-batch] item error ({payload.community_name}): {e}")

    # Update scraper_stats.json
    stats_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scraper_stats.json")
    try:
        stats = {}
        if os.path.exists(stats_file):
            with open(stats_file, "r", encoding="utf-8") as f:
                stats = json.load(f)
        kst_tz = timezone(timedelta(hours=9))
        stats[payload.display_name] = {
            "last_count": inserted,
            "fetched_count": len(payload.deals),
            "updated_count": inserted,
            "last_run": datetime.now(kst_tz).strftime("%Y-%m-%d %H:%M:%S"),
            "status": "Success",
        }
        with open(stats_file, "w", encoding="utf-8") as f:
            json.dump(stats, f, ensure_ascii=False, indent=2)
    except Exception:
        pass

    invalidate_feed_api_cache()
    return {"status": "ok", "community": payload.display_name, "processed": inserted}

