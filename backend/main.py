"""
🚀 InsightDeal FastAPI 서버 - 4몰 통합 가격 비교 API + 네이버 쇼핑 API + 관심상품 시스템

사용자 중심 설계:
- 2초 내 4몰 가격 비교 응답 (사용자는 기다리지 않는다)
- 실패해도 사용자 경험 방해 없음 (최소 2개 플랫폼 성공 시 결과 제공)
- 상세한 로깅으로 문제 발생 시 즉시 추적 가능
- 캐시로 반복 요청 시 즉시 응답
- 네이버 쇼핑 API로 안정적인 검색 결과 보장
- 키워드 기반 관심상품 + 가격 추적 시스템

"매일 쓰고 싶은 앱"을 위한 안정적이고 빠른 백엔드
"""

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

# 네이버 쇼핑 API 스크래퍼 import
from scrapers.naver_shopping_scraper import NaverShoppingScraper

# 관심상품 시스템 import
from database.models import KeywordWishlist, KeywordPriceHistory, KeywordAlert, Base, get_db_engine
from database.session import get_db_session  # 수정: get_db -> get_db_session

# 🎯 사용자 중심 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(name)s | %(levelname)s | %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)

# 구조화된 로깅 (JSON 형태로 파싱 가능)
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

# 글로벌 가격 비교 엔진
price_engine = PriceComparisonEngine()

# 네이버 쇼핑 API 스크래퍼 전역 인스턴스
naver_scraper = None

# 성능 메트릭 수집 (네이버 쇼핑 추가)
metrics = {
    "total_requests": 0,
    "successful_requests": 0,
    "failed_requests": 0,
    "avg_response_time": 0.0,
    "cache_hits": 0,
    "platform_success_rates": {
        "coupang": {"success": 0, "total": 0},
        "eleventh": {"success": 0, "total": 0},
        "gmarket": {"success": 0, "total": 0},
        "auction": {"success": 0, "total": 0},
        "naver_shopping": {"success": 0, "total": 0}  # 네이버 쇼핑 추가
    },
    "wishlist_stats": {
        "total_items": 0,
        "active_items": 0,
        "price_alerts_sent": 0
    }
}

