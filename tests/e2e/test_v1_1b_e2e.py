# -*- coding: utf-8 -*-
"""
InsightDeal(얼만데) v1.1b (P1-01 ~ P1-06, P1-12) Opaque-box E2E 테스트 스위트

설계 원칙:
1. 기존 tests/e2e/conftest.py 및 test_tier1~4_*.py(24건)와 충돌하지 않도록
   독립적인 함수 스코프 격리 픽스처 `v11b_env`를 사용합니다.
   - StaticPool 인메모리 SQLite 엔진 + CommunityBase/WishlistBase 동시 스키마 생성
   - 10대 표준 커뮤니티(ppomppu_overseas/'뽐뿌해외' 포함) 시드 주입
   - get_db / get_db_session / SessionLocal 및 이미지 캐시/전역 레이트리밋/피드 캐시 격리
2. Wave 1 수집 호환성:
   - 모듈 최상단에는 현존 확정 심볼만 import하며, M1에서 추가될 심볼은
     테스트 함수 내부에서 지연 import / getattr로 바인딩합니다.
3. 엄격한 계약 검증:
   - skip/xfail/가짜 통과 없이 PROJECT.md §4 및 ORIGINAL_REQUEST.md 계약을
     상태 코드, 응답 스키마(이중 키), DB 영속성, 보안 경계까지 엄격히 단언합니다.

계층 구성 (Iteration 2 보강 스위트 — Tier 1 ~ Tier 4):
- Tier 1: P1-01, P1-02, P1-03, P1-04, P1-05(11종 출처·대조군·320건+DSU·popular 커서·8조합), P1-06(다중캐시 무효화·4차제보), P1-12 핵심 기능 E2E
- Tier 2: 만료·동일기기 5회 오답 잠금(타인 코드 보호)·브루트포스 429·IDOR/하이재킹·PBKDF2 업그레이드·충돌병합·XSS/SSRF·커스텀 DND 해제/ALTER TABLE 마이그레이션·Wishlist/키워드 합산 레이트리밋 경계
- Tier 3: 출처×동기화×필터×대조군 교차 페어와이즈(5케이스) & 본문 이미지→프록시 연계
- Tier 4: 게스트 다기기 동기화→야간 DND→아침 요약→품절 제보→종료딜 재발견 풀 라이프사이클 & 레거시 유저 마이그레이션→대용량 커서 탐색
"""

import asyncio
from datetime import datetime, timedelta, timezone
import hashlib
import inspect
import json
import os
import re
import socket
import sys
from types import SimpleNamespace
import urllib.parse
import uuid

# 모듈 임포트 시 실로컬 DB(insight_deal.db) 접근 및 스케줄러 기동 차단
os.environ.setdefault("ENABLE_SCHEDULER", "false")
os.environ.setdefault("DATABASE_URL", "sqlite:///:memory:")
os.environ.setdefault("ENV", "development")

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from fastapi.testclient import TestClient
import httpx
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from backend import main as main_mod
from backend.core.categories import STANDARD_SOURCES
from backend.core.device_auth import create_device_token
from backend.database import models
from backend.database import session as db_session_mod
from backend.database.models import Base as CommunityBase
from backend.database.session import get_db, get_db_session
from backend.models.wishlist_models import Base as WishlistBase
from backend.routers import auth as auth_router
from backend.routers import community as community_router
from backend.services import notification_service
from backend.services import push_worker

# _detect_image_mime 및 최소 크기(>= 60바이트) 조건을 충족하는 128바이트 유효 PNG 페이로드
VALID_PNG_BYTES = b"\x89PNG\r\n\x1a\n" + (b"\x42" * 120)
KST = timezone(timedelta(hours=9))

STANDARD_11_SOURCES = [
    "전체",
    "펨코",
    "뽐뿌",
    "뽐뿌해외",
    "루리웹",
    "퀘이사존",
    "클리앙",
    "알리뽐뿌",
    "빠삭국내",
    "빠삭해외",
    "빠삭육아",
]


def _public_dns_only(host, port, *args, **kwargs):
    """오프라인 테스트 환경에서 모든 도메인을 공인 IP로 해석하는 DNS 스텁입니다."""
    return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))]


def _clear_runtime_caches_and_rate_limits():
    """테스트 간 상태 오염을 막기 위해 피드 캐시, 커뮤니티 캐시, 키워드 캐시, 레이트리밋 저장소를 초기화합니다."""
    community_router.invalidate_feed_api_cache()
    if hasattr(community_router, "_COMMUNITY_ID_CACHE"):
        community_router._COMMUNITY_ID_CACHE.clear()
    push_worker._push_rate_limit.clear()
    notification_service._keyword_cache = {}
    notification_service._cache_last_updated = None

    # M1에서 추가될 수 있는 인메모리 레이트리밋/시도횟수/차단 저장소(dict, deque, set 등) 방어적 초기화
    rate_limit_keywords = (
        "rate_limit",
        "ratelimit",
        "attempt",
        "throttle",
        "bruteforce",
        "report_limit",
        "pairing_limit",
        "sliding_window",
        "lockout",
        "fail_count",
        "failed_attempt",
    )
    for mod in (auth_router, community_router, push_worker):
        for hook_name in ("reset_rate_limits", "clear_rate_limits"):
            hook_fn = getattr(mod, hook_name, None)
            if callable(hook_fn):
                hook_fn()
        for attr_name in dir(mod):
            low = attr_name.lower()
            if any(tok in low for tok in rate_limit_keywords):
                val = getattr(mod, attr_name, None)
                if isinstance(val, (dict, list, set)) or (
                    val is not None and not inspect.ismodule(val) and not inspect.isclass(val) and hasattr(val, "clear") and callable(val.clear)
                ):
                    val.clear()


@pytest.fixture(scope="function")
def v11b_env(monkeypatch, tmp_path):
    """
    v1.1b 전용 완전 격리 환경 픽스처.
    - CommunityBase와 WishlistBase를 모두 StaticPool 인메모리 SQLite에 생성합니다.
    - 10대 기본 커뮤니티('뽐뿌해외' 포함)를 시드합니다.
    - get_db, get_db_session, SessionLocal, IMAGE_CACHE_DIR, 전역 캐시를 격리합니다.
    """
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
        echo=False,
    )
    CommunityBase.metadata.create_all(bind=engine)
    WishlistBase.metadata.create_all(bind=engine)
    SessionCls = sessionmaker(
        autocommit=False,
        autoflush=False,
        expire_on_commit=False,
        bind=engine,
    )

    seed_db = SessionCls()
    try:
        default_communities = [
            models.Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr"),
            models.Community(id=2, name="ppomppu_overseas", display_name="뽐뿌해외", base_url="https://www.ppomppu.co.kr"),
            models.Community(id=3, name="ruliweb", display_name="루리웹", base_url="https://bbs.ruliweb.com"),
            models.Community(id=4, name="clien", display_name="클리앙", base_url="https://www.clien.net"),
            models.Community(id=5, name="ali_ppomppu", display_name="알리뽐뿌", base_url="https://www.ppomppu.co.kr"),
            models.Community(id=6, name="quasarzone", display_name="퀘이사존", base_url="https://quasarzone.com"),
            models.Community(id=7, name="fmkorea", display_name="펨코", base_url="https://www.fmkorea.com"),
            models.Community(id=8, name="bbasak_domestic", display_name="빠삭국내", base_url="https://bbasak.com"),
            models.Community(id=9, name="bbasak_overseas", display_name="빠삭해외", base_url="https://bbasak.com"),
            models.Community(id=10, name="bbasak_parenting", display_name="빠삭육아", base_url="https://bbasak.com"),
        ]
        seed_db.add_all(default_communities)
        seed_db.commit()
    finally:
        seed_db.close()

    def _override_db():
        db = SessionCls()
        try:
            yield db
        finally:
            db.close()

    main_mod.app.dependency_overrides[get_db] = _override_db
    main_mod.app.dependency_overrides[get_db_session] = _override_db
    monkeypatch.setattr(db_session_mod, "SessionLocal", SessionCls)
    if getattr(db_session_mod, "db_manager", None) is not None:
        monkeypatch.setattr(db_session_mod.db_manager, "SessionLocal", SessionCls, raising=False)

    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "v11b-e2e-device-secret")
    monkeypatch.setenv("VAPID_PUBLIC_KEY", "BTestVapidPublicKeyForV11bE2E1234567890")
    monkeypatch.setenv("VAPID_PRIVATE_KEY", "v11b-e2e-vapid-private-key")
    monkeypatch.setattr(main_mod, "IMAGE_CACHE_DIR", str(tmp_path))
    monkeypatch.setattr(main_mod, "_get_deal_backup_candidates_sync", lambda *a, **kw: (None, []))

    _clear_runtime_caches_and_rate_limits()

    try:
        with TestClient(main_mod.app) as test_client:
            yield SimpleNamespace(
                client=test_client,
                SessionCls=SessionCls,
                engine=engine,
                tmp_path=tmp_path,
            )
    finally:
        _clear_runtime_caches_and_rate_limits()
        main_mod.app.dependency_overrides.clear()
        engine.dispose()


def _register_device(
    client: TestClient,
    *,
    fcm_token: str = "",
    dnd_enabled: bool = False,
    dnd_start_time: str = "21:00",
    dnd_end_time: str = "08:00",
    night_push_consent: bool = False,
    web_push_endpoint: str = "",
) -> tuple[str, str, dict[str, str]]:
    """신규 기기를 등록하고 (device_uuid, device_token, X-Device-Token 헤더)를 반환합니다."""
    payload = {
        "dnd_enabled": dnd_enabled,
        "dnd_start_time": dnd_start_time,
        "dnd_end_time": dnd_end_time,
        "night_push_consent": night_push_consent,
    }
    if fcm_token:
        payload["fcm_token"] = fcm_token

    res = client.post("/api/push/device", json=payload)
    assert res.status_code == 200, f"기기 등록 실패: {res.status_code} {res.text}"
    body = res.json()
    dev_uuid = body["device_uuid"]
    dev_token = body["device_token"]
    headers = {"X-Device-Token": dev_token}

    if web_push_endpoint:
        web_res = client.post(
            "/api/push/register-web",
            json={
                "endpoint": web_push_endpoint,
                "keys": {"p256dh": "test-p256dh", "auth": "test-auth"},
            },
            headers=headers,
        )
        assert web_res.status_code == 200, f"웹 푸시 구독 등록 실패: {web_res.status_code} {web_res.text}"

    return dev_uuid, dev_token, headers


def _mock_push_transports(monkeypatch) -> tuple[list[dict], list[list]]:
    """pywebpush.webpush 및 firebase_admin.messaging.send_each 호출을 메모리 리스트로 캡처합니다."""
    webpush_calls: list[dict] = []
    fcm_batches: list[list] = []

    class FakeWebPushException(Exception):
        def __init__(self, status_code=500):
            super().__init__(f"status {status_code}")
            self.response = SimpleNamespace(status_code=status_code)

    def _fake_webpush(**kwargs):
        webpush_calls.append(kwargs)

    monkeypatch.setitem(
        sys.modules,
        "pywebpush",
        SimpleNamespace(webpush=_fake_webpush, WebPushException=FakeWebPushException),
    )

    def _fake_send_each(chunk):
        fcm_batches.append(chunk)
        return SimpleNamespace(success_count=len(chunk), failure_count=0)

    monkeypatch.setattr(push_worker.messaging, "send_each", _fake_send_each)
    return webpush_calls, fcm_batches


def _seed_deal(SessionCls, **overrides) -> int:
    """테스트용 고유 Deal 레코드를 DB에 생성하고 ID를 반환합니다."""
    uid = uuid.uuid4().hex[:10]
    defaults = {
        "source_community_id": 1,
        "title": f"[쿠팡] 유니크 테스트 상품 {uid} (29,900원/무배)",
        "post_link": f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no={uid}",
        "ecommerce_link": f"https://www.coupang.com/vp/products/{uid}",
        "shop_name": "쿠팡",
        "price": "29900",
        "shipping_fee": "무료",
        "currency": "KRW",
        "image_url": f"https://cdn.example.com/thumb_{uid}.jpg",
        "category": "가전제품",
        "indexed_at": datetime.now(timezone.utc).replace(tzinfo=None),
        "is_closed": False,
        "content_html": f"<p>상세 테스트 본문 {uid} 입니다.</p>",
        "base_product_name": f"유니크테스트상품_{uid}",
        "model_code": f"MDL{uid.upper()}",
        "honey_score": 50,
        "view_count": 100,
        "like_count": 5,
        "comment_count": 2,
    }
    defaults.update(overrides)
    db = SessionCls()
    try:
        deal = models.Deal(**defaults)
        db.add(deal)
        db.commit()
        db.refresh(deal)
        return deal.id
    finally:
        db.close()


def _run_morning_digest_job(SessionCls, now_kst: datetime):
    """
    M1에서 추가될 dnd_morning_digest_job을 지연 탐색/호출합니다.
    (Wave 1에서는 모듈 최상단 import 오류 없이 수집되도록 함수 내부에서 바인딩합니다.)
    """
    import backend.scheduler.main as sched_main

    job = getattr(sched_main, "dnd_morning_digest_job", None)
    if job is None:
        job = getattr(push_worker, "dnd_morning_digest_job", None)
    assert callable(job), "M1 미구현: dnd_morning_digest_job 함수가 필요합니다."

    sig = inspect.signature(job)
    kwargs = {}
    if "session_factory" in sig.parameters:
        kwargs["session_factory"] = SessionCls
    if "now_kst" in sig.parameters:
        kwargs["now_kst"] = now_kst

    if asyncio.iscoroutinefunction(job):
        return asyncio.run(job(**kwargs))
    return job(**kwargs)


def _extract_push_payload_texts(webpush_calls: list[dict], fcm_batches: list[list]) -> list[str]:
    """캡처된 Web Push 및 FCM 메시지에서 제목/본문 텍스트를 추출합니다."""
    texts: list[str] = []
    for call in webpush_calls:
        raw_data = call.get("data", "")
        try:
            parsed = json.loads(raw_data) if isinstance(raw_data, str) else raw_data
            texts.append(f"{parsed.get('title', '')} {parsed.get('body', '')}".strip())
        except Exception:
            texts.append(str(raw_data))
    for batch in fcm_batches:
        for msg in batch:
            notif = getattr(msg, "notification", None)
            title = getattr(notif, "title", "") if notif else ""
            body = getattr(notif, "body", "") if notif else ""
            texts.append(f"{title} {body}".strip())
    return texts


def _extract_pairing_table():
    """M1에서 추가된 6자리 페어링 코드 테이블을 메타데이터에서 동적으로 탐색합니다."""
    for table in CommunityBase.metadata.tables.values():
        col_names = set(table.c.keys())
        if "code" in col_names and "expires_at" in col_names:
            return table
    return None


# ===========================================================================
# Tier 1: 핵심 기능 커버리지 (11개 테스트)
# ===========================================================================


