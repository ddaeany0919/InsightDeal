# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_wave2_zero_search_and_voc.py

Wave 2 실사용자 피드백 기반 핵심 기능 + t15 보안 잔여 회귀 테스트:
- 검색 결과 0건 키워드 자동 수집 (hit_count 누적, 캐시 히트 포함) & 관리자 /admin/zero-search-queries 조회
- 경량 사용자 VOC 피드백 접수 (POST /api/community/feedback, 유효성·레이트리밋) & /admin/feedback 상태 전환
- device_auth.py 및 community.py ingest-batch 의 ENV=production* Fail-Closed(503) 정합성
"""

from datetime import datetime
import os
import sys

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

from backend import main as main_mod
from backend.core import device_auth
from backend.database import models
from backend.database.session import get_db, get_db_session
from backend.models.wishlist_models import Base as WishlistBase
from backend.routers import community


@pytest.fixture()
def wave2_db(monkeypatch):
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    models.Base.metadata.create_all(bind=engine)
    WishlistBase.metadata.create_all(bind=engine)
    SessionCls = sessionmaker(autocommit=False, autoflush=False, expire_on_commit=False, bind=engine)

    db = SessionCls()
    db.add(models.Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr"))
    db.add(
        models.Deal(
            id=801,
            source_community_id=1,
            title="[쿠팡] 테스트 무선 키보드 특가",
            shop_name="쿠팡",
            category="PC제품",
            price="39900",
            currency="KRW",
            indexed_at=datetime.utcnow(),
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=801",
            ecommerce_link="https://www.coupang.com/vp/products/801",
        )
    )
    db.commit()

    def _override_db():
        s = SessionCls()
        try:
            yield s
        finally:
            s.close()

    main_mod.app.dependency_overrides[get_db] = _override_db
    main_mod.app.dependency_overrides[get_db_session] = _override_db
    monkeypatch.setenv("ENV", "development")
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "wave2-test-device-secret")
    monkeypatch.setenv("ADMIN_API_KEY", "wave2-admin-secret")
    community.invalidate_feed_api_cache()
    community.reset_feedback_rate_limits()

    try:
        with TestClient(main_mod.app) as client:
            yield client, db
    finally:
        community.invalidate_feed_api_cache()
        community.reset_feedback_rate_limits()
        main_mod.app.dependency_overrides.clear()
        db.close()
        engine.dispose()


def test_zero_search_query_collection_and_admin_list(wave2_db):
    client, db = wave2_db
    admin_headers = {"X-Admin-Key": "wave2-admin-secret"}

    # 정상 매칭 검색은 0건 테이블에 쌓이지 않아야 함
    hit_res = client.get("/api/community/hot-deals?keyword=키보드")
    assert hit_res.status_code == 200
    assert len(hit_res.json()["deals"]) == 1
    assert db.query(models.ZeroSearchQuery).count() == 0

    # 0건 검색어 2회 호출 -> 캐시 히트 경로 포함 hit_count == 2 누적
    for _ in range(2):
        miss_res = client.get("/api/community/hot-deals?keyword=없는특수키워드99&category=가전제품")
        assert miss_res.status_code == 200
        assert miss_res.json()["deals"] == []

    # 관리자 인증 없이 조회 시 403
    unauth = client.get("/admin/zero-search-queries")
    assert unauth.status_code == 403

    admin_res = client.get("/admin/zero-search-queries", headers=admin_headers)
    assert admin_res.status_code == 200
    items = admin_res.json()["items"]
    assert len(items) == 1
    assert items[0]["query"] == "없는특수키워드99"
    assert items[0]["hit_count"] == 2
    assert items[0]["last_category"] == "가전제품"


def test_voc_feedback_submit_rate_limit_and_admin_resolve(wave2_db):
    client, _db = wave2_db
    dev_uuid = "wave2-voc-device-1"
    dev_token = device_auth.create_device_token(dev_uuid)
    headers = {"X-Device-Token": dev_token}
    admin_headers = {"X-Admin-Key": "wave2-admin-secret"}

    # 토큰 누락 시 401
    no_auth = client.post("/api/community/feedback", json={"category": "bug", "message": "내용이 길어도 인증 필요"})
    assert no_auth.status_code == 401

    # 5자 미만 거부
    short_res = client.post(
        "/api/community/feedback",
        json={"category": "bug", "message": "짧음"},
        headers=headers,
    )
    assert short_res.status_code == 400

    # 정상 등록 + 상태 조회/처리완료 전환
    ok_res = client.post(
        "/api/community/feedback",
        json={
            "category": "feature",
            "message": "로보락 S9 동의어 검색 추가 부탁드려요",
            "contact": "voc@example.com",
            "platform": "web",
            "app_version": "1.1g",
        },
        headers=headers,
    )
    assert ok_res.status_code == 200
    fb_id = ok_res.json()["id"]

    list_open = client.get("/admin/feedback?status=open", headers=admin_headers)
    assert list_open.status_code == 200
    assert any(it["id"] == fb_id and it["status"] == "open" for it in list_open.json()["items"])

    patch_res = client.patch(f"/admin/feedback/{fb_id}", json={"status": "resolved"}, headers=admin_headers)
    assert patch_res.status_code == 200
    assert patch_res.json()["status"] == "resolved"

    # 동일 기기 5회(위 1회 + 추가 4회) 후 6회째 429 레이트리밋
    for idx in range(4):
        r = client.post(
            "/api/community/feedback",
            json={"category": "general", "message": f"추가 문의 테스트 메시지 {idx}"},
            headers=headers,
        )
        assert r.status_code == 200
    limited = client.post(
        "/api/community/feedback",
        json={"category": "general", "message": "레이트리밋 초과 테스트 메시지"},
        headers=headers,
    )
    assert limited.status_code == 429


def test_prod_prefix_fail_closed_guards_in_device_auth_and_ingest_batch(wave2_db, monkeypatch):
    client, _db = wave2_db
    monkeypatch.setenv("ENV", "production-kr")
    monkeypatch.delenv("SERVER_DEVICE_SECRET", raising=False)
    monkeypatch.delenv("FEEDER_API_TOKEN", raising=False)

    with pytest.raises(Exception) as exc_info:
        device_auth.get_device_secret()
    assert getattr(exc_info.value, "status_code", None) == 503

    res = client.post(
        "/api/community/ingest-batch",
        json={
            "community_name": "ppomppu",
            "display_name": "뽐뿌",
            "base_url": "https://www.ppomppu.co.kr",
            "deals": [],
        },
    )
    assert res.status_code == 503
