import datetime
import html
import json
import logging
import os
import re
import tempfile
import threading
from typing import Any, Optional

import httpx

from backend.core.url_utils import (
    apply_affiliate_monetization,
    is_safe_external_url,
    is_search_or_community_url,
)
from backend.database.models import Deal
from backend.database.session import SessionLocal
from backend.services.push_worker import _format_push_price, is_in_dnd_range

logger = logging.getLogger(__name__)

_KST = datetime.timezone(datetime.timedelta(hours=9))
_BACKEND_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_STATE_LOCK = threading.RLock()
_MAX_HISTORY_ITEMS = 500
_SPAM_TITLE_KEYWORDS = ("출석", "출첵", "출석체크", "적립 이벤트", "포인트 적립")
_DISALLOWED_PHOTO_TOKENS = (
    "d2w999htn2iva3.cloudfront.net",
    "kakaocdn.net/argon",
    "search.pstatic.net",
    "nplus-store/og_",
    "_next/static/og/",
    "shopv.pstatic.net/web/r/",
    "/small_",
    "noimage",
)


def _parse_int_env(name: str, default: int, minimum: int = 0) -> int:
    raw = os.getenv(name, "").strip()
    if raw:
        try:
            val = int(raw)
            if val >= minimum:
                return val
        except ValueError:
            pass
    return default


def get_telegram_config() -> dict[str, Any]:
    token = os.getenv("TELEGRAM_BOT_TOKEN", "").strip()
    channel_id = os.getenv("TELEGRAM_CHANNEL_ID", "").strip()
    enabled_raw = os.getenv("TELEGRAM_ENABLED", "true").strip().lower()
    enabled = enabled_raw not in ("0", "false", "no", "off")
    quiet_hours = os.getenv("TELEGRAM_QUIET_HOURS", "00:00-07:30").strip()
    public_web_base = (
        os.getenv("TELEGRAM_PUBLIC_WEB_BASE", "https://eolmande.pages.dev").strip().rstrip("/")
        or "https://eolmande.pages.dev"
    )
    state_file = (
        os.getenv("TELEGRAM_STATE_FILE", "").strip()
        or os.path.join(_BACKEND_DIR, "logs", "telegram_broadcast_state.json")
    )
    return {
        "bot_token": token,
        "channel_id": channel_id,
        "configured": bool(token and channel_id),
        "enabled": enabled,
        "min_honey_score": _parse_int_env("TELEGRAM_MIN_HONEY_SCORE", 100, minimum=0),
        "max_daily_posts": _parse_int_env("TELEGRAM_MAX_DAILY_POSTS", 20, minimum=1),
        "min_interval_sec": _parse_int_env("TELEGRAM_MIN_INTERVAL_SEC", 180, minimum=0),
        "quiet_hours": quiet_hours,
        "public_web_base": public_web_base,
        "state_file": state_file,
    }


def _now_kst(override: Optional[datetime.datetime] = None) -> datetime.datetime:
    if override is None:
        return datetime.datetime.now(_KST)
    if override.tzinfo is None:
        return override.replace(tzinfo=_KST)
    return override.astimezone(_KST)


def _default_state(date_str: str) -> dict[str, Any]:
    return {
        "date_kst": date_str,
        "daily_count": 0,
        "last_sent_ts": 0.0,
        "last_sent_at_kst": None,
        "sent_deal_ids": [],
        "sent_dedup_keys": [],
        "sent_total": 0,
    }


