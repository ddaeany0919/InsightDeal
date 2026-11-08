import sys
import os
from pathlib import Path
from unittest.mock import AsyncMock, patch, MagicMock
import pytest

# Ensure project root is in sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import backend.scrapers.base_scraper as base_scraper_mod
from backend.scrapers.base_scraper import AsyncBaseScraper
from backend.scrapers.fmkorea_scraper import FmkoreaScraper
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
from backend.scrapers.ruliweb_scraper import RuliwebScraper
from backend.scrapers.clien_scraper import ClienScraper
from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from backend.scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from backend.scrapers.bbasak_parenting_scraper import BbasakParentingScraper


# Concrete subclass of AsyncBaseScraper for testing base methods
class DummyScraper(AsyncBaseScraper):
    def __init__(self):
        super().__init__("테스트커뮤니티", max_concurrent_requests=2)
        self.list_url = "https://example.com/deals"

    async def parse_list(self, html: str) -> list[dict]:
        return []

    async def get_detail(self, url: str) -> dict:
        return {}


# ---------------------------------------------------------------------------
# 1. BaseScraper: Vercel Seoul Relay direct routing (clien.net, bbasak, ppomppu)
# ---------------------------------------------------------------------------
@pytest.mark.asyncio
async def test_base_scraper_seoul_relay_clien_direct():
    """클라우드 환경에서 clien.net, bbasak.com, ppomppu.co.kr은 서울 릴레이로 직행해야 함"""
    scraper = DummyScraper()
    mock_client = MagicMock()
    scraper.client = mock_client

    with patch.object(base_scraper_mod, "IS_CLOUD_ENV", True):
        with patch.object(scraper, "_fetch_via_seoul_relay", new_callable=AsyncMock) as mock_relay:
            mock_relay.return_value = "<html>Relay Success</html>"

            # 1. clien.net URL
            clien_url = "https://www.clien.net/service/board/jirum"
            html = await scraper.fetch_html(clien_url)
            assert html == "<html>Relay Success</html>"
            mock_relay.assert_awaited_with(clien_url)

            # 2. bbasak.com URL
            mock_relay.reset_mock()
            bbasak_url = "https://bbasak.com/bbs/board.php?bo_table=bbasak2"
            html = await scraper.fetch_html(bbasak_url)
            assert html == "<html>Relay Success</html>"
            mock_relay.assert_awaited_with(bbasak_url)

            # 3. ppomppu.co.kr URL
            mock_relay.reset_mock()
            ppomppu_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4"
            html = await scraper.fetch_html(ppomppu_url)
            assert html == "<html>Relay Success</html>"
            mock_relay.assert_awaited_with(ppomppu_url)

            # 4. Other URL should not call direct relay on line 59
            mock_relay.reset_mock()
            other_url = "https://example.com/other"
            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.text = "<html>Direct OK</html>"
            mock_client.get = AsyncMock(return_value=mock_resp)

            html = await scraper.fetch_html(other_url)
            assert html == "<html>Direct OK</html>"
            mock_relay.assert_not_called()


@pytest.mark.asyncio
async def test_base_scraper_non_cloud_env_does_not_force_relay():
    """로컬 환경(IS_CLOUD_ENV=False)에서는 직행 릴레이를 타지 않고 직접 호출해야 함"""
    scraper = DummyScraper()
    mock_client = MagicMock()
    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.text = "<html>Local OK</html>"
    mock_client.get = AsyncMock(return_value=mock_resp)
    scraper.client = mock_client

    with patch.object(base_scraper_mod, "IS_CLOUD_ENV", False):
        with patch.object(scraper, "_fetch_via_seoul_relay", new_callable=AsyncMock) as mock_relay:
            clien_url = "https://www.clien.net/service/board/jirum"
            html = await scraper.fetch_html(clien_url)
            assert html == "<html>Local OK</html>"
            mock_relay.assert_not_called()


