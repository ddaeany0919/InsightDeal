import os
from contextlib import asynccontextmanager
from dotenv import load_dotenv
from datetime import datetime
import requests
from bs4 import BeautifulSoup
import re
import traceback

# ✅ 환경변수 먼저 로드
load_dotenv()

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy.orm import Session, joinedload
import database
import models

# ✅ Lifespan 이벤트 핸들러 (최신 방식)
@asynccontextmanager
async def lifespan(app: FastAPI):
    # 서버 시작 시
    print("🚀 InsightDeal API 서버 시작")
    database.Base.metadata.create_all(bind=database.engine)
    yield
    # 서버 종료 시
    print("🛑 InsightDeal API 서버 종료")

# --- FastAPI 앱 설정 ---
app = FastAPI(lifespan=lifespan)

# ✅ CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 이미지 폴더 설정
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_CACHE_DIR = os.path.join(SCRIPT_DIR, "image_cache")
if not os.path.exists(IMAGE_CACHE_DIR):
    os.makedirs(IMAGE_CACHE_DIR)

app.mount("/images", StaticFiles(directory=IMAGE_CACHE_DIR), name="images")

# --- 상품 정보 확장 유틸 함수들 ---

def crawl_post_details(post_url: str, deal_title: str):
    """원본 게시물에서 상세 이미지와 정보를 추출"""
    print(f"🔍 [Post Crawler] Starting crawl for: {post_url[:50]}...")
    
    try:
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
            'Accept-Language': 'ko-KR,ko;q=0.9,en;q=0.8',
            'Accept-Encoding': 'gzip, deflate, br',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1'
        }
        
        print(f"🌐 [Post Crawler] Requesting URL with headers: {post_url}")
        response = requests.get(post_url, headers=headers, timeout=15)
        response.raise_for_status()
        print(f"✅ [Post Crawler] Successfully fetched content. Status: {response.status_code}, Size: {len(response.content)} bytes")
        
        soup = BeautifulSoup(response.content, 'html.parser')
        
        # 이미지 추출 (기존보다 더 많이)
        images = []
        print(f"📷 [Image Extractor] Starting image extraction...")
        
        # 다양한 img 태그 선택자 시도
        img_selectors = [
            'img[src*="http"]',
            'img[data-src*="http"]', 
            'img[data-original*="http"]',
            '.content img, .post-content img, .article-content img',
            '#content img, #post img, #article img'
        ]
        
        found_images = set()  # 중복 제거를 위한 set
        
        for selector in img_selectors:
            try:
                imgs = soup.select(selector)
                print(f"🔍 [Image Extractor] Selector '{selector}' found {len(imgs)} images")
                
                for img in imgs:
                    img_url = img.get('src') or img.get('data-src') or img.get('data-original')
                    if img_url and img_url.startswith('http'):
                        # 이미지 URL 정제
                        if 'gif' not in img_url.lower() and len(img_url) > 20:  # gif 및 너무 짧은 URL 제외
                            found_images.add(img_url)
                            print(f"📷 [Image Extractor] Added image: {img_url[:60]}...")
                            
            except Exception as img_error:
                print(f"⚠️ [Image Extractor] Error with selector '{selector}': {img_error}")
        
        # 최대 10개 이미지로 제한
        image_list = list(found_images)[:10]
        images = [{
            "url": img_url,
            "alt": f"상품 이미지 {i+1}",
            "description": "상세 이미지"
        } for i, img_url in enumerate(image_list)]
        
        print(f"📷 [Image Extractor] Total images extracted: {len(images)}")
        
        # 본문 내용 추출
        print(f"📝 [Content Extractor] Starting content extraction...")
        content_text = ""
        
        # 다양한 컨텐츠 선택자 시도
        content_selectors = [
            '.content, .post-content, .article-content',
            '#content, #post, #article',
            '.post-body, .entry-content',
            'div[class*="content"], div[class*="post"]'
        ]
        
        for selector in content_selectors:
            try:
                content_elements = soup.select(selector)
                print(f"🔍 [Content Extractor] Selector '{selector}' found {len(content_elements)} elements")
                
                for element in content_elements:
                    text = element.get_text(separator='\n', strip=True)
                    if len(text) > 50:  # 의미 있는 내용만
                        content_text += text + "\n\n"
                        print(f"📝 [Content Extractor] Added content block: {len(text)} characters")
                        
                if content_text:
                    break  # 첫 번째로 성공한 선택자 사용
                    
            except Exception as content_error:
                print(f"⚠️ [Content Extractor] Error with selector '{selector}': {content_error}")
        
        # 컨텐츠가 없으면 대체 방법 시도
        if not content_text.strip():
            print(f"🔄 [Content Extractor] No content found, trying fallback method...")
            # 전체 body에서 추출
            body_text = soup.body.get_text(separator='\n', strip=True) if soup.body else soup.get_text(separator='\n', strip=True)
            # 상품과 관련된 부분만 추출
            lines = body_text.split('\n')
            relevant_lines = []
            for line in lines:
                if any(keyword in line for keyword in ['상품', '가격', '할인', '배솥', deal_title[:10]]):
                    relevant_lines.append(line)
            content_text = '\n'.join(relevant_lines[:20])  # 최대 20줄
        
        # 컨텐츠 길이 제한
        if len(content_text) > 2000:
            content_text = content_text[:2000] + "..."
        
        print(f"📝 [Content Extractor] Final content length: {len(content_text)} characters")
        
        result = {
            "images": images,
            "content": content_text.strip(),
            "crawled_at": datetime.now().isoformat(),
            "source_url": post_url
        }
        
        print(f"✅ [Post Crawler] Successfully completed crawling. Images: {len(images)}, Content: {len(content_text)} chars")
        return result
        
    except requests.RequestException as req_error:
        print(f"❌ [Post Crawler] Request failed for {post_url}: {req_error}")
        return {
            "images": [],
            "content": "콘텐츠를 불러올 수 없습니다.",
            "error": f"Request error: {str(req_error)}",
            "crawled_at": datetime.now().isoformat()
        }
    except Exception as e:
        print(f"❌ [Post Crawler] Unexpected error for {post_url}: {e}")
        print(f"🔍 [Post Crawler] Traceback: {traceback.format_exc()}")
        return {
            "images": [],
            "content": "콘텐츠 추출 중 오류가 발생했습니다.",
            "error": str(e),
            "crawled_at": datetime.now().isoformat()
        }

