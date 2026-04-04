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

    # 카테고리 키워드
    CATEGORIES = {
        "노트북": ["노트북", "랩탑", "맥북", "macbook", "그램", "갤북", "갤럭시북"],
        "스마트폰": ["스마트폰", "폰", "아이폰", "iphone", "갤럭시", "galaxy"],
        "음향기기": ["이어폰", "헤드폰", "에어팟", "airpods", "버즈", "buds", "스피커"],
        "영양제": ["영양제", "비타민", "오메가3", "유산균", "루테인", "밀크씨슬"],
        "의류": ["티셔츠", "바지", "패딩", "니트", "자켓"],
        "신발": ["운동화", "스니커즈", "구두", "슬리퍼"],
        "식품": ["라면", "생수", "햇반", "돼지고기", "소고기", "밀키트"]
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
