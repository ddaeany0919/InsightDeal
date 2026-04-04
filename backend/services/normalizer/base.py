from abc import ABC, abstractmethod
from typing import Optional
from pydantic import BaseModel

class NormalizedProduct(BaseModel):
    name: str # 정규화된 상품명
    brand: Optional[str] = None # 추출된 브랜드
    category: str # 추정 카테고리
    raw_title: str # 원본 제목

class ProductNormalizer(ABC):
    """
    🎯 상품명 정규화 인터페이스
    - 현재는 Regex 기반으로 동작하지만, 추후 LLM으로 쉽게 교체 가능하도록 설계
    """
    
    @abstractmethod
    async def normalize(self, title: str) -> NormalizedProduct:
        pass
