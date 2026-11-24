# -*- coding: utf-8 -*-
"""
스크래퍼 파서 무결성, 상세 종료 감지, 좀비 딜 자동 만료,
릴레이 업스트림 403 구분 및 cloud_feeder 브릿지 폴백 회귀 테스트.
"""

from datetime import datetime, timedelta, timezone
import os
import sys
from unittest.mock import AsyncMock, MagicMock, patch

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
from backend.scheduler import main as sched_main
from backend.scrapers.base_scraper import AsyncBaseScraper, reset_relay_circuit_breaker
from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from backend.scrapers.clien_scraper import ClienScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.scrapers.ruliweb_scraper import RuliwebScraper


@pytest.mark.asyncio
async def test_quasarzone_parse_list_with_background_style_image_no_re_shadow():
    """span.img-background-wrap style URL 파싱 시 UnboundLocalError(re) 없이 정상 파싱 검증."""
    html = """
    <div class="market-info-type-list">
      <table><tbody>
        <tr class="v2-list-row">
          <td>
            <span class="label">식품</span>
            <span class="img-background-wrap" style="background-image:url('/data/file/test_thumb.jpg')"></span>
            <a class="subject-link" href="/bbs/qb_saleinfo/views/2004999">
              <span class="ellipsis-with-reply-cnt">[네이버] 테스트 생수 2L 12병</span>
            </a>
            <span class="text-orange">9,900원</span>
            <span class="v2-list-row__ship">배송비 무료</span>
          </td>
        </tr>
        <tr class="v2-list-row">
          <td>
            <span class="label">종료</span>
            <span class="img-background-wrap" style="background-image:url('https://cdn.example.com/ended.jpg')"></span>
            <a class="subject-link" href="/bbs/qb_saleinfo/views/2004998">
              <span class="ellipsis-with-reply-cnt">[쿠팡] 마감 테스트 모니터</span>
            </a>
            <span class="text-orange">199,000원</span>
          </td>
        </tr>
      </tbody></table>
    </div>
    """
    sc = QuasarzoneScraper(community_id=5)
    deals = await sc.parse_list(html)
    assert len(deals) == 2
    assert deals[0]["is_closed"] is False
    assert deals[0]["image_url"] == "https://quasarzone.com/data/file/test_thumb.jpg"
    assert deals[1]["is_closed"] is True
    assert deals[1]["image_url"] == "https://cdn.example.com/ended.jpg"


