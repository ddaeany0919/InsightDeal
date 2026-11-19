import base64
import hashlib
import hmac
import ipaddress
import logging
import os
from typing import Optional
from fastapi import Header, HTTPException, Request

logger = logging.getLogger(__name__)
_DEV_FALLBACK_SECRET = "insightdeal-dev-device-secret-local-only"
_WARNED_DEV_FALLBACK = False


def get_device_secret() -> str:
    """
    게스트 디바이스 토큰 서명/검증용 시크릿 반환.
    ENV=production(prod) 상태에서 SERVER_DEVICE_SECRET 미설정 시 503 Fail-Closed.
    """
    global _WARNED_DEV_FALLBACK
    secret = os.getenv("SERVER_DEVICE_SECRET", "").strip()
    if secret:
        return secret
    env = os.getenv("ENV", "development").strip().lower()
    if env in ("production", "prod"):
        raise HTTPException(
            status_code=503,
            detail="SERVER_DEVICE_SECRET is not configured",
        )
    if not _WARNED_DEV_FALLBACK:
        logger.warning("[device_auth] SERVER_DEVICE_SECRET 미설정 — 개발용 기본 폴백 키를 사용합니다.")
        _WARNED_DEV_FALLBACK = True
    return _DEV_FALLBACK_SECRET


def create_device_token(device_uuid: str) -> str:
    """v1.<urlsafe_b64_uuid>.<hmac_sha256_hex> 형식의 무만료 디바이스 토큰 생성."""
    cleaned = (device_uuid or "").strip()
    if not cleaned:
        raise ValueError("device_uuid must not be empty")
    secret = get_device_secret()
    payload_b64 = base64.urlsafe_b64encode(cleaned.encode("utf-8")).decode("ascii").rstrip("=")
    signing_input = f"v1.{payload_b64}"
    sig = hmac.new(secret.encode("utf-8"), signing_input.encode("utf-8"), hashlib.sha256).hexdigest()
    return f"v1.{payload_b64}.{sig}"


def verify_device_token_value(token: Optional[str]) -> str:
    """X-Device-Token 헤더 값을 검증하고 서명된 device_uuid를 반환 (실패 시 401)."""
    raw = (token or "").strip()
    if not raw:
        raise HTTPException(status_code=401, detail="Missing X-Device-Token header")
    parts = raw.split(".")
    if len(parts) != 3 or parts[0] != "v1" or not parts[1] or not parts[2]:
        raise HTTPException(status_code=401, detail="Invalid X-Device-Token format")
    _, payload_b64, provided_sig = parts
    secret = get_device_secret()
    expected_sig = hmac.new(
        secret.encode("utf-8"),
        f"v1.{payload_b64}".encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    if not hmac.compare_digest(provided_sig, expected_sig):
        raise HTTPException(status_code=401, detail="Invalid X-Device-Token signature")
    try:
        padded = payload_b64 + "=" * (-len(payload_b64) % 4)
        device_uuid = base64.urlsafe_b64decode(padded.encode("ascii")).decode("utf-8").strip()
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid X-Device-Token payload")
    if not device_uuid:
        raise HTTPException(status_code=401, detail="Empty device_uuid in X-Device-Token")
    return device_uuid


def require_device_token(
    x_device_token: Optional[str] = Header(default=None, alias="X-Device-Token"),
) -> str:
    """FastAPI 필수 게스트 디바이스 토큰 의존성 (401 또는 503)."""
    return verify_device_token_value(x_device_token)


def optional_device_token(
    x_device_token: Optional[str] = Header(default=None, alias="X-Device-Token"),
) -> Optional[str]:
    """FastAPI 선택 게스트 디바이스 토큰 의존성 (미첨부 시 None, 첨부 시 서명 검증)."""
    if not x_device_token or not x_device_token.strip():
        return None
    return verify_device_token_value(x_device_token)


def assert_device_owner(auth_device_uuid: str, target_id: Optional[str]) -> str:
    """요청 타깃 device_uuid/user_id가 토큰의 소유자와 일치하는지 검증 (불일치 시 403)."""
    target = (target_id or "").strip()
    if target and target != auth_device_uuid:
        raise HTTPException(
            status_code=403,
            detail="X-Device-Token does not match target device_uuid/user_id",
        )
    return auth_device_uuid


def get_trusted_proxy_ips() -> set[str]:
    """신뢰할 수 있는 리버스 프록시 IP/호스트 목록 반환 (환경변수 TRUSTED_PROXY_IPS)"""
    raw = os.getenv("TRUSTED_PROXY_IPS", "127.0.0.1,::1,localhost,testclient")
    return {item.strip().lower() for item in raw.split(",") if item.strip()}


def extract_client_ip(request: Request) -> str:
    """
    신뢰 프록시 경유 시에만 X-Forwarded-For(우측 최초 비신뢰 유효 IP) / X-Real-IP를 신뢰하고,
    외부 직접 접속 시에는 스푸핑 방지를 위해 request.client.host만 강제 사용합니다.
    """
    peer_ip = ((request.client.host if request.client else "") or "").strip()
    trusted_proxies = get_trusted_proxy_ips()
    if peer_ip.lower() not in trusted_proxies:
        return peer_ip or "unknown"

    xff = (request.headers.get("x-forwarded-for") or "").strip()
    if xff:
        parts = [p.strip() for p in xff.split(",") if p.strip()]
        for raw_tok in reversed(parts):
            try:
                ip_obj = ipaddress.ip_address(raw_tok)
                norm_ip = str(ip_obj)
                if norm_ip.lower() not in trusted_proxies:
                    return norm_ip
            except ValueError:
                break

    xri = (request.headers.get("x-real-ip") or "").strip()
    if xri:
        try:
            ip_obj = ipaddress.ip_address(xri)
            norm_ip = str(ip_obj)
            if norm_ip.lower() not in trusted_proxies:
                return norm_ip
        except ValueError:
            pass

    return peer_ip or "unknown"
