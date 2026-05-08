import asyncio
from backend.database.session import get_db
from backend.database.models import Deal

def check_db():
    db = next(get_db())
    deals = db.query(Deal).order_by(Deal.id.desc()).limit(15).all()
    for d in deals:
        print(f"[{d.source_community_id}] {d.title}")
        print(f"  AI Summary: {bool(d.ai_summary)}")
        if d.ai_summary:
             print(f"  > {d.ai_summary[:100]}...")
        print(f"  Content HTML Length: {len(d.content_html) if hasattr(d, 'content_html') and d.content_html else 0}")
        if hasattr(d, 'content_html') and d.content_html:
             print(f"  > {d.content_html[:100]}...")
        print("-" * 50)

check_db()
