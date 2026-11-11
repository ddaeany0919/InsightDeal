import base64
import json
from datetime import datetime, timedelta

import pytest
from bs4 import BeautifulSoup
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from backend.core.deal_normalizer import (
    clean_canonical_title,
    extract_body_fallback_price,
    has_currency_aware_price_conflict,
    normalize_shipping_fee,
    parse_price_string,
    parse_title_price_and_shipping,
    renormalize_existing_deals,
    resolve_canonical_shop_name,
    sanitize_ingested_title,
    to_krw_equivalent,
    unwrap_redirect_target_url,
)
from backend.core.url_utils import extract_canonical_product_key
from backend.database import models
from backend.database.models import Base, Community, Deal, PriceHistory
from backend.database.session import get_db_session
from backend.main import app
from backend.routers.community import invalidate_deals_cache
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.clien_scraper import ClienScraper
from backend.scrapers.fmkorea_scraper import FmkoreaScraper
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.scrapers.ruliweb_scraper import RuliwebScraper
from backend.services.aggregator_service import AggregatorService


@pytest.fixture
def mem_db():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=engine)
    TestingSession = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    session = TestingSession()
    try:
        session.add_all(
            [
                Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr"),
                Community(id=2, name="fmkorea", display_name="펨코", base_url="https://www.fmkorea.com"),
                Community(id=3, name="quasarzone", display_name="퀘이사존", base_url="https://quasarzone.com"),
                Community(id=4, name="ruliweb", display_name="루리웹", base_url="https://bbs.ruliweb.com"),
            ]
        )
        session.commit()
        invalidate_deals_cache()
        yield session
    finally:
        invalidate_deals_cache()
        session.close()


