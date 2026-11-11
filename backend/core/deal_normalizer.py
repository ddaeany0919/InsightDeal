import base64
import html
import logging
import re
import urllib.parse
from typing import Any, Optional

logger = logging.getLogger(__name__)

# 저장 단위 기준 원화 환산 계수:
# - KRW: 원 단위 정수 (15000 -> 15,000원)
# - USD: 센트 단위 정수 (15000 == $150.00 -> 210,000원)
# - EUR: 센트 단위 정수 (10000 == €100.00 -> 152,000원)
# - CNY: 전(fen, *100) 단위 정수 (18900 == ¥189.00 -> 36,855원)
# - JPY: 엔 단위 정수 (3000 == 3,000엔 -> 27,900원)
CURRENCY_KRW_RATES: dict[str, float] = {
    "KRW": 1.0,
    "USD": 14.0,
    "EUR": 15.2,
    "CNY": 1.95,
    "JPY": 9.3,
}

COLLECTION_DUMMY_PRICES: set[int] = {
    1, 10, 100, 111, 777, 999, 1000, 1111, 9999, 1234, 12345,
}

_MICRO_POINT_KEYWORDS = (
    "네이버페이", "네페", "폐지", "포인트", "적립", "클릭", "라방", "출석", "출첵",
)

_COLLECTION_TITLE_RE = re.compile(
    r"(?:모음|다양|할인\s*코드|혜택|이벤트|기획전|총정리|골라담기)",
    re.IGNORECASE,
)

# 날짜/시간 괄호 선제 마스킹 정규식: (10/13 오전10:30), (11/2~11/30), [11/11 00시], (10.13) 등
_DATE_TIME_PAREN_RE = re.compile(
    r"[\(\[]\s*(?:20\d{2}\s*[./\-년]\s*)?(?:0?[1-9]|1[0-2])\s*(?:[/\.]|월)\s*"
    r"(?:0?[1-9]|[12]\d|3[01])\s*일?"
    r"(?:\s*(?:[~∼\-]|부터|까지|오전|오후|낮|밤|새벽|자정|정오|am|pm|\d{1,2}\s*[:시]|\([월화수목금토일]\)|[월화수목금토일]요일)[^\)\]]*)?"
    r"[\)\]]",
    re.IGNORECASE,
)

# 단위당 가격 선제 마스킹: 100g당 990원, 개당 500원, 10ml당 등
_UNIT_PRICE_RE = re.compile(
    r"\d+(?:\.\d+)?\s*(?:kg|g|mg|ml|l|개|입|봉|팩|포|장|매|구|미|알|병|캔|통|박스|캡슐|정|롤)\s*당"
    r"(?:\s*(?:->|→|[:\-])?\s*약?\s*[$₩￦€¥￥]?\s*[0-9,]+(?:\.[0-9]+)?\s*(?:만\s*원?|천\s*원?|원|달러|위안|엔|유로)?)?",
    re.IGNORECASE,
)

# 과거 시드/플레이스홀더 오염 패턴
_PLACEHOLDER_COMMA_RE = re.compile(r"1,000\s*단위\s*구분\s*쉼표\s*(?:원\s*)?", re.IGNORECASE)
_SEED_DUMMY_TAIL_RE = re.compile(
    r"\s*\((?:에어|미니|프로|맥스|울트라|플러스)[\s\w가-힣]*\d+\s*[wW]\s*택배배송\)",
    re.IGNORECASE,
)

# 본문 폴백 시 대표 가격에서 제외할 문맥(정가/배송비/추가금/할인액 등)
_BODY_CONTEXT_PREFIX_MASK_RE = re.compile(
    r"(?:기존\s*가(?:격)?|정가|출시\s*가(?:격)?|정상\s*가(?:격)?|출고\s*가(?:격)?|시중\s*가(?:격)?|권장\s*소비자가|"
    r"배송\s*비|택배\s*비|우편\s*비|도서\s*산간|"
    r"추가\s*(?:금액|금|요금|옵션|선택|구매|비용)?|옵션\s*추가|"
    r"쿠폰(?:\s*할인)?|할인(?:\s*금액|\s*쿠폰|\s*코드|\s*폭)?|"
    r"적립(?:\s*금|\s*포인트|\s*혜택)?|페이백|캐시백|청구\s*할인|카드\s*할인|즉시\s*할인|"
    r"최소\s*(?:주문|결제)(?:\s*금액)?|최대\s*할인)"
    r"\s*[:：=\-]?\s*[+\-±]?\s*(?:약\s*)?(?:[$₩￦€¥￥]\s*)?[0-9,]+(?:\.[0-9]+)?\s*"
    r"(?:만\s*원?|천\s*원?|원|달러|불|위안|엔|円|유로|USD|EUR|CNY|JPY)?",
    re.IGNORECASE,
)

_BODY_CONTEXT_SUFFIX_MASK_RE = re.compile(
    r"[+\-]\s*[0-9,]+(?:\.[0-9]+)?\s*(?:만\s*원?|천\s*원?|원|달러|불|위안|엔|유로)"
    r"|(?:[$₩￦€¥￥]?\s*[0-9,]+(?:\.[0-9]+)?\s*(?:만\s*원?|천\s*원?|원|달러|불|위안|엔|유로))\s*"
    r"(?:추가|할인|적립|쿠폰|캐시백|페이백|차감|인하|절감|배송비|택배비|이상\s*(?:구매|결제|무료|무배|주문))",
    re.IGNORECASE,
)

_BODY_SURCHARGE_PAREN_RE = re.compile(
    r"\(\s*\+\s*[0-9,]+\s*원?\s*\)",
    re.IGNORECASE,
)

_EXPLICIT_SHIPPING_KEYWORD_RE = re.compile(
    r"^(?:무배|무료배송|무료|유배|유료배송|배송비|택배비?|와우무료|와우무배|네멤무료|네멤무배|조건부무료|조건부|free(?:\s*shipping)?|fs)\b",
    re.IGNORECASE,
)

# 비쇼핑몰 말머리 및 일반 수식어 집합
_NON_MALL_EXACT: set[str] = {
    "특가", "초특가", "타임특가", "런칭특가", "알리특가", "알뜰특가", "주말특가", "일일특가", "한정특가",
    "재입고", "기타", "역대가", "역대급", "브랜드데이", "핫딜", "국내핫딜", "해외핫딜", "인기핫딜",
    "공구", "공동구매", "광군제", "광군제공구", "잔여공구코드", "잔여코드", "공구코드", "할인코드", "쿠폰코드", "프로모션코드",
    "할인", "청구할인", "카드할인", "즉시할인", "중복할인", "쿠폰", "선착순쿠폰",
    "이벤트", "품절", "일시품절", "종료", "마감", "임박", "끌올", "가격인하",
    "모음", "모음전", "골라담기", "다양", "선택", "총정리", "기획전",
    "선착순", "무료", "무배", "무료배송", "무료배포", "일시무료", "한정무료", "나눔",
    "적립", "슈퍼적립", "즉시적립", "일일적립", "포인트", "앱테크", "폐지",
    "라방", "라이브", "사전예약", "체험단", "응모", "출석", "출첵",
    "직구", "해외직구", "국내정발", "정품", "신형", "리퍼", "미개봉", "완본체", "반본체",
    "빅스마일", "빅스마일데이", "블프", "블랙프라이데이", "초이스데이", "코인딜", "골드박스", "타임딜", "투데이",
    "정보없음", "확인필요", "none", "null", "unknown",
    # 커뮤니티 명칭 (쇼핑몰 배지 오입 방지)
    "뽐뿌", "뽐뿌해외", "알리뽐뿌", "펨코", "에펨", "에펨코리아", "퀘이사존", "루리웹", "클리앙",
    "빠삭", "빠삭국내", "빠삭해외", "빠삭육아", "쿨엔조이", "다모앙",
    # 카테고리 명칭
    "먹거리", "식품", "음식", "음료", "가전", "가전제품", "디지털", "컴퓨터", "pc", "pc용품", "pc/하드웨어", "하드웨어",
    "생활", "생활용품", "생필품", "패션", "의류", "신발", "잡화", "뷰티", "화장품", "육아", "유아용품",
    "상품권", "기프티콘", "모바일", "스마트폰", "게임", "sw", "소프트웨어", "콘솔",
}

_NON_MALL_SUBSTRINGS = (
    "특가", "재입고", "광군제", "모음", "공구", "공동구매", "단독", "선착순", "최저가", "역대가",
    "끌올", "할인", "코드", "쿠폰", "임박", "마감", "종료", "품절", "핫딜", "직구",
    "무료", "무배", "사전예약", "라이브", "라방", "이벤트", "적립", "앱테크", "골라담기",
    "가격인하", "브랜드데이", "페스타", "끝딜", "신형", "세대", "인치", "배터리", "충전기",
    "이어폰", "청소기", "모니터", "그래픽카드", "노트북", "키보드", "마우스",
)

MALL_ALIAS_MAP: dict[str, str] = {
    "g마켓": "지마켓",
    "gmarket": "지마켓",
    "지마켓": "지마켓",
    "지마켓글로벌": "지마켓",
    "옥션": "옥션",
    "auction": "옥션",
    "11번가": "11번가",
    "십일번가": "11번가",
    "11st": "11번가",
    "11번가아마존": "11번가",
    "아마존11번가": "11번가",
    "쿠팡": "쿠팡",
    "coupang": "쿠팡",
    "로켓배송": "쿠팡",
    "로켓직구": "쿠팡",
    "로켓와우": "쿠팡",
    "롯데온": "롯데온",
    "롯데on": "롯데온",
    "lotteon": "롯데온",
    "롯데백화점": "롯데온",
    "네이버": "네이버",
    "naver": "네이버",
    "스마트스토어": "네이버",
    "브랜드스토어": "네이버",
    "네이버스토어": "네이버",
    "네이버쇼핑": "네이버",
    "네쇼라": "네이버",
    "네이버쇼핑라이브": "네이버",
    "넾다세일": "네이버",
    "네이버페이": "네이버",
    "알리": "알리익스프레스",
    "알익": "알리익스프레스",
    "알리익스프레스": "알리익스프레스",
    "aliexpress": "알리익스프레스",
    "ali": "알리익스프레스",
    "ssg": "SSG닷컴",
    "ssg닷컴": "SSG닷컴",
    "쓱": "SSG닷컴",
    "쓱닷컴": "SSG닷컴",
    "신세계몰": "신세계몰",
    "이마트몰": "이마트몰",
    "이마트": "이마트몰",
    "오늘의집": "오늘의집",
    "ohouse": "오늘의집",
    "무신사": "무신사",
    "musinsa": "무신사",
    "29cm": "29CM",
    "w컨셉": "W컨셉",
    "wconcept": "W컨셉",
    "토스": "토스",
    "토스쇼핑": "토스",
    "톡딜": "톡딜",
    "카카오톡딜": "톡딜",
    "카카오": "카카오",
    "카카오쇼핑": "카카오",
    "카카오톡쇼핑하기": "카카오",
    "카카오선물하기": "카카오",
    "카카오톡선물하기": "카카오",
    "우체국쇼핑": "우체국쇼핑",
    "우체국": "우체국쇼핑",
    "하이마트": "하이마트",
    "하이마트몰": "하이마트",
    "롯데하이마트": "하이마트",
    "cj온스타일": "CJ온스타일",
    "cjonstyle": "CJ온스타일",
    "cj더마켓": "CJ더마켓",
    "cj제일제당": "CJ더마켓",
    "gs샵": "GS샵",
    "gsshop": "GS샵",
    "현대hmall": "현대Hmall",
    "hmall": "현대Hmall",
    "더현대": "더현대",
    "컬리": "컬리",
    "마켓컬리": "컬리",
    "kurly": "컬리",
    "아마존": "아마존",
    "아마존재팬": "아마존",
    "amazon": "아마존",
    "큐텐": "큐텐",
    "qoo10": "큐텐",
    "테무": "테무",
    "temu": "테무",
    "타오바오": "타오바오",
    "taobao": "타오바오",
    "티몰": "타오바오",
    "tmall": "타오바오",
    "아이허브": "아이허브",
    "iherb": "아이허브",
    "스팀": "스팀",
    "steam": "스팀",
    "에픽게임즈": "에픽게임즈",
    "에픽": "에픽게임즈",
    "epicgames": "에픽게임즈",
    "epic": "에픽게임즈",
    "gog": "GOG",
    "ps스토어": "PS스토어",
    "플레이스테이션": "PS스토어",
    "닌텐도": "닌텐도",
    "올리브영": "올리브영",
    "oliveyoung": "올리브영",
    "다이소": "다이소몰",
    "다이소몰": "다이소몰",
    "코스트코": "코스트코",
    "홈플러스": "홈플러스",
    "lf몰": "LF몰",
    "lfmall": "LF몰",
    "lf스퀘어": "LF몰",
    "하프클럽": "하프클럽",
    "보리보리": "보리보리",
    "삼성닷컴": "삼성닷컴",
    "lg전자": "LG전자",
    "컴퓨존": "컴퓨존",
    "compuzone": "컴퓨존",
    "탁탁몰": "탁탁몰",
    "tagtag": "탁탁몰",
    "커버낫": "커버낫",
    "covernat": "커버낫",
    "다나와": "다나와",
    "지오다노": "지오다노",
    "아디다스": "아디다스",
    "나이키": "나이키",
    "지그재그": "지그재그",
    "에이블리": "에이블리",
    "배달의민족": "배달의민족",
    "배민": "배달의민족",
    "요기요": "요기요",
    "티몬": "티몬",
    "위메프": "위메프",
    "인터파크": "인터파크",
    "롯데홈쇼핑": "롯데홈쇼핑",
    "ns홈쇼핑": "NS홈쇼핑",
    "공영홈쇼핑": "공영홈쇼핑",
    "sk스토아": "SK스토아",
    "예스24": "예스24",
    "yes24": "예스24",
    "교보문고": "교보문고",
    "동원몰": "동원몰",
    "풀무원": "풀무원",
    "오뚜기몰": "오뚜기몰",
    "소프라노몰": "소프라노몰",
}

