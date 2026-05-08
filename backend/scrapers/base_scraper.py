import asyncio
import httpx
import logging
import random
from abc import ABC, abstractmethod
from typing import List, Optional

logger = logging.getLogger(__name__)

from curl_cffi.requests import AsyncSession

class AsyncBaseScraper(ABC):
    """
    🏗️ [비동기 v2.0] 통합 스크래퍼 기본 클래스
    - curl_cffi 기반 완벽한 브라우저(Chrome) 지문 위장 (TLS Fingerprint)
    - Semaphore 기반 IP 차단 방지 (동시성 제한)
    """

    def __init__(self, platform_name: str, max_concurrent_requests: int = 5):
        self.platform_name = platform_name
        self.semaphore = asyncio.Semaphore(max_concurrent_requests)
        self.client: Optional[AsyncSession] = None
        self.max_retries = 3

    async def __aenter__(self):
        # chrome124 위장을 통해 Cloudflare 430/403 우회
        self.client = AsyncSession(impersonate='chrome124', timeout=20.0)
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.client:
            await self.client.close()

    def _get_headers(self) -> dict:
        """User-Agent는 curl_cffi가 자동으로 처리하므로 기본 헤더만 추가"""
        return {
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        }

    from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

    @retry(
        stop=stop_after_attempt(4),
        wait=wait_exponential(multiplier=1, min=2, max=10),
        retry=retry_if_exception_type(Exception),
        reraise=True
    )
    async def fetch_html(self, url: str) -> Optional[str]:
        """Tenacity 기반 비동기 네트워크 페칭 (지수 백오프 적용)"""
        if not self.client:
            raise RuntimeError("Scraper must be used within 'async with' context")

        async with self.semaphore:
            await asyncio.sleep(random.uniform(0.5, 1.5))
            response = await self.client.get(url, headers=self._get_headers())
            
            if response.status_code in [403, 430]:
                logger.warning(f"[{self.platform_name}] 안티봇 차단 감지 (HTTP {response.status_code}) - {url}")
                raise Exception(f"Anti-bot block detected: {response.status_code}")
                
            response.raise_for_status()
            return response.text
    @abstractmethod
    async def parse_list(self, html: str) -> List[dict]:
        """리스트 페이지 파싱 (자식 구현)"""
        pass

    @abstractmethod
    async def get_detail(self, url: str) -> Optional[dict]:
        """상세 페이지 파싱 (자식 구현)"""
        pass

    async def run(self, url: str) -> List[dict]:
        """메인 실행 파이프라인"""
        logger.info(f"▶️ [{self.platform_name}] 핫딜 수집 시작: {url}")
        try:
            html = await self.fetch_html(url)
            if not html:
                return []
        except Exception as e:
            logger.error(f"❌ [{self.platform_name}] fetch_html 실패: {e}")
            return []
        
        try:
            deals = await self.parse_list(html)
            # Validate with Pydantic schema
            from backend.scrapers.schemas import ScrapedDeal
            validated_deals = []
            for d in deals:
                try:
                    # Remove empty strings to let Pydantic handle default values or None
                    cleaned = {k: v for k, v in d.items() if v != ""}
                    ScrapedDeal(**cleaned)
                    validated_deals.append(d)
                except Exception as e:
                    logger.warning(f"[{self.platform_name}] 스키마 검증 실패: {e} | 데이터: {d.get('title')}")
            
            logger.info(f"✅ [{self.platform_name}] 수집 완료: 총 {len(validated_deals)}건 (유효)")
            return validated_deals
        except Exception as e:
            logger.error(f"❌ [{self.platform_name}] 파싱 실패: {e}")
            return []

    @staticmethod
    def parse_time_str(time_str: str) -> Optional[str]:
        """다양한 형식의 시간 문자열을 UTC ISO 8601 형식(시간대 오프셋 제거)으로 변환"""
        import re
        from datetime import datetime, timedelta, timezone
        
        if not time_str: return None
        time_str = time_str.strip()
        kst = timezone(timedelta(hours=9))
        now_kst = datetime.now(kst)
        posted_dt_kst = None
        
        try:
            if "분 전" in time_str or "분전" in time_str:
                match = re.search(r'\d+', time_str)
                if match: posted_dt_kst = now_kst - timedelta(minutes=int(match.group()))
            elif "시간 전" in time_str or "시간전" in time_str:
                match = re.search(r'\d+', time_str)
                if match: posted_dt_kst = now_kst - timedelta(hours=int(match.group()))
            elif "초 전" in time_str or "초전" in time_str:
                match = re.search(r'\d+', time_str)
                if match: posted_dt_kst = now_kst - timedelta(seconds=int(match.group()))
            elif "일 전" in time_str or "일전" in time_str:
                match = re.search(r'\d+', time_str)
                if match: posted_dt_kst = now_kst - timedelta(days=int(match.group()))
            elif ':' in time_str and not ('/' in time_str or '-' in time_str or '.' in time_str):
                # 14:20 or 14:20:10
                parts = time_str.split(':')
                if len(parts) >= 2:
                    h, m = int(parts[0]), int(parts[1])
                    s = int(parts[2]) if len(parts) > 2 else 0
                    posted_dt_kst = now_kst.replace(hour=h, minute=m, second=s, microsecond=0)
                    if posted_dt_kst > now_kst:
                        posted_dt_kst -= timedelta(days=1)
            else:
                # 2024.04.28 or 04.28 or 24/04/28 12:34:56
                date_match = re.search(r'(\d{2,4})[/.-](\d{1,2})[/.-](\d{1,2})(?:\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?', time_str)
                if date_match:
                    y, month, d = int(date_match.group(1)), int(date_match.group(2)), int(date_match.group(3))
                    if y < 100: y += 2000
                    h = int(date_match.group(4)) if date_match.group(4) else 0
                    m_val = int(date_match.group(5)) if date_match.group(5) else 0
                    s_val = int(date_match.group(6)) if date_match.group(6) else 0
                    posted_dt_kst = datetime(y, month, d, h, m_val, s_val, tzinfo=kst)
                    # 만약 미래 시간이면 작년으로 간주
                    if posted_dt_kst and posted_dt_kst > now_kst:
                        posted_dt_kst = posted_dt_kst.replace(year=posted_dt_kst.year - 1)
            
            if posted_dt_kst:
                utc_dt = posted_dt_kst.astimezone(timezone.utc)
                # Android가 완벽히 파싱할 수 있도록 +00:00 대신 timezone info 제거 (yyyy-MM-ddTHH:mm:ss)
                return utc_dt.replace(tzinfo=None).isoformat(timespec='seconds')
        except Exception as e:
            logger.warning(f"시간 파싱 실패 '{time_str}': {e}")
        
        return None