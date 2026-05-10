# coding=utf-8
import sys, os, asyncio
sys.path.append(os.path.abspath('backend'))
sys.path.append(os.path.abspath('.'))

from backend.services.aggregator_service import AggregatorService
from backend.database.session import SessionLocal

async def run():
    print('Testing AI Splitting Logic directly...')
    db = SessionLocal()
    aggregator = AggregatorService(db)
    
    # We use a fake URL so it doesn't collide with existing deals in the DB
    mock_item = {
        'title': '남독마이 태국망고 대과 2.5kg (18,429원) 제스프리 골드키위 슈퍼점보과 3.4kg (15,000원)',
        'url': 'https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&no=99999999',
        'price': 0,
        'shop_name': '알리',
        'shipping_fee': '무료',
        'is_super_hotdeal': False,
        'posted_at': '2026-05-09T10:00:00Z',
        'view_count': 100,
        'like_count': 0,
        'comment_count': 0,
        'category': '식품',
        'content_html': '<p>맛있는 태국 망고와 골드키위입니다.</p><br>[첨부된 이미지 링크들]: https://example.com/mango.jpg, https://example.com/kiwi.jpg'
    }
    
    # Process directly through aggregator (which will trigger AI)
    try:
        await aggregator.process_scraped_deal(12, mock_item)
        print('Done! DB processed successfully.')
        db.commit()
    except Exception as e:
        db.rollback()
        print('Error processing deal:', e)
    finally:
        db.close()

    # Query the DB to show the user how it split!
    db = SessionLocal()
    from backend.database.models import Deal
    deals = db.query(Deal).filter(Deal.post_link == mock_item['url']).all()
    print(f"\\n--- Split Results ({len(deals)} items) ---")
    for d in deals:
        print(f"Title: {d.title}, Price: {d.price}")
    db.close()

if sys.platform == 'win32':
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
asyncio.run(run())
