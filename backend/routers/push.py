import json
import logging
import re
import uuid
from typing import Optional
from fastapi import APIRouter, Body, Depends, Header, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session
from backend.core.device_auth import (
    assert_device_owner,
    create_device_token,
    require_device_token,
    verify_device_token_value,
)
from backend.core.utils import to_utc_iso
from backend.database import models
from backend.database.session import get_db_session

router = APIRouter()
logger = logging.getLogger(__name__)

_PLACEHOLDER_DEVICE_UUIDS = {"default", "guest", "anonymous", "web_guest"}
_HHMM_RE = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")


def _validate_hhmm(value: str, field_name: str) -> str:
    cleaned = value.strip()
    if not _HHMM_RE.match(cleaned):
        raise HTTPException(
            status_code=400,
            detail=f"Invalid {field_name} format: expected HH:MM",
        )
    return cleaned


def _validate_dnd_json(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    cleaned = value.strip()
    if not cleaned:
        return None
    try:
        parsed = json.loads(cleaned)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid dnd_settings_json: malformed JSON")
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=400, detail="Invalid dnd_settings_json: JSON object required")
    return cleaned


class RegisterDeviceReq(BaseModel):
    device_uuid: Optional[str] = None
    fcm_token: Optional[str] = None
    night_push_consent: Optional[bool] = None
    dnd_enabled: Optional[bool] = None
    dnd_start_time: Optional[str] = None
    dnd_end_time: Optional[str] = None
    dnd_settings_json: Optional[str] = None  # 요일별 세부 DND (ex: JSON string)


class UpdateDndReq(BaseModel):
    device_uuid: Optional[str] = None
    dnd_enabled: Optional[bool] = None
    dnd_start_time: Optional[str] = None
    dnd_end_time: Optional[str] = None
    dnd_settings_json: Optional[str] = None
    night_push_consent: Optional[bool] = None


class KeywordReq(BaseModel):
    device_uuid: Optional[str] = None
    keyword: str


@router.post("/device")
def register_device(
    req: RegisterDeviceReq,
    x_device_token: Optional[str] = Header(default=None, alias="X-Device-Token"),
    db: Session = Depends(get_db_session),
):
    raw_uuid = (req.device_uuid or "").strip()
    if not raw_uuid or raw_uuid.lower() in _PLACEHOLDER_DEVICE_UUIDS:
        req_uuid = str(uuid.uuid4())
    else:
        req_uuid = raw_uuid

    validated_start = (
        _validate_hhmm(req.dnd_start_time, "dnd_start_time")
        if req.dnd_start_time is not None and req.dnd_start_time.strip()
        else None
    )
    validated_end = (
        _validate_hhmm(req.dnd_end_time, "dnd_end_time")
        if req.dnd_end_time is not None and req.dnd_end_time.strip()
        else None
    )
    validated_dnd_json = _validate_dnd_json(req.dnd_settings_json) if req.dnd_settings_json is not None else None

    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == req_uuid).first()
    if not device:
        device_token = create_device_token(req_uuid)
        clean_fcm = req.fcm_token.strip() if isinstance(req.fcm_token, str) and req.fcm_token.strip() else None
        device = models.DeviceToken(
            device_uuid=req_uuid,
            fcm_token=clean_fcm,
            night_push_consent=bool(req.night_push_consent) if req.night_push_consent is not None else False,
            dnd_enabled=bool(req.dnd_enabled) if req.dnd_enabled is not None else False,
            dnd_start_time=validated_start or "21:00",
            dnd_end_time=validated_end or "08:00",
            dnd_settings_json=validated_dnd_json,
        )
        db.add(device)
        db.commit()
        db.refresh(device)
        return {
            "status": "ok",
            "success": True,
            "message": "Device registered",
            "device_uuid": req_uuid,
            "device_token": device_token,
            "registered_at": to_utc_iso(device.registered_at),
        }

    auth_uuid = verify_device_token_value(x_device_token)
    assert_device_owner(auth_uuid, device.device_uuid)

    # fcm_token 보호 가드: None이거나 빈 문자열/공백이면 기존 DB fcm_token 절대 덮어쓰기 금지
    if isinstance(req.fcm_token, str) and req.fcm_token.strip():
        device.fcm_token = req.fcm_token.strip()

    if req.night_push_consent is not None:
        device.night_push_consent = req.night_push_consent
    if req.dnd_enabled is not None:
        device.dnd_enabled = req.dnd_enabled
    if validated_start is not None:
        device.dnd_start_time = validated_start
    if validated_end is not None:
        device.dnd_end_time = validated_end
    if req.dnd_settings_json is not None:
        device.dnd_settings_json = validated_dnd_json

    db.commit()
    db.refresh(device)
    return {
        "status": "ok",
        "success": True,
        "message": "Device registered",
        "device_uuid": device.device_uuid,
        "registered_at": to_utc_iso(device.registered_at),
    }


