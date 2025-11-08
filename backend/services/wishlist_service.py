from sqlalchemy.orm import Session
from models.wishlist_models import Wishlist, WishlistCreate, WishlistUpdate, WishlistResponse
from services.price_comparison_service import PriceComparisonService
from services.url_product_extractor import URLProductExtractor
from datetime import datetime
import logging

logger = logging.getLogger(__name__)

class WishlistService:
    @staticmethod
    async def create_from_url(product_url: str, target_price: int, user_id: str, db: Session):
        """
        URL로 위시리스트 등록
        1. URL에서 상품명 추출
        2. 키워드로 최저가 검색
        3. DB 저장
        """
        logger.info(f"[WISHLIST_SERVICE] URL 등록 시도: {product_url}")
        
        # 1. URL에서 상품명 추출
        product_title = URLProductExtractor.extract_product_name(product_url)
        if not product_title:
            raise Exception("상품명을 추출할 수 없습니다")
        
        # 2. 키워드 추출
        keyword = URLProductExtractor.extract_keyword_from_title(product_title)
        logger.info(f"[WISHLIST_SERVICE] 추출된 키워드: {keyword}")
        
        # 3. 기존 등록 여부 확인
        existing = db.query(Wishlist).filter(
            Wishlist.user_id == user_id,
            Wishlist.keyword == keyword
        ).first()
        
        if existing:
            raise Exception(f"이미 '{keyword}' 관심상품이 등록되어 있습니다")
        
        # 4. 최저가 검색 (AI 필터링)
        price_result = await PriceComparisonService.search_lowest_price(keyword)
        
        # 5. DB 저장
        wishlist = Wishlist(
            keyword=keyword,
            target_price=target_price,
            user_id=user_id
        )
        
        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)
        
        logger.info(f"[WISHLIST_SERVICE] URL로 등록 성공: {keyword}")
        return wishlist
    
    @staticmethod
    async def create_from_keyword(keyword: str, target_price: int, user_id: str, db: Session):
        """
        키워드로 위시리스트 등록
        """
        logger.info(f"[WISHLIST_SERVICE] 키워드 등록 시도: {keyword}")
        
        # 1. 기존 등록 여부 확인
        existing = db.query(Wishlist).filter(
            Wishlist.user_id == user_id,
            Wishlist.keyword == keyword
        ).first()
        
        if existing:
            raise Exception(f"이미 '{keyword}' 관심상품이 등록되어 있습니다")
        
        # 2. 최저가 검색 (AI 필터링)
        price_result = await PriceComparisonService.search_lowest_price(keyword)
        
        # 3. DB 저장
        wishlist = Wishlist(
            keyword=keyword,
            target_price=target_price,
            user_id=user_id
        )
        
        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)
        
        logger.info(f"[WISHLIST_SERVICE] 키워드로 등록 성공: {keyword}")
        return wishlist
    
    @staticmethod
    async def get_user_wishlist(user_id: str, db: Session):
        """사용자의 위시리스트 목록 조회"""
        logger.info(f"[WISHLIST_SERVICE] 위시리스트 조회: user_id={user_id}")
        wishlists = db.query(Wishlist).filter(Wishlist.user_id == user_id).all()
        return wishlists
    
    @staticmethod
    async def check_price(wishlist_id: int, user_id: str, db: Session):
        """가격 체크 (AI 필터링 적용)"""
        logger.info(f"[WISHLIST_SERVICE] 가격 체크 시도: wishlist_id={wishlist_id}, user_id={user_id}")
        
        wishlist = db.query(Wishlist).filter(
            Wishlist.id == wishlist_id,
            Wishlist.user_id == user_id
        ).first()
        
        if not wishlist:
            raise Exception("위시리스트를 찾을 수 없습니다")
        
        logger.info(f"[WISHLIST_SERVICE] 가격 체크: keyword={wishlist.keyword}")
        
        # AI 필터링 최저가 검색
        result = await PriceComparisonService.search_lowest_price(wishlist.keyword)
        
        if result:
            # DB 업데이트 (필요시 커멘트 해제)
            # wishlist.current_lowest_price = result['lowest_price']
            # wishlist.current_lowest_platform = result['mall']
            # wishlist.updated_at = datetime.now()
            db.commit()
            
            logger.info(f"[WISHLIST_SERVICE] 가격 체크 성공: {result['lowest_price']}원 - {result['mall']}")
            
            return {
                "message": "가격 체크 완료",
                "keyword": wishlist.keyword,
                "current_price": result['lowest_price'],
                "target_price": wishlist.target_price,
                "platform": result['mall'],
                "product_url": result['product_url'],
                "title": result['product_title'],
                "is_target_reached": result['lowest_price'] <= wishlist.target_price
            }
        else:
            logger.warning(f"[WISHLIST_SERVICE] 가격 체크 실패: 상품을 찾을 수 없음")
            return {
                "message": "최저가를 찾을 수 없습니다",
                "keyword": wishlist.keyword
            }
    
    @staticmethod
    async def delete_wishlist(wishlist_id: int, user_id: str, db: Session):
        """위시리스트 삭제"""
        logger.info(f"[WISHLIST_SERVICE] 삭제 시도: wishlist_id={wishlist_id}, user_id={user_id}")
        
        wishlist = db.query(Wishlist).filter(
            Wishlist.id == wishlist_id,
            Wishlist.user_id == user_id
        ).first()
        
        if not wishlist:
            raise Exception("위시리스트를 찾을 수 없습니다")
        
        db.delete(wishlist)
        db.commit()
        logger.info(f"[WISHLIST_SERVICE] 삭제 성공: ID={wishlist_id}")
    
    @staticmethod
    async def update_wishlist(wishlist_id: int, user_id: str, updates: dict, db: Session):
        """위시리스트 업데이트"""
        logger.info(f"[WISHLIST_SERVICE] 업데이트 시도: wishlist_id={wishlist_id}, user_id={user_id}")
        
        wishlist = db.query(Wishlist).filter(
            Wishlist.id == wishlist_id,
            Wishlist.user_id == user_id
        ).first()
        
        if not wishlist:
            raise Exception("위시리스트를 찾을 수 없습니다")
        
        for key, value in updates.items():
            if hasattr(wishlist, key):
                setattr(wishlist, key, value)
        
        wishlist.updated_at = datetime.now()
        db.commit()
        db.refresh(wishlist)
        
        logger.info(f"[WISHLIST_SERVICE] 업데이트 성공: ID={wishlist_id}")
        return wishlist
