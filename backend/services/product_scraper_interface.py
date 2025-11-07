"""
상품 정보 추출 인터페이스
나중에 쿠팡 API로 쉽게 교체할 수 있도록 설계
"""
from abc import ABC, abstractmethod
from typing import Optional

class ProductScraperInterface(ABC):
    """상품 정보 추출 인터페이스"""
    
    @abstractmethod
    async def get_product_name(self, url: str) -> Optional[str]:
        """
        상품 URL에서 정확한 상품명 추출
        
        Args:
            url: 상품 URL (예: https://www.coupang.com/vp/products/123456)
            
        Returns:
            상품명 (브랜드 + 모델명 포함) 또는 None
        """
        pass
    
    @abstractmethod
    async def get_product_info(self, url: str) -> Optional[dict]:
        """
        상품 URL에서 상세 정보 추출 (확장용)
        
        Args:
            url: 상품 URL
            
        Returns:
            dict: {
                'name': str,      # 상품명
                'price': int,     # 가격
                'brand': str,     # 브랜드
                'model': str,     # 모델명
                'image_url': str  # 이미지 URL
            } 또는 None
        """
        pass
