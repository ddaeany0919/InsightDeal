from pydantic import BaseModel, validator
from typing import Optional
from datetime import datetime

class WishlistCreate(BaseModel):
    keyword: str
    target_price: int
    user_id: Optional[str] = "default"
    @validator('keyword')
    def v_kw(cls, v):
        v = v.strip()
        if not v or len(v) < 2: raise ValueError('키워드는 2글자 이상')
        if len(v) > 100: raise ValueError('키워드는 100글자 이하')
        return v
    @validator('target_price')
    def v_tp(cls, v):
        if v <= 0: raise ValueError('목표 가격은 0원보다 커야 합니다')
        if v > 100000000: raise ValueError('목표 가격은 1억원 이하')
        return v

class WishlistUpdate(BaseModel):
    target_price: Optional[int] = None
    is_active: Optional[bool] = None
    alert_enabled: Optional[bool] = None

class WishlistResponse(BaseModel):
    id: int
    keyword: str
    target_price: int
    current_lowest_price: Optional[int]
    current_lowest_platform: Optional[str]
    current_lowest_product_title: Optional[str]
    price_drop_percentage: Optional[float] = 0.0
    is_target_reached: bool = False
    is_active: bool
    alert_enabled: bool
    created_at: datetime
    updated_at: datetime
    last_checked: Optional[datetime]
    class Config:
        orm_mode = True
        from_attributes = True
