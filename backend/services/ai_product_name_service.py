import os
import requests
import json
import logging

GOOGLE_API_KEY = os.getenv('GOOGLE_API_KEY')
GEMINI_ENDPOINT = 'https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent'

class AIProductNameService:
    @staticmethod
    def judge_valid_products(keyword, items):
        """
        Gemini AI를 사용하여 상품이 정상인지 비정상인지 판단
        
        Args:
            keyword: 사용자가 검색한 키워드
            items: 판단할 상품 리스트 [{'index': 1, 'price': 10000, 'title': '...', 'mall': '...'}]
            
        Returns:
            dict: {'1': '정상', '2': '비정상(낱개)', ...}
        """
        if not GOOGLE_API_KEY:
            logging.error("[AI] GOOGLE_API_KEY가 설정되지 않았습니다. 모든 상품을 정상으로 처리합니다.")
            return {str(item['index']): "정상" for item in items}
        
        prompt = f"""
사용자가 검색한 키워드: '{keyword}'

다음은 쇼핑몰 검색 결과입니다 (가격순).
각 상품이 실제 완제품(정상)인지, 낱개/부품/증정/서비스류인지(비정상) 판단해주세요.

정상 상품의 예:
- 실제 구매 가능한 정품, 세트, 신품
- 사은품이 포함된 정품 (예: "갤럭시버즈3 + 케이스")

비정상 상품의 예:
- 증정품만 단독으로 판매 (예: "폰 구매시 증정")
- 약정/번호이동 상품 (예: "번호이동시 10원")
- 낱개/부품 (예: "왼쪽 이어폰만", "배터리만")
- 서비스 (예: "인쇄 서비스", "복사 서비스")

결과는 JSON 형식으로 작성해주세요.
key는 상품 번호(문자열), value는 '정상' 또는 '비정상(사유)'로 작성하세요.

상품 목록:
"""
        
        for item in items:
            prompt += f"{item['index']}. {item['price']:,}원 - {item['title']} ({item['mall']})\n"
        
        prompt += "\nJSON 형식으로만 답변해주세요. 예: {\"1\": \"정상\", \"2\": \"비정상(낱개)\", ...}\n"
        
        payload = {
            "contents": [{"parts": [{"text": prompt}]}]
        }
        headers = {
            "Content-Type": "application/json"
        }
        
        try:
            url = f"{GEMINI_ENDPOINT}?key={GOOGLE_API_KEY}"
            logging.info(f"[AI] Gemini API 호출 중... (상품 {len(items)}개)")
            
            response = requests.post(url, headers=headers, data=json.dumps(payload), timeout=30)
            
            if response.status_code != 200:
                logging.error(f"[AI] Gemini API 오류: {response.status_code} - {response.text}")
                return {str(item['index']): "정상" for item in items}
            
            content = response.json()
            text = content['candidates'][0]['content']['parts'][0]['text']
            logging.info(f"[AI] Gemini 응답: {text[:200]}...")
            
            # JSON 부분만 추출
            start = text.find('{')
            end = text.rfind('}')
            
            if start == -1 or end == -1:
                logging.error("[AI] JSON을 찾을 수 없습니다. 모든 상품을 정상으로 처리합니다.")
                return {str(item['index']): "정상" for item in items}
            
            json_str = text[start:end+1]
            judgment = json.loads(json_str)
            
            logging.info(f"[AI] 판단 결과: {judgment}")
            return judgment
            
        except Exception as e:
            logging.error(f"[AI] 예외 발생: {str(e)}", exc_info=True)
            return {str(item['index']): "정상" for item in items}
    
    @staticmethod
    def extract_core_keyword(product_title: str) -> str:
        """
        상품명에서 핵심 키워드만 추출 (Gemini AI 활용)
        예: '삼성전자 갤럭시버즈3 프로 SM-R630 정품 국내정식 새상품' -> '갤럽시버즈3 프로'
        """
        if not GOOGLE_API_KEY:
            logging.error("[AI] GOOGLE_API_KEY가 설정되지 않았습니다.")
            return product_title[:30]  # 기본: 앞 30자
        
        prompt = f"""
다음 상품명에서 핵심 키워드만 추출해주세요.
불필요한 단어(새상품, 정품, 국내정식, SM-XXX, 색상, 용량 등)는 제거하고, 
제품명과 모델명만 간결하게 추출해주세요.

상품명: {product_title}

키워드만 텍스트로 답변하세요. JSON 형식이나 다른 설명 없이 키워드만 반환하세요.
"""
        
        payload = {
            "contents": [{"parts": [{"text": prompt}]}]
        }
        headers = {
            "Content-Type": "application/json"
        }
        
        try:
            url = f"{GEMINI_ENDPOINT}?key={GOOGLE_API_KEY}"
            response = requests.post(url, headers=headers, data=json.dumps(payload), timeout=15)
            
            if response.status_code != 200:
                logging.error(f"[AI_KEYWORD] Gemini API 오류: {response.status_code}")
                return product_title[:30]
            
            content = response.json()
            keyword = content['candidates'][0]['content']['parts'][0]['text'].strip()
            
            logging.info(f"[AI_KEYWORD] 추출 결과: '{product_title}' -> '{keyword}'")
            return keyword
            
        except Exception as e:
            logging.error(f"[AI_KEYWORD] 예외 발생: {str(e)}")
            return product_title[:30]
