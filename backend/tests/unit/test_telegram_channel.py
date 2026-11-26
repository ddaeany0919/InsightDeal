# -*- coding: utf-8 -*-
import datetime
import json
import os
import sys

import httpx
import pytest
from fastapi.testclient import TestClient

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from backend.database.models import Community, Deal
from backend.main import app
from backend.services import telegram_channel as tg


_KST = datetime.timezone(datetime.timedelta(hours=9))


def _make_deal(**overrides):
    defaults = dict(
        id=101,
        source_community_id=1,
        title="[쿠팡] 코카콜라 제로 24캔 <초특가> & 할인",
        post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=101",
        ecommerce_link="https://www.coupang.com/vp/products/123456789?itemId=111",
        shop_name="쿠팡",
        price="14900",
        shipping_fee="무료배송",
        currency="KRW",
        image_url="https://cdn.example.com/cola_hd.jpg",
        category="식품",
        is_closed=False,
        honey_score=140,
        ai_summary="캔당 620원 역대 최저가 <신규>",
        like_count=12,
        is_super_hotdeal=True,
    )
    defaults.update(overrides)
    return type("FakeDeal", (), defaults)()


def test_telegram_unconfigured_noop(tmp_path, monkeypatch):
    monkeypatch.delenv("TELEGRAM_BOT_TOKEN", raising=False)
    monkeypatch.delenv("TELEGRAM_CHANNEL_ID", raising=False)
    monkeypatch.setenv("TELEGRAM_STATE_FILE", str(tmp_path / "tg_state.json"))

    res = tg.send_telegram_deal(_make_deal())
    assert res == {"sent": False, "reason": "not_configured"}
    assert not (tmp_path / "tg_state.json").exists()

    status = tg.get_telegram_status()
    assert status["configured"] is False
    assert "bot_token" not in status


def test_telegram_eligibility_filters_and_limits(tmp_path, monkeypatch):
    monkeypatch.setenv("TELEGRAM_BOT_TOKEN", "123:TEST_TOKEN")
    monkeypatch.setenv("TELEGRAM_CHANNEL_ID", "@eolmande_test")
    monkeypatch.setenv("TELEGRAM_STATE_FILE", str(tmp_path / "tg_state.json"))
    monkeypatch.setenv("TELEGRAM_QUIET_HOURS", "00:00-07:30")
    monkeypatch.setenv("TELEGRAM_MAX_DAILY_POSTS", "2")
    monkeypatch.setenv("TELEGRAM_MIN_INTERVAL_SEC", "180")

    day_noon = datetime.datetime(2026, 11, 25, 12, 0, tzinfo=_KST)
    quiet_dawn = datetime.datetime(2026, 11, 25, 3, 15, tzinfo=_KST)

    assert tg.evaluate_telegram_broadcast_eligibility(_make_deal(is_closed=True), now_kst=day_noon) == (False, "deal_closed")
    assert tg.evaluate_telegram_broadcast_eligibility(_make_deal(category="적립"), now_kst=day_noon) == (False, "filtered_points_or_attendance")
    assert tg.evaluate_telegram_broadcast_eligibility(_make_deal(title="[네이버] 매일 출첵 이벤트"), now_kst=day_noon) == (False, "filtered_points_or_attendance")
    assert tg.evaluate_telegram_broadcast_eligibility(
        _make_deal(is_super_hotdeal=False, honey_score=50, like_count=2),
        now_kst=day_noon,
    ) == (False, "below_hotdeal_threshold")
    assert tg.evaluate_telegram_broadcast_eligibility(_make_deal(), now_kst=quiet_dawn) == (False, "quiet_hours")

    # State checks: duplicate id, duplicate product key, interval, daily cap
    state = {
        "date_kst": "2026-11-25",
        "daily_count": 1,
        "last_sent_ts": day_noon.timestamp() - 60,
        "sent_deal_ids": [101],
        "sent_dedup_keys": [tg._build_deal_dedup_key(_make_deal(id=101))],
    }
    assert tg.evaluate_telegram_broadcast_eligibility(_make_deal(id=101), now_kst=day_noon, state=state) == (False, "duplicate_deal_id")
    assert tg.evaluate_telegram_broadcast_eligibility(_make_deal(id=102), now_kst=day_noon, state=state) == (False, "duplicate_product_key")

    other_deal = _make_deal(id=202, title="[쿠팡] 삼성 SSD 2TB", shop_name="쿠팡")
    assert tg.evaluate_telegram_broadcast_eligibility(other_deal, now_kst=day_noon, state=state) == (False, "min_interval_cooldown")

    state["last_sent_ts"] = day_noon.timestamp() - 300
    assert tg.evaluate_telegram_broadcast_eligibility(other_deal, now_kst=day_noon, state=state) == (True, "eligible")

    state["daily_count"] = 2
    assert tg.evaluate_telegram_broadcast_eligibility(other_deal, now_kst=day_noon, state=state) == (False, "daily_cap_reached")


