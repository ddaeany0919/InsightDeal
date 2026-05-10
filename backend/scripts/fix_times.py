import asyncio
from datetime import datetime, timezone
from backend.database.session import SessionLocal
from backend.database.models import Deal, Community
from backend.scrapers.fmkorea_scraper import FmkoreaScraper

async def fix_fmkorea_times():
    db = SessionLocal()
    comm = db.query(Community).filter(Community.name == 'fmkorea').first()
    # Get recent 30 deals that are valid hotdeals
    deals = db.query(Deal).filter(Deal.source_community_id == comm.id, Deal.post_link.like('%hotdeal%')).order_by(Deal.id.desc()).limit(30).all()
    
    async with FmkoreaScraper(community_id=comm.id) as scraper:
        for deal in deals:
            if not deal.post_link:
                continue
            
            detail = await scraper.get_detail(deal.post_link)
            if not detail:
                print(f'[{deal.id}] {deal.title[:15]} - detail fetch failed')
                continue
                
            posted_at_iso = detail.get('posted_at')
            if posted_at_iso:
                try:
                    posted_dt = datetime.fromisoformat(posted_at_iso)
                    if posted_dt.tzinfo is None:
                        posted_dt = posted_dt.replace(tzinfo=timezone.utc)
                    
                    old_time = deal.indexed_at
                    if old_time.tzinfo is None:
                        old_time = old_time.replace(tzinfo=timezone.utc)
                    
                    if old_time != posted_dt:
                        deal.indexed_at = posted_dt
                        print(f'[{deal.id}] {deal.title[:15]} : {old_time.strftime("%H:%M:%S")} -> {posted_dt.strftime("%H:%M:%S")}')
                    else:
                        print(f'[{deal.id}] {deal.title[:15]} : No change ({old_time.strftime("%H:%M:%S")})')
                except Exception as e:
                    print(e)
            else:
                print(f'[{deal.id}] {deal.title[:15]} : No posted_at in detail')
                
    db.commit()
    db.close()

if __name__ == '__main__':
    asyncio.run(fix_fmkorea_times())
