import asyncio
import httpx
import logging
import random
from abc import ABC, abstractmethod
from typing import List, Optional

logger = logging.getLogger(__name__)

class AsyncBaseScraper(ABC):
    """
    🏗️ [비동기 v2.0] 통합 스크래퍼 기본 클래스
    - httpx / asyncio 기반 완벽한 논블로킹 I/O
    - Semaphore 기반 IP 차단 방지 (동시성 제한)
    """

    def __init__(self, platform_name: str, max_concurrent_requests: int = 5):
        self.platform_name = platform_name
        self.semaphore = asyncio.Semaphore(max_concurrent_requests)
        self.client: Optional[httpx.AsyncClient] = None
        self.max_retries = 3

        # 차단 회피용 User-Agent 풀
        self.user_agents = [
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0"
        ]

    async def __aenter__(self):
        # http2 지원으로 통신 속도 극대화
        self.client = httpx.AsyncClient(timeout=20.0, http2=True)
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.client:
            await self.client.aclose()

    def _get_headers(self) -> dict:
        """User-Agent 매번 무작위 변경 (Rotation)"""
        return {
            "User-Agent": random.choice(self.user_agents),
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        }

    async def fetch_html(self, url: str) -> Optional[str]:
        """Retry Logic 포함 비동기 네트워크 페칭 (최대 3회 재시도)"""
        if not self.client:
            raise RuntimeError("Scraper must be used within 'async with' context")

        # 동시성 제한 (Semaphore 락)
        async with self.semaphore:
            for attempt in range(1, self.max_retries + 1):
                try:
                    # 안티 크롤링 우회를 위한 랜덤 딜레이 Jitter (0.5초 ~ 1.5초)
                    await asyncio.sleep(random.uniform(0.5, 1.5))
                    
                    response = await self.client.get(
                        url, 
                        headers=self._get_headers(),
                        follow_redirects=True
                    )
                    response.raise_for_status()
                    return response.text
                except Exception as e:
                    logger.warning(f"[{self.platform_name}] 네트워크 에러 {url} (재시도 {attempt}/{self.max_retries}): {e}")
                    # 지수 백오프 기반 재시도 대기
                    if attempt < self.max_retries:
                        backoff = (2 ** attempt) + random.uniform(0, 1)
                        await asyncio.sleep(backoff)
            
            logger.error(f"❌ [{self.platform_name}] {self.max_retries}회 재시도 실패: {url}")
            return None

    @abstractmethod
    async def parse_list(self, html: str) -> List[dict]:
        """리스트 페이지 파싱 (자식 구현)"""
        pass

    @abstractmethod
    async def get_detail(self, url: str) -> Optional[dict]:
        """상세 페이지 파싱 (자식 구현)"""
        pass