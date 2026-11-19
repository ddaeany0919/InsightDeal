import base64
from datetime import datetime, timedelta, timezone
import hashlib
import hmac
import json
import os
import re
import secrets
import time
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from pydantic import BaseModel
from sqlalchemy import update
from sqlalchemy.orm import Session

from backend.core.device_auth import (
    extract_client_ip,
    get_device_secret,
    optional_device_token,
    require_device_token,
)
from backend.core.utils import to_utc_iso
from backend.database import models
from backend.database.session import get_db_session
from backend.models.wishlist_models import Wishlist

router = APIRouter()

_PBKDF2_ITERATIONS = 200_000
_PBKDF2_PREFIX = "pbkdf2_sha256"
_PAIRING_TTL_SECONDS = 600
_PAIRING_DEVICE_MAX_FAILS = 5
_PAIRING_IP_MAX_ATTEMPTS = 10
_PAIRING_ISSUE_MAX_PER_WINDOW = 20
_PAIRING_WINDOW_SECONDS = 600
_ACCESS_TOKEN_TTL_SECONDS = 86400 * 7

# 테스트 격리용 reset_rate_limits() 및 자동 탐색 키워드(attempt/rate_limit)와 호환되는 인메모리 저장소
_pairing_device_fail_attempts: dict[str, list[float]] = {}
_pairing_ip_rate_limit: dict[str, list[float]] = {}
_pairing_issue_rate_limit: dict[str, list[float]] = {}


def reset_rate_limits() -> None:
    """테스트 및 런타임 격리를 위한 인증/페어링 레이트리밋 초기화 훅"""
    _pairing_device_fail_attempts.clear()
    _pairing_ip_rate_limit.clear()
    _pairing_issue_rate_limit.clear()
    try:
        from backend.routers import push as push_router
        push_router.reset_rate_limits()
    except Exception:
        pass


clear_rate_limits = reset_rate_limits


class UserCreate(BaseModel):
    username: str
    password: str
    nickname: str


class UserLogin(BaseModel):
    username: str
    password: str


class UserResponse(BaseModel):
    username: str
    nickname: str
    honey_points: int
    access_token: Optional[str] = None
    token_type: str = "bearer"

    class Config:
        orm_mode = True


class UserWithdraw(BaseModel):
    username: str
    password: str


class ClaimPairingCodeReq(BaseModel):
    code: str


def _get_auth_token_secret() -> str:
    secret = os.getenv("AUTH_TOKEN_SECRET", "").strip()
    return secret or get_device_secret()


def hash_password(password: str) -> str:
    """PBKDF2-HMAC-SHA256 솔트 해시 생성 (외부 패키지 불필요)"""
    salt = secrets.token_bytes(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, _PBKDF2_ITERATIONS)
    salt_b64 = base64.urlsafe_b64encode(salt).decode("ascii").rstrip("=")
    dk_b64 = base64.urlsafe_b64encode(dk).decode("ascii").rstrip("=")
    return f"{_PBKDF2_PREFIX}${_PBKDF2_ITERATIONS}${salt_b64}${dk_b64}"


def verify_password(plain_password: str, stored_hash: str) -> tuple[bool, bool]:
    """
    비밀번호 검증 및 레거시 64자 SHA-256 해시 업그레이드 필요 여부 반환.
    Returns: (matched, needs_upgrade)
    """
    if not stored_hash or not plain_password:
        return False, False

    if stored_hash.startswith(f"{_PBKDF2_PREFIX}$"):
        parts = stored_hash.split("$")
        if len(parts) != 4:
            return False, False
        _, iter_str, salt_b64, dk_b64 = parts
        try:
            iterations = int(iter_str)
            salt = base64.urlsafe_b64decode((salt_b64 + "=" * (-len(salt_b64) % 4)).encode("ascii"))
            expected_dk = base64.urlsafe_b64decode((dk_b64 + "=" * (-len(dk_b64) % 4)).encode("ascii"))
            computed_dk = hashlib.pbkdf2_hmac("sha256", plain_password.encode("utf-8"), salt, iterations)
            return hmac.compare_digest(computed_dk, expected_dk), False
        except Exception:
            return False, False

    # 레거시 64자 hex 무솔트 SHA-256 호환 검증
    if len(stored_hash) == 64 and re.fullmatch(r"[0-9a-fA-F]{64}", stored_hash):
        legacy_hex = hashlib.sha256(plain_password.encode("utf-8")).hexdigest()
        matched = hmac.compare_digest(legacy_hex.lower(), stored_hash.lower())
        return matched, matched

    return False, False


