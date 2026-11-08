import json
import pytest
from backend.services.aggregator_service import AggregatorService, extract_structured_multi_options
from backend.database.models import Deal


class TestTier2BoundaryAndCornerCases:
    """
    Tier 2: 경계값 및 코너 케이스 (Boundary & Corner Cases)
    - 12.7, 2025, 65, 144 등 통화 기호 없는 규격/연도/인치 숫자의 가격 오인 원천 차단
    - 재활용/끌올 글의 옵션 역오염 (구 옵션 누출 및 잔류) 차단
    - 극단적 길이 (초장문 제목 1000자, 수만 자 거대 HTML, 빈 문자열)
    - 특수문자 및 꺾쇠 할인코드 (<BALPUM01>) 보존 및 스크립트 안전 탈출
    - 음수 할인액, 구매 조건문, 화살표 정가 표기의 경계선 판별
    """

    # -------------------------------------------------------------------------
    # 1. 통화 기호 없는 숫자 가격 오인 차단 (Feature 3 경계값)
    # -------------------------------------------------------------------------
    def test_pure_numbers_without_currency_not_misidentified_as_price(self):
        """
        [경계값 1] 12.7(인치), 2025(연도), 65(인치), 144(Hz) 등
        통화 기호나 화폐 단위가 결합되지 않은 순수 스펙 숫자가
        절대 가격(KRW/USD)으로 오인 파싱되지 않아야 합니다.
        """
        # 케이스 1: 제목 내 스펙 숫자와 실제 원화 가격 병기
        title_with_specs = "[지마켓] 레노버 샤오신패드 프로 12.7 2025년형 144Hz 태블릿 (239,000원/무료)"
        opts_specs = extract_structured_multi_options(
            raw_title=title_with_specs,
            content_html="",
            default_currency="KRW",
            default_url="https://example.com/pad"
        )
        if opts_specs:
            for opt in opts_specs:
                assert opt["price"] not in (12, 13, 127, 1270, 2025, 144), f"스펙 숫자가 가격으로 오인됨: {opt}"

        # 케이스 2: 슬래시 구분에서 한쪽이 통화 없는 스펙 숫자인 경우
        slash_spec_title = "샤오신패드 12.7/갤럭시탭 S9 FE 450,000원"
        opts_slash = extract_structured_multi_options(
            raw_title=slash_spec_title,
            content_html="",
            default_currency="KRW",
            default_url="https://example.com/test"
        )
        if len(opts_slash) >= 2:
            assert opts_slash[0]["price"] != 1270, "12.7이 $12.70(1270)으로 오인 파싱됨!"
            assert opts_slash[0]["price"] != 12, "12.7이 12원으로 오인 파싱됨!"

    # -------------------------------------------------------------------------
    # 2. 재활용 글 옵션 역오염 (구 옵션 잔류) 차단 (Feature 4 코너 케이스)
    # -------------------------------------------------------------------------
    @pytest.mark.asyncio
    async def test_recycled_post_options_count_reduction_no_pollution(self, db_session):
        """
        [경계값 2] 이전 게시글에 5개의 옵션이 있던 글이 단 1개의 단일 상품 또는 2개의 신규 옵션으로
        재활용되었을 때, 기존의 5개 구 옵션이 신규 옵션에 섞여 역오염되거나 잔류하지 않고
        신규 옵션만으로 깨끗하게 갱신되는지 검증합니다.
        """
        aggregator = AggregatorService(db_session)
        post_url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=77777"

        # 1. 5개 옵션 등록
        initial_opts = [
            {"name": f"구형 티셔츠 옵션 {i}", "price": 10000 * i, "currency": "KRW", "url": f"https://old.com/{i}"}
            for i in range(1, 6)
        ]
        await aggregator.process_scraped_deal(1, {
            "title": "[G마켓] 구형 티셔츠 5종 골라담기 (10,000원/무료)",
            "url": post_url,
            "ecommerce_link": "https://old.com/main",
            "price": 10000,
            "currency": "KRW",
            "content_html": "<p>구형 티셔츠 5종 모음전입니다.</p>",
            "options_data": initial_opts,
        })

        saved_old = db_session.query(Deal).filter(Deal.post_link == post_url).first()
        assert len(json.loads(saved_old.options_data)) == 5

        # 2. 완전히 다른 신규 2개 옵션 상품으로 재활용 수정 수집
        recycled_new_opts = [
            {"name": "신형 무선 마우스 블랙", "price": 45000, "currency": "KRW", "url": "https://new.com/mouse_b"},
            {"name": "신형 무선 마우스 화이트", "price": 47000, "currency": "KRW", "url": "https://new.com/mouse_w"},
        ]
        await aggregator.process_scraped_deal(1, {
            "title": "[옥션] 로지텍 신형 무선 마우스 (45,000원/무료)",
            "url": post_url,
            "ecommerce_link": "https://new.com/mouse_b",
            "price": 45000,
            "currency": "KRW",
            "content_html": "<p>로지텍 마우스 특가입니다.</p>",
            "options_data": recycled_new_opts,
        })

        db_session.expire_all()
        refreshed = db_session.query(Deal).filter(Deal.post_link == post_url).first()

        # 검증: 구 옵션 5개가 아닌 신규 옵션 2개만 존재해야 함 (M2 구현 대상)
        if refreshed.options_data:
            opts_after = json.loads(refreshed.options_data)
            assert len(opts_after) == 2, f"구 옵션이 역오염되어 {len(opts_after)}개로 남아있음!"
            assert not any("구형 티셔츠" in o["name"] for o in opts_after)
            assert all("무선 마우스" in o["name"] for o in opts_after)

    # -------------------------------------------------------------------------
    # 3. 극단적 길이 스트레스 테스트 (Extreme Length Stress)
    # -------------------------------------------------------------------------
    @pytest.mark.asyncio
    async def test_extreme_length_handling(self, db_session):
        """
        [경계값 3] 1,000자 초과 초장문 제목 및 수만 자의 거대 HTML 본문 입력 시
        파서와 어그리게이터가 크래시 없이 안전하게 정규화 및 저장하는지 검증합니다.
        """
        aggregator = AggregatorService(db_session)
        long_title = "[초특가] " + "초특가 대박 상품 긴 제목 " * 80 + " (50,000원/무료)"
        assert len(long_title) > 1000

        massive_html = "<div>" + "<p>상세 본문 단락 내용 테스트입니다. </p>" * 2000 + "</div>"
        assert len(massive_html) > 50000

        scraped = {
            "title": long_title,
            "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=66666",
            "ecommerce_link": "https://example.com/item",
            "price": 50000,
            "currency": "KRW",
            "content_html": massive_html,
        }

        # 크래시 없이 정상 수집 완료되어야 함
        await aggregator.process_scraped_deal(1, scraped)
        saved = db_session.query(Deal).filter(Deal.post_link == scraped["url"]).first()
        assert saved is not None
        assert int(float(str(saved.price).replace(',', ''))) == 50000
        assert len(saved.content_html) > 1000

    # -------------------------------------------------------------------------
    # 4. 특수문자, XSS 방어 및 프로모션 코드 보존
    # -------------------------------------------------------------------------
    def test_special_chars_and_angle_bracket_promo_code_preservation(self):
        """
        [경계값 4]
        1. <script> 등 악성 HTML 태그 탈출 방어
        2. 인라인 및 블록 할인코드(BALPUM01, WLRRN517)가 파서에 의해 보존되는지 검증
        """
        content_with_code = """
        <p>알리익스프레스 광군제 단독 특가!</p>
        <p>1. UGREEN 100W GaN 고속충전기</p>
        <p>공구가: $19.99</p>
        <p>공구코드: BALPUM01</p>
        <p><a href="https://ko.aliexpress.com/item/1005001.html">구매링크 바로가기</a></p>
        <script>alert('xss');</script>
        <p>2. Baseus 65W 보조배터리</p>
        <p>공구가: $24.50</p>
        <p>공구코드: WLRRN517</p>
        <p><a href="https://ko.aliexpress.com/item/1005002.html">구매링크 바로가기</a></p>
        """

        opts = extract_structured_multi_options(
            raw_title="[알리] 충전기 모음전 외 2종",
            content_html=content_with_code,
            default_currency="USD",
            default_url=""
        )

        assert len(opts) >= 2, f"2개 이상 옵션 추출 기대, 실제: {len(opts)}"
        codes = [o.get("code") for o in opts if o.get("code")]
        assert any("BALPUM01" in c for c in codes), f"BALPUM01 코드 누락: {codes}"
        assert any("WLRRN517" in c for c in codes), f"WLRRN517 코드 누락: {codes}"

    # -------------------------------------------------------------------------
    # 5. 음수 할인액, 구매 조건문 및 화살표 가격 경계 조건
    # -------------------------------------------------------------------------
    def test_negative_discount_and_condition_boundary(self):
        """
        [경계값 5]
        - 음수 할인액(-$10, -₩15,000)이 상품 가격으로 선택되지 않아야 함
        - '$150 이상 구매 시 -$22' 같은 조건문이 상품 가격이 되지 않아야 함
        - '166,800원 > 98,200원'에서 정가 166,800원이 아닌 최종가 98,200원이 채택되어야 함
        """
        body_text = """
        1. 프리미엄 무선 노이즈캔슬링 헤드폰
        166,800원 > 98,200원
        - 판매자할인 -$15.00
        - $100 이상 결제 시 -$10 추가할인
        구매링크: https://item.com/headphone
        2. 고속 충전 보조배터리 20000mAh
        45,000원 -> 29,000원
        구매링크: https://item.com/battery
        """

        opts = extract_structured_multi_options(
            raw_title="음향/배터리 모음전 외 2종",
            content_html=body_text,
            default_currency="KRW",
            default_url=""
        )

        assert len(opts) >= 2
        # 1번 헤드폰의 가격은 98,200원이어야 함 (166,800원이나 15달러, 10달러가 아님)
        assert opts[0]["price"] == 98200
        # 2번 배터리의 가격은 29,000원이어야 함 (45,000원이 아님)
        assert opts[1]["price"] == 29000
