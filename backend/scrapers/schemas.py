from pydantic import BaseModel, HttpUrl, Field, validator
from typing import Optional, Any
from datetime import datetime

class ScrapedDeal(BaseModel):
    title: str = Field(..., description="핫딜 게시글 제목")
    url: str = Field(..., description="핫딜 게시글 링크")
    ecommerce_link: Optional[str] = Field(None, description="원본 쇼핑몰 링크")
    
    price: str = Field("0", description="파싱된 가격 문자열 (원화 등)")
    original_price: Optional[str] = Field(None, description="할인 전 원래 가격")
    shipping_fee: Optional[str] = Field(None, description="배송비 문자열")
    
    category: Optional[str] = Field(None, description="카테고리 (e.g. PC, 의류, 이벤트)")
    mall_name: Optional[str] = Field(None, description="쇼핑몰 이름 (e.g. 옥션, 11번가)")
    image_url: Optional[str] = Field(None, description="썸네일 이미지 URL")
    
    is_closed: bool = Field(False, description="종료/품절 여부")
    
    view_count: int = Field(0, description="조회수")
    like_count: int = Field(0, description="추천수")
    comment_count: int = Field(0, description="댓글수")
    
    posted_at: Optional[str] = Field(None, description="작성 시간 (ISO 8601 형식 문자열)")
    
    content_html: Optional[str] = Field(None, description="상세 본문 HTML 내용")

    @validator('title', 'url')
    @classmethod
    def must_not_be_empty(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("Must not be empty")
        return v.strip()
