import asyncio
from bs4 import BeautifulSoup
import httpx

async def main():
    async with httpx.AsyncClient(headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}) as client:
        resp = await client.get("https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu")
        html = resp.text
        
        soup = BeautifulSoup(html, 'html.parser')
        post_rows = soup.select('tr.baseList, tr.list1, tr.list0')
        
        for idx, row in enumerate(post_rows[:10]):
            print(f"--- Row {idx} ---")
            
            # Print text of the entire row
            print(f"Row text length: {len(row.get_text())}")
            
            title_el = row.select_one('a.baseList-title') or row.select_one('.list_title') or row.select_one('font')
            full_title = title_el.get_text(strip=True) if title_el else "None"
            print(f"Title: {full_title}")
            
            link_el = title_el if (title_el and title_el.has_attr('href')) else (title_el.find_parent('a') if title_el else None)
            if not link_el or not link_el.get('href'):
                link_el = row.select_one('a')
            
            href = link_el.get('href') if link_el else 'None'
            print(f"Link: {href}")

if __name__ == "__main__":
    asyncio.run(main())
