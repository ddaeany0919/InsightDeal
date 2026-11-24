# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_base_scraper_image_filter.py

AsyncBaseScraper.fetch_og_image() / _is_valid_product_img() 이미지 필터링 회귀 테스트:
1. 서린컴퓨터(25827번 딜) 상세 페이지 HTML 패턴에서 og:image(logo.png),
   /data/design/chuchyn_*.png, /skin/shop/basic/customizing/sns_blog_v3.png,
   /skin/shop/basic/images/icon/bnr_prev.png 등을 모두 배제하고
   실제 상품 이미지(https://seorincomputer.co.kr/data/pimg/163/163082_600.jpg)를 선택하는지 검증
2. 추가된 6개 bad_tokens('skin/', 'customizing/', 'sns_', 'bnr_', 'design/', 'popup/')
   각각의 거부 동작을 메타 태그와 본문 <img> 순회 양쪽에서 외부 네트워크 없이 검증
"""

import os
import sys
from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from backend.scrapers.base_scraper import AsyncBaseScraper  # noqa: E402


class _DummyImageFilterScraper(AsyncBaseScraper):
    def __init__(self):
        super().__init__("이미지필터테스트", max_concurrent_requests=2)

    async def parse_list(self, html: str) -> list[dict]:
        return []

    async def get_detail(self, url: str) -> dict | None:
        return None


def _make_mock_html_response(url: str, html: str, status_code: int = 200) -> MagicMock:
    resp = MagicMock()
    resp.status_code = status_code
    resp.url = url
    resp.text = html
    return resp


@pytest.mark.asyncio
async def test_fetch_og_image_seorincomputer_rejects_sns_and_skin_icons_and_picks_product_image(
    monkeypatch,
):
    target_url = "https://seorincomputer.co.kr/shop/system_detail.html?pd_no=163082"
    seorin_html = """
    <html>
      <head>
        <meta property="og:image" content="/skin/shop/basic/customizing/logo.png">
      </head>
      <body>
        <img src="/data/design/chuchyn_51_1.png" alt="추천">
        <img src="/skin/shop/basic/customizing/sns_blog_v3.png" alt="블로그">
        <img src="/skin/shop/basic/images/icon/bnr_prev.png" alt="이전배너">
        <img src="/data/pimg/163/163082_600.jpg" alt="9850X3D + Astral RTX 5090">
      </body>
    </html>
    """

    async def forbid_external_httpx_get(*args, **kwargs):
        raise AssertionError("외부 검색 폴백 httpx 네트워크 요청이 발생하면 안 됩니다.")

    monkeypatch.setattr(httpx.AsyncClient, "get", forbid_external_httpx_get)

    scraper = _DummyImageFilterScraper()
    mock_client = MagicMock()
    mock_client.get = AsyncMock(return_value=_make_mock_html_response(target_url, seorin_html))
    scraper.client = mock_client

    selected = await scraper.fetch_og_image(
        target_url,
        fallback_title="9850X3D + Astral RTX 5090 + 2TB + 48G",
    )

    assert mock_client.get.await_count == 1
    assert selected == "https://seorincomputer.co.kr/data/pimg/163/163082_600.jpg"
    assert "sns_blog_v3.png" not in selected


@pytest.mark.parametrize(
    "token,isolated_bad_path",
    [
        ("skin/", "/skin/shop/basic/banner_01.jpg"),
        ("customizing/", "/shop/customizing/promo_01.jpg"),
        ("sns_", "/shop/sns_blog_v3.png"),
        ("bnr_", "/shop/bnr_prev.png"),
        ("design/", "/shop/design/chuchyn_51_1.png"),
        ("popup/", "/shop/popup/event_notice.jpg"),
    ],
)
@pytest.mark.asyncio
async def test_fetch_og_image_rejects_each_new_bad_token(
    monkeypatch, token: str, isolated_bad_path: str
):
    """
    추가된 6개 bad_tokens 각각이 단독으로 존재할 때도 거부되는지 검증한다.
    isolated_bad_path에는 'shop' 키워드를 포함시켜 L665 키워드 가드는 통과하되
    오직 _is_valid_product_img()의 해당 token 검사로만 거부됨을 증명한다.
    """
    assert token in isolated_bad_path.lower()

    async def forbid_external_httpx_get(*args, **kwargs):
        raise AssertionError("외부 검색 폴백 httpx 네트워크 요청이 발생하면 안 됩니다.")

    monkeypatch.setattr(httpx.AsyncClient, "get", forbid_external_httpx_get)

    target_url = "https://seorincomputer.co.kr/shop/system_detail.html?pd_no=163082"

    # 1) meta og:image 및 본문 <img> 모두 bad token URL만 있을 때 -> None 반환(거부)
    only_bad_html = f"""
    <html>
      <head>
        <meta property="og:image" content="{isolated_bad_path}">
      </head>
      <body>
        <img src="{isolated_bad_path}" alt="장식 이미지">
      </body>
    </html>
    """
    scraper = _DummyImageFilterScraper()
    mock_client = MagicMock()
    mock_client.get = AsyncMock(return_value=_make_mock_html_response(target_url, only_bad_html))
    scraper.client = mock_client

    rejected = await scraper.fetch_og_image(target_url, fallback_title="")
    assert rejected is None, f"토큰 {token!r}이 거부되지 않았습니다: {rejected}"

    # 2) bad token 이미지 뒤에 실제 상품 이미지가 있으면 건너뛰고 실제 상품 이미지를 채택
    with_product_html = f"""
    <html>
      <head>
        <meta property="og:image" content="{isolated_bad_path}">
      </head>
      <body>
        <img src="{isolated_bad_path}" alt="장식 이미지">
        <img src="/data/pimg/163/163082_600.jpg" alt="실제 상품">
      </body>
    </html>
    """
    mock_client.get = AsyncMock(return_value=_make_mock_html_response(target_url, with_product_html))

    selected = await scraper.fetch_og_image(target_url, fallback_title="")
    assert selected == "https://seorincomputer.co.kr/data/pimg/163/163082_600.jpg"


@pytest.mark.asyncio
async def test_fetch_og_image_never_uses_daum_naver_bing_web_search_guess(monkeypatch):
    """스마트스토어 429 등 직접 파싱 실패 시 다음 w=tot / 네이버 / 빙 웹검색 추측을 0회 호출하고 None 반환한다."""

    async def forbid_external_httpx_get(*args, **kwargs):
        raise AssertionError("외부 검색(daum/naver/bing) httpx 요청이 절대 발생하면 안 됩니다.")

    monkeypatch.setattr(httpx.AsyncClient, "get", forbid_external_httpx_get)

    scraper = _DummyImageFilterScraper()
    mock_client = MagicMock()
    mock_client.get = AsyncMock(
        return_value=_make_mock_html_response(
            "https://smartstore.naver.com/mobilelounge/products/803834583",
            "<html><title>에러페이지</title></html>",
            status_code=429,
        )
    )
    scraper.client = mock_client

    selected = await scraper.fetch_og_image(
        "https://smartstore.naver.com/mobilelounge/products/803834583",
        fallback_title="벨루가 퓨어웨이브 맥세이프 홀더 베이스",
    )
    assert selected is None


@pytest.mark.asyncio
async def test_fetch_og_image_bad_tokens_match_case_insensitively(monkeypatch):
    async def forbid_external_httpx_get(*args, **kwargs):
        raise AssertionError("외부 검색 폴백 httpx 네트워크 요청이 발생하면 안 됩니다.")

    monkeypatch.setattr(httpx.AsyncClient, "get", forbid_external_httpx_get)

    target_url = "https://seorincomputer.co.kr/shop/system_detail.html?pd_no=163082"
    mixed_case_html = """
    <html>
      <head>
        <meta property="og:image" content="/SKIN/shop/basic/CUSTOMIZING/SNS_BLOG_V3.PNG">
      </head>
      <body>
        <img src="/shop/DESIGN/chuchyn_01.jpg">
        <img src="/shop/POPUP/notice_01.png">
        <img src="/shop/BNR_top.png">
        <img src="/data/pimg/163/163082_600.jpg">
      </body>
    </html>
    """

    scraper = _DummyImageFilterScraper()
    mock_client = MagicMock()
    mock_client.get = AsyncMock(return_value=_make_mock_html_response(target_url, mixed_case_html))
    scraper.client = mock_client

    selected = await scraper.fetch_og_image(target_url, fallback_title="")
    assert selected == "https://seorincomputer.co.kr/data/pimg/163/163082_600.jpg"


@pytest.mark.asyncio
async def test_fetch_og_image_never_uses_daum_naver_bing_web_search_guess(monkeypatch):
    """스마트스토어 429 등 직접 파싱 실패 시 다음 w=tot / 네이버 / 빙 웹검색 추측을 0회 호출하고 None 반환한다."""

    async def forbid_external_httpx_get(*args, **kwargs):
        raise AssertionError("외부 검색(daum/naver/bing) httpx 요청이 절대 발생하면 안 됩니다.")

    monkeypatch.setattr(httpx.AsyncClient, "get", forbid_external_httpx_get)

    scraper = _DummyImageFilterScraper()
    mock_client = MagicMock()
    mock_client.get = AsyncMock(
        return_value=_make_mock_html_response(
            "https://smartstore.naver.com/mobilelounge/products/803834583",
            "<html><title>에러페이지</title></html>",
            status_code=429,
        )
    )
    scraper.client = mock_client

    selected = await scraper.fetch_og_image(
        "https://smartstore.naver.com/mobilelounge/products/803834583",
        fallback_title="벨루가 퓨어웨이브 맥세이프 홀더 베이스",
    )
    assert selected is None