def test_t1_p1_01_pairing_code_issue_and_claim_sync(v11b_env):
    """[T1-01 / P1-01] 6자리 일회성 페어링 코드 발급 및 타 기기 찜·키워드·DND·알림 병합"""
    client = v11b_env.client
    _, _, headers_a = _register_device(client, fcm_token="fcm-device-a")
    _, _, headers_b = _register_device(client, fcm_token="fcm-device-b")

    # 기기 A 상태 구성: 찜, 스마트 키워드, DND(22:00~08:00), 알림 내역
    wl_res = client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "에어팟프로", "target_price": 260000},
        headers=headers_a,
    )
    assert wl_res.status_code == 200

    kw_res = client.post(
        "/api/push/keywords",
        json={"keyword": "맥북 <=2200000 -케이스"},
        headers=headers_a,
    )
    assert kw_res.status_code == 200

    dnd_res = client.patch(
        "/api/push/dnd",
        json={"dnd_enabled": True, "dnd_start_time": "22:00", "dnd_end_time": "08:00"},
        headers=headers_a,
    )
    assert dnd_res.status_code == 200

    notif_res = client.post(
        "/api/users/notifications",
        json={"title": "에어팟프로 특가 알림", "keyword": "에어팟프로", "deal_url": "https://example.com/deal/1"},
        headers=headers_a,
    )
    assert notif_res.status_code == 200

    # 1. 기기 A 6자리 페어링 코드 발급
    issue_res = client.post("/api/auth/pairing-code", headers=headers_a)
    assert issue_res.status_code == 200, f"pairing-code 발급 실패: {issue_res.status_code} {issue_res.text}"
    issue_body = issue_res.json()
    code = issue_body["code"]
    assert re.fullmatch(r"\d{6}", code), f"6자리 숫자 코드 규격 불일치: {code!r}"
    assert issue_body["expires_at"].endswith("Z")
    assert issue_body["expiresAt"] == issue_body["expires_at"]
    assert issue_body["expires_in_sec"] > 0

    # 2. 기기 B가 코드 입력하여 병합
    claim_res = client.post(
        "/api/auth/claim-pairing-code",
        json={"code": code},
        headers=headers_b,
    )
    assert claim_res.status_code == 200, f"claim-pairing-code 실패: {claim_res.status_code} {claim_res.text}"
    claim_body = claim_res.json()
    assert claim_body["status"] == "ok"
    assert claim_body["success"] is True
    assert claim_body["merged_wishlists"] >= 1
    assert claim_body["merged_keywords"] >= 1
    assert claim_body["merged_alerts"] >= 1
    assert claim_body["dnd_synced"] is True

    # 3. 기기 B 조회 API로 병합 데이터 영속성 검증
    b_wl = client.get("/api/wishlist/", headers=headers_b)
    assert b_wl.status_code == 200
    assert any(item["keyword"] == "에어팟프로" and item["target_price"] == 260000 for item in b_wl.json())

    b_kw = client.get("/api/push/keywords", headers=headers_b)
    assert b_kw.status_code == 200
    b_kw_body = b_kw.json()
    assert "맥북 <=2200000 -케이스" in b_kw_body["keywords"]
    assert b_kw_body["dnd_enabled"] is True
    assert b_kw_body["dnd_start_time"] == "22:00"
    assert b_kw_body["dnd_end_time"] == "08:00"

    b_notif = client.get("/api/users/notifications", headers=headers_b)
    assert b_notif.status_code == 200
    assert any("에어팟프로 특가 알림" in item["title"] for item in b_notif.json())


def test_t1_p1_01_merge_device_alias_sync(v11b_env):
    """[T1-02 / P1-01] 별칭 엔드포인트 POST /api/auth/merge-device 동기화 계약"""
    client = v11b_env.client
    _, _, headers_a = _register_device(client)
    _, _, headers_c = _register_device(client)

    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "다이슨청소기", "target_price": 450000},
        headers=headers_a,
    ).status_code == 200
    assert client.post(
        "/api/push/keywords",
        json={"keyword": "로보락"},
        headers=headers_a,
    ).status_code == 200

    issue_res = client.post("/api/auth/pairing-code", headers=headers_a)
    assert issue_res.status_code == 200
    code = issue_res.json()["code"]

    merge_res = client.post(
        "/api/auth/merge-device",
        json={"code": code},
        headers=headers_c,
    )
    assert merge_res.status_code == 200
    merge_body = merge_res.json()
    assert merge_body["status"] == "ok"
    assert merge_body["success"] is True
    assert merge_body["merged_wishlists"] >= 1
    assert merge_body["merged_keywords"] >= 1
    assert "dnd_synced" in merge_body

    c_wl = client.get("/api/wishlist/", headers=headers_c)
    assert c_wl.status_code == 200
    assert any(item["keyword"] == "다이슨청소기" for item in c_wl.json())
    c_kw = client.get("/api/push/keywords", headers=headers_c)
    assert c_kw.status_code == 200
    assert "로보락" in c_kw.json()["keywords"]


def test_t1_p1_01_optional_signup_login_and_link_device(v11b_env):
    """[T1-03 / P1-01] 선택형 계정 가입·로그인(선택적 X-Device-Token 병합/변조 401)·link-device(알림·DND JSON 포함)"""
    client = v11b_env.client
    _, _, headers_a = _register_device(client)
    _, _, headers_b = _register_device(client)
    _, _, headers_c = _register_device(client)

    dnd_json = '{"mon":{"enabled":true,"start":"22:30","end":"07:30"}}'
    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "아이패드미니", "target_price": 590000},
        headers=headers_a,
    ).status_code == 200
    assert client.post(
        "/api/push/keywords",
        json={"keyword": "oled모니터"},
        headers=headers_a,
    ).status_code == 200
    assert client.patch(
        "/api/push/dnd",
        json={"dnd_enabled": True, "dnd_start_time": "22:30", "dnd_end_time": "07:30", "dnd_settings_json": dnd_json},
        headers=headers_a,
    ).status_code == 200
    assert client.post(
        "/api/users/notifications",
        json={"title": "계정동기화 알림", "keyword": "아이패드미니", "deal_url": "https://example.com/deal/a"},
        headers=headers_a,
    ).status_code == 200

    # 기기 C는 로그인 전 게스트 상태로 독자적 찜/키워드 보유
    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "게스트C전용찜", "target_price": 150000},
        headers=headers_c,
    ).status_code == 200
    assert client.post(
        "/api/push/keywords",
        json={"keyword": "게스트C키워드"},
        headers=headers_c,
    ).status_code == 200

    forged_dev_headers = {"X-Device-Token": "v1.Zm9yZ2Vk.0000000000000000000000000000000000000000000000000000000000000000"}

    # 변조된 X-Device-Token으로 signup 시도 시 401 거부 (조용히 무시하면 안 됨)
    uname = f"user_{uuid.uuid4().hex[:8]}"
    bad_signup = client.post(
        "/api/auth/signup",
        json={"username": uname, "password": "StrongPassword!99", "nickname": f"닉_{uname}"},
        headers=forged_dev_headers,
    )
    assert bad_signup.status_code == 401

    # 기기 A에서 선택형 계정 가입 (X-Device-Token 동반 시 기기 A 상태 계정 귀속)
    signup_res = client.post(
        "/api/auth/signup",
        json={"username": uname, "password": "StrongPassword!99", "nickname": f"닉_{uname}"},
        headers=headers_a,
    )
    assert signup_res.status_code == 200, f"signup 실패: {signup_res.status_code} {signup_res.text}"
    signup_body = signup_res.json()
    assert signup_body["username"] == uname
    assert signup_body["nickname"] == f"닉_{uname}"
    assert isinstance(signup_body["honey_points"], int)
    assert isinstance(signup_body.get("access_token"), str) and len(signup_body["access_token"]) > 10
    assert signup_body.get("token_type") == "bearer"

    # 변조된 X-Device-Token으로 login 시도 시 401 거부
    bad_login = client.post(
        "/api/auth/login",
        json={"username": uname, "password": "StrongPassword!99"},
        headers=forged_dev_headers,
    )
    assert bad_login.status_code == 401

    # 기기 C에서 선택적 X-Device-Token을 동반하여 로그인 -> 게스트 C의 찜/키워드와 계정 데이터 양방향 병합
    login_res = client.post(
        "/api/auth/login",
        json={"username": uname, "password": "StrongPassword!99"},
        headers=headers_c,
    )
    assert login_res.status_code == 200
    login_body = login_res.json()
    access_token = login_body["access_token"]
    assert isinstance(access_token, str) and len(access_token) > 10
    assert login_body.get("token_type") == "bearer"

    c_wl = client.get("/api/wishlist/", headers=headers_c).json()
    assert any(item["keyword"] == "아이패드미니" for item in c_wl)
    assert any(item["keyword"] == "게스트C전용찜" for item in c_wl)
    c_kw = client.get("/api/push/keywords", headers=headers_c).json()["keywords"]
    assert "oled모니터" in c_kw and "게스트C키워드" in c_kw

    # 기기 B가 Bearer + X-Device-Token으로 link-device 호출
    link_res = client.post(
        "/api/auth/link-device",
        headers={
            "Authorization": f"Bearer {access_token}",
            **headers_b,
        },
    )
    assert link_res.status_code == 200, f"link-device 실패: {link_res.status_code} {link_res.text}"
    link_body = link_res.json()
    assert link_body["status"] == "ok"
    assert link_body["success"] is True

    # 기기 B에 기기 A/C의 찜·키워드·알림·dnd_settings_json까지 동기화되었는지 검증
    b_wl = client.get("/api/wishlist/", headers=headers_b)
    assert b_wl.status_code == 200
    b_wl_kws = {item["keyword"] for item in b_wl.json()}
    assert {"아이패드미니", "게스트C전용찜"}.issubset(b_wl_kws)

    b_kw = client.get("/api/push/keywords", headers=headers_b)
    assert b_kw.status_code == 200
    b_kw_body = b_kw.json()
    assert {"oled모니터", "게스트C키워드"}.issubset(set(b_kw_body["keywords"]))
    assert b_kw_body["dnd_enabled"] is True
    assert b_kw_body["dnd_settings_json"] is not None
    assert json.loads(b_kw_body["dnd_settings_json"]) == json.loads(dnd_json)

    b_notif = client.get("/api/users/notifications", headers=headers_b)
    assert b_notif.status_code == 200
    assert any("계정동기화 알림" in item["title"] for item in b_notif.json())


def test_t1_p1_02_proxy_image_cache_headers_and_404_no_store(v11b_env, monkeypatch):
    """[T1-04 / P1-02] proxy-image 디스크 .cache 원자적 영속성, 별칭 라우트, 실패 시 잔여 파일 0건 검증"""
    client = v11b_env.client
    monkeypatch.setattr(socket, "getaddrinfo", _public_dns_only)

    fetch_count = 0

    async def _fake_get(url, **kwargs):
        nonlocal fetch_count
        fetch_count += 1
        if url.endswith("/ok.png"):
            return httpx.Response(200, content=VALID_PNG_BYTES, request=httpx.Request("GET", url))
        return httpx.Response(404, content=b"not found", request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _fake_get)

    ok_url = "https://cdn.example.com/ok.png"
    r1 = client.get("/api/proxy-image", params={"url": ok_url})
    assert r1.status_code == 200
    assert r1.headers["content-type"].startswith("image/png")
    assert r1.headers.get("cache-control") == "public, max-age=604800, immutable"
    assert r1.content == VALID_PNG_BYTES
    assert fetch_count == 1

    # 실제 디스크 .cache 파일 1개 생성 및 VALID_PNG_BYTES 바이트 일치 + .tmp 미잔류 단언
    cache_files = list(v11b_env.tmp_path.glob("*.cache"))
    assert len(cache_files) == 1, f"디스크 캐시 파일 생성 불일치: {cache_files}"
    assert cache_files[0].read_bytes() == VALID_PNG_BYTES
    assert list(v11b_env.tmp_path.glob("*.tmp")) == []

    # 2차 요청 및 별칭 라우트(/api/image-proxy, /api/community/proxy-image): 디스크 캐시 히트로 업스트림 fetch 0회 추가
    for alias_path in ("/api/proxy-image", "/api/image-proxy", "/api/community/proxy-image"):
        r_alias = client.get(alias_path, params={"url": ok_url})
        assert r_alias.status_code == 200, f"별칭 라우트 실패 ({alias_path}): {r_alias.status_code}"
        assert r_alias.headers.get("cache-control") == "public, max-age=604800, immutable"
        assert r_alias.content == VALID_PNG_BYTES
        assert fetch_count == 1

    # 업스트림 404 시 404 + Cache-Control: no-store + 실패 URL용 캐시/.tmp 잔여 파일 0건
    r_miss = client.get("/api/proxy-image", params={"url": "https://cdn.example.com/missing.png"})
    assert r_miss.status_code == 404
    assert r_miss.headers.get("cache-control") == "no-store"
    assert len(list(v11b_env.tmp_path.iterdir())) == 1


@pytest.mark.asyncio
async def test_t1_p1_02_proxy_image_single_flight_concurrent_requests(v11b_env, monkeypatch):
    """[T1-05 / P1-02] 동일 URL Single-flight 1회 fetch + 디스크 .cache + 이종 URL 병렬 fetch + 404 Single-flight"""
    monkeypatch.setattr(socket, "getaddrinfo", _public_dns_only)

    fetch_count = 0
    started_event = asyncio.Event()
    release_event = asyncio.Event()

    async def _slow_fake_get(url, **kwargs):
        nonlocal fetch_count
        fetch_count += 1
        started_event.set()
        await release_event.wait()
        if "missing" in url:
            return httpx.Response(404, content=b"not found", request=httpx.Request("GET", url))
        return httpx.Response(200, content=VALID_PNG_BYTES, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _slow_fake_get)

    target_url = f"https://cdn.example.com/single-flight-{uuid.uuid4().hex[:8]}.png"
    async with httpx.AsyncClient(app=main_mod.app, base_url="http://testserver") as ac:
        tasks = [
            asyncio.create_task(ac.get("/api/proxy-image", params={"url": target_url}))
            for _ in range(5)
        ]
        await asyncio.wait_for(started_event.wait(), timeout=3.0)
        await asyncio.sleep(0.05)
        release_event.set()
        responses = await asyncio.wait_for(asyncio.gather(*tasks), timeout=5.0)

    assert fetch_count == 1, f"Single-flight 병합 실패: 업스트림 호출 횟수={fetch_count}"
    for resp in responses:
        assert resp.status_code == 200
        assert resp.content == VALID_PNG_BYTES
        assert resp.headers.get("cache-control") == "public, max-age=604800, immutable"

    cache_files = list(v11b_env.tmp_path.glob("*.cache"))
    assert len(cache_files) == 1 and cache_files[0].read_bytes() == VALID_PNG_BYTES
    leftover_tmp = list(v11b_env.tmp_path.glob("*.tmp"))
    assert leftover_tmp == [], f"원자적 캐시 쓰기 후 .tmp 파일 잔류: {leftover_tmp}"

    # 이종 2개 URL 동시 요청 시 전역 락으로 직렬화되지 않고 진정한 URL별 병렬 fetch(2회) 수행 검증
    distinct_in_flight = 0
    distinct_fetch_count = 0
    both_entered = asyncio.Event()

    async def _parallel_distinct_get(url, **kwargs):
        nonlocal distinct_in_flight, distinct_fetch_count
        distinct_fetch_count += 1
        distinct_in_flight += 1
        if distinct_in_flight >= 2:
            both_entered.set()
        await asyncio.wait_for(both_entered.wait(), timeout=3.0)
        return httpx.Response(200, content=VALID_PNG_BYTES, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _parallel_distinct_get)
    url_a = f"https://cdn.example.com/distinct-a-{uuid.uuid4().hex[:6]}.png"
    url_b = f"https://cdn.example.com/distinct-b-{uuid.uuid4().hex[:6]}.png"
    async with httpx.AsyncClient(app=main_mod.app, base_url="http://testserver") as ac:
        res_a, res_b = await asyncio.wait_for(
            asyncio.gather(
                ac.get("/api/proxy-image", params={"url": url_a}),
                ac.get("/api/community/proxy-image", params={"url": url_b}),
            ),
            timeout=5.0,
        )
    assert res_a.status_code == 200 and res_b.status_code == 200
    assert distinct_fetch_count == 2
    assert len(list(v11b_env.tmp_path.glob("*.cache"))) == 3
    assert list(v11b_env.tmp_path.glob("*.tmp")) == []

    # 실패(404) URL 동시 5회 요청 시 Single-flight 1회 + 잔여 파일 추가 0건 검증
    miss_fetch_count = 0
    miss_started = asyncio.Event()
    miss_release = asyncio.Event()

    async def _slow_miss_get(url, **kwargs):
        nonlocal miss_fetch_count
        miss_fetch_count += 1
        miss_started.set()
        await miss_release.wait()
        return httpx.Response(404, content=b"missing", request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _slow_miss_get)
    miss_url = f"https://cdn.example.com/missing-sf-{uuid.uuid4().hex[:6]}.png"
    async with httpx.AsyncClient(app=main_mod.app, base_url="http://testserver") as ac:
        miss_tasks = [asyncio.create_task(ac.get("/api/image-proxy", params={"url": miss_url})) for _ in range(5)]
        await asyncio.wait_for(miss_started.wait(), timeout=3.0)
        await asyncio.sleep(0.05)
        miss_release.set()
        miss_resps = await asyncio.wait_for(asyncio.gather(*miss_tasks), timeout=5.0)
    assert miss_fetch_count == 1
    assert all(r.status_code == 404 and r.headers.get("cache-control") == "no-store" for r in miss_resps)
    assert len(list(v11b_env.tmp_path.iterdir())) == 3


