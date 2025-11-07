from pydantic import BaseModel
from typing import Dict, Any, Optional, List

class ProductLinkRequest(BaseModel):
    url: str
    user_id: Optional[str] = "default"
    target_price: Optional[int] = None

class ProductAnalysisResponse(BaseModel):
    trace_id: str
    original_url: str
    extracted_info: Dict[str, Any]
    price_comparison: List[Dict[str, Any]]
    lowest_platform: Optional[str]
    lowest_total_price: Optional[int]
