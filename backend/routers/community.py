from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy.orm import Session
from backend.database.session import get_db_session
from backend.database import models
import logging
import os
import re
import requests
from datetime import datetime, timedelta

import json

router = APIRouter()
logger = logging.getLogger(__name__)

BASE_URL = os.getenv("BASE_URL", "http://10.0.2.2:8000") # 에뮬레이터 접속용으로 맞춤

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
def proxy_image(url: str):
    try:
        headers = {'User-Agent': 'Mozilla/5.0'}
        if 'bbasak.com' in url:
            headers['Referer'] = 'https://bbasak.com/'
        elif 'ppomppu.co.kr' in url:
            headers['Referer'] = 'https://www.ppomppu.co.kr/'
            
        r = requests.get(url, headers=headers, stream=True, timeout=5)
        return Response(content=r.content, media_type=r.headers.get('Content-Type', 'image/jpeg'))
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
    if '무료' in str(fee_str) or '무배' in str(fee_str): return 0
    nums = re.findall(r'\d+', str(fee_str))
    if nums: return int(''.join(nums))
    return 0

def get_cluster_key(deal):
    ecommerce_link = getattr(deal, 'ecommerce_link', None)
    post_link = getattr(deal, 'post_link', None)
    
    # ecommerce_link가 존재하고, 단순 게시판 링크(post_link)와 다를 때만 URL 기반 클러스터링 적용
    if ecommerce_link and ecommerce_link != post_link:
        link = ecommerce_link
        # Strip scheme and www
        link = re.sub(r'^https?://', '', link)
        link = re.sub(r'^www\.', '', link)
        
        # 쇼핑몰 별로 불필요한 트래킹 파라미터만 제거 (전체 쿼리를 날리면 G마켓 등 식별 불가)
        link = re.sub(r'[?&](utm_source|utm_medium|utm_campaign)=[^&]+', '', link)
        link = link.rstrip('/')
        
        if len(link) > 5:
            return f"url:{link}"
            
    if deal.base_product_name:
        base_str = deal.base_product_name
        is_ai_parsed = True
    else:
        base_str = re.sub(r'\[.*?\]|\(.*?\)', '', deal.title)
        is_ai_parsed = False
        
    clean_cluster = re.sub(r'(삼성|samsung|엘지|lg|애플|apple|소니|sony|닌텐도|nintendo|레노버|lenovo|에이수스|아수스|asus|롯데|농심|오리온|특가|무료배송|무배)', '', base_str, flags=re.IGNORECASE)
    clean_cluster = re.sub(r'(통|병|개|박스|팩|매|장|봉|봉지|캔|페트|입)', '', clean_cluster)
    norm_title = re.sub(r'[^가-힣a-zA-Z0-9]', '', clean_cluster).lower()
    if not is_ai_parsed and len(norm_title) > 15:
        norm_title = norm_title[:15]
    return f"title:{norm_title}"

