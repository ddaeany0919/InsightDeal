import os
import asyncio
import logging
from sqlalchemy.orm import Session
from sqlalchemy.sql import func
from datetime import datetime, timezone
from backend.database.models import Deal, PriceHistory, Community
from backend.services.normalizer.llm_normalizer import LlmNormalizer
from backend.services.ai_product_name_service import AIProductNameService
import re

logger = logging.getLogger(__name__)

def _decode_and_clean_shop_url(u: str) -> str:
    if not u:
        return ""
    u = u.strip().rstrip(".,;)>]")
    # URL 끝단에 실수로 붙은 한글 텍스트나 달러 가격 태그($27.28 등) 절단
    u = re.split(r'[가-힣\s"\'<>]|(?:\$\d)', u)[0].strip().rstrip(".,;)>]")
    if "s.ppomppu.co.kr" in u:
        m = re.search(r"target=([^&]+)", u)
        if m:
            try:
                import base64
                b64 = m.group(1)
                pad = len(b64) % 4
                if pad:
                    b64 += "=" * (4 - pad)
                dec = base64.b64decode(b64).decode("utf-8", errors="ignore")
                if dec.startswith("http"):
                    u = dec
            except Exception:
                pass
    elif "link.php" in u and ("url=" in u or "ol=" in u):
        m = re.search(r"(?:url|ol)=([^&]+)", u)
        if m:
            try:
                import urllib.parse
                dec = urllib.parse.unquote(m.group(1))
                if dec.startswith("http"):
                    u = dec
            except Exception:
                pass
    u = re.split(r'[가-힣\s"\'<>]|(?:\$\d)', u)[0].strip().rstrip(".,;)>]")
    u = u.replace("/./", "/")
    return u


_FAKE_OPTION_NAME_RE = re.compile(
    r"^(?:[▪▫•◆◇★☆※#]+\s*)?(?:앱?기본가(?:격)?|최종가(?:격)?|최종혜택가|체감가(?:격)?|할인가(?:격)?|쿠폰가(?:격)?|정상가(?:격)?|판매가(?:격)?|행사가(?:격)?|공구가(?:격)?|혜택가(?:격)?|실구매가(?:격)?|앱사용가|판매자할인|스토어쿠폰|코인|코인할인|공구\s*코드|특가\s*코드|프로모션\s*코드|쿠폰\s*코드|할인\s*코드|스토어\s*코드|전용\s*코드|장바구니|결제시?|삼카시|신카시|국카시|현카시|롯카시|농카시|우카시|하카시|비카시|카할시|카드할인시?|청구할인시?|결제할인시?|네멤가?|와우가?|쿠폰적용가?|무료배송|배송비|택배비|합계|총\s*금액|스토어|채널|코드|링크|선착순|선착순\s*최대|멤버십|멤버십\s*할인|전체보기|바로가기|가게\s*리스트.*|오프라인\s*매장.*|0원|무료|kg|g|ml|l|개|입|미|팩|박스)$",
    re.IGNORECASE,
)

_PURE_QTY_OPTION_RE = re.compile(
    r"^\d+(?:\.\d+)?\s*(?:개|입|팩|봉|병|캔|통|박스|세트|롤|포|매|구|미|kg|g|ml|l)(?:\s*세트|\s*구성)?$",
    re.IGNORECASE,
)

_PROSE_NOISE_SUBSTRINGS = (
    "기본가", "앱기본가", "최종가", "기본가격", "앱사용가", "판매자할인", "스토어쿠폰", "코인할인",
    "리뷰 작성", "텍스트 리뷰", "포토/동영상", "네이버페이 포인트", "받으시고", "받으세요",
    "추천릴레이", "무료배송", "감사제", "기대는 안되긴", "미당 약", "종류들은", "제품이고",
    "(링크", "링크:", "결제 시", "할인코드", "장바구니 담고", "가격이 좋은데", "돈없어서", "작년에 똑같은거",
    "즉시 할인", "즉시할인", "카드 할인", "카드할인", "청구할인", "청구 할인", "추가 할인", "추가할인",
    "다운로드 쿠폰", "중복쿠폰", "기존가 대비", "댓글 달아놓으시면", "업데이트 해 드릴", "업데이트 확인하시면",
    "유튜버 코드", "채널 할인코드", "스토어 할인코드", "와드하시면", "미리 장바구니에",
    "순수하게", "결제기준", "스마일캐시", "신세계 상품권", "꼭멤버십", "파는데 프리미엄은",
    "맞는거 있으면", "탑승하시길", "금액대였던걸로", "나눠 볼 수", "쿠폰팩데이", "멤버십데이", "쿠폰 링크",
)


def infer_shop_name(raw_title: str, ecommerce_link: str = "", current_shop: str = "") -> str:
    """제목 앞머리의 [쇼핑몰] 대괄호 또는 구매 링크 도메인에서 쇼핑몰명을 정규화하여 추출합니다."""
    non_mall_brackets = {
        "끌올", "초특가", "특가", "알리특가", "알뜰특가", "코인딜", "골드박스", "핫딜", "직구", "해외직구", "역대급", "품절", "종료", "마감", "모음", "모음전",
        "광군제", "빅스마일", "빅스마일데이", "무료", "무배", "무료배송", "할인", "타임딜", "원두모음", "다이슨부품", "기타",
        "공동구매", "선착순", "쿠폰", "이벤트", "체험단", "앱테크", "적립", "국내핫딜", "해외핫딜"
    }
    mall_alias_map = {
        "지마켓": "G마켓", "g마켓": "G마켓", "gmarket": "G마켓",
        "옥션": "옥션", "11번가": "11번가", "쿠팡": "쿠팡", "롯데온": "롯데온", "롯데백화점": "롯데온",
        "네이버": "네이버", "스마트스토어": "네이버", "네쇼라": "네이버", "넾다세일": "네이버",
        "오늘의집": "오늘의집", "알리": "알리익스프레스", "알리익스프레스": "알리익스프레스",
        "티몬": "티몬", "위메프": "위메프", "인터파크": "인터파크", "ssg": "SSG", "쓱": "SSG", "신세계몰": "신세계몰", "이마트몰": "이마트몰",
        "무신사": "무신사", "29cm": "29CM", "토스": "토스", "토스쇼핑": "토스", "톡딜": "톡딜", "카카오": "카카오", "카카오톡딜": "톡딜", "카카오선물하기": "카카오",
        "우체국쇼핑": "우체국쇼핑", "우체국": "우체국쇼핑", "하이마트": "하이마트", "롯데하이마트": "하이마트",
        "cj온스타일": "CJ온스타일", "cjonstyle": "CJ온스타일", "gs샵": "GS샵", "gsshop": "GS샵", "gs25": "GS샵", "gs25앱": "GS샵",
        "현대hmall": "현대Hmall", "hmall": "현대Hmall", "컬리": "컬리", "마켓컬리": "컬리",
        "아마존": "아마존", "큐텐": "큐텐", "qoo10": "큐텐", "테무": "테무",
        "스팀": "스팀", "steam": "스팀", "에픽게임즈": "에픽게임즈", "에픽": "에픽게임즈",
        "올리브영": "올리브영", "다이소": "다이소몰", "다이소몰": "다이소몰", "코스트코": "코스트코", "홈플러스": "홈플러스",
        "lf몰": "LF몰", "lf스퀘어": "LF몰", "하프클럽": "하프클럽", "보리보리": "보리보리"
    }

    if current_shop and current_shop.strip() and current_shop.strip() not in ("정보 없음", "-", "기타", "None"):
        cs_clean = re.sub(r"\s+", "", current_shop.strip()).lower()
        if cs_clean in mall_alias_map:
            return mall_alias_map[cs_clean]
        for k, v in mall_alias_map.items():
            if k in cs_clean:
                return v
        if cs_clean not in non_mall_brackets:
            return current_shop.strip()

    if raw_title:
        m_br = re.match(r"^\s*\[([^\]]{1,16})\]", raw_title)
        if m_br:
            cand = m_br.group(1).strip()
            cand_clean = re.sub(r"\s+", "", cand).lower()
            if cand_clean not in non_mall_brackets:
                for k, v in mall_alias_map.items():
                    if k == cand_clean or k in cand_clean:
                        return v
                if len(cand) <= 10 and not any(ch.isdigit() for ch in cand if cand != "11번가" and cand.upper() != "29CM"):
                    return cand

    u = (ecommerce_link or "").lower()
    domain_map = [
        ("aliexpress.", "알리익스프레스"),
        ("gmarket.co.kr", "G마켓"),
        ("auction.co.kr", "옥션"),
        ("11st.co.kr", "11번가"),
        ("coupang.com", "쿠팡"),
        ("lotteon.com", "롯데온"),
        ("smartstore.naver.com", "네이버"),
        ("brand.naver.com", "네이버"),
        ("shopping.naver.com", "네이버"),
        ("ssg.com", "SSG"),
        ("ohou.se", "오늘의집"),
        ("musinsa.com", "무신사"),
        ("29cm.co.kr", "29CM"),
        ("store.kakao.com", "톡딜"),
        ("toss.im", "토스"),
        ("epost.go.kr", "우체국쇼핑"),
        ("e-himart.co.kr", "하이마트"),
        ("cjonstyle.com", "CJ온스타일"),
        ("gsshop.com", "GS샵"),
        ("hmall.com", "현대Hmall"),
        ("kurly.com", "컬리"),
        ("qoo10.", "큐텐"),
        ("amazon.", "아마존"),
        ("steampowered.com", "스팀"),
        ("epicgames.com", "에픽게임즈"),
        ("oliveyoung.co.kr", "올리브영"),
        ("daisomall.co.kr", "다이소몰"),
        ("costco.co.kr", "코스트코"),
        ("homeplus.co.kr", "홈플러스"),
    ]
    for dom, sname in domain_map:
        if dom in u:
            return sname
    return ""


def _clean_option_display_name(raw_name: str) -> str:
    if not raw_name:
        return ""
    n = re.sub(r"[\xa0\s]+", " ", str(raw_name)).strip()
    n = re.sub(r"^(?:[▪▫•◆◇★☆※#>\-]+\s*)+", "", n).strip()
    n = re.sub(r"^(?:\d{1,2}\s*[.)](?!\d)\s+|[\u2460-\u2473]\s*)", "", n).strip()
    n = re.sub(r"^\d{1,2}(?:\.\d+)?%\s*[/\u00B7\-|]\s*", "", n).strip()
    n = re.sub(r"^(?:vs\.?)\s+", "", n, flags=re.IGNORECASE).strip()
    while True:
        # 1) 여는 괄호 없이 닫는 대괄호/소괄호만 남은 머리말 (예: '광군제특가] ', '잔여공구코드] ', '알리 잔여코드) ') 제거
        nxt = re.sub(r"^[^\[\]\(\)]{1,14}[\]\)]\s*", "", n).strip()
        if nxt != n and nxt:
            n = nxt
            continue
        # 2) 수량/용량 스펙이 아닌 일반 머리말 대괄호/소괄호 (예: '[가격인하]', '[초특가]', '[알리]', '(끌올)') 제거
        m_lead = re.match(r"^(?:\[([^\]]{1,16})\]|\(([^\)]{1,16})\))\s*(.+)$", n)
        if m_lead:
            tag_txt = (m_lead.group(1) or m_lead.group(2) or "").strip()
            if not re.search(r"\d+\s*(?:개|입|팩|박스|세트|병|캔|봉|포|롤|매|kg|g|ml|l|w|인치|세대|년)", tag_txt, re.IGNORECASE):
                n = m_lead.group(3).strip()
                continue
        break
    # 끝단에 닫히지 않고 잘린 괄호 조각 (예: '(옵션', '[옵션', '(무료') 제거
    n = re.sub(r"\s*[\(\[][^\)\]]*$", "", n).strip()
    while n.endswith(")") and n.count(")") > n.count("("):
        n = n[:-1].strip()
    while n.endswith("]") and n.count("]") > n.count("["):
        n = n[:-1].strip()
    return n.strip(" -·:,/~+>")


def _is_valid_option_name(name: str) -> bool:
    if not name:
        return False
    n = _clean_option_display_name(name)
    if len(n) < 2 or len(n) > 130:
        return False
    if re.match(r"^[\$\u20A9\uFFE6\u20AC\u00A5]\s*\d", n) or re.search(r"[-\+]\s*[\$\u20A9\uFFE6\u20AC\u00A5]\s*\d", n):
        return False
    if re.search(r"(?:\uC720\uD6A8\uAE30\uAC04|ID\uB2F9\s*\d+\s*\uAC1C|1\uC778\uB2F9\s*\d+\s*\uAC1C|\uAD6C\uB9E4\s*\uC81C\uD55C|\uAD6C\uB9E4\s*\uACE0\uBBFC|\uAD1C\uCC2E\uC74C|\uC9C4\uACA9|^\d[\d,]*\s*(?:\uCC9C|\uB9CC|\uBC31)?\s*\uC6D0\s*(?:\uCD08\uBC18|\uC911\uBC18|\uD6C4\uBC18|\uB300|\uBBF8\uB9CC|\uC774\uD558|\uC774\uC0C1)?\s*[:\-])", n, re.IGNORECASE):
        return False
    if re.match(r"^\d+(?:\.\d+)?$", n):
        return False
    if _FAKE_OPTION_NAME_RE.match(n):
        return False
    if re.match(r"^(?:[가-힣a-zA-Z]{1,6}(?:카드|카|페이|멤버십|네멤|와우)\s*(?:결제\s*|할인\s*|적용\s*)?시|카할\s*시|카드\s*할인|청구\s*할인)$", n, re.IGNORECASE):
        return False
    if any(sub in n for sub in _PROSE_NOISE_SUBSTRINGS):
        return False
    if re.search(r"(?:에서|으론|기억|시길|가격이|대비|같아요|네요|입니다|합니다|습니다|세요|군요|더라고요|드려요|드립니다|공동구매|총정리|기획전|모음전|못감|헤헷)[.!?~]*\s*$", n):
        return False
    if any(k in n for k in ("가능성이 높지만", "변동될 수", "공개됩니다", "참고만하세요", "해결방법", "똑같은거탔었는데")):
        return False
    return True