def create_access_token(user: models.User) -> str:
    """도메인 분리(auth_v1) HMAC-SHA256 서명 Bearer 액세스 토큰 발급"""
    now_ts = int(time.time())
    created_utc = _to_utc(getattr(user, "created_at", None))
    uca = int(round(created_utc.timestamp() * 1_000_000)) if created_utc is not None else None
    payload = {
        "uid": user.id,
        "sub": user.username,
        "tv": int(getattr(user, "token_version", 0) or 0),
        "iat": now_ts,
        "exp": now_ts + _ACCESS_TOKEN_TTL_SECONDS,
    }
    if uca is not None:
        payload["uca"] = uca
    payload_bytes = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    payload_b64 = base64.urlsafe_b64encode(payload_bytes).decode("ascii").rstrip("=")
    signing_input = f"auth_v1.{payload_b64}"
    secret = _get_auth_token_secret()
    sig = hmac.new(secret.encode("utf-8"), signing_input.encode("utf-8"), hashlib.sha256).hexdigest()
    return f"auth_v1.{payload_b64}.{sig}"


def verify_access_token_payload(token: str) -> dict:
    """auth_v1 Bearer 토큰 서명 및 만료 검증 (실패 시 401)"""
    raw = (token or "").strip()
    parts = raw.split(".")
    if len(parts) != 3 or parts[0] != "auth_v1" or not parts[1] or not parts[2]:
        raise HTTPException(status_code=401, detail="Invalid authorization token format")
    _, payload_b64, provided_sig = parts
    secret = _get_auth_token_secret()
    expected_sig = hmac.new(
        secret.encode("utf-8"),
        f"auth_v1.{payload_b64}".encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    if not hmac.compare_digest(provided_sig, expected_sig):
        raise HTTPException(status_code=401, detail="Invalid authorization token signature")
    try:
        padded = payload_b64 + "=" * (-len(payload_b64) % 4)
        payload = json.loads(base64.urlsafe_b64decode(padded.encode("ascii")).decode("utf-8"))
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid authorization token payload")
    if not isinstance(payload, dict) or "uid" not in payload or "sub" not in payload:
        raise HTTPException(status_code=401, detail="Invalid authorization token claims")
    exp = payload.get("exp")
    if not isinstance(exp, (int, float)) or int(time.time()) >= int(exp):
        raise HTTPException(status_code=401, detail="Authorization token expired")
    return payload


def get_current_user(
    authorization: Optional[str] = Header(default=None, alias="Authorization"),
    db: Session = Depends(get_db_session),
) -> models.User:
    """Authorization: Bearer <token> 헤더에서 현재 로그인 사용자 조회 (탈퇴/버전 불일치 시 401)"""
    raw_auth = (authorization or "").strip()
    if not raw_auth or not raw_auth.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Missing Bearer authorization token")
    token = raw_auth[7:].strip()
    payload = verify_access_token_payload(token)

    db_user = db.query(models.User).filter(models.User.id == payload.get("uid")).first()
    if not db_user or db_user.username != payload.get("sub"):
        raise HTTPException(status_code=401, detail="User not found or withdrawn")
    if int(getattr(db_user, "token_version", 0) or 0) != int(payload.get("tv", 0) or 0):
        raise HTTPException(status_code=401, detail="Authorization token revoked")
    db_created_utc = _to_utc(getattr(db_user, "created_at", None))
    if db_created_utc is not None:
        token_uca = payload.get("uca")
        if token_uca is not None:
            db_uca = int(round(db_created_utc.timestamp() * 1_000_000))
            if db_uca != int(token_uca):
                raise HTTPException(status_code=401, detail="Authorization token revoked")
        token_iat = payload.get("iat")
        if isinstance(token_iat, (int, float)) and int(token_iat) < int(db_created_utc.timestamp()):
            raise HTTPException(status_code=401, detail="Authorization token revoked")
    return db_user


def _to_utc(dt: Optional[datetime]) -> Optional[datetime]:
    if dt is None:
        return None
    if getattr(dt, "tzinfo", None) is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _extract_client_ip(request: Request) -> str:
    return extract_client_ip(request)


def _prune_window(bucket: dict[str, list[float]], key: str, now_ts: float, window_sec: int) -> list[float]:
    entries = [ts for ts in bucket.get(key, []) if (now_ts - ts) < window_sec]
    if entries:
        bucket[key] = entries
    else:
        bucket.pop(key, None)
    return entries


def _record_pairing_failure(target_uuid: str, client_ip: str, now_ts: float) -> None:
    dev_entries = _prune_window(_pairing_device_fail_attempts, target_uuid, now_ts, _PAIRING_WINDOW_SECONDS)
    dev_entries.append(now_ts)
    _pairing_device_fail_attempts[target_uuid] = dev_entries

    ip_entries = _prune_window(_pairing_ip_rate_limit, client_ip, now_ts, _PAIRING_WINDOW_SECONDS)
    ip_entries.append(now_ts)
    _pairing_ip_rate_limit[client_ip] = ip_entries


def _merge_dnd_json(target_raw: Optional[str], source_raw: Optional[str]) -> Optional[str]:
    if not source_raw:
        return target_raw
    if not target_raw:
        return source_raw
    try:
        src_obj = json.loads(source_raw)
        tgt_obj = json.loads(target_raw)
        if isinstance(src_obj, dict) and isinstance(tgt_obj, dict):
            merged = {**tgt_obj, **src_obj}
            return json.dumps(merged, ensure_ascii=False, separators=(",", ":"))
    except Exception:
        pass
    return source_raw


def _merge_device_data(db: Session, source_uuid: str, target_uuid: str) -> dict:
    """source_uuid 기기의 찜·키워드·알림·DND 설정을 target_uuid 기기로 무손실 병합 (채널 토큰 비복제)"""
    if not source_uuid or not target_uuid or source_uuid == target_uuid:
        return {
            "merged_keywords": 0,
            "merged_wishlists": 0,
            "merged_alerts": 0,
            "dnd_synced": False,
        }

    src_dev = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == source_uuid).first()
    tgt_dev = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == target_uuid).first()
    if tgt_dev is None:
        tgt_dev = models.DeviceToken(device_uuid=target_uuid, is_active=True)
        db.add(tgt_dev)
        db.flush()

    dnd_synced = False
    merged_keywords = 0
    if src_dev is not None:
        src_has_dnd_custom = bool(
            src_dev.dnd_enabled
            or src_dev.dnd_settings_json
            or src_dev.night_push_consent
            or (src_dev.dnd_start_time and src_dev.dnd_start_time != "21:00")
            or (src_dev.dnd_end_time and src_dev.dnd_end_time != "08:00")
        )
        can_override_schedule = bool(src_dev.dnd_enabled) or not bool(tgt_dev.dnd_enabled)
        if src_has_dnd_custom:
            if src_dev.dnd_enabled:
                tgt_dev.dnd_enabled = True
            if src_dev.night_push_consent:
                tgt_dev.night_push_consent = True
            if can_override_schedule and src_dev.dnd_start_time and (src_dev.dnd_enabled or src_dev.dnd_start_time != "21:00"):
                tgt_dev.dnd_start_time = src_dev.dnd_start_time
            if can_override_schedule and src_dev.dnd_end_time and (src_dev.dnd_enabled or src_dev.dnd_end_time != "08:00"):
                tgt_dev.dnd_end_time = src_dev.dnd_end_time
            if src_dev.dnd_settings_json and can_override_schedule:
                tgt_dev.dnd_settings_json = _merge_dnd_json(tgt_dev.dnd_settings_json, src_dev.dnd_settings_json)
            dnd_synced = True

        src_keywords = db.query(models.PushKeyword).filter(models.PushKeyword.device_token_id == src_dev.id).all()
        tgt_kw_map = {
            k.keyword: k
            for k in db.query(models.PushKeyword).filter(models.PushKeyword.device_token_id == tgt_dev.id).all()
        }
        for sk in src_keywords:
            existing_kw = tgt_kw_map.get(sk.keyword)
            if existing_kw is None:
                new_kw = models.PushKeyword(
                    device_token_id=tgt_dev.id,
                    keyword=sk.keyword,
                    is_active=bool(sk.is_active),
                )
                db.add(new_kw)
                tgt_kw_map[sk.keyword] = new_kw
                merged_keywords += 1
            else:
                if not existing_kw.is_active and sk.is_active:
                    existing_kw.is_active = True
                    merged_keywords += 1

    # Wishlist 병합 (동일 keyword 중복 방지, 더 낮은 양수 target_price 채택)
    merged_wishlists = 0
    src_wishlists = db.query(Wishlist).filter(Wishlist.user_id == source_uuid).all()
    tgt_wl_map = {
        w.keyword: w
        for w in db.query(Wishlist).filter(Wishlist.user_id == target_uuid).all()
    }
    for sw in src_wishlists:
        existing_wl = tgt_wl_map.get(sw.keyword)
        if existing_wl is None:
            new_wl = Wishlist(
                keyword=sw.keyword,
                target_price=sw.target_price,
                user_id=target_uuid,
                is_active=bool(sw.is_active),
                alert_enabled=bool(sw.alert_enabled),
            )
            db.add(new_wl)
            tgt_wl_map[sw.keyword] = new_wl
            merged_wishlists += 1
        else:
            if sw.target_price and sw.target_price > 0:
                if not existing_wl.target_price or existing_wl.target_price <= 0 or sw.target_price < existing_wl.target_price:
                    existing_wl.target_price = sw.target_price
            existing_wl.is_active = bool(existing_wl.is_active or sw.is_active)
            existing_wl.alert_enabled = bool(existing_wl.alert_enabled or sw.alert_enabled)

    # NotificationAlert 병합 (동일 title 중복 방지, P1-04 컬럼 보존)
    merged_alerts = 0
    src_alerts = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == source_uuid).all()
    tgt_titles = {
        a.title
        for a in db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == target_uuid).all()
    }
    for sa in src_alerts:
        if sa.title in tgt_titles:
            continue
        tgt_titles.add(sa.title)
        db.add(
            models.NotificationAlert(
                user_id=target_uuid,
                title=sa.title,
                keyword=sa.keyword,
                received_at=sa.received_at,
                deal_url=sa.deal_url,
                is_read=bool(getattr(sa, "is_read", False)),
                dnd_suppressed=bool(getattr(sa, "dnd_suppressed", False)),
                digest_sent_at=getattr(sa, "digest_sent_at", None),
            )
        )
        merged_alerts += 1

    db.flush()
    return {
        "merged_keywords": merged_keywords,
        "merged_wishlists": merged_wishlists,
        "merged_alerts": merged_alerts,
        "dnd_synced": dnd_synced,
    }