@router.patch("/dnd")
def update_dnd(
    req: UpdateDndReq,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_uuid = (req.device_uuid or "").strip() or auth_uuid
    assert_device_owner(auth_uuid, target_uuid)

    validated_start = _validate_hhmm(req.dnd_start_time, "dnd_start_time") if req.dnd_start_time is not None else None
    validated_end = _validate_hhmm(req.dnd_end_time, "dnd_end_time") if req.dnd_end_time is not None else None
    validated_dnd_json = _validate_dnd_json(req.dnd_settings_json) if req.dnd_settings_json is not None else None

    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_uuid).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    if req.dnd_enabled is not None:
        device.dnd_enabled = req.dnd_enabled
    if validated_start is not None:
        device.dnd_start_time = validated_start
    if validated_end is not None:
        device.dnd_end_time = validated_end
    if req.dnd_settings_json is not None:
        device.dnd_settings_json = validated_dnd_json
    if req.night_push_consent is not None:
        device.night_push_consent = req.night_push_consent

    db.commit()
    db.refresh(device)
    return {
        "status": "ok",
        "success": True,
        "device_uuid": device.device_uuid,
        "dnd_enabled": device.dnd_enabled,
        "dnd_start_time": device.dnd_start_time,
        "dnd_end_time": device.dnd_end_time,
        "dnd_settings_json": device.dnd_settings_json,
        "night_push_consent": device.night_push_consent,
        "registered_at": to_utc_iso(device.registered_at),
    }


@router.get("/keywords")
def get_keywords(
    device_uuid: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_uuid = (device_uuid or "").strip() or auth_uuid
    assert_device_owner(auth_uuid, target_uuid)

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


@router.post("/keywords/toggle")
def toggle_keyword(
    req: KeywordReq,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_uuid = (req.device_uuid or "").strip() or auth_uuid
    assert_device_owner(auth_uuid, target_uuid)

    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_uuid).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    kw = db.query(models.PushKeyword).filter(
        models.PushKeyword.device_token_id == device.id,
        models.PushKeyword.keyword == req.keyword,
    ).first()

    if not kw:
        raise HTTPException(status_code=404, detail="Keyword not registered")

    kw.is_active = not kw.is_active
    db.commit()
    db.refresh(kw)
    return {
        "success": True,
        "is_active": kw.is_active,
        "message": f"Keyword active status toggled to {kw.is_active}",
    }


@router.post("/keywords")
def add_keyword(
    req: KeywordReq,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_uuid = (req.device_uuid or "").strip() or auth_uuid
    assert_device_owner(auth_uuid, target_uuid)

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


@router.delete("/keywords")
def delete_keyword(
    device_uuid: Optional[str] = Query(default=None),
    keyword: Optional[str] = Query(default=None),
    req: Optional[KeywordReq] = Body(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    if device_uuid:
        assert_device_owner(auth_uuid, device_uuid)
    if req and req.device_uuid:
        assert_device_owner(auth_uuid, req.device_uuid)
    target_uuid = (device_uuid or (req.device_uuid if req else None) or "").strip() or auth_uuid
    target_keyword = (keyword or (req.keyword if req else None) or "").strip()
    if not target_keyword:
        return {"success": False, "message": "Keyword not provided"}

    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_uuid).first()
    if not device:
        return {"success": False, "message": "Not found"}

    kw = db.query(models.PushKeyword).filter(
        models.PushKeyword.device_token_id == device.id,
        models.PushKeyword.keyword == target_keyword,
    ).first()

    if kw:
        db.delete(kw)
        db.commit()
    return {"success": True, "message": "Keyword removed"}


class RegisterWebPushReq(BaseModel):
    endpoint: str
    keys: dict
    device_uuid: Optional[str] = None


class DeleteWebPushReq(BaseModel):
    endpoint: Optional[str] = ""
    device_uuid: Optional[str] = None


@router.post("/register-web")
def register_web_push(
    req: RegisterWebPushReq,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    target_uuid = (req.device_uuid or "").strip() or auth_uuid
    assert_device_owner(auth_uuid, target_uuid)

    sub_str = json.dumps({"endpoint": req.endpoint, "keys": req.keys})
    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_uuid).first()
    if not device:
        device = models.DeviceToken(
            device_uuid=target_uuid,
            web_push_subscription=sub_str,
            is_active=True,
        )
        db.add(device)
    else:
        device.web_push_subscription = sub_str
        device.is_active = True
    db.commit()
    return {
        "success": True,
        "message": "Web Push subscription registered",
        "device_uuid": target_uuid,
    }


@router.delete("/register-web")
def delete_web_push(
    endpoint: str = Query(default=""),
    device_uuid: Optional[str] = Query(default=None),
    req: Optional[DeleteWebPushReq] = Body(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    if device_uuid:
        assert_device_owner(auth_uuid, device_uuid)
    if req and req.device_uuid:
        assert_device_owner(auth_uuid, req.device_uuid)

    target_uuid = (device_uuid or (req.device_uuid if req else None) or "").strip() or auth_uuid
    assert_device_owner(auth_uuid, target_uuid)

    device = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_uuid).first()
    if device:
        device.web_push_subscription = None
        db.commit()
    return {"success": True, "message": "Web Push subscription removed"}
