import os
from contextlib import asynccontextmanager
from dotenv import load_dotenv
from datetime import datetime  # ✅ 추가 필요

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

# --- API 엔드포인트 ---

@app.get("/")
def read_root():
    return {"message": "InsightDeal API Server is running!", "version": "2.1"}

@app.get("/api/deals")
def get_deals_list(page: int = 1, page_size: int = 20, community_id: int = None):
    """딜 목록 조회 (500 오류 해결 버전)"""
    db: Session = database.SessionLocal()
    try:
        offset = (page - 1) * page_size
        
        # 기본 쿼리 구성
        query = db.query(models.Deal)
        
        # 커뮤니티 필터링 (옵션)
        if community_id:
            query = query.filter(models.Deal.source_community_id == community_id)
        
        # 관계 데이터 미리 로드 및 정렬
        deals_from_db = (query
                        .options(joinedload(models.Deal.community))
                        .order_by(models.Deal.indexed_at.desc())
                        .offset(offset)
                        .limit(page_size)
                        .all())
        
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
                print(f"⚠️ Deal {deal.id} 처리 중 오류 (건너뜀): {deal_error}")
                continue
        
        print(f"✅ {len(results)}개 딜 반환")
        return results
        
    except Exception as e:
        print(f"❌ 딜 조회 실패: {e}")
        import traceback
        traceback.print_exc()
        return []
        
    finally:
        db.close()

@app.get("/api/communities")  
def get_communities():
    """커뮤니티 목록 조회"""
    db: Session = database.SessionLocal()
    try:
        communities = db.query(models.Community).all()
        return [{
            "id": comm.id,
            "name": comm.name,
            "base_url": comm.base_url
        } for comm in communities]
    except Exception as e:
        print(f"❌ 커뮤니티 조회 실패: {e}")
        return []
    finally:
        db.close()

@app.get("/api/deals/{deal_id}")
def get_deal_detail(deal_id: int):
    """특정 딜의 상세 정보를 반환합니다."""
    db: Session = database.SessionLocal()
    try:
        deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
        if not deal:
            raise HTTPException(status_code=404, detail="Deal not found")
        
        # 커뮤니티 정보 가져오기
        community = db.query(models.Community).filter(
            models.Community.id == deal.source_community_id
        ).first()
        
        return {
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
            "isClosed": getattr(deal, 'is_closed', False)
        }
    except Exception as e:
        print(f"❌ 딜 상세 조회 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()

@app.get("/api/deals/{deal_id}/history")
def get_price_history(deal_id: int):
    """특정 딜의 가격 변동 기록을 시간순으로 반환합니다."""
    db: Session = database.SessionLocal()
    try:
        price_records = db.query(models.PriceHistory).filter(
            models.PriceHistory.deal_id == deal_id
        ).order_by(models.PriceHistory.checked_at.asc()).all()
        
        if not price_records:
            return []
        
        return [{
            "price": record.price,
            "checked_at": record.checked_at.isoformat()
        } for record in price_records]
        
    except Exception as e:
        print(f"❌ 가격 히스토리 조회 오류: {e}")
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
        log_level="info"
    )