def extract_structured_multi_options(raw_title: str, content_html: str, default_currency: str = "KRW", default_url: str = "") -> list[dict]:
    """
    제목 또는 본문(content_html)에서 다중 상품/옵션 목록과 개별 가격·할인코드·구매링크·상품이미지를 규칙 기반으로 정밀 추출합니다.
    (방향 무관 번호형 세그먼트 + 다중 직링크 블록 + 단일 링크 다중 옵션 + 제목 슬래시/괄호 다중 옵션 통합 지원)
    """
    import html as _html

    if raw_title and any(k in raw_title for k in ("선착순 쿠폰", "할인코드", "쿠폰 받기", "쿠폰 미리", "쿠폰 받아두세요", "프로모션 코드")) and not re.search(r'[\d,]+\s*(?:원|만|\$|달러|유로|€)', raw_title):
        return []

    base_title_prefix = re.sub(r'^\s*\[[^\]]+\]\s*', '', raw_title or '').strip()
    base_title_prefix = re.sub(r'\([^\)]*\)|\[[^\]]*\]|/.*$', '', base_title_prefix).strip()
    base_title_prefix = re.sub(r'(?:[\d,]+\s*원|\$\s*[\d.]+).*$', '', base_title_prefix).strip(' -·,~')

    def normalize_price_str(s: str) -> str:
        if not s:
            return ""
        s = _html.unescape(s)
        # 62,70$ 또는 $51,91 같은 유럽식/오타 쉼표 소수점 달러 표기를 62.70$ / $51.91로 정규화 (단, 1,000$ 같은 3자리 천단위는 제외)
        s = re.sub(r'(\d+),(\d{1,2})\s*(\$|달러|불|usd|유로|€)', r'\1.\2\3', s, flags=re.IGNORECASE)
        s = re.sub(r'(\$\s*\d+),(\d{1,2})(?!\d)', r'\1.\2', s)
        return s

    def parse_price_token(token_str: str, fallback_curr: str = "KRW") -> tuple[int, str]:
        if not token_str:
            return 0, fallback_curr
        s = normalize_price_str(token_str.strip())
        if any(c in s for c in ('$', '달러', '불', 'usd', 'USD')):
            m = re.search(r'\d[\d,]*(?:\.\d+)?', s)
            if m:
                return round(float(m.group(0).replace(',', '')) * 100), "USD"
        if any(c in s for c in ('€', '유로', 'eur', 'EUR')):
            m = re.search(r'\d[\d,]*(?:\.\d+)?', s)
            if m:
                return round(float(m.group(0).replace(',', '')) * 100), "EUR"
        if '만' in s:
            m = re.search(r'\d[\d,]*(?:\.\d+)?', s)
            if m:
                return int(float(m.group(0).replace(',', '')) * 10000), "KRW"
        if any(c in s for c in ('원', '₩', '￦')):
            m = re.search(r'\d[\d,]*', s)
            if m:
                return int(m.group(0).replace(',', '')), "KRW"
        if any(c in s for c in ('엔', '円', '¥', 'jpy', 'JPY')):
            m = re.search(r'\d[\d,]*', s)
            if m:
                return int(m.group(0).replace(',', '')), "JPY"
        # 통화 기호(₩, $, €, 원, 만, 엔 등)가 없는 순수 숫자(예: 12.7, 2025, 65, 75 등)는
        # 모델명/연도/화면크기(인치)일 수 있으므로 가격으로 임의 변환하지 않고 0 반환
        return 0, fallback_curr

    def extract_scored_prices_from_line(line: str) -> list[tuple[int, str, int]]:
        """
        한 줄에서 음수 할인액(-$36.49, -₩37,229)과 조건문($150 이상 -$22)을 제거한 뒤
        (price_int, currency, priority_score) 리스트를 반환합니다.
        priority_score:
          4 = 공구가 / 체감가 / 최종가 / 실구매가 / 혜택가 / 쿠폰가 / 결제가 (최우선)
          3 = 카드할인가 / 할인가 / 카드 시 최저
          2 = 일반 가격 표기
          1 = 앱판매가 / 정상가 / 기본가 / 출고가 (폴백)
        """
        s = normalize_price_str(line)
        # 0-a) 순수 할인/차감 안내 불릿 줄('- 코인할인 21,987원', '- 할인 코드: 38,000원', '- 카드할인 14,000원', '판매자 할인 $10', '스토어할인 $5', '코드 할인 $4' 등)은 상품 가격이 아니므로 즉시 배제
        if re.match(
            r'^\s*[-－—•]?\s*(?:(?:코인|판매자|스토어|코드|특가\s*코드|공구\s*코드|카드|결제|할인\s*코드|스토어\s*쿠폰|쿠폰)\s*(?:할인|쿠폰|코드)?|할인\s*코드|공구\s*코드|특가\s*코드|코인)\b',
            s,
            flags=re.IGNORECASE,
        ) and not any(k in s for k in ('최종가', '체감가', '공구가', '혜택가', '실구매', '할인가', '적용가', '=', '→', '->')):
            return []
        if re.search(r'(?:결제\s*할인|카드\s*할인|코드\s*할인|코인\s*할인)\s*$', s.strip()) and not any(k in s for k in ('최종가', '체감가', '공구가', '할인가', '=', '→', '->')):
            return []
        # 0) '166,800 > 98,200원' / '94,800원 > 66,000원' 같은 화살표(>, →, ->) 앞의 취소선 정가 제거
        s = re.sub(r'(?:[₩￦$]?\s*[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,8})\s*원?\s*(?:>|→|->)\s*(?=[₩￦$]?[0-9])', ' ', s)
        # 1) 카드 조건문 ($150 이상 -$22, $150↑ -$22, 10만원 이상 1만원 할인 등) 제거
        s = re.sub(r'(?:[$₩￦€]?\s*[0-9,]+(?:\.[0-9]+)?\s*(?:만\s*원?|천\s*원?|원|\$|달러|불|유로|€)?\s*(?:이상|이하|↑)\s*(?:구매\s*시|결제\s*시)?\s*[-－—~]?\s*[$₩￦€]?\s*[0-9,]+(?:\.[0-9]+)?\s*(?:만\s*원?|천\s*원?|원|\$|달러|불|유로|€)?)', ' ', s)
        # 2) 음수 할인 차감액 (-₩37,229, -$13.50, -$36.49, 할인 -10,000원 등) 제거
        s = re.sub(r'(?:(?<!\w)[-－—]|할인\s*[-:－]?|차감\s*[-:－]?)\s*(?:[$₩￦\\€]|KRW|USD)?\s*[0-9,]+(?:\.[0-9]+)?\s*(?:만\s*원?|천\s*원?|원|\$|달러|불|유로|€)?', ' ', s, flags=re.IGNORECASE)
        # 3) 단위당 가격 (100g당 1,200원, 개당 500원 등) 제거
        s = re.sub(r'\([^\)]*(?:당|개당|병당|포당|통당|박스당|팩당|캔당|g당|ml당|알당|캡슐당)[^\)]*\)', ' ', s)
        s = re.sub(r'(?:개당|병당|포당|통당|박스당|팩당|캔당|100g당|10g당|미당)\s*약?\s*[₩￦$]?\s*[\d,]+\s*원?', ' ', s)
        # 4) 쿠폰/코인/적립금/결제할인 단독 할인 금액 문구 ($4 할인쿠폰, 코인 할인 : $1.96, 결제할인 (최대 $22), 유튜버 코드 할인 $42, 스토어 할인코드 $5 등) 제거
        s = re.sub(
            r'(?:코인\s*할인[^\n:]*:\s*[$₩￦]?\s*[0-9,.]+|\$\s*[0-9,.]+\s*(?:할인\s*)?(?:쿠폰|코드)|[0-9,.]+\s*(?:만\s*원?|원|달러|\$)\s*(?:할인|중복|다운로드|스토어|선착순)?\s*(?:쿠폰|코드)|(?:스토어|판매자|코드|특가\s*코드|공구\s*코드|카드|결제|코인)\s*(?:코드\s*|쿠폰\s*)?할인\s*[:\-]?\s*[$₩￦]?\s*[0-9,.]+\s*(?:만\s*원?|원|달러|\$)?|[0-9,.]+\s*(?:달러|원|\$)\s*기준|[0-9,]+\s*원?\s*(?:적립|줘서)|(?:결제\s*할인|유튜버\s*코드\s*할인|스토어\s*중복\s*할인|중복\s*카드\s*할인|스토어\s*할인\s*코드|채널\s*할인\s*코드|추가)\s*(?:\([^\)]*\)\s*)?(?:최대\s*)?[$₩￦]?\s*[0-9,.]+\s*(?:만\s*원?|원|달러|\$)?|최대\s*[$₩￦]\s*[0-9,.]+)',
            ' ',
            s,
            flags=re.IGNORECASE,
        )

        # 달러 가격과 같은 줄에 병기된 (약 ₩90,915) 환산 참고가는 달러 가격이 있을 때 제외
        if re.search(r'(?:\$\s*[0-9]+|[0-9]+(?:\.[0-9]+)?\s*(?:\$|달러|불))', s):
            s = re.sub(r'\(\s*₩?\s*약\s*[₩￦\\]?\s*[0-9,]+\s*원?\s*\)', ' ', s)

        results: list[tuple[int, str, int]] = []
        # 슬래시(/)로 '공구가 ₩151,382 / 우리 VISA 카드 시 최저 ₩122,382'가 나뉘어 있으면 파트별로 점수 부여
        parts = [p.strip() for p in re.split(r'\s+/\s+', s) if p.strip()]
        for part in parts:
            if (
                any(k in part for k in ('공구가', '체감가', '최종가', '역대 최저가', '실구매', '혜택가', '쿠폰가', '결제가', '할인 가격', '할인가격', '코드+코인샵', '코인샵+코드'))
                and '카드 시' not in part
                and '최대카할' not in part
                and not any(sub_k in part for sub_k in ('그래픽카드 체감가', '그래픽 체감가', 'CPU 체감가'))
            ) or bool(re.search(r'(?:→|->|^\s*=|\s=\s)\s*[$₩￦]?\s*[0-9]', part)):
                score = 4
            elif any(k in part for k in ('카드할인가', '카드 할인가', '할인가', '카드 시', '최대카할', '최저')):
                score = 3
            elif any(k in part for k in ('상세페이지 가격', '앱판매가', '앱기본가', '기본가', '정상가', '출고가', '정가', '엡 가격', '앱 가격')):
                score = 1
            else:
                score = 2

            # 원화 (₩151,382 또는 151,382원)
            for m_won in re.finditer(r'(?:[₩￦]\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,8})|([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,8})\s*원)', part):
                num_str = m_won.group(1) or m_won.group(2)
                pv = int(num_str.replace(',', ''))
                if 500 <= pv <= 15000000:
                    results.append((pv, "KRW", score))

            # 만원 단위 (57.6만원, 16만, 11만원대, 194.6만 등 - 단, '22.5W 1만', '45W 2만' 같은 보조배터리 용량 표기는 제외)
            if not any(r[1] == "KRW" for r in results):
                for m_man in re.finditer(r'(?<!W\s)(?<!w\s)([0-9]+(?:\.[0-9]+)?)\s*만\s*(?:원(?:대|\s*중반|\s*초반|\s*후반)?|대|정도|가량|\s*중반|\s*초반|\s*후반)?(?!\s*(?:mAh|mah|개|명|장|번|회|점))', part):
                    m_float = float(m_man.group(1))
                    has_man_suffix = bool(re.search(r'만\s*(?:원|대|정도|가량|중반|초반|후반)', m_man.group(0)))
                    if has_man_suffix or m_float >= 6:
                        mv = int(m_float * 10000)
                        if 5000 <= mv <= 15000000:
                            results.append((mv, "KRW", max(1, score - 1)))

            # 달러 ($62.70 또는 62.70$ 또는 62.70달러)
            for m_usd in re.finditer(r'(?:\$\s*([0-9]+(?:\.[0-9]+)?)|([0-9]+(?:\.[0-9]+)?)\s*(?:\$(?!\s*[0-9])|달러|불)(?:대)?)', part, re.IGNORECASE):
                d_str = m_usd.group(1) or m_usd.group(2)
                dv = round(float(d_str) * 100)
                if 100 <= dv <= 1000000:
                    results.append((dv, "USD", score))

            # 유로 (€49.50 또는 49.50€ 또는 49.50유로)
            for m_eur in re.finditer(r'(?:€\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?)|([0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?)\s*(?:€(?!\s*[0-9])|유로|eur)(?:대)?)', part, re.IGNORECASE):
                e_str = (m_eur.group(1) or m_eur.group(2)).replace(',', '')
                ev = round(float(e_str) * 100)
                if 100 <= ev <= 1000000:
                    results.append((ev, "EUR", score))

        return results

    url_pattern = re.compile(r'(https?://[^\s\)\"\'<>]+)')
    community_hosts = ('ppomppu.co.kr', 'fmkorea.com', 'fmkorea.org', 'ruliweb.com', 'quasarzone.com', 'clien.net', 'bbasak.com', 'daumcdn.net')
    image_exts = ('.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.svg', '.avif')

    raw_lines: list[str] = []
    blocks: list[tuple[list[str], str, str]] = []
    body_shop_urls: list[tuple[str, str]] = []

    if content_html:
        from bs4 import BeautifulSoup
        clean_html_source = re.split(r'\[첨부된 이미지|추천수\s*\d+\s*비추천수|\(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\)', _html.unescape(content_html))[0]
        # '<WLRRN517>', '<GONS13 , KBTY13>' 같은 꺾쇠 괄호 할인코드가 HTML 태그로 오인되어 증발하는 현상 방지
        clean_html_source = re.sub(
            r'<\s*([A-Z0-9_]{4,16}(?:\s*[,/]\s*[A-Z0-9_]{4,16})*)\s*>',
            r'[\1]',
            clean_html_source,
        )
        if re.search(r'</?(?:p|div|span|br|a|img|table|thead|tbody|tr|td|th|ul|ol|li|strong|b|i|u|em|font|center|h[1-6]|blockquote|hr)\b', clean_html_source, re.I):
            soup = BeautifulSoup(clean_html_source, 'html.parser')
            for cmt_node in soup.select('.fdb_lst_ul, #cmtPosition, .comment_list, .fdb_v2, a.scrap_bx_href, div.scrap_bx'):
                cmt_node.decompose()
            for img in soup.find_all('img'):
                src = (img.get('data-original') or img.get('data-src') or img.get('src') or '').strip()
                if src.startswith('//'):
                    src = 'https:' + src
                if src.startswith('http') and not any(x in src.lower() for x in ('emoticon', 'sticker', 'transparent', 'logo', 'icon', 'reply', 'blank', 'avatar', 'profile', 'lazyloading', '/t.gif', 'drag_img')):
                    if "ppomppu" in src:
                        src = src.replace("_thumb/", "").replace("thumb/", "").replace("/small_", "/").replace("small_", "").replace("_thumb.", ".")
                    img.replace_with(f"\n[상품이미지: {src}]\n")
            raw_text_body = soup.get_text(separator='\n')
        else:
            raw_text_body = clean_html_source

        raw_text_body = re.sub(r'(\[상품이미지:\s*https?://[^\]]+\])', r'\n\1\n', raw_text_body)
        raw_lines = [ln.strip() for ln in raw_text_body.splitlines() if ln.strip()]

    def is_noise_line(line: str) -> bool:
        if len(line) < 2 or len(line) > 180:
            return True
        low = line.lower()
        if low.startswith('[첨부된 이미지') or low.startswith('[상품이미지:'):
            return True
        if any(w in low for w in (
            '게시글 신고 기능 안내', '신고버튼을 누르면', '빠삭 운영진', '링크오류시', '링크가 열리지 않는다면',
            '사파리에서 링크', '링크 꾹 눌러', '알리앱 꾹 눌러서', '예열기간:', '세일기간:', '사용기간:',
            '주요 페이지 바로가기', '메인 기획전', '알리가 차 쏜다', '쿠폰 센터', '쿠폰센터 바로가기', '선착순, 앱전용',
            '전상품 적용 가능', '해외직구 상품 전용', '한국발송 상품 전용', '슈퍼 브랜드 위크',
            '공구 채널이 공개한 전용코드', '카드 결제할인(해외직구)', '카드 결제할인:', '↑ -$',
            '할인 코드는 결제화면', '코인몰으로 이동', '코인몰에서 안보이는', '쿠폰팩데이', '멤버십데이 쿠폰',
            '개별 가격이', '계산상 약', '비교해 볼', '비슷한 금액대', '네페 2만 받으면', '네페 4만 받으면', '네페 5만 받으면', '체감가 3만 더',
            '이상 구매 시', '이상 결제 시', '이상 -$', '이상 -1만', '이상 -2만', '이상 -8천', '이상 -7천', '이상 -2800',
            '포토 리뷰 1,000원 차이', '체감가는 천원 더 높습니다', '가격은 대략적인 예상치', '환율 변동에 따라', '표기된 가격은 예상가'
        )):
            return True
        if re.match(r'^(?:링크|링크\s*\d+|구매\s*링크|\d+\s*순위|\|)$', line.strip()):
            return True
        return False

    def extract_clean_urls_from_line(ln: str) -> list[str]:
        if ln.startswith('[상품이미지:') or ln.startswith('[첨부된 이미지'):
            return []
        found = []
        for u in url_pattern.findall(ln):
            cu = _decode_and_clean_shop_url(u)
            if not cu:
                continue
            cu_low = cu.lower()
            if any(h in cu_low for h in community_hosts):
                continue
            if any(p in cu_low for p in ('campaign2.naver.com', 'ofw.adison.co', 'campaign-cdn.pstatic.net', 'shopping.naver.com/event', 'event.naver.com', 'rpp.gmarket.co.kr/?exhib=', 'store.ohou.se/exhibitions/', 'ko.aliexpress.com/item/1005010123406060.html')):
                continue
            if cu_low.split('?')[0].endswith(image_exts):
                continue
            if cu not in found:
                found.append(cu)
        return found

    for ln in raw_lines:
        for cu in extract_clean_urls_from_line(ln):
            txt_clean = url_pattern.sub('', ln).replace('(링크:', '').replace('()', '').strip(' -·:[]>')
            body_shop_urls.append((txt_clean, cu))

    cleaned_default_url = _decode_and_clean_shop_url(default_url)
    if any(h in cleaned_default_url.lower() for h in community_hosts):
        cleaned_default_url = body_shop_urls[0][1] if body_shop_urls else ""

    # =========================================================================
    # 1단계: 본문 내 '번호형 세그먼트(1. / 2. / 1번｜ / ① / [1])' 및 명시적 구분자('바로가기 >>', '방금 AliExpress에서 찾은 상품이에요:') 스마트 파서
    # - 링크가 제목 줄(Top-Link)에 있든 하단(Bottom-Link)에 있든 단일 링크(Type-B)이든
    #   상품 경계 기준으로 정확히 분할하며, 각 상품 직전/내부의 [상품이미지: URL]을 1:1 매칭합니다.
    # =========================================================================
    circle_map = {c: i for i, c in enumerate("①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳", 1)}
    num_header_re = re.compile(
        r'^\s*(?:(\d{1,2})\s*(?:\.\s+|번\s*(?:[｜|:\-]\s*|\s+)|\)\s+)|\[(\d{1,2})\]\s*|([①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])\s*)(.+)$'
    )
    ali_found_re = re.compile(r'^\s*방금\s*AliExpress에서\s*찾은\s*상품이에요\s*:?\s*(.*)$', re.IGNORECASE)
    img_marker_re = re.compile(r'^\[상품이미지:\s*(https?://[^\]]+)\]$')
    code_stop_words = {
        "HTTP", "HTTPS", "HTML", "VISA", "MASTER", "MASTERCARD", "FREE", "NONE", "NULL",
        "TYPE", "TYPEC", "USBC", "HDMI", "NVME", "DDR4", "DDR5", "OLED", "QLED", "WIFI",
        "UGREEN", "BASEUS", "ANKER", "ASOMETECH", "QOOVI", "ESSAGER", "TOOCKI", "VENTION",
    }

    def is_non_product_numbered_header(txt: str) -> bool:
        t = txt.strip()
        if re.match(r"^[-+\s]*(?:[\$\u20A9\uFFE6\u20AC\u00A5]\s*\d|\d[\d,.]*\s*(?:\uC6D0|\uB9CC\s*\uC6D0?|\uB2EC\uB7EC|\uBD88|\$|\u20AC|\uC720\uB85C))", t):
            return True
        # (1) 선착순/할인코드 번호 나열 (예: '① WLRRN114 (선착순 100장) → $37.63', '1. BALPUM003 (선착순 250장)')
        if re.match(r'^[A-Z0-9_]{4,16}\s*(?:\(|→|->|\||적용|할인|선착순|$)', t):
            return True
        # (2) 앱 설정/장바구니/쿠폰 혜택 안내 문구 및 음향/제품 리뷰 섹션 헤더
        if re.search(
            r'(?:결제\s*방법|알리\s*쿠폰|쿠폰\s*안내|주의사항|참고사항|적용\s*방법|구매\s*방법|장바구니\s*담기|장바구니담기|최근\s*본\s*상품|최근본상품|장바구니\s*탭|장바구니탭|바로\s*구매|바로구매|링크\s*꾹|알리앱\s*꾹|새\s*창에서\s*열기|지원되는\s*링크|지원되는링크|상단에\s*앱\s*연결|다운로드\s*쿠폰|할인\s*쿠폰|할인쿠폰|중복\s*쿠폰|중복쿠폰|청구\s*할인|청구할인|즉시\s*할인|즉시할인|추가\s*할인|추가할인|슈퍼\s*적립|슈퍼적립|한정\s*예산\s*소진|조기\s*종료|시간\s*동안만\s*진행|상세페이지에서\s*다운로드|적용됩니다|받으실\s*수\s*있습니다|진행됩니다|토스페이\s*앱|최저가\s*보상제|모든\s*할인|프로모션\s*코드|프로모션\s*할인코드|결제\s*할인\s*혜택|전용\s*공동구매|전반적인\s*사운드|구동력과\s*다이내믹스|이런\s*분에게\s*추천|이런\s*분은\s*피하셔야|저음\s*\(|중음\s*\(|고음\s*\(|음감\s*모드|하드웨어\s*모드)',
            t,
        ):
            return True
        return False

    numbered_segments: list[dict] = []
    pending_next_img: str = ""
    current_seg: dict | None = None
    in_guide_section = False

    for ln in raw_lines:
        if ln.startswith('[첨부된 이미지'):
            break
        if re.match(r'^(?:코인딜\s*코인샵\s*할인법|※\s*이벤트\s*및\s*결제\s*유의사항|링크\s*오류\s*시|링크\s*오류\s*해결\s*방법)', ln):
            in_guide_section = True
            continue

        m_img = img_marker_re.match(ln)
        if m_img:
            img_u = m_img.group(1).strip()
            if current_seg is not None and not current_seg["image_url"] and not current_seg["has_seen_price"]:
                current_seg["image_url"] = img_u
            else:
                pending_next_img = img_u
            continue

        m_af = ali_found_re.match(ln)
        if m_af:
            in_guide_section = False
            rest_text = m_af.group(1).strip()
            if current_seg is not None:
                numbered_segments.append(current_seg)
            current_seg = {
                "num": None,
                "header_raw": rest_text,
                "lines": [rest_text] if rest_text else [],
                "image_url": pending_next_img,
                "has_seen_price": bool(extract_scored_prices_from_line(rest_text)) if rest_text else False,
            }
            pending_next_img = ""
            continue

        m_num = num_header_re.match(ln)
        if m_num:
            num_val = int(m_num.group(1) or m_num.group(2) or circle_map.get(m_num.group(3), 0))
            rest_text = m_num.group(4).strip()
            if not in_guide_section and not is_non_product_numbered_header(rest_text):
                if current_seg is not None:
                    numbered_segments.append(current_seg)
                current_seg = {
                    "num": num_val,
                    "header_raw": rest_text,
                    "lines": [rest_text],
                    "image_url": pending_next_img,
                    "has_seen_price": bool(extract_scored_prices_from_line(rest_text)),
                }
                pending_next_img = ""
                continue

        if current_seg is not None and not in_guide_section:
            if is_noise_line(ln):
                continue
            if not current_seg["header_raw"] and not extract_clean_urls_from_line(ln):
                current_seg["header_raw"] = ln
            current_seg["lines"].append(ln)
            if extract_scored_prices_from_line(ln) or "후기" in ln:
                current_seg["has_seen_price"] = True

    if current_seg is not None:
        numbered_segments.append(current_seg)

    if len(numbered_segments) >= 2:
        numbered_options: list[dict] = []
        seen_num_keys = set()
        for seg in numbered_segments:
            seg_urls: list[str] = []
            seg_prices: list[tuple[int, str, int]] = []
            seg_codes: list[str] = []
            expect_code_next_line = False
            expect_high_priority_price = False

            for idx_s, s_ln in enumerate(seg["lines"]):
                for cu in extract_clean_urls_from_line(s_ln):
                    if cu not in seg_urls:
                        seg_urls.append(cu)

                s_ln_no_url = url_pattern.sub('', s_ln).replace('(링크:', '').replace('()', '').strip()
                if not s_ln_no_url:
                    continue

                # 할인코드 추출:
                # (1) '공구코드'/'코드' 단독 줄 다음 줄
                # (2) '공구코드: QSZONE14' / '$4 할인쿠폰 : FMKR04' / '$5 쿠폰 : 11FKO5' 인라인
                # (3) '① WLRRN114 (선착순 100장)' / '11FHG7 적용 ->' 패턴
                if re.match(r'^(?:공구|할인|쿠폰|프로모션|스토어|전용)?\s*코드\s*:?\s*$', s_ln_no_url):
                    expect_code_next_line = True
                    continue
                if expect_code_next_line:
                    expect_code_next_line = False
                    m_c = re.match(r'^([A-Z0-9_]{4,16})\b', s_ln_no_url.strip())
                    if m_c and m_c.group(1).upper() not in code_stop_words and any(ch.isdigit() for ch in m_c.group(1)):
                        if m_c.group(1) not in seg_codes:
                            seg_codes.append(m_c.group(1))
                        continue

                for m_inl in re.finditer(
                    r'(?:(?:공구|할인|쿠폰|프로모션|스토어|전용|단독\s*할인)\s*(?:코드|쿠폰)|코드|쿠폰)\s*[:\-]?\s*([A-Z0-9_]{4,16})\b',
                    s_ln_no_url,
                ):
                    c_tok = m_inl.group(1)
                    if c_tok.upper() not in code_stop_words and any(ch.isdigit() for ch in c_tok):
                        if c_tok not in seg_codes:
                            seg_codes.append(c_tok)

                for m_num_code in re.finditer(r'(?:[①-⑳]|\b\d{1,2}[\.\)])\s*([A-Z0-9_]{4,16})\s*(?:\(|→|->|\|)', s_ln_no_url):
                    c_tok = m_num_code.group(1)
                    if c_tok.upper() not in code_stop_words and any(ch.isdigit() for ch in c_tok):
                        if c_tok not in seg_codes:
                            seg_codes.append(c_tok)

                for m_app_code in re.finditer(r'\b([A-Z0-9_]{4,16})\s*적용\b', s_ln_no_url):
                    c_tok = m_app_code.group(1)
                    if c_tok.upper() not in code_stop_words and any(ch.isdigit() for ch in c_tok):
                        if c_tok not in seg_codes:
                            seg_codes.append(c_tok)

                line_prices = extract_scored_prices_from_line(s_ln_no_url)
                if line_prices:
                    for p_val, p_curr, p_sc in line_prices:
                        seg_prices.append((p_val, p_curr, 4 if expect_high_priority_price else p_sc))
                    expect_high_priority_price = False
                else:
                    expect_high_priority_price = any(
                        k in s_ln_no_url for k in ('공구가', '체감가', '최종가', '혜택가', '구매가', '실구매', '쿠폰가', '할인 가격')
                    )

            # 상품명 정제 (첫 줄 header_raw 기준)
            raw_head = url_pattern.sub('', seg["header_raw"]).replace('(링크:', '').replace('(링크 :', '').strip()
            # 링크 치환 후 남은 빈 괄호나 끝의 닫는 괄호 정리
            raw_head = re.sub(r'\(\s*\)', '', raw_head).strip()
            while raw_head.endswith(')') and raw_head.count(')') > raw_head.count('('):
                raw_head = raw_head[:-1].strip()
            # 헤더 줄 끝에 가격이 함께 적힌 경우 가격 부분 분리
            head_clean = re.sub(
                r'\s*(?:[₩￦]\s*[0-9,]+|[0-9]{1,3}(?:,[0-9]{3})+\s*원|[0-9]+(?:\.[0-9]+)?\s*만\s*원?|\$\s*[0-9,.]+|[0-9,.]+\s*(?:\$|달러|불)).*$',
                '',
                raw_head,
            ).strip(' -·:,/~+>')
            if not head_clean:
                head_clean = raw_head.strip(' -·:,/~+>')

            # 두 번째 줄이 CPU/GPU/RAM 스펙 요약이면서 첫 줄이 짧으면 보충 (예: 펨코 완본체 '1번｜라이젠5 9500F + RTX 5060 Ti')
            if len(seg["lines"]) >= 2 and len(head_clean) < 12:
                second_ln = url_pattern.sub('', seg["lines"][1]).replace('(링크:', '').strip()
                if second_ln and not extract_scored_prices_from_line(second_ln) and len(second_ln) <= 45:
                    head_clean = f"{head_clean} {second_ln}".strip()

            head_clean = _clean_option_display_name(head_clean)
            if not head_clean or len(head_clean) < 2 or not _is_valid_option_name(head_clean):
                continue

            opt_display_name = head_clean

            chosen_price = 0
            chosen_curr = default_currency
            if seg_prices:
                # 가장 높은 priority_score를 가진 가격 중 첫 번째(공구가/체감가 우선) 선택
                max_score = max(x[2] for x in seg_prices)
                best_candidates = [x for x in seg_prices if x[2] == max_score]
                chosen_price, chosen_curr, _ = best_candidates[0]

            primary_url = seg_urls[0] if seg_urls else cleaned_default_url
            sub_urls = seg_urls[1:4] if len(seg_urls) > 1 else []

            if chosen_price == 0 and any("품절" in l for l in seg["lines"]):
                continue

            if chosen_price > 0 or primary_url:
                norm_k = (head_clean[:30].lower(), chosen_price, primary_url)
                if norm_k not in seen_num_keys:
                    seen_num_keys.add(norm_k)
                    opt_obj = {
                        "name": opt_display_name[:65],
                        "price": chosen_price,
                        "currency": chosen_curr,
                        "url": primary_url,
                    }
                    opt_obj["code"] = seg_codes[0] if seg_codes else ""
                    opt_obj["image_url"] = seg.get("image_url") or ""
                    opt_obj["sub_urls"] = sub_urls
                    numbered_options.append(opt_obj)

        priced_num_opts = [o for o in numbered_options if int(o.get("price", 0)) > 0]
        if len(priced_num_opts) >= 2:
            return priced_num_opts[:35]

    # =========================================================================
    # 1.5단계: 퀘이사존/파트너사 다중 상품 카드 블록 ('🛒 <모델명> 특가 구매하러 가기') 파서
    # =========================================================================
    cart_btn_re = re.compile(r'🛒\s*(.+?)\s*(?:특가\s*)?구매하러\s*가기')
    if raw_lines and sum(1 for ln in raw_lines if cart_btn_re.search(ln)) >= 2:
        card_options: list[dict] = []
        card_lines: list[str] = []
        card_img = ""
        for ln in raw_lines:
            m_img = img_marker_re.match(ln)
            if m_img:
                card_img = m_img.group(1).strip()
                continue
            m_cart = cart_btn_re.search(ln)
            if m_cart:
                model_hint = m_cart.group(1).strip()
                c_urls = extract_clean_urls_from_line(ln)
                for cl in card_lines:
                    for cu in extract_clean_urls_from_line(cl):
                        if cu not in c_urls:
                            c_urls.append(cu)
                c_prices: list[tuple[int, str, int]] = []
                expect_hp = False
                full_model_name = model_hint
                for cl in card_lines:
                    if model_hint.lower() in cl.lower() and len(cl) <= 45 and not extract_scored_prices_from_line(cl):
                        full_model_name = cl.strip()
                    lp = extract_scored_prices_from_line(cl)
                    if lp:
                        for pv, pc, psc in lp:
                            c_prices.append((pv, pc, 4 if expect_hp else psc))
                        expect_hp = False
                    else:
                        expect_hp = any(k in cl for k in ('공구가', '체감가', '최종가', '혜택가', '구매가', '실구매'))
                if c_prices:
                    max_sc = max(x[2] for x in c_prices)
                    cp_val, cp_curr, _ = [x for x in c_prices if x[2] == max_sc][-1]
                    card_options.append({
                        "name": full_model_name[:60],
                        "price": cp_val,
                        "currency": cp_curr,
                        "url": c_urls[0] if c_urls else cleaned_default_url,
                        "code": "",
                        "image_url": card_img,
                        "sub_urls": [],
                    })
                card_lines = []
                card_img = ""
            else:
                card_lines.append(ln)
        if len(card_options) >= 2:
            return card_options[:25]

    # =========================================================================
    # 2단계: 비번호형 다중 직링크 블록(Multi-Link Block) 파서 (Top-Link / Bottom-Link 양방향 지원)
    # - 번호(1., 2.) 없이 상품명 + 쇼핑몰 직링크가 여러 개 나열된 게시글(루리웹 반다이몰 30종, 알리 모음전 등) 처리
    # =========================================================================
    if raw_lines:
        current_lines: list[str] = []
        pending_blk_img = ""
        open_top_blk: dict | None = None

        def _is_price_or_code_meta_line(t: str) -> bool:
            t_strip = t.strip()
            if t_strip.startswith('='):
                return True
            if extract_scored_prices_from_line(t_strip):
                return True
            if re.search(
                r'(?:기본가|공구가|체감가|최종가|최종가격|최저가|할인가|쿠폰|코드|할인|코인|옵션|선택|상세|스토어|멤버십|적립|체감|자동\s*적용|직접\s*수령|선착순|예상|참고|결제\s*할인|카드\s*할인|중복\s*할인|주문\s*시|결제\s*시|관부가세|부가세\s*포함|배터리\s*\d+개|구성\b|카드|비자|마스터|유니온페이|카카오페이|토스페이|토스뱅크|신한|롯데|국민|우리|농협|하나|비씨|IBK|KB|^→|^->|^⭕|^✅|^🎟|^🔗)',
                t_strip,
                re.IGNORECASE,
            ):
                return True
            if re.match(r'^[A-Z0-9_,\s]{4,40}$', t_strip) and any(ch.isdigit() for ch in t_strip):
                return True
            return False

        for idx_ln, ln in enumerate(raw_lines):
            if ln.startswith('[첨부된 이미지'):
                break
            m_img = img_marker_re.match(ln)
            if m_img:
                if open_top_blk is not None:
                    blocks.append((open_top_blk["lines"], open_top_blk["url"], open_top_blk["img"]))
                    open_top_blk = None
                current_lines = []
                pending_blk_img = m_img.group(1).strip()
                continue
            cleaned_urls = extract_clean_urls_from_line(ln)
            text_only = url_pattern.sub('', ln).replace('(링크:', '').replace('()', '').strip(' -·:[]>🔗')
            while text_only.endswith(')') and text_only.count(')') > text_only.count('('):
                text_only = text_only[:-1].strip()
            if text_only == "링크":
                text_only = ""

            if idx_ln == 0 and not text_only and cleaned_urls:
                continue

            if cleaned_urls:
                if open_top_blk is not None:
                    blocks.append((open_top_blk["lines"], open_top_blk["url"], open_top_blk["img"]))
                    open_top_blk = None
                # 쿠폰 다운로드 전용 링크('선착순20%쿠폰', '쿠폰 링크')는 상품 블록에서 제외
                prev_label = text_only or (current_lines[-1] if current_lines else "")
                if re.search(r'^(?:선착순\s*\d*%?\s*쿠폰|.*쿠폰\s*링크|.*쿠폰\s*받기)\s*:?\s*$', prev_label):
                    current_lines = []
                    pending_blk_img = ""
                    continue
                is_explicit_bottom_link = bool(
                    not text_only
                    or re.match(r'^(?:(?:구매\s*)?링크|바로가기)\b', ln.strip())
                    or re.match(r'^(?:링크|구매\s*링크|바로가기)$', text_only.strip())
                )
                has_price_before_url = any(bool(extract_scored_prices_from_line(cl)) for cl in current_lines)
                if has_price_before_url and (is_explicit_bottom_link or not pending_blk_img):
                    # Bottom-Link 구조: 이미 제목과 가격이 위쪽에 등장했고 하단 링크로 블록이 닫히는 경우
                    if text_only and not is_noise_line(text_only):
                        current_lines.append(text_only)
                    blocks.append((list(current_lines), cleaned_urls[0], pending_blk_img))
                else:
                    # Top-Link 구조: 제목 + 링크가 먼저 등장하고 아래쪽에 가격/할인코드가 이어지는 경우
                    top_init_lines = [text_only] if (text_only and not is_noise_line(text_only)) else list(current_lines[-2:])
                    open_top_blk = {
                        "lines": top_init_lines,
                        "url": cleaned_urls[0],
                        "img": pending_blk_img,
                        "seen_price": False,
                    }
                current_lines = []
                pending_blk_img = ""
                continue

            if not text_only or is_noise_line(text_only):
                continue

            if open_top_blk is not None:
                if _is_price_or_code_meta_line(text_only):
                    open_top_blk["lines"].append(text_only)
                    if extract_scored_prices_from_line(text_only):
                        open_top_blk["seen_price"] = True
                else:
                    # 가격/코드 메타 줄이 아닌 새로운 상품명 줄이 등장하면 이전 Top-Link 블록 마감
                    blocks.append((open_top_blk["lines"], open_top_blk["url"], open_top_blk["img"]))
                    open_top_blk = None
                    current_lines.append(text_only)
            else:
                current_lines.append(text_only)

        if open_top_blk is not None:
            blocks.append((open_top_blk["lines"], open_top_blk["url"], open_top_blk["img"]))

    if len(blocks) >= 2:
        block_options: list[dict] = []
        seen_blk_keys = set()
        for blk_lines, blk_url, blk_img in blocks:
            if not blk_lines:
                continue
            cand_name = ""
            cand_prices: list[tuple[int, str, int]] = []
            blk_codes: list[str] = []
            expect_blk_code = False
            size_variant_hint = ""
            for bl in blk_lines:
                m_sz = re.search(r'\(\s*(싱글|슈퍼싱글|퀸|킹|더블)\b', bl)
                if m_sz and not size_variant_hint:
                    size_variant_hint = m_sz.group(1)
                if re.match(r'^[-－—•\s]*(?:[$₩￦]?\s*[0-9,.]+\s*(?:원|달러|\$)?\s*)?(?:(?:공구|할인|쿠폰|프로모션|스토어|채널|전용|특가)\s*(?:코드|쿠폰)|코드\s*할인|코드|쿠폰)(?:\s*[$₩￦]?\s*[0-9,.]+\s*(?:원|달러|\$)?)?\s*:?\s*$', bl):
                    expect_blk_code = True
                    continue
                if expect_blk_code:
                    m_bc = re.match(r'^[\[<(\s]*([A-Z0-9_]{4,16})\b', bl.strip())
                    if m_bc and m_bc.group(1).upper() not in code_stop_words and any(ch.isdigit() for ch in m_bc.group(1)):
                        if m_bc.group(1) not in blk_codes:
                            blk_codes.append(m_bc.group(1))
                        # 줄 끝에 쉼표(,)가 있으면 다음 줄도 코드일 수 있음
                        expect_blk_code = bl.strip().endswith(',')
                        continue
                    expect_blk_code = False
                for m_inl in re.finditer(
                    r'(?:(?:(?:공구|할인|쿠폰|프로모션|스토어|채널|전용|특가|단독\s*할인)\s*(?:할인\s*)?(?:코드|쿠폰)(?:\s*[:\-]?\s*\$?[0-9,.]+\s*(?:원|달러|\$)?)?|코드\s*할인(?:\s*[:\-]?\s*\$?[0-9,.]+\s*(?:원|달러|\$)?)?|코드|쿠폰)\s*[:\-<\[\(\s]+\s*|(?:🎟|→|->)[^\n:]*:\s*)([A-Z0-9_]{4,16})\b',
                    bl,
                ):
                    c_tok = m_inl.group(1)
                    if c_tok.upper() not in code_stop_words and any(ch.isdigit() for ch in c_tok):
                        if c_tok not in blk_codes:
                            blk_codes.append(c_tok)
                for m_num_code in re.finditer(r'(?:[①-⑳]|\b\d{1,2}[\.\)])\s*([A-Z0-9_]{4,16})\s*(?:\(|→|->|\|)', bl):
                    c_tok = m_num_code.group(1)
                    if c_tok.upper() not in code_stop_words and any(ch.isdigit() for ch in c_tok):
                        if c_tok not in blk_codes:
                            blk_codes.append(c_tok)

                for p_tup in extract_scored_prices_from_line(bl):
                    cand_prices.append(p_tup)

                if _is_price_or_code_meta_line(bl) and cand_name:
                    continue

                cleaned_candidate = re.sub(
                    r'(?:\(\s*[0-9,]+(?:\s*원)?\s*(?:>|→|->).*|[₩￦]\s*[0-9,]+|(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,7})\s*원.*|(?:[0-9]+(?:\.[0-9]+)?)\s*만\s*원?(?:대|\s*중반|\s*초반|\s*후반)?.*|\$\s*[0-9]+(?:\.[0-9]+)?.*|[0-9]+(?:\.[0-9]+)?\s*(?:\$(?!\s*[0-9])|달러|불)(?:대)?.*)',
                    '',
                    bl,
                )
                cleaned_candidate = re.sub(r'(?:바로가기|보기|링크)\s*>*\s*', '', cleaned_candidate)
                cleaned_candidate = re.sub(r'^(?:>+|→|->|⭕️?|✅|🎟️?)\s*', '', cleaned_candidate)
                cleaned_candidate = re.sub(
                    r'\s*(?:[\d,]+\s*(?:포인트|원|p|프로)\s*[\+\-/]?\s*)*(?:금액대별\s*쿠폰|중복\s*쿠폰|장바구니\s*쿠폰|쿠폰|할인|적립|토스페이|네이버페이|카카오페이|스마일페이|카드할인|적용시|감안시|결제시|체감가?|최종가?|앱기본가?|기본가?).*$',
                    '',
                    cleaned_candidate,
                    flags=re.IGNORECASE,
                ).strip(' -·:,/~+>')

                if _PURE_QTY_OPTION_RE.match(cleaned_candidate) and base_title_prefix:
                    cleaned_candidate = f"{base_title_prefix[:30]} ({cleaned_candidate})"

                if _is_valid_option_name(cleaned_candidate):
                    if not cand_name:
                        cand_name = cleaned_candidate
                    else:
                        is_spec_or_subline = bool(
                            re.search(r'(?:RAM|SSD|GB|TB|Core\s*i\d|Ryzen|라이젠|인텔|[0-9]{4}[A-Z]{1,2}|H255|옵션\s*:|스테이션\s*포함|포함|증정|구성)', cleaned_candidate, re.I)
                            and not any(b in cleaned_candidate.lower() for b in ('레노버', '에디파이어', '드리미', 'firebat', 'gmktec', 'ninkear', 'jonr', 'uwant', 'alldocube', '삼성', 'lg', '알파스캔', '필립스'))
                        )
                        if is_spec_or_subline:
                            spec_short = re.sub(r'^옵션\s*:\s*', '', cleaned_candidate).strip()
                            if len(cand_name) + len(spec_short) <= 52 and spec_short.lower() not in cand_name.lower():
                                cand_name = f"{cand_name} ({spec_short})"

            if cand_name:
                if size_variant_hint and any(x in cand_name for x in ('싱글/퀸', '싱글/더블', '퀸/킹')):
                    cand_name = f"{cand_name[:48]} ({size_variant_hint})"
                cand_name = _clean_option_display_name(cand_name)
                if not _is_valid_option_name(cand_name):
                    continue
                chosen_price = 0
                chosen_curr = default_currency
                if cand_prices:
                    max_sc = max(x[2] for x in cand_prices)
                    top_cands = [x for x in cand_prices if x[2] == max_sc]
                    chosen_price, chosen_curr, _ = min(top_cands, key=lambda x: x[0]) if max_sc >= 3 else top_cands[0]
                # 가격이 있는 블록이거나, 루리웹 모음전처럼 서로 다른 외부 쇼핑몰 상품 직링크가 3개 이상 나열된 경우 허용
                if chosen_price > 0 or (len(blocks) >= 3 and blk_url):
                    key = (cand_name[:30].lower(), chosen_price, blk_url)
                    if key not in seen_blk_keys:
                        seen_blk_keys.add(key)
                        opt_d = {
                            "name": cand_name[:60],
                            "price": chosen_price,
                            "currency": chosen_curr,
                            "url": blk_url,
                        }
                        opt_d["code"] = blk_codes[0] if blk_codes else ""
                        opt_d["image_url"] = blk_img or ""
                        opt_d["sub_urls"] = []
                        block_options.append(opt_d)

        block_options = [o for o in block_options if int(o.get("price", 0)) > 0]
        distinct_names = {o["name"].lower() for o in block_options}
        if len(block_options) >= 2 and len(distinct_names) >= 2:
            return block_options[:35]

    # =========================================================================
    # 3단계: 제목 내 다중 상품 패턴 추출
    # - 패턴 A: 괄호 안 슬래시(/) 구분형, 예: (UGREEN 300W 151,382원 / Baseus 허브 62,70$ / 유그린 165W 52.00$)
    # - 패턴 B: 개별 괄호 나열형, 예: 상품A (15,000원), 상품B (20,000원), 상품C ($105), 상품D ($82/무료)
    # - 패턴 C: 괄호 밖 슬래시(/) 또는 쉼표(,) 분할형
    # =========================================================================
    title_norm = normalize_price_str(raw_title or '')
    title_no_mall = re.sub(r'^\s*\[[^\]]+\]\s*', '', title_norm).strip()
    title_no_mall = re.sub(r'^\s*(?:\[[^\]]+\]|[^\[\]\(\)]{1,14}\]|\([^\)]+\)|[^\[\]\(\)]{1,14}\))\s*', '', title_no_mall).strip()

    # 패턴 A: 괄호 내부에 '/'로 구분된 다중 '상품명 + 가격' 추출
    for paren_m in re.finditer(r'\(([^\(\)]{12,})\)', title_no_mall):
        inner = paren_m.group(1)
        if '/' in inner:
            slash_parts = [sp.strip() for sp in inner.split('/') if sp.strip()]
            slash_opts: list[dict] = []
            for idx_sp, sp in enumerate(slash_parts):
                m_sp = re.match(
                    r'^(.{2,50}?)\s+(\$\s*[0-9,]+(?:\.[0-9]+)?|[0-9,]+(?:\.[0-9]+)?\s*(?:만\s*원?|원|달러|불|유로|€(?!\s*[0-9])|\$(?!\s*[0-9]))|[₩￦€]\s*[0-9,]+(?:\.[0-9]+)?)\s*$',
                    sp,
                )
                if m_sp:
                    sp_name = _clean_option_display_name(m_sp.group(1).strip(' ,/·-~'))
                    p_val, p_curr = parse_price_token(m_sp.group(2), default_currency)
                    if _is_valid_option_name(sp_name) and p_val >= 100:
                        matched_url = body_shop_urls[idx_sp][1] if idx_sp < len(body_shop_urls) else cleaned_default_url
                        slash_opts.append({
                            "name": sp_name[:60],
                            "price": p_val,
                            "currency": p_curr,
                            "url": matched_url,
                            "code": "",
                            "image_url": "",
                            "sub_urls": [],
                        })
            if len(slash_opts) >= 2:
                return slash_opts

    # 패턴 B: 개별 괄호 나열형 (전위 $105, $237.68/무료 및 후위 15,000원, 11.7만 등 모두 지원)
    seen_keys: set = set()
    options: list[dict] = []
    title_matches = list(re.finditer(
        r'([^,\(\)/]+?)\s*\(\s*(?:약\s*)?([\$₩￦€¥]\s*[\d,]+(?:\.\d+)?|\$?\s*(?:[\d,]+(?:\.\d+)?)\s*(?:만\s*원?|천\s*원?|원|달러|불|유로|€(?!\s*[0-9])|\$(?!\s*[0-9])))\s*(?:외|부터|~)?\s*(?:/[^\)]*)?\)',
        title_no_mall
    ))
    if len(title_matches) >= 2:
        first_valid_prefix = ""
        for idx_m, m in enumerate(title_matches):
            opt_name = _clean_option_display_name(m.group(1).strip(' ,/·-~'))
            if re.match(r'^(?:프로\s*)?\d{1,4}(?:\.\d+)?$', opt_name) and '샤오신' in (raw_title or ''):
                opt_name = f"샤오신 패드 {opt_name}"
            if _PURE_QTY_OPTION_RE.match(opt_name) and (first_valid_prefix or base_title_prefix):
                prefix_to_use = first_valid_prefix or base_title_prefix
                opt_name = f"{prefix_to_use[:30]} ({opt_name})"
            p_val, p_curr = parse_price_token(m.group(2), default_currency)
            if _is_valid_option_name(opt_name) and p_val >= 100:
                if not first_valid_prefix:
                    first_valid_prefix = re.sub(r'\s*(?:1\+1|\d+\+\d+|\d+\s*(?:개|입|팩|세트)).*$', '', opt_name).strip()
                key = (opt_name.lower(), p_val)
                if key not in seen_keys:
                    seen_keys.add(key)
                    matched_url = ""
                    opt_tokens = [t.lower() for t in re.findall(r'[가-힣a-zA-Z0-9.]{2,}', opt_name)]
                    best_overlap = 0
                    for blk_text, blk_u in body_shop_urls:
                        bt_low = blk_text.lower()
                        ov = sum(1 for tok in opt_tokens if tok in bt_low)
                        if ov > best_overlap:
                            best_overlap = ov
                            matched_url = blk_u
                    if not matched_url and idx_m < len(body_shop_urls):
                        matched_url = body_shop_urls[idx_m][1]
                    if not matched_url:
                        matched_url = cleaned_default_url
                    options.append({
                        "name": opt_name[:60],
                        "price": p_val,
                        "currency": p_curr,
                        "url": matched_url,
                        "code": "",
                        "image_url": "",
                        "sub_urls": [],
                    })
        if len(options) >= 2:
            return options

    # 패턴 C: 괄호 밖 슬래시(/) 또는 쉼표(,) 분할형, 예: 뷰몬트 15.6 모니터 $34.56/경동나비엔 EME550S 24만, 상스치 3900원, 오레오 맥플러리 1800원
    paren_tokens = {}
    def _mask_paren(m):
        key = f"__PAREN_{len(paren_tokens)}__"
        paren_tokens[key] = m.group(0)
        return key

    # 대괄호와 소괄호 임시 마스킹 (괄호 내부의 슬래시/쉼표 및 배송비 오탐 방지)
    masked_title = re.sub(r'\[[^\]]*\]', _mask_paren, title_no_mall)
    masked_title = re.sub(r'\([^\)]*\)', _mask_paren, masked_title)

    if '/' in masked_title or ',' in masked_title:
        slash_segments = [
            s.strip()
            for s in re.split(r'\s*/\s*|\s*,\s+|(?<=[원만불러\$])\s*,\s*', masked_title)
            if s and s.strip()
        ]
        if len(slash_segments) >= 2:
            slash_options = []
            for idx_seg, raw_seg in enumerate(slash_segments):
                # 마스킹 복원
                seg = raw_seg
                for pk, pv in paren_tokens.items():
                    seg = seg.replace(pk, pv)

                # 가격 및 통화 추출
                seg_prices = extract_scored_prices_from_line(seg)
                if not seg_prices:
                    m_p = re.search(
                        r'(\$\s*[0-9,]+(?:\.[0-9]+)?|[0-9,]+(?:\.[0-9]+)?\s*(?:만\s*원?|원|달러|불|유로|€(?!\s*[0-9])|\$(?!\s*[0-9]))(?!\s*(?:적립|할인|쿠폰|캐시백|페이백|청구|이상|이하|증정|상당))|[₩￦€]\s*[0-9,]+(?:\.[0-9]+)?)',
                        seg,
                    )
                    if m_p:
                        p_val, p_curr = parse_price_token(m_p.group(1), default_currency)
                    else:
                        continue
                else:
                    p_val, p_curr, _ = max(seg_prices, key=lambda x: x[2])

                # 상품명 정제 (가격 문자열 및 배송비/수식어 제거)
                name_cand = re.sub(
                    r'(?:[₩￦]\s*[0-9,]+|(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,7})\s*원|[0-9]+(?:\.[0-9]+)?\s*만\s*원?|\$\s*[0-9,.]+|[0-9,.]+\s*(?:\$(?!\s*[0-9])|달러|불|유로|€(?!\s*[0-9]))).*$',
                    '',
                    seg,
                ).strip()
                name_cand = re.sub(r'^\s*\[[^\]]+\]\s*', '', name_cand).strip(' -·:,/~+[]()')
                name_cand = re.sub(r'(?:무배|무료배송|배송비무료|무료|네멤무배|외)$', '', name_cand).strip(' -·:,/~+[]()')
                name_cand = _clean_option_display_name(name_cand)
                if re.match(r'^(?:프로\s*)?\d{1,4}(?:\.\d+)?$', name_cand) and '샤오신' in (raw_title or ''):
                    name_cand = f"샤오신 패드 {name_cand}"

                if _is_valid_option_name(name_cand) and p_val >= 100:
                    matched_u = body_shop_urls[idx_seg][1] if idx_seg < len(body_shop_urls) else cleaned_default_url
                    slash_options.append({
                        "name": name_cand[:60],
                        "price": p_val,
                        "currency": p_curr,
                        "url": matched_u,
                        "code": "",
                        "image_url": "",
                        "sub_urls": [],
                    })

            if len(slash_options) >= 2:
                return slash_options

    # =========================================================================
    # 4단계: 단일 링크 모음전/골라담기 게시글의 본문 라인별 '상품명 + 가격' 추출
    # =========================================================================
    is_collection_title = bool(re.search(r'(?:외\s*\d+|골라담기|모음|선택|다양|\d+\s*종|~)', raw_title or ''))
    if not is_collection_title or not raw_lines:
        return []

    line_options: list[dict] = []
    for ln in raw_lines:
        if len(ln) < 3 or len(ln) > 100 or ln.startswith('[첨부된 이미지') or ln.startswith('[상품이미지:'):
            continue
        ln_clean = url_pattern.sub('', ln).replace('()', '').strip()
        if not ln_clean:
            continue
        ln_no_unit = re.sub(r'\([^\)]*(?:당|개당|병당|포당|통당|박스당|팩당|캔당|g당|ml당|미당)[^\)]*\)', '', ln_clean)
        ln_no_cond = re.sub(r'[\d,]+\s*(?:만\s*원?|천\s*원?|원)\s*(?:이상|이하|할인|적립|쿠폰|캐시백|배송비|택배비)', '', ln_no_unit)

        m_opt = re.search(r'^(.{2,45}?)\s*[:\-–~·/(\[]+\s*(\$?\s*(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,7}|[0-9]+\.[0-9]+)\s*(?:원|달러|불|유로|€|\$)|[₩￦]\s*[0-9,]+)', ln_no_cond)
        if m_opt:
            opt_name = _clean_option_display_name(m_opt.group(1))
            if not _is_valid_option_name(opt_name):
                continue
            p_val, p_curr = parse_price_token(m_opt.group(2), default_currency)
            if p_val >= 500:
                key = (opt_name[:30].lower(), p_val)
                if key not in seen_keys:
                    seen_keys.add(key)
                    line_options.append({
                        "name": opt_name[:60],
                        "price": p_val,
                        "currency": p_curr,
                        "url": cleaned_default_url,
                        "code": "",
                        "image_url": "",
                        "sub_urls": [],
                    })

    distinct_line_names = {o["name"].lower() for o in line_options}
    if len(line_options) >= 2 and len(distinct_line_names) >= 2:
        return line_options[:25]

    return []


