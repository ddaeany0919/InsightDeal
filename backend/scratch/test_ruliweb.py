import asyncio
from curl_cffi.requests import AsyncSession
import httpx

async def test():
    print("Testing curl_cffi with chrome124...")
    try:
        async with AsyncSession(impersonate='chrome124', timeout=10.0) as s:
            r = await s.get('https://bbs.ruliweb.com/market/board/1020')
            print("curl_cffi status:", r.status_code)
    except Exception as e:
        print("curl_cffi error:", e)
        
    print("\nTesting httpx...")
    try:
        async with httpx.AsyncClient(timeout=10.0) as c:
            r = await c.get('https://bbs.ruliweb.com/market/board/1020', headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"})
            print("httpx status:", r.status_code)
    except Exception as e:
        print("httpx error:", e)

asyncio.run(test())
