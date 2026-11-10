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
async def test_enrich_option_images_safely_no_search_pollution():
    """8. 고유 직링크(지마켓/옥션)는 1:1 이미지를 조립하고, URL이 없거나 공유된 옵션은 무관한 웹검색 이미지로 오염시키지 않고 빈 문자열("")을 유지하는지 검증"""
    options = [
        {"name": "지마켓 단독 옵션", "price": 15000, "currency": "KRW", "url": "https://item.gmarket.co.kr/Item?goodscode=1234567890", "image_url": ""},
        {"name": "뷰몬트 15.6 모니터", "price": 3456, "currency": "USD", "url": "", "image_url": ""},
        {"name": "경동나비엔 EME550S", "price": 240000, "currency": "KRW", "url": "", "image_url": ""}
    ]
    await AggregatorService._enrich_option_images_safely(options)

    assert options[0]["image_url"] == "https://gdimg.gmarket.co.kr/1234567890/still/600"
    assert options[1]["image_url"] == ""
    assert options[2]["image_url"] == ""


def test_parenthesized_usd_and_unmatched_bracket_multi_options():
    """9. 괄호 내부 전위 달러(($105), ($237.68/무료)) 및 닫는 괄호 오타 머리말(잔여공구코드], 알리 잔여코드)) 정밀 파싱 검증"""
    title1 = "잔여공구코드] 빅미 B6 전자책($105), 샥즈 오픈런 프로2($82), 오픈닷2($100), 오픈핏 프로($176) 등"
    opts1 = extract_structured_multi_options(raw_title=title1, content_html="", default_currency="USD")
    assert len(opts1) == 4
    assert opts1[0]["name"] == "빅미 B6 전자책"
    assert opts1[0]["price"] == 10500
    assert opts1[1]["name"] == "샥즈 오픈런 프로2"
    assert opts1[1]["price"] == 8200
    assert opts1[2]["name"] == "오픈닷2"
    assert opts1[2]["price"] == 10000
    assert opts1[3]["name"] == "오픈핏 프로"
    assert opts1[3]["price"] == 17600

    title2 = "샤오신프로13($237.68/무료) 16인치 165Hz 모니터($59.26/무료) GMKtec M6 Ultra 베어본($149.16/무료)"
    opts2 = extract_structured_multi_options(raw_title=title2, content_html="", default_currency="USD")
    assert len(opts2) == 3
    assert opts2[0]["name"] == "샤오신프로13"
    assert opts2[0]["price"] == 23768
    assert opts2[1]["name"] == "16인치 165Hz 모니터"
    assert opts2[1]["price"] == 5926
    assert opts2[2]["name"] == "GMKtec M6 Ultra 베어본"
    assert opts2[2]["price"] == 14916

    title3 = "알리 잔여코드) Etenwolf V3 압축기($22.8) 버니플라이 알루미늄 캐리어 ($48) V500 Ultra 무선청소기($82)"
    opts3 = extract_structured_multi_options(raw_title=title3, content_html="", default_currency="USD")
    assert len(opts3) == 3
    assert opts3[0]["name"] == "Etenwolf V3 압축기"
    assert opts3[0]["price"] == 2280


def test_comma_and_mixed_slash_multi_options_and_noise_guard():
    """10. 쉼표(,) 및 혼합(,/슬래시) 다중 상품 파싱과 결제조건(삼카시) 가짜 옵션 차단 검증"""
    from backend.services.aggregator_service import _clean_option_display_name, _is_valid_option_name

    title_mixed = "[샤오신] 프로 12.7 $164.25, 2025 $88/버니플라이 캐리어 $48.9"
    opts_m = extract_structured_multi_options(raw_title=title_mixed, content_html="", default_currency="USD")
    assert len(opts_m) == 3
    assert opts_m[0]["name"] == "샤오신 패드 프로 12.7"
    assert opts_m[0]["price"] == 16425
    assert opts_m[1]["name"] == "샤오신 패드 2025"
    assert opts_m[1]["price"] == 8800
    assert opts_m[2]["name"] == "버니플라이 캐리어"
    assert opts_m[2]["price"] == 4890

    title_comma = "[맥도날드앱] 상스치 3900원, 오레오 맥플러리 1800원 외"
    opts_c = extract_structured_multi_options(raw_title=title_comma, content_html="", default_currency="KRW")
    assert len(opts_c) == 2
    assert opts_c[0]["name"] == "상스치"
    assert opts_c[0]["price"] == 3900
    assert opts_c[1]["name"] == "오레오 맥플러리"
    assert opts_c[1]["price"] == 1800

    assert _is_valid_option_name("삼카시") is False
    assert _is_valid_option_name("신카시") is False
    assert _is_valid_option_name("카할시") is False
    assert _is_valid_option_name("$70.83 -$1.26 코인") is False
    assert _is_valid_option_name("$70.83 -$14 롯데카드") is False
    assert _is_valid_option_name("ID당 10개, 유효기간 27년 2월 16일까지") is False
    assert _clean_option_display_name("2. 한입 닭다리살 30팩 (옵션") == "한입 닭다리살 30팩"
    assert _clean_option_display_name("광군제특가] 로지텍 지슈라2\xa0") == "로지텍 지슈라2"
    assert _clean_option_display_name("15.6인치 휴대용 모니터") == "15.6인치 휴대용 모니터"
    assert _clean_option_display_name("48% / (버거킹) 불고기와퍼주니어+콜라R") == "불고기와퍼주니어+콜라R"


