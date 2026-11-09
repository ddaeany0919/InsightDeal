import asyncio
import logging
import os
import sys
from datetime import datetime
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

async def scrape_community(community_name: str, ScraperClass, pages: int = 1):
    """지정된 커뮤니티의 비동기 수집 파이프라인 태스크 (Producer-Consumer 큐 방식 적용)"""
    cooldown_until = COMMUNITY_COOLDOWN_UNTIL.get(community_name)
    if cooldown_until and datetime.now() < cooldown_until:
        logger.info(f"⏳ [{community_name}] 430 쿨다운 보호 대기 중 (~{cooldown_until.strftime('%H:%M:%S')}) - 이번 주기는 건너뜁니다.")
        return 0
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
        community_id = community.id
        community_display_name = community.display_name
    finally:
        db.close() # 메인 트랜잭션 종료 (워커들이 개별 세션 사용)

    try:
        success_count = 0
        update_count = 0
        fetched_count = 0
        scraper = ScraperClass(community_id=community_id)
        queue = asyncio.Queue()

        # [Phase 13] Async Queue 기반 Consumer Worker 정의 (병렬 스크래핑 및 DB 저장)
        async def worker():
            nonlocal success_count, update_count
            while True:
                item = await queue.get()
                if item is None:
                    queue.task_done()
                    break
                
                # 각 워커별 독립적인 DB 세션 생성 (동시성 데드락 및 세션 오염 완벽 차단)
                local_db = SessionLocal()
                try:
                    from backend.core.url_utils import normalize_url
                    normalized_url = normalize_url(item['url'])
                    item['url'] = normalized_url  # 큐 내부 아이템의 URL도 정규화된 규격으로 통일
                    
                    # 0. 게시판 공지/안내 스팸글은 상세 페이지 요청조차 하지 않고 즉시 스킵
                    raw_t = item.get("title", "") or ""
                    if any(kw in raw_t for kw in ("공지", "정보 안내", "사전 안내", "입고금지", "수익링크", "금지조치", "활동내역", "제재조치", "도와주세요", "어떤가요", "어디까지", "갈수있는데", "할수있는데")):
                        local_db.close()
                        queue.task_done()
                        continue

                    # 1. Exact Match 또는 이미 병합된 서브 커뮤니티 링크(merged_communities) 여부 판별
                    existing_deal = local_db.query(Deal).filter(Deal.post_link == normalized_url).first()
                    if not existing_deal:
                        existing_deal = local_db.query(Deal).filter(Deal.merged_communities.like(f"%{normalized_url}%")).first()
                        if existing_deal and existing_deal.content_html:
                            local_db.close()
                            queue.task_done()
                            continue
                    
                    # 2. 롤링 윈도우 (24시간 내 동일 상품명/쇼핑몰 링크) 사전 검출로 중복상세 원천 차단
                    if not existing_deal:
                        from datetime import timedelta
                        # 24시간 이내 동일 글/상품 검출 (동일 쇼핑몰 링크 우선 매칭)
                        target_url = item.get("ecommerce_link")
                        if target_url and len(target_url) > 20 and 'coupang' not in target_url:
                            target_url = normalize_url(target_url)
                            existing_deal = local_db.query(Deal).filter(
                                Deal.ecommerce_link == target_url,
                                Deal.indexed_at >= datetime.utcnow() - timedelta(hours=24)
                            ).first()
                    
                    if not existing_deal:
                        # 1. 완전한 신규 딜인 경우 ➔ 상세 페이지(HTML, AI 등) 파싱
                        # (최적화) 1~3페이지 게시글만 상세 파싱(고화질/컨텐츠 추출) 수행하여 속도 향상
                        detail = {}
                        if item.get("page", 1) <= 3:
                            detail = await scraper.get_detail(normalized_url, item.get("title", ""))
                        
                        if detail:
                            # 상세 페이지 내부의 외부 링크가 있다면 그것도 정규화
                            if detail.get("ecommerce_link"):
                                detail["ecommerce_link"] = normalize_url(detail["ecommerce_link"])
                            # [Smart Merge Guard] 빈 문자열이나 None, 혹은 0원 가격으로 인해 목록 단의 유효값 유실 방지
                            for k, v in detail.items():
                                if k == "price" and (not v or v == 0) and item.get("price", 0) > 0:
                                    continue
                                if v is not None and v != "":
                                    item[k] = v
                    else:
                        # 2. 기존 수집된 딜인 경우 ➔ 시간 기반 스마트 델타 스킵 가드 (Time-based Delta Skip Guard) 적용!
                        from datetime import datetime, timedelta
                        now = datetime.utcnow()
                        indexed_time = existing_deal.indexed_at
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
                            )
                        )
                        
                        # 가드 1: 이미 작성된 지 12시간이 경과한 노후 핫딜은 핫딜마크 달성 기한이 끝났으므로 갱신 연산 스킵! (단, 110자 축약 뽐뿌/알리뽐뿌 딜은 본문 복구 허용)
                        if time_diff > timedelta(hours=12) and not is_truncated_ppomppu:
                            local_db.close()
                            queue.task_done()
                            continue
                            
                        # 가드 2: 3~12시간 사이의 안정기 글은 최근 20분 이내에 갱신되었다면 DB 및 I/O 절감을 위해 스킵!
                        from backend.database.models import DealReaction
                        reaction = local_db.query(DealReaction).filter(DealReaction.deal_id == existing_deal.id).first()
                        if reaction and reaction.last_updated:
                            rx_time = reaction.last_updated
                            if rx_time.tzinfo is not None:
                                now = datetime.now(rx_time.tzinfo)
                            rx_diff = now - rx_time
                            
                            if time_diff > timedelta(hours=3) and rx_diff < timedelta(minutes=20) and not is_truncated_ppomppu:
                                local_db.close()
                                queue.task_done()
                                continue
                        
                        # [Lazy-Loading & Zero-Cost Delta Skip 최적화 핵심]:
                        # 상세 정보(본문 및 대표 썸네일 이미지)가 이미 모두 있고, 추천수/댓글수/품절여부/인기여부가 변하지 않았다면 무거운 정규화·병합 연산을 0ms로 즉시 스킵!
                        if existing_deal.content_html and existing_deal.image_url and not is_truncated_ppomppu:
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
                                local_db.close()
                                queue.task_done()
                                continue
                        else:
                            detail = await scraper.get_detail(normalized_url, item.get("title", ""))
                            if detail:
                                if detail.get("ecommerce_link"):
                                    detail["ecommerce_link"] = normalize_url(detail["ecommerce_link"])
                                # [Smart Merge Guard] 빈 문자열이나 None, 혹은 0원 가격으로 인해 목록 단의 유효값 유실 방지
                                for k, v in detail.items():
                                    if k == "price" and (not v or v == 0) and item.get("price", 0) > 0:
                                        continue
                                    if v is not None and v != "":
                                        item[k] = v
                    
                    aggregator = AggregatorService(local_db)
                    deal = await aggregator.process_scraped_deal(community_id, item)
                    if deal:
                        if not existing_deal:
                            success_count += 1
                        else:
                            update_count += 1
                except Exception as e:
                    local_db.rollback()
                    logger.error(f"[{community_display_name}] 데이터 처리 중 에러: {e}")
                local_db.close()
                queue.task_done()

        async with scraper:
            num_workers = 1 if "fmkorea" in community_name else 3
            logger.info(f"▶ [{community_display_name}] 큐 기반 스크래핑 워커 가동 (pages={pages}, workers={num_workers})")
            
            # SQLite WAL 모드 + busy_timeout=60000 환경에서 네트워크 I/O 병렬화를 위해 워커 다중화 (펨코는 WAF 보호용 1개 유지)
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
                        items = await scraper.parse_list(html)
                        
                        if not items:
                            break
                        fetched_count += len(items)
                            
                        # [최적화] 개별 N+1 쿼리 대신 단 1번의 IN 쿼리로 기존 DB 존재 여부 일괄 스캔
                        duplicate_count = 0
                        check_db = SessionLocal()
                        unique_items = []
                        try:
                            from backend.core.url_utils import normalize_url
                            for item in items:
                                norm_url = normalize_url(item['url'])
                                if norm_url in global_seen_urls:
                                    continue
                                global_seen_urls.add(norm_url)
                                item['url'] = norm_url # 아이템의 url을 정규화된 것으로 미리 치환
                                unique_items.append(item)
                            if unique_items:
                                batch_urls = [it['url'] for it in unique_items]
                                existing_url_rows = check_db.query(Deal.post_link).filter(Deal.post_link.in_(batch_urls)).all()
                                duplicate_count = len({r[0] for r in existing_url_rows})
                        finally:
                            check_db.close()
                            
                        for item in unique_items:
                            item['page'] = page
                            await queue.put(item)
                            
                        # 핫딜 종료/점수 강등 상태 업데이트를 위해 페이지 조기 종료 스킵 (1~3페이지 모두 스캔 보장)
                        if unique_items and duplicate_count >= len(unique_items) - 1:
                            if page > 3:
                                logger.info(f"⏭️ [{community_display_name}] {page}페이지 대부분({duplicate_count}/{len(items)})이 기존 딜입니다. 백필 구간이 연결되었으므로 조기 종료합니다.")
                                break
                            else:
                                logger.info(f"ℹ️ [{community_display_name}] {page}페이지 대부분({duplicate_count}/{len(items)})이 기존 딜이지만, 상태 갱신을 위해 스캔을 계속합니다.")
                except Exception as e:
                    logger.error(f"[{community_display_name}] 리스트 페이지 {page} 파싱 에러: {e}")
                    if "430" in str(e):
                        from datetime import timedelta
                        COMMUNITY_COOLDOWN_UNTIL[community_name] = datetime.now() + timedelta(minutes=12)
                        scraper.parsing_pop = False
                        setattr(scraper, "_skip_pop_due_to_430", True)
                    # 타임아웃/차단 등 심각한 에러 발생 시 다음 페이지 조회를 중단하여 파이프라인 지연 방지
                    break

            # [Optimization] 포텐/인기글 전용 URL이 있는 경우 추가 1페이지 크롤링 (과거 수집 딜의 핫딜 승격 상태 업데이트용)
            if hasattr(scraper, 'pop_url') and getattr(scraper, 'pop_url') and not getattr(scraper, '_skip_pop_due_to_430', False):
                try:
                    logger.info(f"▶ [{community_display_name}] 핫딜 승격 감지용 인기글 페이지 스크래핑 추가 실행")
                    scraper.parsing_pop = True
                    raw_html = await scraper.fetch_html(scraper.pop_url)
                    if raw_html:
                        items = await scraper.parse_list(raw_html)
                        for item in items:
                            await queue.put(item)
                    scraper.parsing_pop = False
                except Exception as e:
                    logger.error(f"[{community_display_name}] 인기글 페이지 파싱 에러: {e}")
                    scraper.parsing_pop = False
                    
            # 모든 아이템이 처리될 때까지 대기
            await queue.join()
            
            # 워커 종료 신호 전송
            for _ in range(num_workers):
                await queue.put(None)
            
            await asyncio.gather(*workers)
            
            logger.info(f"✅ [{community_display_name}] 스크래핑 성공 (신규: {success_count}건, 갱신(중복): {update_count}건)")
                
            # 통계 JSON 쓰기 (동시성 보호를 위해 try/except 사용)
            import json
            stats_file = os.path.join(root_dir, "scraper_stats.json")
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
                    "status": "Success" if (success_count > 0 or fetched_count > 0) else "Blocked/Failed"
                }
                with open(stats_file, 'w', encoding='utf-8') as f:
                    json.dump(stats, f, ensure_ascii=False, indent=2)
            except Exception:
                pass
                
            return success_count
    except Exception as e:
        logger.error(f"❌ [{community_name}] 파이프라인 크롤링 에러: {e}")
        return 0

