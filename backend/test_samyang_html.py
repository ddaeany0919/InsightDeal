import asyncio
import re
from bs4 import BeautifulSoup
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def main():
    scraper = FmkoreaScraper(community_id=2)
    hot_html = await scraper.fetch_html("https://www.fmkorea.com/index.php?mid=hotdeal&sort_index=pop&order_type=desc&listStyle=webzine")
    if hot_html:
        hot_soup = BeautifulSoup(hot_html, 'html.parser')
        for r in hot_soup.select('li.li, div.list_item'):
            title_a = r.select_one('a.hotdeal_var8, a.hotdeal_var8Y, a.title, h3.title a')
            if title_a:
                href = title_a.get('href', '')
                doc_match = re.search(r'document_srl=([0-9]+)', href)
                if doc_match and doc_match.group(1) == '9808117789':
                    is_poten = r.select_one('span.STAR-BEST') is not None
                    print(f"Samyang Ramen Poten status: {is_poten}")
                    print(f"HTML snippet: {r}")

if __name__ == "__main__":
    asyncio.run(main())
