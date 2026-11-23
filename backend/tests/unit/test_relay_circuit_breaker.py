# -*- coding: utf-8 -*-
"""
Vercel /api/scraper-relay 106만 회 폭주 방지 회귀 테스트:
1. fetch_html()에서 실패 URL당 _fetch_via_seoul_relay 최대 1회 호출 보장 (2x 중복 차단)
2. HTTP 402/429 수신 시 디스크 기반 글로벌 회로 차단기 가동 및 재호출 0회 보장
3. HTTP 403 3회 연속 시 detail/list 스코프만 30분 쿨다운, ppomppu rss 스코프는 정상 유지
4. cloud_feeder.py is_truncated_ppomppu 최대 재시도(2회) · 30분 백오프 · 글로벌 차단 시 스킵 검증
5. backend/main.py CORS: Cloudflare Pages 및 GCP SSLIP 허용 / 비신뢰 pages.dev 차단 검증
"""

import os
import sys
import time
from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest
from fastapi.testclient import TestClient

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import backend.scrapers.base_scraper as base_scraper_mod
from backend import main as main_mod
from backend.scrapers.base_scraper import (
    AsyncBaseScraper,
    is_relay_blocked,
    record_relay_status,
    reset_relay_circuit_breaker,
)


class _DummyRelayScraper(AsyncBaseScraper):
    def __init__(self):
        super().__init__("뽐뿌테스트", max_concurrent_requests=2)
        self.list_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu"

    async def parse_list(self, html: str) -> list[dict]:
        return []

    async def get_detail(self, url: str, title: str = "") -> dict:
        return {}


@pytest.fixture(autouse=True)
def _isolated_breaker(tmp_path, monkeypatch):
    breaker_file = tmp_path / "relay_breaker.json"
    monkeypatch.setenv("INSIGHTDEAL_RELAY_BREAKER_FILE", str(breaker_file))
    monkeypatch.setenv("SCRAPER_RELAY_ENABLED", "true")
    reset_relay_circuit_breaker()
    yield
    reset_relay_circuit_breaker()


@pytest.mark.asyncio
async def test_fetch_html_calls_seoul_relay_at_most_once_on_failure(monkeypatch):
    monkeypatch.setattr(base_scraper_mod, "IS_CLOUD_ENV", True)
    scraper = _DummyRelayScraper()
    mock_client = MagicMock()
    scraper.client = mock_client

    relay_calls = []

    async def fake_relay(url: str):
        relay_calls.append(url)
        return None

    monkeypatch.setattr(scraper, "_fetch_via_seoul_relay", fake_relay)

    # 1) 직접 요청 403 반환 시 2차 릴레이 중복 호출 금지
    resp_403 = MagicMock()
    resp_403.status_code = 403
    mock_client.get = AsyncMock(return_value=resp_403)

    target = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=750999"
    assert await scraper.fetch_html(target) is None
    assert len(relay_calls) == 1

    # 2) 직접 요청 타임아웃 예외 시에도 2차 릴레이 중복 호출 금지
    relay_calls.clear()
    mock_client.get = AsyncMock(side_effect=TimeoutError("connect timed out"))
    assert await scraper.fetch_html(target) is None
    assert len(relay_calls) == 1


@pytest.mark.asyncio
async def test_relay_402_trips_disk_persisted_global_breaker_with_zero_rss_and_zero_subsequent_calls(
    monkeypatch,
):
    calls = []

    async def fake_get(self, url, headers=None, **kwargs):
        calls.append(url)
        resp = MagicMock()
        resp.status_code = 402
        resp.text = "DEPLOYMENT_DISABLED"
        return resp

    monkeypatch.setattr(httpx.AsyncClient, "get", fake_get)

    scraper1 = _DummyRelayScraper()
    list_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu"
    res1 = await scraper1._fetch_via_seoul_relay(list_url)

    assert res1 is None
    # 402 응답 즉시 중단: RSS 폴백까지 포함해 1회만 호출
    assert len(calls) == 1

    # 새 프로세스(--one-shot 크론 다음 회차) 시뮬레이션: 인메모리 캐시 비우고 디스크 상태만 사용
    base_scraper_mod._RELAY_BREAKER_CACHE = None
    scraper2 = _DummyRelayScraper()
    res2 = await scraper2._fetch_via_seoul_relay(
        "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&no=106580"
    )
    assert res2 is None
    assert len(calls) == 1  # 추가 HTTP 호출 0회

    blocked, reason = is_relay_blocked("https://bbasak.com/bbs/board.php?bo_table=bbasak1")
    assert blocked is True
    assert reason == "http_402"


@pytest.mark.asyncio
async def test_relay_403_three_times_cools_detail_only_keeping_rss_active(monkeypatch):
    detail_url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&no=106580"
    rss_url = "https://www.ppomppu.co.kr/rss.php?id=ppomppu8"

    calls = []

    async def fake_get(self, url, headers=None, **kwargs):
        calls.append(url)
        resp = MagicMock()
        resp.status_code = 403
        resp.text = "Forbidden"
        return resp

    monkeypatch.setattr(httpx.AsyncClient, "get", fake_get)

    scraper = _DummyRelayScraper()
    for i in range(3):
        await scraper._fetch_via_seoul_relay(f"{detail_url}&try={i}")
    assert len(calls) == 3

    # 4번째 detail 호출은 HTTP 0회로 즉시 차단
    await scraper._fetch_via_seoul_relay(f"{detail_url}&try=4")
    assert len(calls) == 3

    blocked_detail, reason_detail = is_relay_blocked(detail_url)
    assert blocked_detail is True
    assert reason_detail == "host_403_cooldown"

    # RSS 스코프는 쿨다운 영향 없음
    blocked_rss, reason_rss = is_relay_blocked(rss_url)
    assert blocked_rss is False
    assert reason_rss == ""


