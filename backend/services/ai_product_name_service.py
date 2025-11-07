import os
import requests

def is_url(text):
    return text.startswith('http://') or text.startswith('https://')

class AIProductNameService:
    @staticmethod
    def extract_name_from_url(url: str):
        # Google Gemini API 기반
        api_key = os.getenv('GOOGLE_API_KEY')
        if not api_key:
            return None
        endpoint = 'https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent'
        headers = {"Content-Type":"application/json"}
        data = {
            "contents": [{
                "parts": [{
                  "text": f"다음 쇼핑몰 상품 링크에서 실제 상품명을 한국어로 40자 이내로 자연스럽게 추출해서 최대한 간결한 텍스트만 주세요. 부가설명 금지. 링크:{url}" 
                }]
            }]
        }
        params = {"key": api_key}
        try:
            response = requests.post(endpoint, headers=headers, params=params, json=data, timeout=15)
            if response.status_code==200:
                candidates = response.json().get('candidates',[])
                for c in candidates:
                    txt = c.get('content',{}).get('parts',[{}])[0].get('text','')
                    if txt.strip():
                        return txt.strip()
        except Exception:
            pass
        return None
