# -*- coding: utf-8 -*-
import datetime
import json
import os
import sys

import httpx
import pytest
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

from backend.database.models import Base as CommunityBase, Community, Deal, DeviceToken, PushKeyword
from backend.database.session import get_db, get_db_session
from backend.main import app
from backend.models.wishlist_models import Base as WishlistBase, Wishlist
from backend.services import push_worker
from backend.services import toss_push_service as tps


@pytest.fixture()
def isolated_toss_env():
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

    push_worker.reset_rate_limits()
    with TestClient(app) as client:
        yield client, SessionCls

    push_worker.reset_rate_limits()
    app.dependency_overrides.clear()
    engine.dispose()


def test_toss_push_unconfigured_noop(monkeypatch):
    monkeypatch.delenv("TOSS_MTLS_CERT_PATH", raising=False)
    monkeypatch.delenv("TOSS_MTLS_KEY_PATH", raising=False)
    monkeypatch.delenv("TOSS_PUSH_TEMPLATE_CODE", raising=False)

    res = tps.send_toss_push_message("anon-hash-123", {"keyword": "생수", "dealId": "10"})
    assert res == {"sent": False, "reason": "not_configured"}

    status = tps.get_toss_push_status()
    assert status["configured"] is False
    assert status["cert_exists"] is False
    assert status["key_exists"] is False
    assert status["template_code_set"] is False


def test_toss_push_send_success_and_error_codes(monkeypatch):
    monkeypatch.setenv("TOSS_PUSH_TEMPLATE_CODE", "insightdeal-keyword-hotdeal")
    monkeypatch.setenv("TOSS_PUSH_ENABLED", "true")

    captured = []

    def ok_handler(request: httpx.Request) -> httpx.Response:
        captured.append((
            str(request.url),
            request.headers.get("x-anon-key"),
            json.loads(request.content.decode("utf-8")),
        ))
        return httpx.Response(
            200,
            json={"resultType": "SUCCESS", "success": {"msgCount": 1, "sentPushCount": 1, "failCount": 0}},
        )

    ok_client = httpx.Client(transport=httpx.MockTransport(ok_handler))
    res = tps.send_toss_push_message(
        "anon-user-xyz",
        context={"keyword": "삼다수", "dealId": 555},
        http_client=ok_client,
    )
    assert res["sent"] is True
    assert res["msg_count"] == 1
    assert len(captured) == 1
    assert captured[0][0] == "https://apps-in-toss-api.toss.im/api-partner/v1/apps-in-toss/messenger/send-message"
    assert captured[0][1] == "anon-user-xyz"
    assert captured[0][2] == {
        "templateSetCode": "insightdeal-keyword-hotdeal",
        "context": {"keyword": "삼다수", "dealId": "555"},
    }

    for err_code in ("4010", "5004", "4095"):
        err_client = httpx.Client(
            transport=httpx.MockTransport(
                lambda _req, code=err_code: httpx.Response(
                    400,
                    json={"resultType": "FAIL", "error": {"errorCode": code, "reason": "test"}},
                )
            )
        )
        err_res = tps.send_toss_push_message(
            "anon-user-xyz",
            context={"keyword": "삼다수", "dealId": "555"},
            http_client=err_client,
        )
        assert err_res["sent"] is False
        assert err_res["error_code"] == err_code
        assert err_res["reason"] == f"api_error_{err_code}"


def test_device_registration_preserves_toss_anon_key_and_admin_status(isolated_toss_env, monkeypatch):
    client, SessionCls = isolated_toss_env
    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "unit-test-secret")
    monkeypatch.setenv("ADMIN_API_KEY", "unit-admin-key")
    monkeypatch.setenv("TOSS_PUSH_TEMPLATE_CODE", "insightdeal-keyword-hotdeal")

    reg = client.post(
        "/api/push/device",
        json={"device_uuid": "toss-dev-1", "toss_anon_key": "anon-key-abc"},
    )
    assert reg.status_code == 200
    body = reg.json()
    assert body["has_toss_push"] is True
    token = body["device_token"]
    headers = {"X-Device-Token": token}

    # 빈 문자열로 재요청해도 기존 toss_anon_key가 보존되어야 함
    upd = client.post(
        "/api/push/device",
        json={"device_uuid": "toss-dev-1", "toss_anon_key": ""},
        headers=headers,
    )
    assert upd.status_code == 200
    assert upd.json()["has_toss_push"] is True

    kw_res = client.get("/api/push/keywords?device_uuid=toss-dev-1", headers=headers)
    assert kw_res.status_code == 200
    assert kw_res.json()["has_toss_push"] is True

    admin_res = client.get("/admin/toss-push/status", headers={"X-Admin-Key": "unit-admin-key"})
    assert admin_res.status_code == 200
    assert admin_res.json()["registered_toss_devices"] == 1
    assert admin_res.json()["template_set_code"] == "insightdeal-keyword-hotdeal"


