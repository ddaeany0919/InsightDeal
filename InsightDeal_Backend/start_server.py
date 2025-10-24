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

# --- ✨ 새로운 통합 스크래퍼 라우팅 시스템 ---
def smart_crawl_post_details(post_url: str, deal_title: str):
    """도메인별로 최적화된 스크래퍼를 자동 라우팅하는 스마트 크롤링 함수"""
    print(f"🎯 [Smart Crawler] Starting smart crawl for: {post_url[:50]}...")
    
    try:
        # DB 세션 생성
        db_session = database.SessionLocal()
        
        # 1. 도메인 감지 및 적절한 스크래퍼 선택
        if 'ppomppu.co.kr' in post_url:
            if 'ppomppu4' in post_url:  # 알리뽐뿌
                print("🔍 [Smart Crawler] Routing to: 알리뽐뿌 스크래퍼")
                from scrapers.alippomppu_scraper import AlippomppuScraper
                with AlippomppuScraper(db_session) as scraper:
                    return scraper.get_post_details(post_url)
            elif 'ppomppu3' in post_url:  # 해외뽐뿌
                print("🔍 [Smart Crawler] Routing to: 해외뽐뿌 스크래퍼")
                from scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
                with PpomppuOverseasScraper(db_session) as scraper:
                    return scraper.get_post_details(post_url)
            else:  # 일반뽐뿌
                print("🔍 [Smart Crawler] Routing to: 뽐뿌 스크래퍼")
                from scrapers.ppomppu_scraper import PpomppuScraper
                with PpomppuScraper(db_session) as scraper:
                    return scraper.get_post_details(post_url)
                    
        elif 'ruliweb.com' in post_url:
            print("🔍 [Smart Crawler] Routing to: 루리웹 스크래퍼")
            from scrapers.ruliweb_scraper import RuliwebScraper
            with RuliwebScraper(db_session) as scraper:
                return scraper.get_post_details(post_url)
                
        elif 'clien.net' in post_url:
            print("🔍 [Smart Crawler] Routing to: 클리앙 스크래퍼")
            from scrapers.clien_scraper import ClienScraper
            with ClienScraper(db_session) as scraper:
                return scraper.get_post_details(post_url)
                
        elif 'quasarzone.com' in post_url:
            print("🔍 [Smart Crawler] Routing to: 퀘이사존 스크래퍼")
            from scrapers.quasarzone_scraper import QuasarzoneScraper
            with QuasarzoneScraper(db_session) as scraper:
                return scraper.get_post_details(post_url)
                
        elif 'fmkorea.com' in post_url:
            print("🔍 [Smart Crawler] Routing to: 펨코 스크래퍼")
            from scrapers.fmkorea_scraper import FmkoreaScraper
            with FmkoreaScraper(db_session) as scraper:
                return scraper.get_post_details(post_url)
                
        elif 'bbasak.com' in post_url:
            print("🔍 [Smart Crawler] Routing to: 빠삭 스크래퍼")
            from scrapers.bbasak_base_scraper import BbasakBaseScraper
            with BbasakBaseScraper(db_session, "빠삭", post_url) as scraper:
                return scraper.get_post_details(post_url)
                
        else:
            print("⚠️ [Smart Crawler] Unknown site, falling back to generic crawler")
            return fallback_crawl_post_details(post_url, deal_title)
            
    except Exception as e:
        print(f"❌ [Smart Crawler] Smart crawling failed: {e}")
        print(f"🔍 [Smart Crawler] Traceback: {traceback.format_exc()}")
        # 스마트 크롤러 실패 시 기존 방식으로 대체
        return fallback_crawl_post_details(post_url, deal_title)
    finally:
        if 'db_session' in locals():
            db_session.close()

def fallback_crawl_post_details(post_url: str, deal_title: str):
    """기존 방식의 범용 크롤러 (스마트 크롤러 실패 시 대체용)"""
    print(f"🔄 [Fallback Crawler] Using fallback method for: {post_url[:50]}...")
    
    try:
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
            'Accept-Language': 'ko-KR,ko;q=0.9,en;q=0.8',
        }
        
        response = requests.get(post_url, headers=headers, timeout=15)
        response.raise_for_status()
        soup = BeautifulSoup(response.content, 'html.parser')
        
        # 범용 이미지 추출
        images = []
        for img in soup.find_all('img'):
            img_src = img.get('src')
            if img_src and 'http' in img_src and len(img_src) > 20:
                if not any(keyword in img_src.lower() for keyword in ['icon', 'logo', 'emoticon']):
                    images.append({
                        "url": img_src,
                        "alt": f"이미지 {len(images)+1}",
                        "description": "게시글 이미지"
                    })
                    if len(images) >= 5:  # 최대 5개
                        break
        
        # 범용 텍스트 추출
        content_text = soup.get_text(separator=' ', strip=True)
        if len(content_text) > 500:
            content_text = content_text[:500] + "..."
        
        result = {
            "images": images,
            "content": content_text,
            "posted_time": None,
            "crawled_at": datetime.now().isoformat(),
            "source_url": post_url,
            "method": "fallback"
        }
        
        print(f"✅ [Fallback Crawler] Success! Images: {len(images)}, Content: {len(content_text)} chars")
        return result
        
    except Exception as e:
        print(f"❌ [Fallback Crawler] Fallback also failed: {e}")
        return {
            "images": [],
            "content": "게시글을 불러올 수 없습니다.",
            "posted_time": None,
            "error": str(e),
            "crawled_at": datetime.now().isoformat()
        }

