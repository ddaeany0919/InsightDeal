import asyncio
import bs4
from scrapers.fmkorea_scraper import FmkoreaScraper

async def main():
    s = FmkoreaScraper(3)
    html = await s.fetch_html('https://www.fmkorea.com/index.php?mid=hotdeal&document_srl=9800579200')
    soup = bs4.BeautifulSoup(html, 'html.parser')
    date_el = soup.select_one('.date')
    if date_el:
        print("DATE:", date_el.get_text())
    else:
        print("NO .date found")
        
    for el in soup.select('span.date, div.date, span.time, div.time, .regdate, span.time_m'):
        print("FOUND:", el.name, el.get('class'), el.get_text())
        
if __name__ == '__main__':
    asyncio.run(main())
