import sys
import os
import pytest
import asyncio
from datetime import datetime
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# 프로젝트 루트 경로 설정
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from backend.database.models import Base, Deal, PriceHistory, Community
from backend.services.aggregator_service import (
    AggregatorService,
    extract_structured_multi_options,
)


@pytest.fixture
def db_session():
    """인메모리 SQLite DB 세션 피스처"""
    engine = create_engine("sqlite:///:memory:", echo=False)
    Base.metadata.create_all(bind=engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    try:
        # 기본 커뮤니티 데이터 시딩
        c_ppomppu = Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://ppomppu.co.kr")
        session.add(c_ppomppu)
        session.commit()
        yield session
    finally:
        session.close()


# =============================================================================
# 1. 현안 2 검증: parse_price_token 및 슬래시/괄호 파서의 모델명 숫자 가격 오인 방지
# =============================================================================

def test_pure_numbers_not_converted_to_price_in_options_parser():
    """통화 기호 없는 순수 숫자(12.7, 2025, 65, 75, 15.6 등)가 가격으로 오인되지 않는지 검증"""
    # 1. 태블릿 인치 및 출시 연도 슬래시 표기 단일 상품 -> 0개 옵션 (단일 딜 유지)
    title1 = "[11번가] 레노버 샤오신패드 프로 12.7 / 2025 관부가세포함 (189,000원/무배)"
    opts1 = extract_structured_multi_options(title1, "", "KRW")
    assert len(opts1) == 0, f"Expected 0 options (single deal), got {opts1}"

    # 2. TV 화면 인치 나열 단일 상품 -> 0개 옵션
    title2 = "[하이마트] LG 올레드 TV 65인치 / 75인치 (1,690,000원/무배)"
    opts2 = extract_structured_multi_options(title2, "", "KRW")
    assert len(opts2) == 0, f"Expected 0 options (single deal), got {opts2}"

    # 3. 휴대용 모니터 인치 슬래시 단일 상품 -> 0개 옵션
    title3 = "제우스랩 15.6 / 16인치 휴대용 모니터"
    opts3 = extract_structured_multi_options(title3, "", "KRW")
    assert len(opts3) == 0, f"Expected 0 options (single deal), got {opts3}"

    # 4. 괄호 속 인치/연도 표기 (12.7), (2025) -> 0개 옵션
    title4 = "샤오신패드 (12.7) / (2025)"
    opts4 = extract_structured_multi_options(title4, "", "KRW")
    assert len(opts4) == 0, f"Expected 0 options, got {opts4}"


def test_valid_currency_slash_multi_options_are_correctly_split():
    """통화 기호/단위가 온전히 결합된 진짜 슬래시 다중 옵션은 정상적으로 2개 이상 분할되는지 검증"""
    title = "뷰몬트 15.6 모니터 $34.56 / 경동나비엔 EME550S 24만"
    opts = extract_structured_multi_options(
        raw_title=title,
        content_html="",
        default_currency="KRW",
        default_url="https://example.com/test",
    )
    assert len(opts) == 2, f"Expected 2 options, got {len(opts)}"

    assert "뷰몬트" in opts[0]["name"]
    assert "15.6" in opts[0]["name"]
    assert opts[0]["price"] == 3456
    assert opts[0]["currency"] == "USD"

    assert "경동나비엔" in opts[1]["name"]
    assert "EME550S" in opts[1]["name"]
    assert opts[1]["price"] == 240000
    assert opts[1]["currency"] == "KRW"


def test_valid_bracket_multi_options_are_correctly_split():
    """통화 단위가 결합된 괄호 나열형 다중 옵션 정상 분할 검증"""
    title = "CJ 비비고 왕교자 1.05kg (12,900원) / 고메 크리스피 핫도그 (9,900원)"
    opts = extract_structured_multi_options(
        raw_title=title,
        content_html="",
        default_currency="KRW",
        default_url="https://example.com/cj",
    )
    assert len(opts) == 2, f"Expected 2 options, got {len(opts)}"
    assert opts[0]["price"] == 12900
    assert opts[0]["currency"] == "KRW"
    assert opts[1]["price"] == 9900
    assert opts[1]["currency"] == "KRW"


# =============================================================================
# 2. 현안 3 검증: 뽐뿌 게시글 끌올/재활용 Full Force Sync
# =============================================================================

@pytest.mark.asyncio
async def test_recycled_post_full_force_sync(db_session):
    """
    동일 URL의 게시글을 작성자가 다른 상품으로 재활용(수정)했을 때:
    제목, 본문, 구매링크, 대표이미지, options_data, PriceHistory가 100% 최신화되는지 검증
    """
    aggregator = AggregatorService(db_session)
    url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=999999"

    # 1. 기존 글(8종 커피 모음전, 긴 본문, 15,000원) 최초 수집 등록
    old_data = {
        "title": "[G마켓] 캡슐커피 8종 골라담기 모음전 (15,000원/무배)",
        "url": url,
        "price": 15000,
        "content_html": "<p>과거의 매우 긴 8종 커피 상세 설명 본문입니다.</p>" * 20,
        "ecommerce_link": "https://gmarket.co.kr/item/old_coffee",
        "image_url": "https://gmarket.co.kr/img/old_coffee.jpg",
        "options_data": [{"name": f"커피 캡슐 {i}호", "price": 15000 + i * 500, "url": ""} for i in range(8)],
    }
    deal1 = await aggregator.process_scraped_deal(1, old_data)
    assert deal1.id is not None
    assert deal1.title == old_data["title"]
    assert deal1.has_options is True
    assert deal1.price == "15000"

    # 최초 가격 히스토리 확인
    ph_count1 = db_session.query(PriceHistory).filter(PriceHistory.deal_id == deal1.id).count()
    assert ph_count1 == 1

    # 2. 작성자가 해당 글을 완전히 다른 단일 모니터 상품으로 수정(재활용)
    new_data = {
        "title": "[11번가] LG 27인치 게이밍 모니터 27GP850 (289,000원/무배)",
        "url": url,
        "price": 289000,
        "content_html": "<p>새로운 모니터 특가 간결 본문</p>",
        "ecommerce_link": "https://11st.co.kr/products/new_monitor",
        "image_url": "https://11st.co.kr/img/new_monitor.jpg",
        "options_data": [],
    }
    deal2 = await aggregator.process_scraped_deal(1, new_data)

    # 3. Full Force Sync 검증: 동일 레코드에서 모든 정보가 새 상품으로 완전 최신화되었는가?
    assert deal2.id == deal1.id, "동일한 post_link이므로 같은 Deal ID를 유지해야 함"
    assert deal2.title == new_data["title"], "제목이 최신 모니터 제목으로 갱신되어야 함"
    assert deal2.price == "289000", "가격이 최신 모니터 가격으로 갱신되어야 함"
    assert deal2.ecommerce_link == new_data["ecommerce_link"], "구매 링크가 새 쇼핑몰 링크로 갱신되어야 함"
    assert deal2.image_url == new_data["image_url"], "대표 이미지가 새 상품 이미지로 갱신되어야 함"
    assert deal2.content_html == new_data["content_html"], "본문이 짧아졌더라도 최신 본문으로 갱신되어야 함"
    assert deal2.has_options is False, "과거 8종 옵션이 역오염되지 않고 말끔히 제거되어야 함"
    assert deal2.options_data is None, "options_data가 None이어야 함"

    # PriceHistory 검증: 과거 커피 가격(15,000원)은 정리되고 새 모니터 가격(289,000원)이 기준점이 됨
    histories = db_session.query(PriceHistory).filter(PriceHistory.deal_id == deal2.id).all()
    assert len(histories) == 1, "과거 이종 상품 가격이 제거되고 새 가격 1건만 존재해야 함"
    assert histories[0].price == "289000", "새로운 가격 289,000원이 기록되어야 함"


@pytest.mark.asyncio
async def test_recycled_post_with_new_options_sync(db_session):
    """재활용 게시글이 새로운 다중 옵션을 포함할 때, 새 옵션으로 완전 대체되는지 검증"""
    aggregator = AggregatorService(db_session)
    url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=888888"

    # 1. 단일 라면 특가 등록
    old_data = {
        "title": "[옥션] 신라면 20봉 (14,000원/무배)",
        "url": url,
        "price": 14000,
        "content_html": "<p>라면 본문</p>",
        "ecommerce_link": "https://auction.co.kr/ramen",
        "options_data": [],
    }
    deal1 = await aggregator.process_scraped_deal(1, old_data)
    assert deal1.has_options is False

    # 2. 2종 마우스 모음전으로 재활용 수정
    new_data = {
        "title": "[G마켓] 로지텍 무선마우스 G304 / MX Master 3S 특가 (39,000원/무배)",
        "url": url,
        "price": 39000,
        "content_html": "<p>마우스 본문</p>",
        "ecommerce_link": "https://gmarket.co.kr/mouse",
        "options_data": [
            {"name": "로지텍 G304", "price": 39000, "url": "https://gmarket.co.kr/g304"},
            {"name": "로지텍 MX Master 3S", "price": 99000, "url": "https://gmarket.co.kr/mx3s"},
        ],
    }
    deal2 = await aggregator.process_scraped_deal(1, new_data)

    assert deal2.id == deal1.id
    assert deal2.title == new_data["title"]
    assert deal2.has_options is True
    assert "로지텍 G304" in deal2.options_data
    assert "로지텍 MX Master 3S" in deal2.options_data


@pytest.mark.asyncio
async def test_normal_title_tweak_not_treated_as_recycled(db_session):
    """동일 상품의 단순 말머리 추가([끌올])나 소폭 가격 변경은 재활용으로 오인하지 않고 정상 업데이트되는지 검증"""
    aggregator = AggregatorService(db_session)
    url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=777777"

    # 1. 최초 등록
    d1_data = {
        "title": "[11번가] 레노버 샤오신패드 프로 12.7 (189,000원/무배)",
        "url": url,
        "price": 189000,
        "content_html": "<p>샤오신패드 설명 본문</p>",
        "ecommerce_link": "https://11st.co.kr/pad127",
        "options_data": [],
    }
    deal1 = await aggregator.process_scraped_deal(1, d1_data)

    # 2. 동일 상품 [끌올] 및 가격 소폭 하락(179,000원)
    d2_data = {
        "title": "[11번가] [끌올] 레노버 샤오신패드 프로 12.7 관부가세포함 (179,000원/무배)",
        "url": url,
        "price": 179000,
        "content_html": "<p>샤오신패드 설명 본문</p>",
        "ecommerce_link": "https://11st.co.kr/pad127",
        "options_data": [],
    }
    deal2 = await aggregator.process_scraped_deal(1, d2_data)

    assert deal2.id == deal1.id
    assert deal2.title == d2_data["title"]
    assert deal2.price == "179000"

    # 동일 상품의 가격 변동이므로 PriceHistory에 이전 가격과 새 가격이 모두 보존되어 시세 히스토리를 구성해야 함
    histories = db_session.query(PriceHistory).filter(PriceHistory.deal_id == deal2.id).order_by(PriceHistory.id.asc()).all()
    assert len(histories) == 2
    assert histories[0].price == "189000"
    assert histories[1].price == "179000"