# ==============================================================================
# 1. 60+ 실전 한국 핫딜 Golden Corpus 회귀 테스트
# ==============================================================================
GOLDEN_TITLE_CORPUS = [
    # (1~6) 날짜+시간 괄호 선제 마스킹 (월/일 숫자가 가격으로 오인되지 않아야 함)
    ("(10/13 오전10:30) 네이버 쇼핑라이브 특가", 0, "KRW", "", False),
    ("(10/13 오전10:30) [네이버] 오설록 티세트 (19,900원/무배)", 19900, "KRW", "무료배송", False),
    ("[11/11 00시] 알리 광군제 무선 이어폰 ($24.90/무료)", 2490, "USD", "무료배송", False),
    ("(11/2~11/30) 삼성 세일 페스타 로봇청소기 (890,000원/무배)", 890000, "KRW", "무료배송", False),
    ("(12.25 오후 8시) 크리스마스 케이크 사전예약 (23,000원/무료배송)", 23000, "KRW", "무료배송", False),
    ("(5월 8일 밤11시) 어버이날 카네이션 세트", 0, "KRW", "", False),

    # (7~16) 스펙/연도/모델명 대괄호·소괄호 배제 및 우측 가격 괄호 우선 선택
    ("[2026 신형] LG 그램 프로 16 노트북 (1,490,000원/무배)", 1490000, "KRW", "무료배송", False),
    ("[2026년형] 삼성 비스포크 AI 무풍에어컨", 0, "KRW", "", False),
    ("[지마켓] 이엠텍 지포스 [5070 Ti] 미라클 (1,290,000원/무배)", 1290000, "KRW", "무료배송", False),
    ("[퀘이사존] MSI 지포스 RTX [5080] 슈프림 사전안내", 0, "KRW", "", False),
    ("[컴퓨존] AMD 라이젠 [9800X3D] 정품 멀티팩 (699,000원/2,500원)", 699000, "KRW", "2,500원", False),
    ("[알리] 제우스랩 포터블 모니터 (15.6인치) ($69.99/무배)", 6999, "USD", "무료배송", False),
    ("[쿠팡] 제우스랩 휴대용 모니터 (15.6인치)", 0, "KRW", "", False),
    ("[쿠팡] 오디오테크니카 유선 이어폰 (3.5mm) (14,900원/와우무료)", 14900, "KRW", "와우무료", False),
    ("[11번가] C타입 변환 젠더 케이블 (3.5mm)", 0, "KRW", "", False),
    ("[네이버] 종근당 락토핏 골드 유산균 (180일분) (28,900원/네멤무료)", 28900, "KRW", "네멤무료", False),

    # (17~21) 닫는 괄호 누락 후행 괄호 파싱
    ("[지마켓] CJ 비비고 왕교자 만두 세트 (16,680원/무료", 16680, "KRW", "무료배송", False),
    ("[쿠팡] 제주 삼다수 2L 12병 (10,900원/와우무배", 10900, "KRW", "와우무료", False),
    ("[알리] 유그린 100W GaN 충전기 ($ 17.97/무배", 1797, "USD", "무료배송", False),
    ("[네이버] 해남 꿀고구마 5kg 특상 (13,500원/무배", 13500, "KRW", "무료배송", False),
    ("[옥션] 동원참치 라이트스탠다드 150g 10캔 (18,900/무료", 18900, "KRW", "무료배송", False),

    # (22~31) 다중 통화(USD, EUR, CNY, JPY) 및 기호 뒤 공백 허용 / '불' 오탐 차단
    ("[알리] 앤커 C타입 140W 충전기 ($ 17.97 / 무료)", 1797, "USD", "무료배송", False),
    ("[타오바오] 독거미 키보드 F99 프로 (¥189/무배)", 18900, "CNY", "무료배송", False),
    ("[타오바오] 샤오미 미지아 무선 선풍기 189위안 무배", 18900, "CNY", "무료배송", False),
    ("[타오바오] VXE 잠자리 마우스 R1 Pro Max (￥ 159.5 / 무료)", 15950, "CNY", "무료배송", False),
    ("[아마존재팬] 소니 WH-1000XM5 정품 이어패드 (3,980엔/무료)", 3980, "JPY", "무료배송", False),
    ("[아마존재팬] 닌텐도 프로콘 스플래툰 에디션 7,480엔", 7480, "JPY", "", False),
    ("[아마존독일] 브라운 시리즈9 프로 면도기 (€149.50/무배)", 14950, "EUR", "무료배송", False),
    ("[아마존] 커세어 DDR5 32GB 6000MHz 램 (89.99달러/무배)", 8999, "USD", "무료배송", False),
    ("[뽐뿌] 해외직구 WD SN850X 2TB NVMe (119불/무배)", 11900, "USD", "무료배송", False),
    ("[퀘이사존] 알파스캔 Q27G4 게이밍 모니터 불량화소 무결점 (259,000원/무배)", 259000, "KRW", "무료배송", False),
    ("[옥션] 다이슨 리퍼비시 청소기 단순변심 환불불가 (189,000원/3,000원)", 189000, "KRW", "3,000원", False),

    # (33~37) 알리/큐텐/직구 원화 저가 딜 보존 (과거 KRW<=10000 -> USD*100 독소 휴리스틱 영구 차단)
    ("[알리] 천원마트 다용도 케이블 정리함 (2,500원/무배)", 2500, "KRW", "무료배송", False),
    ("[알익] 코인딜 실리콘 맥세이프 케이스 (1,900원/무료)", 1900, "KRW", "무료배송", False),
    ("[큐텐] 직구 특가 일본 카레 블럭 4개입 (8,900원/무배)", 8900, "KRW", "무료배송", False),
    ("[알리익스프레스] 한국발송 아기 물티슈 10팩 (9,900원/무료배송)", 9900, "KRW", "무료배송", False),
    ("[알리] 코인샵 초이스 USB C타입 케이블 (1,500원/무배)", 1500, "KRW", "무료배송", False),

    # (38~43) 한국식 '만' / '만 천원' / '천원' 표기 및 보조배터리 mAh 보호
    ("[하이마트] 삼성 갤럭시북4 프로 16인치 139만원 무배", 1390000, "KRW", "무료배송", False),
    ("[롯데온] 나이키 에어맥스 운동화 5.9만 무료배송", 59000, "KRW", "무료배송", False),
    ("[네이버] 횡성 한우 1등급 등심 세트 3만9천원 무료배송", 39000, "KRW", "무료배송", False),
    ("[11번가] 샤오미 보조배터리 2만mAh 고속충전 (19,900원/무배)", 19900, "KRW", "무료배송", False),
    ("[쿠팡] 아트뮤 보조배터리 1만mAh PD충전 (15,900원/와우무배)", 15900, "KRW", "와우무료", False),
    ("[지마켓] 맥심 모카골드 커피믹스 5천원 할인쿠폰 적용가 (24,500원/무배)", 24500, "KRW", "무료배송", False),

    # (44~47) 단위당 가격(100g당 / 개당 / ml당) 선제 마스킹
    ("[쿠팡] 국내산 한돈 삼겹살 1kg 100g당 1,490원 (14,900원/와우무료)", 14900, "KRW", "와우무료", False),
    ("[지마켓] 코카콜라 제로 355ml 24캔 개당 750원 (18,000원/무배)", 18000, "KRW", "무료배송", False),
    ("[11번가] 다우니 섬유유연제 1L 3개 10ml당 39원 (11,700원/무배)", 11700, "KRW", "무료배송", False),
    ("[네이버] 크리넥스 3겹 데코앤소프트 30롤 롤당 590원 (17,700원/무배)", 17700, "KRW", "무료배송", False),

    # (48~53) 모음전 더미 가격(777, 999, 111, 100, 1234, 9999) 원천 차단 vs 실제 저가 상품 보존
    ("[지마켓] 겨울 니트/가디건 의류 특가 모음전 (777원/무배)", 0, "KRW", "무료배송", False),
    ("[옥션] 생필품 골라담기 다양한 구성 (999원/무료)", 0, "KRW", "무료배송", False),
    ("[11번가] CJ제일제당 브랜드 기획전 (111원/무배)", 0, "KRW", "무료배송", False),
    ("[알리] 광군제 할인코드 총정리 이벤트 (100원/무료)", 0, "KRW", "무료배송", False),
    ("[롯데온] 키즈 겨울 패딩 모음전 (9,999원/무배)", 0, "KRW", "무료배송", False),
    ("[쿠팡] 하리보 골드베렌 미니 젤리 단품 (990원/와우무배)", 990, "KRW", "와우무료", False),

    # (54~58) 네이버페이/폐지/라방 소액 적립 딜(5~90원) 보존
    ("[네이버페이] 클릭 적립 20원", 20, "KRW", "", False),
    ("[네페] 라이브 방송 폐지 줍기 15원 적립", 15, "KRW", "", False),
    ("[네이버페이] 출석체크 포인트 10원", 10, "KRW", "", False),
    ("[네이버] 라방 클릭 포인트 5원", 5, "KRW", "", False),
    ("[네이버페이] 퀴즈 정답 맞추고 50원 적립", 50, "KRW", "", False),

    # (59~62) 무료 배포 / 시드 오염 플레이스홀더 정제 후 파싱
    ("[에픽게임즈] 문명6 플래티넘 에디션 무료배포 (0원)", 0, "KRW", "", True),
    ("[스팀] 주말 한정 100% 할인 무료 배포", 0, "KRW", "", True),
    ("[지마켓] 로지텍 G304 마우스 1,000단위 구분 쉼표 (39,000원/무배)", 39000, "KRW", "무료배송", False),
    ("[쿠팡] 맥세이프 충전기 (에어 미니 프로 35w 택배배송) (24,900원/와우무료)", 24900, "KRW", "와우무료", False),
]