def test_push_worker_dispatches_toss_push_for_keyword_and_wishlist_with_dnd_and_cap(isolated_toss_env, monkeypatch):
    _, SessionCls = isolated_toss_env
    monkeypatch.setenv("INSIGHTDEAL_DAILY_PUSH_LIMIT", "2")

    sent_calls = []

    def fake_send_toss(anon_key, context=None, **kwargs):
        sent_calls.append((anon_key, context))
        return {"sent": True, "reason": "sent", "msg_count": 1}

    monkeypatch.setattr("backend.services.toss_push_service.send_toss_push_message", fake_send_toss)

    db = SessionCls()
    try:
        dev_ok = DeviceToken(
            device_uuid="toss-user-ok",
            toss_anon_key="anon-ok-1",
            is_active=True,
            night_push_consent=False,
            dnd_enabled=False,
        )
        dev_dnd = DeviceToken(
            device_uuid="toss-user-dnd",
            toss_anon_key="anon-dnd-2",
            is_active=True,
            night_push_consent=False,
            dnd_enabled=True,
            dnd_start_time="10:00",
            dnd_end_time="15:00",
        )
        db.add_all([dev_ok, dev_dnd])
        db.commit()
        db.refresh(dev_ok)
        db.refresh(dev_dnd)

        db.add_all([
            PushKeyword(device_token_id=dev_ok.id, keyword="삼다수", is_active=True),
            PushKeyword(device_token_id=dev_dnd.id, keyword="삼다수", is_active=True),
            Wishlist(user_id="toss-user-ok", keyword="삼다수", target_price=20000, is_active=True, alert_enabled=True),
        ])
        deals = []
        for idx in (1, 2, 3):
            d = Deal(
                source_community_id=1,
                title=f"[쿠팡] 제주 삼다수 2L 24병 #{idx}",
                post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=700{idx}",
                ecommerce_link=f"https://www.coupang.com/vp/products/700{idx}",
                shop_name="쿠팡",
                price="14900",
                currency="KRW",
                is_closed=False,
            )
            db.add(d)
            deals.append(d)
        db.commit()
        deal_ids = [d.id for d in deals]
    finally:
        db.close()

    kst = datetime.timezone(datetime.timedelta(hours=9))
    noon = datetime.datetime(2026, 11, 26, 12, 0, tzinfo=kst)

    # 1차 딜: keyword + wishlist 2건 발송 (daily cap=2 도달), dnd 유저는 차단
    st1 = push_worker.background_trigger_keyword_alarms(deal_ids[0], session_factory=SessionCls, now_kst=noon)
    assert st1["toss_push_sent"] == 2
    assert st1["dropped_dnd"] == 1
    assert len(sent_calls) == 2
    assert all(k == "anon-ok-1" for k, _ in sent_calls)

    # 2차 딜: dev_ok 이미 daily cap=2 도달 -> 추가 발송 0건
    st2 = push_worker.background_trigger_keyword_alarms(deal_ids[1], session_factory=SessionCls, now_kst=noon)
    assert st2["toss_push_sent"] == 0
    assert len(sent_calls) == 2


def test_toss_push_client_init_error_and_error_payload_handling(isolated_toss_env, monkeypatch):
    """mTLS 클라이언트 생성 예외 격리 및 HTTP 200 내 error 페이로드 실패 판정 검증."""
    from backend.services import toss_push_service as tps

    monkeypatch.setattr(
        tps,
        "get_toss_push_config",
        lambda: {
            "enabled": True,
            "configured": True,
            "cert_path": "/tmp/fake.crt",
            "key_path": "/tmp/fake.key",
            "cert_exists": True,
            "key_exists": True,
            "template_set_code": "insightdeal-keyword-hotdeal",
            "api_base": "https://apps-in-toss-api.toss.im",
        },
    )

    def _raise_client(*args, **kwargs):
        raise PermissionError("cert file unreadable")

    monkeypatch.setattr("backend.services.toss_push_service.httpx.Client", _raise_client)
    res_err = tps.send_toss_push_message("anon-test-key", context={"keyword": "테스트", "dealId": "1"})
    assert res_err["sent"] is False
    assert res_err["reason"] == "error:PermissionError"

    class _FakeResp:
        status_code = 200
        def json(self):
            return {"error": {"errorCode": "4010", "reason": "invalid"}}

    class _FakeClient:
        def post(self, *args, **kwargs):
            return _FakeResp()

    res_payload_err = tps.send_toss_push_message("anon-test-key", context={"keyword": "테스트"}, http_client=_FakeClient())
    assert res_payload_err["sent"] is False
    assert res_payload_err["error_code"] == "4010"

