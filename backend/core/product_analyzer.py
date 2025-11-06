import aiohttp
import asyncio
import re
import json
from bs4 import BeautifulSoup
from typing import Optional
from models.product_models import ExtractedProductInfo
import structlog

logger = structlog.get_logger("product_analyzer")

class ProductLinkAnalyzer:
    def __init__(self, openai_api_key: Optional[str] = None):
        self.openai_api_key = openai_api_key
        self.session = None
        
    async def __aenter__(self):
        self.session = aiohttp.ClientSession(
            headers={
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            },
            timeout=aiohttp.ClientTimeout(total=30)
        )
        return self
        
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.session:
            await self.session.close()
            
    async def scrape_product_page(self, url: str) -> str:
        """상품 페이지 HTML 크롤링"""
        try:
            async with self.session.get(url) as response:
                if response.status == 200:
                    html = await response.text()
                    soup = BeautifulSoup(html, 'html.parser')
                    
                    # 불필요한 요소 제거
                    for tag in soup(["script", "style", "nav", "footer", "header"]):
                        tag.decompose()
                        
                    return soup.get_text(separator=' ', strip=True)[:5000]  # 5KB 제한
                else:
                    raise Exception(f"HTTP {response.status}: {url}")
        except Exception as e:
            logger.error(f"크롤링 실패: {url} - {e}")
            raise
            
    async def ai_extract_product_info(self, html_content: str, original_url: str) -> ExtractedProductInfo:
        """
AI로 상품 정보 추출"""
        if not self.openai_api_key:
            # API 키가 없으면 규칙 기반 추출
            return self._rule_based_extraction(html_content, original_url)
            
        try:
            import openai
            openai.api_key = self.openai_api_key
            
            prompt = f"""
다음 쇼핑몰 페이지에서 상품 정보를 추출해주세요.
URL: {original_url}

페이지 내용:
{html_content[:2000]}

다음 JSON 형식으로 응답해주세요:
{{
    "product_name": "정확한 상품명",
    "brand": "브랜드명",
    "model": "모델명/형번",
    "options": "용량/색상/사이즈 등",
    "search_keyword": "다른 사이트 검색용 최적화 키워드",
    "category": "카테고리",
    "original_price": 원본 가격(숫자만)
}}
예시: "삼성 갤럭시 S24 128GB 화이트" -> search_keyword: "갤럭시 S24 128GB"
            """
            
            response = await openai.ChatCompletion.acreate(
                model="gpt-3.5-turbo",
                messages=[
                    {"role": "system", "content": "당신은 쇼핑몰 상품 분석 전문가입니다. 정확하고 일관된 JSON을 반환해주세요."},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=500
            )
            
            ai_result = json.loads(response.choices[0].message.content)
            return ExtractedProductInfo(**ai_result)
            
        except Exception as e:
            logger.warning(f"AI 추출 실패, 규칙 기반으로 대체: {e}")
            return self._rule_based_extraction(html_content, original_url)
            
    def _rule_based_extraction(self, html_content: str, url: str) -> ExtractedProductInfo:
        """규칙 기반 상품 정보 추출 (백업용)"""
        lines = html_content.split('\n')[:50]
        text = ' '.join(lines).lower()
        
        # 상품명 추정 (사이트별 키워드 기반)
        product_name = "내용 분서하고 다른 사이트 검색용"
        search_keyword = "미분류 상품"
        
        if 'coupang.com' in url:
            # 쿠팡 상품명 추출 로직
            product_match = re.search(r'"상품명"\s*:\s*"([^"]+)"', text)
            if product_match:
                product_name = product_match.group(1)[:100]
                search_keyword = re.sub(r'[\[\(].*?[\]\)]', '', product_name).strip()
                
        elif 'gmarket.co.kr' in url or 'auction.co.kr' in url:
            # G마켓/옥션 로직
            title_match = re.search(r'<title>([^<]+)</title>', text, re.IGNORECASE)
            if title_match:
                product_name = title_match.group(1).split('|')[0].strip()[:100]
                search_keyword = re.sub(r'[\[\(].*?[\]\)]', '', product_name).strip()
                
        elif '11st.co.kr' in url:
            # 11번가 로직
            pass
            
        return ExtractedProductInfo(
            product_name=product_name,
            search_keyword=search_keyword,
            category="일반",
            original_price=None
        )
