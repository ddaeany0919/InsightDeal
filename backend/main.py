import asyncio
import json
import time
import uuid
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query, Request, BackgroundTasks, Depends
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import uvicorn
import logging
import structlog
from pydantic import BaseModel, validator
from sqlalchemy.orm import Session
from sqlalchemy import desc, and_

from scrapers.base_scraper import PriceComparisonEngine, ProductInfo
from scrapers.coupang_scraper import CoupangScraper
from scrapers.eleventh_scraper import EleventhScraper
from scrapers.gmarket_scraper import GmarketScraper
from scrapers.auction_scraper import AuctionScraper
from scrapers.naver_shopping_scraper import NaverShoppingScraper
from database.models import KeywordWishlist, KeywordPriceHistory, KeywordAlert, Base, get_db_engine
from database.session import get_db_session
from core.product_analyzer import ProductLinkAnalyzer
from models.product_models import ProductLinkRequest, ProductAnalysisResponse, LinkBasedWishlistCreate, ExtractedProductInfo, PlatformPriceInfo

# ===== App & Logging first =====
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(name)s | %(levelname)s | %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)

structlog.configure(
    processors=[
        structlog.stdlib.filter_by_level,
        structlog.stdlib.add_logger_name,
        structlog.stdlib.add_log_level,
        structlog.stdlib.PositionalArgumentsFormatter(),
        structlog.processors.TimeStamper(fmt="%Y-%m-%d %H:%M:%S"),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.processors.UnicodeDecoder(),
        structlog.processors.JSONRenderer()
    ],
    context_class=dict,
    logger_factory=structlog.stdlib.LoggerFactory(),
    wrapper_class=structlog.stdlib.BoundLogger,
    cache_logger_on_first_use=True,
)

logger = structlog.get_logger("insightdeal.api")
price_engine = PriceComparisonEngine()
naver_scraper = None
product_analyzer = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global naver_scraper, product_analyzer
    start_time = time.time()
    try:
        try:
            engine = get_db_engine()
            Base.metadata.create_all(engine)
            logger.info("✅ 데이터베이스 테이블 초기화 완료")
        except Exception as e:
            logger.warning(f"⚠️ 데이터베이스 초기화 실패: {e}")

        for name, cls in [("coupang", CoupangScraper), ("eleventh", EleventhScraper), ("gmarket", GmarketScraper), ("auction", AuctionScraper)]:
            try:
                price_engine.register_scraper(name, cls())
                logger.info(f"✅ {name} scraper 등록 완료")
            except Exception as e:
                logger.warning(f"⚠️ {name} scraper 등록 실패: {e}")

        try:
            naver_scraper = NaverShoppingScraper()
            logger.info("✅ Naver Shopping API scraper 초기화 완료")
        except Exception as e:
            logger.error(f"❌ Naver Shopping API 초기화 실패: {e}")
            naver_scraper = None
            
        try:
            import os
            openai_key = os.getenv('OPENAI_API_KEY')
            product_analyzer = ProductLinkAnalyzer(openai_api_key=openai_key)
            logger.info("✅ Product Link Analyzer 초기화 완료")
        except Exception as e:
            logger.warning(f"⚠️ Product Analyzer 초기화 실패: {e}")
            product_analyzer = ProductLinkAnalyzer()  # AI 없이도 동작
        yield
    finally:
        logger.info("서버 종료")