@pytest.mark.parametrize(
    "raw_title,expected_price,expected_currency,expected_ship,expected_free",
    GOLDEN_TITLE_CORPUS,
)
def test_golden_corpus_title_parsing(
    raw_title, expected_price, expected_currency, expected_ship, expected_free
):
    price, curr, ship, is_free = parse_title_price_and_shipping(raw_title)
    assert price == expected_price, f"Failed price for: {raw_title}"
    assert curr == expected_currency, f"Failed currency for: {raw_title}"
    assert ship == expected_ship, f"Failed shipping for: {raw_title}"
    assert is_free is expected_free, f"Failed free flag for: {raw_title}"


# ==============================================================================
# 2. 제목 정제(clean_canonical_title / sanitize_ingested_title) 및 배송비·쇼핑몰 표준화
# ==============================================================================
def test_title_cleaning_preserves_regions_specs_and_safe_backslashes():
    assert clean_canonical_title("[지마켓] 로지텍 마우스 1,000단위 구분 쉼표 (39,000원/무배)") == "[지마켓] 로지텍 마우스"
    assert clean_canonical_title("[쿠팡] 맥세이프 충전기 (에어 미니 프로 35w 택배배송) (24,900원/무배)") == "[쿠팡] 맥세이프 충전기"
    assert clean_canonical_title("[지마켓] CJ 비비고 왕교자 만두 세트 (16,680원/무료") == "[지마켓] CJ 비비고 왕교자 만두 세트"
    assert clean_canonical_title("[트레이더스] 발베니 12년 위스키 오프라인 행사 (수원) (89,800원)") == "[트레이더스] 발베니 12년 위스키 오프라인 행사 (수원)"
    assert clean_canonical_title("[롯데마트] 동해안 활꽃게 행사 (강원) (19,900원/무배)") == "[롯데마트] 동해안 활꽃게 행사 (강원)"
    assert clean_canonical_title("[알리] 제우스랩 포터블 모니터 (15.6인치) ($69.99/무배)") == "[알리] 제우스랩 포터블 모니터 (15.6인치)"
    assert clean_canonical_title(r"[퀘이사존] C:\Windows 드라이버 호환 USB 어댑터 \ 15,000") == r"[퀘이사존] C:\Windows 드라이버 호환 USB 어댑터"
    assert clean_canonical_title("잔여공구코드] 알리 미니PC &amp; 독 세트 ($98.50/무배)") == "[잔여공구코드] 알리 미니PC & 독 세트"

    # DB 저장용 sanitize_ingested_title은 오염 플레이스홀더만 제거하고 정상 가격 괄호는 보존
    assert sanitize_ingested_title("[11번가] LG 27인치 게이밍 모니터 27GP850 (289,000원/무배)") == "[11번가] LG 27인치 게이밍 모니터 27GP850 (289,000원/무배)"
    assert sanitize_ingested_title("[지마켓] 로지텍 마우스 1,000단위 구분 쉼표 (39,000원/무배)") == "[지마켓] 로지텍 마우스 (39,000원/무배)"


