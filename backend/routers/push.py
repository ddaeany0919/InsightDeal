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

class KeywordReq(BaseModel):
    device_uuid: str
    keyword: str

@router.post("/device")
def register_device(req: RegisterDeviceReq, db: Session = Depends(get_db_session)):
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == req.device_uuid).first()
    if not device:
        device = models.DeviceToken(device_uuid=req.device_uuid, fcm_token=req.fcm_token, night_push_consent=req.night_push_consent)
        db.add(device)
    else:
        if req.fcm_token is not None:
            device.fcm_token = req.fcm_token
        device.night_push_consent = req.night_push_consent
    db.commit()
    return {"message": "Device registered"}

@router.get("/keywords")
def get_keywords(device_uuid: str, db: Session = Depends(get_db_session)):
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == device_uuid).first()
    if not device:
        return {"keywords": []}
    return {"keywords": [k.keyword for k in device.keywords if k.is_active]}

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
