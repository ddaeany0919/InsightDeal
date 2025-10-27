"""
🏗️ BaseScraper - 4개 쇼핑몰 통합 스크래퍼 기본 클래스

국내 최초 4몰 통합 가격 비교를 위한 공통 인터페이스
- 쿠팡, 11번가, G마켓, 옥션 모두 지원
- 에러 처리, 로깅, Rate Limiting 포함
- 사용자 중심: "매일 쓰고 싶은 앱"을 위한 안정성 확보
"""

from abc import ABC, abstractmethod
from typing import Dict, List, Optional, Union
import asyncio
import aiohttp
import time
import logging
from datetime import datetime, timedelta
from dataclasses import dataclass
import re

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

@dataclass
class ProductInfo:
    """🛍️ 상품 정보 표준 데이터 클래스"""
    platform: str           # 플랫폼명 (coupang, eleventh, gmarket, auction)
    product_name: str        # 상품명
    current_price: int       # 현재 가격
    original_price: int      # 원가 (할인 전)
    discount_rate: int       # 할인율 (%)
    product_url: str         # 상품 링크
    image_url: str           # 상품 이미지
    shipping_fee: int        # 배송비 (0이면 무료배송)
    rating: float            # 평점 (0-5)
    review_count: int        # 리뷰 수
    seller_name: str         # 판매자명
    is_available: bool       # 재고 여부
    updated_at: datetime     # 수집 시간
    
@dataclass 
class PriceComparison:
    """📊 4몰 가격 비교 결과"""
    product_name: str
    platforms: Dict[str, ProductInfo]
    lowest_platform: str
    lowest_price: int
    max_saving: int          # 최대 절약 금액
    average_price: int       # 평균 가격
    last_updated: datetime

class BaseScraper(ABC):
    """🏗️ 4몰 통합 스크래퍼 기본 클래스"""
    
    def __init__(self, platform_name: str):
        self.platform_name = platform_name
        self.session = None
        self.last_request_time = 0
        self.min_delay = 1.0  # 최소 요청 간격 (초)
        self.max_retries = 3  # 최대 재시도
        
    async def __aenter__(self):
        """비동기 컨텍스트 매니저 진입"""
        self.session = aiohttp.ClientSession(
            timeout=aiohttp.ClientTimeout(total=30),
            headers={
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36'
            }
        )
        return self
        
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """비동기 컨텍스트 매니저 종료"""
        if self.session:
            await self.session.close()
    
    def _respect_rate_limit(self):
        """⏱️ Rate Limiting 준수"""
        current_time = time.time()
        time_since_last = current_time - self.last_request_time
        if time_since_last < self.min_delay:
            sleep_time = self.min_delay - time_since_last
            logger.info(f"🕐 {self.platform_name} Rate limiting: {sleep_time:.1f}초 대기")
            time.sleep(sleep_time)
        self.last_request_time = time.time()
    
    def _clean_price(self, price_text: str) -> int:
        """💰 가격 텍스트를 정수로 변환"""
        if not price_text:
            return 0
        # 숫자만 추출
        numbers = re.findall(r'\d+', str(price_text).replace(',', ''))
        if numbers:
            return int(''.join(numbers))
        return 0
    
    def _clean_product_name(self, name: str) -> str:
        """🧹 상품명 정리 (브랜드명/모델명 추출)"""
        if not name:
            return ""
        # HTML 태그 제거
        clean_name = re.sub(r'<[^>]+>', '', name)
        # 특수문자 정리
        clean_name = re.sub(r'[^\w\s가-힣]', ' ', clean_name)
        # 공백 정리
        return ' '.join(clean_name.split())
    
    async def _make_request(self, url: str, method: str = 'GET', **kwargs) -> Optional[Union[str, dict]]:
        """🌐 HTTP 요청 (재시도 + 에러 처리)"""
        self._respect_rate_limit()
        
        for attempt in range(self.max_retries):
            try:
                if method.upper() == 'GET':
                    async with self.session.get(url, **kwargs) as response:
                        if response.status == 200:
                            return await response.text()
                        else:
                            logger.warning(f"⚠️ {self.platform_name} HTTP {response.status}: {url}")
                            
                elif method.upper() == 'POST':
                    async with self.session.post(url, **kwargs) as response:
                        if response.status == 200:
                            return await response.json()
                            
            except Exception as e:
                logger.error(f"❌ {self.platform_name} 요청 실패 (시도 {attempt+1}/{self.max_retries}): {e}")
                if attempt < self.max_retries - 1:
                    await asyncio.sleep(2 ** attempt)  # 지수 백오프
                    
        return None
    
    @abstractmethod
    async def search_product(self, product_name: str, limit: int = 10) -> List[ProductInfo]:
        """🔍 상품 검색 (추상 메소드)"""
        pass
    
    @abstractmethod  
    async def get_product_detail(self, product_url: str) -> Optional[ProductInfo]:
        """📋 상품 상세 정보 (추상 메소드)"""
        pass
    
    async def get_price_only(self, product_url: str) -> Optional[int]:
        """💰 가격만 빠르게 조회"""
        try:
            product = await self.get_product_detail(product_url)
            return product.current_price if product else None
        except Exception as e:
            logger.error(f"❌ {self.platform_name} 가격 조회 실패: {e}")
            return None
    
    def is_valid_url(self, url: str) -> bool:
        """✅ 플랫폼별 URL 유효성 검증"""
        platform_domains = {
            'coupang': 'coupang.com',
            'eleventh': '11st.co.kr', 
            'gmarket': 'gmarket.co.kr',
            'auction': 'auction.co.kr'
        }
        domain = platform_domains.get(self.platform_name.lower())
        return domain in url if domain else False

