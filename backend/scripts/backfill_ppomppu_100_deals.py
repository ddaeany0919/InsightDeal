#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
backend/scripts/backfill_ppomppu_100_deals.py

100자 절단 뽐뿌/알리뽐뿌/뽐뿌해외 핫딜 일괄 복구 백필 스크립트
- 클라우드 단독 모드 또는 RSS 수집으로 인해 본문이 100자 내외 요약본으로 축약 저장된 딜을 식별
- Vercel 서울 릴레이(/api/scraper-relay)를 통해 get_detail(url)을 재호출하여 온전한 본문, 직링크, 멀티옵션 복구
- Direct DB Mode (SQLite / PostgreSQL 원자적 업데이트) 및 Remote API Mode (/ingest-batch 전송) 지원
- Dry-run 모드(--dry-run) 지원
"""

import argparse
import asyncio
import json
import logging
import os
import re
import sys
from datetime import datetime, timedelta, timezone
from typing import Optional, List, Dict, Any

_backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_project_root = os.path.dirname(_backend_dir)
for _p in (_project_root, _backend_dir):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import httpx
from sqlalchemy import create_engine, func, or_, text
from sqlalchemy.orm import sessionmaker, Session

from backend.core.url_utils import normalize_url, is_search_or_community_url
from backend.database.models import Deal, Community
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
from backend.services.aggregator_service import extract_structured_multi_options

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("BackfillPpomppu")

CLOUD_DEALS_API = os.environ.get("CLOUD_DEALS_API_URL", "https://34.59.208.14.sslip.io/api/community/hot-deals")
CLOUD_API = os.environ.get("CLOUD_API_URL", "https://34.59.208.14.sslip.io/api/community/ingest-batch")


def get_scraper_for_url(url: str, community_name: str = ""):
    """게시판 URL 및 커뮤니티 이름에 대응하는 스크래퍼 인스턴스 반환"""
    low_u = url.lower()
    if "ppomppu8" in low_u or community_name == "ali_ppomppu":
        return AlippomppuScraper(4)
    elif "ppomppu4" in low_u or community_name == "ppomppu_overseas":
        return PpomppuOverseasScraper(10)
    else:
        return PpomppuScraper(1)


def get_db_session(custom_db_path: Optional[str] = None) -> Session:
    """DB 세션 생성 (custom_db_path > DATABASE_URL > default SQLite)"""
    if custom_db_path:
        db_file = os.path.abspath(custom_db_path)
        os.makedirs(os.path.dirname(db_file), exist_ok=True)
        db_url = f"sqlite:///{db_file}"
        logger.info(f"📁 지정된 SQLite 경로로 연결: {db_url}")
        engine = create_engine(db_url, connect_args={"check_same_thread": False})
        SessionCls = sessionmaker(autocommit=False, autoflush=False, bind=engine)
        return SessionCls()

    db_env = os.getenv("DATABASE_URL")
    if db_env:
        logger.info(f"🌐 환경변수 DATABASE_URL 연결")
        connect_args = {"check_same_thread": False} if db_env.startswith("sqlite") else {"options": "-c timezone=Asia/Seoul"}
        engine = create_engine(db_env, connect_args=connect_args)
        SessionCls = sessionmaker(autocommit=False, autoflush=False, bind=engine)
        return SessionCls()

    # 기본 경로 탐색
    default_candidates = [
        os.path.join(_backend_dir, "data", "deals.db"),
        os.path.join(_backend_dir, "insight_deal.db"),
        os.path.join(_backend_dir, "insightdeal.db"),
    ]
    chosen_path = None
    for p in default_candidates:
        if os.path.exists(p) and os.path.getsize(p) > 0:
            chosen_path = p
            break
    if not chosen_path:
        chosen_path = default_candidates[1]

    db_url = f"sqlite:///{chosen_path}"
    logger.info(f"📁 기본 로컬 SQLite 연결: {db_url}")
    engine = create_engine(db_url, connect_args={"check_same_thread": False})
    SessionCls = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    return SessionCls()


async def backfill_direct_db(
    db: Session,
    days: int = 7,
    min_len: int = 120,
    limit: int = 100,
    dry_run: bool = False,
    concurrency: int = 2,
    delay: float = 0.8,
) -> Dict[str, int]:
    """로컬/서버 직접 DB 접속 모드 백필 실행"""
    stats = {"inspected": 0, "candidates": 0, "recovered": 0, "failed": 0, "skipped": 0}

    # 1. 뽐뿌 커뮤니티 ID 조회
    ppomppu_comm_ids = []
    try:
        comms = db.query(Community).filter(
            Community.name.in_(["ppomppu", "ali_ppomppu", "ppomppu_overseas"])
        ).all()
        ppomppu_comm_ids = [c.id for c in comms]
    except Exception as ce:
        logger.warning(f"Community 테이블 조회 오류 (post_link로 대체): {ce}")

    # 2. 100자 절단 뽐뿌 딜 쿼리 구성
    query = db.query(Deal)
    if ppomppu_comm_ids:
        query = query.filter(
            or_(
                Deal.source_community_id.in_(ppomppu_comm_ids),
                Deal.post_link.like("%ppomppu.co.kr%"),
            )
        )
    else:
        query = query.filter(Deal.post_link.like("%ppomppu.co.kr%"))

    # 본문 길이 조건 (120자 미만 축약본 또는 이미지가 없으면서 250자 미만인 본문)
    query = query.filter(
        or_(
            Deal.content_html == None,
            Deal.content_html == "",
            func.length(Deal.content_html) < min_len,
            (func.length(Deal.content_html) < 250) & (~Deal.content_html.like("%<img%")),
        )
    )

    if days > 0:
        time_cutoff = datetime.now(timezone.utc) - timedelta(days=days)
        query = query.filter(Deal.indexed_at >= time_cutoff)

    query = query.order_by(Deal.indexed_at.desc())
    if limit > 0:
        query = query.limit(limit)

    candidate_deals = query.all()
    stats["candidates"] = len(candidate_deals)
    logger.info(f"🎯 100자 절단 뽐뿌 대상 딜 {len(candidate_deals)}건 발견 (기간: 최근 {days}일, 최소길이: {min_len}자)")

    if not candidate_deals:
        logger.info("✅ 복구 대상 절단 딜이 없습니다.")
        return stats

    sem = asyncio.Semaphore(concurrency)

    async def recover_deal(deal: Deal, idx: int):
        stats["inspected"] += 1
        comm_name = getattr(deal.community, "name", "") if getattr(deal, "community", None) else ""
        norm_post_url = normalize_url(deal.post_link)
        old_len = len(deal.content_html or "")
        old_ecom = deal.ecommerce_link or ""

        async with sem:
            try:
                sc = get_scraper_for_url(norm_post_url, comm_name)
                async with sc:
                    det = await sc.get_detail(norm_post_url, deal.title)

                if not det:
                    logger.warning(f"[{idx}/{len(candidate_deals)}] ❌ 상세 획득 실패 (빈 응답): ID {deal.id} - {deal.title}")
                    stats["failed"] += 1
                    return

                new_content = (det.get("content_html") or "").strip()
                new_len = len(new_content)

                if new_len < min_len and not det.get("is_closed"):
                    logger.warning(
                        f"[{idx}/{len(candidate_deals)}] ⚠️ 복구 후 본문도 {new_len}자로 여전히 짧음 (스킵): ID {deal.id}"
                    )
                    stats["skipped"] += 1
                    return

                new_ecom = (det.get("ecommerce_link") or "").strip()
                new_img = det.get("image_url") or ""
                new_shipping = det.get("shipping_fee") or ""
                new_closed = bool(det.get("is_closed", False))
                new_price = int(det.get("price") or 0)

                # 멀티 옵션 추출
                opts = extract_structured_multi_options(
                    raw_title=deal.title,
                    content_html=new_content,
                    default_currency=deal.currency or "KRW",
                    default_url=new_ecom or old_ecom,
                )

                if dry_run:
                    logger.info(
                        f"[DRY-RUN {idx}/{len(candidate_deals)}] ID {deal.id} 복구 시뮬레이션:\n"
                        f"  - 제목: {deal.title}\n"
                        f"  - 본문 길이: {old_len}자 -> {new_len}자 (확장 {new_len - old_len}자)\n"
                        f"  - 직링크: '{old_ecom}' -> '{new_ecom}'\n"
                        f"  - 멀티옵션: {len(opts)}개 추출\n"
                        f"  - 마감여부: {new_closed}"
                    )
                    stats["recovered"] += 1
                    return

                # 실제 DB 원자적 반영
                deal.content_html = new_content
                if new_ecom and not is_search_or_community_url(new_ecom):
                    deal.ecommerce_link = new_ecom
                if new_img and (not deal.image_url or "thumb" in deal.image_url):
                    deal.image_url = new_img
                if new_shipping and not deal.shipping_fee:
                    deal.shipping_fee = new_shipping
                if new_closed:
                    deal.is_closed = True
                if new_price > 0 and (not deal.price or deal.price in ("0", "")):
                    deal.price = str(new_price)

                if opts and len(opts) >= 2:
                    deal.options_data = json.dumps(opts, ensure_ascii=False)
                    deal.has_options = True

                db.commit()
                stats["recovered"] += 1
                logger.info(
                    f"[{idx}/{len(candidate_deals)}] ✅ ID {deal.id} 원자적 복구 완료: "
                    f"본문 {old_len}자->{new_len}자, 직링크={bool(new_ecom)}, 옵션={len(opts)}개"
                )
            except Exception as e:
                db.rollback()
                logger.error(f"[{idx}/{len(candidate_deals)}] ❌ ID {deal.id} 복구 오류: {e}")
                stats["failed"] += 1
            finally:
                await asyncio.sleep(delay)

    for i, d in enumerate(candidate_deals, start=1):
        await recover_deal(d, i)

    return stats


async def backfill_remote_api(
    days: int = 7,
    min_len: int = 120,
    limit: int = 100,
    dry_run: bool = False,
    concurrency: int = 2,
    delay: float = 0.8,
) -> Dict[str, int]:
    """클라우드 REST API 기반 원격 백필 실행 (/hot-deals 페칭 후 /ingest-batch 전송)"""
    stats = {"inspected": 0, "candidates": 0, "recovered": 0, "failed": 0, "skipped": 0}
    platforms = [("ppomppu", "뽐뿌"), ("ali_ppomppu", "알리뽐뿌"), ("ppomppu_overseas", "뽐뿌해외")]

    async with httpx.AsyncClient(timeout=30.0) as http_client:
        all_candidates = []
        for name, display_name in platforms:
            for off in (0, 100, 200):
                url = f"{CLOUD_DEALS_API}?limit=100&offset={off}&platform={display_name}"
                try:
                    res = await http_client.get(url)
                    if res.status_code != 200:
                        break
                    data = res.json()
                    deals = data if isinstance(data, list) else data.get("items", data.get("deals", []))
                    if not deals:
                        break
                    for d in deals:
                        c_html = d.get("content_html") or ""
                        if len(c_html) < min_len or (len(c_html) < 250 and "<img" not in c_html):
                            all_candidates.append((name, display_name, d))
                    if len(deals) < 100:
                        break
                except Exception as fe:
                    logger.warning(f"[{display_name}] 원격 API 페칭 경고: {fe}")
                    break

        if limit > 0:
            all_candidates = all_candidates[:limit]
        stats["candidates"] = len(all_candidates)
        logger.info(f"🎯 [원격 API] 100자 절단 뽐뿌 대상 딜 {len(all_candidates)}건 발견")

        if not all_candidates:
            logger.info("✅ 복구 대상 원격 절단 딜이 없습니다.")
            return stats

        sem = asyncio.Semaphore(concurrency)
        batches_to_push: Dict[str, List[dict]] = {}

        for idx, (name, display_name, deal_dict) in enumerate(all_candidates, start=1):
            stats["inspected"] += 1
            post_u = deal_dict.get("post_url") or deal_dict.get("post_link") or deal_dict.get("url") or ""
            norm_post_u = normalize_url(post_u)
            old_len = len(deal_dict.get("content_html") or "")
            old_ecom = deal_dict.get("ecommerce_url") or deal_dict.get("ecommerce_link") or ""

            async with sem:
                try:
                    sc = get_scraper_for_url(norm_post_u, name)
                    async with sc:
                        det = await sc.get_detail(norm_post_u, deal_dict.get("title", ""))

                    if not det:
                        stats["failed"] += 1
                        continue

                    new_content = (det.get("content_html") or "").strip()
                    new_len = len(new_content)
                    if new_len < min_len and not det.get("is_closed"):
                        stats["skipped"] += 1
                        continue

                    new_ecom = (det.get("ecommerce_link") or "").strip()
                    new_img = det.get("image_url") or ""
                    new_shipping = det.get("shipping_fee") or ""
                    new_closed = bool(det.get("is_closed", False))
                    new_price = int(det.get("price") or 0)

                    opts = extract_structured_multi_options(
                        raw_title=deal_dict.get("title", ""),
                        content_html=new_content,
                        default_currency=deal_dict.get("currency") or "KRW",
                        default_url=new_ecom or old_ecom,
                    )

                    if dry_run:
                        logger.info(
                            f"[DRY-RUN {idx}/{len(all_candidates)}] ID {deal_dict.get('id')} 원격 복구 시뮬레이션:\n"
                            f"  - 제목: {deal_dict.get('title')}\n"
                            f"  - 본문 길이: {old_len}자 -> {new_len}자 (확장 {new_len - old_len}자)\n"
                            f"  - 직링크: '{old_ecom}' -> '{new_ecom}'\n"
                            f"  - 멀티옵션: {len(opts)}개 추출"
                        )
                        stats["recovered"] += 1
                    else:
                        enriched_item = dict(deal_dict)
                        enriched_item["url"] = norm_post_u
                        enriched_item["content_html"] = new_content
                        if new_ecom and not is_search_or_community_url(new_ecom):
                            enriched_item["ecommerce_link"] = new_ecom
                        if new_img:
                            enriched_item["image_url"] = new_img
                        if new_shipping:
                            enriched_item["shipping_fee"] = new_shipping
                        if new_closed:
                            enriched_item["is_closed"] = True
                        if new_price > 0:
                            enriched_item["price"] = new_price
                        if opts and len(opts) >= 2:
                            enriched_item["options_data"] = opts
                            enriched_item["has_options"] = True

                        batches_to_push.setdefault(name, []).append(enriched_item)
                        stats["recovered"] += 1
                except Exception as e:
                    logger.error(f"[{idx}/{len(all_candidates)}] ❌ 원격 복구 실패: {e}")
                    stats["failed"] += 1
                finally:
                    await asyncio.sleep(delay)

        # 실제 전송
        if not dry_run and batches_to_push:
            for c_name, items in batches_to_push.items():
                d_name = "뽐뿌" if c_name == "ppomppu" else ("알리뽐뿌" if c_name == "ali_ppomppu" else "뽐뿌해외")
                base_u = "https://www.ppomppu.co.kr"
                for i in range(0, len(items), 5):
                    chunk = items[i:i + 5]
                    try:
                        res = await http_client.post(
                            CLOUD_API,
                            json={
                                "community_name": c_name,
                                "display_name": d_name,
                                "base_url": base_u,
                                "deals": chunk,
                            },
                        )
                        if res.status_code == 200:
                            logger.info(f"🚀 [원격 서버 전송] {d_name} {len(chunk)}건 복구 완료 (/ingest-batch)")
                    except Exception as pe:
                        logger.error(f"❌ /ingest-batch 전송 오류: {pe}")
                    await asyncio.sleep(0.5)

    return stats


async def main_async(args):
    logger.info("=" * 60)
    logger.info("🛠️  InsightDeal 뽐뿌 100자 절단 딜 일괄 복구 백필 시작")
    logger.info(f"   모드: {'[DRY-RUN 모의 실행]' if args.dry_run else '[실제 DB/서버 업데이트]'}")
    logger.info(f"   방식: {'원격 REST API 모드' if args.api_mode else '직접 DB 접속 모드'}")
    logger.info(f"   조건: 최근 {args.days}일, 최소 본문 {args.min_len}자 미만, 최대 {args.limit}건")
    logger.info("=" * 60)

    # 릴레이 강제 플래그 적용
    if args.use_relay:
        os.environ["IS_CLOUD"] = "true"
        logger.info("🌐 Vercel 서울 릴레이(/api/scraper-relay) 강제 활성화 (IS_CLOUD=true)")

    t0 = datetime.now()
    if args.api_mode:
        stats = await backfill_remote_api(
            days=args.days,
            min_len=args.min_len,
            limit=args.limit,
            dry_run=args.dry_run,
            concurrency=args.concurrency,
            delay=args.delay,
        )
    else:
        db = get_db_session(args.db_path)
        try:
            stats = await backfill_direct_db(
                db=db,
                days=args.days,
                min_len=args.min_len,
                limit=args.limit,
                dry_run=args.dry_run,
                concurrency=args.concurrency,
                delay=args.delay,
            )
        finally:
            db.close()

    elapsed = (datetime.now() - t0).total_seconds()
    logger.info("=" * 60)
    logger.info(f"🏁 백필 완료 (소요시간: {elapsed:.2f}초)")
    logger.info(f"   - 대상 발견: {stats['candidates']}건")
    logger.info(f"   - 복구 완료: {stats['recovered']}건")
    logger.info(f"   - 스킵/보존: {stats['skipped']}건")
    logger.info(f"   - 실패 건수: {stats['failed']}건")
    logger.info("=" * 60)


def main():
    if sys.platform == "win32":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

    parser = argparse.ArgumentParser(description="뽐뿌 100자 절단 핫딜 일괄 복구 백필 스크립트")
    parser.add_argument("--dry-run", action="store_true", help="DB 쓰기 또는 API 전송 없이 대상 딜과 추출 결과만 시뮬레이션")
    parser.add_argument("--api-mode", action="store_true", help="로컬 DB 대신 원격 클라우드 API(/hot-deals, /ingest-batch)를 통해 백필")
    parser.add_argument("--db-path", type=str, default=None, help="대상 SQLite DB 파일 경로 (기본값: 자동 탐색 또는 DATABASE_URL)")
    parser.add_argument("--days", type=int, default=7, help="최근 N일 이내의 딜 대상 (기본: 7, 0은 전체 기간)")
    parser.add_argument("--min-len", type=int, default=120, help="절단 판정 기준 최소 본문 길이 (기본: 120)")
    parser.add_argument("--limit", type=int, default=100, help="최대 복구 건수 (기본: 100, 0은 제한 없음)")
    parser.add_argument("--use-relay", action="store_true", help="Vercel 서울 릴레이(/api/scraper-relay) 강제 사용")
    parser.add_argument("--concurrency", type=int, default=2, help="상세 페이지 동시 요청 수 (기본: 2)")
    parser.add_argument("--delay", type=float, default=0.8, help="요청 간 딜레이 초 (기본: 0.8)")

    args = parser.parse_args()
    asyncio.run(main_async(args))


if __name__ == "__main__":
    main()