app = FastAPI(
    title="InsightDeal API",
    description="🛒 국내 최초 4몰 통합 가격비교 API + 네이버 쇼핑 API + 관심상품 시스템 + 🤖 AI 상품 분석",
    version="2.1.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ===== Models =====
class ComparisonResponse(BaseModel):
    trace_id: str
    query: str
    platforms: Dict[str, Dict[str, Any]]
    lowest_platform: Optional[str]
    lowest_price: Optional[int]
    max_saving: int
    average_price: Optional[int]
    success_count: int
    total_platforms: int
    response_time_ms: int
    updated_at: str
    errors: List[str] = []

class WishlistCreate(BaseModel):
    keyword: str
    target_price: int
    user_id: Optional[str] = "default"
    @validator('keyword')
    def v_kw(cls, v):
        v = v.strip()
        if not v or len(v) < 2: raise ValueError('키워드는 2글자 이상')
        if len(v) > 100: raise ValueError('키워드는 100글자 이하')
        return v
    @validator('target_price')
    def v_tp(cls, v):
        if v <= 0: raise ValueError('목표 가격은 0원보다 커야 합니다')
        if v > 100000000: raise ValueError('목표 가격은 1억원 이하')
        return v

class WishlistUpdate(BaseModel):
    target_price: Optional[int] = None
    is_active: Optional[bool] = None
    alert_enabled: Optional[bool] = None

class WishlistResponse(BaseModel):
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
    recorded_at: datetime
    lowest_price: int
    platform: str
    product_title: Optional[str]
    class Config:
        from_attributes = True

# ===== Utils =====
def _generate_trace_id() -> str:
    return f"trace_{int(time.time())}_{uuid.uuid4().hex[:8]}"

def calculate_price_drop_percentage(current_price: Optional[int], target_price: int) -> float:
    if current_price is None or current_price >= target_price: return 0.0
    return round(((target_price - current_price) / target_price) * 100, 1)

# ===== Middleware =====
@app.middleware("http")
async def log_requests(request: Request, call_next):
    start_time = time.time()
    trace_id = _generate_trace_id()
    logger.info("API 요청 시작", trace_id=trace_id, method=request.method, url=str(request.url))
    response = await call_next(request)
    elapsed_ms = round((time.time() - start_time) * 1000, 2)
    logger.info("API 요청 완료", trace_id=trace_id, status_code=response.status_code, elapsed_ms=elapsed_ms)
    response.headers["X-Trace-ID"] = trace_id
    return response

# ===== Endpoints =====
@app.get("/api/health")
async def health_check():
    total_scrapers = len(price_engine.scrapers) + (1 if naver_scraper else 0)
    analyzer_status = "AI+규칙" if product_analyzer and product_analyzer.openai_api_key else "규칙만"
    return {"status":"healthy","timestamp":datetime.now().isoformat(),"scrapers":total_scrapers,"analyzer":analyzer_status}

# ===== 🤖 AI 링크 분석 엔드포인트 =====
@app.post("/api/product/analyze-link", response_model=ProductAnalysisResponse)
async def analyze_product_link(link_request: ProductLinkRequest, db: Session = Depends(get_db_session)):
    """🔗 링크를 받아서 AI로 상품 정보 추출 후 다중 사이트 가격 비교"""
    trace_id = _generate_trace_id()
    start_time = time.time()
    
    if not product_analyzer:
        raise HTTPException(status_code=500, detail="상품 분석기가 초기화되지 않았습니다")
    
    try:
        async with product_analyzer as analyzer:
            # 1단계: HTML 크롤링
            html_content = await analyzer.scrape_product_page(link_request.url)
            
            # 2단계: AI 상품 정보 추출
            extracted = await analyzer.ai_extract_product_info(html_content, link_request.url)
            
            # 3단계: 다중 사이트 가격 비교
            comparison_results = {}
            
            # 네이버 쇼핑 검색
            if naver_scraper:
                try:
                    naver_results = await naver_scraper.search_products(extracted.search_keyword)
                    if naver_results:
                        best_naver = min(naver_results, key=lambda x: x.get('price', float('inf')))
                        comparison_results["naver"] = PlatformPriceInfo(
                            platform="네이버쇼핑",
                            price=best_naver.get('price'),
                            product_title=best_naver.get('title'),
                            product_url=best_naver.get('link')
                        )
                except Exception as e:
                    logger.warning(f"네이버 검색 실패: {e}")
            
            # 기존 스크래퍼들로 검색
            for platform_name, scraper in price_engine.scrapers.items():
                try:
                    results = await scraper.search_products(extracted.search_keyword)
                    if results:
                        best_result = min(results, key=lambda x: x.price if x.price else float('inf'))
                        comparison_results[platform_name] = PlatformPriceInfo(
                            platform=platform_name,
                            price=best_result.price,
                            product_title=best_result.title,
                            product_url=best_result.url
                        )
                except Exception as e:
                    logger.warning(f"{platform_name} 검색 실패: {e}")
            
            # 최저가 계산
            lowest_platform = None
            lowest_price = None
            if comparison_results:
                valid_prices = [(k, v.price) for k, v in comparison_results.items() if v.price]
                if valid_prices:
                    lowest_platform, lowest_price = min(valid_prices, key=lambda x: x[1])
            
            max_saving = 0
            if lowest_price and len([p for p in comparison_results.values() if p.price]) > 1:
                highest_price = max([p.price for p in comparison_results.values() if p.price])
                max_saving = highest_price - lowest_price
            
            return ProductAnalysisResponse(
                trace_id=trace_id,
                original_url=link_request.url,
                extracted_info=extracted,
                price_comparison=comparison_results,
                lowest_platform=lowest_platform,
                lowest_total_price=lowest_price,
                max_saving=max_saving,
                analysis_time_ms=round((time.time() - start_time) * 1000)
            )
            
    except Exception as e:
        logger.error(f"링크 분석 실패: {e}")
        raise HTTPException(status_code=400, detail=f"상품 분석에 실패했습니다: {str(e)}")

@app.post("/api/wishlist/add-from-link", response_model=WishlistResponse)
async def add_wishlist_from_link(request: LinkBasedWishlistCreate, db: Session = Depends(get_db_session)):
    """🔗 링크로 관심상품 추가: AI 분석 후 최적 키워드로 위시리스트 생성"""
    # 1단계: 링크 분석
    analysis = await analyze_product_link(ProductLinkRequest(
        url=request.url,
        target_price=request.target_price,
        user_id=request.user_id
    ), db)
    
    # 2단계: 분석 결과로 위시리스트 생성
    extracted = analysis.extracted_info
    existing = db.query(KeywordWishlist).filter(
        and_(
            KeywordWishlist.user_id == request.user_id,
            KeywordWishlist.keyword == extracted.search_keyword
        )
    ).first()
    
    if existing:
        raise HTTPException(status_code=400, detail=f"이미 '{extracted.search_keyword}' 관심상품이 등록되어 있습니다")
    
    # DB 저장
    db_wishlist = KeywordWishlist(
        user_id=request.user_id,
        keyword=extracted.search_keyword,
        target_price=request.target_price,
        current_lowest_price=analysis.lowest_total_price,
        current_lowest_platform=analysis.lowest_platform,
        current_lowest_product_title=extracted.product_name[:200] if extracted.product_name else None,
        original_url=request.url,  # 원본 링크 저장
        last_checked=datetime.utcnow()
    )
    
    db.add(db_wishlist)
    db.commit()
    db.refresh(db_wishlist)
    
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
        is_target_reached=(db_wishlist.current_lowest_price is not None and db_wishlist.current_lowest_price <= db_wishlist.target_price),
        is_active=db_wishlist.is_active,
        alert_enabled=db_wishlist.alert_enabled,
        created_at=db_wishlist.created_at,
        updated_at=db_wishlist.updated_at,
        last_checked=db_wishlist.last_checked
    )

