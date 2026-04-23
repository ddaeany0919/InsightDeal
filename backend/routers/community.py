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
            query = query.filter(models.Deal.category == category)
        else:
            query = query.filter(models.Deal.category != "적립/이벤트")
            
        if keyword and keyword.strip():
            search = f"%{keyword.strip()}%"
            query = query.filter(models.Deal.title.ilike(search))
            
        if platform and platform not in ["전체", ""]:
            # DB의 communities.name 또는 display_name 필터링
            query = query.filter(
                (models.Community.name.ilike(f"%{platform}%")) | 
                (models.Community.display_name.ilike(f"%{platform}%"))
            )
            
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
            # 제목 클러스터링 정규화 (쇼핑몰 태그, 괄호 내용, 단위 제거)
            clean_cluster = re.sub(r'\[.*?\]|\(.*?\)', '', deal.title)
            clean_cluster = re.sub(r'(통|병|개|박스|팩|매|장|봉|봉지|캔|페트)', '', clean_cluster)
            norm_title = re.sub(r'[^가-힣a-zA-Z0-9]', '', clean_cluster).lower()
            if len(norm_title) > 8: norm_title = norm_title[:15]
            
            comp_name = getattr(deal.community, 'display_name', None) or COMMUNITY_MAP.get(deal.community.name, deal.community.name)

            if norm_title in cluster_map:
                existing = cluster_map[norm_title]
                if not any(s['site_name'] == comp_name for s in existing.get("sources", [])):
                    existing["site_names"].append(comp_name)
                    existing["site_name"] = ", ".join(existing["site_names"])
                    existing.setdefault("sources", []).append({
                        "site_name": comp_name, 
                        "post_url": deal.post_link or ""
                    })
                
                # 병합 시 여러 커뮤니티 중 "가장 저렴한 가격"으로 노출 (0원 제외)
                parsed_price_int = extract_price(deal.price)
                if parsed_price_int > 0 and (existing["price"] == 0 or parsed_price_int < existing["price"]):
                    existing["price"] = parsed_price_int
            else:
                image_url = deal.image_url
                if image_url:
                    if image_url.startswith("/images/"):
                        image_url = f"{BASE_URL}{image_url}"
                    elif not image_url.startswith("http"):
                        image_url = f"{BASE_URL}/images/{image_url}"

                # UI를 위해 제목에서 가격 태그 날리기
                clean_title = re.sub(r'\s*\([^)]*[가-힣0-9]+(?:원|달러|배송|무배|무료)[^)]*\)\s*$', '', deal.title).strip()
                
                # 원본 가격 유지 (Int 포맷터에 넘기기 전)
                parsed_price_int = extract_price(deal.price)

                deal_dict = {
                    "id": deal.id,
                    "title": clean_title,
                    "price": parsed_price_int,
                    "original_price": None, 
                    "discount_rate": 0,
                    "shipping_fee": deal.shipping_fee,
                    "image_url": image_url,
                    "ecommerce_url": deal.ecommerce_link or "",
                    "post_url": deal.post_link or "",
                    "site_name": comp_name,
                    "site_names": [comp_name],
                    "sources": [{"site_name": comp_name, "post_url": deal.post_link or ""}],
                    "category": deal.category or "기타",
                    "honey_score": deal.honey_score or 0,
                    "ai_summary": deal.ai_summary or "",
                    "created_at": deal.indexed_at.isoformat() if deal.indexed_at else None,
                    "view_count": 0, "comment_count": 0, "like_count": 0
                }
                cluster_map[norm_title] = deal_dict
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

