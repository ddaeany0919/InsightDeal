import asyncio
from concurrent.futures import ThreadPoolExecutor
import logging
import os
import sys
import time
from datetime import datetime, timedelta, timezone
from dotenv import load_dotenv

# 모듈 경로 및 환경 변수 로드 (API 서버와 동일한 DB를 바라보게 설정)
root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(root_dir)
load_dotenv(os.path.join(root_dir, '.env'))

from apscheduler.schedulers.asyncio import AsyncIOScheduler


from backend.database.models import Base, Community, Deal
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.scrapers.fmkorea_scraper import FmkoreaScraper
from backend.scrapers.ruliweb_scraper import RuliwebScraper
from backend.scrapers.clien_scraper import ClienScraper
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from backend.scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from backend.scrapers.bbasak_parenting_scraper import BbasakParentingScraper
from backend.scrapers.fmkorea_trending_scraper import update_fmkorea_trending_keywords
from backend.services.aggregator_service import AggregatorService

logger = logging.getLogger(__name__)

# 스케줄러 전역 상태 (API 헬스체크 및 대시보드 조회용)
SCHEDULER_STATE = {
    "last_run_time": None,
    "last_status": "Idle",
    "total_run_count": 0
}
COMMUNITY_COOLDOWN_UNTIL = {}

# 🚀 메인 API 웹서버와 완전히 동일한 데이터베이스 파이프라인(session.py) 공유
from backend.database.session import SessionLocal, db_manager

# 이벤트 루프 프리징 차단용 전용 백그라운드 스레드풀 & 커뮤니티 동시성 세마포어
_SCRAPER_BG_EXECUTOR = ThreadPoolExecutor(max_workers=4, thread_name_prefix="scraper-bg")
_COMMUNITY_CONCURRENCY_SEM = asyncio.Semaphore(2)
_INITIAL_STARTUP_DELAY_DONE = False
_PPOMPPU_DETAIL_BACKOFF: dict[str, tuple[int, float]] = {}


async def _run_bg_blocking(fn, *args, **kwargs):
    """동기 DB/BeautifulSoup/파일 I/O 작업을 전용 스레드풀로 격리해 FastAPI 이벤트 루프 블로킹 차단"""
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(_SCRAPER_BG_EXECUTOR, lambda: fn(*args, **kwargs))


def _ensure_community_record_sync(community_name: str) -> tuple[int, str]:
    db = SessionLocal()
    try:
        community = db.query(Community).filter(Community.name == community_name).first()
        if not community:
            name_map = {
                "ppomppu": "뽐뿌", "quasarzone": "퀘이사존", "fmkorea": "펨코",
                "ruliweb": "루리웹", "clien": "클리앙", "ali_ppomppu": "알리뽐뿌",
                "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외",
                "bbasak_parenting": "빠삭육아", "ppomppu_overseas": "뽐뿌해외"
            }
            display_name = name_map.get(community_name, community_name)
            base_url = f"https://{community_name}.co.kr" if "ppomppu" in community_name else f"https://{community_name}.com"
            community = Community(name=community_name, display_name=display_name, base_url=base_url)
            db.add(community)
            db.commit()
            db.refresh(community)
        return community.id, community.display_name
    finally:
        db.close()


def _filter_batch_items_db_sync(items: list[dict], global_seen_urls: set[str]) -> tuple[list[dict], int]:
    """개별 N+1 쿼리 대신 단 1번의 IN 쿼리로 기존 DB 존재 여부 일괄 스캔 (전용 스레드풀 실행)"""
    from backend.core.url_utils import normalize_url

    duplicate_count = 0
    unique_items: list[dict] = []
    check_db = SessionLocal()
    try:
        for item in items:
            norm_url = normalize_url(item['url'])
            if norm_url in global_seen_urls:
                continue
            global_seen_urls.add(norm_url)
            item['url'] = norm_url
            unique_items.append(item)
        if unique_items:
            batch_urls = [it['url'] for it in unique_items]
            existing_url_rows = check_db.query(Deal.post_link).filter(Deal.post_link.in_(batch_urls)).all()
            duplicate_count = len({r[0] for r in existing_url_rows})
    finally:
        check_db.close()
    return unique_items, duplicate_count


