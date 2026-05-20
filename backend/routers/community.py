from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy.orm import Session
from backend.database.session import get_db_session
from fastapi.responses import StreamingResponse
import httpx
from backend.database import models
import logging
import os
import re
import requests
from datetime import datetime, timedelta

import json
from pydantic import BaseModel
from typing import List, Optional

def get_deal_sources(deal, comp_name, parsed_price_int):
    deal_sources = []
    currency = getattr(deal, 'currency', 'KRW') or 'KRW'
    if hasattr(deal, 'options_data') and deal.options_data:
        try:
            options = json.loads(deal.options_data)
            for opt in options:
                deal_sources.append({
                    "site_name": f"{comp_name} - {opt.get('name', '?듭뀡')}",
                    "post_url": deal.post_link or "",
                    "ecommerce_url": deal.ecommerce_link or deal.post_link or "",
                    "price": int(opt.get("price", parsed_price_int)),
                    "currency": currency
                })
        except Exception:
            deal_sources.append({
                "site_name": comp_name, 
                "post_url": deal.post_link or "",
                "ecommerce_url": deal.ecommerce_link or deal.post_link or "",
                "price": parsed_price_int,
                "currency": currency
            })
    else:
        deal_sources.append({
            "site_name": comp_name, 
            "post_url": deal.post_link or "",
            "ecommerce_url": deal.ecommerce_link or deal.post_link or "",
            "price": parsed_price_int,
            "currency": currency
        })
    return deal_sources

router = APIRouter()
logger = logging.getLogger(__name__)

BASE_URL = os.getenv("BASE_URL", "http://10.0.2.2:8000") # ?먮??덉씠???묒냽?⑹쑝濡?留욎땄

@router.get("/scraper-stats")
def get_scraper_stats():
    import os
    stats_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scraper_stats.json")
    if os.path.exists(stats_file):
        try:
            with open(stats_file, 'r', encoding='utf-8') as f:
                return json.load(f)
        except:
            return {}
    return {}

@router.get("/proxy-image")
async def proxy_image(url: str):
    try:
        headers = {'User-Agent': 'Mozilla/5.0'}
        if 'bbasak.com' in url:
            headers['Referer'] = 'https://bbasak.com/'
        elif 'ppomppu.co.kr' in url:
            headers['Referer'] = 'https://www.ppomppu.co.kr/'
            
        # Asynchronously fetch the image and return a StreamingResponse
        client = httpx.AsyncClient(timeout=5.0)
        req = client.build_request("GET", url, headers=headers)
        r = await client.send(req, stream=True)
        
        # Ensure we close the client when the stream is consumed
        async def stream_generator():
            try:
                async for chunk in r.aiter_bytes():
                    yield chunk
            finally:
                await r.aclose()
                await client.aclose()
                
        return StreamingResponse(
            stream_generator(), 
            status_code=r.status_code, 
            media_type=r.headers.get('Content-Type', 'image/jpeg')
        )
    except Exception as e:
        logger.error(f"Image proxy failed: {e}")
        return Response(status_code=404)

def extract_price(price_str):
    if not price_str: return 0
    nums = re.findall(r'\d+', str(price_str))
    if nums: return int(''.join(nums))
    return 0

def extract_shipping_fee(fee_str):
    if not fee_str: return 0
    if '臾대즺' in str(fee_str) or '臾대같' in str(fee_str): return 0
    nums = re.findall(r'\d+', str(fee_str))
    if nums: return int(''.join(nums))
    return 0


def normalize_units(text: str) -> str:
    t = text.lower()
    t = re.sub(r'(\d+)\s*\+\s*(\d+)', r'\1 \2', t)
    t = re.sub(r'(\d+)\s*기가바이트', r'\1gb', t)
    t = re.sub(r'(\d+)\s*기가', r'\1gb', t)
    t = re.sub(r'(\d+)\s*tb', r'\1000gb', t)
    t = re.sub(r'(\d+)\s*gb', r'\1gb', t)
    t = re.sub(r'(\d+\.\d+)\s*kg', lambda m: str(int(float(m.group(1)) * 1000)) + 'g', t)
    t = re.sub(r'(\d+\.\d+)\s*l', lambda m: str(int(float(m.group(1)) * 1000)) + 'ml', t)
    t = re.sub(r'(?<!\d)(\d+)\s*kg', lambda m: str(int(m.group(1)) * 1000) + 'g', t)
    t = re.sub(r'(?<!\d)(\d+)\s*l', lambda m: str(int(m.group(1)) * 1000) + 'ml', t)
    t = re.sub(r'(\d+)\s*킬로그램', r'\1kg', t)
    return t
def get_normalized_base_name(deal):
    if not getattr(deal, 'title', None):
        return None
    clean = deal.title
    clean = re.sub(r'\([^)]*(?:달러|배송|무배|무료|체감|할인|쿠폰|최저가|특가)[^)]*\)', '', clean).strip()
    clean = re.sub(r'\s*\([\d,\s/]+\)\s*$', '', clean).strip()
    clean = re.sub(r'\[[^\]]*\]', '', clean).strip()
    clean = re.sub(r'\d+(?:,\d+)*\s*원', '', clean)
    clean = normalize_units(clean)
    clean = re.sub(r'아이(?:스)?\s*아메리카노', '아이스아메리카노', clean)
    base_str = re.sub(r'[^가-힣a-zA-Z0-9]', ' ', clean)
    malls = r'(쿠팡|g마켓|지마켓|11번가|십일번가|옥션|위메프|티몬|ssg|쓱|이마트|홈플러스|롯데마트|하이마트|오늘의집|집꾸미기|알리|알리익스프레스|테무|큐텐|아마존|무신사|올리브영|에이블리|지그재그|아이디어스)'
    modifiers = r'(역대급|대박|미친|오늘만|단하루|품절|임박|로켓|스마일|새벽|무료|배송|무배|체감|추가|할인|반값|특가|쿠폰|카드|핫딜|최저가|이벤트|세일|오픈|론칭|한정|수량|마감|긴급|선착순|단독|예약|할인가|공식|인증|행사|사은품|증정|패키지|초특가|투데이|반짝|게릴라|종결|우주패스|빅스마일데이|광군제|블프|블랙프라이데이|크리스마스|명절|설날|추석|옵션|행상\d+종|행상\d+가지|재입고|출시|추천|종료)'
    clean = re.sub(malls, '', base_str, flags=re.IGNORECASE)
    clean = re.sub(modifiers, '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\s+', ' ', clean).strip()
    return clean if len(clean) > 1 else base_str.strip()
def get_normalized_url(deal):
    if not getattr(deal, 'ecommerce_link', None):
        return None
    url = deal.ecommerce_link
    
    if "ruliweb.com/link.php" in url:
        import urllib.parse
        match = re.search(r'ol=([^&]+)', url)
        if match:
            url = urllib.parse.unquote(match.group(1))
            
    url = url.replace("m.gmarket.co.kr", "item.gmarket.co.kr")
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
        item_match = re.search(r'itemId=([0-9]+)', url, re.IGNORECASE)
        if match: 
            if item_match:
                return f"coupang_{match.group(1)}_{item_match.group(1)}"
            return f"coupang_{match.group(1)}"
        
    match = re.search(r'goods/([0-9]+)', url, re.IGNORECASE)
    if match: return f"ohouse_{match.group(1)}"
    match = re.search(r'item/([0-9]+)\.html', url, re.IGNORECASE)
    if match: return f"aliexpress_{match.group(1)}"
    
    if "ego.aspx" in url:
        match = re.search(r'p=([a-zA-Z0-9]+)', url, re.IGNORECASE)
        if match: return f"auction_{match.group(1)}"
        return None

    if "link.php" in url or "out.php" in url:
        return None
        
    return url

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

