import re

with open('backend/routers/community.py', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Fix get_deal
old_get_deal = """    # 현재 딜과 cluster_key가 동일한 딜들을 모아서 출처(sources) 병합
    cluster_key = get_cluster_key(deal)
    from sqlalchemy import and_
    time_limit = datetime.now() - timedelta(days=7)
    similar_deals = db.query(models.Deal).join(models.Community).filter(
        models.Deal.indexed_at >= time_limit
    ).order_by(models.Deal.price.asc()).all()
    
    site_names = []
    sources = []
    for d in similar_deals:
        if get_cluster_key(d) == cluster_key:"""

new_get_deal = """    # 현재 딜과 cluster_key가 동일한 딜들을 모아서 출처(sources) 병합
    from sqlalchemy import and_
    time_limit = datetime.now() - timedelta(days=7)
    similar_deals = db.query(models.Deal).join(models.Community).filter(
        models.Deal.indexed_at >= time_limit
    ).order_by(models.Deal.price.asc()).all()
    
    if not any(d.id == deal.id for d in similar_deals):
        similar_deals.append(deal)
        
    dsu = build_dsu(similar_deals)
    cluster_key = dsu.find(deal.id)
    
    site_names = []
    sources = []
    for d in similar_deals:
        if dsu.find(d.id) == cluster_key:"""

content = content.replace(old_get_deal, new_get_deal)

# 2. Fix get_normalized_url
old_url = """    url = url.replace("m.gmarket.co.kr", "item.gmarket.co.kr")
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
    match = re.search(r'item/([0-9]+)\.html', url, re.IGNORECASE)
    if match: return f"aliexpress_{match.group(1)}"
    
    return url.split('?')[0]"""

new_url = """    url = url.replace("m.gmarket.co.kr", "item.gmarket.co.kr")
    url = url.replace("m.auction.co.kr", "itempage3.auction.co.kr")
    url = url.replace("m.11st.co.kr", "www.11st.co.kr")
    
    match = re.search(r'goodscode=([a-zA-Z0-9]+)', url, re.IGNORECASE)
    if match: return f"gmarket_{match.group(1)}"
    match = re.search(r'itemno=([a-zA-Z0-9]+)', url, re.IGNORECASE)
    if match: return f"auction_{match.group(1)}"
    
    if "11st.co.kr" in url:
        match = re.search(r'products/([a-zA-Z0-9]+)', url, re.IGNORECASE)
        if match: return f"11st_{match.group(1)}"
        
    if "coupang.com" in url:
        match = re.search(r'products/([0-9]+)', url, re.IGNORECASE)
        if match: return f"coupang_{match.group(1)}"
        
    match = re.search(r'goods/([0-9]+)', url, re.IGNORECASE)
    if match: return f"ohouse_{match.group(1)}"
    match = re.search(r'item/([0-9]+)\.html', url, re.IGNORECASE)
    if match: return f"aliexpress_{match.group(1)}"
    
    return url.split('?')[0]"""

content = content.replace(old_url, new_url)

# 3. Fix get_normalized_base_name
old_base_name = """def get_normalized_base_name(deal) -> str:
    if not deal.base_product_name:
        return None
    base_str = deal.base_product_name
    clean_cluster = re.sub(r'(삼성|samsung|엘지|lg|애플|apple|소니|sony|닌텐도|nintendo|레노버|lenovo|에이수스|아수스|asus|롯데|농심|오리온|특가|무료배송|무배)', '', base_str, flags=re.IGNORECASE)
    clean_cluster = re.sub(r'(통|병|개|박스|팩|매|장|봉|봉지|캔|페트|입)', '', clean_cluster)
    norm_title = re.sub(r'[^가-힣a-zA-Z0-9]', '', clean_cluster).lower()
    if len(norm_title) < 4:
        return None
    if re.fullmatch(r'\d+[gkgmlL]+', norm_title):
        return None
    return norm_title"""

new_base_name = """def get_normalized_base_name(deal) -> str:
    norm_title = None
    if deal.base_product_name:
        base_str = deal.base_product_name
        clean_cluster = re.sub(r'(삼성|samsung|엘지|lg|애플|apple|소니|sony|닌텐도|nintendo|레노버|lenovo|에이수스|아수스|asus|롯데|농심|오리온|특가|무료배송|무배)', '', base_str, flags=re.IGNORECASE)
        clean_cluster = re.sub(r'(통|병|개|박스|팩|매|장|봉|봉지|캔|페트|입)', '', clean_cluster)
        norm = re.sub(r'[^가-힣a-zA-Z0-9]', '', clean_cluster).lower()
        if len(norm) >= 4 and not re.fullmatch(r'\\d+[gkgmlL]+', norm):
            norm_title = norm
            
    if not norm_title:
        base_str = deal.title
        base_str = re.sub(r'\\[.*?\\]|\\(.*?\\)', '', base_str)
        clean_cluster = re.sub(r'(삼성|samsung|엘지|lg|애플|apple|소니|sony|닌텐도|nintendo|레노버|lenovo|에이수스|아수스|asus|롯데|농심|오리온|특가|무료배송|무배)', '', base_str, flags=re.IGNORECASE)
        words = re.findall(r'[가-힣a-zA-Z0-9]{2,}', clean_cluster)
        for w in words:
            norm = re.sub(r'[^가-힣a-zA-Z0-9]', '', w).lower()
            if len(norm) >= 2 and not re.fullmatch(r'\\d+[gkgmlL]+', norm) and not norm.isdigit():
                norm_title = norm
                break
                
    return norm_title"""

content = content.replace(old_base_name, new_base_name)

with open('backend/routers/community.py', 'w', encoding='utf-8') as f:
    f.write(content)
print("done")
