import os
import logging
import google.generativeai as genai
from google.generativeai.types import GenerationConfig
import re

def is_url(text):
    return text.startswith('http://') or text.startswith('https://')

class AIProductNameService:
    @staticmethod
    def extract_name_from_html(html: str, url: str):
        """
        HTML 내용을 Gemini에게 전달하여 정확한 상품명 추출
        """
        api_key = os.getenv('GOOGLE_API_KEY')
        if not api_key:
            logging.error('[AI] GOOGLE_API_KEY 환경변수가 설정되지 않았습니다.')
            return None
        
        genai.configure(api_key=api_key)
        generation_config = GenerationConfig(
            temperature=0.0,
            max_output_tokens=100,
        )
        model = genai.GenerativeModel('gemini-2.5-flash-lite-preview-09-2025', generation_config=generation_config)
        
        # HTML에서 불필요한 스크립트/스타일 제거
        clean_html = re.sub(r'<script[^>]*>.*?</script>', '', html, flags=re.DOTALL)
        clean_html = re.sub(r'<style[^>]*>.*?</style>', '', clean_html, flags=re.DOTALL)
        
        # 처음 8000자만 전송 (Gemini 토큰 제한)
        clean_html = clean_html[:8000]
        
        prompt = f"""[쿠팡 상품 페이지 HTML 분석]

URL: {url}

HTML 내용:
{clean_html}

위 HTML에서 정확한 상품명을 추출하세요.

요구사항:
1. 브랜드명 + 모델명 + 주요 사양 포함
2. 예: "삼성전자 갤럭시버즈3 그래파이트" (O)
3. 예: "에어프라이어" (X - 너무 일반적)
4. HTML에서 prod-buy-header__title, product-title 등의 태그를 우선 참고
5. 최대 80자
6. 부가 설명 없이 상품명만 출력

상품명:
"""
        
        try:
            response = model.generate_content(prompt)
            txt = response.text.strip()
            logging.info(f"[AI_HTML] Gemini response: {txt}")
            
            # 첫 번째 줄만 추출, 최대 80자
            product_name = txt.split('\n')[0].strip()[:80]
            
            # 인용부호 제거
            product_name = product_name.strip('"\'')
            
            if product_name and len(product_name) > 3:  # 3자 이상이어야 유효
                logging.info(f"[AI_HTML] 추출 성공: {product_name}")
                return product_name
            else:
                logging.warning(f"[AI_HTML] 유효하지 않은 상품명: {txt}")
                return None
                
        except Exception as e:
            logging.error(f"[AI_HTML] Gemini 호출 오류: {e}", exc_info=True)
            return None
    
    @staticmethod
    def extract_name_from_url(url: str):
        """
        URL만으로 상품명 추출 (fallback 용도)
        """
        api_key = os.getenv('GOOGLE_API_KEY')
        if not api_key:
            logging.error('[AI] GOOGLE_API_KEY 환경변수가 설정되지 않았습니다.')
            return None
        
        genai.configure(api_key=api_key)
        generation_config = GenerationConfig(
            temperature=0.0,
            max_output_tokens=512,
        )
        model = genai.GenerativeModel('gemini-2.5-flash-lite-preview-09-2025', generation_config=generation_config)
        
        prompt = f"""다음 쿠팡 상품 URL을 분석하여 정확한 상품명을 추출하세요.

URL: {url}

요구사항:
1. 브랜드명 + 모델명 포함
2. 예: "삼성전자 갤럭시버즈3"
3. 부가 설명 없이 상품명만
4. 최대 40자

상품명:
"""
        
        try:
            response = model.generate_content(prompt)
            txt = response.text.strip()
            logging.info(f"[AI] Gemini response: {txt}")
            
            # 최대 40자, 줄바꿈, 부가설명 등 모두 제거
            product_name = txt.split('\n')[0].strip()[:40]
            product_name = product_name.strip('"\'')
            
            if product_name:
                return product_name
        except Exception as e:
            logging.error(f"[AI] Gemini 호출 오류: {e}")
        
        return None
