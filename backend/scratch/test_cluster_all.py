# -*- coding: utf-8 -*-
"""DB 전체 카테고리 클러스터링 검증 - 최근 7일치 딜 샘플"""
import re, sys, os
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from database.session import SessionLocal
from database import models
from datetime import datetime, timedelta
from collections import defaultdict

def normalize_units(text: str) -> str:
    t = text.lower()
    t = re.sub(r'(\d+)\s*\+\s*(\d+)', r'\1 \2', t)  # 8+128 → 8 128
    t = re.sub(r'(\d+)\s*기가바이트', r'\1gb', t)
    t = re.sub(r'(\d+)\s*기가', r'\1gb', t)
    t = re.sub(r'(\d+)\s*tb', r'\1000gb', t)
    t = re.sub(r'(\d+)\s*gb', r'\1gb', t)
    t = re.sub(r'(\d+)\s*mb', r'\1mb', t)
    t = re.sub(r'(\d+)\s*킬로그램', r'\1kg', t)
    t = re.sub(r'(\d+)\s*그램', r'\1g', t)
    t = re.sub(r'갤럭시\s*', '', t)
    t = re.sub(r'galaxy\s*', '', t)
    t = re.sub(r'그레이', 'gray', t)
    t = re.sub(r'블랙', 'black', t)
    t = re.sub(r'화이트', 'white', t)
    t = re.sub(r'골드', 'gold', t)
    t = re.sub(r'([a-zA-Z])\+', r'\1plus', t)
    return t

malls = r'(쿠팡|g마켓|지마켓|11번가|십일절|옥션|티몬|위메프|ssg|쓱|이마트|홈플러스|롯데온|하이마트|오늘의집|오하우스|알리|알리익스프레스|테무|큐텐|아마존|무신사|올리브영|네이버|스토어|카카오|메이커스|톡딜|지그재그|에이블리)'
modifiers = r'(역대급|대박|미친|오늘만|단하루|품절|임박|로켓|스마일|새벽|무료|배송|무배|체감|추가|할인|역대가|특가|쿠폰|카드|핫딜|최저가|이벤트|세트|오픈|런칭|한정|수량|마감|긴급|선착순|단독|예약|할인가|공식|인증|행사|사은품|증정|패키지|초특가|투데이|반짝|게릴라|종결|원|색상\d+종|색상\d+가지|\d+색)'

def get_words(title):
    clean = re.sub(r'\s*\([^)]*[가-힣0-9]+(?:원|달러|배송|무배|무료)[^)]*\)\s*$', '', title).strip()
    clean = re.sub(r'\[[^\]]*\]', '', clean).strip()
    clean = normalize_units(clean)
    base_str = re.sub(r'[^가-힣a-zA-Z0-9]', ' ', clean)
    clean2 = re.sub(malls, '', base_str, flags=re.IGNORECASE)
    clean2 = re.sub(modifiers, '', clean2, flags=re.IGNORECASE)
    words = clean2.split()
    return set(w for w in words if len(w) >= 2 or any(c.isdigit() for c in w))

def jaccard(a, b):
    inter = len(a & b)
    union = len(a | b)
    return inter / union if union > 0 else 0

db = SessionLocal()
time_limit = datetime.utcnow() - timedelta(days=7)

# 전체 딜 300개 샘플
deals = db.query(models.Deal).filter(
    models.Deal.indexed_at >= time_limit,
    models.Deal.price != None,
    models.Deal.price != '',
    models.Deal.price != '0'
).order_by(models.Deal.indexed_at.desc()).limit(300).all()

print(f"최근 7일 딜 샘플: {len(deals)}개\n")

# 커뮤니티별 분포
comm_count = defaultdict(int)
for d in deals:
    comm_count[d.community.name if d.community else '?'] += 1
print("=== 커뮤니티별 딜 수 ===")
for comm, cnt in sorted(comm_count.items(), key=lambda x: -x[1]):
    print(f"  {comm}: {cnt}개")

# 교차 Jaccard로 잠재 동일상품 쌍 찾기
print("\n=== 잠재적 동일 상품 클러스터 (Jaccard >= 0.5, 다른 커뮤니티) ===\n")
deal_data = []
for d in deals:
    ws = get_words(d.title)
    comm = d.community.name if d.community else '?'
    deal_data.append((d.id, d.title, comm, ws, d.price))

matched_pairs = []
for i, (id_a, title_a, comm_a, ws_a, price_a) in enumerate(deal_data):
    for id_b, title_b, comm_b, ws_b, price_b in deal_data[i+1:]:
        if comm_a == comm_b:  # 같은 커뮤니티는 건너뜀
            continue
        if len(ws_a) < 2 or len(ws_b) < 2:
            continue
        j = jaccard(ws_a, ws_b)
        if j >= 0.5:
            matched_pairs.append((j, comm_a, title_a, price_a, comm_b, title_b, price_b))

matched_pairs.sort(key=lambda x: -x[0])
print(f"총 {len(matched_pairs)}쌍 발견\n")

if matched_pairs:
    for j, ca, ta, pa, cb, tb, pb in matched_pairs[:20]:
        print(f"  ✅ Jaccard={j:.2f}")
        print(f"     [{ca}] {ta!r}  ({pa}원)")
        print(f"     [{cb}] {tb!r}  ({pb}원)")
        print()

# 동일 커뮤니티 내 중복 가능성도 확인
print("=== 동일 커뮤니티 내 유사 딜 (중복 수집 의심) ===\n")
same_comm_pairs = []
for i, (id_a, title_a, comm_a, ws_a, price_a) in enumerate(deal_data):
    for id_b, title_b, comm_b, ws_b, price_b in deal_data[i+1:]:
        if comm_a != comm_b or id_a == id_b:
            continue
        if len(ws_a) < 2 or len(ws_b) < 2:
            continue
        j = jaccard(ws_a, ws_b)
        if j >= 0.7 and title_a != title_b:
            same_comm_pairs.append((j, comm_a, title_a, price_a, title_b, price_b))

same_comm_pairs.sort(key=lambda x: -x[0])
print(f"총 {len(same_comm_pairs)}쌍\n")
for j, ca, ta, pa, tb, pb in same_comm_pairs[:10]:
    print(f"  ⚠️ Jaccard={j:.2f} [{ca}]")
    print(f"     A: {ta!r} ({pa}원)")
    print(f"     B: {tb!r} ({pb}원)")
    print()

db.close()
