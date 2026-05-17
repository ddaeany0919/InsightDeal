import sys
import os
import json
import re

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from backend.database.session import SessionLocal
from backend.database.models import Deal
from backend.routers.community import build_dsu, get_normalized_base_name

def get_super_clean_title(title):
    # 띄어쓰기, 특수문자, 불용어 모두 날린 가장 원초적인 형태의 문자열
    # 이 문자열이 같은데 클러스터가 다르면 띄어쓰기/특수기호 문제로 못 묶인 것임
    clean = title
    clean = re.sub(r'\s*\([^)]*[가-힣0-9]+(?:원|달러|배송|무배|무료)[^)]*\)\s*$', '', clean).strip()
    clean = re.sub(r'\[[^\]]*\]', '', clean).strip()
    clean = re.sub(r'\d+(?:,\d+)*\s*원', '', clean)
    
    malls = r'(쿠팡|g마켓|지마켓|11번가|십일절|옥션|티몬|위메프|ssg|쓱|이마트|홈플러스|롯데온|하이마트|오늘의집|오하우스|알리|알리익스프레스|테무|큐텐|아마존|무신사|올리브영|네이버|스토어|카카오|메이커스|톡딜|지그재그|에이블리)'
    modifiers = r'(역대급|대박|미친|오늘만|단하루|품절|임박|로켓|스마일|새벽|무료|배송|무배|체감|추가|할인|역대가|특가|쿠폰|카드|핫딜|최저가|이벤트|세트|오픈|런칭|한정|수량|마감|긴급|선착순|단독|예약|할인가|공식|인증|행사|사은품|증정|패키지|초특가|투데이|반짝|게릴라|종결|우주패스|빅스마일|광군제|블프|블랙프라이데이|크리스마스|명절|설날|추석|원|색상\d+종|색상\d+가지|자급제팩|\d+색)'
    
    clean = re.sub(malls, '', clean, flags=re.IGNORECASE)
    clean = re.sub(modifiers, '', clean, flags=re.IGNORECASE)
    
    # 띄어쓰기 특수문자 전면 제거
    super_clean = re.sub(r'[^가-힣a-zA-Z0-9]', '', clean).lower()
    return super_clean

def main():
    db = SessionLocal()
    deals = db.query(Deal).order_by(Deal.indexed_at.desc()).limit(3000).all()
    
    dsu = build_dsu(deals, time_window_days=30)
    
    super_clean_map = {}
    
    for d in deals:
        if not d.title: continue
        sc = get_super_clean_title(d.title)
        if len(sc) < 5: continue # 너무 짧은건 무시
        
        cid = dsu.find(d.id)
        if sc not in super_clean_map:
            super_clean_map[sc] = []
        super_clean_map[sc].append((cid, d))
        
    missed_clusters = []
    
    for sc, items in super_clean_map.items():
        unique_cids = set(cid for cid, d in items)
        if len(unique_cids) > 1:
            # 같은 글자인데 클러스터가 2개 이상으로 쪼개진 경우
            missed_clusters.append({
                "super_clean": sc,
                "deals": [
                    {"id": d.id, "title": d.title, "cluster_id": cid} for cid, d in items
                ]
            })
            
    with open("c:/Users/kth00/StudioProjects/InsightDeal/backend/scratch/missed_clusters.json", "w", encoding="utf-8") as f:
        json.dump(missed_clusters[:50], f, ensure_ascii=False, indent=2)

    db.close()

if __name__ == "__main__":
    main()