def test_t1_p1_03_deal_detail_body_images_extraction_and_dedup(v11b_env):
    """[T1-06 / P1-03] 상세·목록 본문 다중 이미지 추출·중복제거·// 정규화·&amp; 디코딩·unquote 검증"""
    client = v11b_env.client
    raw_html = (
        '<p>가성비 좋은 모니터 상세 스펙입니다.</p>'
        '<img src="https://cdn.example.com/body1.jpg" alt="1">'
        '[상품이미지: https://cdn.example.com/body1.jpg]'
        '<img src="//cdn.example.com/body2.png?w=800&amp;q=90">'
        '<p>[첨부된 이미지 링크들]: https://cdn.example.com/thumb.jpg, https://cdn.example.com/body2.png?w=800&amp;q=90</p>'
    )
    deal_id = _seed_deal(
        v11b_env.SessionCls,
        title="[알리] LG 울트라기어 게이밍 모니터 (299,000원/무배)",
        image_url="https://cdn.example.com/thumb.jpg",
        content_html=raw_html,
    )

    res = client.get(f"/api/community/deals/{deal_id}")
    assert res.status_code == 200
    body = res.json()

    assert isinstance(body.get("body_images"), list)
    assert isinstance(body.get("bodyImages"), list)
    assert body["body_images"] == body["bodyImages"]

    imgs = body["body_images"]
    assert len(imgs) >= 2
    unquoted_imgs = [urllib.parse.unquote(u) for u in imgs]
    assert len(unquoted_imgs) == len(set(unquoted_imgs)), f"중복 이미지가 제거되지 않았습니다: {imgs}"
    for u in unquoted_imgs:
        assert "&amp;" not in u, f"HTML 엔티티(&amp;)가 디코딩되지 않았습니다: {u!r}"
        assert not u.startswith("//"), f"스킴 상대 경로(//)가 정규화되지 않았습니다: {u!r}"
    joined = " ".join(unquoted_imgs)
    assert "https://cdn.example.com/body1.jpg" in joined
    assert "https://cdn.example.com/body2.png?w=800&q=90" in joined
    assert "<img" not in (body.get("content_html") or "").lower()

    # hot-deals 목록 아이템에도 body_images / bodyImages 이중 키 노출 검증
    list_res = client.get("/api/community/hot-deals")
    assert list_res.status_code == 200
    list_item = next((d for d in list_res.json()["deals"] if d["id"] == deal_id), None)
    assert list_item is not None
    assert isinstance(list_item.get("body_images"), list)
    assert list_item.get("body_images") == list_item.get("bodyImages")
    list_joined = " ".join(urllib.parse.unquote(u) for u in list_item["body_images"])
    assert "https://cdn.example.com/body1.jpg" in list_joined


def test_t1_p1_05_11_sources_including_ppomppu_overseas(v11b_env):
    """[T1-07 / P1-05] 표준 11종 출처 전수·대조군 배제·다중출처·merged_communities 필터 정합성"""
    client = v11b_env.client

    meta_res = client.get("/api/meta/categories")
    assert meta_res.status_code == 200
    meta = meta_res.json()
    assert meta["sources"] == STANDARD_11_SOURCES
    assert len(STANDARD_SOURCES) == 11 and "뽐뿌해외" in STANDARD_SOURCES

    # 10개 커뮤니티별 각 1건씩 고유 딜 시드 (타 출처 대조군으로 상호 배제 검증)
    comm_specs = [
        (1, "뽐뿌"),
        (2, "뽐뿌해외"),
        (3, "루리웹"),
        (4, "클리앙"),
        (5, "알리뽐뿌"),
        (6, "퀘이사존"),
        (7, "펨코"),
        (8, "빠삭국내"),
        (9, "빠삭해외"),
        (10, "빠삭육아"),
    ]
    deal_by_source: dict[str, int] = {}
    base_utc = datetime(2026, 11, 19, 11, 0, 0)
    for offset_idx, (cid, sname) in enumerate(comm_specs):
        did = _seed_deal(
            v11b_env.SessionCls,
            source_community_id=cid,
            title=f"[몰_{sname}] 출처단독검증_{sname}_{offset_idx:02d} (29,000원)",
            post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=comm{cid}&no={55000 + offset_idx}",
            ecommerce_link=f"https://www.coupang.com/vp/products/{660000 + offset_idx}",
            base_product_name=f"출처단독검증상품_{sname}_{offset_idx}",
            model_code=f"SRC{cid:02d}{offset_idx:02d}",
            indexed_at=base_utc - timedelta(minutes=offset_idx),
        )
        deal_by_source[sname] = did

    # merged_communities에 뽐뿌해외(id=2)가 병합된 뽐뿌(id=1) 딜 추가 시드
    merged_id = _seed_deal(
        v11b_env.SessionCls,
        source_community_id=1,
        merged_communities="2::https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&no=99887",
        title="[아마존] 병합출처검증_뽐뿌해외포함 SN850X ($119)",
        post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=99887",
        ecommerce_link="https://www.amazon.com/dp/B0B7CMZ3QH",
        shop_name="아마존",
        price="119",
        currency="USD",
        category="해외직구",
        base_product_name="병합출처검증상품_SN850X",
        model_code="MRG850X",
        indexed_at=base_utc + timedelta(minutes=1),
    )
    all_seeded_ids = set(deal_by_source.values()) | {merged_id}

    # 1. "전체" 조회 시 11건 모두 포함
    r_all = client.get("/api/community/hot-deals", params={"source": "전체"})
    assert r_all.status_code == 200
    assert {d["id"] for d in r_all.json()["deals"]} == all_seeded_ids

    # 2. 10개 개별 출처 전수 필터 검증 (타 출처 대조군 반드시 제외되어야 함)
    for _, sname in comm_specs:
        community_router.invalidate_feed_api_cache()
        expected_ids = {deal_by_source[sname]}
        if sname in ("뽐뿌", "뽐뿌해외"):
            expected_ids.add(merged_id)

        for param_name in ("source", "platform"):
            res = client.get("/api/community/hot-deals", params={param_name: sname})
            assert res.status_code == 200
            returned_deals = res.json()["deals"]
            returned_ids = {d["id"] for d in returned_deals}
            assert returned_ids == expected_ids, (
                f"{param_name}={sname} 필터 불일치: 기대={expected_ids}, 실제={returned_ids}"
            )
            for item in returned_deals:
                assert sname in item["site_name"]
                assert sname in item["site_names"]

    # 3. 콤마 구분 다중 출처("뽐뿌해외,루리웹") 필터 검증 및 타 출처 배제
    r_multi = client.get("/api/community/hot-deals", params={"source": "뽐뿌해외,루리웹"})
    assert r_multi.status_code == 200
    multi_ids = {d["id"] for d in r_multi.json()["deals"]}
    assert multi_ids == {deal_by_source["뽐뿌해외"], deal_by_source["루리웹"], merged_id}


def test_t1_p1_05_hot_deals_cursor_pagination_over_300_items(v11b_env):
    """[T1-08 / P1-05] 320건 초과+DSU 병합 클러스터+종료딜 혼재 환경에서 limit=49 동률 타임스탬프 경계 커서 페이징 완주"""
    client = v11b_env.client
    base_utc = datetime(2026, 11, 19, 12, 0, 0)
    db = v11b_env.SessionCls()
    unmerged_ids: set[int] = set()
    closed_ids: set[int] = set()
    cluster_pair_ids: set[int] = set()
    tied_group_ids: set[int] = set()
    try:
        bulk_deals = []
        for idx in range(320):
            # 92~103번(12건)은 동일 indexed_at을 부여해 cluster_a/b 2행 시프트(raw-row vs grouped-deal)와 무관하게 limit=49 페이지 2/3 경계를 가로지르도록 배치
            ts_offset_min = 92 if 92 <= idx <= 103 else idx
            deal = models.Deal(
                source_community_id=(idx % 10) + 1,
                title=f"[커서몰{idx}] 유니크초고유상품_{idx:04d}_모델X{idx} ({10000 + idx * 100}원)",
                post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no={700000 + idx}",
                ecommerce_link=f"https://www.coupang.com/vp/products/{8000000 + idx}",
                shop_name="쿠팡",
                price=str(10000 + idx * 100),
                currency="KRW",
                category="가전제품",
                base_product_name=f"유니크초고유상품_{idx:04d}",
                model_code=f"UNIQ{idx:04d}",
                indexed_at=base_utc - timedelta(minutes=ts_offset_min),
                is_closed=False,
                honey_score=50,
            )
            bulk_deals.append(deal)

        # 중간 구간에 섞인 종료 딜 2건 (기본 커서 조회 시 제외되어야 하며 조기 종료/누락을 유발하면 안 됨)
        closed_mid_1 = models.Deal(
            source_community_id=1,
            title="[커서종료1] 중간종료딜 AlphaClosed (11,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=799901",
            ecommerce_link="https://www.coupang.com/vp/products/8999901",
            shop_name="쿠팡",
            price="11000",
            currency="KRW",
            category="가전제품",
            base_product_name="중간종료딜AlphaClosed",
            model_code="CLSD901",
            indexed_at=base_utc - timedelta(minutes=50, seconds=30),
            is_closed=True,
            honey_score=50,
        )
        closed_mid_2 = models.Deal(
            source_community_id=3,
            title="[커서종료2] 중간종료딜 BetaClosed (12,000원)",
            post_link="https://bbs.ruliweb.com/market/board/1020/read/799902",
            ecommerce_link="https://www.coupang.com/vp/products/8999902",
            shop_name="쿠팡",
            price="12000",
            currency="KRW",
            category="가전제품",
            base_product_name="중간종료딜BetaClosed",
            model_code="CLSD902",
            indexed_at=base_utc - timedelta(minutes=150, seconds=30),
            is_closed=True,
            honey_score=50,
        )

        # DSU 병합 클러스터 1쌍 (동일 시간대 페이지 1 내부에서 병합되어 논리 1건으로만 노출되어야 함)
        cluster_a = models.Deal(
            source_community_id=1,
            title="[클러스터] 동시병합노트북 CLUS999 (990,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=799951",
            ecommerce_link="https://www.coupang.com/vp/products/8999950",
            shop_name="쿠팡",
            price="990000",
            currency="KRW",
            category="PC용품",
            base_product_name="동시병합노트북",
            model_code="CLUS999",
            indexed_at=base_utc - timedelta(minutes=10, seconds=10),
            is_closed=False,
            honey_score=80,
        )
        cluster_b = models.Deal(
            source_community_id=3,
            title="[클러스터] 동시병합노트북 CLUS999 루리웹 (995,000원)",
            post_link="https://bbs.ruliweb.com/market/board/1020/read/799952",
            ecommerce_link="https://www.coupang.com/vp/products/8999950",
            shop_name="쿠팡",
            price="995000",
            currency="KRW",
            category="PC용품",
            base_product_name="동시병합노트북",
            model_code="CLUS999",
            indexed_at=base_utc - timedelta(minutes=10, seconds=20),
            is_closed=False,
            honey_score=80,
        )

        db.add_all(bulk_deals + [closed_mid_1, closed_mid_2, cluster_a, cluster_b])
        db.commit()
        unmerged_ids = {d.id for d in bulk_deals}
        tied_group_ids = {bulk_deals[i].id for i in range(92, 104)}
        closed_ids = {closed_mid_1.id, closed_mid_2.id}
        cluster_pair_ids = {cluster_a.id, cluster_b.id}
    finally:
        db.close()

    collected_ids: list[int] = []
    page_id_sets: list[set[int]] = []
    cursor_indexed_at = None
    cursor_id = None
    pages = 0

    while True:
        pages += 1
        assert pages <= 20, "커서 페이징 무한 루프 의심"
        params = {"limit": 49}
        if cursor_indexed_at is not None and cursor_id is not None:
            params["cursor_indexed_at"] = cursor_indexed_at
            params["cursor_id"] = cursor_id

        res = client.get("/api/community/hot-deals", params=params)
        assert res.status_code == 200
        body = res.json()
        page_deals = body["deals"]
        assert len(page_deals) <= 49
        cur_page_ids = [d["id"] for d in page_deals]
        collected_ids.extend(cur_page_ids)
        page_id_sets.append(set(cur_page_ids))

        assert body.get("next_cursor") == body.get("nextCursor")
        next_cur = body.get("next_cursor")
        if body["has_more"]:
            assert next_cur is not None
            assert next_cur["indexed_at"].endswith("Z")
            assert isinstance(next_cur["id"], int)
            cursor_indexed_at = next_cur["indexed_at"]
            cursor_id = next_cur["id"]
        else:
            assert next_cur is None
            break

    collected_set = set(collected_ids)
    # 1. 비병합 320건 누락/중복 없음 + 종료 딜 2건 제외
    assert len(collected_ids) == len(collected_set), f"커서 페이지 간 중복 딜 발생: {len(collected_ids)} vs {len(collected_set)}"
    assert unmerged_ids.issubset(collected_set), f"누락된 딜 발생: {len(unmerged_ids - collected_set)}건"
    assert collected_set.isdisjoint(closed_ids), f"종료 딜이 기본 커서 피드에 노출됨: {collected_set & closed_ids}"

    # 2. DSU 병합 클러스터는 정확히 1건으로만 중복 제거 노출
    assert len(collected_set & cluster_pair_ids) == 1, f"DSU 병합 클러스터 중복 또는 누락: {collected_set & cluster_pair_ids}"
    assert len(collected_ids) == 321

    # 3. 동률 타임스탬프(92~103번, 12건)가 페이지 경계를 가로질러 분할되었고 전량 누락/중복 없이 수집되었는지 확인
    pages_with_tied = [idx for idx, pset in enumerate(page_id_sets) if pset & tied_group_ids]
    assert len(pages_with_tied) >= 2, f"동률 타임스탬프 그룹이 페이지 경계를 가로지르지 못함: {pages_with_tied}"
    assert tied_group_ids.issubset(collected_set)


