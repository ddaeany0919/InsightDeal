import html
import os
import re
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse, unquote, quote

_SHORTLINK_UNWRAP_CACHE: dict[str, str] = {}
_AFFILIATE_DEEPLINK_CACHE: dict[str, str] = {}

LINKPRICE_MERCHANTS = {
    "gmarket.co.kr": ("gmarket", "G마켓"),
    "auction.co.kr": ("auction", "옥션"),
    "11st.co.kr": ("11st", "11번가"),
    "lotteon.com": ("lotteon", "롯데온"),
    "e-himart.co.kr": ("himart", "하이마트"),
    "ssg.com": ("ssg", "SSG"),
    "emart.ssg.com": ("emart", "이마트몰"),
    "cjonstyle.com": ("cjonstyle", "CJ온스타일"),
    "gsshop.com": ("gsshop", "GS샵"),
    "hmall.com": ("hmall", "현대Hmall"),
    "yes24.com": ("yes24", "예스24"),
    "kyobobook.co.kr": ("kyobo", "교보문고"),
    "ohou.se": ("ohou", "오늘의집"),
    "musinsa.com": ("musinsa", "무신사"),
    "29cm.co.kr": ("29cm", "29CM"),
}


def unwrap_third_party_affiliate_url(url: str, allow_network: bool = False) -> str:
    """
    🧼 [타 커뮤니티/원작성자 제휴코드 세탁 엔진]
    - click.linkprice.com, lpweb.kr, ilikeclick.com, adpick.co.kr 등의 tu=/url= 파라미터를 즉시 해독합니다.
    - allow_network=True (수집/백필/리다이렉트 시점)일 때는 s.click.aliexpress.com, link.coupang.com, coupa.ng
      단축 제휴 링크를 실제 쇼핑몰 원본 상품 주소로 복원하고 타인의 lptag/aff_fcid를 100% 제거합니다.
    """
    if not url or not str(url).startswith("http"):
        return url or ""

    u = html.unescape(str(url)).replace("&amp;", "&").strip()
    if u in _SHORTLINK_UNWRAP_CACHE:
        return _SHORTLINK_UNWRAP_CACHE[u]

    # 1. 쿼리 파라미터 기반 제휴 래퍼 (네트워크 요청 없이 0ms 해독)
    if any(k in u.lower() for k in ("click.linkprice.com", "lpweb.kr", "ilikeclick.com", "adpick.co.kr")):
        m_tu = re.search(r"[?&](?:tu|url)=([^&]+)", u, re.IGNORECASE)
        if m_tu:
            try:
                dec = unquote(m_tu.group(1)).strip()
                if dec.startswith("http"):
                    u = dec
            except Exception:
                pass

    # 2. 단축 제휴 도메인 (s.click.aliexpress.com, a.aliexpress.com, link.coupang.com, coupa.ng)
    u_lower = u.lower()
    if allow_network and any(
        sd in u_lower for sd in ("s.click.aliexpress.com", "a.aliexpress.com", "link.coupang.com", "coupa.ng")
    ):
        try:
            import requests
            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
            }
            if "link.coupang.com" in u_lower or "coupa.ng" in u_lower:
                resp = requests.get(u, headers=headers, allow_redirects=False, timeout=3.5)
                loc = resp.headers.get("Location") or ""
                if loc.startswith("http"):
                    u = loc
            else:
                resp = requests.get(u, headers=headers, allow_redirects=True, timeout=4.0)
                if resp.url and resp.url.startswith("http") and "s.click.aliexpress.com" not in resp.url.lower():
                    u = resp.url
        except Exception:
            pass

    # 3. 쿠팡 / 알리익스프레스 URL 내 타인 제휴 파라미터(lptag, subid, aff_fcid, aff_fsk 등) 제거
    try:
        parsed = urlparse(u)
        host = parsed.netloc.lower()
        if "aliexpress.com" in host and "/item/" in parsed.path:
            # 알리 상품 페이지는 쿼리 파라미터(aff_fcid 등)를 전부 걷어내면 가장 깨끗한 원본 URL이 됨
            clean_u = f"https://ko.aliexpress.com{parsed.path}"
            _SHORTLINK_UNWRAP_CACHE[str(url).strip()] = clean_u
            return clean_u
        if "coupang.com" in host and "/products/" in parsed.path:
            qs = parse_qs(parsed.query)
            keep_cp = {k: v for k, v in qs.items() if k.lower() in ("itemid", "vendoritemid")}
            clean_u = urlunparse(
                parsed._replace(
                    scheme="https",
                    netloc="www.coupang.com",
                    query=urlencode(keep_cp, doseq=True),
                    fragment="",
                )
            )
            _SHORTLINK_UNWRAP_CACHE[str(url).strip()] = clean_u
            return clean_u
    except Exception:
        pass

    return u


