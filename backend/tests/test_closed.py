import asyncio
from backend.scrapers.ppomppu_scraper import PpomppuScraper

async def test():
    async with PpomppuScraper(7) as scraper:
        for page in range(1, 10):
            html = await scraper.fetch_html(f'https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu&page={page}')
            from bs4 import BeautifulSoup
            soup = BeautifulSoup(html, 'html.parser')
            rows = soup.select('tr.baseList, tr.list1, tr.list0')
            for row in rows:
                title_el = row.select_one('.baseList-title') or row.select_one('.list_title') or row.select_one('font')
                if not title_el: continue
                
                title_text = title_el.get_text(strip=True)
                
                is_closed = False
                if title_el.select_one('del, s, strike') or row.select_one('img[src*="end_icon"]'):
                    is_closed = True
                    
                for font in row.select('font'):
                    if font.get('color') == '#999999' or font.get('color') == '#cccccc':
                        is_closed = True
                        
                # 뽐뿌는 font 색상이나 종료 아이콘 외에 <span style="color:#999999"> 등도 사용
                for span in row.select('span'):
                    style = span.get('style', '')
                    if '#999' in style or '#ccc' in style:
                        is_closed = True
                        
                # 제목 텍스트에 "종료된"이 있는 경우도 있음
                if '종료' in title_text or '품절' in title_text:
                    is_closed = True

                if is_closed:
                    print('[CLOSED]', title_text)
                    print(row.prettify()[:500])
                    return

asyncio.run(test())
