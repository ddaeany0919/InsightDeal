import os
from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles
from sqlalchemy.orm import Session
import database
import models

# --- FastAPI 앱 설정 ---
app = FastAPI()

# 이미지 폴더 경로 설정 및 static 폴더로 마운트
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_CACHE_DIR = os.path.join(SCRIPT_DIR, "image_cache")
if not os.path.exists(IMAGE_CACHE_DIR):
    os.makedirs(IMAGE_CACHE_DIR)
app.mount("/images", StaticFiles(directory=IMAGE_CACHE_DIR), name="images")


# --- 서버 시작 시 이벤트 ---
@app.on_event("startup")
def startup_event():
    # DB 테이블이 없으면 생성합니다.
    database.Base.metadata.create_all(bind=database.engine)


# --- API 엔드포인트 정의 ---

@app.get("/")
def read_root():
    """서버가 살아있는지 확인하기 위한 기본 경로입니다."""
    return {"message": "InsightDeal API Server is running!"}

@app.get("/api/deals")
def get_deals_list(page: int = 1, page_size: int = 20):
    """DB에서 딜 목록을 페이지별로 조회하여 반환합니다."""
    db: Session = database.SessionLocal()
    try:
        offset = (page - 1) * page_size
        deals_from_db = db.query(models.Deal).order_by(models.Deal.id.desc()).offset(offset).limit(page_size).all()
        
        # --- ✨ 수정: is_closed, deal_type 필드 추가 ---
        results = [{
            "id": str(deal.id),
            "title": deal.title,
            "community": deal.community.name,
            "shopName": deal.shop_name,
            "price": deal.price,
            "shippingFee": deal.shipping_fee,
            "imageUrl": deal.image_url,
            "category": deal.category,
            "is_closed": deal.is_closed, # 품절/종료 여부
            "deal_type": deal.deal_type  # 딜 유형 (일반/이벤트)
        } for deal in deals_from_db]
        
        return results
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
        
        # --- ✨ 수정: 반환하는 필드를 최신 모델에 맞게 수정 ---
        return {
            "id": str(deal.id),
            "title": deal.title,
            "community": deal.community.name,
            "shop_name": deal.shop_name,
            "price": deal.price,
            "shipping_fee": deal.shipping_fee,
            "category": deal.category,        # 카테고리 추가
            "post_link": deal.post_link,        # 출처 링크 추가
            "ecommerce_link": deal.ecommerce_link, # 상품 링크로 변경
            "content_html": deal.content_html
        }
    finally:
        db.close()

@app.get("/api/deals/{deal_id}/history")
def get_price_history(deal_id: int):
    """특정 딜의 가격 변동 기록을 시간순으로 반환합니다."""
    db: Session = database.SessionLocal()
    try:
        price_records = db.query(models.PriceHistory).filter(models.PriceHistory.deal_id == deal_id).order_by(models.PriceHistory.checked_at.asc()).all()
        if not price_records:
            return []
        return [{
            "price": record.price,
            "checked_at": record.checked_at.isoformat()
        } for record in price_records]
    finally:
        db.close()