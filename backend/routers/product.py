from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session
from database.session import get_db_session
from database.models import Product, ProductPriceHistory
from models.product_models import ProductLinkRequest, ProductAnalysisResponse
from services.product_analyzer_service import ProductAnalyzerService

router = APIRouter()

@router.post("/analyze-link", response_model=ProductAnalysisResponse)
async def analyze_link(
    req: ProductLinkRequest,
):
    return await ProductAnalyzerService.analyze_product_link(req)

@router.get("/{product_id}/history")
async def get_price_history(
    product_id: int,
    db: Session = Depends(get_db_session)
):
    """상품 가격 히스토리 조회"""
    history = db.query(ProductPriceHistory).filter(
        ProductPriceHistory.product_id == product_id
    ).order_by(ProductPriceHistory.tracked_at.desc()).all()
    
    return [
        {
            "price": h.price,
            "date": h.tracked_at.isoformat(),
            "is_available": h.is_available
        }
        for h in history
    ]

@router.post("/track")
async def track_product(
    product_id: str,
    target_price: int,
    user_id: str = "anonymous",
    db: Session = Depends(get_db_session)
):
    """상품 추적 등록"""
    # 기존 상품 확인
    product = db.query(Product).filter(
        Product.product_id == product_id,
        Product.user_id == user_id
    ).first()
    
    if product:
        product.is_tracking = True
        product.target_price = target_price
    else:
        # 새 상품 등록 로직 (실제로는 analyze_link 결과를 바탕으로 등록해야 함)
        pass
        
    db.commit()
    return {"status": "success", "message": "Tracking started"}
