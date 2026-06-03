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
    async def fetch_html(self, url: str, headers: Optional[dict] = None) -> Optional[str]:
        """비동기 네트워크 페칭 (동적 브라우저 지문 로테이션 및 재시도 적용)"""
        if not self.client:
            raise RuntimeError("Scraper must be used within 'async with' context")

        impersonates = ['chrome120', 'chrome116', 'chrome110', 'edge101']
        req_headers = headers if headers is not None else self._get_headers()
        
        async with self.semaphore:
            for attempt, imp in enumerate(impersonates):
                try:
                    await asyncio.sleep(random.uniform(0.8, 2.0))
                    
                    # 430/403 재시도 시 세션 재빌드
                    if attempt > 0:
                        logger.info(f"🔄 [{self.platform_name}] 지문 로테이션 재시도 ({imp}) - {url}")
                        await self.client.close()
                        self.client = AsyncSession(impersonate=imp, timeout=20.0)
                        
                        # 지문이 Chrome이 아닐 경우 Chrome 특화 Client Hints 헤더 제거하여 지문 불일치(Mismatch) 방지
                        if 'chrome' not in imp:
                            req_headers = req_headers.copy()
                            req_headers.pop("Sec-Ch-Ua", None)
                            req_headers.pop("Sec-Ch-Ua-Mobile", None)
                            req_headers.pop("Sec-Ch-Ua-Platform", None)
                        
                        # 펨코의 경우 쿠키 웜업 다시 진행
                        if "fmkorea.com" in url or "fmkorea.org" in url:
                            try:
                                await self.client.get("https://www.fmkorea.com/", headers=req_headers, timeout=5.0)
                            except: pass
                            
                    response = await self.client.get(url, headers=req_headers)
                    
                    if response.status_code in [403, 430]:
                        logger.warning(f"[{self.platform_name}] 안티봇 차단 감지 (HTTP {response.status_code}) [시도 {attempt+1}/{len(impersonates)}] - {url}")
                        if attempt == len(impersonates) - 1:
                            raise Exception(f"Anti-bot block detected after all retries: {response.status_code}")
                        continue
                        
                    response.raise_for_status()
                    return response.text
                except Exception as e:
                    if attempt == len(impersonates) - 1:
                        raise e
                    logger.warning(f"⚠️ [{self.platform_name}] 요청 오류 (시도 {attempt+1}): {e}. 다음 지문으로 재시도합니다.")
                    await asyncio.sleep(2 ** attempt)
            return None

    async def fetch_og_image(self, url: str) -> Optional[str]:
        """외부 쇼핑몰 URL의 og:image 메타 태그를 파싱하여 고화질 대표 이미지 획득"""
        if not url or not url.startswith('http'):
            return None

        # 1. 지마켓(Gmarket) 안티봇 차단 원천 우회를 위한 초고속 CDN 이미지 조립 룰 적용
        if 'gmarket.co.kr' in url:
            import re
            match = re.search(r'goodscode=([0-9]+)', url)
            if match:
                g_id = match.group(1)
                return f"https://gdimg.gmarket.co.kr/{g_id}/still/600"

        # 뽐뿌/펨코 내부용 도메인은 외부 쇼핑몰이 아니므로 배제
        if any(x in url for x in ['ppomppu.co.kr', 'fmkorea.com', 'fmkorea.org', 'quasarzone.com', 'clien.net', 'ruliweb.com', 'bbasak.com']):
            return None
            
        try:
            if not self.client:
                return None
            
            logger.info(f"🔍 [og:image] 외부 쇼핑몰 og:image 탐색 시도: {url}")
            # 외부 링크이므로 딜레이 최소화 및 빠른 타임아웃(3초) 설정
            response = await self.client.get(url, timeout=3.0, headers={
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            })
            if response.status_code == 200:
                from bs4 import BeautifulSoup
                soup = BeautifulSoup(response.text, 'html.parser')
                meta_img = soup.find('meta', property='og:image') or soup.find('meta', attrs={'name': 'twitter:image'}) or soup.find('meta', itemprop='image')
                if meta_img and meta_img.get('content'):
                    img_url = meta_img['content'].strip()
                    if img_url.startswith('//'):
                        img_url = 'https:' + img_url
                    return img_url
        except Exception as e:
            logger.warning(f"⚠️ [og:image] 외부 쇼핑몰 이미지 파싱 실패 ({url}): {e}")
        return None

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
            # 분 전, 시간 전, 방금 전 등의 상대 시간 처리
            if '분 전' in time_str or '분전' in time_str:
                match = re.search(r'(\d+)\s*분', time_str)
                if match:
                    posted_dt_kst = now_kst - timedelta(minutes=int(match.group(1)))
            elif '시간 전' in time_str or '시간전' in time_str:
                match = re.search(r'(\d+)\s*시간', time_str)
                if match:
                    posted_dt_kst = now_kst - timedelta(hours=int(match.group(1)))
            elif '방금' in time_str:
                posted_dt_kst = now_kst
            elif ':' in time_str and not ('/' in time_str or '-' in time_str or '.' in time_str):
                # 14:20 or 14:20:10
                parts = time_str.split(':')
                if len(parts) >= 2:
                    h, m = int(parts[0]), int(parts[1])
                    s = int(parts[2]) if len(parts) > 2 else 0
                    posted_dt_kst = now_kst.replace(hour=h, minute=m, second=s, microsecond=0)
                    # 만약 미래 시간이면 작년/어제로 간주
                    if posted_dt_kst > now_kst + timedelta(hours=1):
                        posted_dt_kst -= timedelta(days=1)
            else:
                # 2024.04.28 or 24/04/28 12:34:56 (세 개의 날짜 세그먼트)
                date_match_3 = re.search(r'(\d{2,4})[/.-](\d{1,2})[/.-](\d{1,2})(?:\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?', time_str)
                # 06.02 or 06/02 12:34:56 (두 개의 날짜 세그먼트, 연도 생략)
                date_match_2 = re.search(r'(?<!\d)(\d{1,2})[/.-](\d{1,2})(?:\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?', time_str)
                
                if date_match_3:
                    y_str = date_match_3.group(1)
                    y = int(y_str)
                    if y < 100: y += 2000
                    # 혹시 파싱 오류로 2012년 등이 되면 2024년으로 자동 교정 (안전망)
                    if y < 2020: y = now_kst.year
                    
                    month, d = int(date_match_3.group(2)), int(date_match_3.group(3))
                    h = int(date_match_3.group(4)) if date_match_3.group(4) else 0
                    m_val = int(date_match_3.group(5)) if date_match_3.group(5) else 0
                    s_val = int(date_match_3.group(6)) if date_match_3.group(6) else 0
                    
                    if 1 <= month <= 12 and 1 <= d <= 31:
                        try:
                            posted_dt_kst = datetime(y, month, d, h, m_val, s_val, tzinfo=kst)
                            if posted_dt_kst and posted_dt_kst > now_kst + timedelta(hours=1):
                                posted_dt_kst = posted_dt_kst.replace(year=posted_dt_kst.year - 1)
                        except ValueError:
                            pass
                elif date_match_2:
                    y = now_kst.year
                    month, d = int(date_match_2.group(1)), int(date_match_2.group(2))
                    h = int(date_match_2.group(3)) if date_match_2.group(3) else 0
                    m_val = int(date_match_2.group(4)) if date_match_2.group(4) else 0
                    s_val = int(date_match_2.group(5)) if date_match_2.group(5) else 0
                    
                    if 1 <= month <= 12 and 1 <= d <= 31:
                        try:
                            posted_dt_kst = datetime(y, month, d, h, m_val, s_val, tzinfo=kst)
                            if posted_dt_kst and posted_dt_kst > now_kst + timedelta(hours=1):
                                posted_dt_kst = posted_dt_kst.replace(year=posted_dt_kst.year - 1)
                        except ValueError:
                            pass
                else:
                    # 한국어 날짜 형식 지원: "6월 1일" 또는 "2024년 6월 1일 15:30"
                    kor_date_match = re.search(r'(?:(\d{2,4})년\s*)?(\d{1,2})월\s*(\d{1,2})일(?:\s+(\d{1,2})[:시]\s*(\d{1,2})(?:분)?)?', time_str)
                    if kor_date_match:
                        y_str = kor_date_match.group(1)
                        if y_str:
                            y = int(y_str)
                            if y < 100: y += 2000
                        else:
                            y = now_kst.year
                        
                        month = int(kor_date_match.group(2))
                        d = int(kor_date_match.group(3))
                        h = int(kor_date_match.group(4)) if kor_date_match.group(4) else 0
                        m_val = int(kor_date_match.group(5)) if kor_date_match.group(5) else 0
                        s_val = 0
                        
                        if 1 <= month <= 12 and 1 <= d <= 31:
                            try:
                                posted_dt_kst = datetime(y, month, d, h, m_val, s_val, tzinfo=kst)
                                if posted_dt_kst and posted_dt_kst > now_kst + timedelta(hours=1):
                                    posted_dt_kst = posted_dt_kst.replace(year=posted_dt_kst.year - 1)
                            except ValueError:
                                pass
            
            if posted_dt_kst:
                utc_dt = posted_dt_kst.astimezone(timezone.utc)
                # Android가 완벽히 파싱할 수 있도록 Z 부착
                return utc_dt.replace(tzinfo=None).isoformat(timespec='seconds') + "Z"
        except Exception as e:
            logger.warning(f"시간 파싱 실패 '{time_str}': {e}")
        
        return None