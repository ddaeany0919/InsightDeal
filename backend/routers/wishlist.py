from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import Optional
from database.session import get_db
from models.wishlist_models import WishlistCreate, WishlistUpdate, WishlistResponse, Wishlist
from services.wishlist_service import WishlistService
import logging

router = APIRouter(tags=["wishlist"])  # prefix 제거

logger = logging.getLogger(__name__)

class WishlistCreateFromURL(BaseModel):
    product_url: str
    target_price: int
    user_id: str

class WishlistCreateFromKeyword(BaseModel):
    keyword: str
    target_price: int
    user_id: str

class WishlistUpdateRequest(BaseModel):
    target_price: Optional[int] = None
    alert_enabled: Optional[bool] = None

@router.post("/from-url")
async def create_wishlist_from_url(data: WishlistCreateFromURL, db: Session = Depends(get_db)):
    """URL 기반 위시리스트 추가"""
    logger.info(f"[WISHLIST_URL] URL 등록: {data.product_url}")
    try:
        # ORM 모델 사용으로 서비스 호출 변경
        existing = db.query(Wishlist).filter(
            Wishlist.user_id == data.user_id, 
            Wishlist.keyword == data.product_url
        ).first()
        
        if existing:
            raise HTTPException(status_code=400, detail="이미 등록된 위시리스트입니다.")
        
        wishlist = Wishlist(
            keyword=data.product_url,
            target_price=data.target_price,
            user_id=data.user_id
        )
        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)
        logger.info(f"[WISHLIST_URL] 등록 성공: ID={wishlist.id}")
        return wishlist
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_URL] 오류: {str(e)}", exc_info=True)
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/from-keyword")
async def create_wishlist_from_keyword(data: WishlistCreateFromKeyword, db: Session = Depends(get_db)):
    """키워드 기반 위시리스트 추가"""
    logger.info(f"[WISHLIST_KEYWORD] 키워드 등록: {data.keyword}")
    try:
        existing = db.query(Wishlist).filter(
            Wishlist.user_id == data.user_id, 
            Wishlist.keyword == data.keyword
        ).first()
        
        if existing:
            raise HTTPException(status_code=400, detail="이미 등록된 위시리스트입니다.")
        
        wishlist = Wishlist(
            keyword=data.keyword,
            target_price=data.target_price,
            user_id=data.user_id
        )
        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)
        logger.info(f"[WISHLIST_KEYWORD] 등록 성공: ID={wishlist.id}")
        return wishlist
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_KEYWORD] 오류: {str(e)}", exc_info=True)
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/")
async def get_wishlist(user_id: str = Query(...), db: Session = Depends(get_db)):
    """사용자의 위시리스트 조회"""
    logger.info(f"[WISHLIST_GET] 조회: user_id={user_id}")
    try:
        wishlists = db.query(Wishlist).filter(Wishlist.user_id == user_id).all()
        logger.info(f"[WISHLIST_GET] 조회 성공: {len(wishlists)}개")
        return wishlists
    except Exception as e:
        logger.error(f"[WISHLIST_GET] 오류: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{wishlist_id}/check-price")
async def check_price(wishlist_id: int, user_id: str = Query(...), db: Session = Depends(get_db)):
    """위시리스트 가격 체크"""
    logger.info(f"[WISHLIST_PRICE] 가격 체크: wishlist_id={wishlist_id}, user_id={user_id}")
    try:
        result = await WishlistService.check_price(wishlist_id, user_id, db)
        logger.info(f"[WISHLIST_PRICE] 가격 체크 완료")
        return result
    except Exception as e:
        logger.error(f"[WISHLIST_PRICE] 오류: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"가격 체크 실패: {str(e)}")

@router.delete("/{wishlist_id}")
async def delete_wishlist(wishlist_id: int, user_id: str = Query(...), db: Session = Depends(get_db)):
    """위시리스트 삭제"""
    logger.info(f"[WISHLIST_DELETE] 삭제 시도: wishlist_id={wishlist_id}, user_id={user_id}")
    try:
        wishlist = db.query(Wishlist).filter(
            Wishlist.id == wishlist_id, 
            Wishlist.user_id == user_id
        ).first()
        
        if not wishlist:
            logger.warning(f"[WISHLIST_DELETE] 위시리스트 없음: ID={wishlist_id}")
            raise HTTPException(status_code=404, detail="해당 위시리스트가 없습니다.")
        
        db.delete(wishlist)
        db.commit()
        logger.info(f"[WISHLIST_DELETE] 삭제 성공: ID={wishlist_id}")
        return {"message": "삭제 성공"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_DELETE] 오류: {str(e)}", exc_info=True)
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@router.patch("/{wishlist_id}")
async def update_wishlist(
    wishlist_id: int, 
    data: WishlistUpdateRequest, 
    user_id: str = Query(...), 
    db: Session = Depends(get_db)
):
    """위시리스트 업데이트"""
    logger.info(f"[WISHLIST_UPDATE] 업데이트 시도: wishlist_id={wishlist_id}, user_id={user_id}")
    try:
        wishlist = db.query(Wishlist).filter(
            Wishlist.id == wishlist_id, 
            Wishlist.user_id == user_id
        ).first()
        
        if not wishlist:
            logger.warning(f"[WISHLIST_UPDATE] 위시리스트 없음: ID={wishlist_id}")
            raise HTTPException(status_code=404, detail="해당 위시리스트가 없습니다.")
        
        update_data = data.dict(exclude_unset=True)
        for key, value in update_data.items():
            setattr(wishlist, key, value)
        
        db.commit()
        db.refresh(wishlist)
        logger.info(f"[WISHLIST_UPDATE] 업데이트 성공: ID={wishlist_id}")
        return wishlist
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_UPDATE] 오류: {str(e)}", exc_info=True)
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
