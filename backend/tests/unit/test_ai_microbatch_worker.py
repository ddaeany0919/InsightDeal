# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_ai_microbatch_worker.py

0원 무료 멀티 프로바이더 마이크로배치 AI 요약 엔진 회귀 테스트:
1. Gemini REST 배치 성공 + Deal.ai_summary DB 업데이트 + 캐시 무효화
2. Gemini 429(Rate Limit) 시 Groq/OpenAI 호환 무료 프로바이더 자동 폴백
3. Cloudflare Workers AI 무료 폴백 동작
4. 전 프로바이더 실패 시 더미 문자열('AI 요약 대체') 미기록 (None 유지)
5. AI_MAX_DAILY_CALLS 상한 도달 시 외부 HTTP 호출 차단
6. schedule_ai_summary_microbatch 디바운스 다건 유입 1회 묶음 처리
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

from backend.core.ai_utils import reset_dead_keys  # noqa: E402
from backend.database.models import Base, Community, Deal  # noqa: E402
from backend.scheduler.batch_ai_worker import (  # noqa: E402
    get_daily_call_count,
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
        "GEMINI_API_KEY",
        "GEMINI_API_KEYS",
        "GOOGLE_API_KEY",
        "GROQ_API_KEY",
        "LLM_BASE_URL",
        "LLM_API_KEY",
        "CLOUDFLARE_ACCOUNT_ID",
        "CLOUDFLARE_AI_API_TOKEN",
        "AI_MAX_DAILY_CALLS",
        "AI_MICROBATCH_DEBOUNCE_SEC",
        "AI_MICROBATCH_ALLOW_IN_TEST",
    ):
        monkeypatch.delenv(key, raising=False)
    yield
    reset_dead_keys()
    reset_ai_worker_state()


def test_gemini_rest_batch_updates_deals_and_caches(tmp_path, monkeypatch):
    SessionFactory = _create_isolated_session_factory(tmp_path)
    deal_ids = _seed_deals(SessionFactory, count=2)

    monkeypatch.setenv("USE_GEMINI_AI", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "free_tier_gemini_key_123456")

    called_urls = []

    def mock_post(self, url, **kwargs):
        called_urls.append(url)
        return _MockHttpResp(
            200,
            {
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
                ]
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    updated = process_ai_batch(deal_ids=deal_ids, db_session_factory=SessionFactory)
    assert updated == 2
    assert len(called_urls) == 1
    assert "generativelanguage.googleapis.com" in called_urls[0]
    assert get_daily_call_count() == 1

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
                ]
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

    # 1) 모든 프로바이더 실패 시 더미 텍스트를 저장하지 않고 None을 유지
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

    # 쿨다운 해제 후 Cloudflare Workers AI 성공
    reset_ai_worker_state()

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
                ]
            },
        )

    monkeypatch.setattr(httpx.Client, "post", mock_post)

    # 1번째 호출 성공 (상한 1/1 소진)
    assert process_ai_batch(deal_ids=[deal_ids[0]], db_session_factory=SessionFactory) == 1
    assert call_count == 1
    assert get_daily_call_count() == 1

    # 2번째 호출은 상한 도달로 외부 HTTP 호출 없이 0 반환
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
                ]
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
