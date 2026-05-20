import asyncio
import logging
import os
import sys
from datetime import datetime, timedelta
import random
import httpx
from sqlalchemy import func
from typing import Optional

# 프로젝트 루트 디렉토리를 Python path에 추가 (2단계 거쳐서 상위인 프로젝트 루트 참조)
root_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(root_dir)

from backend.database.session import SessionLocal
from backend.database.models import Deal, NaverPriceHistory

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("NaverPriceScheduler")

# 네이버 쇼핑 API 인증 정보 로드
NAVER_CLIENT_ID = os.getenv("NAVER_CLIENT_ID")
NAVER_CLIENT_SECRET = os.getenv("NAVER_CLIENT_SECRET")

async def fetch_naver_lowest_price(brand: str, model_code: str) -> Optional[int]:
    """
    네이버 쇼핑 API를 호출하여 해당 브랜드와 모델 코드의 시장 최저가를 조회합니다.
    API 인증 키가 없거나 에러가 발생할 경우, 디펜시브 처리를 통해 None을 반환하거나 Mock 데이터를 제공합니다.
    """
    query = f"{brand} {model_code}"
    
    # API 키가 없거나 기본 플레이스홀더인 경우 디펜시브 모드로 진입 (개발 환경 친화적 Mock 가격 생성)
    if not NAVER_CLIENT_ID or NAVER_CLIENT_ID.startswith("your_") or not NAVER_CLIENT_SECRET:
        logger.warning(f"⚠️ 네이버 쇼핑 API 키가 설정되지 않았습니다. '{query}'에 대한 Mock 가격 데이터를 생성합니다.")
        # DB에서 해당 브랜드/모델의 딜 평균 가격을 찾아 그 주변 가격으로 Mocking
        db = SessionLocal()
        try:
            # SQLAlchemy 1.4 버전에 최적화된 Python 단에서의 수치 파싱 및 평균 계산 처리 (Dialect 호환성 확보)
            deals = db.query(Deal.price).filter(Deal.brand == brand, Deal.model_code == model_code).all()
            valid_prices = []
            for d in deals:
                if d.price:
                    digits = ''.join(c for c in d.price if c.isdigit())
                    if digits:
                        valid_prices.append(int(digits))
            
            avg_deal_price = sum(valid_prices) / len(valid_prices) if valid_prices else None
            
            base_price = int(avg_deal_price) if avg_deal_price else random.randint(50000, 300000)
            # 시장 최저가는 핫딜가 근처나 조금 더 높은 가격대에서 횡보하는 경향 반영 (85% ~ 115% 범위의 변동성)
            mock_price = int(base_price * random.uniform(0.85, 1.15))
            return max(1000, mock_price)
        except Exception as e:
            logger.error(f"❌ Mock 가격 생성 중 오류 발생: {e}")
            return random.randint(15000, 150000)
        finally:
            db.close()

    url = "https://openapi.naver.com/v1/search/shop.json"
    headers = {
        "X-Naver-Client-Id": NAVER_CLIENT_ID,
        "X-Naver-Client-Secret": NAVER_CLIENT_SECRET,
        "User-Agent": "InsightDeal-Price-Analyzer/1.0"
    }
    params = {
        "query": query,
        "display": 10,
        "start": 1,
        "sort": "sim",        # 유사도 순으로 가장 부합하는 제품 우선 필터링
        "exclude": "used:cbprice"  # 중고 상품 및 대여 상품 제외하여 정밀도 향상
    }

    try:
        async with httpx.AsyncClient(timeout=8.0) as client:
            response = await client.get(url, headers=headers, params=params)
            
            if response.status_code == 200:
                data = response.json()
                items = data.get("items", [])
                
                if not items:
                    logger.info(f"🔍 [Naver API] '{query}' 검색 결과 없음")
                    return None
                
                # 검색 결과 중 실제 가격이 있고 유사 모델명이 대조되는 항목 중 최저가 판별
                valid_prices = []
                for item in items:
                    lprice_str = item.get("lprice")
                    if lprice_str and lprice_str.isdigit():
                        price = int(lprice_str)
                        if price > 0:
                            valid_prices.append(price)
                
                if valid_prices:
                    lowest = min(valid_prices)
                    logger.info(f"✨ [Naver API] '{query}' 최저가 조회 완료: {lowest:,}원")
                    return lowest
                
                return None
            else:
                logger.error(f"❌ [Naver API] 에러 발생 (Status Code: {response.status_code}): {response.text}")
                return None
                
    except Exception as e:
        logger.error(f"❌ [Naver API] 호출 실패: {e}")
        return None

