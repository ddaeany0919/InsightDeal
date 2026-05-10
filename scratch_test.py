import asyncio
import sys
sys.path.append('.')

from backend.scrapers.ppomppu_scraper import PpomppuScraper

async def main():
    async with PpomppuScraper(community_id=1) as scraper:
        html = await scraper.fetch_html(scraper.list_url)
        if html:
            items = await scraper.parse_list(html)
            found = False
            for i, item in enumerate(items):
                if '카카오' in item['title'] or '두피' in item['title'] or '마사지' in item['title']:
                    print(f"[{i}] {item['title']} - {item['price']}원")
                    print(f"배송비 파싱 결과: '{item['shipping_fee']}'")
                    print(f"원문 URL: {item['url']}")
                    print(f"본문 내용 추출부:\n{item['content_html'][:500]}")
                    print("-" * 50)
                    found = True
            
            if not found:
                print("해당 게시물을 첫 페이지에서 찾지 못했습니다.")
                
if __name__ == "__main__":
    asyncio.run(main())
