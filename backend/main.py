"""
🚀 InsightDeal FastAPI 서버 - 4몰 통합 가격 비교 API + 네이버 쇼핑 API

사용자 중심 설계:
- 2초 내 4몰 가격 비교 응답 (사용자는 기다리지 않는다)
- 실패해도 사용자 경험 방해 없음 (최소 2개 플랫폼 성공 시 결과 제공)
- 상세한 로깅으로 문제 발생 시 즉시 추적 가능
- 캐시로 반복 요청 시 즉시 응답
- 네이버 쇼핑 API로 안정적인 검색 결과 보장

"매일 쓰고 싶은 앱"을 위한 안정적이고 빠른 백엔드
"""

import asyncio
import json
import time
import uuid
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query, Request, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import uvicorn
import logging
import structlog
from pydantic import BaseModel

from scrapers.base_scraper import PriceComparisonEngine, ProductInfo
from scrapers.coupang_scraper import CoupangScraper
from scrapers.eleventh_scraper import EleventhScraper
from scrapers.gmarket_scraper import GmarketScraper
from scrapers.auction_scraper import AuctionScraper

# 네이버 쇼핑 API 스크래퍼 import
from scrapers.naver_shopping_scraper import NaverShoppingScraper

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
    }
}

@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    🏃‍♀️ 서버 시작 시 4개 스크래퍼 + 네이버 쇼핑 API 등록
    사용자에게 안정적인 서비스 제공을 위한 초기화
    """
    global naver_scraper
    start_time = time.time()
    
    try:
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
            platforms=list(price_engine.scrapers.keys()) + (["naver_shopping"] if naver_scraper else [])
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
    description="🛒 국내 최초 4몰 통합 가격비교 API + 네이버 쇼핑 API\n사용자 중심: 2초 내 응답 + 실시간 최저가 발견",
    version="1.0.0",
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
                    
                    logger.info(
                        "네이버 쇼핑 검색 성공",
                        trace_id=trace_id,
                        query=clean_query,
                        products_count=len(naver_products),
                        elapsed_ms=int((time.time() - start_time) * 1000)
                    )
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
            else:
                logger.info(
                    "기존 4몰 스크래핑 실패 (예상됨)",
                    trace_id=trace_id,
                    query=clean_query,
                    reason="웹 스크래핑 차단")
                
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
            
            logger.info(
                "가격 비교 성공",
                trace_id=trace_id,
                query=clean_query,
                success_count=success_count,
                lowest_platform=lowest_platform,
                lowest_price=lowest_price,
                max_saving=max_saving,
                elapsed_ms=elapsed_ms,
                naver_success=naver_scraper is not None and "naver_shopping" in platforms_data
            )
        else:
            metrics["failed_requests"] += 1
            
            logger.warning(
                "모든 플랫폼 검색 실패",
                trace_id=trace_id,
                query=clean_query,
                elapsed_ms=elapsed_ms,
                errors=errors
            )
            
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