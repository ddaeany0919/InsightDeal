import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
from backend.database.session import create_db_session
from backend.database.models import Deal, Community

def clean_db():
    with create_db_session() as session:
        # Ppomppu & Quasarzone: less than 20 likes
        deals = session.query(Deal).join(Community).filter(
            Deal.honey_score == 100, 
            Community.name.in_(['ppomppu', 'quasarzone']),
            Deal.like_count < 20
        ).all()
        for p in deals:
            p.honey_score = 99
            if p.ai_summary and "🔥" in p.ai_summary:
                p.ai_summary = p.ai_summary.replace("🔥 [커뮤니티 인기] ", "").replace("🔥 [커뮤니티 인증 핫딜] ", "").replace("🔥", "").strip()
        
        session.commit()
        print(f"Cleaned {len(deals)} Ppomppu/Quasarzone deals.")

clean_db()