@pytest.mark.parametrize(
    "raw_fee,title_hint,expected",
    [
        ("쿠팡와우0무료", "", "와우무료"),
        ("와우무배", "", "와우무료"),
        ("멤버십0무료", "", "네멤무료"),
        ("네이버멤버십 무료", "", "네멤무료"),
        ("3만원이상무료배송", "", "조건부무료"),
        ("조건부 무료배송", "", "조건부무료"),
        ("무배", "", "무료배송"),
        ("0원", "", "무료배송"),
        ("2500", "", "2,500원"),
        ("배송비 3,000원", "", "3,000원"),
        ("$3.50", "", "$3.50"),
        ("정보 없음", "", ""),
        ("-", "", ""),
        ("", "[지마켓] 비비고 왕교자 (16,680원/무료", "무료배송"),
    ],
)
def test_shipping_fee_normalization(raw_fee, title_hint, expected):
    assert normalize_shipping_fee(raw_fee, title_hint=title_hint) == expected


@pytest.mark.parametrize(
    "raw_title,ecom_url,current_shop,expected_shop",
    [
        ("[G마켓] 비비고 왕교자", "", "G마켓", "지마켓"),
        ("[알익] 유그린 충전기", "", "", "알리익스프레스"),
        ("[스마트스토어] 한우 세트", "", "", "네이버"),
        ("[네쇼라] 설화수 세트", "", "", "네이버"),
        ("[SSG] 신세계 푸드", "", "SSG", "SSG닷컴"),
        ("[특가] RTX 5070 Ti 그래픽카드", "https://www.compuzone.co.kr/product/product_detail.htm?ProductNo=112233", "기타", "컴퓨존"),
        ("[재입고] 조텍 그래픽카드", "https://www.tagtag.co.kr/product/detail.html?product_no=991", "", "탁탁몰"),
        ("[역대가] 독거미 키보드", "https://item.taobao.com/item.htm?id=77889900", "기타", "타오바오"),
        ("[광군제 공구] 스팀덱 OLED", "https://store.steampowered.com/app/1675200/", "", "스팀"),
        ("[핫딜] 문명6 무료", "https://store.epicgames.com/ko/p/sid-meiers-civilization-vi", "[핫딜]", "에픽게임즈"),
        ("[브랜드데이] 어센틱 로고 후드", "https://covernat.net/product/detail.html?product_no=5544", "", "커버낫"),
        (
            "[잔여공구코드] 무선 청소기",
            "https://newtip.net/click.php?m=11st&a=A100&l=9999&tu=https%3A%2F%2Fwww.11st.co.kr%2Fproducts%2F6543210",
            "잔여공구코드",
            "11번가",
        ),
        ("[기타] 출처 미상 게시글", "", "기타", ""),
    ],
)
def test_shop_canonicalization_and_domain_inference(raw_title, ecom_url, current_shop, expected_shop):
    resolved = resolve_canonical_shop_name(
        raw_title=raw_title,
        ecommerce_url=ecom_url,
        current_shop=current_shop,
        default_empty="",
    )
    assert resolved == expected_shop


def test_redirect_unwrap_and_canonical_product_keys():
    raw_coupang = "https://link.coupang.com/re/AFFSDP?lptag=AF123&pageKey=789123&itemId=456789&vendorItemId=999"
    assert extract_canonical_product_key(raw_coupang) == "coupang_789123_456789"

    b64_target = base64.b64encode(
        b"https://smartstore.naver.com/main/products/1029384756"
    ).decode("ascii")
    ppomppu_redir = f"https://s.ppomppu.co.kr/?idno=ppomppu_123&target={b64_target}&encode=on"
    assert unwrap_redirect_target_url(ppomppu_redir) == "https://smartstore.naver.com/main/products/1029384756"
    assert extract_canonical_product_key(ppomppu_redir) == "naver_1029384756"

    lp_wrapped = (
        "https://click.linkprice.com/click.php?m=gmarket&a=A100702333"
        "&tu=https%3A%2F%2Fitem.gmarket.co.kr%2FItem%3Fgoodscode%3D4701840459"
    )
    assert extract_canonical_product_key(lp_wrapped) == "gmarket_4701840459"


# ==============================================================================
# 3. 스크래퍼 계층 단위 검증 (펨코 무배 증발, 퀘이사존 외화/불 오탐, 뽐뿌/루리웹/클리앙 본문 마스킹)
# ==============================================================================
def test_body_fallback_masks_contextual_surcharges_and_original_prices():
    body = (
        "기존가: 32,000원 / 정가 28,000원 / 양념치킨 소스 추가: 200원 (+500원) / "
        "배송비: 3,000원 / 쿠폰 할인 4,000원 / 실결제 특가 16,900원입니다."
    )
    price, curr = extract_body_fallback_price(body, "[배민] 후라이드 치킨 세트 특가")
    assert price == 16900
    assert curr == "KRW"