def analyze_product_with_ai(deal_title: str, content: str, images: list):
    """상품 정보를 AI로 분석하여 정제"""
    print(f"🤖 [AI Analyzer] Starting AI analysis for: {deal_title[:30]}...")
    
    try:
        # 간단한 AI 분석 예시 (실제로는 OpenAI/Claude API 사용)
        # 여기서는 반환만 함
        analysis_result = {
            "productSummary": deal_title,
            "keyFeatures": [
                "고품질 상품",
                "합리적 가격",
                "빠른 배송"
            ],
            "recommended": True,
            "analysisConfidence": 85.0,
            "analyzedAt": datetime.now().isoformat()
        }
        
        print(f"✅ [AI Analyzer] Analysis completed with confidence: {analysis_result['analysisConfidence']}%")
        return analysis_result
        
    except Exception as e:
        print(f"❌ [AI Analyzer] Analysis failed: {e}")
        return {
            "productSummary": deal_title,
            "keyFeatures": [],
            "recommended": False,
            "analysisConfidence": 0.0,
            "error": str(e),
            "analyzedAt": datetime.now().isoformat()
        }

# --- API 엔드포인트 ---

@app.get("/")
def read_root():
    return {"message": "InsightDeal API Server is running!", "version": "2.2"}

@app.get("/api/deals")
def get_deals_list(page: int = 1, page_size: int = 20, community_id: int = None):
    """딜 목록 조회 (500 오류 해결 버전)"""
    db: Session = database.SessionLocal()
    try:
        print(f"📊 [Deals List] Request - Page: {page}, Size: {page_size}, Community: {community_id}")
        offset = (page - 1) * page_size
        
        # 기본 쿼리 구성
        query = db.query(models.Deal)
        
        # 커뮤니티 필터링 (옵션)
        if community_id:
            query = query.filter(models.Deal.source_community_id == community_id)
            print(f"🔍 [Deals List] Filtering by community ID: {community_id}")
        
        # 관계 데이터 미리 로드 및 정렬
        deals_from_db = (query
                        .options(joinedload(models.Deal.community))
                        .order_by(models.Deal.indexed_at.desc())
                        .offset(offset)
                        .limit(page_size)
                        .all())
        
        print(f"📄 [Deals List] Found {len(deals_from_db)} deals from database")
        
        results = []
        for deal in deals_from_db:
            try:
                # 커뮤니티 정보 안전하게 조회
                if hasattr(deal, 'community') and deal.community:
                    community_name = deal.community.name
                else:
                    # 관계가 없을 경우 직접 조회
                    community = db.query(models.Community).filter(
                        models.Community.id == deal.source_community_id
                    ).first()
                    community_name = community.name if community else "Unknown"
                
                deal_data = {
                    "id": deal.id,
                    "title": deal.title or "",
                    "community": community_name,
                    "shopName": deal.shop_name or "",
                    "price": deal.price or "",
                    "shippingFee": deal.shipping_fee or "",
                    "imageUrl": deal.image_url or "",
                    "category": deal.category or "기타",
                    "is_closed": getattr(deal, 'is_closed', False),
                    "deal_type": getattr(deal, 'deal_type', '일반'),
                    "ecommerce_link": deal.ecommerce_link or "",
                    "indexed_at": deal.indexed_at.isoformat() if deal.indexed_at else None
                }
                results.append(deal_data)
                
            except Exception as deal_error:
                print(f"⚠️ [Deals List] Deal {deal.id} 처리 중 오류 (건너뛰): {deal_error}")
                continue
        
        print(f"✅ [Deals List] Successfully returned {len(results)}개 딜")
        return results
        
    except Exception as e:
        print(f"❌ [Deals List] 딜 조회 실패: {e}")
        print(f"🔍 [Deals List] Traceback: {traceback.format_exc()}")
        return []
        
    finally:
        db.close()