def test_t1_p1_05_sort_popular_offset_pagination(v11b_env):
    """[T1-08b / P1-05] sort='popular' offset/limit 페이지네이션(비클램핑 점수 내림차순 및 동률 점수 like_count 타이브레이커) 검증"""
    client = v11b_env.client
    base_utc = datetime(2026, 11, 19, 13, 0, 0)
    db = v11b_env.SessionCls()
    seeded_ids: set[int] = set()
    expected_ordered_ids: list[int] = []
    try:
        deals = []
        for idx in range(65):
            if idx < 6:
                # >= 100 핫딜 구간(6건): 응답 시 honey_score=100으로 클램핑되나 DB 점수와 like_count 모두 내림차순 정렬 유지
                if idx < 3:
                    score = 110
                    likes = [15, 12, 9][idx]
                else:
                    score = 105
                    likes = [6, 4, 2][idx - 3]
                is_super = True
            else:
                # 비클램핑 구간(59건, 39~96점): buy_count >= 8 핫딜 자동 승격을 피하기 위해 like_count는 < 8([6, 4, 2])로 부여
                group_idx = (idx - 6) // 3
                pos_in_group = (idx - 6) % 3
                score = 96 - group_idx * 3
                likes = [6, 4, 2][pos_in_group]
                is_super = False

            deals.append(
                models.Deal(
                    source_community_id=(idx % 10) + 1,
                    title=f"[인기오프셋{idx}] 인기순검증상품_{idx:03d}_POP{idx} ({20000 + idx * 100}원)",
                    post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no={780000 + idx}",
                    ecommerce_link=f"https://www.coupang.com/vp/products/{8800000 + idx}",
                    shop_name="쿠팡",
                    price=str(20000 + idx * 100),
                    currency="KRW",
                    category="가전제품",
                    base_product_name=f"인기순검증상품_{idx:03d}",
                    model_code=f"POP{idx:03d}",
                    indexed_at=base_utc - timedelta(minutes=idx),
                    is_closed=False,
                    is_super_hotdeal=is_super,
                    honey_score=score,
                    like_count=likes,
                    comment_count=3,
                )
            )
        db.add_all(deals)
        db.commit()
        seeded_ids = {d.id for d in deals}
        expected_ordered_ids = [d.id for d in deals]
    finally:
        db.close()

    collected_items: list[dict] = []
    collected_ids: list[int] = []
    collected_scores: list[int] = []

    for offset in (0, 20, 40, 60):
        res = client.get(
            "/api/community/hot-deals",
            params={"sort": "popular", "offset": offset, "limit": 20},
        )
        assert res.status_code == 200
        body = res.json()
        page_deals = body["deals"]
        expected_page_len = min(20, 65 - offset)
        assert len(page_deals) == expected_page_len, (
            f"offset={offset} 페이지 건수 불일치: {len(page_deals)} != {expected_page_len}"
        )
        assert body["has_more"] is ((offset + expected_page_len) < 65)

        collected_items.extend(page_deals)
        collected_ids.extend(d["id"] for d in page_deals)
        collected_scores.extend(int(d.get("honey_score") or 0) for d in page_deals)

    # 1. 전체 65건 중복/누락 없이 완주 및 결정론적 정렬 순서 일치 검증
    assert len(collected_ids) == 65, f"sort=popular offset 페이징 누락/중복: {len(collected_ids)}건"
    assert len(set(collected_ids)) == 65
    assert set(collected_ids) == seeded_ids
    assert collected_ids == expected_ordered_ids

    # 2. 비클램핑 구간 점수(39~96)가 100점으로 뭉개지지 않고 실제 다양하게 변별되며 내림차순인지 검증
    assert collected_scores == sorted(collected_scores, reverse=True)
    assert collected_scores[:6] == [100] * 6
    under_100_scores = [s for s in collected_scores if s < 100]
    assert len(under_100_scores) == 59
    assert set(under_100_scores) == {96 - g * 3 for g in range(20)}

    # 3. 동률 응답 honey_score 그룹 내 like_count 내림차순 타이브레이커 검증(페이지 경계 포함)
    idx_cursor = 0
    tie_groups_checked = 0
    while idx_cursor < len(collected_items):
        cur_score = int(collected_items[idx_cursor].get("honey_score") or 0)
        group_likes: list[int] = []
        while idx_cursor < len(collected_items) and int(collected_items[idx_cursor].get("honey_score") or 0) == cur_score:
            group_likes.append(int(collected_items[idx_cursor].get("like_count") or 0))
            idx_cursor += 1
        assert group_likes == sorted(group_likes, reverse=True), (
            f"동률 점수({cur_score}) 내 like_count 내림차순 타이브레이커 실패: {group_likes}"
        )
        if len(group_likes) >= 2:
            assert len(set(group_likes)) >= 2, f"동률 그룹 타이브레이커 변별력 부족: {group_likes}"
            tie_groups_checked += 1
    assert tie_groups_checked >= 20


def test_t1_p1_05_hot_deals_include_closed_only_hot_hide_closed_filters(v11b_env):
    """[T1-09 / P1-05] (include_closed, only_hot, hide_closed) 8조합 전체 및 honey_score=99/100·is_super_hotdeal 경계 검증"""
    client = v11b_env.client
    base_utc = datetime(2026, 11, 19, 10, 0, 0)

    id_active_99 = _seed_deal(
        v11b_env.SessionCls,
        title="[쇼핑A] 활성99점경계상품 Alpha99 (10,000원)",
        is_closed=False,
        honey_score=99,
        is_super_hotdeal=False,
        indexed_at=base_utc,
    )
    id_active_100 = _seed_deal(
        v11b_env.SessionCls,
        title="[쇼핑B] 활성100점핫딜 Beta100 (20,000원)",
        is_closed=False,
        honey_score=100,
        is_super_hotdeal=False,
        indexed_at=base_utc - timedelta(minutes=1),
    )
    id_active_super_0 = _seed_deal(
        v11b_env.SessionCls,
        title="[쇼핑S] 활성슈퍼핫딜영점 Super0 (25,000원)",
        is_closed=False,
        honey_score=0,
        is_super_hotdeal=True,
        indexed_at=base_utc - timedelta(minutes=2),
    )
    id_closed_hot = _seed_deal(
        v11b_env.SessionCls,
        title="[쇼핑C] 종료역대가핫딜 Gamma333 (30,000원)",
        is_closed=True,
        honey_score=150,
        is_super_hotdeal=True,
        indexed_at=base_utc - timedelta(minutes=3),
    )
    id_closed_normal = _seed_deal(
        v11b_env.SessionCls,
        title="[쇼핑D] 종료일반상품 Delta444 (40,000원)",
        is_closed=True,
        honey_score=10,
        is_super_hotdeal=False,
        indexed_at=base_utc - timedelta(minutes=4),
    )

    active_all = {id_active_99, id_active_100, id_active_super_0}
    active_hot = {id_active_100, id_active_super_0}
    all_deals = active_all | {id_closed_hot, id_closed_normal}
    all_hot = active_hot | {id_closed_hot}

    # (include_closed, only_hot, hide_closed) 2^3 = 8조합 전수 검증 (캐시 오염 없음 포함)
    matrix = [
        (False, False, False, active_all),
        (False, False, True, active_all),
        (False, True, False, active_hot),
        (False, True, True, active_hot),
        (True, False, False, all_deals),
        (True, False, True, active_all),
        (True, True, False, all_hot),
        (True, True, True, active_hot),
    ]
    for inc_c, only_h, hide_c, expected in matrix:
        res = client.get(
            "/api/community/hot-deals",
            params={
                "include_closed": inc_c,
                "only_hot": only_h,
                "hide_closed": hide_c,
            },
        )
        assert res.status_code == 200
        actual = {d["id"] for d in res.json()["deals"]}
        assert actual == expected, (
            f"조합 (include_closed={inc_c}, only_hot={only_h}, hide_closed={hide_c}) "
            f"불일치: 기대={expected}, 실제={actual}"
        )


def test_t1_p1_06_report_closed_threshold_3_transitions_and_hides_from_feed(v11b_env):
    """[T1-10 / P1-06] 3명 품절 제보 임계치→다중 피드 캐시 즉시 무효화→4번째 제보(report_count=4) 검증"""
    client = v11b_env.client
    deal_id = _seed_deal(
        v11b_env.SessionCls,
        title="[네이버] 품절임박 커피머신 특가 (149,000원)",
        is_closed=False,
        honey_score=130,
    )

    _, _, h1 = _register_device(client)
    _, _, h2 = _register_device(client)
    _, _, h3 = _register_device(client)
    _, _, h4 = _register_device(client)

    # 1. 사전 다중 캐시 워밍 (기본 피드, top-hot-deals, sort=popular, include_closed=True)
    for path, params in (
        ("/api/community/hot-deals", None),
        ("/api/community/top-hot-deals", None),
        ("/api/community/hot-deals", {"sort": "popular"}),
        ("/api/community/hot-deals", {"include_closed": True}),
    ):
        pre = client.get(path, params=params)
        assert pre.status_code == 200
        assert any(d["id"] == deal_id and d["is_closed"] is False for d in pre.json()["deals"]), (
            f"사전 캐시 워밍 누락 ({path}, {params})"
        )

    # 1차 제보
    r1 = client.post(f"/api/community/deals/{deal_id}/report-closed", headers=h1)
    assert r1.status_code == 200
    b1 = r1.json()
    assert b1["status"] == "ok"
    assert b1["deal_id"] == deal_id and b1["dealId"] == deal_id
    assert b1["report_count"] == 1 and b1["reportCount"] == 1
    assert b1["is_closed"] is False and b1["isClosed"] is False
    assert b1["threshold"] == 3

    # 2차 제보
    r2 = client.post(f"/api/community/deals/{deal_id}/report-closed", headers=h2)
    assert r2.status_code == 200
    b2 = r2.json()
    assert b2["report_count"] == 2
    assert b2["is_closed"] is False

    # 3차 제보 -> 임계치(3) 도달, 자동 종료 전환
    r3 = client.post(f"/api/community/deals/{deal_id}/report-closed", headers=h3)
    assert r3.status_code == 200
    b3 = r3.json()
    assert b3["report_count"] == 3 and b3["reportCount"] == 3
    assert b3["is_closed"] is True and b3["isClosed"] is True
    assert b3["threshold"] == 3

    # 상세 조회 동기화 확인
    detail_res = client.get(f"/api/community/deals/{deal_id}")
    assert detail_res.status_code == 200
    detail = detail_res.json()
    assert detail["is_closed"] is True
    assert detail["report_count"] == 3 and detail["reportCount"] == 3

    # 워밍된 모든 캐시 경로(기본, top-hot-deals, sort=popular, include_closed=True) 즉시 무효화 확인
    for path, params in (
        ("/api/community/hot-deals", None),
        ("/api/community/top-hot-deals", None),
        ("/api/community/hot-deals", {"sort": "popular"}),
    ):
        after = client.get(path, params=params)
        assert after.status_code == 200
        assert all(d["id"] != deal_id for d in after.json()["deals"]), (
            f"품절 전환 후 캐시 즉시 무효화 실패 ({path}, {params})"
        )

    closed_feed = client.get("/api/community/hot-deals", params={"include_closed": True})
    assert closed_feed.status_code == 200
    assert any(d["id"] == deal_id and d["is_closed"] is True for d in closed_feed.json()["deals"])

    # 4번째 별도 기기 제보 -> 200 OK, report_count=4, is_closed=True 유지 / 기존 기기 재제보는 409
    r4 = client.post(f"/api/community/deals/{deal_id}/report-closed", headers=h4)
    assert r4.status_code == 200
    b4 = r4.json()
    assert b4["report_count"] == 4 and b4["reportCount"] == 4
    assert b4["is_closed"] is True and b4["isClosed"] is True
    assert client.post(f"/api/community/deals/{deal_id}/report-closed", headers=h1).status_code == 409


def test_t1_p1_12_search_fallback_list_flag_and_p1_04_morning_digest(v11b_env, monkeypatch):
    """[T1-11 / P1-12 & P1-04] 목록·상세 isSearchFallback 노출 & 전용 키워드 선등록 기반 야간 DND→08:00 요약 발송"""
    client = v11b_env.client
    webpush_calls, fcm_batches = _mock_push_transports(monkeypatch)

    # Part A (P1-12): 직접 URL 핫딜 vs 검색 폴백(post_link 동일) vs 빈 링크 모음전 핫딜
    direct_id = _seed_deal(
        v11b_env.SessionCls,
        title="[쿠팡] 스탠리 퀜처 텀블러 직접링크 (32,000원)",
        ecommerce_link="https://www.coupang.com/vp/products/911001",
        shop_name="쿠팡",
        price="32000",
        honey_score=120,
    )
    fallback_post = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=911002"
    fallback_id = _seed_deal(
        v11b_env.SessionCls,
        title="[쿠팡] 스탠리 클래식 보온병 폴백 (35,000원)",
        post_link=fallback_post,
        ecommerce_link=fallback_post,
        shop_name="쿠팡",
        price="35000",
        honey_score=130,
    )
    empty_bundle_id = _seed_deal(
        v11b_env.SessionCls,
        title="[네이버] 스탠리 모음전 텀블러 선택 특가 (29,000원)",
        post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=911003",
        ecommerce_link="",
        shop_name="네이버",
        price="29000",
        honey_score=115,
    )

    for endpoint in ("/api/community/hot-deals", "/api/community/top-hot-deals"):
        res = client.get(endpoint)
        assert res.status_code == 200
        by_id = {d["id"]: d for d in res.json()["deals"]}
        assert direct_id in by_id and fallback_id in by_id and empty_bundle_id in by_id
        for did, expected_flag in ((direct_id, False), (fallback_id, True), (empty_bundle_id, True)):
            item = by_id[did]
            assert isinstance(item.get("is_search_fallback"), bool)
            assert item["is_search_fallback"] == item.get("isSearchFallback")
            assert item["is_search_fallback"] is expected_flag

    # 상세 엔드포인트(deals/{id})에서도 is_search_fallback + isSearchFallback camelCase 동시 노출 확인
    for did, expected_flag in ((direct_id, False), (fallback_id, True), (empty_bundle_id, True)):
        det_res = client.get(f"/api/community/deals/{did}")
        assert det_res.status_code == 200
        det = det_res.json()
        assert isinstance(det.get("is_search_fallback"), bool)
        assert det.get("isSearchFallback") == det["is_search_fallback"]
        assert det["is_search_fallback"] is expected_flag

    # Part B (P1-04): Part A("스탠리")와 분리된 전용 키워드("보스스피커")를 먼저 등록 후 야간 딜 시드
    # (match_existing_deals_for_keyword의 선적재 dnd_suppressed=False 충돌 원천 차단)
    dev_uuid, _, headers = _register_device(
        client,
        fcm_token="fcm-dnd-digest-1",
        dnd_enabled=True,
        dnd_start_time="22:00",
        dnd_end_time="08:00",
        web_push_endpoint="https://push.example/digest-1",
    )
    assert client.post("/api/push/keywords", json={"keyword": "보스스피커"}, headers=headers).status_code == 200

    night_deal_1 = _seed_deal(
        v11b_env.SessionCls,
        title="[롯데온] 보스스피커 사운드링크 플렉스 특가 (149,000원)",
        price="149000",
        honey_score=110,
    )
    night_deal_2 = _seed_deal(
        v11b_env.SessionCls,
        title="[11번가] 보스스피커 사운드링크 미니2 SE (159,000원)",
        price="159000",
        honey_score=115,
    )

    night_kst = datetime(2026, 11, 19, 23, 30, 0, tzinfo=KST)
    s1 = push_worker.background_trigger_keyword_alarms(
        night_deal_1,
        session_factory=v11b_env.SessionCls,
        now_kst=night_kst,
    )
    s2 = push_worker.background_trigger_keyword_alarms(
        night_deal_2,
        session_factory=v11b_env.SessionCls,
        now_kst=night_kst,
    )
    assert s1["dropped_dnd"] >= 1 and s2["dropped_dnd"] >= 1
    assert len(fcm_batches) == 0 and len(webpush_calls) == 0

    db = v11b_env.SessionCls()
    try:
        alerts = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == dev_uuid).all()
        assert len(alerts) == 2
        for al in alerts:
            assert getattr(al, "dnd_suppressed", None) is True
            assert getattr(al, "digest_sent_at", "MISSING") is None
    finally:
        db.close()

    # 아침 08:00 KST 요약 배치 실행
    morning_kst = datetime(2026, 11, 20, 8, 0, 0, tzinfo=KST)
    _run_morning_digest_job(v11b_env.SessionCls, morning_kst)

    # 기기당 1건의 요약 푸시만 발송되어야 함 (FCM 1건 + WebPush 1건)
    sent_msgs = [m for b in fcm_batches for m in b]
    assert [m.token for m in sent_msgs] == ["fcm-dnd-digest-1"]
    assert len(webpush_calls) == 1
    payload_texts = _extract_push_payload_texts(webpush_calls, fcm_batches)
    assert any("밤사이 놓친 핫딜 2건" in txt for txt in payload_texts), f"요약 문구 누락: {payload_texts}"

    db = v11b_env.SessionCls()
    try:
        alerts_after = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == dev_uuid).all()
        assert all(getattr(al, "digest_sent_at", None) is not None for al in alerts_after)
    finally:
        db.close()