@pytest.mark.asyncio
async def test_fmkorea_scraper_preserves_price_with_free_shipping_label(monkeypatch):
    scraper = FmkoreaScraper(2)
    html_doc = """
    <html><body>
      <table>
        <tr><th>쇼핑몰</th><td>네이버스토어</td></tr>
        <tr><th>가격</th><td>12,900원 (무배)</td></tr>
        <tr><th>배송</th><td>무료</td></tr>
      </table>
      <div class="xe_content"><a href="https://smartstore.naver.com/shop/products/111">구매링크</a> 본문 설명</div>
    </body></html>
    """
    monkeypatch.setattr(scraper, "fetch_html", lambda url: _async_return(html_doc))
    monkeypatch.setattr(scraper, "fetch_og_image", lambda url, title="": _async_return(""))
    detail = await scraper.get_detail("https://www.fmkorea.com/12345", "[네이버] 테스트 딜")
    assert detail["price"] == 12900
    assert detail["currency"] == "KRW"


@pytest.mark.asyncio
async def test_quasarzone_scraper_foreign_currencies_and_bul_guard(monkeypatch):
    scraper = QuasarzoneScraper(3)
    list_html = """
    <div class="market-info-type-list">
      <table><tbody>
        <tr>
          <td><a class="subject-link" href="/bbs/qb_saleinfo/views/101">독거미 키보드 직구</a></td>
          <td><span class="text-orange">¥ 189</span></td>
        </tr>
      </tbody></table>
    </div>
    """
    deals = await scraper.parse_list(list_html)
    assert len(deals) == 1
    assert deals[0]["price"] == 18900
    assert deals[0]["currency"] == "CNY"

    # '불량화소'에 포함된 '불' 때문에 원화가 USD로 둔갑하지 않는지 확인
    p_krw, c_krw = parse_price_string("259,000원 (불량화소 무결점)", allow_bare_number=True)
    assert (p_krw, c_krw) == (259000, "KRW")
    p_usd, c_usd = parse_price_string("25.5불", allow_bare_number=True)
    assert (p_usd, c_usd) == (2550, "USD")


@pytest.mark.asyncio
async def test_ppomppu_ruliweb_clien_scrapers_no_usd_explosion_and_context_mask(monkeypatch):
    ppomppu_ali = AlippomppuScraper(8)
    ali_list_html = """
    <table>
      <tr class="baseList">
        <td><a class="baseList-title" href="view.php?id=ppomppu8&no=777">[알리] 천원마트 케이블 홀더 (2,500원/무배)</a></td>
      </tr>
    </table>
    """
    monkeypatch.setattr(ppomppu_ali, "get_detail", lambda url, full_title="": _async_return({}))
    parsed_ali_list = await ppomppu_ali.parse_list(ali_list_html)
    assert len(parsed_ali_list) == 1
    assert parsed_ali_list[0]["price"] == 2500
    assert parsed_ali_list[0]["currency"] == "KRW"

    ppomppu_dom = PpomppuScraper(1)
    ppomppu_html = """
    <html><body>
      <td class="board-contents">정가: 45,000원 / 추가금: 300원 / 최종가 $19.99 배송비 무배</td>
    </body></html>
    """
    monkeypatch.setattr(ppomppu_dom, "fetch_html", lambda *args, **kwargs: _async_return(ppomppu_html))
    monkeypatch.setattr(ppomppu_dom, "fetch_og_image", lambda *args, **kwargs: _async_return(""))
    p_det = await ppomppu_dom.get_detail("https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=888", "[아마존] 직구 특가")
    assert p_det["price"] == 1999
    assert p_det["currency"] == "USD"
    assert p_det["shipping_fee"] == "무료배송"

    ruli = RuliwebScraper(4)
    ruli_html = """
    <html><body>
      <div class="view_content">정상가: 29,000원\n치킨무 추가: 500원\n실구매가 17,900원 무료배송</div>
    </body></html>
    """
    monkeypatch.setattr(ruli, "fetch_html", lambda *args, **kwargs: _async_return(ruli_html))
    monkeypatch.setattr(ruli, "fetch_og_image", lambda *args, **kwargs: _async_return(""))
    r_det = await ruli.get_detail("https://bbs.ruliweb.com/market/board/1020/read/1", "[쿠팡잇츠] 치킨 특가")
    assert r_det["price"] == 17900
    assert r_det["currency"] == "KRW"

    clien = ClienScraper(5)
    clien_html = """
    <html><body>
      <div class="post_article">출시가: 59,000원 / 택배비: 3,000원 / 특가 29,900원</div>
    </body></html>
    """
    monkeypatch.setattr(clien, "fetch_html", lambda *args, **kwargs: _async_return(clien_html))
    monkeypatch.setattr(clien, "fetch_og_image", lambda *args, **kwargs: _async_return(""))
    c_det = await clien.get_detail("https://www.clien.net/service/board/jirum/1", "[지마켓] 무선 마우스")
    assert c_det["price"] == 29900
    assert c_det["currency"] == "KRW"


def _async_return(val):
    async def _coro():
        return val
    return _coro()


