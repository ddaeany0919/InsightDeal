from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from database.session import get_db
from models.wishlist_models import WishlistCreate, WishlistUpdate, WishlistResponse, Wishlist
import logging

router = APIRouter(tags=["wishlist"])  # prefix 제거

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
    logging.info(f"[WISHLIST_URL] URL 등록: {data.product_url}")
    try:
        # ORM 모델 사용으로 서비스 호출 변경
        existing = db.query(Wishlist).filter(Wishlist.user_id == data.user_id, Wishlist.keyword == data.product_url).first()
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
        return wishlist
    except Exception as e:
        logging.error(f"[WISHLIST_URL] 오류: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/from-keyword")
async def create_wishlist_from_keyword(data: WishlistCreateFromKeyword, db: Session = Depends(get_db)):
    logging.info(f"[WISHLIST_KEYWORD] 키워드 등록: {data.keyword}")
    try:
        existing = db.query(Wishlist).filter(Wishlist.user_id == data.user_id, Wishlist.keyword == data.keyword).first()
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
        return wishlist
    except Exception as e:
        logging.error(f"[WISHLIST_KEYWORD] 오류: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/")
async def get_wishlist(user_id: str = Query(...), db: Session = Depends(get_db)):
    wishlists = db.query(Wishlist).filter(Wishlist.user_id == user_id).all()
    return wishlists

@router.post("/{wishlist_id}/check-price")
async def check_price(wishlist_id: int, user_id: str = Query(...), db: Session = Depends(get_db)):
    result = await WishlistService.check_price(wishlist_id, user_id, db)
    return result

@router.delete("/{wishlist_id}")
async def delete_wishlist(wishlist_id: int, user_id: str = Query(...), db: Session = Depends(get_db)):
    wishlist = db.query(Wishlist).filter(Wishlist.id == wishlist_id, Wishlist.user_id == user_id).first()
    if not wishlist:
        raise HTTPException(status_code=404, detail="해당 위시리스트가 없습니다.")
    db.delete(wishlist)
    db.commit()
    return {"message": "삭제 성공"}

@router.patch("/{wishlist_id}")
async def update_wishlist(wishlist_id: int, data: WishlistUpdate, user_id: str = Query(...), db: Session = Depends(get_db)):
    wishlist = db.query(Wishlist).filter(Wishlist.id == wishlist_id, Wishlist.user_id == user_id).first()
    if not wishlist:
        raise HTTPException(status_code=404, detail="해당 위시리스트가 없습니다.")
    update_data = data.dict(exclude_unset=True)
    for key, value in update_data.items():
        setattr(wishlist, key, value)
    db.commit()
    db.refresh(wishlist)
    return wishlist
