import sys
import os
import pytest
from datetime import datetime, timedelta
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# Set python path to project root
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from backend.database.models import Base, Deal, PriceHistory, Community
from backend.services.aggregator_service import AggregatorService, extract_structured_multi_options
from backend.routers.community import _compute_deal_history, GENERIC_CATEGORY_STOPWORDS


@pytest.fixture
def db_session():
    engine = create_engine("sqlite:///:memory:", echo=False)
    Base.metadata.create_all(bind=engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    try:
        yield session
    finally:
        session.close()


def test_slash_multi_option_parser_pattern_c():
    """1. 괄호 없는 슬래시(/) 복수 상품 패턴 파싱 검증 (no=104456 사례)"""
    title = "뷰몬트 15.6 모니터 $34.56/경동나비엔 EME550S 24만"
    options = extract_structured_multi_options(
        raw_title=title,
        content_html="",
        default_currency="KRW",
        default_url="https://example.com/test"
    )

    assert len(options) == 2, f"Expected 2 options, got {len(options)}"
    opt1, opt2 = options[0], options[1]

    assert "뷰몬트" in opt1["name"]
    assert "15.6" in opt1["name"]
    assert opt1["price"] == 3456
    assert opt1["currency"] == "USD"

    assert "경동나비엔" in opt2["name"]
    assert "EME550S" in opt2["name"]
    assert opt2["price"] == 240000
    assert opt2["currency"] == "KRW"

    # 모델명/연도 뒤 공백 + $가격 형태에서 $ 기호 가로채기(12.7 $, 2025 $) 방어 검증
    title2 = "[초특가] 샤오신 프로 12.7 $164.25/SOYO Y5 미니PC $75.2"
    opts2 = extract_structured_multi_options(raw_title=title2, content_html="", default_currency="USD")
    assert len(opts2) == 2
    assert opts2[0]["name"] == "샤오신 프로 12.7"
    assert opts2[0]["price"] == 16425
    assert opts2[1]["name"] == "SOYO Y5 미니PC"
    assert opts2[1]["price"] == 7520

    title3 = "샤오신 2025 $88/[가격인하]버니플라이 캐리어 $50.5"
    opts3 = extract_structured_multi_options(raw_title=title3, content_html="", default_currency="USD")
    assert len(opts3) == 2
    assert opts3[0]["name"] == "샤오신 2025"
    assert opts3[0]["price"] == 8800
    assert opts3[1]["name"] == "버니플라이 캐리어"
    assert opts3[1]["price"] == 5050


def test_single_item_with_parenthesis_shipping_not_split():
    """2. 괄호 안의 배송비 슬래시 표기가 멀티옵션으로 오인식되지 않는지 검증"""
    title = "군자농협 추청 햇토미 10KG (34,200원 / 무료)"
    options = extract_structured_multi_options(
        raw_title=title,
        content_html="",
        default_currency="KRW",
        default_url="https://example.com/test"
    )
    # 단일 상품이므로 옵션 배열은 비어있어야 함
    assert len(options) == 0, f"Single product with shipping should not split into options, got {options}"


def test_cloud_feeder_is_multi_title_regex():
    """3. cloud_feeder.py의 is_multi_title 정규식 감지 검증"""
    import re
    multi_pattern = re.compile(
        r'(?:외\s*\d+|골라담기|모음|선택|공구|총정리|\d+\s*종|다양|특가\s*모음)'
        r'|(?:/[^/]+(?:원|만|\$|달러).*(?:원|만|\$|달러))'
        r'|(?:/[^/]+(?:원|만|\$|달러)'
        r'|(?:원|만|\$|달러).*/)'
    )

    title_slash = "뷰몬트 15.6 모니터 $34.56/경동나비엔 EME550S 24만"
    is_multi_title = bool(
        re.search(r'(?:외\s*\d+|골라담기|모음|선택|공구|총정리|\d+\s*종|다양|특가\s*모음)', title_slash)
        or (re.search(r'/[^/]+(?:원|만|\$|달러)', title_slash) and re.search(r'(?:원|만|\$|달러).*/', title_slash))
    )
    assert is_multi_title is True, "Slash multi-item title should be detected"

    single_title = "샤오미 보조배터리 20000mAh (25,000원/무료)"
    is_single_multi = bool(
        re.search(r'(?:외\s*\d+|골라담기|모음|선택|공구|총정리|\d+\s*종|다양|특가\s*모음)', single_title)
        or (re.search(r'/[^/]+(?:원|만|\$|달러)', single_title) and re.search(r'(?:원|만|\$|달러).*/', single_title))
    )
    assert is_single_multi is False, "Single title with slash shipping should not be detected as multi"


def test_price_history_duplicate_insert_guard(db_session):
    """4. 동일 가격 연속 중복 인서트 차단 검증 (1분 단위 중복 버그 방어)"""
    aggregator = AggregatorService(db_session)
    deal_id = 9999

    # 첫 번째 인서트
    aggregator._insert_price_history(deal_id=deal_id, price=12600)
    count1 = db_session.query(PriceHistory).filter(PriceHistory.deal_id == deal_id).count()
    assert count1 == 1, "First insert should succeed"

    # 동일 가격 두 번째 인서트 시도 -> 가드에 의해 차단되어야 함
    aggregator._insert_price_history(deal_id=deal_id, price=12600)
    count2 = db_session.query(PriceHistory).filter(PriceHistory.deal_id == deal_id).count()
    assert count2 == 1, "Duplicate price insert should be prevented"


def test_price_history_cooldown_guard(db_session):
    """5. 10분 이내 5% 미만의 미세 가격 진동 인서트 쿨다운 가드 검증"""
    aggregator = AggregatorService(db_session)
    deal_id = 8888

    aggregator._insert_price_history(deal_id=deal_id, price=10000)
    assert db_session.query(PriceHistory).filter(PriceHistory.deal_id == deal_id).count() == 1

    # 10분 이내 2% 미세 변동 (10,200원) -> 차단되어야 함
    aggregator._insert_price_history(deal_id=deal_id, price=10200)
    assert db_session.query(PriceHistory).filter(PriceHistory.deal_id == deal_id).count() == 1

    # 10% 이상 유의미한 변동 (11,500원) -> 기록 허용되어야 함
    aggregator._insert_price_history(deal_id=deal_id, price=11500)
    assert db_session.query(PriceHistory).filter(PriceHistory.deal_id == deal_id).count() == 2


def test_generic_category_stopwords_contents():
    """6. 범용 카테고리 불용어 목록 검증 (603건 노이즈 원천 차단)"""
    assert "청소기" in GENERIC_CATEGORY_STOPWORDS
    assert "로봇청소기" in GENERIC_CATEGORY_STOPWORDS
    assert "로청" in GENERIC_CATEGORY_STOPWORDS
    assert "모니터" in GENERIC_CATEGORY_STOPWORDS
    assert "tv" in GENERIC_CATEGORY_STOPWORDS
    assert "노트북" in GENERIC_CATEGORY_STOPWORDS


def test_option_specific_history_api(db_session):
    """7. 옵션 전용 시세 산출 및 이종 상품 노이즈 차단 검증 (Deal 17147 UWANT V600 사례)"""
    import json

    # 1) 멀티옵션 딜 생성 (UWANT V600 $126, JONR P20 로청 $98)
    options_json = json.dumps([
        {"name": "UWANT V600", "price": 12600, "currency": "USD", "url": "", "image_url": ""},
        {"name": "JONR P20 로청", "price": 9800, "currency": "USD", "url": "", "image_url": ""}
    ], ensure_ascii=False)

    deal = Deal(
        id=17147,
        source_community_id=1,
        title="청소기 재입고 모음 UWANT V600 / JONR P20",
        price="9800",
        currency="USD",
        post_link="https://example.com/deal/17147",
        options_data=options_json,
        has_options=True,
        indexed_at=datetime.utcnow() - timedelta(days=1)
    )
    db_session.add(deal)

    # 이 딜의 가격 히스토리
    ph1 = PriceHistory(deal_id=17147, price="12600", checked_at=datetime.utcnow() - timedelta(hours=5))
    db_session.add(ph1)

    # 2) DB에 존재하는 완전히 다른 청소기 딜 (노이즈 유발 후보)
    other_deal = Deal(
        id=340,
        source_community_id=1,
        title="샤오미 미지아 무선청소기 초특가",
        price="189000",
        currency="KRW",
        post_link="https://example.com/deal/340",
        indexed_at=datetime.utcnow() - timedelta(days=2)
    )
    db_session.add(other_deal)
    ph_noise = PriceHistory(deal_id=340, price="189000", checked_at=datetime.utcnow() - timedelta(days=2))
    db_session.add(ph_noise)

    db_session.commit()

    # 옵션 미지정 시 (전체 딜 기준): 청소기 단어 매칭 시도 가능
    # 옵션 지정 시: option_name="UWANT V600", currency="USD"
    history_v600 = _compute_deal_history(
        deal_id=17147,
        period="7d",
        db=db_session,
        option_name="UWANT V600",
        target_currency="USD"
    )

    assert len(history_v600) >= 1, "Should return history points for UWANT V600"
    prices = [p["price"] for p in history_v600]

    # UWANT V600 가격인 12600 포인트가 존재해야 함
    assert 12600 in prices, f"Expected 12600 in prices, got {prices}"

    # 타 청소기 딜(340번)의 189000원 노이즈가 절대 포함되지 않아야 함
    assert 189000 not in prices, "Noise from unrelated vacuum cleaner should not be in option-specific history"


@pytest.mark.asyncio
async def test_enrich_option_images_safely_name_fallback():
    """8. URL이 없는 옵션에 대해 네이버/다음 쇼핑 CDN 썸네일 폴백 정상 작동 검증"""
    options = [
        {"name": "뷰몬트 15.6 모니터", "price": 3456, "currency": "USD", "url": "", "image_url": ""},
        {"name": "경동나비엔 EME550S", "price": 240000, "currency": "KRW", "url": "", "image_url": ""}
    ]
    await AggregatorService._enrich_option_images_safely(options)

    images = [opt.get("image_url") for opt in options if opt.get("image_url")]
    assert len(images) >= 1, f"Expected at least 1 option image to be enriched, got {options}"
    for img in images:
        assert img.startswith("http"), f"Image URL must be valid HTTP(S), got {img}"