def format_multi_options_summary(options: list[dict], prefix_hot: bool = False) -> str:
    """추출된 다중 옵션 리스트를 앱/웹 카드에서 한눈에 볼 수 있는 요약 문자열로 포맷팅합니다."""
    if not options or len(options) < 2:
        return ""
    lines = []
    for opt in options[:6]:
        name = opt.get("name", "옵션")
        price = int(opt.get("price", 0) or 0)
        curr = (opt.get("currency") or "KRW").upper()
        if curr == "USD":
            p_str = f"${price / 100:.2f}"
        elif curr == "EUR":
            p_str = f"€{price / 100:.2f}"
        else:
            p_str = f"{price:,}원"
        lines.append(f"• {name}: {p_str}")
    if len(options) > 6:
        lines.append(f"• 외 {len(options) - 6}개 구성 상품")
    header = f"[구성 상품 {len(options)}종 가격 안내]"
    if prefix_hot:
        header = "[커뮤니티 인증 핫딜] " + header
    return header + "\n" + "\n".join(lines)


class AggregatorService:
    """
    🔗 스크래핑 결과(Deal)를 정규화하여 DB의 Deal과 매칭 및 병합하는 연동 레이어
    지능형 업서트(Upsert)와 가격 역사(Price History) 추적 로직이 포함됩니다.
    """
    
    def __init__(self, db_session: Session):
        self.db = db_session
        self.normalizer = LlmNormalizer()

    @staticmethod
    async def _enrich_option_images_safely(options: list[dict], max_concurrency: int = 4) -> None:
        """
        개별 옵션 중 인라인 본문 이미지가 없는 항목에 한해,
        오직 해당 옵션의 고유 쇼핑몰 직링크(url)로부터 og:image를 1:1 독립 추출합니다.
        절대 다른 옵션의 이미지를 복사하거나 대표 이미지를 전용하지 않습니다 (Cross-contamination 원천 차단).
        실패 시 빈 상태("")를 유지합니다.
        """
        if not options:
            return

        import httpx
        from bs4 import BeautifulSoup
        from urllib.parse import urljoin

        community_hosts = (
            'ppomppu.co.kr', 'fmkorea.com', 'fmkorea.org', 'ruliweb.com',
            'quasarzone.com', 'clien.net', 'bbasak.com'
        )

        async def _fetch_single_option_image(opt: dict, client: httpx.AsyncClient):
            if opt.get("image_url"):
                return  # 이미 1:1 매칭된 본문 이미지가 있으면 보존
            u = (opt.get("url") or "").strip()
            if u and u.startswith("http") and not any(h in u.lower() for h in community_hosts):
                # 1. 지마켓 초고속 CDN 이미지 우회 조립
                if "gmarket.co.kr" in u:
                    m_g = re.search(r'goodscode=([0-9]+)', u)
                    if m_g:
                        opt["image_url"] = f"https://gdimg.gmarket.co.kr/{m_g.group(1)}/still/600"
                        return

                # 2. 옥션 초고속 CDN 이미지 우회 조립
                if "auction.co.kr" in u:
                    m_a = re.search(r'itemno=([a-zA-Z0-9]+)', u, re.IGNORECASE)
                    if m_a:
                        opt["image_url"] = f"https://itemimage.auction.co.kr/itemimage/{m_a.group(1)}.jpg"
                        return

                # 3. 외부 쇼핑몰 메타태그(og:image / twitter:image) 비동기 타격 (타임아웃 2.5초)
                try:
                    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
                    res = await client.get(u, headers=headers, timeout=2.5, follow_redirects=True)
                    if res.status_code == 200 and res.text:
                        soup = BeautifulSoup(res.text, "html.parser")
                        meta_img = (
                            soup.find("meta", property="og:image")
                            or soup.find("meta", attrs={"name": "twitter:image"})
                            or soup.find("meta", property="twitter:image")
                            or soup.find("meta", property="image")
                        )
                        if meta_img and meta_img.get("content"):
                            img_cand = meta_img["content"].strip()
                            if img_cand.startswith("//"):
                                img_cand = "https:" + img_cand
                            elif not img_cand.startswith("http"):
                                img_cand = urljoin(str(res.url), img_cand)
                            
                            low_img = img_cand.lower().split("?")[0]
                            if not any(bad in low_img for bad in ("logo", "icon", "blank", "noimage", "transparent", "avatar")) and not low_img.endswith((".svg", ".ico")):
                                opt["image_url"] = img_cand
                                return
                except Exception:
                    pass

            # 4. 옵션 상품명 기반 네이버/다음 쇼핑 썸네일 초고속 검색 폴백 (외부 링크 부재 또는 og:image 실패 시)
            opt_name = (opt.get("name") or "").strip()
            clean_q = re.sub(r'^(?:[#\d]+[\.\)\s]+|[①-⑳]\s*)', '', opt_name).strip()
            clean_q = re.sub(r'[\d,.]+\s*(?:만\s*원?|원|달러|불|유로|€|\$)', '', clean_q).strip(' -·:,/~+[]()')
            clean_q = re.sub(r'\([^\)]*\)', '', clean_q).strip()

            if len(clean_q) >= 2:
                try:
                    from urllib.parse import quote, unquote
                    req_hdrs = {
                        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                        "Accept-Language": "ko-KR,ko;q=0.9"
                    }
                    # 1) 네이버 검색 썸네일 (search.pstatic.net)
                    rn = await client.get(
                        f"https://search.naver.com/search.naver?where=nexearch&query={quote(clean_q)}",
                        headers=req_hdrs,
                        timeout=3.0
                    )
                    if rn.status_code == 200 and rn.text:
                        n_imgs = re.findall(r'(https://search\.pstatic\.net/common/\?src=[^\s"\'&<>]+)', rn.text)
                        for cand in n_imgs[:6]:
                            low_dec = unquote(cand).lower()
                            if not any(b in low_dec for b in ('blogpfthumb', 'kinimage', 'musicmeta', 'profile', 'icon', 'logo', 'blank', 'avatar', 'default')):
                                opt["image_url"] = cand
                                return

                    # 2) 다음 카카오 CDN (kakaocdn.net)
                    rd = await client.get(
                        f"https://search.daum.net/search?w=tot&q={quote(clean_q)}",
                        headers=req_hdrs,
                        timeout=3.0
                    )
                    if rd.status_code == 200 and rd.text:
                        d_imgs = re.findall(r'(https://search\d+\.kakaocdn\.net/argon/[^\s"\'<>]+)', rd.text)
                        for cand in d_imgs[:4]:
                            low_cand = cand.lower()
                            if not any(b in low_cand for b in ('profile', 'icon', 'logo', 'blank', 'avatar')):
                                opt["image_url"] = cand
                                return

                    # 3) 빙 이미지 검색 폴백 (희귀 직구/단종 모델)
                    res_b = await client.get(
                        f"https://www.bing.com/images/search?q={quote(clean_q + ' 상품')}&FORM=HDRSC2",
                        headers=req_hdrs,
                        timeout=3.0
                    )
                    if res_b.status_code == 200 and res_b.text:
                        murls = re.findall(r'murl&quot;:&quot;(https?://[^&]+?)&quot;', res_b.text)
                        for candidate in murls[:6]:
                            low_c = candidate.lower()
                            if not any(b in low_c for b in ('logo', 'icon', 'blank', 'avatar', 'profile')) and not low_c.endswith(('.svg', '.ico')):
                                opt["image_url"] = candidate
                                return
                except Exception as e:
                    logger.debug(f"Option image search fallback failed for '{clean_q}': {e}")

        targets = [o for o in options if not o.get("image_url")][:8]
        if not targets:
            return

        sem = asyncio.Semaphore(max_concurrency)
        async with httpx.AsyncClient(timeout=3.5, follow_redirects=True) as client:
            async def _worker(opt):
                async with sem:
                    await _fetch_single_option_image(opt, client)
            await asyncio.gather(*[_worker(t) for t in targets], return_exceptions=True)

    async def process_scraped_deal(self, community_id: int, scraped_data: dict) -> Deal:
        from backend.core.url_utils import normalize_url
        raw_title = (scraped_data.get("title", "") or "").replace("\xa0", " ").replace("릿模셈", "넾다세일").replace("\ufffd", "").strip()
        provided_price = scraped_data.get("price", 0)
        url = normalize_url(scraped_data.get("url", "") or "")
        scraped_data["url"] = url
        if scraped_data.get("ecommerce_link"):
            scraped_data["ecommerce_link"] = normalize_url(_decode_and_clean_shop_url(scraped_data["ecommerce_link"]))
        shop_name = infer_shop_name(raw_title, scraped_data.get("ecommerce_link", "") or url, scraped_data.get("shop_name", ""))
        raw_img = (scraped_data.get("image_url", "") or "").strip()
        if raw_img and any(b in raw_img.lower() for b in ("noimage", "no_image", "thumb_no_image", "dot03.gif", "logo_f.svg")):
            raw_img = None
        scraped_data["image_url"] = raw_img
        image_url = raw_img
        
        community = self.db.query(Community).filter(Community.id == community_id).first()
        community_name = community.name if community else ""
        
        # 0. 정규식 파서를 통한 1차 정보 완전 추출
        is_closed = scraped_data.get("is_closed", False) or "종료" in raw_title or "마감" in raw_title or "품절" in raw_title
        
        # 기본 배송비는 스크래퍼가 제공한 값이나 '정보 없음'
        shipping_fee = scraped_data.get("shipping_fee")
        if not shipping_fee:
            shipping_fee = "정보 없음"

        final_price = 0
        currency = "KRW"
        
        title_price = 0
        title_currency = "KRW"

        # 단위당 가격(예: 100g당 990원)의 단위 숫자가 대표 가격(100원)으로 오인되는 현상 및 날짜 괄호((11/2), (11/2~30)) 오인 차단
        title_for_price = re.sub(r'\d+(?:\.\d+)?\s*(?:kg|g|ml|l|개|입|봉|팩|포|장|매|구|미|알|병|캔|통|박스)\s*당', ' ', raw_title, flags=re.IGNORECASE)
        title_for_price = re.sub(r'\(\s*(?:0?[1-9]|1[0-2])\s*[/\.]\s*(?:0?[1-9]|[12]\d|3[01])(?:\s*~\s*[^\)]*)?\s*\)', ' ', title_for_price)
        has_nonzero_price_in_title = bool(re.search(r'[1-9][\d,.]*\s*(?:만\s*원?|원|달러|불|유로|€|\$)|\$\s*[1-9][\d,.]*', title_for_price))
        is_explicit_free_deal = bool(
            re.search(r'\(\s*0\s*원\s*\)|무료\s*배포|한정\s*무료|일시\s*무료|무료\s*\(유료', raw_title, re.IGNORECASE)
            or (re.search(r'\(\s*(?:무료|free)\s*\)', raw_title, re.IGNORECASE) and not has_nonzero_price_in_title)
        )
        
        # 1. 괄호/대괄호 속 달러/유로/원 우선 파싱 (예: (53,475원 외/무료), (129,000원 무료배송), ($7.66/$4.4), (46638/무), [899,000])
        match = re.search(
            r'[\(\[]\s*(?:최저가?\s*|체감가?\s*|쿠폰가?\s*|할인가?\s*|약\s*)*\$?\s*((?:\d{1,3}(?:,\d{3})+|\d+(?:\.\d+)?)(?!\s*,\s*\d{3})\s*(?:만\s*원?|원|달러|불|유로|€|\$)?)\s*(?:외|부터|~|각|이상|이하|전후)?\s*(?:[/,\s]\s*([^\)\]]*))?[\)\]]',
            title_for_price
        )
        if match:
            raw_token_p = match.group(1).replace(',', '').strip()
            has_slash_or_unit = bool(match.group(2)) or any(u in match.group(0) for u in ('원', '만', '달러', '불', '유로', '$', '€'))
            # 단순 수량/용량 괄호 (예: (180일분), (2024)) 오인 방지를 위해 단위 또는 슬래시 배송비가 있거나 4자리 이상일 때만 인정
            if has_slash_or_unit or (raw_token_p.isdigit() and len(raw_token_p) >= 4 and not re.match(r'^202\d$', raw_token_p)):
                if '달러' in raw_token_p or '불' in raw_token_p or '$' in match.group(0) or ('.' in raw_token_p and '만' not in raw_token_p and '유로' not in raw_token_p and '€' not in match.group(0)):
                    title_currency = "USD"
                    clean_p = re.sub(r'[^\d.]', '', raw_token_p)
                    if clean_p.replace('.', '', 1).isdigit():
                        title_price = round(float(clean_p) * 100)
                elif '유로' in raw_token_p or '€' in match.group(0):
                    title_currency = "EUR"
                    clean_p = re.sub(r'[^\d.]', '', raw_token_p)
                    if clean_p.replace('.', '', 1).isdigit():
                        title_price = round(float(clean_p) * 100)
                elif '만' in raw_token_p:
                    clean_p = re.sub(r'[^\d.]', '', raw_token_p)
                    if clean_p.replace('.', '', 1).isdigit():
                        title_price = int(float(clean_p) * 10000)
                else:
                    clean_p = re.sub(r'[^\d]', '', raw_token_p)
                    if clean_p.isdigit():
                        title_price = int(clean_p)
                if match.group(2):
                    shipping_raw = match.group(2).lower()
                    if any(k in shipping_raw for k in ('무료', '무배', 'fs', 'free')) or shipping_raw.strip() == '무':
                        shipping_fee = "무료배송"

        # 2. 괄호 외 만/원/달러/유로 및 슬래시(5980/무배), 대괄호([롯데온 18000]) 파싱
        if title_price == 0 and not is_explicit_free_deal:
            title_currency = "KRW"
            match_man = re.search(
                r'(?<!보배\s)(?<!보조배터리\s)(?<![0-9Ww]\s)([\d,]*\.[\d]+|[\d,]+)만(?!\s*(?:원\s*)?(?:할인|적립|쿠폰|캐시백|페이백|이상|권|mAh|mah|암페어))',
                title_for_price
            )
            if match_man and ('$' in title_for_price and '만원' not in title_for_price and '만 원' not in title_for_price):
                match_man = None
            if match_man:
                title_price = int(float(match_man.group(1).replace(',', '')) * 10000)
            else:
                match_won = re.search(
                    r'(?<![+\-\d,])(?<!\d\s~\s)(?<!~\s)([0-9,]{3,})\s*원(?!\s*(?:할인|적립|쿠폰|캐시백|페이백|청구|즉시|추가|이상|권))',
                    title_for_price
                )
                if match_won:
                    title_price = int(match_won.group(1).replace(',', ''))
                else:
                    match_slash = re.search(r'(?<!\d)([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,7})\s*원?\s*(?:외|부터|~)?\s*(?:[/,]\s*(무배|무료|무료배송|무|유료|택배|와우|배송비|[0-9,]{3,5}\s*원?)|\s+(무배|무료배송))', title_for_price)
                    if match_slash:
                        title_price = int(match_slash.group(1).replace(',', ''))
                        if (match_slash.group(2) or match_slash.group(3)) in ('무배', '무료', '무료배송', '무', '와우'):
                            shipping_fee = "무료배송"
                    else:
                        match_bracket = re.search(r'\[\s*(?:[가-힣a-zA-Z]+\s+)?([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,7})\s*원?\s*\]', title_for_price)
                        if match_bracket:
                            title_price = int(match_bracket.group(1).replace(',', ''))
                        else:
                            match_dollar = re.search(r'\$\s*([0-9,]+(?:\.[0-9]+)?)|([0-9,]+(?:\.[0-9]+)?)\s*달러', title_for_price)
                            if match_dollar: 
                                title_currency = "USD"
                                val = match_dollar.group(1) or match_dollar.group(2)
                                title_price = round(float(val.replace(',', '')) * 100)
                            else:
                                match_euro = re.search(r'€\s*([0-9,]+(?:\.[0-9]+)?)|([0-9,]+(?:\.[0-9]+)?)\s*유로', title_for_price)
                                if match_euro:
                                    title_currency = "EUR"
                                    val = match_euro.group(1) or match_euro.group(2)
                                    title_price = round(float(val.replace(',', '')) * 100)
        
        # 기본 정책: 타이틀 가격을 우선시
        if is_explicit_free_deal:
            final_price = 0
            currency = "KRW"
        elif title_price > 0:
            final_price = title_price
            currency = title_currency
            
        # 퀘이사존, 펨코는 핫딜 규정상 기재된 가격 태그가 가장 정확하므로 우선시함 (단, 무료배포/무료게임 제외)
        if not is_explicit_free_deal and community_name in ["quasarzone", "퀘이사존", "fmkorea", "펨코"] and provided_price > 0:
            final_price = provided_price
            currency = scraped_data.get("currency", "KRW")
        elif not is_explicit_free_deal and provided_price > 0:
            if title_price > 0 and abs(title_price - provided_price) / max(title_price, provided_price) > 0.1:
                logger.info(f"[가격 불일치 감지] 타이틀: {title_price}, 본문: {provided_price}. 대표 가격을 {title_price}로 고정합니다.")
                final_price = title_price
                currency = title_currency

        # 제목에서 추출 실패 시 본문에서 스크래퍼가 넘겨준 가격 사용 (단, 선착순 쿠폰/할인코드 안내글 및 무료배포 글은 제외)
        is_coupon_guide_post = any(k in raw_title for k in ("선착순 쿠폰", "할인코드", "쿠폰 받기", "쿠폰 미리", "쿠폰 받아두세요")) and not re.search(r'[\d,]+\s*(?:원|만|\$|달러|유로|€)', raw_title)
        if final_price == 0 and provided_price > 0 and not is_coupon_guide_post and not is_explicit_free_deal:
            final_price = provided_price
            if scraped_data.get("currency"):
                currency = scraped_data.get("currency")

        # 다중 상품/옵션 규칙 기반 정밀 추출 (타이틀 다중 옵션 & 본문 멀티 링크/라인 옵션)
        content_html_for_opts = scraped_data.get("content_html", scraped_data.get("content", "")) or ""
        structured_opts = extract_structured_multi_options(
            raw_title=raw_title,
            content_html=content_html_for_opts,
            default_currency=currency,
            default_url=scraped_data.get("ecommerce_link") or ""
        )
        if not structured_opts and scraped_data.get("options_data") and isinstance(scraped_data["options_data"], list):
            structured_opts = scraped_data["options_data"]

        if structured_opts and len(structured_opts) >= 2:
            try:
                await self._enrich_option_images_safely(structured_opts)
            except Exception as e:
                logger.error(f"Error enriching option images safely: {e}")
            scraped_data["options_data"] = structured_opts
            valid_opts = [o for o in structured_opts if int(o.get("price", 0)) > 0]
            if valid_opts:
                first_opt = valid_opts[0]
                opt_currencies = {o.get("currency", "KRW") for o in valid_opts}
                has_explicit_krw_title = bool(re.search(r'(?:[0-9,]+\s*원|[0-9.]+\s*만)', raw_title)) and '$' not in raw_title
                if final_price == 0 or title_price == 0:
                    final_price = int(first_opt["price"])
                    currency = first_opt.get("currency", currency or "KRW")
                elif opt_currencies == {"USD"} and currency == "KRW" and not has_explicit_krw_title:
                    final_price = int(first_opt["price"])
                    currency = "USD"
                elif has_explicit_krw_title and currency == "KRW" and opt_currencies == {"USD"}:
                    # 제목에 명확한 원화(만/원)가 있는데 본문 옵션이 전부 달러인 경우 제목 통화(KRW) 유지
                    pass
            if not image_url:
                opts_with_img = [o for o in structured_opts if o.get("image_url")]
                if opts_with_img:
                    image_url = opts_with_img[0]["image_url"]
                    scraped_data["image_url"] = image_url
            if not scraped_data.get("ecommerce_link"):
                opts_with_url = [o for o in structured_opts if o.get("url")]
                if opts_with_url:
                    scraped_data["ecommerce_link"] = opts_with_url[0]["url"]

        if "무배" in raw_title or "무료배송" in raw_title or "무료" in raw_title or "/무)" in raw_title: 
            shipping_fee = "무료배송"
        
        if shipping_fee:
            sf_str = re.sub(r'\s+', ' ', str(shipping_fee)).strip()
            if len(sf_str) > 25 or 'http://' in sf_str or 'https://' in sf_str or '게시물입니다' in sf_str:
                shipping_fee = "무료배송" if any(k in sf_str for k in ("무료배송", "무배", "배송비 무료", "배송비포함", "배송비 포함", "Free shipping")) else "정보 없음"
            elif sf_str in ["0", "0원", "무", "무료", "무배", "무료배송", "臾대즺", "臾대같", "-", "."]:
                shipping_fee = "무료배송" if sf_str not in ("-", ".") else "정보 없음"
            elif re.match(r'^[0-9,]+$', sf_str):
                val_sf = int(sf_str.replace(',', ''))
                shipping_fee = "무료배송" if val_sf == 0 else f"{val_sf:,}원"
            elif re.match(r'^(0원?|무료|무배|臾대즺|臾대같)\s*(/|\+)', sf_str):
                shipping_fee = re.sub(r'^(0원?|무료|무배|臾대즺|臾대같)\s*', '무료배송 ', sf_str)
            else:
                shipping_fee = sf_str
        
        # [Phase 6] 하이브리드 파이프라인 Step 2: 쇼핑몰 메타태그 직공 (비동기 httpx로 이벤트 루프 블로킹 원천 차단)
        import httpx
        from bs4 import BeautifulSoup
        
        if final_price == 0 and scraped_data.get("ecommerce_link"):
            try:
                target_url = scraped_data.get("ecommerce_link")
                if 'coupang' not in target_url: # 쿠팡은 WAF 방어가 100%이므로 제외
                    logger.info(f"[Spider] 정규식 실패. 쇼핑몰({target_url})에 직접 침투하여 메타태그 스나이핑 시도...")
                    async with httpx.AsyncClient(timeout=2.0, follow_redirects=True) as client:
                        res = await client.get(target_url, headers={'User-Agent': 'Mozilla/5.0'})
                        if res.status_code == 200:
                            soup = BeautifulSoup(res.text, 'html.parser')
                            # og:price:amount, itemprop=price 등 표준 마크업 탐색
                            meta_price = soup.find('meta', property='product:price:amount') or soup.find('meta', property='og:price:amount')
                            if meta_price and meta_price.get('content'):
                                final_price = int(float(meta_price['content'].replace(',', '')))
                                logger.info(f"[Hit] 쇼핑몰 메타태그에서 가격 적중! {final_price}원")
            except Exception as e:
                logger.debug(f"쇼핑몰 메타 스나이핑 실패: {e}")
                
        # 산술/논리 검증: 원화(KRW) 기준 터무니없이 낮은 가격(예: 100원 미만)은 추출 오류나 적립금액(53원 등)일 확률이 높으므로 0으로 보정
        if currency == "KRW" and final_price > 0 and final_price < 100:
            logger.info(f"[Warning] 가격 비정상 감지 ({final_price}원). 오류 또는 포인트성 숫자로 간주하여 0원으로 초기화.")
            final_price = 0
        # 상한선 검증: 고사양 조립PC(RTX 5080/5090 등) 및 대형 가전을 수용하도록 1,500만 원까지 허용
        elif currency == "KRW" and final_price > 15000000:
            logger.info(f"[Warning] 가격 비정상 감지 ({final_price}원). 오류 또는 상품코드로 간주하여 0원으로 초기화.")
            final_price = 0

        price = final_price
        
        # 🚨 스팸 및 공지사항 / 게시판 잡담·질문글 / URL만 적힌 글 필터링
        spam_keywords = [
            "공지", "질문", "투패", "몰테일", "폐업", "출고", "지연", "지쟈스", "배대지", "안내",
            "도와주세요", "어떤가요", "어디까지", "갈수있는데", "할수있는데", "가능할까요",
            "입고금지", "수익링크", "바이럴", "금지조치", "활동내역", "제재조치"
        ]
        if (
            raw_title.startswith("http://")
            or raw_title.startswith("https://")
            or any(keyword in raw_title for keyword in spam_keywords)
            or (price == 0 and "?" in raw_title)
        ):
            logger.info(f"[Spam Filtered] 핫딜이 아닌 게시판 정보 스킵: {raw_title}")
            return None
        
        # 스크래퍼 단에서 명시적으로 수집한 카테고리가 있다면 전달
        scraped_category = scraped_data.get("category")

        # 1. 원본 텍스트 및 수집처 카테고리를 함께 정규화기에 전달
        normalized = await self.normalizer.normalize(raw_title, scraped_category)
        
        final_category = normalized.category

        # 적립/포인트 강제 보정 (사용자 요청: 적립 탭으로 분리)
        event_keywords = ["추첨", "설문", "무료배포", "체험단", "선착순", "라이브", "방송", "라방"]
        point_keywords = ["적립", "포인트", "페이백", "앱테크"]
        # 순수 적립이 아닌 구매/조건부 쇼핑을 걸러내기 위한 금지어
        not_point_keywords = ["결제", "구매", "이상", "슈퍼적립", "혜택", "사은품", "증정", "할인"]
        
        check_title = raw_title.replace("드라이브", "")
        
        is_event = any(kw in check_title for kw in event_keywords)
        is_point = False
        
        if any(kw in check_title for kw in point_keywords) and not any(kw in check_title for kw in not_point_keywords):
            # 사용자가 명시한 대로, "적립" 카테고리는 100원 이하(또는 0원)일 때만 순수 적립(앱테크)으로 취급
            if price <= 100:
                is_point = True
        
        # '적립'이나 '페이백'이 있어도 일반적인 라이브 방송 상품 판매 예고면 포인트(적립)가 아니라 이벤트로 처리
        # 단, 가격이 100원 이하인 소액 적립(라이브 시청 보상 등)은 예외로 '적립' 유지
        if is_point and any(kw in check_title for kw in ["라이브", "예고", "방송", "라방"]):
            if price > 100:
                is_point = False
                is_event = True
            
        if not is_event and "예고" in check_title:
            if price <= 1000:
                is_event = True
                
        if "무료" in check_title and "배송" not in check_title and "무배" not in check_title and "택배" not in check_title:
            if price == 0:
                is_event = True
            
        # 적립 또는 이벤트(특히 선착순 쿠폰/할인코드 안내글) 강제 보정 적용
        if is_coupon_guide_post:
            final_category = "이벤트"
        elif community_id in [8, 10] or price == 0:
            if is_point:
                final_category = "적립"
            elif is_event:
                final_category = "이벤트"

        # [Phase 12] 실제 게시글 작성 시간 반영 (스크래핑 시점이 아닌 실제 업로드 시점)
        posted_at_iso = scraped_data.get("posted_at")
        posted_dt = None
        if posted_at_iso:
            try:
                posted_dt = datetime.fromisoformat(posted_at_iso.replace("Z", "+00:00"))
                # Ensure datetime has UTC timezone info before storing
                if posted_dt.tzinfo is None:
                    posted_dt = posted_dt.replace(tzinfo=timezone.utc)
                else:
                    posted_dt = posted_dt.astimezone(timezone.utc)
                # 미래 시간(KST 중복 가산 등) 방어: 현재 UTC 시각을 초과하면 현재 시각으로 클램핑
                now_utc_chk = datetime.now(timezone.utc)
                if posted_dt > now_utc_chk:
                    posted_dt = now_utc_chk
            except Exception:
                pass

        # [Phase 6.5] 💡 글로벌 쇼핑몰 링크 캐싱 (고유 상품 키가 확인되고 제목이 유사한 동일 상품일 때만 캐시 재사용)
        cached_ai_summary = None
        ecommerce_url = scraped_data.get("ecommerce_link")
        from backend.core.url_utils import extract_canonical_product_key
        if ecommerce_url and len(ecommerce_url) > 15 and extract_canonical_product_key(ecommerce_url):
            from datetime import timedelta
            recent_duplicate = self.db.query(Deal).filter(
                Deal.ecommerce_link == ecommerce_url,
                Deal.title == raw_title,
                Deal.indexed_at >= datetime.utcnow() - timedelta(days=2)
            ).first()
            
            if recent_duplicate and recent_duplicate.ai_summary:
                logger.info(f"[Global Cache Hit] 중복 핫딜의 AI 분석을 복사합니다: {ecommerce_url}")
                cached_ai_summary = recent_duplicate.ai_summary
                if final_price == 0 and recent_duplicate.price and recent_duplicate.price != "0":
                    price = int(recent_duplicate.price)
                    final_price = price
                if not final_category:
                    final_category = recent_duplicate.category

        content_html = scraped_data.get("content_html", scraped_data.get("content", ""))
        image_url = scraped_data.get("image_url")

        # 2. 중복 및 롤링 윈도우 클러스터링 체크 (Upsert 로직의 핵심)
        existing_deals = self.db.query(Deal).filter(Deal.post_link == url).all()
        
        # [이중 중복 방어 가드]
        # 1) 일반 게시글: 최근 72시간 이내 동일 커뮤니티 또는 알리뽐뿌(4)/뽐뿌해외(11) 간 완전히 동일한 제목이면 중복으로 간주하여 Upsert 처리
        # 2) 다중 옵션/모음전 게시글(예: [원두모음], [미니PC 8종]): 최근 14일 이내 동일 제목이면 재등록(끌올) 시 신규 행을 늘리지 않고 기존 행을 최신화
        if not existing_deals and raw_title:
            from datetime import timedelta
            is_multi_or_promo = bool(scraped_data.get("options_data")) or bool(re.search(r'^\s*\[(?:원두모음|광군제|특가|미니PC|보배)[^\]]*\]|^특가\)', raw_title))
            lookback_hours = 336 if is_multi_or_promo else 72
            from sqlalchemy import or_
            comm_candidates = [community_id, 4, 11] if community_id in (1, 4, 11) else [community_id]
            existing_deals = self.db.query(Deal).filter(
                Deal.source_community_id.in_(comm_candidates),
                Deal.title == raw_title,
                or_(
                    Deal.is_closed == False,
                    Deal.indexed_at >= datetime.utcnow() - timedelta(hours=lookback_hours)
                )
            ).all()
            
        
        # [24시간 롤링 윈도우 클러스터링]: 동일 URL이 아니더라도 24시간 내 동일 상품이면 병합 시도
        if not existing_deals:
            from datetime import timedelta
            from backend.core.url_utils import normalize_url, extract_canonical_product_key
            from backend.routers.community import (
                has_quantity_conflict,
                has_model_conflict,
                has_brand_or_keyword_conflict,
                has_price_conflict,
            )

            recent_deals = self.db.query(Deal).filter(
                Deal.source_community_id == community_id,
                Deal.indexed_at >= datetime.utcnow() - timedelta(hours=24)
            ).all()
            target_deals = []

            def get_clean_tokens(text: str) -> set:
                cleaned = re.sub(r'[^a-zA-Z0-9가-힣\s]', ' ', (text or "").lower())
                return {
                    token for token in cleaned.split()
                    if len(token) >= 2 and token not in ["to", "and", "or", "for", "pcs", "무료", "무배", "무료배송", "특가", "할인"]
                }

            input_tokens = get_clean_tokens(raw_title)

            def is_safe_to_merge(candidate: Deal) -> bool:
                cand_tokens = get_clean_tokens(candidate.title)
                cand_price = int(candidate.price) if str(candidate.price or "").isdigit() else 0
                if has_quantity_conflict(input_tokens, cand_tokens):
                    return False
                if has_model_conflict(input_tokens, cand_tokens):
                    return False
                if has_brand_or_keyword_conflict(input_tokens, cand_tokens):
                    return False
                if has_price_conflict(price, cand_price):
                    return False
                return True

            # 1. 쇼핑몰 고유 상품 키(Canonical Product Key) 및 정규화 링크 기준 매칭 (쿠팡, 네이버, 11번가, 지마켓 등 모두 지원)
            target_url = scraped_data.get("ecommerce_link")
            if target_url and len(target_url) > 12:
                normalized_target_url = normalize_url(target_url)
                target_canon_key = extract_canonical_product_key(target_url)
                for d in recent_deals:
                    if not d.ecommerce_link:
                        continue
                    d_canon = extract_canonical_product_key(d.ecommerce_link)
                    if (target_canon_key and d_canon == target_canon_key) or (d.ecommerce_link in (target_url, normalized_target_url)):
                        if is_safe_to_merge(d):
                            target_deals.append(d)

            # 2. 정규화된 상품명(base_product_name) 기준 매칭 (용량/모델/가격 충돌 방어 + 핵심 명사 최소 2개 일치 필수)
            if not target_deals and normalized.name and len(normalized.name) >= 8:
                non_num_input = {tk for tk in input_tokens if not tk.isdigit() and not _PURE_QTY_OPTION_RE.match(tk)}
                for d in recent_deals:
                    if d.base_product_name == normalized.name and is_safe_to_merge(d):
                        d_non_num = {tk for tk in get_clean_tokens(d.title) if not tk.isdigit() and not _PURE_QTY_OPTION_RE.match(tk)}
                        if len(non_num_input.intersection(d_non_num)) >= 2:
                            target_deals.append(d)

            # 3. 핵심 토큰 교차 매칭 (단순 숫자/수량 토큰이 아닌 진짜 영문+숫자 모델코드 또는 4글자 이상 영문 브랜드/모델명 일치 필수!)
            if not target_deals:
                unit_suffix_re = re.compile(r'^\d+(?:kg|g|mg|ml|l|gb|tb|mb|mah|w|v|hz|mm|cm|m|ea|p|pc|pcs|입|개|팩|봉|병|캔|미|구|포|장|매)$', re.IGNORECASE)
                core_keywords = {
                    tk for tk in input_tokens
                    if not unit_suffix_re.match(tk)
                    and (re.search(r'(?:[a-zA-Z]+[0-9]+|[0-9]+[a-zA-Z]+)[a-zA-Z0-9-]*', tk) or re.search(r'^[a-zA-Z]{4,}$', tk))
                }
                if len(core_keywords) >= 2:
                    for d in recent_deals:
                        d_tokens = get_clean_tokens(d.title)
                        overlap_core = core_keywords.intersection(d_tokens)
                        if len(overlap_core) >= 2 and len(input_tokens.intersection(d_tokens)) >= 3:
                            if is_safe_to_merge(d):
                                logger.info(f"[Token Merge Hit] 동일 핫딜 병합: '{raw_title}' ➔ '{d.title}'")
                                target_deals = [d]
                                break

            if target_deals:
                logger.info(f"[Rolling Window Clustering] 타 게시글({url})이지만 동일 상품으로 판단하여 병합: {normalized.name}")
                existing_deals = [max(target_deals, key=lambda d: d.indexed_at if d.indexed_at else datetime.min)]
        if existing_deals:
             # 이미 수집했던 글이거나, 클러스터링으로 묶인 글이라면 가격 등 메타정보 갱신 (Upsert)
             # 만약 AI가 다중 분할한 상품들이라면, 모든 split deal에 대해 종료 상태 등을 일괄 갱신합니다.
             for idx_d, existing_deal in enumerate(existing_deals):
                 # 새로 수집된 진짜 작성 시간 정보(posted_dt)가 존재하면, 기존의 잘못된 수집 시간을 진짜 시간으로 강제 갱신/보정
                 if posted_dt:
                     current_idx = existing_deal.indexed_at
                     if not current_idx or current_idx != posted_dt:
                         existing_deal.indexed_at = posted_dt

                 is_same_post_url = (existing_deal.post_link == url)
                 is_recycled_post = False

                 if is_same_post_url and existing_deal.title != raw_title:
                     def _extract_title_tokens(t: str) -> set:
                         cleaned = re.sub(r'\[[^\]]*\]|\([^\)]*\)', ' ', t or '')
                         cleaned = re.sub(r'[^a-zA-Z0-9가-힣\s]', ' ', cleaned.lower())
                         stop = {"무료", "무배", "무료배송", "특가", "할인", "쿠폰", "체감가", "역대급", "대박", "추천", "공구", "행사", "마감", "종료", "끌올"}
                         return {w for w in cleaned.split() if len(w) >= 2 and w not in stop}

                     old_tokens = _extract_title_tokens(existing_deal.title)
                     new_tokens = _extract_title_tokens(raw_title)
                     overlap = len(old_tokens.intersection(new_tokens))
                     min_tokens = min(len(old_tokens), len(new_tokens)) if (old_tokens and new_tokens) else 0
                     token_sim = (overlap / min_tokens) if min_tokens > 0 else 0.0

                     old_p = int(existing_deal.price) if str(existing_deal.price).isdigit() else 0
                     price_jump = False
                     if old_p > 0 and price > 0:
                         ratio = price / old_p
                         if ratio >= 2.5 or ratio <= 0.4:
                             price_jump = True

                     if token_sim < 0.35 or (price_jump and token_sim < 0.6) or (not old_tokens or not new_tokens):
                         is_recycled_post = True
                         logger.info(
                             f"[Recycled Post Detected!] 기존글(ID: {existing_deal.id}): '{existing_deal.title}'({old_p}원) -> "
                             f"재활용글: '{raw_title}'({price}원) (유사도: {token_sim:.2f}, 가격비율: {price/old_p if old_p else 0:.2f})"
                         )

                 if is_recycled_post:
                     import json
                     # 뽐뿌 끌올/재활용 게시글: 기존 방어 가드를 모두 우회하여 100% 최신 상태로 Full Force Sync
                     existing_deal.title = raw_title
                     if normalized.name:
                         existing_deal.base_product_name = normalized.name
                     else:
                         existing_deal.base_product_name = None

                     if price > 0:
                         existing_deal.price = str(price)

                     # 구매 링크 강제 갱신
                     new_ecom = (scraped_data.get("ecommerce_link") or "").strip()
                     if new_ecom:
                         existing_deal.ecommerce_link = new_ecom

                     # 대표 이미지 강제 갱신
                     new_img = scraped_data.get("image_url")
                     if new_img:
                         existing_deal.image_url = new_img

                     # 본문 강제 갱신 (길이 가드 무시)
                     if content_html:
                         existing_deal.content_html = content_html

                     if shop_name:
                         existing_deal.shop_name = shop_name

                     # 브랜드/모델코드 새 제목으로 재추출
                     existing_deal.brand = None
                     existing_deal.model_code = None
                     try:
                         brand_info = AIProductNameService.extract_brand_and_model(raw_title)
                         if brand_info.get("brand"):
                             existing_deal.brand = brand_info.get("brand")
                         if brand_info.get("model_code"):
                             existing_deal.model_code = brand_info.get("model_code")
                     except Exception as e:
                         logger.error(f"Error extracting brand/model for recycled deal: {e}")

                     # 카테고리/통화/종료상태 최신화
                     if final_category:
                         existing_deal.category = final_category
                     if getattr(existing_deal, 'currency', None) != currency:
                         existing_deal.currency = currency
                     existing_deal.is_closed = is_closed
                     if shipping_fee:
                         existing_deal.shipping_fee = shipping_fee

                     # 옵션 데이터: 과거 옵션 역오염 차단 및 최신화
                     new_opts_list = scraped_data.get("options_data") or []
                     if not new_opts_list and (raw_title or content_html):
                         re_extracted = extract_structured_multi_options(
                             raw_title=raw_title,
                             content_html=content_html or "",
                             default_currency=currency,
                             default_url=existing_deal.ecommerce_link or "",
                         )
                         if len(re_extracted) >= 2:
                             new_opts_list = re_extracted

                     if new_opts_list:
                         scraped_data["options_data"] = new_opts_list
                         existing_deal.options_data = json.dumps(new_opts_list, ensure_ascii=False)
                         existing_deal.has_options = True
                         existing_deal.ai_summary = format_multi_options_summary(
                             new_opts_list,
                             prefix_hot=bool(scraped_data.get("is_super_hotdeal"))
                         )
                     else:
                         scraped_data["options_data"] = []
                         existing_deal.options_data = None
                         existing_deal.has_options = False
                         existing_deal.ai_summary = None

                     # PriceHistory: 과거 이종 상품의 가격 시세 오염 방지를 위해 기존 이력 삭제 후 새 가격 기준점 등록
                     self.db.query(PriceHistory).filter(PriceHistory.deal_id == existing_deal.id).delete(synchronize_session=False)
                     if price > 0:
                         self._insert_price_history(existing_deal.id, price)

                     # 과거 split 딜이 여러 개였을 경우 첫 번째 딜 외 나머지는 닫힘 처리
                     if idx_d > 0:
                         existing_deal.is_closed = True

                     # 지표 업데이트
                     view_count = int(scraped_data.get("view_count", 0))
                     like_count = int(scraped_data.get("like_count", 0))
                     comment_count = int(scraped_data.get("comment_count", 0))
                     if view_count > (existing_deal.view_count or 0):
                         existing_deal.view_count = view_count
                     if like_count > (existing_deal.like_count or 0):
                         existing_deal.like_count = like_count
                     if comment_count > (existing_deal.comment_count or 0):
                         existing_deal.comment_count = comment_count

                     is_promoted_hotdeal = bool(
                         scraped_data.get("is_super_hotdeal")
                         or existing_deal.like_count >= 8
                         or (existing_deal.view_count or 0) >= 3500
                     )
                     calc_score = int((existing_deal.view_count / 100) + (existing_deal.like_count * 10) + (existing_deal.comment_count * 5))
                     if is_promoted_hotdeal:
                         calc_score = 100
                     existing_deal.honey_score = calc_score
                     existing_deal.is_super_hotdeal = is_promoted_hotdeal

                     continue

                 price_changed = False
                 lowest_price_dropped = False
                 
                 # 제목 갱신: 단일 상품이거나 동일 URL일 때
                 # 클러스터링(롤링 윈도우)된 경우, 새 핫딜 가격이 더 싸거나 동일 URL일 때만 원본 제목 갱신
                 old_price = int(existing_deal.price) if str(existing_deal.price).isdigit() else float('inf')
                 if is_same_post_url and existing_deal.title != raw_title:
                     existing_deal.title = raw_title
                     if normalized.name:
                         existing_deal.base_product_name = normalized.name
                 elif len(existing_deals) == 1 and existing_deal.title != raw_title and price > 0 and price < old_price:
                     existing_deal.title = raw_title # 더 싼 새로운 게시글의 제목으로 교체
                     if normalized.name:
                         existing_deal.base_product_name = normalized.name
                     if content_html:
                         existing_deal.content_html = content_html
                         
                 # 가격 갱신 및 시세 이력 기록:
                 # 1) 더 저렴한 가격이 등장했을 때 대표 최저가 갱신 + 푸시 트리거 예약
                 # 2) 타 커뮤니티 병합 시 가격이 달라도(소폭 높더라도) 시세 등락 그래프를 위해 PriceHistory에는 기록
                 if is_same_post_url and price > 0 and str(existing_deal.price) != str(price):
                     existing_deal.price = str(price)
                     price_changed = True
                 elif len(existing_deals) == 1 and price > 0:
                     if price < old_price:
                         logger.info(f"[Lowest Price Updated!] 기존 {old_price}원 -> 새 핫딜 {price}원 (URL 교체: {url})")
                         existing_deal.price = str(price)
                         
                         # 기존 링크를 백업
                         if existing_deal.source_community_id:
                             curr_merged = existing_deal.merged_communities or ""
                             merged_list = curr_merged.split(",") if curr_merged else []
                             if not any(item.startswith(f"{existing_deal.source_community_id}::") or item == str(existing_deal.source_community_id) for item in merged_list):
                                 merged_list.append(f"{existing_deal.source_community_id}::{existing_deal.post_link}")
                                 existing_deal.merged_communities = ",".join(merged_list)
                         
                         existing_deal.post_link = url # 유저가 구매하러 갈 링크도 더 싼 곳으로 교체
                         price_changed = True
                         lowest_price_dropped = True
                     elif price != old_price:
                         # 단순 다른 글 병합 시 가격이 다르다고 해서 무조건 price_changed를 켜지 않고,
                         # 해당 딜의 직전 PriceHistory와 실제 다른 가격일 때만 플래그 활성화
                         last_ph_check = self.db.query(PriceHistory).filter(
                             PriceHistory.deal_id == existing_deal.id
                         ).order_by(PriceHistory.checked_at.desc(), PriceHistory.id.desc()).first()
                         if not last_ph_check or str(last_ph_check.price) != str(price):
                             price_changed = True
                     
                 # 📸 [고화질 이미지 덮어쓰기 가드]
                 # 기존에 이미지가 없거나, 기존 이미지가 찌그러진 저화질 썸네일이고 새 이미지는 고화질 원본인 경우 강제 갱신!
                 if image_url:
                     is_low_quality = lambda u: any(x in u.lower() for x in ['_thumb', 'thumb/', 'small_', '_70x50', '_120x90', '_150x150', 'crop.webp', 'filesn', 'zboard/data/ppomppu'])
                     is_high_quality = lambda u: any(x in u.lower() for x in ['files/attach', 'ruliweb.com/img', 'quasarzone.com/editor', 'quasarzone.com/qb_saleinfo'])
                     if not existing_deal.image_url:
                         existing_deal.image_url = image_url
                     elif is_low_quality(existing_deal.image_url) and not is_low_quality(image_url):
                         existing_deal.image_url = image_url
                     elif not is_low_quality(existing_deal.image_url) and is_low_quality(image_url):
                         # 🎯 이미 고화질이 선점되어 있는데 저화질이 순회에서 들어오면 덮어쓰지 않고 절대 무시!
                         pass
                     elif is_high_quality(existing_deal.image_url) and not is_high_quality(image_url):
                          # 🎯 기존에 상세 페이지 고화질 원본이 선점되어 있는데, 뽐뿌 썸네일 등 일반 이미지가 들어오면 무시!
                          pass
                     elif existing_deal.image_url != image_url:
                         existing_deal.image_url = image_url
                    
                 new_ecom_cand = (scraped_data.get("ecommerce_link") or "").strip()
                 if new_ecom_cand and not any(cd in new_ecom_cand.lower() for cd in ("ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "quasarzone.com", "ruliweb.com", "clien.net", "bbasak.com")):
                     old_ecom_curr = (existing_deal.ecommerce_link or "").strip().lower()
                     is_old_invalid_or_search = (
                         not old_ecom_curr
                         or any(cd in old_ecom_curr for cd in ("ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "quasarzone.com", "ruliweb.com", "clien.net", "bbasak.com"))
                         or any(sk in old_ecom_curr for sk in ("search?keyword=", "search/all?query=", "total-search?kwd=", "np/search?q=", "wholesale-"))
                     )
                     if is_old_invalid_or_search:
                         existing_deal.ecommerce_link = new_ecom_cand
                 
                 if shop_name and (is_recycled_post or not existing_deal.shop_name):
                     existing_deal.shop_name = shop_name

                 if content_html and (not existing_deal.content_html or len(content_html) > len(existing_deal.content_html) + 40):
                     existing_deal.content_html = content_html
                  
                 # [가격분석] 브랜드 및 모델명 메타 정보 보완 (기존에 비어있던 경우 업데이트)
                 if not existing_deal.brand or not existing_deal.model_code:
                     try:
                         brand_info = AIProductNameService.extract_brand_and_model(existing_deal.title)
                         if brand_info.get("brand") and not existing_deal.brand:
                             existing_deal.brand = brand_info.get("brand")
                         if brand_info.get("model_code") and not existing_deal.model_code:
                             existing_deal.model_code = brand_info.get("model_code")
                     except Exception as e:
                         logger.error(f"Error extracting brand/model for existing deal during upsert: {e}")
                 
                 # 적립/카테고리 강제 보정 적용
                 if existing_deal.category != final_category and final_category:
                     existing_deal.category = final_category

                 if getattr(existing_deal, 'currency', None) != currency:
                     existing_deal.currency = currency

                 existing_deal.is_closed = is_closed

                 # [다중 커뮤니티 병합 정보 저장]
                 if existing_deal.source_community_id != community_id:
                     curr_merged = existing_deal.merged_communities or ""
                     merged_list = curr_merged.split(",") if curr_merged else []
                     # URL과 함께 저장하여 프론트엔드에서 클릭 시 이동할 수 있도록 함
                     if not any(item.startswith(f"{community_id}::") or item == str(community_id) for item in merged_list):
                         merged_list.append(f"{community_id}::{url}")
                         existing_deal.merged_communities = ",".join(merged_list)

                 # [Smart Shipping Fee Merge Guard]
                 # 새로 긁어온 배송비가 '정보 없음'이고 기존에 이미 유효한 배송 정보가 기재되어 있다면 덮어쓰지 않고 기존 값 유지!
                 if shipping_fee and shipping_fee != "정보 없음":
                     existing_deal.shipping_fee = shipping_fee
                 elif not existing_deal.shipping_fee or existing_deal.shipping_fee == "정보 없음":
                     existing_deal.shipping_fee = shipping_fee or "정보 없음"
                 
                 import json
                 new_opts_list = scraped_data.get("options_data") or []
                 if not content_html and existing_deal.content_html:
                     re_extracted = extract_structured_multi_options(
                         raw_title=existing_deal.title or raw_title,
                         content_html=existing_deal.content_html,
                         default_currency=getattr(existing_deal, "currency", None) or currency,
                         default_url=existing_deal.ecommerce_link or scraped_data.get("ecommerce_link") or "",
                     )
                     if len(re_extracted) > len(new_opts_list):
                         new_opts_list = re_extracted
                 if existing_deal.options_data:
                     try:
                         old_opts_list = json.loads(existing_deal.options_data)
                         if isinstance(old_opts_list, list) and old_opts_list:
                             old_by_name = {str(o.get("name", "")).strip().lower(): o for o in old_opts_list if isinstance(o, dict)}
                             for idx_o, no in enumerate(new_opts_list):
                                 match_old = old_by_name.get(str(no.get("name", "")).strip().lower())
                                 if not match_old and idx_o < len(old_opts_list):
                                     match_old = old_opts_list[idx_o]
                                 if isinstance(match_old, dict):
                                     for k_preserve in ("url", "code", "image_url", "sub_urls"):
                                         if not no.get(k_preserve) and match_old.get(k_preserve):
                                             no[k_preserve] = match_old[k_preserve]
                             old_url_cnt = sum(1 for o in old_opts_list if isinstance(o, dict) and o.get("url"))
                             new_url_cnt = sum(1 for o in new_opts_list if isinstance(o, dict) and o.get("url"))
                             if len(old_opts_list) > len(new_opts_list) or old_url_cnt > new_url_cnt:
                                 new_opts_list = old_opts_list
                     except Exception:
                         pass
                 if new_opts_list:
                     scraped_data["options_data"] = new_opts_list
                     existing_deal.options_data = json.dumps(new_opts_list, ensure_ascii=False)
                     existing_deal.has_options = True
                     if len(new_opts_list) >= 2:
                         multi_summary = format_multi_options_summary(
                             new_opts_list,
                             prefix_hot=bool(scraped_data.get("is_super_hotdeal"))
                         )
                         if multi_summary and (not existing_deal.ai_summary or "여러 상품이 포함된 핫딜" in existing_deal.ai_summary or existing_deal.ai_summary.strip() in ("🔥 [커뮤니티 인기]", "🔥 [커뮤니티 인증 핫딜]")):
                             existing_deal.ai_summary = multi_summary
                 
                 # 새로 수집된 날짜 정보가 존재하면 시간 정보가 더 구체적일 때만 업데이트
                 if posted_dt:
                     current_idx = existing_deal.indexed_at
                     if not current_idx:
                         existing_deal.indexed_at = posted_dt
                     elif (current_idx.hour == 0 and current_idx.minute == 0) and (posted_dt.hour != 0 or posted_dt.minute != 0):
                         existing_deal.indexed_at = posted_dt
                 
                 # 지표 업데이트 (Continuous Upsert)
                 view_count = int(scraped_data.get("view_count", 0))
                 like_count = int(scraped_data.get("like_count", 0))
                 comment_count = int(scraped_data.get("comment_count", 0))
                 
                 if view_count > (existing_deal.view_count or 0):
                     existing_deal.view_count = view_count
                 if like_count > (existing_deal.like_count or 0):
                     existing_deal.like_count = like_count
                 if comment_count > (existing_deal.comment_count or 0):
                     existing_deal.comment_count = comment_count
                 
                 # 동적 꿀딜 점수 갱신 (추천수 급증 및 포텐 승격 즉시 반영)
                 is_promoted_hotdeal = bool(
                     scraped_data.get("is_super_hotdeal")
                     or existing_deal.like_count >= 8
                     or (existing_deal.view_count or 0) >= 3500
                 )
                 calc_score = int((existing_deal.view_count / 100) + (existing_deal.like_count * 10) + (existing_deal.comment_count * 5))
                 if is_promoted_hotdeal:
                     calc_score = 100
                     if not existing_deal.ai_summary:
                         existing_deal.ai_summary = "🔥 [커뮤니티 인증 핫딜] "
                     elif "🔥" not in existing_deal.ai_summary:
                         existing_deal.ai_summary = "🔥 [커뮤니티 인증 핫딜] " + existing_deal.ai_summary
                 else:
                     calc_score = min(99, calc_score)
                     # 기존에 100점이었지만 더 이상 핫딜이 아니면 99점으로 강등 (단, 직접 지정된 경우 제외 가능하나 여기선 스크래퍼 동기화 우선)
                     
                 if is_promoted_hotdeal or existing_deal.honey_score is None:
                     existing_deal.honey_score = calc_score
                 else:
                     # 슈퍼핫딜이 아닐 때는 일반적인 갱신(기존 점수가 100이었다면 99로 하락 가능)
                     if existing_deal.honey_score == 100 and not is_promoted_hotdeal:
                         existing_deal.honey_score = calc_score
                     else:
                         existing_deal.honey_score = max(calc_score, existing_deal.honey_score)
                 
                 # 🎯 [is_super_hotdeal 필드 동기화 추가]
                 existing_deal.is_super_hotdeal = is_promoted_hotdeal
                 
                 if price_changed and price > 0:
                     self._insert_price_history(existing_deal.id, price)

                 if lowest_price_dropped and price > 0:
                     try:
                         import asyncio
                         from concurrent.futures import ThreadPoolExecutor
                         from backend.services.notification_service import NotificationService
                         if not hasattr(self.__class__, '_push_executor'):
                             self.__class__._push_executor = ThreadPoolExecutor(max_workers=5)
                         def _trigger_upsert_push(did, dtitle, dprice, dshop, dlink):
                             from backend.database.session import SessionLocal
                             s = SessionLocal()
                             try:
                                 NotificationService.process_new_deal(
                                     deal_id=did,
                                     title=dtitle,
                                     price=int(dprice),
                                     site_name=dshop or "HotDeal",
                                     deal_url=dlink,
                                     db=s
                                 )
                             except Exception as ex:
                                 logger.error(f"Upsert push dispatch error: {ex}")
                             finally:
                                 s.close()
                         asyncio.get_event_loop().run_in_executor(
                             self.__class__._push_executor,
                             _trigger_upsert_push,
                             existing_deal.id,
                             existing_deal.title,
                             price,
                             existing_deal.shop_name,
                             existing_deal.post_link
                         )
                     except Exception as push_err:
                         logger.warning(f"Upsert push schedule warning: {push_err}")

             logger.debug(f"🔄 기존 Deal 가격/상태 업데이트 (Upsert) - 총 {len(existing_deals)}개 항목: {url}")
             self.db.commit()
             
             return existing_deals[0]

        ai_summary = cached_ai_summary
        
        # 동적 꿀딜 점수 초기 계산
        view_count = int(scraped_data.get("view_count", 0))
        like_count = int(scraped_data.get("like_count", 0))
        comment_count = int(scraped_data.get("comment_count", 0))
        
        honey_score = int((view_count / 100) + (like_count * 10) + (comment_count * 5))
        
        # [DB 평균치 기반 세밀한 점수화 로직 추가]
        try:
            from sqlalchemy import func, cast, Integer
            if price > 0 and normalized.category:
                # PostgreSQL에서는 VARCHAR 컬럼에 대한 avg() 연산이 제한되므로 정수형 캐스팅 필수
                query = self.db.query(func.avg(cast(Deal.price, Integer))).filter(
                    Deal.category == normalized.category,
                    cast(Deal.price, Integer) > 0
                )
                
                avg_price = query.scalar()
                
                if avg_price:
                    avg_price = float(avg_price)
                    if price < avg_price * 0.5:
                        honey_score += 40
                    elif price < avg_price * 0.7:
                        honey_score += 25
                    elif price < avg_price * 0.9:
                        honey_score += 10
                    elif price > avg_price * 1.1:
                        honey_score -= 20
        except Exception as e:
            self.db.rollback()  # 롤백 처리하여 후속 INSERT 트랜잭션 붕괴 원천 차단
            logger.error(f"DB 평균가 계산 에러: {e}")
            
        if honey_score < 50:
            import random
            honey_score = random.randint(50, 70)  # 최소 점수 보장
        
        # 일반 딜은 최대 99점까지만 허용
        honey_score = min(99, max(0, honey_score))

        # 커뮤니티 추천수/조회수/인기마크 기반 슈퍼 핫딜 판별
        is_initial_hotdeal = bool(scraped_data.get("is_super_hotdeal") or like_count >= 8 or view_count >= 3500)
        if is_initial_hotdeal:
            # 🎯 [CEO 정책 결정]: 커뮤니티 100% 핫딜 판별 기준 충족 시 가격 감점 무시하고 무조건 100점 부여!
            honey_score = 100
            scraped_data["is_super_hotdeal"] = True
                
            if ai_summary is None:
                ai_summary = "🔥 [커뮤니티 인기] "
            elif "🔥" not in ai_summary:
                ai_summary = "🔥 [커뮤니티 인기] " + ai_summary

        # [다중 상품 자동 분할 - Phase 5.1 토큰 최적화 (CEO 피드백)]
        # 1. 제목 기반으로 상품 갯수 추정 (예: 상품명.상품명.상품명 -> 점이 2개면 상품 3개)
        estimated_item_count = raw_title.count(".") + raw_title.count(",") + 1
        # 2. 본문 텍스트 내에서 정규식으로 '원' 단위 숫자 패턴 갯수 추출
        price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+)\s*원', content_html) if content_html else []
        
        # 핫픽스: 가격에 들어간 1000단위 구분 쉼표(예: 6,590원)가 제목 쉼표 조건에 걸리는 오인식 원천 제거
        clean_title_for_multi = re.sub(r'\d,\d', '', raw_title)
        is_multi_item = ("(" in raw_title and "다양" in raw_title) or raw_title.count(".") >= 2 or "모음" in raw_title or "선택" in raw_title or "," in clean_title_for_multi or len(price_matches) >= 3
        is_missing_price = (price == 0)
        
        # [핵심 로직] 가격이 아예 없거나(0원), 다중 상품일 가능성이 높으면 AI를 가동하여 분할(Split) 및 가격 추출을 시도합니다.
        # 글로벌 캐싱으로 이미 ai_summary를 가져왔다면 불필요한 AI 가동을 스킵합니다.
        token_saving_trigger = (is_multi_item or is_missing_price) and not cached_ai_summary and not scraped_data.get("options_data")

        
        
        
        split_items = []
        from backend.core.ai_utils import get_random_gemini_key
        if token_saving_trigger and (len(content_html) > 50 or raw_title) and get_random_gemini_key():
            from backend.core.ai_utils import get_random_gemini_key
            import asyncio
            import google.generativeai as genai
            
            logger.info(f"[AI] 가격 누락 또는 모음전 감지! Gemini 1.5/2.0(최신)으로 정밀 파싱 시작... (원제: {raw_title})")
            
            prompt = f"""
            넌 쇼핑몰 핫딜 데이터 추출 AI야. 다음 게시글 내용(HTML)과 제목을 읽고, 판매 중인 상품의 이름과 가격을 정확히 뽑아서 순수 JSON 배열만 반환해.
            만약 여러 개의 상품이 포함된 벌크 핫딜이면 배열에 여러 객체를 넣고, 단일 상품이면 1개만 넣어. 다른 말은 절대 하지마.
            
            🚨 [아주 중요한 단일화 예외 규칙] 🚨
            1. 만약 게시글 본문에 각 상품별 구매 링크가 개별적으로 존재하지 않고, 오직 1개의 대표 구매 링크만 존재하는 '모음전(옵션 선택형)'이라면, 절대 상품을 여러 개로 분리하지 마! 
            무조건 전체를 대표하는 이름으로 단일 객체(1개)만 반환해. (예: "무파마 삼겹살 외 14종 모음전")
            2. [거대 모음전 방지 룰]: 본문에 개별 링크가 각각 존재하더라도, 발견된 상품의 총 개수가 30개 이상(30개 등)이면 절대로 쪼개지 마라! 이 경우에도 무조건 전체를 대표하는 1개의 객체(단일 핫딜)로 합쳐서 반환해야 한다. (쪼개는 것은 29개 이하일 때 허용)
            위 1번이나 2번에 해당하는 단일화 케이스일 경우, 포함된 세부 상품들의 대표적인 목록이나 특징을 'ai_summary' 속성에 3줄 요약으로 보기 좋게 작성해줘. (예: "🔥 [모음전] 레데리2(1.8만), 스택랜드(4천) 등 30종 할인")

            
            [가격 추출 필수 규칙]
            1. '59요금제' 같은 휴대폰 요금제의 경우 월 요금인 59000 처럼 계산해서 적어줘.
            2. 달러($)나 유로(€) 같은 외화면 대략적인 원화(KRW)로 환산해서 정수로 적어줘.
            3. 본문에 정가(원래 가격)와 할인율(예: 75% SALE)만 명시되어 있다면, 반드시 (정가 * (100 - 할인율) / 100)으로 계산해서 최종 할인된 가격을 정수로 적어. (예: 40000원 75% 할인 -> 10000)
            4. 할인율, 쿠폰가, 청구할인가 등이 명시되어 있으면 무조건 최종 할인가를 적어.
            5. 가격은 반드시 정수형 숫자만 들어가야 하고, 본문에 가격 정보가 아예 없고 완전 무료(나눔 등)이거나, 옵션별로 가격이 다양해서 알 수 없으면 0을 적어.
            6. 💡 [중요] 여러 상품이 병렬로 나열되고 괄호 안에 개별 가격이 있다면(예: 키위 (27000원)), 각 분할된 객체의 'price'에 해당 개별 금액을 정확히 추출해 매핑해.
            
            [다중 옵션 및 메타정보 매핑 규칙]
            1. [이미지 매핑]: 본문 끝 `[첨부된 이미지 링크들]`을 꼼꼼히 분석해, 분리된 각 옵션(예: 망고, 키위)과 문맥상 가장 매칭되는 이미지 URL을 찾아 각 객체의 'image_url'에 연결해. 매칭 안되면 null 처리. 🚨절대 부모(대표) 이미지를 모든 분할 객체에 똑같이 복사하지 마!
            2. [배송비]: 원문 제목 끝의 '/ 무료' 혹은 본문의 배송비 정보를 파악해, 모든 생성된 객체의 'shipping_fee' 속성에 '무료배송' 등 값을 일괄 복사해 넣어줘.
            3. 만약 텍스트 내에 (링크: http...) 형식으로 각 상품별 스토어 주소가 있다면 추출해서 'ecommerce_link' 속성에 넣어줘. 없으면 null 처리해.
            
            [양식]: [{{"name": "...", "price": 10000, "image_url": "http...", "shipping_fee": "무료배송", "ecommerce_link": "http...", "ai_summary": "옵션별 요약 내용..."}}]
            
            게시글 제목: {raw_title}
            게시글 내용: {content_html[:2000]}
            """
            
            max_retries = 2
            for attempt in range(max_retries):
                try:
                    api_key = get_random_gemini_key()
                    if not api_key:
                        logger.info("[Warning] API 키 없음. 상품 자동 분리를 건너뜁니다.")
                        break
                        
                    genai.configure(api_key=api_key)
                    model = genai.GenerativeModel('gemini-flash-latest')
                    
                    response = await asyncio.wait_for(model.generate_content_async(prompt), timeout=8.0)
                    import json
                    text_resp = response.text.replace("```json", "").replace("```", "").strip()
                    parsed = json.loads(text_resp)
                    if isinstance(parsed, list) and len(parsed) > 0:
                        split_items = parsed
                        logger.info(f"[AI Success] Gemini 자동 분할/단일화 성공! {len(split_items)}개 상품 추출됨")
                    break
                except Exception as e:
                    error_msg = str(e).lower()
                    if "403" in error_msg or "leaked" in error_msg or "api_key_invalid" in error_msg or "permission_denied" in error_msg:
                        from backend.core.ai_utils import mark_key_dead
                        mark_key_dead(api_key)
                        continue
                    if "429" in error_msg:
                        if "spending cap" in error_msg or "quota" in error_msg or "exhausted" in error_msg:
                            from backend.core.ai_utils import mark_key_dead
                            mark_key_dead(api_key)
                            continue
                        if attempt < max_retries - 1:
                            await asyncio.sleep(1.5)
                            continue
                    logger.warning(f"Gemini 분할 폴백 처리: {e}")
                    break
            else:
                logger.info("[Warning] API 키 없음. 상품 자동 분리를 건너뜁니다.")
                split_items = []
        inserted_deals = []
        deals_to_push = []
        
        if split_items:
            for idx, item in enumerate(split_items):
                # 가격 파싱 에러 방어 (LLM이 "1.1만" 같은 문자열을 반환할 경우)
                try:
                    raw_price = str(item.get("price", "0")).replace(",", "").replace("원", "").strip()
                    if "만" in raw_price:
                        num_part = raw_price.replace("만", "").strip()
                        item_price = int(float(num_part) * 10000)
                    else:
                        item_price = int(float(raw_price)) if raw_price.replace(".", "").isdigit() else 0
                except Exception:
                    item_price = 0
                    
                # 고유 식별을 위해 파생 상품명 조합 (모음전 단일화일 경우 raw_title 포맷 유지)
                if len(split_items) == 1:
                    derived_title = f"{raw_title.split(']')[0] + ']' if ']' in raw_title else ''} {item.get('name', raw_title)}"
                else:
                    derived_title = f"{raw_title.split(']')[0] + ']' if ']' in raw_title else ''} {item.get('name', '')}"
                
                item_ecommerce_link = item.get("ecommerce_link") or scraped_data.get("ecommerce_link")
                
                # ai_summary가 있으면 사용, 없으면 디폴트 문자열
                default_summary = ""
                final_ai_summary = item.get("ai_summary") or default_summary
                if honey_score >= 100 and "🔥" not in final_ai_summary:
                    final_ai_summary = "🔥 [커뮤니티 인증 핫딜] " + final_ai_summary
                
                try:
                    # 본문 내 첨부된 이미지 추출 (Gemini가 빈 문자열 반환 시 대비)
                    extracted_img = ""
                    if content_html:
                        img_matches = re.findall(r'https?://[^\s,"\'<>]+?\.(?:jpg|jpeg|png|gif|webp|bmp)', content_html, re.IGNORECASE)
                        if img_matches:
                            # 인덱스에 맞춰 이미지 매핑 시도, 없으면 첫 번째 이미지
                            extracted_img = img_matches[idx] if idx < len(img_matches) else img_matches[0]

                            
                    # 브랜드 및 모델명 메타 정보 추출
                    brand_info = {"brand": "", "model_code": ""}
                    try:
                        brand_info = AIProductNameService.extract_brand_and_model(derived_title)
                    except Exception as e:
                        logger.error(f"Error extracting brand/model for split deal: {e}")

                    new_deal = Deal(
                        source_community_id=community_id,
                        title=derived_title[:255],
                        price=str(item_price) if item_price else "0",
                        currency=currency,
                        post_link=url,
                        ecommerce_link=item_ecommerce_link or url,
                        shop_name=shop_name,
                        shipping_fee=item.get("shipping_fee") or shipping_fee,
                        is_closed=is_closed,
                        category=final_category,
                        base_product_name=item.get("name", normalized.name),
                        image_url=item.get("image_url") or extracted_img or (image_url if idx == 0 else ""), # 💡 [CEO 피드백 변형: 다중 이미지 추출 시도 후 안드로이드 처리]
                        ai_summary=final_ai_summary,
                        content_html=content_html,
                        honey_score=honey_score,
                        view_count=view_count,
                        like_count=like_count,
                        comment_count=comment_count,
                        brand=brand_info.get("brand"),
                        model_code=brand_info.get("model_code"),
                        indexed_at=posted_dt if posted_dt else func.now()
                    )
                    self.db.add(new_deal)
                    self.db.commit()
                    self.db.refresh(new_deal)
                    if item_price > 0: self._insert_price_history(new_deal.id, item_price)
                    inserted_deals.append(new_deal)
                    deals_to_push.append({
                        "id": new_deal.id,
                        "title": new_deal.title,
                        "price": new_deal.price,
                        "shop_name": new_deal.shop_name,
                        "post_link": new_deal.post_link
                    })
                except Exception as e:
                    self.db.rollback()
                    logger.error(f"Error inserting split item '{derived_title}': {e}")
        else:
            # 단일 등록 (기존 로직)
            try:
                import json
                options_data_str = None
                has_options = False
                if scraped_data.get("options_data"):
                    options_data_str = json.dumps(scraped_data.get("options_data"), ensure_ascii=False).replace("\xa0", " ")
                    has_options = True

                # 브랜드 및 모델명 메타 정보 추출
                brand_info = {"brand": "", "model_code": ""}
                try:
                    brand_info = AIProductNameService.extract_brand_and_model(raw_title)
                except Exception as e:
                    logger.error(f"Error extracting brand/model for single deal: {e}")

                # [대표님 초정밀 안전 장치]: 쪼개지지 않는 모음전이거나, 단일 등록인 모음전(is_multi_item)인 경우,
                # 아웃링크를 이상한 디코딩 주소로 억지 매핑하지 않고, 뽐뿌 상세 원본글 주소(url)로 강제 고정하여 복원성을 100% 사수한다!
                # 🎯 단, 유효한 외부 쇼핑몰 아웃링크가 확보되었다면(뽐뿌 도메인이 아닌 외부 주소) 그 외부 주소를 사수한다!
                raw_single_ecom = (scraped_data.get("ecommerce_link") or "").strip()
                if any(cd in raw_single_ecom.lower() for cd in ("ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "quasarzone.com", "ruliweb.com", "clien.net", "bbasak.com")):
                    raw_single_ecom = ""
                single_ecommerce_link = raw_single_ecom

                single_ai_summary = ai_summary
                if scraped_data.get("options_data") and len(scraped_data["options_data"]) >= 2:
                    multi_summary = format_multi_options_summary(
                        scraped_data["options_data"],
                        prefix_hot=bool(scraped_data.get("is_super_hotdeal"))
                    )
                    if multi_summary and (not single_ai_summary or single_ai_summary.strip() in ("🔥 [커뮤니티 인기]", "🔥 [커뮤니티 인증 핫딜]")):
                        single_ai_summary = multi_summary
                if not single_ai_summary and is_multi_item:
                    single_ai_summary = ""

                new_deal = Deal(
                    source_community_id=community_id,
                    title=raw_title,
                    price=str(final_price) if final_price else "0",
                    currency=currency,
                    post_link=url,
                    ecommerce_link=single_ecommerce_link,
                    shop_name=shop_name,
                    shipping_fee=shipping_fee,

                    is_closed=is_closed,
                    is_super_hotdeal=bool(scraped_data.get("is_super_hotdeal")),
                    category=final_category,
                    base_product_name=normalized.name,
                    image_url=image_url,
                    ai_summary=single_ai_summary,
                    content_html=content_html,
                    honey_score=honey_score,
                    view_count=view_count,
                    like_count=like_count,
                    comment_count=comment_count,
                    options_data=options_data_str,
                    has_options=has_options,
                    brand=brand_info.get("brand"),
                    model_code=brand_info.get("model_code"),
                    indexed_at=posted_dt if posted_dt else func.now()
                )
                self.db.add(new_deal)
                self.db.commit()
                self.db.refresh(new_deal)
                if final_price > 0: self._insert_price_history(new_deal.id, final_price)
            except Exception as e:
                self.db.rollback()
                logger.error(f"Error inserting deal '{raw_title}': {e}")
                return None
            
            inserted_deals.append(new_deal)
            deals_to_push.append({
                "id": new_deal.id,
                "title": new_deal.title,
                "price": new_deal.price,
                "shop_name": new_deal.shop_name,
                "post_link": new_deal.post_link
            })
            
        # [Epic 3] 신규 인서트된 모든 딜(AI 분할 딜 포함)에 대해 푸시 알림 비동기 격발
        if deals_to_push:
            import asyncio
            from concurrent.futures import ThreadPoolExecutor
            from backend.services.notification_service import NotificationService
            
            if not hasattr(self.__class__, '_push_executor'):
                self.__class__._push_executor = ThreadPoolExecutor(max_workers=5)
            
            def trigger_push_task(deal_id, title, price, shop_name, post_link):
                from backend.database.session import SessionLocal
                db_session = SessionLocal()
                try:
                    price_val = 0
                    try:
                        price_val = int(price) if str(price).isdigit() else 0
                    except Exception:
                        pass
                    NotificationService.process_new_deal(
                        deal_id=deal_id,
                        title=title,
                        price=price_val,
                        site_name=shop_name or "HotDeal",
                        deal_url=post_link,
                        db=db_session
                    )
                except Exception as ex:
                    logger.error(f"Push dispatch error in executor: {ex}")
                finally:
                    db_session.close()

            loop = asyncio.get_event_loop()
            for d_info in deals_to_push:
                loop.run_in_executor(
                    self.__class__._push_executor, 
                    trigger_push_task, 
                    d_info["id"], 
                    d_info["title"], 
                    d_info["price"], 
                    d_info["shop_name"], 
                    d_info["post_link"]
                )

        logger.debug(f"[Merge Complete] Deal analysis and DB merge completed: {len(inserted_deals)} inserted")
        return inserted_deals[0] if inserted_deals else None

    def _insert_price_history(self, deal_id: int, price: int):
        """특정 Deal의 가격 변동(골든타임)을 추적합니다 (동일 가격 중복 인서트 및 10분 쿨다운 가드)."""
        if price <= 0:
            return

        # 직전 최신 히스토리 조회
        last_ph = self.db.query(PriceHistory).filter(
            PriceHistory.deal_id == deal_id
        ).order_by(PriceHistory.checked_at.desc(), PriceHistory.id.desc()).first()

        # 동일한 가격이 이미 최신 레코드에 존재하면 인서트 생략!
        if last_ph and str(last_ph.price) == str(price):
            return

        # 동일 가격이 아니더라도 10분 이내의 미세 진동(< 5%)은 방어
        from datetime import datetime, timedelta
        if last_ph and last_ph.checked_at:
            last_dt = last_ph.checked_at.replace(tzinfo=None) if hasattr(last_ph.checked_at, 'replace') else last_ph.checked_at
            if (datetime.utcnow() - last_dt) < timedelta(minutes=10):
                old_p_str = re.sub(r'[^\d]', '', str(last_ph.price))
                old_p = int(old_p_str) if old_p_str.isdigit() else 0
                if old_p > 0 and abs(price - old_p) / old_p < 0.05:
                    return

        history = PriceHistory(
            deal_id=deal_id,
            price=str(price)
        )
        self.db.add(history)
        self.db.commit()
        logger.info(f"[Price History] 가격 변동 확정 기록: Deal ID {deal_id} -> {price:,}원")