def _prepare_worker_item_sync(item: dict) -> tuple[str, bool]:
    """워커 아이템의 중복/델타 스킵 사전 DB 판별 (네트워크 요청 전 DB 연결 즉시 반환)"""
    from backend.core.url_utils import normalize_url
    from backend.database.models import DealReaction

    normalized_url = normalize_url(item['url'])
    item['url'] = normalized_url

    raw_t = item.get("title", "") or ""
    if any(kw in raw_t for kw in ("공지", "정보 안내", "사전 안내", "입고금지", "수익링크", "금지조치", "활동내역", "제재조치", "도와주세요", "어떤가요", "어디까지", "갈수있는데", "할수있는데")):
        return "skip", False

    local_db = SessionLocal()
    try:
        existing_deal = local_db.query(Deal).filter(Deal.post_link == normalized_url).first()
        if not existing_deal:
            existing_deal = local_db.query(Deal).filter(Deal.merged_communities.like(f"%{normalized_url}%")).first()
            if existing_deal and existing_deal.content_html:
                return "skip", False

        if not existing_deal:
            target_url = item.get("ecommerce_link")
            if target_url and len(target_url) > 20 and 'coupang' not in target_url:
                target_url = normalize_url(target_url)
                cutoff_24h = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(hours=24)
                existing_deal = local_db.query(Deal).filter(
                    Deal.ecommerce_link == target_url,
                    Deal.indexed_at >= cutoff_24h,
                ).first()

        if not existing_deal:
            return "new", False

        now = datetime.now(timezone.utc).replace(tzinfo=None)
        indexed_time = existing_deal.indexed_at or now
        if indexed_time.tzinfo is not None:
            now = datetime.now(indexed_time.tzinfo)

        time_diff = now - indexed_time
        existing_html_str = (existing_deal.content_html or "").strip()
        is_truncated_ppomppu = (
            "ppomppu.co.kr" in normalized_url
            and (
                not existing_html_str
                or len(existing_html_str) == 110
                or existing_html_str.endswith("...</p>")
                or existing_html_str.endswith("...")
                or "뽐뿌 - 사람이 좋아 함께하는 곳" in existing_html_str
            )
        )

        if time_diff > timedelta(hours=12):
            return "skip", True

        from backend.scrapers.base_scraper import is_relay_blocked

        now_epoch = time.time()
        attempts, last_ts = _PPOMPPU_DETAIL_BACKOFF.get(normalized_url, (0, 0.0))
        can_retry_ppomppu_detail = (
            is_truncated_ppomppu
            and attempts < 2
            and (now_epoch - last_ts) >= 1800
            and not is_relay_blocked(normalized_url, now_ts=now_epoch)[0]
        )

        reaction = local_db.query(DealReaction).filter(DealReaction.deal_id == existing_deal.id).first()
        if reaction and reaction.last_updated:
            rx_time = reaction.last_updated
            rx_now = datetime.now(rx_time.tzinfo) if rx_time.tzinfo is not None else datetime.now(timezone.utc).replace(tzinfo=None)
            rx_diff = rx_now - rx_time
            if time_diff > timedelta(hours=3) and rx_diff < timedelta(minutes=20) and not can_retry_ppomppu_detail:
                return "skip", True

        if existing_deal.content_html and existing_deal.image_url and not can_retry_ppomppu_detail:
            new_closed = bool(item.get("is_closed", False))
            new_super = bool(item.get("is_super_hotdeal", False))
            new_likes = int(item.get("like_count") or 0)
            new_comments = int(item.get("comment_count") or 0)
            new_views = int(item.get("view_count") or 0)
            if (
                bool(existing_deal.is_closed) == new_closed
                and (bool(existing_deal.is_super_hotdeal) or not new_super)
                and (existing_deal.like_count or 0) == new_likes
                and (existing_deal.comment_count or 0) == new_comments
                and abs((existing_deal.view_count or 0) - new_views) < 30
            ):
                return "skip", True
            return "update_no_detail", True
        if is_truncated_ppomppu:
            if not can_retry_ppomppu_detail:
                return "skip", True
            if len(_PPOMPPU_DETAIL_BACKOFF) >= 2000:
                oldest_key = next(iter(_PPOMPPU_DETAIL_BACKOFF))
                _PPOMPPU_DETAIL_BACKOFF.pop(oldest_key, None)
            _PPOMPPU_DETAIL_BACKOFF[normalized_url] = (attempts + 1, now_epoch)
        return "update_need_detail", True
    finally:
        local_db.close()


