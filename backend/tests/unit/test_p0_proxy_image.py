# -*- coding: utf-8 -*-
"""
backend/tests/unit/test_p0_proxy_image.py

M4 이미지 전 구간 무결성(proxy-image) 단위 테스트:
1. 정상 PNG/JPEG(>=60B) 프록시 200 OK + Cache-Control: public, max-age=604800, immutable
2. 디스크 캐시 히트(2차 호출 시 외부 fetch 0회) 및 손상 캐시 복구
3. 공인 도메인 간 허용 리다이렉트(1~3홉 200 + 캐시 헤더) 및 4홉 초과 차단(404 no-store)
4. IPv6([::1]), IPv4-mapped IPv6([::ffff:127.0.0.1], [::ffff:10.0.0.1]), .local/.internal,
   자격증명 URL, file://, 다중 DNS 혼합, 공인->사설 2차 호스트 리바인딩,
   10MB 초과 / text/html 페이로드 차단(404 no-store), /api/image-proxy & /api/community/proxy-image 별칭 검증
"""

import hashlib
import os
import socket
import sys

import httpx
import pytest
from fastapi.testclient import TestClient

_TEST_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(os.path.dirname(_TEST_DIR))
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from backend import main as main_mod  # noqa: E402

VALID_PNG = b"\x89PNG\r\n\x1a\n" + (b"\x00" * 80)
VALID_JPEG = b"\xff\xd8\xff\xe0" + (b"\x11" * 80)


def _public_dns_only(host, port, *args, **kwargs):
    return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))]


