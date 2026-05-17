import asyncio
from bs4 import BeautifulSoup
from backend.scrapers.ppomppu_scraper import PpomppuScraper

async def fetch_and_print():
    async with PpomppuScraper(1) as scraper:
        html = await scraper.fetch_html(scraper.list_url)
        
        if not html:
            print("Failed to fetch HTML")
            return
            
        soup = BeautifulSoup(html, 'html.parser')
        
        post_rows = soup.select('tr.baseList, tr.list1, tr.list0')
    print(f"Found {len(post_rows)} rows using original selectors")
    
    if len(post_rows) == 0:
        # Try to find what rows are actually there
        all_tr = soup.find_all('tr')
        print(f"Total tr tags: {len(all_tr)}")
        for tr in all_tr[:20]:
            print(f"TR classes: {tr.get('class')}")
            
    for row in post_rows:
        title_el = row.select_one('a.baseList-title') or row.select_one('.list_title') or row.select_one('font')
        if not title_el:
            print("No title_el")
            continue
        full_title = title_el.get_text(strip=True)
        
        hot_icon = row.select_one('img[src*="hot_icon2.jpg"]')
        
        img_srcs = [img.get('src') for img in row.select('img')]
        
        print(f"Title: {full_title}")
        print(f"Has hot_icon2.jpg: {bool(hot_icon)}")
        print(f"Images in row: {img_srcs}")
        print("-" * 50)

if __name__ == '__main__':
    asyncio.run(fetch_and_print())
