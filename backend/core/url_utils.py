import html
import re
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse

def normalize_url(url: str) -> str:
    """
    🔗 커뮤니티 및 쇼핑몰 URL을 데이터베이스 매칭용으로 완벽하게 규격화(Normalization)합니다.
    - 프로토콜 https로 통일
    - 도메인 호스트명 PC 버전으로 일원화 (m.ppomppu.co.kr -> www.ppomppu.co.kr 등)
    - 트래킹/페이징 쿼리 파라미터(&page, &divpage, &spage, &mid 등) 제거
    - 끝단 슬래시(/) 정리
    """
    if not url:
        return ""
    
    url = html.unescape(str(url)).replace("&amp;", "&").strip()
    
    # 1. 프로토콜 강제 일원화
    if url.startswith("//"):
        url = "https:" + url
    elif url.startswith("http://"):
        url = "https://" + url[7:]
    elif not url.startswith("http"):
        url = "https://" + url

    # 1.5 커뮤니티 리다이렉트 래퍼(s.ppomppu, link.fmkorea, ruliweb/link.php, out_link.php, popup/outlink) 즉시 해독
    if "s.ppomppu.co.kr" in url:
        m_b64 = re.search(r"target=([^&]+)", url)
        if m_b64:
            try:
                import base64
                b64 = m_b64.group(1)
                pad = len(b64) % 4
                if pad:
                    b64 += "=" * (4 - pad)
                dec = base64.b64decode(b64).decode("utf-8", errors="ignore").strip()
                if dec.startswith("http"):
                    url = dec
            except Exception:
                pass
    elif any(k in url for k in ("link.php", "out_link.php", "view_gourl_show.php", "popup/outlink")):
        m_wrap = re.search(r"[?&](?:url|ol|link|gourl)=([^&]+)", url)
        if m_wrap:
            try:
                import urllib.parse
                dec = urllib.parse.unquote(m_wrap.group(1)).strip()
                if dec.startswith("http"):
                    url = dec
            except Exception:
                pass

    try:
        parsed = urlparse(url)
        host = re.sub(r":(?:443|80)$", "", parsed.netloc.lower())
        path = parsed.path
        
        # 2. 도메인 호스트 호환성 정규화
        if "ppomppu.co.kr" in host:
            host = "www.ppomppu.co.kr"
        elif "fmkorea.com" in host:
            host = "www.fmkorea.com"
        elif "ruliweb.com" in host:
            host = "bbs.ruliweb.com"
        elif "clien.net" in host:
            host = "www.clien.net"
        elif "quasarzone.com" in host:
            host = "quasarzone.com"
        elif "bbasak.com" in host:
            host = "bbasak.com"
            
        # 3. 커뮤니티 도메인 vs 쇼핑몰 아웃링크 분리 처리
        is_community_host = any(
            c in host for c in ["ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "ruliweb.com", "clien.net", "quasarzone.com", "bbasak.com"]
        )
        qs = parse_qs(parsed.query)

        if is_community_host:
            # 커뮤니티 게시글 및 리다이렉트 링크에 필수적인 키만 보존 (페이징 page, divpage 등 제거)
            keep_keys_lower = {
                "id", "no", "document_srl", "article_id", "board_id",
                "bo_table", "wr_id", "ol", "target", "link", "gourl", "url", "bridge_id"
            }
            filtered_qs = {k: v for k, v in qs.items() if k.lower() in keep_keys_lower}

            # 뽐뿌 게시판 ID(ppomppu, ppomppu4, ppomppu8 등)는 각각 별도의 글번호(no) 체계를 가지므로 절대 덮어쓰지 않고 보존!
        else:
            # 쇼핑몰(G마켓 goodscode, 옥션 itemno, 11번가 prdNo, 쿠팡 itemId/vendorItemId 등)은
            # 상품 식별 쿼리 파라미터가 지워지면 링크가 깨지므로 트래킹용 파라미터만 제거하고 모두 보존!
            drop_tracking_keys_lower = {
                "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
                "fbclid", "gclid", "napm", "t_page", "t_ch", "t_imp_id", "trtypecd", "trcrtnno"
            }
            filtered_qs = {k: v for k, v in qs.items() if k.lower() not in drop_tracking_keys_lower}

        clean_query = urlencode(filtered_qs, doseq=True)

        # 4. trailing slash 및 꼬리 정리
        if path.endswith("/") and len(path) > 1:
            path = path[:-1]

        normalized = urlunparse(parsed._replace(netloc=host, path=path, query=clean_query, fragment=""))
        return normalized
    except Exception:
        return url


def apply_affiliate_monetization(url: str) -> str:
    """
    💰 [수익화 엔진] 정화된 쇼핑몰 아웃링크에 제휴 마케팅(Affiliate) 파라미터/래퍼를 안전하게 주입합니다.
    - 환경변수(AFFILIATE_COUPANG_LPTAG, AFFILIATE_ALIEXPRESS_ID, AFFILIATE_LINKPRICE_ID)가 설정된 경우에만 작동하며,
      원본 상품 링크가 절대 깨지지 않도록 쿼리 파라미터 또는 표준 딥링크 포맷으로 결합합니다.
    - 이미 제휴 링크(link.coupang.com, s.click.aliexpress.com, click.linkprice.com)인 경우 중복 변환하지 않습니다.
    """
    if not url or not url.startswith("http"):
        return url

    import os
    from urllib.parse import quote

    try:
        parsed = urlparse(url)
        host = parsed.netloc.lower()

        # 이미 제휴 단축/리다이렉트 도메인인 경우 원본 보존
        if any(ah in host for ah in ["link.coupang.com", "s.click.aliexpress.com", "click.linkprice.com", "app.ac"]):
            return url

        # 1. 쿠팡 파트너스 (AFFILIATE_COUPANG_LPTAG, 예: AF1234567)
        coupang_lptag = os.getenv("AFFILIATE_COUPANG_LPTAG", "").strip()
        if coupang_lptag and "coupang.com" in host:
            qs = parse_qs(parsed.query)
            qs.setdefault("lptag", [coupang_lptag])
            qs.setdefault("subid", ["insightdeal"])
            return urlunparse(parsed._replace(query=urlencode(qs, doseq=True)))

        # 2. 알리익스프레스 Portals (AFFILIATE_ALIEXPRESS_ID)
        ali_aff_id = os.getenv("AFFILIATE_ALIEXPRESS_ID", "").strip()
        if ali_aff_id and "aliexpress.com" in host:
            qs = parse_qs(parsed.query)
            qs.setdefault("aff_fcid", [ali_aff_id])
            qs.setdefault("aff_platform", ["portals-tool"])
            return urlunparse(parsed._replace(query=urlencode(qs, doseq=True)))

        # 3. 링크프라이스 CPS 제휴몰 (G마켓, 옥션, 11번가, 롯데온, 하이마트 등 - AFFILIATE_LINKPRICE_ID)
        lp_aff_id = os.getenv("AFFILIATE_LINKPRICE_ID", "").strip()
        if lp_aff_id:
            lp_merchants = {
                "gmarket.co.kr": "gmarket",
                "auction.co.kr": "auction",
                "11st.co.kr": "11st",
                "lotteon.com": "lotteon",
                "e-himart.co.kr": "himart",
            }
            for domain_key, m_id in lp_merchants.items():
                if domain_key in host:
                    return f"https://click.linkprice.com/click.php?m={m_id}&a={lp_aff_id}&l=9999&l_cd1=3&l_cd2=0&tu={quote(url, safe='')}"

        return url
    except Exception:
        return url


def extract_canonical_product_key(url: str) -> str:
    """
    🔍 [초정밀 동일 상품 식별 엔진]
    각 커뮤니티마다 모바일/PC/리다이렉트/트래킹 주소가 달라도 쇼핑몰의 '고유 상품 번호(Product ID)'를 추출하여
    100% 동일한 상품 키(예: 'naver_10293847', 'coupang_789123_456789', 'gmarket_4701840459')로 반환합니다.
    """
    if not url:
        return ""

    import urllib.parse

    u = url.strip()
    # 1. 커뮤니티 아웃링크 래퍼(ruliweb, clien, ppomppu 등) 내부 실제 URL 우선 해독
    for param_pattern in [r"[?&]ol=([^&]+)", r"[?&]gourl=([^&]+)", r"[?&]url=([^&]+)", r"[?&]target=([^&]+)"]:
        m_wrap = re.search(param_pattern, u)
        if m_wrap:
            try:
                decoded = urllib.parse.unquote(m_wrap.group(1))
                if decoded.startswith("http"):
                    u = decoded
                    break
            except Exception:
                pass

    u_lower = u.lower()

    # 2. G마켓 (goodscode)
    m = re.search(r"goodscode=([0-9]+)", u, re.IGNORECASE)
    if m:
        return f"gmarket_{m.group(1)}"

    # 3. 옥션 (itemno 또는 ego.aspx?p=)
    m = re.search(r"itemno=([a-zA-Z0-9]+)", u, re.IGNORECASE)
    if m:
        return f"auction_{m.group(1).upper()}"
    if "ego.aspx" in u_lower:
        m = re.search(r"[?&]p=([a-zA-Z0-9]+)", u, re.IGNORECASE)
        if m:
            return f"auction_{m.group(1).upper()}"

    # 4. 11번가 (/products/번호 또는 prdNo=번호)
    if "11st.co.kr" in u_lower:
        m = re.search(r"(?:products/|prdNo=)([0-9]+)", u, re.IGNORECASE)
        if m:
            return f"11st_{m.group(1)}"

    # 5. 쿠팡 (/vp/products/번호, /vm/products/번호 + itemId 옵션 구분)
    if "coupang.com" in u_lower:
        m = re.search(r"products/([0-9]+)", u, re.IGNORECASE)
        if m:
            item_m = re.search(r"itemId=([0-9]+)", u, re.IGNORECASE)
            if item_m:
                return f"coupang_{m.group(1)}_{item_m.group(1)}"
            return f"coupang_{m.group(1)}"

    # 6. 네이버 스마트스토어 / 브랜드스토어 / 쇼핑윈도
    if any(nd in u_lower for nd in ["smartstore.naver.com", "brand.naver.com", "shopping.naver.com"]):
        m = re.search(r"/products/([0-9]+)", u, re.IGNORECASE)
        if m:
            return f"naver_{m.group(1)}"

    # 7. 알리익스프레스 (/item/번호.html)
    if "aliexpress.com" in u_lower:
        m = re.search(r"item/([0-9]+)\.html", u, re.IGNORECASE)
        if m:
            return f"aliexpress_{m.group(1)}"

    # 8. 롯데온 (/p/product/LO번호 또는 PD번호)
    if "lotteon.com" in u_lower:
        m = re.search(r"/product/([A-Za-z0-9]+)", u, re.IGNORECASE)
        if m:
            return f"lotteon_{m.group(1).upper()}"

    # 9. SSG / 이마트몰 (itemId=번호)
    if "ssg.com" in u_lower:
        m = re.search(r"itemId=([0-9]+)", u, re.IGNORECASE)
        if m:
            return f"ssg_{m.group(1)}"

    # 10. 무신사 (/products/번호 또는 /app/goods/번호)
    if "musinsa.com" in u_lower:
        m = re.search(r"(?:products|goods)/([0-9]+)", u, re.IGNORECASE)
        if m:
            return f"musinsa_{m.group(1)}"

    # 11. 오늘의집 (ohou.se/goods/번호 또는 bucketplace)
    if "ohou.se" in u_lower or "bucketplace" in u_lower:
        m = re.search(r"goods/([0-9]+)", u, re.IGNORECASE)
        if m:
            return f"ohouse_{m.group(1)}"

    # 12. 티몬 / 위메프 / 하이마트 / 인터파크 / 카카오톡쇼핑(톡딜)
    if "store.kakao.com" in u_lower:
        m = re.search(r"/products/([0-9]+)", u, re.IGNORECASE)
        if m:
            return f"kakao_{m.group(1)}"
    if "e-himart.co.kr" in u_lower:
        m = re.search(r"goodsNo=([0-9]+)", u, re.IGNORECASE)
        if m:
            return f"himart_{m.group(1)}"

    return ""


def is_search_or_community_url(url: str) -> bool:
    """
    🔍 주어진 URL이 쇼핑몰 검색 결과 페이지이거나 커뮤니티 내부 링크인지 감지합니다.
    - True: 쇼핑몰 검색 폴백 URL(/search, /wholesale- 등) 또는 커뮤니티(뽐뿌, 펨코, 퀘이사존 등) 게시글 주소
    - False: 실제 단일 상품 상세 직링크 (쿠팡, 네이버 스마트스토어, 지마켓, 11번가, 옥션 등)
    """
    if not url:
        return False
    
    u = str(url).strip().lower()
    if not u.startswith("http"):
        return False

    # 1. 커뮤니티 내부 글 주소 및 리다이렉트 도메인 감지 (최우선 차단)
    community_domains = (
        "ppomppu.co.kr",
        "fmkorea.com",
        "fmkorea.org",
        "quasarzone.com",
        "ruliweb.com",
        "clien.net",
        "bbasak.com",
    )
    if any(cd in u for cd in community_domains):
        return True

    # 2. 실제 이커머스 단일 상품 직링크 고유 식별 패턴 우선 감지
    # (검색 전용 도메인이 아닌 이상, 직링크 식별자가 포함되어 있으면 즉시 False 반환)
    dedicated_search_domains = (
        "search.shopping.naver.com",
        "search.naver.com",
        "search.daum.net",
        "google.com/search",
        "browse.gmarket.co.kr",
    )
    is_dedicated_search_domain = any(dsd in u for dsd in dedicated_search_domains)

    if not is_dedicated_search_domain:
        direct_product_indicators = (
            "/products/",
            "/vp/products/",
            "/item/",
            "/items/",
            "goodscode=",
            "itemno=",
            "item_no=",
            "itemid=",
            "productid=",
            "product_id=",
            "prodcode=",
            "productno=",
            "product_no=",
            "prd_no=",
        )
        if any(dpi in u for dpi in direct_product_indicators):
            return False

    # 3. 쇼핑몰 검색 폴백 URL 및 검색 엔진 패턴 감지 (경로 경계 및 쿼리 정밀 매칭)
    search_indicators = (
        "/wholesale-",
        "search.shopping.naver.com",
        "search.naver.com",
        "coupang.com/np/search",
        "gmarket.co.kr/n/search",
        "browse.gmarket.co.kr",
        "browse.auction.co.kr/search",
        "11st.co.kr/browsing/search",
        "search?keyword=",
        "search/all?query=",
        "total-search?kwd=",
        "np/search?q=",
        "letsearch?searchterm=",
        "letsearch",
        "search?q=",
        "search.daum.net",
        "google.com/search",
        "/search?",
        "/search/",
        "/search&",
        "/search#",
    )
    if any(si in u for si in search_indicators) or u.endswith("/search"):
        return True

    return False
