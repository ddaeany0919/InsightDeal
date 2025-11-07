from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from database.session import get_db
from services.wishlist_service import WishlistService
from pydantic import BaseModel
from typing import Optional
import logging

router = APIRouter(prefix="/api/wishlist", tags=["wishlist"])

class WishlistCreateFromURL(BaseModel):
    product_url: str
    target_price: int
    user_id: str

class WishlistCreateFromKeyword(BaseModel):
    keyword: str
    target_price: int
    user_id: str

class WishlistUpdate(BaseModel):
    target_price: Optional[int] = None
    alert_enabled: Optional[bool] = None

@router.post("/from-url")
async def create_wishlist_from_url(data: WishlistCreateFromURL, db: Session = Depends(get_db)):
    """
    URL로 위시리스트 등록
    1. URL에서 상품명 추출
    2. 키워드로 최저가 검색 (AI 필터링 적용)
    3. DB 저장
    """
    logging.info(f"[WISHLIST_URL] URL 등록: {data.product_url}")
    try:
        result = await WishlistService.create_from_url(
            product_url=data.product_url,
            target_price=data.target_price,
            user_id=data.user_id,
            db=db
        )
        return result
    except Exception as e:
        logging.error(f"[WISHLIST_URL] 오류: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/from-keyword")
async def create_wishlist_from_keyword(data: WishlistCreateFromKeyword, db: Session = Depends(get_db)):
    """
    키워드로 위시리스트 등록
    1. 키워드로 최저가 검색 (AI 필터링 적용)
    2. DB 저장
    """
    logging.info(f"[WISHLIST_KEYWORD] 키워드 등록: {data.keyword}")
    try:
        result = await WishlistService.create_from_keyword(
            keyword=data.keyword,
            target_price=data.target_price,
            user_id=data.user_id,
            db=db
        )
        return result
    except Exception as e:
        logging.error(f"[WISHLIST_KEYWORD] 오류: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/")
async def get_wishlist(user_id: str = Query(...), db: Session = Depends(get_db)):
    """사용자의 위시리스트 목록 조회"""
    return await WishlistService.get_user_wishlist(user_id, db)

@router.post("/{wishlist_id}/check-price")
async def check_price(wishlist_id: int, user_id: str = Query(...), db: Session = Depends(get_db)):
    """위시리스트 가격 체크 (AI 필터링 적용)"""
    result = await WishlistService.check_price(wishlist_id, user_id, db)
    return result

@router.delete("/{wishlist_id}")
async def delete_wishlist(wishlist_id: int, user_id: str = Query(...), db: Session = Depends(get_db)):
    """위시리스트 삭제"""
    await WishlistService.delete_wishlist(wishlist_id, user_id, db)
    return {"message": "삭제 성공"}

@router.patch("/{wishlist_id}")
async def update_wishlist(wishlist_id: int, data: WishlistUpdate, user_id: str = Query(...), db: Session = Depends(get_db)):
    """위시리스트 업데이트"""
    result = await WishlistService.update_wishlist(wishlist_id, user_id, data.dict(exclude_unset=True), db)
    return result
