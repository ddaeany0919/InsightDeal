import os
import time
import hmac
import hashlib
import requests
import logging
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from datetime import datetime

# 로깅 설정
logger = logging.getLogger("coupang_partners")
logger.setLevel(logging.INFO)

router = APIRouter(
    prefix="/coupang",
    tags=["Coupang Partners API Proxy"]
)

# 🔒 금융권 수준의 크레덴셜 격리 보안 서비스
class CoupangPartnersService:
    def __init__(self):
        # 환경 변수로부터 안전하게 자격증명 로드 (하드코딩 유출 0%)
        self.access_key = os.getenv("COUPANG_ACCESS_KEY", "").strip()
        self.secret_key = os.getenv("COUPANG_SECRET_KEY", "").strip()
        self.base_url = "https://gateway.coupang.com"
        
    def is_configured(self) -> bool:
        return bool(self.access_key and self.secret_key)

    def _generate_signature(self, method: str, path: str, query_string: str = "") -> tuple[str, str]:
        """
        쿠팡 파트너스 정식 규격 CEA HMAC-SHA256 서명 생성 엔진
        """
        # UTC 타임스탬프 규격 포맷 (YYMMDDTHHMMSSZ)
        utc_now = datetime.utcnow()
        signed_date = utc_now.strftime("%y%m%dT%H%M%SZ")
        
        # 메시지 조합: {datetime}{method}{path}{query}
        message = f"{signed_date}{method}{path}"
        if query_string:
            message += f"?{query_string}"
            
        # HMAC-SHA256 암호화 서명 도출
        signature = hmac.new(
            self.secret_key.encode("utf-8"),
            message.encode("utf-8"),
            hashlib.sha256
        ).hexdigest()
        
        authorization = f"CEA algorithm=HMAC-SHA256, access-key={self.access_key}, signed-date={signed_date}, signature={signature}"
        return authorization, signed_date

    def search_products(self, keyword: str, limit: int = 10) -> dict:
        """
        쿠팡 파트너스 상품 검색 API 프록시 (서버 대행으로 크레덴셜 비공개 보장)
        """
        if not self.is_configured():
            logger.warning("Coupang Partners credentials are not configured in .env")
            return {"status": "inactive", "message": "쿠팡 파트너스 자격증명이 서버 환경변수에 설정되지 않았습니다.", "items": []}
            
        path = "/gateway/sb/v1/partners/products/search"
        method = "POST"
        
        # 서명 및 인증 헤더 생성
        authorization, signed_date = self._generate_signature(method, path)
        
        headers = {
            "Content-Type": "application/json",
            "Authorization": authorization,
            "x-requested-with": "InsightDeal-Server"
        }
        
        payload = {
            "keyword": keyword,
            "limit": limit
        }
        
        try:
            response = requests.post(
                f"{self.base_url}{path}",
                json=payload,
                headers=headers,
                timeout=5
            )
            
            if response.status_code == 200:
                data = response.json()
                return {
                    "status": "success",
                    "items": data.get("data", {}).get("productData", [])
                }
            else:
                logger.error(f"Coupang API error: {response.status_code} - {response.text}")
                raise HTTPException(status_code=response.status_code, detail=f"Coupang API response failure: {response.text}")
                
        except Exception as e:
            logger.error(f"Failed to fetch from Coupang: {e}")
            # 연동 오류 시 서비스 먹통 방지를 위해 우아한 모의 폴백 데이터 서빙 (UX 보존)
            return self._get_mock_fallback_products(keyword)

    def _get_mock_fallback_products(self, keyword: str) -> dict:
        """
        크레덴셜 미등록 혹은 API 에러 시 시뮬레이션용 프리미엄 데이터 빌더
        """
        return {
            "status": "simulation",
            "message": "쿠팡 파트너스 시뮬레이션 활성화 중 (정식 키 입력 시 즉시 연동)",
            "items": [
                {
                    "productId": 7001,
                    "productName": f"[쿠팡] {keyword} 프리미엄 스마트 패키지 역대급 최저가 패키지",
                    "productPrice": 29800,
                    "productImage": "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=500&auto=format&fit=crop&q=60",
                    "productUrl": "https://www.coupang.com",
                    "categoryName": "전자제품",
                    "discountRate": 15
                },
                {
                    "productId": 7002,
                    "productName": f"[쿠팡] {keyword} 1+1 기획 특가 패키지 [단독 무료배송]",
                    "productPrice": 14900,
                    "productImage": "https://images.unsplash.com/photo-1534422298391-e4f8c172dddb?w=500&auto=format&fit=crop&q=60",
                    "productUrl": "https://www.coupang.com",
                    "categoryName": "식품/생활",
                    "discountRate": 20
                }
            ]
        }

# 싱글톤 서비스 격발
coupang_service = CoupangPartnersService()

@router.get("/search")
def search_coupang_products(
    keyword: str = Query(..., description="검색할 상품 키워드"),
    limit: int = Query(10, description="최대 노출 개수")
):
    try:
        return coupang_service.search_products(keyword, limit)
    except Exception as e:
        logger.error(f"Router error: {e}")
        return {"status": "error", "message": str(e), "items": []}
