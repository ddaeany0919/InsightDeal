import asyncio
import httpx
import logging
import random
from abc import ABC, abstractmethod
from typing import List, Optional, Any
from datetime import datetime

logger = logging.getLogger(__name__)

class AsyncBaseScraper(ABC):
    """
    🏗️ AsyncBaseScraper v2.0 - 비동기 고성능 스크래퍼 기반 뼈대
    
    특징:
    - httpx.AsyncClient 활용한 완전한 논블로킹 I/O
    - asyncio.Semaphore를 활용한 동시성 제한 (IP 차단 방지)
    - Jitter(랜덤 딜레이)를 통한 안티 크롤링 우회
    - 에러 발생 시 재시도 로직(Retry) 및 로깅 내장
    """

    def __init__(
        self, 
        platform_name: str, 
        base_url: str, 
        max_concurrent_requests: int = 5,
        max_retries: int = 3
    ):
        self.platform_name = platform_name
        self.base_url = base_url
        self.max_retries = max_retries
        self.semaphore = asyncio.Semaphore(max_concurrent_requests)
        self.client: Optional[httpx.AsyncClient] = None
        
        # User-Agent 풀을 두어 차단 회피
        self.user_agents = [
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0"
        ]

    async def __aenter__(self):
        """async with 구문을 위한 컨텍스트 매니저 진입"""
        # HTTP/2 지원 가능하도록 설정 (필요시 활성화)
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(20.0), 
            limits=httpx.Limits(max_keepalive_connections=5, max_connections=10),
            http2=True
        )
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """컨텍스트 매니저 종료 시 리소스 반환"""
        if self.client:
            await self.client.aclose()

    def _get_random_headers(self) -> dict:
        return {
            "User-Agent": random.choice(self.user_agents),
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language": "ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3",
            "Connection": "keep-alive",
            "Upgrade-Insecure-Requests": "1"
        }

    async def fetch(self, url: str, method: str = 'GET', **kwargs) -> Optional[str]:
        """네트워크 요청 처리기 (세마포어 + 백오프 재시도 포함)"""
        if not self.client:
            raise RuntimeError("Scraper must be used within an 'async with' context block")

        async with self.semaphore:  # 동시성 제한
            for attempt in range(1, self.max_retries + 1):
                try:
                    # 요청 사이에 짧은 Jitter(랜덤 딜레이) 추가하여 봇 탐지 회피
                    await asyncio.sleep(random.uniform(0.5, 1.5))
                    
                    headers = self._get_random_headers()
                    if 'headers' in kwargs:
                        headers.update(kwargs.pop('headers'))

                    response = await self.client.request(
                        method=method, 
                        url=url, 
                        headers=headers,
                        follow_redirects=True,
                        **kwargs
                    )
                    
                    response.raise_for_status()  # 200번대가 아니면 예외 발생
                    return response.text

                except httpx.HTTPStatusError as e:
                    logger.warning(f"[{self.platform_name}] HTTP Error {e.response.status_code} on {url} (Attempt {attempt}/{self.max_retries})")
                except httpx.RequestError as e:
                    logger.warning(f"[{self.platform_name}] Network Error: {e} on {url} (Attempt {attempt}/{self.max_retries})")
                
                # 재시도를 위한 백오프 (Backoff)
                if attempt < self.max_retries:
                    backoff_time = (2 ** attempt) + random.uniform(0, 1)
                    logger.info(f"[{self.platform_name}] Retrying in {backoff_time:.2f} seconds...")
                    await asyncio.sleep(backoff_time)
            
            logger.error(f"❌ [{self.platform_name}] Failed to fetch URL after {self.max_retries} attempts: {url}")
            return None

    @abstractmethod
    async def parse_list(self, html: str) -> List[dict]:
        """HOT 게시판 리스트 파싱 로직 (추상 메서드)"""
        pass

    async def run(self, url: str) -> List[dict]:
        """메인 실행 파이프라인"""
        logger.info(f"▶️ [{self.platform_name}] 핫딜 수집 시작: {url}")
        html = await self.fetch(url)
        if not html:
            return []
        
        deals = await self.parse_list(html)
        logger.info(f"✅ [{self.platform_name}] 수집 완료: 총 {len(deals)}건 추출")
        return deals