def test_decimal_inch_preservation_and_price_step_hijack_guard():
    """11. 소수점 인치(15.6인치) 보존 및 본문 내 1) $70.83 가격 계산 단계가 번호형 옵션으로 오탐되는 현상 방어 검증"""
    title_inch = "15.6인치 휴대용 모니터($35.36/무료) 레노버 샤오신 11($88.01/무료) 버니플라이 20 알루미늄캐리어($56.19/무료)"
    opts_inch = extract_structured_multi_options(raw_title=title_inch, content_html="", default_currency="USD")
    assert len(opts_inch) == 3
    assert opts_inch[0]["name"] == "15.6인치 휴대용 모니터"
    assert opts_inch[0]["price"] == 3536

    body_18491 = """
레노버 샤오신 패드 프로 13인치 2026 (링크: https://s.click.aliexpress.com/e/_c2Raj5cX)
$288.68
-$28 KBTY28
=$237.68
Anmite 16인치 휴대용 터치 모니터 (링크: https://s.click.aliexpress.com/e/_c4eQyExv)
옵션 : 터치
1) $70.83
-$1.26 코인
=$61.57
2) $70.83
-$14 롯데카드
=$56.83
GMKtec M6 Ultra 게이밍 미니 PC (링크: https://s.click.aliexpress.com/e/_c4rUUoKb)
$191.42
-$19 KBTY19
=$149.16
"""
    opts_18491 = extract_structured_multi_options(
        raw_title="샤오신프로13($237.68/무료) 16인치 165Hz 모니터($59.26/무료) GMKtec M6 Ultra 베어본($149.16/무료)",
        content_html=body_18491,
        default_currency="USD",
    )
    assert len(opts_18491) == 3
    assert all(not o["name"].startswith("$") for o in opts_18491)
    assert "레노버 샤오신 패드 프로 13인치 2026" in opts_18491[0]["name"]


def test_post_url_image_layout_and_commentary_guard():
    """12. URL 후행 이미지 레이아웃(Image-After-URL) 및 작성자 코멘트('RGB수냉도 기본이라...', '요즘 시세에 125언더도 쉽지 않음') 오탐 차단 검증 (#19178 사례)"""
    body_19178 = """
1. https://item.gmarket.co.kr/Item?goodscode=4611131400 (링크: https://item.gmarket.co.kr/Item?goodscode=4611131400)
1,299,900원
14400f 5060 qlc 디램리스
[상품이미지: https://image.fmkorea.com/files/attach/new5/20261110/pc1.png]
이 구성은 5060 완본체 입문용으로 딱 무난함
요즘 시세에 125언더도 쉽지 않음
2. https://item.gmarket.co.kr/Item?goodscode=4507120463 (링크: https://item.gmarket.co.kr/Item?goodscode=4507120463)
1,348,890원
7500f 5060 tlc 디램리스
[상품이미지: https://image.fmkorea.com/files/attach/new5/20261110/pc2.png]
1번에서 cpu랑 ssd 체급 올린 버전이라고 보면 됨
7500F에 TLC 조합을 이 가격대에서 찾기 힘듦
3. https://item.gmarket.co.kr/Item?goodscode=3432110761 (링크: https://item.gmarket.co.kr/Item?goodscode=3432110761)
2,799,000원
9800x3d 9070xt b850m 박격포 상급반본체
[상품이미지: https://image.fmkorea.com/files/attach/new5/20261110/pc3.png]
9800X3D에 9070XT면 현시점 게이밍 끝판왕 조합이라고 봐도 무방
RGB수냉도 기본이라 세팅 끝판왕 찾는 사람한테 맞음
[상품이미지: https://image.fmkorea.com/files/attach/new5/20261110/pc3_extra.png]
"""
    opts = extract_structured_multi_options(
        raw_title="프리플로우 5060 R5 7500F / 9800X3D 9070XT 완본체 PC 3종 (1,247,570원 / 무배)",
        content_html=body_19178,
        default_currency="KRW",
    )
    assert len(opts) == 3, f"Expected 3 options, got {len(opts)}: {opts}"
    assert opts[0]["name"] == "14400f 5060 qlc 디램리스"
    assert opts[0]["price"] == 1299900
    assert opts[0]["image_url"].endswith("pc1.png")
    assert opts[1]["name"] == "7500f 5060 tlc 디램리스"
    assert opts[1]["price"] == 1348890
    assert opts[1]["image_url"].endswith("pc2.png")
    assert opts[2]["name"] == "9800x3d 9070xt b850m 박격포 상급반본체"
    assert opts[2]["price"] == 2799000
    assert opts[2]["image_url"].endswith("pc3.png")