_DOMAIN_SHOP_RULES: list[tuple[str, str]] = [
    ("coupang.com", "쿠팡"),
    ("coupa.ng", "쿠팡"),
    ("gmarket.co.kr", "지마켓"),
    ("auction.co.kr", "옥션"),
    ("11st.co.kr", "11번가"),
    ("aliexpress.com", "알리익스프레스"),
    ("smartstore.naver.com", "네이버"),
    ("brand.naver.com", "네이버"),
    ("shopping.naver.com", "네이버"),
    ("shoppinglive.naver.com", "네이버"),
    ("naver.me", "네이버"),
    ("lotteon.com", "롯데온"),
    ("compuzone.co.kr", "컴퓨존"),
    ("tagtag.co.kr", "탁탁몰"),
    ("tmall.com", "타오바오"),
    ("taobao.com", "타오바오"),
    ("tb.cn", "타오바오"),
    ("covernat.net", "커버낫"),
    ("covernat.co.kr", "커버낫"),
    ("steampowered.com", "스팀"),
    ("steamcommunity.com", "스팀"),
    ("epicgames.com", "에픽게임즈"),
    ("gog.com", "GOG"),
    ("playstation.com", "PS스토어"),
    ("nintendo.co.kr", "닌텐도"),
    ("emart.ssg.com", "이마트몰"),
    ("shinsegaemall.ssg.com", "신세계몰"),
    ("ssg.com", "SSG닷컴"),
    ("ohou.se", "오늘의집"),
    ("bucketplace.co", "오늘의집"),
    ("musinsa.com", "무신사"),
    ("29cm.co.kr", "29CM"),
    ("wconcept.co.kr", "W컨셉"),
    ("store.kakao.com", "톡딜"),
    ("gift.kakao.com", "카카오"),
    ("shopping.kakao.com", "카카오"),
    ("kakao.com", "카카오"),
    ("toss.im", "토스"),
    ("toss.shopping", "토스"),
    ("epost.go.kr", "우체국쇼핑"),
    ("e-himart.co.kr", "하이마트"),
    ("cjonstyle.com", "CJ온스타일"),
    ("cjthemarket.com", "CJ더마켓"),
    ("gsshop.com", "GS샵"),
    ("hmall.com", "현대Hmall"),
    ("hyundaihmall.com", "현대Hmall"),
    ("thehyundai.com", "더현대"),
    ("kurly.com", "컬리"),
    ("qoo10.com", "큐텐"),
    ("amazon.co.jp", "아마존"),
    ("amazon.com", "아마존"),
    ("amzn.to", "아마존"),
    ("temu.com", "테무"),
    ("iherb.com", "아이허브"),
    ("oliveyoung.co.kr", "올리브영"),
    ("daisomall.co.kr", "다이소몰"),
    ("costco.co.kr", "코스트코"),
    ("homeplus.co.kr", "홈플러스"),
    ("samsung.com", "삼성닷컴"),
    ("lge.co.kr", "LG전자"),
    ("danawa.com", "다나와"),
    ("giordano.co.kr", "지오다노"),
    ("adidas.co.kr", "아디다스"),
    ("nike.com", "나이키"),
    ("lfmall.co.kr", "LF몰"),
    ("halfclub.com", "하프클럽"),
    ("boribori.co.kr", "보리보리"),
    ("zigzag.kr", "지그재그"),
    ("a-bly.com", "에이블리"),
    ("lotteimall.com", "롯데홈쇼핑"),
    ("nsmall.com", "NS홈쇼핑"),
    ("gongyoungshop.kr", "공영홈쇼핑"),
    ("skstoa.com", "SK스토아"),
    ("yes24.com", "예스24"),
    ("kyobobook.co.kr", "교보문고"),
    ("dongwonmall.com", "동원몰"),
    ("pulmuone.co.kr", "풀무원"),
    ("tmon.co.kr", "티몬"),
    ("wemakeprice.com", "위메프"),
    ("interpark.com", "인터파크"),
    ("naver.com", "네이버"),
]

_COMMUNITY_HOSTS = (
    "ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "quasarzone.com",
    "ruliweb.com", "ruliweb.daum.net", "clien.net", "bbasak.com",
    "coolenjoy.net", "damoang.net",
)


def extract_numeric_price(price_val: Any) -> int:
    if price_val is None:
        return 0
    if isinstance(price_val, (int, float)):
        v = int(round(price_val))
        return v if 0 < v <= 15000000 else 0
    s = str(price_val).strip()
    if not s or "http://" in s or "https://" in s or "products/" in s:
        return 0
    nums = re.findall(r"\d+", s)
    if not nums:
        return 0
    parsed = int("".join(nums))
    return parsed if 0 < parsed <= 15000000 else 0


def to_krw_equivalent(price: Any, currency: Optional[str] = "KRW") -> float:
    p_int = extract_numeric_price(price)
    if p_int <= 0:
        return 0.0
    curr = (currency or "KRW").strip().upper()
    rate = CURRENCY_KRW_RATES.get(curr, 1.0)
    return float(p_int) * rate


def has_currency_aware_price_conflict(
    price_a: Any,
    price_b: Any,
    curr_a: Optional[str] = "KRW",
    curr_b: Optional[str] = "KRW",
    threshold: float = 0.45,
) -> bool:
    krw_a = to_krw_equivalent(price_a, curr_a)
    krw_b = to_krw_equivalent(price_b, curr_b)
    if krw_a > 0 and krw_b > 0:
        hi = max(krw_a, krw_b)
        lo = min(krw_a, krw_b)
        if (hi - lo) / hi > threshold:
            return True
    return False


def format_price_display(price: int, currency: Optional[str] = "KRW") -> str:
    curr = (currency or "KRW").strip().upper()
    p = int(price or 0)
    if curr == "USD":
        return f"${p / 100:.2f}"
    if curr == "EUR":
        return f"€{p / 100:.2f}"
    if curr == "CNY":
        if p % 100 == 0:
            return f"¥{p // 100:,}"
        return f"¥{p / 100:.2f}"
    if curr == "JPY":
        return f"{p:,}엔"
    return f"{p:,}원"


def _decode_base64_url(b64_token: str) -> str:
    if not b64_token:
        return ""
    token = urllib.parse.unquote(b64_token.strip())
    pad = len(token) % 4
    if pad:
        token += "=" * (4 - pad)
    for decoder in (base64.b64decode, base64.urlsafe_b64decode):
        try:
            raw_bytes = decoder(token)
            decoded = raw_bytes.decode("utf-8", errors="ignore").strip()
            if decoded.startswith(("http://", "https://", "//")):
                return ("https:" + decoded) if decoded.startswith("//") else decoded
            unq = urllib.parse.unquote(decoded).strip()
            if unq.startswith(("http://", "https://", "//")):
                return ("https:" + unq) if unq.startswith("//") else unq
        except Exception:
            continue
    return ""


def unwrap_redirect_target_url(url: str) -> str:
    """
    커뮤니티 아웃링크(s.ppomppu.co.kr Base64 target, fmkorea/clien url, ruliweb ol, ppomppu gourl/link)
    및 제휴 래퍼(newtip.net/linkprice tu, lpurl, u) 내부의 실제 쇼핑몰 URL을 복원합니다.
    """
    if not url:
        return ""
    u = html.unescape(str(url)).replace("&amp;", "&").strip()
    u = re.split(r'[가-힣\s"\'<>]|(?:\$\d)', u)[0].strip().rstrip(".,;)>]")
    if not u:
        return ""

    for _ in range(4):
        changed = False
        if "s.ppomppu.co.kr" in u.lower() or "target=ahr0c" in u.lower():
            m_b64 = re.search(r"[?&]target=([^&#\s]+)", u, re.IGNORECASE)
            if m_b64:
                dec_b64 = _decode_base64_url(m_b64.group(1))
                if dec_b64:
                    u = dec_b64
                    changed = True
                    continue

        m_wrap = re.search(
            r"[?&](?:tu|lpurl|u|ol|gourl|url|link|target)=([^&#\s]+)",
            u,
            re.IGNORECASE,
        )
        if m_wrap:
            raw_param = m_wrap.group(1).strip()
            if raw_param.lower().startswith("ahr0c"):
                dec_b64 = _decode_base64_url(raw_param)
                if dec_b64:
                    u = dec_b64
                    changed = True
                    continue
            cand = urllib.parse.unquote(raw_param).strip()
            if "%3a%2f%2f" in cand.lower():
                cand = urllib.parse.unquote(cand).strip()
            if cand.startswith("//"):
                cand = "https:" + cand
            if cand.startswith(("http://", "https://")) and cand != u:
                u = cand
                changed = True
                continue

        if not changed:
            break

    return u.replace("/./", "/").strip()