def _write_scraper_stats_sync(
    stats_file: str,
    community_display_name: str,
    success_count: int,
    fetched_count: int,
    update_count: int,
) -> None:
    import json
    try:
        stats = {}
        if os.path.exists(stats_file):
            with open(stats_file, 'r', encoding='utf-8') as f:
                stats = json.load(f)
        stats[community_display_name] = {
            "last_count": success_count,
            "fetched_count": fetched_count,
            "updated_count": update_count,
            "last_run": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "status": "Success" if (success_count > 0 or fetched_count > 0) else "Blocked/Failed",
        }
        with open(stats_file, 'w', encoding='utf-8') as f:
            json.dump(stats, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


async def scrape_community(community_name: str, ScraperClass, pages: int = 1):
    """지정된 커뮤니티의 비동기 수집 파이프라인 태스크 (Producer-Consumer 큐 방식 적용)"""
    cooldown_until = COMMUNITY_COOLDOWN_UNTIL.get(community_name)
    if cooldown_until and datetime.now() < cooldown_until:
        logger.info(f"⏳ [{community_name}] 430 쿨다운 보호 대기 중 (~{cooldown_until.strftime('%H:%M:%S')}) - 이번 주기는 건너뜁니다.")
        return 0

    async with _COMMUNITY_CONCURRENCY_SEM:
        community_id, community_display_name = await _run_bg_blocking(_ensure_community_record_sync, community_name)

        try:
            success_count = 0
            update_count = 0
            fetched_count = 0
            scraper = ScraperClass(community_id=community_id)
            queue = asyncio.Queue()

            # [Phase 13] Async Queue 기반 Consumer Worker 정의 (병렬 스크래핑 및 DB 저장)
            async def worker():
                nonlocal success_count, update_count
                from backend.core.url_utils import normalize_url

                while True:
                    item = await queue.get()
                    if item is None:
                        queue.task_done()
                        break

                    try:
                        action, existed = await _run_bg_blocking(_prepare_worker_item_sync, item)
                        await asyncio.sleep(0)
                        if action == "skip":
                            queue.task_done()
                            continue

                        normalized_url = item['url']
                        if action == "new":
                            detail = {}
                            if item.get("page", 1) <= 3:
                                detail = await scraper.get_detail(normalized_url, item.get("title", ""))
                        elif action == "update_need_detail":
                            detail = await scraper.get_detail(normalized_url, item.get("title", ""))
                        else:
                            detail = {}

                        if detail:
                            if detail.get("ecommerce_link"):
                                detail["ecommerce_link"] = normalize_url(detail["ecommerce_link"])
                            for k, v in detail.items():
                                if k == "price" and (not v or v == 0) and item.get("price", 0) > 0:
                                    continue
                                if v is not None and v != "":
                                    item[k] = v

                        local_db = SessionLocal()
                        try:
                            aggregator = AggregatorService(local_db)
                            deal = await aggregator.process_scraped_deal(community_id, item)
                            if deal:
                                if not existed:
                                    success_count += 1
                                else:
                                    update_count += 1
                        except Exception as e:
                            local_db.rollback()
                            logger.error(f"[{community_display_name}] 데이터 처리 중 에러: {e}")
                        finally:
                            local_db.close()
                    except Exception as e:
                        logger.error(f"[{community_display_name}] 워커 파이프라인 에러: {e}")
                    queue.task_done()

            async with scraper:
                num_workers = 1 if "fmkorea" in community_name else 2
                logger.info(f"▶ [{community_display_name}] 큐 기반 스크래핑 워커 가동 (pages={pages}, workers={num_workers})")

                workers = [asyncio.create_task(worker()) for _ in range(num_workers)]
                global_seen_urls = set()

                # Producer: 리스트 페이지를 긁어서 Queue에 삽입
                for page in range(1, pages + 1):
                    if page > 1:
                        if "clien" in community_name:
                            sep = "&" if "?" in scraper.list_url else "?"
                            target_url = f"{scraper.list_url}{sep}po={page-1}"
                        else:
                            sep = "&" if "?" in scraper.list_url else "?"
                            target_url = f"{scraper.list_url}{sep}page={page}"
                    else:
                        target_url = scraper.list_url

                    try:
                        html = await scraper.fetch_html(target_url)
                        if html:
                            items = await _run_bg_blocking(lambda h=html: asyncio.run(scraper.parse_list(h)))
                            await asyncio.sleep(0)

                            if not items:
                                break
                            fetched_count += len(items)

                            unique_items, duplicate_count = await _run_bg_blocking(
                                _filter_batch_items_db_sync, items, global_seen_urls
                            )
                            await asyncio.sleep(0)

                            for item in unique_items:
                                item['page'] = page
                                await queue.put(item)

                            if unique_items and duplicate_count >= len(unique_items) - 1:
                                if page > 3:
                                    logger.info(f"⏭️ [{community_display_name}] {page}페이지 대부분({duplicate_count}/{len(items)})이 기존 딜입니다. 백필 구간이 연결되었으므로 조기 종료합니다.")
                                    break
                                else:
                                    logger.info(f"ℹ️ [{community_display_name}] {page}페이지 대부분({duplicate_count}/{len(items)})이 기존 딜이지만, 상태 갱신을 위해 스캔을 계속합니다.")
                    except Exception as e:
                        logger.error(f"[{community_display_name}] 리스트 페이지 {page} 파싱 에러: {e}")
                        if "430" in str(e):
                            COMMUNITY_COOLDOWN_UNTIL[community_name] = datetime.now() + timedelta(minutes=12)
                            scraper.parsing_pop = False
                            setattr(scraper, "_skip_pop_due_to_430", True)
                        break

                if hasattr(scraper, 'pop_url') and getattr(scraper, 'pop_url') and not getattr(scraper, '_skip_pop_due_to_430', False):
                    try:
                        logger.info(f"▶ [{community_display_name}] 핫딜 승격 감지용 인기글 페이지 스크래핑 추가 실행")
                        scraper.parsing_pop = True
                        raw_html = await scraper.fetch_html(scraper.pop_url)
                        if raw_html:
                            items = await _run_bg_blocking(lambda h=raw_html: asyncio.run(scraper.parse_list(h)))
                            await asyncio.sleep(0)
                            for item in items:
                                await queue.put(item)
                        scraper.parsing_pop = False
                    except Exception as e:
                        logger.error(f"[{community_display_name}] 인기글 페이지 파싱 에러: {e}")
                        scraper.parsing_pop = False

                await queue.join()

                for _ in range(num_workers):
                    await queue.put(None)

                await asyncio.gather(*workers)

                logger.info(f"✅ [{community_display_name}] 스크래핑 성공 (신규: {success_count}건, 갱신(중복): {update_count}건)")

                stats_file = os.path.join(root_dir, "logs", "scraper_stats.json")
                os.makedirs(os.path.dirname(stats_file), exist_ok=True)
                await _run_bg_blocking(
                    _write_scraper_stats_sync,
                    stats_file,
                    community_display_name,
                    success_count,
                    fetched_count,
                    update_count,
                )

                return success_count
        except Exception as e:
            logger.error(f"❌ [{community_name}] 파이프라인 크롤링 에러: {e}")
            return 0


def _load_recent_open_deals_sync() -> list[tuple[int, str, str | None, str]]:
    db = SessionLocal()
    try:
        target_date = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(days=7)
        rows = (
            db.query(Deal.id, Deal.post_link, Deal.merged_communities, Deal.title)
            .filter(
                Deal.is_closed == False,
                Deal.indexed_at >= target_date,
                ~Deal.post_link.contains("fmkorea.com"),
                ~Deal.post_link.contains("fmkorea.org"),
            )
            .order_by(Deal.indexed_at.desc())
            .limit(200)
            .all()
        )
        return [(int(r[0]), r[1] or "", r[2], r[3] or "") for r in rows]
    finally:
        db.close()


def _decode_validator_html(resp_content: bytes, url: str = "") -> str:
    if not resp_content:
        return ""
    if "ppomppu.co.kr" in (url or "").lower():
        try:
            decoded_euc = resp_content.decode("euc-kr", errors="replace")
            head_low = decoded_euc[:1200].lower()
            if "charset=utf-8" in head_low or 'charset="utf-8"' in head_low:
                return resp_content.decode("utf-8", errors="replace")
            return decoded_euc
        except Exception:
            pass
    return resp_content.decode("utf-8", errors="replace")


def _detect_closed_soup_sync(resp_content: bytes, url: str = "") -> bool:
    from bs4 import BeautifulSoup
    html_text = _decode_validator_html(resp_content, url)
    soup = BeautifulSoup(html_text, 'html.parser')
    title_tag = soup.title.string if soup.title and soup.title.string else ""
    if any(kw in title_tag for kw in ["종료", "마감", "품절", "블라인드", "삭제", "완료"]):
        return True
    header_el = soup.select_one('h1, h3, .board-title, .view_title .tit, .view_title2, #topTitle, .subject, span.end, .market-end')
    if header_el and any(kw in header_el.get_text(" ", strip=True) for kw in ["종료", "마감", "품절", "블라인드", "삭제"]):
        return True

    url_low = (url or "").lower()
    if "ppomppu.co.kr" in url_low or soup.select_one("#topTitle, .view_title2"):
        if soup.select_one("img[src*='end_icon'], img[src*='end.gif'], .end2"):
            return True
        pp_top = soup.select_one("#topTitle, .view_title2, .topTitle-mainbox")
        if pp_top and ("line-through" in str(pp_top).lower() or any(kw in pp_top.get_text(" ", strip=True) for kw in ("종료", "품절", "마감"))):
            return True

    if "clien.net" in url_low or soup.select_one(".post_subject"):
        cl_badge = soup.select_one(".post_subject .icon_info, .post_subject .sold_out, .sold_out")
        if cl_badge and any(kw in cl_badge.get_text(" ", strip=True) for kw in ("품절", "종료", "마감")):
            return True
        cl_subj = soup.select_one(".post_subject")
        if cl_subj and ("품절" in cl_subj.get_text(" ", strip=True) or "line-through" in str(cl_subj).lower()):
            return True

    if "ruliweb.com" in url_low or soup.select_one(".subject_inner_text"):
        rl_subj = soup.select_one("span.subject_inner_text, .subject_text, h4.subject")
        if rl_subj and (any(kw in rl_subj.get_text(" ", strip=True) for kw in ("종료", "품절", "마감")) or rl_subj.select_one("del, strike, .subject_end") or "line-through" in str(rl_subj).lower()):
            return True
        if soup.select_one(".subject_end"):
            return True

    if "bbasak.com" in url_low or soup.select_one("#bo_v_title, .bo_v_tit, .view_title .tit"):
        bb_tit = soup.select_one("#bo_v_title, .bo_v_tit, .bo_v_title, .view_title .tit, .view_title p.s_tit")
        if bb_tit and (any(kw in bb_tit.get_text(" ", strip=True) for kw in ("종료", "품절", "마감")) or "line-through" in str(bb_tit).lower() or bb_tit.select_one("img[src*='end'], img[src*='soldout'], .end")):
            return True

    if "quasarzone.com" in url_low or soup.select_one(".v2-list-row, .market-info-view-top"):
        if soup.select_one(".label.done, .v2-thumb-done, .market-info-view-top .done"):
            return True

    return False


_BRIDGE_STALE_EXPIRE_COMMUNITIES = (
    "ppomppu",
    "ali_ppomppu",
    "ppomppu_overseas",
    "quasarzone",
)


def _auto_expire_stale_deals_sync(
    max_age_days: int = 14,
    bridge_max_age_days: int | None = None,
) -> int:
    """장기(14일+) 미갱신 활성 딜 및 해외 IP 차단 브릿지 의존 게시판 단기 만료 딜을 자동 마감 처리합니다."""
    db = SessionLocal()
    try:
        now_naive = datetime.now(timezone.utc).replace(tzinfo=None)
        cutoff = now_naive - timedelta(days=max_age_days)
        updated = int(
            db.query(Deal)
            .filter(Deal.is_closed == False, Deal.indexed_at < cutoff)
            .update({Deal.is_closed: True}, synchronize_session=False)
            or 0
        )
        bridge_updated = 0
        if bridge_max_age_days is not None and bridge_max_age_days > 0:
            bridge_cutoff = now_naive - timedelta(days=bridge_max_age_days)
            bridge_cids = [
                int(r[0])
                for r in db.query(Community.id)
                .filter(Community.name.in_(_BRIDGE_STALE_EXPIRE_COMMUNITIES))
                .all()
            ]
            if bridge_cids:
                bridge_updated = int(
                    db.query(Deal)
                    .filter(
                        Deal.is_closed == False,
                        Deal.source_community_id.in_(bridge_cids),
                        Deal.indexed_at < bridge_cutoff,
                    )
                    .update({Deal.is_closed: True}, synchronize_session=False)
                    or 0
                )
        total_updated = updated + bridge_updated
        if total_updated:
            db.commit()
            logger.info(
                f"🧹 [Auto-Expire] {max_age_days}일 경과 {updated}건 + "
                f"브릿지({bridge_max_age_days}일) 경과 {bridge_updated}건 자동 마감 완료"
            )
        return total_updated
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


def _apply_validator_result_db_sync(
    deal_id: int,
    deal_title: str,
    is_repr_deleted: bool,
    valid_merged: list[str],
    new_merged_str: str | None,
    merged_changed: bool,
) -> int:
    if not is_repr_deleted and not merged_changed:
        return 0
    closed_inc = 0
    db = SessionLocal()
    try:
        deal = db.query(Deal).filter(Deal.id == deal_id).first()
        if not deal:
            return 0
        if new_merged_str != deal.merged_communities:
            deal.merged_communities = new_merged_str
            merged_changed = True

        if is_repr_deleted:
            if valid_merged:
                chosen_item = valid_merged[0]
                parts = chosen_item.split("::")
                new_cid = int(parts[0])
                new_url = parts[1]
                old_url = deal.post_link

                deal.source_community_id = new_cid
                deal.post_link = new_url

                sub_deal_in_db = db.query(Deal).filter(Deal.post_link == new_url).first()
                if sub_deal_in_db and sub_deal_in_db.indexed_at:
                    deal.indexed_at = sub_deal_in_db.indexed_at
                    logger.info(f"⏰ [Validator-Healing] 대표 딜의 indexed_at 시간을 서브 딜의 시간({sub_deal_in_db.indexed_at})으로 동기화 완료")

                rem_merged = [m for m in valid_merged if m != chosen_item]
                deal.merged_communities = ",".join(rem_merged) if rem_merged else None
                logger.info(f"🔄 [Validator-Healing] 대표 딜 삭제 감지 -> 서브 딜로 대표 교체 승격 완료! ({old_url} -> {new_url})")
                db.commit()
            else:
                deal.is_closed = True
                closed_inc = 1
                db.commit()
                logger.info(f"🚫 [Validator-Closed] 대표 및 서브 딜 모두 삭제 감지 -> 핫딜 종료: {deal_title}")
        elif merged_changed:
            db.commit()
            logger.info(f"💾 [Validator-Sync] 서브 딜 링크 일부 삭제로 merged_communities 갱신 완료: {deal_title}")
        return closed_inc
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


async def validate_closed_deals():
    """과거 핫딜(3일 이내) 품절 상태(Ping) 검증 데몬 (병합 서브 딜 및 삭제 뱃지 실시간 소거 및 자가치유 탑재)"""
    from curl_cffi.requests import AsyncSession

    try:
        logger.info("🔍 [Background] 과거 핫딜(3일 이내) 품절 상태 검증 데몬 가동")
        deal_targets = await _run_bg_blocking(_load_recent_open_deals_sync)
        await asyncio.sleep(0)

        closed_count = 0
        headers = {
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        }

        async with AsyncSession(impersonate='chrome120', timeout=10.0) as client:
            for deal_id, post_link, orig_merged_str, deal_title in deal_targets:
                try:
                    async def check_url_deleted(url: str) -> bool:
                        if not url or url == "#":
                            return True
                        if "fmkorea" in url:
                            return False
                        try:
                            await asyncio.sleep(0.4)
                            req_headers = headers.copy()
                            if "ppomppu" in url:
                                req_headers["Referer"] = "https://www.ppomppu.co.kr/"
                            elif "quasarzone" in url:
                                req_headers["Referer"] = "https://quasarzone.com/"
                            elif "bbasak" in url:
                                req_headers["Referer"] = "https://bbasak.com/"
                            elif "ruliweb" in url:
                                req_headers["Referer"] = "https://bbs.ruliweb.com/"

                            resp = await client.get(url, headers=req_headers)
                            if resp.status_code in [403, 430] and "ppomppu.co.kr" in url and "m.ppomppu.co.kr" not in url:
                                import re as _re
                                m_bid = _re.search(r"id=([a-zA-Z0-9_]+)", url)
                                m_bno = _re.search(r"no=(\d+)", url)
                                if m_bno:
                                    bid = m_bid.group(1) if m_bid else "ppomppu"
                                    mob_u = f"https://m.ppomppu.co.kr/new/bbs_view.php?id={bid}&no={m_bno.group(1)}"
                                    mob_headers = req_headers.copy()
                                    mob_headers["Referer"] = "https://m.ppomppu.co.kr/"
                                    resp = await client.get(mob_u, headers=mob_headers)
                            if resp.status_code == 404:
                                return True
                            elif resp.status_code in [403, 430]:
                                return False

                            resp_text = _decode_validator_html(resp.content, str(resp.url))

                            if "quasarzone" in url:
                                if "history.back();" in resp_text and "location.href = redirect;" in resp_text:
                                    return True

                            if "dispMemberLoginForm" in str(resp.url) or "dispMemberLoginForm" in resp_text:
                                return True

                            if any(kw in resp_text for kw in [
                                "해당 문서가 존재하지 않습니다",
                                "해당 문서는 존재하지 않습니다",
                                "삭제되었거나 존재하지 않는",
                                "존재하지 않는 게시물",
                                "삭제된 게시글",
                                "삭제된 글"
                            ]):
                                return True

                            is_closed_html = await _run_bg_blocking(_detect_closed_soup_sync, resp.content, url)
                            await asyncio.sleep(0)
                            return is_closed_html
                        except Exception as e:
                            logger.error(f"[Validator Ping Error] {url}: {e}")
                            return False

                    is_repr_deleted = await check_url_deleted(post_link)

                    merged_changed = False
                    valid_merged = []

                    if orig_merged_str:
                        merged_items = [item.strip() for item in orig_merged_str.split(",") if item.strip()]
                        for item in merged_items:
                            parts = item.split("::")
                            if len(parts) < 2:
                                valid_merged.append(item)
                                continue

                            cid = parts[0]
                            sub_url = parts[1]

                            is_sub_deleted = await check_url_deleted(sub_url)
                            if is_sub_deleted:
                                merged_changed = True
                                logger.info(f"🗑️ [Validator] 삭제된 병합 딜 링크 감지되어 제거: ID {cid} -> {sub_url}")
                            else:
                                valid_merged.append(item)

                    new_merged_str = ",".join(valid_merged) if valid_merged else None
                    if new_merged_str != orig_merged_str:
                        merged_changed = True

                    closed_count += await _run_bg_blocking(
                        _apply_validator_result_db_sync,
                        deal_id,
                        deal_title,
                        is_repr_deleted,
                        valid_merged,
                        new_merged_str,
                        merged_changed,
                    )
                    await asyncio.sleep(0)

                except Exception as deal_err:
                    logger.error(f"[Validator Deal Error] Deal ID {deal_id}: {deal_err}")

        stale_expired = await _run_bg_blocking(_auto_expire_stale_deals_sync, 14, 5)
        logger.info(f"✅ 상태 검증 완료: 총 {len(deal_targets)}개 핑(Ping) 테스트 수행 -> {closed_count}개 품절 처리 (장기만료 {stale_expired}건)")
    except Exception as e:
        logger.error(f"❌ 상태 검증 데몬 에러: {e}")


def _compute_pipeline_gap_hours_sync() -> float:
    db = SessionLocal()
    try:
        last_deal = db.query(Deal).order_by(Deal.indexed_at.desc()).first()
        if last_deal and last_deal.indexed_at:
            now = datetime.now(timezone.utc).replace(tzinfo=None)
            last_time = last_deal.indexed_at
            if last_time.tzinfo is not None:
                now = datetime.now(last_time.tzinfo)
            return (now - last_time).total_seconds() / 3600.0
        return 0.0
    finally:
        db.close()


async def run_pipeline_job():
    """
    자가 치유 복구(Self-Healing Backfill)가 내장된 투트랙 오케스트레이션 엔진:
    - 매 3분 사이클: 단기 수집 (1페이지 초고속 - 신규 글 즉시 반영)
    - 매 2번째 사이클 (6분): 심층 수집 (1~2페이지 - 핫딜마크 및 추천/댓글수 갱신)
    """
    global _INITIAL_STARTUP_DELAY_DONE
    if not _INITIAL_STARTUP_DELAY_DONE:
        _INITIAL_STARTUP_DELAY_DONE = True
        if "--one-shot" not in sys.argv:
            logger.info("⏳ [Startup] 초기 부팅 안정화를 위해 8초 지연 후 첫 파이프라인을 시작합니다.")
            await asyncio.sleep(8.0)

    logger.info("====================================")

    pages_to_scrape = 1
    is_backfill_mode = False
    gap_hours = 0.0

    try:
        gap_hours = await _run_bg_blocking(_compute_pipeline_gap_hours_sync)
        # 수집 공백이 2시간을 초과한 경우, 첫 사이클 병목을 막기 위해 최대 2페이지로 빠르게 최신 글부터 확보
        if gap_hours > 2.0 and "--one-shot" not in sys.argv:
            is_backfill_mode = True
            pages_to_scrape = 2
    except Exception as e:
        logger.error(f"[Self-Healing] 수집 공백 분석 실패: {e}")
        
    if "--one-shot" in sys.argv:
        pages_to_scrape = 1
        mode_str = "⚡ 즉시 동기화 (1페이지 초고속 수집)"
    elif not is_backfill_mode:
        is_deep_scan = (SCHEDULER_STATE["total_run_count"] % 2 == 0)
        pages_to_scrape = 2 if is_deep_scan else 1
        mode_str = "핫딜마크 추적대(1~2페이지)" if is_deep_scan else "신규글 감시조(1페이지)"
    else:
        mode_str = f"🔥 고속 백필 복구 가동 (공백 {gap_hours:.1f}시간 감지 -> {pages_to_scrape}페이지 우선 추적)"
        
    logger.info(f"🚀 [Background] 정기 핫딜 동시 수집 파이프라인 가동 - 모드: {mode_str}")
    logger.info("====================================")
    
    SCHEDULER_STATE["last_status"] = f"Running ({mode_str})"
    
    try:
        # 각 스크래퍼가 독립적인 DB Session을 사용하므로, asyncio.gather 시 데드락 방지 완벽 보장
        tasks = [
            scrape_community("ppomppu", PpomppuScraper, pages_to_scrape),
            scrape_community("ppomppu_overseas", PpomppuOverseasScraper, pages_to_scrape),
            scrape_community("quasarzone", QuasarzoneScraper, pages_to_scrape),
            scrape_community("fmkorea", FmkoreaScraper, pages_to_scrape),
            scrape_community("ruliweb", RuliwebScraper, pages_to_scrape),
            scrape_community("clien", ClienScraper, min(pages_to_scrape, 2)),
            scrape_community("ali_ppomppu", AlippomppuScraper, pages_to_scrape),
            scrape_community("bbasak_domestic", BbasakDomesticScraper, pages_to_scrape),
            scrape_community("bbasak_overseas", BbasakOverseasScraper, pages_to_scrape),
            scrape_community("bbasak_parenting", BbasakParentingScraper, pages_to_scrape)
        ]
        
        results = await asyncio.gather(*tasks)
        total_collected = sum(results)
        
        SCHEDULER_STATE["last_run_time"] = datetime.now()
        SCHEDULER_STATE["last_status"] = f"Success ({total_collected} deals process)"
        SCHEDULER_STATE["total_run_count"] += 1
        
        try:
            from backend.routers.community import invalidate_feed_api_cache
            invalidate_feed_api_cache(clear_ecom_url_cache=False)
        except Exception:
            pass
        
        logger.info(f"🎉 파이프라인 사이클 완료! 총 {total_collected}건의 Deal 데이터가 갱신되었습니다.")
        logger.info("====================================\n")
        
    except Exception as e:
        SCHEDULER_STATE["last_status"] = f"Error: {e}"
        logger.error(f"❌ 전체 파이프라인 사이클 붕괴 에러: {e}")

def db_maintenance_job():
    """
    🧹 [새벽 4시 통합 일일 유지보수]
    1) 180일 경과 딜 본문 슬림화
    2) SQLite WAL 체크포인트 & 온라인 백업 (최근 3일치 유지)
    3) 이미지 캐시 7일 만료 및 500MB 상한선 LRU 자동 정제 (GCP 디스크 용량 영구 방어)
    """
    try:
        summary = db_manager.perform_maintenance_and_backup(
            max_backups=3, 
            slim_days=180,
            clean_image_cache=True,
            cache_max_age_days=7,
            cache_max_mb=500
        )
        cleaned_pairing = 0
        try:
            from backend.database.session import SessionLocal
            from backend.routers.auth import cleanup_expired_pairing_codes
            with SessionLocal() as cleanup_db:
                cleaned_pairing = cleanup_expired_pairing_codes(cleanup_db, force=True)
        except Exception as pairing_err:
            logger.warning(f"⚠️ 만료 기기 동기화 코드 정리 건너뜀: {pairing_err}")
        stale_expired = 0
        try:
            stale_expired = _auto_expire_stale_deals_sync(14, bridge_max_age_days=5)
        except Exception as stale_err:
            logger.warning(f"⚠️ 장기 미갱신 딜 자동 마감 건너뜀: {stale_err}")
        logger.info(f"✅ [새벽 4시 통합 유지보수 성공] {summary} | pairing_cleaned={cleaned_pairing} | stale_expired={stale_expired}")
    except Exception as e:
        logger.error(f"❌ DB 일일 안전 백업 및 최적화 작업 중 오류: {e}")

async def dispatch_github_cloud_feeder_job():
    """
    ⏰ [24/7 클라우드 무정지 스케줄러]
    GHA 내장 cron의 드롭 및 지연을 방지하기 위해 24시간 가동 중인 백엔드 스케줄러가
    매 10분마다 GitHub Actions workflow_dispatch API를 호출하여 클라우드 피더를 정각에 격발합니다.
    """
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if not token:
        logger.warning("⚠️ [Cloud Scheduler] GITHUB_TOKEN 환경변수가 설정되지 않아 GHA 격발을 건너뜁니다.")
        return
    repo = "ddaeany0919/InsightDeal"
    url = f"https://api.github.com/repos/{repo}/actions/workflows/cloud-feeder.yml/dispatches"
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "InsightDeal-Scheduler"
    }
    payload = {"ref": "main"}
    try:
        import httpx
        async with httpx.AsyncClient(timeout=10.0) as client:
            res = await client.post(url, headers=headers, json=payload)
            if res.status_code in (200, 204):
                logger.info("🚀 [Cloud Scheduler] GitHub Actions cloud-feeder.yml 워크플로우 정시 격발 성공 (HTTP 204)")
            else:
                logger.warning(f"⚠️ [Cloud Scheduler] GitHub Actions 격발 응답: {res.status_code} - {res.text[:100]}")
    except Exception as e:
        logger.warning(f"⚠️ [Cloud Scheduler] GitHub Actions 격발 실패: {e}")

