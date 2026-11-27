# -*- coding: utf-8 -*-
import asyncio
import hashlib
import json
import logging
import math
import os
import re
import sys
import threading
import time
from datetime import datetime, timedelta, timezone
from typing import Any, Callable, Optional

import httpx
from bs4 import BeautifulSoup
from sqlalchemy import and_, func, or_

# 환경 셋업
root_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if root_dir not in sys.path:
    sys.path.append(root_dir)

from dotenv import load_dotenv

for _env_path in (
    os.path.join(root_dir, ".env"),
    os.path.join(root_dir, "backend", ".env"),
):
    if os.path.exists(_env_path):
        load_dotenv(_env_path, override=False)

from backend.core.ai_utils import (  # noqa: E402
    get_cloudflare_ai_config,
    get_gemini_summary_model,
    get_microbatch_gemini_key,
    get_openai_compatible_config,
    is_ai_summary_enabled,
    mark_key_dead,
)
from backend.database.models import Deal  # noqa: E402
from backend.database.session import SessionLocal  # noqa: E402

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

_BUDGET_STATE_PATH = os.path.join(root_dir, "backend", ".ai_budget_state.json")
_STATE_LOCK = threading.Lock()
_DAILY_BUDGET = {"date": "", "calls": 0, "tokens": 0}
_BUDGET_LOADED_FROM_DISK = False
_LAST_CALL_MONOTONIC = 0.0
_PROVIDER_COOLDOWN_UNTIL: dict[str, float] = {}

_MICROBATCH_LOCK = threading.Lock()
_PENDING_DEAL_IDS: list[int] = []
_MICROBATCH_TASK: Optional[asyncio.Task] = None

_STUB_AI_SUMMARIES = (
    "",
    "🔥 [커뮤니티 인증 핫딜]",
    "[커뮤니티 인증 핫딜]",
    "🔥 [커뮤니티 인기]",
    "[커뮤니티 인기]",
    "[모음전]",
)

_COMPLEX_SIGNAL_RE = re.compile(
    r"(쿠폰|할인|체감|카드|페이|토스|카카오|네이버|적립|캐시백|코드|프로모션|경유|"
    r"선착순|관부과세|직구|옵션|개당|캔당|팩당|원대|최종|결제|사은품|증정|마감|임박|"
    r"유통기한|리퍼|중고|뽑기|래플|멤버십|와우|네멤)",
    re.IGNORECASE,
)

_ALLOWED_ITEM_KEYS = ("체감가", "구매경로", "스펙/구성", "주의")
_KRW_AMOUNT_RE = re.compile(r"(\d[\d,]{2,})\s*원")
_FX_AMOUNT_RE = re.compile(r"(?:\$|USD\s*)(\d+(?:\.\d{1,2})?)", re.IGNORECASE)
_HTML_BLOCK_BREAK_RE = re.compile(r"(?:<br\s*/?>|</(?:p|div|li|tr|h[1-6])>)", re.IGNORECASE)


def _unsummarized_ai_filter():
    return or_(
        Deal.ai_summary_status.in_(("pending", "failed")),
        and_(
            or_(Deal.ai_summary_status.is_(None), Deal.ai_summary_status == ""),
            or_(
                Deal.ai_summary.is_(None),
                func.trim(Deal.ai_summary).in_(_STUB_AI_SUMMARIES),
            ),
        ),
    )


class _ProviderCallError(Exception):
    def __init__(self, provider: str, status_code: Optional[int], message: str):
        super().__init__(message)
        self.provider = provider
        self.status_code = status_code


def _is_test_runtime() -> bool:
    return (
        os.getenv("ENV") == "test"
        or "pytest" in sys.modules
        or "memory" in os.getenv("DATABASE_URL", "").lower()
    )


def reset_ai_worker_state() -> None:
    """일일 호출/토큰 카운터, 최소 간격, 쿨다운, 대기 큐를 초기화합니다 (테스트 격리용)."""
    global _MICROBATCH_TASK, _BUDGET_LOADED_FROM_DISK, _LAST_CALL_MONOTONIC
    with _STATE_LOCK:
        _DAILY_BUDGET["date"] = ""
        _DAILY_BUDGET["calls"] = 0
        _DAILY_BUDGET["tokens"] = 0
        _BUDGET_LOADED_FROM_DISK = False
        _LAST_CALL_MONOTONIC = 0.0
        _PROVIDER_COOLDOWN_UNTIL.clear()
    with _MICROBATCH_LOCK:
        _PENDING_DEAL_IDS.clear()
        _MICROBATCH_TASK = None


def _utc_today_str() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%d")


