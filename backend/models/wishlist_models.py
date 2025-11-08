from sqlalchemy import Column, Integer, String, Boolean, DateTime
from sqlalchemy.ext.declarative import declarative_base
from datetime import datetime
from pydantic import BaseModel
from typing import Optional

Base = declarative_base()

class Wishlist(Base):
    __tablename__ = "wishlist"

    id = Column(Integer, primary_key=True, index=True)
    keyword = Column(String, index=True)
    target_price = Column(Integer)
    user_id = Column(String, index=True)
    is_active = Column(Boolean, default=True)
    alert_enabled = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

class WishlistCreate(BaseModel):
    keyword: str
    target_price: int
    user_id: Optional[str] = "default"

class WishlistUpdate(BaseModel):
    target_price: Optional[int] = None
    is_active: Optional[bool] = None
    alert_enabled: Optional[bool] = None

class WishlistResponse(BaseModel):
    id: int
    keyword: str
    target_price: int
    user_id: str
    is_active: bool
    alert_enabled: bool
    created_at: datetime
    updated_at: datetime

    class Config:
        orm_mode = True