def _link_and_sync_device_to_user(db: Session, user_id: int, device_uuid: str) -> dict:
    """기기를 계정에 귀속(타 계정 하이재킹 409 차단)하고 계정 내 모든 연결 기기 간 양방향 병합 수행"""
    existing_link = db.query(models.UserDeviceLink).filter(
        models.UserDeviceLink.device_uuid == device_uuid
    ).first()
    if existing_link is not None and existing_link.user_id != user_id:
        raise HTTPException(status_code=409, detail="Device is already linked to another account")

    if existing_link is None:
        db.add(models.UserDeviceLink(user_id=user_id, device_uuid=device_uuid))
        db.flush()

    peer_links = db.query(models.UserDeviceLink).filter(
        models.UserDeviceLink.user_id == user_id
    ).all()

    total_kw = 0
    total_wl = 0
    total_al = 0
    any_dnd = False
    for peer in peer_links:
        if peer.device_uuid == device_uuid:
            continue
        res_in = _merge_device_data(db, peer.device_uuid, device_uuid)
        res_out = _merge_device_data(db, device_uuid, peer.device_uuid)
        total_kw += res_in["merged_keywords"] + res_out["merged_keywords"]
        total_wl += res_in["merged_wishlists"] + res_out["merged_wishlists"]
        total_al += res_in["merged_alerts"] + res_out["merged_alerts"]
        any_dnd = any_dnd or res_in["dnd_synced"] or res_out["dnd_synced"]

    return {
        "merged_keywords": total_kw,
        "merged_wishlists": total_wl,
        "merged_alerts": total_al,
        "dnd_synced": any_dnd,
    }