def sanitize_ingested_title(raw_title: str) -> str:
    """
    DB 적재 및 기존 레코드 치유용 제목 정제:
    - HTML 엔티티 복원 (&amp;, &#039; 등)
    - 뽐뿌 작성폼 플레이스홀더 '1,000단위 구분 쉼표' 제거
    - 과거 시드 오염 꼬리표 '(에어 미니 ... 35w 택배배송)' 제거
    - 앞머리 닫는 괄호 오타('잔여공구코드] ')를 '[잔여공구코드] '로 교정
    - 잘린 끝단 괄호/말줄임표 균형 복구 (정상적인 가격/배송비 괄호 '(289,000원/무배)'는 보존)
    """
    if not raw_title:
        return ""
    t = html.unescape(str(raw_title))
    t = t.replace("\xa0", " ").replace("릿模셈", "넾다세일").replace("\ufffd", "").strip()
    t = _PLACEHOLDER_COMMA_RE.sub("", t)
    t = _SEED_DUMMY_TAIL_RE.sub("", t)
    t = re.sub(r"\s*\([^)]*울트라\s*맥스[^)]*택배배송\)", "", t, flags=re.IGNORECASE)
    # 플레이스홀더 제거 후 남는 빈 괄호나 슬래시 정리: '(/무배)' -> '(무배)', '()' -> ''
    t = re.sub(r"\(\s*[/,|]\s*([^\)]+)\)", r"(\1)", t)
    t = re.sub(r"\(\s*[/,|]?\s*\)", "", t)
    t = re.sub(r"\[\s*[/,|]?\s*\]", "", t)

    m_head = re.match(r"^([^\[\]\(\)]{1,22})[\)\]]\s+(.+)$", t)
    if m_head:
        head_tok = m_head.group(1).strip()
        if not re.search(r"\d+(?:\.\d+)?\s*(?:인치|inch|kg|g|ml|l|w|gb|tb)$", head_tok, re.IGNORECASE):
            t = f"[{head_tok}] {m_head.group(2).strip()}"

    while t.endswith(")") and t.count(")") > t.count("("):
        t = t[:-1].strip()
    while t.endswith("]") and t.count("]") > t.count("["):
        t = t[:-1].strip()
    while t.count("(") > t.count(")"):
        if re.search(r"\(\s*\$?\s*\.{2,}\s*$", t):
            t = re.sub(r"\s*\(\s*\$?\s*\.{2,}\s*$", "", t).strip()
        else:
            t = re.sub(r"\.{2,}\s*$", "", t).rstrip(" ,/-") + ")"
    while t.count("[") > t.count("]"):
        t = re.sub(r"\.{2,}\s*$", "", t).rstrip(" ,/-") + "]"
    if re.search(r"(?:\.{2,}|…)\s*$", t):
        t = re.sub(r"(?:\.{2,}|…)+\s*$", "", t).rstrip(" ,/-·")
    if t.count(")") > t.count("("):
        t = t.replace(")", "")
    if t.count("]") > t.count("["):
        t = t.replace("]", "")
    return re.sub(r"\s{2,}", " ", t).strip()


def clean_canonical_title(raw_title: str) -> str:
    """
    API 응답(목록/상세) 표시용 제목 정제:
    - HTML 엔티티 복원, '1,000단위 구분 쉼표', '(... \\d+w 택배배송)', '(... 울트라 맥스 ...)' 제거
    - 백슬래시 원화 기호 절삭은 숫자 앞('\\(?=\\s*\\d)')으로만 한정하여 일반 기호 오절삭 방지
    - 후행 가격/배송비 괄호 및 닫히지 않은 후행 가격/배송비 괄호 제거
    - 한글 지명((수원), (강원)) 및 스펙 괄호((15.6인치), (블랙))는 보존
    """
    if not raw_title:
        return ""
    t = html.unescape(str(raw_title))
    t = t.replace("\xa0", " ").replace("릿模셈", "넾다세일").replace("\ufffd", "").strip()
    if len(t) > 500:
        t = t[:500]

    t = _PLACEHOLDER_COMMA_RE.sub("", t)
    t = _SEED_DUMMY_TAIL_RE.sub("", t)
    t = re.sub(r"\s*\([^)]*울트라\s*맥스[^)]*\)", "", t, flags=re.IGNORECASE)
    t = re.sub(r"\(\s*[/,|]?\s*\)", "", t)

    # 앞머리 닫는 괄호 오타 교정 ('잔여공구코드] ' -> '[잔여공구코드] ')
    m_head = re.match(r"^\s*([^\[\]\(\)\s][^\[\]\(\)]{0,15})[\]\)]\s+(.+)$", t)
    if m_head:
        head_tok = m_head.group(1).strip()
        if not re.search(r"\d+(?:\.\d+)?\s*(?:인치|inch|kg|g|ml|l|w|gb|tb)$", head_tok, re.IGNORECASE):
            t = f"[{head_tok}] {m_head.group(2).strip()}"

    # 통화 기호/백슬래시(반드시 뒤에 숫자가 오는 경우만) 및 게시판 지름 꼬리표 절삭
    t = re.sub(r"[\s/]*(?:[￦₩💰]|\\(?=\s*\d)).*$", "", t)
    t = re.sub(r"[\s/]*(?:🚚|퀘이사존/지름|지름&할인정보).*$", "", t)

    # 후행 가격/배송비 괄호 반복 제거 (예: '(15.6인치) (89,000원/무배)' -> '(15.6인치)')
    for _ in range(2):
        prev = t
        t = re.sub(
            r"\s*\([^)]*(?:"
            r"(?:[$€¥￥]\s*[0-9]+(?:\.[0-9]+)?)"
            r"|(?:(?:[0-9,]+|[0-9]+(?:\.[0-9]+)?)\s*(?:만\s*원?|천\s*원?|원|달러|불|엔|円|위안|유로|\$|€|¥|￥|USD|EUR|CNY|JPY|원\s*대|원\s*상당))"
            r"|(?:[0-9]{1,3}(?:,[0-9]{3})+\s*[/,|]\s*(?:무배|무료|무료배송|유배|배송비|[0-9,]+))"
            r")[^)]*\)\s*$",
            "",
            t,
            flags=re.IGNORECASE,
        )
        t = re.sub(
            r"\s*\([^)]*(?:무료배송|배송비|무배|유배|와우무료|와우무배|네멤무료|네멤무배|조건부무료|카드할인)[^)]*\)\s*$",
            "",
            t,
            flags=re.IGNORECASE,
        )
        if t == prev:
            break

    # 닫는 괄호가 누락된 후행 열린 괄호 제거 (예: '(16,680원/무료')
    t = re.sub(r"\s*\([^)]*$", "", t)
    t = re.sub(r"[\s\.,\-\/~]+$", "", t)
    t = re.sub(r"\s{2,}", " ", t).strip()
    return t or html.unescape(str(raw_title)).strip()


def normalize_shipping_fee(raw_fee: Optional[str], title_hint: str = "") -> str:
    """
    배송비 표기를 표준 규격('무료배송', '와우무료', '네멤무료', '조건부무료', '2,500원', '')으로 정규화합니다.
    """
    s = html.unescape(str(raw_fee or "")).replace("\xa0", " ").strip()
    s = re.sub(r"\s+", " ", s)

    def _classify_single(val_str: str) -> Optional[str]:
        if not val_str:
            return ""
        low = val_str.lower().strip()
        compact = re.sub(r"\s+", "", low)

        if low in ("-", ".", "정보 없음", "정보없음", "확인 필요", "확인필요", "없음", "none", "null", "미정"):
            return ""
        if "http://" in low or "https://" in low or "게시물입니다" in low:
            if any(k in low for k in ("무료배송", "무배", "배송비 무료", "배송비포함", "배송비 포함", "free shipping")):
                return "무료배송"
            return ""
        if len(val_str) > 35:
            if "와우" in compact and ("무료" in compact or "무배" in compact):
                return "와우무료"
            if ("멤버십" in compact or "네멤" in compact) and ("무료" in compact or "무배" in compact):
                return "네멤무료"
            if any(k in low for k in ("무료배송", "무배", "배송비 무료", "배송비포함", "배송비 포함", "free shipping")):
                return "무료배송"
            return ""

        if "와우" in compact and any(k in compact for k in ("무료", "무배", "0")):
            return "와우무료"
        if any(m in compact for m in ("멤버십", "네멤", "네이버멤버십", "플러스멤버십")) and any(
            k in compact for k in ("무료", "무배", "0")
        ):
            return "네멤무료"
        if "조건부" in compact or ("이상" in compact and ("무료" in compact or "무배" in compact)):
            return "조건부무료"

        if compact in (
            "0", "0원", "무", "무배", "무료", "무료배송", "배송비무료", "택배비무료",
            "배송비포함", "무료(포함)", "포함", "free", "freeshipping", "fs", "臾대즺", "臾대같",
        ):
            return "무료배송"
        if any(k in low for k in ("무료배송", "무배", "배송비 무료", "택배비 무료", "배송비 포함", "배송비포함", "free shipping", "臾대즺", "臾대같")):
            return "무료배송"

        if "$" in low or "달러" in low or "usd" in low:
            m_usd = re.search(r"([0-9]+(?:\.[0-9]+)?)", low)
            if m_usd:
                f_val = float(m_usd.group(1))
                return "무료배송" if f_val == 0 else f"${f_val:.2f}"

        m_krw = re.search(r"([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,6})", low)
        if m_krw:
            num = int(m_krw.group(1).replace(",", ""))
            if num == 0:
                return "무료배송"
            if 100 <= num <= 50000:
                return f"{num:,}원"
            return ""

        return None

    res = _classify_single(s)
    if res is None:
        res = s if len(s) <= 16 else ""

    if not res and title_hint:
        th = html.unescape(title_hint)
        if re.search(r"(?:와우\s*무배|와우\s*무료|쿠팡와우\s*0?\s*무료)", th, re.IGNORECASE):
            return "와우무료"
        if re.search(r"(?:네멤\s*무배|네멤\s*무료|멤버십\s*0?\s*무료)", th, re.IGNORECASE):
            return "네멤무료"
        if re.search(r"(?:무배|무료배송|/\s*(?:무료|무|0원?|free|fs)\s*(?:[\)\]]|$))", th, re.IGNORECASE):
            return "무료배송"

    return res or ""


def is_non_mall_label(label: Optional[str]) -> bool:
    if not label:
        return True
    cleaned = html.unescape(str(label)).strip()
    cleaned = re.sub(r"^[\[\(\{]+|[\]\)\}]+$", "", cleaned).strip()
    cleaned = re.sub(r"\s*공식(?:몰|스토어)?$", "", cleaned).strip()
    if not cleaned or len(cleaned) > 20:
        return True
    low = cleaned.lower()
    compact = re.sub(r"\s+", "", low)
    if compact in MALL_ALIAS_MAP or low in MALL_ALIAS_MAP:
        return False
    if low in _NON_MALL_EXACT or compact in _NON_MALL_EXACT:
        return True
    if any(sub in compact for sub in _NON_MALL_SUBSTRINGS):
        return True
    if re.match(r"^[0-9,.%\s원만천달러불위안엔유로$€¥￥~+\-/]+$", cleaned):
        return True
    # 모델명/스펙 형태 (예: 5070 Ti, 2026, RTX5080, 15.6인치) 배제
    if re.match(r"^(?:20\d{2}|rtx\s*\d+|gtx\s*\d+|rx\s*\d+|\d{3,5}\s*(?:ti|super|xt|x3d)?)$", low):
        return True
    return False


def infer_shop_from_url(raw_url: Optional[str], post_url: Optional[str] = "") -> str:
    if raw_url:
        unwrapped = unwrap_redirect_target_url(raw_url).lower()
        if unwrapped.startswith(("http://", "https://")) and not any(ch in unwrapped for ch in _COMMUNITY_HOSTS):
            try:
                host = urllib.parse.urlparse(unwrapped).netloc.lower()
            except Exception:
                host = unwrapped
            for dom, shop in _DOMAIN_SHOP_RULES:
                if host == dom or host.endswith("." + dom) or dom in unwrapped:
                    return shop
    if post_url and "id=ppomppu8" in post_url.lower():
        return "알리익스프레스"
    return ""


