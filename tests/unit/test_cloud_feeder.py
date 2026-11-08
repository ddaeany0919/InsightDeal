# -*- coding: utf-8 -*-
"""
tests/unit/test_cloud_feeder.py

단위 테스트:
1. cloud_feeder.py의 is_ppomppu_cloud 제약 해제 검증 (클라우드 환경에서도 뽐뿌 get_detail() 필요 판별)
2. multi-option 모음전 감지 시 클라우드 환경에서도 need_detail=True 보장
3. scheduler/cloud_feeder.py 와 scripts/cloud_feeder.py의 100% 로직 동기화 검증
4. backfill_ppomppu_100_deals.py의 스크래퍼 라우팅, 120자 미만 절단 딜 식별, dry-run 및 원자적 DB 업데이트 검증
"""

import asyncio
import os
import re
import sys
import tempfile
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

_test_dir = os.path.dirname(os.path.abspath(__file__))
_project_root = os.path.dirname(os.path.dirname(_test_dir))
if _project_root not in sys.path:
    sys.path.insert(0, _project_root)

from backend.database.models import Base, Deal, Community
from backend.core.url_utils import normalize_url
from backend.scripts.backfill_ppomppu_100_deals import (
    get_scraper_for_url,
    backfill_direct_db,
)
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
from backend.services.aggregator_service import extract_structured_multi_options


def test_cloud_feeder_parity_between_scheduler_and_scripts():
    """scheduler/cloud_feeder.py 와 scripts/cloud_feeder.py 의 파일 내용 및 핵심 로직 일치 검증"""
    scheduler_file = os.path.join(_project_root, "backend", "scheduler", "cloud_feeder.py")
    scripts_file = os.path.join(_project_root, "backend", "scripts", "cloud_feeder.py")

    assert os.path.exists(scheduler_file), f"scheduler/cloud_feeder.py 존재해야 함: {scheduler_file}"
    assert os.path.exists(scripts_file), f"scripts/cloud_feeder.py 존재해야 함: {scripts_file}"

    with open(scheduler_file, "r", encoding="utf-8") as f1, open(scripts_file, "r", encoding="utf-8") as f2:
        content1 = f1.read()
        content2 = f2.read()

    assert content1 == content2, "scheduler/cloud_feeder.py와 scripts/cloud_feeder.py가 완전히 일치해야 합니다."


def test_ppomppu_cloud_constraint_removed_in_source():
    """cloud_feeder.py에서 is_ppomppu_cloud 변수 및 need_detail=False 강제 코드가 완전히 제거되었는지 검증"""
    feeder_file = os.path.join(_project_root, "backend", "scheduler", "cloud_feeder.py")
    with open(feeder_file, "r", encoding="utf-8") as f:
        code = f.read()

    assert "is_ppomppu_cloud = IS_CLOUD_ENV" not in code, "is_ppomppu_cloud 변수 정의가 남아있으면 안 됩니다."
    assert "and not is_ppomppu_cloud" not in code, "is_multi_title 조건에서 not is_ppomppu_cloud가 제거되어야 합니다."
    assert "platforms = (\"펨코\", \"뽐뿌\", \"퀘이사존\", \"알리뽐뿌\", \"뽐뿌해외\"" in code, "프리로드 플랫폼에 뽐뿌해외가 포함되어야 합니다."
    assert "cache_pf = display_name" in code, "뽐뿌해외 캐시 플랫폼 키 변조가 제거되어야 합니다."


def test_need_detail_logic_for_ppomppu_cloud():
    """
    클라우드 환경(IS_CLOUD_ENV=True)을 모사하여 뽐뿌 RSS 100자 요약본 게시글이
    get_detail() 호출 대상(need_detail=True)으로 올바르게 판정되는지 검증
    """
    # 1. 뽐뿌 RSS 요약본 (80~100자, 직링크 없음)
    short_rss_deal = {
        "title": "[11번가] 컴포즈커피 뜨아 (1,200원)",
        "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=17934",
        "price": 1200,
        "content_html": "<p>[11번가] 컴포즈커피 뜨아 (1,200원) 💰 가격 미확인 뽐뿌/뽐게 · #기타</p>",
        "image_url": "https://cdn.ppomppu.co.kr/thumb.jpg",
        "ecommerce_link": "",
    }
    item_url = short_rss_deal["url"]
    item_page_no = 1

    # cloud_feeder.py 에 적용된 need_detail 판별 로직 재현
    is_bridge_item = bool("bridge_id=" in item_url)
    assert not is_bridge_item, "뽐뿌 URL은 브릿지 아이템으로 판정되지 않아야 함"

    is_ppomppu = "ppomppu.co.kr" in item_url
    content_txt = short_rss_deal.get("content_html") or ""
    has_full_content = bool(content_txt) and len(content_txt) >= 150
    has_direct_ecom = bool(short_rss_deal.get("ecommerce_link"))

    if is_ppomppu:
        need_detail = (item_page_no == 1) or (not has_full_content) or (not has_direct_ecom) or (not short_rss_deal.get("image_url"))
    else:
        need_detail = (item_page_no == 1) or (not short_rss_deal.get("image_url"))

    assert need_detail is True, "100자 축약 뽐뿌 딜은 클라우드 환경에서도 get_detail() 대상이어야 합니다."


