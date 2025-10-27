"""
📈 Price History API - 90일 가격 히스토리 API

사용자 중심 설계:
- 사용자는 즐시 그래프를 보고 싶어함 (1초 내 응답 목표)
- 90일까지 지원하여 폴센트 뛰어넘기 (30일 vs 90일)
- 7/30/90일 선택 옵션으로 사용자 맞춤화
- 4개 플랫폼 가격 비교 가능
- 모바일에서 빠른 차트 렌더링을 위한 데이터 최적화
"""

from fastapi import APIRouter, HTTPException, Query, BackgroundTasks
from fastapi.responses import JSONResponse
from typing import List, Dict, Optional, Any
from datetime import datetime, timedelta
from dataclasses import dataclass
import asyncpg
import structlog
import os
from urllib.parse import urlparse

# 로깅 설정
logger = structlog.get_logger("api.history")

# DB 연결 풀
db_pool = None

@dataclass
class PricePoint:
    """가격 그래프의 한 지점"""
    date: str  # ISO 8601 형식
    price: int
    platform: str
    is_available: bool = True

@dataclass
class PriceHistoryResponse:
    """가격 히스토리 API 응답"""
    product_name: str
    period_days: int
    data_points: List[PricePoint]
    platforms: List[str]
    lowest_ever: int
    highest_ever: int
    current_trend: str  # "up", "down", "stable"
    last_updated: str
    trace_id: str

router = APIRouter(prefix="/api", tags=["history"])

async def init_db_pool():
    """DB 연결 풀 초기화"""
    global db_pool
    if db_pool is None:
        db_url = os.getenv('DATABASE_URL', 'postgresql://localhost/insightdeal')
        db_pool = await asyncpg.create_pool(
            db_url,
            min_size=2,
            max_size=10,
            command_timeout=30
        )
        logger.info("📈 가격 히스토리 API DB 연결 초기화")

def _generate_trace_id() -> str:
    """추적 ID 생성"""
    import time
    import uuid
    return f"hist_{int(time.time())}_{uuid.uuid4().hex[:8]}"

def _normalize_product_name(name: str) -> str:
    """상품명 정규화 (검색 용)"""
    import re
    # 특수문자 제거, 소문자 변환, 공백 정리
    normalized = re.sub(r'[^\w\s가-힣]', '', name.lower())
    return ' '.join(normalized.split())

def _calculate_trend(prices: List[int]) -> str:
    """가격 트렌드 계산"""
    if len(prices) < 2:
        return "stable"
    
    recent_prices = prices[-7:]  # 최근 7일
    if len(recent_prices) < 2:
        return "stable"
    
    # 선형 회귀를 사용한 트렌드 감지
    x = list(range(len(recent_prices)))
    y = recent_prices
    
    n = len(x)
    sum_x = sum(x)
    sum_y = sum(y)
    sum_xy = sum(x[i] * y[i] for i in range(n))
    sum_x2 = sum(x[i] ** 2 for i in range(n))
    
    # 기울기 계산
    slope = (n * sum_xy - sum_x * sum_y) / (n * sum_x2 - sum_x ** 2) if (n * sum_x2 - sum_x ** 2) != 0 else 0
    
    # 기울기에 따른 트렌드 결정
    avg_price = sum(recent_prices) / len(recent_prices)
    threshold = avg_price * 0.02  # 2% 임계값
    
    if slope > threshold:
        return "up"
    elif slope < -threshold:
        return "down"
    else:
        return "stable"