@app.post("/api/wishlist", response_model=WishlistResponse)
async def create_wishlist(wishlist: WishlistCreate, db: Session = Depends(get_db_session)):
    existing = db.query(KeywordWishlist).filter(and_(KeywordWishlist.user_id==wishlist.user_id, KeywordWishlist.keyword==wishlist.keyword)).first()
    if existing:
        raise HTTPException(status_code=400, detail=f"이미 '{wishlist.keyword}' 관심상품이 등록되어 있습니다")
    db_wishlist = KeywordWishlist(user_id=wishlist.user_id, keyword=wishlist.keyword, target_price=wishlist.target_price)
    db.add(db_wishlist); db.commit(); db.refresh(db_wishlist)
    price_drop_percentage = calculate_price_drop_percentage(db_wishlist.current_lowest_price, db_wishlist.target_price)
    return WishlistResponse(
        id=db_wishlist.id, keyword=db_wishlist.keyword, target_price=db_wishlist.target_price,
        current_lowest_price=db_wishlist.current_lowest_price, current_lowest_platform=db_wishlist.current_lowest_platform,
        current_lowest_product_title=db_wishlist.current_lowest_product_title, price_drop_percentage=price_drop_percentage,
        is_target_reached=(db_wishlist.current_lowest_price is not None and db_wishlist.current_lowest_price <= db_wishlist.target_price),
        is_active=db_wishlist.is_active, alert_enabled=db_wishlist.alert_enabled,
        created_at=db_wishlist.created_at, updated_at=db_wishlist.updated_at, last_checked=db_wishlist.last_checked)

