from typing import Optional

STANDARD_CATEGORIES: list[dict[str, str]] = [
    {"key": "음식", "label": "식품/건강"},
    {"key": "SW/게임", "label": "SW/게임"},
    {"key": "PC용품", "label": "PC/하드웨어"},
    {"key": "가전제품", "label": "가전/TV"},
    {"key": "생활용품", "label": "생활용품"},
    {"key": "패션", "label": "의류/패션"},
    {"key": "뷰티", "label": "뷰티"},
    {"key": "모바일/기프티콘", "label": "모바일/기프티콘"},
    {"key": "상품권", "label": "상품권"},
    {"key": "적립", "label": "적립/포인트"},
    {"key": "이벤트", "label": "이벤트"},
    {"key": "육아/유아용품", "label": "육아/유아용품"},
    {"key": "해외직구", "label": "해외직구"},
    {"key": "핫딜모음", "label": "핫딜모음"},
    {"key": "기타", "label": "기타"},
]

STANDARD_SOURCES: list[str] = [
    "전체",
    "펨코",
    "뽐뿌",
    "루리웹",
    "퀘이사존",
    "클리앙",
    "알리뽐뿌",
    "빠삭국내",
    "빠삭해외",
    "빠삭육아",
]

_STANDARD_CATEGORY_KEYS: set[str] = {item["key"] for item in STANDARD_CATEGORIES}

_CATEGORY_ALIASES: dict[str, str] = {
    # 음식
    "음식": "음식",
    "식품/건강": "음식",
    "식품": "음식",
    "먹거리": "음식",
    "간식": "음식",
    "음료": "음식",
    "건강": "음식",
    # SW/게임
    "sw/게임": "SW/게임",
    "sw": "SW/게임",
    "게임": "SW/게임",
    "소프트웨어": "SW/게임",
    "콘솔": "SW/게임",
    # PC용품
    "pc용품": "PC용품",
    "pc/하드웨어": "PC용품",
    "pc": "PC용품",
    "하드웨어": "PC용품",
    "컴퓨터": "PC용품",
    "노트북": "PC용품",
    # 가전제품
    "가전제품": "가전제품",
    "가전/tv": "가전제품",
    "가전": "가전제품",
    "가전/디지털": "가전제품",
    "디지털": "가전제품",
    "tv": "가전제품",
    # 생활용품
    "생활용품": "생활용품",
    "생활": "생활용품",
    "생필품": "생활용품",
    "주방": "생활용품",
    # 패션
    "패션": "패션",
    "의류/패션": "패션",
    "의류": "패션",
    "신발": "패션",
    "잡화": "패션",
    # 뷰티
    "뷰티": "뷰티",
    "화장품": "뷰티",
    "미용": "뷰티",
    # 모바일/기프티콘
    "모바일/기프티콘": "모바일/기프티콘",
    "모바일": "모바일/기프티콘",
    "기프티콘": "모바일/기프티콘",
    "e쿠폰": "모바일/기프티콘",
    # 상품권
    "상품권": "상품권",
    "이용권": "상품권",
    # 적립
    "적립": "적립",
    "적립/포인트": "적립",
    "포인트": "적립",
    # 이벤트
    "이벤트": "이벤트",
    "체험단": "이벤트",
    # 육아/유아용품
    "육아/유아용품": "육아/유아용품",
    "육아": "육아/유아용품",
    "유아용품": "육아/유아용품",
    "유아": "육아/유아용품",
    # 해외직구 (레거시 여행.해외핫딜/해외핫딜 정규화 포함)
    "해외직구": "해외직구",
    "여행.해외핫딜": "해외직구",
    "해외핫딜": "해외직구",
    "여행/해외핫딜": "해외직구",
    "여행/해외": "해외직구",
    "직구": "해외직구",
    "해외": "해외직구",
    # 핫딜모음
    "핫딜모음": "핫딜모음",
    # 기타
    "기타": "기타",
}


def normalize_category_key(cat: Optional[str]) -> str:
    """레거시/비표준 카테고리명(예: '여행.해외핫딜', '가전', '가전/TV')을 15종 표준 카테고리 키로 정규화합니다."""
    cleaned = (cat or "").strip()
    if not cleaned:
        return "기타"
    if cleaned in _STANDARD_CATEGORY_KEYS:
        return cleaned

    lowered = cleaned.lower()
    if lowered in _CATEGORY_ALIASES:
        return _CATEGORY_ALIASES[lowered]

    if "해외핫딜" in lowered or "해외직구" in lowered or "여행.해외" in lowered:
        return "해외직구"
    if "가전" in lowered:
        return "가전제품"
    if "육아" in lowered or "유아" in lowered:
        return "육아/유아용품"
    if "식품" in lowered or "음식" in lowered or "먹거리" in lowered:
        return "음식"
    if "pc" in lowered or "하드웨어" in lowered or "컴퓨터" in lowered:
        return "PC용품"
    if "게임" in lowered or "sw" in lowered:
        return "SW/게임"
    if "패션" in lowered or "의류" in lowered:
        return "패션"
    if "뷰티" in lowered or "화장품" in lowered:
        return "뷰티"
    if "모바일" in lowered or "기프티콘" in lowered:
        return "모바일/기프티콘"
    if "상품권" in lowered:
        return "상품권"
    if "적립" in lowered or "포인트" in lowered:
        return "적립"
    if "이벤트" in lowered:
        return "이벤트"
    if "생활" in lowered or "생필품" in lowered:
        return "생활용품"
    if "핫딜모음" in lowered:
        return "핫딜모음"
    return "기타"