# ==============================================================================
# 4. Aggregator + API 서빙 + 통화 인지 DSU 클러스터링 + PriceHistory 반등 곡선 검증
# ==============================================================================
@pytest.mark.asyncio
async def test_aggregator_preserves_micro_points_blocks_dummy_and_keeps_discount_code_deals(mem_db):
    agg = AggregatorService(mem_db)

    d_point = await agg.process_scraped_deal(
        1,
        {
            "title": "[네이버페이] 클릭 적립 20원",
            "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1001",
            "price": 20,
            "currency": "KRW",
            "content_html": "<p>네페 20원 적립 링크</p>",
        },
    )
    assert d_point.price == "20"
    assert d_point.currency == "KRW"

    d_dummy = await agg.process_scraped_deal(
        1,
        {
            "title": "[G마켓] 겨울 니트 의류 특가 모음전 (777원/무배)",
            "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1002",
            "price": 777,
            "currency": "KRW",
            "ecommerce_link": "https://www.gmarket.co.kr/n/plan?plan_id=1",
            "content_html": "<p>다양한 니트 모음전</p>",
        },
    )
    assert d_dummy.price == "0"
    assert d_dummy.shop_name == "지마켓"
    assert d_dummy.shipping_fee == "무료배송"

    d_code_product = await agg.process_scraped_deal(
        1,
        {
            "title": "[알리] 파이어배트 미니PC N100 할인코드 적용 특가",
            "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&no=1003",
            "price": 9850,
            "currency": "USD",
            "ecommerce_link": "https://ko.aliexpress.com/item/100500998877.html",
            "content_html": "<p>할인코드 KR10 입력 시 최종 $98.50 무료배송</p>",
        },
    )
    assert d_code_product.price == "9850"
    assert d_code_product.currency == "USD"
    assert d_code_product.shop_name == "알리익스프레스"


def test_api_serving_currency_aware_dsu_sorting_shop_options_and_rebound_history(mem_db):
    now = datetime.utcnow()
    # 동일 모델명·동일 숫자 '15000'이지만 통화가 USD($150.00 = 약 21만원) vs KRW(15,000원) -> 절대 병합 금지!
    deal_usd = Deal(
        id=501,
        source_community_id=1,
        title="[아마존] 로지텍 무선 헤드셋 G733 ($150.00/배송비 3,000원)",
        price="15000",
        currency="USD",
        shipping_fee="3,000원",
        post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&no=501",
        ecommerce_link="https://www.amazon.com/dp/B08141501",
        shop_name="[특가]",
        honey_score=100,
        indexed_at=now - timedelta(hours=2),
    )
    deal_krw = Deal(
        id=502,
        source_community_id=2,
        title="[특가] 로지텍 무선 헤드셋 G733 전용 이어패드 (15,000원/쿠팡와우0무료)",
        price="15000",
        currency="KRW",
        shipping_fee="쿠팡와우0무료",
        post_link="https://www.fmkorea.com/502",
        ecommerce_link="https://www.compuzone.co.kr/product/product_detail.htm?ProductNo=502",
        shop_name="기타",
        honey_score=100,
        indexed_at=now - timedelta(hours=1),
    )
    deal_cny_opts = Deal(
        id=503,
        source_community_id=3,
        title="[역대가] 독거미 키보드 F99 모음 (¥189/무배",
        price="18900",
        currency="CNY",
        shipping_fee="",
        post_link="https://quasarzone.com/bbs/qb_saleinfo/views/503",
        ecommerce_link="https://item.taobao.com/item.htm?id=503503",
        shop_name="[역대가]",
        has_options=True,
        options_data=json.dumps(
            [
                {"name": "F99 경해축", "price": 18900, "currency": "CNY", "url": "https://item.taobao.com/item.htm?id=5031"},
                {"name": "F99 황축", "price": 20900, "currency": "CNY", "url": "https://item.taobao.com/item.htm?id=5032"},
            ],
            ensure_ascii=False,
        ),
        honey_score=100,
        indexed_at=now - timedelta(minutes=30),
    )
    # 반등 곡선 테스트용 딜: 20,000원(T-3d) -> 25,000원(T-2d) -> 20,000원(T-1h)
    deal_rebound = Deal(
        id=504,
        source_community_id=1,
        title="[지마켓] 삼성 EVO Plus 256GB 메모리카드 (20,000원/무배)",
        price="20000",
        currency="KRW",
        shipping_fee="무배",
        model_code="MBMC256SA",
        post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=504",
        ecommerce_link="https://item.gmarket.co.kr/Item?goodscode=504504",
        shop_name="G마켓",
        honey_score=90,
        indexed_at=now - timedelta(days=3),
    )
    mem_db.add_all([deal_usd, deal_krw, deal_cny_opts, deal_rebound])
    mem_db.add_all(
        [
            PriceHistory(deal_id=504, price="20000", checked_at=now - timedelta(days=3)),
            PriceHistory(deal_id=504, price="25000", checked_at=now - timedelta(days=2)),
            PriceHistory(deal_id=504, price="20000", checked_at=now - timedelta(hours=1)),
        ]
    )
    mem_db.commit()

    assert has_currency_aware_price_conflict(15000, 15000, "USD", "KRW") is True
    assert to_krw_equivalent(18900, "CNY") == pytest.approx(18900 * 1.95)

    def _override_db():
        yield mem_db

    invalidate_deals_cache()
    app.dependency_overrides[get_db_session] = _override_db
    try:
        client = TestClient(app)

        # 1) /api/community/hot-deals?sort=price_asc 검증
        res_hot = client.get("/api/community/hot-deals?sort=price_asc&limit=20")
        assert res_hot.status_code == 200
        hot_items = res_hot.json()["deals"]
        ids_in_order = [item["id"] for item in hot_items]
        # 502(15,000원) < 504(20,000원) < 503(¥189 ≈ 36,855원) < 501($150.00 ≈ 210,000원)
        assert ids_in_order == [502, 504, 503, 501]

        by_id = {item["id"]: item for item in hot_items}
        # 외화(USD) 가격 15000센트에 원화 배송비 3,000원이 합산되지 않아야 함!
        assert by_id[501]["total_price"] == 15000
        assert by_id[501]["shop_name"] == "아마존"
        assert by_id[501]["options_data"] == []

        assert by_id[502]["shop_name"] == "컴퓨존"
        assert by_id[502]["shipping_fee"] == "와우무료"

        assert by_id[503]["shop_name"] == "타오바오"
        assert by_id[503]["shipping_fee"] == "무료배송"
        assert by_id[503]["title"] == "[역대가] 독거미 키보드 F99 모음"
        assert isinstance(by_id[503]["options_data"], list)
        assert len(by_id[503]["options_data"]) == 2

        # 2) /api/community/top-hot-deals 필드 일관성 검증
        res_top = client.get("/api/community/top-hot-deals?limit=10")
        assert res_top.status_code == 200
        top_by_id = {item["id"]: item for item in res_top.json()["deals"]}
        assert 501 in top_by_id and 502 in top_by_id  # USD/KRW 오병합 없음
        assert top_by_id[503]["shop_name"] == "타오바오"
        assert isinstance(top_by_id[503]["options_data"], list)

        # 3) /api/community/deals/{id} 상세 응답 검증
        res_detail = client.get("/api/community/deals/503")
        assert res_detail.status_code == 200
        det = res_detail.json()
        assert det["shop_name"] == "타오바오"
        assert det["shipping_fee"] == "무료배송"
        assert det["title"] == "[역대가] 독거미 키보드 F99 모음"
        assert isinstance(det["options_data"], list) and len(det["options_data"]) == 2

        # 4) /api/community/deals/504/history 시세 차트 반등 곡선(20,000 -> 25,000 -> 20,000) 검증
        res_hist = client.get("/api/community/deals/504/history?period=7d")
        assert res_hist.status_code == 200
        hist_pts = res_hist.json()
        assert [pt["price"] for pt in hist_pts] == [20000, 25000, 20000]
    finally:
        invalidate_deals_cache()
        app.dependency_overrides.pop(get_db_session, None)