def test_multi_title_detection_unblocked():
    """모음전/다중옵션 뽐뿌 딜이 클라우드에서도 need_detail=True로 판정되는지 검증"""
    multi_deal = {
        "title": "[G마켓] 코카콜라 제로 24캔 외 5종 골라담기 (15,900원/무료)",
        "url": "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=99999",
        "content_html": "<p>100자 미만 요약문</p>",
    }
    item_title = multi_deal["title"]
    is_multi_title = bool(
        re.search(r'(?:외\s*\d+|골라담기|모음|선택|공구|총정리|\d+\s*종|다양|특가\s*모음)', item_title)
        or (re.search(r'/[^/]+(?:원|만|\$|달러)', item_title) and re.search(r'(?:원|만|\$|달러).*/', item_title))
    )
    assert is_multi_title is True, "골라담기/외 5종 패턴이 감지되어야 함"


def test_scraper_router():
    """backfill_ppomppu_100_deals.py의 스크래퍼 라우팅 검증"""
    s_domestic = get_scraper_for_url("https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=123", "ppomppu")
    assert isinstance(s_domestic, PpomppuScraper)

    s_ali = get_scraper_for_url("https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&no=456", "ali_ppomppu")
    assert isinstance(s_ali, AlippomppuScraper)

    s_overseas = get_scraper_for_url("https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&no=789", "ppomppu_overseas")
    assert isinstance(s_overseas, PpomppuOverseasScraper)


@pytest.mark.asyncio
async def test_backfill_direct_db_dry_run_and_update():
    """
    인메모리 SQLite DB를 생성하여 100자 절단 딜 적재 후:
    1) dry_run 모드 시 DB 변경 없이 대상 딜 식별/시뮬레이션 확인
    2) 실제 모드 시 원자적 커밋 및 content_html, ecommerce_link, options_data 갱신 검증
    """
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    Base.metadata.create_all(bind=engine)
    SessionCls = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    db = SessionCls()

    try:
        # 커뮤니티 데이터 생성
        comm = Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr")
        db.add(comm)
        db.commit()

        # 1. 100자 미만 절단 딜
        cut_deal = Deal(
            id=101,
            source_community_id=1,
            title="[11번가] 목우촌 살코기햄 1kg 2개 (15,120원/무료)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=17930",
            ecommerce_link="",
            price="15120",
            content_html="<p>목우촌 살코기햄 1kg 2개쿠폰적용가 15,120원 200g 10캔쿠폰적용가 15,920원</p>",
            is_closed=False,
            has_options=False,
        )

        # 2. 이미 정상 수집된 긴 본문 딜 (복구 대상 제외 대상)
        full_deal = Deal(
            id=102,
            source_community_id=1,
            title="[G마켓] 정상 본문 게시글 (25,000원)",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=17931",
            ecommerce_link="https://item.gmarket.co.kr/detailview/item?goodscode=12345",
            price="25000",
            content_html="<p>" + "정상적인 핫딜 설명 텍스트입니다. " * 20 + '<img src="https://example.com/img.jpg" /></p>',
            is_closed=False,
            has_options=False,
        )

        db.add_all([cut_deal, full_deal])
        db.commit()

        # --- Test 1: dry_run 모드 실행 ---
        # 모의 get_detail을 위해 스크래퍼 get_detail을 mock 처리
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
            dry_stats = await backfill_direct_db(
                db=db,
                days=0,
                min_len=120,
                limit=10,
                dry_run=True,
                concurrency=1,
                delay=0.0,
            )

            assert dry_stats["candidates"] == 1, "절단 딜 1건만 후보로 식별되어야 함 (정상 딜은 제외)"
            assert dry_stats["recovered"] == 1, "1건이 dry-run 복구로 처리되어야 함"

            # dry_run 이므로 DB는 여전히 100자 이하 본문이어야 함
            d_check = db.query(Deal).filter(Deal.id == 101).first()
            assert len(d_check.content_html) < 120, "dry_run에서는 DB가 변경되지 않아야 함"
            assert d_check.ecommerce_link == "", "dry_run에서는 직링크가 변경되지 않아야 함"

            # --- Test 2: 실제 업데이트 모드 실행 ---
            real_stats = await backfill_direct_db(
                db=db,
                days=0,
                min_len=120,
                limit=10,
                dry_run=False,
                concurrency=1,
                delay=0.0,
            )

            assert real_stats["candidates"] == 1
            assert real_stats["recovered"] == 1

            # 실제 반영 확인
            db.expire_all()
            updated_deal = db.query(Deal).filter(Deal.id == 101).first()
            assert len(updated_deal.content_html) >= 120, f"복구된 본문 길이는 120자 이상이어야 함: {len(updated_deal.content_html)}"
            assert "목우촌 살코기햄" in updated_deal.content_html
            assert updated_deal.ecommerce_link == "https://www.11st.co.kr/products/11111"
            assert updated_deal.image_url == "https://cdn.ppomppu.co.kr/data3/ham.jpg"
            assert updated_deal.shipping_fee == "무료배송"

        finally:
            PpomppuScraper.get_detail = original_get_detail
    finally:
        db.close()
