import json
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from backend.core.deal_normalizer import renormalize_existing_deals
from backend.database import models
from backend.database.session import get_db_session
from backend.main import app
from backend.routers.community import (
    _compute_deal_history,
    invalidate_feed_api_cache,
)


@pytest.fixture
def audit_db():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    models.Base.metadata.create_all(bind=engine)
    session_cls = sessionmaker(bind=engine)
    db = session_cls()
    try:
        invalidate_feed_api_cache()
        yield db
        invalidate_feed_api_cache()
    finally:
        db.close()


def _make_client(db):
    def override_get_db():
        yield db

    app.dependency_overrides[get_db_session] = override_get_db
    return TestClient(app)


def test_search_sql_wildcard_escaping_and_special_chars(audit_db):
    """50%, 100%, _, 1+1, C++, [특가] 등 특수문자 검색 시 SQL LIKE 와일드카드 오염이 없어야 한다."""
    comm = models.Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://ppomppu.co.kr")
    audit_db.add(comm)
    now_utc = datetime.now(timezone.utc).replace(tzinfo=None)

    audit_db.add_all(
        [
            models.Deal(
                id=1,
                source_community_id=1,
                title="[지마켓] 스테비아 방울토마토 500g 4팩",
                price="7980",
                currency="KRW",
                post_link="https://ppomppu.co.kr/1",
                ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=11111111",
                indexed_at=now_utc - timedelta(minutes=10),
            ),
            models.Deal(
                id=2,
                source_community_id=1,
                title="[스팀] 인디게임 50% 할인 이벤트",
                price="5000",
                currency="KRW",
                post_link="https://ppomppu.co.kr/2",
                ecommerce_link="https://store.steampowered.com/app/222222",
                indexed_at=now_utc - timedelta(minutes=8),
            ),
            models.Deal(
                id=3,
                source_community_id=1,
                title="유기농 100% 착즙 사과주스 1L",
                price="9900",
                currency="KRW",
                post_link="https://ppomppu.co.kr/3",
                ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=33333333",
                indexed_at=now_utc - timedelta(minutes=6),
            ),
            models.Deal(
                id=4,
                source_community_id=1,
                title="샤오미 보조배터리 10000mAh 고속충전",
                price="15900",
                currency="KRW",
                post_link="https://ppomppu.co.kr/4",
                ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=44444444",
                indexed_at=now_utc - timedelta(minutes=5),
            ),
            models.Deal(
                id=5,
                source_community_id=1,
                title="KFC 치킨올데이 1+1 프로모션",
                price="3500",
                currency="KRW",
                post_link="https://ppomppu.co.kr/5",
                ecommerce_link="https://www.kfc.co.kr/event/5",
                indexed_at=now_utc - timedelta(minutes=4),
            ),
            models.Deal(
                id=6,
                source_community_id=1,
                title="모던 C++ 프로그래밍 입문서 전자책",
                price="18000",
                currency="KRW",
                post_link="https://ppomppu.co.kr/6",
                ecommerce_link="https://www.yes24.com/Product/Goods/666666",
                indexed_at=now_utc - timedelta(minutes=3),
            ),
            models.Deal(
                id=7,
                source_community_id=1,
                title="모델코드 A_B 테스트 케이블",
                price="4900",
                currency="KRW",
                post_link="https://ppomppu.co.kr/7",
                ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=77777777",
                indexed_at=now_utc - timedelta(minutes=2),
            ),
            models.Deal(
                id=8,
                source_community_id=1,
                title="모델코드 AXB 일반 케이블",
                price="4900",
                currency="KRW",
                post_link="https://ppomppu.co.kr/8",
                ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=88888888",
                indexed_at=now_utc - timedelta(minutes=1),
            ),
        ]
    )
    audit_db.commit()

    try:
        client = _make_client(audit_db)

        # 50% 검색 시 '500g'(id=1)이 와일드카드로 딸려 나오면 안 되고 '50%'(id=2)만 나와야 함
        r_50 = client.get("/api/community/hot-deals", params={"keyword": "50%"}).json()["deals"]
        assert [d["id"] for d in r_50] == [2]

        # 100% 검색 시 '10000mAh'(id=4)가 딸려 나오면 안 되고 '100%'(id=3)만 나와야 함
        r_100 = client.get("/api/community/hot-deals", params={"keyword": "100%"}).json()["deals"]
        assert [d["id"] for d in r_100] == [3]

        # 단독 와일드카드 '_' 및 '%' 검색 시 전체 딜이 쏟아지지 않아야 함
        assert client.get("/api/community/hot-deals", params={"keyword": "_"}).json()["deals"] == []
        assert client.get("/api/community/hot-deals", params={"keyword": "%"}).json()["deals"] == []

        # 'A_B' 검색 시 '_'가 임의 1글자('AXB', id=8)에 매칭되지 않고 리터럴 'A_B'(id=7)만 매칭되어야 함
        r_ab = client.get("/api/community/hot-deals", params={"keyword": "A_B"}).json()["deals"]
        assert [d["id"] for d in r_ab] == [7]

        # 1+1, C++ 검색 정상 동작
        assert [d["id"] for d in client.get("/api/community/hot-deals", params={"keyword": "1+1"}).json()["deals"]] == [5]
        assert [d["id"] for d in client.get("/api/community/hot-deals", params={"keyword": "C++"}).json()["deals"]] == [6]
    finally:
        app.dependency_overrides.clear()