def _match_mall_alias(text: Optional[str]) -> str:
    if not text:
        return ""
    cleaned = html.unescape(str(text)).strip()
    cleaned = re.sub(r"^[\[\(\{]+|[\]\)\}]+$", "", cleaned).strip()
    cleaned = re.sub(r"\s*공식(?:몰|스토어)?$", "", cleaned).strip()
    if not cleaned:
        return ""
    compact = re.sub(r"\s+", "", cleaned).lower()
    if compact in MALL_ALIAS_MAP:
        return MALL_ALIAS_MAP[compact]
    if is_non_mall_label(cleaned):
        return ""
    for alias_k, canon_v in sorted(MALL_ALIAS_MAP.items(), key=lambda x: len(x[0]), reverse=True):
        if len(alias_k) < 2:
            continue
        if alias_k == "이마트" and "하이마트" in compact:
            continue
        if alias_k == "이마트몰" and "하이마트" in compact:
            continue
        if alias_k in compact:
            return canon_v
    return ""


def resolve_canonical_shop_name(
    raw_title: Optional[str] = "",
    ecommerce_url: Optional[str] = "",
    current_shop: Optional[str] = "",
    post_url: Optional[str] = "",
    default_empty: str = "",
) -> str:
    """
    쇼핑몰명 단일 정규화 함수:
    1) current_shop 별칭 매칭 (비쇼핑몰 단어 제외)
    2) 제목 선두 [말머리] 별칭 매칭 (비쇼핑몰 말머리 '[특가]', '[재입고]', '[광군제 공구]', '[잔여공구코드]' 등 원천 배제)
    3) ecommerce_url 도메인 역추론 (래퍼 URL 자동 언래핑 포함)
    4) 유효한 미등록 고유 쇼핑몰명 보존 (숫자/스펙/비쇼핑몰 단어 제외)
    """
    # 1) current_shop에서 표준 별칭 매칭
    matched_curr = _match_mall_alias(current_shop)
    if matched_curr:
        return matched_curr

    # 2) 제목 앞머리 [말머리] (오타 머리말 '쿠팡] ' 포함)에서 표준 별칭 매칭
    title_norm = sanitize_ingested_title(raw_title or "")
    brackets = re.findall(r"\[([^\]]{1,20})\]", title_norm[:65])
    for br in brackets:
        matched_br = _match_mall_alias(br)
        if matched_br:
            return matched_br

    # 3) ecommerce_url 도메인 역추론
    url_shop = infer_shop_from_url(ecommerce_url, post_url=post_url)
    if url_shop:
        return url_shop

    # 4) current_shop이 유효한 고유 몰명인 경우 보존
    if current_shop:
        cs = html.unescape(str(current_shop)).split("[")[0].strip()
        if cs and not is_non_mall_label(cs) and len(cs) <= 12:
            return cs

    # 5) 제목 첫 번째 대괄호가 유효한 고유 몰명(숫자 미포함, 예: [소프라노몰], [한성컴퓨터])인 경우 보존
    for br in brackets[:1]:
        cand = re.sub(r"\s*공식(?:몰|스토어)?$", "", br.strip()).strip()
        if cand and not is_non_mall_label(cand) and len(cand) <= 10:
            if not any(ch.isdigit() for ch in cand if cand not in ("11번가", "29CM")):
                return cand

    return default_empty


def is_micro_point_deal(price: int, raw_title: str) -> bool:
    if not (1 <= int(price or 0) < 100):
        return False
    t = html.unescape(raw_title or "")
    if not any(kw in t for kw in _MICRO_POINT_KEYWORDS):
        return False
    return bool(re.search(rf"(?<![0-9,]){int(price)}\s*원", t))


def is_collection_dummy_price(price: int, raw_title: str, currency: str = "KRW") -> bool:
    p = int(price or 0)
    if (currency or "KRW").upper() != "KRW" or p not in COLLECTION_DUMMY_PRICES:
        return False
    t = html.unescape(raw_title or "")
    if is_micro_point_deal(p, t):
        return False
    return bool(_COLLECTION_TITLE_RE.search(t))


def is_pure_coupon_guide_post(raw_title: str, body_text: str = "", extracted_price: int = 0) -> bool:
    t = html.unescape(raw_title or "").strip()
    if not t:
        return False
    has_title_price = bool(
        re.search(
            r"(?:[1-9][\d,]*\s*(?:만\s*원?|천\s*원?|원|달러|위안|엔|유로)|[$€¥￥]\s*[1-9][\d,.]*)",
            t,
            re.IGNORECASE,
        )
    )
    if has_title_price:
        return False

    explicit_guide_phrases = (
        "선착순 쿠폰", "쿠폰 받기", "쿠폰 미리", "쿠폰 받아두세요",
        "할인코드 모음", "할인코드 총정리", "할인코드 공유", "할인코드 안내",
        "프로모션 코드 모음", "프로모션 코드 안내", "쿠폰 총정리", "쿠폰 안내",
    )
    if any(p in t for p in explicit_guide_phrases):
        return True

    if any(k in t for k in ("할인코드", "프로모션 코드", "할인 코드")):
        # 본문 또는 스크래퍼에서 실제 상품 가격이 확인되고, 제목이 코드 단독 안내글로 끝나지 않으면 일반 상품 딜로 인정
        if re.search(r"(?:할인\s*코드|프로모션\s*코드|쿠폰)\s*(?:모음|정리|안내|공유)?\s*$", t):
            if extracted_price <= 0 and not body_text:
                return True
        if extracted_price > 0:
            return False
        if body_text:
            body_p, _ = extract_body_fallback_price(body_text, "")
            if body_p > 0:
                return False
        return True

    return False


def parse_price_string(
    price_str: Optional[str],
    fallback_currency: str = "KRW",
    allow_bare_number: bool = False,
    context_hint: str = "",
) -> tuple[int, str]:
    """
    단일 가격 문자열(테이블/목록 셀/토큰)에서 (정수 가격, 통화 코드)를 추출합니다.
    - '12,900원 (무배)'처럼 유효 가격과 배송비 표기가 함께 있을 때 '무배' 때문에 0원으로 증발하지 않습니다.
    - '불'은 반드시 숫자 뒤의 통화 표기('\\d\\s*불(?!가|량|능)')일 때만 USD로 인정합니다.
    - '¥'/'위안'/'CNY'는 CNY(*100 전 단위), '엔'/'円'/'JPY'는 JPY(정수 엔 단위), '€'/'유로'는 EUR(*100 센트 단위)로 변환합니다.
    """
    if not price_str:
        return 0, fallback_currency
    s = html.unescape(str(price_str)).replace("\xa0", " ").strip()
    if not s:
        return 0, fallback_currency

    # 유럽식/오타 소수점 정규화 ($51,91 -> $51.91, 단 1,000$ 천단위는 유지)
    s = re.sub(r"(\d+),(\d{1,2})\s*(\$|달러|usd|유로|€|위안|cny)", r"\1.\2\3", s, flags=re.IGNORECASE)
    s = re.sub(r"((?:\$|€|¥|￥)\s*\d+),(\d{1,2})(?!\d)", r"\1.\2", s)

    # 단위당 가격(100g당 990원) 및 배송비 부속 문구((무배), 무료배송) 선제 제거 후 가격 판별
    s_clean = _UNIT_PRICE_RE.sub(" ", s)
    s_no_ship = re.sub(
        r"\(\s*(?:무배|무료배송|무료|와우무배|와우무료|네멤무배|네멤무료|배송비\s*무료)\s*\)|무료배송|무배|배송비\s*무료",
        " ",
        s_clean,
        flags=re.IGNORECASE,
    ).strip()

    ctx_low = f"{s_no_ship} {context_hint}".lower()

    # 1) 외화 명시 기호/단위 판별 (USD / EUR / JPY / CNY)
    is_usd = bool(
        "$" in s_no_ship
        or re.search(r"\busd\b|달러", s_no_ship, re.IGNORECASE)
        or re.search(r"\d\s*불(?!가|량|능|편|안|발|포함|투명|필요|완전|만)", s_no_ship)
    )
    is_eur = bool("€" in s_no_ship or re.search(r"\beur\b|유로", s_no_ship, re.IGNORECASE))
    is_jpy = bool(
        re.search(r"\bjpy\b|엔(?!진|터|드|젤)|円", s_no_ship, re.IGNORECASE)
        or (("¥" in s_no_ship or "￥" in s_no_ship) and any(jp in ctx_low for jp in ("amazon.co.jp", "아마존재팬", "일본", "라쿠텐", "jpy")))
    )
    is_cny = bool(
        not is_jpy
        and (
            "¥" in s_no_ship
            or "￥" in s_no_ship
            or re.search(r"\b(?:cny|rmb)\b|위안", s_no_ship, re.IGNORECASE)
        )
    )

    if is_usd:
        m = re.search(r"([0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?)", s_no_ship)
        if m:
            val = float(m.group(1).replace(",", ""))
            return (round(val * 100), "USD") if val > 0 else (0, "USD")

    if is_eur:
        m = re.search(r"([0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?)", s_no_ship)
        if m:
            val = float(m.group(1).replace(",", ""))
            return (round(val * 100), "EUR") if val > 0 else (0, "EUR")

    if is_cny:
        m = re.search(r"([0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?)", s_no_ship)
        if m:
            val = float(m.group(1).replace(",", ""))
            return (round(val * 100), "CNY") if val > 0 else (0, "CNY")

    if is_jpy:
        m = re.search(r"([0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?)", s_no_ship)
        if m:
            val = float(m.group(1).replace(",", ""))
            return (int(round(val)), "JPY") if val > 0 else (0, "JPY")

    # 2) 원화 '만' / '천원' / '원' 단위
    m_man_chun = re.search(r"([0-9]+(?:\.[0-9]+)?)\s*만\s*([0-9]+(?:\.[0-9]+)?)\s*천\s*원?", s_no_ship)
    if m_man_chun:
        val = int(round(float(m_man_chun.group(1)) * 10000 + float(m_man_chun.group(2)) * 1000))
        return val, "KRW"

    if "만" in s_no_ship and not re.search(r"만\s*(?:mAh|개|명|원\s*이상)", s_no_ship, re.IGNORECASE):
        m_man = re.search(r"(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)\s*만", s_no_ship)
        if m_man:
            val = int(round(float(m_man.group(1).replace(",", "")) * 10000))
            return val, "KRW"

    m_chun = re.search(r"([0-9]+(?:\.[0-9]+)?)\s*천\s*원", s_no_ship)
    if m_chun:
        return int(round(float(m_chun.group(1)) * 1000)), "KRW"

    if any(c in s_no_ship for c in ("원", "₩", "￦")):
        m_won = re.search(r"([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)", s_no_ship)
        if m_won:
            val = int(m_won.group(1).replace(",", ""))
            if any(x in s_no_ship.lower() for x in ("0원", "무료", "공짜", "나눔", "free")) and val == 0:
                return 0, "KRW"
            return val, "KRW"

    # 명시적 무료 딜 키워드 (숫자 가격이 전혀 없을 때만 0 처리)
    if any(x in s_no_ship.lower() for x in ("무료", "공짜", "나눔", "free", "0원")):
        return 0, "KRW"

    if fallback_currency == "KRW" and re.match(r"^[1-9]\d{0,2}(?:,\d{3})+$", s_no_ship):
        return int(s_no_ship.replace(",", "")), "KRW"

    if allow_bare_number:
        m_bare = re.search(r"([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]+)?)", s_no_ship)
        if m_bare:
            tok = m_bare.group(1).replace(",", "")
            if "." in tok:
                if fallback_currency in ("USD", "EUR", "CNY"):
                    return round(float(tok) * 100), fallback_currency
                return 0, fallback_currency
            val = int(tok)
            if fallback_currency in ("USD", "EUR", "CNY"):
                return val * 100, fallback_currency
            return val, fallback_currency

    return 0, fallback_currency


