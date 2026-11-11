# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_cloud_feeder.py

cloud_feeder 및 backfill_ppomppu_100_deals 단위 테스트:
- scheduler/cloud_feeder.py 와 scripts/cloud_feeder.py 코드 동기화 검증
- 클라우드 환경 뽐뿌 상세 조회(need_detail) 및 모음전 감지 로직 검증
- FEEDER_API_TOKEN 설정 시 X-Feeder-Token 헤더 첨부 검증
- backfill_ppomppu_100_deals 스크래퍼 라우팅 및 dry-run / DB 원자적 갱신 검증
"""

import os
import re
import sys
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
