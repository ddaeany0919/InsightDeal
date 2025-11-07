from fastapi import APIRouter, Depends, Query, HTTPException
from sqlalchemy.orm import Session
from database.session import get_db_session
from database.models import KeywordWishlist
from sqlalchemy import and_, desc
from models.wishlist_models import WishlistCreate, WishlistUpdate, WishlistResponse
from services.wishlist_service import WishlistService

router = APIRouter()

@router.get("/", response_model=list[WishlistResponse])
async def get_wishlist(user_id: str = Query(default="default"), active_only: bool = Query(default=True), db: Session = Depends(get_db_session)):
    return await WishlistService.get_wishlist(user_id, active_only, db)

@router.post("/", response_model=WishlistResponse)
async def create_wishlist(wishlist: WishlistCreate, db: Session = Depends(get_db_session)):
    return await WishlistService.create_wishlist(wishlist, db)

@router.delete("/{wishlist_id}")
async def delete_wishlist(wishlist_id: int, user_id: str = Query(default="default"), db: Session = Depends(get_db_session)):
    return await WishlistService.delete_wishlist(wishlist_id, user_id, db)