# 우측부터 탐색할 가격/배송비 괄호 후보 (닫는 괄호가 잘린 후행 괄호 '(16,680원/무료' 포함)
_PAREN_CANDIDATE_RE = re.compile(
    r"([\(\[])([^\(\)\[\]]{1,60})(?:[\)\]]|$)"
)

_PRICE_PREFIX_MODIFIERS_RE = re.compile(
    r"^(?:최저가?|체감가?|쿠폰가?|할인가?|최종가?|혜택가?|실구매가?|공구가?|행사가?|결제가?|"
    r"카페머|카할|네멤|유클|와우|토스|페이코|삼카|신카|국민|롯데|현대|농협|약)\s*[:：]?\s*",
    re.IGNORECASE,
)


def _parse_paren_candidate(inner_raw: str, is_unclosed_end: bool = False, context_hint: str = "") -> tuple[int, str, str]:
    """
    단일 괄호 내부 문자열에서 (price, currency, shipping_fee)를 추출합니다.
    - 단순 공백 구분자를 불허하고 '[/,|]' 또는 명시적 배송비 키워드만 허용합니다.
    - 통화 기호 없는 소수점('15.6인치', '3.5mm')은 절대 USD로 추정하지 않습니다.
    - '[2026 신형]', '[5070 Ti]' 등 연도/모델명 대괄호를 100% 차단합니다.
    """
    s = inner_raw.strip()
    if not s:
        return 0, "KRW", ""

    # 순수 스펙/단위/수량 괄호 즉시 배제: (15.6인치), (3.5mm), (500ml x 24페트), (RTX 5070 Ti), (2026 신형)
    if re.search(
        r"^\d+(?:\.\d+)?\s*(?:인치|inch|mm|cm|m|kg|g|mg|ml|l|gb|tb|mb|w|v|hz|mah|개입|개|입|팩|봉|병|캔|구|미|호|일분|개월|년|세대)\b",
        s,
        re.IGNORECASE,
    ) and not re.search(r"(?:원|만|달러|\$|€|¥|￥|위안|엔|유로|[/,|])", s):
        return 0, "KRW", ""

    if re.match(r"^(?:202\d|19\d{2})\s*[년형]?\b", s) and not re.search(r"(?:원|만|달러|\$|€|¥|￥|위안|엔|[/,|])", s):
        return 0, "KRW", ""

    if re.match(r"^\d{3,5}\s*(?:ti|super|xt|x3d|fe|pro|max|plus|ultra|mini)\b", s, re.IGNORECASE):
        return 0, "KRW", ""

    # 수식어 반복 제거 (예: '체감가 약 49,000원/무배' -> '49,000원/무배')
    cleaned = s
    for _ in range(3):
        nxt = _PRICE_PREFIX_MODIFIERS_RE.sub("", cleaned).strip()
        if nxt == cleaned:
            break
        cleaned = nxt

    # 가격 파트와 배송비 파트 분리:
    # 1) '/' 또는 '|' 구분자 분리
    # 2) 천단위 숫자 구분자가 아닌 ',' 구분자 분리 (예: '19,900원, 무배', '$19.99, FS')
    # 3) 구분자 없이 공백으로 이어진 경우, 오직 명시적 배송비 키워드('무배', '무료배송', '배송비...' 등)로 시작할 때만 분리!
    price_part = cleaned
    ship_part = ""

    m_sep = re.match(r"^([^/|]+?)\s*[/|]\s*(.+)$", cleaned)
    if m_sep:
        price_part = m_sep.group(1).strip()
        ship_part = m_sep.group(2).strip()
    else:
        m_comma = re.match(r"^(.+?)\s*,\s*(?![0-9]{3}(?:\D|$))(.+)$", cleaned)
        if m_comma and (
            _EXPLICIT_SHIPPING_KEYWORD_RE.search(m_comma.group(2))
            or m_comma.group(2).strip().lower() in ("무", "0", "0원", "무배", "무료", "무료배송", "free", "fs", "다양")
        ):
            price_part = m_comma.group(1).strip()
            ship_part = m_comma.group(2).strip()
        else:
            m_ship_kw = re.match(
                r"^(.+?)\s+((?:무배|무료배송|무료|유배|유료배송|배송비[^\)\]]*|택배비?[^\)\]]*|와우무료|와우무배|네멤무료|네멤무배|조건부무료|free(?:\s*shipping)?|fs))$",
                cleaned,
                re.IGNORECASE,
            )
            if m_ship_kw:
                price_part = m_ship_kw.group(1).strip()
                ship_part = m_ship_kw.group(2).strip()

    # 가격 파트 뒤쪽 '외', '부터', '~', '각', '전후' 접미어 정리
    price_part_core = re.sub(r"\s*(?:외(?:\s*\d+\s*종)?|부터|~|각|전후|대)\s*$", "", price_part).strip()

    # 후행 잔여 텍스트 검증: 숫자/통화 뒤에 일반 명사('신형', 'Ti', '모니터' 등)가 붙어 있으면 가격 괄호가 아님!
    valid_price_token_re = re.compile(
        r"^(?:"
        r"(?:[$€¥￥]|USD|EUR|CNY|RMB|JPY)\s*[0-9]{1,6}(?:,[0-9]{3})*(?:\.[0-9]+)?"
        r"|[0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]+)?\s*(?:만\s*원?|천\s*원?|원|달러|불|유로|위안|엔|円|\$|€|¥|￥|USD|EUR|CNY|RMB|JPY)?"
        r"|[0-9]+(?:\.[0-9]+)?\s*(?:만\s*(?:[0-9]+\s*천\s*)?원?|천\s*원|원|달러|불|유로|위안|엔|円|\$|€|¥|￥|USD|EUR|CNY|RMB|JPY)"
        r"|[0-9]{4,7}"
        r")$",
        re.IGNORECASE,
    )
    if not valid_price_token_re.match(price_part_core):
        return 0, "KRW", ""

    has_explicit_unit = bool(
        re.search(
            r"(?:원|만|천\s*원|달러|\d\s*불(?!가|량|능)|유로|위안|엔|円|\$|€|¥|￥|\b(?:USD|EUR|CNY|RMB|JPY)\b)",
            price_part_core,
            re.IGNORECASE,
        )
    )
    has_valid_ship = bool(
        ship_part
        and (
            _EXPLICIT_SHIPPING_KEYWORD_RE.search(ship_part)
            or ship_part.lower() in ("무", "0", "0원", "무배", "무료", "무료배송", "free", "fs", "다양")
            or re.match(r"^(?:[$€¥]?\s*[0-9,.]+\s*(?:원|달러|위안|엔)?|배송비\s*[0-9,]+원?)$", ship_part, re.IGNORECASE)
        )
    )

    # 닫히지 않은 후행 괄호는 반드시 통화 단위가 있거나 유효한 배송비 슬래시가 있어야만 인정
    if is_unclosed_end and not (has_explicit_unit or has_valid_ship):
        return 0, "KRW", ""

    # 통화 단위가 없는 순수 숫자인 경우: 콤마 포맷(899,000)이거나 배송비 슬래시(46638/무)가 있거나 5자리 이상일 때만 KRW 인정 (2024~2029 연도 배제)
    if not has_explicit_unit:
        digits_only = price_part_core.replace(",", "")
        if "." in digits_only or not digits_only.isdigit():
            return 0, "KRW", ""
        num_val = int(digits_only)
        if 1990 <= num_val <= 2035 and not has_valid_ship:
            return 0, "KRW", ""
        if not ("," in price_part_core or has_valid_ship or len(digits_only) >= 5):
            return 0, "KRW", ""
        parsed_price, parsed_curr = num_val, "KRW"
    else:
        parsed_price, parsed_curr = parse_price_string(
            price_part_core, fallback_currency="KRW", allow_bare_number=False, context_hint=context_hint
        )

    norm_ship = normalize_shipping_fee(ship_part) if ship_part else ""
    return parsed_price, parsed_curr, norm_ship