async def validate_closed_deals():
    """과거 핫딜(3일 이내) 품절 상태(Ping) 검증 데몬 (병합 서브 딜 및 삭제 뱃지 실시간 소거 및 자가치유 탑재)"""
    from datetime import datetime, timedelta
    from backend.database.models import Deal
    from curl_cffi.requests import AsyncSession
    from bs4 import BeautifulSoup
    import asyncio

    db = SessionLocal()
    try:
        logger.info("🔍 [Background] 과거 핫딜(3일 이내) 품절 상태 검증 데몬 가동")
        target_date = datetime.utcnow() - timedelta(days=3)
        deals = db.query(Deal).filter(
            Deal.is_closed == False, 
            Deal.indexed_at >= target_date
        ).order_by(Deal.indexed_at.desc()).limit(150).all()
        
        closed_count = 0
        headers = {
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        }
        
        async with AsyncSession(impersonate='chrome120', timeout=10.0) as client:
            for deal in deals:
                try:
                    # 1. 핑 테스트 헬퍼 함수
                    async def check_url_deleted(url: str) -> bool:
                        if not url or url == "#":
                            return True
                        if "fmkorea" in url:
                            # fmkorea는 스크래퍼에서 차단을 완벽히 통제하므로 일단 무조건 안전으로 살려둠 (430 차단 방지)
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
                            if resp.status_code == 404:
                                return True
                            elif resp.status_code in [403, 430]:
                                # 안티봇 차단 시 일단 삭제되지 않은 것으로 간주 (False)
                                return False
                                
                            resp_text = resp.text
                            
                            # 퀘이사존 글 삭제 튕김 경고창 스크립트 시그니처 감지 (200 OK 상태 코드 우회 방어)
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
                                
                            soup = BeautifulSoup(resp.content, 'html.parser')
                            title_tag = soup.title.string if soup.title else ""
                            if any(kw in title_tag for kw in ["종료", "마감", "품절", "블라인드", "삭제", "완료"]):
                                return True

                            # 본문 헤더 및 품절/종료 배지 추가 스캔 (뽐뿌, 퀘이사존, 루리웹 등 종료 전환 즉시 감지)
                            header_el = soup.select_one('h1, h3, .board-title, .view_title, .subject, span.end, .market-end')
                            if header_el and any(kw in header_el.get_text(" ", strip=True) for kw in ["종료", "마감", "품절", "블라인드", "삭제"]):
                                return True
                                
                            return False
                        except Exception as e:
                            logger.error(f"[Validator Ping Error] {url}: {e}")
                            return False # 핑 전송 에러 시 일단 살아있는 것으로 임시 판정

                    # 2. 대표 딜 링크 삭제 감증
                    is_repr_deleted = await check_url_deleted(deal.post_link)

                    # 3. 병합된 서브 커뮤니티 주소들 순회 검증 및 정제
                    merged_changed = False
                    valid_merged = []
                    
                    if deal.merged_communities:
                        merged_items = [item.strip() for item in deal.merged_communities.split(",") if item.strip()]
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
                    if new_merged_str != deal.merged_communities:
                        deal.merged_communities = new_merged_str
                        merged_changed = True

                    # 4. 분기 및 자가치유 작동
                    if is_repr_deleted:
                        if valid_merged:
                            # 🔄 자가치유 작동: 대표 딜은 삭제되었지만 살아있는 서브 딜이 존재함 -> 서브 딜로 대표 정보 승격
                            chosen_item = valid_merged[0]
                            parts = chosen_item.split("::")
                            new_cid = int(parts[0])
                            new_url = parts[1]
                            
                            # 기존 대표 딜을 백업
                            old_cid = deal.source_community_id
                            old_url = deal.post_link
                            
                            # 대표 정보 교체
                            deal.source_community_id = new_cid
                            deal.post_link = new_url
                            
                            # ⏰ 승격될 서브 딜의 실제 작성 시간(indexed_at)을 DB에서 조회하여 대표 딜의 시간으로 갱신
                            sub_deal_in_db = db.query(Deal).filter(Deal.post_link == new_url).first()
                            if sub_deal_in_db and sub_deal_in_db.indexed_at:
                                deal.indexed_at = sub_deal_in_db.indexed_at
                                logger.info(f"⏰ [Validator-Healing] 대표 딜의 indexed_at 시간을 서브 딜의 시간({sub_deal_in_db.indexed_at})으로 동기화 완료")
                            
                            # merged_communities 재정리
                            valid_merged.remove(chosen_item)
                            
                            # 기존 대표 딜이 비록 삭제되었더라도 이력 차원에서 merged_communities로 밀어주거나 혹은 제외 (여기서는 완전 제외 처리)
                            deal.merged_communities = ",".join(valid_merged) if valid_merged else None
                            
                            logger.info(f"🔄 [Validator-Healing] 대표 딜 삭제 감지 -> 서브 딜로 대표 교체 승격 완료! ({old_url} -> {new_url})")
                            db.commit()
                        else:
                            # 대표 및 모든 서브 딜이 전원 삭제됨 -> 핫딜 최종 종료 처리
                            deal.is_closed = True
                            closed_count += 1
                            db.commit()
                            logger.info(f"🚫 [Validator-Closed] 대표 및 서브 딜 모두 삭제 감지 -> 핫딜 종료: {deal.title}")
                    else:
                        if merged_changed:
                            db.commit()
                            logger.info(f"💾 [Validator-Sync] 서브 딜 링크 일부 삭제로 merged_communities 갱신 완료: {deal.title}")
                            
                except Exception as deal_err:
                    logger.error(f"[Validator Deal Error] Deal ID {deal.id}: {deal_err}")
                    
        logger.info(f"✅ 상태 검증 완료: 총 {len(deals)}개 핑(Ping) 테스트 수행 -> {closed_count}개 품절 처리")
    except Exception as e:
        logger.error(f"❌ 상태 검증 데몬 에러: {e}")
    finally:
        db.close()