def _load_budget_state_unlocked() -> None:
    global _BUDGET_LOADED_FROM_DISK
    if _BUDGET_LOADED_FROM_DISK:
        return
    _BUDGET_LOADED_FROM_DISK = True
    if _is_test_runtime() or not os.path.exists(_BUDGET_STATE_PATH):
        return
    try:
        with open(_BUDGET_STATE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        today = _utc_today_str()
        if isinstance(data, dict) and data.get("date") == today:
            _DAILY_BUDGET["date"] = today
            _DAILY_BUDGET["calls"] = max(0, int(data.get("calls", 0) or 0))
            _DAILY_BUDGET["tokens"] = max(0, int(data.get("tokens", 0) or 0))
    except Exception as exc:
        logger.debug("AI budget state load skipped: %s", exc)


def _save_budget_state_unlocked() -> None:
    if _is_test_runtime():
        return
    try:
        tmp_path = f"{_BUDGET_STATE_PATH}.tmp"
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(_DAILY_BUDGET, f, ensure_ascii=False)
        os.replace(tmp_path, _BUDGET_STATE_PATH)
    except Exception as exc:
        logger.debug("AI budget state save skipped: %s", exc)


def _rollover_budget_day_unlocked() -> None:
    _load_budget_state_unlocked()
    today = _utc_today_str()
    if _DAILY_BUDGET["date"] != today:
        _DAILY_BUDGET["date"] = today
        _DAILY_BUDGET["calls"] = 0
        _DAILY_BUDGET["tokens"] = 0


def get_daily_call_count() -> int:
    with _STATE_LOCK:
        _rollover_budget_day_unlocked()
        return int(_DAILY_BUDGET["calls"])


def get_daily_token_count() -> int:
    with _STATE_LOCK:
        _rollover_budget_day_unlocked()
        return int(_DAILY_BUDGET["tokens"])


def _get_max_daily_calls() -> int:
    try:
        return max(0, int(os.getenv("AI_MAX_DAILY_CALLS", "60") or "60"))
    except ValueError:
        return 60


def _get_max_daily_tokens() -> int:
    try:
        return max(0, int(os.getenv("AI_MAX_DAILY_TOKENS", "150000") or "150000"))
    except ValueError:
        return 150000


def _get_max_batch_size() -> int:
    try:
        return max(1, min(30, int(os.getenv("AI_MICROBATCH_MAX_SIZE", "12") or "12")))
    except ValueError:
        return 12


def _get_content_preview_chars() -> int:
    try:
        return max(0, min(1500, int(os.getenv("AI_CONTENT_PREVIEW_CHARS", "900") or "900")))
    except ValueError:
        return 900


def _get_rate_limit_cooldown_sec() -> float:
    try:
        return max(1.0, float(os.getenv("AI_RATE_LIMIT_COOLDOWN_SEC", "300") or "300"))
    except ValueError:
        return 300.0


def _get_min_call_interval_sec() -> float:
    try:
        return max(0.0, float(os.getenv("AI_MIN_CALL_INTERVAL_SEC", "15") or "15"))
    except ValueError:
        return 15.0


def _get_microbatch_debounce_sec() -> float:
    try:
        return max(0.0, float(os.getenv("AI_MICROBATCH_DEBOUNCE_SEC", "20") or "20"))
    except ValueError:
        return 20.0


def _estimate_tokens(text: str) -> int:
    """한글/영문 혼합 텍스트의 보수적인 토큰 수를 추정합니다 (한글 1자 ≈ 0.8~1.2토큰)."""
    if not text:
        return 0
    return max(1, math.ceil(len(text) * 0.9))


def _reserve_call_budget(
    estimated_input_tokens: int,
    enforce_min_interval: bool = True,
) -> tuple[bool, str]:
    global _LAST_CALL_MONOTONIC
    max_calls = _get_max_daily_calls()
    max_tokens = _get_max_daily_tokens()
    min_interval = _get_min_call_interval_sec()

    if max_calls <= 0 or max_tokens <= 0:
        return False, "hard_cap_disabled"

    now_mono = time.monotonic()
    with _STATE_LOCK:
        _rollover_budget_day_unlocked()
        if (
            enforce_min_interval
            and min_interval > 0
            and _LAST_CALL_MONOTONIC > 0
            and (now_mono - _LAST_CALL_MONOTONIC) < min_interval
        ):
            return False, "min_interval"
        if _DAILY_BUDGET["calls"] >= max_calls:
            return False, "daily_calls"
        if (_DAILY_BUDGET["tokens"] + max(0, estimated_input_tokens)) > max_tokens:
            return False, "daily_tokens"

        _DAILY_BUDGET["calls"] += 1
        _DAILY_BUDGET["tokens"] += max(0, estimated_input_tokens)
        _LAST_CALL_MONOTONIC = now_mono
        _save_budget_state_unlocked()
        return True, "ok"


def _reconcile_token_usage(
    estimated_input_tokens: int,
    actual_total_tokens: Optional[int],
    raw_output_text: str = "",
) -> None:
    final_tokens = (
        max(0, int(actual_total_tokens))
        if actual_total_tokens is not None
        else max(0, estimated_input_tokens) + _estimate_tokens(raw_output_text)
    )
    delta = final_tokens - max(0, estimated_input_tokens)
    if delta == 0:
        return
    with _STATE_LOCK:
        _rollover_budget_day_unlocked()
        _DAILY_BUDGET["tokens"] = max(0, _DAILY_BUDGET["tokens"] + delta)
        _save_budget_state_unlocked()


def _is_provider_in_cooldown(provider: str) -> bool:
    now = time.monotonic()
    with _STATE_LOCK:
        until = _PROVIDER_COOLDOWN_UNTIL.get(provider, 0.0)
        return now < until


def _set_provider_cooldown(provider: str, seconds: Optional[float] = None) -> None:
    duration = seconds if seconds is not None else _get_rate_limit_cooldown_sec()
    with _STATE_LOCK:
        _PROVIDER_COOLDOWN_UNTIL[provider] = time.monotonic() + duration


def _clean_content_for_ai(raw_html: Optional[str]) -> str:
    if not raw_html:
        return ""
    cleaned = re.sub(r"\[상품이미지\s*:\s*https?://[^\]]+\]", "\n", str(raw_html), flags=re.IGNORECASE)
    cleaned = re.sub(r"\[첨부된\s*이미지[^\]]*\]", "\n", cleaned, flags=re.IGNORECASE)
    cleaned = _HTML_BLOCK_BREAK_RE.sub("\n", cleaned)
    try:
        text = BeautifulSoup(cleaned, "html.parser").get_text("", strip=False)
    except Exception:
        text = re.sub(r"<[^>]+>", " ", cleaned)
    text = re.sub(r"https?://\S+", " ", text)
    lines = []
    for raw_line in text.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        norm_line = re.sub(r"[^\S\n]+", " ", raw_line).strip()
        if norm_line:
            lines.append(norm_line)
    return "\n".join(lines)


def _extract_keyword_windows_one_liner(clean_text: str, budget: int = 350) -> str:
    windows: list[tuple[int, int]] = []
    for m in _COMPLEX_SIGNAL_RE.finditer(clean_text):
        start = max(0, m.start() - 80)
        end = min(len(clean_text), m.end() + 80)
        if windows and start <= windows[-1][1] + 10:
            windows[-1] = (windows[-1][0], max(windows[-1][1], end))
        else:
            windows.append((start, end))
    parts: list[str] = []
    used = 0
    for s, e in windows:
        snippet = clean_text[s:e].strip()
        if not snippet:
            continue
        add_len = len(snippet) + (3 if parts else 0)
        if used + add_len > budget and parts:
            break
        parts.append(snippet[: max(40, budget - used)])
        used += len(parts[-1]) + 3
        if used >= budget:
            break
    return " / ".join(parts)


def _strip_html_preview(raw_html: Optional[str], max_chars: int = 900) -> str:
    """줄바꿈을 보존한 채 본문을 스마트 발췌(Head + Keyword/Window + Tail)합니다."""
    if not raw_html or max_chars <= 0:
        return ""
    clean_text = _clean_content_for_ai(raw_html)
    if not clean_text:
        return ""
    if len(clean_text) <= max_chars:
        return clean_text

    head_budget = min(350, max(120, int(max_chars * 0.39)))
    tail_budget = min(200, max(80, int(max_chars * 0.22)))
    mid_budget = max(0, max_chars - head_budget - tail_budget - 8)

    lines = clean_text.split("\n")
    head = clean_text[:head_budget].rstrip()
    tail = clean_text[-tail_budget:].lstrip()

    mid = ""
    if mid_budget > 0:
        if len(lines) <= 2 and len(clean_text) > 400:
            mid = _extract_keyword_windows_one_liner(clean_text[head_budget:-tail_budget], mid_budget)
        else:
            kw_lines: list[str] = []
            used = 0
            for ln in lines:
                if _COMPLEX_SIGNAL_RE.search(ln) and ln not in head and ln not in tail:
                    ln_clip = ln[:140]
                    if used + len(ln_clip) + 1 > mid_budget and kw_lines:
                        break
                    kw_lines.append(ln_clip)
                    used += len(ln_clip) + 1
            mid = "\n".join(kw_lines)

    chunks = [head]
    if mid:
        chunks.append(mid)
    if tail and tail not in head:
        chunks.append(tail)
    return "\n...\n".join(chunks)[:max_chars]


def _compute_ai_source_hash(deal: Deal, clean_text: str) -> str:
    payload = f"{getattr(deal, 'title', '') or ''}|{getattr(deal, 'price', '') or ''}|{getattr(deal, 'currency', '') or ''}|{clean_text}"
    return hashlib.md5(payload.encode("utf-8")).hexdigest()[:32]


def _should_skip_simple_deal(deal: Deal, clean_text: str) -> Optional[str]:
    text_no_ws = re.sub(r"\s+", "", clean_text or "")
    if len(text_no_ws) < 10:
        return "skipped_empty"

    if len(text_no_ws) >= 60:
        return None

    has_opts = bool(getattr(deal, "has_options", False))
    opts_raw = getattr(deal, "options_data", None)
    if opts_raw:
        try:
            opts_parsed = json.loads(opts_raw) if isinstance(opts_raw, str) else opts_raw
            if isinstance(opts_parsed, list) and len(opts_parsed) > 0:
                has_opts = True
        except Exception:
            pass
    if has_opts:
        return None

    if _COMPLEX_SIGNAL_RE.search(clean_text):
        return None

    deal_curr = (getattr(deal, "currency", None) or "KRW").strip().upper()
    try:
        deal_price_raw = int(float(getattr(deal, "price", 0) or 0))
    except (TypeError, ValueError):
        deal_price_raw = 0

    if deal_curr == "KRW":
        for m in _KRW_AMOUNT_RE.finditer(clean_text):
            try:
                amt = int(m.group(1).replace(",", ""))
            except ValueError:
                continue
            if amt > 0 and (deal_price_raw <= 0 or abs(amt - deal_price_raw) >= 100):
                return None
    else:
        base_fx = deal_price_raw / 100.0 if deal_curr in ("USD", "EUR", "CNY") else float(deal_price_raw)
        for m in _FX_AMOUNT_RE.finditer(clean_text):
            try:
                amt = float(m.group(1))
            except ValueError:
                continue
            if amt > 0 and (base_fx <= 0 or abs(amt - base_fx) >= 1.0):
                return None

    return "skipped_simple"


def _is_deal_hot(deal: Deal) -> bool:
    return bool(
        getattr(deal, "is_super_hotdeal", False)
        or (getattr(deal, "honey_score", 0) or 0) >= 100
        or (
            getattr(deal, "ai_summary", None)
            and ("🔥" in str(deal.ai_summary) or "인증 핫딜" in str(deal.ai_summary))
        )
    )


def _format_price_for_prompt(deal: Deal) -> str:
    try:
        price_val = float(deal.price)
        if deal.currency == "USD":
            return f"${price_val / 100:.2f}"
        if deal.currency == "EUR":
            return f"€{price_val / 100:.2f}"
        if deal.currency == "JPY":
            return f"¥{int(price_val):,}"
        if deal.currency == "CNY":
            return f"¥{price_val / 100:.2f}"
        if int(price_val) <= 0:
            return "가격미상/무료"
        return f"{int(price_val):,}원"
    except Exception:
        return "가격미상"


def _build_batch_prompt(deals_to_process: list[Deal]) -> str:
    preview_chars = _get_content_preview_chars()
    input_data = []
    for d in deals_to_process:
        entry: dict[str, Any] = {
            "id": d.id,
            "t": d.title,
            "p": _format_price_for_prompt(d),
            "shop": (getattr(d, "shop_name", None) or "").strip(),
            "ship": (getattr(d, "shipping_fee", None) or "").strip(),
            "cat": (getattr(d, "category", None) or "").strip(),
        }
        snippet = _strip_html_preview(d.content_html, preview_chars)
        if snippet:
            entry["c"] = snippet
        input_data.append(entry)

    compact_json = json.dumps(input_data, ensure_ascii=False, separators=(",", ":"))
    return (
        "한국 핫딜 구매 조건 구조화 추출기. 각 딜의 제목(t), 가격(p), 판매처(shop), 배송(ship), 카테고리(cat), 본문(c)을 읽고 순수 JSON 배열만 반환하라.\n"
        "규칙:\n"
        '1. 본문에 가격 외 추가 혜택(체감가·쿠폰·결제수단), 복잡한 구매경로, 중요 주의사항/특이 스펙이 없으면 "skip": true, "items": []를 반환하라.\n'
        "2. 제목·가격·판매처·배송비에 이미 있는 정보를 그대로 반복하지 마라.\n"
        "3. 입력에 없는 원산지·스펙·할인·역대가 표현을 추측해 지어내지 마라.\n"
        "4. items는 실제로 새로운 정보가 있는 항목만 1~3개 작성하라(억지로 3개를 채우지 마라).\n"
        '5. 각 item의 k는 "체감가", "구매경로", "스펙/구성", "주의" 중 하나만 사용하고, v 안에 키 이름을 반복하지 마라(각 v 60자 이내).\n'
        '출력 스키마: [{"id":1,"skip":false,"items":[{"k":"체감가","v":"..."}]}]\n'
        f"입력:{compact_json}"
    )


def _extract_bracket_json_objects(cleaned: str) -> list[dict]:
    """문자열 리터럴과 이스케이프를 인지해 잘린 중첩 JSON 배열에서도 완성된 최상위 객체를 복구합니다."""
    objs: list[dict] = []
    depth = 0
    in_string = False
    escaped = False
    obj_start: Optional[int] = None

    for idx, ch in enumerate(cleaned):
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue

        if ch == '"':
            in_string = True
            continue
        if ch == "{":
            if depth == 0:
                obj_start = idx
            depth += 1
        elif ch == "}":
            if depth > 0:
                depth -= 1
                if depth == 0 and obj_start is not None:
                    candidate = cleaned[obj_start : idx + 1]
                    try:
                        parsed_obj = json.loads(candidate)
                        if isinstance(parsed_obj, dict) and "id" in parsed_obj:
                            objs.append(parsed_obj)
                    except json.JSONDecodeError:
                        pass
                    obj_start = None
    return objs


def _sanitize_summary_items(raw_items: Any) -> list[dict[str, str]]:
    if not isinstance(raw_items, list):
        return []
    result: list[dict[str, str]] = []
    for it in raw_items:
        if not isinstance(it, dict):
            continue
        k = str(it.get("k") or "").strip()
        v = str(it.get("v") or "").strip()
        if not k or not v:
            continue
        if k not in _ALLOWED_ITEM_KEYS:
            if "체감" in k or "혜택" in k or "가격" in k:
                k = "체감가"
            elif "구매" in k or "경로" in k or "방법" in k or "쿠폰" in k:
                k = "구매경로"
            elif "주의" in k or "조건" in k:
                k = "주의"
            else:
                k = "스펙/구성"
        v = re.sub(rf"^{re.escape(k)}\s*[:：\-]?\s*", "", v).strip()[:120]
        if len(v) >= 2:
            result.append({"k": k, "v": v})
        if len(result) >= 3:
            break
    return result


def _parse_batch_summaries(raw_text: str) -> dict[int, dict[str, Any]]:
    if not raw_text:
        return {}
    cleaned = raw_text.strip()
    cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s*```$", "", cleaned).strip()

    parsed = None
    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError:
        arr_match = re.search(r"\[.*\]", cleaned, re.DOTALL)
        if arr_match:
            try:
                parsed = json.loads(arr_match.group(0))
            except json.JSONDecodeError:
                parsed = None

    if parsed is None:
        recovered = _extract_bracket_json_objects(cleaned)
        if not recovered:
            return {}
        parsed = recovered

    if isinstance(parsed, dict):
        for key in ("items", "results", "deals", "data"):
            if isinstance(parsed.get(key), list):
                parsed = parsed[key]
                break

    if not isinstance(parsed, list):
        return {}

    summaries: dict[int, dict[str, Any]] = {}
    for entry in parsed:
        if not isinstance(entry, dict):
            continue
        raw_id = entry.get("id")
        if raw_id is None:
            continue
        try:
            deal_id = int(raw_id)
        except (TypeError, ValueError):
            continue

        skip_flag = bool(entry.get("skip", False))
        items = _sanitize_summary_items(entry.get("items"))
        raw_legacy = entry.get("ai_summary")
        legacy_text = raw_legacy.strip()[:1500] if isinstance(raw_legacy, str) and raw_legacy.strip() else ""

        if skip_flag and not items:
            summaries[deal_id] = {"skip": True, "items": [], "legacy_summary": ""}
        elif items:
            summaries[deal_id] = {"skip": False, "items": items, "legacy_summary": ""}
        elif legacy_text:
            summaries[deal_id] = {"skip": False, "items": [], "legacy_summary": legacy_text}
        elif "skip" in entry:
            summaries[deal_id] = {"skip": True, "items": [], "legacy_summary": ""}

    return summaries


def _is_fatal_key_error(status_code: int, body_text: str) -> bool:
    low = (body_text or "").lower()
    if status_code in (401, 403):
        return True
    return any(
        tok in low
        for tok in ("spending cap", "leaked", "api key not valid", "permission_denied")
    )


def _extract_gemini_total_tokens(data: dict) -> Optional[int]:
    usage = data.get("usageMetadata")
    if not isinstance(usage, dict):
        return None
    total = usage.get("totalTokenCount")
    if total is not None:
        try:
            return max(0, int(total))
        except (TypeError, ValueError):
            return None
    return None


def _extract_openai_total_tokens(data: dict) -> Optional[int]:
    usage = data.get("usage")
    if not isinstance(usage, dict):
        return None
    total = usage.get("total_tokens")
    if total is not None:
        try:
            return max(0, int(total))
        except (TypeError, ValueError):
            return None
    return None


def _call_gemini_rest(
    prompt: str, cfg: dict, max_output_tokens: int, timeout: float = 25.0
) -> tuple[str, Optional[int]]:
    api_key = cfg["api_key"]
    model = cfg["model"]
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    thinking_cfg = (
        {"thinkingBudget": 0}
        if model.lower().startswith("gemini-2.")
        else {"thinkingLevel": "MINIMAL"}
    )
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.1,
            "maxOutputTokens": max_output_tokens,
            "responseMimeType": "application/json",
            "thinkingConfig": thinking_cfg,
        },
    }
    headers = {
        "Content-Type": "application/json",
        "x-goog-api-key": api_key,
    }
    with httpx.Client(timeout=timeout) as client:
        resp = client.post(url, json=payload, headers=headers)

    if resp.status_code >= 400:
        body = resp.text[:400]
        if _is_fatal_key_error(resp.status_code, body):
            mark_key_dead(api_key)
        raise _ProviderCallError("gemini", resp.status_code, f"HTTP {resp.status_code}: {body}")

    data = resp.json()
    try:
        parts = data["candidates"][0]["content"]["parts"]
        text = "".join(
            str(p.get("text", ""))
            for p in parts
            if isinstance(p, dict) and not p.get("thought")
        )
        return text, _extract_gemini_total_tokens(data)
    except (KeyError, IndexError, TypeError) as exc:
        raise _ProviderCallError("gemini", resp.status_code, f"Malformed response: {exc}") from exc


def _call_openai_compatible_rest(
    prompt: str, cfg: dict, max_output_tokens: int, timeout: float = 25.0
) -> tuple[str, Optional[int]]:
    provider = cfg["provider"]
    url = f"{cfg['base_url']}/chat/completions"
    payload = {
        "model": cfg["model"],
        "temperature": 0.1,
        "max_tokens": max_output_tokens,
        "messages": [
            {"role": "system", "content": "Return only a valid JSON array."},
            {"role": "user", "content": prompt},
        ],
    }
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {cfg['api_key']}",
    }
    with httpx.Client(timeout=timeout) as client:
        resp = client.post(url, json=payload, headers=headers)

    if resp.status_code >= 400:
        body = resp.text[:400]
        if _is_fatal_key_error(resp.status_code, body):
            mark_key_dead(cfg["api_key"])
        raise _ProviderCallError(provider, resp.status_code, f"HTTP {resp.status_code}: {body}")

    data = resp.json()
    try:
        text = str(data["choices"][0]["message"]["content"])
        return text, _extract_openai_total_tokens(data)
    except (KeyError, IndexError, TypeError) as exc:
        raise _ProviderCallError(provider, resp.status_code, f"Malformed response: {exc}") from exc


def _call_cloudflare_workers_ai_rest(
    prompt: str, cfg: dict, max_output_tokens: int, timeout: float = 25.0
) -> tuple[str, Optional[int]]:
    url = f"https://api.cloudflare.com/client/v4/accounts/{cfg['account_id']}/ai/run/{cfg['model']}"
    payload = {
        "messages": [
            {"role": "system", "content": "Return only a valid JSON array."},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.1,
        "max_tokens": max_output_tokens,
    }
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {cfg['api_token']}",
    }
    with httpx.Client(timeout=timeout) as client:
        resp = client.post(url, json=payload, headers=headers)

    if resp.status_code >= 400:
        body = resp.text[:400]
        if _is_fatal_key_error(resp.status_code, body):
            mark_key_dead(cfg["api_token"])
        raise _ProviderCallError("cloudflare_ai", resp.status_code, f"HTTP {resp.status_code}: {body}")

    data = resp.json()
    try:
        result = data["result"]
        if isinstance(result, dict):
            resp_val = result.get("response")
            text = (
                json.dumps(resp_val, ensure_ascii=False)
                if isinstance(resp_val, (list, dict))
                else str(resp_val or "")
            )
            return text, None
        return str(result), None
    except (KeyError, TypeError) as exc:
        raise _ProviderCallError("cloudflare_ai", resp.status_code, f"Malformed response: {exc}") from exc


def _collect_available_providers() -> list[tuple[str, dict, Callable[[str, dict, int], tuple[str, Optional[int]]]]]:
    providers: list[tuple[str, dict, Callable[[str, dict, int], tuple[str, Optional[int]]]]] = []

    gemini_key = get_microbatch_gemini_key()
    if gemini_key and not _is_provider_in_cooldown("gemini"):
        providers.append(
            (
                "gemini",
                {"api_key": gemini_key, "model": get_gemini_summary_model()},
                _call_gemini_rest,
            )
        )

    openai_cfg = get_openai_compatible_config()
    if openai_cfg and not _is_provider_in_cooldown(openai_cfg["provider"]):
        providers.append(
            (
                openai_cfg["provider"],
                openai_cfg,
                _call_openai_compatible_rest,
            )
        )

    cf_cfg = get_cloudflare_ai_config()
    if cf_cfg and not _is_provider_in_cooldown("cloudflare_ai"):
        providers.append(
            (
                "cloudflare_ai",
                cf_cfg,
                _call_cloudflare_workers_ai_rest,
            )
        )

    return providers


def _invalidate_caches_for_deals(updated_ids: list[int]) -> None:
    if not updated_ids:
        return
    try:
        from backend.routers.community import (
            _evict_deal_detail_cache,
            invalidate_feed_api_cache,
        )

        invalidate_feed_api_cache(clear_ecom_url_cache=False)
        for did in updated_ids:
            _evict_deal_detail_cache(did)
    except Exception as exc:
        logger.debug("AI summary cache invalidation skipped: %s", exc)


def process_ai_batch(
    deal_ids: Optional[list[int]] = None,
    db_session_factory: Optional[Callable] = None,
) -> int:
    if not is_ai_summary_enabled():
        return 0

    factory = db_session_factory or SessionLocal
    db = factory()
    try:
        max_batch = _get_max_batch_size()
        if deal_ids is not None:
            unique_ids = list(dict.fromkeys(int(x) for x in deal_ids if x))[:max_batch]
            if not unique_ids:
                return 0
            deals_to_process = (
                db.query(Deal)
                .filter(
                    Deal.id.in_(unique_ids),
                    _unsummarized_ai_filter(),
                )
                .order_by(Deal.id.desc())
                .all()
            )
        else:
            limit_time = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(hours=48)
            deals_to_process = (
                db.query(Deal)
                .filter(
                    _unsummarized_ai_filter(),
                    Deal.price != "0",
                    Deal.price.isnot(None),
                    or_(Deal.is_closed == False, Deal.is_closed.is_(None)),
                    Deal.indexed_at >= limit_time,
                )
                .order_by(Deal.indexed_at.desc(), Deal.id.desc())
                .limit(max_batch)
                .all()
            )

        if not deals_to_process:
            return 0

        llm_targets: list[Deal] = []
        hash_by_id: dict[int, str] = {}
        hot_by_id: dict[int, bool] = {}
        skipped_ids: list[int] = []

        for d in deals_to_process:
            clean_text = _clean_content_for_ai(d.content_html)
            src_hash = _compute_ai_source_hash(d, clean_text)
            is_hot = _is_deal_hot(d)
            hash_by_id[d.id] = src_hash
            hot_by_id[d.id] = is_hot

            skip_reason = _should_skip_simple_deal(d, clean_text)
            if skip_reason:
                d.ai_summary_status = skip_reason
                d.ai_summary_source_hash = src_hash
                d.ai_summary_json = None
                d.ai_summary = "🔥 [커뮤니티 인증 핫딜] " if is_hot else None
                skipped_ids.append(d.id)
            else:
                llm_targets.append(d)

        if skipped_ids:
            db.commit()
            _invalidate_caches_for_deals(skipped_ids)
            logger.info("⏭️ 단순/초단문 딜 %d건 AI 호출 없이 게이팅 스킵 (%s)", len(skipped_ids), skipped_ids)

        if not llm_targets:
            return len(skipped_ids)

        providers = _collect_available_providers()
        if not providers:
            logger.debug("사용 가능한 무료 AI 프로바이더가 없거나 모두 쿨다운 중입니다.")
            return len(skipped_ids)

        logger.info("🚀 %d개 핫딜 구조화 AI 구매 요약 마이크로배치 시작...", len(llm_targets))
        prompt = _build_batch_prompt(llm_targets)
        estimated_in_tokens = _estimate_tokens(prompt)
        max_output_tokens = min(4096, 300 + len(llm_targets) * 240)

        for idx, (provider_name, cfg, call_fn) in enumerate(providers):
            allowed, reason = _reserve_call_budget(
                estimated_in_tokens, enforce_min_interval=(idx == 0)
            )
            if not allowed:
                logger.warning(
                    "AI 예산/빈도 가드(%s)로 외부 호출 차단 (calls=%d/%d, tokens=%d/%d).",
                    reason,
                    get_daily_call_count(),
                    _get_max_daily_calls(),
                    get_daily_token_count(),
                    _get_max_daily_tokens(),
                )
                return len(skipped_ids)

            try:
                raw_text, actual_total_tokens = call_fn(prompt, cfg, max_output_tokens)
                _reconcile_token_usage(estimated_in_tokens, actual_total_tokens, raw_text)
                summaries_by_id = _parse_batch_summaries(raw_text)
                if not summaries_by_id:
                    logger.warning("[%s] 빈 응답 또는 JSON 파싱 실패 → 다음 프로바이더 시도", provider_name)
                    continue

                updated_ids: list[int] = []
                for d in llm_targets:
                    parsed_entry = summaries_by_id.get(d.id)
                    if not parsed_entry:
                        continue
                    is_hot = hot_by_id.get(d.id, False)
                    d.ai_summary_source_hash = hash_by_id.get(d.id)

                    if parsed_entry.get("skip"):
                        d.ai_summary_status = "skipped_llm"
                        d.ai_summary_json = None
                        d.ai_summary = "🔥 [커뮤니티 인증 핫딜] " if is_hot else None
                        updated_ids.append(d.id)
                    elif parsed_entry.get("items"):
                        items = parsed_entry["items"]
                        d.ai_summary_json = json.dumps(items, ensure_ascii=False)
                        body_lines = "\n".join(f"✅ {it['k']}: {it['v']}" for it in items)
                        d.ai_summary = (
                            f"🔥 [커뮤니티 인증 핫딜]\n{body_lines}" if is_hot else body_lines
                        )[:1500]
                        d.ai_summary_status = "done"
                        updated_ids.append(d.id)
                    elif parsed_entry.get("legacy_summary"):
                        d.ai_summary_json = None
                        d.ai_summary = str(parsed_entry["legacy_summary"])[:1500]
                        d.ai_summary_status = "done"
                        updated_ids.append(d.id)

                if not updated_ids:
                    logger.warning("[%s] 반환 ID가 요청 배치와 일치하지 않음", provider_name)
                    continue

                db.commit()
                _invalidate_caches_for_deals(updated_ids)
                logger.info(
                    "✅ [%s] 구조화 AI 배치 완료: %d/%d건 (일일 %d회/%d토큰)",
                    provider_name,
                    len(updated_ids),
                    len(llm_targets),
                    get_daily_call_count(),
                    get_daily_token_count(),
                )
                return len(skipped_ids) + len(updated_ids)

            except _ProviderCallError as exc:
                if exc.status_code == 429:
                    _set_provider_cooldown(provider_name)
                else:
                    _set_provider_cooldown(provider_name, seconds=30.0)
                logger.warning("⚠️ [%s] 호출 실패 (%s) → 다음 무료 프로바이더 폴백", provider_name, exc)
            except Exception as exc:
                logger.warning("⚠️ [%s] 예외 발생 (%s) → 다음 무료 프로바이더 폴백", provider_name, exc)

        # 실패 시 더미 문자열 저장 금지: pending/None 유지 후 다음 배치 재시도 허용
        return len(skipped_ids)
    finally:
        db.close()


async def _run_microbatch_loop(db_session_factory: Optional[Callable] = None) -> None:
    global _MICROBATCH_TASK
    try:
        while True:
            debounce_sec = _get_microbatch_debounce_sec()
            if debounce_sec > 0:
                await asyncio.sleep(debounce_sec)

            max_batch = _get_max_batch_size()
            with _MICROBATCH_LOCK:
                if not _PENDING_DEAL_IDS:
                    return
                batch_ids = _PENDING_DEAL_IDS[:max_batch]
                del _PENDING_DEAL_IDS[:max_batch]

            try:
                await asyncio.to_thread(process_ai_batch, batch_ids, db_session_factory)
            except Exception as exc:
                logger.warning("AI 마이크로배치 실행 중 비치명 오류: %s", exc)

            with _MICROBATCH_LOCK:
                if not _PENDING_DEAL_IDS:
                    return
    finally:
        with _MICROBATCH_LOCK:
            _MICROBATCH_TASK = None


def schedule_ai_summary_microbatch(
    deal_ids: list[int],
    db_session_factory: Optional[Callable] = None,
) -> bool:
    """
    신규 수집 딜 ID를 디바운스 큐에 적재하고 비차단 마이크로배치로 AI 요약을 생성합니다.
    테스트 환경에서는 AI_MICROBATCH_ALLOW_IN_TEST=true 가 아니면 즉시 no-op 반환합니다.
    """
    global _MICROBATCH_TASK

    allow_in_test = os.getenv("AI_MICROBATCH_ALLOW_IN_TEST", "").strip().lower() in ("true", "1", "yes")
    if not allow_in_test and _is_test_runtime():
        return False

    if not deal_ids or not is_ai_summary_enabled():
        return False

    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        return False

    clean_ids = [int(x) for x in deal_ids if x]
    if not clean_ids:
        return False

    with _MICROBATCH_LOCK:
        existing = set(_PENDING_DEAL_IDS)
        for did in clean_ids:
            if did not in existing:
                _PENDING_DEAL_IDS.append(did)
                existing.add(did)

        if _MICROBATCH_TASK is None or _MICROBATCH_TASK.done():
            _MICROBATCH_TASK = loop.create_task(_run_microbatch_loop(db_session_factory))

    return True


def run_all_batches(max_batches: int = 20) -> int:
    """최근 48시간 미요약 딜을 최대 max_batches 라운드까지 배치 처리합니다."""
    if not is_ai_summary_enabled():
        return 0

    total_processed = 0
    limit_time = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(hours=48)

    for i in range(max_batches):
        db = SessionLocal()
        try:
            count = (
                db.query(Deal)
                .filter(
                    _unsummarized_ai_filter(),
                    Deal.price != "0",
                    Deal.price.isnot(None),
                    or_(Deal.is_closed == False, Deal.is_closed.is_(None)),
                    Deal.indexed_at >= limit_time,
                )
                .count()
            )
        finally:
            db.close()

        if count == 0:
            logger.info("🎉 모든 최근 요약 처리가 완료되었습니다.")
            break

        logger.info("배치 라운드 %d/%d 시작 (남은 최근 항목: %d개)", i + 1, max_batches, count)
        processed = process_ai_batch()
        if processed == 0:
            logger.warning("진척이 없어 배치를 중단합니다.")
            break

        total_processed += processed
        time.sleep(max(2.0, _get_min_call_interval_sec()))

    return total_processed


if __name__ == "__main__":
    run_all_batches()
