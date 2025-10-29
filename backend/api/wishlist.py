"""
💎 InsightDeal 관심상품 API
키워드 기반 관심상품 등록, 조회, 수정, 삭제 및 가격 추적 기능
"""

import asyncio
from datetime import datetime
from typing import List, Optional
from fastapi import APIRouter, HTTPException, Depends, Query
from pydantic import BaseModel, validator
from sqlalchemy.orm import Session
from sqlalchemy import desc, and_

from database.models import KeywordWishlist, KeywordPriceHistory, KeywordAlert
from database.session import get_db

# 외부 의존성 import (메인 앱에서 가져오기)
# from scrapers.naver_shopping_scraper import NaverShoppingScraper
# from scrapers.base_scraper import PriceComparisonEngine

router = APIRouter(prefix="/api/wishlist", tags=["wishlist"])

# Pydantic 모델들
class WishlistCreate(BaseModel):
    """관심상품 등록 요청 모델"""
    keyword: str
    target_price: int
    user_id: Optional[str] = "default"
    
    @validator('keyword')
    def validate_keyword(cls, v):
        v = v.strip()
        if not v or len(v) < 2:
            raise ValueError('키워드는 2글자 이상 입력해주세요')
        if len(v) > 100:
            raise ValueError('키워드는 100글자 이하로 입력해주세요')
        return v
    
    @validator('target_price')
    def validate_target_price(cls, v):
        if v <= 0:
            raise ValueError('목표 가격은 0원보다 커야 합니다')
        if v > 100000000:  # 1억원
            raise ValueError('목표 가격은 1억원 이하로 입력해주세요')
        return v

class WishlistUpdate(BaseModel):
    """관심상품 수정 요청 모델"""
    target_price: Optional[int] = None
    is_active: Optional[bool] = None
    alert_enabled: Optional[bool] = None

class WishlistResponse(BaseModel):
    """관심상품 응답 모델"""
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
        from_attributes = True

class PriceHistoryResponse(BaseModel):
    """가격 히스토리 응답 모델"""
    recorded_at: datetime
    lowest_price: int
    platform: str
    product_title: Optional[str]
    
    class Config:
        from_attributes = True

def calculate_price_drop_percentage(current_price: Optional[int], target_price: int) -> float:
    """가격 하락 비율 계산"""
    if current_price is None or current_price >= target_price:
        return 0.0
    return round(((target_price - current_price) / target_price) * 100, 1)

@router.post("/", response_model=WishlistResponse)
async def create_wishlist(
    wishlist: WishlistCreate,
    db: Session = Depends(get_db)
):
    """
    🆕 관심상품 등록
    키워드를 바탕으로 관심상품을 등록하고 즉시 가격 검색
    """
    # 중복 체크
    existing = db.query(KeywordWishlist).filter(
        and_(
            KeywordWishlist.user_id == wishlist.user_id,
            KeywordWishlist.keyword == wishlist.keyword
        )
    ).first()
    
    if existing:
        raise HTTPException(
            status_code=400, 
            detail=f"이미 '{wishlist.keyword}' 관심상품이 등록되어 있습니다"
        )
    
    # 새로운 관심상품 생성
    db_wishlist = KeywordWishlist(
        user_id=wishlist.user_id,
        keyword=wishlist.keyword,
        target_price=wishlist.target_price
    )
    
    db.add(db_wishlist)
    db.commit()
    db.refresh(db_wishlist)
    
    # TODO: 비동기로 즉시 가격 검색 수행
    # await perform_initial_price_check(db_wishlist.id, wishlist.keyword)
    
    # 응답 데이터 생성
    price_drop_percentage = calculate_price_drop_percentage(
        db_wishlist.current_lowest_price, 
        db_wishlist.target_price
    )
    
    return WishlistResponse(
        id=db_wishlist.id,
        keyword=db_wishlist.keyword,
        target_price=db_wishlist.target_price,
        current_lowest_price=db_wishlist.current_lowest_price,
        current_lowest_platform=db_wishlist.current_lowest_platform,
        current_lowest_product_title=db_wishlist.current_lowest_product_title,
        price_drop_percentage=price_drop_percentage,
        is_target_reached=(
            db_wishlist.current_lowest_price is not None and 
            db_wishlist.current_lowest_price <= db_wishlist.target_price
        ),
        is_active=db_wishlist.is_active,
        alert_enabled=db_wishlist.alert_enabled,
        created_at=db_wishlist.created_at,
        updated_at=db_wishlist.updated_at,
        last_checked=db_wishlist.last_checked
    )

