import logging
import os
import re
from typing import Any, Optional

import httpx

logger = logging.getLogger(__name__)

_DEFAULT_API_BASE = "https://apps-in-toss-api.toss.im"
_SEND_MESSAGE_PATH = "/api-partner/v1/apps-in-toss/messenger/send-message"


def get_toss_push_config() -> dict[str, Any]:
    """앱인토스 mTLS 기능성 푸시(send-message) 런타임 설정을 조회합니다."""
    cert_path = os.getenv("TOSS_MTLS_CERT_PATH", "").strip()
    key_path = os.getenv("TOSS_MTLS_KEY_PATH", "").strip()
    template_set_code = os.getenv("TOSS_PUSH_TEMPLATE_CODE", "").strip()
    enabled_raw = os.getenv("TOSS_PUSH_ENABLED", "true").strip().lower()
    enabled = enabled_raw not in ("0", "false", "no", "off")
    api_base = (
        os.getenv("TOSS_PUSH_API_BASE", _DEFAULT_API_BASE).strip().rstrip("/")
        or _DEFAULT_API_BASE
    )
    cert_exists = bool(cert_path and os.path.isfile(cert_path))
    key_exists = bool(key_path and os.path.isfile(key_path))
    configured = bool(cert_exists and key_exists and template_set_code)
    return {
        "cert_path": cert_path,
        "key_path": key_path,
        "cert_exists": cert_exists,
        "key_exists": key_exists,
        "template_set_code": template_set_code,
        "template_code_set": bool(template_set_code),
        "enabled": enabled,
        "configured": configured,
        "api_base": api_base,
    }


def get_toss_push_status() -> dict[str, Any]:
    """관리자 조회용 안전 상태 요약 (인증서/키 파일 경로 및 비밀 내용은 절대 미노출)."""
    cfg = get_toss_push_config()
    return {
        "configured": cfg["configured"],
        "enabled": cfg["enabled"],
        "cert_exists": cfg["cert_exists"],
        "key_exists": cfg["key_exists"],
        "template_code_set": cfg["template_code_set"],
        "template_set_code": cfg["template_set_code"],
    }


def sanitize_toss_keyword(keyword: str, max_len: int = 12) -> str:
    """토스 푸시 본문 길이 제한(25자) 준수를 위해 키워드 표시 문자열을 정제·축약합니다."""
    cleaned = re.sub(r"\s+", " ", (keyword or "").strip())
    if not cleaned:
        return "관심상품"
    if len(cleaned) <= max_len:
        return cleaned
    return cleaned[: max(1, max_len - 1)].rstrip() + "…"


def _sanitize_context(context: Optional[dict[str, Any]]) -> dict[str, str]:
    out: dict[str, str] = {}
    if not isinstance(context, dict):
        return out
    for key, val in context.items():
        clean_key = str(key or "").strip()
        if not clean_key or val is None:
            continue
        out[clean_key] = str(val).strip()
    return out


def send_toss_push_message(
    anon_key: str,
    context: Optional[dict[str, Any]] = None,
    template_set_code: Optional[str] = None,
    http_client: Optional[httpx.Client] = None,
) -> dict[str, Any]:
    """
    앱인토스 파트너 API(/messenger/send-message)로 mTLS 기반 기능성 푸시를 발송합니다.
    mTLS 인증서 또는 템플릿 코드가 미설정된 환경에서는 네트워크 호출 없이 즉시 반환합니다.
    """
    clean_anon_key = (anon_key or "").strip()
    if not clean_anon_key:
        return {"sent": False, "reason": "missing_anon_key"}

    cfg = get_toss_push_config()
    if not cfg["enabled"]:
        return {"sent": False, "reason": "disabled"}

    resolved_code = (template_set_code or cfg["template_set_code"] or "").strip()
    if not resolved_code:
        return {"sent": False, "reason": "not_configured"}

    own_client = http_client is None
    if own_client and not (cfg["cert_exists"] and cfg["key_exists"]):
        return {"sent": False, "reason": "not_configured"}

    url = f"{cfg['api_base']}{_SEND_MESSAGE_PATH}"
    payload = {
        "templateSetCode": resolved_code,
        "context": _sanitize_context(context),
    }
    headers = {
        "Content-Type": "application/json",
        "x-anon-key": clean_anon_key,
    }

    client = http_client or httpx.Client(
        cert=(cfg["cert_path"], cfg["key_path"]),
        timeout=8.0,
    )
    try:
        res = client.post(url, json=payload, headers=headers)
        data: dict[str, Any] = {}
        try:
            parsed = res.json()
            if isinstance(parsed, dict):
                data = parsed
        except Exception:
            data = {}

        if res.status_code == 200:
            result_type = str(data.get("resultType") or "").upper()
            success_obj = data.get("success") if isinstance(data.get("success"), dict) else {}
            fail_count = int(success_obj.get("failCount") or 0) if success_obj else 0
            msg_count = int(success_obj.get("msgCount") or 0) if success_obj else 0
            if (result_type == "SUCCESS" or not result_type) and fail_count == 0:
                return {
                    "sent": True,
                    "reason": "sent",
                    "msg_count": msg_count,
                }
            err_obj = data.get("error") if isinstance(data.get("error"), dict) else {}
            err_code = str(err_obj.get("errorCode") or data.get("errorCode") or f"fail_{fail_count}")
            logger.warning(f"[TossPush] 발송 거부/부분실패: code={err_code}, fail_count={fail_count}")
            return {
                "sent": False,
                "reason": f"api_error_{err_code}",
                "error_code": err_code,
                "http_status": res.status_code,
            }

        err_obj = data.get("error") if isinstance(data.get("error"), dict) else {}
        err_code = str(err_obj.get("errorCode") or data.get("errorCode") or f"http_{res.status_code}")
        logger.warning(f"[TossPush] HTTP {res.status_code} 실패: error_code={err_code}")
        return {
            "sent": False,
            "reason": f"api_error_{err_code}",
            "error_code": err_code,
            "http_status": res.status_code,
        }
    except Exception as exc:
        logger.warning(f"[TossPush] 요청 예외: {exc}")
        return {"sent": False, "reason": f"error:{exc}"}
    finally:
        if own_client:
            try:
                client.close()
            except Exception:
                pass