@pytest.mark.asyncio
async def test_clien_ruliweb_bbasak_get_detail_closed_and_active_guard():
    """상세 페이지 제목/배지 종료만 is_closed=True, 본문 단순 '품절' 언급은 is_closed=False 검증."""
    clien = ClienScraper(community_id=3)
    clien_closed_html = """
    <html><body>
      <h3 class="post_subject"><span class="icon_info">품절</span> <span>[쿠팡] 테스트 상품</span></h3>
      <div class="post_article"><p>좋은 가격입니다. 10,900원</p></div>
    </body></html>
    """
    clien_active_html = """
    <html><body>
      <h3 class="post_subject"><span>[쿠팡] 테스트 활성 상품</span></h3>
      <div class="post_article"><p>어제는 품절이었는데 오늘 재입고됐습니다. 10,900원</p></div>
    </body></html>
    """
    with patch.object(clien, "fetch_html", new=AsyncMock(return_value=clien_closed_html)), \
         patch.object(clien, "fetch_og_image", new=AsyncMock(return_value="")):
        det = await clien.get_detail("https://www.clien.net/service/board/jirum/19290001", "[쿠팡] 테스트 상품")
        assert det["is_closed"] is True
    with patch.object(clien, "fetch_html", new=AsyncMock(return_value=clien_active_html)), \
         patch.object(clien, "fetch_og_image", new=AsyncMock(return_value="")):
        det = await clien.get_detail("https://www.clien.net/service/board/jirum/19290002", "[쿠팡] 테스트 활성 상품")
        assert det["is_closed"] is False

    ruli = RuliwebScraper(community_id=2)
    ruli_closed_html = """
    <html><body>
      <h4><span class="subject_inner_text">[종료] [네이버] 테스트 키보드 (1)</span></h4>
      <div class="view_content"><p>29,000원 무료배송</p></div>
    </body></html>
    """
    ruli_active_html = """
    <html><body>
      <h4><span class="subject_inner_text">[네이버] 테스트 정상 키보드 (1)</span></h4>
      <div class="view_content"><p>이전 딜 품절 후 재오픈 29,000원</p></div>
    </body></html>
    """
    with patch.object(ruli, "fetch_html", new=AsyncMock(return_value=ruli_closed_html)), \
         patch.object(ruli, "fetch_og_image", new=AsyncMock(return_value="")):
        det = await ruli.get_detail("https://bbs.ruliweb.com/market/board/1020/read/109901")
        assert det["is_closed"] is True
    with patch.object(ruli, "fetch_html", new=AsyncMock(return_value=ruli_active_html)), \
         patch.object(ruli, "fetch_og_image", new=AsyncMock(return_value="")):
        det = await ruli.get_detail("https://bbs.ruliweb.com/market/board/1020/read/109902")
        assert det["is_closed"] is False

    bbasak = BbasakDomesticScraper(community_id=7)
    bbasak_closed_html = """
    <html><body>
      <h2 id="bo_v_title">[마감] 테스트 핫딜 9,900원</h2>
      <div id="view_content"><p>상세 본문</p></div>
    </body></html>
    """
    bbasak_active_html = """
    <html><body>
      <h2 id="bo_v_title">테스트 진행중 핫딜 9,900원</h2>
      <div id="view_content"><p>화이트 색상은 품절이고 블랙은 판매중</p></div>
    </body></html>
    """
    with patch.object(bbasak, "fetch_html", new=AsyncMock(return_value=bbasak_closed_html)), \
         patch.object(bbasak, "fetch_og_image", new=AsyncMock(return_value="")):
        det = await bbasak.get_detail("https://bbasak.com/bbs/board.php?bo_table=bbasak1&wr_id=19991")
        assert det["is_closed"] is True
    with patch.object(bbasak, "fetch_html", new=AsyncMock(return_value=bbasak_active_html)), \
         patch.object(bbasak, "fetch_og_image", new=AsyncMock(return_value="")):
        det = await bbasak.get_detail("https://bbasak.com/bbs/board.php?bo_table=bbasak1&wr_id=19992")
        assert det["is_closed"] is False

    qz = QuasarzoneScraper(community_id=5)
    qz_active_with_bottom_blind_html = """
    <html>
      <head><title>정상 딜 제목 > 핫딜 | 퀘이사존</title></head>
      <body>
        <div class="market-info-view-top"><h1 class="title">정상 딜 제목</h1></div>
        <div class="view-content"><p>정상 본문 15,900원</p></div>
        <div class="board-list">
          <a class="subject-link">블라인드 처리된 글입니다.</a>
        </div>
      </body>
    </html>
    """
    with patch.object(qz, "fetch_html", new=AsyncMock(return_value=qz_active_with_bottom_blind_html)), \
         patch.object(qz, "fetch_og_image", new=AsyncMock(return_value="")):
        det = await qz.get_detail("https://quasarzone.com/bbs/qb_saleinfo/views/2004999", "정상 딜 제목")
        assert det["is_closed"] is False


