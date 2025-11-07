from database.models import KeywordWishlist
from sqlalchemy.orm import Session
from sqlalchemy import and_, desc
from models.wishlist_models import WishlistCreate, WishlistUpdate, WishlistResponse
from services.price_comparison_service import PriceComparisonService
from services.ai_product_name_service import AIProductNameService, is_url
from services.product_scraper_factory import ProductScraperFactory
import logging

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
        
        search_keyword = w.keyword
        logging.info(f"[PRICE] check_price userid={user_id}, wishlist_id={wishlist_id}, keyword={search_keyword}")
        
        # 1. 링크이면 상품명 추출
        if is_url(search_keyword):
            try:
                scraper = ProductScraperFactory.get_scraper(search_keyword)
                
                # 1-1. 먼저 Selenium으로 HTML 가져오기
                scraped_html = None
                if hasattr(scraper, 'get_html'):
                    scraped_html = await scraper.get_html(search_keyword)
                
                # 1-2. HTML이 있으면 AI로 분석
                if scraped_html:
                    logging.info(f"[HTML] HTML 크기: {len(scraped_html)} bytes")
                    ai_name = AIProductNameService.extract_name_from_html(scraped_html, search_keyword)
                    
                    if ai_name:
                        logging.info(f"[AI_HTML] HTML 기반 추출 성공: {ai_name}")
                        search_keyword = ai_name
                    else:
                        # HTML 분석 실패 시 URL 기반 AI로 fallback
                        logging.warning("[AI_HTML] HTML 분석 실패, URL 기반 AI로 fallback")
                        ai_name = AIProductNameService.extract_name_from_url(search_keyword)
                        if ai_name:
                            search_keyword = ai_name
                else:
                    # HTML 가져오기 실패 시 URL 기반 AI로 fallback
                    logging.warning("[HTML] HTML 가져오기 실패, URL 기반 AI로 fallback")
                    ai_name = AIProductNameService.extract_name_from_url(search_keyword)
                    if ai_name:
                        search_keyword = ai_name
                        
            except Exception as e:
                # 모든 오류 시 URL 기반 AI로 fallback
                logging.error(f"[크롤링] 예외 발생: {e}, URL 기반 AI로 fallback", exc_info=True)
                ai_name = AIProductNameService.extract_name_from_url(search_keyword)
                if ai_name:
                    search_keyword = ai_name
        
        # 2. 네이버 최저가 검색
        result = await PriceComparisonService.search_lowest_price(search_keyword)
        logging.info(f"[NAVER] 최저가 검색 result={result}")
        
        if not result:
            logging.error(f"[FAIL] 가격 검색 실패, 최종 검색어={search_keyword}")
            return {"message": "가격 검색 실패: 상품명 또는 링크를 확인해주세요."}
        
        # 3. DB에 최저가 정보 업데이트
        w.current_lowest_price = result["lowest_price"]
        w.current_lowest_platform = result["mall"]
        w.current_lowest_product_title = result["product_title"]
        w.last_checked = None  # 실전 사용시 datetime.now()로!
        db.commit()
        
        logging.info(f"[SUCCESS] DB 업데이트 완료, price={result['lowest_price']}, platform={result['mall']}")
        
        return {
            "message": "가격 체크 완료",
            "lowest_price": result["lowest_price"],
            "mall": result["mall"],
            "product_url": result["product_url"],
            "title": result["product_title"]
        }