@router.post("/signup", response_model=UserResponse)
def signup(
    user: UserCreate,
    auth_device_uuid: Optional[str] = Depends(optional_device_token),
    db: Session = Depends(get_db_session),
):
    if auth_device_uuid:
        existing_link = db.query(models.UserDeviceLink).filter(
            models.UserDeviceLink.device_uuid == auth_device_uuid
        ).first()
        if existing_link is not None:
            raise HTTPException(status_code=409, detail="Device is already linked to another account")

    existing_user = db.query(models.User).filter(models.User.username == user.username).first()
    if existing_user:
        raise HTTPException(status_code=400, detail="Username already registered")

    existing_nick = db.query(models.User).filter(models.User.nickname == user.nickname).first()
    if existing_nick:
        raise HTTPException(status_code=400, detail="Nickname already taken")

    new_user = models.User(
        username=user.username,
        password_hash=hash_password(user.password),
        nickname=user.nickname,
        token_version=secrets.randbelow(1_900_000_000) + 1,
        created_at=datetime.now(timezone.utc).replace(tzinfo=None),
    )
    db.add(new_user)
    db.flush()

    if auth_device_uuid:
        _link_and_sync_device_to_user(db, new_user.id, auth_device_uuid)

    db.commit()
    db.refresh(new_user)

    token = create_access_token(new_user)
    return {
        "username": new_user.username,
        "nickname": new_user.nickname,
        "honey_points": int(new_user.honey_points or 0),
        "access_token": token,
        "token_type": "bearer",
    }


