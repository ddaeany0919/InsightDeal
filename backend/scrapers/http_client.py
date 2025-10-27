import asyncio
from typing import Optional, Dict, Any

import aiohttp


class HttpClient:
    """
    공통 비동기 HTTP 클라이언트 유틸리티.
    - 기본 한국어 헤더
    - 타임아웃, 지연(랜덤 지연 가능) 설정
    - 재시도 로직 (간단한 지수 백오프)
    """

    def __init__(
        self,
        timeout: int = 15,
        default_delay: float = 0.2,
        max_retries: int = 3,
        user_agent: str = (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/118.0 Safari/537.36"
        ),
        accept_language: str = "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        extra_headers: Optional[Dict[str, str]] = None,
    ) -> None:
        self.timeout = aiohttp.ClientTimeout(total=timeout)
        self.default_delay = default_delay
        self.max_retries = max_retries
        self.headers = {
            "User-Agent": user_agent,
            "Accept-Language": accept_language,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Connection": "keep-alive",
            "Cache-Control": "no-cache",
            "Pragma": "no-cache",
        }
        if extra_headers:
            self.headers.update(extra_headers)

        self._session: Optional[aiohttp.ClientSession] = None

    async def __aenter__(self):
        self._session = aiohttp.ClientSession(timeout=self.timeout, headers=self.headers)
        return self

    async def __aexit__(self, exc_type, exc, tb):
        if self._session:
            await self._session.close()

    async def _sleep(self, delay: Optional[float] = None):
        await asyncio.sleep(delay if delay is not None else self.default_delay)

    async def _request(self, method: str, url: str, **kwargs) -> aiohttp.ClientResponse:
        assert self._session is not None, "HttpClient session is not initialized. Use 'async with'."

        last_exc: Optional[Exception] = None
        for attempt in range(1, self.max_retries + 1):
            try:
                resp = await self._session.request(method, url, **kwargs)
                if resp.status >= 500:
                    # 서버 측 에러는 재시도
                    last_exc = RuntimeError(f"Server error: {resp.status}")
                else:
                    return resp
            except (aiohttp.ClientError, asyncio.TimeoutError) as e:
                last_exc = e

            # 다음 재시도 전 지수 백오프
            backoff = min(2 ** attempt * 0.1, 2.0)
            await self._sleep(backoff)

        # 모든 시도 실패 시 마지막 예외 throw
        if last_exc:
            raise last_exc
        raise RuntimeError("Unknown HTTP error without exception")

    async def get(self, url: str, params: Optional[Dict[str, Any]] = None, delay: Optional[float] = None, **kwargs) -> str:
        await self._sleep(delay)
        async with (await self._request("GET", url, params=params, **kwargs)) as resp:
            return await resp.text()

    async def get_bytes(self, url: str, params: Optional[Dict[str, Any]] = None, delay: Optional[float] = None, **kwargs) -> bytes:
        await self._sleep(delay)
        async with (await self._request("GET", url, params=params, **kwargs)) as resp:
            return await resp.read()

    async def head(self, url: str, delay: Optional[float] = None, **kwargs) -> Dict[str, str]:
        await self._sleep(delay)
        async with (await self._request("HEAD", url, **kwargs)) as resp:
            return dict(resp.headers)


# 사용 예시
# async def main():
#     async with HttpClient() as client:
#         html = await client.get("https://www.ruliweb.com/")
#         print(len(html))
#
# if __name__ == "__main__":
#     asyncio.run(main())