def _load_state(state_file: str, date_str: str) -> dict[str, Any]:
    state = _default_state(date_str)
    if state_file and os.path.exists(state_file):
        try:
            with open(state_file, "r", encoding="utf-8") as f:
                raw = json.load(f)
            if isinstance(raw, dict):
                state["sent_total"] = int(raw.get("sent_total") or 0)
                state["last_sent_ts"] = float(raw.get("last_sent_ts") or 0.0)
                state["last_sent_at_kst"] = raw.get("last_sent_at_kst")
                state["sent_deal_ids"] = [
                    int(x) for x in (raw.get("sent_deal_ids") or [])[-_MAX_HISTORY_ITEMS:]
                    if isinstance(x, (int, str)) and str(x).isdigit()
                ]
                state["sent_dedup_keys"] = [
                    str(x) for x in (raw.get("sent_dedup_keys") or [])[-_MAX_HISTORY_ITEMS:] if x
                ]
                if raw.get("date_kst") == date_str:
                    state["daily_count"] = int(raw.get("daily_count") or 0)
        except Exception as err:
            logger.warning(f"[Telegram] 상태 파일 로드 실패 ({state_file}): {err}")
    return state


def _save_state(state_file: str, state: dict[str, Any]) -> None:
    if not state_file:
        return
    try:
        parent = os.path.dirname(os.path.abspath(state_file))
        if parent:
            os.makedirs(parent, exist_ok=True)
        payload = {
            "date_kst": state.get("date_kst"),
            "daily_count": int(state.get("daily_count") or 0),
            "last_sent_ts": float(state.get("last_sent_ts") or 0.0),
            "last_sent_at_kst": state.get("last_sent_at_kst"),
            "sent_deal_ids": list(state.get("sent_deal_ids") or [])[-_MAX_HISTORY_ITEMS:],
            "sent_dedup_keys": list(state.get("sent_dedup_keys") or [])[-_MAX_HISTORY_ITEMS:],
            "sent_total": int(state.get("sent_total") or 0),
        }
        fd, tmp_path = tempfile.mkstemp(dir=parent or ".", prefix="tg_state_", suffix=".tmp")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False)
        os.replace(tmp_path, state_file)
    except Exception as err:
        logger.warning(f"[Telegram] 상태 파일 저장 실패 ({state_file}): {err}")


def _build_deal_dedup_key(deal: Any) -> str:
    shop = re.sub(r"\s+", "", str(getattr(deal, "shop_name", None) or "").lower())
    title = str(getattr(deal, "title", None) or "").strip()
    title = re.sub(r"^\[[^\]]+\]\s*", "", title)
    title_norm = re.sub(r"[^0-9a-z가-힣]+", "", title.lower())[:40]
    return f"{shop}|{title_norm}"


def _is_quiet_time(now_kst: datetime.datetime, quiet_hours: str) -> bool:
    qh = (quiet_hours or "").strip()
    if not qh or "-" not in qh:
        return False
    parts = [p.strip() for p in qh.split("-", 1)]
    if len(parts) != 2 or ":" not in parts[0] or ":" not in parts[1]:
        return False
    return is_in_dnd_range(now_kst.time(), parts[0], parts[1])


