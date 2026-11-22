# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_v11f_real_user_polish.py

v1.1f 실사용자 100대 개선 백엔드 회귀 테스트:
- 카테고리 가드 (애플TV vs 육아/유아용품, [슈퍼적립] 의류 보호, 알뜰폰 MVNO 가드, 아가드/목욕시간)
- 쇼핑몰 정규화/URL 추론 (카카오쇼핑, CJ온스타일, 엑스박스)
- 배송비 정규화 ('평생' 노이즈 배제, 유클무료/스클무료 유지)
- 스마트 키워드 매칭 (@쇼핑몰, #카테고리, -제외, :상한가, 옵션 텍스트, 외화 환산) 및 일일 알림 상한/포맷
- /api/community/hot-deals 다중 필터(free_shipping, shop, min_price, max_price) & 6종 sort
- /api/community/deals/{id} similar_deals 노출 & POST /api/push/test 200 / 429 레이트리밋
"""

from datetime import datetime, timedelta
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
from backend.core.deal_normalizer import (
    infer_shop_from_url,
    normalize_shipping_fee,
    resolve_canonical_shop_name,
)
from backend.core.device_auth import create_device_token
from backend.database import models
from backend.database.session import get_db, get_db_session
from backend.models.wishlist_models import Base as WishlistBase
from backend.routers import community, push as push_router
from backend.services.normalizer.regex_normalizer import RegexNormalizer
from backend.services import push_worker


@pytest.fixture()
def v11f_db(monkeypatch):
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    models.Base.metadata.create_all(bind=engine)
    WishlistBase.metadata.create_all(bind=engine)
    SessionCls = sessionmaker(autocommit=False, autoflush=False, expire_on_commit=False, bind=engine)

    db = SessionCls()
    comm1 = models.Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr")
    db.add(comm1)
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
    monkeypatch.setenv("SERVER_DEVICE_SECRET", "unit-v11f-secret-key")
    community.invalidate_feed_api_cache()
    push_router.reset_rate_limits()
    push_worker.reset_rate_limits()

    try:
        with TestClient(main_mod.app) as client:
            yield client, db
    finally:
        community.invalidate_feed_api_cache()
        push_router.reset_rate_limits()
        push_worker.reset_rate_limits()
        main_mod.app.dependency_overrides.clear()
        db.close()
        engine.dispose()


def test_normalizers_categories_shops_and_shipping():
    norm = RegexNormalizer()
    assert norm._map_category("아가드 모서리 안전가드 4개입") == "생활용품"
    assert norm._map_category("구름목욕시간 아기 바스") == "생활용품"
    assert norm._map_category("애플TV 4K 128GB 셋톱") != "육아/유아용품"
    assert norm._map_category("[슈퍼적립] 나이키 스포츠웨어 패딩 자켓") == "의류"
    assert norm._map_category("알뜰폰 KTM모바일 7GB 요금제") == "패키지/이용권"

    assert infer_shop_from_url("https://store.kakao.com/brand/products/100") == "카카오쇼핑"
    assert infer_shop_from_url("https://www.cjonstyle.com/prd/100") == "CJ온스타일"
    assert infer_shop_from_url("https://www.xbox.com/ko-KR/games/store/foo") == "엑스박스"

    assert resolve_canonical_shop_name("[카카오톡쇼핑하기] 제주 삼다수", "") == "카카오쇼핑"
    assert resolve_canonical_shop_name("[CJ온] 백설 올리브유", "") == "CJ온스타일"

    assert normalize_shipping_fee("평생") == ""
    assert normalize_shipping_fee("12개월") == ""
    assert normalize_shipping_fee("유클무료") in ("유클무료", "무료 (유클)")
    assert normalize_shipping_fee("스클무료") in ("스클무료", "무료 (스클)")


def test_push_worker_smart_keyword_and_currency_helpers(monkeypatch):
    deal = models.Deal(
        id=9901,
        source_community_id=1,
        title="[쿠팡] 아이패드 에어 M2 11형 와이파이",
        shop_name="쿠팡",
        category="PC제품",
        price="650000",
        currency="KRW",
        options_data='[{"name": "256GB 스페이스 그레이", "price": 750000}]',
        post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=9901",
    )

    ok_opt, _ = push_worker._match_smart_keyword("256gb @쿠팡 #PC :800000 -케이스", deal, 650000)
    assert ok_opt is True

    ng_shop, _ = push_worker._match_smart_keyword("아이패드 @네이버", deal, 650000)
    assert ng_shop is False

    ng_excl, _ = push_worker._match_smart_keyword("아이패드 -와이파이", deal, 650000)
    assert ng_excl is False

    monkeypatch.setenv("INSIGHTDEAL_DAILY_PUSH_LIMIT", "15")
    assert push_worker._get_daily_push_limit() == 15

    usd_text = push_worker._format_push_price(5900, "USD")
    assert "$59.00" in usd_text and "원" in usd_text


def test_hot_deals_extended_filters_sorts_similar_deals_and_push_test(v11f_db):
    client, db = v11f_db
    now = datetime.utcnow()

    deals = [
        models.Deal(
            id=501,
            source_community_id=1,
            title="[쿠팡] 코카콜라 제로 355ml 24캔",
            shop_name="쿠팡",
            category="음식",
            price="15900",
            shipping_fee="무료",
            currency="KRW",
            like_count=45,
            comment_count=30,
            honey_score=320,
            indexed_at=now - timedelta(hours=1),
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=501",
            ecommerce_link="https://www.coupang.com/vp/products/501",
        ),
        models.Deal(
            id=502,
            source_community_id=1,
            title="[네이버] 코카콜라 제로 라임 355ml 24캔",
            shop_name="네이버",
            category="음식",
            price="18900",
            shipping_fee="3000원",
            currency="KRW",
            like_count=12,
            comment_count=80,
            honey_score=220,
            indexed_at=now - timedelta(hours=2),
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=502",
            ecommerce_link="https://smartstore.naver.com/item/502",
        ),
        models.Deal(
            id=503,
            source_community_id=1,
            title="[지마켓] 펩시 제로 355ml 24캔",
            shop_name="지마켓",
            category="음식",
            price="13500",
            shipping_fee="유클무료",
            currency="KRW",
            like_count=90,
            comment_count=10,
            honey_score=500,
            indexed_at=now - timedelta(hours=3),
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=503",
            ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=503",
        ),
        models.Deal(
            id=504,
            source_community_id=1,
            title="애플TV 4K 128GB",
            shop_name="아마존",
            category="육아/유아용품",
            price="199000",
            shipping_fee="무료",
            currency="KRW",
            like_count=5,
            comment_count=2,
            honey_score=50,
            indexed_at=now - timedelta(hours=4),
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=504",
            ecommerce_link="https://www.amazon.com/dp/504",
        ),
        models.Deal(
            id=505,
            source_community_id=1,
            title="[롯데온] 오뚜기 옛날 삼계탕 (30일 전 레거시 인기글)",
            shop_name="롯데온",
            category="음식",
            price="14900",
            shipping_fee="무료",
            currency="KRW",
            like_count=500,
            comment_count=300,
            honey_score=999,
            indexed_at=now - timedelta(days=30),
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=505",
            ecommerce_link="https://www.lotteon.com/p/product/505",
        ),
        models.Deal(
            id=506,
            source_community_id=1,
            title="[카카오쇼핑] 도미노 피자 슈퍼디럭스 L 기프티콘",
            shop_name="카카오쇼핑",
            category="음식",
            price="21900",
            shipping_fee="무료",
            currency="KRW",
            like_count=40,
            comment_count=25,
            honey_score=400,
            indexed_at=now - timedelta(hours=5),
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=506",
            ecommerce_link="https://store.kakao.com/brand/products/506",
        ),
        models.Deal(
            id=507,
            source_community_id=1,
            title="[쿠팡] 벤션 블루투스 무선 이어폰 ANC",
            shop_name="쿠팡",
            category="PC제품",
            price="19900",
            shipping_fee="무료",
            currency="KRW",
            like_count=15,
            comment_count=8,
            honey_score=110,
            indexed_at=now - timedelta(hours=2),
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=507",
            ecommerce_link="https://www.coupang.com/vp/products/507",
        ),
    ]
    db.add_all(deals)
    db.commit()

    # free_shipping + price range filter
    res_fs = client.get("/api/community/hot-deals?free_shipping=true&min_price=14000&max_price=20000")
    assert res_fs.status_code == 200
    fs_ids = [d["id"] for d in res_fs.json()["deals"]]
    assert 501 in fs_ids
    assert 502 not in fs_ids

    # v1.1g list dual keys (comments / recommendations / score)
    deal_501 = next(d for d in res_fs.json()["deals"] if d["id"] == 501)
    assert deal_501["comments"] == deal_501["comment_count"] == 30
    assert deal_501["recommendations"] == deal_501["like_count"] == 45
    assert deal_501["score"] == deal_501["honey_score"]

    # shop filter
    res_shop = client.get("/api/community/hot-deals?shop=쿠팡")
    assert res_shop.status_code == 200
    assert 501 in [d["id"] for d in res_shop.json()["deals"]]

    # sort variants + v1.1g recency decay (30d old 505 must lose to fresh deals)
    res_desc = client.get("/api/community/hot-deals?sort=price_desc&category=음식")
    assert res_desc.status_code == 200
    assert res_desc.json()["deals"][0]["id"] == 506

    res_comments = client.get("/api/community/hot-deals?sort=comments&category=음식")
    assert res_comments.status_code == 200
    comment_ids = [d["id"] for d in res_comments.json()["deals"]]
    assert comment_ids[0] == 502
    assert comment_ids.index(505) > comment_ids.index(502)

    res_recs = client.get("/api/community/hot-deals?sort=recommendations&category=음식")
    assert res_recs.status_code == 200
    rec_ids = [d["id"] for d in res_recs.json()["deals"]]
    assert rec_ids[0] == 503
    assert rec_ids.index(505) > rec_ids.index(503)

    # category guard on 육아/유아용품 should exclude 애플TV
    res_baby = client.get("/api/community/hot-deals?category=육아/유아용품")
    assert res_baby.status_code == 200
    assert 504 not in [d["id"] for d in res_baby.json()["deals"]]

    # v1.1g 가전제품 vs PC용품 오디오 중복 차단
    res_pc_audio = client.get("/api/community/hot-deals?category=PC용품")
    assert res_pc_audio.status_code == 200
    assert 507 in [d["id"] for d in res_pc_audio.json()["deals"]]

    res_app_audio = client.get("/api/community/hot-deals?category=가전제품")
    assert res_app_audio.status_code == 200
    assert 507 not in [d["id"] for d in res_app_audio.json()["deals"]]

    # detail endpoint includes matching similar_deals (502), excludes unrelated pizza (506)
    res_detail = client.get("/api/community/deals/501")
    assert res_detail.status_code == 200
    sim_ids = [item["id"] for item in res_detail.json().get("similar_deals", [])]
    assert 502 in sim_ids
    assert 506 not in sim_ids

    # POST /api/push/test + rate limit
    reg = client.post("/api/push/device", json={"device_uuid": "default", "fcm_token": ""})
    assert reg.status_code == 200
    dev_uuid = reg.json()["device_uuid"]
    dev_token = create_device_token(dev_uuid)
    headers = {"X-Device-Token": dev_token}

    for _ in range(6):
        ok_push = client.post("/api/push/test", json={"device_uuid": dev_uuid}, headers=headers)
        assert ok_push.status_code == 200
        assert ok_push.json()["success"] is True

    limited = client.post("/api/push/test", json={"device_uuid": dev_uuid}, headers=headers)
    assert limited.status_code == 429

    # Direct Python invocation (like _warm_default_feeds_sync) must unwrap FastAPI Query defaults cleanly
    direct_res = community.get_hot_deals(limit=5, db=db)
    assert isinstance(direct_res, dict)
    assert "deals" in direct_res
    assert len(direct_res["deals"]) >= 1