@pytest.mark.asyncio
async def test_cloud_feeder_ppomppu_truncated_backoff_and_global_breaker_skip(monkeypatch):
    import backend.scheduler.cloud_feeder as cf
    from backend.scrapers.ppomppu_scraper import PpomppuScraper

    monkeypatch.setattr(cf, "IS_CLOUD_ENV", True)
    monkeypatch.setattr(cf, "_preload_existing_urls", AsyncMock())
    monkeypatch.setattr(cf, "_fetch_jsmon_and_hotgusa_enrichment", AsyncMock(return_value={}))
    monkeypatch.setattr(cf, "_save_local_seen_cache", lambda: None)

    ppomppu_item_url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=777001"
    sample_rss = f"""<?xml version="1.0" encoding="utf-8"?>
    <rss version="2.0"><channel><item>
      <title><![CDATA[[지마켓] 백오프 테스트 핫딜 (10,000원/무배)]]></title>
      <link>{ppomppu_item_url}</link>
      <description><![CDATA[요약...]]></description>
    </item></channel></rss>"""

    relay_gets = []

    class FakeResp:
        def __init__(self, status_code, text="", json_data=None):
            self.status_code = status_code
            self.text = text
            self._json = json_data or {}

        def json(self):
            return self._json

    class FakeHttpClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return False

        async def get(self, url, headers=None, **kwargs):
            relay_gets.append(url)
            if "id%3Dppomppu&" in url or url.endswith("id%3Dppomppu"):
                return FakeResp(200, text=sample_rss)
            return FakeResp(200, text="")

        async def post(self, url, json=None, headers=None, **kwargs):
            return FakeResp(200, json_data={"processed": len((json or {}).get("deals", []))})

    detail_calls = []

    async def fake_get_detail(self, url, title=""):
        detail_calls.append(url)
        return {}

    async def fake_run(self, url=None):
        return []

    monkeypatch.setattr(cf.httpx, "AsyncClient", FakeHttpClient)
    monkeypatch.setattr(AsyncBaseScraper, "run", fake_run)
    monkeypatch.setattr(PpomppuScraper, "get_detail", fake_get_detail)

    # Case 1: 이미 최대 2회 재시도된 축약 글은 get_detail 재호출 0회
    cf._SEEN_DETAIL_CACHE.clear()
    cf._SEEN_DETAIL_CACHE[("뽐뿌", ppomppu_item_url)] = {
        "title": "[지마켓] 백오프 테스트 핫딜 (10,000원/무배)",
        "price": 10000,
        "currency": "KRW",
        "content_html": "<p>요약...</p>",
        "ecommerce_link": "",
        "image_url": "https://cdn.example.com/a.jpg",
        "shipping_fee": "무배",
        "is_closed": False,
        "is_super_hotdeal": False,
        "like_count": 0,
        "view_count": 1500,
        "comment_count": 0,
        "detail_retry_count": 2,
        "detail_last_attempt_ts": int(time.time()) - 7200,
    }
    await cf.scrape_and_push_once()
    assert detail_calls == []

    # Case 2: 재시도 1회 후 30분 미만 경과 시에도 get_detail 재호출 0회
    cf._SEEN_DETAIL_CACHE[("뽐뿌", ppomppu_item_url)]["detail_retry_count"] = 1
    cf._SEEN_DETAIL_CACHE[("뽐뿌", ppomppu_item_url)]["detail_last_attempt_ts"] = int(time.time()) - 120
    await cf.scrape_and_push_once()
    assert detail_calls == []

    # Case 3: 글로벌 402 회로 차단 활성 상태에서는 RSS 릴레이 GET도 0회
    relay_gets.clear()
    record_relay_status("https://www.ppomppu.co.kr/rss.php?id=ppomppu", 402)
    await cf.scrape_and_push_once()
    assert not any("scraper-relay" in u for u in relay_gets)


def test_cors_allows_cloudflare_pages_and_gcp_and_blocks_untrusted():
    client = TestClient(main_mod.app)

    for allowed_origin in (
        "https://insightdeal.pages.dev",
        "https://abc123.insightdeal.pages.dev",
        "https://insightdeal-2ai.pages.dev",
        "https://98710e1d.insightdeal-2ai.pages.dev",
        "https://34.59.208.14.sslip.io",
    ):
        res = client.options(
            "/api/meta/categories",
            headers={
                "Origin": allowed_origin,
                "Access-Control-Request-Method": "GET",
                "Access-Control-Request-Headers": "X-Device-Token, Content-Type",
            },
        )
        hdrs = {k.lower(): v for k, v in res.headers.items()}
        assert hdrs.get("access-control-allow-origin") == allowed_origin

    for evil_origin in (
        "https://evil.pages.dev",
        "https://evil-insightdeal.pages.dev",
        "https://evil-insightdeal-2ai.pages.dev",
    ):
        res = client.options(
            "/api/meta/categories",
            headers={
                "Origin": evil_origin,
                "Access-Control-Request-Method": "GET",
            },
        )
        hdrs = {k.lower(): v for k, v in res.headers.items()}
        assert "access-control-allow-origin" not in hdrs
