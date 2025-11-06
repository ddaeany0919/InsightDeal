from pydantic import BaseModel
from typing import Optional, Dict, List, Any
from datetime import datetime

class ProductLinkRequest(BaseModel):
    url: str
    target_price: int
    user_id: Optional[str] = "default"

class ExtractedProductInfo(BaseModel):
    product_name: str
    brand: Optional[str] = None
    model: Optional[str] = None
    options: Optional[str] = None
    search_keyword: str  # 다른 사이트 검색용 최적화된 키워드
    category: Optional[str] = None
    original_price: Optional[int] = None
    image_url: Optional[str] = None

class PlatformPriceInfo(BaseModel):
    platform: str
    price: Optional[int]
    product_title: Optional[str]
    product_url: Optional[str]
    shipping_fee: Optional[int] = 0
    availability: str = "재고 있음"
    seller_rating: Optional[float] = None
    
class ProductAnalysisResponse(BaseModel):
    trace_id: str
    original_url: str
    extracted_info: ExtractedProductInfo
    price_comparison: Dict[str, PlatformPriceInfo]
    lowest_platform: Optional[str]
    lowest_total_price: Optional[int]  # 상품가 + 배송비
    max_saving: int
    analysis_time_ms: int
    created_wishlist_id: Optional[int] = None
    
class LinkBasedWishlistCreate(BaseModel):
    url: str
    target_price: int
    user_id: Optional[str] = "default"
    auto_track: bool = True  # 자동 가격 추적 여부