def test_payment_discount_amount_and_unit_price_stripping():
    """13. 결제수단 할인 차감액('<카카오/토스페이>', '$5.8 토스페이') 및 단위당 가격('1kg당 -> 9,185원') 오탐 차단 + '●' 불릿 Top-Link 검증"""
    body_18283 = """
드리미 V10s 무선 진공 청소기
(링크: https://s.click.aliexpress.com/e/_c36b4o19)
앱기본가 : 166,440원
- 19,500원 <드리미 V10s 전용 쿠폰 : DMKR10>
- 7,000원 <초이스데이 쿠폰 : CDKR05>
- 8,000원 <카카오/토스페이>
= 체감가 131,940원
드리미 L10s 히트 올인원 로봇청소기
(링크: https://s.click.aliexpress.com/e/_c4L47Rwf)
앱기본가 : 422,000원
- 42,000원 <초이스데이 쿠폰 : CDKR30>
- 16,000원 <카카오/토스페이>
= 체감가 364,000원
"""
    opts_18283 = extract_structured_multi_options(
        raw_title="[알리] 드리미 V10s 무선 진공 청소기 외 3건 (131,940원~/무료)",
        content_html=body_18283,
        default_currency="KRW",
    )
    assert len(opts_18283) == 2
    assert opts_18283[0]["price"] == 131940
    assert opts_18283[1]["price"] == 364000

    body_18825 = """
●당일작업 신선한 냉장 한돈 목살 구이용 1kg
(링크: http://item.gmarket.co.kr/Item?goodscode=2087777067)
소상공인 20% 적용시
최종가 -> 18,370원
1kg당 -> 9,185원
●당일작업 한돈 오겹살 구이용 1kg
(링크: http://item.gmarket.co.kr/Item?goodscode=2161467647)
소상공인 20% 적용시
최종가 -> 18,160원
"""
    opts_18825 = extract_structured_multi_options(
        raw_title="[G마켓] 당일작업 한돈 삼겹살 구이용 1kg (18,870원 / 무배)",
        content_html=body_18825,
        default_currency="KRW",
    )
    assert len(opts_18825) == 2
    assert "목살" in opts_18825[0]["name"]
    assert opts_18825[0]["price"] == 18370
    assert "오겹살" in opts_18825[1]["name"]
    assert opts_18825[1]["price"] == 18160


def test_top_link_spec_variants_and_size_hints():
    """14. Top-Link 직후 규격/옵션 보조줄('42인치, 조명없음', '(싱글매트...)')이 블록을 조기 종료시키지 않고 옵션명에 반영되는지 검증"""
    body_19087 = """
[상품이미지: https://cdn2.ppomppu.co.kr/zboard/data3/2026/1106/f1.jpg]
SOVE 실링팬 조명 천장 선풍기 바로가기>>> (링크: https://s.click.aliexpress.com/e/_c440uNIn)
42인치, 조명없음
판매가 : $65.40
할인코드 -$7 : CDKR07, ALIBOK07
결제할인 -$7 : KB/토스
할인가 : $51.40
[상품이미지: https://cdn2.ppomppu.co.kr/zboard/data3/2026/1106/f2.jpg]
SOVE 천장 선풍기 바로가기>>> (링크: https://s.click.aliexpress.com/e/_c3t7lQnd)
44인치, 화이트컬러, 조명있음
판매가 : $77.53
할인코드 -$10 : CDKR10
결제할인 -$7 : KB/토스
할인가 : $60.53
"""
    opts_19087 = extract_structured_multi_options(
        raw_title="[알리] 실링팬 모음",
        content_html=body_19087,
        default_currency="USD",
    )
    assert len(opts_19087) == 2
    assert "42인치" in opts_19087[0]["name"]
    assert opts_19087[0]["price"] == 5140
    assert opts_19087[0]["image_url"].endswith("f1.jpg")
    assert "44인치" in opts_19087[1]["name"]
    assert opts_19087[1]["price"] == 6053
    assert opts_19087[1]["image_url"].endswith("f2.jpg")