@router.get("/top-hot-deals")
async def get_top_hot_deals(db: Session = Depends(get_db_session)):
    try:
        from sqlalchemy import and_
        query = db.query(models.Deal).join(models.Community)
        time_limit = datetime.now() - timedelta(hours=24)
        
        deals = query.filter(
            and_(
                models.Deal.honey_score >= 100,
                models.Deal.indexed_at >= time_limit,
                models.Deal.is_closed == False,
                models.Deal.category != "적립",
                models.Deal.category != "이벤트",
                models.Deal.category != "적립/이벤트"
            )
        ).order_by(models.Deal.indexed_at.desc()).limit(200).all()

        COMMUNITY_MAP = {
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
            
            cluster_key = get_cluster_key(deal)
            
            if cluster_key in cluster_map:
                existing = cluster_map[cluster_key]
                if total_price > 0:
                    if existing.get("total_price", 0) == 0 or total_price < existing["total_price"]:
                        # 더 싼 딜이 발견되면 비싼 딜의 출처를 지우고 새로운 최저가 출처로 덮어씀
                        existing["price"] = parsed_price_int
                        existing["shipping_fee"] = deal.shipping_fee or ""
                        existing["total_price"] = total_price
                        existing["site_names"] = [comp_name]
                        existing["site_name"] = comp_name
                        existing["sources"] = [{"site_name": comp_name, "post_url": deal.post_link or ""}]
                        existing["is_closed"] = getattr(deal, 'is_closed', False)
                    elif total_price == existing["total_price"]:
                        # 최저가와 가격이 동일할 때만 출처 배지 추가
                        if not any(s['site_name'] == comp_name for s in existing.get("sources", [])):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                            existing.setdefault("sources", []).append({
                                "site_name": comp_name, 
                                "post_url": deal.post_link or ""
                            })
                        # 최저가 동일 그룹 중 하나라도 살아있다면 전체 딜은 살아있는 것으로 처리
                        if not getattr(deal, 'is_closed', False):
                            existing["is_closed"] = False
                else:
                    if existing.get("total_price", 0) == 0:
                        if not any(s['site_name'] == comp_name for s in existing.get("sources", [])):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                            existing.setdefault("sources", []).append({
                                "site_name": comp_name, 
                                "post_url": deal.post_link or ""
                            })
                        if not getattr(deal, 'is_closed', False):
                            existing["is_closed"] = False
                    
                # 다수의 중복 딜 중 하나라도 핫딜이면 핫딜 상태 병합
                if getattr(deal, 'honey_score', 0) >= 100:
                    existing["honey_score"] = max(existing.get("honey_score", 0), 100)
                if deal.ai_summary and "🔥" in deal.ai_summary:
                    if not existing.get("ai_summary"):
                        existing["ai_summary"] = "🔥 [커뮤니티 인증 핫딜] "
                    elif "🔥" not in existing["ai_summary"]:
                        existing["ai_summary"] = "🔥 [커뮤니티 인증 핫딜] " + existing["ai_summary"]
            else:
                ai_sum = deal.ai_summary
                # Clean title for display
                clean_title = re.sub(r'\s*\([^)]*[가-힣0-9]+(?:원|달러|배송|무배|무료)[^)]*\)\s*$', '', deal.title).strip()
                
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
                    "sources": [{"site_name": comp_name, "post_url": deal.post_link or ""}],
                    "shipping_fee": deal.shipping_fee or "",
                    "category": deal.category or "기타",
                    "created_at": deal.indexed_at.isoformat() if deal.indexed_at else None,
                    "view_count": deal.view_count or 0,
                    "like_count": deal.like_count or 0,
                    "comment_count": deal.comment_count or 0,
                    "dislike_count": 0,
                    "tags": [],
                    "is_closed": deal.is_closed,
                    "honey_score": deal.honey_score or 0,
                    "ai_summary": ai_sum,
                }
                cluster_map[cluster_key] = deal_dict
                grouped_result.append(deal_dict)
        
        return {"deals": grouped_result}
    except Exception as e:
        logger.error(f"Error fetching top hot deals: {e}")
        raise HTTPException(status_code=500, detail="Internal Server Error")

