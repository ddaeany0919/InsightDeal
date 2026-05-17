import os

file_path = r"c:\Users\kth00\StudioProjects\InsightDeal\backend\routers\community.py"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

new_functions = """
def get_normalized_base_name(deal):
    if not deal.base_product_name:
        return None
    base_str = deal.base_product_name
    clean_cluster = re.sub(r'(삼성|samsung|엘지|lg|애플|apple|소니|sony|닌텐도|nintendo|레노버|lenovo|에이수스|아수스|asus|롯데|농심|오리온|특가|무료배송|무배)', '', base_str, flags=re.IGNORECASE)
    clean_cluster = re.sub(r'(통|병|개|박스|팩|매|장|봉|봉지|캔|페트|입)', '', clean_cluster)
    norm_title = re.sub(r'[^가-힣a-zA-Z0-9]', '', clean_cluster).lower()
    if len(norm_title) < 4:
        return None
    if re.fullmatch(r'\\d+[gkgmlL]+', norm_title):
        return None
    return norm_title

def get_normalized_url(deal):
    if not getattr(deal, 'ecommerce_link', None):
        return None
    url = deal.ecommerce_link
    url = url.replace("m.gmarket.co.kr", "item.gmarket.co.kr")
    url = url.replace("m.auction.co.kr", "itempage3.auction.co.kr")
    url = url.replace("m.11st.co.kr", "www.11st.co.kr")
    
    match = re.search(r'goodscode=([a-zA-Z0-9]+)', url, re.IGNORECASE)
    if match: return f"gmarket_{match.group(1)}"
    match = re.search(r'itemno=([a-zA-Z0-9]+)', url, re.IGNORECASE)
    if match: return f"auction_{match.group(1)}"
    match = re.search(r'products/([a-zA-Z0-9]+)', url, re.IGNORECASE)
    if match: return f"11st_{match.group(1)}"
    match = re.search(r'products/([0-9]+)', url, re.IGNORECASE)
    if match: return f"coupang_{match.group(1)}"
    match = re.search(r'goods/([0-9]+)', url, re.IGNORECASE)
    if match: return f"ohouse_{match.group(1)}"
    match = re.search(r'item/([0-9]+)\\.html', url, re.IGNORECASE)
    if match: return f"aliexpress_{match.group(1)}"
    
    return url.split('?')[0]

class DSU:
    def __init__(self):
        self.parent = {}
    def find(self, i):
        if self.parent.setdefault(i, i) != i:
            self.parent[i] = self.find(self.parent[i])
        return self.parent[i]
    def union(self, i, j):
        root_i = self.find(i)
        root_j = self.find(j)
        if root_i != root_j:
            self.parent[root_i] = root_j

def build_dsu(deals):
    dsu = DSU()
    name_to_deal = {}
    url_to_deal = {}
    
    for deal in deals:
        dsu.find(deal.id)
        n_name = get_normalized_base_name(deal)
        n_url = get_normalized_url(deal)
        
        if n_name:
            if n_name in name_to_deal:
                dsu.union(deal.id, name_to_deal[n_name])
            name_to_deal[n_name] = deal.id
        
        if n_url:
            if n_url in url_to_deal:
                dsu.union(deal.id, url_to_deal[n_url])
            url_to_deal[n_url] = deal.id
            
    return dsu
"""

# Replace get_cluster_key
old_get_cluster_key = """def get_cluster_key(deal):
    # 1. 우선적으로 AI가 파싱한 base_product_name이 있으면 무조건 이걸 기준으로 클러스터링
    if deal.base_product_name:
        base_str = deal.base_product_name
        clean_cluster = re.sub(r'(삼성|samsung|엘지|lg|애플|apple|소니|sony|닌텐도|nintendo|레노버|lenovo|에이수스|아수스|asus|롯데|농심|오리온|특가|무료배송|무배)', '', base_str, flags=re.IGNORECASE)
        clean_cluster = re.sub(r'(통|병|개|박스|팩|매|장|봉|봉지|캔|페트|입)', '', clean_cluster)
        norm_title = re.sub(r'[^가-힣a-zA-Z0-9]', '', clean_cluster).lower()
        if len(norm_title) > 3:
            return f"base_name:{norm_title}"
            
    # 2. base_product_name이 없거나 너무 짧으면 URL 기반으로 시도
    ecommerce_link = getattr(deal, 'ecommerce_link', None)
    post_link = getattr(deal, 'post_link', None)
    
    if ecommerce_link and ecommerce_link != post_link:
        link = ecommerce_link
        # url 정규화 (m. 모바일 도메인 제거, 파라미터 제거 등)
        link = re.sub(r'^https?://', '', link)
        link = re.sub(r'^www\.', '', link)
        link = re.sub(r'^m\.', '', link)
        link = re.sub(r'[?&](utm_source|utm_medium|utm_campaign|NaPm|nv_pna|sm)=[^&]+', '', link)
        link = link.split('#')[0].rstrip('/')
        if len(link) > 5:
            return f"url:{link}"
            
    # 3. 둘 다 없으면 원본 제목 기반 폴백
    base_str = re.sub(r'\\[.*?\\]|\\(.*?\\)', '', deal.title)
    clean_cluster = re.sub(r'(삼성|samsung|엘지|lg|애플|apple|소니|sony|닌텐도|nintendo|레노버|lenovo|에이수스|아수스|asus|롯데|농심|오리온|특가|무료배송|무배)', '', base_str, flags=re.IGNORECASE)
    clean_cluster = re.sub(r'(통|병|개|박스|팩|매|장|봉|봉지|캔|페트|입)', '', clean_cluster)
    norm_title = re.sub(r'[^가-힣a-zA-Z0-9]', '', clean_cluster).lower()
    if len(norm_title) > 15:
        norm_title = norm_title[:15]
    return f"title:{norm_title}"
"""