class PriceComparisonEngine:
    """📊 4몰 가격 비교 엔진"""
    
    def __init__(self):
        self.scrapers = {}
        self.cache = {}  # 단순 메모리 캐시 (5분)
        self.cache_duration = 300  # 5분
    
    def register_scraper(self, platform: str, scraper: BaseScraper):
        """🔧 스크래퍼 등록"""
        self.scrapers[platform] = scraper
        logger.info(f"✅ {platform} 스크래퍼 등록 완료")
    
    def _get_cache_key(self, product_name: str) -> str:
        """🗝️ 캐시 키 생성"""
        return f"compare_{product_name.lower().replace(' ', '_')}"
    
    def _is_cache_valid(self, cache_key: str) -> bool:
        """⏰ 캐시 유효성 검사"""
        if cache_key not in self.cache:
            return False
        cache_time = self.cache[cache_key]['timestamp']
        return (datetime.now() - cache_time).seconds < self.cache_duration
    
    async def compare_prices(self, product_name: str) -> Optional[PriceComparison]:
        """🔥 4몰 가격 비교 실행"""
        
        # 캐시 확인
        cache_key = self._get_cache_key(product_name)
        if self._is_cache_valid(cache_key):
            logger.info(f"💨 캐시에서 반환: {product_name}")
            return self.cache[cache_key]['data']
        
        logger.info(f"🔍 {product_name} 4몰 가격 비교 시작...")
        
        # 모든 플랫폼에서 동시 검색
        tasks = []
        for platform, scraper in self.scrapers.items():
            task = self._search_single_platform(platform, scraper, product_name)
            tasks.append(task)
        
        # 동시 실행 (병렬 처리)
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        # 결과 정리
        platforms = {}
        for i, result in enumerate(results):
            platform = list(self.scrapers.keys())[i]
            if isinstance(result, Exception):
                logger.error(f"❌ {platform} 검색 실패: {result}")
                continue
            if result:
                platforms[platform] = result
        
        if not platforms:
            logger.warning(f"⚠️ {product_name} 모든 플랫폼에서 검색 실패")
            return None
        
        # 최저가 계산
        prices = {p: info.current_price for p, info in platforms.items() if info.current_price > 0}
        if not prices:
            return None
            
        lowest_platform = min(prices, key=prices.get)
        lowest_price = prices[lowest_platform]
        max_price = max(prices.values())
        max_saving = max_price - lowest_price
        average_price = sum(prices.values()) // len(prices)
        
        comparison = PriceComparison(
            product_name=product_name,
            platforms=platforms,
            lowest_platform=lowest_platform,
            lowest_price=lowest_price,
            max_saving=max_saving,
            average_price=average_price,
            last_updated=datetime.now()
        )
        
        # 캐시 저장
        self.cache[cache_key] = {
            'data': comparison,
            'timestamp': datetime.now()
        }
        
        logger.info(f"✅ {product_name} 비교 완료: {lowest_platform} {lowest_price:,}원 (최저)")
        return comparison
    
    async def _search_single_platform(self, platform: str, scraper: BaseScraper, product_name: str) -> Optional[ProductInfo]:
        """🔍 단일 플랫폼 검색"""
        try:
            async with scraper:
                results = await scraper.search_product(product_name, limit=1)
                if results:
                    logger.info(f"✅ {platform}: {results[0].current_price:,}원")
                    return results[0]
                else:
                    logger.warning(f"⚠️ {platform}: 검색 결과 없음")
                    return None
        except Exception as e:
            logger.error(f"❌ {platform} 검색 중 오류: {e}")
            return None

# 전역 비교 엔진 인스턴스
price_engine = PriceComparisonEngine()