def has_quantity_conflict(words_a, words_b):
    def get_quants(words):
        q = {}
        for w in words:
            m = re.search(r'^(\d+)(g|ml|kg|l|\uac1c|\ucea0|\ubcd1|\ud329|\uc138\ud2b8|\ub9e4|\ud310|\ubc15\uc2a4)$', w.lower())
            if m:
                val = int(m.group(1))
                unit = m.group(2)
                if unit in ['\uac1c', '\ud310', '\ud329', '\uc138\ud2b8', '\ub9e4', '\ucea0', '\ubcd1']:
                    unit = 'count'
                elif unit == 'kg':
                    val *= 1000
                    unit = 'weight'
                elif unit == 'g':
                    unit = 'weight'
                elif unit == 'l':
                    val *= 1000
                    unit = 'volume'
                elif unit == 'ml':
                    unit = 'volume'
                    
                if unit not in q:
                    q[unit] = set()
                q[unit].add(val)
        return q
        
    qa = get_quants(words_a)
    qb = get_quants(words_b)
    for unit, vals_a in qa.items():
        if unit in qb:
            if not vals_a.intersection(qb[unit]):
                return True
    return False

def has_model_conflict(words_a, words_b):
    """Check model number conflicts between two word sets"""
    # ?곸닽???쇳빀 5???댁긽 ?⑥뼱 = 紐⑤뜽踰덊샇 ?꾨낫
    def get_model_codes(words):
        codes = set()
        for w in words:
            # ?곷Ц+?レ옄 ?쇳빀, 5???댁긽, ?쒖닔 ?レ옄???쒖닔 ?곷Ц ?꾨땶 寃?
            if (len(w) >= 5
                    and any(c.isdigit() for c in w)
                    and any(c.isalpha() for c in w)
                    and not w.endswith('gb')
                    and not w.endswith('mb')
                    and not w.endswith('kg')
                    and not w.endswith('ml')):
                codes.add(w.lower())
        return codes

    codes_a = get_model_codes(words_a)
    codes_b = get_model_codes(words_b)

    # ?묒そ 紐⑤몢 紐⑤뜽 肄붾뱶媛 ?덈뒗??援먯쭛?⑹씠 ?놁쑝硫??ㅻⅨ ?곹뭹
    if codes_a and codes_b and not codes_a.intersection(codes_b):
        return True
    return False

def build_dsu(deals, time_window_days=1):
    dsu = DSU()
    url_to_deal = {}
    
    # name_clusters = list of dicts: {'id': deal.id, 'words': set_of_words, 'times': [datetime, ...]}
    name_clusters = []
    
    for deal in deals:
        dsu.find(deal.id)
        n_url = get_normalized_url(deal)
        n_name = get_normalized_base_name(deal)
        deal_time = deal.indexed_at
        
        # 1. ?꾨꼍?섍쾶 ?숈씪??URL??寃쎌슦 臾띠쓬 (?? ?대쫫??理쒖냼?쒖쓽 援먯쭛?⑹씠 ?덇퀬 ?섎웾 異⑸룎???놁뼱????
        curr_words_set = set(n_name.split('_')) if n_name else set()
        curr_sc = n_name.replace('_', '') if n_name else ""
        
        if n_url:
            n_url_key = n_url
            if n_url_key in url_to_deal:
                for past_id, past_words_set in url_to_deal[n_url_key]:
                    if curr_words_set and past_words_set:
                        intersection = len(curr_words_set.intersection(past_words_set))
                        union = len(curr_words_set.union(past_words_set))
                        if intersection > 0 and (intersection / union) >= 0.1:
                            if not has_quantity_conflict(curr_words_set, past_words_set) and not has_model_conflict(curr_words_set, past_words_set):
                                dsu.union(deal.id, past_id)
                                break
                    else:
                        dsu.union(deal.id, past_id)
                        break
            if n_url_key not in url_to_deal:
                url_to_deal[n_url_key] = []
            url_to_deal[n_url_key].append((deal.id, curr_words_set))
            
        # 2. ?대쫫 湲곕컲 Jaccard ?좎궗??留ㅼ묶 (?⑥뼱??60% ?댁긽???쇱튂?섎㈃ 媛숈? ?곹뭹)
        if n_name:
            words_set = curr_words_set
            matched = False
            for cluster in name_clusters:
                # ?쒓컙 泥댄겕: ?대윭?ㅽ꽣???랁븳 ?쒕뱾???쒓컙 以??섎굹?쇰룄 36?쒓컙 ?대궡?몄? ?뺤씤 (??1.5??泥댁씤 ?덉슜)
                time_diff_ok = False
                if deal_time:
                    for c_time in cluster.get('times', []):
                        if c_time and abs((deal_time - c_time).total_seconds()) <= time_window_days * 24 * 3600:
                            time_diff_ok = True
                            break
                    # ?대윭?ㅽ꽣???쒓컙???녿뒗 寃쎌슦(?덉쇅???곹솴) ?덉슜
                    if not cluster.get('times'):
                        time_diff_ok = True
                else:
                    # ???먯껜???쒓컙???녿뒗 寃쎌슦 ?덉슜
                    time_diff_ok = True
                
                if not time_diff_ok:
                    continue
                
                intersection = len(words_set.intersection(cluster['words']))
                union = len(words_set.union(cluster['words']))
                jaccard = intersection / union if union > 0 else 0
                
                min_len = min(len(words_set), len(cluster['words']))
                subset_ratio = intersection / min_len if min_len > 0 else 0
                
                # ?꾩뼱?곌린 李⑥씠 ?꾨꼍 洹밸났: ?꾩뼱?곌린 ?쒖쇅?섍퀬 ?꾩쟾??媛숈쑝硫?留ㅼ묶
                sc_match = False
                if curr_sc and cluster.get('sc') and curr_sc == cluster['sc']:
                    sc_match = True
                
                # 1. Jaccard ?좎궗??50% ?댁긽
                # 2. ?꾩뼱?곌린 類€ ?꾩쟾 ?쇱튂
                # 3. ?쒖そ???ㅻⅨ履쎌쓽 80% ?댁긽 ?ы븿?섎뒗 遺€遺꾩쭛?⑹씠硫댁꽌 理쒖냼 3?⑥뼱 ?댁긽 寃뱀튌 ??(湲??쒕ぉ vs 吏㏃? ?쒕ぉ 洹밸났)
                if jaccard >= 0.5 or sc_match or (subset_ratio >= 0.8 and intersection >= 3):
                    if not has_quantity_conflict(words_set, cluster['words']) and not has_model_conflict(words_set, cluster['words']):
                        dsu.union(deal.id, cluster['id'])
                        if deal_time:
                            cluster.setdefault('times', []).append(deal_time)
                        matched = True
                        break
            
            if not matched:
                name_clusters.append({
                    'id': deal.id, 
                    'words': words_set, 
                    'sc': curr_sc,
                    'times': [deal_time] if deal_time else []
                })
            
    return dsu

