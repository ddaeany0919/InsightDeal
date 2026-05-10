import sqlite3
import sys

sys.stdout.reconfigure(encoding='utf-8')
conn = sqlite3.connect('backend/insight_deal.db')
c = conn.cursor()

# 빠삭 게시글들의 조회수를 어떻게 알지? 
# view_count 컬럼은 스키마에 없기 때문에 이미 들어간 데이터의 조회수를 정확히 복구하긴 힘들 수 있습니다.
# 하지만 일단 기존 빠삭 100점 게시글들은 일단 모두 60점으로 리셋하는게 안전합니다.
c.execute("""
    UPDATE deals 
    SET honey_score = 60, ai_summary = REPLACE(ai_summary, '🔥 [커뮤니티 인증 핫딜] ', '')
    WHERE honey_score = 100 AND source_community_id IN (
        SELECT id FROM communities WHERE name LIKE 'bbasak%'
    )
""")

print(f"Updated {c.rowcount} Bbasak deals")

# 클리앙 게시글 중에서 조회수로만 100점을 받은 기존 데이터들도 60점으로 리셋
c.execute("""
    UPDATE deals 
    SET honey_score = 60, ai_summary = REPLACE(ai_summary, '🔥 [커뮤니티 인증 핫딜] ', '')
    WHERE honey_score = 100 AND source_community_id = (
        SELECT id FROM communities WHERE name = 'clien'
    )
""")

print(f"Updated {c.rowcount} Clien deals")

conn.commit()
conn.close()
