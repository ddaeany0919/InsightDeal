from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from database.session import get_db_session
import logging
import os

router = APIRouter()
logger = logging.getLogger(__name__)

# BASE_URL for image URLs
BASE_URL = os.getenv("BASE_URL", "http://localhost:8000")

# Mock data for testing (AI 파서 없이 빠르게 응답)
MOCK_DEALS = [
    {
        "id": 1,
        "title": "[쿠팡] 갤럭시 버즈 프로",
        "price": "99,000원",
        "originalPrice": "229,000원",
        "discountRate": 57,
        "mallName": "쿠팡",
        "imageUrl": None,
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
        "imageUrl": None,
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
        "imageUrl": None,
        "category": "가전",
        "communityName": "펨코",
        "shippingFee": "2,500원",
        "likeCount": 256,
        "commentCount": 41,
        "timeAgo": "1시간 전",
        "link": "https://gmarket.co.kr"
    }
]

@router.get("/hot-deals")
async def get_hot_deals(db: Session = Depends(get_db_session)):
    """
    커뮤니티 핫딜 목록 (Mock 데이터 - AI 파서 없이 빠른 응답)
    """
    try:
        logger.info("Returning mock hot deals data (fast mode)")
        return {"deals": MOCK_DEALS}
    except Exception as e:
        logger.error(f"Error fetching hot deals: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