def test_scheduler_detect_closed_soup_and_auto_expire(tmp_path, monkeypatch):
    """뽐뿌 EUC-KR/클리앙/루리웹/빠삭 종료 마크업 감지 및 14일 경과 딜 자동 만료 검증."""
    ppomppu_euc = (
        '<html><head><title>뽐뿌:테스트</title></head>'
        '<body><div id="topTitle"><span style="text-decoration:line-through">테스트 종료상품</span></div></body></html>'
    ).encode("euc-kr")
    assert sched_main._detect_closed_soup_sync(ppomppu_euc, "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1") is True

    clien_b = '<html><head><title>클리앙</title></head><body><h3 class="post_subject"><span class="icon_info">품절</span> 글</h3></body></html>'.encode("utf-8")
    assert sched_main._detect_closed_soup_sync(clien_b, "https://www.clien.net/service/board/jirum/1") is True

    ruli_b = '<html><head><title>루리웹</title></head><body><span class="subject_inner_text">[종료] 글</span></body></html>'.encode("utf-8")
    assert sched_main._detect_closed_soup_sync(ruli_b, "https://bbs.ruliweb.com/market/board/1020/read/1") is True

    bbasak_b = '<html><head><title>빠삭</title></head><body><h2 id="bo_v_title">[마감] 글</h2></body></html>'.encode("utf-8")
    assert sched_main._detect_closed_soup_sync(bbasak_b, "https://bbasak.com/bbs/board.php?bo_table=bbasak1&wr_id=1") is True

    active_b = '<html><head><title>정상글</title></head><body><h2 id="bo_v_title">정상 핫딜</h2></body></html>'.encode("utf-8")
    assert sched_main._detect_closed_soup_sync(active_b, "https://bbasak.com/bbs/board.php?bo_table=bbasak1&wr_id=2") is False

    # SQLite 격리 DB에서 14일 초과 미갱신 딜만 자동 마감되는지 검증
    db_file = tmp_path / "expire_test.db"
    engine = create_engine(f"sqlite:///{db_file}", connect_args={"check_same_thread": False})
    TestingSession = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    Base.metadata.create_all(bind=engine)
    monkeypatch.setattr(sched_main, "SessionLocal", TestingSession)

    now = datetime.now(timezone.utc).replace(tzinfo=None)
    with TestingSession() as db:
        comm = Community(name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr")
        db.add(comm)
        db.flush()
        old_deal = Deal(
            source_community_id=comm.id,
            title="오래된 좀비 딜",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1001",
            is_closed=False,
            indexed_at=now - timedelta(days=20),
        )
        fresh_deal = Deal(
            source_community_id=comm.id,
            title="최신 활성 딜",
            post_link="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1002",
            is_closed=False,
            indexed_at=now - timedelta(days=2),
        )
        db.add_all([old_deal, fresh_deal])
        db.commit()

    expired = sched_main._auto_expire_stale_deals_sync(max_age_days=14)
    assert expired == 1

    with TestingSession() as db:
        rows = {d.post_link: d.is_closed for d in db.query(Deal).all()}
        assert rows["https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1001"] is True
        assert rows["https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1002"] is False


@pytest.mark.asyncio
async def test_relay_upstream_403_logs_distinction_and_host_cooldown(caplog):
    """X-Relay-Upstream: 1 헤더 동반 403은 릴레이 인증 오류가 아닌 업스트림 차단으로 로깅 검증."""
    class _StubScraper(AsyncBaseScraper):
        def parse_list(self, html):
            return []
        async def get_detail(self, url, title=""):
            return {}

    reset_relay_circuit_breaker()
    sc = _StubScraper("뽐뿌테스트")
    target = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu&page=2"
    mock_resp = MagicMock()
    mock_resp.status_code = 403
    mock_resp.text = "Forbidden"
    mock_resp.headers = {"x-relay-upstream": "1"}

    with caplog.at_level("WARNING"), \
         patch("httpx.AsyncClient.get", new=AsyncMock(return_value=mock_resp)), \
         patch("backend.scrapers.base_scraper.get_scraper_relay_urls", return_value=["https://eolmande.pages.dev/api/scraper-relay"]):
        html = await sc._fetch_via_seoul_relay(target)
        assert html is None
    assert "업스트림 403 차단" in caplog.text
    assert "인증/설정 오류" not in caplog.text
    reset_relay_circuit_breaker()


def test_cloud_feeder_byte_parity():
    """scheduler/cloud_feeder.py와 scripts/cloud_feeder.py가 바이트 단위로 동일한지 검증."""
    p1 = os.path.join(_BACKEND_DIR, "scheduler", "cloud_feeder.py")
    p2 = os.path.join(_BACKEND_DIR, "scripts", "cloud_feeder.py")
    assert open(p1, "rb").read() == open(p2, "rb").read()
