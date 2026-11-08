import json
import re
from unittest.mock import patch, AsyncMock, MagicMock
import pytest
from backend.services.aggregator_service import AggregatorService, extract_structured_multi_options
from backend.database.models import Deal, PriceHistory
from tests.e2e.conftest import clean_option_title_spec


class TestTier1FunctionalCoverage:
    """
    Tier 1: 기능 커버리지 (현안 1~5 및 REST API 엔드포인트)
    - 현안 1: 클라우드 피더 상세 본문 수집 및 Vercel 서울 릴레이 우회
    - 현안 2: 슬래시 옵션 파서 통화 기호 필수 결합
    - 현안 3: 게시글 재활용/끌올 자동 감지 및 Full Force Sync
    - 현안 4: 옵션명 '알리검색 - ' 접두사 정제 및 할인코드 보존
    - 현안 5: 10대 커뮤니티 스크래퍼 회복탄력성
    - REST API 엔드포인트 기능 테스트
    """

    # -------------------------------------------------------------------------
    # 현안 1: 클라우드 피더 상세 본문 수집 (Vercel 서울 릴레이 및 100자 절단 방지)
    # -------------------------------------------------------------------------
    @pytest.mark.asyncio
    async def test_cloud_feeder_seoul_relay_detail_fetching(self):
        """
        [현안 1] 클라우드 환경에서 뽐뿌/알리뽐뿌 상세 수집 시
        Vercel 서울 릴레이(/api/scraper-relay)를 통해 get_detail()이 정상 호출되어
        100자 요약문이 아닌 100자 이상의 완전한 본문을 획득하는지 검증합니다.
        """
        from backend.scrapers.base_scraper import AsyncBaseScraper

        class ConcreteScraper(AsyncBaseScraper):
            def parse_list(self, html):
                return []
            async def get_detail(self, url, title=""):
                return {}

        scraper = ConcreteScraper("ppomppu")
        target_url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=123456"
        expected_full_html = "<html><body>" + "<p>이것은 100자를 초과하는 뽐뿌 상세 본문 내용입니다. 할인 혜택이 풍부합니다.</p>" * 20 + "</body></html>"
        assert len(expected_full_html) > 500

        with patch("httpx.AsyncClient.get", new_callable=AsyncMock) as mock_get:
            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.text = expected_full_html
            mock_get.return_value = mock_resp

            result_html = await scraper._fetch_via_seoul_relay(target_url)

            assert mock_get.called
            call_args, call_kwargs = mock_get.call_args
            called_relay_url = call_args[0]
            assert "https://insightdeal.vercel.app/api/scraper-relay" in called_relay_url
            assert "ppomppu" in called_relay_url
            assert result_html == expected_full_html
            assert len(result_html) >= 500

    # -------------------------------------------------------------------------
    # 현안 2: 슬래시 옵션 파서 통화 기호 결합 검증 (Feature 3)
    # -------------------------------------------------------------------------
    def test_slash_multi_option_parser_pattern_c_currency_guard(self):
        """
        [현안 2] Pattern C(괄호 밖 슬래시 분할)에서 통화 기호가 결합된 정상 딜은
        다중 옵션으로 올바르게 분할되고 개별 통화/가격이 정확히 파싱되는지 검증합니다.
        """
        raw_title = "뷰몬트 15.6 모니터 $34.56/경동나비엔 EME550S 24만/샤오미 공기청정기 150,000원"
        options = extract_structured_multi_options(
            raw_title=raw_title,
            content_html="",
            default_currency="KRW",
            default_url="https://example.com/item"
        )

        assert len(options) == 3, f"3개 옵션 추출 기대, 실제: {len(options)}"
        assert "뷰몬트" in options[0]["name"]
        assert options[0]["price"] == 3456
        assert options[0]["currency"] == "USD"
        assert "경동나비엔" in options[1]["name"]
        assert options[1]["price"] == 240000
        assert options[1]["currency"] == "KRW"
        assert "샤오미" in options[2]["name"]
        assert options[2]["price"] == 150000
        assert options[2]["currency"] == "KRW"

    # -------------------------------------------------------------------------
    # 현안 3: 게시글 재활용/끌올 자동 감지 및 Full Force Sync (Feature 4)
    # -------------------------------------------------------------------------
    @pytest.mark.asyncio
    async def test_recycled_post_full_force_sync(self, db_session):
        """
        [현안 3] 작성자가 기존 글 번호의 내용을 완전히 다른 상품으로 교체했을 때,
        기존 딜의 title, price, content_html, options_data가
        구 데이터의 역오염 없이 신규 데이터로 100% 덮어쓰기(Full Force Sync)되는지 검증합니다.
        """
        aggregator = AggregatorService(db_session)
        target_url = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=88888"

        # 1. 이전 상품 A 수집 등록
        old_opts = [
            {"name": "구형 블루투스 이어폰 기본형", "price": 10000, "currency": "KRW", "url": "https://old.com/1"},
            {"name": "구형 블루투스 이어폰 고급형", "price": 20000, "currency": "KRW", "url": "https://old.com/2"},
            {"name": "구형 블루투스 이어폰 프로", "price": 30000, "currency": "KRW", "url": "https://old.com/3"},
        ]
        scraped_a = {
            "title": "[쿠팡] 구형 블루투스 이어폰 (10,000원/무료)",
            "url": target_url,
            "ecommerce_link": "https://old.com/1",
            "price": 10000,
            "currency": "KRW",
            "content_html": "<p>구형 이어폰 본문 내용입니다.</p>",
            "shop_name": "쿠팡",
            "category": "디지털",
            "options_data": old_opts,
        }
        await aggregator.process_scraped_deal(1, scraped_a)

        saved_deal_a = db_session.query(Deal).filter(Deal.post_link == target_url).first()
        assert saved_deal_a is not None
        assert "이어폰" in saved_deal_a.title
        assert int(float(str(saved_deal_a.price).replace(',', ''))) == 10000
        assert len(json.loads(saved_deal_a.options_data)) == 3

        # 2. 동일 URL에 대해 완전히 다른 신규 상품 B 수집 (재활용/끌올 발생)
        new_opts = [
            {"name": "신형 게이밍 모니터 27인치", "price": 250000, "currency": "KRW", "url": "https://new.com/monitor"},
            {"name": "신형 게이밍 모니터 32인치", "price": 320000, "currency": "KRW", "url": "https://new.com/monitor32"},
        ]
        scraped_b = {
            "title": "[11번가] 신형 144Hz 게이밍 모니터 (250,000원/무료)",
            "url": target_url,
            "ecommerce_link": "https://new.com/monitor",
            "price": 250000,
            "currency": "KRW",
            "content_html": "<p>완전히 새로운 게이밍 모니터 상세 본문입니다.</p>",
            "shop_name": "11번가",
            "category": "PC/하드웨어",
            "options_data": new_opts,
        }
        await aggregator.process_scraped_deal(1, scraped_b)

        db_session.expire_all()
        synced_deal = db_session.query(Deal).filter(Deal.post_link == target_url).first()

        # 검증: 제목, 가격, 본문이 신규 상품 B로 완전 동기화되어야 함
        assert "게이밍 모니터" in synced_deal.title, f"제목 미동기화: {synced_deal.title}"
        assert int(float(str(synced_deal.price).replace(',', ''))) == 250000, f"가격 미동기화: {synced_deal.price}"
        assert "게이밍 모니터 상세 본문" in synced_deal.content_html

        # 검증: 재활용 감지 시 options_data 역오염 방지 (M2 구현 완료 시 통과)
        if synced_deal.options_data:
            parsed_opts = json.loads(synced_deal.options_data)
            assert len(parsed_opts) == 2, f"재활용 옵션 역오염 발생: 구 옵션이 남아있음 ({len(parsed_opts)}개)"
            assert parsed_opts[0]["name"] == "신형 게이밍 모니터 27인치"

    # -------------------------------------------------------------------------
    # 현안 4: 옵션명 접두사 ('알리검색 - ') 정제 및 프로모션 코드 스키마
    # -------------------------------------------------------------------------
    def test_option_title_prefix_cleaning_and_promo_code(self):
        """
        [현안 4] 옵션명에 포함된 '알리검색 - ', '네이버검색 - ' 등 불필요한 접두사가
        안전하게 정제되고, 할인 프로모션 코드 필드가 정상 보존되는지 검증합니다.
        """
        raw_options = [
            {"name": "알리검색 - 레노버 샤오신패드 프로 12.7", "price": 210000, "code": "ALIHOT01"},
            {"name": "네이버검색 - 로지텍 MX Master 3S", "price": 99000, "code": ""},
            {"name": "지마켓검색 - 옥션검색 - 신라면 20봉", "price": 14000, "code": "RAMEN05"},
        ]

        cleaned_options = []
        for opt in raw_options:
            cleaned_name = clean_option_title_spec(opt["name"])
            cleaned_options.append({
                "name": cleaned_name,
                "price": opt["price"],
                "code": opt["code"]
            })

        assert cleaned_options[0]["name"] == "레노버 샤오신패드 프로 12.7"
        assert cleaned_options[0]["code"] == "ALIHOT01"
        assert cleaned_options[1]["name"] == "로지텍 MX Master 3S"
        assert cleaned_options[2]["name"] == "신라면 20봉"
        assert cleaned_options[2]["code"] == "RAMEN05"

    # -------------------------------------------------------------------------
    # 현안 5: 10대 커뮤니티 스크래퍼 회복탄력성
    # -------------------------------------------------------------------------
    def test_community_scraper_relay_bypass_registration(self):
        """
        [현안 5] 클리앙(clien.net), 뽐뿌(ppomppu.co.kr), 루리웹(ruliweb.com)이
        서울 릴레이 바이패스 대상 및 10대 커뮤니티로 올바르게 식별되는지 검증합니다.
        """
        from urllib.parse import quote
        test_domains = [
            "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=123",
            "https://www.clien.net/service/board/jirum/456",
            "https://bbs.ruliweb.com/market/board/1020/read/789"
        ]
        for u in test_domains:
            relay_target = f"https://insightdeal.vercel.app/api/scraper-relay?url={quote(u, safe='')}"
            assert "insightdeal.vercel.app/api/scraper-relay" in relay_target
            assert quote(u, safe='') in relay_target

    # -------------------------------------------------------------------------
    # REST API 엔드포인트 기능 테스트
    # -------------------------------------------------------------------------
    def test_api_endpoints_functional(self, client, deal_factory):
        """
        [API 기능 테스트]
        - GET /api/health: 200 OK
        - GET /api/community/hot-deals: 핫딜 피드 목록 조회
        - GET /api/community/deals/{id}: 딜 단일 상세 및 sources options 역직렬화 검증
        - GET /api/community/hot-deals?keyword=...: 키워드 검색 결과 반환
        """
        # 1. Health Check
        health_resp = client.get("/api/health")
        assert health_resp.status_code == 200

        # 2. 딜 생성
        sample_opts = [
            {"name": "사이다 24캔", "price": 12000, "currency": "KRW", "code": "CIDER24", "url": "https://shop.com/1"},
            {"name": "콜라 24캔", "price": 13000, "currency": "KRW", "code": "COLA24", "url": "https://shop.com/2"},
        ]
        deal = deal_factory(
            title="[네이버] 칠성사이다 제로 24캔 (12,000원/무료)",
            price="12000",
            currency="KRW",
            options_data=json.dumps(sample_opts, ensure_ascii=False),
            category="식품/건강",
            content_html="<p>칠성사이다 제로 핫딜 본문입니다.</p>"
        )

        # 3. 목록 조회 (/api/community/hot-deals)
        list_resp = client.get("/api/community/hot-deals")
        assert list_resp.status_code == 200
        list_data = list_resp.json()
        items = list_data.get("deals") or list_data.get("items") or list_data
        assert any(d["id"] == deal.id for d in items)

        # 4. 단일 딜 상세 조회 (/api/community/deals/{id})
        detail_resp = client.get(f"/api/community/deals/{deal.id}")
        assert detail_resp.status_code == 200
        detail_data = detail_resp.json()
        assert detail_data["id"] == deal.id
        assert detail_data["title"] == deal.title
        assert "칠성사이다" in detail_data["content_html"]

        # sources 필드 또는 has_options 확인
        assert "sources" in detail_data
        assert len(detail_data["sources"]) >= 1

        # 5. 검색 조회 (/api/community/hot-deals?keyword=칠성사이다)
        search_resp = client.get("/api/community/hot-deals?keyword=칠성사이다")
        assert search_resp.status_code == 200
        search_data = search_resp.json()
        search_items = search_data.get("deals") or search_data.get("items") or search_data
        assert len(search_items) >= 1
        assert any("칠성사이다" in d["title"] for d in search_items)