@router.get("/", response_model=List[WishlistResponse])
async def get_wishlist(
    user_id: str = Query(default="default", description="사용자 ID"),
    active_only: bool = Query(default=True, description="활성상태만 조회"),
    db: Session = Depends(get_db)
):
    """
    📝 관심상품 목록 조회
    사용자의 모든 관심상품을 최신 등록 순으로 조회
    """
    query = db.query(KeywordWishlist).filter(KeywordWishlist.user_id == user_id)
    
    if active_only:
        query = query.filter(KeywordWishlist.is_active == True)
    
    wishlists = query.order_by(desc(KeywordWishlist.created_at)).all()
    
    # 응답 데이터 생성
    response_list = []
    for wishlist in wishlists:
        price_drop_percentage = calculate_price_drop_percentage(
            wishlist.current_lowest_price, 
            wishlist.target_price
        )
        
        response_list.append(WishlistResponse(
            id=wishlist.id,
            keyword=wishlist.keyword,
            target_price=wishlist.target_price,
            current_lowest_price=wishlist.current_lowest_price,
            current_lowest_platform=wishlist.current_lowest_platform,
            current_lowest_product_title=wishlist.current_lowest_product_title,
            price_drop_percentage=price_drop_percentage,
            is_target_reached=(
                wishlist.current_lowest_price is not None and 
                wishlist.current_lowest_price <= wishlist.target_price
            ),
            is_active=wishlist.is_active,
            alert_enabled=wishlist.alert_enabled,
            created_at=wishlist.created_at,
            updated_at=wishlist.updated_at,
            last_checked=wishlist.last_checked
        ))
    
    return response_list

@router.get("/{wishlist_id}", response_model=WishlistResponse)
async def get_wishlist_item(
    wishlist_id: int,
    user_id: str = Query(default="default", description="사용자 ID"),
    db: Session = Depends(get_db)
):
    """
    🔍 관심상품 상세 조회
    """
    wishlist = db.query(KeywordWishlist).filter(
        and_(
            KeywordWishlist.id == wishlist_id,
            KeywordWishlist.user_id == user_id
        )
    ).first()
    
    if not wishlist:
        raise HTTPException(status_code=404, detail="관심상품을 찾을 수 없습니다")
    
    price_drop_percentage = calculate_price_drop_percentage(
        wishlist.current_lowest_price, 
        wishlist.target_price
    )
    
    return WishlistResponse(
        id=wishlist.id,
        keyword=wishlist.keyword,
        target_price=wishlist.target_price,
        current_lowest_price=wishlist.current_lowest_price,
        current_lowest_platform=wishlist.current_lowest_platform,
        current_lowest_product_title=wishlist.current_lowest_product_title,
        price_drop_percentage=price_drop_percentage,
        is_target_reached=(
            wishlist.current_lowest_price is not None and 
            wishlist.current_lowest_price <= wishlist.target_price
        ),
        is_active=wishlist.is_active,
        alert_enabled=wishlist.alert_enabled,
        created_at=wishlist.created_at,
        updated_at=wishlist.updated_at,
        last_checked=wishlist.last_checked
    )

@router.put("/{wishlist_id}", response_model=WishlistResponse)
async def update_wishlist(
    wishlist_id: int,
    update_data: WishlistUpdate,
    user_id: str = Query(default="default", description="사용자 ID"),
    db: Session = Depends(get_db)
):
    """
    📝 관심상품 수정
    """
    wishlist = db.query(KeywordWishlist).filter(
        and_(
            KeywordWishlist.id == wishlist_id,
            KeywordWishlist.user_id == user_id
        )
    ).first()
    
    if not wishlist:
        raise HTTPException(status_code=404, detail="관심상품을 찾을 수 없습니다")
    
    # 데이터 업데이트
    if update_data.target_price is not None:
        wishlist.target_price = update_data.target_price
    if update_data.is_active is not None:
        wishlist.is_active = update_data.is_active
    if update_data.alert_enabled is not None:
        wishlist.alert_enabled = update_data.alert_enabled
    
    wishlist.updated_at = datetime.utcnow()
    
    db.commit()
    db.refresh(wishlist)
    
    price_drop_percentage = calculate_price_drop_percentage(
        wishlist.current_lowest_price, 
        wishlist.target_price
    )
    
    return WishlistResponse(
        id=wishlist.id,
        keyword=wishlist.keyword,
        target_price=wishlist.target_price,
        current_lowest_price=wishlist.current_lowest_price,
        current_lowest_platform=wishlist.current_lowest_platform,
        current_lowest_product_title=wishlist.current_lowest_product_title,
        price_drop_percentage=price_drop_percentage,
        is_target_reached=(
            wishlist.current_lowest_price is not None and 
            wishlist.current_lowest_price <= wishlist.target_price
        ),
        is_active=wishlist.is_active,
        alert_enabled=wishlist.alert_enabled,
        created_at=wishlist.created_at,
        updated_at=wishlist.updated_at,
        last_checked=wishlist.last_checked
    )

