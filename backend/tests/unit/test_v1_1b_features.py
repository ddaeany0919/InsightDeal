# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_v1_1b_features.py

InsightDeal(얼만데) v1.1b (P1-01 ~ P1-06, P1-12) 백엔드 단위·보안 테스트:
1. P1-01: PBKDF2-HMAC-SHA256 해싱, 레거시 SHA-256 투명 업그레이드, auth_v1 HMAC 토큰 발급/변조/탈퇴 무효화,
          6자리 페어링 코드 발급·소비·만료·자기병합·중복사용·5회 오답 잠금·IP 429·무손실 병합(fcm_token 비복제)
2. P1-02: proxy_image 이벤트 루프별 WeakKeyDictionary 격리, 404/200 Single-flight Coalescing, 원자적 .tmp -> os.replace
3. P1-03: _extract_body_images 다중 마커/엔티티/스킴 정규화 및 18종 XSS/SSRF/플레이스홀더 차단
4. P1-04: is_in_dnd_range 반개구간 [start, end), dnd_morning_digest_job 멱등성·토큰 중복방지·야간 미해제 보류,
          ensure_schema_migrations 구형 SQLite ALTER TABLE 및 users.notifications DTO 신규 필드 노출
5. P1-05: 11종 출처('뽐뿌해외'), 마이크로초 커서 정밀도 보존(_format_cursor_iso), 비정상 커서 400(500 차단),
          include_closed/only_hot/hide_closed 우선순위 및 캐시 격리