def parse_title_price_and_shipping(raw_title: str) -> tuple[int, str, str, bool]:
    """
    제목에서 (price, currency, shipping_fee, is_explicit_free_deal)을 단일 진실 공급원으로 정밀 추출합니다.
    1) (10/13 오전10:30) 등 날짜+시간 괄호 및 100g당 단위가격 선제 마스킹
    2) 우측(뒤쪽) 괄호 우선 탐색(reversed(finditer)), 닫는 괄호 누락 '(16,680원/무료' 지원
    3) 괄호 외 만/원/슬래시/외화($, €, ¥/위안, 엔) 파싱
    """
    if not raw_title:
        return 0, "KRW", "", False

    sanitized = html.unescape(str(raw_title)).replace("\xa0", " ").strip()
    sanitized = _PLACEHOLDER_COMMA_RE.sub("", sanitized)
    sanitized = _SEED_DUMMY_TAIL_RE.sub("", sanitized)

    masked = _UNIT_PRICE_RE.sub(" ", sanitized)
    masked = _DATE_TIME_PAREN_RE.sub(" ", masked)

    has_nonzero_price_in_title = bool(
        re.search(
            r"[1-9][\d,.]*\s*(?:만\s*원?|천\s*원?|원|달러|\d*불(?!가|량|능)|유로|위안|엔|円|€|\$|¥|￥)"
            r"|(?:[$€¥￥])\s*[1-9][\d,.]*",
            masked,
            re.IGNORECASE,
        )
    )
    is_explicit_free = bool(
        re.search(r"\(\s*0\s*원\s*\)|무료\s*배포|한정\s*무료|일시\s*무료|무료\s*\(유료|100%\s*할인", sanitized, re.IGNORECASE)
        or (re.search(r"\(\s*(?:무료|free)\s*\)", sanitized, re.IGNORECASE) and not has_nonzero_price_in_title)
    )

    title_price = 0
    title_curr = "KRW"
    title_ship = ""

    # 1. 우측(뒤쪽) 괄호/대괄호 후보부터 역순 탐색
    candidates = list(_PAREN_CANDIDATE_RE.finditer(masked))
    for m in reversed(candidates):
        full_m = m.group(0)
        is_unclosed = not full_m.endswith((")", "]"))
        p_val, p_curr, p_ship = _parse_paren_candidate(
            m.group(2), is_unclosed_end=is_unclosed, context_hint=sanitized
        )
        if p_ship and not title_ship:
            title_ship = p_ship
        if p_val > 0:
            title_price = p_val
            title_curr = p_curr
            if p_ship:
                title_ship = p_ship
            break

    # 2. 괄호 밖 가격 표기 탐색 (우측 가격 우선)
    if title_price == 0 and not is_explicit_free:
        # 2-a) 외화 기호/단위 ($ 17.97, 17.97달러, ¥189, 189위안, 3,980엔, €49.50)
        m_usd_all = list(
            re.finditer(
                r"(?:\$\s*(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)|(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)\s*(?:달러|USD|불(?!가|량|능|편|안|발|포함|빛)))",
                masked,
                re.IGNORECASE,
            )
        )
        m_eur_all = list(
            re.finditer(
                r"(?:€\s*(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)|(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)\s*(?:유로|EUR))",
                masked,
                re.IGNORECASE,
            )
        )
        m_cny_all = list(
            re.finditer(
                r"(?:[¥￥]\s*(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)|(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)\s*(?:위안|CNY|RMB))",
                masked,
                re.IGNORECASE,
            )
        )
        m_jpy_all = list(
            re.finditer(
                r"(?:JPY\s*(\d(?:[\d,]*\d)?)|(\d(?:[\d,]*\d)?)\s*(?:엔(?!진|터|드|젤)|円|JPY))",
                masked,
                re.IGNORECASE,
            )
        )

        # 만원 / 원화 패턴
        m_man_chun = re.search(
            r"(?<!보배\s)(?<!보조배터리\s)(?<![0-9Ww]\s)([0-9]+)\s*만\s*([0-9]+)\s*천\s*원?",
            masked,
        )
        m_man = re.search(
            r"(?<!보배\s)(?<!보조배터리\s)(?<![0-9Ww]\s)(\d(?:[\d,]*\d)?(?:\.[\d]+)?)\s*만(?!\s*(?:원\s*)?(?:할인|적립|쿠폰|캐시백|페이백|이상|권|mAh|mah|암페어|개|명))",
            masked,
        )
        if m_man and ("$" in masked and "만원" not in masked and "만 원" not in masked):
            m_man = None

        won_matches = list(
            re.finditer(
                r"(?<![+\-\d,])(?<!\d\s~\s)(?<!~\s)([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,7}|[1-9][0-9]?)\s*원"
                r"(?!\s*(?:할인|적립\s*쿠폰|쿠폰|캐시백|페이백|청구|즉시|추가|이상|권))",
                masked,
            )
        )

        if m_man_chun:
            title_price = int(m_man_chun.group(1)) * 10000 + int(m_man_chun.group(2)) * 1000
            title_curr = "KRW"
        elif m_man:
            title_price = int(round(float(m_man.group(1).replace(",", "")) * 10000))
            title_curr = "KRW"
        elif won_matches:
            # 우측(최종 할인가) 우선 선택하되 100원 미만은 소액 적립 딜일 때만 허용
            for wm in reversed(won_matches):
                cand_w = int(wm.group(1).replace(",", ""))
                if cand_w >= 100 or is_micro_point_deal(cand_w, sanitized):
                    title_price = cand_w
                    title_curr = "KRW"
                    break
        elif m_usd_all:
            val_s = m_usd_all[-1].group(1) or m_usd_all[-1].group(2)
            title_price = round(float(val_s.replace(",", "")) * 100)
            title_curr = "USD"
        elif m_eur_all:
            val_s = m_eur_all[-1].group(1) or m_eur_all[-1].group(2)
            title_price = round(float(val_s.replace(",", "")) * 100)
            title_curr = "EUR"
        elif m_jpy_all:
            val_s = m_jpy_all[-1].group(1) or m_jpy_all[-1].group(2)
            title_price = int(val_s.replace(",", ""))
            title_curr = "JPY"
        elif m_cny_all:
            val_s = m_cny_all[-1].group(1) or m_cny_all[-1].group(2)
            if any(jp in sanitized.lower() for jp in ("amazon.co.jp", "아마존재팬", "라쿠텐")):
                title_price = int(round(float(val_s.replace(",", ""))))
                title_curr = "JPY"
            else:
                title_price = round(float(val_s.replace(",", "")) * 100)
                title_curr = "CNY"
        else:
            m_slash = re.search(
                r"(?<!\d)([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,7})\s*원?\s*(?:외|부터|~)?\s*"
                r"(?:[/,]\s*(무배|무료|무료배송|무|유배|유료|택배|와우|와우무료|네멤무료|배송비|[0-9,]{3,5}\s*원?)|\s+(무배|무료배송))",
                masked,
            )
            if m_slash:
                cand_sl = int(m_slash.group(1).replace(",", ""))
                if not (1990 <= cand_sl <= 2035 and not m_slash.group(2)):
                    title_price = cand_sl
                    title_curr = "KRW"
                    raw_sf = m_slash.group(2) or m_slash.group(3) or ""
                    norm_sf = normalize_shipping_fee(raw_sf)
                    if norm_sf:
                        title_ship = norm_sf

    if not title_ship:
        title_ship = normalize_shipping_fee("", title_hint=sanitized)

    if is_explicit_free:
        return 0, "KRW", title_ship, True

    if title_price > 0 and is_collection_dummy_price(title_price, sanitized, title_curr):
        return 0, "KRW", title_ship, False

    return title_price, title_curr, title_ship, False


def extract_body_fallback_price(body_text: Optional[str], title_text: str = "") -> tuple[int, str]:
    """
    스크래퍼 상세 본문에서 대표 가격을 폴백 추출할 때 사용합니다.
    - 제목에 유효 가격이 있으면 제목 가격을 최우선 반환합니다.
    - 본문 내 URL, 단위당 가격(100g당), 정가/기존가/배송비/추가금(소스 추가 200원 등)/할인액을 선제 마스킹합니다.
    """
    if title_text:
        tp, tc, _, is_free = parse_title_price_and_shipping(title_text)
        if is_free:
            return 0, "KRW"
        if tp > 0:
            return tp, tc

    if not body_text:
        return 0, "KRW"

    cleaned = html.unescape(str(body_text))
    cleaned = re.sub(r"\[첨부된 이미지 링크들\][\s\S]*$", " ", cleaned)
    cleaned = re.sub(r"\[상품이미지\s*:[^\]]*\]", " ", cleaned)
    cleaned = re.sub(r"https?://[^\s\)\]\'\"<>]+", " ", cleaned)
    cleaned = _UNIT_PRICE_RE.sub(" ", cleaned)
    cleaned = _DATE_TIME_PAREN_RE.sub(" ", cleaned)
    cleaned = _BODY_SURCHARGE_PAREN_RE.sub(" ", cleaned)
    cleaned = _BODY_CONTEXT_PREFIX_MASK_RE.sub(" ", cleaned)
    cleaned = _BODY_CONTEXT_SUFFIX_MASK_RE.sub(" ", cleaned)

    # 외화 표기 탐색
    m_usd = re.search(
        r"(?:\$\s*(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)|(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)\s*(?:달러|USD|불(?!가|량|능|편|안|발|포함|빛)))",
        cleaned,
        re.IGNORECASE,
    )
    if m_usd:
        val = float((m_usd.group(1) or m_usd.group(2)).replace(",", ""))
        if 0.5 <= val <= 50000:
            return round(val * 100), "USD"

    m_eur = re.search(
        r"(?:€\s*(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)|(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)\s*(?:유로|EUR))",
        cleaned,
        re.IGNORECASE,
    )
    if m_eur:
        val = float((m_eur.group(1) or m_eur.group(2)).replace(",", ""))
        if 0.5 <= val <= 50000:
            return round(val * 100), "EUR"

    m_cny = re.search(
        r"(?:[¥￥]\s*(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)|(\d(?:[\d,]*\d)?(?:\.[0-9]+)?)\s*(?:위안|CNY|RMB))",
        cleaned,
        re.IGNORECASE,
    )
    if m_cny:
        val = float((m_cny.group(1) or m_cny.group(2)).replace(",", ""))
        if 1 <= val <= 500000:
            return round(val * 100), "CNY"

    m_jpy = re.search(
        r"(?:JPY\s*(\d(?:[\d,]*\d)?)|(\d(?:[\d,]*\d)?)\s*(?:엔(?!진|터|드|젤)|円|JPY))",
        cleaned,
        re.IGNORECASE,
    )
    if m_jpy:
        val = int((m_jpy.group(1) or m_jpy.group(2)).replace(",", ""))
        if 50 <= val <= 2000000:
            return val, "JPY"

    for m_won in re.finditer(r"([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,8})\s*원", cleaned):
        cand = int(m_won.group(1).replace(",", ""))
        if 500 <= cand <= 15000000:
            if not is_collection_dummy_price(cand, title_text, "KRW"):
                return cand, "KRW"

    return 0, "KRW"


KOR_ENG_SEARCH_SYNONYMS: dict[str, tuple[str, ...]] = {
    "갤럭시": ("galaxy",),
    "galaxy": ("갤럭시",),
    "버즈": ("buds",),
    "buds": ("버즈",),
    "폴드": ("fold",),
    "fold": ("폴드",),
    "플립": ("flip",),
    "flip": ("플립",),
    "워치": ("watch",),
    "watch": ("워치",),
    "애플": ("apple",),
    "apple": ("애플",),
    "아이폰": ("iphone",),
    "iphone": ("아이폰",),
    "아이패드": ("ipad",),
    "ipad": ("아이패드",),
    "맥북": ("macbook",),
    "macbook": ("맥북",),
    "맥미니": ("macmini",),
    "macmini": ("맥미니",),
    "에어팟": ("airpods",),
    "airpods": ("에어팟",),
    "삼성": ("samsung",),
    "samsung": ("삼성",),
    "엘지": ("lg",),
    "lg": ("엘지",),
    "로지텍": ("logitech",),
    "logitech": ("로지텍",),
    "지슈라": ("superlight", "gpro"),
    "샥즈": ("shokz",),
    "shokz": ("샥즈",),
    "오픈런": ("openrun",),
    "openrun": ("오픈런",),
    "오픈핏": ("openfit",),
    "openfit": ("오픈핏",),
    "오픈닷": ("opendots",),
    "opendots": ("오픈닷",),
    "레노버": ("lenovo",),
    "lenovo": ("레노버",),
    "샤오신": ("xiaoxin",),
    "xiaoxin": ("샤오신",),
    "샤오미": ("xiaomi",),
    "xiaomi": ("샤오미",),
    "다이슨": ("dyson",),
    "dyson": ("다이슨",),
    "로보락": ("roborock",),
    "roborock": ("로보락",),
    "드리미": ("dreame",),
    "dreame": ("드리미",),
    "유그린": ("ugreen",),
    "ugreen": ("유그린",),
    "베이스어스": ("baseus",),
    "baseus": ("베이스어스",),
    "앤커": ("anker",),
    "anker": ("앤커",),
    "아수스": ("asus", "에이수스"),
    "에이수스": ("asus", "아수스"),
    "asus": ("아수스", "에이수스"),
    "기가바이트": ("gigabyte",),
    "gigabyte": ("기가바이트",),
    "닌텐도": ("nintendo",),
    "nintendo": ("닌텐도",),
    "스위치": ("switch",),
    "switch": ("스위치",),
    "플스": ("ps5", "playstation"),
    "ps5": ("플스", "플레이스테이션"),
    "소니": ("sony",),
    "sony": ("소니",),
    "보스": ("bose",),
    "bose": ("보스",),
}


def build_search_keywords(
    raw_title: str,
    brand: str = "",
    base_name: str = "",
    model_code: str = "",
) -> str:
    """
    띄어쓰기 변형, 영숫자 분리/결합, 한/영 브랜드·시리즈 동의어를 결합한 검색 색인 문자열을 생성합니다.
    """
    src = html.unescape(f"{raw_title or ''} {brand or ''} {base_name or ''} {model_code or ''}").strip()
    if not src:
        return ""
    clean = re.sub(r"\[[^\]]*\]", " ", src)
    tokens: list[str] = []
    seen: set[str] = set()

    def _add(tok: str) -> None:
        t = tok.strip().lower()
        if len(t) >= 2 and t not in seen:
            seen.add(t)
            tokens.append(t)

    compact_title = re.sub(r"[\s\-_/]+", "", clean).lower()
    if 2 <= len(compact_title) <= 80:
        _add(compact_title)

    words = re.findall(r"[가-힣a-zA-Z0-9]+", clean.lower())
    for w in words:
        _add(w)
        # 영문+숫자 경계 분해 (예: rtx5070ti -> rtx, 5070, rtx5070)
        alnum_parts = re.findall(r"[a-z]+|\d+|[가-힣]+", w)
        if len(alnum_parts) >= 2:
            for p in alnum_parts:
                _add(p)
            if len(alnum_parts) >= 3:
                _add(alnum_parts[0] + alnum_parts[1])
        for key, syns in KOR_ENG_SEARCH_SYNONYMS.items():
            if key in w:
                for s in syns:
                    _add(s)

    joined = " ".join(tokens)
    return joined[:480]


