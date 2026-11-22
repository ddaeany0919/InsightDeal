# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_p0_device_auth_and_push.py

M1-B 핵심 계약 및 파이프라인 검증 테스트:
- POST /api/push/device (uuid4 생성, HMAC device_token 발급, ENV=production 503, 기등록 401/403/200, fcm_token="" 보존)
- PATCH /api/push/dnd (X-Device-Token 검증, fcm_token 불변, 형식 오류 400)
- GET /api/push-public-key (503 / 200)
- push.py / wishlist.py / users.py X-Device-Token IDOR 차단(401/403) & trailing slash 리다이렉트 방지 & UTC Z 직렬화
- GET /api/users/notifications UTC epoch / ISO-8601 정합성 (9시간 시차 버그 제거)
- aggregator_service 신규 Insert + 동일 URL 가격 하락(0 < price < old_price) -> commit() 후 push_worker 연동
- push_worker Wishlist 목표가 푸시 + DND drop_with_history(NotificationAlert 기록, FCM/Web Push 0건) + Web Push 모킹 발송 + VAPID 미설정 차단
"""

import asyncio
from datetime import datetime, timedelta, timezone
import json
import os
import sys
import uuid
from types import SimpleNamespace
from unittest.mock import MagicMock

from fastapi.testclient import TestClient
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from backend.core.deal_normalizer import extract_numeric_price, is_collection_dummy_price
from backend.core.device_auth import create_device_token
from backend.database.models import Base as CommunityBase, Community, Deal, DeviceToken, NotificationAlert, PushKeyword
from backend.database.session import get_db, get_db_session
from backend.main import app
from backend.models.wishlist_models import Base as WishlistBase, Wishlist
from backend.services.aggregator_service import AggregatorService
from backend.services.notification_service import NotificationService
from backend.services import push_worker


@pytest.fixture()
def isolated_env():
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    CommunityBase.metadata.create_all(bind=engine)
    WishlistBase.metadata.create_all(bind=engine)
    SessionCls = sessionmaker(autocommit=False, autoflush=False, expire_on_commit=False, bind=engine)

    seed_db = SessionCls()
    seed_db.add(Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr"))
    seed_db.commit()
    seed_db.close()

    def override_get_db():
        db = SessionCls()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_db_session] = override_get_db

    with TestClient(app) as client:
        yield client, SessionCls, engine

    app.dependency_overrides.clear()
    engine.dispose()


def test_register_device_uuid_generation_token_issue_and_fcm_guard(isolated_env, monkeypatch):
    client, SessionCls, _ = isolated_env
    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "unit-test-device-secret")

    # 1. placeholder("default") 전송 시 서버가 uuid4 생성 및 device_token 발급
    res = client.post(
        "/api/push/device",
        json={"device_uuid": "default", "fcm_token": "real-fcm-token-123"},
    )
    assert res.status_code == 200
    body = res.json()
    assert body["status"] == "ok"
    assert body["success"] is True
    assert body["device_uuid"] != "default"
    uuid.UUID(body["device_uuid"], version=4)
    assert body["device_token"].startswith("v1.")
    assert body["registered_at"].endswith("Z")

    dev_uuid = body["device_uuid"]
    dev_token = body["device_token"]

    # 2. 기등록 UUID 갱신 시 X-Device-Token 누락 -> 401
    res_no_tok = client.post("/api/push/device", json={"device_uuid": dev_uuid, "dnd_enabled": True})
    assert res_no_tok.status_code == 401

    # 3. 타인 토큰으로 기등록 UUID 갱신 시도 -> 403
    other_token = create_device_token(str(uuid.uuid4()))
    res_other = client.post(
        "/api/push/device",
        json={"device_uuid": dev_uuid, "dnd_enabled": True},
        headers={"X-Device-Token": other_token},
    )
    assert res_other.status_code == 403

    # 4. 본인 토큰 + fcm_token="" 전달 시 기존 fcm_token 보존
    res_update = client.post(
        "/api/push/device",
        json={"device_uuid": dev_uuid, "fcm_token": "", "dnd_enabled": True},
        headers={"X-Device-Token": dev_token},
    )
    assert res_update.status_code == 200

    db = SessionCls()
    try:
        dev = db.query(DeviceToken).filter(DeviceToken.device_uuid == dev_uuid).first()
        assert dev is not None
        assert dev.fcm_token == "real-fcm-token-123"
        assert dev.dnd_enabled is True
    finally:
        db.close()

    # 5. ENV=production에서 SERVER_DEVICE_SECRET 미설정 시 503 Fail-Closed
    monkeypatch.setenv("ENV", "production")
    monkeypatch.delenv("SERVER_DEVICE_SECRET", raising=False)
    res_prod_fail = client.post("/api/push/device", json={"fcm_token": "abc"})
    assert res_prod_fail.status_code == 503


def test_patch_dnd_and_push_public_key(isolated_env, monkeypatch):
    client, SessionCls, _ = isolated_env
    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "unit-test-device-secret")

    # 1. VAPID_PUBLIC_KEY 미설정 시 503
    monkeypatch.delenv("VAPID_PUBLIC_KEY", raising=False)
    assert client.get("/api/push-public-key").status_code == 503

    # 설정 시 200 + 이중 키 반환
    monkeypatch.setenv("VAPID_PUBLIC_KEY", "BTestPublicKeyValue1234567890")
    res_key = client.get("/api/push-public-key")
    assert res_key.status_code == 200
    assert res_key.json() == {
        "public_key": "BTestPublicKeyValue1234567890",
        "publicKey": "BTestPublicKeyValue1234567890",
    }

    # 2. 기기 등록 후 PATCH /api/push/dnd 검증
    reg = client.post("/api/push/device", json={"fcm_token": "keep-this-fcm"}).json()
    dev_uuid = reg["device_uuid"]
    dev_token = reg["device_token"]

    # 무인증 401
    assert client.patch("/api/push/dnd", json={"device_uuid": dev_uuid, "dnd_enabled": True}).status_code == 401

    # 타인 토큰 403
    other_token = create_device_token("other-device-uuid")
    assert (
        client.patch(
            "/api/push/dnd",
            json={"device_uuid": dev_uuid, "dnd_enabled": True},
            headers={"X-Device-Token": other_token},
        ).status_code
        == 403
    )

    # 잘못된 시각/JSON 400
    assert (
        client.patch(
            "/api/push/dnd",
            json={"device_uuid": dev_uuid, "dnd_start_time": "25:99"},
            headers={"X-Device-Token": dev_token},
        ).status_code
        == 400
    )
    assert (
        client.patch(
            "/api/push/dnd",
            json={"device_uuid": dev_uuid, "dnd_settings_json": "{broken-json"},
            headers={"X-Device-Token": dev_token},
        ).status_code
        == 400
    )

    # 정상 갱신 + fcm_token 불변 확인
    dnd_json = json.dumps({"mon": {"enabled": True, "start": "13:00", "end": "14:00"}})
    res_dnd = client.patch(
        "/api/push/dnd",
        json={
            "device_uuid": dev_uuid,
            "dnd_enabled": True,
            "dnd_start_time": "22:30",
            "dnd_end_time": "07:30",
            "dnd_settings_json": dnd_json,
            "night_push_consent": True,
        },
        headers={"X-Device-Token": dev_token},
    )
    assert res_dnd.status_code == 200
    assert res_dnd.json()["dnd_enabled"] is True
    assert res_dnd.json()["dnd_start_time"] == "22:30"

    db = SessionCls()
    try:
        dev = db.query(DeviceToken).filter(DeviceToken.device_uuid == dev_uuid).first()
        assert dev.fcm_token == "keep-this-fcm"
        assert dev.dnd_start_time == "22:30"
        assert dev.night_push_consent is True
    finally:
        db.close()


def test_idor_protection_across_push_wishlist_users_and_utc_epoch(isolated_env, monkeypatch):
    client, SessionCls, _ = isolated_env
    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "unit-test-device-secret")

    user_a = client.post("/api/push/device", json={}).json()
    user_b = client.post("/api/push/device", json={}).json()
    uuid_a, tok_a = user_a["device_uuid"], user_a["device_token"]
    uuid_b, tok_b = user_b["device_uuid"], user_b["device_token"]
    headers_a = {"X-Device-Token": tok_a}
    headers_b = {"X-Device-Token": tok_b}

    # --- push.py IDOR ---
    assert client.get(f"/api/push/keywords?device_uuid={uuid_a}").status_code == 401
    assert client.get(f"/api/push/keywords?device_uuid={uuid_a}", headers=headers_b).status_code == 403
    assert client.post("/api/push/keywords", json={"device_uuid": uuid_a, "keyword": "아이패드"}, headers=headers_a).status_code == 200
    assert client.post("/api/push/keywords/toggle", json={"device_uuid": uuid_a, "keyword": "아이패드"}, headers=headers_b).status_code == 403
    assert client.post("/api/push/register-web", json={"endpoint": "https://push.example/1", "keys": {"p256dh": "a", "auth": "b"}, "device_uuid": uuid_a}, headers=headers_b).status_code == 403
    assert client.post("/api/push/register-web", json={"endpoint": "https://push.example/1", "keys": {"p256dh": "a", "auth": "b"}}, headers=headers_a).status_code == 200
    assert client.request("DELETE", "/api/push/register-web", json={"endpoint": "https://push.example/1", "device_uuid": uuid_a}, headers=headers_b).status_code == 403
    assert client.request("DELETE", "/api/push/register-web", json={"endpoint": "https://push.example/1"}, headers=headers_a).status_code == 200

    # --- wishlist.py IDOR + trailing slash 미발생 + UTC Z ---
    assert client.post("/api/wishlist/item-alert", json={"keyword": "에어팟", "target_price": 200000}).status_code == 401
    assert client.post("/api/wishlist/item-alert", json={"keyword": "에어팟", "target_price": 200000, "user_id": "default"}, headers=headers_a).status_code == 403
    wl_res = client.post("/api/wishlist/item-alert", json={"keyword": "에어팟", "target_price": 200000}, headers=headers_a)
    assert wl_res.status_code == 200
    wl_body = wl_res.json()
    wl_id = wl_body["id"]
    assert wl_body["user_id"] == uuid_a
    assert wl_body["created_at"].endswith("Z")

    # trailing slash 없는 GET /api/wishlist 호출 시 리다이렉트(307) 없이 200 반환
    wl_get = client.get("/api/wishlist", headers=headers_a, allow_redirects=False)
    assert wl_get.status_code == 200
    assert len(wl_get.json()) == 1
    assert client.get(f"/api/wishlist?user_id={uuid_a}", headers=headers_b).status_code == 403
    assert client.patch(f"/api/wishlist/{wl_id}", json={"target_price": 180000}, headers=headers_b).status_code == 403
    assert client.delete(f"/api/wishlist/{wl_id}", headers=headers_b).status_code == 403
    assert client.patch(f"/api/wishlist/{wl_id}", json={"target_price": 180000}, headers=headers_a).status_code == 200

    # --- users.py IDOR + UTC epoch 정합성 ---
    assert client.get("/api/users/notifications").status_code == 401
    assert client.get(f"/api/users/notifications?user_id={uuid_a}", headers=headers_b).status_code == 403

    # naive UTC datetime을 DB에 직접 삽입 후 KST 시차(9시간) 오차가 없는지 검증
    naive_utc_dt = datetime(2026, 11, 19, 10, 0, 0)
    expected_epoch_ms = int(datetime(2026, 11, 19, 10, 0, 0, tzinfo=timezone.utc).timestamp() * 1000)
    db = SessionCls()
    try:
        db.add(
            NotificationAlert(
                user_id=uuid_a,
                title="시차 검증 알림",
                keyword="에어팟",
                deal_url="https://example.com/deal",
                received_at=naive_utc_dt,
            )
        )
        db.commit()
    finally:
        db.close()

    notif_res = client.get("/api/users/notifications", headers=headers_a)
    assert notif_res.status_code == 200
    items = notif_res.json()
    assert len(items) == 1
    assert items[0]["receivedAt"] == expected_epoch_ms
    assert items[0]["received_at"] == "2026-11-19T10:00:00Z"
    assert items[0]["deal_url"] == "https://example.com/deal"

    alert_id = int(items[0]["id"])
    assert client.delete(f"/api/users/notifications/{alert_id}", headers=headers_b).status_code == 403
    assert client.delete(f"/api/users/notifications/{alert_id}", headers=headers_a).status_code == 200
    assert client.post(f"/api/users/notifications/clear?user_id={uuid_a}", headers=headers_b).status_code == 403


@pytest.mark.asyncio
async def test_aggregator_to_push_worker_new_insert_and_upsert_price_drop(isolated_env, monkeypatch):
    _, SessionCls, _ = isolated_env
    monkeypatch.setenv("VAPID_PRIVATE_KEY", "test-valid-vapid-private-key")
    push_worker._push_rate_limit.clear()

    # pywebpush 모킹
    webpush_calls = []

    class FakeWebPushException(Exception):
        def __init__(self, status_code=500):
            super().__init__(f"status {status_code}")
            self.response = SimpleNamespace(status_code=status_code)

    def fake_webpush(**kwargs):
        webpush_calls.append(kwargs)

    monkeypatch.setitem(
        sys.modules,
        "pywebpush",
        SimpleNamespace(webpush=fake_webpush, WebPushException=FakeWebPushException),
    )

    # firebase_admin.messaging 모킹
    fcm_batches = []

    def fake_send_each(chunk):
        fcm_batches.append(chunk)
        return SimpleNamespace(success_count=len(chunk), failure_count=0)

    monkeypatch.setattr(push_worker.messaging, "send_each", fake_send_each)

    # 주간 KST 14:00 고정 (야간 제한에 걸리지 않도록)
    daytime_kst = datetime(2026, 11, 19, 14, 0, 0, tzinfo=timezone(timedelta(hours=9)))
    orig_bg = push_worker.background_trigger_keyword_alarms

    def daytime_bg(deal_id, session_factory=None, now_kst=None):
        return orig_bg(deal_id, session_factory=session_factory, now_kst=daytime_kst)

    monkeypatch.setattr(push_worker, "background_trigger_keyword_alarms", daytime_bg)

    # 사용자(키워드 + 위시리스트 + FCM + Web Push) 시드
    db = SessionCls()
    try:
        dev = DeviceToken(
            device_uuid="user-push-e2e",
            fcm_token="fcm-token-e2e",
            web_push_subscription=json.dumps({"endpoint": "https://push.example/e2e", "keys": {"p256dh": "k", "auth": "a"}}),
            is_active=True,
            night_push_consent=True,
            dnd_enabled=False,
        )
        db.add(dev)
        db.commit()
        db.refresh(dev)
        db.add(PushKeyword(device_token_id=dev.id, keyword="삼성SSD <=200000 -중고"))
        db.add(Wishlist(user_id="user-push-e2e", keyword="갤럭시버즈", target_price=160000, is_active=True, alert_enabled=True))
        db.commit()

        svc = AggregatorService(db)
        post_url_1 = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=88001"
        await svc.process_scraped_deal(
            1,
            {
                "title": "[쿠팡] 삼성SSD 990 PRO 2TB (180,000원/무료)",
                "url": post_url_1,
                "price": 180000,
                "content_html": "<p>" + ("상세 본문 테스트입니다. " * 12) + "</p>",
                "ecommerce_link": "https://www.coupang.com/vp/products/88001",
                "shop_name": "쿠팡",
            },
        )
        if getattr(svc, "_last_push_futures", None):
            await asyncio.gather(*svc._last_push_futures)

        alerts_1 = db.query(NotificationAlert).filter(NotificationAlert.user_id == "user-push-e2e").all()
        assert len(alerts_1) == 1
        assert "삼성SSD" in alerts_1[0].title
        assert len(fcm_batches) == 1
        assert len(webpush_calls) == 1

        # 2. 동일 URL Upsert 가격 하락 (200,000 -> 150,000) 시 Wishlist 목표가(160,000) 푸시 발동 검증
        post_url_2 = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=88002"
        await svc.process_scraped_deal(
            1,
            {
                "title": "[11번가] 갤럭시버즈 프로3 특가 (200,000원/무료)",
                "url": post_url_2,
                "price": 200000,
                "content_html": "<p>" + ("상세 본문 테스트입니다. " * 12) + "</p>",
                "ecommerce_link": "https://www.11st.co.kr/products/88002",
                "shop_name": "11번가",
            },
        )
        if getattr(svc, "_last_push_futures", None):
            await asyncio.gather(*svc._last_push_futures)

        fcm_batches.clear()
        webpush_calls.clear()

        # 동일 post_url_2로 가격 하락(200000 -> 150000)
        await svc.process_scraped_deal(
            1,
            {
                "title": "[11번가] 갤럭시버즈 프로3 특가 (150,000원/무료)",
                "url": post_url_2,
                "price": 150000,
                "content_html": "<p>" + ("상세 본문 테스트입니다. " * 12) + "</p>",
                "ecommerce_link": "https://www.11st.co.kr/products/88002",
                "shop_name": "11번가",
            },
        )
        if getattr(svc, "_last_push_futures", None):
            await asyncio.gather(*svc._last_push_futures)

        db.expire_all()
        alerts_2 = db.query(NotificationAlert).filter(NotificationAlert.user_id == "user-push-e2e").all()
        assert any("목표가 160,000원 이하" in a.title and "150,000원" in a.title for a in alerts_2)
        assert len(fcm_batches) == 1
        assert len(webpush_calls) == 1
    finally:
        db.close()


def test_push_worker_dnd_drop_with_history_and_vapid_guard(isolated_env, monkeypatch):
    _, SessionCls, _ = isolated_env
    push_worker._push_rate_limit.clear()

    webpush_calls = []

    class FakeWebPushException(Exception):
        def __init__(self, status_code=410):
            super().__init__(f"status {status_code}")
            self.response = SimpleNamespace(status_code=status_code)

    def fake_webpush(**kwargs):
        webpush_calls.append(kwargs)

    monkeypatch.setitem(
        sys.modules,
        "pywebpush",
        SimpleNamespace(webpush=fake_webpush, WebPushException=FakeWebPushException),
    )

    fcm_batches = []
    monkeypatch.setattr(
        push_worker.messaging,
        "send_each",
        lambda chunk: fcm_batches.append(chunk) or SimpleNamespace(success_count=len(chunk), failure_count=0),
    )

    db = SessionCls()
    try:
        # dnd_settings_json에 오늘(목요일) 키가 빠져 있어도 dnd_start_time~dnd_end_time 폴백으로 차단되어야 함
        dev = DeviceToken(
            device_uuid="dnd-user-1",
            fcm_token="fcm-dnd-1",
            web_push_subscription=json.dumps({"endpoint": "https://push.example/dnd", "keys": {"p256dh": "k", "auth": "a"}}),
            is_active=True,
            night_push_consent=True,
            dnd_enabled=True,
            dnd_start_time="13:00",
            dnd_end_time="15:00",
            dnd_settings_json=json.dumps({"mon": {"enabled": False}}),
        )
        db.add(dev)
        db.commit()
        db.refresh(dev)
        db.add(PushKeyword(device_token_id=dev.id, keyword="로지텍"))
        db.add(Wishlist(user_id="dnd-user-1", keyword="로지텍", target_price=50000, is_active=True, alert_enabled=True))

        deal = Deal(
            source_community_id=1,
            title="[쿠팡] 로지텍 마우스 (39,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=77701",
            ecommerce_link="https://www.coupang.com/vp/products/77701",
            price="39000",
            shop_name="쿠팡",
        )
        db.add(deal)
        db.commit()
        db.refresh(deal)

        monkeypatch.setenv("VAPID_PRIVATE_KEY", "valid-key")
        thursday_1400 = datetime(2026, 11, 19, 14, 0, 0, tzinfo=timezone(timedelta(hours=9)))
        stats = push_worker.background_trigger_keyword_alarms(
            deal.id,
            session_factory=SessionCls,
            now_kst=thursday_1400,
        )

        # drop_with_history: 인앱 NotificationAlert는 2건(키워드+목표가) 모두 적재되나, FCM/Web Push 발송은 0건
        alerts = db.query(NotificationAlert).filter(NotificationAlert.user_id == "dnd-user-1").all()
        assert len(alerts) == 2
        assert stats["dropped_dnd"] == 2
        assert len(fcm_batches) == 0
        assert len(webpush_calls) == 0

        # VAPID_PRIVATE_KEY 미설정 시 send_web_push_notification 차단(False 반환, pywebpush 미호출)
        monkeypatch.delenv("VAPID_PRIVATE_KEY", raising=False)
        ok = NotificationService.send_web_push_notification(
            subscription_json=dev.web_push_subscription,
            keyword="로지텍",
            title="로지텍 마우스",
            price=39000,
            site_name="쿠팡",
            deal_id=deal.id,
            deal_url="https://example.com",
        )
        assert ok is False
        assert len(webpush_calls) == 0

        # 410 Gone WebPushException 발생 시 만료 구독 자동 정리
        monkeypatch.setenv("VAPID_PRIVATE_KEY", "valid-key")

        def raise_410(**kwargs):
            raise FakeWebPushException(410)

        monkeypatch.setitem(
            sys.modules,
            "pywebpush",
            SimpleNamespace(webpush=raise_410, WebPushException=FakeWebPushException),
        )
        ok_410 = NotificationService.send_web_push_notification(
            subscription_json=dev.web_push_subscription,
            keyword="로지텍",
            title="로지텍 마우스",
            price=39000,
            site_name="쿠팡",
            deal_id=deal.id,
            deal_url="https://example.com",
            db=db,
            device=dev,
        )
        assert ok_410 is False
        db.refresh(dev)
        assert dev.web_push_subscription is None
    finally:
        db.close()


def test_wishlist_check_price_uses_internal_deals_and_guards_idor(isolated_env, monkeypatch):
    client, SessionCls, _ = isolated_env
    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "unit-test-device-secret")

    user_a = client.post("/api/push/device", json={}).json()
    user_b = client.post("/api/push/device", json={}).json()
    headers_a = {"X-Device-Token": user_a["device_token"]}
    headers_b = {"X-Device-Token": user_b["device_token"]}

    # 1. 위시리스트 등록 (문자열 정렬 함정/종료딜/0원/외화/다중단어 검증용)
    wl_ssd = client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "삼성SSD", "target_price": 35000},
        headers=headers_a,
    ).json()
    wl_usd = client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "소니헤드폰", "target_price": 135000},
        headers=headers_a,
    ).json()
    wl_mac = client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "맥북 에어", "target_price": 1200000},
        headers=headers_a,
    ).json()

    # 인증/IDOR/404 방어 검증
    assert client.post(f"/api/wishlist/{wl_ssd['id']}/check-price").status_code == 401
    assert client.post(f"/api/wishlist/{wl_ssd['id']}/check-price", headers=headers_b).status_code == 403
    assert client.post("/api/wishlist/999999/check-price", headers=headers_a).status_code == 404

    db = SessionCls()
    try:
        db.add_all([
            # 종료 딜(10000원) 및 0원/무효가 딜 -> 반드시 제외되어야 함
            Deal(
                source_community_id=1,
                title="[쿠팡] 삼성SSD 990 PRO 종료특가 (10,000원)",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=91001",
                ecommerce_link="https://www.coupang.com/vp/products/91001",
                price="10000",
                shop_name="쿠팡",
                is_closed=True,
            ),
            Deal(
                source_community_id=1,
                title="[쿠팡] 삼성SSD 0원 오류 딜",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=91002",
                ecommerce_link="https://www.coupang.com/vp/products/91002",
                price="0",
                shop_name="쿠팡",
                is_closed=False,
            ),
            Deal(
                source_community_id=1,
                title="[쿠팡] 삼성SSD 가격미정 안내",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=91003",
                ecommerce_link="https://www.coupang.com/vp/products/91003",
                price="가격미정",
                shop_name="쿠팡",
                is_closed=False,
            ),
            # 문자열 정렬 함정("120000" < "29000") 방어: 수치상 최저가 29000원이 선택되어야 함
            Deal(
                source_community_id=1,
                title="[11번가] 삼성SSD 1TB 특가 (120,000원)",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=91004",
                ecommerce_link="https://www.11st.co.kr/products/91004",
                price="120000",
                shop_name="11번가",
                is_closed=False,
            ),
            Deal(
                source_community_id=1,
                title="[지마켓] 삼성SSD 방열판 특가 (29,000원)",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=91005",
                ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=91005",
                price="29000",
                shop_name="지마켓",
                is_closed=False,
            ),
            # 외화 USD($100.00 = 10000센트 -> 140,000원) vs KRW(150,000원) 비교
            Deal(
                source_community_id=1,
                title="[아마존] 소니헤드폰 XM5 직구 ($100.00)",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=91006",
                ecommerce_link="https://www.amazon.com/dp/B000091006",
                price="10000",
                currency="USD",
                shop_name="아마존",
                is_closed=False,
            ),
            Deal(
                source_community_id=1,
                title="[쿠팡] 소니헤드폰 XM5 국내정발 (150,000원)",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=91007",
                ecommerce_link="https://www.coupang.com/vp/products/91007",
                price="150000",
                currency="KRW",
                shop_name="쿠팡",
                is_closed=False,
            ),
            # 다중 단어 + 띄어쓰기 무시 매칭 ("맥북 에어" <-> "맥북에어")
            Deal(
                source_community_id=1,
                title="[쿠팡] 애플 맥북에어 M3 13인치 (1,290,000원/무료)",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=91008",
                ecommerce_link="https://www.coupang.com/vp/products/91008",
                price="1290000",
                currency="KRW",
                shop_name="쿠팡",
                is_closed=False,
            ),
        ])
        db.commit()
    finally:
        db.close()

    # 2. 삼성SSD 최저가(29,000원) 및 PriceCheckResponse 9필드 검증
    res_ssd = client.post(f"/api/wishlist/{wl_ssd['id']}/check-price", headers=headers_a)
    assert res_ssd.status_code == 200
    body_ssd = res_ssd.json()
    assert set(body_ssd.keys()) == {
        "message",
        "keyword",
        "current_price",
        "target_price",
        "platform",
        "product_url",
        "title",
        "is_target_reached",
        "updated_at",
    }
    assert body_ssd["message"] == "가격 체크 완료"
    assert body_ssd["keyword"] == "삼성SSD"
    assert body_ssd["current_price"] == 29000
    assert body_ssd["target_price"] == 35000
    assert body_ssd["platform"] == "지마켓"
    assert body_ssd["product_url"] == "https://item.gmarket.co.kr/Item?goodscode=91005"
    assert "삼성SSD 방열판 특가" in body_ssd["title"]
    assert body_ssd["is_target_reached"] is True
    assert body_ssd["updated_at"].endswith("Z")

    # 3. USD -> KRW 환산 최저가(140,000원) 검증
    res_usd = client.post(f"/api/wishlist/{wl_usd['id']}/check-price", headers=headers_a)
    assert res_usd.status_code == 200
    body_usd = res_usd.json()
    assert body_usd["current_price"] == 140000
    assert body_usd["platform"] == "아마존"
    assert body_usd["is_target_reached"] is False

    # 4. 다중 단어/공백 무시 매칭 및 target_price 변경에 따른 is_target_reached 전환 검증
    res_mac_miss = client.post(f"/api/wishlist/{wl_mac['id']}/check-price", headers=headers_a)
    assert res_mac_miss.status_code == 200
    assert res_mac_miss.json()["current_price"] == 1290000
    assert res_mac_miss.json()["is_target_reached"] is False

    assert (
        client.patch(
            f"/api/wishlist/{wl_mac['id']}",
            json={"target_price": 1300000},
            headers=headers_a,
        ).status_code
        == 200
    )
    res_mac_hit = client.post(f"/api/wishlist/{wl_mac['id']}/check-price", headers=headers_a)
    assert res_mac_hit.status_code == 200
    assert res_mac_hit.json()["current_price"] == 1290000
    assert res_mac_hit.json()["is_target_reached"] is True


def test_wishlist_check_price_multiword_and_empty_match(isolated_env, monkeypatch):
    client, SessionCls, _ = isolated_env
    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "unit-test-device-secret")

    user = client.post("/api/push/device", json={}).json()
    headers = {"X-Device-Token": user["device_token"]}

    wl_missing = client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "존재하지않는초희귀상품", "target_price": 50000},
        headers=headers,
    ).json()
    wl_like_literal = client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "100%_테스트", "target_price": 20000},
        headers=headers,
    ).json()

    db = SessionCls()
    try:
        db.add(
            Deal(
                source_community_id=1,
                title="[쿠팡] 1000ABC테스트 상품 (9,900원)",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=92001",
                ecommerce_link="https://www.coupang.com/vp/products/92001",
                price="9900",
                shop_name="쿠팡",
                is_closed=False,
            )
        )
        db.commit()
    finally:
        db.close()

    # 1. 매칭 실패 시에도 PriceCheckResponse 9필드 계약 유지
    res_missing = client.post(f"/api/wishlist/{wl_missing['id']}/check-price", headers=headers)
    assert res_missing.status_code == 200
    body_missing = res_missing.json()
    assert set(body_missing.keys()) == {
        "message",
        "keyword",
        "current_price",
        "target_price",
        "platform",
        "product_url",
        "title",
        "is_target_reached",
        "updated_at",
    }
    assert body_missing["message"] == "최저가를 찾을 수 없습니다"
    assert body_missing["keyword"] == "존재하지않는초희귀상품"
    assert body_missing["current_price"] is None
    assert body_missing["target_price"] == 50000
    assert body_missing["platform"] is None
    assert body_missing["product_url"] is None
    assert body_missing["title"] is None
    assert body_missing["is_target_reached"] is False
    assert body_missing["updated_at"].endswith("Z")

    # 2. LIKE 와일드카드('%', '_') 리터럴 이스케이프 검증: "1000ABC테스트"는 매칭되면 안 됨
    res_no_wild = client.post(f"/api/wishlist/{wl_like_literal['id']}/check-price", headers=headers)
    assert res_no_wild.status_code == 200
    assert res_no_wild.json()["message"] == "최저가를 찾을 수 없습니다"
    assert res_no_wild.json()["current_price"] is None

    # 실제 "100%_테스트" 리터럴 포함 딜 추가 시에는 정상 매칭
    db = SessionCls()
    try:
        db.add(
            Deal(
                source_community_id=1,
                title="[네이버] 100%_테스트 리터럴 상품 (15,000원)",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=92002",
                ecommerce_link="https://smartstore.naver.com/test/92002",
                price="15000",
                shop_name="네이버",
                is_closed=False,
            )
        )
        db.commit()
    finally:
        db.close()

    res_literal_hit = client.post(f"/api/wishlist/{wl_like_literal['id']}/check-price", headers=headers)
    assert res_literal_hit.status_code == 200
    assert res_literal_hit.json()["message"] == "가격 체크 완료"
    assert res_literal_hit.json()["current_price"] == 15000
    assert res_literal_hit.json()["is_target_reached"] is True


def test_wishlist_check_price_rejects_negative_prices_and_currency_whitespace(isolated_env, monkeypatch):
    client, SessionCls, _ = isolated_env
    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "unit-test-device-secret")

    # 1. extract_numeric_price 음수 문자열/수치 차단 및 정상 문자열 파싱 검증
    for neg in ("-1000", "- 1,000원", "$-100", "-$100", "−500", "USD -100", "-", -1000, -0.5, 0):
        assert extract_numeric_price(neg) == 0
    assert extract_numeric_price("19,900원") == 19900

    # 2. is_collection_dummy_price 통화 공백 정규화(" krw ") 검증
    assert is_collection_dummy_price(100, "네거티브테스트 브랜드 모음전", " krw ") is True

    user = client.post("/api/push/device", json={}).json()
    headers = {"X-Device-Token": user["device_token"]}
    wl = client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "네거티브테스트", "target_price": 20000},
        headers=headers,
    ).json()

    db = SessionCls()
    try:
        db.add_all([
            Deal(
                source_community_id=1,
                title="[쿠팡] 네거티브테스트 음수가격 딜1",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=93001",
                ecommerce_link="https://www.coupang.com/vp/products/93001",
                price="-1000",
                currency="KRW",
                shop_name="쿠팡",
                is_closed=False,
            ),
            Deal(
                source_community_id=1,
                title="[쿠팡] 네거티브테스트 음수가격 딜2",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=93002",
                ecommerce_link="https://www.coupang.com/vp/products/93002",
                price="- 1,000원",
                currency="KRW",
                shop_name="쿠팡",
                is_closed=False,
            ),
            Deal(
                source_community_id=1,
                title="[아마존] 네거티브테스트 외화음수 딜3",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=93003",
                ecommerce_link="https://www.amazon.com/dp/B000093003",
                price="$-100",
                currency="USD",
                shop_name="아마존",
                is_closed=False,
            ),
            Deal(
                source_community_id=1,
                title="[지마켓] 네거티브테스트 브랜드 모음전",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=93004",
                ecommerce_link="https://www.gmarket.co.kr/n/plan?id=93004",
                price="100",
                currency=" krw ",
                shop_name="지마켓",
                is_closed=False,
            ),
            Deal(
                source_community_id=1,
                title="[네이버] 네거티브테스트 정상 상품 (19,900원)",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=93005",
                ecommerce_link="https://smartstore.naver.com/test/93005",
                price="19,900원",
                currency="KRW",
                shop_name="네이버",
                is_closed=False,
            ),
        ])
        db.commit()
    finally:
        db.close()

    res = client.post(f"/api/wishlist/{wl['id']}/check-price", headers=headers)
    assert res.status_code == 200
    body = res.json()
    assert body["message"] == "가격 체크 완료"
    assert body["current_price"] == 19900
    assert body["platform"] == "네이버"
    assert body["is_target_reached"] is True


def test_wishlist_check_price_scans_past_300_deals_for_older_lowest_and_after_invalid_prices(isolated_env, monkeypatch):
    client, SessionCls, _ = isolated_env
    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "unit-test-device-secret")

    user = client.post("/api/push/device", json={}).json()
    headers = {"X-Device-Token": user["device_token"]}

    wl_cheaper_older = client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "대량스캔고가선행", "target_price": 15000},
        headers=headers,
    ).json()
    wl_invalid_newer = client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "대량스캔무효선행", "target_price": 15000},
        headers=headers,
    ).json()

    base_time = datetime(2026, 1, 1, 0, 0, 0)
    db = SessionCls()
    try:
        bulk_deals = []
        # 1) 최신 305건이 50,000원이고 구형 5건이 10,000원인 대량 딜 310건
        for idx in range(310):
            is_older_cheaper = idx < 5
            bulk_deals.append(
                Deal(
                    source_community_id=1,
                    title=f"[쿠팡] 대량스캔고가선행 상품 #{idx}",
                    post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=94{idx:03d}",
                    ecommerce_link=f"https://www.coupang.com/vp/products/94{idx:03d}",
                    price="10000" if is_older_cheaper else "50000",
                    currency="KRW",
                    shop_name="쿠팡",
                    is_closed=False,
                    indexed_at=base_time + timedelta(minutes=idx),
                )
            )

        # 2) 최신 305건이 "가격미정"이고 구형 5건이 10,000원인 대량 딜 310건
        for idx in range(310):
            is_older_valid = idx < 5
            bulk_deals.append(
                Deal(
                    source_community_id=1,
                    title=f"[네이버] 대량스캔무효선행 상품 #{idx}",
                    post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=95{idx:03d}",
                    ecommerce_link=f"https://smartstore.naver.com/test/95{idx:03d}",
                    price="10000" if is_older_valid else "가격미정",
                    currency="KRW",
                    shop_name="네이버",
                    is_closed=False,
                    indexed_at=base_time + timedelta(minutes=idx),
                )
            )

        db.add_all(bulk_deals)
        db.commit()
    finally:
        db.close()

    res_older = client.post(f"/api/wishlist/{wl_cheaper_older['id']}/check-price", headers=headers)
    assert res_older.status_code == 200
    body_older = res_older.json()
    assert body_older["message"] == "가격 체크 완료"
    assert body_older["current_price"] == 10000
    assert body_older["is_target_reached"] is True

    res_invalid = client.post(f"/api/wishlist/{wl_invalid_newer['id']}/check-price", headers=headers)
    assert res_invalid.status_code == 200
    body_invalid = res_invalid.json()
    assert body_invalid["message"] == "가격 체크 완료"
    assert body_invalid["current_price"] == 10000
    assert body_invalid["platform"] == "네이버"
    assert body_invalid["is_target_reached"] is True
