# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_ai_microbatch_worker.py

Option B 무료 AI 구조화 구매 요약 워커 회귀 테스트:
1. Gemini REST 배치 성공 + 구조화 JSON(ai_summary_json) & 하위호환 ai_summary + status='done' + 토큰 정산
2. Gemini 429(Rate Limit) 시 Groq/OpenAI 호환 무료 프로바이더 자동 폴백
3. Cloudflare Workers AI 무료 폴백 & 전 프로바이더 실패 시 더미 문자열 미기록
4. AI_MAX_DAILY_CALLS / AI_MAX_DAILY_TOKENS 상한 가드
5. 레거시 건별 호출 잠금
6. Gemini 3.x(thinkingLevel=MINIMAL) & 2.5(thinkingBudget=0) 분기 및 Groq qwen/qwen3.8-27b 기본 모델
7. 2단계 사전 게이팅: ID=27966 유형 단순 단문(skipped_simple) · 초단문(skipped_empty) LLM 스킵
8. 클리앙/빠삭 1줄 장문(±80자 윈도우) 발췌 & 브래킷 깊이 중첩 JSON 복구 파서
9. 배치 전체 LLM skip 응답 시 status='skipped_llm' 커밋 및 Groq 폴백 차단 + 종료 딜 스킵
10. 배지 스텁('🔥 [커뮤니티 인증 핫딜] ') 미요약 포함 및 디바운스 마이크로배치
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
    _parse_batch_summaries,
    _strip_html_preview,
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


def _seed_deals(SessionFactory, count: int = 2, content_html: str | None = None) -> list[int]:
    db = SessionFactory()
    try:
        comm = Community(name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr")
        db.add(comm)
        db.flush()
        ids = []
        body = (
            content_html
            if content_html is not None
            else "<p>역대가 특가 쿠폰 2천원 + 토스결제 할인 적용 시 체감가 하락합니다.</p>"
        )
        for idx in range(count):
            d = Deal(
                source_community_id=comm.id,
                title=f"[쿠팡] 테스트 핫딜 상품 {idx + 1}",
                price=str(19900 + idx * 1000),
                currency="KRW",
                post_link=f"https://www.ppomppu.co.kr/zboard/view.php?no={9000 + idx}",
                content_html=body,
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


def test_gemini_rest_batch_writes_structured_items_and_legacy_summary(tmp_path, monkeypatch):
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
                                                "skip": False,
                                                "items": [
                                                    {"k": "체감가", "v": "17,900원 (토스결제 시)"},
                                                    {"k": "구매경로", "v": "상품페이지 쿠폰 2천원 적용"},
                                                ],
                                            },
                                            {
                                                "id": deal_ids[1],
                                                "skip": False,
                                                "items": [
                                                    {"k": "주의", "v": "카드사 청구할인 예산 소진 주의"},
                                                ],
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
    assert "gemini-3.5-flash-lite" in req_url
    gen_cfg = req_body["generationConfig"]
    assert gen_cfg["thinkingConfig"] == {"thinkingLevel": "MINIMAL"}
    assert gen_cfg["maxOutputTokens"] == min(4096, 300 + 2 * 240)
    prompt_text = req_body["contents"][0]["parts"][0]["text"]
    assert "\n  {" not in prompt_text

    assert get_daily_call_count() == 1
    assert get_daily_token_count() == 420

    db = SessionFactory()
    try:
        d0 = db.query(Deal).filter(Deal.id == deal_ids[0]).first()
        d1 = db.query(Deal).filter(Deal.id == deal_ids[1]).first()
        assert d0.ai_summary_status == "done"
        assert d0.ai_summary_source_hash and len(d0.ai_summary_source_hash) == 32
        assert d0.ai_summary.startswith("🔥 [커뮤니티 인증 핫딜]")
        assert "✅ 체감가: 17,900원 (토스결제 시)" in d0.ai_summary
        assert json.loads(d0.ai_summary_json)[0] == {"k": "체감가", "v": "17,900원 (토스결제 시)"}
        assert d1.ai_summary_status == "done"
        assert "✅ 주의: 카드사 청구할인 예산 소진 주의" in d1.ai_summary
    finally:
        db.close()


def test_gemini_429_falls_back_to_groq_and_bypasses_min_interval(tmp_path, monkeypatch):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=1)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_rate_limited_key_1234")
    monkeypatch.setenv("GROQ_API_KEY", "groq_free_key_9999")
    monkeypatch.setenv("AI_MIN_CALL_INTERVAL_SEC", "15")

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
                                        "skip": False,
                                        "items": [{"k": "체감가", "v": "18,500원 (Groq 폴백)"}],
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
    assert process_ai_batch(deal_ids=deal_ids, db_session_factory=SessionFactory) == 0

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
                                "ai_summary": "✅ Cloudflare 레거시 호환 요약\n✅ 무료배송",
                            }
                        ],
                        ensure_ascii=False,
                    )
                },
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post_cf_ok)
    assert process_ai_batch(deal_ids=deal_ids, db_session_factory=SessionFactory) == 1