@router.get("/hot-deals")
async def get_hot_deals(
    limit: int = 20,
    offset: int = 0,
    category: str = Query(None, description="분류할 카테고리명 (ex: 전자기기, 의류 등)"),
    keyword: str = Query(None, description="검색어"),
    platform: str = Query(None, description="커뮤니티/사이트 필터"),
    db: Session = Depends(get_db_session)
):
    try:
        query = db.query(models.Deal).join(models.Community)
        
        if category and category not in ["전체", ""]:
            # 앱 카테고리명을 스크래퍼 카테고리/키워드 배열로 매핑
            target_keywords = []
            if category == "음식":
                target_keywords = ["음식", "식품", "먹거리", "간식", "식음료", "건강"]
            elif category == "SW/게임":
                target_keywords = ["게임", "sw", "소프트웨어", "콘솔"]
            elif category == "PC제품":
                target_keywords = ["pc", "하드웨어", "노트북", "컴퓨터", "데스크탑", "모니터"]
            elif category == "가전제품":
                target_keywords = ["가전", "디지털", "tv", "음향"]
            elif category == "생활용품":
                target_keywords = ["생활", "생필품", "주방", "가구", "인테리어"]
            elif category == "의류":
                target_keywords = ["의류", "패션", "잡화", "신발", "가방", "옷"]
            elif category == "화장품":
                target_keywords = ["화장품", "뷰티", "미용"]
            elif category == "모바일/기프티콘":
                target_keywords = ["모바일", "스마트폰", "휴대폰", "기프티콘", "쿠폰", "e쿠폰", "e-쿠폰"]
            elif category == "상품권":
                target_keywords = ["상품권", "컬쳐", "해피머니"]
            elif category == "적립":
                target_keywords = ["적립", "포인트", "페이백", "앱테크"]
            elif category == "이벤트":
                target_keywords = ["이벤트", "출석", "응모", "무료증정", "룰렛", "체험단", "선착순", "퀴즈", "무료배포"]
            elif category == "패키지/이용권":
                target_keywords = ["이용권", "패키지", "서비스", "티켓"]
            elif category == "여행.해외핫딜":
                target_keywords = ["여행", "해외", "알리", "아마존", "큐텐", "직구"]
            else:
                target_keywords = [category]

            from sqlalchemy import or_
            filter_conditions = [models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords]
            query = query.filter(or_(*filter_conditions))
        else:
            from sqlalchemy import and_
            # 전체 탭일 경우 '적립' 및 '이벤트' 카테고리 숨김 처리 (순수 핫딜만 노출)
            query = query.filter(and_(models.Deal.category != "적립", models.Deal.category != "적립/이벤트", models.Deal.category != "이벤트"))
            
        if keyword and keyword.strip():
            search = f"%{keyword.strip()}%"
            from sqlalchemy import or_
            query = query.filter(
                or_(
                    models.Deal.title.ilike(search),
                    models.Deal.search_keywords.ilike(search)
                )
            )
            
        if platform and platform not in ["전체", ""]:
            # DB의 communities.name 또는 display_name 필터링
            REVERSE_COMMUNITY_MAP = {
                "펨코": "fmkorea", "뽐뿌": "ppomppu", "루리웹": "ruliweb",
                "클리앙": "clien", "퀘이사존": "quasarzone", "알리뽐뿌": "ali_ppomppu",
                "빠삭국내": "bbasak_domestic", "빠삭해외": "bbasak_overseas"
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
            
        # 넉넉하게 가져와서 파이썬 단에서 클러스터링
        deals = query.order_by(models.Deal.indexed_at.desc()).limit(300).all()

        COMMUNITY_MAP = {
            "fmkorea": "펨코", "ppomppu": "뽐뿌", "ruliweb": "루리웹",
            "clien": "클리앙", "quasarzone": "퀘이사존", "ali_ppomppu": "알리뽐뿌",
            "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외"
        }

        cluster_map = {}
        grouped_result = []

        for deal in deals:
            cluster_key = get_cluster_key(deal)
            comp_name = getattr(deal.community, 'display_name', None) or COMMUNITY_MAP.get(deal.community.name, deal.community.name)

            if cluster_key in cluster_map:
                existing = cluster_map[cluster_key]
                parsed_price_int = extract_price(deal.price)
                parsed_shipping_fee = extract_shipping_fee(deal.shipping_fee)
                total_price = parsed_price_int + parsed_shipping_fee if parsed_price_int > 0 else 0
                
                if total_price > 0:
                    if existing.get("total_price", 0) == 0 or total_price < existing["total_price"]:
                        # 더 싼 딜이 발견되면 비싼 딜의 출처를 지우고 새로운 최저가 출처로 덮어씀
                        existing["price"] = parsed_price_int
                        existing["shipping_fee"] = deal.shipping_fee or ""
                        existing["total_price"] = total_price
                        existing["site_names"] = [comp_name]
                        existing["site_name"] = comp_name
                        existing["sources"] = [{"site_name": comp_name, "post_url": deal.post_link or ""}]
                        existing["is_closed"] = getattr(deal, 'is_closed', False)
                    elif total_price == existing["total_price"]:
                        # 최저가와 가격이 동일할 때만 출처 배지 추가
                        if not any(s['site_name'] == comp_name for s in existing.get("sources", [])):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                            existing.setdefault("sources", []).append({
                                "site_name": comp_name, 
                                "post_url": deal.post_link or ""
                            })
                        # 최저가 동일 그룹 중 하나라도 살아있다면 전체 딜은 살아있는 것으로 처리
                        if not getattr(deal, 'is_closed', False):
                            existing["is_closed"] = False
                else:
                    if existing.get("total_price", 0) == 0:
                        if not any(s['site_name'] == comp_name for s in existing.get("sources", [])):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                            existing.setdefault("sources", []).append({
                                "site_name": comp_name, 
                                "post_url": deal.post_link or ""
                            })
                        if not getattr(deal, 'is_closed', False):
                            existing["is_closed"] = False
                
                # 다수의 중복 딜 중 하나라도 핫딜이면 핫딜 상태 병합
                if getattr(deal, 'honey_score', 0) >= 100:
                    existing["honey_score"] = max(existing.get("honey_score", 0), 100)
                if deal.ai_summary and "🔥" in deal.ai_summary:
                    if not existing.get("ai_summary"):
                        existing["ai_summary"] = "🔥 [커뮤니티 인증 핫딜] "
                    elif "🔥" not in existing["ai_summary"]:
                        existing["ai_summary"] = "🔥 [커뮤니티 인증 핫딜] " + existing["ai_summary"]
            else:
                image_url = deal.image_url
                if image_url:
                    if image_url.startswith("/images/"):
                        image_url = f"{BASE_URL}{image_url}"
                    elif not image_url.startswith("http"):
                        image_url = f"{BASE_URL}/images/{image_url}"
                else:
                    if any(kw in deal.title for kw in ["네이버페이", "네이버 페이", "네이버 적립", "일일적립"]):
                        # 4번째 캡쳐에 있는 실제 이미지 링크 사용
                        image_url = "https://img2.quasarzone.com/editor/2023/12/11/49841804f3d132d75a6c11b1510af812.png"
                    else:
                        import urllib.parse
                        fallback_text = (deal.community.name or "D")[0].upper()
                        encoded_name = urllib.parse.quote(fallback_text)
                        image_url = f"https://ui-avatars.com/api/?name={encoded_name}&background=e2e8f0&color=475569&size=200&font-size=0.5"

                # UI를 위해 제목에서 가격 태그 날리기
                clean_title = re.sub(r'\s*\([^)]*[가-힣0-9]+(?:원|달러|배송|무배|무료)[^)]*\)\s*$', '', deal.title).strip()
                
                # 원본 가격 유지 (Int 포맷터에 넘기기 전)
                parsed_price_int = extract_price(deal.price)
                parsed_shipping_fee = extract_shipping_fee(deal.shipping_fee)
                total_price = parsed_price_int + parsed_shipping_fee if parsed_price_int > 0 else 0

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
                    "sources": [{"site_name": comp_name, "post_url": deal.post_link or ""}],
                    "category": deal.category or "기타",
                    "honey_score": deal.honey_score or 0,
                    "ai_summary": deal.ai_summary or "",
                    "content_html": getattr(deal, "content_html", "") or "",
                    "is_closed": getattr(deal, 'is_closed', False),
                    "created_at": deal.indexed_at.isoformat() if deal.indexed_at else None,
                    "view_count": deal.view_count or 0,
                    "comment_count": deal.comment_count or 0,
                    "like_count": deal.like_count or 0,
                    "dislike_count": getattr(deal, 'dislike_count', 0) or 0,
                    "tags": []
                }
                cluster_map[cluster_key] = deal_dict
                grouped_result.append(deal_dict)

        # 그룹핑된 결과에서 페이지네이션 수행
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
        # 1. 펨코 실시간 급상승 검색어 JSON 확인
        data_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data")
        file_path = os.path.join(data_dir, "fmkorea_trending.json")
        if os.path.exists(file_path):
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                keywords = data.get("keywords", [])
                if keywords:
                    return {"keywords": keywords[:10]}
                    
        # 2. 캐시된 JSON이 없을 경우 기존 백업 로직 (핫딜 제목 분석)
        deals = db.query(models.Deal.title).order_by(models.Deal.indexed_at.desc()).limit(1000).all()
        words_count = {}
        stop_words = ["특가", "할인", "무료배송", "무배", "체감가", "역대급", "최저가", "핫딜", "쿠폰", "카드", "할인", "적립", "스마일캐시", "원"]
        
        for deal in deals:
            # 괄호 안의 내용 제거, 특수문자 제거 후 단어 분리
            clean_title = re.sub(r'\[.*?\]|\(.*?\)', '', deal[0])
            words = re.findall(r'[가-힣a-zA-Z0-9]{2,}', clean_title)
            for w in words:
                if w not in stop_words and not w.isdigit():
                    words_count[w] = words_count.get(w, 0) + 1
                    
        # 상위 15개 추출
        sorted_words = sorted(words_count.items(), key=lambda x: x[1], reverse=True)
        top_keywords = [w[0] for w in sorted_words[:15]]
        
        # 만약 너무 적으면 기본값 섞어주기
        import random
        base_keywords = ["아이폰", "가습기", "로봇청소기", "다이슨", "노트북", "에어팟", "갤럭시", "제로콜라", "모니터", "냉장고", "생수", "닭가슴살"]
        if len(top_keywords) < 5:
            top_keywords.extend(base_keywords)
            
        random.shuffle(top_keywords)
        return {"keywords": top_keywords[:10]}
    except Exception as e:
        logger.error(f"Error fetching keywords: {e}", exc_info=True)
        return {"keywords": ["아이폰", "가습기", "다이슨", "제로콜라", "노트북"]}

