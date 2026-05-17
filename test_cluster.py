import sqlite3
import re
from datetime import datetime, timedelta

def normalize_units(text: str) -> str:
    t = text.lower()
    t = re.sub(r'(\d+)\s*\+\s*(\d+)', r'\1 \2', t)
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

def get_normalized_base_name(title):
    if not title: return None
    clean = title
    clean = re.sub(r'\s*\([^)]*[가-힣0-9]+(?:원|달러|배송|무배|무료)[^)]*\)\s*$', '', clean).strip()
    clean = re.sub(r'\[[^\]]*\]', '', clean).strip()
    clean = normalize_units(clean)
    base_str = re.sub(r'[^가-힣a-zA-Z0-9]', ' ', clean)
    malls = r'(쿠팡|g마켓|지마켓|11번가|십일절|옥션|티몬|위메프|ssg|쓱|이마트|홈플러스|롯데온|하이마트|오늘의집|오하우스|알리|알리익스프레스|테무|큐텐|아마존|무신사|올리브영|네이버|스토어|카카오|메이커스|톡딜|지그재그|에이블리)'
    modifiers = r'(역대급|대박|미친|오늘만|단하루|품절|임박|로켓|스마일|새벽|무료|배송|무배|체감|추가|할인|역대가|특가|쿠폰|카드|핫딜|최저가|이벤트|세트|오픈|런칭|한정|수량|마감|긴급|선착순|단독|예약|할인가|공식|인증|행사|사은품|증정|패키지|초특가|투데이|반짝|게릴라|종결|우주패스|빅스마일|광군제|블프|블랙프라이데이|크리스마스|명절|설날|추석|원|색상\d+종|색상\d+가지|자급제팩|\d+색)'
    clean = re.sub(malls, '', base_str, flags=re.IGNORECASE)
    clean = re.sub(modifiers, '', clean, flags=re.IGNORECASE)
    words = clean.split()
    filtered = [w for w in words if len(w) >= 2 or any(c.isdigit() for c in w)]
    if not filtered: return None
    return '_'.join(filtered)

deal_4984_title = "[11번가] 삼성전자 갤럭시S26 256GB, 자급제 (989,880원)"
deal_5856_title = "갤럭시 S26 256GB 자급제 색상4종"

n_4984 = get_normalized_base_name(deal_4984_title)
n_5856 = get_normalized_base_name(deal_5856_title)

w_4984 = set(n_4984.split('_'))
w_5856 = set(n_5856.split('_'))

inter = w_4984.intersection(w_5856)
union = w_4984.union(w_5856)

print("4984 words:", w_4984)
print("5856 words:", w_5856)
print("Intersection:", inter)
print("Union:", union)
print("Score:", len(inter) / len(union))

time_4984 = datetime.fromisoformat('2026-05-10 07:22:00.000000')
time_5856 = datetime.fromisoformat('2026-05-13 12:06:00.000000')
diff_hours = abs((time_5856 - time_4984).total_seconds()) / 3600
print("Time Diff Hours:", diff_hours)