async def run_naver_price_collection():
    """
    네이버 쇼핑 최저가 수집 정기 배치 메인 로직
    - 최근 30일 이내에 수집된 활성 핫딜 중 brand와 model_code가 온전히 존재하는 유니크한 조합 식별
    - 하루 1회 네이버 검색 API를 통해 최저가 트래킹하여 `naver_price_history` DB 적재
    """
    logger.info("🚀 [Background] 네이버 쇼핑 시장 최저가 정기 추적 배치 가동")
    db = SessionLocal()
    
    try:
        # 1. 대상 상품군 추출: 최근 30일 이내에 적재되었으며 brand, model_code가 유효한 딜 식별
        limit_date = datetime.now() - timedelta(days=30)
        
        # 중복 쿼리를 방지하기 위해 brand와 model_code의 유니크한 쌍 목록 가져오기
        active_products = db.query(Deal.brand, Deal.model_code)\
            .filter(Deal.brand.isnot(None), Deal.brand != "")\
            .filter(Deal.model_code.isnot(None), Deal.model_code != "")\
            .filter(Deal.indexed_at >= limit_date)\
            .group_by(Deal.brand, Deal.model_code)\
            .all()
            
        logger.info(f"📋 분석 대상 유니크 상품군 식별 완료: 총 {len(active_products)}개 품목")
        
        if not active_products:
            logger.info("ℹ️ 최근 30일 이내에 추출된 브랜드 및 모델 코드 딜이 없습니다. 수집을 건너뜁니다.")
            return
 
        today_start = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        today_end = today_start + timedelta(days=1)
        
        success_count = 0
        
        # 네이버 쇼핑 API 초당 호출 속도 제한(QPS) 방어를 위해 순차적으로 슬롯을 두고 처리
        for brand, model_code in active_products:
            try:
                # 2. 오늘 이미 수집된 이력이 있는지 대조 (중복 수집 방지하여 데이터 중복 및 트래픽 방어)
                already_collected = db.query(NaverPriceHistory)\
                    .filter(
                        NaverPriceHistory.brand == brand,
                        NaverPriceHistory.model_code == model_code,
                        NaverPriceHistory.checked_at >= today_start,
                        NaverPriceHistory.checked_at < today_end
                    ).first()
                
                if already_collected:
                    logger.info(f"⏭️ [Skip] '{brand} {model_code}'은 오늘 이미 시장 가격이 수집되었습니다.")
                    continue
                
                # 3. 네이버 쇼핑 실시간 최저가 조회
                lowest_price = await fetch_naver_lowest_price(brand, model_code)
                
                if lowest_price is not None:
                    # 4. NaverPriceHistory 적재
                    history_entry = NaverPriceHistory(
                        brand=brand,
                        model_code=model_code,
                        price=lowest_price,
                        checked_at=datetime.now()
                    )
                    db.add(history_entry)
                    success_count += 1
                    
                    # 과도한 미세 트랜잭션 방지 위해 개별 커밋
                    db.commit()
                
                # 네이버 API 호출 간의 적절한 Delay 부여 (API 속도 제한 준수)
                await asyncio.sleep(0.5)
                
            except Exception as item_err:
                db.rollback()
                logger.error(f"❌ '{brand} {model_code}' 가격 수집 중 개별 오류: {item_err}")
                
        logger.info(f"🎉 네이버 쇼핑 가격 수집 완료! 오늘 수집된 건수: {success_count}건")
        
    except Exception as e:
        logger.error(f"❌ 네이버 쇼핑 스케줄러 전역 크래시 에러: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    # 로컬 수동 작동 테스트용
    async def main():
        await run_naver_price_collection()
    
    asyncio.run(main())
