import requests
from bs4 import BeautifulSoup
import re
import logging
from services.ai_product_name_service import AIProductNameService

class URLProductExtractor:
    @staticmethod
    def extract_product_name(url: str) -> str:
        """
        네이버 쇼핑 URL에서 상품명 추출
        """
        try:
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            }
            response = requests.get(url, headers=headers, timeout=10)
            response.raise_for_status()
            
            soup = BeautifulSoup(response.text, 'html.parser')
            
            # 네이버 쇼핑 상품명 추출
            title = None
            
            # 스마트스토어
            if 'smartstore.naver.com' in url:
                title_tag = soup.find('meta', property='og:title')
                if title_tag:
                    title = title_tag.get('content')
            
            # 일반 네이버 쇼핑
            elif 'shopping.naver.com' in url:
                title_tag = soup.find('h2', class_='product_title') or \
                           soup.find('h1', class_='product_title') or \
                           soup.find('meta', property='og:title')
                if title_tag:
                    title = title_tag.get('content') if title_tag.name == 'meta' else title_tag.get_text(strip=True)
            
            if not title:
                logging.warning(f"[URL_EXTRACT] 상품명을 찾을 수 없습니다: {url}")
                return None
            
            # HTML 태그 제거
            title = re.sub(r'<[^>]+>', '', title)
            title = title.strip()
            
            logging.info(f"[URL_EXTRACT] 추출된 상품명: {title}")
            return title
            
        except Exception as e:
            logging.error(f"[URL_EXTRACT] 오류: {str(e)}", exc_info=True)
            return None
    
    @staticmethod
    def extract_keyword_from_title(title: str) -> str:
        """
        상품명에서 핵심 키워드 추출 (AI 활용)
        """
        # 기본 정제: 특수문자, 불필요 단어 제거
        keyword = re.sub(r'\[[^\]]+\]', '', title)  # [새상품] 제거
        keyword = re.sub(r'<[^>]+>', '', keyword)    # HTML 태그 제거
        keyword = re.sub(r'[\(\)\[\]\{\}]', '', keyword)  # 괄호 제거
        keyword = keyword.strip()
        
        # 너무 길면 AI로 추출
        if len(keyword) > 50:
            try:
                keyword = AIProductNameService.extract_core_keyword(keyword)
            except:
                # AI 실패 시 기본 처리
                words = keyword.split()
                keyword = ' '.join(words[:5])  # 앞 5단어만
        
        logging.info(f"[KEYWORD_EXTRACT] 추출된 키워드: {keyword}")
        return keyword
