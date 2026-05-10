import asyncio
import httpx
from bs4 import BeautifulSoup

async def run():
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    }
    url = "https://www.fmkorea.com/index.php?mid=hotdeal&listStyle=list"
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, headers=headers)
        soup = BeautifulSoup(resp.text, 'lxml')
        items = soup.select('.bd_tb_lst tbody tr:not(.notice)')
        for item in items[:5]:
            time_td = item.select_one('.time')
            if time_td:
                print("List Time:", time_td.text.strip())
            title_a = item.select_one('.title a.hx')
            if title_a:
                print("Title:", title_a.text.strip())
                detail_url = "https://www.fmkorea.com" + title_a['href']
                detail_resp = await client.get(detail_url, headers=headers)
                dsoup = BeautifulSoup(detail_resp.text, 'lxml')
                date_span = dsoup.select_one('.date.m_no')
                if date_span:
                    print("Detail Time:", date_span.text.strip())
            print("---")

asyncio.run(run())
