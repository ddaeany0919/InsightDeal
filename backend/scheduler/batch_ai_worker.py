# -*- coding: utf-8 -*-
import asyncio
import json
import logging
import os
import re
import sys
import threading
import time
from datetime import datetime, timedelta, timezone
from typing import Callable, Optional

import httpx
from bs4 import BeautifulSoup
from sqlalchemy import or_

# 환경 셋업
root_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if root_dir not in sys.path:
    sys.path.append(root_dir)

from dotenv import load_dotenv

load_dotenv(os.path.join(root_dir, ".env"))

from backend.core.ai_utils import (  # noqa: E402
    get_cloudflare_ai_config,
    get_gemini_summary_model,
    get_openai_compatible_config,
    get_random_gemini_key,
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

_STATE_LOCK = threading.Lock()
_DAILY_COUNTER = {"date": "", "count": 0}
_PROVIDER_COOLDOWN_UNTIL: dict[str, float] = {}

_MICROBATCH_LOCK = threading.Lock()
_PENDING_DEAL_IDS: list[int] = []
_MICROBATCH_TASK: Optional[asyncio.Task] = None


class _ProviderCallError(Exception):
    def __init__(self, provider: str, status_code: Optional[int], message: str):
        super().__init__(message)
        self.provider = provider
        self.status_code = status_code


def reset_ai_worker_state() -> None:
    """일일 호출 카운터, 쿨다운, 대기 큐를 초기화합니다 (단위 테스트 격리용)."""
    global _MICROBATCH_TASK
    with _STATE_LOCK:
        _DAILY_COUNTER["date"] = ""
        _DAILY_COUNTER["count"] = 0
        _PROVIDER_COOLDOWN_UNTIL.clear()
    with _MICROBATCH_LOCK:
        _PENDING_DEAL_IDS.clear()
        _MICROBATCH_TASK = None


def get_daily_call_count() -> int:
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    with _STATE_LOCK:
        if _DAILY_COUNTER["date"] != today:
            return 0
        return int(_DAILY_COUNTER["count"])


def _get_max_daily_calls() -> int:
    try:
        return max(0, int(os.getenv("AI_MAX_DAILY_CALLS", "200") or "200"))
    except ValueError:
        return 200


def _get_max_batch_size() -> int:
    try:
        return max(1, min(30, int(os.getenv("AI_MICROBATCH_MAX_SIZE", "20") or "20")))
    except ValueError:
        return 20


def _get_rate_limit_cooldown_sec() -> float:
    try:
        return max(1.0, float(os.getenv("AI_RATE_LIMIT_COOLDOWN_SEC", "300") or "300"))
    except ValueError:
        return 300.0


def _get_microbatch_debounce_sec() -> float:
    try:
        return max(0.0, float(os.getenv("AI_MICROBATCH_DEBOUNCE_SEC", "8") or "8"))
    except ValueError:
        return 8.0


def _try_consume_daily_quota() -> bool:
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    limit = _get_max_daily_calls()
    if limit <= 0:
        return False
    with _STATE_LOCK:
        if _DAILY_COUNTER["date"] != today:
            _DAILY_COUNTER["date"] = today
            _DAILY_COUNTER["count"] = 0
        if _DAILY_COUNTER["count"] >= limit:
            return False
        _DAILY_COUNTER["count"] += 1
        return True


def _is_provider_in_cooldown(provider: str) -> bool:
    now = time.monotonic()
    with _STATE_LOCK:
        until = _PROVIDER_COOLDOWN_UNTIL.get(provider, 0.0)
        return now < until


def _set_provider_cooldown(provider: str, seconds: Optional[float] = None) -> None:
    duration = seconds if seconds is not None else _get_rate_limit_cooldown_sec()
    with _STATE_LOCK:
        _PROVIDER_COOLDOWN_UNTIL[provider] = time.monotonic() + duration


def _strip_html_preview(raw_html: Optional[str], max_chars: int = 350) -> str:
    if not raw_html:
        return ""
    try:
        text = BeautifulSoup(str(raw_html), "html.parser").get_text(" ", strip=True)
    except Exception:
        text = re.sub(r"<[^>]+>", " ", str(raw_html))
    return re.sub(r"\s+", " ", text).strip()[:max_chars]


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
    input_data = []
    for d in deals_to_process:
        input_data.append(
            {
                "id": d.id,
                "title": d.title,
                "price": _format_price_for_prompt(d),
                "content": _strip_html_preview(d.content_html, 350),
                "is_super_hotdeal": bool(d.honey_score == 100 or d.is_super_hotdeal),
            }
        )

    return f"""너는 국내 쇼핑몰 핫딜 분석 전문가야. 아래 JSON 배열로 전달된 {len(input_data)}개의 핫딜 데이터를 분석해서, 각 핫딜에 대해 핵심 스펙과 체감가·구매 포인트를 정리하는 솔직하고 간결한 3줄 요약을 작성해줘.

[규칙]
1. 반드시 순수 JSON 배열만 반환할 것 (마크다운 백틱 제외).
2. 각 객체는 입력받은 'id'를 반드시 정수로 그대로 유지하고, 'ai_summary' 필드에 3줄 요약을 넣을 것.
3. 'ai_summary'의 각 줄은 '✅' 이모지로 시작하고 줄바꿈(\\n)으로 구분할 것. (전체 150자 이내)
4. 만약 is_super_hotdeal 값이 true라면, 요약 맨 앞에 '🔥 [커뮤니티 인증 핫딜] '을 붙일 것.

[입력 데이터]:
{json.dumps(input_data, ensure_ascii=False, indent=2)}

[출력 예시]:
[
  {{"id": 101, "ai_summary": "✅ 역대급 가격 메리트\\n✅ 스펙 대비 가성비 우수\\n✅ 구매 전 카드 할인 확인"}},
  {{"id": 102, "ai_summary": "🔥 [커뮤니티 인증 핫딜] ✅ 품절 임박 인기 딜\\n✅ 체감가 기준 최저 수준\\n✅ 커뮤니티 반응 긍정적"}}
]"""


def _parse_batch_summaries(raw_text: str) -> dict[int, str]:
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
        if not arr_match:
            return {}
        try:
            parsed = json.loads(arr_match.group(0))
        except json.JSONDecodeError:
            return {}

    if isinstance(parsed, dict):
        for key in ("items", "results", "deals", "data"):
            if isinstance(parsed.get(key), list):
                parsed = parsed[key]
                break

    if not isinstance(parsed, list):
        return {}

    summaries: dict[int, str] = {}
    for entry in parsed:
        if not isinstance(entry, dict):
            continue
        raw_id = entry.get("id")
        raw_summary = entry.get("ai_summary")
        if raw_id is None or not isinstance(raw_summary, str):
            continue
        try:
            deal_id = int(raw_id)
        except (TypeError, ValueError):
            continue
        summary_text = raw_summary.strip()[:500]
        if summary_text:
            summaries[deal_id] = summary_text
    return summaries


def _is_fatal_key_error(status_code: int, body_text: str) -> bool:
    low = (body_text or "").lower()
    if status_code in (401, 403):
        return True
    return any(
        tok in low
        for tok in ("spending cap", "leaked", "api key not valid", "permission_denied")
    )


def _call_gemini_rest(prompt: str, cfg: dict, timeout: float = 25.0) -> str:
    api_key = cfg["api_key"]
    model = cfg["model"]
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.1,
            "maxOutputTokens": 1500,
            "responseMimeType": "application/json",
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
        return "".join(str(p.get("text", "")) for p in parts if isinstance(p, dict))
    except (KeyError, IndexError, TypeError) as exc:
        raise _ProviderCallError("gemini", resp.status_code, f"Malformed response: {exc}") from exc


def _call_openai_compatible_rest(prompt: str, cfg: dict, timeout: float = 25.0) -> str:
    provider = cfg["provider"]
    url = f"{cfg['base_url']}/chat/completions"
    payload = {
        "model": cfg["model"],
        "temperature": 0.1,
        "messages": [
            {"role": "system", "content": "You return only valid JSON arrays."},
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
        return str(data["choices"][0]["message"]["content"])
    except (KeyError, IndexError, TypeError) as exc:
        raise _ProviderCallError(provider, resp.status_code, f"Malformed response: {exc}") from exc


def _call_cloudflare_workers_ai_rest(prompt: str, cfg: dict, timeout: float = 25.0) -> str:
    url = f"https://api.cloudflare.com/client/v4/accounts/{cfg['account_id']}/ai/run/{cfg['model']}"
    payload = {
        "messages": [
            {"role": "system", "content": "You return only valid JSON arrays."},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.1,
        "max_tokens": 1500,
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
            if isinstance(resp_val, (list, dict)):
                return json.dumps(resp_val, ensure_ascii=False)
            return str(resp_val or "")
        return str(result)
    except (KeyError, TypeError) as exc:
        raise _ProviderCallError("cloudflare_ai", resp.status_code, f"Malformed response: {exc}") from exc


def _collect_available_providers() -> list[tuple[str, dict, Callable[[str, dict], str]]]:
    providers: list[tuple[str, dict, Callable[[str, dict], str]]] = []

    gemini_key = get_random_gemini_key()
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

    providers = _collect_available_providers()
    if not providers:
        logger.debug("사용 가능한 무료 AI 프로바이더가 없거나 모두 쿨다운 중입니다.")
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
                    or_(Deal.ai_summary.is_(None), Deal.ai_summary == ""),
                )
                .order_by(Deal.id.desc())
                .all()
            )
        else:
            limit_time = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(hours=48)
            deals_to_process = (
                db.query(Deal)
                .filter(
                    or_(Deal.ai_summary.is_(None), Deal.ai_summary == ""),
                    Deal.price != "0",
                    Deal.price.isnot(None),
                    Deal.indexed_at >= limit_time,
                )
                .order_by(Deal.indexed_at.desc(), Deal.id.desc())
                .limit(max_batch)
                .all()
            )

        if not deals_to_process:
            return 0

        logger.info("🚀 %d개 핫딜 3줄 요약 마이크로배치 시작...", len(deals_to_process))
        prompt = _build_batch_prompt(deals_to_process)

        for provider_name, cfg, call_fn in providers:
            if not _try_consume_daily_quota():
                logger.warning(
                    "일일 AI 호출 상한(%d회)에 도달하여 요약을 건너뜁니다 (ai_summary=None 유지).",
                    _get_max_daily_calls(),
                )
                return 0

            try:
                raw_text = call_fn(prompt, cfg)
                summaries_by_id = _parse_batch_summaries(raw_text)
                if not summaries_by_id:
                    logger.warning("[%s] 빈 응답 또는 JSON 파싱 실패 → 다음 프로바이더 시도", provider_name)
                    continue

                updated_ids: list[int] = []
                for d in deals_to_process:
                    summary = summaries_by_id.get(d.id)
                    if summary:
                        d.ai_summary = summary
                        updated_ids.append(d.id)

                if not updated_ids:
                    logger.warning("[%s] 반환 ID가 요청 배치와 일치하지 않음", provider_name)
                    continue

                db.commit()
                _invalidate_caches_for_deals(updated_ids)
                logger.info(
                    "✅ [%s] AI 마이크로배치 완료: %d/%d건 업데이트",
                    provider_name,
                    len(updated_ids),
                    len(deals_to_process),
                )
                return len(updated_ids)

            except _ProviderCallError as exc:
                if exc.status_code == 429:
                    _set_provider_cooldown(provider_name)
                else:
                    _set_provider_cooldown(provider_name, seconds=30.0)
                logger.warning("⚠️ [%s] 호출 실패 (%s) → 다음 무료 프로바이더 폴백", provider_name, exc)
            except Exception as exc:
                logger.warning("⚠️ [%s] 예외 발생 (%s) → 다음 무료 프로바이더 폴백", provider_name, exc)

        # 실패 시 더미 문자열 저장 금지: ai_summary=None 유지 후 다음 배치/상세 재시도 허용
        return 0
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
    신규 수집 딜 ID를 디바운스 큐에 적재하고 비차단 마이크로배치로 3줄 요약을 생성합니다.
    테스트 환경에서는 AI_MICROBATCH_ALLOW_IN_TEST=true 가 아니면 즉시 no-op 반환합니다.
    """
    global _MICROBATCH_TASK

    allow_in_test = os.getenv("AI_MICROBATCH_ALLOW_IN_TEST", "").strip().lower() in ("true", "1", "yes")
    if not allow_in_test and (
        os.getenv("ENV") == "test"
        or "pytest" in sys.modules
        or "memory" in os.getenv("DATABASE_URL", "").lower()
    ):
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
                    or_(Deal.ai_summary.is_(None), Deal.ai_summary == ""),
                    Deal.price != "0",
                    Deal.price.isnot(None),
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
        time.sleep(2)

    return total_processed


if __name__ == "__main__":
    run_all_batches()
