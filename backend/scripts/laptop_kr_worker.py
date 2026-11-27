# -*- coding: utf-8 -*-
"""
노트북(한국 가정/유선 랜 IP)용 상시 보조 워커:
1. IS_CLOUD=false 모드로 cloud_feeder.py --one-shot 주기 실행 (한국 IP 직접 크롤링 + 실시간 종료 감지)
2. 3사이클마다 GCP 최신 딜 중 이미지가 누락/저화질(small_)인 딜을 한국 IP로 OG 이미지 업그레이드 후 ingest-batch 전송
3. 외부 포트 오픈(ngrok 등) 없이 outbound HTTPS(34.59.208.14.sslip.io)로만 안전하게 통신
"""

import asyncio
import logging
import os
import sys
import time
import urllib.parse

import httpx
from dotenv import load_dotenv

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_BACKEND_DIR = os.path.dirname(_SCRIPT_DIR)
_PROJECT_ROOT = os.path.dirname(_BACKEND_DIR)
for _p in (_PROJECT_ROOT, _BACKEND_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

for _env_name in (
    os.path.join(_PROJECT_ROOT, ".env.laptop"),
    os.path.join(_BACKEND_DIR, ".env.laptop"),
    os.path.join(_PROJECT_ROOT, ".env"),
    os.path.join(_BACKEND_DIR, ".env"),
):
    if os.path.exists(_env_name):
        load_dotenv(_env_name, override=False)

# 한국 가정 IP 직접 접속 모드 강제
os.environ["IS_CLOUD"] = "false"
os.environ.setdefault("CLOUD_API_URL", "https://34.59.208.14.sslip.io/api/community/ingest-batch")
os.environ.setdefault("CLOUD_DEALS_API_URL", "https://34.59.208.14.sslip.io/api/community/hot-deals")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [KR-Laptop-Worker] [%(levelname)s] %(message)s",
)
logger = logging.getLogger("LaptopKrWorker")

if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())


def _is_low_quality_or_missing_image(img_url: str | None) -> bool:
    u = (img_url or "").strip().lower()
    if not u:
        return True
    if "small_" in u or "noimg" in u or "no_image" in u or "placeholder" in u:
        return True
    return False


async def _upgrade_missing_images_kr_ip(max_deals: int = 12) -> int:
    """해외 GCP IP에서 차단된 쇼핑몰(스마트스토어/11번가/쿠팡 등) 고화질 OG 이미지를 한국 IP로 복구해 서버에 보강 푸시."""
    deals_api = os.environ["CLOUD_DEALS_API_URL"]
    ingest_api = os.environ["CLOUD_API_URL"]
    token = (os.environ.get("FEEDER_API_TOKEN") or "").strip()
    if not token:
        logger.warning("FEEDER_API_TOKEN 미설정: 이미지 보강 푸시를 건너뜁니다.")
        return 0

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.get(deals_api, params={"limit": 50})
            if resp.status_code != 200:
                return 0
            data = resp.json()
    except Exception as exc:
        logger.debug("최신 딜 조회 실패: %s", exc)
        return 0

    deals = data.get("deals") if isinstance(data, dict) else []
    if not isinstance(deals, list):
        return 0

    from backend.scrapers.base_scraper import BaseScraper

    scraper = BaseScraper()
    upgrades: list[dict] = []

    for item in deals:
        if len(upgrades) >= max_deals:
            break
        if not isinstance(item, dict) or item.get("is_closed"):
            continue
        if not _is_low_quality_or_missing_image(item.get("image_url")):
            continue

        post_url = (item.get("post_url") or "").strip()
        ecom_url = (item.get("ecommerce_url") or "").strip()
        site_name = (item.get("site_name") or "뽐뿌").split(",")[0].strip()
        title = (item.get("title") or "").strip()
        if not post_url or not ecom_url or not title:
            continue

        try:
            new_img = await scraper.fetch_og_image(ecom_url)
        except Exception:
            new_img = None

        if new_img and not _is_low_quality_or_missing_image(new_img) and new_img != item.get("image_url"):
            upgrades.append(
                {
                    "community_name": item.get("community_name") or "ppomppu",
                    "display_name": site_name,
                    "base_url": f"https://{urllib.parse.urlparse(post_url).netloc}",
                    "deals": [
                        {
                            "title": title,
                            "price": int(item.get("price") or 0),
                            "currency": item.get("currency") or "KRW",
                            "url": post_url,
                            "ecommerce_link": ecom_url,
                            "image_url": new_img,
                            "is_closed": False,
                        }
                    ],
                }
            )

    pushed = 0
    if upgrades:
        headers = {"X-Feeder-Token": token, "Content-Type": "application/json"}
        async with httpx.AsyncClient(timeout=20.0) as client:
            for payload in upgrades:
                try:
                    r = await client.post(ingest_api, json=payload, headers=headers)
                    if r.status_code < 300:
                        pushed += 1
                except Exception as exc:
                    logger.debug("이미지 보강 전송 실패: %s", exc)
    if pushed:
        logger.info("🖼️ 한국 IP 고화질 이미지 복구 완료: %d건 서버 반영", pushed)
    return pushed


async def run_forever() -> None:
    from backend.scripts.cloud_feeder import main as run_feeder_once

    interval_sec = max(60, int(os.environ.get("LAPTOP_INTERVAL_SEC", "180") or "180"))
    cycle = 0
    logger.info(
        "🚀 노트북 한국 IP 워커 시작 (주기=%d초, 서버=%s)",
        interval_sec,
        os.environ["CLOUD_API_URL"],
    )

    while True:
        cycle += 1
        started = time.monotonic()
        try:
            await run_feeder_once(one_shot=True)
        except Exception as exc:
            logger.warning("피더 사이클 오류(자동 재시도): %s", exc)

        if cycle % 3 == 1:
            try:
                await _upgrade_missing_images_kr_ip(max_deals=12)
            except Exception as exc:
                logger.warning("이미지 보강 사이클 오류: %s", exc)

        elapsed = time.monotonic() - started
        sleep_for = max(15.0, interval_sec - elapsed)
        logger.info("⏳ 사이클 #%d 완료 (%.1f초 소요). %.0f초 후 다음 실행...", cycle, elapsed, sleep_for)
        await asyncio.sleep(sleep_for)


if __name__ == "__main__":
    try:
        asyncio.run(run_forever())
    except KeyboardInterrupt:
        logger.info("🛑 노트북 한국 IP 워커를 종료합니다.")