@app.get("/api/wishlist", response_model=List[WishlistResponse])
async def get_wishlist(user_id: str = Query(default="default"), active_only: bool = Query(default=True), db: Session = Depends(get_db_session)):
    query = db.query(KeywordWishlist).filter(KeywordWishlist.user_id==user_id)
    if active_only: query = query.filter(KeywordWishlist.is_active==True)
    wishlists = query.order_by(desc(KeywordWishlist.created_at)).all()
    res = []
    for w in wishlists:
        res.append(WishlistResponse(
            id=w.id, keyword=w.keyword, target_price=w.target_price,
            current_lowest_price=w.current_lowest_price, current_lowest_platform=w.current_lowest_platform,
            current_lowest_product_title=w.current_lowest_product_title,
            price_drop_percentage=calculate_price_drop_percentage(w.current_lowest_price, w.target_price),
            is_active=w.is_active, alert_enabled=w.alert_enabled, created_at=w.created_at, updated_at=w.updated_at, last_checked=w.last_checked))
    return res

@app.delete("/api/wishlist/{wishlist_id}")
async def delete_wishlist(wishlist_id: int, user_id: str = Query(default="default"), db: Session = Depends(get_db_session)):
    w = db.query(KeywordWishlist).filter(and_(KeywordWishlist.id==wishlist_id, KeywordWishlist.user_id==user_id)).first()
    if not w:
        raise HTTPException(status_code=404, detail="관심상품을 찾을 수 없습니다")
    db.delete(w); db.commit()
    return {"message":"삭제되었습니다"}

@app.post("/api/wishlist/{wishlist_id}/check-price")
async def manual_price_check(wishlist_id: int, user_id: str = Query(default="default"), db: Session = Depends(get_db_session)):
    """🔄 수동 가격 체크: 네이버 쇼핑 API로 최신 가격 업데이트"""
    w = db.query(KeywordWishlist).filter(and_(KeywordWishlist.id==wishlist_id, KeywordWishlist.user_id==user_id, KeywordWishlist.is_active==True)).first()
    if not w:
        raise HTTPException(status_code=404, detail="활성상태의 관심상품을 찾을 수 없습니다")
    
    # 네이버 쇼핑 검색으로 최신 가격 업데이트
    if naver_scraper:
        try:
            results = await naver_scraper.search_products(w.keyword)
            if results:
                best_result = min(results, key=lambda x: x.get('price', float('inf')))
                w.current_lowest_price = best_result.get('price')
                w.current_lowest_platform = "네이버쇼핑"
                w.current_lowest_product_title = best_result.get('title', '')[:200]
                w.last_checked = datetime.utcnow()
                db.commit()
                
                return {
                    "message": f"'{w.keyword}' 가격 체크를 완료했습니다",
                    "keyword": w.keyword,
                    "current_price": w.current_lowest_price,
                    "target_price": w.target_price,
                    "platform": w.current_lowest_platform,
                    "is_target_reached": w.current_lowest_price <= w.target_price if w.current_lowest_price else False,
                    "updated_at": w.last_checked.isoformat()
                }
        except Exception as e:
            logger.error(f"가격 체크 실패: {e}")
            raise HTTPException(status_code=500, detail="가격 체크에 실패했습니다")
    else:
        raise HTTPException(status_code=503, detail="네이버 쇼핑 서비스를 사용할 수 없습니다")

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True, workers=1, log_level="info", access_log=True)