@router.get("/history")
async def get_price_history(
    product: str = Query(..., description="상품명 (예: 갤럭시 버즈 프로)"),
    period: int = Query(30, description="기간 (일): 7, 30, 90 지원", ge=1, le=90),
    platform: Optional[str] = Query(None, description="특정 플랫폼: coupang, eleventh, gmarket, auction")
) -> JSONResponse:
    """
    📈 90일 가격 히스토리 조회 - 폴센트 뛰어넘기
    
    사용자 경험 우선순위:
    1. 1초 내 응답 (모바일에서 즐시 차트 렌더링)
    2. 90일 지원으로 긴 기간 트렌드 파악 가능
    3. 반응형 차트를 위한 최소화된 데이터 전송
    """
    start_time = datetime.now()
    trace_id = _generate_trace_id()
    
    # DB 연결 확인
    if not db_pool:
        await init_db_pool()
    
    logger.info("📈 가격 히스토리 요청", 
                trace_id=trace_id, 
                product=product, 
                period_days=period, 
                platform=platform)
    
    try:
        # 상품명 정규화
        normalized_name = _normalize_product_name(product)
        
        async with db_pool.acquire() as conn:
            # 상품 ID 찾기 또는 생성
            product_id = await conn.fetchval(
                "SELECT id FROM products WHERE normalized_name = $1 OR name ILIKE $2",
                normalized_name, f"%{product}%"
            )
            
            if not product_id:
                # 상품이 없으면 새로 생성 (추후 수집용)
                product_id = await conn.fetchval(
                    """INSERT INTO products (name, normalized_name, first_seen_at) 
                       VALUES ($1, $2, NOW()) RETURNING id""",
                    product, normalized_name
                )
                
                logger.info(f"🆕 새 상품 등록: {product} (ID: {product_id})", trace_id=trace_id)
            
            # 기간 설정
            start_date = datetime.now() - timedelta(days=period)
            
            # 가격 히스토리 조회 - 성능 최적화된 쿼리
            platform_filter = "AND platform = $3" if platform else ""
            params = [product_id, start_date]
            if platform:
                params.append(platform)
            
            query = f"""
                SELECT 
                    DATE(captured_at) as date,
                    platform,
                    AVG(current_price)::int as avg_price,
                    MIN(current_price) as min_price,
                    MAX(current_price) as max_price,
                    COUNT(*) as data_points,
                    BOOL_OR(is_available) as is_available
                FROM price_history 
                WHERE product_id = $1 
                  AND captured_at >= $2
                  {platform_filter}
                GROUP BY DATE(captured_at), platform
                ORDER BY date ASC, platform ASC
            """
            
            rows = await conn.fetch(query, *params)
            
            if not rows:
                logger.warning("📋 가격 히스토리 데이터 없음", 
                              trace_id=trace_id, 
                              product=product, 
                              product_id=product_id)
                
                return JSONResponse(
                    status_code=404,
                    content={
                        "error": "가격 히스토리를 찾을 수 없습니다",
                        "message": f"{product} 상품의 {period}일 간 가격 데이터가 없습니다",
                        "trace_id": trace_id
                    }
                )
            
            # 데이터 처리
            data_points = []
            platforms = set()
            all_prices = []
            
            for row in rows:
                price_point = PricePoint(
                    date=row['date'].isoformat(),
                    price=row['avg_price'],
                    platform=row['platform'],
                    is_available=row['is_available']
                )
                data_points.append(price_point)
                platforms.add(row['platform'])
                all_prices.append(row['avg_price'])
            
            # 사용자에게 유용한 통계 정보 계산
            lowest_ever = min(all_prices) if all_prices else 0
            highest_ever = max(all_prices) if all_prices else 0
            current_trend = _calculate_trend(all_prices)
            
            elapsed_ms = int((datetime.now() - start_time).total_seconds() * 1000)
            
            # 성능 로깅
            is_fast_response = elapsed_ms <= 1000  # 1초 목표
            logger.info("✅ 가격 히스토리 조회 완료",
                       trace_id=trace_id,
                       product=product,
                       period_days=period,
                       platforms=len(platforms),
                       data_points=len(data_points),
                       lowest_ever=lowest_ever,
                       highest_ever=highest_ever,
                       trend=current_trend,
                       elapsed_ms=elapsed_ms,
                       is_fast=is_fast_response)
            
            response = PriceHistoryResponse(
                product_name=product,
                period_days=period,
                data_points=data_points,
                platforms=sorted(list(platforms)),
                lowest_ever=lowest_ever,
                highest_ever=highest_ever,
                current_trend=current_trend,
                last_updated=datetime.now().isoformat(),
                trace_id=trace_id
            )
            
            return JSONResponse(
                content={
                    "product_name": response.product_name,
                    "period_days": response.period_days,
                    "data_points": [
                        {
                            "date": p.date,
                            "price": p.price,
                            "platform": p.platform,
                            "is_available": p.is_available
                        } for p in response.data_points
                    ],
                    "platforms": response.platforms,
                    "lowest_ever": response.lowest_ever,
                    "highest_ever": response.highest_ever,
                    "current_trend": response.current_trend,
                    "last_updated": response.last_updated,
                    "trace_id": response.trace_id,
                    "performance": {
                        "response_time_ms": elapsed_ms,
                        "is_fast": is_fast_response
                    }
                },
                headers={
                    "X-Trace-ID": trace_id,
                    "Cache-Control": "max-age=300"  # 5분 캐시
                }
            )
            
    except Exception as e:
        elapsed_ms = int((datetime.now() - start_time).total_seconds() * 1000)
        logger.error("❌ 가격 히스토리 조회 실패",
                     trace_id=trace_id,
                     product=product,
                     period=period,
                     error=str(e),
                     elapsed_ms=elapsed_ms,
                     exc_info=True)
        
        raise HTTPException(
            status_code=500,
            detail={
                "error": "가격 히스토리 조회 중 오류가 발생했습니다",
                "trace_id": trace_id
            }
        )

@router.post("/track")
async def add_track(
    product_url: str,
    target_price: Optional[int] = None,
    device_token: Optional[str] = None
) -> JSONResponse:
    """
    🔍 상품 추적 등록
    
    사용자가 상품 URL을 입력하면 자동으로 추적 시작
    """
    trace_id = _generate_trace_id()
    
    logger.info("🔍 상품 추적 등록 요청", 
                trace_id=trace_id, 
                url=product_url, 
                target_price=target_price)
    
    try:
        # TODO: Day 5 간소 버전 - URL 분석해서 플랫폼 식별 및 상품 등록
        # 전체 기능은 Day 6에서 구현
        
        return JSONResponse(content={
            "message": "상품 추적이 등록되었습니다",
            "trace_id": trace_id,
            "status": "registered"
        })
        
    except Exception as e:
        logger.error("❌ 상품 추적 등록 실패", 
                     trace_id=trace_id, 
                     error=str(e), 
                     exc_info=True)
        
        raise HTTPException(
            status_code=500,
            detail={
                "error": "상품 추적 등록 중 오류가 발생했습니다",
                "trace_id": trace_id
            }
        )