def evaluate_telegram_broadcast_eligibility(
    deal: Any,
    now_kst: Optional[datetime.datetime] = None,
    state: Optional[dict[str, Any]] = None,
    force: bool = False,
) -> tuple[bool, str]:
    """초특가 텔레그램 채널 발송 자격과 차단 사유를 판별합니다."""
    if deal is None or not (getattr(deal, "title", None) or "").strip():
        return False, "missing_deal"

    if force:
        return True, "forced"

    cfg = get_telegram_config()
    if not cfg["configured"]:
        return False, "not_configured"
    if not cfg["enabled"]:
        return False, "disabled"
    if bool(getattr(deal, "is_closed", False)):
        return False, "deal_closed"

    title_raw = str(getattr(deal, "title", "") or "").strip()
    cat_raw = str(getattr(deal, "category", None) or "").strip()
    if cat_raw == "적립" or any(kw in title_raw for kw in _SPAM_TITLE_KEYWORDS):
        return False, "filtered_points_or_attendance"

    honey_score = int(getattr(deal, "honey_score", 0) or 0)
    like_count = int(getattr(deal, "like_count", 0) or 0)
    is_super = bool(getattr(deal, "is_super_hotdeal", False))
    if not (is_super or honey_score >= cfg["min_honey_score"] or like_count >= 10):
        return False, "below_hotdeal_threshold"

    now_dt = _now_kst(now_kst)
    date_str = now_dt.strftime("%Y-%m-%d")
    cur_state = state if state is not None else _load_state(cfg["state_file"], date_str)

    deal_id = getattr(deal, "id", None)
    if deal_id is not None and int(deal_id) in (cur_state.get("sent_deal_ids") or []):
        return False, "duplicate_deal_id"

    dedup_key = _build_deal_dedup_key(deal)
    if dedup_key and dedup_key in (cur_state.get("sent_dedup_keys") or []):
        return False, "duplicate_product_key"

    if _is_quiet_time(now_dt, cfg["quiet_hours"]):
        return False, "quiet_hours"

    if int(cur_state.get("daily_count") or 0) >= cfg["max_daily_posts"]:
        return False, "daily_cap_reached"

    last_ts = float(cur_state.get("last_sent_ts") or 0.0)
    if cfg["min_interval_sec"] > 0 and last_ts > 0:
        if (now_dt.timestamp() - last_ts) < cfg["min_interval_sec"]:
            return False, "min_interval_cooldown"

    return True, "eligible"


def _resolve_outbound_url(deal: Any) -> Optional[str]:
    eco = str(getattr(deal, "ecommerce_link", None) or "").strip()
    if not eco or not eco.startswith("https://"):
        return None
    if not is_safe_external_url(eco) or is_search_or_community_url(eco):
        return None
    try:
        aff = apply_affiliate_monetization(eco)
        if aff and aff.startswith("https://") and is_safe_external_url(aff):
            return aff
    except Exception:
        pass
    return eco


def _resolve_photo_url(deal: Any) -> Optional[str]:
    img = str(getattr(deal, "image_url", None) or "").strip()
    if not img or not img.startswith("https://"):
        return None
    low = img.lower()
    if any(tok in low for tok in _DISALLOWED_PHOTO_TOKENS):
        return None
    return img


def _truncate_plain(value: str, limit: int) -> str:
    cleaned = re.sub(r"\s+", " ", (value or "")).strip()
    if len(cleaned) <= limit:
        return cleaned
    return cleaned[: max(0, limit - 1)].rstrip() + "…"


def format_telegram_deal_message(
    deal: Any,
    public_web_base: Optional[str] = None,
) -> dict[str, Any]:
    """딜 정보를 Telegram HTML 메시지(캡션·인라인 버튼·사진 후보)로 포매팅합니다."""
    base = (public_web_base or get_telegram_config()["public_web_base"]).rstrip("/")
    deal_id = int(getattr(deal, "id", 0) or 0)
    share_url = f"{base}/s/{deal_id}" if deal_id > 0 else f"{base}/"

    raw_title = str(getattr(deal, "title", None) or "얼만데 핫딜").strip()
    shop_name = str(getattr(deal, "shop_name", None) or "").strip()
    clean_title = raw_title
    if shop_name:
        clean_title = re.sub(
            rf"^\[\s*{re.escape(shop_name)}\s*\]\s*",
            "",
            clean_title,
            flags=re.IGNORECASE,
        ).strip() or raw_title

    price_raw = getattr(deal, "price", 0)
    try:
        price_int = int(float(str(price_raw or "0").replace(",", "").strip()))
    except (TypeError, ValueError):
        price_int = 0
    currency = str(getattr(deal, "currency", None) or "KRW").strip().upper()
    if price_int > 0:
        price_txt = _format_push_price(price_int, currency)
    elif "무료" in raw_title or "공짜" in raw_title or "0원" in raw_title:
        price_txt = "무료"
    else:
        price_txt = "옵션·할인율 확인"

    shipping = str(getattr(deal, "shipping_fee", None) or "").strip()
    ai_summary = str(getattr(deal, "ai_summary", None) or "").strip()

    prefix = f"[{shop_name}] " if shop_name else ""
    header_plain = _truncate_plain(f"{prefix}{clean_title}", 160)
    header_line = f"🔥 <b>{html.escape(header_plain)}</b>"

    price_line = f"💰 <b>{html.escape(price_txt)}</b>"
    if shipping:
        price_line += f" · {html.escape(_truncate_plain(shipping, 40))}"

    lines = [header_line, price_line]
    if ai_summary:
        lines.append(f"💡 {html.escape(_truncate_plain(ai_summary, 240))}")
    lines.append(f'👉 <a href="{html.escape(share_url, quote=True)}">얼만데에서 상세·시세 보기</a>')
    lines.append("<i>※ 제휴 링크를 통해 구매 시 일정액의 수수료를 제공받을 수 있습니다.</i>")

    text = "\n".join(lines)
    buttons: list[dict[str, str]] = [
        {"text": "🔥 얼만데 상세 보기", "url": share_url},
    ]
    outbound_url = _resolve_outbound_url(deal)
    if outbound_url:
        buttons.append({"text": "🛒 바로 구매", "url": outbound_url})

    return {
        "text": text,
        "photo_url": _resolve_photo_url(deal),
        "reply_markup": {"inline_keyboard": [buttons]},
        "share_url": share_url,
        "outbound_url": outbound_url,
    }


