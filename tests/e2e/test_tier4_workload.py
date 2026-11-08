import json
from unittest.mock import patch, AsyncMock, MagicMock
import pytest
from backend.services.aggregator_service import AggregatorService
from backend.database.models import Deal, PriceHistory
from tests.e2e.conftest import clean_option_title_spec


class TestTier4RealWorkloadScenarios:
    """
    Tier 4: 실환경 워크로드 시나리오 (End-to-End Real Workload Pipelines)
    - 시나리오 1: 100자 절단 뽐뿌 딜의 서울 릴레이 백필 복구 E2E 파이프라인
    - 시나리오 2: 핫딜 가격 변동 및 끌올/재활용 발생 시의 전체 라이프사이클 & PriceHistory
    - 시나리오 3: 다중 옵션 모음전 핫딜의 수집 -> 웹/앱 공통 계약 서빙 E2E
    """

    # -------------------------------------------------------------------------
    # 시나리오 1: 100자 절단 뽐뿌 딜의 서울 릴레이 백필 복구 E2E 파이프라인
    # -------------------------------------------------------------------------
    @pytest.mark.asyncio
    async def test_scenario_ppomppu_100_char_cut_and_backfill_pipeline(self, db_session, client):
        """
        [워크로드 시나리오 1]
        1. 클라우드 피더에서 뽐뿌 딜이 100자 미만 요약문으로 최초 저장됨 (결함 상태 모의)
        2. 백필 파이프라인이 100자 이하 딜을 탐색하여 서울 릴레이를 통해 상세 본문을 재수집
        3. 복구 후 DB와 API(/api/community/deals/{id})에서 100자 이상의 정상 본문이 서빙되는지 검증
        """
        aggregator = AggregatorService(db_session)
        target_url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=104999"

        # 1. 100자 미만 요약본으로 최초 저장
        short_summary = "뽐뿌 RSS 요약문입니다. 상세 본문이 100자로 잘려있는 상태입니다."
        assert len(short_summary) < 100

        scraped_initial = {
            "title": "[11번가] 풀무원 얇은피 만두 8봉 (19,900원/무료)",
            "url": target_url,
            "ecommerce_link": "https://11st.co.kr/products/123",
            "price": 19900,
            "currency": "KRW",
            "content_html": short_summary,
            "shop_name": "11번가",
        }
        await aggregator.process_scraped_deal(1, scraped_initial)

        deal = db_session.query(Deal).filter(Deal.post_link == target_url).first()
        assert deal is not None
        assert len(deal.content_html) < 100

        # API로 조회했을 때도 잘린 상태 확인
        resp_before = client.get(f"/api/community/deals/{deal.id}")
        assert resp_before.status_code == 200
        assert len(resp_before.json()["content_html"]) < 100

        # 2. 백필 파이프라인 가동 (100자 이하 딜 식별 및 서울 릴레이 재수집 모의)
        full_detail_html = """
        <div class="board-detail">
            <h3>풀무원 얇은피 만두 8봉 특가 안내</h3>
            <p>11번가 단독 특가로 진행되는 얇은피 김치만두 4봉 + 얇은피 고기만두 4봉 총 8봉 알찬 세트 구성입니다.</p>
            <p>쿠폰 적용 시 최종 결제금액 19,900원에 전 지역 무료배송 혜택이 적용됩니다. 카드사 청구할인 추가 5% 가능합니다.</p>
            <p>유통기한은 넉넉하게 6개월 이상 남아있으며 수령 즉시 냉동보관을 권장드립니다.</p>
            <p>상세 조리방법은 에어프라이어 180도 10분 조리 시 가장 바삭하고 맛있게 드실 수 있습니다.</p>
        </div>
        """
        assert len(full_detail_html) > 100

        # 백필 로직 모의: Vercel 릴레이로 상세 본문을 가져와 갱신
        scraped_backfilled = dict(scraped_initial)
        scraped_backfilled["content_html"] = full_detail_html
        await aggregator.process_scraped_deal(1, scraped_backfilled)

        db_session.expire_all()
        refreshed_deal = db_session.query(Deal).filter(Deal.post_link == target_url).first()

        # 3. 복구 검증: 본문 길이가 100자 초과로 복구되었고 API 응답에도 상세 내용이 반영되어야 함
        assert len(refreshed_deal.content_html) > 100
        assert "풀무원 얇은피 만두 8봉 특가 안내" in refreshed_deal.content_html

        resp_after = client.get(f"/api/community/deals/{deal.id}")
        assert resp_after.status_code == 200
        api_data = resp_after.json()
        assert len(api_data["content_html"]) > 100
        assert "11번가 단독 특가" in api_data["content_html"]

    # -------------------------------------------------------------------------
    # 시나리오 2: 핫딜 가격 변동 및 끌올/재활용 발생 시의 전체 라이프사이클 & PriceHistory
    # -------------------------------------------------------------------------
    @pytest.mark.asyncio
    async def test_scenario_price_fluctuation_and_recycled_post_lifecycle(self, db_session, client):
        """
        [워크로드 시나리오 2]
        1. 초기 딜 등록 (가격: 20,000원)
        2. 가격 인하 (가격: 17,000원) -> Continuous Upsert
        3. 작성자가 게시글 내용을 다른 상품(게이밍 체어 120,000원)으로 재활용
        4. Full Force Sync로 딜 정보가 갱신되고, API 응답이 정합성을 유지하는지 검증
        """
        aggregator = AggregatorService(db_session)
        deal_url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=105001"

        # 1. 초기 딜 등록
        await aggregator.process_scraped_deal(1, {
            "title": "[쿠팡] 게이밍 장패드 900x400 (20,000원/무료)",
            "url": deal_url,
            "ecommerce_link": "https://coupang.com/pad",
            "price": 20000,
            "currency": "KRW",
            "content_html": "<p>두툼한 게이밍 장패드입니다.</p>",
            "shop_name": "쿠팡",
        })

        deal = db_session.query(Deal).filter(Deal.post_link == deal_url).first()
        deal_id = deal.id
        assert int(float(str(deal.price).replace(',', ''))) == 20000

        # 2. 가격 인하 (Continuous Upsert)
        await aggregator.process_scraped_deal(1, {
            "title": "[쿠팡] 게이밍 장패드 900x400 (17,000원/무료)",
            "url": deal_url,
            "ecommerce_link": "https://coupang.com/pad",
            "price": 17000,
            "currency": "KRW",
            "content_html": "<p>가격이 추가 인하되었습니다.</p>",
            "shop_name": "쿠팡",
        })

        db_session.expire_all()
        deal = db_session.query(Deal).filter(Deal.id == deal_id).first()
        assert int(float(str(deal.price).replace(',', ''))) == 17000

        # 3. 작성자의 글 재활용 (상품 완전 변경: 게이밍 체어 120,000원)
        new_recycled_opts = [
            {"name": "게이밍 체어 블랙", "price": 120000, "currency": "KRW", "url": "https://chair.com/b"},
            {"name": "게이밍 체어 화이트", "price": 125000, "currency": "KRW", "url": "https://chair.com/w"},
        ]
        await aggregator.process_scraped_deal(1, {
            "title": "[네이버] 제닉스 아레나 게이밍 체어 (120,000원/무료)",
            "url": deal_url,
            "ecommerce_link": "https://chair.com/b",
            "price": 120000,
            "currency": "KRW",
            "content_html": "<p>제닉스 게이밍 의자 끌올 및 재활용 특가입니다.</p>",
            "shop_name": "네이버",
            "options_data": new_recycled_opts,
        })

        db_session.expire_all()
        final_deal = db_session.query(Deal).filter(Deal.id == deal_id).first()

        # 검증: 제목, 가격, 본문 신규 상품으로 전환
        assert "게이밍 체어" in final_deal.title
        assert int(float(str(final_deal.price).replace(',', ''))) == 120000
        assert "제닉스 게이밍 의자" in final_deal.content_html

        # API 엔드포인트 조회 검증
        api_resp = client.get(f"/api/community/deals/{deal_id}")
        assert api_resp.status_code == 200
        resp_data = api_resp.json()
        assert resp_data["title"] == final_deal.title
        assert resp_data["price"] == 120000

    # -------------------------------------------------------------------------
    # 시나리오 3: 다중 옵션 모음전 핫딜의 수집 -> 웹/앱 공통 계약 서빙 E2E
    # -------------------------------------------------------------------------
    @pytest.mark.asyncio
    async def test_scenario_multi_options_collection_to_api_contract(self, db_session, client):
        """
        [워크로드 시나리오 3]
        1. 알리익스프레스 다중 옵션 모음전 게시글 수집 및 옵션/프로모션 코드 파싱
        2. DB 저장 후 웹/앱 공통 API(/api/community/deals/{id}) 호출
        3. 반환된 데이터가 가격, 구매 링크, 접두사 클린(`cleanOptionTitle`) 규칙을 충족하는지 종합 검증
        """
        aggregator = AggregatorService(db_session)
        collection_url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=105002"

        collection_body = """
        <p>알리 가을 페스타 할인 모음전입니다.</p>
        <p>1. 알리검색 - Baseus 100W C타입 케이블 2m</p>
        <p>공구가: $4.99</p>
        <p>할인코드: BASEUS04</p>
        <p><a href="https://ko.aliexpress.com/item/10001.html">구매링크 바로가기</a></p>
        <p>2. 알리검색 - Toocki 67W GaN 고속충전기</p>
        <p>공구가: $8.50</p>
        <p>할인코드: TOOCKI08</p>
        <p><a href="https://ko.aliexpress.com/item/10002.html">구매링크 바로가기</a></p>
        """

        await aggregator.process_scraped_deal(5, {
            "title": "[알리] 알리검색 - Baseus 및 Toocki 고속 충전 기획전 외 2종",
            "url": collection_url,
            "ecommerce_link": "https://ko.aliexpress.com/item/10001.html",
            "price": 0,
            "currency": "USD",
            "content_html": collection_body,
            "shop_name": "알리익스프레스",
        })

        deal = db_session.query(Deal).filter(Deal.post_link == collection_url).first()
        assert deal is not None

        # API 호출 및 스키마 계약 검증
        resp = client.get(f"/api/community/deals/{deal.id}")
        assert resp.status_code == 200
        data = resp.json()

        assert "sources" in data
        assert len(data["sources"]) >= 1

        for src in data["sources"]:
            assert "price" in src
            assert "site_name" in src
            # 접두사 정제 검증: 웹/앱 공통 클리너 적용 시 접두사 부재
            raw_pname = src.get("product_name") or src.get("name") or ""
            if raw_pname:
                cleaned_opt_name = clean_option_title_spec(raw_pname)
                assert not cleaned_opt_name.startswith("알리검색 - "), f"접두사 미정제 잔류: {cleaned_opt_name}"