def test_proxy_image_success_png_and_jpeg_cache_headers(monkeypatch, tmp_path):
    monkeypatch.setattr(main_mod, "IMAGE_CACHE_DIR", str(tmp_path))
    monkeypatch.setattr(main_mod, "_get_deal_backup_candidates_sync", lambda *a, **kw: (None, []))
    monkeypatch.setattr(socket, "getaddrinfo", _public_dns_only)

    async def fake_get(url, **kwargs):
        if url.endswith(".png"):
            return httpx.Response(200, content=VALID_PNG, request=httpx.Request("GET", url))
        return httpx.Response(200, content=VALID_JPEG, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", fake_get)
    client = TestClient(main_mod.app)

    r_png = client.get("/api/proxy-image", params={"url": "https://cdn.example.com/item.png"})
    assert r_png.status_code == 200
    assert r_png.headers["content-type"].startswith("image/png")
    assert r_png.headers.get("cache-control") == "public, max-age=604800, immutable"
    assert r_png.content == VALID_PNG

    r_jpg = client.get("/api/proxy-image", params={"url": "https://cdn.example.com/item.jpg"})
    assert r_jpg.status_code == 200
    assert r_jpg.headers["content-type"].startswith("image/jpeg")
    assert r_jpg.headers.get("cache-control") == "public, max-age=604800, immutable"
    assert r_jpg.content == VALID_JPEG


def test_proxy_image_disk_cache_hit_and_corrupt_cache_recovery(monkeypatch, tmp_path):
    monkeypatch.setattr(main_mod, "IMAGE_CACHE_DIR", str(tmp_path))
    monkeypatch.setattr(main_mod, "_get_deal_backup_candidates_sync", lambda *a, **kw: (None, []))
    monkeypatch.setattr(socket, "getaddrinfo", _public_dns_only)

    fetch_count = 0

    async def fake_get(url, **kwargs):
        nonlocal fetch_count
        fetch_count += 1
        return httpx.Response(200, content=VALID_PNG, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", fake_get)
    client = TestClient(main_mod.app)

    target_url = "https://cdn.example.com/cached-product.png"
    r1 = client.get("/api/proxy-image", params={"url": target_url})
    assert r1.status_code == 200
    assert r1.headers.get("cache-control") == "public, max-age=604800, immutable"
    assert fetch_count == 1

    # 2차 호출: 디스크 캐시 적중으로 외부 요청 0회
    r2 = client.get("/api/proxy-image", params={"url": target_url})
    assert r2.status_code == 200
    assert r2.headers.get("cache-control") == "public, max-age=604800, immutable"
    assert r2.content == VALID_PNG
    assert fetch_count == 1

    # 손상된 캐시 파일 시뮬레이션 후 3차 호출 -> 손상 캐시 제거 및 재수집 복구
    cache_hash = hashlib.md5(target_url.encode("utf-8")).hexdigest()
    cache_path = os.path.join(str(tmp_path), f"{cache_hash}.cache")
    with open(cache_path, "wb") as f:
        f.write(b"<html>corrupted-cache</html>")

    r3 = client.get("/api/proxy-image", params={"url": target_url})
    assert r3.status_code == 200
    assert r3.content == VALID_PNG
    assert fetch_count == 2
    with open(cache_path, "rb") as f:
        assert f.read() == VALID_PNG


def test_proxy_image_allowed_redirects_up_to_3_hops_and_blocks_4th_hop(monkeypatch, tmp_path):
    monkeypatch.setattr(main_mod, "IMAGE_CACHE_DIR", str(tmp_path))
    monkeypatch.setattr(main_mod, "_get_deal_backup_candidates_sync", lambda *a, **kw: (None, []))
    monkeypatch.setattr(socket, "getaddrinfo", _public_dns_only)

    async def fake_get(url, **kwargs):
        routes = {
            "https://cdn.example.com/1hop.png": httpx.Response(
                301, headers={"Location": "https://img2.example.com/final.png"}, request=httpx.Request("GET", url)
            ),
            "https://cdn.example.com/3hop.png": httpx.Response(
                302, headers={"Location": "https://img1.example.com/step1.png"}, request=httpx.Request("GET", url)
            ),
            "https://img1.example.com/step1.png": httpx.Response(
                307, headers={"Location": "https://img2.example.com/step2.png"}, request=httpx.Request("GET", url)
            ),
            "https://img2.example.com/step2.png": httpx.Response(
                308, headers={"Location": "https://img3.example.com/final.png"}, request=httpx.Request("GET", url)
            ),
            "https://cdn.example.com/4hop.png": httpx.Response(
                302, headers={"Location": "https://hop1.example.com/a.png"}, request=httpx.Request("GET", url)
            ),
            "https://hop1.example.com/a.png": httpx.Response(
                302, headers={"Location": "https://hop2.example.com/b.png"}, request=httpx.Request("GET", url)
            ),
            "https://hop2.example.com/b.png": httpx.Response(
                302, headers={"Location": "https://hop3.example.com/c.png"}, request=httpx.Request("GET", url)
            ),
            "https://hop3.example.com/c.png": httpx.Response(
                302, headers={"Location": "https://hop4.example.com/final.png"}, request=httpx.Request("GET", url)
            ),
        }
        if url in routes:
            return routes[url]
        return httpx.Response(200, content=VALID_PNG, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", fake_get)
    client = TestClient(main_mod.app)

    # 1홉 및 3홉 허용 리다이렉트 -> 200 OK + 캐시 헤더 유지
    for ok_url in ("https://cdn.example.com/1hop.png", "https://cdn.example.com/3hop.png"):
        res = client.get("/api/proxy-image", params={"url": ok_url})
        assert res.status_code == 200
        assert res.headers.get("cache-control") == "public, max-age=604800, immutable"
        assert res.content == VALID_PNG

    # 4홉 초과 리다이렉트 -> 차단(404 no-store)
    r_4hop = client.get("/api/proxy-image", params={"url": "https://cdn.example.com/4hop.png"})
    assert r_4hop.status_code == 404
    assert r_4hop.headers.get("cache-control") == "no-store"


def test_proxy_image_ssrf_ipv6_mapped_schemes_mixed_dns_rebind_and_payload_guards(monkeypatch, tmp_path):
    monkeypatch.setattr(main_mod, "IMAGE_CACHE_DIR", str(tmp_path))
    monkeypatch.setattr(main_mod, "_get_deal_backup_candidates_sync", lambda *a, **kw: (None, []))

    fetched_urls: list[str] = []

    async def fake_get(url, **kwargs):
        fetched_urls.append(url)
        if url == "https://public-redirect.example.com/start.png":
            return httpx.Response(
                302,
                headers={"Location": "https://private-hop.example.com/internal.png"},
                request=httpx.Request("GET", url),
            )
        if url == "https://cdn.example.com/header-too-big.png":
            return httpx.Response(
                200,
                content=VALID_PNG,
                headers={"Content-Length": str((10 * 1024 * 1024) + 1)},
                request=httpx.Request("GET", url),
            )
        if url == "https://cdn.example.com/body-too-big.png":
            return httpx.Response(
                200,
                content=b"\x89PNG\r\n\x1a\n" + (b"\x00" * (10 * 1024 * 1024)),
                request=httpx.Request("GET", url),
            )
        if url == "https://cdn.example.com/html-payload.png":
            return httpx.Response(
                200,
                content=b"<html><body><script>alert(1)</script>" + (b"x" * 100) + b"</body></html>",
                headers={"Content-Type": "text/html"},
                request=httpx.Request("GET", url),
            )
        return httpx.Response(404, content=b"not found", request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", fake_get)

    def fake_getaddrinfo(host, port, *args, **kwargs):
        if host == "mixed-dns.example.com":
            return [
                (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port)),
                (socket.AF_INET6, socket.SOCK_STREAM, 6, "", ("::1", port, 0, 0)),
            ]
        if host == "private-hop.example.com":
            return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("192.168.1.50", port))]
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))]

    monkeypatch.setattr(socket, "getaddrinfo", fake_getaddrinfo)
    client = TestClient(main_mod.app)

    # 1. 리터럴 IPv6/IPv4-mapped IPv6/.local/.internal/자격증명/비HTTP 스킴/혼합 DNS -> 외부 fetch 0회 + 404 no-store
    blocked_urls = (
        "http://[::1]/a.png",
        "http://[::ffff:127.0.0.1]/a.png",
        "http://[::ffff:10.0.0.1]/a.png",
        "http://0.0.0.0/a.png",
        "http://printer.local/a.png",
        "http://metadata.google.internal/a.png",
        "https://user:pass@cdn.example.com/a.png",
        "file:///etc/passwd",
        "https://mixed-dns.example.com/a.png",
    )
    for bad_url in blocked_urls:
        res = client.get("/api/proxy-image", params={"url": bad_url})
        assert res.status_code == 404, f"차단 실패: {bad_url}"
        assert res.headers.get("cache-control") == "no-store"
    assert fetched_urls == []

    # 2. 공인 1차 -> 사설 2차 호스트 DNS 리바인딩 리다이렉트 차단 (2차 요청 0회)
    res_rebind = client.get("/api/proxy-image", params={"url": "https://public-redirect.example.com/start.png"})
    assert res_rebind.status_code == 404
    assert res_rebind.headers.get("cache-control") == "no-store"
    assert fetched_urls == ["https://public-redirect.example.com/start.png"]

    # 3. 10MB 초과 / text/html 페이로드 차단 + 캐시 파일 미생성
    for payload_url in (
        "https://cdn.example.com/header-too-big.png",
        "https://cdn.example.com/body-too-big.png",
        "https://cdn.example.com/html-payload.png",
    ):
        res_p = client.get("/api/proxy-image", params={"url": payload_url})
        assert res_p.status_code == 404
        assert res_p.headers.get("cache-control") == "no-store"
    assert list(tmp_path.iterdir()) == []