def normalize_url(url: str) -> str:
    """
    🔗 커뮤니티 및 쇼핑몰 URL을 데이터베이스 매칭용으로 완벽하게 규격화(Normalization)합니다.
    - 프로토콜 https로 통일
    - 도메인 호스트명 PC 버전으로 일원화 (m.ppomppu.co.kr -> www.ppomppu.co.kr 등)
    - 트래킹/페이징 및 타인 제휴 쿼리 파라미터(lptag, aff_fcid 등) 제거
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

    # 1.5 커뮤니티 리다이렉트 래퍼 및 제휴 래퍼(click.linkprice.com 등) 즉시 해독
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
    elif any(k in url for k in ("link.php", "out_link.php", "view_gourl_show.php", "popup/outlink", "click.linkprice.com", "lpweb.kr")):
        m_wrap = re.search(r"[?&](?:url|ol|link|gourl|tu)=([^&]+)", url)
        if m_wrap:
            try:
                dec = unquote(m_wrap.group(1)).strip()
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
        else:
            # 쇼핑몰(G마켓 goodscode, 옥션 itemno, 11번가 prdNo, 쿠팡 itemId/vendorItemId 등)은
            # 상품 식별 쿼리 파라미터가 지워지면 링크가 깨지므로 트래킹/타인 제휴 파라미터만 제거하고 모두 보존!
            drop_tracking_keys_lower = {
                "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
                "fbclid", "gclid", "napm", "t_page", "t_ch", "t_imp_id", "trtypecd", "trcrtnno",
                "lptag", "subid", "wpcid", "wref", "wtime", "traceid", "mcid", "addtag", "ctag", "itime",
                "aff_fcid", "aff_fsk", "aff_platform", "aff_trace_key", "sk", "aff_short_key",
                "terminal_id", "afsmartredirect", "pdp_npi", "af",
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


def classify_outbound_mall(url: str) -> tuple[str, str, str, float]:
    """
    📊 아웃링크 URL을 분석하여 (쇼핑몰 한글명, 도메인, 제휴네트워크, 기본수수료율)을 반환합니다.
    """
    if not url:
        return ("기타", "", "direct", 0.0)
    try:
        parsed = urlparse(url)
        host = (parsed.netloc or "").lower().replace("www.", "").replace("m.", "")
        if "click.linkprice.com" in host:
            qs = parse_qs(parsed.query)
            tu = unquote((qs.get("tu") or [""])[0])
            if tu:
                parsed = urlparse(tu)
                host = (parsed.netloc or "").lower().replace("www.", "").replace("m.", "")
        if "coupang.com" in host or "coupa.ng" in host:
            return ("쿠팡", "coupang.com", "coupang", 0.03)
        if "aliexpress.com" in host:
            return ("알리익스프레스", "aliexpress.com", "aliexpress", 0.06)
        for domain_key, (m_id, mall_ko) in LINKPRICE_MERCHANTS.items():
            if domain_key in host:
                return (mall_ko, domain_key, "linkprice", 0.02)
        if "naver.com" in host:
            return ("네이버쇼핑", "shopping.naver.com", "direct", 0.0)
        if "kakao.com" in host:
            return ("카카오쇼핑", "store.kakao.com", "direct", 0.0)
        return (host or "기타", host, "direct", 0.0)
    except Exception:
        return ("기타", "", "direct", 0.0)


def apply_affiliate_monetization(url: str) -> str:
    """
    💰 [수익화 엔진] 쇼핑몰 아웃링크에서 타인/타 커뮤니티 제휴 코드를 세탁한 뒤,
    인사이트딜 자체 제휴 마케팅(Coupang Partners / AliExpress Portals / LinkPrice) 코드를 주입합니다.
    """
    if not url or not url.startswith("http"):
        return url

    try:
        # 0. 타 커뮤니티 제휴 래퍼(click.linkprice.com 등) 및 캐시된 단축링크 해독
        clean_url = unwrap_third_party_affiliate_url(url, allow_network=False)
        parsed = urlparse(clean_url)
        host = parsed.netloc.lower()

        # 1. 쿠팡 파트너스 (COUPANG_ACCESS_KEY+SECRET_KEY 딥링크 API 또는 AFFILIATE_COUPANG_LPTAG / COUPANG_PARTNERS_ID)
        coupang_lptag = (
            os.getenv("AFFILIATE_COUPANG_LPTAG", "").strip()
            or os.getenv("COUPANG_PARTNERS_ID", "").strip()
        )
        coupang_subid = os.getenv("COUPANG_SUB_ID", "insightdeal").strip() or "insightdeal"
        if "coupang.com" in host and "link.coupang.com" not in host:
            from backend.routers.coupang import coupang_service
            if coupang_service.is_configured():
                cached_dl = _AFFILIATE_DEEPLINK_CACHE.get(clean_url)
                if cached_dl:
                    return cached_dl
                dl = coupang_service.create_deeplink(clean_url, sub_id=coupang_subid)
                if dl:
                    _AFFILIATE_DEEPLINK_CACHE[clean_url] = dl
                    return dl
            if coupang_lptag:
                qs = parse_qs(parsed.query)
                qs["lptag"] = [coupang_lptag]
                qs["subid"] = [coupang_subid]
                qs["redirect"] = ["landing"]
                return urlunparse(parsed._replace(query=urlencode(qs, doseq=True)))

        # 2. 알리익스프레스 Portals (AFFILIATE_ALIEXPRESS_ID / ALIEXPRESS_TRACKING_ID)
        ali_aff_id = (
            os.getenv("AFFILIATE_ALIEXPRESS_ID", "").strip()
            or os.getenv("ALIEXPRESS_TRACKING_ID", "").strip()
        )
        if "aliexpress.com" in host and "s.click.aliexpress.com" not in host:
            if ali_aff_id:
                qs = parse_qs(parsed.query)
                qs["aff_fcid"] = [ali_aff_id]
                qs["aff_platform"] = ["portals-tool"]
                qs["sk"] = [ali_aff_id]
                return urlunparse(parsed._replace(query=urlencode(qs, doseq=True)))

        # 3. 링크프라이스 CPS 제휴몰 (G마켓, 옥션, 11번가, 롯데온, 하이마트, SSG, 오늘의집, 무신사 등 15대 쇼핑몰)
        lp_aff_id = (
            os.getenv("AFFILIATE_LINKPRICE_ID", "").strip()
            or os.getenv("LINKPRICE_AFFILIATE_ID", "").strip()
        )
        if lp_aff_id and "click.linkprice.com" not in host:
            for domain_key, (m_id, _mall_ko) in LINKPRICE_MERCHANTS.items():
                if domain_key in host:
                    return f"https://click.linkprice.com/click.php?m={m_id}&a={lp_aff_id}&l=9999&l_cd1=3&l_cd2=0&tu={quote(clean_url, safe='')}"

        return clean_url
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
