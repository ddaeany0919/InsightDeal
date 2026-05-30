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
    
    url = url.strip()
    
    # 1. 프로토콜 강제 일원화
    if url.startswith("//"):
        url = "https:" + url
    elif url.startswith("http://"):
        url = "https://" + url[7:]
    elif not url.startswith("http"):
        url = "https://" + url

    try:
        parsed = urlparse(url)
        host = parsed.netloc.lower()
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
            
        # 3. 불필요 쿼리 매개변수 스나이핑 (페이징, 광고, 스타일 필터링)
        qs = parse_qs(parsed.query)
        
        # 보존할 쿼리 키 정의 (커뮤니티 글 구분에 결정적인 번호나 고유 식별 매개변수만 남김)
        keep_keys = ["id", "no", "document_srl", "article_id", "board_id", "itemId", "productId", "goodsNo", "bo_table", "wr_id"]
        
        filtered_qs = {}
        for k, v in qs.items():
            if k in keep_keys or k.lower() in keep_keys:
                filtered_qs[k] = v
                
        # 뽐뿌 핫딜 아이디 정규화
        if "ppomppu" in host and "id" in filtered_qs:
            # ppomppu, ppomppu4, ppomppu8 등 게시판 id 강제 보정
            board_id = filtered_qs["id"][0]
            if board_id in ["ppomppu", "ppomppu4", "ppomppu8"]:
                filtered_qs["id"] = ["ppomppu"]
                
        clean_query = urlencode(filtered_qs, doseq=True)
        
        # 4. trailing slash 및 꼬리 정리
        if path.endswith("/"):
            path = path[:-1]
            
        normalized = urlunparse(parsed._replace(netloc=host, path=path, query=clean_query, fragment=""))
        return normalized
    except Exception:
        return url