def test_search_spacing_compound_kor_eng_and_dsu_backfill_isolation(audit_db):
    """띄어쓰기/붙여쓰기(갤럭시 버즈↔갤럭시버즈, 갤럭시 폴드↔갤럭시폴드, RTX 5070↔RTX5070), 한/영 동의어, DSU 백필 격리를 검증한다."""
    comm = models.Community(id=1, name="fmkorea", display_name="펨코", base_url="https://fmkorea.com")
    audit_db.add(comm)
    now_utc = datetime.now(timezone.utc).replace(tzinfo=None)

    audit_db.add_all(
        [
            models.Deal(
                id=101,
                source_community_id=1,
                title="[네이버] 삼성 갤럭시 버즈4 프로 SM-R640 블루투스 이어폰",
                price="192110",
                currency="KRW",
                post_link="https://fmkorea.com/101",
                ecommerce_link="https://smartstore.naver.com/shop/products/101",
                indexed_at=now_utc - timedelta(minutes=20),
            ),
            models.Deal(
                id=102,
                source_community_id=1,
                title="[지마켓] 갤럭시버즈3FE 무선 이어폰",
                price="99000",
                currency="KRW",
                post_link="https://fmkorea.com/102",
                ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=10200000",
                indexed_at=now_utc - timedelta(minutes=19),
            ),
            models.Deal(
                id=103,
                source_community_id=1,
                title="삼성전자 갤럭시 Z폴드8 자급제 256GB",
                price="1915300",
                currency="KRW",
                post_link="https://fmkorea.com/103",
                ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=10300000",
                indexed_at=now_utc - timedelta(minutes=18),
            ),
            models.Deal(
                id=104,
                source_community_id=1,
                title="ASUS 게이밍노트북 RTX 5070 탑재 모델",
                price="1999000",
                currency="KRW",
                post_link="https://fmkorea.com/104",
                ecommerce_link="https://www.11st.co.kr/products/10400000",
                indexed_at=now_utc - timedelta(minutes=17),
            ),
            models.Deal(
                id=105,
                source_community_id=1,
                title="조립PC 9800X3D + RTX5070Ti 완본체",
                price="3100000",
                currency="KRW",
                post_link="https://fmkorea.com/105",
                ecommerce_link="https://www.11st.co.kr/products/10500000",
                indexed_at=now_utc - timedelta(minutes=16),
            ),
            models.Deal(
                id=106,
                source_community_id=1,
                title="Logitech 지슈라2 무선 게이밍 마우스",
                price="139000",
                currency="KRW",
                post_link="https://fmkorea.com/106",
                ecommerce_link="https://ko.aliexpress.com/",
                indexed_at=now_utc - timedelta(minutes=15),
            ),
            # 루트 도메인(https://ko.aliexpress.com/)을 공유하지만 검색어와 전혀 무관한 다른 알리 딜 (DSU 백필 누수 유발 후보)
            models.Deal(
                id=107,
                source_community_id=1,
                title="무관한 알리 천원마트 수세미 10개입",
                price="1500",
                currency="KRW",
                post_link="https://fmkorea.com/107",
                ecommerce_link="https://ko.aliexpress.com/",
                indexed_at=now_utc - timedelta(minutes=14),
            ),
        ]
    )
    audit_db.commit()

    try:
        client = _make_client(audit_db)

        ids_buds_spaced = {d["id"] for d in client.get("/api/community/hot-deals", params={"keyword": "갤럭시 버즈"}).json()["deals"]}
        ids_buds_compact = {d["id"] for d in client.get("/api/community/hot-deals", params={"keyword": "갤럭시버즈"}).json()["deals"]}
        assert ids_buds_spaced == {101, 102}
        assert ids_buds_compact == {101, 102}

        ids_fold_spaced = {d["id"] for d in client.get("/api/community/hot-deals", params={"keyword": "갤럭시 폴드"}).json()["deals"]}
        ids_fold_compact = {d["id"] for d in client.get("/api/community/hot-deals", params={"keyword": "갤럭시폴드"}).json()["deals"]}
        assert ids_fold_spaced == {103}
        assert ids_fold_compact == {103}

        ids_rtx_spaced = {d["id"] for d in client.get("/api/community/hot-deals", params={"keyword": "RTX 5070"}).json()["deals"]}
        ids_rtx_compact = {d["id"] for d in client.get("/api/community/hot-deals", params={"keyword": "RTX5070"}).json()["deals"]}
        assert ids_rtx_spaced == {104, 105}
        assert ids_rtx_compact == {104, 105}

        # 한/영 동의어('로지텍' -> 'Logitech') 매칭 + 루트 도메인 공유 무관 딜(107)이 DSU 백필로 섞여 나오지 않아야 함
        ids_logi = {d["id"] for d in client.get("/api/community/hot-deals", params={"keyword": "로지텍"}).json()["deals"]}
        assert ids_logi == {106}
        assert 107 not in ids_logi
    finally:
        app.dependency_overrides.clear()


