from fastapi import APIRouter, Depends, HTTPException, Body
from sqlalchemy.orm import Session
from backend.database.session import get_db_session
from backend.database import models
from pydantic import BaseModel
import logging

router = APIRouter()
logger = logging.getLogger(__name__)

class RegisterDeviceReq(BaseModel):
    device_uuid: str
    fcm_token: str = None
    night_push_consent: bool = False
    dnd_enabled: bool = False
    dnd_start_time: str = "21:00"
    dnd_end_time: str = "08:00"
    dnd_settings_json: str = None # 요일별 세부 DND (ex: JSON string)

class KeywordReq(BaseModel):
    device_uuid: str
    keyword: str

@router.post("/device")
def register_device(req: RegisterDeviceReq, db: Session = Depends(get_db_session)):
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == req.device_uuid).first()
    if not device:
        device = models.DeviceToken(
            device_uuid=req.device_uuid,
            fcm_token=req.fcm_token,
            night_push_consent=req.night_push_consent,
            dnd_enabled=req.dnd_enabled,
            dnd_start_time=req.dnd_start_time,
            dnd_end_time=req.dnd_end_time,
            dnd_settings_json=req.dnd_settings_json
        )
        db.add(device)
    else:
        if req.fcm_token is not None:
            device.fcm_token = req.fcm_token
        device.night_push_consent = req.night_push_consent
        device.dnd_enabled = req.dnd_enabled
        device.dnd_start_time = req.dnd_start_time
        device.dnd_end_time = req.dnd_end_time
        device.dnd_settings_json = req.dnd_settings_json
    db.commit()
    return {"message": "Device registered"}

@router.get("/keywords")
def get_keywords(device_uuid: str, db: Session = Depends(get_db_session)):
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == device_uuid).first()
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

@router.post("/keywords/toggle")
def toggle_keyword(req: KeywordReq, db: Session = Depends(get_db_session)):
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == req.device_uuid).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
        
    kw = db.query(models.PushKeyword).filter(
        models.PushKeyword.device_token_id == device.id,
        models.PushKeyword.keyword == req.keyword
    ).first()
    
    if not kw:
        raise HTTPException(status_code=404, detail="Keyword not registered")
        
    kw.is_active = not kw.is_active
    db.commit()
    db.refresh(kw)
    return {"success": True, "is_active": kw.is_active, "message": f"Keyword active status toggled to {kw.is_active}"}

@router.post("/keywords")
def add_keyword(req: KeywordReq, db: Session = Depends(get_db_session)):
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == req.device_uuid).first()
    if not device:
        device = models.DeviceToken(device_uuid=req.device_uuid)
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
        return {"success": True, "message": "Keyword active"}
        
    kw = models.PushKeyword(device_token_id=device.id, keyword=req.keyword)
    db.add(kw)
    db.commit()
    return {"success": True, "message": "Keyword added"}

@router.delete("/keywords")
def delete_keyword(req: KeywordReq, db: Session = Depends(get_db_session)):
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == req.device_uuid).first()
    if not device:
        return {"success": False, "message": "Not found"}
        
    kw = db.query(models.PushKeyword).filter(
        models.PushKeyword.device_token_id == device.id,
        models.PushKeyword.keyword == req.keyword
    ).first()
    
    if kw:
        db.delete(kw)
        db.commit()
    return {"success": True, "message": "Keyword removed"}

class RegisterWebPushReq(BaseModel):
    endpoint: str
    keys: dict

@router.post("/register-web")
def register_web_push(req: RegisterWebPushReq, db: Session = Depends(get_db_session)):
    import json
    sub_str = json.dumps(req.dict())
    
    # endpoint 주소를 고유식별 해시로 변환하여 매핑/생성
    endpoint_hash = req.endpoint.split("/")[-1][:100]
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == f"web_{endpoint_hash}").first()
    if not device:
        device = models.DeviceToken(
            device_uuid=f"web_{endpoint_hash}",
            web_push_subscription=sub_str,
            is_active=True
        )
        db.add(device)
    else:
        device.web_push_subscription = sub_str
        device.is_active = True
    db.commit()
    return {"success": True, "message": "Web Push subscription registered"}

@router.delete("/register-web")
def delete_web_push(endpoint: str, db: Session = Depends(get_db_session)):
    endpoint_hash = endpoint.split("/")[-1][:100]
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == f"web_{endpoint_hash}").first()
    if device:
        device.web_push_subscription = None
        db.commit()
    return {"success": True, "message": "Web Push subscription removed"}