@router.post("/login", response_model=UserResponse)
def login(
    user: UserLogin,
    auth_device_uuid: Optional[str] = Depends(optional_device_token),
    db: Session = Depends(get_db_session),
):
    db_user = db.query(models.User).filter(models.User.username == user.username).first()
    if not db_user:
        raise HTTPException(status_code=400, detail="Invalid username or password")

    matched, needs_upgrade = verify_password(user.password, db_user.password_hash)
    if not matched:
        raise HTTPException(status_code=400, detail="Invalid username or password")

    if needs_upgrade:
        db_user.password_hash = hash_password(user.password)

    if auth_device_uuid:
        _link_and_sync_device_to_user(db, db_user.id, auth_device_uuid)

    db.commit()
    db.refresh(db_user)

    token = create_access_token(db_user)
    return {
        "username": db_user.username,
        "nickname": db_user.nickname,
        "honey_points": int(db_user.honey_points or 0),
        "access_token": token,
        "token_type": "bearer",
    }


@router.post("/withdraw")
def withdraw(user: UserWithdraw, db: Session = Depends(get_db_session)):
    db_user = db.query(models.User).filter(models.User.username == user.username).first()
    if not db_user:
        raise HTTPException(status_code=400, detail="존재하지 않는 회원입니다.")

    matched, _ = verify_password(user.password, db_user.password_hash)
    if not matched:
        raise HTTPException(status_code=400, detail="비밀번호가 올바르지 않습니다.")

    db.query(models.UserDeviceLink).filter(models.UserDeviceLink.user_id == db_user.id).delete()
    db.delete(db_user)
    db.commit()
    return {"success": True, "message": "회원 탈퇴가 완료되었습니다."}