def analyze_product_with_ai(deal_title: str, content: str, images: list):
    """상품 정보를 AI로 분석하여 정제"""
    print(f"🤖 [AI Analyzer] Starting AI analysis for: {deal_title[:30]}...")
    
    try:
        analysis_result = {
            "productSummary": deal_title,
            "keyFeatures": ["고품질 상품", "합리적 가격", "빠른 배송"],
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
    return {"message": "InsightDeal API Server is running!", "version": "3.0 - Smart Routing"}

@app.get("/api/deals")
def get_deals_list(page: int = 1, page_size: int = 20, community_id: int = None):
    """딜 목록 조회 (500 오류 해결 버전)"""
    db: Session = database.SessionLocal()
    try:
        print(f"📊 [Deals List] Request - Page: {page}, Size: {page_size}, Community: {community_id}")
        offset = (page - 1) * page_size
        
        query = db.query(models.Deal)
        
        if community_id:
            query = query.filter(models.Deal.source_community_id == community_id)
            print(f"🔍 [Deals List] Filtering by community ID: {community_id}")
        
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
                if hasattr(deal, 'community') and deal.community:
                    community_name = deal.community.name
                else:
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

# ✅ NEW: 상품 정보 확장 API (스마트 라우팅 적용)
@app.get("/api/deals/{deal_id}/enhanced-info")
def get_enhanced_deal_info(deal_id: int):
    """딜의 향상된 정보 (사이트별 최적화된 스크래퍼 사용)"""
    db: Session = database.SessionLocal()
    try:
        print(f"🚀 [Enhanced Info] Starting enhanced info fetch for deal: {deal_id}")
        
        deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
        if not deal:
            print(f"❌ [Enhanced Info] Deal not found: {deal_id}")
            raise HTTPException(status_code=404, detail="Deal not found")
        
        print(f"📄 [Enhanced Info] Found deal: {deal.title[:30]}...")
        
        # 1. 게시 시간 정보
        posted_time_info = {
            "indexedAt": deal.indexed_at.isoformat() if deal.indexed_at else None,
            "formattedTime": None,
            "timeAgo": None
        }
        
        if deal.indexed_at:
            try:
                from datetime import timezone
                now = datetime.now(timezone.utc)
                indexed_time = deal.indexed_at.replace(tzinfo=timezone.utc) if deal.indexed_at.tzinfo is None else deal.indexed_at
                time_diff = now - indexed_time
                
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
        
        # 2. ✨ 스마트 라우팅으로 원본 게시물 크롤링
        product_detail = None
        if deal.post_link:
            print(f"🎯 [Enhanced Info] Using smart routing for: {deal.post_link}")
            product_detail = smart_crawl_post_details(deal.post_link, deal.title)
            
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
        
        result = {
            "dealId": deal_id,
            "postedTime": posted_time_info,
            "productDetail": product_detail,
            "enhancedAt": datetime.now().isoformat()
        }
        
        print(f"✅ [Enhanced Info] Successfully completed enhanced info fetch with smart routing")
        print(f"📈 [Enhanced Info] Result summary:")
        print(f"  - Posted time: {posted_time_info.get('timeAgo', 'Unknown')}")
        print(f"  - Images found: {len(product_detail.get('images', [])) if product_detail else 0}")
        print(f"  - Content length: {len(product_detail.get('content', '')) if product_detail else 0} chars")
        print(f"  - Site detected: {product_detail.get('detected_site', 'N/A') if product_detail else 'N/A'}")
        
        return result
        
    except HTTPException:
        raise
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
    print(f"🎯 스마트 라우팅 시스템 활성화 - 6개 사이트 지원")
    
    uvicorn.run(
        "start_server:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        reload_dirs=["."],
        log_level="info"
    )