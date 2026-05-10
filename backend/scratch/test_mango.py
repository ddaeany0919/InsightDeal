import asyncio
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.services.aggregator_service import AggregatorService
from backend.database.session import SessionLocal

async def run():
    db = SessionLocal()
    agg_service = AggregatorService(db)
    
    async with PpomppuScraper(community_id=12) as scraper:
        url = 'https://m.ppomppu.co.kr/new/bbs_view.php?id=ppomppu8&page=1&divpage=15&no=87695'
        # The scraper needs the list page items usually, but we can mock it
        mock_deal = {
            'title': '남독마이 태국망고 대과 2.5kg (18,429원) 제스프리 골드키위 슈퍼점보과 3.4kg',
            'url': url,
            'price': '18,429',
            'image_url': '',
            'like_count': 0,
            'comment_count': 0,
            'view_count': 0,
            'is_closed': False,
            'deal_type': '일반'
        }
        detail = await scraper.get_detail(url)
        if detail:
            mock_deal.update(detail)
            print("Successfully parsed detail!")
            print(f"Content HTML length: {len(detail.get('content_html', ''))}")
            
            result = await agg_service.process_scraped_deal(12, mock_deal)
            print(f"Aggregator returned {result} items")
            if isinstance(result, list):
                for r in result:
                    print(f"ID: {r.id}, Title: {r.title}, Image: {r.image_url}")
            else:
                print(f"ID: {result.id}, Title: {result.title}, Image: {result.image_url}")
        else:
            print("Failed to parse detail")

    db.close()

import sys
if sys.platform == 'win32':
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
asyncio.run(run())