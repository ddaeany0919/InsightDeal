import os
import requests
import json
import logging

# Removed global GOOGLE_API_KEY
GEMINI_ENDPOINT = 'https://generativelanguage.googleapis.com/v1/models/gemini-flash-latest:generateContent'

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
        from backend.core.ai_utils import get_random_gemini_key
        api_key = get_random_gemini_key()
        if not api_key:
            logging.error("[AI] API Key가 설정되지 않았습니다. 모든 상품을 정상으로 처리합니다.")
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
        
        max_retries = 3
        for attempt in range(max_retries):
            from backend.core.ai_utils import get_random_gemini_key
            import time
            api_key = get_random_gemini_key()
            if not api_key:
                logging.error("[AI] API Key가 설정되지 않았습니다. 모든 상품을 정상으로 처리합니다.")
                return {str(item['index']): "정상" for item in items}
                
            try:
                url = f"{GEMINI_ENDPOINT}?key={api_key}"
                logging.info(f"[AI] Gemini API 호출 중... (상품 {len(items)}개, attempt {attempt+1})")
                
                response = requests.post(url, headers=headers, data=json.dumps(payload), timeout=30)
                
                if response.status_code != 200:
                    if response.status_code == 429 and attempt < max_retries - 1:
                        logging.warning("Gemini 429 Error in ProductService. Rotating key...")
                        time.sleep(1)
                        continue
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
                if "429" in str(e) and attempt < max_retries - 1:
                    time.sleep(1)
                    continue
                logging.error(f"[AI] 예외 발생: {str(e)}", exc_info=True)
                return {str(item['index']): "정상" for item in items}
                
        return {str(item['index']): "정상" for item in items}
    
    @staticmethod
    def extract_core_keyword(product_title: str) -> str:
        """
        상품명에서 핵심 키워드만 추출 (Gemini AI 활용)
        예: '삼성전자 갤럭시버즈3 프로 SM-R630 정품 국내정식 새상품' -> '갤럽시버즈3 프로'
        """
        max_retries = 3
        for attempt in range(max_retries):
            from backend.core.ai_utils import get_random_gemini_key
            import time
            api_key = get_random_gemini_key()
            if not api_key:
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
                url = f"{GEMINI_ENDPOINT}?key={api_key}"
                response = requests.post(url, headers=headers, data=json.dumps(payload), timeout=15)
                
                if response.status_code != 200:
                    if response.status_code == 429 and attempt < max_retries - 1:
                        time.sleep(1)
                        continue
                    logging.error(f"[AI_KEYWORD] Gemini API 오류: {response.status_code}")
                    return product_title[:30]
                
                content = response.json()
                keyword = content['candidates'][0]['content']['parts'][0]['text'].strip()
                
                logging.info(f"[AI_KEYWORD] 추출 결과: '{product_title}' -> '{keyword}'")
import os
import requests
import json
import logging