# ===========================================================================
# Tier 2: 경계 / 보안 / 에러 케이스 (13개 테스트)
# ===========================================================================


def test_t2_p1_01_pairing_code_expired_returns_400(v11b_env):
    """[T2-01 / P1-01] 만료된 6자리 페어링 코드 제출 시 400 Bad Request 및 병합 0건"""
    client = v11b_env.client
    _, _, headers_a = _register_device(client)
    _, _, headers_b = _register_device(client)

    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "만료테스트상품", "target_price": 10000},
        headers=headers_a,
    ).status_code == 200

    issue_res = client.post("/api/auth/pairing-code", headers=headers_a)
    assert issue_res.status_code == 200
    code = issue_res.json()["code"]

    pairing_table = _extract_pairing_table()
    assert pairing_table is not None, "M1 미구현: 페어링 코드 테이블이 필요합니다."

    db = v11b_env.SessionCls()
    try:
        past_dt = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(minutes=15)
        db.execute(
            pairing_table.update()
            .where(pairing_table.c.code == code)
            .values(expires_at=past_dt)
        )
        db.commit()
    finally:
        db.close()

    claim_res = client.post("/api/auth/claim-pairing-code", json={"code": code}, headers=headers_b)
    assert claim_res.status_code == 400
    assert client.get("/api/wishlist/", headers=headers_b).json() == []


def test_t2_p1_01_pairing_code_5_wrong_attempts_destroys_code(v11b_env):
    """[T2-02 / P1-01] 동일 기기 5회 오답 시 시도 기기 잠금/차단 & 제3자 코드 살아있음(전역 DoS 차단 검증)"""
    client = v11b_env.client
    _, _, headers_a = _register_device(client)
    _, _, headers_x = _register_device(client)
    _, _, headers_b = _register_device(client)
    _, _, headers_y = _register_device(client)

    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "기기A비밀상품", "target_price": 100000},
        headers=headers_a,
    ).status_code == 200
    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "기기X정상상품", "target_price": 200000},
        headers=headers_x,
    ).status_code == 200

    issue_a = client.post("/api/auth/pairing-code", headers=headers_a)
    assert issue_a.status_code == 200
    real_code = issue_a.json()["code"]

    issue_x = client.post("/api/auth/pairing-code", headers=headers_x)
    assert issue_x.status_code == 200
    other_code = issue_x.json()["code"]

    # 두 유효 코드와 겹치지 않는 오답 5종 생성
    forbidden = {real_code, other_code}
    wrong_codes = [c for c in ("000000", "111111", "222222", "333333", "444444", "555555", "666666") if c not in forbidden][:5]

    b_ip_headers = {**headers_b, "X-Forwarded-For": "203.0.113.10"}
    for wc in wrong_codes:
        w_res = client.post(
            "/api/auth/claim-pairing-code",
            json={"code": wc},
            headers=b_ip_headers,
        )
        assert w_res.status_code in (400, 404, 429), f"예상치 못한 오답 응답: {w_res.status_code}"

    # 6회째: 동일 기기 B가 기기 A의 유효 real_code를 제출해도 잠금/차단되어 병합되지 않아야 함
    c_res = client.post(
        "/api/auth/claim-pairing-code",
        json={"code": real_code},
        headers=b_ip_headers,
    )
    assert c_res.status_code in (400, 403, 429), f"5회 오답 후 동일 기기 잠금/차단 실패: {c_res.status_code}"
    assert client.get("/api/wishlist/", headers=headers_b).json() == []

    # 전역 DoS 미발생 검증: 제3자 기기 X의 other_code는 DB에서 used_at is None으로 살아있어야 함
    pairing_table = _extract_pairing_table()
    assert pairing_table is not None, "M1 미구현: 페어링 코드 테이블이 필요합니다."
    db = v11b_env.SessionCls()
    try:
        x_row = db.execute(
            pairing_table.select().where(pairing_table.c.code == other_code)
        ).mappings().first()
        assert x_row is not None
        assert x_row["used_at"] is None, f"타인의 유효 코드가 오답 시도로 오염/폐기됨(전역 DoS): {x_row}"
    finally:
        db.close()

    # 별도 IP의 정상 기기 Y는 제3자 기기 X의 other_code를 정상 병합 가능해야 함
    y_res = client.post(
        "/api/auth/claim-pairing-code",
        json={"code": other_code},
        headers={**headers_y, "X-Forwarded-For": "203.0.113.12"},
    )
    assert y_res.status_code == 200
    assert any(item["keyword"] == "기기X정상상품" for item in client.get("/api/wishlist/", headers=headers_y).json())


def test_t2_p1_01_pairing_code_bruteforce_sliding_window_429(v11b_env):
    """[T2-03 / P1-01] 동일 기기의 단시간 반복 오답 시도 시 슬라이딩 윈도우 429 차단"""
    client = v11b_env.client
    _, _, headers_a = _register_device(client)
    _, _, headers_b = _register_device(client)

    issue_res = client.post("/api/auth/pairing-code", headers=headers_a)
    assert issue_res.status_code == 200
    real_code = issue_res.json()["code"]
    wrong_code = "123456" if real_code != "123456" else "654321"

    statuses = []
    for _ in range(20):
        res = client.post(
            "/api/auth/claim-pairing-code",
            json={"code": wrong_code},
            headers={**headers_b, "X-Forwarded-For": "203.0.113.20"},
        )
        statuses.append(res.status_code)
        if res.status_code == 429:
            break

    assert 429 in statuses, f"브루트포스 슬라이딩 윈도우 429 미발동: {statuses}"


def test_t2_p1_01_pairing_and_link_device_idor_401_403(v11b_env):
    """[T2-04 / P1-01] X-Device-Token·서명변조/탈퇴 Bearer 401 및 타 계정 기기 하이재킹·IDOR 차단"""
    client = v11b_env.client
    uuid_a, _, headers_a = _register_device(client)
    _, _, headers_b = _register_device(client)
    _, _, headers_x = _register_device(client)
    _, _, headers_y = _register_device(client)

    forged_headers = {"X-Device-Token": "v1.Zm9yZ2Vk.0000000000000000000000000000000000000000000000000000000000000000"}

    # 1. X-Device-Token 누락/변조 -> 401
    assert client.post("/api/auth/pairing-code").status_code == 401
    assert client.post("/api/auth/pairing-code", headers=forged_headers).status_code == 401
    assert client.post("/api/auth/claim-pairing-code", json={"code": "123456"}).status_code == 401
    assert client.post("/api/auth/claim-pairing-code", json={"code": "123456"}, headers=forged_headers).status_code == 401
    assert client.post("/api/auth/link-device", headers=headers_b).status_code == 401

    # 2. 유효 Bearer + 1바이트 서명 변조 Bearer + 유효 Bearer+위조 X-Device-Token -> 401
    uname = f"idor_{uuid.uuid4().hex[:8]}"
    s_res = client.post(
        "/api/auth/signup",
        json={"username": uname, "password": "IdorPass!123", "nickname": f"닉_{uname}"},
        headers=headers_a,
    )
    assert s_res.status_code == 200
    token = s_res.json()["access_token"]

    assert client.post("/api/auth/link-device", headers={"Authorization": f"Bearer {token}"}).status_code == 401
    assert client.post(
        "/api/auth/link-device",
        headers={"Authorization": "Bearer invalid.jwt.or.hmac", **headers_b},
    ).status_code == 401

    # 마지막 1글자만 변조한 Bearer 토큰 거부 확인
    tampered_char = "0" if token[-1] != "0" else "1"
    tampered_token = token[:-1] + tampered_char
    assert client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {tampered_token}", **headers_b},
    ).status_code == 401

    # 유효 Bearer + 변조 X-Device-Token 거부 확인
    assert client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {token}", **forged_headers},
    ).status_code == 401

    # 3. 이미 User A 계정에 귀속된 기기 X를 다른 User 2가 link-device로 하이재킹 시도 -> 403/409 거부
    assert client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {token}", **headers_x},
    ).status_code == 200

    uname2 = f"idor2_{uuid.uuid4().hex[:8]}"
    s2_res = client.post(
        "/api/auth/signup",
        json={"username": uname2, "password": "IdorPass2!123", "nickname": f"닉2_{uname2}"},
        headers=headers_y,
    )
    assert s2_res.status_code == 200
    token2 = s2_res.json()["access_token"]

    hijack_res = client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {token2}", **headers_x},
    )
    assert hijack_res.status_code in (403, 409), f"타 계정 귀속 기기 하이재킹 허용됨: {hijack_res.status_code}"

    # 4. 회원 탈퇴(/withdraw) 후 기존 유효 Bearer 토큰으로 link-device 시도 -> 401 거부
    w_res = client.post(
        "/api/auth/withdraw",
        json={"username": uname2, "password": "IdorPass2!123"},
    )
    assert w_res.status_code == 200
    assert client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {token2}", **headers_b},
    ).status_code == 401

    # 5. 타인 device_uuid 접근(IDOR) -> 403
    assert client.get("/api/wishlist/", params={"user_id": uuid_a}, headers=headers_b).status_code == 403
    assert client.patch("/api/push/dnd", json={"device_uuid": uuid_a, "dnd_enabled": True}, headers=headers_b).status_code == 403


def test_t2_p1_01_conflict_merge_different_wishlist_price_and_alerts_dnd_json(v11b_env):
    """[T2-04b / P1-01] 상이 target_price Wishlist·중복 NotificationAlert·dnd_settings_json·비활성 키워드 충돌 병합(500 없음)"""
    client = v11b_env.client
    _, _, headers_a = _register_device(client)
    uuid_b, _, headers_b = _register_device(client)

    dnd_json = '{"mon":{"enabled":true,"start":"22:00","end":"08:00"},"tue":{"enabled":false,"start":"22:00","end":"08:00"}}'

    # 기기 A: target_price=50000, 동일 제목 알림, 비활성 키워드, dnd_settings_json
    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "충돌노트북", "target_price": 50000},
        headers=headers_a,
    ).status_code == 200
    assert client.post(
        "/api/users/notifications",
        json={"title": "동일제목알림", "keyword": "충돌노트북", "deal_url": "https://example.com/1"},
        headers=headers_a,
    ).status_code == 200
    assert client.post("/api/push/keywords", json={"keyword": "비활성테스트키워드"}, headers=headers_a).status_code == 200
    assert client.post("/api/push/keywords/toggle", json={"keyword": "비활성테스트키워드"}, headers=headers_a).status_code == 200
    assert client.patch(
        "/api/push/dnd",
        json={"dnd_enabled": True, "dnd_start_time": "22:00", "dnd_end_time": "08:00", "dnd_settings_json": dnd_json},
        headers=headers_a,
    ).status_code == 200

    # 기기 B: 같은 키워드 "충돌노트북"이지만 상이한 target_price=40000 + 동일 제목 알림 선보유
    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "충돌노트북", "target_price": 40000},
        headers=headers_b,
    ).status_code == 200
    assert client.post(
        "/api/users/notifications",
        json={"title": "동일제목알림", "keyword": "충돌노트북", "deal_url": "https://example.com/1"},
        headers=headers_b,
    ).status_code == 200

    code = client.post("/api/auth/pairing-code", headers=headers_a).json()["code"]
    claim_res = client.post("/api/auth/claim-pairing-code", json={"code": code}, headers=headers_b)
    assert claim_res.status_code == 200, f"충돌 병합 시 500 등 오류 발생: {claim_res.status_code} {claim_res.text}"

    # Wishlist 중복 없이 1건 유지 (IntegrityError 없음)
    b_wl = client.get("/api/wishlist/", headers=headers_b).json()
    matched_wl = [item for item in b_wl if item["keyword"] == "충돌노트북"]
    assert len(matched_wl) == 1
    assert matched_wl[0]["target_price"] in (40000, 50000)

    # NotificationAlert 동일 제목 중복 없이 1건 유지
    b_notifs = client.get("/api/users/notifications", headers=headers_b).json()
    assert sum(1 for n in b_notifs if n["title"] == "동일제목알림") == 1

    # dnd_settings_json 동기화 확인
    b_kw = client.get("/api/push/keywords", headers=headers_b).json()
    assert b_kw["dnd_enabled"] is True
    assert b_kw["dnd_settings_json"] is not None
    assert json.loads(b_kw["dnd_settings_json"]) == json.loads(dnd_json)
    assert "비활성테스트키워드" not in b_kw["keywords"]


def test_t2_p1_01_pairing_self_merge_400_and_double_claim_409(v11b_env):
    """[T2-05 / P1-01] 자기 자신 병합 400 차단 및 일회성 코드 이중 사용 409 Conflict 차단"""
    client = v11b_env.client
    _, _, headers_a = _register_device(client)
    _, _, headers_b = _register_device(client)
    _, _, headers_c = _register_device(client)

    issue_res = client.post("/api/auth/pairing-code", headers=headers_a)
    assert issue_res.status_code == 200
    code = issue_res.json()["code"]

    # 자기 자신 병합 -> 400
    self_res = client.post("/api/auth/claim-pairing-code", json={"code": code}, headers=headers_a)
    assert self_res.status_code == 400

    # 기기 B 정상 병합 -> 200
    ok_res = client.post("/api/auth/claim-pairing-code", json={"code": code}, headers=headers_b)
    assert ok_res.status_code == 200

    # 기기 C가 동일 코드 재사용 -> 409 Conflict
    dup_res = client.post("/api/auth/claim-pairing-code", json={"code": code}, headers=headers_c)
    assert dup_res.status_code == 409