def test_daily_call_and_token_caps_block_extra_requests(tmp_path, monkeypatch):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=3)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_cap_test_key_1234")
    monkeypatch.setenv("AI_MAX_DAILY_CALLS", "1")

    call_count = 0
    returned_tokens = 480

    def mock_post(self, url, **kwargs):
        nonlocal call_count
        call_count += 1
        req_text = kwargs["json"]["contents"][0]["parts"][0]["text"]
        payload_items = json.loads(req_text.split("입력:", 1)[1])
        return _MockHttpResp(
            200,
            {
                "usageMetadata": {"totalTokenCount": returned_tokens},
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": json.dumps(
                                        [
                                            {
                                                "id": payload_items[0]["id"],
                                                "skip": False,
                                                "items": [{"k": "체감가", "v": "18,000원"}],
                                            }
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

    assert process_ai_batch(deal_ids=[deal_ids[0]], db_session_factory=SessionFactory) == 1
    assert call_count == 1
    assert process_ai_batch(deal_ids=[deal_ids[1]], db_session_factory=SessionFactory) == 0
    assert call_count == 1

    reset_ai_worker_state()
    monkeypatch.setenv("AI_MIN_CALL_INTERVAL_SEC", "0")
    monkeypatch.setenv("AI_MAX_DAILY_CALLS", "60")
    monkeypatch.setenv("AI_MAX_DAILY_TOKENS", "1000")
    returned_tokens = 960

    assert process_ai_batch(deal_ids=[deal_ids[1]], db_session_factory=SessionFactory) == 1
    assert get_daily_token_count() == 960
    assert process_ai_batch(deal_ids=[deal_ids[2]], db_session_factory=SessionFactory) == 0


def test_pre_gating_skips_simple_and_empty_deals_without_llm_call(tmp_path, monkeypatch):
    """27966처럼 짧고 조건 없는 단순 딜(37자) 및 10자 미만 딜은 LLM 호출 없이 skipped_*로 마킹되어야 한다."""
    SessionFactory = _create_isolated_session_factory(tmp_path)
    db = SessionFactory()
    try:
        comm = Community(name="fmkorea", display_name="에펨코리아", base_url="https://www.fmkorea.com")
        db.add(comm)
        db.flush()
        d_simple = Deal(
            source_community_id=comm.id,
            title="농협안심한우 손질된 국내산 한우스지 500g x 2팩",
            price="13980",
            currency="KRW",
            post_link="https://www.fmkorea.com/27966",
            content_html="전골이나 수육 등 다양하게 활용 가능\n손질되어 있어 바로 사용하면 됨",
            honey_score=100,
            ai_summary="🔥 [커뮤니티 인증 핫딜] ",
        )
        d_empty = Deal(
            source_community_id=comm.id,
            title="단순 링크 글",
            price="10000",
            currency="KRW",
            post_link="https://www.fmkorea.com/27967",
            content_html="[상품이미지: https://image.fmkorea.com/a.jpg]\n좋네요",
            honey_score=10,
        )
        db.add_all([d_simple, d_empty])
        db.commit()
        simple_id, empty_id = d_simple.id, d_empty.id
    finally:
        db.close()

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_gate_test_key_1234")

    http_calls = 0

    def mock_post(self, url, **kwargs):
        nonlocal http_calls
        http_calls += 1
        return _MockHttpResp(200, {})

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    processed = process_ai_batch(deal_ids=[simple_id, empty_id], db_session_factory=SessionFactory)
    assert processed == 2
    assert http_calls == 0

    db = SessionFactory()
    try:
        s_row = db.query(Deal).filter(Deal.id == simple_id).first()
        e_row = db.query(Deal).filter(Deal.id == empty_id).first()
        assert s_row.ai_summary_status == "skipped_simple"
        assert s_row.ai_summary_json is None
        assert s_row.ai_summary == "🔥 [커뮤니티 인증 핫딜] "
        assert e_row.ai_summary_status == "skipped_empty"
        assert e_row.ai_summary is None
    finally:
        db.close()

    # 재호출 시 무한 루프에 빠지지 않아야 함
    assert process_ai_batch(deal_ids=None, db_session_factory=SessionFactory) == 0


def test_smart_preview_one_liner_window_and_bracket_depth_json_recovery():
    """1줄 장문(클리앙/빠삭) ±80자 윈도우 발췌 및 잘린 중첩 JSON 복구 파서를 검증한다."""
    filler_a = "가나다라마바사아자차카타파하 " * 26  # ~400자
    filler_b = "제품기본설명줄글내용입니다 " * 26
    long_one_liner = (
        f"[상품이미지: https://image.fmkorea.com/long.jpg]\n"
        f"{filler_a} 핵심: 토스앱 혜택 쿠폰 2천원 받고 토스결제 시 19,440원 적용됨 {filler_b}"
    )
    preview = _strip_html_preview(long_one_liner, max_chars=900)
    assert "image.fmkorea.com" not in preview
    assert "19,440원" in preview
    assert "토스앱 혜택" in preview

    truncated_nested = (
        '[{"id": 101, "skip": false, "items": [{"k": "체감가", "v": "19,440원"}, {"k": "주의", "v": "앱 쿠폰"}]}, '
        '{"id": 102, "skip": true, "items": []}, '
        '{"id": 103, "skip": false, "items": [{"k": "구매경로", "v": "잘린중간'
    )
    parsed = _parse_batch_summaries(truncated_nested)
    assert 101 in parsed
    assert parsed[101]["items"] == [
        {"k": "체감가", "v": "19,440원"},
        {"k": "주의", "v": "앱 쿠폰"},
    ]
    assert parsed[102]["skip"] is True
    assert 103 not in parsed


def test_all_skip_llm_response_commits_skipped_llm_and_stops_fallback(tmp_path, monkeypatch):
    """배치 내 모든 딜을 LLM이 skip=true로 응답한 경우 Groq 폴백으로 재요청하지 않고 skipped_llm 저장해야 한다."""
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=1)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_all_skip_key_1234")
    monkeypatch.setenv("GROQ_API_KEY", "groq_should_not_be_called")

    called_urls = []

    def mock_post(self, url, **kwargs):
        called_urls.append(url)
        return _MockHttpResp(
            200,
            {
                "usageMetadata": {"totalTokenCount": 190},
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": json.dumps(
                                        [{"id": deal_ids[0], "skip": True, "items": []}],
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

    assert process_ai_batch(deal_ids=deal_ids, db_session_factory=SessionFactory) == 1
    assert len(called_urls) == 1
    assert "generativelanguage.googleapis.com" in called_urls[0]

    db = SessionFactory()
    try:
        row = db.query(Deal).filter(Deal.id == deal_ids[0]).first()
        assert row.ai_summary_status == "skipped_llm"
        assert row.ai_summary_json is None
    finally:
        db.close()


def test_gemini_2_override_and_closed_deal_skip(tmp_path, monkeypatch):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=2)
    db = SessionFactory()
    try:
        db.query(Deal).filter(Deal.id == deal_ids[0]).first().is_closed = True
        db.commit()
    finally:
        db.close()

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "gemini_legacy2_key_1234")
    monkeypatch.setenv("GEMINI_SUMMARY_MODEL", "gemini-2.5-flash-lite")
    monkeypatch.setenv("GROQ_API_KEY", "groq_default_model_test_key")

    groq_cfg = get_openai_compatible_config()
    assert groq_cfg is not None
    assert groq_cfg["model"] == "qwen/qwen3.8-27b"

    sent_ids = []

    def mock_post(self, url, **kwargs):
        req_json = kwargs.get("json")
        assert "gemini-2.5-flash-lite" in url
        assert req_json["generationConfig"]["thinkingConfig"] == {"thinkingBudget": 0}
        payload_part = json.loads(req_json["contents"][0]["parts"][0]["text"].split("입력:", 1)[1])
        sent_ids.extend(item["id"] for item in payload_part)
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
                                        [
                                            {
                                                "id": deal_ids[1],
                                                "skip": False,
                                                "items": [{"k": "체감가", "v": "2.5 호환 18,900원"}],
                                            }
                                        ],
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

    assert process_ai_batch(deal_ids=None, db_session_factory=SessionFactory) == 1
    assert sent_ids == [deal_ids[1]]


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
                                            {"id": deal_ids[0], "skip": False, "items": [{"k": "체감가", "v": "디바운스 1"}]},
                                            {"id": deal_ids[1], "skip": False, "items": [{"k": "체감가", "v": "디바운스 2"}]},
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
            assert row.ai_summary_status == "done"
    finally:
        db.close()