from backend.services.push_worker import dnd_morning_digest_job


def start_scheduler():
    db_manager.init_database()
    scheduler = AsyncIOScheduler()
    # 핫딜 수집 데몬 (3분 주기 - 알구몬급 실시간 반영)
    scheduler.add_job(run_pipeline_job, 'interval', minutes=3, id='hotdeal_pipeline')
    # 24/7 GitHub Actions 클라우드 피더 정시 디스패처 (10분 주기)
    scheduler.add_job(dispatch_github_cloud_feeder_job, 'interval', minutes=10, id='github_cloud_feeder_dispatcher')
    # 과거 딜 품절/종료 검증 데몬 (10분 주기)
    scheduler.add_job(validate_closed_deals, 'interval', minutes=10, id='hotdeal_validator')
    # 펨코 실시간 급상승 검색어 수집 (1시간 주기, 정각 실행)
    scheduler.add_job(
        update_fmkorea_trending_keywords,
        'cron',
        minute=0,
        id='fmkorea_trending_keywords',
        replace_existing=True,
    )
    # ☀️ DND 해제 시각 아침 요약 푸시 배치 (5분 주기 점검)
    scheduler.add_job(
        dnd_morning_digest_job,
        'interval',
        minutes=5,
        id='dnd_morning_digest',
        replace_existing=True,
    )
    # 🧹 DB 유지보수 및 이미지 캐시 LRU 정제 통합 배치 (매일 새벽 4시 실행)
    scheduler.add_job(db_maintenance_job, 'cron', hour=4, minute=0, id='db_maintenance')
    scheduler.start()
    logger.info("⏰ [System] 투트랙 스케줄러(3분 수집 / 10분 GHA디스패치 / 10분 품절검증) 시동 완료")
    return scheduler

if __name__ == "__main__":
    # 이 파일을 직접 실행 시 엔진 즉시 가동용입니다.
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    
    scheduler = start_scheduler()
    
    import asyncio
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    
    # 1. 켜자마자 바로 1회 돌려보기
    loop.run_until_complete(run_pipeline_job())
    
    if "--one-shot" in sys.argv:
        logger.info("👋 [One-Shot Mode] 1 사이클 수집 완료. 백그라운드 프로세스를 종료합니다.")
        sys.exit(0)
    
    try:
        loop.run_forever()
    except (KeyboardInterrupt, SystemExit):
        pass
