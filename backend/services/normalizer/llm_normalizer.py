import os
import json
import logging
import google.generativeai as genai
from .base import ProductNormalizer, NormalizedProduct
from .regex_normalizer import RegexNormalizer

logger = logging.getLogger(__name__)

class LlmNormalizer(ProductNormalizer):
    """
    Gemini API를 이용한 지능형 상품명 정규화기 (AI Scraper)
    API 키가 없거나 할당량 초과 시 자동으로 RegexNormalizer(정규식)로 Fallback 됩니다.
    """
    def __init__(self):
        self.api_key = os.getenv("GEMINI_API_KEY")
        self.fallback_normalizer = RegexNormalizer()
        self.is_active = False

        if self.api_key:
            try:
                genai.configure(api_key=self.api_key)
                self.model = genai.GenerativeModel('gemini-1.5-flash-latest')
                self.is_active = True
                logger.info("🤖 LlmNormalizer initialized with Gemini API.")
            except Exception as e:
                logger.warning(f"⚠️ Failed to init Gemini API, falling back to Regex: {e}")
        else:
            logger.info("⚠️ GEMINI_API_KEY not found. LlmNormalizer will skip and use Regex fallback.")

    async def normalize(self, title: str) -> NormalizedProduct:
        # ⚡ [하이브리드 캐싱 필터] 1단계: 정규표현식 파서 강제 선공
        # LLM 무조건 호출로 인한 딜레이(2~5분)를 1초 미만으로 단축시킵니다.
        regex_result = await self.fallback_normalizer.normalize(title)
        
        # 만약 정규식 파서가 성공적으로 카테고리를 찾았거나, 충분한 정보가 확보됐다면 AI 생략! (비용 & 시간 절약)
        if regex_result.category != "기타" and len(regex_result.name) > 3:
            logger.debug(f"⚡ [Hybrid Fast-Pass] 정규식 성능 최고도화 선방어: {regex_result.name}")
            return regex_result

        # 정규식 파싱이 불확실한 소수(기타 등)의 데이터에만 2순위로 Gemini를 투입 (Fall-Forward)
        if not self.is_active:
            return regex_result

        prompt = f"""
        당신은 핫딜 커뮤니티의 파편화된 게시글 제목을 분석하여 정확한 상품명, 브랜드, 카테고리를 추출하는 AI 파서입니다.
        아래의 제목에서 잡다한 수식어(할인율, 괄호, 특수기호, 쇼핑몰 이름)를 모두 제거하고, 
        '가장 본질적인 상품명(모델명)'과 '브랜드'를 찾아주세요.
        카테고리는 반드시 다음 중 하나만 선택하세요:
        (음식, SW/게임, PC제품, 가전제품, 생활용품, 의류, 화장품, 모바일/기프티콘, 패키지/이용권, 해외핫딜, 기타)
        
        반드시 JSON 형식으로만 반환하세요.
        Example:
        제목: "[11마존] 삼성전자 오디세이 G7 S28AG70 4K 모니터 (600,000/무료배송)"
        출력: {{"name": "오디세이 G7 S28AG70 4K 모니터", "brand": "삼성전자", "category": "PC제품"}}
        
        진짜 분석할 제목: "{title}"
        """
        try:
            response = self.model.generate_content(prompt)
            text = response.text.replace('```json', '').replace('```', '').strip()
            data = json.loads(text)
            
            # AI가 결과를 제대로 뱉지 않았을 경우 방어
            if not data.get("name"):
                 return await self.fallback_normalizer.normalize(title)

            return NormalizedProduct(
                name=data["name"][:255],
                brand=data.get("brand", "원석"),
                category=data.get("category", "기타"),
                raw_title=title
            )
        except Exception as e:
            logger.error(f"🔥 LLM Normalization failed for '{title}': {e}. Falling back to Regex.")
            return await self.fallback_normalizer.normalize(title)