def test_t2_p1_01_pbkdf2_new_hash_and_legacy_sha256_transparent_upgrade(v11b_env):
    """[T2-06 / P1-01] 신규 가입 PBKDF2-HMAC-SHA256 해싱 및 레거시 무솔트 SHA-256 로그인 투명 업그레이드"""
    client = v11b_env.client

    # 1. 신규 가입자 PBKDF2 해시 확인
    new_uname = f"pbkdf2_{uuid.uuid4().hex[:8]}"
    res_new = client.post(
        "/api/auth/signup",
        json={"username": new_uname, "password": "MySecurePassword!1", "nickname": f"신규_{new_uname}"},
    )
    assert res_new.status_code == 200

    db = v11b_env.SessionCls()
    try:
        u_new = db.query(models.User).filter(models.User.username == new_uname).first()
        assert u_new is not None
        assert "pbkdf2" in u_new.password_hash.lower()
        assert len(u_new.password_hash) > 64
        assert ("$" in u_new.password_hash) or (":" in u_new.password_hash)
    finally:
        db.close()

    # 2. 레거시 64자 SHA-256 해시 계정 직접 시드 후 로그인 업그레이드 검증
    legacy_uname = f"legacy_{uuid.uuid4().hex[:8]}"
    legacy_pw = "LegacySecret#2026"
    legacy_sha256 = hashlib.sha256(legacy_pw.encode()).hexdigest()
    assert len(legacy_sha256) == 64

    db = v11b_env.SessionCls()
    try:
        db.add(
            models.User(
                username=legacy_uname,
                password_hash=legacy_sha256,
                nickname=f"레거시_{legacy_uname}",
            )
        )
        db.commit()
    finally:
        db.close()

    # 오답 비밀번호 거부
    bad_login = client.post("/api/auth/login", json={"username": legacy_uname, "password": "WrongPassword!"})
    assert bad_login.status_code in (400, 401)

    # 정답 로그인 -> 200 + access_token + DB 해시 PBKDF2 업그레이드
    ok_login = client.post("/api/auth/login", json={"username": legacy_uname, "password": legacy_pw})
    assert ok_login.status_code == 200
    ok_body = ok_login.json()
    assert isinstance(ok_body.get("access_token"), str) and len(ok_body["access_token"]) > 10

    db = v11b_env.SessionCls()
    try:
        u_leg = db.query(models.User).filter(models.User.username == legacy_uname).first()
        assert u_leg is not None
        assert u_leg.password_hash != legacy_sha256
        assert "pbkdf2" in u_leg.password_hash.lower()
    finally:
        db.close()

    # 업그레이드 후 2차 로그인도 정상 성공해야 함
    ok_login_2 = client.post("/api/auth/login", json={"username": legacy_uname, "password": legacy_pw})
    assert ok_login_2.status_code == 200


def test_t2_p1_06_report_closed_duplicate_409_auth_401_missing_404(v11b_env):
    """[T2-07 / P1-06] report-closed 미인증 401, 미존재 딜 404, 동일 기기 중복 제보 409"""
    client = v11b_env.client
    deal_id = _seed_deal(v11b_env.SessionCls)
    _, _, headers_a = _register_device(client)

    # 미인증 / 위조 토큰 -> 401
    assert client.post(f"/api/community/deals/{deal_id}/report-closed").status_code == 401
    assert client.post(
        f"/api/community/deals/{deal_id}/report-closed",
        headers={"X-Device-Token": "v1.bad.token"},
    ).status_code == 401

    # 미존재 딜 -> 404
    assert client.post("/api/community/deals/999999/report-closed", headers=headers_a).status_code == 404

    # 1차 정상 제보 -> 200, 동일 기기 2차 제보 -> 409 Conflict
    first = client.post(f"/api/community/deals/{deal_id}/report-closed", headers=headers_a)
    assert first.status_code == 200
    assert first.json()["report_count"] == 1

    second = client.post(f"/api/community/deals/{deal_id}/report-closed", headers=headers_a)
    assert second.status_code == 409

    detail = client.get(f"/api/community/deals/{deal_id}")
    assert detail.status_code == 200
    assert detail.json()["report_count"] == 1


def test_t2_p1_06_report_closed_rate_limit_429(v11b_env):
    """[T2-08 / P1-06] 단일 기기의 report-closed 연속 폭주 제보 시 시간당 레이트리밋 429 차단"""
    client = v11b_env.client
    _, _, headers_spammer = _register_device(client)
    deal_ids = [_seed_deal(v11b_env.SessionCls) for _ in range(25)]

    statuses = []
    spam_headers = {**headers_spammer, "X-Forwarded-For": "203.0.113.30"}
    for did in deal_ids:
        res = client.post(f"/api/community/deals/{did}/report-closed", headers=spam_headers)
        statuses.append(res.status_code)
        if res.status_code == 429:
            break

    assert 429 in statuses, f"report-closed 과다 제보 429 미발동: {statuses}"


def test_t2_p1_03_body_images_blocks_xss_and_ssrf_urls(v11b_env):
    """[T2-09 / P1-03] body_images 상세/목록 추출 시 RFC1918·로컬·IPv6-mapped·XSS·플레이스홀더 URL 전면 차단"""
    client = v11b_env.client
    malicious_html = (
        '<img src="javascript:alert(1)">'
        '<img src="vbscript:msgbox(1)">'
        '<img src="data:text/html,<script>alert(1)</script>">'
        '<img src="http://127.0.0.1/internal.png">'
        '<img src="http://10.0.0.1/priv10.png">'
        '<img src="http://172.16.0.1/priv172.png">'
        '<img src="http://192.168.1.1/priv192.png">'
        '<img src="http://localhost/local.png">'
        '<img src="http://0.0.0.0/zero.png">'
        '<img src="http://[::ffff:127.0.0.1]/v6mapped.png">'
        '<img src="http://169.254.169.254/latest/meta-data/iam.png">'
        '<img src="http://[::1]/loopback.png">'
        '<img src="https://evil.com/<script>x</script>.jpg">'
        '<img src="https://cdn.example.com/bad.jpg" onerror="alert(1)">'
        '<img src="https://cdn.example.com/inject.jpg?q=onerror=alert(1)">'
        '<img src="https://user:pass@cdn.example.com/secret.jpg">'
        '<img src="https://cdn.example.com/no_image.svg">'
        '<img src="https://cdn.example.com/dot03.gif">'
        '<img src="https://cdn.example.com/ok.jpg">'
    )
    deal_id = _seed_deal(
        v11b_env.SessionCls,
        image_url="",
        content_html=malicious_html,
    )

    dangerous_tokens = (
        "javascript:",
        "vbscript:",
        "data:",
        "127.0.0.1",
        "10.0.0.1",
        "172.16.0.1",
        "192.168.1.1",
        "localhost",
        "0.0.0.0",
        "::ffff:127.0.0.1",
        "169.254.169.254",
        "::1",
        "<script",
        "onerror=",
        "user:pass@",
        "evil.com",
        "no_image.svg",
        "dot03.gif",
    )

    res = client.get(f"/api/community/deals/{deal_id}")
    assert res.status_code == 200
    body = res.json()
    imgs = body.get("body_images")
    assert isinstance(imgs, list)
    assert body.get("bodyImages") == imgs

    unquoted_detail = [urllib.parse.unquote(u) for u in imgs]
    assert any("https://cdn.example.com/ok.jpg" in u for u in unquoted_detail), f"정상 이미지 누락: {imgs}"
    for u in unquoted_detail:
        low = u.lower()
        for tok in dangerous_tokens:
            assert tok not in low, f"위험 URL({tok})이 상세 body_images에 노출됨: {u!r}"

    # hot-deals 목록 아이템에서도 동일하게 위험 URL 차단 및 정상 URL 노출 검증
    list_res = client.get("/api/community/hot-deals")
    assert list_res.status_code == 200
    list_item = next((d for d in list_res.json()["deals"] if d["id"] == deal_id), None)
    assert list_item is not None
    list_imgs = list_item.get("body_images")
    assert isinstance(list_imgs, list) and list_item.get("bodyImages") == list_imgs
    unquoted_list = [urllib.parse.unquote(u) for u in list_imgs]
    assert any("https://cdn.example.com/ok.jpg" in u for u in unquoted_list)
    for u in unquoted_list:
        low = u.lower()
        for tok in dangerous_tokens:
            assert tok not in low, f"위험 URL({tok})이 목록 body_images에 노출됨: {u!r}"


def test_t2_p1_02_proxy_image_ssrf_and_invalid_upstream_404_no_store(v11b_env, monkeypatch):
    """[T2-10 / P1-02] proxy_image 사설IP/IPv6-mapped/비HTTP 스킴 SSRF 차단(외부 fetch 0회, 404 no-store)"""
    client = v11b_env.client
    fetch_calls: list[str] = []

    async def _fail_if_called(url, **kwargs):
        fetch_calls.append(url)
        return httpx.Response(200, content=VALID_PNG_BYTES, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _fail_if_called)

    ssrf_urls = [
        "http://127.0.0.1/a.png",
        "http://169.254.169.254/latest/meta-data/",
        "http://[::ffff:127.0.0.1]/a.png",
        "javascript:alert(1)",
        "file:///etc/passwd",
    ]
    for bad_url in ssrf_urls:
        res = client.get("/api/proxy-image", params={"url": bad_url})
        assert res.status_code == 404, f"SSRF URL 미차단 ({bad_url}): {res.status_code}"
        assert res.headers.get("cache-control") == "no-store"

    assert fetch_calls == [], f"SSRF 차단 전 외부 HTTP 요청 발생: {fetch_calls}"


def test_t2_p1_04_morning_digest_idempotent_rerun_zero_duplicates(v11b_env, monkeypatch):
    """[T2-11 / P1-04] 아침 요약 배치 멱등 재실행 중복 발송 0건 & DND 비활성 기기 미집계 검증"""
    client = v11b_env.client
    webpush_calls, fcm_batches = _mock_push_transports(monkeypatch)

    uuid_dnd, _, h_dnd = _register_device(
        client,
        fcm_token="fcm-dnd-idem",
        dnd_enabled=True,
        dnd_start_time="22:00",
        dnd_end_time="08:00",
        web_push_endpoint="https://push.example/dnd-idem",
    )
    uuid_normal, _, h_norm = _register_device(
        client,
        fcm_token="fcm-norm-idem",
        dnd_enabled=False,
        night_push_consent=True,
        web_push_endpoint="https://push.example/norm-idem",
    )

    assert client.post("/api/push/keywords", json={"keyword": "소니헤드폰"}, headers=h_dnd).status_code == 200
    assert client.post("/api/push/keywords", json={"keyword": "소니헤드폰"}, headers=h_norm).status_code == 200

    d1 = _seed_deal(v11b_env.SessionCls, title="[11번가] 소니헤드폰 WH1000XM5 1차 (299,000원)")
    d2 = _seed_deal(v11b_env.SessionCls, title="[지마켓] 소니헤드폰 WH1000XM5 2차 (289,000원)")

    night_kst = datetime(2026, 11, 19, 23, 45, 0, tzinfo=KST)
    push_worker.background_trigger_keyword_alarms(d1, session_factory=v11b_env.SessionCls, now_kst=night_kst)
    push_worker.background_trigger_keyword_alarms(d2, session_factory=v11b_env.SessionCls, now_kst=night_kst)

    # 야간 일반 기기는 즉시 2건 발송, DND 기기는 억제 적재됨
    fcm_batches.clear()
    webpush_calls.clear()

    morning_kst = datetime(2026, 11, 20, 8, 0, 0, tzinfo=KST)
    _run_morning_digest_job(v11b_env.SessionCls, morning_kst)

    # 1차 실행: DND 기기에게만 1건 발송, 일반 기기(uuid_normal)에게는 발송 0건
    sent_msgs = [m for b in fcm_batches for m in b]
    assert [m.token for m in sent_msgs] == ["fcm-dnd-idem"]
    assert len(webpush_calls) == 1
    sub_info = webpush_calls[0]["subscription_info"]
    sub_dict = json.loads(sub_info) if isinstance(sub_info, str) else sub_info
    assert sub_dict["endpoint"] == "https://push.example/dnd-idem"

    # 2차 재실행: digest_sent_at 멱등 가드로 추가 발송 0건
    fcm_batches.clear()
    webpush_calls.clear()
    _run_morning_digest_job(v11b_env.SessionCls, morning_kst + timedelta(minutes=5))
    assert len(fcm_batches) == 0
    assert len(webpush_calls) == 0

    db = v11b_env.SessionCls()
    try:
        norm_alerts = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == uuid_normal).all()
        assert len(norm_alerts) == 2
        assert all(getattr(a, "dnd_suppressed", False) is False for a in norm_alerts)
        dnd_alerts = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == uuid_dnd).all()
        assert len(dnd_alerts) == 2
        assert all(getattr(a, "digest_sent_at", None) is not None for a in dnd_alerts)
    finally:
        db.close()


def test_t2_p1_04_custom_dnd_end_time_0900_and_legacy_sqlite_alter_migration(v11b_env, monkeypatch):
    """[T2-11b / P1-04] 커스텀 dnd_end_time='09:00' 보류(08:00)→발송(09:00) & 구형 SQLite ALTER TABLE 안전 마이그레이션"""
    from sqlalchemy import text

    client = v11b_env.client
    webpush_calls, fcm_batches = _mock_push_transports(monkeypatch)

    # 1. 커스텀 DND 해제 시각(09:00) 검증
    dev_uuid, _, headers = _register_device(
        client,
        fcm_token="fcm-custom-0900",
        dnd_enabled=True,
        dnd_start_time="22:00",
        dnd_end_time="09:00",
    )
    assert client.post("/api/push/keywords", json={"keyword": "다이슨드라이기"}, headers=headers).status_code == 200
    d_id = _seed_deal(v11b_env.SessionCls, title="[SSG] 다이슨드라이기 슈퍼소닉 뉴럴 (499,000원)")

    night_kst = datetime(2026, 11, 19, 23, 30, 0, tzinfo=KST)
    stats = push_worker.background_trigger_keyword_alarms(d_id, session_factory=v11b_env.SessionCls, now_kst=night_kst)
    assert stats["dropped_dnd"] >= 1
    assert len(fcm_batches) == 0

    # 08:00 KST 실행 시에는 아직 DND(22:00~09:00) 중이므로 발송 보류(0건)
    _run_morning_digest_job(v11b_env.SessionCls, datetime(2026, 11, 20, 8, 0, 0, tzinfo=KST))
    assert [m for b in fcm_batches for m in b] == []
    assert len(webpush_calls) == 0

    db = v11b_env.SessionCls()
    try:
        al_08 = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == dev_uuid).first()
        assert al_08 is not None and getattr(al_08, "digest_sent_at", None) is None
    finally:
        db.close()

    # 09:00 KST 실행 시 DND 해제 완료 -> 1건 발송 및 digest_sent_at 기록
    _run_morning_digest_job(v11b_env.SessionCls, datetime(2026, 11, 20, 9, 0, 0, tzinfo=KST))
    sent_09 = [m for b in fcm_batches for m in b]
    assert [m.token for m in sent_09] == ["fcm-custom-0900"]
    assert any("밤사이 놓친 핫딜 1건" in t for t in _extract_push_payload_texts(webpush_calls, fcm_batches))

    fcm_batches.clear()
    _run_morning_digest_job(v11b_env.SessionCls, datetime(2026, 11, 20, 9, 5, 0, tzinfo=KST))
    assert [m for b in fcm_batches for m in b] == []

    # 2. 구형(v1.1a) NotificationAlert 테이블에 대한 ALTER TABLE 안전 마이그레이션 검증
    legacy_db_path = v11b_env.tmp_path / "legacy_v11a_alerts.db"
    legacy_engine = create_engine(
        f"sqlite:///{legacy_db_path}",
        connect_args={"check_same_thread": False},
    )
    try:
        with legacy_engine.begin() as conn:
            conn.execute(
                text(
                    "CREATE TABLE notification_alerts ("
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    "user_id VARCHAR(100), "
                    "title TEXT NOT NULL, "
                    "keyword VARCHAR(100) NOT NULL, "
                    "received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    "deal_url VARCHAR(2048)"
                    ")"
                )
            )
            conn.execute(
                text(
                    "INSERT INTO notification_alerts (user_id, title, keyword, deal_url) "
                    "VALUES ('legacy-dev-1', '레거시알림보존', '레거시키워드', 'https://example.com/legacy')"
                )
            )

        LegacySession = sessionmaker(autocommit=False, autoflush=False, bind=legacy_engine)
        monkeypatch.setattr(db_session_mod.db_manager, "engine", legacy_engine)
        monkeypatch.setattr(db_session_mod.db_manager, "SessionLocal", LegacySession)
        monkeypatch.setenv("ENABLE_SCHEDULER", "false")

        # startup_event -> db_manager.init_database() 실행 시 기존 레거시 테이블 ALTER TABLE 업그레이드 수행 확인
        asyncio.run(main_mod.startup_event())

        with legacy_engine.connect() as conn:
            cols = {row[1] for row in conn.execute(text("PRAGMA table_info(notification_alerts)")).fetchall()}
        assert {"is_read", "dnd_suppressed", "digest_sent_at"}.issubset(cols), (
            f"ALTER TABLE 마이그레이션 컬럼 누락: {cols}"
        )

        # ORM 조회 시 OperationalError 없이 기존 레거시 행이 보존되어야 함
        leg_db = LegacySession()
        try:
            row = leg_db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == "legacy-dev-1").first()
            assert row is not None
            assert row.title == "레거시알림보존"
            assert getattr(row, "digest_sent_at", None) is None
        finally:
            leg_db.close()
    finally:
        legacy_engine.dispose()


