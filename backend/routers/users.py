from datetime import datetime, timezone
import logging
from typing import List, Optional
from fastapi import APIRouter, Body, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session
from backend.core.device_auth import assert_device_owner, require_device_token
from backend.core.utils import to_utc_iso
from backend.database import models
from backend.database.session import get_db_session

router = APIRouter()
logger = logging.getLogger(__name__)


# --- Pydantic DTOs ---
class KeywordReq(BaseModel):
    user_id: Optional[str] = None
    device_uuid: Optional[str] = None
    keyword: str

    @property
    def final_user_id(self) -> str:
        return (self.user_id or self.device_uuid or "").strip()


class AddNotificationReq(BaseModel):
    user_id: Optional[str] = None
    title: str
    keyword: str
    deal_url: str


class NotificationAlertDto(BaseModel):
    id: str
    title: str
    keyword: str
    receivedAt: int
    received_at: str
    dealUrl: str
    deal_url: str


def _resolve_user_owner(auth_uuid: str, *candidates: Optional[str]) -> str:
    for cand in candidates:
        if cand is not None and cand.strip():
            assert_device_owner(auth_uuid, cand)
    for cand in candidates:
        if cand is not None and cand.strip():
            return cand.strip()
    return auth_uuid


# --- 1. Keywords API for users ---

@router.get("/keywords")
def get_user_keywords(
    user_id: Optional[str] = Query(default=None),
    device_uuid: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_uuid = _resolve_user_owner(auth_uuid, user_id, device_uuid)
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_uuid).first()
    if not device:
        return {
            "keywords": [],
            "detailed_keywords": [],
            "dnd_enabled": False,
            "dnd_start_time": "21:00",
            "dnd_end_time": "08:00",
            "dnd_settings_json": None,
            "night_push_consent": False,
        }
    return {
        "keywords": [k.keyword for k in device.keywords if k.is_active],
        "detailed_keywords": [
            {
                "id": k.id,
                "keyword": k.keyword,
                "is_active": k.is_active,
                "created_at": to_utc_iso(k.created_at),
            }
            for k in device.keywords
        ],
        "dnd_enabled": device.dnd_enabled,
        "dnd_start_time": device.dnd_start_time,
        "dnd_end_time": device.dnd_end_time,
        "dnd_settings_json": device.dnd_settings_json,
        "night_push_consent": device.night_push_consent,
    }


@router.post("/keywords")
def add_user_keyword(
    req: KeywordReq,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_uuid = _resolve_user_owner(auth_uuid, req.user_id, req.device_uuid)
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_uuid).first()
    if not device:
        device = models.DeviceToken(device_uuid=target_uuid)
        db.add(device)
        db.commit()
        db.refresh(device)

    existing = db.query(models.PushKeyword).filter(
        models.PushKeyword.device_token_id == device.id,
        models.PushKeyword.keyword == req.keyword,
    ).first()

    if existing:
        if not existing.is_active:
            existing.is_active = True
            db.commit()
        from backend.services.push_worker import match_existing_deals_for_keyword
        match_existing_deals_for_keyword(db, target_uuid, req.keyword)
        return {"success": True, "message": "Keyword active"}

    kw = models.PushKeyword(device_token_id=device.id, keyword=req.keyword)
    db.add(kw)
    db.commit()
    from backend.services.push_worker import match_existing_deals_for_keyword
    match_existing_deals_for_keyword(db, target_uuid, req.keyword)
    return {"success": True, "message": "Keyword added"}