@router.get("/top-hot-deals")
async def get_top_hot_deals(db: Session = Depends(get_db_session)):
    try:
        from sqlalchemy import and_, or_
        query = db.query(models.Deal).join(models.Community)
        time_limit = datetime.utcnow() - timedelta(hours=24)
        
        deals = query.filter(
            and_(
                models.Deal.honey_score >= 100,
                or_(
                    models.Deal.ai_summary.like("%🔥 [커뮤니티 인기]%"),
                    models.Deal.ai_summary.like("%🔥 [커뮤니티 인증 핫딜]%")
                ),
                models.Deal.indexed_at >= time_limit,
                models.Deal.is_closed == False,
                models.Deal.category != "?곷┰",
                models.Deal.category != "이벤트",
                models.Deal.category != "적립/이벤트"
            )
        ).order_by(models.Deal.indexed_at.desc()).limit(1000).all()

        COMMUNITY_MAP = {
            "fmkorea": "에펀", "ppomppu": "뽐뻐", "ruliweb": "루리웹",
            "clien": "클리앙", "quasarzone": "퀸이사존", "ali_ppomppu": "알리뽐뻐",
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
            
            cluster_key = dsu.find(deal.id)
            
            image_url = deal.image_url
            if image_url:
                if image_url.startswith("/images/"):
                    image_url = f"{BASE_URL}{image_url}"
                elif not image_url.startswith("http"):
                    image_url = f"{BASE_URL}/images/{image_url}"
            else:
                if any(kw in deal.title for kw in ["네이버페이", "네이버하이", "네이버적립", "일일적립"]):
                    image_url = "https://img2.quasarzone.com/editor/2023/12/11/49841804f3d132d75a6c11b1510af812.png"
                else:
                    import urllib.parse
                    fallback_text = (deal.community.name or "D")[0].upper()
                    encoded_name = urllib.parse.quote(fallback_text)
                    image_url = f"https://ui-avatars.com/api/?name={encoded_name}&background=e2e8f0&color=475569&size=200&font-size=0.5"

            if cluster_key in cluster_map:
                existing = cluster_map[cluster_key]
                if total_price > 0:
                    if existing.get("total_price", 0) == 0 or total_price < existing["total_price"]:
                        # ?????쒖씠 諛쒓껄?섎㈃ ?€??媛€寃? 媛깆떊 (異쒖쿂??吏€?곗? ?딆쓬)
                        existing["price"] = parsed_price_int
                        existing["shipping_fee"] = deal.shipping_fee or ""
                        existing["total_price"] = total_price
                        clean_title = re.sub(r'\s*\([^)]*[가-힣0-9]+(?:달러|배송|무배|무료)[^)]*\)\s*$', '', deal.title).strip()
                        existing["title"] = clean_title
                        
                        # ????理쒖?媛€)???ㅼ젣 ?대?吏€瑜?媛€議뚭굅?? 湲곗〈 ?쒖씠 ?꾨컮?€ ?대?吏€??寃쎌슦?먮쭔 ??뼱?€
                        if deal.image_url or "ui-avatars.com" in existing.get("image_url", ""):
                            existing["image_url"] = image_url
                            
                        existing["ecommerce_url"] = deal.ecommerce_link or ""
                        existing["post_url"] = deal.post_link or ""
                    
                    # 異쒖쿂 異붽? (post_url 湲곗? 以묐났 諛⑹?)
                    if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                        if comp_name not in existing.setdefault("site_names", []):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                        existing["sources"].extend(get_deal_sources(deal, comp_name, parsed_price_int))
                else:
                    if existing.get("total_price", 0) == 0:
                        if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                            if comp_name not in existing.setdefault("site_names", []):
                                existing["site_names"].append(comp_name)
                                existing["site_name"] = ", ".join(existing["site_names"])
                            existing["sources"].extend(get_deal_sources(deal, comp_name, parsed_price_int))
                
                # ?섎굹?쇰룄 ?댁븘?덈떎硫??꾩껜 ?쒖쓣 吏꾪뻾以묒씤 寃껋쑝濡?泥섎━
                existing["is_closed"] = existing.get("is_closed", True) and getattr(deal, 'is_closed', False)
                    
                # ?ㅼ닔??以묐났 ??以??섎굹?쇰룄 ?ル뵜?대㈃ ?ル뵜 ?곹깭 蹂묓빀
                if getattr(deal, 'honey_score', 0) >= 100:
                    existing["honey_score"] = max(existing.get("honey_score", 0), 100)
                if deal.ai_summary and "?뵦" in deal.ai_summary:
                    if not existing.get("ai_summary"):
                        existing["ai_summary"] = "핫딜 [커뮤니티 인증 핫딜] "
                    elif "?뵦" not in existing["ai_summary"]:
                        existing["ai_summary"] = "핫딜 [커뮤니티 인증 핫딜] " + existing["ai_summary"]
            else:
                ai_sum = deal.ai_summary
                # Clean title for display
                clean_title = re.sub(r'\s*\([^)]*[가-힣0-9]+(?:달러|배송|무배|무료)[^)]*\)\s*$', '', deal.title).strip()
                
                buy_count = deal.like_count or 0
                save_count = 0
                if hasattr(deal, 'deal_reactions') and deal.deal_reactions:
                    reaction = deal.deal_reactions[0]
                    buy_count += reaction.positive_reactions
                    save_count = reaction.negative_reactions

                deal_dict = {
                    "id": deal.id,
                    "title": clean_title,
                    "price": parsed_price_int,
                    "shipping_fee": deal.shipping_fee or "",
                    "total_price": total_price,
                    "currency": getattr(deal, 'currency', 'KRW') or 'KRW',
                    "original_price": None,
                    "discount_rate": 0,
                    "image_url": deal.image_url,
                    "ecommerce_url": deal.ecommerce_link or "",
                    "post_url": deal.post_link or "",
                    "site_name": comp_name,
                    "site_names": [comp_name],
                    "sources": get_deal_sources(deal, comp_name, parsed_price_int),
                    "category": deal.category or "湲고?",
                    "created_at": deal.indexed_at.isoformat() if deal.indexed_at else None,
                    "view_count": deal.view_count or 0,
                    "like_count": buy_count,
                    "comment_count": deal.comment_count or 0,
                    "dislike_count": save_count,
                    "tags": [],
                    "is_closed": deal.is_closed,
                    "honey_score": deal.honey_score or 0,
                    "ai_summary": ai_sum,
                }
                cluster_map[cluster_key] = deal_dict
                grouped_result.append(deal_dict)
        # 24?쒓컙 ?댁뿉 ?щ씪???쒖쓠 ?ы븿???대윭?ㅽ꽣留??꾪꽣留?
        cutoff = datetime.utcnow() - timedelta(hours=24)
        filtered_result = []
        for deal_dict in grouped_result:
            if deal_dict.get("created_at"):
                dt = datetime.fromisoformat(deal_dict["created_at"])
                # ?€?꾩〈 ?뺣낫媛€ ?덉쑝硫??쒓굅 ??鍮꾧탳
                if dt.tzinfo:
                    dt = dt.replace(tzinfo=None)
                if dt >= cutoff:
                    filtered_result.append(deal_dict)
            else:
                filtered_result.append(deal_dict) # ?좎쭨 ?놁쑝硫??쇰떒 ?듦낵
                
        # ?꾪꽣留곷맂 寃곌낵 以??욎뿉??200媛쒕쭔 諛섑솚 (?쒓컙???뺣젹 ?좎?)
        final_top_result = filtered_result[:200]
        
        return {"deals": final_top_result}
    except Exception as e:
        logger.error(f"Error fetching top hot deals: {e}")
        raise HTTPException(status_code=500, detail="Internal Server Error")

@router.get("/hot-deals")
async def get_hot_deals(
    limit: int = 20,
    offset: int = 0,
    category: str = Query(None, description="분류별 카테고리명(ex: 전자기기, 패션 등)"),
    keyword: str = Query(None, description="寃€?됱뼱"),
    platform: str = Query(None, description="커뮤니티/사이트 필터"),
    db: Session = Depends(get_db_session)
):
    try:
        query = db.query(models.Deal).join(models.Community)
        
        if category and category not in ["전체", ""]:
            # ??移댄뀒怨좊━紐낆쓣 ?ㅽ겕?섑띁 移댄뀒怨좊━/?ㅼ썙??諛곗뿴濡?留ㅽ븨
            target_keywords = []
            if category == "음식":
                target_keywords = ["음식", "식품", "먹거리", "간식", "음료수", "건강"]
            elif category == "SW/게임":
                target_keywords = ["게임", "sw", "소프트웨어", "콘솔"]
            elif category == "PC용품":
                target_keywords = ["pc", "하드웨어", "노트북", "컴퓨터", "데스크탑", "모니터"]
            elif category == "가전제품":
                target_keywords = ["가전", "냉장고", "tv", "음향"]
            elif category == "생활용품":
                target_keywords = ["생활", "생필품", "주방", "가구", "인테리어"]
            elif category == "패션":
                target_keywords = ["패션", "의류", "신발", "잡화", "가방", "화장품"]
            elif category == "뷰티":
                target_keywords = ["뷰티", "미용", "화장품"]
            elif category == "모바일/기프티콘":
                target_keywords = ["모바일", "스마트폰", "태블릿", "기프티콘", "쿠폰", "e쿠폰", "e-쿠폰"]
            elif category == "상품권":
                target_keywords = ["상품권", "커피", "이커머스"]
            elif category == "적립":
                target_keywords = ["적립", "사인업", "하이패스", "리퀘스트"]
            elif category == "이벤트":
                target_keywords = ["이벤트", "추석", "명절", "무료증정", "리뷰", "체험단", "선착순", "이벤트", "무료배포"]
            elif category == "육아/유아용품":
                target_keywords = ["유아용품", "육아", "디저트", "식품"]
            elif category == "여행.해외핫딜":
                target_keywords = ["여행", "해외", "호텔", "프리미엄", "항공", "직구"]
            elif category == "핫딜모음":
                target_keywords = [] # ?ル뵜紐⑥쓬?€ ?꾨옒?먯꽌 蹂꾨룄濡??꾪꽣留?(honey_score >= 100 or ?뵦)
            else:
                target_keywords = [category]

            from sqlalchemy import or_, and_
            if category == "핫딜모음":
                query = query.filter(
                    and_(
                        models.Deal.honey_score >= 100,
                        or_(
                            models.Deal.ai_summary.like("%🔥 [커뮤니티 인기]%"),
                            models.Deal.ai_summary.like("%🔥 [커뮤니티 인증 핫딜]%")
                        )
                    )
                )
            else:
                filter_conditions = [models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords]
                query = query.filter(or_(*filter_conditions))
        else:
            from sqlalchemy import and_, not_
            # 전체 탭일 경우 '적립' 및 '이벤트' 카테고리와 정보성/적립성 제목 하드 필터링 (순수 핫딜만 노출)
            query = query.filter(
                and_(
                    models.Deal.category != "적립", 
                    models.Deal.category != "적립/이벤트", 
                    models.Deal.category != "이벤트",
                    not_(models.Deal.title.like("%적립%")),
                    not_(models.Deal.title.like("%정보%")),
                    not_(models.Deal.title.like("%불가%"))
                )
            )
            
        # [Epic 4: Noise Elimination] 
        # ?ㅽ뙵/?낆옄湲 ?댁텧: ?깅줉?쒖? 2?쒓컙??吏?щ뒗?곕룄 轅?먯닔(honey_score)媛 10??誘몃쭔?대㈃ ?쇰뱶?먯꽌 ?쒖쇅
        from datetime import datetime, timedelta
        from sqlalchemy import or_
        two_hours_ago = datetime.utcnow() - timedelta(hours=2)
        query = query.filter(
            or_(
                models.Deal.indexed_at >= two_hours_ago, # 2?쒓컙 ?대궡???좉퇋 ?쒖? 臾댁“嫄??몄텧
                models.Deal.honey_score >= 10 # 2?쒓컙??吏?ъ뼱???먯닔媛 10???댁긽?대㈃ ?몄텧
            )
        )

        if keyword and keyword.strip():
            search = f"%{keyword.strip()}%"
            from sqlalchemy import or_
            query = query.filter(
                or_(
                    models.Deal.title.ilike(search),
                    models.Deal.search_keywords.ilike(search)
                )
            )
            
        if platform and platform not in ["?꾩껜", ""]:
            # DB??communities.name ?먮뒗 display_name ?꾪꽣留?
            REVERSE_COMMUNITY_MAP = {
                "에펀": "fmkorea", "뽐뻐": "ppomppu", "루리웹": "ruliweb",
                "클리앙": "clien", "퀸이사존": "quasarzone", "알리뽐뻐": "ali_ppomppu",
                "鍮좎궘援?궡": "bbasak_domestic", "鍮좎궘?댁쇅": "bbasak_overseas"
            }
            platforms = [p.strip() for p in platform.split(',') if p.strip()]
            from sqlalchemy import or_
            platform_conditions = []
            for p in platforms:
                eng_name = REVERSE_COMMUNITY_MAP.get(p)
                if eng_name:
                    platform_conditions.append(models.Community.name == eng_name)
                platform_conditions.append(models.Community.name.ilike(f"%{p}%"))
                platform_conditions.append(models.Community.display_name.ilike(f"%{p}%"))
            if platform_conditions:
                query = query.filter(or_(*platform_conditions))
            
        # ?됰꼮?섍쾶 媛?몄????뚯씠???⑥뿉???대윭?ㅽ꽣留?
        deals = query.order_by(models.Deal.indexed_at.desc()).limit(300).all()

        COMMUNITY_MAP = {
            "fmkorea": "에펀", "ppomppu": "뽐뻐", "ruliweb": "루리웹",
            "clien": "클리앙", "quasarzone": "퀸이사존", "ali_ppomppu": "알리뽐뻐",
            "bbasak_domestic": "鍮좎궘援?궡", "bbasak_overseas": "鍮좎궘?댁쇅"
        }

        cluster_map = {}
        grouped_result = []
        dsu = build_dsu(deals)

        for deal in deals:
            cluster_key = dsu.find(deal.id)
            comp_name = getattr(deal.community, 'display_name', None) or COMMUNITY_MAP.get(deal.community.name, deal.community.name)

            image_url = deal.image_url
            if image_url:
                if image_url.startswith("/images/"):
                    image_url = f"{BASE_URL}{image_url}"
                elif not image_url.startswith("http"):
                    image_url = f"{BASE_URL}/images/{image_url}"
            else:
                if any(kw in deal.title for kw in ["네이버페이", "네이버하이", "네이버적립", "일일적립"]):
                    image_url = "https://img2.quasarzone.com/editor/2023/12/11/49841804f3d132d75a6c11b1510af812.png"
                else:
                    import urllib.parse
                    fallback_text = (deal.community.name or "D")[0].upper()
                    encoded_name = urllib.parse.quote(fallback_text)
                    image_url = f"https://ui-avatars.com/api/?name={encoded_name}&background=e2e8f0&color=475569&size=200&font-size=0.5"

            # UI瑜??꾪빐 ?쒕ぉ?먯꽌 媛寃??쒓렇 ?좊━湲?
            clean_title = re.sub(r'\s*\([^)]*[가-힣0-9]+(?:달러|배송|무배|무료)[^)]*\)\s*$', '', deal.title).strip()
            
            # ?먮낯 媛寃??좎? (Int ?щ㎎?곗뿉 ?섍린湲???
            parsed_price_int = extract_price(deal.price)
            parsed_shipping_fee = extract_shipping_fee(deal.shipping_fee)
            total_price = parsed_price_int + parsed_shipping_fee if parsed_price_int > 0 else 0

            if cluster_key in cluster_map:
                existing = cluster_map[cluster_key]
                
                if total_price > 0:
                    if existing.get("total_price", 0) == 0 or total_price < existing["total_price"]:
                        # ?????쒖씠 諛쒓껄?섎㈃ ???媛寃? ?대?吏, ?쒕ぉ 媛깆떊
                        existing["price"] = parsed_price_int
                        existing["shipping_fee"] = deal.shipping_fee or ""
                        existing["total_price"] = total_price
                        
                        # ????理쒖?媛)???ㅼ젣 ?대?吏瑜?媛議뚭굅?? 湲곗〈 ?쒖씠 ?꾨컮? ?대?吏??寃쎌슦?먮쭔 ??뼱?
                        if deal.image_url or "ui-avatars.com" in existing.get("image_url", ""):
                            existing["image_url"] = image_url
                            
                        existing["title"] = clean_title
                        existing["ecommerce_url"] = deal.ecommerce_link or ""
                        
                        # ?ъ씠???대쫫(硫붿씤 諭껋?)??????怨녹씠 ?욎쑝濡??ㅻ룄濡?議곗젙
                        if comp_name in existing.setdefault("site_names", []):
                            existing["site_names"].remove(comp_name)
                        existing["site_names"].insert(0, comp_name)
                        existing["site_name"] = ", ".join(existing["site_names"])
                    else:
                        # ??鍮꾩떬 ?쒖씠?쇰룄 ?ㅼ젣 ?대?吏媛 ?덇퀬, 湲곗〈 ?대?吏媛 ?꾨컮??쇰㈃ ?ㅼ젣 ?대?吏濡?蹂닿컯
                        if deal.image_url and "ui-avatars.com" in existing.get("image_url", ""):
                            existing["image_url"] = image_url

                    
                    # 異쒖쿂 諛곗? 異붽? (post_url 以묐났 諛⑹?)
                    if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                        if comp_name not in existing.setdefault("site_names", []):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                        existing["sources"].extend(get_deal_sources(deal, comp_name, parsed_price_int))
                else:
                    if existing.get("total_price", 0) == 0:
                        if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                            if comp_name not in existing.setdefault("site_names", []):
                                existing["site_names"].append(comp_name)
                                existing["site_name"] = ", ".join(existing["site_names"])
                            existing["sources"].extend(get_deal_sources(deal, comp_name, parsed_price_int))
                            
                # ?섎굹?쇰룄 ?댁븘?덈떎硫??꾩껜 ?쒖쓣 吏꾪뻾以묒씤 寃껋쑝濡?泥섎━
                existing["is_closed"] = existing.get("is_closed", True) and getattr(deal, 'is_closed', False)
                
                # ?ㅼ닔??以묐났 ??以??섎굹?쇰룄 ?ル뵜?대㈃ ?ル뵜 ?곹깭 蹂묓빀
                if getattr(deal, 'honey_score', 0) >= 100:
                    existing["honey_score"] = max(existing.get("honey_score", 0), 100)
                if deal.ai_summary and "?뵦" in deal.ai_summary:
                    if not existing.get("ai_summary"):
                        existing["ai_summary"] = "핫딜 [커뮤니티 인증 핫딜] "
                    elif "?뵦" not in existing["ai_summary"]:
                        existing["ai_summary"] = "핫딜 [커뮤니티 인증 핫딜] " + existing["ai_summary"]
            else:

                buy_count = deal.like_count or 0
                save_count = 0
                if hasattr(deal, 'deal_reactions') and deal.deal_reactions:
                    reaction = deal.deal_reactions[0]
                    buy_count += reaction.positive_reactions
                    save_count = reaction.negative_reactions

                deal_dict = {
                    "id": deal.id,
                    "title": clean_title,
                    "price": parsed_price_int,
                    "shipping_fee": deal.shipping_fee or "",
                    "total_price": total_price,
                    "currency": getattr(deal, 'currency', 'KRW') or 'KRW',
                    "image_url": image_url,
                    "ecommerce_url": deal.ecommerce_link or "",
                    "post_url": deal.post_link or "",
                    "site_name": comp_name,
                    "site_names": [comp_name],
                    "sources": get_deal_sources(deal, comp_name, parsed_price_int),
                    "category": deal.category or "湲고?",
                    "honey_score": deal.honey_score or 0,
                    "ai_summary": deal.ai_summary or "",
                    "content_html": getattr(deal, "content_html", "") or "",
                    "is_closed": getattr(deal, 'is_closed', False),
                    "created_at": deal.indexed_at.isoformat() if deal.indexed_at else None,
                    "view_count": deal.view_count or 0,
                    "comment_count": deal.comment_count or 0,
                    "like_count": buy_count,
                    "dislike_count": save_count,
                    "tags": []
                }
                cluster_map[cluster_key] = deal_dict
                grouped_result.append(deal_dict)

        # 洹몃９?묐맂 寃곌낵?먯꽌 ?섏씠吏?ㅼ씠???섑뻾
        final_result = grouped_result[offset:offset+limit]
        

            
        return {"deals": final_result}

    except Exception as e:
        logger.error(f"Error fetching hot deals: {e}", exc_info=True)
        return {"deals": []}

from fastapi import BackgroundTasks

def run_scraper_script():
    import subprocess
    import sys
    root_dir = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
    fetch_script = os.path.join(root_dir, 'backend', 'scheduler', 'main.py')
    subprocess.run([sys.executable, fetch_script, "--one-shot"], cwd=root_dir)

@router.post("/force-scrape")
def force_scrape_deals(background_tasks: BackgroundTasks):
    try:
        background_tasks.add_task(run_scraper_script)
        return {"status": "success", "message": "Manual scraping is now running in the background."}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@router.get("/popular-keywords")
def get_popular_keywords(db: Session = Depends(get_db_session)):
    try:
        # 1. ?⑥퐫 ?ㅼ떆媛?湲됱긽??寃?됱뼱 JSON ?뺤씤
        data_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data")
        file_path = os.path.join(data_dir, "fmkorea_trending.json")
        if os.path.exists(file_path):
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                keywords = data.get("keywords", [])
                if keywords:
                    return {"keywords": keywords[:10]}
                    
        # 2. 罹먯떆??JSON???놁쓣 寃쎌슦 湲곗〈 諛깆뾽 濡쒖쭅 (?ル뵜 ?쒕ぉ 遺꾩꽍)
        deals = db.query(models.Deal.title).order_by(models.Deal.indexed_at.desc()).limit(1000).all()
        words_count = {}
        stop_words = ["특가", "할인", "무료배송", "무배", "체감가", "역대급", "최저가", "핫딜", "쿠폰", "카드", "할인", "적립", "오늘마켓", "추천"]
        
        for deal in deals:
            # 愿꾪샇 ?덉쓽 ?댁슜 ?쒓굅, ?뱀닔臾몄옄 ?쒓굅 ???⑥뼱 遺꾨━
            clean_title = re.sub(r'\[.*?\]|\(.*?\)', '', deal[0])
            words = re.findall(r'[媛-?즑-zA-Z0-9]{2,}', clean_title)
            for w in words:
                if w not in stop_words and not w.isdigit():
                    words_count[w] = words_count.get(w, 0) + 1
                    
        # ?곸쐞 15媛?異붿텧
        sorted_words = sorted(words_count.items(), key=lambda x: x[1], reverse=True)
        top_keywords = [w[0] for w in sorted_words[:15]]
        
        # 留뚯빟 ?덈Т ?곸쑝硫?湲곕낯媛??욎뼱二쇨린
        import random
        base_keywords = ["아이폰", "갤럭시", "로봇청소기", "다이슨", "노트북", "에어팟", "갈비탕", "제로콜라", "모니터", "냉장고", "생수", "치킨/치느님"]
        if len(top_keywords) < 5:
            top_keywords.extend(base_keywords)
            
        random.shuffle(top_keywords)
        return {"keywords": top_keywords[:10]}
    except Exception as e:
        logger.error(f"Error fetching keywords: {e}", exc_info=True)
        return {"keywords": ["아이폰", "갤럭시", "다이슨", "제로콜라", "노트북"]}

@router.get("/deals/{deal_id}")
def get_deal(deal_id: int, db: Session = Depends(get_db_session)):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    COMMUNITY_MAP = {
        "fmkorea": "에펀", "ppomppu": "뽐뻐", "ruliweb": "루리웹",
        "clien": "클리앙", "quasarzone": "퀸이사존", "ali_ppomppu": "알리뽐뻐",
        "bbasak_domestic": "鍮좎궘援?궡", "bbasak_overseas": "鍮좎궘?댁쇅"
    }
    
    # ?꾩옱 ?쒓낵 cluster_key媛 ?숈씪???쒕뱾??紐⑥븘??異쒖쿂(sources) 蹂묓빀
    from sqlalchemy import and_
    if deal.indexed_at:
        end_time = deal.indexed_at
    else:
        end_time = datetime.utcnow()
        
    time_limit = end_time - timedelta(days=7)
    
    similar_deals = db.query(models.Deal).join(models.Community).filter(
        models.Deal.indexed_at >= time_limit,
        models.Deal.indexed_at <= end_time
    ).order_by(models.Deal.price.asc()).all()
    
    if not any(d.id == deal.id for d in similar_deals):
        similar_deals.append(deal)
        
    dsu = build_dsu(similar_deals)
    cluster_key = dsu.find(deal.id)
    
    site_names = []
    sources = []
    
    best_deal = deal
    min_total_price = float('inf')
    
    for d in similar_deals:
        if dsu.find(d.id) == cluster_key:
            c_name = d.community.display_name if d.community and hasattr(d.community, 'display_name') and d.community.display_name else (COMMUNITY_MAP.get(d.community.name, d.community.name) if d.community else "Unknown")
            
            p_val = extract_price(d.price) if isinstance(d.price, str) else (d.price or 0)
            s_val = extract_shipping_fee(d.shipping_fee)
            tot_val = p_val + s_val if p_val > 0 else 0
            
            if tot_val > 0 and tot_val < min_total_price:
                min_total_price = tot_val
                best_deal = d
            elif tot_val == 0 and min_total_price == float('inf'):
                best_deal = d
            
            if not any(s.get('post_url') == (d.post_link or "") for s in sources):
                if c_name not in site_names:
                    site_names.append(c_name)
                sources.extend(get_deal_sources(d, c_name, p_val))
                
    if not sources:
        c_name = deal.community.display_name if deal.community and hasattr(deal.community, 'display_name') and deal.community.display_name else (COMMUNITY_MAP.get(deal.community.name, deal.community.name) if deal.community else "Unknown")
        site_names = [c_name]
        p_val = extract_price(deal.price) if isinstance(deal.price, str) else (deal.price or 0)
        sources = get_deal_sources(deal, c_name, p_val)
        best_deal = deal

    community_name = ", ".join(site_names)
    
    # ?대윭?ㅽ꽣 ?꾩껜媛 醫낅즺?섏뿀?붿? ?뺤씤 (?섎굹?쇰룄 ?대젮?덉쑝硫?False)
    is_closed = True
    for d in similar_deals:
        if dsu.find(d.id) == cluster_key:
            if not getattr(d, 'is_closed', False):
                is_closed = False
                break
    
    # 理쒖?媛 怨꾩궛
    min_price = min([s["price"] for s in sources if s.get("price", 0) > 0], default=0)
    original_price = extract_price(deal.price) if isinstance(deal.price, str) else (deal.price or 0)
    final_price_int = min_price if min_price > 0 and min_price < original_price else original_price
    
    curr = getattr(best_deal, 'currency', 'KRW') or 'KRW'
                     
    return {
        "id": deal.id,
        "title": deal.title, # 蹂몃옒 ?ъ슜?먭? ?대┃???쒖쓽 ?쒕ぉ ?좎? (?먮뒗 best_deal.title ?ъ슜 媛?ν븯吏留? ?쇨??깆쓣 ?꾪빐)
        "price": final_price_int,
        "shipping_fee": getattr(best_deal, "shipping_fee", None),
        "post_url": best_deal.post_link,
        "ecommerce_url": best_deal.ecommerce_link or best_deal.post_link,
        "image_url": getattr(best_deal, "image_url", None) or getattr(deal, "image_url", None),
        "site_name": community_name,
        "site_names": site_names,
        "currency": curr,
        "sources": sources,
        "category": getattr(best_deal, "category", "湲고?"),
        "author": getattr(best_deal, "author", "?듬챸"),
        "views": getattr(best_deal, "view_count", 0) or 0,
        "recommendations": getattr(best_deal, "like_count", 0) or 0,
        "comments": getattr(best_deal, "comment_count", 0) or 0,
        "indexed_at": deal.indexed_at.isoformat() if deal.indexed_at else None,
        "is_closed": is_closed,
        "score": getattr(best_deal, "honey_score", 0) or getattr(deal, "honey_score", 0),
        "ai_summary": getattr(best_deal, "ai_summary", "") or getattr(deal, "ai_summary", "") or ""
    }

@router.get("/deals/{deal_id}/history")
def get_deal_history(deal_id: int, period: str = "7d", db: Session = Depends(get_db_session)):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    # 현재 조회하는 딜의 시간대를 기준으로, 해당 시간 이전 기간의 데이터만 조회
    if deal.indexed_at:
        end_time = deal.indexed_at
    else:
        end_time = datetime.utcnow()
        
    # 기간 파라미터 파싱 (7일, 1개월, 3개월, 6개월, 1개년)
    days_map = {
        "7d": 7,
        "1m": 30,
        "3m": 90,
        "6m": 180,
        "1y": 365
    }
    days = days_map.get(period, 7)
    time_limit = end_time - timedelta(days=days)
    
    # 1. 동일 brand 및 model_code를 가진 Deal들을 O(1) 인덱스 기반으로 직접 DB에서 조회
    # DSU 알고리즘의 O(N^2) 실시간 연산을 완전히 제거하여 타임아웃 박멸
    if deal.brand and deal.model_code:
        matched_deals = db.query(models.Deal).filter(
            models.Deal.brand == deal.brand,
            models.Deal.model_code == deal.model_code,
            models.Deal.indexed_at >= time_limit,
            models.Deal.indexed_at <= end_time
        ).all()
    else:
        matched_deals = [deal]
        
    matched_deal_ids = [d.id for d in matched_deals]
    if deal.id not in matched_deal_ids:
        matched_deals.append(deal)
        matched_deal_ids.append(deal.id)
        
    # 2. 커뮤니티 핫딜 가격 히스토리(PriceHistory) 조회
    history = db.query(models.PriceHistory).filter(
        models.PriceHistory.deal_id.in_(matched_deal_ids),
        models.PriceHistory.checked_at >= time_limit,
        models.PriceHistory.checked_at <= end_time
    ).order_by(models.PriceHistory.checked_at.asc()).all()
    
    time_format = "%m.%d %H:%M" if days <= 7 else "%y.%m.%d"
    
    community_points = []
    comm_map = {} # 날짜 키별 최저가 필터링을 위한 임시 맵
    
    # 2-1. PriceHistory 데이터 정렬 및 최저가 선정
    for h in history:
        try:
            p_val = int(re.sub(r'[^\d]', '', str(h.price)))
            if p_val > 0:
                time_key = h.checked_at.strftime(time_format) if h.checked_at else ""
                dt = h.checked_at
                if time_key not in comm_map or p_val < comm_map[time_key]["price"]:
                    comm_map[time_key] = {"price": p_val, "dt": dt, "source": "community"}
        except:
            pass
            
    # 2-2. Deal 자체의 수집 시점 가격 반영
    for d in matched_deals:
        try:
            p_val = int(re.sub(r'[^\d]', '', str(d.price)))
            if p_val > 0:
                time_key = d.indexed_at.strftime(time_format) if d.indexed_at else ""
                dt = d.indexed_at
                if time_key not in comm_map or p_val < comm_map[time_key]["price"]:
                    comm_map[time_key] = {"price": p_val, "dt": dt, "source": "community"}
        except:
            pass
            
    for k, v in comm_map.items():
        community_points.append({
            "price": v["price"],
            "originalPrice": None,
            "discountRate": None,
            "recordedAt": k.replace(" 00:00", ""),
            "source": "community",
            "dt": v["dt"]
        })

    # 3. 네이버 쇼핑 최저가 히스토리(NaverPriceHistory) 조회 및 병합
    naver_points = []
    if deal.brand and deal.model_code:
        naver_history = db.query(models.NaverPriceHistory).filter(
            models.NaverPriceHistory.brand == deal.brand,
            models.NaverPriceHistory.model_code == deal.model_code,
            models.NaverPriceHistory.checked_at >= time_limit,
            models.NaverPriceHistory.checked_at <= end_time
        ).order_by(models.NaverPriceHistory.checked_at.asc()).all()
        
        naver_map = {}
        for nh in naver_history:
            try:
                time_key = nh.checked_at.strftime(time_format) if nh.checked_at else ""
                dt = nh.checked_at
                p_val = int(nh.price)
                if time_key not in naver_map or p_val < naver_map[time_key]["price"]:
                    naver_map[time_key] = {"price": p_val, "dt": dt, "source": "naver"}
            except:
                pass
                
        for k, v in naver_map.items():
            naver_points.append({
                "price": v["price"],
                "originalPrice": None,
                "discountRate": None,
                "recordedAt": k.replace(" 00:00", ""),
                "source": "naver",
                "dt": v["dt"]
            })
            
    # 4. 두 최저가 스트림을 통합하여 날짜(dt) 순으로 오름차순 정렬
    all_points = community_points + naver_points
    all_points.sort(key=lambda x: x["dt"])
    
    # 5. 최종 반환 형태 정제 (dt 제외)
    result = []
    for p in all_points:
        result.append({
            "price": p["price"],
            "originalPrice": p["originalPrice"],
            "discountRate": p["discountRate"],
            "recordedAt": p["recordedAt"],
            "source": p["source"]
        })
        
    return result

# --- ?뚮퉬 李멸껄 嫄곗?諛?API ---

class CommentCreate(BaseModel):
    user_id: str
    nickname: str = "익명의찰떡이"
    content: str

class VoteCreate(BaseModel):
    user_id: str
    vote_type: str # "buy" or "save"

@router.get("/deals/{deal_id}/comments")
def get_deal_comments(deal_id: int, db: Session = Depends(get_db_session)):
    comments = db.query(models.DealComment).filter(
        models.DealComment.deal_id == deal_id,
        models.DealComment.is_deleted == False
    ).order_by(models.DealComment.created_at.desc()).all()
    
    return [
        {
            "id": c.id,
            "userId": c.user_id,
            "nickname": c.nickname,
            "content": c.content,
            "createdAt": c.created_at.isoformat() if c.created_at else None
        }
        for c in comments
    ]

@router.post("/deals/{deal_id}/comments")
def add_deal_comment(deal_id: int, comment: CommentCreate, db: Session = Depends(get_db_session)):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    new_comment = models.DealComment(
        deal_id=deal_id,
        user_id=comment.user_id,
        nickname=comment.nickname,
        content=comment.content
    )
    db.add(new_comment)
    
    # ??諛섏쓳 ?낅뜲?댄듃 (轅??吏??怨꾩궛 ?깆뿉 ?ъ슜)
    reaction = db.query(models.DealReaction).filter(models.DealReaction.deal_id == deal_id).first()
    if not reaction:
        reaction = models.DealReaction(deal_id=deal_id, comment_count=1)
        db.add(reaction)
    else:
        reaction.comment_count += 1
        
    deal.comment_count += 1
    
    db.commit()
    db.refresh(new_comment)
    
    return {
        "id": new_comment.id,
        "userId": new_comment.user_id,
        "nickname": new_comment.nickname,
        "content": new_comment.content,
        "createdAt": new_comment.created_at.isoformat() if new_comment.created_at else None
    }

@router.get("/deals/{deal_id}/votes")
def get_deal_votes(deal_id: int, user_id: str = None, db: Session = Depends(get_db_session)):
    votes = db.query(models.DealVote).filter(models.DealVote.deal_id == deal_id).all()
    
    buy_count = sum(1 for v in votes if v.vote_type == "buy")
    save_count = sum(1 for v in votes if v.vote_type == "save")
    
    user_vote = None
    if user_id:
        uv = next((v for v in votes if v.user_id == user_id), None)
        if uv:
            user_vote = uv.vote_type
            
    return {
        "buyCount": buy_count,
        "saveCount": save_count,
        "userVote": user_vote
    }

@router.post("/deals/{deal_id}/votes")
def add_deal_vote(deal_id: int, vote: VoteCreate, db: Session = Depends(get_db_session)):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    if vote.vote_type not in ["buy", "save"]:
        raise HTTPException(status_code=400, detail="Invalid vote_type. Must be 'buy' or 'save'")
        
    existing_vote = db.query(models.DealVote).filter(
        models.DealVote.deal_id == deal_id,
        models.DealVote.user_id == vote.user_id
    ).first()
    
    if existing_vote:
        # 湲곗〈 ?ы몴 蹂寃??먮뒗 痍⑥냼
        if existing_vote.vote_type == vote.vote_type:
            # 媛숈? ?ы몴 ??踰????꾨Ⅴ硫?痍⑥냼
            db.delete(existing_vote)
        else:
            # ?ㅻⅨ ?ы몴濡?蹂寃?
            existing_vote.vote_type = vote.vote_type
    else:
        # ?덈줈 ?ы몴
        new_vote = models.DealVote(
            deal_id=deal_id,
            user_id=vote.user_id,
            vote_type=vote.vote_type
        )
        db.add(new_vote)
        
    db.commit()
    
    # 媛깆떊???ы몴 ??怨꾩궛
    all_votes = db.query(models.DealVote).filter(models.DealVote.deal_id == deal_id).all()
    buy_count = sum(1 for v in all_votes if v.vote_type == "buy")
    save_count = sum(1 for v in all_votes if v.vote_type == "save")
    
    # ??諛섏쓳 ?낅뜲?댄듃 (醫뗭븘???レ뼱??諛섏쁺)
    reaction = db.query(models.DealReaction).filter(models.DealReaction.deal_id == deal_id).first()
    if not reaction:
        reaction = models.DealReaction(
            deal_id=deal_id, 
            positive_reactions=buy_count,
            negative_reactions=save_count
        )
        db.add(reaction)
    else:
        reaction.positive_reactions = buy_count
        reaction.negative_reactions = save_count
        
    db.commit()
    
    uv = db.query(models.DealVote).filter(
        models.DealVote.deal_id == deal_id,
        models.DealVote.user_id == vote.user_id
    ).first()
    
    return {
        "buyCount": buy_count,
        "saveCount": save_count,
        "userVote": uv.vote_type if uv else None
    }

# --- UGC Community Features (??꼍留?& ?ㅽ봽?쇱씤 ?ル뵜) ---

class CommunityPostCreate(BaseModel):
    post_type: str
    user_id: str
    nickname: str
    title: str
    content: str
    target_price: Optional[int] = None
    bounty_points: Optional[int] = 0
    like_count: Optional[int] = 0
    location: Optional[str] = None

class CommunityPostCommentCreate(BaseModel):
    user_id: str
    nickname: str
    content: str
    deal_url: Optional[str] = None
    parent_id: Optional[int] = None



@router.post("/posts")
def create_community_post(post: CommunityPostCreate, db: Session = Depends(get_db_session)):
    new_post = models.CommunityPost(
        post_type=post.post_type,
        user_id=post.user_id,
        nickname=post.nickname,
        title=post.title,
        content=post.content,
        target_price=post.target_price,
        bounty_points=post.bounty_points,
        like_count=post.like_count if post.like_count is not None else 0,
        location=post.location
    )
    db.add(new_post)
    db.commit()
    db.refresh(new_post)
    return new_post

@router.put("/posts/{post_id}")
def update_community_post(
    post_id: int, 
    post_data: CommunityPostCreate, 
    db: Session = Depends(get_db_session)
):
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")
        
    if post.user_id != post_data.user_id:
        raise HTTPException(status_code=403, detail="Only the post creator can edit")
        
    post.title = post_data.title
    post.content = post_data.content
    post.target_price = post_data.target_price
    post.bounty_points = post_data.bounty_points
    if post_data.like_count is not None:
        post.like_count = post_data.like_count
    post.location = post_data.location
    post.post_type = post_data.post_type
    
    db.commit()
    db.refresh(post)
    return post


@router.get("/posts")
def get_community_posts(
    post_type: Optional[str] = None,
    skip: int = 0,
    limit: int = 20,
    db: Session = Depends(get_db_session)
):
    query = db.query(models.CommunityPost)
    if post_type:
        query = query.filter(models.CommunityPost.post_type == post_type)
        
    posts = query.order_by(models.CommunityPost.created_at.desc()).offset(skip).limit(limit).all()
    
    result = []
    for p in posts:
        result.append({
            "id": p.id,
            "post_type": p.post_type,
            "user_id": p.user_id,
            "nickname": p.nickname,
            "title": p.title,
            "content": p.content,
            "target_price": p.target_price,
            "bounty_points": p.bounty_points,
            "is_resolved": p.is_resolved,
            "location": p.location,
            "view_count": p.view_count,
            "like_count": p.like_count,
            "created_at": p.created_at.isoformat() if p.created_at else None,
            "comment_count": len(p.comments)
        })
    return result

@router.get("/posts/{post_id}")
def get_community_post(post_id: int, db: Session = Depends(get_db_session)):
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")
        
    # 議고쉶??利앷?
    post.view_count += 1
    db.commit()
    db.refresh(post)
    
    # 댓글 정렬: 먼저 COALESCE(parent_id, id) 로 그룹화하고, 
    # parent_id가 없는(부모) 댓글이 먼저 오고 그 다음 자식 댓글들이 작성순으로 오도록 정렬
    sorted_comments = sorted(
        post.comments, 
        key=lambda c: (c.parent_id if c.parent_id is not None else c.id, c.parent_id is not None, c.created_at)
    )
    
    comments = []
    for c in sorted_comments:
        comments.append({
            "id": c.id,
            "user_id": c.user_id,
            "nickname": c.nickname,
            "content": c.content,
            "deal_url": c.deal_url,
            "is_accepted": c.is_accepted,
            "parent_id": c.parent_id,
            "created_at": c.created_at.isoformat() if c.created_at else None
        })
        
    return {
        "id": post.id,
        "post_type": post.post_type,
        "user_id": post.user_id,
        "nickname": post.nickname,
        "title": post.title,
        "content": post.content,
        "target_price": post.target_price,
        "bounty_points": post.bounty_points,
        "is_resolved": post.is_resolved,
        "location": post.location,
        "view_count": post.view_count,
        "like_count": post.like_count,
        "created_at": post.created_at.isoformat() if post.created_at else None,
        "comments": comments
    }

@router.post("/posts/{post_id}/comments")
def create_community_post_comment(
    post_id: int, 
    comment: CommunityPostCommentCreate, 
    db: Session = Depends(get_db_session)
):
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")
        
    new_comment = models.CommunityPostComment(
        post_id=post_id,
        user_id=comment.user_id,
        nickname=comment.nickname,
        content=comment.content,
        deal_url=comment.deal_url,
        parent_id=comment.parent_id
    )
    db.add(new_comment)
    db.commit()
    db.refresh(new_comment)
    
    return {
        "id": new_comment.id,
        "user_id": new_comment.user_id,
        "nickname": new_comment.nickname,
        "content": new_comment.content,
        "deal_url": new_comment.deal_url,
        "is_accepted": new_comment.is_accepted,
        "parent_id": new_comment.parent_id,
        "created_at": new_comment.created_at.isoformat() if new_comment.created_at else None
    }

@router.put("/posts/{post_id}/comments/{comment_id}")
def update_community_post_comment(
    post_id: int, 
    comment_id: int,
    comment_data: CommunityPostCommentCreate, 
    db: Session = Depends(get_db_session)
):
    comment = db.query(models.CommunityPostComment).filter(
        models.CommunityPostComment.id == comment_id,
        models.CommunityPostComment.post_id == post_id
    ).first()
    
    if not comment:
        raise HTTPException(status_code=404, detail="Comment not found")
        
    if comment.user_id != comment_data.user_id:
        raise HTTPException(status_code=403, detail="Only the comment creator can edit")
        
    comment.content = comment_data.content
    if comment_data.deal_url is not None:
        comment.deal_url = comment_data.deal_url
        
    db.commit()
    db.refresh(comment)
    
    return {
        "id": comment.id,
        "user_id": comment.user_id,
        "nickname": comment.nickname,
        "content": comment.content,
        "deal_url": comment.deal_url,
        "is_accepted": comment.is_accepted,
        "parent_id": comment.parent_id,
        "created_at": comment.created_at.isoformat() if comment.created_at else None
    }

@router.delete("/posts/{post_id}/comments/{comment_id}")
def delete_community_post_comment(
    post_id: int, 
    comment_id: int,
    user_id: str = Query(...),
    db: Session = Depends(get_db_session)
):
    comment = db.query(models.CommunityPostComment).filter(
        models.CommunityPostComment.id == comment_id,
        models.CommunityPostComment.post_id == post_id
    ).first()
    
    if not comment:
        raise HTTPException(status_code=404, detail="Comment not found")
        
    if comment.user_id != user_id:
        raise HTTPException(status_code=403, detail="Only the comment creator can delete")
        
    # 만약 자식 댓글이 있으면 삭제 상태로만 마킹 (또는 내용만 변경)
    # 여기서는 간단히 삭제 처리
    db.delete(comment)
    db.commit()
    
    return {"message": "Comment deleted successfully"}

@router.post("/posts/{post_id}/comments/{comment_id}/accept")
def accept_community_post_comment(
    post_id: int, 
    comment_id: int,
    user_id: str = Query(...),
    db: Session = Depends(get_db_session)
):
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")
        
    if post.user_id != user_id:
        raise HTTPException(status_code=403, detail="Only the post creator can accept comments")
        
    if post.is_resolved:
        raise HTTPException(status_code=400, detail="Post is already resolved")
        
    comment = db.query(models.CommunityPostComment).filter(
        models.CommunityPostComment.id == comment_id,
        models.CommunityPostComment.post_id == post_id
    ).first()
    
    if not comment:
        raise HTTPException(status_code=404, detail="Comment not found")
        
    comment.is_accepted = True
    post.is_resolved = True
    
    db.commit()
    
    return {"message": "Comment accepted successfully", "bounty_points_awarded": post.bounty_points}

@router.get("/users/{user_id}/comments")
def get_user_comments(
    user_id: str,
    skip: int = 0,
    limit: int = 20,
    db: Session = Depends(get_db_session)
):
    comments = db.query(models.CommunityPostComment)\
        .filter(models.CommunityPostComment.user_id == user_id)\
        .order_by(models.CommunityPostComment.created_at.desc())\
        .offset(skip).limit(limit).all()
        
    result = []
    for c in comments:
        # Get parent post for context
        post = db.query(models.CommunityPost).filter(models.CommunityPost.id == c.post_id).first()
        
        result.append({
            "id": c.id,
            "post_id": c.post_id,
            "post_title": post.title if post else "삭제된 게시글",
            "user_id": c.user_id,
            "nickname": c.nickname,
            "content": c.content,
            "deal_url": c.deal_url,
            "is_accepted": c.is_accepted,
            "parent_id": c.parent_id,
            "created_at": c.created_at.isoformat() if c.created_at else None
        })
        
    return result
