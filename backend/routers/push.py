import json
import logging
import re
import time
import uuid
from typing import Optional
from fastapi import APIRouter, Body, Depends, Header, HTTPException, Query, Request
from pydantic import BaseModel
from sqlalchemy.orm import Session
from backend.core.device_auth import (
    assert_device_owner,
    create_device_token,
    extract_client_ip,
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
_DEVICE_ISSUE_MAX_PER_WINDOW = 120
_DEVICE_ISSUE_WINDOW_SECONDS = 60
_device_issue_rate_limit: dict[str, list[float]] = {}
_TEST_PUSH_MAX_PER_WINDOW = 6
_test_push_rate_limit: dict[str, list[float]] = {}


def reset_rate_limits() -> None:
    """테스트 및 런타임 격리를 위한 기기 등록 레이트리밋 초기화 훅"""
    _device_issue_rate_limit.clear()
    _test_push_rate_limit.clear()


clear_rate_limits = reset_rate_limits


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
    toss_anon_key: Optional[str] = None
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
    request: Request,
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
        now_ts = time.time()
        client_ip = extract_client_ip(request)
        entries = [
            ts for ts in _device_issue_rate_limit.get(client_ip, [])
            if (now_ts - ts) < _DEVICE_ISSUE_WINDOW_SECONDS
        ]
        if len(entries) >= _DEVICE_ISSUE_MAX_PER_WINDOW:
            _device_issue_rate_limit[client_ip] = entries
            raise HTTPException(status_code=429, detail="Too many device registration requests")
        entries.append(now_ts)
        _device_issue_rate_limit[client_ip] = entries

        device_token = create_device_token(req_uuid)
        clean_fcm = req.fcm_token.strip() if isinstance(req.fcm_token, str) and req.fcm_token.strip() else None
        clean_toss_anon = (
            req.toss_anon_key.strip()[:255]
            if isinstance(req.toss_anon_key, str) and req.toss_anon_key.strip()
            else None
        )
        device = models.DeviceToken(
            device_uuid=req_uuid,
            fcm_token=clean_fcm,
            toss_anon_key=clean_toss_anon,
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
            "has_toss_push": bool(device.toss_anon_key),
            "registered_at": to_utc_iso(device.registered_at),
        }

    auth_uuid = verify_device_token_value(x_device_token)
    assert_device_owner(auth_uuid, device.device_uuid)

    # fcm_token / toss_anon_key 보호 가드: None이거나 빈 문자열/공백이면 기존 DB 토큰 절대 덮어쓰기 금지
    if isinstance(req.fcm_token, str) and req.fcm_token.strip():
        device.fcm_token = req.fcm_token.strip()
    if isinstance(req.toss_anon_key, str) and req.toss_anon_key.strip():
        device.toss_anon_key = req.toss_anon_key.strip()[:255]

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
        "has_toss_push": bool(device.toss_anon_key),
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
            "has_toss_push": False,
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
        "has_toss_push": bool(getattr(device, "toss_anon_key", None)),
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


class TestPushReq(BaseModel):
    device_uuid: Optional[str] = None


@router.post("/test")
def send_test_push(
    req: Optional[TestPushReq] = Body(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    """사용자 설정 화면에서 현재 기기의 웹/앱 푸시 수신 여부를 즉시 점검하는 테스트 푸시 발송"""
    target_uuid = (req.device_uuid if req and req.device_uuid else "").strip() or auth_uuid
    assert_device_owner(auth_uuid, target_uuid)

    now_ts = time.time()
    recent = [
        ts for ts in _test_push_rate_limit.get(target_uuid, [])
        if (now_ts - ts) < _DEVICE_ISSUE_WINDOW_SECONDS
    ]
    if len(recent) >= _TEST_PUSH_MAX_PER_WINDOW:
        _test_push_rate_limit[target_uuid] = recent
        raise HTTPException(status_code=429, detail="테스트 알림은 1분에 최대 6회까지 보낼 수 있어요.")
    recent.append(now_ts)
    _test_push_rate_limit[target_uuid] = recent

    device = db.query(models.DeviceToken).filter(
        models.DeviceToken.device_uuid == target_uuid,
        models.DeviceToken.is_active == True,
    ).first()
    if not device:
        raise HTTPException(status_code=404, detail="등록된 알림 기기를 찾지 못했어요.")

    test_title = "얼만데 테스트 알림"
    test_body = "얼만데 테스트 알림이에요! 실시간 특가 알림이 정상 작동 중입니다."
    sent_web = False
    sent_fcm = False
    sent_toss = False

    db.add(
        models.NotificationAlert(
            user_id=target_uuid,
            title=test_body,
            keyword="테스트 알림",
            deal_url="",
            dnd_suppressed=False,
        )
    )
    db.commit()

    if device.web_push_subscription:
        try:
            from backend.services.notification_service import NotificationService

            sent_web = bool(
                NotificationService.send_web_push_notification(
                    subscription_json=device.web_push_subscription,
                    keyword="테스트 알림",
                    title=test_title,
                    price=0,
                    site_name="얼만데",
                    deal_id=0,
                    deal_url="",
                    is_night_time=False,
                    db=db,
                    device=device,
                    custom_title=test_title,
                    custom_body=test_body,
                )
            )
        except Exception as exc:
            logger.warning(f"테스트 웹 푸시 발송 실패 ({target_uuid}): {exc}")

    if device.fcm_token:
        try:
            from firebase_admin import messaging

            messaging.send(
                messaging.Message(
                    notification=messaging.Notification(title=test_title, body=test_body),
                    data={"navigate_to": "notifications", "deal_id": "0"},
                    token=device.fcm_token,
                )
            )
            sent_fcm = True
        except Exception as exc:
            logger.warning(f"테스트 FCM 푸시 발송 실패 ({target_uuid}): {exc}")

    if getattr(device, "toss_anon_key", None):
        try:
            from backend.services.toss_push_service import send_toss_push_message

            toss_res = send_toss_push_message(
                device.toss_anon_key,
                context={"keyword": "테스트", "dealId": "0"},
            )
            sent_toss = bool(toss_res.get("sent"))
        except Exception as exc:
            logger.warning(f"테스트 토스 푸시 발송 실패 ({target_uuid}): {exc}")

    return {
        "success": True,
        "sent_web": sent_web,
        "sent_fcm": sent_fcm,
        "sent_toss": sent_toss,
        "has_subscription": bool(
            device.web_push_subscription
            or device.fcm_token
            or getattr(device, "toss_anon_key", None)
        ),
        "message": (
            "테스트 푸시 알림을 발송했어요!"
            if (sent_web or sent_fcm or sent_toss)
            else "인앱 알림함에 테스트 알림을 기록했어요. (푸시 권한이 꺼져 있으면 브라우저 알림은 울리지 않을 수 있어요)"
        ),
    }