def test_t2_p1_05_cursor_pagination_invalid_cursor_and_offset_compat(v11b_env):
    """[T2-12 / P1-05] 잘못된/부분 커서·빈 결과 경계(500 미발생) 및 기존 offset/limit 하위 호환"""
    client = v11b_env.client
    base_utc = datetime(2026, 11, 19, 9, 0, 0)

    seeded_active = []
    for idx in range(5):
        did = _seed_deal(
            v11b_env.SessionCls,
            title=f"[호환몰] 고유호환상품_{idx:02d}_XYZ{idx} ({10000 + idx * 1000}원)",
            indexed_at=base_utc - timedelta(minutes=idx),
            is_closed=False,
        )
        seeded_active.append(did)

    # 1. 잘못된/불완전 커서 입력 시 500 Internal Server Error가 절대 발생하면 안 됨
    for bad_params in (
        {"cursor_indexed_at": "not-a-valid-iso-date", "cursor_id": 1},
        {"cursor_id": 1},
        {"cursor_indexed_at": "2026-11-19T09:00:00Z"},
    ):
        r_bad = client.get("/api/community/hot-deals", params=bad_params)
        assert r_bad.status_code in (200, 400, 422), f"비정상 커서 500 발생 ({bad_params}): {r_bad.status_code}"
        if r_bad.status_code == 200:
            assert isinstance(r_bad.json().get("deals"), list)

    # 2. 과거 경계 커서 -> 빈 결과 + has_more=False + next_cursor=None
    r_empty_cur = client.get(
        "/api/community/hot-deals",
        params={"cursor_indexed_at": "1999-01-01T00:00:00Z", "cursor_id": 1},
    )
    assert r_empty_cur.status_code == 200
    empty_body = r_empty_cur.json()
    assert empty_body["deals"] == []
    assert empty_body["has_more"] is False
    assert empty_body.get("next_cursor") is None and empty_body.get("nextCursor") is None

    # 3. 기존 offset/limit 하위 호환 검증
    r_offset = client.get("/api/community/hot-deals", params={"limit": 2, "offset": 1})
    assert r_offset.status_code == 200
    offset_body = r_offset.json()
    assert [d["id"] for d in offset_body["deals"]] == seeded_active[1:3]
    assert offset_body["has_more"] is True


def test_t2_p1_12_push_worker_wishlist_rate_limit_blocks_flood(v11b_env, monkeypatch):
    """[T2-13 / P1-12] Wishlist·키워드 합산 일일 한도(==3)·DND 비소모·낮 DND 억제·날짜 롤오버 리셋 검증"""
    client = v11b_env.client
    webpush_calls, fcm_batches = _mock_push_transports(monkeypatch)

    # 1. 일반 기기 (dnd_enabled=False, night_push_consent=False, FCM + WebPush 동시 보유)
    dev_uuid, _, headers = _register_device(
        client,
        fcm_token="fcm-wishlist-flood-guard",
        dnd_enabled=False,
        night_push_consent=False,
        web_push_endpoint="https://push.example/wishlist-guard",
    )
    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "트레이더스", "target_price": 500000},
        headers=headers,
    ).status_code == 200

    daytime_kst = datetime(2026, 11, 19, 14, 0, 0, tzinfo=KST)
    for idx in range(5):
        d_id = _seed_deal(
            v11b_env.SessionCls,
            title=f"[이마트] 트레이더스 특가 상품 {idx}호 ({100000 + idx * 1000}원)",
            price=str(100000 + idx * 1000),
        )
        push_worker.background_trigger_keyword_alarms(
            d_id,
            session_factory=v11b_env.SessionCls,
            now_kst=daytime_kst,
        )

    # 정확히 일일 한도 3건만 발송되어야 함 (1 <= ... <= 3 금지!)
    total_fcm_sent = sum(len(batch) for batch in fcm_batches)
    assert total_fcm_sent == 3, f"일반 기기 Wishlist 일일 한도(==3) 불일치: {total_fcm_sent}건"
    assert len(webpush_calls) == 3, f"WebPush 일일 한도(==3) 불일치: {len(webpush_calls)}건"

    # 레이트리밋으로 차단되어도 인앱 NotificationAlert 5건은 모두 보존되어야 함
    db = v11b_env.SessionCls()
    try:
        in_app_count = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == dev_uuid).count()
        assert in_app_count == 5, f"레이트리밋 시 인앱 알림 기록 누락: {in_app_count}건"
    finally:
        db.close()

    # 2. 같은 날 키워드 알림 추가 매칭 -> Wishlist와 일일 한도(3)를 공유하므로 즉시 차단(0건 추가)
    fcm_batches.clear()
    webpush_calls.clear()
    assert client.post("/api/push/keywords", json={"keyword": "레고테크닉"}, headers=headers).status_code == 200
    d_kw = _seed_deal(
        v11b_env.SessionCls,
        title="[토이저러스] 레고테크닉 포르쉐 911 특가 (180,000원)",
        price="180000",
    )
    push_worker.background_trigger_keyword_alarms(
        d_kw,
        session_factory=v11b_env.SessionCls,
        now_kst=datetime(2026, 11, 19, 14, 30, 0, tzinfo=KST),
    )
    assert sum(len(b) for b in fcm_batches) == 0
    assert len(webpush_calls) == 0

    # 3. 날짜 롤오버(+1일) 시 카운터 초기화 -> 정상 1건 발송
    next_day_kst = datetime(2026, 11, 20, 14, 0, 0, tzinfo=KST)
    d_next = _seed_deal(
        v11b_env.SessionCls,
        title="[이마트] 트레이더스 롤오버 신상 (120,000원)",
        price="120000",
    )
    push_worker.background_trigger_keyword_alarms(
        d_next,
        session_factory=v11b_env.SessionCls,
        now_kst=next_day_kst,
    )
    assert sum(len(b) for b in fcm_batches) == 1
    assert len(webpush_calls) == 1

    # 4. 낮 DND(13:00~15:00) 기기: DND 구간 억제(4건) 시 일일 한도를 소모하지 않고, DND 종료 후(16:00) 3건 발송
    fcm_batches.clear()
    webpush_calls.clear()
    day_dnd_uuid, _, h_day_dnd = _register_device(
        client,
        fcm_token="fcm-daytime-dnd",
        dnd_enabled=True,
        dnd_start_time="13:00",
        dnd_end_time="15:00",
    )
    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "낮디엔디상품", "target_price": 500000},
        headers=h_day_dnd,
    ).status_code == 200

    for idx in range(4):
        d_sup = _seed_deal(
            v11b_env.SessionCls,
            title=f"[홈플러스] 낮디엔디상품 억제 {idx}호 ({50000 + idx * 1000}원)",
            price=str(50000 + idx * 1000),
        )
        sup_stats = push_worker.background_trigger_keyword_alarms(
            d_sup,
            session_factory=v11b_env.SessionCls,
            now_kst=datetime(2026, 11, 19, 14, 0, 0, tzinfo=KST),
        )
        assert sup_stats["dropped_dnd"] >= 1
    assert sum(len(b) for b in fcm_batches) == 0

    # DND 해제 후(16:00 KST) 4건 트리거 -> DND 억제가 쿼터를 소모하지 않았으므로 정확히 3건 발송, 4번째만 차단!
    after_dnd_kst = datetime(2026, 11, 19, 16, 0, 0, tzinfo=KST)
    for idx in range(4):
        d_after = _seed_deal(
            v11b_env.SessionCls,
            title=f"[홈플러스] 낮디엔디상품 해제후 {idx}호 ({60000 + idx * 1000}원)",
            price=str(60000 + idx * 1000),
        )
        push_worker.background_trigger_keyword_alarms(
            d_after,
            session_factory=v11b_env.SessionCls,
            now_kst=after_dnd_kst,
        )
    assert sum(len(b) for b in fcm_batches) == 3

    db = v11b_env.SessionCls()
    try:
        suppressed_rows = db.query(models.NotificationAlert).filter(
            models.NotificationAlert.user_id == day_dnd_uuid,
            models.NotificationAlert.dnd_suppressed == True,
        ).count()
        assert suppressed_rows == 4
    finally:
        db.close()


# ===========================================================================
# Tier 3: 교차 조합 페어와이즈 & 연계 파이프라인 (2개 함수 / 6개 케이스)
# ===========================================================================


@pytest.mark.parametrize(
    "source_comm_id,source_name,sync_mode,only_hot,include_closed,expect_fallback",
    [
        (2, "뽐뿌해외", "pairing", True, False, True),
        (5, "알리뽐뿌", "link_device", False, True, False),
        (7, "펨코", "pairing", True, True, False),
        (2, "뽐뿌해외", "link_device", False, False, True),
        (5, "알리뽐뿌", "pairing", False, False, False),
    ],
)
def test_t3_cross_matrix_sources_auth_filters_and_conflict_merge(
    v11b_env,
    source_comm_id,
    source_name,
    sync_mode,
    only_hot,
    include_closed,
    expect_fallback,
):
    """[T3-01 / Tier 3] 출처×동기화방식×중복충돌멱등×피드필터×폴백플래그 5조합 페어와이즈"""
    client = v11b_env.client
    _, _, headers_a = _register_device(client)
    _, _, headers_b = _register_device(client)

    # 양쪽 기기에 동일 키워드/위시리스트가 이미 있을 때 멱등 중복 방지 검증
    for h in (headers_a, headers_b):
        assert client.post("/api/push/keywords", json={"keyword": "공통키워드"}, headers=h).status_code == 200
        assert client.post(
            "/api/wishlist/item-alert",
            json={"keyword": "공통찜", "target_price": 50000},
            headers=h,
        ).status_code == 200

    assert client.post("/api/push/keywords", json={"keyword": f"전용_{source_name}"}, headers=headers_a).status_code == 200
    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": f"찜전용_{source_name}", "target_price": 77000},
        headers=headers_a,
    ).status_code == 200

    if sync_mode == "pairing":
        code = client.post("/api/auth/pairing-code", headers=headers_a).json()["code"]
        claim_res = client.post("/api/auth/claim-pairing-code", json={"code": code}, headers=headers_b)
        assert claim_res.status_code == 200
    else:
        uname = f"pair_{uuid.uuid4().hex[:8]}"
        s_res = client.post(
            "/api/auth/signup",
            json={"username": uname, "password": "PairPass!123", "nickname": f"페어_{uname}"},
            headers=headers_a,
        )
        assert s_res.status_code == 200
        access_token = s_res.json()["access_token"]
        link_res = client.post(
            "/api/auth/link-device",
            headers={"Authorization": f"Bearer {access_token}", **headers_b},
        )
        assert link_res.status_code == 200

    b_kws = client.get("/api/push/keywords", headers=headers_b).json()["keywords"]
    assert b_kws.count("공통키워드") == 1, f"중복 키워드 폭증 발생: {b_kws}"
    assert f"전용_{source_name}" in b_kws

    b_wls = client.get("/api/wishlist/", headers=headers_b).json()
    assert sum(1 for item in b_wls if item["keyword"] == "공통찜") == 1
    assert any(item["keyword"] == f"찜전용_{source_name}" for item in b_wls)

    # 조건에 일치하는 대상 딜 & 반드시 제외되어야 할 대조군 딜(타 출처·비핫딜·종료딜) + 반대 폴백 플래그 딜 시드
    uid_t = uuid.uuid4().hex[:8]
    post_url = f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no={uid_t}"
    ecom_url = post_url if expect_fallback else f"https://www.aliexpress.com/item/{uid_t}.html"
    target_id = _seed_deal(
        v11b_env.SessionCls,
        source_community_id=source_comm_id,
        title=f"[아마존] 페어와이즈 타겟상품 {uid_t} (49,000원)",
        post_link=post_url,
        ecommerce_link=ecom_url,
        shop_name="아마존",
        price="49000",
        base_product_name=f"페어와이즈타겟_{uid_t}",
        model_code=f"TGT{uid_t.upper()}",
        honey_score=140 if only_hot else 40,
        is_closed=include_closed,
    )

    uid_opp = uuid.uuid4().hex[:8]
    opp_post = f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no={uid_opp}"
    opp_ecom = f"https://www.aliexpress.com/item/{uid_opp}.html" if expect_fallback else opp_post
    ctrl_opposite_fb_id = _seed_deal(
        v11b_env.SessionCls,
        source_community_id=source_comm_id,
        title=f"[아마존] 페어와이즈 반대폴백대조 {uid_opp} (51,000원)",
        post_link=opp_post,
        ecommerce_link=opp_ecom,
        shop_name="아마존",
        price="51000",
        base_product_name=f"페어와이즈반대폴백_{uid_opp}",
        model_code=f"OPP{uid_opp.upper()}",
        honey_score=140 if only_hot else 40,
        is_closed=include_closed,
    )

    ctrl_other_source_id = _seed_deal(
        v11b_env.SessionCls,
        source_community_id=3,  # 루리웹: 매트릭스 {2, 5, 7} 외 타 출처 대조군
        title=f"[쿠팡] 페어와이즈 타출처대조_{uuid.uuid4().hex[:6]} (33,000원)",
        honey_score=160,
        is_closed=False,
    )
    ctrl_non_hot_id = _seed_deal(
        v11b_env.SessionCls,
        source_community_id=source_comm_id,
        title=f"[쿠팡] 페어와이즈 비핫딜대조_{uuid.uuid4().hex[:6]} (22,000원)",
        honey_score=20,
        is_super_hotdeal=False,
        is_closed=False,
    )
    ctrl_closed_id = _seed_deal(
        v11b_env.SessionCls,
        source_community_id=source_comm_id,
        title=f"[쿠팡] 페어와이즈 종료대조_{uuid.uuid4().hex[:6]} (19,000원)",
        honey_score=160,
        is_closed=True,
    )

    feed_res = client.get(
        "/api/community/hot-deals",
        params={
            "source": source_name,
            "only_hot": only_hot,
            "include_closed": include_closed,
        },
    )
    assert feed_res.status_code == 200
    deals = feed_res.json()["deals"]
    by_id = {d["id"]: d for d in deals}

    # 타겟 딜 포함 및 타 출처/조건 불일치 대조군 배제 검증 (거짓 양성 방지)
    assert target_id in by_id
    assert ctrl_opposite_fb_id in by_id
    assert ctrl_other_source_id not in by_id, "타 출처 대조군 딜이 필터링되지 않고 노출됨"
    if only_hot:
        assert ctrl_non_hot_id not in by_id, "only_hot=True에서 비핫딜 대조군이 노출됨"
    if not include_closed:
        assert ctrl_closed_id not in by_id, "include_closed=False에서 종료 대조군이 노출됨"

    item = by_id[target_id]
    assert source_name in item["site_name"]
    assert item["is_closed"] is include_closed
    assert item["is_search_fallback"] is expect_fallback
    assert item["isSearchFallback"] is expect_fallback

    opp_item = by_id[ctrl_opposite_fb_id]
    assert opp_item["is_search_fallback"] is (not expect_fallback)
    assert opp_item["isSearchFallback"] is (not expect_fallback)