6. P1-06: report-closed 401/404/409/429, 3명 임계치 is_closed=True + 캐시 무효화
7. P1-12: is_search_or_community_url() 폴백 URL 탐지(amazon/29cm/ohou/temu 등) 및 push_worker Wishlist 일일 한도 공유
"""

import asyncio
from datetime import datetime, timedelta, timezone
import hashlib
import os
import socket
import sys
from types import SimpleNamespace

import httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from backend import main as main_mod
from backend.core.categories import STANDARD_SOURCES
from backend.core.device_auth import create_device_token
from backend.core.url_utils import is_safe_external_url, is_search_or_community_url
from backend.database import models
from backend.database import session as db_session_mod
from backend.database.models import Base as CommunityBase
from backend.database.session import get_db, get_db_session
from backend.models.wishlist_models import Base as WishlistBase, Wishlist
from backend.routers import auth as auth_router
from backend.routers import community as community_router
from backend.services import push_worker

KST = timezone(timedelta(hours=9))
VALID_PNG = b"\x89PNG\r\n\x1a\n" + (b"\x21" * 100)


@pytest.fixture()
def unit_v11b_env(monkeypatch, tmp_path):
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    CommunityBase.metadata.create_all(bind=engine)
    WishlistBase.metadata.create_all(bind=engine)
    SessionCls = sessionmaker(autocommit=False, autoflush=False, expire_on_commit=False, bind=engine)

    seed = SessionCls()
    try:
        seed.add_all([
            models.Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr"),
            models.Community(id=2, name="ppomppu_overseas", display_name="뽐뿌해외", base_url="https://www.ppomppu.co.kr"),
            models.Community(id=3, name="ruliweb", display_name="루리웹", base_url="https://bbs.ruliweb.com"),
        ])
        seed.commit()
    finally:
        seed.close()

    def _override_db():
        db = SessionCls()
        try:
            yield db
        finally:
            db.close()

    main_mod.app.dependency_overrides[get_db] = _override_db
    main_mod.app.dependency_overrides[get_db_session] = _override_db
    monkeypatch.setattr(db_session_mod, "SessionLocal", SessionCls)
    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "unit-v11b-secret-key")
    monkeypatch.setattr(main_mod, "IMAGE_CACHE_DIR", str(tmp_path))
    monkeypatch.setattr(main_mod, "_get_deal_backup_candidates_sync", lambda *a, **kw: (None, []))

    auth_router.reset_rate_limits()
    community_router.reset_rate_limits()
    community_router.invalidate_feed_api_cache()
    community_router._COMMUNITY_ID_CACHE.clear()
    push_worker.reset_rate_limits()

    try:
        with TestClient(main_mod.app) as client:
            yield SimpleNamespace(client=client, SessionCls=SessionCls, engine=engine, tmp_path=tmp_path)
    finally:
        auth_router.reset_rate_limits()
        community_router.reset_rate_limits()
        community_router.invalidate_feed_api_cache()
        push_worker.reset_rate_limits()
        main_mod.app.dependency_overrides.clear()
        engine.dispose()


def test_p1_01_pbkdf2_hash_verify_upgrade_and_token_revocation(unit_v11b_env):
    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls

    # 1. PBKDF2 해시 생성·검증 단위 검증
    hashed = auth_router.hash_password("UltraSafePass!2026")
    assert hashed.startswith("pbkdf2_sha256$200000$")
    ok, upgrade = auth_router.verify_password("UltraSafePass!2026", hashed)
    assert ok is True and upgrade is False
    bad, _ = auth_router.verify_password("WrongPass", hashed)
    assert bad is False

    # 2. 레거시 SHA-256 해시 검증 및 로그인 시 PBKDF2 투명 업그레이드
    legacy_pw = "LegacyPw!99"
    legacy_hex = hashlib.sha256(legacy_pw.encode("utf-8")).hexdigest()
    ok_leg, upg_leg = auth_router.verify_password(legacy_pw, legacy_hex)
    assert ok_leg is True and upg_leg is True

    db = SessionCls()
    try:
        db.add(models.User(username="leg_u1", password_hash=legacy_hex, nickname="레거시유저1"))
        db.commit()
    finally:
        db.close()

    res_login = client.post("/api/auth/login", json={"username": "leg_u1", "password": legacy_pw})
    assert res_login.status_code == 200
    token = res_login.json()["access_token"]
    assert token.startswith("auth_v1.")
    assert res_login.json()["token_type"] == "bearer"

    # DB에 PBKDF2로 업그레이드 확인
    db = SessionCls()
    try:
        u = db.query(models.User).filter(models.User.username == "leg_u1").first()
        assert u.password_hash.startswith("pbkdf2_sha256$200000$")
    finally:
        db.close()

    # 3. link-device 후 회원 탈퇴(/withdraw) -> 기존 Bearer 즉시 401
    dev_tok = create_device_token("unit-dev-1")
    assert client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {token}", "X-Device-Token": dev_tok},
    ).status_code == 200

    w_res = client.post("/api/auth/withdraw", json={"username": "leg_u1", "password": legacy_pw})
    assert w_res.status_code == 200

    dev_tok2 = create_device_token("unit-dev-2")
    assert client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {token}", "X-Device-Token": dev_tok2},
    ).status_code == 401


def test_p1_01_pairing_merge_preserves_channel_tokens_and_handles_conflicts(unit_v11b_env):
    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls

    r_a = client.post("/api/push/device", json={"fcm_token": "secret-fcm-a", "dnd_enabled": True, "dnd_start_time": "22:00", "dnd_end_time": "08:00"}).json()
    r_b = client.post("/api/push/device", json={"fcm_token": "secret-fcm-b"}).json()
    h_a = {"X-Device-Token": r_a["device_token"]}
    h_b = {"X-Device-Token": r_b["device_token"]}

    client.post("/api/push/keywords", json={"keyword": "공통키워드"}, headers=h_a)
    client.post("/api/push/keywords", json={"keyword": "비활성키워드"}, headers=h_a)
    client.post("/api/push/keywords/toggle", json={"keyword": "비활성키워드"}, headers=h_a)
    client.post("/api/push/keywords", json={"keyword": "공통키워드"}, headers=h_b)

    client.post("/api/wishlist/item-alert", json={"keyword": "노트북", "target_price": 1200000}, headers=h_a)
    client.post("/api/wishlist/item-alert", json={"keyword": "노트북", "target_price": 1300000}, headers=h_b)

    code = client.post("/api/auth/pairing-code", headers=h_a).json()["code"]
    claim = client.post("/api/auth/merge-device", json={"code": code}, headers=h_b)
    assert claim.status_code == 200
    body = claim.json()
    assert body["success"] is True and body["dnd_synced"] is True

    # 채널 토큰(fcm_token)은 복제되지 않고 각 기기 고유 토큰 유지
    db = SessionCls()
    try:
        dev_b = db.query(models.DeviceToken).filter(models.DeviceToken.device_uuid == r_b["device_uuid"]).first()
        assert dev_b.fcm_token == "secret-fcm-b"
        assert dev_b.dnd_enabled is True and dev_b.dnd_start_time == "22:00"
        wls = db.query(Wishlist).filter(Wishlist.user_id == r_b["device_uuid"], Wishlist.keyword == "노트북").all()
        assert len(wls) == 1
        assert wls[0].target_price == 1200000
    finally:
        db.close()

    b_kws = client.get("/api/push/keywords", headers=h_b).json()["keywords"]
    assert b_kws.count("공통키워드") == 1
    assert "비활성키워드" not in b_kws


def test_p1_02_proxy_image_multi_loop_weakref_and_atomic_write(unit_v11b_env, monkeypatch):
    monkeypatch.setattr(socket, "getaddrinfo", lambda host, port, *a, **kw: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))])

    async def _ok_get(url, **kwargs):
        return httpx.Response(200, content=VALID_PNG, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _ok_get)

    # 별도 이벤트 루프 2회 연속 실행 시 RuntimeError(different event loop)가 발생하지 않아야 함
    async def _run_once(u: str):
        async with httpx.AsyncClient(app=main_mod.app, base_url="http://testserver") as ac:
            return await ac.get("/api/proxy-image", params={"url": u})

    r1 = asyncio.run(_run_once("https://cdn.example.com/loop-1.png"))
    r2 = asyncio.run(_run_once("https://cdn.example.com/loop-2.png"))
    assert r1.status_code == 200 and r2.status_code == 200
    assert list(unit_v11b_env.tmp_path.glob("*.tmp")) == []
    assert len(list(unit_v11b_env.tmp_path.glob("*.cache"))) == 2


def test_p1_03_extract_body_images_security_and_normalization():
    raw_html = (
        '<img data-original="//cdn.example.com/p1.jpg?a=1&amp;b=2">'
        '<img src="https://cdn.example.com/p1.jpg?a=1&b=2">'
        '[상품이미지: https://cdn.example.com/p2.png]'
        '<img src="javascript:alert(1)">'
        '<img src="vbscript:msgbox(1)">'
        '<img src="data:image/svg+xml,<svg onload=alert(1)>">'
        '<img src="http://[::ffff:127.0.0.1]/admin.png">'
        '<img src="http://169.254.169.254/latest/meta-data/">'
        '<img src="https://cdn.example.com/x.jpg?q=onerror%3Dalert(1)">'
        '<img src="https://user:pass@cdn.example.com/secret.png">'
        '<img src="https://cdn.example.com/no_image.svg">'
        '<p>[첨부된 이미지 링크들]: https://cdn.example.com/p3.webp, https://cdn.example.com/dot03.gif</p>'
    )
    imgs = community_router._extract_body_images(raw_html)
    assert imgs == [
        "https://cdn.example.com/p1.jpg?a=1&b=2",
        "https://cdn.example.com/p2.png",
        "https://cdn.example.com/p3.webp",
    ]
    assert is_safe_external_url("https://cdn.example.com/ok.png") is True
    assert is_safe_external_url("http://[::1]/bad.png") is False


def test_p1_04_dnd_half_open_digest_idempotency_and_legacy_alter(unit_v11b_env, monkeypatch):
    # 1. DND 반개구간 [start, end) 경계 검증
    assert push_worker.is_in_dnd_range(datetime(2026, 11, 20, 22, 0, 0).time(), "22:00", "08:00") is True
    assert push_worker.is_in_dnd_range(datetime(2026, 11, 20, 7, 59, 59).time(), "22:00", "08:00") is True
    assert push_worker.is_in_dnd_range(datetime(2026, 11, 20, 8, 0, 0).time(), "22:00", "08:00") is False
    assert push_worker.is_in_dnd_range(datetime(2026, 11, 20, 15, 0, 0).time(), "13:00", "15:00") is False

    # 2. 구형 SQLite 테이블 ensure_schema_migrations 검증 + NotificationAlertDto 노출 검증
    legacy_path = unit_v11b_env.tmp_path / "legacy_unit.db"
    leg_engine = create_engine(f"sqlite:///{legacy_path}", connect_args={"check_same_thread": False})
    try:
        with leg_engine.begin() as conn:
            conn.execute(text(
                "CREATE TABLE notification_alerts ("
                "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id VARCHAR(100), title TEXT NOT NULL, "
                "keyword VARCHAR(100) NOT NULL, received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, deal_url VARCHAR(2048))"
            ))
            conn.execute(text(
                "INSERT INTO notification_alerts (user_id, title, keyword, deal_url) VALUES ('d1', '구형알림', '노트북', 'https://example.com')"
            ))
        mgr = db_session_mod.DatabaseManager.__new__(db_session_mod.DatabaseManager)
        mgr.engine = leg_engine
        mgr.ensure_schema_migrations()
        with leg_engine.connect() as conn:
            cols = {r[1] for r in conn.execute(text("PRAGMA table_info(notification_alerts)")).fetchall()}
        assert {"is_read", "dnd_suppressed", "digest_sent_at"}.issubset(cols)
    finally:
        leg_engine.dispose()

    # 3. GET /api/users/notifications 신규 필드 포함 및 dnd_suppressed=True 유지 반환
    reg = unit_v11b_env.client.post("/api/push/device", json={"dnd_enabled": True}).json()
    headers = {"X-Device-Token": reg["device_token"]}
    db = unit_v11b_env.SessionCls()
    try:
        db.add(models.NotificationAlert(
            user_id=reg["device_uuid"],
            title="야간 억제 테스트 딜",
            keyword="테스트",
            deal_url="https://example.com/1",
            dnd_suppressed=True,
        ))
        db.commit()
    finally:
        db.close()

    notifs = unit_v11b_env.client.get("/api/users/notifications", headers=headers).json()
    assert len(notifs) == 1
    assert notifs[0]["dnd_suppressed"] is True
    assert notifs[0]["is_read"] is False
    assert notifs[0]["digest_sent_at"] is None


def test_p1_05_microsecond_cursor_precision_invalid_cursor_and_11_sources(unit_v11b_env):
    assert len(STANDARD_SOURCES) == 11
    assert STANDARD_SOURCES[3] == "뽐뿌해외"

    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls
    base_dt = datetime(2026, 11, 19, 10, 0, 0)

    # 마이크로초 차이가 있는 3건 시드 -> page_size=2로 누락/중복 없이 커서 순회 검증
    distinct_names = ["마이크로초모니터알파", "마이크로초키보드베타", "마이크로초헤드셋감마"]
    db = SessionCls()
    try:
        deals = [
            models.Deal(
                source_community_id=2,
                title=f"[아마존] {distinct_names[i]} ({10000 + i * 100}원)",
                post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&no={1000 + i}",
                ecommerce_link=f"https://www.amazon.com/dp/TESTMICRO{i}",
                price=str(10000 + i * 100),
                base_product_name=distinct_names[i],
                model_code=f"MC{i:03d}X",
                indexed_at=base_dt + timedelta(microseconds=500000 - i * 100000),
            )
            for i in range(3)
        ]
        db.add_all(deals)
        db.commit()
        expected_ids = [d.id for d in deals]
    finally:
        db.close()

    p1 = client.get("/api/community/hot-deals", params={"limit": 2, "source": "뽐뿌해외"}).json()
    assert [d["id"] for d in p1["deals"]] == expected_ids[:2]
    assert p1["has_more"] is True
    assert "." in p1["next_cursor"]["indexed_at"]  # 마이크로초 정밀도 보존

    p2 = client.get(
        "/api/community/hot-deals",
        params={
            "limit": 2,
            "source": "뽐뿌해외",
            "cursor_indexed_at": p1["next_cursor"]["indexed_at"],
            "cursor_id": p1["next_cursor"]["id"],
        },
    ).json()
    assert [d["id"] for d in p2["deals"]] == expected_ids[2:]
    assert p2["has_more"] is False
    assert p2["next_cursor"] is None

    # 잘못된/부분 커서는 400 반환 (500 금지)
    assert client.get("/api/community/hot-deals", params={"cursor_id": 1}).status_code == 400
    assert client.get("/api/community/hot-deals", params={"cursor_indexed_at": "bad-date", "cursor_id": 1}).status_code == 400


def test_p1_06_and_p1_12_report_closed_fallback_detection_and_wishlist_quota(unit_v11b_env, monkeypatch):
    # 1. P1-12 검색 폴백 URL 탐지 확장 검증
    assert is_search_or_community_url("https://www.amazon.com/s?k=ssd") is True
    assert is_search_or_community_url("https://search.29cm.co.kr/?keyword=nike") is True
    assert is_search_or_community_url("https://ohou.se/productions/feed?query=chair") is True
    assert is_search_or_community_url("https://www.temu.com/search_result.html?search_key=usb") is True
    assert is_search_or_community_url("https://www.amazon.com/dp/B09XS7JWHH") is False

    # 2. P1-06 report-closed 3회 임계치 검증
    db = unit_v11b_env.SessionCls()
    try:
        d = models.Deal(
            source_community_id=1,
            title="[아마존] 폴백 테스트 상품 (15,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=55555",
            ecommerce_link="https://www.amazon.com/s?k=test",
            price="15000",
        )
        db.add(d)
        db.commit()
        db.refresh(d)
        did = d.id
    finally:
        db.close()

    det = unit_v11b_env.client.get(f"/api/community/deals/{did}").json()
    assert det["is_search_fallback"] is True and det["isSearchFallback"] is True
    assert det["report_count"] == 0 and det["reportCount"] == 0

    toks = [create_device_token(f"rep-dev-{i}") for i in range(3)]
    for idx, tok in enumerate(toks, start=1):
        res = unit_v11b_env.client.post(f"/api/community/deals/{did}/report-closed", headers={"X-Device-Token": tok})
        assert res.status_code == 200
        assert res.json()["report_count"] == idx
        assert res.json()["is_closed"] is (idx >= 3)

    assert unit_v11b_env.client.post(f"/api/community/deals/{did}/report-closed", headers={"X-Device-Token": toks[0]}).status_code == 409

    # 3. P1-12 Wishlist 레이트리밋 일일 3회 공유 검증
    fcm_sent = []
    monkeypatch.setattr(
        push_worker.messaging,
        "send_each",
        lambda chunk: fcm_sent.extend(chunk) or SimpleNamespace(success_count=len(chunk), failure_count=0),
    )
    db = unit_v11b_env.SessionCls()
    try:
        dev = models.DeviceToken(device_uuid="wl-limit-dev", fcm_token="fcm-wl-limit", is_active=True)
        db.add(dev)
        db.add(Wishlist(user_id="wl-limit-dev", keyword="무선마우스", target_price=100000, is_active=True, alert_enabled=True))
        deal_ids = []
        for i in range(4):
            item = models.Deal(
                source_community_id=1,
                title=f"[쿠팡] 무선마우스 {i}호 (30,000원)",
                post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=81{i}",
                ecommerce_link=f"https://www.coupang.com/vp/products/81{i}",
                price="30000",
            )
            db.add(item)
            db.flush()
            deal_ids.append(item.id)
        db.commit()
    finally:
        db.close()

    day_kst = datetime(2026, 11, 19, 14, 0, 0, tzinfo=KST)
    for target_did in deal_ids:
        push_worker.background_trigger_keyword_alarms(target_did, session_factory=unit_v11b_env.SessionCls, now_kst=day_kst)
    assert len(fcm_sent) == 3


def test_iter2_security_nonstandard_ipv4_port_double_encode_event_handler_and_img_priority(unit_v11b_env, monkeypatch):
    """[Iter2 보안] 비표준 IPv4·비HTTP 포트 SSRF, 이중 URL 인코딩·임의 이벤트 핸들러 차단 및 data-original 우선순위 추출"""
    client = unit_v11b_env.client
    monkeypatch.setattr(socket, "getaddrinfo", lambda host, port, *a, **kw: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))])

    async def _fail_if_called(url, **kwargs):
        raise AssertionError(f"차단 대상 URL에 업스트림 요청이 발생함: {url}")

    monkeypatch.setattr(main_mod.proxy_client, "get", _fail_if_called)

    blocked_urls = [
        "http://0177.0.0.1/a.png",
        "http://2130706433/a.png",
        "http://0x7f000001/a.png",
        "http://127.1/a.png",
        "http://0/a.png",
        "http://8.8.8.8:6379/a.png",
        "https://cdn.example.com:8443/a.png",
    ]
    for bad_u in blocked_urls:
        assert is_safe_external_url(bad_u) is False, f"is_safe_external_url 미차단: {bad_u}"
        res = client.get("/api/proxy-image", params={"url": bad_u})
        assert res.status_code == 404, f"proxy_image 미차단: {bad_u}"
        assert res.headers.get("cache-control") == "no-store"

    # 80/443 표준 포트는 허용
    assert is_safe_external_url("http://cdn.example.com:80/a.png") is True
    assert is_safe_external_url("https://cdn.example.com:443/a.png") is True

    # 이중 URL 인코딩, 임의 이벤트 핸들러, 비표준 IP, 지연로딩 data-original 우선순위 추출
    html_payload = (
        '<img src="http://0177.0.0.1/a.png">'
        '<img src="http://2130706433/a.png">'
        '<img src="http://0x7f000001/a.png">'
        '<img src="http://127.1/a.png">'
        '<img src="http://0/a.png">'
        '<img src="http://8.8.8.8:6379/a.png">'
        '<img src="https://cdn.example.com/a.jpg?q=%253Cscript%253Ealert(1)%253C/script%253E">'
        '<img src="https://cdn.example.com/a.png?onerror%253Dalert(1)">'
        '<img src="https://cdn.example.com/a.png?onpointerover=alert(1)">'
        '<img src="https://cdn.example.com/a.png?onanimationstart=alert(1)">'
        '<img src="https://cdn.example.com/lazyloading.gif" data-original="https://cdn.example.com/real.jpg">'
    )
    extracted = community_router._extract_body_images(html_payload)
    assert extracted == ["https://cdn.example.com/real.jpg"]


def test_iter2_security_xff_spoofing_and_withdraw_id_reuse_revocation(unit_v11b_env):
    """[Iter2 보안] 비신뢰 직접 접속 X-Forwarded-For 스푸핑 차단, 기기 발급 한도 및 탈퇴 후 ID 재사용 토큰 부활 차단"""
    def _client_with_ip(ip: str, port: int = 51234) -> TestClient:
        async def _wrapped(scope, receive, send):
            if scope["type"] in ("http", "websocket"):
                scope = dict(scope)
                scope["client"] = (ip, port)
            await main_mod.app(scope, receive, send)
        return TestClient(_wrapped)

    ext_client = _client_with_ip("198.51.100.5")

    # 1. 외부 직접 접속 IP가 매 요청 X-Forwarded-For 및 기기 토큰을 회전해도 11회째 claim 429 차단
    statuses = []
    for i in range(12):
        reg = ext_client.post(
            "/api/push/device",
            json={},
            headers={"X-Forwarded-For": f"203.0.113.{10 + i}"},
        )
        assert reg.status_code == 200
        tok = reg.json()["device_token"]
        claim = ext_client.post(
            "/api/auth/claim-pairing-code",
            json={"code": "999999"},
            headers={"X-Device-Token": tok, "X-Forwarded-For": f"203.0.113.{50 + i}, 127.0.0.1"},
        )
        statuses.append(claim.status_code)
    assert 429 in statuses, f"비신뢰 직접 접속 XFF 스푸핑이 레이트리밋을 우회함: {statuses}"

    # 2. 신뢰 프록시(127.0.0.1) 경유 시 우측 비신뢰 유효 IP 선택 확인
    proxy_client = _client_with_ip("127.0.0.1")
    fake_req = SimpleNamespace(
        client=SimpleNamespace(host="127.0.0.1"),
        headers={"x-forwarded-for": "192.0.2.1, 203.0.113.77, 127.0.0.1"},
    )
    assert auth_router._extract_client_ip(fake_req) == "203.0.113.77"
    direct_req = SimpleNamespace(
        client=SimpleNamespace(host="198.51.100.5"),
        headers={"x-forwarded-for": "203.0.113.77"},
    )
    assert auth_router._extract_client_ip(direct_req) == "198.51.100.5"

    # 3. /withdraw 후 동일 username 재가입 시 SQLite ID 재사용 여부와 무관하게 구 auth_v1 토큰 401 거부
    uname = "reuse_victim_user"
    s1 = proxy_client.post("/api/auth/signup", json={"username": uname, "password": "OldPassword!1", "nickname": "원조유저"})
    assert s1.status_code == 200
    old_bearer = s1.json()["access_token"]

    w_res = proxy_client.post("/api/auth/withdraw", json={"username": uname, "password": "OldPassword!1"})
    assert w_res.status_code == 200

    s2 = proxy_client.post("/api/auth/signup", json={"username": uname, "password": "NewPassword!2", "nickname": "재가입유저"})
    assert s2.status_code == 200
    new_bearer = s2.json()["access_token"]

    dev_reg = proxy_client.post("/api/push/device", json={}).json()
    dev_tok = dev_reg["device_token"]
    assert proxy_client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {old_bearer}", "X-Device-Token": dev_tok},
    ).status_code == 401
    assert proxy_client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {new_bearer}", "X-Device-Token": dev_tok},
    ).status_code == 200

    # 4. POST /api/push/device IP당 분당 발급 레이트리밋(120/60s) 확인 및 reset_rate_limits 복구
    rate_client = _client_with_ip("198.51.100.99")
    dev_statuses = [rate_client.post("/api/push/device", json={}).status_code for _ in range(122)]
    assert dev_statuses[:120] == [200] * 120
    assert dev_statuses[120:] == [429, 429]
    auth_router.reset_rate_limits()
    assert rate_client.post("/api/push/device", json={}).status_code == 200


def test_iter2_sqlite_current_timestamp_cursor_and_dsu_cross_page_and_limits(unit_v11b_env):
    """[Iter2 경계/로직] SQLite 19자 CURRENT_TIMESTAMP 커서 무한루프 차단, DSU 페이지 경계(119건) 중복 차단, limit/offset 하한 및 커버낫 폴백"""
    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls

    # 1. limit <= 0 / offset < 0 거부
    for bad_params in ({"limit": 0}, {"limit": -1}, {"limit": -5}, {"offset": -1}, {"limit": 5, "offset": -5}):
        r_bad = client.get("/api/community/hot-deals", params=bad_params)
        assert r_bad.status_code in (400, 422), f"음수/0 파라미터 미차단 ({bad_params}): {r_bad.status_code}"

    # 2. 커버낫 포함 42개 쇼핑몰 검색 폴백 URL 전수 탐지
    from backend.routers.community import _build_direct_mall_search_url
    assert is_search_or_community_url("https://covernat.net/product/search.html?keyword=%ED%85%8C%EC%8A%A4%ED%8A%B8") is True
    for shop_name in community_router._DIRECT_MALL_SEARCH_BUILDERS if hasattr(community_router, "_DIRECT_MALL_SEARCH_BUILDERS") else [
        "쿠팡", "네이버", "지마켓", "옥션", "11번가", "롯데온", "SSG", "이마트몰", "신세계몰",
        "하이마트", "교보문고", "예스24", "알라딘", "무신사", "29CM", "W컨셉", "올리브영",
        "오늘의집", "GS샵", "CJ온스타일", "현대Hmall", "NS홈쇼핑", "홈앤쇼핑", "SK스토아",
        "아마존", "알리익스프레스", "테무", "큐텐", "아이허브", "다이소몰", "컬리",
        "배달의민족", "요기요", "야놀자", "여기어때", "카카오", "스팀", "다이렉트게임즈",
        "에픽게임즈", "스타벅스", "투썸플레이스", "커버낫",
    ]:
        built = _build_direct_mall_search_url(shop_name, "테스트 상품 128GB", "https://ppomppu.co.kr/1", "뽐뿌")
        assert is_search_or_community_url(built) is True, f"폴백 URL 미탐지: {shop_name} -> {built}"

    # 3. SQLite 19자 CURRENT_TIMESTAMP 문자열과 26자 마이크로초 문자열 혼재 시 limit=1 커서 무한루프 방지
    db = SessionCls()
    try:
        distinct_items = ["모니터알파", "키보드베타", "헤드셋감마"]
        for i, item_name in enumerate(distinct_items, start=1):
            db.execute(
                text(
                    "INSERT INTO deals (source_community_id, title, post_link, ecommerce_link, price, category, indexed_at, is_closed, deal_type, has_options, is_super_hotdeal) "
                    "VALUES (1, :title, :plink, :elink, '10000', '가전제품', '2026-11-19 18:14:02', 0, '일반', 0, 0)"
                ),
                {
                    "title": f"[커서19] {item_name}",
                    "plink": f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=910{i}",
                    "elink": f"https://www.coupang.com/vp/products/910{i}",
                },
            )
        db.execute(
            text(
                "INSERT INTO deals (source_community_id, title, post_link, ecommerce_link, price, category, indexed_at, is_closed, deal_type, has_options, is_super_hotdeal) "
                "VALUES (1, '[커서26] 마우스델타', 'https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=9104', 'https://www.coupang.com/vp/products/9104', '10000', '가전제품', '2026-11-19 18:14:02.500000', 0, '일반', 0, 0)"
            )
        )
        db.commit()
    finally:
        db.close()

    seen_ts_ids = []
    cur_ts, cur_id = None, None
    for step in range(10):
        params = {"limit": 1}
        if cur_ts is not None:
            params["cursor_indexed_at"] = cur_ts
            params["cursor_id"] = cur_id
        page = client.get("/api/community/hot-deals", params=params).json()
        assert len(page["deals"]) == 1
        seen_ts_ids.append(page["deals"][0]["id"])
        if not page["has_more"]:
            break
        cur_ts = page["next_cursor"]["indexed_at"]
        cur_id = page["next_cursor"]["id"]
    assert len(seen_ts_ids) == 4 and len(set(seen_ts_ids)) == 4

    # 4. DSU 페이지 경계 가로지름 중복 차단 (120건 중 idx 5 & 55 동일 클러스터 -> limit=49 완주시 119건)
    db = SessionCls()
    try:
        db.query(models.Deal).delete()
        db.commit()
        base_dt = datetime(2026, 11, 19, 12, 0, 0)
        bulk = []
        for idx in range(120):
            ts = base_dt - timedelta(minutes=idx)
            if idx in (5, 55):
                bulk.append(
                    models.Deal(
                        source_community_id=1 if idx == 5 else 3,
                        title="[쿠팡] 경계클러스터 노트북 CLUSC (990,000원)",
                        post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=555{idx}",
                        ecommerce_link="https://www.coupang.com/vp/products/99999955",
                        price="990000",
                        base_product_name="경계클러스터노트북",
                        model_code="CLUSC",
                        indexed_at=ts,
                    )
                )
            else:
                bulk.append(
                    models.Deal(
                        source_community_id=1,
                        title=f"[몰{idx}] 고유경계상품_{idx:03d} ({10000 + idx}원)",
                        post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=777{idx}",
                        ecommerce_link=f"https://www.coupang.com/vp/products/777{idx}",
                        price=str(10000 + idx),
                        base_product_name=f"고유경계상품_{idx:03d}",
                        model_code=f"UNQ{idx:03d}",
                        indexed_at=ts,
                    )
                )
        db.add_all(bulk)
        db.commit()
        cluster_pair = {bulk[5].id, bulk[55].id}
    finally:
        db.close()

    community_router.invalidate_feed_api_cache()
    all_ids = []
    cur_ts, cur_id = None, None
    for _ in range(10):
        params = {"limit": 49}
        if cur_ts is not None:
            params["cursor_indexed_at"] = cur_ts
            params["cursor_id"] = cur_id
        page = client.get("/api/community/hot-deals", params=params).json()
        all_ids.extend(d["id"] for d in page["deals"])
        if not page["has_more"]:
            break
        cur_ts = page["next_cursor"]["indexed_at"]
        cur_id = page["next_cursor"]["id"]

    assert len(all_ids) == 119 and len(set(all_ids)) == 119
    assert len(set(all_ids) & cluster_pair) == 1


def test_iter2_minor_dsu_transitive_model_code_and_digest_failure_and_dnd_merge(unit_v11b_env, monkeypatch):
    """[Iter2 Minor] build_dsu 전이 model_code 차단, 아침 요약 send_each 예외 시 재시도 보존, 비활성 기기 DND 덮어쓰기 방지"""
    SessionCls = unit_v11b_env.SessionCls
    base_dt = datetime(2026, 11, 19, 12, 0, 0)

    # 1. build_dsu 전이 병합 차단: None -> AAA111 -> BBB222
    d1 = models.Deal(id=11, source_community_id=1, title="[쿠팡] 갤럭시북 프로 16인치", price="1500000", model_code=None, indexed_at=base_dt, ecommerce_link="https://www.coupang.com/vp/products/1111")
    d2 = models.Deal(id=12, source_community_id=2, title="[쿠팡] 갤럭시북 프로 16인치", price="1500000", model_code="AAA111", indexed_at=base_dt, ecommerce_link="https://www.coupang.com/vp/products/2222")
    d3 = models.Deal(id=13, source_community_id=3, title="[쿠팡] 갤럭시북 프로 16인치", price="1500000", model_code="BBB222", indexed_at=base_dt, ecommerce_link="https://www.coupang.com/vp/products/3333")
    dsu = community_router.build_dsu([d1, d2, d3])
    assert dsu.find(11) == dsu.find(12)
    assert dsu.find(13) != dsu.find(11)

    # 2. _merge_device_data: 비활성 기기(dnd_enabled=False)가 활성 기기(dnd_enabled=True)의 시간대 덮어쓰지 않음
    db = SessionCls()
    try:
        tgt = models.DeviceToken(device_uuid="tgt-active-dnd", dnd_enabled=True, dnd_start_time="22:00", dnd_end_time="08:00")
        src = models.DeviceToken(device_uuid="src-inactive-dnd", dnd_enabled=False, dnd_start_time="23:30", dnd_end_time="06:30")
        db.add_all([tgt, src])
        db.commit()
        auth_router._merge_device_data(db, "src-inactive-dnd", "tgt-active-dnd")
        db.commit()
        db.refresh(tgt)
        assert tgt.dnd_enabled is True
        assert tgt.dnd_start_time == "22:00" and tgt.dnd_end_time == "08:00"
    finally:
        db.close()

    # 3. dnd_morning_digest_job: send_each 예외 시 digest_sent_at 미기록 -> 다음 실행에서 정상 재발송
    db = SessionCls()
    try:
        db.add(models.DeviceToken(device_uuid="digest-retry-dev", fcm_token="fcm-retry-1", is_active=True, dnd_enabled=True, dnd_start_time="22:00", dnd_end_time="08:00"))
        db.add(models.NotificationAlert(user_id="digest-retry-dev", title="재시도알림", keyword="모니터", deal_url="https://example.com", dnd_suppressed=True))
        db.commit()
    finally:
        db.close()

    def _failing_send_each(chunk):
        raise RuntimeError("transient FCM error")

    monkeypatch.setattr(push_worker.messaging, "send_each", _failing_send_each)
    morning_kst = datetime(2026, 11, 20, 8, 0, 0, tzinfo=KST)
    s_fail = push_worker.dnd_morning_digest_job(session_factory=SessionCls, now_kst=morning_kst)
    assert s_fail["fcm_sent"] == 0 and s_fail["digests_sent"] == 0

    db = SessionCls()
    try:
        al = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == "digest-retry-dev").first()
        assert al.digest_sent_at is None
    finally:
        db.close()

    monkeypatch.setattr(
        push_worker.messaging,
        "send_each",
        lambda chunk: SimpleNamespace(success_count=len(chunk), failure_count=0),
    )
    s_ok = push_worker.dnd_morning_digest_job(session_factory=SessionCls, now_kst=morning_kst + timedelta(minutes=5))
    assert s_ok["fcm_sent"] == 1 and s_ok["digests_sent"] == 1
    db = SessionCls()
    try:
        al = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == "digest-retry-dev").first()
        assert al.digest_sent_at is not None
    finally:
        db.close()

    # 4. WebPush 단독 실패(False) 시 digest_sent_at 미기록 및 일일 레이트리밋 미소모
    from backend.services.notification_service import NotificationService
    monkeypatch.setattr(NotificationService, "send_web_push_notification", lambda *a, **kw: False)
    db = SessionCls()
    try:
        wp_dev = models.DeviceToken(
            device_uuid="wp-fail-dev",
            web_push_subscription='{"endpoint":"https://push.example/fail","keys":{"p256dh":"a","auth":"b"}}',
            is_active=True,
            dnd_enabled=True,
            dnd_start_time="22:00",
            dnd_end_time="08:00",
        )
        db.add(wp_dev)
        db.flush()
        wp_dev_id = wp_dev.id
        db.add(models.PushKeyword(device_token_id=wp_dev_id, keyword="웹푸시실패", is_active=True))
        db.add(models.NotificationAlert(user_id="wp-fail-dev", title="웹푸시다이제스트", keyword="테스트", deal_url="https://example.com", dnd_suppressed=True))
        wp_deal = models.Deal(
            source_community_id=1,
            title="[쿠팡] 웹푸시실패 테스트상품 (10,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=989898",
            ecommerce_link="https://www.coupang.com/vp/products/989898",
            price="10000",
        )
        db.add(wp_deal)
        db.commit()
        wp_deal_id = wp_deal.id
    finally:
        db.close()

    wp_dig = push_worker.dnd_morning_digest_job(session_factory=SessionCls, now_kst=morning_kst)
    assert wp_dig["web_push_sent"] == 0 and wp_dig["digests_sent"] == 0
    push_worker.background_trigger_keyword_alarms(wp_deal_id, session_factory=SessionCls, now_kst=datetime(2026, 11, 19, 14, 0, 0, tzinfo=KST))
    assert push_worker._is_push_rate_limited(wp_dev_id, "2026-11-19") is False
    assert push_worker._push_rate_limit.get(wp_dev_id) is None


def test_iter3_security_octal_int_cgnat_ssrf_and_mixed_xss(unit_v11b_env, monkeypatch):
    """[Iter3 보안] 선행 0 단일 8진수 정수 IPv4, RFC 6598 CGNAT(100.64.0.0/10) SSRF 및 URL↔HTML 교차 인코딩 XSS 차단 회귀"""
    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls
    monkeypatch.setattr(
        socket,
        "getaddrinfo",
        lambda host, port, *a, **kw: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))],
    )

    called_urls: list[str] = []

    async def _spy_proxy_get(url, **kwargs):
        called_urls.append(url)
        return httpx.Response(200, content=VALID_PNG, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _spy_proxy_get)

    # 1. 선행 0 단일 8진수 정수 IPv4 및 CGNAT 대역(100.64.0.0/10) 차단 검증
    ssrf_urls = [
        "http://01200000001/a.png",
        "http://01200000002/a.png",
        "http://01237777777/a.png",
        "http://100.64.0.1/a.png",
        "http://[::ffff:100.64.0.1]/a.png",
    ]
    for bad_u in ssrf_urls:
        assert is_safe_external_url(bad_u) is False, f"is_safe_external_url 미차단: {bad_u}"
        res = client.get("/api/proxy-image", params={"url": bad_u})
        assert res.status_code == 404, f"proxy_image 미차단: {bad_u}"
        assert res.headers.get("cache-control") == "no-store"

    # 차단 대상 URL들에 대해 proxy_client.get 호출이 0건이어야 함
    assert called_urls == []

    # 2. 정상 공인 IPv4/IPv6 및 도메인 URL은 오탐 차단 없이 통과
    safe_urls = [
        "https://8.8.8.8/a.png",
        "https://1.1.1.1/a.png",
        "https://93.184.216.34/a.png",
        "https://[2606:4700:4700::1111]/a.png",
        "https://cdn.example.com/a.jpg",
    ]
    for ok_u in safe_urls:
        assert is_safe_external_url(ok_u) is True, f"정상 공인 URL 오탐 차단: {ok_u}"

    ok_proxy = client.get("/api/proxy-image", params={"url": "https://cdn.example.com/a.jpg"})
    assert ok_proxy.status_code == 200
    assert called_urls == ["https://cdn.example.com/a.jpg"]

    # 3. URL 인코딩 + HTML 엔티티 복합 인코딩 및 5중 URL 인코딩 XSS 차단 + 상세 API body_images 검증
    mixed_xss_payload = (
        '<img src="http://01200000001/a.png">'
        '<img src="http://01200000002/a.png">'
        '<img src="http://01237777777/a.png">'
        '<img src="http://100.64.0.1/a.png">'
        '<img src="https://cdn.example.com/a.jpg?q=%26%23x3c%3Bscript%26%23x3e%3B">'
        '<img src="https://cdn.example.com/a.jpg?q=%252525253Cscript%252525253E">'
        '<img src="https://cdn.example.com/a.jpg?q=%26%23x25%3B3Cscript%26%23x25%3B3E">'
        '<img src="https://8.8.8.8/safe.png">'
        '<img src="https://[2606:4700:4700::1111]/safe6.png">'
        '<img src="https://cdn.example.com/a.jpg">'
    )
    expected_safe_imgs = [
        "https://8.8.8.8/safe.png",
        "https://[2606:4700:4700::1111]/safe6.png",
        "https://cdn.example.com/a.jpg",
    ]
    assert community_router._extract_body_images(mixed_xss_payload) == expected_safe_imgs

    db = SessionCls()
    try:
        deal = models.Deal(
            source_community_id=1,
            title="[쿠팡] Iter3 SSRF/XSS 회귀 테스트 상품 (19,900원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=777333",
            ecommerce_link="https://www.coupang.com/vp/products/777333",
            price="19900",
            content_html=mixed_xss_payload,
        )
        db.add(deal)
        db.commit()
        db.refresh(deal)
        deal_id = deal.id
    finally:
        db.close()

    det_res = client.get(f"/api/community/deals/{deal_id}")
    assert det_res.status_code == 200
    assert det_res.json()["body_images"] == expected_safe_imgs
    assert det_res.json()["bodyImages"] == expected_safe_imgs


def test_iter4_security_ipv6_idna_and_body_image_false_positives(unit_v11b_env, monkeypatch):
    """[Iter4 보안·정확도] IPv6 6to4/Teredo/Site-Local/zone/전각 IPv4 차단 + 정상 IDN·%20·레거시엔티티·on* 쿼리 오탐 해소 + 7~15중 XSS 차단"""
    from backend.core.url_utils import is_public_ip
    import ipaddress

    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls
    monkeypatch.setattr(
        socket,
        "getaddrinfo",
        lambda host, port, *a, **kw: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))],
    )

    called_urls: list[str] = []

    async def _spy_proxy_get(url, **kwargs):
        called_urls.append(url)
        return httpx.Response(200, content=VALID_PNG, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _spy_proxy_get)

    # 1. is_public_ip IPv6 6to4 / Teredo / Site-Local 차단 및 정상 공인 IPv6 통과
    for bad_ip in ("2002:7f00:0001::", "2002:a9fe:a9fe::", "2002:6440:0001::", "2001::1", "fec0::1"):
        assert is_public_ip(ipaddress.ip_address(bad_ip)) is False, f"is_public_ip 미차단: {bad_ip}"
    for ok_ip in ("2606:4700:4700::1111", "2001:4860:4860::8888", "8.8.8.8"):
        assert is_public_ip(ipaddress.ip_address(ok_ip)) is True, f"is_public_ip 오탐: {ok_ip}"

    # 2. IPv6 터널/Site-Local/zone ID 및 전각·아랍숫자 IPv4 호스트 차단 (proxy_image 포함)
    ssrf_urls = [
        "http://[2002:7f00:0001::]/a.png",
        "http://[2002:a9fe:a9fe::]/a.png",
        "http://[2002:6440:0001::]/a.png",
        "http://[2001::1]/a.png",
        "http://[fec0::1]/a.png",
        "https://[2606:4700:4700::1111%eth0]/a.png",
        "https://[2606:4700:4700::1111%25eth0]/a.png",
        "http://[::1%eth0]/a.png",
        "http://127.0.0.1%00/a.png",
        "http://+2130706433/a.png",
        "http://١٢٧.٠.٠.١/a.png",
        "http://１２７.０.０.１/a.png",
        "http://１６９.２５４.１６９.２５４/a.png",
        "http://１２７。０。０。１/a.png",
        "http://０１７７.０.０.１/a.png",
    ]
    for bad_u in ssrf_urls:
        assert is_safe_external_url(bad_u) is False, f"is_safe_external_url 미차단: {bad_u}"
        res = client.get("/api/proxy-image", params={"url": bad_u})
        assert res.status_code == 404, f"proxy_image 미차단: {bad_u}"
        assert res.headers.get("cache-control") == "no-store"

    assert called_urls == []

    # 3. 정상 IDN 도메인 오탐 없이 통과
    assert is_safe_external_url("https://한국어.kr/a.jpg") is True
    assert community_router._normalize_and_validate_body_image("https://한국어.kr/a.jpg") == "https://한국어.kr/a.jpg"

    # 4. _normalize_and_validate_body_image 정상 URL 오탐·변조 해소 검증
    preserved_cases = [
        (
            "https://shop-phinf.pstatic.net/20261119_123/product%20image.jpg?type=w860",
            "https://shop-phinf.pstatic.net/20261119_123/product%20image.jpg?type=w860",
        ),
        (
            "https://cdn.example.com/my%20product.jpg",
            "https://cdn.example.com/my%20product.jpg",
        ),
        (
            "https://cdn.example.com/p.jpg?q=a%20b",
            "https://cdn.example.com/p.jpg?q=a%20b",
        ),
        (
            "https://cdn.example.com/p.jpg?q=a%2520b",
            "https://cdn.example.com/p.jpg?q=a%2520b",
        ),
        (
            "https://gdimg.gmarket.co.kr/123456789/still/600?ver=1&reg=kr",
            "https://gdimg.gmarket.co.kr/123456789/still/600?ver=1&reg=kr",
        ),
        (
            "https://cdn.example.com/p.jpg?a=1&copy=1",
            "https://cdn.example.com/p.jpg?a=1&copy=1",
        ),
        (
            "https://cdn.example.com/p.jpg?a=1&para=1",
            "https://cdn.example.com/p.jpg?a=1&para=1",
        ),
        (
            "https://cdn.example.com/p.jpg?a=1&not=1",
            "https://cdn.example.com/p.jpg?a=1&not=1",
        ),
        (
            "https://cdn.example.com/p.jpg?a=1&lt=2",
            "https://cdn.example.com/p.jpg?a=1&lt=2",
        ),
        (
            "https://cdn.example.com/p.jpg?a=1&gt=2",
            "https://cdn.example.com/p.jpg?a=1&gt=2",
        ),
        (
            "https://cdn.example.com/p.jpg?a=1&quot=1",
            "https://cdn.example.com/p.jpg?a=1&quot=1",
        ),
        (
            "https://cdn.example.com/p.jpg?w=800&amp;copy=1",
            "https://cdn.example.com/p.jpg?w=800&copy=1",
        ),
        (
            "https://cdn.example.com/p.jpg?a=1&amp;lt=2",
            "https://cdn.example.com/p.jpg?a=1&lt=2",
        ),
        (
            "https://cdn.example.com/img.png?a=1&online=1",
            "https://cdn.example.com/img.png?a=1&online=1",
        ),
        (
            "https://cdn.example.com/p.jpg?only=1",
            "https://cdn.example.com/p.jpg?only=1",
        ),
        (
            "https://cdn.example.com/p.jpg?once=1&ongoing=1",
            "https://cdn.example.com/p.jpg?once=1&ongoing=1",
        ),
    ]
    for raw_u, expected_u in preserved_cases:
        assert community_router._normalize_and_validate_body_image(raw_u) == expected_u, f"정상 URL 오탐/변조: {raw_u}"

    # 5. 7중~15중 URL 인코딩, 제어문자, 혼합 XSS 및 이벤트 핸들러 차단 유지 검증
    url_15x = "https://cdn.example.com/a.jpg?q=" + ("%25" * 14) + "3Cscript"
    blocked_cases = [
        "https://cdn.example.com/a.jpg?q=%2525252525253Cscript%2525252525253E",
        url_15x,
        "https://cdn.example.com/a.jpg?q=%26%23x3c%3Bscript%26%23x3e%3B",
        "https://cdn.example.com/a.jpg?q=&#x3cscript",
        "https://cdn.example.com/a.jpg?q=&lt;script&gt;",
        "https://cdn.example.com/a.jpg?q=a%0Ab",
        "https://cdn.example.com/a.jpg?q=a%0Db",
        "https://cdn.example.com/a.jpg?q=a%00b",
        "https://cdn.example.com/a.jpg?q=javascript%3Aalert(1)",
        "http://%31%32%37.0.0.1/a.png",
        "https://cdn.example.com/a.jpg?onerror=alert(1)",
        "https://cdn.example.com/a.jpg?onpointerover=alert(1)",
        "https://cdn.example.com/a.jpg?onanimationstart=alert(1)",
        "https://cdn.example.com/a.jpg?onwheel=alert(1)",
        "https://cdn.example.com/a.jpg?q=1%20oncustom=alert(1)",
        "https://cdn.example.com/raw space.jpg",
    ] + ssrf_urls
    for bad_img in blocked_cases:
        assert community_router._normalize_and_validate_body_image(bad_img) is None, f"위험 URL 미차단: {bad_img}"

    # 6. _extract_body_images 및 상세 API 종단 검증
    html_mix = (
        '<img src="https://gdimg.gmarket.co.kr/123456789/still/600?ver=1&reg=kr">'
        '<img src="https://shop-phinf.pstatic.net/20261119_123/product%20image.jpg?type=w860">'
        '<img src="https://cdn.example.com/img.png?a=1&online=1&amp;lt=2">'
        '<img src="http://[2002:7f00:0001::]/a.png">'
        '<img src="http://１２７.０.０.１/a.png">'
        '<img src="https://cdn.example.com/a.jpg?q=%2525252525253Cscript%2525252525253E">'
        '<img src="https://cdn.example.com/a.jpg?onwheel=alert(1)">'
    )
    expected_extracted = [
        "https://gdimg.gmarket.co.kr/123456789/still/600?ver=1&reg=kr",
        "https://shop-phinf.pstatic.net/20261119_123/product%20image.jpg?type=w860",
        "https://cdn.example.com/img.png?a=1&online=1&lt=2",
    ]
    assert community_router._extract_body_images(html_mix) == expected_extracted

    db = SessionCls()
    try:
        deal = models.Deal(
            source_community_id=1,
            title="[지마켓] Iter4 정상쿼리 및 보안 회귀 테스트 상품 (29,900원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=888444",
            ecommerce_link="https://www.gmarket.co.kr/n/sale?goodscode=123456789",
            price="29900",
            content_html=html_mix,
        )
        db.add(deal)
        db.commit()
        db.refresh(deal)
        deal_id = deal.id
    finally:
        db.close()

    det = client.get(f"/api/community/deals/{deal_id}")
    assert det.status_code == 200
    assert det.json()["body_images"] == expected_extracted


def test_multi_category_and_multi_platform_cross_filter_and_cache_normalization(unit_v11b_env):
    """[과제 B] GET /api/community/hot-deals 다중 커뮤니티 + 다중 카테고리 교차 필터 및 순서 무관 캐시 정규화 검증"""
    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls
    base_dt = datetime(2026, 11, 20, 9, 0, 0)

    db = SessionCls()
    try:
        db.add_all([
            models.Community(id=6, name="quasarzone", display_name="퀘이사존", base_url="https://quasarzone.com"),
            models.Community(id=7, name="fmkorea", display_name="펨코", base_url="https://www.fmkorea.com"),
        ])
        db.flush()

        # 매칭 대상 3건 + 배제 대조군 4건
        d_match_ppomppu_app = models.Deal(
            source_community_id=1,
            title="[하이마트] LG 휘센 제습기 20L (399,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=880001",
            ecommerce_link="https://www.e-himart.co.kr/app/goods/goodsDetail?goodsNo=880001",
            price="399000",
            category="가전제품",
            base_product_name="LG휘센제습기880001",
            model_code="DQ2008801",
            indexed_at=base_dt - timedelta(minutes=1),
        )
        d_match_fm_pc = models.Deal(
            source_community_id=7,
            title="[컴퓨존] 삼성 990 PRO NVMe SSD 2TB (219,000원)",
            post_link="https://www.fmkorea.com/880002",
            ecommerce_link="https://www.compuzone.co.kr/product/product_detail.htm?ProductNo=880002",
            price="219000",
            category="PC용품",
            base_product_name="삼성990PRO880002",
            model_code="MZ880002",
            indexed_at=base_dt - timedelta(minutes=2),
        )
        d_match_merged_fm_pc_label = models.Deal(
            source_community_id=3,
            merged_communities="7::https://www.fmkorea.com/880003",
            title="[지마켓] AMD 라이젠 7800X3D 정품 (459,000원)",
            post_link="https://bbs.ruliweb.com/market/board/1020/read/880003",
            ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=880003",
            price="459000",
            category="PC/하드웨어",
            base_product_name="라이젠7800X3D880003",
            model_code="RYZ880003",
            indexed_at=base_dt - timedelta(minutes=3),
        )
        d_excl_wrong_cat = models.Deal(
            source_community_id=1,
            title="[쿠팡] 제주 삼다수 2L 12병 (12,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=880004",
            ecommerce_link="https://www.coupang.com/vp/products/880004",
            price="12000",
            category="음식",
            base_product_name="삼다수880004",
            model_code="WTR880004",
            indexed_at=base_dt - timedelta(minutes=4),
        )
        d_excl_wrong_comm_app = models.Deal(
            source_community_id=3,
            title="[쿠팡] 필립스 전기면도기 (89,000원)",
            post_link="https://bbs.ruliweb.com/market/board/1020/read/880005",
            ecommerce_link="https://www.coupang.com/vp/products/880005",
            price="89000",
            category="가전제품",
            base_product_name="필립스면도기880005",
            model_code="SHV880005",
            indexed_at=base_dt - timedelta(minutes=5),
        )
        d_excl_wrong_comm_pc = models.Deal(
            source_community_id=6,
            title="[알리] 퀘이사존 전용 그래픽카드 RTX 5070 (799,000원)",
            post_link="https://quasarzone.com/bbs/qb_saleinfo/views/880006",
            ecommerce_link="https://ko.aliexpress.com/item/880006.html",
            price="799000",
            category="PC/하드웨어",
            base_product_name="RTX5070_880006",
            model_code="GPU880006",
            indexed_at=base_dt - timedelta(minutes=6),
        )
        d_hot_ppomppu = models.Deal(
            source_community_id=1,
            title="[네이버] 인기 핫딜모음 특가 상품 (25,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=880007",
            ecommerce_link="https://smartstore.naver.com/shop/products/880007",
            price="25000",
            category="기타",
            honey_score=130,
            base_product_name="인기핫딜모음880007",
            model_code="HOT880007",
            indexed_at=base_dt - timedelta(minutes=7),
        )
        db.add_all([
            d_match_ppomppu_app,
            d_match_fm_pc,
            d_match_merged_fm_pc_label,
            d_excl_wrong_cat,
            d_excl_wrong_comm_app,
            d_excl_wrong_comm_pc,
            d_hot_ppomppu,
        ])
        db.commit()
        expected_ids = {
            d_match_ppomppu_app.id,
            d_match_fm_pc.id,
            d_match_merged_fm_pc_label.id,
        }
        hot_ppomppu_id = d_hot_ppomppu.id
    finally:
        db.close()

    community_router.invalidate_feed_api_cache()
    res1 = client.get(
        "/api/community/hot-deals",
        params={"platform": "뽐뿌,펨코", "category": "가전제품,PC/하드웨어"},
    )
    assert res1.status_code == 200
    got_ids_1 = {d["id"] for d in res1.json()["deals"]}
    assert got_ids_1 == expected_ids
    cache_size_after_first = len(community_router._HOT_DEALS_API_CACHE)

    # 순서가 뒤집힌 동일 필터 조합 호출 시 캐시 키 정규화로 캐시 히트 및 동일 결과 반환
    res2 = client.get(
        "/api/community/hot-deals",
        params={"platform": "펨코,뽐뿌", "category": "PC/하드웨어,가전제품"},
    )
    assert res2.status_code == 200
    assert {d["id"] for d in res2.json()["deals"]} == expected_ids
    assert len(community_router._HOT_DEALS_API_CACHE) == cache_size_after_first

    # 핫딜모음 단일 및 다중 카테고리 결합 호환성 검증
    res_hot_combo = client.get(
        "/api/community/hot-deals",
        params={"platform": "뽐뿌", "category": "가전제품,핫딜모음"},
    )
    assert res_hot_combo.status_code == 200
    assert {d["id"] for d in res_hot_combo.json()["deals"]} == {d_match_ppomppu_app.id, hot_ppomppu_id}


def test_db_pool_config_and_sqlite_indexed_at_normalization_migration(monkeypatch, tmp_path):
    """[과제 C] QueuePool 풀 한도(30/60/15) 및 SQLite 19자 indexed_at 정규화·인덱스 생성 멱등성 검증"""
    from sqlalchemy.pool import QueuePool, StaticPool

    db_file = tmp_path / "pool_and_mig.db"
    monkeypatch.setenv("DATABASE_URL", f"sqlite:///{db_file}")
    mgr = db_session_mod.DatabaseManager()
    try:
        assert isinstance(mgr.engine.pool, QueuePool)
        assert mgr.engine.pool.size() == 30
        assert mgr.engine.pool._max_overflow == 60
        assert mgr.engine.pool._timeout == 15

        with mgr.engine.begin() as conn:
            conn.execute(text(
                "CREATE TABLE deals ("
                "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                "indexed_at TIMESTAMP, "
                "is_closed BOOLEAN NOT NULL DEFAULT 0)"
            ))
            conn.execute(text("INSERT INTO deals (indexed_at, is_closed) VALUES ('2026-11-19 18:14:02', 0)"))
            conn.execute(text("INSERT INTO deals (indexed_at, is_closed) VALUES ('2026-11-19 18:14:03.123456', 0)"))

        mgr.ensure_schema_migrations()
        mgr.ensure_schema_migrations()  # 2회 반복 호출 시에도 멱등 유지

        with mgr.engine.connect() as conn:
            rows = conn.execute(text("SELECT id, indexed_at FROM deals ORDER BY id ASC")).fetchall()
            idx_list = [r[1] for r in conn.execute(text("PRAGMA index_list('deals')")).fetchall()]

        assert rows[0][1] == "2026-11-19 18:14:02.000000"
        assert rows[1][1] == "2026-11-19 18:14:03.123456"
        assert "idx_deals_closed_indexed_id" in idx_list
    finally:
        mgr.close_all_connections()

    monkeypatch.setenv("DATABASE_URL", "sqlite:///:memory:")
    mem_mgr = db_session_mod.DatabaseManager()
    try:
        assert isinstance(mem_mgr.engine.pool, StaticPool)
    finally:
        mem_mgr.close_all_connections()


def test_proxy_image_large_png_auto_webp_and_width_buckets(unit_v11b_env, monkeypatch):
    """대용량 PNG(50KB+) 자동 WebP 변환, ?w=400/?w=1200 버킷 양자화, 외부 1회 수집, 소형 PNG 원본 유지 검증"""
    import io
    from PIL import Image

    monkeypatch.setattr(
        socket,
        "getaddrinfo",
        lambda host, port, *a, **kw: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))],
    )

    # 1600x900 고엔트로피 PNG 생성 (압축 후 > 50KB 보장)
    noise_bytes = os.urandom(1600 * 900 * 3)
    src_img = Image.frombytes("RGB", (1600, 900), noise_bytes)
    buf = io.BytesIO()
    src_img.save(buf, format="PNG")
    big_png_bytes = buf.getvalue()
    assert len(big_png_bytes) > 50 * 1024

    fetch_urls: list[str] = []

    async def _fake_get(url, **kwargs):
        fetch_urls.append(url)
        if "tiny" in url:
            return httpx.Response(200, content=VALID_PNG, request=httpx.Request("GET", url))
        return httpx.Response(200, content=big_png_bytes, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _fake_get)
    client = unit_v11b_env.client

    target_big_url = "https://cdn.example.com/large-photo.png"
    url_hash = hashlib.md5(target_big_url.encode("utf-8")).hexdigest()

    # 1. w 미지정 + 50KB+ PNG -> 자동 WebP(_auto800.webp.cache, 최대 너비 800) 변환
    r_auto = client.get("/api/proxy-image", params={"url": target_big_url})
    assert r_auto.status_code == 200
    assert r_auto.headers["content-type"].startswith("image/webp")
    assert r_auto.headers.get("cache-control") == "public, max-age=604800, immutable"
    assert len(fetch_urls) == 1
    with Image.open(io.BytesIO(r_auto.content)) as out_auto:
        assert out_auto.format == "WEBP"
        assert out_auto.size == (800, 450)

    # 2. ?w=350 -> 400 버킷 양자화 (_w400.webp.cache), 외부 fetch 추가 0회
    r_w400 = client.get("/api/proxy-image", params={"url": target_big_url, "w": 350})
    assert r_w400.status_code == 200
    assert r_w400.headers["content-type"].startswith("image/webp")
    assert len(fetch_urls) == 1
    with Image.open(io.BytesIO(r_w400.content)) as out_400:
        assert out_400.format == "WEBP"
        assert out_400.size == (400, 225)

    # 3. ?width=1000 -> 1200 버킷 양자화 (_w1200.webp.cache), /api/community/proxy-image 경유 및 v 파라미터 호환
    r_w1200 = client.get("/api/community/proxy-image", params={"url": target_big_url, "width": 1000, "v": "2"})
    assert r_w1200.status_code == 200
    assert r_w1200.headers["content-type"].startswith("image/webp")
    assert len(fetch_urls) == 1
    with Image.open(io.BytesIO(r_w1200.content)) as out_1200:
        assert out_1200.format == "WEBP"
        assert out_1200.size == (1200, 675)

    # 디스크 캐시 파일명 검증 (.cache 접미사로 cache_purger.py 정제 호환)
    assert (unit_v11b_env.tmp_path / f"{url_hash}.cache").exists()
    assert (unit_v11b_env.tmp_path / f"{url_hash}_auto800.webp.cache").exists()
    assert (unit_v11b_env.tmp_path / f"{url_hash}_w400.webp.cache").exists()
    assert (unit_v11b_env.tmp_path / f"{url_hash}_w1200.webp.cache").exists()

    # 4. 인메모리 핫 LRU 재요청 히트 + 소형 PNG(<=40KB) 원본 바이트/MIME 보존 계약 검증
    r_w400_lru = client.get("/api/proxy-image", params={"url": target_big_url, "w": 400, "v": "hot"})
    assert r_w400_lru.status_code == 200 and r_w400_lru.content == r_w400.content
    assert len(fetch_urls) == 1

    r_tiny = client.get("/api/proxy-image", params={"url": "https://cdn.example.com/tiny.png"})
    assert r_tiny.status_code == 200
    assert r_tiny.headers["content-type"].startswith("image/png")
    assert r_tiny.content == VALID_PNG

    # 5. 파이썬 직접 함수 호출 시 Query() 객체 충돌 없이 정상 동작 검증
    direct_resp = asyncio.run(main_mod.proxy_image("https://cdn.example.com/tiny.png"))
    assert direct_resp.status_code == 200 and direct_resp.body == VALID_PNG


def test_deal_detail_1rtt_initial_history_triggers_and_max_id_cache(unit_v11b_env):
    """상세 API initial_history 1-RTT 동봉, SQLite indexed_at 트리거 자동 정규화, _DEAL_HISTORY_CACHE 및 max_id 캐시 최신화 검증"""
    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls
    base_utc = datetime(2026, 11, 19, 10, 0, 0)

    db = SessionCls()
    try:
        # 1. SQLite after_create 트리거: raw SQL 19자 및 ISO T/Z INSERT 자동 26자 정규화 검증
        db.execute(
            text(
                "INSERT INTO deals (source_community_id, title, post_link, ecommerce_link, price, category, indexed_at, is_closed, deal_type, has_options, is_super_hotdeal) "
                "VALUES (1, '[트리거테스트] 19자 타임스탬프 상품', 'https://www.ppomppu.co.kr/trg1', 'https://www.coupang.com/vp/products/11111', '29000', '가전제품', '2026-11-19 08:10:00', 0, '일반', 0, 0)"
            )
        )
        db.execute(
            text(
                "INSERT INTO deals (source_community_id, title, post_link, ecommerce_link, price, category, indexed_at, is_closed, deal_type, has_options, is_super_hotdeal) "
                "VALUES (1, '[트리거테스트] ISO TZ 타임스탬프 상품', 'https://www.ppomppu.co.kr/trg2', 'https://www.coupang.com/vp/products/22222', '31000', '가전제품', '2026-11-19T08:11:00Z', 0, '일반', 0, 0)"
            )
        )
        db.commit()

        raw_ts_rows = db.execute(
            text("SELECT post_link, indexed_at FROM deals WHERE post_link LIKE 'https://www.ppomppu.co.kr/trg%' ORDER BY id ASC")
        ).fetchall()
        assert raw_ts_rows[0][1] == "2026-11-19 08:10:00.000000"
        assert raw_ts_rows[1][1] == "2026-11-19 08:11:00.000000"

        # 2. 7d 2포인트 딜 + 3m 폴백(7d 1포인트, 3m 2포인트) 딜 시드
        d_7d = models.Deal(
            source_community_id=1,
            title="[쿠팡] 로지텍 MX Master 3S 무선 마우스 (99,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=99001",
            ecommerce_link="https://www.coupang.com/vp/products/99001",
            price="99000",
            currency="KRW",
            indexed_at=base_utc,
        )
        d_3m = models.Deal(
            source_community_id=1,
            title="[네이버] 브라운 전기면도기 시리즈9 (249,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=99002",
            ecommerce_link="https://smartstore.naver.com/shop/products/99002",
            price="249000",
            currency="KRW",
            indexed_at=base_utc,
        )
        db.add_all([d_7d, d_3m])
        db.flush()
        d7_id, d3m_id = d_7d.id, d_3m.id

        db.add_all([
            models.PriceHistory(deal_id=d7_id, price="119000", checked_at=base_utc - timedelta(days=2)),
            models.PriceHistory(deal_id=d7_id, price="99000", checked_at=base_utc),
            models.PriceHistory(deal_id=d3m_id, price="279000", checked_at=base_utc - timedelta(days=25)),
            models.PriceHistory(deal_id=d3m_id, price="249000", checked_at=base_utc),
        ])
        db.commit()
    finally:
        db.close()

    # 3. GET /api/community/deals/{id} 1-RTT initial_history / initialHistory 및 기간 폴백 검증
    det_7d = client.get(f"/api/community/deals/{d7_id}").json()
    assert det_7d["initial_history_period"] == "7d"
    assert det_7d["initialHistoryPeriod"] == "7d"
    assert isinstance(det_7d["initial_history"], list)
    assert det_7d["initial_history"] == det_7d["initialHistory"]
    assert [p["price"] for p in det_7d["initial_history"]] == [119000, 99000]
    assert det_7d["history_count"] == 2
    assert det_7d["initial_history"] == client.get(f"/api/community/deals/{d7_id}/history", params={"period": "7d_auto"}).json()

    det_3m = client.get(f"/api/community/deals/{d3m_id}").json()
    assert det_3m["initial_history_period"] == "3m"
    assert det_3m["initialHistoryPeriod"] == "3m"
    assert [p["price"] for p in det_3m["initial_history"]] == [279000, 249000]
    assert det_3m["history_count"] == 2
    assert len(community_router._DEAL_HISTORY_CACHE) >= 2

    community_router.invalidate_feed_api_cache()
    assert len(community_router._DEAL_HISTORY_CACHE) == 0

    # 4. _HOT_DEALS_API_CACHE max_id 비교를 통한 별도 프로세스 적재 즉시 반영 검증
    feed_before = client.get("/api/community/hot-deals", params={"limit": 20}).json()
    ids_before = {d["id"] for d in feed_before["deals"]}

    db = SessionCls()
    try:
        new_deal = models.Deal(
            source_community_id=1,
            title="[지마켓] 신규 적재 삼성 SSD 2TB (189,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=99003",
            ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=99003",
            price="189000",
            currency="KRW",
            indexed_at=base_utc + timedelta(minutes=10),
        )
        db.add(new_deal)
        db.commit()
        new_id = new_deal.id
    finally:
        db.close()

    # invalidate_feed_api_cache() 미호출 상태에서도 max(id) 변화로 즉시 최신 딜 노출
    assert new_id not in ids_before
    feed_after = client.get("/api/community/hot-deals", params={"limit": 20}).json()
    assert new_id in {d["id"] for d in feed_after["deals"]}


def test_build_dsu_inverted_index_fast_and_consistent():
    """역인덱스 적용 build_dsu가 400건 이상에서도 0.25초 이내 완료되며 동일 클러스터 병합 정합성을 유지한다."""
    import time
    from types import SimpleNamespace

    now = datetime(2026, 11, 20, 2, 0, 0)
    deals = [
        SimpleNamespace(
            id=1,
            title="[쿠팡] 삼성전자 오디세이 OLED G8 32인치 모니터",
            base_product_name="삼성전자 오디세이 OLED G8 32인치 모니터",
            model_code="LS32DG800",
            ecommerce_link="https://www.coupang.com/vp/products/100001",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1",
            price="990000",
            currency="KRW",
            indexed_at=now,
        ),
        SimpleNamespace(
            id=2,
            title="[G마켓] 삼성 오디세이 OLED G8 32인치 모니터 특가",
            base_product_name="삼성전자 오디세이 OLED G8 32인치 모니터",
            model_code="LS32DG800",
            ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=200002",
            post_link="https://www.fmkorea.com/123456",
            price="985000",
            currency="KRW",
            indexed_at=now - timedelta(minutes=15),
        ),
    ]
    for i in range(3, 450):
        deals.append(
            SimpleNamespace(
                id=i,
                title=f"[몰{i}] 유니크알파브랜드{i} 제품명{i}모델X{i} ({10000 + i}원)",
                base_product_name=f"유니크알파브랜드{i} 제품명{i}모델X{i}",
                model_code=f"UNIQMDL{i:04d}",
                ecommerce_link=f"https://www.coupang.com/vp/products/{900000 + i}",
                post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no={i}",
                price=str(10000 + i),
                currency="KRW",
                indexed_at=now - timedelta(minutes=i),
            )
        )

    t0 = time.perf_counter()
    dsu = community_router.build_dsu(deals)
    elapsed = time.perf_counter() - t0
    assert elapsed < 0.25, f"build_dsu 역인덱스 지연: {elapsed:.4f}s"
    assert dsu.find(1) == dsu.find(2)
    assert dsu.find(1) != dsu.find(3)
    assert dsu.find(3) != dsu.find(400)


@pytest.mark.asyncio
async def test_cloud_feeder_fresh_cache_skips_remote_preload(tmp_path, monkeypatch):
    """신선한 로컬 feeder_seen_cache.json(>=20건, <30분) 존재 시 _preload_existing_urls가 원격 API를 0회 호출한다."""
    import json
    import backend.scheduler.cloud_feeder as cf

    cache_path = tmp_path / "feeder_seen_cache.json"
    seed = {
        f"뽐뿌::https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no={1000 + i}": {
            "title": f"캐시딜 {i}",
            "price": 10000 + i,
            "currency": "KRW",
            "is_closed": False,
        }
        for i in range(25)
    }
    cache_path.write_text(json.dumps(seed, ensure_ascii=False), encoding="utf-8")

    monkeypatch.setattr(cf, "_CACHE_FILE", str(cache_path))
    cf._SEEN_DETAIL_CACHE.clear()

    called_urls: list[str] = []

    class FakeClient:
        async def get(self, url, *args, **kwargs):
            called_urls.append(url)
            return httpx.Response(200, json={"deals": []}, request=httpx.Request("GET", url))

    await cf._preload_existing_urls(FakeClient())
    assert len(cf._SEEN_DETAIL_CACHE) == 25
    assert called_urls == []

    # 30분 초과(stale) 캐시일 때도 offset=0&limit=60 경량 조회만 수행
    old_ts = os.path.getmtime(cache_path) - 3600
    os.utime(cache_path, (old_ts, old_ts))
    cf._SEEN_DETAIL_CACHE.clear()
    await cf._preload_existing_urls(FakeClient())
    assert len(called_urls) == 10
    assert all("limit=60&offset=0" in u for u in called_urls)


def test_hot_deals_popular_and_price_asc_zero_n_plus_1_content_html_loads(unit_v11b_env):
    """sort=popular / price_asc 시 defer(content_html) 우회 N+1 SQL 0건 및 2단계 배치 하이드레이션·인기검색어 캐시 검증"""
    from sqlalchemy import event

    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls
    engine = unit_v11b_env.engine
    base_utc = datetime(2026, 11, 20, 2, 0, 0)

    db = SessionCls()
    try:
        db.query(models.Deal).delete()
        bulk = []
        for idx in range(45):
            bulk.append(
                models.Deal(
                    source_community_id=1,
                    title=f"[지마켓] 고유제품_{idx:03d}호 ({15000 + idx * 100}원/무료)",
                    post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=880{idx:03d}",
                    ecommerce_link=f"https://item.gmarket.co.kr/Item?goodscode=880{idx:03d}",
                    price=str(15000 + idx * 100),
                    currency="KRW",
                    base_product_name=f"고유제품_{idx:03d}",
                    model_code=f"ZNQ{idx:03d}",
                    honey_score=100 - idx,
                    like_count=10,
                    content_html=f"<p>상세 본문 {idx} (무료배송 삼성카드)</p><img src=\"https://cdn.example.com/item_{idx}.jpg\" />",
                    indexed_at=base_utc - timedelta(minutes=idx),
                )
            )
        db.add_all(bulk)
        db.commit()
    finally:
        db.close()

    community_router.invalidate_feed_api_cache()
    sql_log: list[str] = []

    def _capture_sql(conn, cursor, statement, parameters, context, executemany):
        sql_log.append(statement)

    event.listen(engine, "before_cursor_execute", _capture_sql)
    try:
        for sort_mode in ("popular", "price_asc"):
            sql_log.clear()
            res = client.get("/api/community/hot-deals", params={"sort": sort_mode, "limit": 20, "offset": 0})
            assert res.status_code == 200
            items = res.json()["deals"]
            assert len(items) == 20
            assert items[0]["content_html"]
            assert items[0]["body_images"]
            assert items[0]["ecommerce_url"].startswith("https://")
            assert items[0]["sources"] and items[0]["sources"][0]["ecommerce_url"].startswith("https://")

            single_lazy_loads = [
                q for q in sql_log
                if "deals.content_html" in q and "WHERE deals.id = ?" in q
            ]
            assert single_lazy_loads == [], f"{sort_mode} N+1 지연 로딩 SQL 감지: {len(single_lazy_loads)}건"

        # popular-keywords 캐시 히트(SQL 쿼리 감소) 검증
        sql_log.clear()
        k1 = client.get("/api/community/popular-keywords", params={"limit": 10})
        first_queries = len(sql_log)
        sql_log.clear()
        k2 = client.get("/api/community/popular-keywords", params={"limit": 10})
        assert k1.status_code == 200 and k2.status_code == 200
        assert k1.json()["keywords"] == k2.json()["keywords"]
        assert len(sql_log) < first_queries
    finally:
        event.remove(engine, "before_cursor_execute", _capture_sql)


def test_ppomppu_rss_no_shipping_text_uses_title_hint():
    """뽐뿌 RSS 파싱 시 제목에 배송비 키워드가 없어도 title_hint 키워드 인자로 TypeError 없이 정상 파싱되어야 한다."""
    from backend.scrapers.ppomppu_scraper import PpomppuScraper

    rss_xml = """<?xml version="1.0" encoding="utf-8"?>
    <rss version="2.0"><channel>
      <item>
        <title>[쿠팡] 삼성 갤럭시 핏3 (69,000원/와우무료)</title>
        <link>https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&amp;no=999001</link>
        <description>와우회원 무료배송 안내 본문입니다.</description>
      </item>
      <item>
        <title>[지마켓] 로지텍 무선 마우스 (29,000원)</title>
        <link>https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&amp;no=999002</link>
        <description>상세 본문 테스트입니다.</description>
      </item>
    </channel></rss>"""

    scraper = PpomppuScraper(community_id=1)
    parsed = asyncio.run(scraper.parse_list(rss_xml))
    assert len(parsed) == 2
    assert parsed[0]["title"] == "[쿠팡] 삼성 갤럭시 핏3 (69,000원/와우무료)"
    assert parsed[1]["title"] == "[지마켓] 로지텍 무선 마우스 (29,000원)"
    assert parsed[1]["shipping_fee"] == ""


def test_ingest_batch_upsert_preserves_cache_and_insert_invalidates_feed_keeps_ecom(unit_v11b_env, monkeypatch):
    """ingest-batch 순수 Upsert 시 피드/제휴URL 캐시를 모두 보존하고, 신규 Insert 시에만 피드 캐시를 무효화(제휴URL 캐시 유지)한다."""
    from backend.services.aggregator_service import AggregatorService

    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls
    monkeypatch.setenv("FEEDER_API_TOKEN", "test-feeder-secret")
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)

    async def _noop_url_passthrough(self, url, *args, **kwargs):
        return url

    monkeypatch.setattr(AggregatorService, "_unwrap_outbound_url_async", _noop_url_passthrough)
    monkeypatch.setattr(AggregatorService, "_monetize_outbound_url_async", _noop_url_passthrough)

    db = SessionCls()
    try:
        db.query(models.Deal).delete()
        existing = models.Deal(
            source_community_id=1,
            title="[지마켓] 기존 캐시 보존 테스트 핫딜 (19,900원/무배)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=777001",
            ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=777001",
            price="19900",
            currency="KRW",
            shipping_fee="무료배송",
            is_closed=False,
            is_super_hotdeal=False,
            honey_score=20,
            like_count=1,
            view_count=500,
            content_html="<p>이미 수집되어 데이터베이스에 저장된 충분히 긴 상세 본문 텍스트입니다. 스텁 방어 조건을 통과합니다.</p>",
            indexed_at=datetime(2026, 11, 20, 2, 0, 0),
        )
        db.add(existing)
        db.commit()
    finally:
        db.close()

    community_router.invalidate_feed_api_cache()
    warm_resp = client.get("/api/community/hot-deals", params={"limit": 20, "offset": 0})
    assert warm_resp.status_code == 200
    assert len(community_router._HOT_DEALS_API_CACHE) >= 1
    feed_cache_before = len(community_router._HOT_DEALS_API_CACHE)

    ecom_sentinel_key = ("https://sentinel.example.com/item/1", False, community_router._ecom_url_cache_env_sig())
    with community_router._CLEAN_ECOM_URL_LOCK:
        community_router._CLEAN_ECOM_URL_CACHE[ecom_sentinel_key] = "https://sentinel.example.com/item/1"

    headers = {"X-Feeder-Token": "test-feeder-secret"}
    upsert_payload = {
        "community_name": "ppomppu",
        "display_name": "뽐뿌",
        "base_url": "https://www.ppomppu.co.kr",
        "deals": [
            {
                "title": "[지마켓] 기존 캐시 보존 테스트 핫딜 (19,900원/무배)",
                "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=777001",
                "ecommerce_link": "https://item.gmarket.co.kr/Item?goodscode=777001",
                "price": 19900,
                "currency": "KRW",
                "shipping_fee": "무료배송",
                "content_html": "<p>이미 수집되어 데이터베이스에 저장된 충분히 긴 상세 본문 텍스트입니다. 스텁 방어 조건을 통과합니다.</p>",
                "view_count": 700,
                "like_count": 1,
                "is_closed": False,
                "is_super_hotdeal": False,
            }
        ],
    }
    res_upsert = client.post("/api/community/ingest-batch", json=upsert_payload, headers=headers)
    assert res_upsert.status_code == 200
    assert res_upsert.json()["processed"] == 1
    assert len(community_router._HOT_DEALS_API_CACHE) == feed_cache_before
    assert ecom_sentinel_key in community_router._CLEAN_ECOM_URL_CACHE

    insert_payload = {
        "community_name": "ppomppu",
        "display_name": "뽐뿌",
        "base_url": "https://www.ppomppu.co.kr",
        "deals": [
            {
                "title": "[지마켓] 신규 인입 핫딜 테스트 모니터 (199,000원/무배)",
                "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=777002",
                "ecommerce_link": "https://item.gmarket.co.kr/Item?goodscode=777002",
                "price": 199000,
                "currency": "KRW",
                "shipping_fee": "무료배송",
                "content_html": "<p>신규 핫딜 상세 본문 텍스트가 충분히 길게 들어갑니다. 무결점 캐시 무효화 검증용 본문입니다. 199,000원 무료배송.</p>",
                "view_count": 1500,
                "like_count": 3,
                "is_closed": False,
                "is_super_hotdeal": False,
            }
        ],
    }
    res_insert = client.post("/api/community/ingest-batch", json=insert_payload, headers=headers)
    assert res_insert.status_code == 200
    assert res_insert.json()["processed"] == 1
    assert len(community_router._HOT_DEALS_API_CACHE) == 0
    assert ecom_sentinel_key in community_router._CLEAN_ECOM_URL_CACHE


def test_get_deal_detail_cache_hit_and_mutation_eviction(unit_v11b_env):
    """단건 상세 API(/deals/{id}) 캐시 히트 시 무거운 조회를 생략하고 투표·댓글·품절제보 시 즉시 캐시가 무효화된다."""
    from sqlalchemy import event

    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls
    engine = unit_v11b_env.engine

    db = SessionCls()
    try:
        db.query(models.Deal).delete()
        deal = models.Deal(
            source_community_id=1,
            title="[쿠팡] 상세 캐시 검증용 헤드셋 (49,000원/무료)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=777900",
            ecommerce_link="https://www.coupang.com/vp/products/777900",
            price="49000",
            currency="KRW",
            shipping_fee="무료배송",
            content_html="<p>상세 캐시 히트 및 변이 무효화 테스트 본문입니다.</p>",
            indexed_at=datetime(2026, 11, 20, 2, 0, 0),
        )
        db.add(deal)
        db.commit()
        db.refresh(deal)
        deal_id = deal.id
    finally:
        db.close()

    community_router.invalidate_feed_api_cache()
    sql_log: list[str] = []

    def _capture_sql(conn, cursor, statement, parameters, context, executemany):
        sql_log.append(statement)

    event.listen(engine, "before_cursor_execute", _capture_sql)
    try:
        sql_log.clear()
        r1 = client.get(f"/api/community/deals/{deal_id}")
        assert r1.status_code == 200
        assert r1.json()["buy_count"] == 0
        miss_queries = len(sql_log)
        assert miss_queries >= 4

        sql_log.clear()
        r2 = client.get(f"/api/community/deals/{deal_id}")
        assert r2.status_code == 200
        assert r2.json()["id"] == deal_id
        assert len(sql_log) == 1

        dev_uuid = "device-detail-cache-tester-1"
        tok = create_device_token(dev_uuid)
        sql_log.clear()
        r3 = client.get(f"/api/community/deals/{deal_id}", params={"user_id": dev_uuid})
        assert r3.status_code == 200
        assert r3.json()["user_vote"] is None
        assert len(sql_log) == 2

        # 투표 시 즉시 캐시 무효화
        rv = client.post(
            f"/api/community/deals/{deal_id}/votes",
            json={"user_id": dev_uuid, "vote_type": "buy"},
            headers={"X-Device-Token": tok},
        )
        assert rv.status_code == 200
        r4 = client.get(f"/api/community/deals/{deal_id}", params={"user_id": dev_uuid})
        assert r4.status_code == 200
        assert r4.json()["buy_count"] == 1
        assert r4.json()["user_vote"] == "buy"

        # 댓글 작성 및 삭제 시 즉시 캐시 무효화
        rc = client.post(
            f"/api/community/deals/{deal_id}/comments",
            json={"user_id": dev_uuid, "nickname": "테스터", "content": "캐시 무효화 확인 댓글"},
            headers={"X-Device-Token": tok},
        )
        assert rc.status_code == 200
        comment_id = rc.json()["id"]
        r5 = client.get(f"/api/community/deals/{deal_id}")
        assert r5.status_code == 200
        assert r5.json()["comment_count"] == 1

        rd = client.delete(
            f"/api/community/deals/{deal_id}/comments/{comment_id}",
            headers={"X-Device-Token": tok},
        )
        assert rd.status_code == 200
        r6 = client.get(f"/api/community/deals/{deal_id}")
        assert r6.status_code == 200
        assert r6.json()["comment_count"] == 0

        # 품절 제보 시 즉시 캐시 무효화
        rr = client.post(
            f"/api/community/deals/{deal_id}/report-closed",
            headers={"X-Device-Token": tok},
        )
        assert rr.status_code == 200
        r7 = client.get(f"/api/community/deals/{deal_id}")
        assert r7.status_code == 200
        assert r7.json()["report_count"] == 1
    finally:
        event.remove(engine, "before_cursor_execute", _capture_sql)


def test_category_misclassification_guards_and_low_price_won_decimal_recovery(unit_v11b_env):
    """오븐구이/워치페이스 카테고리 오분류 차단 및 94원 미만 폴백 차단·해외몰 ￦101.94 USD 복원 검증"""
    from backend.services.normalizer.regex_normalizer import RegexNormalizer

    norm = RegexNormalizer()
    assert norm._map_category("하림 오븐구이 치킨 3팩") == "음식"
    assert norm._map_category("LG 광파오븐 32L 스팀오븐") == "가전제품"
    assert norm._map_category("Blue Linear 워치페이스 외 1종") == "SW/게임"

    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls
    base_utc = datetime(2026, 11, 20, 2, 0, 0)

    db = SessionCls()
    try:
        db.query(models.Deal).delete()
        d_chicken = models.Deal(
            source_community_id=1,
            title="[롯데온] 하림 오븐구이 치킨 3팩 (15,140원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=888001",
            ecommerce_link="https://www.lotteon.com/p/product/888001",
            price="15140",
            currency="KRW",
            category="가전제품",
            content_html="<p>맛있는 오븐구이 치킨 3팩</p>",
            indexed_at=base_utc,
        )
        d_watchface = models.Deal(
            source_community_id=1,
            title="[구글] Blue Linear 워치페이스 외 1종",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=888002",
            ecommerce_link="https://play.google.com/store/apps/888002",
            price="0",
            currency="KRW",
            category="PC용품",
            content_html="<p>갤럭시 워치페이스 무료 배포</p>",
            indexed_at=base_utc - timedelta(minutes=1),
        )
        d_padding_94 = models.Deal(
            source_community_id=1,
            title="[롯데온] 공용 2L 방풍 롱패딩 L몬트벤치 다운자켓3",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=888003",
            ecommerce_link="https://www.lotteon.com/p/product/888003",
            price="94",
            currency="KRW",
            category="패션",
            content_html="<p>토스 적용가 94 롱패딩 90 95 사이즈</p>",
            indexed_at=base_utc - timedelta(minutes=2),
        )
        d_padding_dot_94520 = models.Deal(
            source_community_id=1,
            title="[지마켓] 테스트 울 플리스 하프코트 XZ990 (94.520원/무배)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=888005",
            ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=888005",
            price="0",
            currency="KRW",
            category="패션",
            content_html="<p>울 플리스 하프코트 94.520원 무료배송</p>",
            indexed_at=base_utc - timedelta(minutes=4),
        )
        d_speaker_101 = models.Deal(
            source_community_id=1,
            title="[알리] 에디파이어 M90 블루투스 6.0 스피커_역대가",
            post_link="https://quasarzone.com/bbs/qb_saleinfo/views/888004",
            ecommerce_link="https://ko.aliexpress.com/item/1005012525933520.html",
            price="101",
            currency="KRW",
            category="PC용품",
            content_html="<p>[알리] 에디파이어 M90 블루투스 6.0 스피커 💰 ￦ 101.94 (KRW) 무료배송</p>",
            indexed_at=base_utc - timedelta(minutes=3),
        )
        db.add_all([d_chicken, d_watchface, d_padding_94, d_padding_dot_94520, d_speaker_101])
        db.commit()
        for obj in (d_chicken, d_watchface, d_padding_94, d_padding_dot_94520, d_speaker_101):
            db.refresh(obj)
        id_pad, id_pad_dot, id_spk = d_padding_94.id, d_padding_dot_94520.id, d_speaker_101.id
    finally:
        db.close()

    community_router.invalidate_feed_api_cache()
    res_app = client.get("/api/community/hot-deals", params={"category": "가전제품"})
    assert res_app.status_code == 200
    assert all("오븐구이" not in d["title"] for d in res_app.json()["deals"])

    res_pc = client.get("/api/community/hot-deals", params={"category": "PC용품"})
    assert res_pc.status_code == 200
    assert all("워치페이스" not in d["title"] for d in res_pc.json()["deals"])

    det_pad = client.get(f"/api/community/deals/{id_pad}").json()
    assert det_pad["price"] == 0

    det_pad_dot = client.get(f"/api/community/deals/{id_pad_dot}").json()
    assert det_pad_dot["price"] == 94520
    assert det_pad_dot["currency"] == "KRW"

    det_spk = client.get(f"/api/community/deals/{id_spk}").json()
    assert det_spk["price"] == 10194
    assert det_spk["currency"] == "USD"
    assert det_spk["sources"][0]["currency"] == "USD"


def test_get_hot_deals_latest_cursor_cache_hit_and_invalidation(unit_v11b_env):
    """sort=latest 커서 2페이지 조회 시 메인 피드 캐시 슬라이스 및 _HOT_DEALS_CURSOR_CACHE가 적중해 중복·누락 없이 고속 응답한다."""
    from sqlalchemy import event

    client = unit_v11b_env.client
    SessionCls = unit_v11b_env.SessionCls
    engine = unit_v11b_env.engine
    base_utc = datetime(2026, 11, 20, 4, 0, 0)

    db = SessionCls()
    try:
        db.query(models.Deal).delete()
        for idx in range(1, 9):
            db.add(
                models.Deal(
                    source_community_id=1,
                    title=f"[쿠팡] 커서 캐시 검증 상품 {idx}번 ({10000 + idx * 100}원/무배)",
                    post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no={991000 + idx}",
                    ecommerce_link=f"https://www.coupang.com/vp/products/{991000 + idx}",
                    price=str(10000 + idx * 100),
                    currency="KRW",
                    shipping_fee="무료배송",
                    content_html=f"<p>커서 캐시 검증용 본문 {idx}번입니다.</p>",
                    indexed_at=base_utc + timedelta(minutes=idx),
                )
            )
        db.commit()
    finally:
        db.close()

    community_router.invalidate_feed_api_cache()
    sql_log: list[str] = []

    def _capture_sql(conn, cursor, statement, parameters, context, executemany):
        sql_log.append(statement)

    event.listen(engine, "before_cursor_execute", _capture_sql)
    try:
        # 1페이지 로드 -> 메인 캐시(_HOT_DEALS_API_CACHE) 및 cursors 맵 적재
        p1 = client.get("/api/community/hot-deals", params={"limit": 3, "offset": 0, "sort": "latest"})
        assert p1.status_code == 200
        p1_json = p1.json()
        assert len(p1_json["deals"]) == 3
        assert p1_json["has_more"] is True
        cursor_1 = p1_json["next_cursor"]
        assert cursor_1 is not None
        p1_ids = {d["id"] for d in p1_json["deals"]}

        # 2페이지 로드 -> 메인 캐시 슬라이스 히트(전체 deals 테이블 스캔 없이 MAX(id) + content_html 1회)
        sql_log.clear()
        p2 = client.get(
            "/api/community/hot-deals",
            params={
                "limit": 3,
                "offset": 3,
                "sort": "latest",
                "cursor_indexed_at": cursor_1["indexed_at"],
                "cursor_id": cursor_1["id"],
            },
        )
        assert p2.status_code == 200
        p2_json = p2.json()
        assert len(p2_json["deals"]) == 3
        p2_ids = {d["id"] for d in p2_json["deals"]}
        assert p1_ids.isdisjoint(p2_ids)
        assert len(sql_log) <= 2

        # 메인 캐시만 비워 2차 _HOT_DEALS_CURSOR_CACHE 미스→적재→히트 경로 검증
        with community_router._HOT_DEALS_CACHE_LOCK:
            community_router._HOT_DEALS_API_CACHE.clear()
        p2_miss = client.get(
            "/api/community/hot-deals",
            params={
                "limit": 3,
                "offset": 3,
                "sort": "latest",
                "cursor_indexed_at": cursor_1["indexed_at"],
                "cursor_id": cursor_1["id"],
            },
        )
        assert p2_miss.status_code == 200
        assert {d["id"] for d in p2_miss.json()["deals"]} == p2_ids
        assert len(community_router._HOT_DEALS_CURSOR_CACHE) == 1

        sql_log.clear()
        p2_hit = client.get(
            "/api/community/hot-deals",
            params={
                "limit": 3,
                "offset": 3,
                "sort": "latest",
                "cursor_indexed_at": cursor_1["indexed_at"],
                "cursor_id": cursor_1["id"],
            },
        )
        assert p2_hit.status_code == 200
        assert {d["id"] for d in p2_hit.json()["deals"]} == p2_ids
        assert len(sql_log) == 1  # MAX(id) 단건 확인만 실행

        # 신규 딜 인입 + 무효화 시 커서 캐시도 함께 초기화되어 최신 딜이 즉시 반영됨
        db = SessionCls()
        try:
            db.add(
                models.Deal(
                    source_community_id=1,
                    title="[쿠팡] 커서 캐시 무효화 검증 최신 상품 (25,000원/무배)",
                    post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=991999",
                    ecommerce_link="https://www.coupang.com/vp/products/991999",
                    price="25000",
                    currency="KRW",
                    shipping_fee="무료배송",
                    content_html="<p>신규 딜 인입 시 커서 캐시 초기화 확인</p>",
                    indexed_at=base_utc + timedelta(minutes=30),
                )
            )
            db.commit()
        finally:
            db.close()

        community_router.invalidate_feed_api_cache()
        assert len(community_router._HOT_DEALS_CURSOR_CACHE) == 0
        p1_after = client.get("/api/community/hot-deals", params={"limit": 3, "offset": 0, "sort": "latest"}).json()
        assert "무효화 검증 최신 상품" in p1_after["deals"][0]["title"]
    finally:
        event.remove(engine, "before_cursor_execute", _capture_sql)


def test_regex_normalizer_single_char_boundary_and_pairing_cleanup(unit_v11b_env):
    """1글자 음식 키워드('차','배' 등)의 부분문자열 오분류를 차단하고 만료/사용완료 페어링 코드를 안전 정리한다."""
    from backend.services.normalizer.regex_normalizer import RegexNormalizer
    from backend.routers.auth import cleanup_expired_pairing_codes, reset_rate_limits

    norm = RegexNormalizer()
    assert norm._map_category("스카이 핏 스마트 글래스 S1 블루투스6.0 오디오 안경 오픈이어 블루라이트 UV 차단 IPX4") == "PC제품"
    assert norm._map_category("보성 녹차 500ml 20병") == "음식"
    assert norm._map_category("전통 유자 차 1kg") == "음식"
    assert norm._map_category("고당도 신고 배 5kg 1박스") == "음식"
    assert norm._map_category("하림 오븐구이 치킨 3팩") == "음식"
    assert norm._map_category("하겐다즈 파인트 아이스크림 3개") == "음식"
    assert norm._map_category("오리온 도도한나쵸 사워크림 앤 어니언맛 92g 12개") == "음식"
    assert norm._map_category("크라운산도 딸기크림치즈 323g 3개") == "음식"
    assert norm._map_category("이니스프리 그린티 씨드 히알루론산 크림 50mL 2개") == "화장품"
    assert norm._map_category("[카카오톡선물하기] 백미당 우유/초코 아이스크림 1+1 교환권 (e쿠폰)") == "모바일/기프티콘"
    assert norm._map_category("가정용 철판 아이스크림메이커") == "가전제품"
    assert norm._map_category("트윅스 탑 스니커즈, 21g, 40개") == "음식"
    assert norm._map_category("나이키 에어맥스 런닝화 스니커즈") == "의류"
    assert norm._map_category("사조 로하이 전자레인지 팝콘 달콤한맛 70g x 12입") == "음식"
    assert norm._map_category("네네치킨 네꼬닭 저당소스 순살 닭다리살 30팩 (+전자레인지용 플레이트 증정)") == "음식"
    assert norm._map_category("소문난오부자 재래 도시락김5g 54봉") == "음식"
    assert norm._map_category("코카콜라 190ml 90개+버킷햇+카드지갑 키링") == "음식"
    assert norm._map_category("롯데 칠성 펩시 제로슈거 355ml 48캔+캔쿨러") == "음식"
    assert norm._map_category("농심 신신안너짜 25봉") == "음식"
    assert norm._map_category("[11번가]끼리 크림치즈 디저트 올인원 세트+장바구니") == "음식"
    assert norm._map_category("LG 코드제로 오브제컬렉션 올인원타워 무선청소기") == "가전제품"
    assert norm._map_category("피죤 액츠 파워젤 세탁세제 4.2L 본품+2L 리필 2개") == "생활용품"
    assert norm._map_category("[스팀] 데드스페이스1 리메이크") == "SW/게임"
    assert norm._map_category("[롯데온] 올반키친 인생 왕교자 210g 10봉") == "음식"
    assert norm._map_category("무신사 콜라보 메뉴") == "의류"
    assert norm._map_category("토니모리 패밀리세일 최대 90% 할인") == "화장품"

    import asyncio
    gift_norm = asyncio.run(norm.normalize("파스쿠찌 모바일상품권 3만원", "먹거리"))
    assert gift_norm.category == "모바일/기프티콘"

    SessionCls = unit_v11b_env.SessionCls
    now_naive = datetime.now(timezone.utc).replace(tzinfo=None)
    db = SessionCls()
    try:
        db.query(models.DevicePairingCode).delete()
        db.add_all(
            [
                models.DevicePairingCode(
                    code="111111",
                    source_device_uuid="src-active",
                    expires_at=now_naive + timedelta(minutes=8),
                    used_at=None,
                ),
                models.DevicePairingCode(
                    code="222222",
                    source_device_uuid="src-used",
                    expires_at=now_naive + timedelta(minutes=8),
                    used_at=now_naive - timedelta(minutes=1),
                ),
                models.DevicePairingCode(
                    code="333333",
                    source_device_uuid="src-expired",
                    expires_at=now_naive - timedelta(minutes=25),
                    used_at=None,
                ),
                models.DevicePairingCode(
                    code="444444",
                    source_device_uuid="src-recent-expired",
                    expires_at=now_naive - timedelta(minutes=3),
                    used_at=None,
                ),
            ]
        )
        db.commit()

        reset_rate_limits()
        deleted = cleanup_expired_pairing_codes(db, force=False)
        assert deleted == 2
        remaining = {r.code for r in db.query(models.DevicePairingCode).all()}
        assert remaining == {"111111", "444444"}
    finally:
        db.close()
