from fastapi import APIRouter, Body, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from backend.database.session import get_db_session
from backend.database import models
from pydantic import BaseModel
from typing import List, Optional
import logging
from datetime import datetime

router = APIRouter()
logger = logging.getLogger(__name__)

# --- Pydantic DTOs ---
class KeywordReq(BaseModel):
    user_id: Optional[str] = None
    device_uuid: Optional[str] = None
    keyword: str

    @property
    def final_user_id(self) -> str:
        return self.user_id or self.device_uuid or ""

class AddNotificationReq(BaseModel):
    user_id: str
    title: str
    keyword: str
    deal_url: str

class NotificationAlertDto(BaseModel):
    id: str
    title: str
    keyword: str
    receivedAt: int
    dealUrl: str

# --- 1. Keywords API for users ---

@router.get("/keywords")
def get_user_keywords(user_id: str, db: Session = Depends(get_db_session)):
    # user_id가 device_uuid 형태로 넘어오거나 로그인 id 형태로 넘어올 수 있음
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == user_id).first()
    if not device:
        return {
            "keywords": [],
            "detailed_keywords": [],
            "dnd_enabled": False,
            "dnd_start_time": "21:00",
            "dnd_end_time": "08:00",
            "dnd_settings_json": None,
            "night_push_consent": False
        }
    return {
        "keywords": [k.keyword for k in device.keywords if k.is_active],
        "detailed_keywords": [{"id": k.id, "keyword": k.keyword, "is_active": k.is_active} for k in device.keywords],
        "dnd_enabled": device.dnd_enabled,
        "dnd_start_time": device.dnd_start_time,
        "dnd_end_time": device.dnd_end_time,
        "dnd_settings_json": device.dnd_settings_json,
        "night_push_consent": device.night_push_consent
    }

@router.post("/keywords")
def add_user_keyword(req: KeywordReq, db: Session = Depends(get_db_session)):
    user_id = req.final_user_id
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == user_id).first()
    if not device:
        device = models.DeviceToken(device_uuid=user_id)
        db.add(device)
        db.commit()
        db.refresh(device)
    
    existing = db.query(models.PushKeyword).filter(
        models.PushKeyword.device_token_id == device.id,
        models.PushKeyword.keyword == req.keyword
    ).first()
    
    if existing:
        if not existing.is_active:
            existing.is_active = True
            db.commit()
        from backend.services.push_worker import match_existing_deals_for_keyword
        match_existing_deals_for_keyword(db, user_id, req.keyword)
        return {"success": True, "message": "Keyword active"}
        
    kw = models.PushKeyword(device_token_id=device.id, keyword=req.keyword)
    db.add(kw)
    db.commit()
    from backend.services.push_worker import match_existing_deals_for_keyword
    match_existing_deals_for_keyword(db, user_id, req.keyword)
    return {"success": True, "message": "Keyword added"}

@router.post("/keywords/toggle")
def toggle_user_keyword(req: KeywordReq, db: Session = Depends(get_db_session)):
    user_id = req.final_user_id
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == user_id).first()
    if not device:
        device = models.DeviceToken(device_uuid=user_id)
        db.add(device)
        db.commit()
        db.refresh(device)

    kw = db.query(models.PushKeyword).filter(
        models.PushKeyword.device_token_id == device.id,
        models.PushKeyword.keyword == req.keyword
    ).first()

    if not kw:
        kw = models.PushKeyword(device_token_id=device.id, keyword=req.keyword, is_active=False)
        db.add(kw)
        db.commit()
        db.refresh(kw)
        return {"success": True, "is_active": kw.is_active, "message": f"Keyword active status toggled to {kw.is_active}"}

    kw.is_active = not kw.is_active
    db.commit()
    db.refresh(kw)
    return {"success": True, "is_active": kw.is_active, "message": f"Keyword active status toggled to {kw.is_active}"}

@router.delete("/keywords")
def delete_user_keyword(
    user_id: Optional[str] = Query(None),
    device_uuid: Optional[str] = Query(None),
    keyword: Optional[str] = Query(None),
    req: Optional[KeywordReq] = Body(None),
    db: Session = Depends(get_db_session)
):
    target_user_id = user_id or device_uuid or (req.final_user_id if req else None)
    target_keyword = keyword or (req.keyword if req else None)
    if not target_keyword:
        return {"success": False, "message": "키워드가 지정되지 않았습니다."}

    if target_user_id:
        device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_user_id).first()
        if device:
            kw = db.query(models.PushKeyword).filter(
                models.PushKeyword.device_token_id == device.id,
                models.PushKeyword.keyword == target_keyword
            ).first()
            if kw:
                db.delete(kw)
                db.commit()

    return {"success": True, "message": "Keyword removed"}

# --- 2. Notifications API ---

@router.get("/notifications", response_model=List[NotificationAlertDto])
def get_user_notifications(user_id: str, db: Session = Depends(get_db_session)):
    alerts = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == user_id).order_by(models.NotificationAlert.received_at.desc()).all()
    
    # 💡 전체 삭제 후 무한 복구 참사를 막기 위해, 조회 시점에 무조건적인 mock 재생성 로직은 영구 척결합니다.
    # 최초 웰컴 메시지는 회원가입 또는 디바이스 최초 생성 시점에만 1회성으로 주입됩니다.
    
    result = []
    for a in alerts:
        received_timestamp = int(a.received_at.timestamp() * 1000) if a.received_at else int(datetime.utcnow().timestamp() * 1000)
        result.append(
            NotificationAlertDto(
                id=str(a.id),
                title=a.title,
                keyword=a.keyword,
                receivedAt=received_timestamp,
                dealUrl=a.deal_url or ""
            )
        )
    return result

@router.post("/notifications")
def add_user_notification(req: AddNotificationReq, db: Session = Depends(get_db_session)):
    alert = models.NotificationAlert(
        user_id=req.user_id,
        title=req.title,
        keyword=req.keyword,
        deal_url=req.deal_url
    )
    db.add(alert)
    db.commit()
    return {"success": True, "message": "Notification added"}

@router.delete("/notifications/{alert_id}")
def delete_user_notification(alert_id: int, user_id: str, db: Session = Depends(get_db_session)):
    alert = db.query(models.NotificationAlert).filter(
        models.NotificationAlert.id == alert_id,
        models.NotificationAlert.user_id == user_id
    ).first()
    if alert:
        db.delete(alert)
        db.commit()
        return {"success": True, "message": "Notification deleted"}
    raise HTTPException(status_code=404, detail="Notification not found")

@router.post("/notifications/clear")
def clear_user_notifications(user_id: str = Query(...), db: Session = Depends(get_db_session)):
    db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == user_id).delete()
    db.commit()
    return {"success": True, "message": "All notifications cleared"}
