# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_ai_microbatch_worker.py

0원 무료 멀티 프로바이더 마이크로배치 AI 요약 엔진 & 4중 토큰·비용 하드 캡 가드 회귀 테스트:
1. Gemini REST 배치 성공 + Deal.ai_summary DB 업데이트 + 캐시 무효화 + usageMetadata 토큰 정산
2. Gemini 429(Rate Limit) 시 Groq/OpenAI 호환 무료 프로바이더 자동 폴백
3. Cloudflare Workers AI 무료 폴백 & 전 프로바이더 실패 시 더미 문자열('AI 요약 대체') 미기록
4. AI_MAX_DAILY_CALLS 상한 도달 시 외부 HTTP 호출 차단
5. AI_MAX_DAILY_TOKENS 상한 도달 시 외부 HTTP 호출 차단
6. 레거시 건별 호출(get_random_gemini_key) 영구 잠금 & USE_LEGACY_GEMINI_PER_DEAL=true 시에만 허용
7. Gemini 요청 payload에 thinkingConfig.thinkingBudget=0 · compact JSON · 동적 maxOutputTokens 적용 확인
8. schedule_ai_summary_microbatch 디바운스 다건 유입 1회 묶음 처리
"""

import asyncio
import json
import os
import sys

import httpx
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from backend.core.ai_utils import (  # noqa: E402
    get_microbatch_gemini_key,
    get_openai_compatible_config,
    get_random_gemini_key,
    reset_dead_keys,
)
from backend.database.models import Base, Community, Deal  # noqa: E402
from backend.scheduler.batch_ai_worker import (  # noqa: E402
    get_daily_call_count,
    get_daily_token_count,
    process_ai_batch,
    reset_ai_worker_state,
    schedule_ai_summary_microbatch,
)


def _create_isolated_session_factory(tmp_path):
    db_file = tmp_path / "ai_microbatch_test.db"
    engine = create_engine(
        f"sqlite:///{db_file}",
        connect_args={"check_same_thread": False},
    )
    Base.metadata.create_all(bind=engine)
    return sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _seed_deals(SessionFactory, count: int = 2) -> list[int]:
    db = SessionFactory()
    try:
        comm = Community(name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr")
        db.add(comm)
        db.flush()
        ids = []
        for idx in range(count):
            d = Deal(
                source_community_id=comm.id,
                title=f"[쿠팡] 테스트 핫딜 상품 {idx + 1}",
                price=str(19900 + idx * 1000),
                currency="KRW",
                post_link=f"https://www.ppomppu.co.kr/zboard/view.php?no={9000 + idx}",
                content_html="<p>역대가 특가 카드할인 적용 시 체감가 하락</p>",
                honey_score=100 if idx == 0 else 50,
            )
            db.add(d)
            db.flush()
            ids.append(d.id)
        db.commit()
        return ids
    finally:
        db.close()


class _MockHttpResp:
    def __init__(self, status_code: int, payload: dict):
        self.status_code = status_code
        self._payload = payload
        self.text = json.dumps(payload, ensure_ascii=False)

    def json(self):
        return self._payload


@pytest.fixture(autouse=True)
def _reset_ai_state(monkeypatch):
    reset_dead_keys()
    reset_ai_worker_state()
    for key in (
        "USE_GEMINI_AI",
        "AI_SUMMARY_ENABLED",
        "USE_LEGACY_GEMINI_PER_DEAL",
        "GEMINI_API_KEY",
        "GEMINI_API_KEYS",
        "GEMINI_SUMMARY_MODEL",
        "GOOGLE_API_KEY",
        "GROQ_API_KEY",
        "GROQ_MODEL",
        "LLM_BASE_URL",
        "LLM_API_KEY",
        "LLM_MODEL",
        "CLOUDFLARE_ACCOUNT_ID",
        "CLOUDFLARE_AI_API_TOKEN",
        "AI_MAX_DAILY_CALLS",
        "AI_MAX_DAILY_TOKENS",
        "AI_MICROBATCH_DEBOUNCE_SEC",
        "AI_MIN_CALL_INTERVAL_SEC",
        "AI_MICROBATCH_ALLOW_IN_TEST",
    ):
        monkeypatch.delenv(key, raising=False)
    monkeypatch.setenv("AI_MIN_CALL_INTERVAL_SEC", "0")
    yield
    reset_dead_keys()
    reset_ai_worker_state()


def test_legacy_per_deal_key_is_locked_while_microbatch_key_works(monkeypatch):
    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "free_tier_key_safe_01")

    assert get_random_gemini_key() is None
    assert get_microbatch_gemini_key() == "free_tier_key_safe_01"

    monkeypatch.setenv("USE_LEGACY_GEMINI_PER_DEAL", "true")
    assert get_random_gemini_key() == "free_tier_key_safe_01"


def test_gemini_rest_batch_uses_thinking_budget_zero_compact_json_and_updates_deals(
    tmp_path, monkeypatch
):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=2)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "free_tier_gemini_key_123456")

    captured_requests = []

    def mock_post(self, url, **kwargs):
        captured_requests.append((url, kwargs.get("json")))
        return _MockHttpResp(
            200,
            {
                "usageMetadata": {"totalTokenCount": 420},
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": json.dumps(
                                        [
                                            {
                                                "id": deal_ids[0],
                                                "ai_summary": "🔥 [커뮤니티 인증 핫딜] ✅ 역대가\n✅ 스펙 우수\n✅ 카드 혜택",
                                            },
                                            {
                                                "id": deal_ids[1],
                                                "ai_summary": "✅ 평타 이상 가격\n✅ 실사용 무난\n✅ 배송비 확인",
                                            },
                                        ],
                                        ensure_ascii=False,
                                    )
                                }
                            ]
                        }
                    }
                ],
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    updated = process_ai_batch(deal_ids=deal_ids, db_session_factory=SessionFactory)
    assert updated == 2
    assert len(captured_requests) == 1

    req_url, req_body = captured_requests[0]
    assert "generativelanguage.googleapis.com" in req_url
    assert "gemini-3.5-flash-lite" in req_url
    gen_cfg = req_body["generationConfig"]
    assert gen_cfg["thinkingConfig"] == {"thinkingLevel": "MINIMAL"}
    assert gen_cfg["maxOutputTokens"] == min(4096, 200 + 2 * 160)
    prompt_text = req_body["contents"][0]["parts"][0]["text"]
    assert "\n  {" not in prompt_text  # indent=2 미사용(compact JSON)

    assert get_daily_call_count() == 1
    assert get_daily_token_count() == 420

    db = SessionFactory()
    try:
        d0 = db.query(Deal).filter(Deal.id == deal_ids[0]).first()
        d1 = db.query(Deal).filter(Deal.id == deal_ids[1]).first()
        assert d0.ai_summary.startswith("🔥 [커뮤니티 인증 핫딜]")
        assert "평타 이상 가격" in d1.ai_summary
    finally:
        db.close()


def test_gemini_429_falls_back_to_openai_compatible_or_groq(tmp_path, monkeypatch):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=1)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_rate_limited_key_1234")
    monkeypatch.setenv("GROQ_API_KEY", "groq_free_key_9999")

    called_urls = []

    def mock_post(self, url, **kwargs):
        called_urls.append(url)
        if "generativelanguage.googleapis.com" in url:
            return _MockHttpResp(429, {"error": {"message": "Quota exceeded"}})
        return _MockHttpResp(
            200,
            {
                "usage": {"total_tokens": 310},
                "choices": [
                    {
                        "message": {
                            "content": json.dumps(
                                [
                                    {
                                        "id": deal_ids[0],
                                        "ai_summary": "✅ Groq 무료 폴백 요약\n✅ 가격 메리트\n✅ 조건 무난",
                                    }
                                ],
                                ensure_ascii=False,
                            )
                        }
                    }
                ],
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    updated = process_ai_batch(deal_ids=deal_ids, db_session_factory=SessionFactory)
    assert updated == 1
    assert len(called_urls) == 2
    assert "generativelanguage.googleapis.com" in called_urls[0]
    assert "api.groq.com/openai/v1/chat/completions" in called_urls[1]


def test_cloudflare_workers_ai_fallback_and_no_dummy_when_all_fail(tmp_path, monkeypatch):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=1)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("CLOUDFLARE_ACCOUNT_ID", "cf_acc_test_01")
    monkeypatch.setenv("CLOUDFLARE_AI_API_TOKEN", "cf_token_test_01")

    def mock_post_fail(self, url, **kwargs):
        return _MockHttpResp(503, {"success": False})

    monkeypatch.setattr(httpx.Client, "post", mock_post_fail)
    updated_fail = process_ai_batch(deal_ids=deal_ids, db_session_factory=SessionFactory)
    assert updated_fail == 0

    db = SessionFactory()
    try:
        assert db.query(Deal).filter(Deal.id == deal_ids[0]).first().ai_summary is None
    finally:
        db.close()

    reset_ai_worker_state()
    monkeypatch.setenv("AI_MIN_CALL_INTERVAL_SEC", "0")

    def mock_post_cf_ok(self, url, **kwargs):
        assert "api.cloudflare.com/client/v4/accounts/cf_acc_test_01/ai/run/" in url
        return _MockHttpResp(
            200,
            {
                "success": True,
                "result": {
                    "response": json.dumps(
                        [
                            {
                                "id": deal_ids[0],
                                "ai_summary": "✅ Cloudflare AI 무료 요약\n✅ 실구매가 양호\n✅ 무료배송",
                            }
                        ],
                        ensure_ascii=False,
                    )
                },
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post_cf_ok)
    updated_ok = process_ai_batch(deal_ids=deal_ids, db_session_factory=SessionFactory)
    assert updated_ok == 1


def test_daily_call_cap_blocks_extra_requests(tmp_path, monkeypatch):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=2)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_cap_test_key_1234")
    monkeypatch.setenv("AI_MAX_DAILY_CALLS", "1")

    call_count = 0

    def mock_post(self, url, **kwargs):
        nonlocal call_count
        call_count += 1
        return _MockHttpResp(
            200,
            {
                "usageMetadata": {"totalTokenCount": 250},
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": json.dumps(
                                        [{"id": deal_ids[0], "ai_summary": "✅ 1호 완료\n✅ B\n✅ C"}],
                                        ensure_ascii=False,
                                    )
                                }
                            ]
                        }
                    }
                ],
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    assert process_ai_batch(deal_ids=[deal_ids[0]], db_session_factory=SessionFactory) == 1
    assert call_count == 1
    assert get_daily_call_count() == 1

    assert process_ai_batch(deal_ids=[deal_ids[1]], db_session_factory=SessionFactory) == 0
    assert call_count == 1


def test_daily_token_cap_blocks_extra_requests(tmp_path, monkeypatch):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=2)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_tokencap_key_1234")
    monkeypatch.setenv("AI_MAX_DAILY_CALLS", "60")
    monkeypatch.setenv("AI_MAX_DAILY_TOKENS", "500")

    call_count = 0

    def mock_post(self, url, **kwargs):
        nonlocal call_count
        call_count += 1
        return _MockHttpResp(
            200,
            {
                "usageMetadata": {"totalTokenCount": 480},
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": json.dumps(
                                        [{"id": deal_ids[0], "ai_summary": "✅ 1건 정산\n✅ B\n✅ C"}],
                                        ensure_ascii=False,
                                    )
                                }
                            ]
                        }
                    }
                ],
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    assert process_ai_batch(deal_ids=[deal_ids[0]], db_session_factory=SessionFactory) == 1
    assert call_count == 1
    assert get_daily_token_count() == 480

    # 남은 토큰 여유(20)가 2번째 요청 예상 토큰보다 적으므로 외부 호출 원천 차단
    assert process_ai_batch(deal_ids=[deal_ids[1]], db_session_factory=SessionFactory) == 0
    assert call_count == 1


@pytest.mark.asyncio
async def test_schedule_ai_summary_microbatch_debounces_into_single_call(tmp_path, monkeypatch):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=2)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_debounce_key_1234")
    monkeypatch.setenv("AI_MICROBATCH_DEBOUNCE_SEC", "0.05")
    monkeypatch.setenv("AI_MICROBATCH_ALLOW_IN_TEST", "true")

    http_calls = 0

    def mock_post(self, url, **kwargs):
        nonlocal http_calls
        http_calls += 1
        return _MockHttpResp(
            200,
            {
                "usageMetadata": {"totalTokenCount": 390},
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": json.dumps(
                                        [
                                            {"id": deal_ids[0], "ai_summary": "✅ 디바운스 1\n✅ B\n✅ C"},
                                            {"id": deal_ids[1], "ai_summary": "✅ 디바운스 2\n✅ B\n✅ C"},
                                        ],
                                        ensure_ascii=False,
                                    )
                                }
                            ]
                        }
                    }
                ],
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    assert schedule_ai_summary_microbatch([deal_ids[0]], db_session_factory=SessionFactory) is True
    assert schedule_ai_summary_microbatch([deal_ids[1]], db_session_factory=SessionFactory) is True

    await asyncio.sleep(0.25)

    assert http_calls == 1
    db = SessionFactory()
    try:
        for did in deal_ids:
            row = db.query(Deal).filter(Deal.id == did).first()
            assert row.ai_summary is not None
            assert "디바운스" in row.ai_summary
    finally:
        db.close()


def test_gemini_2_override_uses_thinking_budget_zero_and_groq_default_model(tmp_path, monkeypatch):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=1)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_legacy2_key_1234")
    monkeypatch.setenv("GEMINI_SUMMARY_MODEL", "gemini-2.5-flash-lite")
    monkeypatch.setenv("GROQ_API_KEY", "groq_default_model_test_key")

    groq_cfg = get_openai_compatible_config()
    assert groq_cfg is not None
    assert groq_cfg["provider"] == "groq"
    assert groq_cfg["model"] == "qwen/qwen3.8-27b"

    captured_requests = []

    def mock_post(self, url, **kwargs):
        captured_requests.append((url, kwargs.get("json")))
        return _MockHttpResp(
            200,
            {
                "usageMetadata": {"totalTokenCount": 210},
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {"text": "internal thought", "thought": True},
                                {
                                    "text": json.dumps(
                                        [{"id": deal_ids[0], "ai_summary": "✅ 2.5 모델 호환\n✅ B\n✅ C"}],
                                        ensure_ascii=False,
                                    )
                                },
                            ]
                        }
                    }
                ],
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    updated = process_ai_batch(deal_ids=deal_ids, db_session_factory=SessionFactory)
    assert updated == 1
    assert len(captured_requests) == 1
    req_url, req_body = captured_requests[0]
    assert "gemini-2.5-flash-lite" in req_url
    assert req_body["generationConfig"]["thinkingConfig"] == {"thinkingBudget": 0}


def test_process_ai_batch_unfiltered_skips_closed_deals(tmp_path, monkeypatch):
    """deal_ids=None 자동 배치 모드는 종료된(is_closed=True) 딜에 무료 토큰을 소모하지 않아야 한다."""
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=2)
    db = SessionFactory()
    try:
        closed_deal = db.query(Deal).filter(Deal.id == deal_ids[0]).first()
        closed_deal.is_closed = True
        db.commit()
    finally:
        db.close()

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_active_only_key_1234")

    sent_ids = []

    def mock_post(self, url, **kwargs):
        text = kwargs["json"]["contents"][0]["parts"][0]["text"]
        payload_part = json.loads(text.split("입력:", 1)[1])
        sent_ids.extend(item["id"] for item in payload_part)
        return _MockHttpResp(
            200,
            {
                "usageMetadata": {"totalTokenCount": 180},
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": json.dumps(
                                        [{"id": deal_ids[1], "ai_summary": "✅ 활성 딜 요약\n✅ B\n✅ C"}],
                                        ensure_ascii=False,
                                    )
                                }
                            ]
                        }
                    }
                ],
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    updated = process_ai_batch(deal_ids=None, db_session_factory=SessionFactory)
    assert updated == 1
    assert sent_ids == [deal_ids[1]]


def test_strip_preview_removes_image_tags_and_fallback_bypasses_min_interval(tmp_path, monkeypatch):
    """선두 [상품이미지: URL] 제거로 본문 쿠폰/체감가를 보존하고, 잘린 JSON 복구 및 동일 배치 폴백 시 min_interval 차단을 우회해야 한다."""
    from backend.scheduler.batch_ai_worker import _parse_batch_summaries, _strip_html_preview

    raw_body = "\n".join(
        [
            "[상품이미지: https://image.fmkorea.com/files/attach/new5/20261119/10573430092_img1.jpg.webp]",
            "[상품이미지: https://image.fmkorea.com/files/attach/new5/20261119/10573430092_img2.jpg.webp]",
            "[첨부된 이미지]",
            "토스앱 혜택 2천원 쿠폰 적용 후 토스결제하면 19,440원",
        ]
    )
    preview = _strip_html_preview(raw_body, max_chars=300)
    assert "상품이미지" not in preview
    assert "image.fmkorea.com" not in preview
    assert "19,440원" in preview

    truncated_json = '[{"id": 101, "ai_summary": "✅ 완료 항목\\n✅ B\\n✅ C"}, {"id": 102, "ai_summary": "✅ 잘린'
    assert _parse_batch_summaries(truncated_json) == {101: "✅ 완료 항목\n✅ B\n✅ C"}

    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=1)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_fail_key_1234")
    monkeypatch.setenv("GROQ_API_KEY", "groq_fallback_key_5678")
    monkeypatch.setenv("AI_MIN_CALL_INTERVAL_SEC", "15")

    calls = []

    def mock_post(self, url, **kwargs):
        calls.append(url)
        if "generativelanguage.googleapis.com" in url:
            return _MockHttpResp(200, {"candidates": [{"content": {"parts": [{"text": "["}]}}]})
        return _MockHttpResp(
            200,
            {
                "usage": {"total_tokens": 220},
                "choices": [
                    {
                        "message": {
                            "content": json.dumps(
                                [{"id": deal_ids[0], "ai_summary": "✅ 폴백 성공\n✅ B\n✅ C"}],
                                ensure_ascii=False,
                            )
                        }
                    }
                ],
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    assert process_ai_batch(deal_ids=deal_ids, db_session_factory=SessionFactory) == 1
    assert len(calls) == 2
    assert "api.groq.com" in calls[1]


def test_stub_badge_ai_summaries_are_treated_as_unsummarized(tmp_path, monkeypatch):
    """배지 스텁('🔥 [커뮤니티 인증 핫딜] ' 등)만 채워진 인기 딜도 미요약으로 취급하고 기존 실요약은 보존해야 한다."""
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=3)
    db = SessionFactory()
    try:
        d0 = db.query(Deal).filter(Deal.id == deal_ids[0]).first()
        d1 = db.query(Deal).filter(Deal.id == deal_ids[1]).first()
        d2 = db.query(Deal).filter(Deal.id == deal_ids[2]).first()
        d0.ai_summary = "🔥 [커뮤니티 인증 핫딜] "
        d1.ai_summary = "🔥 [커뮤니티 인기] "
        d2.ai_summary = "✅ 체감가/혜택: 이미 요약 완료\n✅ 구매방법/스펙: B\n✅ 필수조건/주의: C"
        db.commit()
    finally:
        db.close()

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_stub_filter_key_1234")

    sent_ids_batches = []

    def mock_post(self, url, **kwargs):
        text = kwargs["json"]["contents"][0]["parts"][0]["text"]
        payload_part = json.loads(text.split("입력:", 1)[1])
        batch_ids = [item["id"] for item in payload_part]
        sent_ids_batches.append(batch_ids)
        return _MockHttpResp(
            200,
            {
                "usageMetadata": {"totalTokenCount": 240},
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": json.dumps(
                                        [
                                            {
                                                "id": did,
                                                "ai_summary": f"✅ 실요약 교체 {did}\n✅ B\n✅ C",
                                            }
                                            for did in batch_ids
                                        ],
                                        ensure_ascii=False,
                                    )
                                }
                            ]
                        }
                    }
                ],
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    # 1) deal_ids=None 자동 배치에서 d0, d1 선택 & d2 보존 확인
    updated_auto = process_ai_batch(deal_ids=[deal_ids[0], deal_ids[2]], db_session_factory=SessionFactory)
    assert updated_auto == 1
    assert sent_ids_batches[0] == [deal_ids[0]]

    # 2) deal_ids=None에서도 남은 스텁 딜(d1)이 선택되는지 확인
    updated_unfiltered = process_ai_batch(deal_ids=None, db_session_factory=SessionFactory)
    assert updated_unfiltered == 1
    assert sent_ids_batches[1] == [deal_ids[1]]

    db = SessionFactory()
    try:
        assert "실요약 교체" in db.query(Deal).filter(Deal.id == deal_ids[0]).first().ai_summary
        assert "실요약 교체" in db.query(Deal).filter(Deal.id == deal_ids[1]).first().ai_summary
        assert "이미 요약 완료" in db.query(Deal).filter(Deal.id == deal_ids[2]).first().ai_summary
    finally:
        db.close()