def test_telegram_format_html_escape_and_currency(monkeypatch):
    monkeypatch.setenv("TELEGRAM_PUBLIC_WEB_BASE", "https://eolmande.pages.dev")
    deal = _make_deal(
        id=999,
        title='[아마존] WD Black <2TB> & "Gen4" NVMe',
        shop_name="아마존",
        price="8999",
        currency="USD",
        shipping_fee="직배 무료",
        ecommerce_link="https://www.amazon.com/dp/B0B7CMZ3QH",
        image_url="https://cdn4.ppomppu.co.kr/zboard/data/_thumb/ppomppu/1/small_999.jpg",
    )
    msg = tg.format_telegram_deal_message(deal)
    assert "&lt;2TB&gt;" in msg["text"]
    assert "$89.99" in msg["text"]
    assert "https://eolmande.pages.dev/s/999" in msg["text"]
    assert "제휴 링크를 통해 구매 시 일정액의 수수료를 제공받을 수 있습니다." in msg["text"]
    assert msg["photo_url"] is None  # small_ 저화질 썸네일은 사진 대신 링크 미리보기/메시지 사용
    buttons = msg["reply_markup"]["inline_keyboard"][0]
    assert buttons[0]["url"] == "https://eolmande.pages.dev/s/999"
    assert buttons[1]["url"] == "https://www.amazon.com/dp/B0B7CMZ3QH"


def test_telegram_send_photo_fallback_to_send_message_and_persist_state(tmp_path, monkeypatch):
    state_path = tmp_path / "tg_state.json"
    monkeypatch.setenv("TELEGRAM_BOT_TOKEN", "123:TEST_TOKEN")
    monkeypatch.setenv("TELEGRAM_CHANNEL_ID", "@eolmande_test")
    monkeypatch.setenv("TELEGRAM_STATE_FILE", str(state_path))
    monkeypatch.setenv("TELEGRAM_QUIET_HOURS", "")

    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append((request.url.path, json.loads(request.content.decode("utf-8"))))
        if request.url.path.endswith("/sendPhoto"):
            return httpx.Response(400, json={"ok": False, "description": "Bad Request: failed to get HTTP URL content"})
        return httpx.Response(200, json={"ok": True, "result": {"message_id": 77}})

    client = httpx.Client(transport=httpx.MockTransport(handler))
    now_noon = datetime.datetime(2026, 11, 25, 13, 0, tzinfo=_KST)
    res = tg.send_telegram_deal(_make_deal(id=303), now_kst=now_noon, http_client=client)

    assert res["sent"] is True
    assert res["method"] == "sendMessage"
    assert res["daily_count"] == 1
    assert [c[0] for c in calls] == ["/bot123:TEST_TOKEN/sendPhoto", "/bot123:TEST_TOKEN/sendMessage"]

    saved = json.loads(state_path.read_text(encoding="utf-8"))
    assert saved["daily_count"] == 1
    assert saved["sent_total"] == 1
    assert 303 in saved["sent_deal_ids"]


def test_admin_telegram_routes_auth_status_and_dry_run_test(tmp_path, monkeypatch):
    monkeypatch.setenv("ADMIN_API_KEY", "super-secret-admin")
    monkeypatch.delenv("TELEGRAM_BOT_TOKEN", raising=False)
    monkeypatch.delenv("TELEGRAM_CHANNEL_ID", raising=False)
    monkeypatch.setenv("TELEGRAM_STATE_FILE", str(tmp_path / "tg_state.json"))

    from backend.database.session import SessionLocal

    db = SessionLocal()
    try:
        comm = db.query(Community).first()
        if not comm:
            comm = Community(name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr")
            db.add(comm)
            db.commit()
            db.refresh(comm)
        deal_link = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=88888"
        deal_title = "[네이버] 삼다수 2L 24병"
        deal = (
            db.query(Deal)
            .filter(Deal.post_link == deal_link, Deal.title == deal_title)
            .first()
        )
        if not deal:
            deal = Deal(
                source_community_id=comm.id,
                title=deal_title,
                post_link=deal_link,
                ecommerce_link="https://brand.naver.com/samdasoo/products/123",
                shop_name="네이버",
                price="15900",
                shipping_fee="무료배송",
                currency="KRW",
                image_url="https://cdn.example.com/samdasoo.jpg",
                category="식품",
                is_closed=False,
                honey_score=150,
                is_super_hotdeal=True,
            )
            db.add(deal)
            db.commit()
            db.refresh(deal)
        created_id = deal.id
    finally:
        db.close()

    tc = TestClient(app)

    unauth = tc.get("/admin/telegram/status")
    assert unauth.status_code == 403

    st = tc.get("/admin/telegram/status", headers={"X-Admin-Key": "super-secret-admin"})
    assert st.status_code == 200
    assert st.json()["configured"] is False
    assert st.json()["max_daily_posts"] == 20

    dry = tc.post(
        "/admin/telegram/test",
        headers={"X-Admin-Key": "super-secret-admin"},
        json={"deal_id": created_id, "dry_run": True},
    )
    assert dry.status_code == 200
    body = dry.json()
    assert body["deal_id"] == created_id
    assert body["dry_run"] is True
    assert body["eligible"] is True
    assert "삼다수 2L 24병" in body["text"]
