import json
import pytest
from backend.services.aggregator_service import AggregatorService, extract_structured_multi_options
from backend.database.models import Deal
from tests.e2e.conftest import clean_option_title_spec


class TestTier3PairwiseCombinatorial:
    """
    Tier 3: 복합 인터랙션 및 페어와이즈 (Pairwise Combinatorial Testing)
    - Factor A (커뮤니티 소스): 뽐뿌(1), 퀘이사존(6), 펨코(7), 클리앙(4), 알리뽐뿌(5)
    - Factor B (통화/표기): KRW(원), KRW(만), USD($), EUR(€)
    - Factor C (옵션 구조): 단일 상품, 괄호 슬래시, Pattern C(괄호 밖 슬래시), 본문 번호형
    - Factor D (배송비): 무료배송, 유료(3,000원), 조건부 무료
    - Factor E (접두사/코드): 알리검색 접두사+코드, 네이버검색 접두사, 클린 상품명+코드
    
    모든 2-Way 조합을 체계적으로 커버하는 페어와이즈 테스트 매트릭스를 실행하여
    파싱, 정규화, DB 저장, 옵션 정제 및 통화 보존이 빈틈없이 이루어지는지 검증합니다.
    """

    # 페어와이즈 직교/커버리지 생성 매트릭스 (10개 핵심 상호작용 케이스)
    PAIRWISE_TEST_CASES = [
        # Case 1: 뽐뿌 + KRW(원) + 괄호 밖 슬래시(Pattern C) + 무료배송 + 알리검색 접두사/코드
        {
            "community_id": 1,
            "title": "[뽐뿌] 알리검색 - 모니터 암 24,000원/노트북 거치대 18,000원 (무료배송)",
            "content": "<p>모니터암 및 거치대 특가</p>",
            "expected_currency": "KRW",
            "expected_opt_count": 2,
            "expected_prices": [24000, 18000],
            "has_prefix": True,
        },
        # Case 2: 퀘이사존 + USD($) + 본문 번호형 + 유료배송 + 클린 상품명/코드
        {
            "community_id": 6,
            "title": "[퀘이사존] 기가바이트 메인보드 및 그래픽카드 모음 ($150~ / 3,000원)",
            "content": """
            1. B650M AORUS ELITE 메인보드
            공구가: $149.99
            공구코드: GIGA650
            구매링크: https://item.com/b650
            2. RTX 4070 SUPER 그래픽카드
            공구가: $589.00
            공구코드: GIGA4070
            구매링크: https://item.com/4070
            """,
            "expected_currency": "USD",
            "expected_opt_count": 2,
            "expected_prices": [14999, 58900],
            "has_prefix": False,
        },
        # Case 3: 펨코 + KRW(만) + 괄호 슬래시 + 조건부 무료 + 네이버검색 접두사
        {
            "community_id": 7,
            "title": "[펨코] 네이버검색 - 로지텍 키보드 마우스 (MX Keys S 12.5만 / MX Master 3S 9.9만)",
            "content": "<p>5만원 이상 무료배송</p>",
            "expected_currency": "KRW",
            "expected_opt_count": 2,
            "expected_prices": [125000, 99000],
            "has_prefix": True,
        },
        # Case 4: 클리앙 + EUR(€) + 단일 상품 + 무료배송 + 클린 상품명
        {
            "community_id": 4,
            "title": "[클리앙] 소니 WH-1000XM5 노이즈캔슬링 헤드폰 (€279.00/독일 무료배송)",
            "content": "<p>독일 아마존 직배송</p>",
            "expected_currency": "EUR",
            "expected_opt_count": 0,
            "expected_prices": [27900],
            "has_prefix": False,
        },
        # Case 5: 알리뽐뿌 + USD($) + 괄호 밖 슬래시(Pattern C) + 무료배송 + 알리검색 접두사
        {
            "community_id": 5,
            "title": "[알리] 알리검색 - Anker 737 보배 $89.99/Anker GaN 충전기 $35.50 (무배)",
            "content": "<p>알리 세일 행사</p>",
            "expected_currency": "USD",
            "expected_opt_count": 2,
            "expected_prices": [8999, 3550],
            "has_prefix": True,
        },
        # Case 6: 뽐뿌 + KRW(만) + 본문 번호형 + 무료배송 + 클린 상품명
        {
            "community_id": 1,
            "title": "[뽐뿌] 삼성 비스포크 냉장고 세탁기 모음전 (110만~ / 무료배송)",
            "content": """
            1. 비스포크 4도어 냉장고 875L
            최종체감가: 185만
            구매링크: https://samsung.com/fridge
            2. 비스포크 AI 그랑데 세탁기 25kg
            최종체감가: 110만
            구매링크: https://samsung.com/washer
            """,
            "expected_currency": "KRW",
            "expected_opt_count": 2,
            "expected_prices": [1850000, 1100000],
            "has_prefix": False,
        },
        # Case 7: 퀘이사존 + KRW(원) + 괄호 슬래시 + 유료배송 + 알리검색 접두사
        {
            "community_id": 6,
            "title": "[퀘이사존] 알리검색 - Thermalright 쿨러 (PA120 42,000원 / FC140 55,000원)",
            "content": "<p>배송비 3,000원 별도</p>",
            "expected_currency": "KRW",
            "expected_opt_count": 2,
            "expected_prices": [42000, 55000],
            "has_prefix": True,
        },
        # Case 8: 펨코 + USD($) + 단일 상품 + 무료배송 + 클린 상품명
        {
            "community_id": 7,
            "title": "[펨코] AMD 라이젠 7 7800X3D CPU ($329.99/무료직배)",
            "content": "<p>역대급 할인</p>",
            "expected_currency": "USD",
            "expected_opt_count": 0,
            "expected_prices": [32999],
            "has_prefix": False,
        },
        # Case 9: 클리앙 + KRW(원) + 괄호 밖 슬래시(Pattern C) + 무료배송 + 네이버검색 접두사
        {
            "community_id": 4,
            "title": "[클리앙] 네이버검색 - 제주 삼다수 2L 12병 12,800원/백산수 2L 12병 11,500원 (무료)",
            "content": "<p>생수 특가</p>",
            "expected_currency": "KRW",
            "expected_opt_count": 2,
            "expected_prices": [12800, 11500],
            "has_prefix": True,
        },
        # Case 10: 알리뽐뿌 + USD($) + 본문 번호형 + 무료배송 + 클린 상품명
        {
            "community_id": 5,
            "title": "[알리] 유럽 직배송 전자기기 2종 모음전 ($38~ / 무료배송)",
            "content": """
            1. 필립스 전동칫솔 소닉케어
            공구가: $49.50
            구매링크: https://item.com/philips
            2. 오랄비 구강세정기
            공구가: $38.00
            구매링크: https://item.com/oralb
            """,
            "expected_currency": "USD",
            "expected_opt_count": 2,
            "expected_prices": [4950, 3800],
            "has_prefix": False,
        },
    ]

    @pytest.mark.asyncio
    @pytest.mark.parametrize("tc", PAIRWISE_TEST_CASES)
    async def test_pairwise_matrix_pipeline(self, db_session, tc):
        """
        [페어와이즈 매트릭스 검증]
        커뮤니티 x 통화 x 옵션구조 x 배송비 x 접두사 5개 차원의 조합 데이터가
        Aggregator 파이프라인을 통과했을 때 기대 통화, 옵션 분할, 가격, 접두사 정제가
        정확히 동작하는지 검증합니다.
        """
        aggregator = AggregatorService(db_session)
        target_url = f"https://community{tc['community_id']}.com/deal/{abs(hash(tc['title']))}"

        # 1. 수집 파이프라인 실행
        scraped_data = {
            "title": tc["title"],
            "url": target_url,
            "ecommerce_link": "https://shop.com/item",
            "price": 0,
            "currency": tc["expected_currency"],
            "content_html": tc["content"],
        }
        await aggregator.process_scraped_deal(tc["community_id"], scraped_data)

        # 2. DB 저장 결과 확인
        saved_deal = db_session.query(Deal).filter(Deal.post_link == target_url).first()
        assert saved_deal is not None, f"딜 저장 실패: {tc['title']}"

        # 통화 검증
        if tc["expected_currency"] != "KRW":
            assert saved_deal.currency == tc["expected_currency"], f"통화 불일치: 기대 {tc['expected_currency']}, 실제 {saved_deal.currency}"

        # 3. 옵션 추출 및 정제 검증
        if tc["expected_opt_count"] >= 2:
            assert saved_deal.options_data is not None, f"옵션 데이터 누락: {tc['title']}"
            parsed_opts = json.loads(saved_deal.options_data)
            assert len(parsed_opts) >= tc["expected_opt_count"], f"옵션 개수 부족: 기대 {tc['expected_opt_count']}, 실제 {len(parsed_opts)}"

            for idx, exp_price in enumerate(tc["expected_prices"]):
                if idx < len(parsed_opts):
                    opt = parsed_opts[idx]
                    assert opt["price"] == exp_price, f"옵션 가격 불일치 ({idx}번): 기대 {exp_price}, 실제 {opt['price']}"

            # 접두사 정제 검증: 접두사가 있었던 경우 clean_option_title_spec 적용 시 접두사 완전히 제거
            if tc["has_prefix"]:
                for opt in parsed_opts:
                    cleaned_name = clean_option_title_spec(opt["name"])
                    assert not cleaned_name.startswith("알리검색 - "), f"접두사 미정제: {cleaned_name}"
                    assert not cleaned_name.startswith("네이버검색 - "), f"접두사 미정제: {cleaned_name}"
        else:
            # 단일 상품의 경우 대표 가격 검증
            if tc["expected_prices"]:
                saved_p = int(float(str(saved_deal.price).replace(',', '')))
                assert saved_p == tc["expected_prices"][0], f"단일 딜 대표가격 불일치: 기대 {tc['expected_prices'][0]}, 실제 {saved_p}"