def test_proxy_image_alias_routes(monkeypatch, tmp_path):
    monkeypatch.setattr(main_mod, "IMAGE_CACHE_DIR", str(tmp_path))
    monkeypatch.setattr(main_mod, "_get_deal_backup_candidates_sync", lambda *a, **kw: (None, []))
    monkeypatch.setattr(socket, "getaddrinfo", _public_dns_only)

    async def fake_get(url, **kwargs):
        return httpx.Response(200, content=VALID_PNG, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", fake_get)
    client = TestClient(main_mod.app)

    for route in ("/api/image-proxy", "/api/community/proxy-image"):
        ok_res = client.get(route, params={"url": "https://cdn.example.com/alias-ok.png"})
        assert ok_res.status_code == 200
        assert ok_res.headers.get("cache-control") == "public, max-age=604800, immutable"
        assert ok_res.content == VALID_PNG

        blocked_res = client.get(route, params={"url": "http://[::ffff:127.0.0.1]/bad.png"})
        assert blocked_res.status_code == 404
        assert blocked_res.headers.get("cache-control") == "no-store"


def test_proxy_image_head_corp_and_generic_og_rejection(monkeypatch, tmp_path):
    monkeypatch.setattr(main_mod, "IMAGE_CACHE_DIR", str(tmp_path))
    monkeypatch.setattr(main_mod, "_get_deal_backup_candidates_sync", lambda *a, **kw: (None, []))
    monkeypatch.setattr(socket, "getaddrinfo", _public_dns_only)

    async def fake_get(url, **kwargs):
        return httpx.Response(200, content=VALID_PNG, request=httpx.Request("GET", url))

    monkeypatch.setattr(main_mod.proxy_client, "get", fake_get)
    client = TestClient(main_mod.app)

    for route in ("/api/proxy-image", "/api/community/proxy-image"):
        head_res = client.head(route, params={"url": "https://cdn.example.com/head-ok.png"})
        assert head_res.status_code == 200
        assert head_res.headers.get("cross-origin-resource-policy") == "cross-origin"
        assert int(head_res.headers.get("content-length") or 0) == len(VALID_PNG)
        assert head_res.content == b""

    generic_res = client.get(
        "/api/proxy-image",
        params={"url": "https://shopv.pstatic.net/web/r/20241122/_next/static/og/nplus-store/OG_450x235.png"},
    )
    assert generic_res.status_code == 404
    assert generic_res.headers.get("cache-control") == "no-store"