def test_price_asc_sorting_zero_price_and_mislabeled_usd_cents(audit_db):
    """낮은가격순(price_asc)에서 0원 딜은 맨 아래로, USD 센트(본문 $82.93 오저장 포함)는 원화 환산가로 정렬되어야 한다."""
    comm = models.Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://ppomppu.co.kr")
    audit_db.add(comm)
    now_utc = datetime.now(timezone.utc).replace(tzinfo=None)

    audit_db.add_all(
        [
            models.Deal(
                id=201,
                source_community_id=1,
                title="광군제 핫딜 모음전 할인코드 총정리",
                price="0",
                currency="KRW",
                post_link="https://ppomppu.co.kr/201",
                ecommerce_link="https://ko.aliexpress.com/",
                indexed_at=now_utc - timedelta(minutes=10),
            ),
            models.Deal(
                id=202,
                source_community_id=1,
                title="[쿠팡] 삼다수 2L 6병 생수",
                price="2000",
                currency="KRW",
                post_link="https://ppomppu.co.kr/202",
                ecommerce_link="https://www.coupang.com/vp/products/202",
                indexed_at=now_utc - timedelta(minutes=9),
            ),
            models.Deal(
                id=203,
                source_community_id=1,
                title="[특가] Etenwolf V3 압축기 ($19.83/무료)",
                price="1983",
                currency="USD",
                post_link="https://ppomppu.co.kr/203",
                ecommerce_link="https://ko.aliexpress.com/item/203.html",
                indexed_at=now_utc - timedelta(minutes=8),
            ),
            # 실서버 ID=19531 사례: 제목에 $가 없고 본문에만 $82.93이 있는데 KRW 8293으로 잘못 적재된 딜
            models.Deal(
                id=204,
                source_community_id=1,
                title="레노버 샤오신패드 11 2025 / 프로 12.7",
                price="8293",
                currency="KRW",
                content_html="코드 AEKKOL105 적용 시 $102.93 -> $82.93 최대카할 기준",
                post_link="https://ppomppu.co.kr/204",
                ecommerce_link="https://ko.aliexpress.com/item/1005012718447754.html",
                indexed_at=now_utc - timedelta(minutes=7),
            ),
            models.Deal(
                id=205,
                source_community_id=1,
                title="국산 극세사 수면양말 5켤레",
                price="9900",
                currency="KRW",
                post_link="https://ppomppu.co.kr/205",
                ecommerce_link="https://smartstore.naver.com/shop/products/205",
                indexed_at=now_utc - timedelta(minutes=6),
            ),
        ]
    )
    audit_db.commit()

    try:
        client = _make_client(audit_db)
        deals = client.get("/api/community/hot-deals", params={"sort": "price_asc", "limit": 20}).json()["deals"]
        ordered_ids = [d["id"] for d in deals]
        # 기대 순서: 2,000원(202) -> 9,900원(205) -> $19.83≈27,762원(203) -> $82.93≈116,102원(204) -> 0원 가격확인(201)
        assert ordered_ids == [202, 205, 203, 204, 201]
        deal_204 = next(d for d in deals if d["id"] == 204)
        assert deal_204["currency"] == "USD"
    finally:
        app.dependency_overrides.clear()


