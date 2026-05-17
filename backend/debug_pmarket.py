import asyncio
import sys
import codecs
from bs4 import BeautifulSoup
from scrapers.ppomppu_scraper import PpomppuScraper

sys.stdout = codecs.getwriter('utf-8')(sys.stdout.detach())

async def test():
    async with PpomppuScraper(community_id=1) as scraper:
        html = await scraper.fetch_html("https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu")
        soup = BeautifulSoup(html, 'html.parser')
    
    forum_header = soup.find(lambda tag: tag.name == 'tr' and '더 많은 쇼핑 정보와' in tag.get_text())
    if forum_header:
        for sibling in forum_header.find_next_siblings():
            sibling.decompose()
        forum_header.decompose()
        
    rows = soup.select('tr.baseList, tr.list1, tr.list0')
    print(f"Total rows: {len(rows)}")
    
    for row in rows:
        title_el = row.select_one('a.baseList-title') or row.select_one('.list_title') or row.select_one('font')
        if not title_el: continue
        full_title = title_el.get_text(strip=True)
        if "G마켓" in full_title or "pmarket" in str(row):
            print(f"Found suspicious row: {full_title}")
            
            hot_icon = row.select_one('img[src*="hot_icon2.jpg"]')
            print(f"  hot_icon: {hot_icon is not None}")
            
            link_el = title_el if title_el.has_attr('href') else title_el.find_parent('a')
            if not link_el or not link_el.get('href'):
                link_el = row.select_one('a')
            href = link_el.get('href') if link_el else None
            print(f"  href: {href}")

asyncio.run(test())