async def run_pipeline_job():
    """
    자가 치유 복구(Self-Healing Backfill)가 내장된 투트랙 오케스트레이션 엔진:
    - 매 3분 사이클: 단기 수집 (1페이지 초고속 - 신규 글 즉시 반영)
    - 매 2번째 사이클 (6분): 심층 수집 (1~2페이지 - 핫딜마크 및 추천/댓글수 갱신)
    """
    logger.info("====================================")
    
    db = SessionLocal()
    pages_to_scrape = 1
    is_backfill_mode = False
    gap_hours = 0.0
    
    try:
        from backend.database.models import Deal
        last_deal = db.query(Deal).order_by(Deal.indexed_at.desc()).first()
        if last_deal and last_deal.indexed_at:
            from datetime import datetime, timedelta
            now = datetime.utcnow()
            last_time = last_deal.indexed_at
            if last_time.tzinfo is not None:
                now = datetime.now(last_time.tzinfo)
            
            gap_seconds = (now - last_time).total_seconds()
            gap_hours = gap_seconds / 3600.0
            
            # 수집 공백이 2시간을 초과한 경우, 첫 사이클 병목을 막기 위해 최대 2페이지로 빠르게 최신 글부터 확보
            if gap_hours > 2.0 and "--one-shot" not in sys.argv:
                is_backfill_mode = True
                pages_to_scrape = 2
    except Exception as e:
        logger.error(f"[Self-Healing] 수집 공백 분석 실패: {e}")
    finally:
        db.close()
        
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
            invalidate_feed_api_cache()
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
        logger.info(f"✅ [새벽 4시 통합 유지보수 성공] {summary}")
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
    payload = {"ref": "master"}
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
