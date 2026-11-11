# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_security_and_monetization.py

보안, 수익화, 논블로킹 프로브 및 장애 격리 단위 테스트:
1. /api/community/ingest-batch 토큰 인증(X-Feeder-Token) 및 미설정 시 외부 IP 차단(Fail-Closed)
2. /admin/* 엔드포인트 인증(X-Admin-Key 헤더, api_key 쿼리) 및 미설정 시 외부 IP 차단(Fail-Closed)
3. /api/coupang/redirect 허용 도메인 화이트리스트 검증 및 오픈 리다이렉트 차단
4. 단축 제휴 링크(coupa.ng, s.click.aliexpress.com) 복원, 자체 제휴 태그 보존 및 AggregatorService 수익화 적용
5. is_linkprice_merchant_approved 논블로킹 캐시 미스 처리 및 백그라운드 데몬 스레드 갱신
6. 스크레이퍼 개별 행 파싱 오류 격리 및 스케줄러 DB 세션 close 보장
"""

import json
import os
import sys
import threading
import time
import pytest
import requests
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from backend.core import url_utils
from backend.database.models import Base, Community, Deal
from backend.database.session import get_db_session
from backend.routers import admin, community, coupang
from backend.scrapers.clien_scraper import ClienScraper
from backend.scrapers.ruliweb_scraper import RuliwebScraper
from backend.services.aggregator_service import AggregatorService


@pytest.fixture()
def isolated_app_and_db():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=engine)
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

    def override_get_db():
        db = TestingSessionLocal()
        try:
            yield db
        finally:
            db.close()

    test_app = FastAPI()
    test_app.include_router(community.router, prefix="/api/community")
    test_app.include_router(admin.router, prefix="/admin")
    test_app.include_router(coupang.router, prefix="/api/coupang")
    test_app.dependency_overrides[get_db_session] = override_get_db

    yield test_app, TestingSessionLocal
    engine.dispose()


def _client_with_ip(asgi_app, ip: str, port: int = 51000) -> TestClient:
    async def _wrapped(scope, receive, send):
        if scope["type"] in ("http", "websocket"):
            scope = dict(scope)
            scope["client"] = (ip, port)
        await asgi_app(scope, receive, send)

    return TestClient(_wrapped)


# ---------------------------------------------------------------------------
# 1. /api/community/ingest-batch 토큰 인증 및 Fail-Closed 검증
# ---------------------------------------------------------------------------

def test_ingest_batch_token_auth_and_fail_closed(isolated_app_and_db, monkeypatch):
    test_app, _ = isolated_app_and_db
    payload = {
        "community_name": "ppomppu",
        "display_name": "뽐뿌",
        "base_url": "https://www.ppomppu.co.kr",
        "deals": [],
    }

    # (A) FEEDER_API_TOKEN 설정 시: 누락/오류 토큰은 401, 일치 토큰은 200
    monkeypatch.setenv("FEEDER_API_TOKEN", "prod-feeder-secret-123")
    external_client = _client_with_ip(test_app, "203.0.113.10", 51000)

    r_missing = external_client.post("/api/community/ingest-batch", json=payload)
    assert r_missing.status_code == 401
    assert r_missing.json()["detail"] == "Invalid or missing feeder token"

    r_wrong = external_client.post(
        "/api/community/ingest-batch",
        json=payload,
        headers={"X-Feeder-Token": "wrong-token"},
    )
    assert r_wrong.status_code == 401

    r_valid = external_client.post(
        "/api/community/ingest-batch",
        json=payload,
        headers={"X-Feeder-Token": "prod-feeder-secret-123"},
    )
    assert r_valid.status_code == 200
    assert r_valid.json()["status"] == "ok"

    # (B) FEEDER_API_TOKEN 미설정 시: 외부 IP 및 리버스 프록시(X-Forwarded-For)는 503 Fail-Closed, 순수 루프백은 200 허용
    monkeypatch.delenv("FEEDER_API_TOKEN", raising=False)

    r_ext_no_cfg = external_client.post("/api/community/ingest-batch", json=payload)
    assert r_ext_no_cfg.status_code == 503
    assert r_ext_no_cfg.json()["detail"] == "FEEDER_API_TOKEN is not configured"

    loopback_client = _client_with_ip(test_app, "127.0.0.1", 51001)
    r_proxied_ext = loopback_client.post(
        "/api/community/ingest-batch",
        json=payload,
        headers={"X-Forwarded-For": "203.0.113.10"},
    )
    assert r_proxied_ext.status_code == 503

    r_local = loopback_client.post("/api/community/ingest-batch", json=payload)
    assert r_local.status_code == 200


# ---------------------------------------------------------------------------
# 2. /admin/* 인증 보호 및 Fail-Closed 검증
# ---------------------------------------------------------------------------

def test_admin_routes_require_key_and_fail_closed(isolated_app_and_db, monkeypatch):
    test_app, _ = isolated_app_and_db
    external_client = _client_with_ip(test_app, "198.51.100.77", 52000)
    loopback_client = _client_with_ip(test_app, "127.0.0.1", 52001)

    # (A) ADMIN_API_KEY 미설정 시 외부 IP 요청은 대시보드(/admin/)와 로그(/admin/logs) 모두 503 차단
    monkeypatch.delenv("ADMIN_API_KEY", raising=False)
    for path in ("/admin/", "/admin/logs"):
        res = external_client.get(path)
        assert res.status_code == 503
        assert res.json()["detail"] == "ADMIN_API_KEY is not configured"
    assert external_client.post("/admin/backup-db").status_code == 503

    # 로컬 루프백에서는 미설정 시 개발 편의상 접근 허용
    res_local = loopback_client.get("/admin/")
    assert res_local.status_code == 200
    assert "X-Admin-Key" in res_local.text

    # (B) ADMIN_API_KEY 설정 시 헤더(X-Admin-Key) 및 쿼리(api_key) 모두 검증
    monkeypatch.setenv("ADMIN_API_KEY", "super-admin-key-999")

    assert external_client.get("/admin/").status_code == 403
    assert external_client.get("/admin/logs").status_code == 403
    assert external_client.get("/admin/?api_key=wrong").status_code == 403

    assert external_client.get("/admin/?api_key=super-admin-key-999").status_code == 200
    assert external_client.get("/admin/logs", headers={"X-Admin-Key": "super-admin-key-999"}).status_code == 200


# ---------------------------------------------------------------------------
# 3. /api/coupang/redirect 오픈 리다이렉트 방어 검증
# ---------------------------------------------------------------------------

def test_coupang_redirect_domain_whitelist(isolated_app_and_db, monkeypatch):
    test_app, _ = isolated_app_and_db
    client = TestClient(test_app)

    # 네트워크 언랩 호출이 테스트 중 외부로 나가지 않도록 고정
    monkeypatch.setattr(url_utils, "unwrap_third_party_affiliate_url", lambda u, allow_network=True: u)

    # 허용된 쇼핑몰 도메인/서브도메인 -> 302
    allowed_urls = [
        "https://www.coupang.com/vp/products/777777",
        "https://item.gmarket.co.kr/Item?goodscode=123456789",
        "https://ko.aliexpress.com/item/100500600700.html",
        "https://www.11st.co.kr/products/555555",
    ]
    for target in allowed_urls:
        resp = client.get("/api/coupang/redirect", params={"url": target}, follow_redirects=False)
        assert resp.status_code == 302, f"허용 도메인이 거부됨: {target}"

    # 비허용 도메인, 스킴 변조, 사용자정보(@) 우회 시도 -> 400 차단
    disallowed_urls = [
        "https://evil-phishing.example.com/login",
        "javascript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "https://www.coupang.com@evil.com/steal",
        "https://fakecoupang.com/vp/products/1",
    ]
    for bad_url in disallowed_urls:
        resp = client.get("/api/coupang/redirect", params={"url": bad_url}, follow_redirects=False)
        assert resp.status_code == 400, f"비허용 URL이 차단되지 않음: {bad_url}"
        assert resp.json()["detail"] == "Disallowed redirect domain"


# ---------------------------------------------------------------------------
# 4. 단축 링크 복원, 자체 태그 보존 및 AggregatorService 수익화 검증
# ---------------------------------------------------------------------------

def test_shortlink_unwrap_and_affiliate_preservation(monkeypatch):
    # 이미 우리 파트너스 ID(AF4452088) 또는 링크프라이스 ID(A100709825)가 적용된 링크는 그대로 보존
    own_coupang = "https://www.coupang.com/vp/products/12345?lptag=AF4452088&subid=insightdeal"
    assert url_utils.unwrap_third_party_affiliate_url(own_coupang, allow_network=True) == own_coupang
    assert url_utils.apply_affiliate_monetization(own_coupang) == own_coupang

    own_lp = "https://click.linkprice.com/click.php?m=gmarket&a=A100709825&l=9999&l_cd1=3&l_cd2=q&tu=https%3A%2F%2Fitem.gmarket.co.kr%2FItem%3Fgoodscode%3D999"
    assert url_utils.unwrap_third_party_affiliate_url(own_lp, allow_network=True) == own_lp
    assert url_utils.apply_affiliate_monetization(own_lp) == own_lp

    url_utils._SHORTLINK_UNWRAP_CACHE.clear()

    # 제3자 coupa.ng 단축 링크 -> 원본 상품 URL 복원 후 우리 lptag(AF4452088) 적용
    class DummyResp:
        def __init__(self, final_url="", headers=None, status_code=302):
            self.url = final_url
            self.headers = headers or {}
            self.status_code = status_code

        def close(self):
            pass

    def fake_get(u, **kwargs):
        if "coupa.ng" in u:
            return DummyResp(
                headers={
                    "Location": "https://www.coupang.com/vp/products/888888?itemId=111&vendorItemId=222&lptag=AF9999999&subid=otheruser"
                },
                status_code=302,
            )
        if "s.click.aliexpress.com" in u:
            return DummyResp(
                final_url="https://ko.aliexpress.com/item/100500999888.html?aff_fcid=thirdparty&aff_fsk=_abc",
                status_code=200,
            )
        return DummyResp(final_url=u, status_code=200)

    monkeypatch.setattr(requests, "get", fake_get)
    monkeypatch.delenv("COUPANG_ACCESS_KEY", raising=False)
    monkeypatch.delenv("COUPANG_SECRET_KEY", raising=False)

    unwrapped_cp = url_utils.unwrap_third_party_affiliate_url("https://coupa.ng/bMockTest1", allow_network=True)
    assert "www.coupang.com/vp/products/888888" in unwrapped_cp
    assert "AF9999999" not in unwrapped_cp

    monetized_cp = url_utils.apply_affiliate_monetization(unwrapped_cp)
    assert "lptag=AF4452088" in monetized_cp

    unwrapped_ali = url_utils.unwrap_third_party_affiliate_url("https://s.click.aliexpress.com/e/_oDxyz", allow_network=True)
    assert "aliexpress.com/item/100500999888.html" in unwrapped_ali
    assert "aff_fcid=" not in unwrapped_ali


@pytest.mark.asyncio
async def test_aggregator_unwraps_shortlinks_and_monetizes_before_save(isolated_app_and_db, monkeypatch):
    _, SessionLocal = isolated_app_and_db
    db = SessionLocal()
    try:
        comm = Community(name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr")
        db.add(comm)
        db.commit()
        db.refresh(comm)

        def fake_unwrap(url: str, allow_network: bool = True) -> str:
            if "coupa.ng/bTest1" in url:
                return "https://www.coupang.com/vp/products/9123456?itemId=10&vendorItemId=20"
            if "coupa.ng/bTest2" in url:
                return "https://item.gmarket.co.kr/Item?goodscode=44556677"
            return url

        monkeypatch.setattr(url_utils, "unwrap_third_party_affiliate_url", fake_unwrap)
        monkeypatch.delenv("COUPANG_ACCESS_KEY", raising=False)
        monkeypatch.delenv("COUPANG_SECRET_KEY", raising=False)

        agg = AggregatorService(db)
        scraped = {
            "title": "[쿠팡] 테스트 무선 마우스 외 1종 (19,900원/무료)",
            "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=808080",
            "price": 19900,
            "currency": "KRW",
            "ecommerce_link": "https://coupa.ng/bTest1",
            "image_url": "https://cdn.ppomppu.co.kr/data3/mouse.jpg",
            "content_html": (
                "<p>1. 무선 마우스 블랙 19,900원 https://coupa.ng/bTest1<br/>"
                "2. 마우스 패드 XL 9,900원 https://coupa.ng/bTest2</p>"
            ),
            "shipping_fee": "무료배송",
            "is_closed": False,
        }

        saved = await agg.process_scraped_deal(comm.id, scraped)
        assert saved is not None
        assert "coupa.ng/bTest1" not in saved.ecommerce_link
        assert "pageKey=9123456" in saved.ecommerce_link or "www.coupang.com/vp/products/9123456" in saved.ecommerce_link
        assert "lptag=AF4452088" in saved.ecommerce_link

        if saved.options_data:
            opts = json.loads(saved.options_data)
            urls = [o.get("url", "") for o in opts]
            assert any("lptag=AF4452088" in u for u in urls)
            assert any("A100709825" in u for u in urls)
    finally:
        db.close()


def test_community_router_has_no_dummy_af_insightdeal_param():
    router_path = os.path.join(_BACKEND_DIR, "routers", "community.py")
    with open(router_path, "r", encoding="utf-8") as f:
        src = f.read()
    assert "af=insightdeal" not in src


# ---------------------------------------------------------------------------
# 5. is_linkprice_merchant_approved 논블로킹 프로브 검증
# ---------------------------------------------------------------------------

def test_linkprice_probe_is_non_blocking_and_populates_cache(monkeypatch):
    url_utils._LINKPRICE_APPROVAL_CACHE.clear()
    url_utils._INFLIGHT_LINKPRICE_PROBES.clear()

    # 1) 사전 승인 머천트(gmarket)는 네트워크 호출 없이 즉시 True
    assert url_utils.is_linkprice_merchant_approved("gmarket", "A100709825") is True

    # 2) 미승인/미캐시 머천트는 API 요청 스레드를 블로킹하지 않고 즉시 False 반환 후 백그라운드 갱신
    probe_started = threading.Event()
    allow_finish = threading.Event()
    call_count = {"n": 0}

    class DummyProbeResp:
        status_code = 302
        headers = {"Location": "https://www.musinsa.com/products/12345"}

    def slow_probe_get(url, timeout=1.8, allow_redirects=False):
        call_count["n"] += 1
        probe_started.set()
        allow_finish.wait(timeout=2.0)
        return DummyProbeResp()

    monkeypatch.setattr(requests, "get", slow_probe_get)

    t0 = time.perf_counter()
    first_result = url_utils.is_linkprice_merchant_approved("musinsa", "A100709825", ttl_sec=900)
    elapsed_ms = (time.perf_counter() - t0) * 1000.0

    # 동기 블로킹이 없으므로 즉시 False 반환되어야 함
    assert first_result is False
    assert elapsed_ms < 200.0
    assert probe_started.wait(timeout=1.0) is True

    # 프로브 진행 중 중복 호출 시 추가 스레드를 띄우지 않아야 함
    second_result = url_utils.is_linkprice_merchant_approved("musinsa", "A100709825", ttl_sec=900)
    assert second_result is False
    assert call_count["n"] == 1

    # 백그라운드 프로브 완료 허용 후 캐시가 True로 갱신되는지 확인
    allow_finish.set()
    deadline = time.time() + 1.5
    cached_val = False
    while time.time() < deadline:
        cached_val = url_utils.is_linkprice_merchant_approved("musinsa", "A100709825", ttl_sec=900)
        if cached_val is True:
            break
        time.sleep(0.02)

    assert cached_val is True
    assert call_count["n"] == 1


# ---------------------------------------------------------------------------
# 6. 스크레이퍼 개별 행 예외 격리 검증
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_scraper_row_fault_isolation():
    # 루리웹: href가 누락된 깨진 행이 섞여 있어도 정상 행은 유실 없이 파싱되어야 함
    ruliweb_html = """
    <table>
      <tr class="table_body blocktarget">
        <td><a class="board_list_item">깨진 링크 행</a></td>
      </tr>
      <tr class="table_body blocktarget">
        <td class="subject"><a class="subject_link" href="/market/board/1020/read/99999">[쿠팡] 정상 파싱 상품 (12,900원)</a></td>
        <td class="recomd">7</td>
        <td class="hit">1200</td>
        <td class="time">12:30</td>
      </tr>
    </table>
    """
    rw_scraper = RuliwebScraper(community_id=1)
    rw_deals = await rw_scraper.parse_list(ruliweb_html)
    assert len(rw_deals) == 1
    assert "정상 파싱 상품" in rw_deals[0]["title"]

    # 클리앙: 개별 행 처리 중 예외가 발생해도 나머지 행은 정상 반환되어야 함
    clien_html = """
    <div class="list_item">
      <span class="list_subject"><a href="/service/board/jirum/111">[G마켓] 첫번째 딜</a></span>
      <span class="timestamp">2026-11-11 10:00:00</span>
    </div>
    <div class="list_item">
      <span class="list_subject"><a href="/service/board/jirum/222">[11번가] 두번째 딜</a></span>
      <span class="timestamp">BAD_TRIGGER</span>
    </div>
    """
    cl_scraper = ClienScraper(community_id=2)
    orig_parse_time = cl_scraper.parse_time_str

    def flaky_parse_time(ts):
        if ts == "BAD_TRIGGER":
            raise ValueError("깨진 타임스탬프 예외")
        return orig_parse_time(ts)

    cl_scraper.parse_time_str = flaky_parse_time
    cl_deals = await cl_scraper.parse_list(clien_html)
    assert len(cl_deals) == 1
    assert "첫번째 딜" in cl_deals[0]["title"]
