from datetime import datetime, timezone
import logging
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session
from backend.core.device_auth import assert_device_owner, require_device_token
from backend.core.utils import to_utc_iso

try:
    from backend.database.session import get_db
    from backend.models.wishlist_models import WishlistCreate, WishlistUpdate, WishlistResponse, Wishlist
    from backend.services.wishlist_service import WishlistService
except ImportError:
    from database.session import get_db
    from models.wishlist_models import WishlistCreate, WishlistUpdate, WishlistResponse, Wishlist
    from services.wishlist_service import WishlistService

router = APIRouter(tags=["wishlist"])
logger = logging.getLogger(__name__)


class WishlistCreateFromURL(BaseModel):
    product_url: str
    target_price: int
    user_id: Optional[str] = None


class WishlistCreateFromKeyword(BaseModel):
    keyword: str
    target_price: int
    user_id: Optional[str] = None


class WishlistItemAlertRequest(BaseModel):
    keyword: Optional[str] = None
    title: Optional[str] = None
    product_url: Optional[str] = None
    target_price: int
    current_price: Optional[int] = None
    platform: Optional[str] = None
    user_id: Optional[str] = None


class WishlistUpdateRequest(BaseModel):
    target_price: Optional[int] = None
    alert_enabled: Optional[bool] = None


def _resolve_wishlist_owner(auth_uuid: str, requested_user_id: Optional[str]) -> str:
    target = (requested_user_id or "").strip()
    if target:
        if target.lower() == "default" and auth_uuid.lower() != "default":
            raise HTTPException(
                status_code=403,
                detail="X-Device-Token does not match target device_uuid/user_id",
            )
        assert_device_owner(auth_uuid, target)
        return target
    return auth_uuid


def _serialize_wishlist(item: Wishlist) -> dict:
    return {
        "id": item.id,
        "keyword": item.keyword,
        "target_price": item.target_price,
        "user_id": item.user_id,
        "is_active": item.is_active,
        "alert_enabled": item.alert_enabled,
        "created_at": to_utc_iso(item.created_at),
        "updated_at": to_utc_iso(item.updated_at),
    }


@router.post("/item-alert")
def create_or_update_item_alert(
    data: WishlistItemAlertRequest,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db),
):
    """1-RTT 원스톱 목표가 알림 등록 및 갱신 (Upsert)"""
    owner_id = _resolve_wishlist_owner(auth_uuid, data.user_id)
    key_str = (data.keyword or data.title or data.product_url or "").strip()
    if not key_str:
        raise HTTPException(status_code=400, detail="상품명(keyword/title) 또는 product_url이 필요합니다.")
    try:
        existing = db.query(Wishlist).filter(
            Wishlist.user_id == owner_id,
            Wishlist.keyword == key_str,
        ).first()
        if existing:
            existing.target_price = data.target_price
            existing.is_active = True
            existing.alert_enabled = True
            existing.updated_at = datetime.now(timezone.utc).replace(tzinfo=None)
            db.commit()
            db.refresh(existing)
            return _serialize_wishlist(existing)

        wishlist = Wishlist(
            keyword=key_str,
            target_price=data.target_price,
            user_id=owner_id,
            is_active=True,
            alert_enabled=True,
        )
        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)
        return _serialize_wishlist(wishlist)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_ITEM_ALERT] 오류: {str(e)}", exc_info=True)
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/from-url")
def create_wishlist_from_url(
    data: WishlistCreateFromURL,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db),
):
    """URL 기반 위시리스트 추가 (이미 존재 시 목표가 갱신)"""
    owner_id = _resolve_wishlist_owner(auth_uuid, data.user_id)
    logger.info(f"[WISHLIST_URL] URL 등록: {data.product_url}")
    try:
        existing = db.query(Wishlist).filter(
            Wishlist.user_id == owner_id,
            Wishlist.keyword == data.product_url,
        ).first()

        if existing:
            existing.target_price = data.target_price
            existing.is_active = True
            existing.alert_enabled = True
            existing.updated_at = datetime.now(timezone.utc).replace(tzinfo=None)
            db.commit()
            db.refresh(existing)
            return _serialize_wishlist(existing)

        wishlist = Wishlist(
            keyword=data.product_url,
            target_price=data.target_price,
            user_id=owner_id,
        )
        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)
        logger.info(f"[WISHLIST_URL] 등록 성공: ID={wishlist.id}")
        return _serialize_wishlist(wishlist)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_URL] 오류: {str(e)}", exc_info=True)
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/from-keyword")
def create_wishlist_from_keyword(
    data: WishlistCreateFromKeyword,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db),
):
    """키워드 기반 위시리스트 추가 (이미 존재 시 목표가 갱신)"""
    owner_id = _resolve_wishlist_owner(auth_uuid, data.user_id)
    logger.info(f"[WISHLIST_KEYWORD] 키워드 등록: {data.keyword}")
    try:
        existing = db.query(Wishlist).filter(
            Wishlist.user_id == owner_id,
            Wishlist.keyword == data.keyword,
        ).first()

        if existing:
            existing.target_price = data.target_price
            existing.is_active = True
            existing.alert_enabled = True
            existing.updated_at = datetime.now(timezone.utc).replace(tzinfo=None)
            db.commit()
            db.refresh(existing)
            return _serialize_wishlist(existing)

        wishlist = Wishlist(
            keyword=data.keyword,
            target_price=data.target_price,
            user_id=owner_id,
        )
        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)
        logger.info(f"[WISHLIST_KEYWORD] 등록 성공: ID={wishlist.id}")
        return _serialize_wishlist(wishlist)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_KEYWORD] 오류: {str(e)}", exc_info=True)
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.get("", include_in_schema=False)
@router.get("/")
def get_wishlist(
    user_id: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db),
):
    """사용자의 위시리스트 조회"""
    owner_id = _resolve_wishlist_owner(auth_uuid, user_id)
    logger.info(f"[WISHLIST_GET] 조회: user_id={owner_id}")
    try:
        wishlists = db.query(Wishlist).filter(Wishlist.user_id == owner_id).all()
        logger.info(f"[WISHLIST_GET] 조회 성공: {len(wishlists)}개")
        return [_serialize_wishlist(w) for w in wishlists]
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_GET] 오류: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/{wishlist_id}/check-price")
async def check_price(
    wishlist_id: int,
    user_id: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db),
):
    """위시리스트 가격 체크"""
    owner_id = _resolve_wishlist_owner(auth_uuid, user_id)
    logger.info(f"[WISHLIST_PRICE] 가격 체크: wishlist_id={wishlist_id}, user_id={owner_id}")
    wishlist = db.query(Wishlist).filter(Wishlist.id == wishlist_id).first()
    if not wishlist:
        raise HTTPException(status_code=404, detail="해당 위시리스트가 없습니다.")
    if wishlist.user_id != owner_id:
        raise HTTPException(status_code=403, detail="X-Device-Token does not match target device_uuid/user_id")
    try:
        result = await WishlistService.check_price(wishlist_id, owner_id, db)
        logger.info("[WISHLIST_PRICE] 가격 체크 완료")
        return result
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_PRICE] 오류: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"가격 체크 실패: {str(e)}")