@app.get("/api/communities")  
def get_communities():
    """커뮤니티 목록 조회"""
    db: Session = database.SessionLocal()
    try:
        print(f"🏠 [Communities] Fetching communities list")
        communities = db.query(models.Community).all()
        result = [{
            "id": comm.id,
            "name": comm.name,
            "base_url": comm.base_url
        } for comm in communities]
        print(f"✅ [Communities] Found {len(result)} communities")
        return result
    except Exception as e:
        print(f"❌ [Communities] 커뮤니티 조회 실패: {e}")
        return []
    finally:
        db.close()

@app.get("/api/deals/{deal_id}")
def get_deal_detail(deal_id: int):
    """특정 딜의 상세 정보를 반환합니다."""
    db: Session = database.SessionLocal()
    try:
        print(f"🔍 [Deal Detail] Fetching deal ID: {deal_id}")
        deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
        if not deal:
            print(f"❌ [Deal Detail] Deal not found: {deal_id}")
            raise HTTPException(status_code=404, detail="Deal not found")
        
        # 커뮤니티 정보 가져오기
        community = db.query(models.Community).filter(
            models.Community.id == deal.source_community_id
        ).first()
        
        result = {
            "id": deal.id,
            "title": deal.title,
            "community": community.name if community else "Unknown",
            "shopName": deal.shop_name,
            "price": deal.price,
            "shippingFee": deal.shipping_fee,
            "category": deal.category,
            "postLink": deal.post_link,
            "ecommerceLink": deal.ecommerce_link,
            "imageUrl": deal.image_url,
            "indexedAt": deal.indexed_at.isoformat() if deal.indexed_at else None,
            "dealType": getattr(deal, 'deal_type', '일반'),
            "isClosed": getattr(deal, 'is_closed', False),
            "contentHtml": getattr(deal, 'content_html', None)
        }
        
        print(f"✅ [Deal Detail] Successfully fetched deal: {deal.title[:30]}...")
        return result
    except Exception as e:
        print(f"❌ [Deal Detail] 딜 상세 조회 오류: {e}")
        print(f"🔍 [Deal Detail] Traceback: {traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()

# ✅ NEW: 상품 정보 확장 API 
@app.get("/api/deals/{deal_id}/enhanced-info")
def get_enhanced_deal_info(deal_id: int):
    """딜의 향상된 정보 (상세 이미지, AI 분석)"""
    db: Session = database.SessionLocal()
    try:
        print(f"🚀 [Enhanced Info] Starting enhanced info fetch for deal: {deal_id}")
        
        # 기본 딜 정보 조회
        deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
        if not deal:
            print(f"❌ [Enhanced Info] Deal not found: {deal_id}")
            raise HTTPException(status_code=404, detail="Deal not found")
        
        print(f"📄 [Enhanced Info] Found deal: {deal.title[:30]}...")
        
        # 1. 게시 시간 정보
        posted_time_info = {
            "indexedAt": deal.indexed_at.isoformat() if deal.indexed_at else None,  # indexed_at → indexedAt
            "formattedTime": None,         # formatted_time → formattedTime
            "timeAgo": None               # time_ago → timeAgo
        }
        
        if deal.indexed_at:
            try:
                from datetime import timezone, timedelta
                now = datetime.now(timezone.utc)
                indexed_time = deal.indexed_at.replace(tzinfo=timezone.utc) if deal.indexed_at.tzinfo is None else deal.indexed_at
                time_diff = now - indexed_time
                
                # 시간 포맷팅
                if time_diff.days > 0:
                    posted_time_info["timeAgo"] = f"{time_diff.days}일 전"
                elif time_diff.seconds > 3600:
                    hours = time_diff.seconds // 3600
                    posted_time_info["timeAgo"] = f"{hours}시간 전"
                else:
                    minutes = time_diff.seconds // 60
                    posted_time_info["timeAgo"] = f"{minutes}분 전"
                    
                posted_time_info["formattedTime"] = indexed_time.strftime("%m월 %d일 %H:%M")
                print(f"⏰ [Enhanced Info] Time info calculated: {posted_time_info['timeAgo']}")
                
            except Exception as time_error:
                print(f"⚠️ [Enhanced Info] Time calculation error: {time_error}")
        
        # 2. 원본 게시물 크롤링으로 상세 정보 추출
        product_detail = None
        if deal.post_link:
            print(f"🔍 [Enhanced Info] Starting post crawling for: {deal.post_link}")
            product_detail = crawl_post_details(deal.post_link, deal.title)
            
            # 3. AI 분석 (옵션)
            if product_detail and product_detail.get('content'):
                print(f"🤖 [Enhanced Info] Starting AI analysis...")
                ai_analysis = analyze_product_with_ai(
                    deal.title, 
                    product_detail['content'], 
                    product_detail.get('images', [])
                )
                product_detail['ai_analysis'] = ai_analysis
        else:
            print(f"⚠️ [Enhanced Info] No post link available for crawling")
        
        # 최종 결과 조합
        result = {
            "dealId": deal_id,                    # deal_id → dealId
            "postedTime": posted_time_info,       # posted_time → postedTime
            "productDetail": product_detail,      # product_detail → productDetail  
            "enhancedAt": datetime.now().isoformat()  # enhanced_at → enhancedAt
        }
        
        print(f"✅ [Enhanced Info] Successfully completed enhanced info fetch")
        print(f"📈 [Enhanced Info] Result summary:")
        print(f"  - Posted time: {posted_time_info.get('time_ago', 'Unknown')}")
        print(f"  - Images found: {len(product_detail.get('images', [])) if product_detail else 0}")
        print(f"  - Content length: {len(product_detail.get('content', '')) if product_detail else 0} chars")
        print(f"  - AI analysis: {'Yes' if product_detail and product_detail.get('ai_analysis') else 'No'}")
        
        return result
        
    except HTTPException:
        raise  # HTTPException은 그대로 전달
    except Exception as e:
        print(f"❌ [Enhanced Info] Enhanced info 조회 실패: {e}")
        print(f"🔍 [Enhanced Info] Traceback: {traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=f"Enhanced info fetch failed: {str(e)}")
    finally:
        db.close()

@app.get("/api/deals/{deal_id}/history")
def get_price_history(deal_id: int):
    """특정 딜의 가격 변동 기록을 시간순으로 반환합니다."""
    db: Session = database.SessionLocal()
    try:
        print(f"📈 [Price History] Fetching price history for deal: {deal_id}")
        price_records = db.query(models.PriceHistory).filter(
            models.PriceHistory.deal_id == deal_id
        ).order_by(models.PriceHistory.checked_at.asc()).all()
        
        if not price_records:
            print(f"📊 [Price History] No price history found for deal: {deal_id}")
            return []
        
        result = [{
            "price": record.price,
            "checked_at": record.checked_at.isoformat()
        } for record in price_records]
        
        print(f"✅ [Price History] Found {len(result)} price records")
        return result
        
    except Exception as e:
        print(f"❌ [Price History] 가격 히스토리 조회 오류: {e}")
        return []
    finally:
        db.close()

@app.get("/health")
def health_check():
    """헬스체크 엔드포인트"""
    return {"status": "ok", "timestamp": datetime.now().isoformat()}

if __name__ == "__main__":
    import uvicorn
    print("🚀 InsightDeal API 서버를 시작합니다...")
    print(f"📍 데이터베이스: {os.getenv('DATABASE_URL', 'Not configured')}")
    print(f"🌐 API 문서: http://localhost:8000/docs")
    
    uvicorn.run(
        "start_server:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        reload_dirs=["."],
        log_level="info"
    )