@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    🏃‍♀️ 서버 시작 시 4개 스크래퍼 + 네이버 쇼핑 API + DB 초기화
    사용자에게 안정적인 서비스 제공을 위한 초기화
    """
    global naver_scraper
    start_time = time.time()
    
    try:
        # 데이터베이스 테이블 생성
        try:
            engine = get_db_engine()
            Base.metadata.create_all(engine)
            logger.info("✅ 데이터베이스 테이블 초기화 완료")
        except Exception as e:
            logger.warning(f"⚠️ 데이터베이스 초기화 실패: {e}")
        
        # 4몰 스크래퍼 등록 (기존 웹 스크래핑, 차단될 가능성 있음)
        scrapers = [
            ("coupang", CoupangScraper),
            ("eleventh", EleventhScraper),
            ("gmarket", GmarketScraper),
            ("auction", AuctionScraper)
        ]
        
        for name, scraper_class in scrapers:
            try:
                price_engine.register_scraper(name, scraper_class())
                logger.info(f"✅ {name} scraper 등록 완료")
            except Exception as e:
                logger.warning(f"⚠️ {name} scraper 등록 실패: {e}")
        
        # 네이버 쇼핑 API 스크래퍼 초기화 (핵심!)
        try:
            naver_scraper = NaverShoppingScraper()
            logger.info("✅ Naver Shopping API scraper 초기화 완료")
        except Exception as e:
            logger.error(f"❌ Naver Shopping API 초기화 실패: {e}")
            naver_scraper = None
        
        init_time = time.time() - start_time
        total_scrapers = len(price_engine.scrapers) + (1 if naver_scraper else 0)
        
        logger.info(
            "서버 초기화 완료", 
            scrapers_count=total_scrapers,
            init_time_ms=round(init_time * 1000, 2),
            platforms=list(price_engine.scrapers.keys()) + (["naver_shopping"] if naver_scraper else []),
            wishlist_system="enabled"
        )
        
        yield  # 서버 실행
        
    except Exception as e:
        logger.error(
            "서버 초기화 실패",
            error=str(e),
            elapsed_ms=round((time.time() - start_time) * 1000, 2)
        )
        raise
    finally:
        logger.info("서버 종료", final_metrics=metrics)

# FastAPI 앱 생성
app = FastAPI(
    title="InsightDeal API",
    description="🛒 국내 최초 4몰 통합 가격비교 API + 네이버 쇼핑 API + 관심상품 시스템\n사용자 중심: 2초 내 응답 + 실시간 최저가 발견 + 가격 추적 알림",
    version="2.0.0",
    lifespan=lifespan
)

# CORS 설정 (Android 앱 연동)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 프로덕션에서는 특정 도메인으로 제한
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ======= 기존 모델들 =======
class ComparisonResponse(BaseModel):
    """
    📊 4몰 + 네이버 쇼핑 가격 비교 응답 모델
    사용자가 받고 싶어하는 핵심 정보만 간단명료하게
    """
    trace_id: str
    query: str
    platforms: Dict[str, Dict[str, Any]]
    lowest_platform: Optional[str]
    lowest_price: Optional[int] 
    max_saving: int  # 사용자가 가장 관심 있는 "얼마나 아낄 수 있는지"
    average_price: Optional[int]
    success_count: int
    total_platforms: int
    response_time_ms: int
    updated_at: str
    errors: List[str] = []  # 문제 발생 시에도 사용자에게는 숨기고 로그로만

# ======= 관심상품 모델들 =======
class WishlistCreate(BaseModel):
    """관심상품 등록 요청 모델"""
    keyword: str
    target_price: int
    user_id: Optional[str] = "default"
    
    @validator('keyword')
    def validate_keyword(cls, v):
        v = v.strip()
        if not v or len(v) < 2:
            raise ValueError('키워드는 2글자 이상 입력해주세요')
        if len(v) > 100:
            raise ValueError('키워드는 100글자 이하로 입력해주세요')
        return v
    
    @validator('target_price')
    def validate_target_price(cls, v):
        if v <= 0:
            raise ValueError('목표 가격은 0원보다 커야 합니다')
        if v > 100000000:  # 1억원
            raise ValueError('목표 가격은 1억원 이하로 입력해주세요')
        return v

class WishlistUpdate(BaseModel):
    """관심상품 수정 요청 모델"""
    target_price: Optional[int] = None
    is_active: Optional[bool] = None
    alert_enabled: Optional[bool] = None

class WishlistResponse(BaseModel):
    """관심상품 응답 모델"""
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
    """가격 히스토리 응답 모델"""
    recorded_at: datetime
    lowest_price: int
    platform: str
    product_title: Optional[str]
    
    class Config:
        from_attributes = True

# ======= 유틸리티 함수들 =======
def _generate_trace_id() -> str:
    """
    🔍 요청 추적 ID 생성 (문제 발생 시 end-to-end 추적용)
    """
    return f"trace_{int(time.time())}_{uuid.uuid4().hex[:8]}"

def _clean_query(query: str) -> str:
    """
    🧹 사용자 입력 쿼리 정제 (검색 품질 향상)
    """
    if not query:
        return ""
    # 공백 정리, 특수문자 제거, 길이 제한
    cleaned = " ".join(query.strip().split())
    return cleaned[:50]  # 너무 긴 쿼리는 잘라서 성능 확보

def _update_platform_metrics(platform: str, success: bool):
    """
    📈 플랫폼별 성공률 추적 (운영 품질 모니터링)
    """
    if platform in metrics["platform_success_rates"]:
        metrics["platform_success_rates"][platform]["total"] += 1
        if success:
            metrics["platform_success_rates"][platform]["success"] += 1

def calculate_price_drop_percentage(current_price: Optional[int], target_price: int) -> float:
    """가격 하락 비율 계산"""
    if current_price is None or current_price >= target_price:
        return 0.0
    return round(((target_price - current_price) / target_price) * 100, 1)

async def search_and_update_wishlist_price(wishlist: KeywordWishlist, db: Session):
    """관심상품 가격 검색 및 업데이트"""
    global naver_scraper
    
    if not naver_scraper:
        return
    
    try:
        # 네이버 쇼핑 API로 검색
        products = naver_scraper.search_products(wishlist.keyword, display=5, sort="asc")
        
        if products:
            # 최저가 상품 찾기
            lowest_product = min(products, key=lambda x: x.price if x.price > 0 else float('inf'))
            
            if lowest_product.price > 0:
                # 관심상품 정보 업데이트
                old_price = wishlist.current_lowest_price
                wishlist.current_lowest_price = lowest_product.price
                wishlist.current_lowest_platform = "naver_shopping"
                wishlist.current_lowest_product_title = lowest_product.title
                wishlist.last_checked = datetime.utcnow()
                
                # 가격 히스토리 저장
                price_history = KeywordPriceHistory(
                    keyword_wishlist_id=wishlist.id,
                    lowest_price=lowest_product.price,
                    platform="naver_shopping",
                    product_title=lowest_product.title,
                    product_url=lowest_product.url,
                    total_products_found=len(products),
                    platforms_checked="naver_shopping"
                )
                db.add(price_history)
                
                # 목표 가격 도달 시 알림 생성
                if (wishlist.alert_enabled and 
                    lowest_product.price <= wishlist.target_price and
                    (old_price is None or old_price > wishlist.target_price)):
                    
                    alert = KeywordAlert(
                        keyword_wishlist_id=wishlist.id,
                        alert_type="target_reached",
                        triggered_price=lowest_product.price,
                        target_price=wishlist.target_price,
                        platform="naver_shopping",
                        product_title=lowest_product.title,
                        product_url=lowest_product.url
                    )
                    db.add(alert)
                    metrics["wishlist_stats"]["price_alerts_sent"] += 1
                
                db.commit()
                
                logger.info(
                    "관심상품 가격 업데이트 완료",
                    wishlist_id=wishlist.id,
                    keyword=wishlist.keyword,
                    old_price=old_price,
                    new_price=lowest_product.price,
                    target_reached=(lowest_product.price <= wishlist.target_price)
                )
                
    except Exception as e:
        logger.error(
            "관심상품 가격 업데이트 실패",
            wishlist_id=wishlist.id,
            keyword=wishlist.keyword,
            error=str(e)
        )

# ======= 미들웨어 =======
@app.middleware("http")
async def log_requests(request: Request, call_next):
    """
    📝 모든 API 요청 로깅 (문제 발생 시 추적을 위해)
    """
    start_time = time.time()
    trace_id = _generate_trace_id()
    
    # 요청 로그
    logger.info(
        "API 요청 시작",
        trace_id=trace_id,
        method=request.method,
        url=str(request.url),
        user_agent=request.headers.get("user-agent", "unknown")
    )
    
    # 요청 처리
    response = await call_next(request)
    
    # 응답 로그
    elapsed_ms = round((time.time() - start_time) * 1000, 2)
    logger.info(
        "API 요청 완료",
        trace_id=trace_id,
        status_code=response.status_code,
        elapsed_ms=elapsed_ms,
        success=200 <= response.status_code < 300
    )
    
    response.headers["X-Trace-ID"] = trace_id
    return response

# ======= 기존 API 엔드포인트들 =======
@app.get("/api/health")
async def health_check():
    """
    ❤️ 헬스체크 - 서버 상태 및 성능 지표 확인
    """
    total_scrapers = len(price_engine.scrapers) + (1 if naver_scraper else 0)
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "scrapers": total_scrapers,
        "features": ["price_comparison", "wishlist_system", "naver_shopping_api"],
        "metrics": metrics
    }

@app.get("/api/compare", response_model=ComparisonResponse)
async def compare_prices(
    query: str = Query(..., description="상품명 (예: 갤럭시 버즈 프로)", max_length=50),
    trace_id: str = None
):
    """
    🔥 4몰 + 네이버 쇼핑 가격 비교 - 사용자가 가장 많이 사용할 핵심 API
    
    사용자 경험 우선순위:
    1. 빠른 응답 (2초 목표) 
    2. 최저가 정보 정확성
    3. 실패해도 사용 가능한 결과 제공 (네이버 쇼핑 API 덕분에 가능)
    """
    start_time = time.time()
    if not trace_id:
        trace_id = _generate_trace_id()
    
    metrics["total_requests"] += 1
    
    # 사용자 입력 검증 및 정제
    clean_query = _clean_query(query)
    if not clean_query:
        logger.warning("빈 쿼리 요청", trace_id=trace_id, original_query=query)
        raise HTTPException(status_code=400, detail="상품명을 입력해주세요")
    
    logger.info(
        "가격 비교 시작", 
        trace_id=trace_id, 
        query=clean_query,
        target_time_ms=2000,
        total_platforms=len(price_engine.scrapers) + (1 if naver_scraper else 0)
    )
    
    try:
        platforms_data = {}
        errors = []
        success_count = 0
        
        # 🚀 1순위: 네이버 쇼핑 API (가장 안정적)
        if naver_scraper:
            try:
                naver_products = naver_scraper.search_products(clean_query, display=10, sort="asc")
                
                if naver_products:
                    # 네이버 쇼핑 결과를 기존 구조에 맞게 변환
                    formatted_products = []
                    for product in naver_products[:5]:  # 상위 5개만
                        formatted_product = {
                            "title": product.title,
                            "price": product.price,
                            "original_price": product.price,
                            "discount_rate": 0,
                            "url": product.url,
                            "shipping_fee": 0,
                            "seller": product.mall,
                            "rating": 0,
                            "is_available": True,
                            "image": product.image,
                            "mall": product.mall,
                            "category": product.category1 or "일반"
                        }
                        formatted_products.append(formatted_product)
                    
                    platforms_data["naver_shopping"] = {
                        "products": formatted_products,
                        "count": len(formatted_products),
                        "status": "success",
                        "source": "naver_api",
                        "response_time_ms": int((time.time() - start_time) * 1000)
                    }
                    
                    success_count += 1
                    _update_platform_metrics("naver_shopping", True)
                    
                else:
                    platforms_data["naver_shopping"] = {
                        "products": [],
                        "count": 0,
                        "status": "no_results",
                        "source": "naver_api"
                    }
                    errors.append("네이버 쇼핑: 검색 결과 없음")
                    _update_platform_metrics("naver_shopping", False)
                    
            except Exception as e:
                logger.error(
                    "네이버 쇼핑 API 오류",
                    trace_id=trace_id,
                    query=clean_query,
                    error=str(e)
                )
                platforms_data["naver_shopping"] = {
                    "products": [],
                    "count": 0,
                    "status": "error",
                    "error": str(e),
                    "source": "naver_api"
                }
                errors.append(f"네이버 쇼핑 API 오류: {str(e)}")
                _update_platform_metrics("naver_shopping", False)
        
        # 🕸️ 2순위: 기존 4몰 웹 스크래핑 (차단될 가능성 높음)
        try:
            comparison_result = await price_engine.compare_prices(clean_query)
            
            if comparison_result and comparison_result.platforms:
                for platform, product_info in comparison_result.platforms.items():
                    if product_info:
                        platforms_data[platform] = {
                            "price": product_info.current_price,
                            "original_price": product_info.original_price,
                            "discount_rate": product_info.discount_rate,
                            "url": product_info.product_url,
                            "shipping_fee": product_info.shipping_fee,
                            "seller": product_info.seller_name,
                            "rating": product_info.rating,
                            "is_available": product_info.is_available,
                            "source": "web_scraping"
                        }
                        success_count += 1
                        _update_platform_metrics(platform, True)
                    else:
                        _update_platform_metrics(platform, False)
                        errors.append(f"{platform} 검색 실패 (차단 가능성)")
                        
        except Exception as e:
            logger.warning(
                "4몰 웹 스크래핑 실패 (예상됨)",
                trace_id=trace_id,
                query=clean_query,
                error=str(e)
            )
            errors.append("웹 스크래핑 차단으로 인한 일부 몰 검색 실패")
        
        # 📊 사용자가 원하는 핵심 정보 계산
        all_prices = []
        lowest_price = None
        lowest_platform = None
        
        for platform, data in platforms_data.items():
            if data.get("status") == "success":
                if data.get("products"):  # 네이버 쇼핑 형태
                    for product in data["products"]:
                        price = product.get("price", 0)
                        if price > 0:
                            all_prices.append(price)
                            if lowest_price is None or price < lowest_price:
                                lowest_price = price
                                lowest_platform = platform
                elif data.get("price"):  # 기존 스크래퍼 형태
                    price = data["price"]
                    if price > 0:
                        all_prices.append(price)
                        if lowest_price is None or price < lowest_price:
                            lowest_price = price
                            lowest_platform = platform
        
        # 절약 금액 계산
        max_saving = 0
        average_price = None
        
        if len(all_prices) >= 2:
            max_saving = max(all_prices) - min(all_prices)
            average_price = sum(all_prices) // len(all_prices)
        elif len(all_prices) == 1:
            average_price = all_prices[0]
        
        elapsed_ms = round((time.time() - start_time) * 1000, 2)
        
        # 성공/실패 판정
        if success_count > 0:
            metrics["successful_requests"] += 1
        else:
            metrics["failed_requests"] += 1
            if not errors:
                errors = ["모든 쇼핑몰에서 상품을 찾을 수 없습니다"]
        
        # 평균 응답 시간 업데이트
        total_requests = metrics["successful_requests"] + metrics["failed_requests"]
        metrics["avg_response_time"] = (
            (metrics["avg_response_time"] * (total_requests - 1) + elapsed_ms) / total_requests
        )
        
        return ComparisonResponse(
            trace_id=trace_id,
            query=clean_query,
            platforms=platforms_data,
            lowest_platform=lowest_platform,
            lowest_price=lowest_price,
            max_saving=max_saving,
            average_price=average_price,
            success_count=success_count,
            total_platforms=len(price_engine.scrapers) + (1 if naver_scraper else 0),
            response_time_ms=int(elapsed_ms),
            updated_at=datetime.now().isoformat(),
            errors=errors
        )
        
    except Exception as e:
        elapsed_ms = round((time.time() - start_time) * 1000, 2)
        logger.error(
            "가격 비교 예외 발생",
            trace_id=trace_id,
            query=clean_query,
            error=str(e),
            elapsed_ms=elapsed_ms,
            exc_info=True
        )
        
        metrics["failed_requests"] += 1
        
        # 사용자에게는 친화적인 메시지, 개발자에게는 상세 로그
        raise HTTPException(
            status_code=500, 
            detail="잠시 후 다시 시도해주세요"
        )

# ======= 관심상품 API 엔드포인트들 =======
@app.post("/api/wishlist", response_model=WishlistResponse)
async def create_wishlist(
    wishlist: WishlistCreate,
    db: Session = Depends(get_db_session)  # 수정: get_db -> get_db_session
):
    """
    🆕 관심상품 등록
    키워드를 바탕으로 관심상품을 등록하고 즉시 가격 검색
    """
    # 중복 체크
    existing = db.query(KeywordWishlist).filter(
        and_(
            KeywordWishlist.user_id == wishlist.user_id,
            KeywordWishlist.keyword == wishlist.keyword
        )
    ).first()
    
    if existing:
        raise HTTPException(
            status_code=400, 
            detail=f"이미 '{wishlist.keyword}' 관심상품이 등록되어 있습니다"
        )
    
    # 새로운 관심상품 생성
    db_wishlist = KeywordWishlist(
        user_id=wishlist.user_id,
        keyword=wishlist.keyword,
        target_price=wishlist.target_price
    )
    
    db.add(db_wishlist)
    db.commit()
    db.refresh(db_wishlist)
    
    # 메트릭 업데이트
    metrics["wishlist_stats"]["total_items"] += 1
    metrics["wishlist_stats"]["active_items"] += 1
    
    # 비동기로 즉시 가격 검색 수행
    try:
        await search_and_update_wishlist_price(db_wishlist, db)
        db.refresh(db_wishlist)
    except Exception as e:
        logger.warning(f"초기 가격 검색 실패: {e}")
    
    # 응답 데이터 생성
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
        is_target_reached=(
            db_wishlist.current_lowest_price is not None and 
            db_wishlist.current_lowest_price <= db_wishlist.target_price
        ),
        is_active=db_wishlist.is_active,
        alert_enabled=db_wishlist.alert_enabled,
        created_at=db_wishlist.created_at,
        updated_at=db_wishlist.updated_at,
        last_checked=db_wishlist.last_checked
    )

@app.get("/api/wishlist", response_model=List[WishlistResponse])
async def get_wishlist(
    user_id: str = Query(default="default", description="사용자 ID"),
    active_only: bool = Query(default=True, description="활성상태만 조회"),
    db: Session = Depends(get_db_session)  # 수정: get_db -> get_db_session
):
    """
    📝 관심상품 목록 조회
    사용자의 모든 관심상품을 최신 등록 순으로 조회
    """
    query = db.query(KeywordWishlist).filter(KeywordWishlist.user_id == user_id)
    
    if active_only:
        query = query.filter(KeywordWishlist.is_active == True)
    
    wishlists = query.order_by(desc(KeywordWishlist.created_at)).all()
    
    # 응답 데이터 생성
    response_list = []
    for wishlist in wishlists:
        price_drop_percentage = calculate_price_drop_percentage(
            wishlist.current_lowest_price, 
            wishlist.target_price
        )
        
        response_list.append(WishlistResponse(
            id=wishlist.id,
            keyword=wishlist.keyword,
            target_price=wishlist.target_price,
            current_lowest_price=wishlist.current_lowest_price,
            current_lowest_platform=wishlist.current_lowest_platform,
            current_lowest_product_title=wishlist.current_lowest_product_title,
            price_drop_percentage=price_drop_percentage,
            is_target_reached=(
                wishlist.current_lowest_price is not None and 
                wishlist.current_lowest_price <= wishlist.target_price
            ),
            is_active=wishlist.is_active,
            alert_enabled=wishlist.alert_enabled,
            created_at=wishlist.created_at,
            updated_at=wishlist.updated_at,
            last_checked=wishlist.last_checked
        ))
    
    return response_list

@app.post("/api/wishlist/{wishlist_id}/check-price")
async def manual_price_check(
    wishlist_id: int,
    user_id: str = Query(default="default", description="사용자 ID"),
    db: Session = Depends(get_db_session)  # 수정: get_db -> get_db_session
):
    """
    🔄 수동 가격 체크
    사용자가 직접 가격 업데이트를 요청할 때 사용
    """
    # 관심상품 존재 확인
    wishlist = db.query(KeywordWishlist).filter(
        and_(
            KeywordWishlist.id == wishlist_id,
            KeywordWishlist.user_id == user_id,
            KeywordWishlist.is_active == True
        )
    ).first()
    
    if not wishlist:
        raise HTTPException(status_code=404, detail="활성상태의 관심상품을 찾을 수 없습니다")
    
    # 비동기로 가격 검색 수행
    try:
        await search_and_update_wishlist_price(wishlist, db)
        return {
            "message": f"'{wishlist.keyword}' 가격 체크를 완료했습니다",
            "keyword": wishlist.keyword,
            "current_price": wishlist.current_lowest_price,
            "target_price": wishlist.target_price,
            "updated_at": datetime.utcnow().isoformat()
        }
    except Exception as e:
        logger.error(f"수동 가격 체크 실패: {e}")
        raise HTTPException(
            status_code=500,
            detail="가격 체크 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        )

if __name__ == "__main__":
    # 🚀 서버 실행 (사용자 응답속도 최적화 설정)
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,  # 개발 시에만
        workers=1,    # 개발 시에만, 프로덕션에서는 4+ 권장
        log_level="info",
        access_log=True
    )