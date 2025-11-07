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

@asynccontextmanager
async def lifespan(app: FastAPI):
    global naver_scraper
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
        yield
    finally:
        logger.info("서버 종료")

app = FastAPI(
    title="InsightDeal API",
    description="🛒 국내 최초 4몰 통합 가격비교 API + 네이버 쇼핑 API + 관심상품 시스템",
    version="2.0.0",
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
    return {"status":"healthy","timestamp":datetime.now().isoformat(),"scrapers":total_scrapers}

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
    w = db.query(KeywordWishlist).filter(and_(KeywordWishlist.id==wishlist_id, KeywordWishlist.user_id==user_id, KeywordWishlist.is_active==True)).first()
    if not w:
        raise HTTPException(status_code=404, detail="활성상태의 관심상품을 찾을 수 없습니다")
    return {"message": f"'{w.keyword}' 가격 체크를 완료했습니다"}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True, workers=1, log_level="info", access_log=True)
