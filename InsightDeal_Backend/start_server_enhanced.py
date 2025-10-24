import os
from contextlib import asynccontextmanager
from dotenv import load_dotenv
from datetime import datetime
from typing import List, Dict, Any, Optional
import traceback

# ✅ 환경변수 먼저 로드
load_dotenv()

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from sqlalchemy.orm import Session, joinedload
from sqlalchemy import desc, func
import database
import models
from coupang_tracker import CoupangTracker
from firebase_config import send_fcm_notification

# ✅ Lifespan 이벤트 핸들러 (최신 방식)
@asynccontextmanager
async def lifespan(app: FastAPI):
    # 서버 시작 시
    print("🚀 InsightDeal API 서버 시작 (Enhanced)")
    database.Base.metadata.create_all(bind=database.engine)
    yield
    # 서버 종료 시
    print("🛑 InsightDeal API 서버 종료")

# --- FastAPI 앱 설정 ---
app = FastAPI(
    title="InsightDeal API",
    description="커뮤니티 핫딜 + 쿠팡 가격 추적 통합 API",
    version="2.0.0",
    lifespan=lifespan
)

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

# --- Pydantic 모델들 ---
class ProductRequest(BaseModel):
    url: str
    target_price: int
    user_id: str = "anonymous"

class TargetPriceRequest(BaseModel):
    target_price: int

class FCMTokenRequest(BaseModel):
    token: str
    user_id: str = "anonymous"
    device_info: Optional[str] = None
    app_version: Optional[str] = None

class TestPushRequest(BaseModel):
    token: str
    title: Optional[str] = "테스트 알림"
    body: Optional[str] = "InsightDeal 푸시 알림 테스트입니다!"

# --- 기존 커뮤니티 핫딜 API (유지) ---

@app.get("/")
def read_root():
    return {
        "message": "InsightDeal API Server Enhanced", 
        "version": "2.0.0",
        "features": ["Community Deals", "Coupang Tracking", "FCM Push"]
    }

