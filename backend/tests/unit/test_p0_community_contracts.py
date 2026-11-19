# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_p0_community_contracts.py

M1-C P0 커뮤니티 API 계약 단위 테스트:
1. GET /api/community/deals/{id} 1-RTT 요약 필, is_search_fallback, is_free, UTC Z, 카테고리 정규화
2. 투표·댓글 이중 키(snake_case + camelCase), DELETE 댓글, X-Device-Token 소유권 검증 (UGC /posts* 6종 포함)
3. GET /api/community/hot-deals has_more, popular_only(honey_score >= 100), 500/503 예외 전파, history list 유지
4. GET /api/meta/categories 15종, "해외직구" ↔ "여행.해외핫딜" 호환 및 원시 키 미노출, "가전제품" 필터
5. POST /api/community/force-scrape X-Admin-Key 강제
6. proxy-image follow_redirects=False + 사설IP/DNS 리바인딩/리다이렉트 SSRF 차단 및 CORS 비신뢰 origin 차단
"""

from datetime import datetime, timedelta, timezone
import os
import socket
import sys

import httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from backend import main as main_mod
from backend.core.categories import STANDARD_CATEGORIES, STANDARD_SOURCES
from backend.core.device_auth import create_device_token
from backend.database import models
from backend.database.session import get_db_session
from backend.routers import community


@pytest.fixture()
def p0_db():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    models.Base.metadata.create_all(bind=engine)
    session_cls = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    db = session_cls()
    try:
        community.invalidate_feed_api_cache()
        yield db
        community.invalidate_feed_api_cache()
    finally:
        db.close()
        engine.dispose()


def _make_client(db) -> TestClient:
    def _override_db():
        yield db

    main_mod.app.dependency_overrides[get_db_session] = _override_db
    return TestClient(main_mod.app)


def _seed_community(db) -> models.Community:
    comm = db.query(models.Community).filter(models.Community.id == 1).first()
    if not comm:
        comm = models.Community(
            id=1,
            name="ppomppu",
            display_name="뽐뿌",
            base_url="https://www.ppomppu.co.kr",
        )
        db.add(comm)
        db.commit()
        db.refresh(comm)
    return comm


# ---------------------------------------------------------------------------
# 1. GET /api/community/deals/{id} 1-RTT 요약 필 & UTC Z & 정규화
# ---------------------------------------------------------------------------

def test_deal_detail_1rtt_summary_fields_fallback_free_and_utc(p0_db):
    _seed_community(p0_db)
    base_utc = datetime(2026, 11, 18, 6, 30, 0)

    # (A) 직접 링크 + 유료 + 투표/댓글/히스토리 + 레거시 카테고리("여행.해외핫딜")
    d_direct = models.Deal(
        id=1001,
        source_community_id=1,
        title="[아마존] 소니 WH-1000XM5 무선 헤드폰 (199,000원/무배)",
        price="199000",
        currency="KRW",
        category="여행.해외핫딜",
        post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&no=1001",
        ecommerce_link="https://www.amazon.com/dp/B09XS7JWHH",
        indexed_at=base_utc,
    )
    # (B) 검색 폴백 + 명시적 무료 0원 딜
    d_free = models.Deal(
        id=1002,
        source_community_id=1,
        title="[스팀] 인디 어드벤처 게임 한시적 무료배포 (0원)",
        price="0",
        currency="KRW",
        category="SW/게임",
        post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1002",
        ecommerce_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1002",
        indexed_at=base_utc - timedelta(hours=1),
    )
    # (C) 모음전 0원 딜 (is_free=False 오탐 방지)
    d_collection = models.Deal(
        id=1003,
        source_community_id=1,
        title="[지마켓] 빅스마일데이 생필품 모음전 특가 할인코드",
        price="0",
        currency="KRW",
        category="생활용품",
        post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1003",
        ecommerce_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1003",
        indexed_at=base_utc - timedelta(hours=2),
    )
    p0_db.add_all([d_direct, d_free, d_collection])

    # 1001번 딜: 투표(buy 2, save 1, pass 1), 댓글(활성 2, 삭제 1), 가격 이력(2건)
    p0_db.add_all([
        models.DealVote(deal_id=1001, user_id="u-buy-1", vote_type="buy"),
        models.DealVote(deal_id=1001, user_id="u-buy-2", vote_type="buy"),
        models.DealVote(deal_id=1001, user_id="u-save-1", vote_type="save"),
        models.DealVote(deal_id=1001, user_id="u-pass-1", vote_type="pass"),
        models.DealComment(deal_id=1001, user_id="u-buy-1", nickname="유저1", content="좋아요", is_deleted=False),
        models.DealComment(deal_id=1001, user_id="u-buy-2", nickname="유저2", content="가성비", is_deleted=False),
        models.DealComment(deal_id=1001, user_id="u-save-1", nickname="유저3", content="삭제됨", is_deleted=True),
        models.PriceHistory(deal_id=1001, price="220000", checked_at=base_utc - timedelta(days=2)),
        models.PriceHistory(deal_id=1001, price="199000", checked_at=base_utc - timedelta(days=1)),
    ])
    p0_db.commit()

    try:
        client = _make_client(p0_db)

        # user_id 미전달
        r_anon = client.get("/api/community/deals/1001")
        assert r_anon.status_code == 200
        d1 = r_anon.json()
        assert d1["buy_count"] == 2 and d1["buyCount"] == 2
        assert d1["pass_count"] == 2 and d1["saveCount"] == 2 and d1["passCount"] == 2
        assert d1["user_vote"] is None and d1["userVote"] is None
        assert d1["comment_count"] == 2 and d1["commentCount"] == 2
        assert d1["history_count"] == 2 and d1["historyCount"] == 2
        assert d1["is_search_fallback"] is False
        assert d1["is_free"] is False
        assert d1["indexed_at"] == "2026-11-18T06:30:00Z"
        assert d1["category"] == "해외직구"

        # user_id 전달
        r_user = client.get("/api/community/deals/1001", params={"user_id": "u-buy-1"})
        assert r_user.status_code == 200
        d1_u = r_user.json()
        assert d1_u["user_vote"] == "buy" and d1_u["userVote"] == "buy"

        # 검색 폴백 + 명시적 무료
        d2 = client.get("/api/community/deals/1002").json()
        assert d2["is_search_fallback"] is True
        assert d2["is_free"] is True

        # 모음전 0원 -> is_free=False
        d3 = client.get("/api/community/deals/1003").json()
        assert d3["is_search_fallback"] is True
        assert d3["is_free"] is False
    finally:
        main_mod.app.dependency_overrides.clear()


def test_deal_detail_history_count_fallback_on_exception(p0_db, monkeypatch):
    _seed_community(p0_db)
    p0_db.add(
        models.Deal(
            id=1010,
            source_community_id=1,
            title="[쿠팡] 테스트 단품 (10,000원)",
            price="10000",
            currency="KRW",
            post_link="https://www.ppomppu.co.kr/1010",
            ecommerce_link="https://www.coupang.com/vp/products/1010",
            indexed_at=datetime(2026, 11, 18, 5, 0, 0),
        )
    )
    p0_db.commit()

    def _boom(*args, **kwargs):
        raise RuntimeError("history calc error")

    monkeypatch.setattr(community, "_compute_deal_history", _boom)

    try:
        client = _make_client(p0_db)
        res = client.get("/api/community/deals/1010")
        assert res.status_code == 200
        body = res.json()
        assert body["history_count"] == 0
        assert body["historyCount"] == 0
    finally:
        main_mod.app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# 2. 투표·댓글 이중 키, 댓글 삭제, X-Device-Token 소유권 (UGC /posts* 포함)
# ---------------------------------------------------------------------------

def test_votes_comments_dual_keys_delete_and_device_token_ownership(p0_db):
    _seed_community(p0_db)
    p0_db.add(
        models.Deal(
            id=2001,
            source_community_id=1,
            title="[쿠팡] 삼성 보조배터리 (29,000원)",
            price="29000",
            currency="KRW",
            comment_count=0,
            post_link="https://www.ppomppu.co.kr/2001",
            ecommerce_link="https://www.coupang.com/vp/products/2001",
            indexed_at=datetime(2026, 11, 18, 4, 0, 0),
        )
    )
    p0_db.commit()

    owner_uuid = "device-owner-111"
    other_uuid = "device-other-222"
    owner_token = create_device_token(owner_uuid)
    other_token = create_device_token(other_uuid)

    try:
        client = _make_client(p0_db)

        # (A) 댓글 작성 인증/소유권 검증
        r_no_tok = client.post(
            "/api/community/deals/2001/comments",
            json={"user_id": owner_uuid, "nickname": "찰떡", "content": "무토큰"},
        )
        assert r_no_tok.status_code == 401

        r_spoof = client.post(
            "/api/community/deals/2001/comments",
            headers={"X-Device-Token": other_token},
            json={"user_id": owner_uuid, "nickname": "사칭", "content": "타인 사칭"},
        )
        assert r_spoof.status_code == 403

        r_add = client.post(
            "/api/community/deals/2001/comments",
            headers={"X-Device-Token": owner_token},
            json={"nickname": "찰떡", "content": "이거 진짜 싸네요"},
        )
        assert r_add.status_code == 200
        c_body = r_add.json()
        cid = c_body["id"]
        assert c_body["deal_id"] == 2001 and c_body["dealId"] == 2001
        assert c_body["user_id"] == owner_uuid and c_body["userId"] == owner_uuid
        assert c_body["nickname"] == "찰떡"
        assert c_body["content"] == "이거 진짜 싸네요"
        assert c_body["created_at"].endswith("Z") and c_body["createdAt"] == c_body["created_at"]

        # GET comments 이중 키 확인
        r_list = client.get("/api/community/deals/2001/comments")
        assert r_list.status_code == 200
        c_list = r_list.json()
        assert len(c_list) == 1
        assert c_list[0]["user_id"] == owner_uuid and c_list[0]["userId"] == owner_uuid
        assert c_list[0]["created_at"].endswith("Z") and c_list[0]["createdAt"].endswith("Z")

        # (B) 댓글 삭제: 무토큰 401, 타인 403, 미존재 404, 본인 200
        assert client.delete(f"/api/community/deals/2001/comments/{cid}").status_code == 401
        assert client.delete(
            f"/api/community/deals/2001/comments/{cid}",
            headers={"X-Device-Token": other_token},
        ).status_code == 403
        assert client.delete(
            "/api/community/deals/2001/comments/999999",
            headers={"X-Device-Token": owner_token},
        ).status_code == 404

        r_del = client.delete(
            f"/api/community/deals/2001/comments/{cid}",
            headers={"X-Device-Token": owner_token},
        )
        assert r_del.status_code == 200
        assert client.get("/api/community/deals/2001/comments").json() == []

        deal_row = p0_db.query(models.Deal).filter(models.Deal.id == 2001).first()
        reaction_row = p0_db.query(models.DealReaction).filter(models.DealReaction.deal_id == 2001).first()
        p0_db.refresh(deal_row)
        p0_db.refresh(reaction_row)
        assert deal_row.comment_count == 0
        assert reaction_row.comment_count == 0

        # (C) 투표 인증/이중 키/토글 검증
        assert client.post(
            "/api/community/deals/2001/votes",
            json={"user_id": owner_uuid, "vote_type": "buy"},
        ).status_code == 401
        assert client.post(
            "/api/community/deals/2001/votes",
            headers={"X-Device-Token": other_token},
            json={"user_id": owner_uuid, "vote_type": "buy"},
        ).status_code == 403

        r_vote = client.post(
            "/api/community/deals/2001/votes",
            headers={"X-Device-Token": owner_token},
            json={"vote_type": "buy"},
        )
        assert r_vote.status_code == 200
        vb = r_vote.json()
        assert vb["buy_count"] == 1 and vb["buyCount"] == 1
        assert vb["pass_count"] == 0 and vb["saveCount"] == 0 and vb["passCount"] == 0
        assert vb["user_vote"] == "buy" and vb["userVote"] == "buy"

        # GET votes 이중 키 확인
        r_vget = client.get("/api/community/deals/2001/votes", params={"user_id": owner_uuid})
        assert r_vget.status_code == 200
        vg = r_vget.json()
        assert vg == vb

        # save(말까)로 변경
        r_vsave = client.post(
            "/api/community/deals/2001/votes",
            headers={"X-Device-Token": owner_token},
            json={"user_id": owner_uuid, "vote_type": "save"},
        ).json()
        assert r_vsave["buy_count"] == 0 and r_vsave["pass_count"] == 1
        assert r_vsave["saveCount"] == 1 and r_vsave["passCount"] == 1
        assert r_vsave["user_vote"] == "save" and r_vsave["userVote"] == "save"

        # 동일 투표 재클릭 -> 취소
        r_vtoggle = client.post(
            "/api/community/deals/2001/votes",
            headers={"X-Device-Token": owner_token},
            json={"vote_type": "save"},
        ).json()
        assert r_vtoggle["pass_count"] == 0 and r_vtoggle["user_vote"] is None
    finally:
        main_mod.app.dependency_overrides.clear()


def test_ugc_posts_mutations_device_token_and_utc_iso(p0_db):
    owner_uuid = "ugc-owner-001"
    other_uuid = "ugc-other-002"
    helper_uuid = "ugc-helper-003"
    owner_tok = create_device_token(owner_uuid)
    other_tok = create_device_token(other_uuid)
    helper_tok = create_device_token(helper_uuid)

    try:
        client = _make_client(p0_db)

        # 1) POST /posts
        post_payload = {
            "post_type": "reverse_auction",
            "user_id": owner_uuid,
            "nickname": "작성자",
            "title": "맥북 M4 최저가 찾아주세요",
            "content": "200만원 이하 찾습니다",
            "target_price": 2000000,
        }
        assert client.post("/api/community/posts", json=post_payload).status_code == 401
        assert client.post(
            "/api/community/posts",
            headers={"X-Device-Token": other_tok},
            json=post_payload,
        ).status_code == 403

        r_post = client.post(
            "/api/community/posts",
            headers={"X-Device-Token": owner_tok},
            json=post_payload,
        )
        assert r_post.status_code == 200
        post_id = r_post.json()["id"]
        assert r_post.json()["created_at"].endswith("Z")
        assert r_post.json()["createdAt"].endswith("Z")

        # 2) PUT /posts/{post_id}
        upd_payload = dict(post_payload, title="맥북 M4 프로 최저가 요청")
        assert client.put(f"/api/community/posts/{post_id}", json=upd_payload).status_code == 401
        assert client.put(
            f"/api/community/posts/{post_id}",
            headers={"X-Device-Token": other_tok},
            json=dict(upd_payload, user_id=other_uuid),
        ).status_code == 403
        r_put = client.put(
            f"/api/community/posts/{post_id}",
            headers={"X-Device-Token": owner_tok},
            json=upd_payload,
        )
        assert r_put.status_code == 200
        assert r_put.json()["title"] == "맥북 M4 프로 최저가 요청"

        # 3) POST /posts/{post_id}/comments
        cmt_payload = {"user_id": helper_uuid, "nickname": "제보자", "content": "여기 있어요"}
        assert client.post(f"/api/community/posts/{post_id}/comments", json=cmt_payload).status_code == 401
        assert client.post(
            f"/api/community/posts/{post_id}/comments",
            headers={"X-Device-Token": other_tok},
            json=cmt_payload,
        ).status_code == 403
        r_cmt = client.post(
            f"/api/community/posts/{post_id}/comments",
            headers={"X-Device-Token": helper_tok},
            json=cmt_payload,
        )
        assert r_cmt.status_code == 200
        cmt_id = r_cmt.json()["id"]
        assert r_cmt.json()["created_at"].endswith("Z") and r_cmt.json()["createdAt"].endswith("Z")

        # 4) PUT /posts/{post_id}/comments/{cmt_id}
        upd_cmt = {"user_id": helper_uuid, "nickname": "제보자", "content": "링크 추가했습니다"}
        assert client.put(f"/api/community/posts/{post_id}/comments/{cmt_id}", json=upd_cmt).status_code == 401
        assert client.put(
            f"/api/community/posts/{post_id}/comments/{cmt_id}",
            headers={"X-Device-Token": other_tok},
            json=dict(upd_cmt, user_id=other_uuid),
        ).status_code == 403
        r_cmt_upd = client.put(
            f"/api/community/posts/{post_id}/comments/{cmt_id}",
            headers={"X-Device-Token": helper_tok},
            json=upd_cmt,
        )
        assert r_cmt_upd.status_code == 200
        assert r_cmt_upd.json()["content"] == "링크 추가했습니다"

        # GET /posts, GET /posts/{id}, GET /users/{id}/comments UTC Z 확인
        assert client.get("/api/community/posts").json()[0]["created_at"].endswith("Z")
        assert client.get(f"/api/community/posts/{post_id}").json()["created_at"].endswith("Z")
        assert client.get(f"/api/community/users/{helper_uuid}/comments").json()[0]["created_at"].endswith("Z")

        # 5) POST /posts/{post_id}/comments/{cmt_id}/accept
        assert client.post(
            f"/api/community/posts/{post_id}/comments/{cmt_id}/accept",
            params={"user_id": owner_uuid},
        ).status_code == 401
        assert client.post(
            f"/api/community/posts/{post_id}/comments/{cmt_id}/accept",
            headers={"X-Device-Token": helper_tok},
        ).status_code == 403
        assert client.post(
            f"/api/community/posts/{post_id}/comments/{cmt_id}/accept",
            headers={"X-Device-Token": owner_tok},
        ).status_code == 200

        # 6) DELETE /posts/{post_id}/comments/{cmt_id}
        assert client.delete(
            f"/api/community/posts/{post_id}/comments/{cmt_id}",
            params={"user_id": helper_uuid},
        ).status_code == 401
        assert client.delete(
            f"/api/community/posts/{post_id}/comments/{cmt_id}",
            headers={"X-Device-Token": other_tok},
        ).status_code == 403
        assert client.delete(
            f"/api/community/posts/{post_id}/comments/{cmt_id}",
            headers={"X-Device-Token": helper_tok},
        ).status_code == 200
    finally:
        main_mod.app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# 3. GET /api/community/hot-deals: has_more, popular_only, 500/503, history list
# ---------------------------------------------------------------------------

def test_hot_deals_has_more_popular_only_and_cache_isolation(p0_db):
    _seed_community(p0_db)
    now_utc = datetime(2026, 11, 18, 9, 0, 0)

    p0_db.add_all([
        models.Deal(
            id=3001,
            source_community_id=1,
            title="[쿠팡] 로보락 S8 울트라 (990,000원)",
            price="990000",
            currency="KRW",
            category="가전제품",
            honey_score=120,
            post_link="https://www.ppomppu.co.kr/3001",
            ecommerce_link="https://www.coupang.com/vp/products/3001",
            indexed_at=now_utc,
        ),
        models.Deal(
            id=3002,
            source_community_id=1,
            title="[지마켓] 삼성 비스포크 냉장고 (1,190,000원)",
            price="1190000",
            currency="KRW",
            category="가전제품",
            honey_score=85,  # 80~99 사이: popular_only=True 에서 제외되어야 함
            post_link="https://www.ppomppu.co.kr/3002",
            ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=3002",
            indexed_at=now_utc - timedelta(minutes=5),
        ),
        models.Deal(
            id=3003,
            source_community_id=1,
            title="[11번가] 애플 에어팟 프로 2세대 (269,000원)",
            price="269000",
            currency="KRW",
            category="가전제품",
            honey_score=100,
            post_link="https://www.ppomppu.co.kr/3003",
            ecommerce_link="https://www.11st.co.kr/products/3003",
            indexed_at=now_utc - timedelta(minutes=10),
        ),
    ])
    p0_db.commit()

    try:
        client = _make_client(p0_db)

        # 페이지 1 (limit=2, offset=0) -> has_more=True
        r_p1 = client.get("/api/community/hot-deals", params={"limit": 2, "offset": 0})
        assert r_p1.status_code == 200
        b_p1 = r_p1.json()
        assert len(b_p1["deals"]) == 2
        assert b_p1["has_more"] is True

        # 캐시 히트 검증 (동일 파라미터 재호출)
        r_p1_cached = client.get("/api/community/hot-deals", params={"limit": 2, "offset": 0})
        assert r_p1_cached.status_code == 200
        assert r_p1_cached.json()["has_more"] is True

        # 페이지 2 (limit=2, offset=2) -> has_more=False
        r_p2 = client.get("/api/community/hot-deals", params={"limit": 2, "offset": 2})
        assert r_p2.status_code == 200
        b_p2 = r_p2.json()
        assert len(b_p2["deals"]) == 1
        assert b_p2["has_more"] is False

        # 특수문자 조기 반환 -> has_more=False
        r_early = client.get("/api/community/hot-deals", params={"keyword": "_"})
        assert r_early.status_code == 200
        assert r_early.json() == {"deals": [], "has_more": False}

        # popular_only=True -> honey_score >= 100 인 3001, 3003만 반환 (3002=85 제외)
        r_pop = client.get("/api/community/hot-deals", params={"popular_only": True})
        assert r_pop.status_code == 200
        pop_body = r_pop.json()
        assert [d["id"] for d in pop_body["deals"]] == [3001, 3003]
        assert pop_body["has_more"] is False
    finally:
        main_mod.app.dependency_overrides.clear()


def test_hot_deals_propagates_503_and_500_instead_of_masking_200(p0_db):
    class BrokenSqlSession:
        def get_bind(self):
            return object()

        def query(self, *args, **kwargs):
            raise OperationalError("SELECT 1", {}, Exception("db down"))

    class BrokenGenericSession:
        def get_bind(self):
            return object()

        def query(self, *args, **kwargs):
            raise RuntimeError("unexpected bug")

    try:
        main_mod.app.dependency_overrides[get_db_session] = lambda: BrokenSqlSession()
        client = TestClient(main_mod.app)
        r_503 = client.get("/api/community/hot-deals")
        assert r_503.status_code == 503
        assert r_503.json()["detail"] == "Database unavailable"

        main_mod.app.dependency_overrides[get_db_session] = lambda: BrokenGenericSession()
        r_500 = client.get("/api/community/hot-deals")
        assert r_500.status_code == 500
        assert r_500.json()["detail"] == "Internal Server Error"
    finally:
        main_mod.app.dependency_overrides.clear()


def test_history_endpoint_keeps_list_shape_with_utc_z_and_kst_label(p0_db):
    _seed_community(p0_db)
    base_utc = datetime(2026, 11, 18, 4, 15, 0)  # KST 13:15
    p0_db.add_all([
        models.Deal(
            id=3100,
            source_community_id=1,
            title="[쿠팡] 삼성 모니터 S32 (200,000원)",
            price="200000",
            currency="KRW",
            post_link="https://www.ppomppu.co.kr/3100",
            ecommerce_link="https://www.coupang.com/vp/products/3100",
            indexed_at=base_utc,
        ),
        models.PriceHistory(deal_id=3100, price="220000", checked_at=base_utc - timedelta(days=1)),
        models.PriceHistory(deal_id=3100, price="200000", checked_at=base_utc),
    ])
    p0_db.commit()

    try:
        client = _make_client(p0_db)
        res = client.get("/api/community/deals/3100/history", params={"period": "7d"})
        assert res.status_code == 200
        payload = res.json()
        assert isinstance(payload, list)
        assert [p["price"] for p in payload] == [220000, 200000]
        assert payload[-1]["recordedAt"] == "11.18 13:15"
        assert payload[-1]["timestamp"].endswith("Z")
        assert payload[-1]["recorded_at"].endswith("Z")
    finally:
        main_mod.app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# 4. GET /api/meta/categories 15종 · "해외직구" ↔ "여행.해외핫딜" · "가전제품"
# ---------------------------------------------------------------------------

def test_meta_categories_15_and_legacy_overseas_and_appliance_compat(p0_db):
    _seed_community(p0_db)
    now_utc = datetime(2026, 11, 18, 8, 0, 0)
    p0_db.add_all([
        models.Deal(
            id=4001,
            source_community_id=1,
            title="[아마존] 직구 커피 그라인더 (99,000원)",
            price="99000",
            currency="KRW",
            category="여행.해외핫딜",  # 레거시 DB 원시 키
            post_link="https://www.ppomppu.co.kr/4001",
            ecommerce_link="https://www.amazon.com/dp/B0000001",
            indexed_at=now_utc,
        ),
        models.Deal(
            id=4002,
            source_community_id=1,
            title="[하이마트] LG 오브제 공기청정기 (490,000원)",
            price="490000",
            currency="KRW",
            category="가전/TV",  # 비표준 라벨 저장 사례
            post_link="https://www.ppomppu.co.kr/4002",
            ecommerce_link="https://www.e-himart.co.kr/app/goods/goodsDetail?goodsNo=4002",
            indexed_at=now_utc - timedelta(minutes=5),
        ),
    ])
    p0_db.commit()

    try:
        client = _make_client(p0_db)

        r_meta = client.get("/api/meta/categories")
        assert r_meta.status_code == 200
        meta = r_meta.json()
        assert len(meta["categories"]) == 15
        assert meta["categories"] == STANDARD_CATEGORIES
        assert meta["sources"] == STANDARD_SOURCES
        assert "빠삭육아" in meta["sources"]
        assert all(c["key"] != "여행.해외핫딜" for c in meta["categories"])
        assert any(c["key"] == "해외직구" and c["label"] == "해외직구" for c in meta["categories"])
        assert any(c["key"] == "가전제품" and c["label"] == "가전/TV" for c in meta["categories"])

        # 표준 키 "해외직구" 및 레거시 키 "여행.해외핫딜" 모두 매칭되고 응답 category는 "해외직구"로 정규화
        for q_cat in ("해외직구", "여행.해외핫딜"):
            r_overseas = client.get("/api/community/hot-deals", params={"category": q_cat})
            assert r_overseas.status_code == 200
            items = r_overseas.json()["deals"]
            assert [d["id"] for d in items] == [4001]
            assert items[0]["category"] == "해외직구"
            assert items[0]["category"] != "여행.해외핫딜"

        # "가전제품" 필터 매칭 및 응답 정규화
        r_app = client.get("/api/community/hot-deals", params={"category": "가전제품"})
        assert r_app.status_code == 200
        app_items = r_app.json()["deals"]
        assert [d["id"] for d in app_items] == [4002]
        assert app_items[0]["category"] == "가전제품"
    finally:
        main_mod.app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# 5. POST /api/community/force-scrape X-Admin-Key 인증 강제
# ---------------------------------------------------------------------------

def test_force_scrape_requires_x_admin_key(p0_db, monkeypatch):
    monkeypatch.setenv("ADMIN_API_KEY", "force-scrape-secret-777")
    called = []
    monkeypatch.setattr(community, "run_scraper_script", lambda: called.append(True))
    monkeypatch.setattr(community, "_LAST_SCRAPE_TRIGGER_TS", 0.0)
    monkeypatch.setattr(community, "_SCRAPE_RUNNING_LOCK", False)

    try:
        client = _make_client(p0_db)
        assert client.post("/api/community/force-scrape").status_code == 403
        assert client.post(
            "/api/community/force-scrape",
            headers={"X-Admin-Key": "wrong-key"},
        ).status_code == 403

        r_ok = client.post(
            "/api/community/force-scrape",
            headers={"X-Admin-Key": "force-scrape-secret-777"},
        )
        assert r_ok.status_code == 200
        assert r_ok.json()["status"] == "success"
        assert called == [True]
    finally:
        main_mod.app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# 6. proxy-image SSRF(follow_redirects=False/사설IP/DNS 리바인딩) & CORS 차단
# ---------------------------------------------------------------------------

def test_proxy_image_ssrf_and_cors_guards(monkeypatch, tmp_path):
    assert main_mod.proxy_client.follow_redirects is False
    monkeypatch.setattr(main_mod, "IMAGE_CACHE_DIR", str(tmp_path))

    fetched_urls = []

    async def fake_proxy_get(url, **kwargs):
        fetched_urls.append(url)
        if "redirect-to-internal.example.com" in url:
            return httpx.Response(
                302,
                headers={"Location": "http://127.0.0.1/admin"},
                request=httpx.Request("GET", url),
            )
        return httpx.Response(
            404,
            content=b"not found",
            request=httpx.Request("GET", url),
        )

    monkeypatch.setattr(main_mod.proxy_client, "get", fake_proxy_get)

    def fake_getaddrinfo(host, port, *args, **kwargs):
        if host == "rebind.example.com":
            return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("10.0.0.1", port))]
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))]

    monkeypatch.setattr(socket, "getaddrinfo", fake_getaddrinfo)
    monkeypatch.setattr(main_mod, "_get_deal_backup_candidates_sync", lambda *a, **kw: (None, []))

    client = TestClient(main_mod.app)

    # 사설 IP / 메타데이터 IP / DNS 리바인딩 -> 외부 요청 없이 404 (no-store) 차단 반환
    for blocked in (
        "http://127.0.0.1/secret.png",
        "http://169.254.169.254/latest/meta-data/",
        "https://rebind.example.com/img.jpg",
    ):
        res = client.get("/api/proxy-image", params={"url": blocked})
        assert res.status_code == 404
        assert res.headers.get("cache-control") == "no-store"
    assert fetched_urls == []

    # 302 리다이렉트가 사설 IP(127.0.0.1)를 가리킬 때 2홉째는 절대 요청하지 않고 차단
    r_redir = client.get("/api/proxy-image", params={"url": "https://redirect-to-internal.example.com/a.jpg"})
    assert r_redir.status_code == 404
    assert r_redir.headers.get("cache-control") == "no-store"
    assert fetched_urls == ["https://redirect-to-internal.example.com/a.jpg"]

    # CORS: 비신뢰 origin은 allow-origin 미부여, 신뢰 origin + X-Device-Token 허용
    r_evil = client.options(
        "/api/meta/categories",
        headers={
            "Origin": "https://evil-attacker.example.com",
            "Access-Control-Request-Method": "GET",
        },
    )
    assert "access-control-allow-origin" not in {k.lower(): v for k, v in r_evil.headers.items()}

    for allowed_origin in ("https://insightdeal.vercel.app", "https://app.tossmini.com"):
        r_good = client.options(
            "/api/meta/categories",
            headers={
                "Origin": allowed_origin,
                "Access-Control-Request-Method": "POST",
                "Access-Control-Request-Headers": "X-Device-Token, Content-Type",
            },
        )
        hdrs = {k.lower(): v for k, v in r_good.headers.items()}
        assert hdrs.get("access-control-allow-origin") == allowed_origin
        assert "x-device-token" in hdrs.get("access-control-allow-headers", "").lower()
