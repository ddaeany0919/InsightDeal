# -*- coding: utf-8 -*-
"""
tests/unit/test_openapi_cross_platform_contract.py

Gate 2: OpenAPI 스펙 <-> Web(frontend-web/) <-> Android(app/) 교차 계약 자동 검증.
- FastAPI app.openapi() 필수 20+ 경로/메서드 및 보안 헤더(X-Device-Token, X-Admin-Key) 검증
- X-Feeder-Token HMAC(v1.<ts>.<hex>, 300s, ${ts}:${target_url}) 백엔드/릴레이 정합
- 15종 표준 카테고리 & 10종 표준 출처 3자(Backend/Web/Android) 순서·키·라벨 1:1 정합
- 1-RTT 요약 필, 이중 키(snake_case + camelCase), has_more, honey_score >= 100 3자 정합
- 3대 표준 UX 카피, 포트 8080, turbopack: {}, --webpack, 금지 패턴 부재 검증
"""

import os
import re
import sys

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_PROJECT_ROOT = os.path.dirname(os.path.dirname(_TEST_DIR))
if _PROJECT_ROOT not in sys.path:
    sys.path.insert(0, _PROJECT_ROOT)

os.environ.setdefault("ENABLE_SCHEDULER", "false")
os.environ.setdefault("DATABASE_URL", "sqlite:///:memory:")
os.environ.setdefault("ENV", "development")

from backend.core.categories import STANDARD_CATEGORIES, STANDARD_SOURCES  # noqa: E402
from backend.main import app  # noqa: E402


def _read_text(rel_path: str) -> str:
    abs_path = os.path.join(_PROJECT_ROOT, rel_path)
    with open(abs_path, "r", encoding="utf-8") as f:
        return f.read()


def test_openapi_required_p0_paths_and_methods():
    spec = app.openapi()
    paths = spec["paths"]

    required_endpoints = {
        "/api/health": {"get"},
        "/api/push/device": {"post"},
        "/api/push/dnd": {"patch"},
        "/api/push-public-key": {"get"},
        "/api/push/keywords": {"get", "post", "delete"},
        "/api/push/keywords/toggle": {"post"},
        "/api/push/register-web": {"post", "delete"},
        "/api/meta/categories": {"get"},
        "/api/community/hot-deals": {"get"},
        "/api/community/deals/{deal_id}": {"get"},
        "/api/community/deals/{deal_id}/history": {"get"},
        "/api/community/deals/{deal_id}/votes": {"get", "post"},
        "/api/community/deals/{deal_id}/comments": {"get", "post"},
        "/api/community/deals/{deal_id}/comments/{comment_id}": {"delete"},
        "/api/community/force-scrape": {"post"},
        "/api/community/popular-keywords": {"get"},
        "/api/proxy-image": {"get"},
        "/api/image-proxy": {"get"},
        "/api/community/proxy-image": {"get"},
        "/api/wishlist/": {"get"},
        "/api/wishlist/item-alert": {"post"},
        "/api/wishlist/from-keyword": {"post"},
        "/api/wishlist/{wishlist_id}": {"patch", "delete"},
        "/api/users/notifications": {"get", "post"},
        "/api/users/notifications/{alert_id}": {"delete"},
        "/api/coupang/track-click": {"post"},
    }

    for path, expected_methods in required_endpoints.items():
        assert path in paths, f"OpenAPI 필수 경로 누락: {path}"
        actual_methods = {m.lower() for m in paths[path].keys()}
        missing = expected_methods - actual_methods
        assert not missing, f"OpenAPI {path} 메서드 누락: {missing}"


def test_openapi_security_headers_and_hmac_contracts():
    spec = app.openapi()
    paths = spec["paths"]

    device_token_ops = [
        ("/api/push/dnd", "patch"),
        ("/api/community/deals/{deal_id}/votes", "post"),
        ("/api/community/deals/{deal_id}/comments", "post"),
        ("/api/community/deals/{deal_id}/comments/{comment_id}", "delete"),
        ("/api/wishlist/", "get"),
        ("/api/wishlist/{wishlist_id}", "patch"),
        ("/api/wishlist/{wishlist_id}", "delete"),
        ("/api/users/notifications", "get"),
        ("/api/users/notifications", "post"),
    ]
    for path, method in device_token_ops:
        op = paths[path][method]
        param_names = {
            (p.get("name") or "").lower(): p.get("in")
            for p in op.get("parameters", [])
        }
        assert param_names.get("x-device-token") == "header", (
            f"OpenAPI {method.upper()} {path} X-Device-Token 헤더 파라미터 누락"
        )

    force_scrape_params = {
        (p.get("name") or "").lower(): p.get("in")
        for p in paths["/api/community/force-scrape"]["post"].get("parameters", [])
    }
    assert force_scrape_params.get("x-admin-key") == "header", (
        "OpenAPI POST /api/community/force-scrape X-Admin-Key 헤더 파라미터 누락"
    )

    # X-Feeder-Token HMAC 계약 3자 정합 (base_scraper, cloud_feeder, web scraper-relay)
    base_scraper_src = _read_text("backend/scrapers/base_scraper.py")
    cloud_feeder_src = _read_text("backend/scheduler/cloud_feeder.py")
    relay_route_src = _read_text("frontend-web/src/app/api/scraper-relay/route.ts")

    assert "X-Feeder-Token" in base_scraper_src
    assert 'f"v1.{ts}.{sig}"' in base_scraper_src
    assert 'f"{ts}:{target_url}"' in base_scraper_src

    assert "build_scraper_relay_headers" in cloud_feeder_src or "X-Feeder-Token" in cloud_feeder_src
    assert "X-Feeder-Token" in relay_route_src
    assert "HMAC_TOLERANCE_SECONDS = 300" in relay_route_src
    assert "timingSafeEqual" in relay_route_src
    assert "`${ts}:${targetUrl}`" in relay_route_src

    # Web/Android X-Device-Token 헤더 계약
    web_utils_src = _read_text("frontend-web/src/app/lib/utils.ts")
    android_net_src = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/network/NetworkModule.kt"
    )
    assert "X-Device-Token" in web_utils_src
    assert 'HEADER_DEVICE_TOKEN = "X-Device-Token"' in android_net_src