# ---------------------------------------------------------------------------
# 2. PpomppuOverseasScraper: Single URL (ppomppu4), ppomppu8 removed, question filter
# ---------------------------------------------------------------------------
@pytest.mark.asyncio
async def test_ppomppu_overseas_scraper_urls():
    """뽐뿌해외 스크래퍼는 ppomppu4 전용이어야 하며 ppomppu8이 중복 등록되지 않아야 함"""
    scraper = PpomppuOverseasScraper(community_id=10)

    assert scraper.platform_name == "뽐뿌해외"
    assert len(scraper.list_urls) == 1
    assert scraper.list_urls[0] == "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4"
    assert scraper.list_url == "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4"
    assert not any("ppomppu8" in u for u in scraper.list_urls)


@pytest.mark.asyncio
async def test_ppomppu_overseas_scraper_filtering():
    """뽐뿌해외는 제목에 '질문'/'문의'가 들어간 게시글을 필터링해야 함"""
    scraper = PpomppuOverseasScraper(community_id=10)

    rss_content = """<?xml version="1.0" encoding="euc-kr"?>
    <rss version="2.0">
      <channel>
        <item>
          <title><![CDATA[[아마존] 로지텍 MX Master 3S ($79.99/무료)]]></title>
          <link>https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&amp;no=11111</link>
          <description><![CDATA[로지텍 무선 마우스 특가입니다.]]></description>
        </item>
        <item>
          <title><![CDATA[[질문] 아마존 직구 배송기간 문의드립니다]]></title>
          <link>https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&amp;no=11112</link>
          <description><![CDATA[배송이 얼마나 걸릴까요?]]></description>
        </item>
        <item>
          <title><![CDATA[이베이 결제 오류 문의합니다]]></title>
          <link>https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&amp;no=11113</link>
          <description><![CDATA[결제 튕기네요]]></description>
        </item>
      </channel>
    </rss>"""

    deals = await scraper.parse_list(rss_content)
    assert len(deals) == 1
    assert "로지텍 MX Master" in deals[0]["title"]
    assert "질문" not in deals[0]["title"]
    assert "문의" not in deals[0]["title"]


@pytest.mark.asyncio
async def test_ppomppu_overseas_scraper_run_delegation():
    """run() 호출 시 url 지정 및 미지정 시의 정상 위임 확인"""
    scraper = PpomppuOverseasScraper(community_id=10)

    # 1. url 지정 시
    with patch.object(PpomppuScraper, "run", new_callable=AsyncMock) as mock_super_run:
        mock_super_run.return_value = [{"title": "Deal 1", "url": "https://example.com/1"}]
        custom_url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4&page=2"
        res = await scraper.run(custom_url)
        assert len(res) == 1
        mock_super_run.assert_awaited_once_with(custom_url)

    # 2. url 미지정 시
    with patch.object(PpomppuScraper, "run", new_callable=AsyncMock) as mock_super_run:
        mock_super_run.return_value = [{"title": "Deal 2", "url": "https://example.com/2"}]
        res = await scraper.run()
        assert len(res) == 1
        mock_super_run.assert_awaited_once_with(scraper.list_urls[0])


# ---------------------------------------------------------------------------
# 3. BbasakOverseasScraper: Graceful 0-deal handling for low-frequency boards
# ---------------------------------------------------------------------------
@pytest.mark.asyncio
async def test_bbasak_overseas_scraper_graceful_empty_html():
    """빠삭해외 스크래퍼는 빈 HTML이나 공백 수신 시 0건을 에러 없이 반환해야 함"""
    scraper = BbasakOverseasScraper(community_id=8)

    # 빈 문자열
    res1 = await scraper.parse_list("")
    assert res1 == []

    # 공백 문자열
    res2 = await scraper.parse_list("    \n\t   ")
    assert res2 == []

    # None 유사 입력
    res3 = await scraper.parse_list(None)
    assert res3 == []


@pytest.mark.asyncio
async def test_bbasak_overseas_scraper_graceful_no_deals():
    """빠삭해외 스크래퍼는 게시글이 0건인 HTML 수신 시 에러 없이 빈 리스트를 반환해야 함"""
    scraper = BbasakOverseasScraper(community_id=8)

    empty_board_html = """
    <html>
      <body>
        <table class="t1">
          <thead><tr><th>번호</th><th>제목</th><th>작성자</th></tr></thead>
          <tbody>
            <!-- 게시글 없음 -->
          </tbody>
        </table>
      </body>
    </html>
    """

    res = await scraper.parse_list(empty_board_html)
    assert res == []


