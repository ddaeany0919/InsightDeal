import re
import logging
from urllib.parse import urlparse

logger = logging.getLogger(__name__)

class RuleBasedParser:
    """
    AI 사용량을 줄이기 위한 규칙 기반 파서.
    정규표현식과 키워드 매칭을 통해 정보를 추출합니다.
    """

    # 카테고리 매핑 규칙
    CATEGORY_KEYWORDS = {
        "디지털/가전": ["tv", "냉장고", "세탁기", "건조기", "에어컨", "청소기", "선풍기", "모니터", "카메라", "스피커", "이어폰", "헤드폰", "오디세이"],
        "PC/하드웨어": ["노트북", "컴퓨터", "ssd", "hdd", "램", "ram", "cpu", "그래픽카드", "rtx", "gtx", "키보드", "마우스", "공유기", "ddr", "usb"],
        "모바일/상품권": ["상품권", "기프티콘", "금액권", "충전권", "데이터", "유심", "esim", "갤럭시", "아이폰", "애플워치", "워치", "아이패드", "태블릿", "s23", "s24", "zflip"],
        "의류/패션": ["티셔츠", "바지", "패딩", "자켓", "코트", "신발", "운동화", "구두", "모자", "가방", "시계", "탑텐", "지오다노", "나이키", "아디다스", "뉴발란스"],
        "음식/식품": ["햇반", "라면", "커피", "음료", "우유", "생수", "비비고", "만두", "치킨", "피자", "과자", "초콜릿", "햄버거", "스팸", "참치"],
        "생활/잡화": ["휴지", "물티슈", "세제", "샴푸", "린스", "치약", "칫솔", "마스크", "수건", "의자", "책상", "침대", "가구"],
        "패키지/이용권": ["이용권", "구독권", "입장권", "관람권", "여행", "호텔", "항공권", "유튜브", "넷플릭스", "티빙", "웨이브", "왓챠"],
        "적립/이벤트": ["적립", "이벤트", "출석", "룰렛", "응모", "체험단", "무료증정", "선착순", "퀴즈"],
        "해외핫딜": ["amazon", "ebay", "newegg", "bestbuy", "walmart", "aliexpress", "qoo10", "직구", "우주패스"],
    }

    @staticmethod
    def parse_category(title: str) -> str:
        """제목 기반 카테고리 자동 분류"""
        title_lower = title.lower()
        
        # 1. 알리익스프레스 특수 처리
        if "알리" in title_lower or "aliexpress" in title_lower:
            return "알리익스프레스"

        # 2. 키워드 매칭
        for category, keywords in RuleBasedParser.CATEGORY_KEYWORDS.items():
            if any(keyword in title_lower for keyword in keywords):
                return category
        
        return "기타"

    @staticmethod
    def extract_price(text: str) -> str:
        """
        텍스트에서 가격 정보 추출 (정규표현식)
        예: 15,000원, 15000원, 10달러, $10.99
        """
        # 1. 원화 (1,000원 또는 1000원)
        won_pattern = r'(\d{1,3}(?:,\d{3})*|\d+)원'
        match = re.search(won_pattern, text)
        if match:
            return match.group(0) # 15,000원

        # 2. 달러 ($10.99 또는 10.99달러)
        dollar_pattern_1 = r'\$(\d+(?:\.\d{1,2})?)'
        dollar_pattern_2 = r'(\d+(?:\.\d{1,2})?)달러'
        
        match = re.search(dollar_pattern_1, text)
        if match:
            return f"${match.group(1)}"
            
        match = re.search(dollar_pattern_2, text)
        if match:
            return f"${match.group(1)}"

        return "정보 없음"

    @staticmethod
    def clean_product_title(title: str) -> str:
        """상품명에서 불필요한 태그 제거"""
        # [쇼핑몰], (할인율) 등의 패턴 제거
        cleaned = re.sub(r'\[.*?\]', '', title)
        cleaned = re.sub(r'\(.*?\)', '', cleaned)
        return cleaned.strip()

    @staticmethod
    def parse_deal_info(title: str, content_html: str = "") -> dict:
        """제목과 본문에서 딜 정보 추출"""
        category = RuleBasedParser.parse_category(title)
        price = RuleBasedParser.extract_price(title)
        
        # 본문에서도 가격 검색 (제목에 없을 경우)
        if price == "정보 없음" and content_html:
             # HTML 태그 제거 후 텍스트만 추출해서 검색
            clean_content = re.sub(r'<[^>]+>', ' ', content_html)
            price = RuleBasedParser.extract_price(clean_content)

        return {
            "product_title": RuleBasedParser.clean_product_title(title) or title,
            "category": category,
            "price": price,
            "confidence": "high" if price != "정보 없음" else "low"
        }