@router.get("/deals/{deal_id}")
def get_deal(deal_id: int, db: Session = Depends(get_db_session)):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    COMMUNITY_MAP = {
        "fmkorea": "펨코", "ppomppu": "뽐뿌", "ruliweb": "루리웹",
        "clien": "클리앙", "quasarzone": "퀘이사존", "ali_ppomppu": "알리뽐뿌",
        "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외"
    }
    
    community_name = deal.community.display_name if deal.community and hasattr(deal.community, 'display_name') and deal.community.display_name else \
                     (COMMUNITY_MAP.get(deal.community.name, deal.community.name) if deal.community else "Unknown")
                     
    return {
        "id": deal.id,
        "title": deal.title,
        "price": extract_price(deal.price) if isinstance(deal.price, str) else (deal.price or 0),
        "shipping_fee": getattr(deal, "shipping_fee", None),
        "post_url": deal.post_link,
        "ecommerce_url": deal.ecommerce_link or deal.post_link,
        "image_url": getattr(deal, "image_url", None),
        "site_name": community_name,
        "category": getattr(deal, "category", "기타"),
        "author": getattr(deal, "author", "익명"),
        "views": 0,
        "recommendations": 0,
        "comments": 0,
        "indexed_at": deal.indexed_at.isoformat() if deal.indexed_at else None,
        "is_closed": getattr(deal, "is_closed", False),
        "score": getattr(deal, "honey_score", 0),
        "ai_summary": "이 상품은 최근 커뮤니티에서 많은 인기를 끌고 있습니다. 역대 최저가 방어선에 근접할 수 있으니 가격 추이를 확인하세요."
    }

@router.get("/deals/{deal_id}/history")
def get_deal_history(deal_id: int, db: Session = Depends(get_db_session)):
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    history = db.query(models.PriceHistory).filter(models.PriceHistory.deal_id == deal_id).order_by(models.PriceHistory.checked_at.asc()).all()
    
    result = []
    last_price = -1
    for h in history:
        try:
            p_val = int(re.sub(r'[^\d]', '', str(h.price)))
            if p_val > 0:
                result.append({
                    "price": p_val,
                    "originalPrice": None,
                    "discountRate": None,
                    "recordedAt": h.checked_at.strftime("%m.%d %H:%M") if h.checked_at else ""
                })
                last_price = p_val
        except:
            pass
            
    # Include current price as well if history is empty or last element is different
    current_price_int = 0
    try:
        current_price_int = int(re.sub(r'[^\d]', '', str(deal.price)))
    except:
        pass
        
    if current_price_int > 0:
        if last_price != current_price_int:
            result.append({
                "price": current_price_int,
                "originalPrice": None,
                "discountRate": None,
                "recordedAt": deal.indexed_at.strftime("%m.%d %H:%M") if deal.indexed_at else "지금"
            })
            
    return result