# Removed global GOOGLE_API_KEY
GEMINI_ENDPOINT = 'https://generativelanguage.googleapis.com/v1/models/gemini-flash-latest:generateContent'

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
        from backend.core.ai_utils import get_random_gemini_key
        api_key = get_random_gemini_key()
        if not api_key:
            logging.error("[AI] API Key가 설정되지 않았습니다. 모든 상품을 정상으로 처리합니다.")
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
        
        max_retries = 3
        for attempt in range(max_retries):
            from backend.core.ai_utils import get_random_gemini_key
            import time
            api_key = get_random_gemini_key()
            if not api_key:
                logging.error("[AI] API Key가 설정되지 않았습니다. 모든 상품을 정상으로 처리합니다.")
                return {str(item['index']): "정상" for item in items}
                
            try:
                url = f"{GEMINI_ENDPOINT}?key={api_key}"
                logging.info(f"[AI] Gemini API 호출 중... (상품 {len(items)}개, attempt {attempt+1})")
                
                response = requests.post(url, headers=headers, data=json.dumps(payload), timeout=30)
                
                if response.status_code != 200:
                    if response.status_code == 429 and attempt < max_retries - 1:
                        logging.warning("Gemini 429 Error in ProductService. Rotating key...")
                        time.sleep(1)
                        continue
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
                if "429" in str(e) and attempt < max_retries - 1:
                    time.sleep(1)
                    continue
                logging.error(f"[AI] 예외 발생: {str(e)}", exc_info=True)
                return {str(item['index']): "정상" for item in items}
                
        return {str(item['index']): "정상" for item in items}
    
    @staticmethod
    def extract_core_keyword(product_title: str) -> str:
        """
        상품명에서 핵심 키워드만 추출 (Gemini AI 활용)
        예: '삼성전자 갤럭시버즈3 프로 SM-R630 정품 국내정식 새상품' -> '갤럽시버즈3 프로'
        """
        max_retries = 3
        for attempt in range(max_retries):
            from backend.core.ai_utils import get_random_gemini_key
            import time
            api_key = get_random_gemini_key()
            if not api_key:
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
                url = f"{GEMINI_ENDPOINT}?key={api_key}"
                response = requests.post(url, headers=headers, data=json.dumps(payload), timeout=15)
                
                if response.status_code != 200:
                    if response.status_code == 429 and attempt < max_retries - 1:
                        time.sleep(1)
                        continue
                    logging.error(f"[AI_KEYWORD] Gemini API 오류: {response.status_code}")
                    return product_title[:30]
                
                content = response.json()
                keyword = content['candidates'][0]['content']['parts'][0]['text'].strip()
                
                logging.info(f"[AI_KEYWORD] 추출 결과: '{product_title}' -> '{keyword}'")
                return keyword
                
            except Exception as e:
                if "429" in str(e) and attempt < max_retries - 1:
                    time.sleep(1)
                    continue
                logging.error(f"[AI_KEYWORD] 예외 발생: {str(e)}")
                return product_title[:30]
                
        return product_title[:30]

    @staticmethod
    def extract_brand_and_model(product_title: str) -> dict:
        """
        로컬 브랜드 사전 + 모델명 정규식 및 Gemini Flash Fallback을 사용하는 하이브리드 메타 파서
        
        Args:
            product_title: 핫딜 게시글 제목
            
        Returns:
            dict: {'brand': '삼성전자', 'model_code': 'NT960XGK-K71AG'} 형태
        """
        import re
        import logging
        
        # 1. 텍스트 정규화 (대괄호 제거 및 공백 정리)
        cleaned_title = re.sub(r'\[[^\]]+\]', '', product_title) # [G마켓] 등 제거
        cleaned_title = re.sub(r'\([^\)]+\)', '', cleaned_title) # (1,590,000원) 등 제거
        cleaned_title = cleaned_title.strip()
        
        # 2. 로컬 유명 브랜드 사전 정의 및 매칭
        # 소문자로 변환하여 매칭 편의성 도모
        brand_map = {
            "삼성": "삼성전자", "samsung": "삼성전자", "lg": "LG전자", "apple": "애플", "애플": "애플",
            "sony": "소니", "소니": "소니", "asus": "ASUS", "lenovo": "레노버", "hp": "HP",
            "dell": "델", "logitech": "로지텍", "roborock": "로보락", "xiaomi": "샤오미",
            "dyson": "다이슨", "balmuda": "발뮤다", "bose": "보스", "sennheiser": "젠하이저",
            "marshall": "마샬", "nintendo": "닌텐도", "playstation": "소니", "xbox": "마이크로소프트",
            "philips": "필립스", "braun": "브라운", "cuckoo": "쿠쿠", "cuchen": "쿠첸",
            "sk매직": "SK매직", "winix": "위닉스", "nespresso": "네스프레소", "illy": "일리",
            "delonghi": "드롱기", "tefal": "테팔", "anker": "앤커", "belkin": "벨킨",
            "iptime": "아이피타임", "corsair": "커세어", "razer": "레이저", "msi": "MSI",
            "gigabyte": "기가바이트", "acer": "에이서", "crucial": "크루셜", "sandisk": "샌디스크",
            "seagate": "시게이트", "wd": "웨스턴디지털"
        }
        
        found_brand = None
        title_lower = cleaned_title.lower()
        for key, value in brand_map.items():
            # 단어 경계를 고려하거나 단순 포함 여부 검증
            if key in title_lower:
                found_brand = value
                break
                
        # 3. 로컬 모델 코드 정규식 매칭
        found_model = None
        # 전형적인 모델 코드 패턴 (알파벳 대문자/소문자, 숫자, 하이픈이 결합된 형태 또는 특정 칩셋 규격)
        model_patterns = [
            r'\b[A-Za-z0-9]{3,10}-[A-Za-z0-9]{3,10}(?:\b|[A-Za-z0-9]*)', # NT960XGK-K71AG, SM-R630, iPad-Air 등
            r'\b[A-Za-z]{1,2}\d{2,4}[A-Za-z]{0,3}\b',                     # SM-S928, SM-G998 등 기기 번호
            r'\b(?:m1|m2|m3|m4)(?:\s?pro|\s?max|\s?ultra)?\b',             # 애플 실리콘 칩셋
            r'\b(?:rtx|gtx)\s?\d{4}(?:\s?ti)?\b',                         # NVIDIA 그래픽카드
            r'\b(?:ryzen|라이젠)\s?\d(?:\s?\d{4}[a-zA-Z]*)?\b',            # AMD 라이젠 CPU
            r'\bi\d-\d{4,5}[a-zA-Z]?\b'                                   # 인텔 코어 i5-13400 등
        ]
        
        for pattern in model_patterns:
            matches = re.findall(pattern, cleaned_title, re.IGNORECASE)
            if matches:
                # 추출된 첫 번째 매칭 모델명을 대문자로 정규화하여 사용
                found_model = matches[0].strip().upper()
                break
                
        # 로컬에서 완벽하게 브랜드와 모델명을 다 잡았으면 즉시 반환 (API 호출비용 0원)
        if found_brand and found_model:
            logging.info(f"[하이브리드 파서] 로컬 파싱 적중! '{product_title}' -> brand: {found_brand}, model: {found_model}")
            return {"brand": found_brand, "model_code": found_model}
            
        # 4. 로컬 파싱 실패 또는 미진한 부분이 있을 경우에만 Gemini Flash Fallback 호출
        logging.info(f"[하이브리드 파서] 로컬 파싱 미적중. Gemini Fallback 가동... (원제: {product_title})")
        
        max_retries = 3
        for attempt in range(max_retries):
            from backend.core.ai_utils import get_random_gemini_key
            import time
            api_key = get_random_gemini_key()
            if not api_key:
                logging.error("[AI] Gemini API Key가 없어 Fallback 처리를 스킵합니다.")
                break
                
            prompt = f"""
            주어진 핫딜 게시글 제목에서 브랜드명과 고유 모델코드(혹은 핵심 품명/규격)를 정확히 추출해 주세요.
            - 불필요한 단어(할인, 사은품, 특가, 새상품, 완제품, 배송비 등)는 완전히 제거하세요.
            - 브랜드명은 한국어로 대표 브랜드명으로 정규화하세요. (예: Apple/apple -> 애플, Samsung -> 삼성전자, LG -> LG전자, Sony -> 소니)
            - 모델 코드는 영어 대문자, 숫자, 하이픈이 들어간 공식 식별 번호를 찾으세요. (예: NT960XGK-K71AG, SM-R630, SM-S928, iPad Air 6세대 등)
            - 만약 브랜드나 모델 코드를 알 수 없거나 화장품/식품처럼 고유 기기 번호가 없는 경우는 빈 문자열("")로 반환하세요.
            - 반드시 JSON 형식으로만 응답해야 하며 다른 설명은 일체 배제하세요.
            
            핫딜 제목: {product_title}
            
            양식:
            {{"brand": "...", "model_code": "..."}}
            """
            
            payload = {
                "contents": [{"parts": [{"text": prompt}]}]
            }
            headers = {
                "Content-Type": "application/json"
            }
            
            try:
                url = f"{GEMINI_ENDPOINT}?key={api_key}"
                response = requests.post(url, headers=headers, data=json.dumps(payload), timeout=15)
                
                if response.status_code != 200:
                    if response.status_code == 429 and attempt < max_retries - 1:
                        time.sleep(1)
                        continue
                    logging.error(f"[AI_METAPARSER] Gemini API 오류: {response.status_code}")
                    break
                    
                content = response.json()
                text = content['candidates'][0]['content']['parts'][0]['text'].strip()
                
                # JSON 영역만 파싱
                start = text.find('{')
                end = text.rfind('}')
                if start != -1 and end != -1:
                    json_str = text[start:end+1]
                    result = json.loads(json_str)
                    
                    # 로컬에서 찾은 게 있으면 보완
                    final_brand = result.get("brand") or found_brand or ""
                    final_model = result.get("model_code") or found_model or ""
                    
                    logging.info(f"[AI_METAPARSER] Gemini Fallback 적합 추출 완료: brand='{final_brand}', model_code='{final_model}'")
                    return {"brand": final_brand.strip(), "model_code": final_model.strip()}
                    
            except Exception as e:
                if "429" in str(e) and attempt < max_retries - 1:
                    time.sleep(1)
                    continue
                logging.error(f"[AI_METAPARSER] 예외 발생: {str(e)}")
                
        # 최종 Fallback: 로컬에서 찾은 거라도 반환
        return {"brand": found_brand or "", "model_code": found_model or ""}