@pytest.mark.asyncio
async def test_bbasak_overseas_scraper_exception_defense():
    """부모 parse_list에서 예외가 발생하더라도 크래시 없이 0건으로 graceful 처리"""
    scraper = BbasakOverseasScraper(community_id=8)

    with patch("backend.scrapers.bbasak_base_scraper.BbasakBaseScraper.parse_list", side_effect=Exception("Simulated parser failure")):
        res = await scraper.parse_list("<html>Some valid html</html>")
        assert res == []


@pytest.mark.asyncio
async def test_bbasak_overseas_scraper_run_and_detail_graceful():
    """run() 및 get_detail() 에러 발생 시 크래시 없이 graceful 완료"""
    scraper = BbasakOverseasScraper(community_id=8)

    # run 예외 방어
    with patch("backend.scrapers.base_scraper.AsyncBaseScraper.run", side_effect=Exception("Network error")):
        deals = await scraper.run()
        assert deals == []

    # get_detail 예외 방어
    with patch("backend.scrapers.bbasak_base_scraper.BbasakBaseScraper.get_detail", side_effect=Exception("Detail error")):
        detail = await scraper.get_detail("https://bbasak.com/bbs/board.php?bo_table=bbasak2&wr_id=1")
        assert detail == {}


# ---------------------------------------------------------------------------
# 4. Resilience across all 10 scrapers: contracts, empty input defense, mock parse
# ---------------------------------------------------------------------------
ALL_SCRAPER_CLASSES = [
    (FmkoreaScraper, 6, "펨코"),
    (PpomppuScraper, 1, "뽐뿌"),
    (QuasarzoneScraper, 5, "퀘이사존"),
    (AlippomppuScraper, 4, "알리뽐뿌"),
    (PpomppuOverseasScraper, 10, "뽐뿌해외"),
    (RuliwebScraper, 2, "루리웹"),
    (ClienScraper, 3, "클리앙"),
    (BbasakDomesticScraper, 7, "빠삭국내"),
    (BbasakOverseasScraper, 8, "빠삭해외"),
    (BbasakParentingScraper, 9, "빠삭육아"),
]


def test_all_10_scrapers_initialization_contracts():
    """10개 스크래퍼 전수: 정상 초기화 및 계약 필드(platform_name, list_url) 검증"""
    assert len(ALL_SCRAPER_CLASSES) == 10

    for scraper_cls, comm_id, expected_name in ALL_SCRAPER_CLASSES:
        scraper = scraper_cls(community_id=comm_id)
        assert scraper.community_id == comm_id
        assert scraper.platform_name == expected_name
        assert scraper.list_url.startswith("http"), f"{expected_name} list_url invalid: {scraper.list_url}"


@pytest.mark.asyncio
async def test_all_10_scrapers_empty_or_broken_input_resilience():
    """10개 스크래퍼 전수: 빈 문자열 또는 깨진 HTML 입력 시 크래시 없이 안전하게 리스트 반환"""
    broken_html = "<html><body><div><p>Broken tag without closing"

    for scraper_cls, comm_id, name in ALL_SCRAPER_CLASSES:
        scraper = scraper_cls(community_id=comm_id)

        # 1. 빈 문자열 입력
        res_empty = await scraper.parse_list("")
        assert isinstance(res_empty, list), f"{name} failed on empty input"

        # 2. 깨진 HTML 입력
        res_broken = await scraper.parse_list(broken_html)
        assert isinstance(res_broken, list), f"{name} failed on broken HTML"