POPULAR_KEYWORD_STOPWORDS: frozenset[str] = frozenset(
    {
        # 쇼핑몰 / 플랫폼 / 커머스 채널
        "쿠팡", "알리", "알리익스프레스", "aliexpress", "네이버", "네이버페이", "네페", "스마트스토어",
        "지마켓", "g마켓", "gmarket", "옥션", "11번가", "11마존", "롯데온", "티몬", "위메프",
        "카카오", "카카오톡", "톡딜", "카카오선물하기", "우체국", "무신사", "올리브영", "컴퓨존",
        "하이마트", "다이소", "다이소몰", "컬리", "마켓컬리", "오아시스", "오늘의집", "신세계",
        "신세계몰", "이마트", "이마트몰", "홈플러스", "코스트코", "쓱닷컴", "ssg", "타오바오",
        "테무", "temu", "아마존", "amazon", "이베이", "ebay", "큐텐", "qoo10", "스팀", "steam",
        "에픽", "배민", "배달의민족", "요기요", "쿠팡이츠", "토스", "토스쇼핑", "페이코",
        "인터파크", "예스24", "yes24", "교보문고", "알라딘", "동원몰", "cj더마켓", "더마켓",
        "그립", "보고", "패션플러스", "하프클럽", "에이블리", "지그재그", "크림", "kream",
        "넾다", "네쇼라", "쇼핑라이브", "뽐뿌", "펨코", "퀘이사존", "루리웹", "클리앙", "빠삭",
        # 배송 / 할인 / 핫딜 상투어
        "무료", "무배", "무료배송", "와우", "와우무배", "와우무료", "네멤", "네멤무배", "네멤무료",
        "배송", "배송비", "택배", "직배", "직구", "해외직구", "해외", "국내", "관부가세", "관세",
        "특가", "초특가", "핫딜", "역대가", "역대급", "최저가", "할인", "할인가", "추가할인",
        "즉시할인", "중복할인", "쿠폰", "쿠폰가", "코드", "할인코드", "프로모션", "체감", "체감가",
        "최종", "최종가", "혜택", "혜택가", "실구매가", "구매가", "정상가", "판매가",
        "카드", "카드할인", "카할", "청구할인", "적립", "포인트", "페이백", "캐시", "스마일캐시",
        "선착순", "타임딜", "원데이", "광군제", "블프", "빅스", "빅스마일", "빅스마일데이",
        "설빅세일", "썸머세일", "감사제", "기획전", "모음전", "모음", "이벤트", "행사", "공구",
        "공동구매", "예약", "사전예약", "재입고", "끌올", "품절", "마감", "종료", "임박", "한정",
        "한정수량", "단독", "공식", "정품", "인증", "국내정발", "정발", "자급제", "리퍼", "새상품",
        "이월", "클리어런스", "떨이", "처분", "랜덤", "라방", "라이브", "상품권", "기프티콘", "금액권",
        # 보관상태 / 원산지 / 일반 수식어 / 규격 노이즈
        "냉장", "냉동", "실온", "상온", "해동", "생물", "자숙", "훈제", "손질", "슬라이스", "세척",
        "무세척", "유기농", "무농약", "친환경", "무항생제", "국내산", "국산", "수입", "수입산",
        "미국산", "호주산", "칠레산", "노르웨이", "당일", "당일발송", "당일출고", "당일작업", "산지직송",
        "프리미엄", "오리지널", "클래식", "베이직", "스탠다드", "플러스", "울트라", "맥스", "미니",
        "라이트", "프로", "세트", "패키지", "구성", "묶음", "번들", "대용량", "소포장", "개별포장",
        "증정", "사은품", "덤증정", "본품", "리필", "교체용", "호환", "전용", "공용", "남성", "여성",
        "남녀공용", "아동", "키즈", "유아", "성인", "대형", "중형", "소형", "특대", "점보",
        "블랙", "화이트", "실버", "그레이", "네이비", "베이지", "브라운", "핑크", "블루", "그린",
        "옐로우", "레드", "골드", "투명", "혼합", "선택", "옵션", "종류", "다양", "인기", "추천",
        "베스트", "신상", "신제품", "출시", "기념", "최대", "최소", "이상", "이하", "미만", "초과",
        "포함", "제외", "별도", "기준", "적용", "가능", "불가", "필수", "전원", "무조건", "선물",
        "선물세트", "메가", "추가", "가격", "결제", "구매", "전용가", "회원", "멤버십",
        # 깨진 HTML 엔티티 잔재 및 URL 파편
        "amp", "nbsp", "quot", "apos", "lt", "gt", "039", "http", "https", "www", "com", "kr", "html", "php",
    }
)


def unescape_and_strip_html_entities(text: str) -> str:
    """이중 이스케이프 및 세미콜론 누락 깨진 HTML 엔티티(&amp;, &#039;, &nbsp; 등)를 안전하게 정화합니다."""
    if not text:
        return ""
    s = html.unescape(html.unescape(text))
    s = re.sub(r"&(?:#\d+|#x[0-9a-fA-F]+|[a-zA-Z]{2,8});?", " ", s)
    return s.replace("\xa0", " ")


def is_valid_popular_keyword(raw_kw: str) -> bool:
    """
    인기 검색어로 노출 가능한 순수 브랜드/상품 키워드인지 검증합니다.
    쇼핑몰명, 할인/배송 상투어, 보관 수식어(냉장/냉동), 깨진 HTML 엔티티, 숫자/단위/비율(100g, 2026, 1+1, 50%)을 차단합니다.
    """
    if not raw_kw or not isinstance(raw_kw, str):
        return False
    # 원본에 깨진 엔티티나 특수기호(&, +, %)가 포함된 경우 즉시 배제
    if "&" in raw_kw or "+" in raw_kw or "%" in raw_kw:
        return False
    kw = unescape_and_strip_html_entities(raw_kw).strip()
    if not (2 <= len(kw) <= 15):
        return False
    if not re.fullmatch(r"[가-힣a-zA-Z0-9]+", kw):
        return False
    # 숫자로 시작하는 모든 토큰(11번가, 100g, 500ml, 2026, 256gb, 3개입, 039 등) 및 순수 숫자 배제
    if re.match(r"^\d", kw) or kw.isdigit():
        return False
    if not re.search(r"[가-힣a-zA-Z]", kw):
        return False
    kw_low = kw.lower()
    if kw_low in POPULAR_KEYWORD_STOPWORDS:
        return False
    # 숫자+단위 접미 결합형(예: g500 등 비정상 패턴 포함) 및 가격 표기 잔재 배제
    if re.search(r"\d+(?:원|만원|천원|달러|불|위안|개|입|팩|봉|병|캔|박스|세트|종|kg|g|mg|ml|l|gb|tb|mb|mah|w|hz|mm|cm|인치)$", kw_low):
        return False
    if kw.endswith(("으로", "에서", "까지", "부터", "하기", "있는", "없는", "입니다")):
        return False
    return True


