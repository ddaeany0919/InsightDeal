from sqlalchemy.orm import Session
from models.wishlist_models import WishlistCreate, WishlistUpdate, WishlistResponse  # 변경된 import
from services.price_comparison_service import PriceComparisonService
from services.url_product_extractor import URLProductExtractor
from datetime import datetime
import logging

class WishlistService:
    @staticmethod
    async def create_from_url(product_url: str, target_price: int, user_id: str, db: Session):
        """
        URL로 위시리스트 등록
        1. URL에서 상품명 추출
        2. 키워드로 최저가 검색
        3. DB 저장
        """
        # 1. URL에서 상품명 추출
        product_title = URLProductExtractor.extract_product_name(product_url)
        if not product_title:
            raise Exception("상품명을 추출할 수 없습니다")
        
        # 2. 키워드 추출
        keyword = URLProductExtractor.extract_keyword_from_title(product_title)
        
        # 3. 기존 등록 여부 확인
        existing = db.query(WishlistCreate).filter(
            WishlistCreate.user_id == user_id,
            WishlistCreate.keyword == keyword
        ).first()
        
        if existing:
            raise Exception(f"이미 '{keyword}' 관심상품이 등록되어 있습니다")
        
        # 4. 최저가 검색 (AI 필터링)
        price_result = await PriceComparisonService.search_lowest_price(keyword)
        
        # 5. DB 저장
        wishlist = WishlistCreate(
            keyword=keyword,
            target_price=target_price,
            user_id=user_id,
            # 아래 필드는 Response에서나 사용, DB에는 별도 필드 필요할 수 있음
            # current_lowest_price=price_result['lowest_price'] if price_result else None,
            # current_lowest_platform=price_result['mall'] if price_result else None,
            # current_lowest_product_title=price_result['product_title'] if price_result else None
        )
        
        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)
        
        logging.info(f"[WISHLIST] URL로 등록 성공: {keyword}")
        return wishlist
    
    @staticmethod
    async def create_from_keyword(keyword: str, target_price: int, user_id: str, db: Session):
        """
        키워드로 위시리스트 등록
        """
        # 1. 기존 등록 여부 확인
        existing = db.query(WishlistCreate).filter(
            WishlistCreate.user_id == user_id,
            WishlistCreate.keyword == keyword
        ).first()
        
        if existing:
            raise Exception(f"이미 '{keyword}' 관심상품이 등록되어 있습니다")
        
        # 2. 최저가 검색 (AI 필터링)
        price_result = await PriceComparisonService.search_lowest_price(keyword)
        
        # 3. DB 저장
        wishlist = WishlistCreate(
            keyword=keyword,
            target_price=target_price,
            user_id=user_id,
        )
        
        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)
        
        logging.info(f"[WISHLIST] 키워드로 등록 성공: {keyword}")
        return wishlist
    
    @staticmethod
    async def get_user_wishlist(user_id: str, db: Session):
        """사용자의 위시리스트 목록 조회"""
        wishlists = db.query(WishlistCreate).filter(WishlistCreate.user_id == user_id).all()
        return wishlists
    
    @staticmethod
    async def check_price(wishlist_id: int, user_id: str, db: Session):
        """가격 체크 (AI 필터링 적용)"""
        wishlist = db.query(WishlistCreate).filter(
            WishlistCreate.id == wishlist_id,
            WishlistCreate.user_id == user_id
        ).first()
        
        if not wishlist:
            raise Exception("위시리스트를 찾을 수 없습니다")
        
        logging.info(f"[PRICE] check_price userid={user_id}, wishlist_id={wishlist_id}, keyword={wishlist.keyword}")
        
        # AI 필터링 최저가 검색
        result = await PriceComparisonService.search_lowest_price(wishlist.keyword)
        
        if result:
            # wishlist.current_lowest_price = result['lowest_price']
            # wishlist.current_lowest_platform = result['mall']
            # wishlist.current_lowest_product_title = result['product_title']
            # wishlist.last_checked = datetime.now()
            db.commit()
            logging.info(f"[SUCCESS] DB 업데이트 완료, price={result['lowest_price']}, platform={result['mall']}")
            
            return {
                "message": "가격 체크 완료",
                "lowest_price": result['lowest_price'],
                "mall": result['mall'],
                "product_url": result['product_url'],
                "title": result['product_title']
            }
        else:
            return {"message": "최저가를 찾을 수 없습니다"}
    
    @staticmethod
    async def delete_wishlist(wishlist_id: int, user_id: str, db: Session):
        """위시리스트 삭제"""
        wishlist = db.query(WishlistCreate).filter(
            WishlistCreate.id == wishlist_id,
            WishlistCreate.user_id == user_id
        ).first()
        
        if not wishlist:
            raise Exception("위시리스트를 찾을 수 없습니다")
        
        db.delete(wishlist)
        db.commit()
    
    @staticmethod
    async def update_wishlist(wishlist_id: int, user_id: str, updates: dict, db: Session):
        """위시리스트 업데이트"""
        wishlist = db.query(WishlistCreate).filter(
            WishlistCreate.id == wishlist_id,
            WishlistCreate.user_id == user_id
        ).first()
        
        if not wishlist:
            raise Exception("위시리스트를 찾을 수 없습니다")
        
        for key, value in updates.items():
            setattr(wishlist, key, value)
        
        db.commit()
        db.refresh(wishlist)
        return wishlist
