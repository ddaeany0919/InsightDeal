from database.models import KeywordWishlist
from sqlalchemy.orm import Session
from sqlalchemy import and_, desc
from models.wishlist_models import WishlistCreate, WishlistUpdate, WishlistResponse

class WishlistService:
    @staticmethod
    async def get_wishlist(user_id: str, active_only: bool, db: Session):
        query = db.query(KeywordWishlist).filter(KeywordWishlist.user_id==user_id)
        if active_only:
            query = query.filter(KeywordWishlist.is_active==True)
        wishlists = query.order_by(desc(KeywordWishlist.created_at)).all()
        return [WishlistResponse.from_orm(w) for w in wishlists]
    
    @staticmethod
    async def create_wishlist(wishlist: WishlistCreate, db: Session):
        existing = db.query(KeywordWishlist).filter(and_(KeywordWishlist.user_id==wishlist.user_id, KeywordWishlist.keyword==wishlist.keyword)).first()
        if existing:
            raise Exception(f"이미 '{wishlist.keyword}' 관심상품이 등록되어 있습니다")
        db_wishlist = KeywordWishlist(
            user_id=wishlist.user_id,
            keyword=wishlist.keyword,
            target_price=wishlist.target_price
        )
        db.add(db_wishlist)
        db.commit()
        db.refresh(db_wishlist)
        return WishlistResponse.from_orm(db_wishlist)

    @staticmethod
    async def delete_wishlist(wishlist_id: int, user_id: str, db: Session):
        w = db.query(KeywordWishlist).filter(and_(KeywordWishlist.id==wishlist_id, KeywordWishlist.user_id==user_id)).first()
        if not w:
            raise Exception("관심상품을 찾을 수 없습니다")
        db.delete(w)
        db.commit()
        return {"message":"삭제되었습니다"}

    @staticmethod
    async def check_price(wishlist_id: int, user_id: str, db: Session):
        w = db.query(KeywordWishlist).filter(and_(KeywordWishlist.id==wishlist_id, KeywordWishlist.user_id==user_id)).first()
        if not w:
            raise Exception("관심상품을 찾을 수 없습니다")
        # 실제 가격 체크(크롤러/API/AI 등) 로직은 추후 구현, 여기선 단순 확인
        # 예: w.current_lowest_price 업데이트 가능
        return {"message": "가격 체크 완료", "wishlist_id": wishlist_id, "keyword": w.keyword}
