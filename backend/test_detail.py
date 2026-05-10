import asyncio
import httpx
from bs4 import BeautifulSoup

async def main():
    async with httpx.AsyncClient(headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}) as client:
        url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=702688"
        resp = await client.get(url)
        resp.encoding = 'euc-kr'
        html = resp.text
        
        soup = BeautifulSoup(html, 'html.parser')
        
        # print all a tags text and href
        print("All A Tags:")
        for a in soup.select('div.wordfix a'):
            print(f"wordfix A: {a.get('href')}")
            
        for a in soup.select('.sub-top-text-box a'):
            print(f"sub-top A: {a.get('href')}")
            
        # Try finding anywhere that looks like a link to a shop
        for a in soup.find_all('a', href=True):
            href = a['href']
            if 'gmarket.co.kr' in href or '11st.co.kr' in href or 'coupang.com' in href:
                print(f"Shop Link: {href}")
            if 'view_info.php' in href:
                print(f"View Info Link: {href}")

if __name__ == "__main__":
    asyncio.run(main())