def test_popular_keywords_blocks_malls_cliches_html_entities_units_and_storage_modifiers(audit_db, monkeypatch, tmp_path):
    """인기 검색어에서 쇼핑몰명, 할인/배송 상투어, 보관 수식어(냉장/냉동), 깨진 HTML 엔티티, 숫자/단위가 모두 배제되어야 한다."""
    comm = models.Community(id=1, name="fmkorea", display_name="펨코", base_url="https://fmkorea.com")
    audit_db.add(comm)
    now_utc = datetime.now(timezone.utc).replace(tzinfo=None)

    # 오염된 펨코 실시간 검색어 JSON 시뮬레이션
    fake_trending = tmp_path / "fmkorea_trending.json"
    fake_trending.write_text(
        json.dumps(
            {
                "updated_at": "2026-11-11T12:00:00",
                "keywords": ["쿠팡", "11번가", "무신사", "핫딜", "역대가", "무배", "100g", "1+1", "2026", "&amp;", "냉장", "로보락"],
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    for idx in range(6):
        audit_db.add(
            models.Deal(
                id=300 + idx,
                source_community_id=1,
                title=f"[쿠팡] 11번가 무신사 역대가 핫딜 무배 냉장 &amp; &#039; 100g 1+1 2026 로보락 S8 맥스V ({10000 + idx}원/와우무배",
                price="599000",
                currency="KRW",
                post_link=f"https://fmkorea.com/30{idx}",
                indexed_at=now_utc - timedelta(minutes=idx),
            )
        )
    audit_db.commit()

    import backend.routers.community as comm_mod
    monkeypatch.setattr(comm_mod, "_FMKOREA_TRENDING_FILE_OVERRIDE", str(fake_trending), raising=False)

    try:
        client = _make_client(audit_db)
        res = client.get("/api/community/popular-keywords", params={"limit": 10}).json()
        kws = [k.lower() for k in res["keywords"]]
        forbidden = {"쿠팡", "11번가", "무신사", "핫딜", "역대가", "무배", "와우무배", "100g", "1+1", "2026", "amp", "039", "nbsp", "냉장"}
        assert forbidden.isdisjoint(set(kws)), f"Forbidden noise leaked into popular keywords: {kws}"
        assert "로보락" in res["keywords"]
    finally:
        app.dependency_overrides.clear()


def test_history_api_heals_pollution_supports_period_all_kst_iso_and_currency(audit_db):
    """
    1) 0원 딜(ID=19533 모음전 777원 잔재, ID=16 10원 잔재, ID=100 타 단품 노이즈)에서 빈 배열 반환 및 renormalize 치유 검증
    2) period=all 조회 시 7일 폴백 없이 과거(120일 전) 이력까지 모두 반환 검증
    3) UTC 저장 시각이 KST(+09:00)로 정확히 변환되고 응답에 currency 및 ISO-8601('T' 구분자) 타임스탬프가 포함되는지 검증
    4) 단일 게시글 동일 가격에서 불필요한 중복 포인트가 생성되지 않는지 검증
    """
    comm = models.Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://ppomppu.co.kr")
    audit_db.add(comm)

    # KST 2026-11-11 13:10:00 == UTC 2026-11-11 04:10:00
    base_utc = datetime(2026, 11, 11, 4, 10, 0)

    # 케이스 A: 모음전 0원 딜인데 PriceHistory에 과거 777원 더미가 남은 경우 (실서버 ID=19533 재현)
    d_zero_dummy = models.Deal(
        id=19533,
        source_community_id=1,
        title="광군제 핫딜 모음전 | 특별 할인코드·카드·간편결제 혜택",
        price="777",
        currency="KRW",
        post_link="https://ppomppu.co.kr/19533",
        ecommerce_link="https://ko.aliexpress.com/",
        indexed_at=base_utc - timedelta(hours=5),
    )
    ph_zero_dummy = models.PriceHistory(deal_id=19533, price="777", checked_at=base_utc - timedelta(hours=5))

    # 케이스 B: 시각 오파싱 10원이 PriceHistory에 남고 본문 가격이 15,120원인 딜 (실서버 ID=16 유형 재현)
    d_ten_won = models.Deal(
        id=16,
        source_community_id=1,
        title="[네이버] 베베솜 엄마턱받이+아기턱받이 최대57% (10/13 오전10:30)",
        price="10",
        currency="KRW",
        content_html="상품명 베베솜 엄마턱받이 가격 15,120원",
        post_link="https://ppomppu.co.kr/16",
        ecommerce_link="https://shoppinglive.naver.com/livebridge/2022086",
        indexed_at=base_utc - timedelta(days=2),
    )
    ph_ten_won = models.PriceHistory(deal_id=16, price="10", checked_at=base_utc - timedelta(days=2))

    # 케이스 C: 0원 프로모션 안내 딜 주변에 동일 브랜드 단품(1,600원) 딜이 있을 때 0원 딜 히스토리에 섞이지 않아야 함 (실서버 ID=100 재현)
    d_zero_promo = models.Deal(
        id=100,
        source_community_id=1,
        title="[네이버쇼핑] 메가커피 메뉴 22%+멤버십 10% 추가 할인",
        price="0",
        currency="KRW",
        post_link="https://ppomppu.co.kr/100",
        indexed_at=base_utc - timedelta(days=1),
    )
    d_mega_item = models.Deal(
        id=101,
        source_community_id=1,
        title="[네이버쇼핑] 메가커피 아메리카노 기프티콘",
        price="1600",
        currency="KRW",
        post_link="https://ppomppu.co.kr/101",
        indexed_at=base_utc - timedelta(days=2),
    )

    # 케이스 D: 150일 전 ~ 현재까지 이어진 USD 딜 (period=all, KST 변환, ISO-8601 타임스탬프, currency 검증)
    d_usd_old = models.Deal(
        id=500,
        source_community_id=1,
        title="유그린 클립버즈 무선 이어폰 WS200",
        price="1917",
        currency="USD",
        model_code="WS200",
        post_link="https://ppomppu.co.kr/500",
        ecommerce_link="https://ko.aliexpress.com/item/100500999999.html",
        indexed_at=base_utc - timedelta(days=150),
    )
    d_usd_now = models.Deal(
        id=501,
        source_community_id=1,
        title="유그린 클립버즈 무선 이어폰 WS200 ($12.53/무료)",
        price="1253",
        currency="USD",
        model_code="WS200",
        post_link="https://ppomppu.co.kr/501",
        ecommerce_link="https://ko.aliexpress.com/item/100500999999.html",
        indexed_at=base_utc,
    )
    ph_usd_dup1 = models.PriceHistory(deal_id=501, price="1253", checked_at=base_utc)
    ph_usd_dup2 = models.PriceHistory(deal_id=501, price="1253", checked_at=base_utc + timedelta(minutes=3))

    audit_db.add_all([d_zero_dummy, ph_zero_dummy, d_ten_won, ph_ten_won, d_zero_promo, d_mega_item, d_usd_old, d_usd_now, ph_usd_dup1, ph_usd_dup2])
    audit_db.commit()

    # 1) 치유 실행 전에도 읽기 시점 방어가 동작하여 19533과 100은 빈 히스토리를 반환하고, 16은 10원 오염 포인트를 배제해야 함
    assert _compute_deal_history(19533, "7d", audit_db) == []
    assert _compute_deal_history(100, "1y", audit_db) == []
    hist_16_before = _compute_deal_history(16, "7d", audit_db)
    assert all(p["price"] != 10 for p in hist_16_before)

    # 2) renormalize_existing_deals 실행 시 PriceHistory 오염 레코드까지 영구 정화되어야 함
    stats = renormalize_existing_deals(audit_db, dry_run=False)
    assert stats["price_history_cleaned"] >= 2
    assert audit_db.query(models.PriceHistory).filter(models.PriceHistory.deal_id == 19533).count() == 0
    ph_16_after = audit_db.query(models.PriceHistory).filter(models.PriceHistory.deal_id == 16).all()
    assert all(p.price != "10" for p in ph_16_after)

    # 3) period=7d에서는 501 단독 1개 포인트만(중복 생성 없음), period=all에서는 150일 전 500번 딜까지 2개 포인트 반환
    hist_7d = _compute_deal_history(501, "7d", audit_db)
    assert len(hist_7d) == 1
    assert hist_7d[0]["price"] == 1253
    assert hist_7d[0]["currency"] == "USD"
    # UTC 04:10 -> KST 13:10 변환 검증
    assert hist_7d[0]["recordedAt"].startswith("11.11 13:1")
    assert "T" in hist_7d[0]["timestamp"] and " " not in hist_7d[0]["timestamp"]
    assert hist_7d[0]["timestamp"].endswith("+09:00")

    hist_all = _compute_deal_history(501, "all", audit_db)
    assert [p["price"] for p in hist_all] == [1917, 1253]
    assert all(p["currency"] == "USD" for p in hist_all)
    assert all("T" in p["timestamp"] and p["timestamp"].endswith("+09:00") for p in hist_all)


def test_history_cross_currency_isolation_and_uniform_date_format(audit_db):
    """
    1) 실서버 ID=19198 사례: CNY(18900) 딜에 대해 currency=USD 요청 시 CNY 가격이 USD($189.00)로 둔갑하지 않고 USD 딜(2587)만 반환되어야 한다.
    2) 실서버 ID=19471/25 사례: 1m/3m/7d_auto 장기 추이에서 같은 날짜에 복수 포인트가 있어도 YY.MM.DD와 MM.DD HH:MM 포맷이 뒤섞이지 않아야 한다.
    """
    import re

    comm = models.Community(id=1, name="quasarzone", display_name="퀘이사존", base_url="https://quasarzone.com")
    audit_db.add(comm)
    now_utc = datetime(2026, 11, 11, 4, 0, 0)

    d_cny = models.Deal(
        id=19198,
        source_community_id=1,
        title="[타오바오] 독거미 AULA F99 무선 키보드",
        price="18900",
        currency="CNY",
        model_code="AULAF99",
        post_link="https://quasarzone.com/19198",
        ecommerce_link="https://item.taobao.com/item.htm?id=19198",
        indexed_at=now_utc - timedelta(hours=6),
    )
    d_usd_peer = models.Deal(
        id=19200,
        source_community_id=1,
        title="[알리] 독거미 AULA F99 무선 키보드 ($25.87/무료)",
        price="2587",
        currency="USD",
        model_code="AULAF99",
        post_link="https://quasarzone.com/19200",
        ecommerce_link="https://ko.aliexpress.com/item/19200.html",
        indexed_at=now_utc - timedelta(days=20),
    )
    d_usd_peer2 = models.Deal(
        id=19201,
        source_community_id=1,
        title="[알리] 독거미 AULA F99 무선 키보드 특가 ($23.50/무료)",
        price="2350",
        currency="USD",
        model_code="AULAF99",
        post_link="https://quasarzone.com/19201",
        ecommerce_link="https://ko.aliexpress.com/item/19200.html",
        indexed_at=now_utc - timedelta(days=20) + timedelta(hours=3),
    )
    d_usd_peer3 = models.Deal(
        id=19202,
        source_community_id=1,
        title="[알리] 독거미 AULA F99 무선 키보드 광군제 ($21.90/무료)",
        price="2190",
        currency="USD",
        model_code="AULAF99",
        post_link="https://quasarzone.com/19202",
        ecommerce_link="https://ko.aliexpress.com/item/19200.html",
        indexed_at=now_utc - timedelta(days=10),
    )
    audit_db.add_all([d_cny, d_usd_peer, d_usd_peer2, d_usd_peer3])
    audit_db.commit()

    try:
        client = _make_client(audit_db)

        # currency=USD 지정 시 CNY 18900이 절대 섞이지 않고 USD 포인트만 내려와야 함
        hist_usd = client.get("/api/community/deals/19198/history", params={"period": "1m", "currency": "USD"}).json()
        assert [p["price"] for p in hist_usd] == [2587, 2350, 2190]
        assert all(p["currency"] == "USD" for p in hist_usd)

        # 1m 조회 시 같은 날 2개 포인트(2587, 2350)가 있어도 모든 recordedAt이 일관된 YY.MM.DD 포맷이어야 함
        assert all(re.fullmatch(r"\d{2}\.\d{2}\.\d{2}", p["recordedAt"]) for p in hist_usd)

        # currency 미지정 시 Deal(19198) 고유 통화(CNY)가 명시되어 반환되어야 함
        hist_cny = client.get("/api/community/deals/19198/history", params={"period": "7d_auto"}).json()
        assert len(hist_cny) == 1
        assert hist_cny[0]["price"] == 18900
        assert hist_cny[0]["currency"] == "CNY"

        # 상세 API의 indexed_at도 KST(+09:00) ISO-8601('T' 구분자) 포맷이어야 함
        det = client.get("/api/community/deals/19198").json()
        assert "T" in det["indexed_at"] and det["indexed_at"].endswith("+09:00")
    finally:
        app.dependency_overrides.clear()


def test_fmkorea_trending_scraper_filters_noise_and_unescapes_html(monkeypatch, tmp_path):
    """펨코 실시간 검색어 스크래퍼가 수집 단계에서 쇼핑몰명·상투어·깨진 엔티티·단위 숫자를 제거하는지 검증한다."""
    import backend.scrapers.fmkorea_trending_scraper as fm_mod

    sample_html = """
    <ul class="keyword_rank_ticker">
        <li><a data-keyword="쿠팡">1. 쿠팡</a></li>
        <li><a data-keyword="11번가">2. 11번가</a></li>
        <li><a data-keyword="역대가">3. 역대가</a></li>
        <li><a data-keyword="100g">4. 100g</a></li>
        <li><a data-keyword="1+1">5. 1+1</a></li>
        <li><a data-keyword="&amp;">6. &amp;</a></li>
        <li><a data-keyword="갤럭시">7. 갤럭시</a></li>
        <li><a data-keyword="스탠리">8. 스탠리</a></li>
    </ul>
    """

    class DummyResp:
        status_code = 200
        text = sample_html

    out_file = tmp_path / "data" / "fmkorea_trending.json"
    monkeypatch.setattr(fm_mod.requests, "get", lambda *a, **kw: DummyResp())
    monkeypatch.setattr(fm_mod.os.path, "dirname", lambda p: str(tmp_path) if p == fm_mod.__file__ else str(tmp_path))

    ok = fm_mod.update_fmkorea_trending_keywords()
    assert ok is True
    saved = json.loads(out_file.read_text(encoding="utf-8"))
    assert saved["keywords"] == ["갤럭시", "스탠리"]
    assert "T" in saved["updated_at"] and saved["updated_at"].endswith("+09:00")


@pytest.mark.asyncio
async def test_aggregator_builds_search_keywords_and_blocks_cross_currency_merge(audit_db):
    """수집 시 search_keywords가 자동 생성되고, USD 딜과 KRW 딜이 단일 레코드로 오병합되어 통화/히스토리가 오염되지 않아야 한다."""
    from backend.services.aggregator_service import AggregatorService

    comm = models.Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://ppomppu.co.kr")
    audit_db.add(comm)
    audit_db.commit()

    agg = AggregatorService(audit_db)
    d_usd = await agg.process_scraped_deal(
        1,
        {
            "title": "[알리] 유그린 100W 고속충전기 X757 ($25.00/무배)",
            "price": 2500,
            "currency": "USD",
            "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&no=9001",
            "ecommerce_link": "https://ko.aliexpress.com/item/90019001.html",
            "content_html": "유그린 X757 $25.00 무료배송",
        },
    )
    assert d_usd is not None
    assert d_usd.currency == "USD"
    assert d_usd.price == "2500"
    assert d_usd.search_keywords and "ugreen" in d_usd.search_keywords

    # 동일 모델(X757)이지만 원화(35,500원)로 올라온 별도 게시글이 USD 딜 레코드에 덮어써지면 안 됨
    d_krw = await agg.process_scraped_deal(
        1,
        {
            "title": "[쿠팡] 유그린 100W 고속충전기 X757 (35,500원/무배)",
            "price": 35500,
            "currency": "KRW",
            "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=9002",
            "ecommerce_link": "https://www.coupang.com/vp/products/90029002",
            "content_html": "유그린 X757 정발 35,500원",
        },
    )
    assert d_krw is not None
    assert d_krw.id != d_usd.id
    audit_db.refresh(d_usd)
    assert d_usd.currency == "USD"
    assert d_usd.price == "2500"
    ph_usd_prices = [ph.price for ph in audit_db.query(models.PriceHistory).filter_by(deal_id=d_usd.id).all()]
    assert ph_usd_prices == ["2500"]

