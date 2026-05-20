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

    CATEGORIES = {
        "가전제품": ["에어컨", "냉장고", "세탁기", "청소기", "다이슨", "건조기", "선풍기", "티비", "스마트tv", "oled", "에어프라이어", "전자레인지", "제습기", "로봇청소기", "식기세척기", "드라이기", "고데기", "스타일러", "안마의자"],
        "PC제품": ["노트북", "랩탑", "맥북", "태블릿", "아이패드", "스마트폰", "휴대폰", "자급제", "아이폰", "갤럭시", "모니터", "그래픽카드", "ssd", "ram", "아이맥", "맥미니", "키보드", "마우스", "이어폰", "헤드폰", "에어팟", "버즈", "워치", "스마트워치", "애플워치", "갤럭시워치", "일체형pc", "올인원pc", "보조배터리", "충전기", "충전케이블", "액정필름"],
        "SW/게임": ["닌텐도", "스위치", "플스", "ps5", "xbox", "엑스박스", "스팀덱", "스팀월렛", "스팀게임", "윈도우", "마이크로소프트", "소프트웨어", "인디게임", "콘솔", "타이틀", "오피스", "게임"],
        "생활용품": ["휴지", "화장지", "세제", "샴푸", "물티슈", "치약", "칫솔", "치실", "건전지", "면도기", "바디워시", "비누", "생리대", "마스크", "침구", "수건", "타올", "섬유유연제", "세안제", "클렌징", "쓰레기통", "봉투", "텀블러", "머그컵", "보틀"],
        "화장품": ["스킨로션", "선크림", "수분크림", "향수", "에센스", "립스틱", "파운데이션", "화장품", "뷰티", "헤어에센스", "바디로션", "마스크팩", "토너", "앰플"],
        "의류": ["티셔츠", "바지", "패딩", "니트", "자켓", "운동화", "신발", "스니커즈", "슬리퍼", "가방", "양말", "팬티", "아우터", "맨투맨", "후드", "바람막이", "구두", "지갑", "잠옷", "파자마", "등산복", "수트", "정장", "시계", "백팩", "캐리어", "숄더백", "크로스백", "등산화", "안전화"],
        "음식": ["라면", "생수", "햇반", "소고기", "밀키트", "과일", "커피", "콜라", "사이다", "탄산", "음료수", "주스", "즙", "제로콜라", "제로사이다", "제로음료", "제로슈거", "음료", "닭가슴살", "과자", "식품", "초콜릿", "우유", "만두", "즉석밥", "비타민", "유산균", "영양제", "마그네슘", "루테인", "오메가3", "홍삼", "단백질", "프로틴", "건강기능식품", "건기식", "아이스크림", "돼지고기", "한우", "김치", "생선", "햄버거", "버거", "젤리", "볶음밥", "소시지", "소세지", "부대찌개", "갈비탕", "망고", "빵", "정관장", "고기", "치킨", "피자", "오징어", "견과류", "샐러드", "사과", "야채", "채소", "쌀", "수향미", "고구마", "감자", "블루베리"],
        "모바일/기프티콘": ["기프티콘", "스타벅스", "배민", "배달의민족", "요기요", "이디야", "맘스터치", "카카오톡 기프티콘", "카카오톡 선물하기", "카카오 모바일", "교환권", "금액권", "데이터쿠폰", "데이터이용권", "데이터선물", "상품권", "해피머니", "컬쳐랜드", "신세계", "문화상품권", "도서문화", "기프트카드", "이마트", "롯데상품권"],
        "패키지/이용권": ["넷플릭스", "유튜브", "디즈니", "티빙", "웨이브", "음원", "멜론", "이용권", "구독", "멤버십", "스마일클럽", "요금제", "항공권", "호텔", "숙박", "여행패키지", "여행상품", "숙박권", "투어", "패키지여행"],
        "해외핫딜": ["해외직구", "알리", "테무", "아마존", "큐텐", "직구", "관세", "배대지", "알리익스프레스"],
    }

    # 수집처 고유 카테고리 -> 10대 표준 카테고리 화이트리스트 매핑 테이블
    SCRAPER_CATEGORY_MAP = {
        # 뽐뿌 카테고리
        "식품건강": "음식",
        "컴퓨터": "PC제품",
        "가전가구": "가전제품",
        "의류잡화": "의류",
        "뷰티화장품": "화장품",
        "상품권쿠폰": "모바일/기프티콘",
        "육아완구": "생활용품",
        "생활주방": "생활용품",
        "디지털": "PC제품",
        "스포츠레저": "생활용품",
        "등산레저": "생활용품",
        "서적기타": "기타",
        
        # 퀘이사존 카테고리
        "PC하드웨어": "PC제품",
        "노트북모바일": "PC제품",
        "게임SW": "SW/게임",
        "가전TV": "가전제품",
        "생활식품": "음식",
        "의류패션": "의류",
        "기타세일정보": "기타",
        "상품권": "모바일/기프티콘",
        
        # 에펨코리아 카테고리
        "PC가전": "PC제품",
        "디지털가전": "가전제품",
        "식품": "음식",
        "생활용품": "생활용품",
        "화장품미용": "화장품",
        "게임": "SW/게임",
        
        # 기타 변주형태 및 신규 스크래퍼(클리앙, 루리웹, 빠삭, 네이버 쇼핑) 매핑
        "의류": "의류",
        "식품": "음식",
        "생활": "생활용품",
        "가전": "가전제품",
        "화장품": "화장품",
        "패션": "의류",
        "잡화": "의류",
        "쿠폰": "모바일/기프티콘",
        "기프티콘": "모바일/기프티콘",
        "도서": "기타",
        "완구": "생활용품",
        "여행": "패키지/이용권",
        "숙박": "패키지/이용권",
        "패키지": "패키지/이용권",
        "이용권": "패키지/이용권",
        
        # 클리앙/루리웹 대괄호 카테고리 추가 매핑
        "아마존": "해외핫딜",
        "알리익스프레스": "해외핫딜",
        "알리": "해외핫딜",
        "iOS": "PC제품",
        "PC가전": "PC제품",
        "음식": "음식",
        "모바일": "PC제품",
        "게임": "SW/게임",
        "Hobby": "기타",
        "라이프": "생활용품",
        "적립": "모바일/기프티콘",

        # 빠삭 카테고리 매핑
        "국내핫딜": "기타",  # 기타로 설정하여 제목 기반 상세 분석(Fallback)이 작동하게 함
        "해외핫딜": "해외핫딜",
        "육아핫딜": "생활용품",

        # 네이버 쇼핑 API 대분류 카테고리 매핑
        "패션의류": "의류",
        "패션잡화": "의류",
        "화장품미용": "화장품",
        "디지털가전": "PC제품",
        "가구인테리어": "생활용품",
        "생활건강": "생활용품",
        "스포츠레저": "생활용품",
        "출산육아": "생활용품",
        "여가생활편의": "패키지/이용권"
    }

    def map_scraper_category(self, scraped_category: Optional[str]) -> Optional[str]:
        """수집처 고유 카테고리를 당사 10대 정형 카테고리로 1순위 매핑"""
        if not scraped_category:
            return None
        
        # 공백 및 특수문자 제거하여 매핑 유연성 극대화
        clean_cat = re.sub(r'[^가-힣a-zA-Z0-9]', '', scraped_category).strip()
        
        # 직접 매핑 확인
        if clean_cat in self.SCRAPER_CATEGORY_MAP:
            return self.SCRAPER_CATEGORY_MAP[clean_cat]
            
        # 부분 일치로 Fallback 매핑 (더 넓은 매칭을 위함)
        for key, value in self.SCRAPER_CATEGORY_MAP.items():
            if key in clean_cat or clean_cat in key:
                return value
                
        return None


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
        
        # ⚡ [예외 필터 0] '바지락' 수산물 예외 격리 (바지 키워드 오분류 방지)
        if "바지락" in text_lower:
            return "음식"

        # ⚡ [예외 필터 0-1] '바지' 키워드 예외 격리 (아버지, 할아버지 등 서브스트링 오분류 방지)
        if "바지" in text_lower:
            if "아버지" in text_lower or "할아버지" in text_lower:
                clothes_keywords = ["청바지", "반바지", "슬랙스", "팬츠", "면바지", "트레이닝", "조거"]
                if not any(ck in text_lower for ck in clothes_keywords):
                    # 진짜 의류 힌트가 없다면, '바지' 키워드를 지워버려 의류 오분류 방지
                    text_lower = text_lower.replace("바지", " ## ")

        # ⚡ [예외 필터 0-2] '스타벅스' 키워드 조건부 분류 (기프티콘 vs 실물 커피/텀블러)
        if "스타벅스" in text_lower:
            food_starbucks = ["캡슐", "원두", "커피", "드립", "아메리카노", "라떼", "음료", "티바나", "비아"]
            goods_starbucks = ["텀블러", "머그", "컵", "플레이트", "md", "콜드컵", "보틀", "키링"]
            gift_starbucks = ["기프티콘", "쿠폰", "교환권", "금액권", "e카드", "모바일", "선물하기", "e-카드"]
            
            if any(gk in text_lower for gk in gift_starbucks):
                return "모바일/기프티콘"
            if any(fk in text_lower for fk in food_starbucks):
                return "음식"
            if any(gk in text_lower for gk in goods_starbucks):
                return "생활용품"

        # ⚡ [예외 필터 0-3] '티비' 키워드 서브스트링 예외 (멀티비타민, 액티비티, 페스티벌 가전제품 오분류 방지)
        if "티비" in text_lower:
            if "비타민" in text_lower or "액티비티" in text_lower or "페스티벌" in text_lower:
                text_lower = text_lower.replace("티비", " ## ")

        # ⚡ [예외 필터 1] '올인원' 키워드 조건부 분류 (일체형 PC vs 남성 화장품)
        if "올인원" in text_lower:
            pc_keywords = ["pc", "컴퓨터", "프로", "데스크탑", "일체형", "모니터", "삼성", "lg", "intel", "인텔", "amd", "라이젠", "hp", "레노버", "lenovo", "asus", "에이수스"]
            cosmetic_keywords = ["로션", "워시", "에센스", "크림", "클렌징", "플루이드", "스킨", "화장품", "뷰티", "썬크림", "선크림", "남성용", "옴므", "homme", "토너", "모이스처", "올리브영"]
            
            # IT 기기 힌트가 더 지배적이면 PC제품
            if any(pc in text_lower for pc in pc_keywords):
                return "PC제품"
            # 화장품 힌트가 지배적이면 화장품
            if any(cos in text_lower for cos in cosmetic_keywords):
                return "화장품"

        # ⚡ [예외 필터 1-2] '토너' 키워드 조건부 분류 (프린터/복합기 소모품 vs 화장품 토너)
        if "토너" in text_lower:
            printer_keywords = ["프린터", "복합기", "레이저", "잉크", "카트리지", "인쇄", "출력", "재생토너", "토너카트리지", "sl-", "canon", "캐논", "삼성", "hp", "브라더", "brother", "신도리코"]
            cosmetic_keywords = ["스킨", "로션", "크림", "에센스", "화장품", "뷰티", "패드", "에멀젼", "올리브영", "피부", "수분", "진정", "토너패드", "토너 패드"]
            
            if any(pk in text_lower for pk in printer_keywords):
                return "PC제품"
            if not any(ck in text_lower for ck in cosmetic_keywords):
                return "PC제품"

        # ⚡ [예외 필터 2] '스팀' 키워드 조건부 분류 (스팀 청소기/다리미 vs 스팀 게임)
        if "스팀" in text_lower:
            appliance_keywords = ["다리미", "다리비", "청소기", "에어", "식기세척기", "가습기", "쿠커", "에어프라이어", "오븐", "쿠쿠", "테팔", "필립스", "한경희", "스팀팟", "스타일러"]
            game_keywords = ["게임", "월렛", "덱", "코드", "할인", "key", "키", "스토어", "플랫폼", "steam", "충전", "머니"]
            
            if any(app in text_lower for app in appliance_keywords):
                return "가전제품"
            if any(gk in text_lower for gk in game_keywords):
                return "SW/게임"

        # ⚡ [예외 필터 3] '스위치' 키워드 조건부 분류 (네트워크/키보드 스위치 vs 닌텐도 스위치 게임 vs 조명 스위치)
        if "스위치" in text_lower or "스위칭" in text_lower:
            pc_switch_keywords = ["네트워크", "허브", "기가", "포트", "키보드", "축", "윤활", "스위치백", "공유기", "랜선", "스위칭"]
            home_switch_keywords = ["조명", "전등", "콘센트", "멀티탭", "스마트스위치", "디머", "스위치봇"]
            game_switch_keywords = ["닌텐도", "nintendo", "게임", "동물의숲", "젤다", "마리오", "타이틀", "칩", "콘솔", "스위치온", "아미보"]
            
            if any(k in text_lower for k in pc_switch_keywords):
                return "PC제품"
            if any(k in text_lower for k in home_switch_keywords):
                return "가전제품"
            if any(k in text_lower for k in game_switch_keywords):
                return "SW/게임"
            # 게임 연관 키워드가 아예 없는 단순 스위치는 PC제품 혹은 기타로 분류
            if not any(g in text_lower for g in game_switch_keywords):
                return "기타"

        # ⚡ [예외 필터 4] '시계' 키워드 조건부 분류 (스마트워치 vs 일반 패션 시계)
        if "시계" in text_lower or "워치" in text_lower:
            smart_keywords = ["스마트", "애플", "갤럭시", "가민", "핏빗", "샤오미", "미밴드", "액티브", "어메이즈핏"]
            if any(sk in text_lower for sk in smart_keywords) or "워치" in text_lower:
                return "PC제품" # 모바일 기기/웨어러블은 PC제품 카테고리에 편입

        # 5. 일반 키워드 매칭 루프
        for category, keywords in self.CATEGORIES.items():
            if any(keyword in text_lower for keyword in keywords):
                return category
        return "기타"

    async def normalize(self, title: str, scraped_category: Optional[str] = None) -> NormalizedProduct:
        """전체 정규화 파이프라인 실행 엔진 (1순위: 수집처 카테고리 매핑, 2순위: 제목 기반 정규식 Fallback)"""
        cleaned_text = self._clean_title(title)
        brand, remaining_text = self._extract_brand(cleaned_text)
        
        final_name = self._extract_model_and_rebuild(remaining_text, brand)
        
        # 이름이 너무 짧거나 파싱 실패 시 대비책
        if len(final_name) < 2:
            final_name = cleaned_text[:30]
            
        # 1순위: 수집처 자체 카테고리 우선 매핑 시도
        category = self.map_scraper_category(scraped_category)
        
        # 2순위 Fallback: 수집처 카테고리가 없거나 매핑 실패한 경우에만 기존 제목 기반 분석 구동
        if not category or category == "기타":
            category = self._map_category(cleaned_text + " " + title)

        return NormalizedProduct(
            name=final_name,
            brand=brand,
            category=category,
            raw_title=title
        )