def test_15_categories_and_10_sources_cross_platform_parity():
    backend_cats = [(c["key"], c["label"]) for c in STANDARD_CATEGORIES]
    backend_sources = list(STANDARD_SOURCES)

    assert len(backend_cats) == 15
    assert len(backend_sources) == 10
    assert ("가전제품", "가전/TV") in backend_cats
    assert ("해외직구", "해외직구") in backend_cats
    assert all("여행.해외핫딜" not in pair for pair in backend_cats)
    assert "빠삭육아" in backend_sources

    # Web (frontend-web/src/app/page.tsx)
    web_page_src = _read_text("frontend-web/src/app/page.tsx")
    cat_block_match = re.search(
        r"const DEFAULT_CATEGORIES:\s*CategoryMetaItem\[\]\s*=\s*\[(.*?)\];",
        web_page_src,
        re.DOTALL,
    )
    assert cat_block_match, "Web DEFAULT_CATEGORIES 블록 미발견"
    web_cats = re.findall(
        r'\{\s*key:\s*"([^"]+)",\s*label:\s*"([^"]+)"\s*\}',
        cat_block_match.group(1),
    )
    assert web_cats[0] == ("전체", "전체")
    assert web_cats[1:] == backend_cats, f"Web 카테고리 불일치: {web_cats[1:]} vs {backend_cats}"

    plat_block_match = re.search(
        r"const PLATFORMS\s*=\s*\[(.*?)\];",
        web_page_src,
        re.DOTALL,
    )
    assert plat_block_match, "Web PLATFORMS 블록 미발견"
    web_sources = re.findall(r'"([^"]+)"', plat_block_match.group(1))
    assert web_sources == backend_sources, f"Web 출처 불일치: {web_sources} vs {backend_sources}"

    # Android (CategoryCatalog.kt)
    cat_kt_src = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/models/CategoryCatalog.kt"
    )
    kt_cat_block = re.search(
        r"val STANDARD_CATEGORIES:\s*List<StandardCategory>\s*=\s*listOf\((.*?)\n\s*\)",
        cat_kt_src,
        re.DOTALL,
    )
    assert kt_cat_block, "Android STANDARD_CATEGORIES 블록 미발견"
    kt_cats = re.findall(
        r'StandardCategory\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)',
        kt_cat_block.group(1),
    )
    assert kt_cats == backend_cats, f"Android 카테고리 불일치: {kt_cats} vs {backend_cats}"

    kt_src_block = re.search(
        r"val STANDARD_SOURCES:\s*List<String>\s*=\s*listOf\((.*?)\n\s*\)",
        cat_kt_src,
        re.DOTALL,
    )
    assert kt_src_block, "Android STANDARD_SOURCES 블록 미발견"
    kt_sources = re.findall(r'"([^"]+)"', kt_src_block.group(1))
    assert kt_sources == backend_sources, f"Android 출처 불일치: {kt_sources} vs {backend_sources}"


def test_1rtt_summary_fields_dual_keys_has_more_and_honey_score_100():
    community_src = _read_text("backend/routers/community.py")
    web_types_src = _read_text("frontend-web/src/app/lib/types.ts")
    deal_item_kt = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/models/DealItem.kt"
    )
    deals_api_kt = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/network/DealsApiService.kt"
    )
    api_service_kt = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/network/ApiService.kt"
    )

    # 1-RTT 요약 필 및 이중 키
    required_summary_keys = (
        "buy_count",
        "buyCount",
        "pass_count",
        "saveCount",
        "passCount",
        "user_vote",
        "userVote",
        "comment_count",
        "commentCount",
        "history_count",
        "historyCount",
        "is_search_fallback",
        "is_free",
    )
    for k in required_summary_keys:
        assert f'"{k}"' in community_src, f"Backend community.py 키 누락: {k}"
        assert k in web_types_src, f"Web types.ts 필드 누락: {k}"
        assert f'"{k}"' in deal_item_kt, f"Android DealItem.kt 어노테이션 누락: {k}"

    # has_more
    assert '"has_more"' in community_src
    assert "has_more" in web_types_src
    assert '"has_more"' in deals_api_kt and '"hasMore"' in deals_api_kt

    # 댓글/투표 이중 키
    for comment_key in ("user_id", "userId", "created_at", "createdAt"):
        assert f'"{comment_key}"' in community_src
        assert comment_key in web_types_src
        assert f'"{comment_key}"' in api_service_kt

    # honey_score >= 100 3자 정합
    web_page_src = _read_text("frontend-web/src/app/page.tsx")
    deal_dao_kt = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/local/db/DealDao.kt"
    )
    cat_kt_src = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/models/CategoryCatalog.kt"
    )
    assert "100" in community_src and "honey_score" in community_src
    assert ">= 100" in web_page_src
    assert "honeyScore >= 100" in deal_dao_kt
    assert "honeyScore >= 100" in cat_kt_src