@router.delete("/{wishlist_id}")
def delete_wishlist(
    wishlist_id: int,
    user_id: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db),
):
    """위시리스트 삭제"""
    owner_id = _resolve_wishlist_owner(auth_uuid, user_id)
    logger.info(f"[WISHLIST_DELETE] 삭제 시도: wishlist_id={wishlist_id}, user_id={owner_id}")
    try:
        wishlist = db.query(Wishlist).filter(Wishlist.id == wishlist_id).first()

        if not wishlist:
            logger.warning(f"[WISHLIST_DELETE] 위시리스트 없음: ID={wishlist_id}")
            raise HTTPException(status_code=404, detail="해당 위시리스트가 없습니다.")
        if wishlist.user_id != owner_id:
            raise HTTPException(status_code=403, detail="X-Device-Token does not match target device_uuid/user_id")

        db.delete(wishlist)
        db.commit()
        logger.info(f"[WISHLIST_DELETE] 삭제 성공: ID={wishlist_id}")
        return {"message": "삭제 성공"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_DELETE] 오류: {str(e)}", exc_info=True)
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.patch("/{wishlist_id}")
def update_wishlist(
    wishlist_id: int,
    data: WishlistUpdateRequest,
    user_id: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db),
):
    """위시리스트 업데이트"""
    owner_id = _resolve_wishlist_owner(auth_uuid, user_id)
    logger.info(f"[WISHLIST_UPDATE] 업데이트 시도: wishlist_id={wishlist_id}, user_id={owner_id}")
    try:
        wishlist = db.query(Wishlist).filter(Wishlist.id == wishlist_id).first()

        if not wishlist:
            logger.warning(f"[WISHLIST_UPDATE] 위시리스트 없음: ID={wishlist_id}")
            raise HTTPException(status_code=404, detail="해당 위시리스트가 없습니다.")
        if wishlist.user_id != owner_id:
            raise HTTPException(status_code=403, detail="X-Device-Token does not match target device_uuid/user_id")

        update_data = data.dict(exclude_unset=True)
        for key, value in update_data.items():
            setattr(wishlist, key, value)
        wishlist.updated_at = datetime.now(timezone.utc).replace(tzinfo=None)

        db.commit()
        db.refresh(wishlist)
        logger.info(f"[WISHLIST_UPDATE] 업데이트 성공: ID={wishlist_id}")
        return _serialize_wishlist(wishlist)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[WISHLIST_UPDATE] 오류: {str(e)}", exc_info=True)
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