@router.delete("/{wishlist_id}")
async def delete_wishlist(
    wishlist_id: int,
    user_id: str = Query(default="default", description="사용자 ID"),
    db: Session = Depends(get_db)
):
    """
    🗑️ 관심상품 삭제
    """
    wishlist = db.query(KeywordWishlist).filter(
        and_(
            KeywordWishlist.id == wishlist_id,
            KeywordWishlist.user_id == user_id
        )
    ).first()
    
    if not wishlist:
        raise HTTPException(status_code=404, detail="관심상품을 찾을 수 없습니다")
    
    # 상태 비활성화 (물리적 삭제 대신)
    wishlist.is_active = False
    wishlist.updated_at = datetime.utcnow()
    
    db.commit()
    
    return {"message": f"'{wishlist.keyword}' 관심상품이 삭제되었습니다"}

@router.get("/{wishlist_id}/history", response_model=List[PriceHistoryResponse])
async def get_price_history(
    wishlist_id: int,
    user_id: str = Query(default="default", description="사용자 ID"),
    days: int = Query(default=30, ge=1, le=90, description="기간 (일)"),
    db: Session = Depends(get_db)
):
    """
    📈 관심상품 가격 히스토리 조회
    차트에 사용할 가격 변화 데이터
    """
    # 관심상품 존재 확인
    wishlist = db.query(KeywordWishlist).filter(
        and_(
            KeywordWishlist.id == wishlist_id,
            KeywordWishlist.user_id == user_id
        )
    ).first()
    
    if not wishlist:
        raise HTTPException(status_code=404, detail="관심상품을 찾을 수 없습니다")
    
    # 가격 히스토리 조회
    from datetime import timedelta
    start_date = datetime.utcnow() - timedelta(days=days)
    
    history = db.query(KeywordPriceHistory).filter(
        and_(
            KeywordPriceHistory.keyword_wishlist_id == wishlist_id,
            KeywordPriceHistory.recorded_at >= start_date
        )
    ).order_by(KeywordPriceHistory.recorded_at.asc()).all()
    
    return [
        PriceHistoryResponse(
            recorded_at=record.recorded_at,
            lowest_price=record.lowest_price,
            platform=record.platform,
            product_title=record.product_title
        )
        for record in history
    ]

@router.post("/{wishlist_id}/check-price")
async def manual_price_check(
    wishlist_id: int,
    user_id: str = Query(default="default", description="사용자 ID"),
    db: Session = Depends(get_db)
):
    """
    🔄 수동 가격 체크
    사용자가 직접 가격 업데이트를 요청할 때 사용
    """
    # 관심상품 존재 확인
    wishlist = db.query(KeywordWishlist).filter(
        and_(
            KeywordWishlist.id == wishlist_id,
            KeywordWishlist.user_id == user_id,
            KeywordWishlist.is_active == True
        )
    ).first()
    
    if not wishlist:
        raise HTTPException(status_code=404, detail="활성상태의 관심상품을 찾을 수 없습니다")
    
    # TODO: 비동기로 가격 검색 수행
    # await perform_price_check(wishlist)
    
    return {
        "message": f"'{wishlist.keyword}' 가격 체크를 시작했습니다",
        "keyword": wishlist.keyword,
        "estimated_time": "1-2분"
    }

# TODO: 비동기 가격 검색 함수들 (메인 앱과 연동 필요)
"""
async def perform_initial_price_check(wishlist_id: int, keyword: str):
    # 네이버 쇼핑 API로 초기 가격 검색
    pass

async def perform_price_check(wishlist: KeywordWishlist):
    # 전체 플랫폼 검색 후 최저가 업데이트
    pass
"""