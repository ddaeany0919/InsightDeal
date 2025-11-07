import os
import logging
import google.generativeai as genai
from google.generativeai.types import GenerationConfig

def is_url(text):
    return text.startswith('http://') or text.startswith('https://')

class AIProductNameService:
    @staticmethod
    def extract_name_from_url(url: str):
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
        prompt = f"다음 쇼핑몰 상품 링크에서 실제 상품명을 엄청 짧고 정확하게 한글로 추출해서 주세요. 부가설명 금지. 링크는 반드시 상품 페이지여야 하며, 본문이 무엇인지 추론해서 답변하세요.\n링크: {url}" 
        try:
            response = model.generate_content(prompt)
            txt = response.text.strip()
            logging.info(f"[AI] Gemini response: {txt}")
            # 최대 40자, 줄바꿈, 부가설명 등 모두 제거
            product_name = txt.split('\n')[0].strip()[:40]
            if product_name:
                return product_name
        except Exception as e:
            logging.error(f"[AI] Gemini 호출 오류: {e}")
        return None