@router.post("/keywords/toggle")
def toggle_user_keyword(
    req: KeywordReq,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_uuid = _resolve_user_owner(auth_uuid, req.user_id, req.device_uuid)
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_uuid).first()
    if not device:
        device = models.DeviceToken(device_uuid=target_uuid)
        db.add(device)
        db.commit()
        db.refresh(device)

    kw = db.query(models.PushKeyword).filter(
        models.PushKeyword.device_token_id == device.id,
        models.PushKeyword.keyword == req.keyword,
    ).first()

    if not kw:
        kw = models.PushKeyword(device_token_id=device.id, keyword=req.keyword, is_active=False)
        db.add(kw)
        db.commit()
        db.refresh(kw)
        return {
            "success": True,
            "is_active": kw.is_active,
            "message": f"Keyword active status toggled to {kw.is_active}",
        }

    kw.is_active = not kw.is_active
    db.commit()
    db.refresh(kw)
    return {
        "success": True,
        "is_active": kw.is_active,
        "message": f"Keyword active status toggled to {kw.is_active}",
    }


@router.delete("/keywords")
def delete_user_keyword(
    user_id: Optional[str] = Query(None),
    device_uuid: Optional[str] = Query(None),
    keyword: Optional[str] = Query(None),
    req: Optional[KeywordReq] = Body(None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_user_id = _resolve_user_owner(
        auth_uuid,
        user_id,
        device_uuid,
        req.user_id if req else None,
        req.device_uuid if req else None,
    )
    target_keyword = keyword or (req.keyword if req else None)
    if not target_keyword:
        return {"success": False, "message": "키워드가 지정되지 않았습니다."}

    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_user_id).first()
    if device:
        kw = db.query(models.PushKeyword).filter(
            models.PushKeyword.device_token_id == device.id,
            models.PushKeyword.keyword == target_keyword,
        ).first()
        if kw:
            db.delete(kw)
            db.commit()

    return {"success": True, "message": "Keyword removed"}


# --- 2. Notifications API ---

@router.get("/notifications", response_model=List[NotificationAlertDto])
def get_user_notifications(
    user_id: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_user_id = _resolve_user_owner(auth_uuid, user_id)
    alerts = (
        db.query(models.NotificationAlert)
        .filter(models.NotificationAlert.user_id == target_user_id)
        .order_by(models.NotificationAlert.received_at.desc())
        .all()
    )

    result = []
    for a in alerts:
        raw_dt = a.received_at
        if raw_dt is None:
            utc_dt = datetime.now(timezone.utc)
        elif getattr(raw_dt, "tzinfo", None) is None:
            utc_dt = raw_dt.replace(tzinfo=timezone.utc)
        else:
            utc_dt = raw_dt.astimezone(timezone.utc)

        received_timestamp = int(utc_dt.timestamp() * 1000)
        received_iso = to_utc_iso(utc_dt) or utc_dt.strftime("%Y-%m-%dT%H:%M:%SZ")
        deal_url_val = a.deal_url or ""

        result.append(
            NotificationAlertDto(
                id=str(a.id),
                title=a.title,
                keyword=a.keyword,
                receivedAt=received_timestamp,
                received_at=received_iso,
                dealUrl=deal_url_val,
                deal_url=deal_url_val,
            )
        )
    return result


@router.post("/notifications")
def add_user_notification(
    req: AddNotificationReq,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_user_id = _resolve_user_owner(auth_uuid, req.user_id)
    alert = models.NotificationAlert(
        user_id=target_user_id,
        title=req.title,
        keyword=req.keyword,
        deal_url=req.deal_url,
    )
    db.add(alert)
    db.commit()
    return {"success": True, "message": "Notification added"}


@router.delete("/notifications/{alert_id}")
def delete_user_notification(
    alert_id: int,
    user_id: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_user_id = _resolve_user_owner(auth_uuid, user_id)
    alert = db.query(models.NotificationAlert).filter(models.NotificationAlert.id == alert_id).first()
    if not alert:
        raise HTTPException(status_code=404, detail="Notification not found")
    if alert.user_id != target_user_id:
        raise HTTPException(status_code=403, detail="X-Device-Token does not match target device_uuid/user_id")
    db.delete(alert)
    db.commit()
    return {"success": True, "message": "Notification deleted"}


@router.post("/notifications/clear")
def clear_user_notifications(
    user_id: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_user_id = _resolve_user_owner(auth_uuid, user_id)
    db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == target_user_id).delete()
    db.commit()
    return {"success": True, "message": "All notifications cleared"}