def _clean_body_text(raw_html: str) -> str:
    if not raw_html:
        return ""
    s = html.unescape(str(raw_html))
    s = re.sub(r"<[^>]+>", " ", s)
    s = re.sub(r"\[첨부된 이미지 링크들\][\s\S]*$", " ", s)
    s = re.sub(r"\[상품이미지\s*:[^\]]*\]", " ", s)
    s = re.sub(r"https?://[^\s\)\]\'\"<>]+", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def infer_currency_from_deal_context(
    raw_title: str,
    raw_price_int: int,
    current_currency: Optional[str] = "KRW",
    content_html: str = "",
    ecommerce_url: str = "",
) -> str:
    """
    제목에 가격 단위가 생략되고 본문에만 소수점 외화($82.93 등)가 적힌 해외직구 딜이
    서브유닛 정수(8293) 상태로 KRW로 잘못 태깅된 경우 실제 통화(USD/EUR/CNY)를 복원합니다.
    """
    curr = (current_currency or "KRW").strip().upper()
    if curr != "KRW" or not raw_price_int or raw_price_int < 100 or raw_price_int > 1_000_000:
        return curr

    title_txt = html.unescape(raw_title or "")
    # 제목에 명시적 원화 표기가 있으면 원화 딜로 유지
    if re.search(r"(?:\d\s*(?:만\s*원?|천\s*원?|원)|[₩￦]\s*\d)", title_txt):
        return "KRW"

    body_txt = _clean_body_text(content_html or "")
    if not body_txt:
        return curr

    # 본문에 동일한 숫자의 명시적 원화 표기(예: '176원', '8,293원')가 있으면 실제 원화 딜이므로 보존
    formatted_p = f"{raw_price_int:,}"
    if re.search(rf"(?<![0-9,])(?:{raw_price_int}|{re.escape(formatted_p)})\s*원", body_txt):
        return "KRW"

    # 1) 소수점 USD 표기($82.93, 82.93달러 등)와 센트 단위 정밀 일치 검사
    for m_usd in re.finditer(
        r"(?:\$\s*([0-9]{1,4}\.[0-9]{1,2})|([0-9]{1,4}\.[0-9]{1,2})\s*(?:\$|달러|불(?!가|량|능)|USD))",
        body_txt,
        re.IGNORECASE,
    ):
        usd_val = float(m_usd.group(1) or m_usd.group(2))
        if abs(round(usd_val * 100) - raw_price_int) <= 1:
            return "USD"

    # 해외몰 링크이면서 정수 달러($82 -> 8200)가 본문에 정확히 명시된 경우
    if raw_price_int % 100 == 0 and any(
        d in (ecommerce_url or "").lower() for d in ("aliexpress.", "amazon.", "ebay.", "newegg.")
    ):
        whole_usd = raw_price_int // 100
        if 5 <= whole_usd <= 5000 and re.search(
            rf"(?:\$\s*{whole_usd}(?!\.[0-9]|\d)|(?<!\d){whole_usd}\s*(?:\$|달러|USD))",
            body_txt,
            re.IGNORECASE,
        ):
            return "USD"

    # 2) 소수점 EUR 표기(€25.87, 25.87유로) 일치 검사
    for m_eur in re.finditer(
        r"(?:€\s*([0-9]{1,4}\.[0-9]{1,2})|([0-9]{1,4}\.[0-9]{1,2})\s*(?:€|유로|EUR))",
        body_txt,
        re.IGNORECASE,
    ):
        eur_val = float(m_eur.group(1) or m_eur.group(2))
        if abs(round(eur_val * 100) - raw_price_int) <= 1:
            return "EUR"

    # 3) 소수점 CNY 표기(¥189.00, 189.50위안) 일치 검사
    for m_cny in re.finditer(
        r"(?:[¥￥]\s*([0-9]{1,5}\.[0-9]{1,2})|([0-9]{1,5}\.[0-9]{1,2})\s*(?:위안|CNY|RMB))",
        body_txt,
        re.IGNORECASE,
    ):
        cny_val = float(m_cny.group(1) or m_cny.group(2))
        if abs(round(cny_val * 100) - raw_price_int) <= 1:
            return "CNY"

    return curr


def is_suspicious_price_history_value(
    price_val: int,
    currency: Optional[str] = "KRW",
    raw_title: str = "",
) -> bool:
    """
    PriceHistory 레코드가 과거 오파싱 잔재(<=0, 15,000,000 초과, 모음전 777원 더미, 비적립 초소액 10원 등)인지 판별합니다.
    """
    if not price_val or price_val <= 0 or price_val > 15_000_000:
        return True
    curr = (currency or "KRW").strip().upper()
    if is_collection_dummy_price(price_val, raw_title or "", curr):
        return True
    if curr == "KRW" and 0 < price_val < 100 and not is_micro_point_deal(price_val, raw_title or ""):
        return True
    return False


def renormalize_existing_deals(db_session: Any, dry_run: bool = False) -> dict[str, Any]:
    """
    운영 DB에 이미 적재된 오염 레코드(Deal 및 PriceHistory)를 멱등(Idempotent)하게 일괄 치유합니다.
    - 날짜 괄호 오파싱(10원), 모음전 더미 가격(777원/999원 등), 스펙/연도 오파싱(2026원/5070원/$15.60),
      본문 추가금 오파싱(200원), 원화 알리 딜의 USD*100 폭주($2,500 -> 2,500원),
      본문 달러 서브유닛의 KRW 오저장($82.93 -> USD 8293), 타오바오 위안화(189원 -> CNY 18900) 교정
    - PriceHistory 테이블 내 0원 딜 잔재(777원 등), 초소액 오파싱(10원), 스케일/통화 불일치 이상치 일괄 정화
    - 제목 내 '1,000단위 구분 쉼표', '(... \\d+w 택배배송)' 제거 (_post_link_title_uc 유니크 제약 안전 준수)
    - shop_name 표준화 및 도메인 역추론 (비쇼핑몰 말머리/'기타' 정화)
    - shipping_fee 표준화 ('와우무료', '네멤무료', '무료배송', '2,500원')
    """
    from backend.database.models import Deal, PriceHistory

    deals = db_session.query(Deal).order_by(Deal.id.asc()).all()
    occupied_pairs: set[tuple[str, str]] = {
        (d.post_link or "", d.title or "") for d in deals
    }

    stats = {
        "scanned": len(deals),
        "updated": 0,
        "title_fixed": 0,
        "price_fixed": 0,
        "currency_fixed": 0,
        "shop_fixed": 0,
        "shipping_fixed": 0,
        "price_history_cleaned": 0,
        "dry_run": bool(dry_run),
    }

    deal_state_map: dict[int, dict[str, Any]] = {}

    for deal in deals:
        changed = False
        orig_title = deal.title or ""
        new_title = sanitize_ingested_title(orig_title)

        if new_title and new_title != orig_title:
            pair_old = (deal.post_link or "", orig_title)
            pair_new = (deal.post_link or "", new_title)
            if pair_new not in occupied_pairs:
                occupied_pairs.discard(pair_old)
                occupied_pairs.add(pair_new)
                if not dry_run:
                    deal.title = new_title
                stats["title_fixed"] += 1
                changed = True
            else:
                new_title = orig_title

        effective_title = new_title or orig_title
        curr_price = extract_numeric_price(deal.price)
        curr_currency = (deal.currency or "KRW").strip().upper()
        body_html = deal.content_html or ""

        # 1) 쇼핑몰명 재정규화
        canon_shop = resolve_canonical_shop_name(
            raw_title=effective_title,
            ecommerce_url=deal.ecommerce_link or "",
            current_shop=deal.shop_name or "",
            post_url=deal.post_link or "",
            default_empty="",
        )
        old_shop = (deal.shop_name or "").strip()
        if canon_shop != old_shop:
            if canon_shop or is_non_mall_label(old_shop):
                if not dry_run:
                    deal.shop_name = canon_shop
                stats["shop_fixed"] += 1
                changed = True

        # 2) 배송비 재정규화 (닫는 괄호 누락 제목 힌트 포함)
        title_p, title_c, title_sf, is_free = parse_title_price_and_shipping(effective_title)
        raw_sf = deal.shipping_fee or ""
        norm_sf = normalize_shipping_fee(raw_sf, title_hint=effective_title)
        if not norm_sf and title_sf:
            norm_sf = title_sf
        if norm_sf != raw_sf.strip():
            if not dry_run:
                deal.shipping_fee = norm_sf
            stats["shipping_fixed"] += 1
            changed = True

        # 3) 가격 & 통화 오염 치유
        new_price = curr_price
        new_currency = curr_currency

        if is_free:
            new_price = 0
            new_currency = "KRW"
        elif is_collection_dummy_price(curr_price, effective_title, curr_currency):
            new_price = 0
            new_currency = "KRW"
        elif title_p > 0:
            # 3-a) 과거 KRW <= 10000 -> USD * 100 독소 휴리스틱 피해 복구 ([알리] 2,500원 -> USD 250000)
            if curr_currency == "USD" and title_c == "KRW" and curr_price == title_p * 100:
                new_price = title_p
                new_currency = "KRW"
            # 3-b) 타오바오/위안화(¥189) 딜이 KRW 189원으로 잘못 저장된 레코드 복구
            elif curr_currency == "KRW" and title_c in ("CNY", "USD", "EUR") and (
                curr_price == round(title_p / 100) or curr_price < 500
            ):
                new_price = title_p
                new_currency = title_c
            # 3-c) 닫는 괄호 누락 등으로 가격이 0이거나 날짜/연도/추가금(<= 500원)으로 잘못 들어간 경우 제목 가격으로 복원
            elif curr_price == 0 or (
                curr_currency == "KRW"
                and curr_price != title_p
                and (
                    (curr_price < 500 and not is_micro_point_deal(curr_price, effective_title))
                    or curr_price in (2024, 2025, 2026, 5060, 5070, 5080, 5090)
                )
            ):
                new_price = title_p
                new_currency = title_c
        else:
            # 제목에 가격이 없는 상태에서 오염된 가격 패턴 치유
            combined_hint = f"{effective_title} {body_html[:600]}"
            inferred_ctx_curr = infer_currency_from_deal_context(
                raw_title=effective_title,
                raw_price_int=curr_price,
                current_currency=curr_currency,
                content_html=body_html,
                ecommerce_url=deal.ecommerce_link or "",
            )
            if inferred_ctx_curr != curr_currency:
                new_currency = inferred_ctx_curr
            # 본문에 ¥/위안 또는 타오바오 링크가 있는데 KRW 저가(예: 189원)로 저장된 케이스 (ID=19198)
            elif (
                curr_currency == "KRW"
                and 1 <= curr_price <= 5000
                and (canon_shop == "타오바오" or re.search(r"[¥￥]\s*\d|\d\s*위안|\b(?:cny|rmb)\b", combined_hint, re.IGNORECASE))
                and not re.search(rf"(?<!\d){curr_price}\s*원", effective_title)
            ):
                m_cny_fix = re.search(
                    rf"(?:[¥￥]\s*({curr_price}(?:\.\d+)?)|({curr_price}(?:\.\d+)?)\s*(?:위안|CNY|RMB))",
                    combined_hint,
                    re.IGNORECASE,
                )
                if m_cny_fix:
                    cny_f = float(m_cny_fix.group(1) or m_cny_fix.group(2))
                    new_price = round(cny_f * 100)
                    new_currency = "CNY"
                elif canon_shop == "타오바오" and curr_price < 1000:
                    new_price = curr_price * 100
                    new_currency = "CNY"
            # 날짜 괄호(10원) / 소스 추가금(200원) 등 비정상 소액 KRW (< 500원, 소액 적립 딜 제외) 치유 (ID=16, ID=19394)
            elif curr_currency == "KRW" and 0 < curr_price < 500 and not is_micro_point_deal(curr_price, effective_title):
                fb_p, fb_c = extract_body_fallback_price(body_html, effective_title)
                new_price = fb_p
                new_currency = fb_c if fb_p > 0 else "KRW"
            # 스펙 소수점((15.6인치) -> USD 1560) 오파싱 치유
            elif (
                curr_currency == "USD"
                and curr_price > 0
                and not re.search(r"(?:\$|달러|USD|\d\s*불(?!가|량|능))", combined_hint, re.IGNORECASE)
                and re.search(rf"\(\s*{curr_price / 100:g}\s*(?:인치|inch|mm|cm|kg|l)\s*\)", effective_title, re.IGNORECASE)
            ):
                fb_p, fb_c = extract_body_fallback_price(body_html, "")
                new_price = fb_p
                new_currency = fb_c if fb_p > 0 else "KRW"

        new_price_str = str(new_price) if new_price > 0 else "0"
        if str(deal.price or "0") != new_price_str:
            if not dry_run:
                deal.price = new_price_str
            stats["price_fixed"] += 1
            changed = True
        if (deal.currency or "KRW").strip().upper() != new_currency:
            if not dry_run:
                deal.currency = new_currency
            stats["currency_fixed"] += 1
            changed = True

        if changed:
            stats["updated"] += 1

        deal_state_map[deal.id] = {
            "new_price": new_price,
            "new_currency": new_currency,
            "old_price": curr_price,
            "old_currency": curr_currency,
            "title": effective_title,
            "has_options": bool(getattr(deal, "has_options", False) or getattr(deal, "options_data", None)),
        }

    # 4) PriceHistory 오염 레코드 전수 정화 (0원 딜 더미 잔재, 초소액 오파싱 10원, 통화/단위 괴리 이상치)
    all_ph_rows = db_session.query(PriceHistory).order_by(PriceHistory.id.asc()).all()
    for ph in all_ph_rows:
        d_info = deal_state_map.get(ph.deal_id)
        if d_info is None:
            if not dry_run:
                db_session.delete(ph)
            stats["price_history_cleaned"] += 1
            continue

        d_price = d_info["new_price"]
        d_curr = d_info["new_currency"]
        d_title = d_info["title"]
        ph_val = extract_numeric_price(ph.price)

        should_remove = False
        if d_price <= 0 and not d_info["has_options"]:
            # 가격이 0원('가격 확인')인 딜에 매달린 과거 오파싱 히스토리(예: 777원 더미, 10원 등) 제거
            should_remove = True
        elif is_suspicious_price_history_value(ph_val, d_curr, d_title):
            should_remove = True
        elif d_price > 0 and not d_info["has_options"]:
            # 부모 딜이 치유되기 전의 오염 가격(예: 189원, 200원, 250000 USD)이거나 현재가와 3배 이상 괴리된 이종 통화 잔재(예: USD 2587 딜의 CNY 18900) 제거
            if (d_info["old_price"] != d_price or d_info["old_currency"] != d_curr) and ph_val == d_info["old_price"] and ph_val != d_price:
                should_remove = True
            elif ph_val < d_price * 0.25 or ph_val > d_price * 3.50:
                should_remove = True

        if should_remove:
            if not dry_run:
                db_session.delete(ph)
            stats["price_history_cleaned"] += 1

    if (stats["updated"] > 0 or stats["price_history_cleaned"] > 0) and not dry_run:
        db_session.commit()

    return stats


if __name__ == "__main__":
    import argparse
    import json
    import os
    import sys

    backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    project_root = os.path.dirname(backend_dir)
    for p in (project_root, backend_dir):
        if p not in sys.path:
            sys.path.insert(0, p)

    parser = argparse.ArgumentParser(description="Renormalize existing Deal rows in DB.")
    parser.add_argument("--apply", action="store_true", help="Commit changes (default is dry-run)")
    args = parser.parse_args()

    from backend.database.session import SessionLocal
    from backend.routers.community import invalidate_feed_api_cache

    session = SessionLocal()
    try:
        result = renormalize_existing_deals(session, dry_run=not args.apply)
        if args.apply:
            invalidate_feed_api_cache()
        print(json.dumps(result, ensure_ascii=False, indent=2))
    finally:
        session.close()
