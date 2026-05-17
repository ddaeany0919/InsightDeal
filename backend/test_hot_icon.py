import asyncio
import httpx
from bs4 import BeautifulSoup
import sys
import codecs

sys.stdout = codecs.getwriter('utf-8')(sys.stdout.detach())

async def main():
    async with httpx.AsyncClient(headers={'User-Agent': 'Mozilla/5.0'}) as c:
        for p in range(1, 4):
            r = await c.get(f'https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu&page={p}')
            soup = BeautifulSoup(r.text, 'html.parser')
            rows = soup.select('tr.baseList, tr.list1, tr.list0')
            hot = [r for r in rows if r.select_one('img[src*="hot_icon2.jpg"]')]
            pop = [r for r in rows if r.select_one('img[src*="pop_icon2.jpg"]')]
            print(f'Page {p}: total {len(rows)}, hot {len(hot)}, pop {len(pop)}')
            for r in hot:
                title = r.select_one('a.baseList-title') or r.select_one('.list_title') or r.select_one('font')
                if title:
                    print(f"  {title.get_text(strip=True)}")

if __name__ == '__main__':
    asyncio.run(main())
