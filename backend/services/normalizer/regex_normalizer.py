import re
from typing import Optional
from backend.services.normalizer.base import ProductNormalizer, NormalizedProduct

class RegexNormalizer(ProductNormalizer):
    """🔥 정규표현식(Regex) 기반의 초고속 상품명 분석기 (Product Aggregator의 핵심)"""

    # 브랜드 매핑 딕셔너리
    BRANDS = {
        "apple": "Apple", "애플": "Apple",
        "samsung": "Samsung", "삼성": "Samsung",
        "sony": "Sony", "소니": "Sony",
        "lg": "LG", "엘지": "LG",
        "nintendo": "Nintendo", "닌텐도": "Nintendo",
        "asus": "ASUS", "에이수스": "ASUS", "아수스": "ASUS",
        "lenovo": "Lenovo", "레노버": "Lenovo",
    }

    # 카테고리 키워드 (Android UI와 동기화)
    CATEGORIES = {
        "음식": ["라면", "생수", "햇반", "소고기", "밀키트", "과일", "커피", "제로", "음료", "닭가슴살", "과자", "식품", "초콜릿", "우유", "만두", "즉석밥"],
        "SW/게임": ["닌텐도", "스위치", "플스", "ps5", "xbox", "엑스박스", "스팀", "윈도우", "마이크로소프트", "어피스", "소프트웨어", "인디게임", "콘솔", "타이틀", "오피스"],
        "PC제품": ["노트북", "랩탑", "맥북", "태블릿", "아이패드", "스마트폰", "폰", "아이폰", "갤럭시", "모니터", "그래픽카드", "ssd", "ram", "아이맥", "맥미니", "키보드", "마우스"],
        "가전제품": ["에어컨", "냉장고", "세탁기", "청소기", "다이슨", "건조기", "선풍기", "tv", "티비", "에어프라이어", "전자레인지", "제습기", "로봇청소기", "식기세척기"],
        "생활용품": ["휴지", "화장지", "세제", "샴푸", "물티슈", "치약", "건전지", "면도기", "바디워시", "비누", "생리대", "마스크", "침구", "수건"],
        "의류": ["티셔츠", "바지", "패딩", "니트", "자켓", "운동화", "신발", "스니커즈", "슬리퍼", "가방", "양말", "팬티", "아우터"],
        "화장품": ["스킨", "로션", "선크림", "수분크림", "향수", "에센스", "립스틱", "파운데이션", "화장품", "뷰티", "헤어", "바디"],
        "모바일/기프티콘": ["기프티콘", "스타벅스", "배민", "배달의민족", "요기요", "이디야", "맘스터치", "치킨", "피자", "카카오", "교환권", "금액권", "데이터"],
        "상품권": ["해피머니", "컬쳐랜드", "신세계", "문화상품권", "도서문화", "상품권", "기프트카드", "백화점상", "이마트", "롯데"],
        "패키지/이용권": ["넷플릭스", "유튜브", "프리미엄", "디즈니", "티빙", "웨이브", "음원", "멜론", "이용권", "구독", "멤버십", "스마일클럽", "요금제"],
        "여행.해외핫딜": ["항공권", "호텔", "숙박", "여행", "투어", "패키지여행", "해외직구", "알리", "테무", "아마존", "큐텐", "직구", "관세", "배대지"],
    }

    def _clean_title(self, text: str) -> str:
        """1. 불필요한 수식어 및 괄호(대문자 등) 제거"""
        # [뽐뿌], (무료배송), 【특가】 등 괄호와 안의 내용 모두 제거
        clean_text = re.sub(r'\[.*?\]|\(.*?\)|<.*?>|【.*?】|\{.*?\}', ' ', text)
        # 광고성 특수문자 제거
        clean_text = re.sub(r'[!★🔥♥♡🎯🚀⚡~]', ' ', clean_text)
        
        # 스팸 키워드 제거
        spam_words = ["특가", "무배", "무료배송", "역대급", "최저가", "품절임박", "핫딜", "카드할인"]
        for word in spam_words:
            clean_text = clean_text.replace(word, ' ')
            
        return ' '.join(clean_text.split())

    def _extract_brand(self, text: str) -> tuple[Optional[str], str]:
        """2. 브랜드 추출 및 본문에서 분리"""
        text_lower = text.lower()
        extracted_brand = None
        
        for keyword, standardized_brand in self.BRANDS.items():
            if keyword in text_lower:
                extracted_brand = standardized_brand
                break
                
        return extracted_brand, text

    def _extract_model_and_rebuild(self, text: str, brand: Optional[str]) -> str:
        """3. 핵심 모델 조합 (영문/숫자 패턴) 및 상품명 재구성"""
        # 영문 대문자 + 숫자 조합 모델명 매칭 (예: S24, M1, WH-1000XM5)
        model_pattern = re.compile(r'\b[A-Za-z0-9-]{2,}\b')
        models = model_pattern.findall(text)
        
        # 모델명이 발견되면 우선적으로 조합. 실패 시 정제된 텍스트 전체 사용
        base_name = ' '.join(models) if models else text
        if not base_name.strip():
            base_name = text
            
        # 브랜드 + 상품 조합 구조화
        if brand and brand.lower() not in base_name.lower():
             return f"{brand} {base_name}".strip()
        return base_name.strip()

    def _map_category(self, text: str) -> str:
        """4. 카테고리 매핑 (휴리스틱)"""
        text_lower = text.lower()
        for category, keywords in self.CATEGORIES.items():
            if any(keyword in text_lower for keyword in keywords):
                return category
        return "기타"

    async def normalize(self, title: str) -> NormalizedProduct:
        """전체 정규화 파이프라인 실행 엔진"""
        cleaned_text = self._clean_title(title)
        brand, remaining_text = self._extract_brand(cleaned_text)
        
        final_name = self._extract_model_and_rebuild(remaining_text, brand)
        
        # 이름이 너무 짧거나 파싱 실패 시 대비책
        if len(final_name) < 2:
            final_name = cleaned_text[:30]
            
        category = self._map_category(cleaned_text + " " + title)

        return NormalizedProduct(
            name=final_name,
            brand=brand,
            category=category,
            raw_title=title
        )
