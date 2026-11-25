# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_cloud_feeder.py

cloud_feeder 및 backfill_ppomppu_100_deals 단위 테스트:
- scheduler/cloud_feeder.py 와 scripts/cloud_feeder.py 코드 동기화 검증
- 클라우드 환경 뽐뿌 상세 조회(need_detail) 및 모음전 감지 로직 검증
- FEEDER_API_TOKEN 설정 시 X-Feeder-Token 헤더 첨부 검증
- backfill_ppomppu_100_deals 스크래퍼 라우팅 및 dry-run / DB 원자적 갱신 검증
"""

import hashlib
import hmac
import os
import re
import sys
import time
from unittest.mock import AsyncMock, MagicMock
import httpx
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from backend.database.models import Base, Community, Deal
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scripts.backfill_ppomppu_100_deals import (
    backfill_direct_db,
    backfill_remote_api,
    get_scraper_for_url,
)


def test_cloud_feeder_parity_between_scheduler_and_scripts():
    scheduler_file = os.path.join(_BACKEND_DIR, "scheduler", "cloud_feeder.py")
    scripts_file = os.path.join(_BACKEND_DIR, "scripts", "cloud_feeder.py")

    assert os.path.exists(scheduler_file)
    assert os.path.exists(scripts_file)

    with open(scheduler_file, "r", encoding="utf-8") as f1, open(scripts_file, "r", encoding="utf-8") as f2:
        assert f1.read() == f2.read(), "scheduler/cloud_feeder.py와 scripts/cloud_feeder.py 내용이 일치해야 합니다."


def test_ppomppu_cloud_constraint_removed_and_feeder_token_wired():
    feeder_file = os.path.join(_BACKEND_DIR, "scheduler", "cloud_feeder.py")
    with open(feeder_file, "r", encoding="utf-8") as f:
        code = f.read()

    assert "is_ppomppu_cloud = IS_CLOUD_ENV" not in code
    assert "and not is_ppomppu_cloud" not in code
    assert 'platforms = ("펨코", "뽐뿌", "퀘이사존", "알리뽐뿌", "뽐뿌해외"' in code
    assert "cache_pf = display_name" in code
    assert "FEEDER_API_TOKEN" in code
    assert "X-Feeder-Token" in code


def test_need_detail_logic_for_ppomppu_cloud():
    short_rss_deal = {
        "title": "[11번가] 컴포즈커피 아메리카노 (1,200원)",
        "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=17934",
        "price": 1200,
        "content_html": "<p>[11번가] 컴포즈커피 아메리카노 (1,200원)</p>",
        "image_url": "https://cdn.ppomppu.co.kr/thumb.jpg",
        "ecommerce_link": "",
    }
    item_url = short_rss_deal["url"]
    item_page_no = 1

    is_bridge_item = bool("bridge_id=" in item_url)
    assert not is_bridge_item

    is_ppomppu = "ppomppu.co.kr" in item_url
    content_txt = short_rss_deal.get("content_html") or ""
    has_full_content = bool(content_txt) and len(content_txt) >= 150
    has_direct_ecom = bool(short_rss_deal.get("ecommerce_link"))

    if is_ppomppu:
        need_detail = (item_page_no == 1) or (not has_full_content) or (not has_direct_ecom) or (not short_rss_deal.get("image_url"))
    else:
        need_detail = (item_page_no == 1) or (not short_rss_deal.get("image_url"))

    assert need_detail is True


def test_multi_title_detection_unblocked():
    item_title = "[G마켓] 코카콜라 제로 24캔 외 5종 골라담기 (15,900원/무료)"
    is_multi_title = bool(
        re.search(r"(?:외\s*\d+|골라담기|모음|선택|공구|총정리|\d+\s*종|다양|특가\s*모음)", item_title)
        or (re.search(r"/[^/]+(?:원|만|\$|달러)", item_title) and re.search(r"(?:원|만|\$|달러).*/", item_title))
    )
    assert is_multi_title is True


def test_scraper_router():
    assert isinstance(get_scraper_for_url("https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=123", "ppomppu"), PpomppuScraper)
    assert isinstance(get_scraper_for_url("https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&no=456", "ali_ppomppu"), AlippomppuScraper)
    assert isinstance(get_scraper_for_url("https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&no=789", "ppomppu_overseas"), PpomppuOverseasScraper)


@pytest.mark.asyncio
async def test_backfill_remote_api_attaches_feeder_token_header(monkeypatch):
    monkeypatch.setenv("FEEDER_API_TOKEN", "test-secret-feeder-token")
    captured_posts = []

    class DummyResponse:
        def __init__(self, status_code, payload):
            self.status_code = status_code
            self._payload = payload
            self.text = str(payload)

        def json(self):
            return self._payload

    class DummyAsyncClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return False

        async def get(self, url, *args, **kwargs):
            if url.endswith("platform=뽐뿌") and "offset=0" in url:
                return DummyResponse(
                    200,
                    [
                        {
                            "id": 501,
                            "title": "[11번가] 테스트 상품 (9,900원)",
                            "post_link": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=501",
                            "content_html": "<p>짧은 요약</p>",
                            "currency": "KRW",
                        }
                    ],
                )
            return DummyResponse(200, [])

        async def post(self, url, json=None, headers=None, **kwargs):
            captured_posts.append({"url": url, "json": json, "headers": headers or {}})
            return DummyResponse(200, {"status": "ok", "saved_or_updated": 1})

    async def fake_get_detail(self, url, title=""):
        return {
            "content_html": "<p>" + ("상세 복구 본문 테스트 내용입니다. " * 12) + "</p>",
            "ecommerce_link": "https://www.11st.co.kr/products/501",
            "image_url": "https://cdn.ppomppu.co.kr/data3/501.jpg",
            "shipping_fee": "무료배송",
            "is_closed": False,
            "price": 9900,
        }

    monkeypatch.setattr(httpx, "AsyncClient", DummyAsyncClient)
    monkeypatch.setattr(PpomppuScraper, "get_detail", fake_get_detail)

    stats = await backfill_remote_api(days=1, min_len=120, limit=5, dry_run=False, concurrency=1, delay=0.0)
    assert stats["recovered"] == 1
    assert len(captured_posts) == 1
    assert captured_posts[0]["headers"].get("X-Feeder-Token") == "test-secret-feeder-token"


@pytest.mark.asyncio
async def test_backfill_direct_db_dry_run_and_update():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    Base.metadata.create_all(bind=engine)
    SessionCls = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    db = SessionCls()

    try:
        comm = Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr")
        db.add(comm)
        db.commit()

        cut_deal = Deal(
            id=101,
            source_community_id=1,
            title="[11번가] 목우촌 살코기햄 1kg 2개 (15,120원/무료)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=17930",
            ecommerce_link="",
            price="15120",
            content_html="<p>목우촌 살코기햄 1kg 2개 15,120원</p>",
            is_closed=False,
            has_options=False,
        )
        full_deal = Deal(
            id=102,
            source_community_id=1,
            title="[G마켓] 정상 본문 게시글 (25,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=17931",
            ecommerce_link="https://item.gmarket.co.kr/detailview/item?goodscode=12345",
            price="25000",
            content_html="<p>" + ("정상적인 핫딜 설명 텍스트입니다. " * 20) + '<img src="https://example.com/img.jpg" /></p>',
            is_closed=False,
            has_options=False,
        )
        db.add_all([cut_deal, full_deal])
        db.commit()

        mock_recovered_body = (
            "목우촌 살코기햄 1kg 2개 구성입니다.\n"
            "쿠폰 적용 시 15,120원 무료배송입니다.\n"
            "1. 목우촌 1kg 2개 - 15,120원 (링크: https://www.11st.co.kr/products/11111)\n"
            "2. 목우촌 200g 10캔 - 15,920원 (링크: https://www.11st.co.kr/products/22222)\n"
            "[첨부된 이미지 링크들]: https://cdn.ppomppu.co.kr/data3/ham.jpg"
        )

        original_get_detail = PpomppuScraper.get_detail

        async def mock_get_detail(self, url, title=""):
            return {
                "content_html": mock_recovered_body,
                "ecommerce_link": "https://www.11st.co.kr/products/11111",
                "image_url": "https://cdn.ppomppu.co.kr/data3/ham.jpg",
                "shipping_fee": "무료배송",
                "is_closed": False,
                "price": 15120,
            }

        PpomppuScraper.get_detail = mock_get_detail
        try:
            dry_stats = await backfill_direct_db(db=db, days=0, min_len=120, limit=10, dry_run=True, concurrency=1, delay=0.0)
            assert dry_stats["candidates"] == 1
            assert dry_stats["recovered"] == 1

            d_check = db.query(Deal).filter(Deal.id == 101).first()
            assert len(d_check.content_html) < 120

            real_stats = await backfill_direct_db(db=db, days=0, min_len=120, limit=10, dry_run=False, concurrency=1, delay=0.0)
            assert real_stats["candidates"] == 1
            assert real_stats["recovered"] == 1

            db.expire_all()
            updated_deal = db.query(Deal).filter(Deal.id == 101).first()
            assert len(updated_deal.content_html) >= 120
            assert updated_deal.ecommerce_link == "https://www.11st.co.kr/products/11111"
        finally:
            PpomppuScraper.get_detail = original_get_detail
    finally:
        db.close()


def _verify_v1_hmac(token: str, secret: str, target_url: str) -> bool:
    parts = (token or "").split(".")
    if len(parts) != 3 or parts[0] != "v1":
        return False
    ts_str, sig_hex = parts[1], parts[2]
    if not ts_str.isdigit() or len(sig_hex) != 64:
        return False
    ts = int(ts_str)
    if abs(int(time.time()) - ts) > 300:
        return False
    expected = hmac.new(
        secret.encode("utf-8"),
        f"{ts}:{target_url}".encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    return hmac.compare_digest(sig_hex.lower(), expected)


@pytest.mark.asyncio
async def test_base_scraper_seoul_relay_hmac_and_rss_fallback(monkeypatch):
    from backend.scrapers.base_scraper import AsyncBaseScraper

    class DummyRelayScraper(AsyncBaseScraper):
        async def parse_list(self, html):
            return []

        async def get_detail(self, url, title=""):
            return {}

    scraper = DummyRelayScraper("ppomppu")
    target_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu"
    expected_rss_target = "https://www.ppomppu.co.kr/rss.php?id=ppomppu"

    # 1. FEEDER_API_TOKEN 설정 시 일반 요청 + RSS 폴백 각각 타깃 URL 기준으로 독립 서명 검증
    monkeypatch.setenv("FEEDER_API_TOKEN", "relay-secret-xyz")
    monkeypatch.delenv("SCRAPER_RELAY_SECRET", raising=False)
    calls = []

    async def fake_get(self, url, headers=None, **kwargs):
        calls.append({"url": url, "headers": dict(headers or {})})
        resp = MagicMock()
        if len(calls) == 1:
            resp.status_code = 502
            resp.text = "Bad Gateway"
        else:
            resp.status_code = 200
            resp.text = "<rss><channel><item><title>테스트 딜</title></item></channel></rss>"
        return resp

    monkeypatch.setattr(httpx.AsyncClient, "get", fake_get)
    result = await scraper._fetch_via_seoul_relay(target_url)

    assert result is not None and "<item>" in result
    assert len(calls) == 2

    tok_main = calls[0]["headers"].get("X-Feeder-Token", "")
    tok_rss = calls[1]["headers"].get("X-Feeder-Token", "")
    assert _verify_v1_hmac(tok_main, "relay-secret-xyz", target_url)
    assert _verify_v1_hmac(tok_rss, "relay-secret-xyz", expected_rss_target)
    # 타깃 URL이 다르므로 RSS 폴백 서명은 1차 요청 서명과 달라야 함
    assert tok_main != tok_rss
    assert not _verify_v1_hmac(tok_rss, "relay-secret-xyz", target_url)

    # 2. 시크릿 미설정 시 X-Feeder-Token 헤더 미첨부 확인
    monkeypatch.delenv("FEEDER_API_TOKEN", raising=False)
    monkeypatch.delenv("SCRAPER_RELAY_SECRET", raising=False)
    calls.clear()
    await scraper._fetch_via_seoul_relay(target_url)
    assert len(calls) == 2
    assert "X-Feeder-Token" not in calls[0]["headers"]
    assert "X-Feeder-Token" not in calls[1]["headers"]


@pytest.mark.asyncio
async def test_cloud_feeder_rss_1rtt_attaches_hmac_and_ingest_stays_plaintext(monkeypatch):
    import backend.scheduler.cloud_feeder as cf

    monkeypatch.setenv("FEEDER_API_TOKEN", "feeder-hmac-secret")
    monkeypatch.setattr(cf, "IS_CLOUD_ENV", True)
    monkeypatch.setattr(cf, "_preload_existing_urls", AsyncMock())
    monkeypatch.setattr(cf, "_fetch_jsmon_and_hotgusa_enrichment", AsyncMock(return_value={}))
    cf._SEEN_DETAIL_CACHE.clear()

    captured_gets = []
    captured_posts = []

    sample_rss = """<?xml version="1.0" encoding="utf-8"?>
    <rss version="2.0"><channel><item>
      <title><![CDATA[[11번가] 테스트 핫딜 상품 (12,000원/무료)]]></title>
      <link>https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&amp;no=90909</link>
      <description><![CDATA[상세 테스트 본문입니다.]]></description>
    </item></channel></rss>"""

    class FakeResp:
        def __init__(self, status_code, text="", json_data=None):
            self.status_code = status_code
            self.text = text
            self._json = json_data or {}

        def json(self):
            return self._json

    class FakeHttpClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return False

        async def get(self, url, headers=None, **kwargs):
            captured_gets.append({"url": url, "headers": dict(headers or {})})
            if "id%3Dppomppu" in url and "id%3Dppomppu8" not in url and "id%3Dppomppu4" not in url:
                return FakeResp(200, text=sample_rss)
            return FakeResp(200, text="")

        async def post(self, url, json=None, headers=None, **kwargs):
            captured_posts.append({"url": url, "json": json, "headers": dict(headers or {})})
            return FakeResp(200, json_data={"processed": len((json or {}).get("deals", []))})

    async def fake_run(self, url=None):
        return []

    async def fake_get_detail(self, url, title=""):
        return {
            "content_html": "<p>" + ("충분히 긴 상세 본문 내용입니다. " * 10) + "</p>",
            "ecommerce_link": "https://www.11st.co.kr/products/90909",
            "image_url": "https://cdn.ppomppu.co.kr/thumb.jpg",
            "shipping_fee": "무료배송",
            "is_closed": False,
            "price": 12000,
        }

    monkeypatch.setattr(cf.httpx, "AsyncClient", FakeHttpClient)
    from backend.scrapers.base_scraper import AsyncBaseScraper
    monkeypatch.setattr(AsyncBaseScraper, "run", fake_run)
    monkeypatch.setattr(PpomppuScraper, "get_detail", fake_get_detail)

    await cf.scrape_and_push_once()

    rss_relay_gets = [g for g in captured_gets if "scraper-relay" in g["url"] and "id%3Dppomppu" in g["url"]]
    assert len(rss_relay_gets) >= 1
    ppomppu_rss_target = "https://www.ppomppu.co.kr/rss.php?id=ppomppu"
    matched_main = [
        g for g in rss_relay_gets
        if g["url"].endswith(f"url=https%3A%2F%2Fwww.ppomppu.co.kr%2Frss.php%3Fid%3Dppomppu")
    ]
    assert len(matched_main) == 1
    assert _verify_v1_hmac(
        matched_main[0]["headers"].get("X-Feeder-Token", ""),
        "feeder-hmac-secret",
        ppomppu_rss_target,
    )

    # /ingest-batch 전송은 여전히 평문 FEEDER_API_TOKEN 유지
    assert len(captured_posts) >= 1
    assert captured_posts[0]["headers"].get("X-Feeder-Token") == "feeder-hmac-secret"


@pytest.mark.asyncio
async def test_jsmon_enrichment_recovers_ppomppu_cdn_and_gmarket_mobile_without_hotgusa(monkeypatch):
    import backend.scheduler.cloud_feeder as cf
    from backend.scrapers.ppomppu_scraper import PpomppuScraper

    class Resp:
        def __init__(self, status_code, text="", json_data=None, headers=None):
            self.status_code = status_code
            self.text = text
            self._json = json_data or {}
            self.headers = headers or {}

        def json(self):
            return self._json

    jsmon_html = """
    <div class="tgme_widget_message_wrap">
      <div class="tgme_widget_message_text">
        [지마켓] 그래놀라 3팩(13,900원/무료)
        <a href="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=751710">원문</a>
        <a href="https://m.gmarket.co.kr/vi/product/3242140960">구매</a>
      </div>
    </div>
    <div class="tgme_widget_message_wrap">
      <div class="tgme_widget_message_text">
        [네이버] 감귤 4kg(11,900원/무배)
        <a href="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=751709">원문</a>
        <a href="https://smartstore.naver.com/koreafarmers/products/13839774075">구매</a>
      </div>
    </div>
    """

    called_urls = []

    class FakeProbeClient:
        async def get(self, url, **kwargs):
            called_urls.append(url)
            if url == "https://t.me/s/jsmon":
                return Resp(200, text=jsmon_html)
            return Resp(404)

        async def head(self, url, **kwargs):
            called_urls.append(url)
            if "small_751709.jpg" in url:
                return Resp(200, headers={"content-length": "4653"})
            return Resp(404)

    enrichment = await cf._fetch_jsmon_and_hotgusa_enrichment(FakeProbeClient())
    assert not any("hotgusa" in u or "d2w999htn2iva3" in u for u in called_urls)

    gmarket_entry = enrichment["https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=751710"]
    assert gmarket_entry["image_url"] == "https://gdimg.gmarket.co.kr/3242140960/still/600"
    assert gmarket_entry["price"] == 13900
    assert gmarket_entry["shop_name"] == "지마켓"

    naver_entry = enrichment["https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=751709"]
    assert naver_entry["image_url"] == "https://cdn4.ppomppu.co.kr/zboard/data/_thumb/ppomppu/9/small_751709.jpg"
    assert naver_entry["price"] == 11900
    assert naver_entry["shipping_fee"] == "무료배송"

    async with PpomppuScraper(1) as sc:
        assert await sc.fetch_og_image("https://m.gmarket.co.kr/vi/product/3242140960") == "https://gdimg.gmarket.co.kr/3242140960/still/600"
        assert await sc.fetch_og_image("https://newtip.net/click.php?m=gmarket&tu=https%3A%2F%2Fm.gmarket.co.kr%2Fvi%2Fproduct%2F4318086821") == "https://gdimg.gmarket.co.kr/4318086821/still/600"

        class FakeOgResp:
            def __init__(self, status_code, text="", url=""):
                self.status_code = status_code
                self.text = text
                self.url = url

        ua_seen = []

        class FakeOgClient:
            async def get(self, url, headers=None, **kwargs):
                ua = (headers or {}).get("User-Agent", "")
                ua_seen.append(ua)
                if "facebookexternalhit" in ua:
                    return FakeOgResp(200, '<meta property="og:image" content="https://shop-phinf.pstatic.net/2026/hd_product.jpg" />', url)
                return FakeOgResp(490, "", url)

            async def close(self):
                pass

        sc.client = FakeOgClient()
        hd_naver = await sc.fetch_og_image("https://brand.naver.com/ashley/products/5715779766")
        assert hd_naver == "https://shop-phinf.pstatic.net/2026/hd_product.jpg"
        assert any("facebookexternalhit" in u for u in ua_seen)


@pytest.mark.asyncio
async def test_ppomppu_cdn_thumb_naver_bridge_banned_tokens_and_cache_scrub(tmp_path, monkeypatch):
    import json
    from backend.scheduler import cloud_feeder as cf

    # 1. fetch_ppomppu_cdn_thumb: 200 image/jpeg vs 404
    async with PpomppuScraper(1) as sc:
        class FakeHeadResp:
            def __init__(self, status_code, ct="image/jpeg"):
                self.status_code = status_code
                self.headers = {"Content-Type": ct}

        class FakeCdnClient:
            async def head(self, url, headers=None, **kwargs):
                if "ppomppu/4/small_749614.jpg" in url and (headers or {}).get("Referer") == "https://www.ppomppu.co.kr/":
                    return FakeHeadResp(200, "image/jpeg")
                return FakeHeadResp(404, "text/html")

            async def get(self, url, headers=None, **kwargs):
                class FakeR:
                    def __init__(self, status_code, text="", final_url=""):
                        self.status_code = status_code
                        self.text = text
                        self.url = final_url

                if "naver.me/" in url:
                    return FakeR(
                        200,
                        '<meta property="og:image" content="https://shopv.pstatic.net/web/r/_next/static/og/nplus-store/OG_450x235.png" />',
                        "https://app.shopping.naver.com/app-bridge?dst=https%3A%2F%2Fbrand.naver.com%2Fpork%2Fproducts%2F12345",
                    )
                if "brand.naver.com/pork/products/12345" in url:
                    ua = (headers or {}).get("User-Agent", "")
                    if "facebookexternalhit" in ua:
                        return FakeR(
                            200,
                            '<meta property="og:image" content="https://d2w999htn2iva3.cloudfront.net/thumbs/bad.jpg" /><img data-src="https://shop-phinf.pstatic.net/2026/products/pork_hd.jpg" />',
                            url,
                        )
                    return FakeR(429, "", url)
                if "badog.example.com" in url:
                    return FakeR(
                        200,
                        '<meta property="og:image" content="https://search2.kakaocdn.net/argon/600x0_65_wr/JaqiqttJhft" />',
                        url,
                    )
                return FakeR(404, "", url)

            async def close(self):
                pass

        sc.client = FakeCdnClient()
        assert (
            await sc.fetch_ppomppu_cdn_thumb("https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=749614")
            == "https://cdn4.ppomppu.co.kr/zboard/data/_thumb/ppomppu/4/small_749614.jpg"
        )
        assert await sc.fetch_ppomppu_cdn_thumb("https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&no=106807") is None
        assert await sc.fetch_og_image("https://badog.example.com/item") is None
        assert await sc.fetch_og_image("https://naver.me/Fhu9SGqd") == "https://shop-phinf.pstatic.net/2026/products/pork_hd.jpg"

    # 2. _load_local_seen_cache scrubs legacy cloudfront and kakaocdn/argon URLs
    cache_file = tmp_path / "feeder_seen_cache.json"
    cache_file.write_text(
        json.dumps(
            {
                "뽐뿌::https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1": {
                    "image_url": "https://d2w999htn2iva3.cloudfront.net/thumbs/3/1.jpg",
                    "ecommerce_link": "https://www.11st.co.kr/products/1",
                },
                "뽐뿌::https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=2": {
                    "image_url": "https://search2.kakaocdn.net/argon/600x0_65_wr/abc",
                    "ecommerce_link": "https://www.11st.co.kr/products/2",
                },
                "뽐뿌::https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=3": {
                    "image_url": "https://cdn4.ppomppu.co.kr/zboard/data/_thumb/ppomppu/3/small_3.jpg",
                    "ecommerce_link": "https://www.11st.co.kr/products/3",
                },
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setattr(cf, "_CACHE_FILE", str(cache_file))
    cf._SEEN_DETAIL_CACHE.clear()
    cf._load_local_seen_cache()
    assert cf._SEEN_DETAIL_CACHE[("뽐뿌", "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1")]["image_url"] == ""
    assert cf._SEEN_DETAIL_CACHE[("뽐뿌", "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=2")]["image_url"] == ""
    assert cf._SEEN_DETAIL_CACHE[("뽐뿌", "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=3")]["image_url"] == "https://cdn4.ppomppu.co.kr/zboard/data/_thumb/ppomppu/3/small_3.jpg"
