from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from database.session import get_db_session
from database import models
import logging
import os
from datetime import datetime, timedelta

router = APIRouter()
logger = logging.getLogger(__name__)

# BASE_URL for image URLs
BASE_URL = os.getenv("BASE_URL", "http://192.168.0.4:8000") # 사용자 환경에 맞춰 IP 변경

# Mock data for testing (Fallback)
MOCK_DEALS = [
    {
        "id": 1,
        "title": "[쿠팡] 갤럭시 버즈 프로",
        "price": "99,000원",
        "originalPrice": "229,000원",
        "discountRate": 57,
        "mallName": "쿠팡",
        "imageUrl": "https://thumbnail6.coupangcdn.com/thumbnails/remote/230x230ex/image/retail/images/2021/01/15/10/6/34005d36-d66a-44e6-b040-7c2d76555541.jpg",
        "category": "가전",
        "communityName": "뽐뿌",
        "shippingFee": "무료배송",
        "likeCount": 142,
        "commentCount": 23,
        "timeAgo": "5분 전",
        "link": "https://www.coupang.com"
    },
    {
        "id": 2,
        "title": "[11번가] 에어팟 프로 2세대",
        "price": "258,000원",
        "originalPrice": "359,000원",
        "discountRate": 28,
        "mallName": "11번가",
        "imageUrl": "https://store.storeimages.cdn-apple.com/8756/as-images.apple.com/is/MQD83?wid=572&hei=572&fmt=jpeg&qlt=95&.v=1660803972361",
        "category": "가전",
        "communityName": "클리앙",
        "shippingFee": "무료배송",
        "likeCount": 89,
        "commentCount": 15,
        "timeAgo": "12분 전",
        "link": "https://11st.co.kr"
    },
    {
        "id": 3,
        "title": "[다이슨] V12 무선청소기",
        "price": "459,000원",
        "originalPrice": "799,000원",
        "discountRate": 42,
        "mallName": "G마켓",
        "imageUrl": "https://dyson-h.assetsadobe2.com/is/image/content/dam/dyson/images/products/vacuum-cleaners/dyson-v12-detect-slim/v12-detect-slim-gold/640x640/V12-Detect-Slim-Gold_640x640.jpg",
        "category": "가전",
        "communityName": "펨코",
        "shippingFee": "2,500원",
        "likeCount": 256,
        "commentCount": 41,
        "timeAgo": "1시간 전",
        "link": "https://gmarket.co.kr"
    }
]

def calculate_time_ago(dt):
    if not dt:
        return ""
    now = datetime.now(dt.tzinfo)
    diff = now - dt
    
    seconds = diff.total_seconds()
    if seconds < 60:
        return "방금 전"
    elif seconds < 3600:
        return f"{int(seconds // 60)}분 전"
    elif seconds < 86400:
        return f"{int(seconds // 3600)}시간 전"
    else:
        return f"{int(seconds // 86400)}일 전"

@router.get("/hot-deals")
async def get_hot_deals(db: Session = Depends(get_db_session)):
    """
    커뮤니티 핫딜 목록 조회 (DB 연동)
    """
    try:
        # DB에서 최신 딜 50개 조회
        deals = (
            db.query(models.Deal)
            .join(models.Community)
            .order_by(models.Deal.indexed_at.desc())
            .limit(50)
            .all()
        )

        if not deals:
            logger.info("No deals found in DB, returning mock data.")
            return {"deals": MOCK_DEALS}

        result = []
        for deal in deals:
            # 이미지 URL 처리
            image_url = deal.image_url
            if image_url and image_url.startswith("/images/"):
                image_url = f"{BASE_URL}{image_url}"
            elif image_url and not image_url.startswith("http"):
                 image_url = f"{BASE_URL}/images/{image_url}"

            result.append({
                "id": deal.id,
                "title": deal.title,
                "price": deal.price,
                "originalPrice": None, 
                "discountRate": 0,
                "mallName": deal.shop_name or "알수없음",
                "imageUrl": image_url,
                "category": deal.category,
                "communityName": deal.community.name,
                "shippingFee": deal.shipping_fee,
                "likeCount": 0,
                "commentCount": 0,
                "timeAgo": calculate_time_ago(deal.indexed_at),
                "link": deal.post_link
            })
            
        return {"deals": result}

    except Exception as e:
        logger.error(f"Error fetching hot deals: {e}", exc_info=True)
        # DB 오류 시 Mock 데이터 반환
        return {"deals": MOCK_DEALS}