@app.get("/api/deals")
def get_deals_list(page: int = 1, page_size: int = 20, community_id: int = None):
    """기존 커뮤니티 핫딜 목록 조회"""
    db: Session = database.SessionLocal()
    try:
        offset = (page - 1) * page_size
        query = db.query(models.Deal)
        
        if community_id:
            query = query.filter(models.Deal.source_community_id == community_id)
        
        deals = query.order_by(desc(models.Deal.indexed_at)).offset(offset).limit(page_size).all()
        
        deals_list = []
        for deal in deals:
            deals_list.append({
                "id": deal.id,
                "title": deal.title,
                "post_link": deal.post_link,
                "ecommerce_link": deal.ecommerce_link,
                "shop_name": deal.shop_name,
                "price": deal.price,
                "shipping_fee": deal.shipping_fee,
                "image_url": deal.image_url,
                "category": deal.category,
                "indexed_at": deal.indexed_at.isoformat() if deal.indexed_at else None,
                "community_name": deal.community.name if deal.community else None
            })
        
        return deals_list
        
    except Exception as e:
        print(f"❌ Error in get_deals_list: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()

# --- 새로운 쿠팡 상품 추적 API ---

@app.post("/api/products")
def add_coupang_product(request: ProductRequest):
    """새 쿠팡 상품 추가"""
    db: Session = database.SessionLocal()
    try:
        print(f"🛒 [Add Product] URL: {request.url}, Target: {request.target_price:,}원")
        
        # 쿠팡 크롤러로 상품 정보 추출
        tracker = CoupangTracker()
        product_info = tracker.extract_product_info(request.url)
        
        if not product_info:
            raise HTTPException(status_code=400, detail="쿠팡 상품 정보를 추출할 수 없습니다")
        
        # DB에 상품 저장
        new_product = models.Product(
            user_id=request.user_id,
            product_id=product_info.get("product_id"),
            url=request.url,
            title=product_info.get("title", "상품명 없음"),
            brand=product_info.get("brand"),
            image_url=product_info.get("image_url"),
            current_price=product_info.get("current_price", 0),
            original_price=product_info.get("original_price"),
            target_price=request.target_price
        )
        
        db.add(new_product)
        db.commit()
        db.refresh(new_product)
        
        print(f"✅ [Add Product] Success: {new_product.title}")
        
        return {
            "id": new_product.id,
            "title": new_product.title,
            "current_price": new_product.current_price,
            "target_price": new_product.target_price,
            "message": "상품이 성공적으로 추가되었습니다"
        }
        
    except HTTPException:
        raise
    except Exception as e:
        print(f"❌ Error adding product: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail=f"상품 추가 중 오류: {str(e)}")
    finally:
        db.close()

@app.get("/api/products")
def get_user_products(user_id: str = "anonymous"):
    """사용자 쿠팡 상품 목록 조회"""
    db: Session = database.SessionLocal()
    try:
        products = db.query(models.Product).filter(
            models.Product.user_id == user_id,
            models.Product.is_tracking == True
        ).all()
        
        products_list = []
        for product in products:
            products_list.append({
                "id": product.id,
                "title": product.title,
                "brand": product.brand,
                "image_url": product.image_url,
                "current_price": product.current_price,
                "original_price": product.original_price,
                "lowest_price": product.lowest_price,
                "highest_price": product.highest_price,
                "target_price": product.target_price,
                "url": product.url,
                "created_at": product.created_at.isoformat() if product.created_at else None,
                "last_checked": product.last_checked.isoformat() if product.last_checked else None
            })
        
        return products_list
        
    except Exception as e:
        print(f"❌ Error getting products: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()

@app.get("/api/products/{product_id}")
def get_product_detail(product_id: int):
    """특정 상품 정보 조회"""
    db: Session = database.SessionLocal()
    try:
        product = db.query(models.Product).filter(models.Product.id == product_id).first()
        
        if not product:
            raise HTTPException(status_code=404, detail="상품을 찾을 수 없습니다")
        
        return {
            "id": product.id,
            "title": product.title,
            "brand": product.brand,
            "image_url": product.image_url,
            "current_price": product.current_price,
            "original_price": product.original_price,
            "lowest_price": product.lowest_price,
            "highest_price": product.highest_price,
            "target_price": product.target_price,
            "url": product.url,
            "created_at": product.created_at.isoformat() if product.created_at else None,
            "last_checked": product.last_checked.isoformat() if product.last_checked else None
        }
        
    except HTTPException:
        raise
    except Exception as e:
        print(f"❌ Error getting product detail: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()

@app.get("/api/products/{product_id}/history")
def get_product_price_history(product_id: int):
    """상품 가격 히스토리 조회"""
    db: Session = database.SessionLocal()
    try:
        history = db.query(models.ProductPriceHistory).filter(
            models.ProductPriceHistory.product_id == product_id
        ).order_by(models.ProductPriceHistory.tracked_at).all()
        
        history_list = []
        for item in history:
            history_list.append({
                "price": item.price,
                "original_price": item.original_price,
                "discount_rate": item.discount_rate,
                "tracked_at": item.tracked_at.isoformat() if item.tracked_at else None,
                "is_available": item.is_available
            })
        
        return history_list
        
    except Exception as e:
        print(f"❌ Error getting price history: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()

@app.put("/api/products/{product_id}/target")
def update_target_price(product_id: int, request: TargetPriceRequest):
    """목표 가격 업데이트"""
    db: Session = database.SessionLocal()
    try:
        product = db.query(models.Product).filter(models.Product.id == product_id).first()
        
        if not product:
            raise HTTPException(status_code=404, detail="상품을 찾을 수 없습니다")
        
        product.target_price = request.target_price
        db.commit()
        db.refresh(product)
        
        print(f"✅ [Update Target] Product {product_id}: {request.target_price:,}원")
        
        return {
            "id": product.id,
            "title": product.title,
            "target_price": product.target_price,
            "message": "목표 가격이 성공적으로 업데이트되었습니다"
        }
        
    except HTTPException:
        raise
    except Exception as e:
        print(f"❌ Error updating target price: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()

@app.delete("/api/products/{product_id}")
def delete_product(product_id: int):
    """상품 추적 삭제"""
    db: Session = database.SessionLocal()
    try:
        product = db.query(models.Product).filter(models.Product.id == product_id).first()
        
        if not product:
            raise HTTPException(status_code=404, detail="상품을 찾을 수 없습니다")
        
        # 소프트 삭제 (추적 중지)
        product.is_tracking = False
        db.commit()
        
        print(f"✅ [Delete Product] Success: {product.title}")
        
        return {"message": "상품 추적이 중지되었습니다"}
        
    except HTTPException:
        raise
    except Exception as e:
        print(f"❌ Error deleting product: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()

# --- FCM 토큰 관리 API ---

@app.post("/api/fcm/register")
def register_fcm_token(request: FCMTokenRequest):
    """FCM 토큰 등록"""
    db: Session = database.SessionLocal()
    try:
        # 기존 토큰 확인
        existing_token = db.query(models.FCMToken).filter(
            models.FCMToken.token == request.token
        ).first()
        
        if existing_token:
            # 기존 토큰 업데이트
            existing_token.user_id = request.user_id
            existing_token.device_info = request.device_info
            existing_token.app_version = request.app_version
            existing_token.last_used = func.now()
            existing_token.is_active = True
        else:
            # 새 토큰 생성
            new_token = models.FCMToken(
                user_id=request.user_id,
                token=request.token,
                device_info=request.device_info,
                app_version=request.app_version
            )
            db.add(new_token)
        
        db.commit()
        print(f"✅ [FCM Register] Token registered for user: {request.user_id}")
        
        return {"message": "FCM 토큰이 성공적으로 등록되었습니다"}
        
    except Exception as e:
        print(f"❌ Error registering FCM token: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()

@app.post("/api/fcm/test")
def send_test_push(request: TestPushRequest):
    """테스트 푸시 알림 전송"""
    try:
        result = send_fcm_notification(
            token=request.token,
            title=request.title,
            body=request.body,
            data={"type": "test", "timestamp": datetime.now().isoformat()}
        )
        
        if result:
            return {"message": "테스트 푸시 알림이 성공적으로 전송되었습니다"}
        else:
            raise HTTPException(status_code=500, detail="푸시 알림 전송에 실패했습니다")
            
    except Exception as e:
        print(f"❌ Error sending test push: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# --- 기존 딜 상세정보 API (유지) ---

@app.get("/api/deals/{deal_id}")
def get_deal_detail(deal_id: int):
    """딜 상세정보 조회"""
    db: Session = database.SessionLocal()
    try:
        deal = db.query(models.Deal).options(joinedload(models.Deal.community)).filter(models.Deal.id == deal_id).first()
        
        if not deal:
            raise HTTPException(status_code=404, detail="Deal not found")
        
        return {
            "id": deal.id,
            "title": deal.title,
            "post_link": deal.post_link,
            "ecommerce_link": deal.ecommerce_link,
            "shop_name": deal.shop_name,
            "price": deal.price,
            "shipping_fee": deal.shipping_fee,
            "image_url": deal.image_url,
            "category": deal.category,
            "indexed_at": deal.indexed_at.isoformat() if deal.indexed_at else None,
            "is_closed": deal.is_closed,
            "community_name": deal.community.name if deal.community else None,
            "content_html": deal.content_html
        }
        
    except HTTPException:
        raise
    except Exception as e:
        print(f"❌ Error getting deal detail: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()

# --- 서버 실행 ---
if __name__ == "__main__":
    import uvicorn
    print("🚀 InsightDeal Enhanced API 서버 시작...")
    print("📱 Android 앱에서 접속 가능")
    print("🔥 커뮤니티 핫딜 + 쿠팡 추적 + FCM 푸시 지원")
    
    uvicorn.run(
        "start_server_enhanced:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        log_level="info"
    )