content = content.replace(old_get_cluster_key, new_functions)

# 1. get_top_hot_deals
content = content.replace(
"""        COMMUNITY_MAP = {
            "fmkorea": "펨코", "ppomppu": "뽐뿌", "ruliweb": "루리웹",
            "clien": "클리앙", "quasarzone": "퀘이사존", "ali_ppomppu": "알리뽐뿌",
            "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외"
        }

        cluster_map = {}
        grouped_result = []

        for deal in deals:
            comp_name = getattr(deal.community, 'display_name', None) or COMMUNITY_MAP.get(deal.community.name, deal.community.name)
            parsed_price_int = extract_price(deal.price)
            parsed_shipping_fee = extract_shipping_fee(deal.shipping_fee)
            total_price = parsed_price_int + parsed_shipping_fee if parsed_price_int > 0 else 0
            
            cluster_key = get_cluster_key(deal)""",
"""        COMMUNITY_MAP = {
            "fmkorea": "펨코", "ppomppu": "뽐뿌", "ruliweb": "루리웹",
            "clien": "클리앙", "quasarzone": "퀘이사존", "ali_ppomppu": "알리뽐뿌",
            "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외"
        }

        cluster_map = {}
        grouped_result = []
        
        dsu = build_dsu(deals)

        for deal in deals:
            comp_name = getattr(deal.community, 'display_name', None) or COMMUNITY_MAP.get(deal.community.name, deal.community.name)
            parsed_price_int = extract_price(deal.price)
            parsed_shipping_fee = extract_shipping_fee(deal.shipping_fee)
            total_price = parsed_price_int + parsed_shipping_fee if parsed_price_int > 0 else 0
            
            cluster_key = dsu.find(deal.id)""")

# 2. get_hot_deals
content = content.replace(
"""        COMMUNITY_MAP = {
            "fmkorea": "펨코", "ppomppu": "뽐뿌", "ruliweb": "루리웹",
            "clien": "클리앙", "quasarzone": "퀘이사존", "ali_ppomppu": "알리뽐뿌",
            "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외"
        }

        cluster_map = {}
        grouped_result = []

        for deal in deals:
            cluster_key = get_cluster_key(deal)""",
"""        COMMUNITY_MAP = {
            "fmkorea": "펨코", "ppomppu": "뽐뿌", "ruliweb": "루리웹",
            "clien": "클리앙", "quasarzone": "퀘이사존", "ali_ppomppu": "알리뽐뿌",
            "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외"
        }

        cluster_map = {}
        grouped_result = []
        dsu = build_dsu(deals)

        for deal in deals:
            cluster_key = dsu.find(deal.id)""")

# 3. get_deal_detail
content = content.replace(
"""    cluster_key = get_cluster_key(deal)
    cluster_deals = db.query(models.Deal).filter(
        models.Deal.category == deal.category,
        models.Deal.id >= deal.id - 500,
        models.Deal.id <= deal.id + 500
    ).all()
    
    target_deal_ids = []
    for d in cluster_deals:
        if get_cluster_key(d) == cluster_key:
            target_deal_ids.append(d.id)
            
    # 본문 텍스트 생성""",
"""    cluster_deals = db.query(models.Deal).filter(
        models.Deal.category == deal.category,
        models.Deal.id >= deal.id - 500,
        models.Deal.id <= deal.id + 500
    ).all()
    
    if not any(d.id == deal.id for d in cluster_deals):
        cluster_deals.append(deal)
        
    dsu = build_dsu(cluster_deals)
    cluster_key = dsu.find(deal.id)
    
    target_deal_ids = []
    for d in cluster_deals:
        if dsu.find(d.id) == cluster_key:
            target_deal_ids.append(d.id)
            
    # 본문 텍스트 생성""")

content = content.replace(
"""    for d in cluster_deals:
        if get_cluster_key(d) == cluster_key:
            c_name = d.community.display_name if d.community and hasattr(d.community, 'display_name') and d.community.display_name else (COMMUNITY_MAP.get(d.community.name, d.community.name) if d.community else "Unknown")""",
"""    for d in cluster_deals:
        if dsu.find(d.id) == cluster_key:
            c_name = d.community.display_name if d.community and hasattr(d.community, 'display_name') and d.community.display_name else (COMMUNITY_MAP.get(d.community.name, d.community.name) if d.community else "Unknown")""")


# 4. get_deal_history
content = content.replace(
"""@router.get("/deals/{deal_id}/history")
def get_deal_history(deal_id: int, db: Session = Depends(get_db_session)):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    cluster_key = get_cluster_key(deal)
    
    # 7일 내의 동일 클러스터 딜 찾기
    time_limit = datetime.now() - timedelta(days=7)
    recent_deals = db.query(models.Deal).filter(
        models.Deal.indexed_at >= time_limit
    ).all()
    
    cluster_deal_ids = [d.id for d in recent_deals if get_cluster_key(d) == cluster_key]""",
"""@router.get("/deals/{deal_id}/history")
def get_deal_history(deal_id: int, db: Session = Depends(get_db_session)):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    # 7일 내의 동일 클러스터 딜 찾기
    time_limit = datetime.now() - timedelta(days=7)
    recent_deals = db.query(models.Deal).filter(
        models.Deal.indexed_at >= time_limit
    ).all()
    
    if not any(d.id == deal.id for d in recent_deals):
        recent_deals.append(deal)
        
    dsu = build_dsu(recent_deals)
    cluster_key = dsu.find(deal.id)
    
    cluster_deal_ids = [d.id for d in recent_deals if dsu.find(d.id) == cluster_key]""")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("community.py patched successfully.")
