# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_keyword_alert_filters.py

3순위 키워드 알림 고도화 회귀 테스트:
- 제외어('-콜라겐'), 상한 금액(':20000'), 최소 금액('>=700000', 'min:700000'), 인기 딜만('+인기') 조합 필터링
- 외화(USD) 환산 후 최소 금액 비교
"""

import os
import sys
from types import SimpleNamespace

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from backend.services.push_worker import _match_smart_keyword  # noqa: E402


def _make_deal(
    title: str,
    shop_name: str = "쿠팡",
    category: str = "음식",
    currency: str = "KRW",
    honey_score: int = 10,
    like_count: int = 0,
    is_super_hotdeal: bool = False,
):
    return SimpleNamespace(
        title=title,
        shop_name=shop_name,
        category=category,
        currency=currency,
        base_product_name=title,
        search_keywords="",
        options_data=None,
        honey_score=honey_score,
        like_count=like_count,
        is_super_hotdeal=is_super_hotdeal,
    )


def test_keyword_filter_exclude_max_price_and_only_hot():
    expr = "콜라 -콜라겐 -젤리 :20000 +인기"

    # 1. 제외어 포함 시 차단
    d_collagen = _make_deal("[쿠팡] 에버콜라겐 타임 30포", honey_score=100)
    assert _match_smart_keyword(expr, d_collagen, 18900)[0] is False

    # 2. 상한 초과 시 차단
    d_expensive = _make_deal("[11번가] 코카콜라 제로 48캔", honey_score=100)
    assert _match_smart_keyword(expr, d_expensive, 36900)[0] is False

    # 3. 일반 딜(인기 아님) 차단
    d_normal = _make_deal("[오늘의집] 펩시 콜라 제로 라임 24캔", honey_score=20, like_count=1)
    assert _match_smart_keyword(expr, d_normal, 14900)[0] is False

    # 4. 모든 조건 충족 + 인기 딜 통과 및 display_name 포맷 확인
    d_hot = _make_deal("[오늘의집] 펩시 콜라 제로 라임 24캔", honey_score=90, like_count=12)
    matched, display = _match_smart_keyword(expr, d_hot, 14900)
    assert matched is True
    assert "20,000원 이하" in display
    assert "인기" in display


def test_keyword_filter_min_price_and_usd_conversion():
    expr_krw = "아이폰 -케이스 -필름 >=700000"

    d_case = _make_deal("[알리] 아이폰 17 투명 케이스", category="휴대폰", currency="KRW")
    assert _match_smart_keyword(expr_krw, d_case, 2900)[0] is False

    d_cheap_accessory = _make_deal("[쿠팡] 아이폰 호환 무선충전 거치대", category="휴대폰", currency="KRW")
    assert _match_smart_keyword(expr_krw, d_cheap_accessory, 59000)[0] is False

    d_phone = _make_deal("[쿠팡] 아이폰 17 256GB 자급제", category="휴대폰", currency="KRW")
    matched, display = _match_smart_keyword(expr_krw, d_phone, 1120000)
    assert matched is True
    assert "700,000원 이상" in display

    # USD 센트 단위($600 -> 60000 -> 약 84만원)도 >=700000 통과해야 함
    d_usd_phone = _make_deal("[아마존] 아이폰 17 언락 $600", category="휴대폰", currency="USD")
    assert _match_smart_keyword("아이폰 min:700000", d_usd_phone, 60000)[0] is True
