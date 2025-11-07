"""
쿠팁 파트너스 API 클라이언트 (향후 사용)
사업자 등록 후 API 키 발급받으면 활성화
"""
import logging
import os
from typing import Optional
from services.product_scraper_interface import ProductScraperInterface

class CoupangAPIClient(ProductScraperInterface):
    """쿠팁 파트너스 API 클라이언트"""
    
    def __init__(self):
        self.api_key = os.getenv('COUPANG_API_KEY')
        self.api_secret = os.getenv('COUPANG_API_SECRET')
        
        if not self.api_key or not self.api_secret:
            logging.warning("[COUPANG_API] API 키가 설정되지 않았습니다.")
    
    async def get_product_name(self, url: str) -> Optional[str]:
        """
        쿠팁 API로 상품명 추출
        TODO: 실제 API 연동 구현 필요
        """
        if not self.api_key:
            logging.error("[COUPANG_API] API 키가 없어 호출 불가")
            return None
        
        try:
            # TODO: 쿠팁 파트너스 API 호출 로직 구현
            # 1. URL에서 상품 ID 추출
            # 2. API로 상품 정보 조회
            # 3. 상품명 반환
            
            product_id = self._extract_product_id(url)
            if not product_id:
                return None
            
            logging.info(f"[COUPANG_API] 상품 ID: {product_id}")
            
            # 예시: API 호출 로직
            # response = await self._call_api(f'/products/{product_id}')
            # return response.get('productName')
            
            logging.warning("[COUPANG_API] 아직 구현되지 않았습니다.")
            return None
            
        except Exception as e:
            logging.error(f"[COUPANG_API] 에러: {e}")
            return None
    
    async def get_product_info(self, url: str) -> Optional[dict]:
        """쿠팡 API로 상세 정보 추출 (TODO)"""
        logging.warning("[COUPANG_API] get_product_info 아직 구현되지 않음")
        return None
    
    def _extract_product_id(self, url: str) -> Optional[str]:
        """
URL에서 상품 ID 추출
        예: https://www.coupang.com/vp/products/7907854298 -> 7907854298
        """
        import re
        match = re.search(r'/products/(\d+)', url)
        return match.group(1) if match else None
    
    async def _call_api(self, endpoint: str) -> dict:
        """쿠팡 API 호출 (공통 메서드)"""
        # TODO: HMAC 서명, HTTP 요청 등 구현
        pass