@pytest.mark.asyncio
async def test_t3_body_images_to_proxy_image_pipeline_end_to_end(v11b_env, monkeypatch):
    """[T3-02 / Tier 3] 본문 다중 이미지 추출(위험 URL 차단) → /api/proxy-image 병렬 프록시 200 연계"""
    monkeypatch.setattr(socket, "getaddrinfo", _public_dns_only)

    async def _fake_get(url, **kwargs):
        return httpx.Response(200, content=VALID_PNG_BYTES, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _fake_get)

    deal_id = _seed_deal(
        v11b_env.SessionCls,
        source_community_id=2,
        title="[아마존] 다중 이미지 연계 테스트 모니터 ($299)",
        image_url="https://cdn.example.com/main-cover.png",
        content_html=(
            '<img src="https://cdn.example.com/gallery-1.png">'
            '<img src="javascript:alert(9)">'
            '<img src="http://127.0.0.1/secret.png">'
            '<img src="https://cdn.example.com/gallery-2.png">'
        ),
    )

    async with httpx.AsyncClient(app=main_mod.app, base_url="http://testserver") as ac:
        detail_res = await ac.get(f"/api/community/deals/{deal_id}")
        assert detail_res.status_code == 200
        imgs = detail_res.json()["body_images"]
        assert len(imgs) == 2
        assert all("javascript:" not in u and "127.0.0.1" not in u for u in imgs)

        async def _fetch_via_proxy(img_entry: str):
            if img_entry.startswith("/api/"):
                return await ac.get(img_entry)
            return await ac.get("/api/proxy-image", params={"url": img_entry})

        proxy_responses = await asyncio.gather(*(_fetch_via_proxy(u) for u in imgs))

    for resp in proxy_responses:
        assert resp.status_code == 200
        assert resp.content == VALID_PNG_BYTES
        assert resp.headers.get("cache-control") == "public, max-age=604800, immutable"
    assert list(v11b_env.tmp_path.glob("*.tmp")) == []


# ===========================================================================
# Tier 4: 실사용 E2E 풀 워크로드 시나리오 (2개 테스트)
# ===========================================================================


def test_t4_full_v1_1b_guest_sync_night_dnd_digest_and_report_closed_lifecycle(v11b_env, monkeypatch):
    """
    [T4-01 / Tier 4] 게스트 기기A→페어링→기기B→야간 DND 억제→08:00 요약→상세/프록시→품절 3회→include_closed=True 재발견
    """
    client = v11b_env.client
    monkeypatch.setattr(socket, "getaddrinfo", _public_dns_only)
    webpush_calls, fcm_batches = _mock_push_transports(monkeypatch)

    async def _fake_get(url, **kwargs):
        return httpx.Response(200, content=VALID_PNG_BYTES, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", _fake_get)

    # 1. 게스트 기기 A: 찜, 스마트 키워드, 야간 DND(22:00~08:00) 설정
    _, _, headers_a = _register_device(client, fcm_token="fcm-guest-a")
    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "맥북", "target_price": 2000000},
        headers=headers_a,
    ).status_code == 200
    assert client.post(
        "/api/push/keywords",
        json={"keyword": "애플 <=2500000 -케이스"},
        headers=headers_a,
    ).status_code == 200
    assert client.patch(
        "/api/push/dnd",
        json={"dnd_enabled": True, "dnd_start_time": "22:00", "dnd_end_time": "08:00"},
        headers=headers_a,
    ).status_code == 200

    # 2. 기기 B 등록 후 6자리 페어링 코드로 병합
    uuid_b, _, headers_b = _register_device(
        client,
        fcm_token="fcm-guest-b",
        web_push_endpoint="https://push.example/guest-b",
    )
    code = client.post("/api/auth/pairing-code", headers=headers_a).json()["code"]
    claim_res = client.post("/api/auth/claim-pairing-code", json={"code": code}, headers=headers_b)
    assert claim_res.status_code == 200
    assert claim_res.json()["dnd_synced"] is True

    assert any(item["keyword"] == "맥북" for item in client.get("/api/wishlist/", headers=headers_b).json())
    b_kw_info = client.get("/api/push/keywords", headers=headers_b).json()
    assert "애플 <=2500000 -케이스" in b_kw_info["keywords"]
    assert b_kw_info["dnd_enabled"] is True

    # 3. 야간 23:30 KST 뽐뿌해외 신규 딜 시드 및 알림 매칭 (DND 억제 적재)
    post_url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&no=990001"
    deal_id = _seed_deal(
        v11b_env.SessionCls,
        source_community_id=2,
        title="[아마존] 애플 맥북 에어 M3 15인치 (1,890,000원/직배)",
        post_link=post_url,
        ecommerce_link=post_url,
        shop_name="아마존",
        price="1890000",
        category="PC용품",
        honey_score=160,
        image_url="https://cdn.example.com/macbook-thumb.png",
        content_html=(
            '<p>역대 최저가 애플 맥북 에어 딜입니다.</p>'
            '<img src="https://cdn.example.com/macbook-1.png">'
            '<img src="https://cdn.example.com/macbook-2.png">'
        ),
    )

    night_kst = datetime(2026, 11, 19, 23, 30, 0, tzinfo=KST)
    stats = push_worker.background_trigger_keyword_alarms(
        deal_id,
        session_factory=v11b_env.SessionCls,
        now_kst=night_kst,
    )
    assert stats["dropped_dnd"] >= 2
    assert len(fcm_batches) == 0 and len(webpush_calls) == 0

    db = v11b_env.SessionCls()
    try:
        b_alerts = db.query(models.NotificationAlert).filter(models.NotificationAlert.user_id == uuid_b).all()
        assert len(b_alerts) == 2
        assert all(getattr(al, "dnd_suppressed", None) is True for al in b_alerts)
        expected_n = len(b_alerts)
    finally:
        db.close()

    # 4. 아침 08:00 KST 요약 배치 발송 + 다기기 FCM 평탄화 검증 + 멱등 재실행 0건
    morning_kst = datetime(2026, 11, 20, 8, 0, 0, tzinfo=KST)
    _run_morning_digest_job(v11b_env.SessionCls, morning_kst)
    sent_msgs = [m for b in fcm_batches for m in b]
    sent_tokens = [m.token for m in sent_msgs]
    assert len(sent_tokens) == len(set(sent_tokens)), f"FCM 토큰 중복 발송 발생: {sent_tokens}"
    assert sent_tokens.count("fcm-guest-b") == 1, f"기기 B FCM 요약 수신 불일치: {sent_tokens}"
    assert sent_tokens.count("fcm-guest-a") <= 1, f"기기 A FCM 요약 중복 발송: {sent_tokens}"

    assert len(webpush_calls) == 1
    sub_info = webpush_calls[0]["subscription_info"]
    sub_dict = json.loads(sub_info) if isinstance(sub_info, str) else sub_info
    assert sub_dict["endpoint"] == "https://push.example/guest-b"

    texts = _extract_push_payload_texts(webpush_calls, fcm_batches)
    assert any(f"밤사이 놓친 핫딜 {expected_n}건" in txt for txt in texts)

    fcm_batches.clear()
    webpush_calls.clear()
    _run_morning_digest_job(v11b_env.SessionCls, morning_kst + timedelta(minutes=1))
    assert len(fcm_batches) == 0 and len(webpush_calls) == 0

    # 5. 피드/상세/프록시 검증
    feed_res = client.get("/api/community/hot-deals", params={"source": "뽐뿌해외"})
    assert feed_res.status_code == 200
    feed_items = [d for d in feed_res.json()["deals"] if d["id"] == deal_id]
    assert len(feed_items) == 1
    assert feed_items[0]["is_search_fallback"] is True

    detail_res = client.get(f"/api/community/deals/{deal_id}")
    assert detail_res.status_code == 200
    detail = detail_res.json()
    assert len(detail["body_images"]) == 2
    first_img = detail["body_images"][0]
    proxy_res = (
        client.get(first_img)
        if first_img.startswith("/api/")
        else client.get("/api/proxy-image", params={"url": first_img})
    )
    assert proxy_res.status_code == 200
    assert proxy_res.headers.get("cache-control") == "public, max-age=604800, immutable"

    # 6. 품절 제보 3회 -> 종료 전환 -> 기본 피드 제외 -> include_closed=True 재발견
    _, _, r1 = _register_device(client)
    _, _, r2 = _register_device(client)
    _, _, r3 = _register_device(client)
    assert client.post(f"/api/community/deals/{deal_id}/report-closed", headers=r1).status_code == 200
    assert client.post(f"/api/community/deals/{deal_id}/report-closed", headers=r2).status_code == 200
    rep3 = client.post(f"/api/community/deals/{deal_id}/report-closed", headers=r3)
    assert rep3.status_code == 200
    assert rep3.json()["is_closed"] is True

    assert all(d["id"] != deal_id for d in client.get("/api/community/hot-deals").json()["deals"])
    re_found = client.get(
        "/api/community/hot-deals",
        params={"include_closed": True, "keyword": "맥북"},
    )
    assert re_found.status_code == 200
    assert any(d["id"] == deal_id and d["is_closed"] is True for d in re_found.json()["deals"])


def test_t4_legacy_user_migration_to_multi_device_and_large_feed_cursor_browse(v11b_env):
    """
    [T4-02 / Tier 4] 레거시 SHA-256 유저 로그인(PBKDF2 업그레이드) → 다기기 link-device → 320건 커서 탐색 → 품절 후 재검색
    """
    client = v11b_env.client
    legacy_uname = f"legacy_t4_{uuid.uuid4().hex[:8]}"
    legacy_pw = "LegacyMigration#2026"
    legacy_sha256 = hashlib.sha256(legacy_pw.encode()).hexdigest()

    db = v11b_env.SessionCls()
    try:
        db.add(
            models.User(
                username=legacy_uname,
                password_hash=legacy_sha256,
                nickname=f"마이그_{legacy_uname}",
            )
        )
        db.commit()
    finally:
        db.close()

    # 1. 레거시 로그인 -> PBKDF2 업그레이드 + access_token 발급
    login_res = client.post("/api/auth/login", json={"username": legacy_uname, "password": legacy_pw})
    assert login_res.status_code == 200
    access_token = login_res.json()["access_token"]

    db = v11b_env.SessionCls()
    try:
        migrated_user = db.query(models.User).filter(models.User.username == legacy_uname).first()
        assert migrated_user.password_hash != legacy_sha256
        assert "pbkdf2" in migrated_user.password_hash.lower()
    finally:
        db.close()

    # 2. 기기 A 찜 등록 후 기기 A/B 모두 계정 연결
    _, _, headers_a = _register_device(client)
    _, _, headers_b = _register_device(client)
    assert client.post(
        "/api/wishlist/item-alert",
        json={"keyword": "레거시유저찜", "target_price": 99000},
        headers=headers_a,
    ).status_code == 200

    assert client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {access_token}", **headers_a},
    ).status_code == 200
    assert client.post(
        "/api/auth/link-device",
        headers={"Authorization": f"Bearer {access_token}", **headers_b},
    ).status_code == 200

    b_wl = client.get("/api/wishlist/", headers=headers_b)
    assert b_wl.status_code == 200
    assert any(item["keyword"] == "레거시유저찜" for item in b_wl.json())

    # 3. 320건 초과 딜 시드 후 커서 페이징 전수 탐색
    base_utc = datetime(2026, 11, 19, 15, 0, 0)
    db = v11b_env.SessionCls()
    try:
        deals = []
        for idx in range(320):
            deals.append(
                models.Deal(
                    source_community_id=(idx % 10) + 1,
                    title=f"[아카이브{idx}] 대용량유니크아이템_{idx:04d}_SKU{idx} ({15000 + idx * 50}원)",
                    post_link=f"https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no={950000 + idx}",
                    ecommerce_link=f"https://www.11st.co.kr/products/{9500000 + idx}",
                    shop_name="11번가",
                    price=str(15000 + idx * 50),
                    currency="KRW",
                    category="생활용품",
                    base_product_name=f"대용량유니크아이템_{idx:04d}",
                    model_code=f"ARCH{idx:04d}",
                    indexed_at=base_utc - timedelta(minutes=idx),
                    is_closed=False,
                    honey_score=60,
                )
            )
        db.add_all(deals)
        db.commit()
        target_close_id = deals[10].id
        target_kw = "대용량유니크아이템_0010"
    finally:
        db.close()

    seen_ids: list[int] = []
    cur_ts = None
    cur_id = None
    while True:
        params = {"limit": 60}
        if cur_ts is not None and cur_id is not None:
            params["cursor_indexed_at"] = cur_ts
            params["cursor_id"] = cur_id
        page = client.get("/api/community/hot-deals", params=params).json()
        seen_ids.extend(d["id"] for d in page["deals"])
        if not page["has_more"]:
            break
        cur_ts = page["next_cursor"]["indexed_at"]
        cur_id = page["next_cursor"]["id"]

    assert len(seen_ids) == 320 and len(set(seen_ids)) == 320

    # 4. 특정 딜 3명 품절 제보로 종료 전환 후 필터 전환 탐색
    _, _, h1 = _register_device(client)
    _, _, h2 = _register_device(client)
    _, _, h3 = _register_device(client)
    assert client.post(f"/api/community/deals/{target_close_id}/report-closed", headers=h1).status_code == 200
    assert client.post(f"/api/community/deals/{target_close_id}/report-closed", headers=h2).status_code == 200
    assert client.post(f"/api/community/deals/{target_close_id}/report-closed", headers=h3).json()["is_closed"] is True

    active_search = client.get("/api/community/hot-deals", params={"keyword": target_kw}).json()["deals"]
    assert all(d["id"] != target_close_id for d in active_search)

    closed_search = client.get(
        "/api/community/hot-deals",
        params={"keyword": target_kw, "include_closed": True},
    ).json()["deals"]
    assert any(d["id"] == target_close_id and d["is_closed"] is True for d in closed_search)