# ==============================================================================
# 5. 운영 DB 오염 레코드 일괄 치유(renormalize_existing_deals) 및 멱등성 검증
# ==============================================================================
def test_renormalize_existing_deals_heals_audit_anomalies_idempotently(mem_db, monkeypatch):
    monkeypatch.setenv("ADMIN_API_KEY", "test-secret-admin-key")
    mem_db.add_all(
        [
            # ID=16 유형: 날짜 괄호 '10'월이 10원으로 오파싱된 레코드
            Deal(
                id=16,
                source_community_id=1,
                title="(10/13 오전10:30) 네이버 쇼핑라이브 한우 특가",
                price="10",
                currency="KRW",
                shipping_fee="정보 없음",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=16",
                ecommerce_link="https://shoppinglive.naver.com/lives/16",
                shop_name="기타",
                content_html="<p>정가: 59,000원 / 라이브 할인가 34,900원</p>",
            ),
            # ID=19198 유형: 타오바오 ¥189 딜이 KRW 189원으로 잘못 적재된 레코드
            Deal(
                id=19198,
                source_community_id=3,
                title="[타오바오] 독거미 키보드 직구 특가",
                price="189",
                currency="KRW",
                shipping_fee="무배",
                post_link="https://quasarzone.com/bbs/qb_saleinfo/views/19198",
                ecommerce_link="https://item.taobao.com/item.htm?id=19198",
                shop_name="[직구]",
                content_html="<p>타오바오 ¥189 무료배송</p>",
            ),
            # ID=19394 유형: 본문 치킨 소스 추가금 200원이 대표가로 잡힌 레코드
            Deal(
                id=19394,
                source_community_id=4,
                title="[배민] 처갓집 슈프림 양념치킨 특가",
                price="200",
                currency="KRW",
                shipping_fee="-",
                post_link="https://bbs.ruliweb.com/market/board/1020/read/19394",
                ecommerce_link="",
                shop_name="기타",
                content_html="<p>소스 추가: 200원 / 쿠폰 적용 실결제가 15,900원</p>",
            ),
            # ID=19533 유형: 모음전 더미 가격 777원 레코드
            Deal(
                id=19533,
                source_community_id=1,
                title="[G마켓] 겨울 아우터 빅세일 모음전 (777원/무배)",
                price="777",
                currency="KRW",
                shipping_fee="0",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=19533",
                ecommerce_link="https://www.gmarket.co.kr/n/plan?id=19533",
                shop_name="G마켓",
            ),
            # ID=19640 유형: 닫는 괄호 누락 '(16,680원/무료' 및 플레이스홀더/시드 꼬리표 오염 레코드
            Deal(
                id=19640,
                source_community_id=1,
                title="[특가] 비비고 만두 1,000단위 구분 쉼표 (에어 미니 35w 택배배송) (16,680원/무료",
                price="0",
                currency="KRW",
                shipping_fee="정보 없음",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=19640",
                ecommerce_link="https://www.tagtag.co.kr/product/detail.html?product_no=19640",
                shop_name="특가",
            ),
            # 과거 독소 휴리스틱 피해 레코드: '[알리] 2,500원' 원화 딜이 USD 250000($2,500.00)으로 저장된 케이스
            Deal(
                id=19700,
                source_community_id=1,
                title="[알익] 천원마트 다용도 파우치 (2,500원/무배)",
                price="250000",
                currency="USD",
                shipping_fee="쿠팡와우0무료",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&no=19700",
                ecommerce_link="https://ko.aliexpress.com/item/19700.html",
                shop_name="알익",
            ),
            # 네페 소액 적립 딜은 절대 0원으로 지워지지 않고 그대로 보존되어야 함
            Deal(
                id=19800,
                source_community_id=1,
                title="[네이버페이] 클릭 적립 15원",
                price="15",
                currency="KRW",
                shipping_fee="",
                post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=19800",
                ecommerce_link="https://campaign2.naver.com/npay/19800",
                shop_name="네이버페이",
            ),
        ]
    )
    mem_db.commit()

    # 1차 dry_run 검증 (DB 미변경 확인)
    dry_stats = renormalize_existing_deals(mem_db, dry_run=True)
    assert dry_stats["updated"] >= 6
    assert mem_db.query(Deal).filter_by(id=19198).first().currency == "KRW"

    # 2차 실제 치유 실행
    stats = renormalize_existing_deals(mem_db, dry_run=False)
    assert stats["updated"] >= 6

    d16 = mem_db.query(Deal).filter_by(id=16).first()
    assert d16.price == "34900" and d16.currency == "KRW" and d16.shop_name == "네이버"

    d19198 = mem_db.query(Deal).filter_by(id=19198).first()
    assert d19198.price == "18900" and d19198.currency == "CNY" and d19198.shop_name == "타오바오"

    d19394 = mem_db.query(Deal).filter_by(id=19394).first()
    assert d19394.price == "15900" and d19394.currency == "KRW" and d19394.shop_name == "배달의민족"

    d19533 = mem_db.query(Deal).filter_by(id=19533).first()
    assert d19533.price == "0" and d19533.shop_name == "지마켓" and d19533.shipping_fee == "무료배송"

    d19640 = mem_db.query(Deal).filter_by(id=19640).first()
    assert d19640.price == "16680"
    assert d19640.shipping_fee == "무료배송"
    assert d19640.shop_name == "탁탁몰"
    assert "1,000단위 구분 쉼표" not in d19640.title
    assert "택배배송" not in d19640.title

    d19700 = mem_db.query(Deal).filter_by(id=19700).first()
    assert d19700.price == "2500" and d19700.currency == "KRW"
    assert d19700.shop_name == "알리익스프레스"
    assert d19700.shipping_fee == "와우무료"

    d19800 = mem_db.query(Deal).filter_by(id=19800).first()
    assert d19800.price == "15" and d19800.currency == "KRW" and d19800.shop_name == "네이버"

    # 3차 재실행 시 멱등성(updated == 0) 100% 보장 검증
    second_stats = renormalize_existing_deals(mem_db, dry_run=False)
    assert second_stats["updated"] == 0

    # 4차 관리자 엔드포인트(/admin/renormalize-deals) 인증 및 실행 검증
    def _override_db():
        yield mem_db

    app.dependency_overrides[get_db_session] = _override_db
    try:
        client = TestClient(app)
        unauth = client.post("/admin/renormalize-deals")
        assert unauth.status_code == 403

        auth_res = client.post(
            "/admin/renormalize-deals?dry_run=true",
            headers={"X-Admin-Key": "test-secret-admin-key"},
        )
        assert auth_res.status_code == 200
        body = auth_res.json()
        assert body["status"] == "ok"
        assert body["stats"]["updated"] == 0
    finally:
        app.dependency_overrides.pop(get_db_session, None)