def test_standard_ux_copies_port_8080_turbopack_and_forbidden_patterns():
    # 3대 표준 UX 카피 (웹 + 앱)
    dnd_copy = "설정한 시간에는 푸시 알림을 보내지 않아요 (놓친 알림은 알림함에서 확인 가능)"
    fallback_copy = "직접 링크 미제공 · 쇼핑몰 검색 결과로 이동해요"
    fallback_cta = "쇼핑몰 검색 결과 보기"
    insufficient_history_copy = "아직 비교할 가격 이력이 충분하지 않아요"
    hot_deals_err_copy = "핫딜 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."

    web_notif = _read_text("frontend-web/src/app/settings/notifications/page.tsx")
    web_modal = _read_text("frontend-web/src/app/components/DealDetailModal.tsx")
    web_chart = _read_text("frontend-web/src/app/components/PriceTrendChart.tsx")
    web_page = _read_text("frontend-web/src/app/page.tsx")

    assert dnd_copy in web_notif
    assert fallback_copy in web_modal and fallback_cta in web_modal
    assert insufficient_history_copy in web_chart
    assert hot_deals_err_copy in web_page

    kt_kw = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/presentation/KeywordManagerScreen.kt"
    )
    kt_detail = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/presentation/DealDetailScreen.kt"
    )
    kt_home = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/presentation/home/HomeScreen.kt"
    )
    kt_cat_detail = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/presentation/category/CategoryDetailScreen.kt"
    )

    assert dnd_copy in kt_kw
    assert fallback_copy in kt_detail and fallback_cta in kt_detail
    assert insufficient_history_copy in kt_detail
    assert hot_deals_err_copy in kt_home and hot_deals_err_copy in kt_cat_detail

    # 포트 8080 & Next.js Turbopack/Webpack 설정
    next_cfg = _read_text("frontend-web/next.config.ts")
    push_reg_route = _read_text("frontend-web/src/app/api/push-register/route.ts")
    web_pkg = _read_text("frontend-web/package.json")
    app_gradle = _read_text("app/build.gradle")
    net_cfg_kt = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/data/network/NetworkConfig.kt"
    )
    net_mod_kt = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/network/NetworkModule.kt"
    )
    price_repo_kt = _read_text(
        "app/src/main/java/com/ddaeany0919/insightdeal/data/PriceHistoryRepository.kt"
    )

    assert "turbopack: {}" in next_cfg
    assert "http://localhost:8080" in next_cfg and "http://api:8080" in next_cfg
    assert ":8080" in push_reg_route
    assert "next build --webpack" in web_pkg
    assert "10.0.2.2:8080" in app_gradle
    assert "8080" in net_cfg_kt and "8080" in net_mod_kt
    assert "BuildConfig.BASE_URL" in price_repo_kt and "NetworkModule.createService" in price_repo_kt

    # 금지 패턴 부재 검증
    web_src_root = os.path.join(_PROJECT_ROOT, "frontend-web", "src")
    for root, _, files in os.walk(web_src_root):
        if "__tests__" in root:
            continue
        for fn in files:
            if fn.endswith((".ts", ".tsx")):
                content = open(os.path.join(root, fn), encoding="utf-8").read()
                assert "PriceComparison" not in content, f"금지 컴포넌트 발견: {fn}"
                assert "dummyPrices" not in content, f"가짜 차트 잔재 발견: {fn}"
                assert "BEl62i" not in content, f"샘플 VAPID 키 잔재 발견: {fn}"

    android_src_root = os.path.join(_PROJECT_ROOT, "app", "src", "main", "java")
    for root, _, files in os.walk(android_src_root):
        for fn in files:
            if fn.endswith(".kt"):
                content = open(os.path.join(root, fn), encoding="utf-8").read()
                assert "PriceComparison" not in content, f"Android 금지 패턴 발견: {fn}"
                assert "kakao_94821" not in content and "naver_38291" not in content, (
                    f"가짜 소셜 잔재 발견: {fn}"
                )
                if fn.endswith("ViewModel.kt"):
                    assert "android.content.Context" not in content, (
                        f"ViewModel Context 참조 위반: {fn}"
                    )