def _mask_channel_id(channel_id: str) -> str:
    if not channel_id:
        return ""
    if channel_id.startswith("@"):
        return channel_id
    if len(channel_id) <= 6:
        return "***"
    return f"{channel_id[:4]}***{channel_id[-3:]}"


def get_telegram_status() -> dict[str, Any]:
    cfg = get_telegram_config()
    now_dt = _now_kst()
    date_str = now_dt.strftime("%Y-%m-%d")
    with _STATE_LOCK:
        state = _load_state(cfg["state_file"], date_str)
    return {
        "configured": cfg["configured"],
        "enabled": cfg["enabled"],
        "channel_id_masked": _mask_channel_id(cfg["channel_id"]),
        "daily_count": int(state.get("daily_count") or 0),
        "max_daily_posts": cfg["max_daily_posts"],
        "min_honey_score": cfg["min_honey_score"],
        "min_interval_sec": cfg["min_interval_sec"],
        "quiet_hours": cfg["quiet_hours"],
        "in_quiet_hours_now": _is_quiet_time(now_dt, cfg["quiet_hours"]),
        "sent_total": int(state.get("sent_total") or 0),
        "last_sent_at_kst": state.get("last_sent_at_kst"),
    }


def send_telegram_deal(
    deal: Deal | Any,
    dry_run: bool = False,
    force: bool = False,
    now_kst: Optional[datetime.datetime] = None,
    http_client: Optional[httpx.Client] = None,
) -> dict[str, Any]:
    """텔레그램 채널로 초특가 딜을 발송합니다 (미설정 시 네트워크 호출 없이 즉시 반환)."""
    cfg = get_telegram_config()
    now_dt = _now_kst(now_kst)
    date_str = now_dt.strftime("%Y-%m-%d")

    if not dry_run:
        if not cfg["configured"]:
            return {"sent": False, "reason": "not_configured"}
        if not cfg["enabled"] and not force:
            return {"sent": False, "reason": "disabled"}

    formatted = format_telegram_deal_message(deal, public_web_base=cfg["public_web_base"])

    with _STATE_LOCK:
        state = _load_state(cfg["state_file"], date_str)
        eligible, reason = evaluate_telegram_broadcast_eligibility(
            deal,
            now_kst=now_dt,
            state=state,
            force=force,
        )

        if dry_run:
            return {
                "sent": False,
                "dry_run": True,
                "eligible": eligible,
                "reason": reason,
                **formatted,
            }

        if not eligible:
            return {"sent": False, "reason": reason}

        api_base = f"https://api.telegram.org/bot{cfg['bot_token']}"
        own_client = http_client is None
        client = http_client or httpx.Client(timeout=8.0)
        method_used = "sendMessage"
        try:
            sent_ok = False
            last_error = ""
            photo_url = formatted.get("photo_url")
            if photo_url and len(formatted["text"]) <= 1024:
                try:
                    photo_res = client.post(
                        f"{api_base}/sendPhoto",
                        json={
                            "chat_id": cfg["channel_id"],
                            "photo": photo_url,
                            "caption": formatted["text"],
                            "parse_mode": "HTML",
                            "reply_markup": formatted["reply_markup"],
                        },
                    )
                    if photo_res.status_code == 200:
                        sent_ok = True
                        method_used = "sendPhoto"
                    else:
                        last_error = f"sendPhoto_http_{photo_res.status_code}"
                except Exception as photo_err:
                    last_error = f"sendPhoto_err:{photo_err}"

            if not sent_ok:
                msg_res = client.post(
                    f"{api_base}/sendMessage",
                    json={
                        "chat_id": cfg["channel_id"],
                        "text": formatted["text"],
                        "parse_mode": "HTML",
                        "disable_web_page_preview": False,
                        "reply_markup": formatted["reply_markup"],
                    },
                )
                if msg_res.status_code == 200:
                    sent_ok = True
                    method_used = "sendMessage"
                else:
                    last_error = f"sendMessage_http_{msg_res.status_code}"

            if not sent_ok:
                return {"sent": False, "reason": last_error or "telegram_api_failed"}

            state["daily_count"] = int(state.get("daily_count") or 0) + 1
            state["sent_total"] = int(state.get("sent_total") or 0) + 1
            state["last_sent_ts"] = now_dt.timestamp()
            state["last_sent_at_kst"] = now_dt.isoformat()
            deal_id = getattr(deal, "id", None)
            if deal_id is not None:
                ids = list(state.get("sent_deal_ids") or [])
                if int(deal_id) not in ids:
                    ids.append(int(deal_id))
                state["sent_deal_ids"] = ids[-_MAX_HISTORY_ITEMS:]
            dedup_key = _build_deal_dedup_key(deal)
            if dedup_key:
                keys = list(state.get("sent_dedup_keys") or [])
                if dedup_key not in keys:
                    keys.append(dedup_key)
                state["sent_dedup_keys"] = keys[-_MAX_HISTORY_ITEMS:]
            _save_state(cfg["state_file"], state)

            return {
                "sent": True,
                "method": method_used,
                "reason": "sent",
                "daily_count": state["daily_count"],
                "share_url": formatted["share_url"],
            }
        except Exception as err:
            logger.warning(f"[Telegram] 채널 발송 오류: {err}")
            return {"sent": False, "reason": f"error:{err}"}
        finally:
            if own_client:
                try:
                    client.close()
                except Exception:
                    pass


def background_broadcast_telegram_hotdeal(deal_id: int, session_factory=None) -> dict[str, Any]:
    """신규/업서트 딜에 대한 텔레그램 초특가 채널 백그라운드 발송 엔트리포인트."""
    cfg = get_telegram_config()
    if not cfg["configured"] or not cfg["enabled"]:
        return {"sent": False, "reason": "not_configured" if not cfg["configured"] else "disabled"}

    factory = session_factory or SessionLocal
    db = factory()
    try:
        deal = db.query(Deal).filter(Deal.id == deal_id).first()
        if not deal:
            return {"sent": False, "reason": "deal_not_found"}
        return send_telegram_deal(deal)
    except Exception as err:
        logger.warning(f"[Telegram] 백그라운드 발송 실패 (deal_id={deal_id}): {err}")
        return {"sent": False, "reason": f"error:{err}"}
    finally:
        try:
            db.close()
        except Exception:
            pass