@router.post("/pairing-code")
def issue_pairing_code(
    request: Request,
    source_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    """6자리 일회성 기기 동기화 코드 발급 (10분 만료, X-Device-Token 필수)"""
    now_ts = time.time()
    issue_entries = _prune_window(_pairing_issue_rate_limit, source_uuid, now_ts, _PAIRING_WINDOW_SECONDS)
    if len(issue_entries) >= _PAIRING_ISSUE_MAX_PER_WINDOW:
        raise HTTPException(status_code=429, detail="Too many pairing code requests")
    issue_entries.append(now_ts)
    _pairing_issue_rate_limit[source_uuid] = issue_entries

    now_utc_naive = datetime.now(timezone.utc).replace(tzinfo=None)
    expires_naive = now_utc_naive + timedelta(seconds=_PAIRING_TTL_SECONDS)

    code = ""
    for _ in range(10):
        candidate = f"{secrets.randbelow(1_000_000):06d}"
        exists = db.query(models.DevicePairingCode.id).filter(
            models.DevicePairingCode.code == candidate
        ).first()
        if not exists:
            code = candidate
            break
    if not code:
        raise HTTPException(status_code=503, detail="Failed to generate unique pairing code")

    row = models.DevicePairingCode(
        code=code,
        source_device_uuid=source_uuid,
        expires_at=expires_naive,
    )
    db.add(row)
    db.commit()

    expires_iso = to_utc_iso(expires_naive)
    return {
        "code": code,
        "expires_at": expires_iso,
        "expiresAt": expires_iso,
        "expires_in_sec": _PAIRING_TTL_SECONDS,
        "expiresInSec": _PAIRING_TTL_SECONDS,
    }


def _execute_claim_pairing_code(
    req: ClaimPairingCodeReq,
    request: Request,
    target_uuid: str,
    db: Session,
) -> dict:
    now_ts = time.time()
    client_ip = _extract_client_ip(request)

    ip_entries = _prune_window(_pairing_ip_rate_limit, client_ip, now_ts, _PAIRING_WINDOW_SECONDS)
    if len(ip_entries) >= _PAIRING_IP_MAX_ATTEMPTS:
        raise HTTPException(status_code=429, detail="Too many pairing attempts from this IP")

    dev_entries = _prune_window(_pairing_device_fail_attempts, target_uuid, now_ts, _PAIRING_WINDOW_SECONDS)
    if len(dev_entries) >= _PAIRING_DEVICE_MAX_FAILS:
        raise HTTPException(status_code=429, detail="Too many failed pairing attempts; device temporarily locked")

    cleaned_code = (req.code or "").strip()
    if not re.fullmatch(r"\d{6}", cleaned_code):
        _record_pairing_failure(target_uuid, client_ip, now_ts)
        raise HTTPException(status_code=400, detail="Invalid pairing code format")

    pairing = db.query(models.DevicePairingCode).filter(
        models.DevicePairingCode.code == cleaned_code
    ).first()
    if pairing is None:
        _record_pairing_failure(target_uuid, client_ip, now_ts)
        raise HTTPException(status_code=404, detail="Pairing code not found")

    if pairing.used_at is not None:
        raise HTTPException(status_code=409, detail="Pairing code has already been used")

    if pairing.source_device_uuid == target_uuid:
        raise HTTPException(status_code=400, detail="Cannot merge a device with itself")

    now_utc = datetime.now(timezone.utc)
    now_utc_naive = now_utc.replace(tzinfo=None)
    expires_utc = _to_utc(pairing.expires_at)
    if expires_utc is None or expires_utc <= now_utc:
        pairing.failed_attempts = int(pairing.failed_attempts or 0) + 1
        db.commit()
        _record_pairing_failure(target_uuid, client_ip, now_ts)
        raise HTTPException(status_code=400, detail="Pairing code has expired")

    # 원자적 1회 소비 (레이스 컨디션 방지)
    res = db.execute(
        update(models.DevicePairingCode)
        .where(
            models.DevicePairingCode.id == pairing.id,
            models.DevicePairingCode.used_at.is_(None),
            models.DevicePairingCode.expires_at > now_utc_naive,
        )
        .values(used_at=now_utc_naive, claimed_by_device_uuid=target_uuid)
    )
    if res.rowcount != 1:
        db.rollback()
        fresh = db.query(models.DevicePairingCode).filter(models.DevicePairingCode.id == pairing.id).first()
        if fresh is not None and fresh.used_at is not None:
            raise HTTPException(status_code=409, detail="Pairing code has already been used")
        if fresh is not None:
            fresh.failed_attempts = int(fresh.failed_attempts or 0) + 1
            db.commit()
        raise HTTPException(status_code=400, detail="Pairing code is no longer valid")

    stats = _merge_device_data(db, pairing.source_device_uuid, target_uuid)
    db.commit()
    _pairing_device_fail_attempts.pop(target_uuid, None)

    return {
        "status": "ok",
        "success": True,
        "merged_keywords": stats["merged_keywords"],
        "mergedKeywords": stats["merged_keywords"],
        "merged_wishlists": stats["merged_wishlists"],
        "mergedWishlists": stats["merged_wishlists"],
        "merged_alerts": stats["merged_alerts"],
        "mergedAlerts": stats["merged_alerts"],
        "dnd_synced": stats["dnd_synced"],
        "dndSynced": stats["dnd_synced"],
    }


@router.post("/claim-pairing-code")
def claim_pairing_code(
    req: ClaimPairingCodeReq,
    request: Request,
    target_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    """6자리 페어링 코드 입력 및 타 기기 데이터 병합"""
    return _execute_claim_pairing_code(req, request, target_uuid, db)


@router.post("/merge-device")
def merge_device_alias(
    req: ClaimPairingCodeReq,
    request: Request,
    target_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    """/api/auth/claim-pairing-code 별칭 엔드포인트"""
    return _execute_claim_pairing_code(req, request, target_uuid, db)


@router.post("/link-device")
def link_device(
    current_user: models.User = Depends(get_current_user),
    device_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    """Bearer + X-Device-Token 이중 검증 기반 계정-기기 연결 및 양방향 동기화"""
    stats = _link_and_sync_device_to_user(db, current_user.id, device_uuid)
    db.commit()
    return {
        "status": "ok",
        "success": True,
        "username": current_user.username,
        "device_uuid": device_uuid,
        "merged_keywords": stats["merged_keywords"],
        "mergedKeywords": stats["merged_keywords"],
        "merged_wishlists": stats["merged_wishlists"],
        "mergedWishlists": stats["merged_wishlists"],
        "merged_alerts": stats["merged_alerts"],
        "mergedAlerts": stats["merged_alerts"],
        "dnd_synced": stats["dnd_synced"],
        "dndSynced": stats["dnd_synced"],
    }