@pytest.mark.asyncio
async def test_all_10_scrapers_valid_mock_parsing():
    """10개 스크래퍼 전수: 각 커뮤니티별 유효한 HTML/RSS 모의 데이터 파싱 정상 동작 검증"""
    fixtures = {
        "펨코": """
        <ul>
          <li class="li">
            <h3 class="title">
              <a href="/123456789" class="hotdeal_var8">[11번가] 게이밍 마우스 특가 (19,900원 / 무료)</a>
            </h3>
            <div class="hotdeal_info">
              <span>쇼핑몰: <strong class="strong">11번가</strong></span>
              <span>가격: <strong class="strong">19,900원</strong></span>
              <span>배송비: <strong class="strong">무료</strong></span>
            </div>
            <span class="count">조회 1500</span>
          </li>
        </ul>
        """,
        "뽐뿌": """<rss version="2.0">
          <channel>
            <item>
              <title><![CDATA[[지마켓] 오뚜기 진라면 매운맛 40봉 (18,900원/무료)]]></title>
              <link>https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&amp;no=99991</link>
              <description><![CDATA[진라면 40봉 무료배송 핫딜입니다.]]></description>
            </item>
          </channel>
        </rss>""",
        "퀘이사존": """
        <div class="v2-list-row">
          <a class="subject-link" href="/bbs/qb_saleinfo/views/88881">
            <span class="ellipsis-with-reply-cnt">[쿠팡] 삼성전자 990 PRO 2TB (189,000원)</span>
          </a>
          <span class="text-orange">189,000원</span>
        </div>
        """,
        "알리뽐뿌": """<rss version="2.0">
          <channel>
            <item>
              <title><![CDATA[[알리익스프레스] 제우스랩 16인치 포터블 모니터 ($65.40/FS)]]></title>
              <link>https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu8&amp;no=77771</link>
              <description><![CDATA[제우스랩 16인치 휴대용 모니터 특가입니다.]]></description>
            </item>
          </channel>
        </rss>""",
        "뽐뿌해외": """<rss version="2.0">
          <channel>
            <item>
              <title><![CDATA[[아마존] 킨들 페이퍼화이트 16GB ($99.99/직배)]]></title>
              <link>https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu4&amp;no=66661</link>
              <description><![CDATA[킨들 이북 리더기 할인입니다.]]></description>
            </item>
          </channel>
        </rss>""",
        "루리웹": """
        <table class="board_list_table">
          <tbody>
            <tr class="table_body blocktarget">
              <td>
                <a class="subject_link" href="/market/board/1020/read/55551">
                  [네이버] 닌텐도 스위치 OLED (349,000원)
                </a>
              </td>
            </tr>
          </tbody>
        </table>
        """,
        "클리앙": """
        <div>
          <div class="list_item">
            <span class="list_subject">
              <a href="/service/board/jirum/44441">[옥션] CJ 비비고 왕교자 1.4kg 2봉 (15,900원)</a>
            </span>
            <span class="list_symph">10</span>
            <span class="list_hit">1200</span>
          </div>
        </div>
        """,
        "빠삭국내": """
        <table class="t1">
          <tbody>
            <tr>
              <td class="tit">
                <a href="/bbs/board.php?bo_table=bbasak1&amp;wr_id=33331">[지마켓] 코카콜라 제로 190ml 30캔 (14,900원)</a>
              </td>
              <td>1200</td>
            </tr>
          </tbody>
        </table>
        """,
        "빠삭해외": """
        <table class="t1">
          <tbody>
            <tr>
              <td class="tit">
                <a href="/bbs/board.php?bo_table=bbasak2&amp;wr_id=22221">[알리] 샤오미 전동 드라이버 ($15.99)</a>
              </td>
              <td>800</td>
            </tr>
          </tbody>
        </table>
        """,
        "빠삭육아": """
        <table class="t1">
          <tbody>
            <tr>
              <td class="tit">
                <a href="/bbs/board.php?bo_table=bbasak3&amp;wr_id=11111">[쿠팡] 하기스 네이처메이드 4단계 (45,000원)</a>
              </td>
              <td>950</td>
            </tr>
          </tbody>
        </table>
        """,
    }

    for scraper_cls, comm_id, name in ALL_SCRAPER_CLASSES:
        scraper = scraper_cls(community_id=comm_id)
        fixture = fixtures[name]
        deals = await scraper.parse_list(fixture)

        assert len(deals) >= 1, f"{name} failed to parse valid fixture"
        deal = deals[0]
        assert "title" in deal and deal["title"], f"{name} deal title missing"
        assert "url" in deal and deal["url"], f"{name} deal url missing"
        assert "price" in deal, f"{name} deal price missing"
