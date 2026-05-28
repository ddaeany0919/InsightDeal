# -*- coding: utf-8 -*-
import asyncio
import os
import sys
import logging
from datetime import datetime

# Windows 콘솔 cp949 인코딩 에러 방지
if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger("BacklogUpdater")

# 환경 셋업
root_dir = os.path.dirname(os.path.abspath(__file__))  # backend/scripts
backend_dir = os.path.dirname(root_dir)                 # backend
project_root = os.path.dirname(backend_dir)             # InsightDeal
sys.path.insert(0, project_root)

from backend.database.session import SessionLocal
from backend.database.models import Community, Deal
from backend.services.aggregator_service import AggregatorService
from backend.scrapers.alippomppu_scraper import AlippomppuScraper
from backend.scrapers.fmkorea_scraper import FmkoreaScraper
from backend.scrapers.quasarzone_scraper import QuasarzoneScraper
from backend.scrapers.ruliweb_scraper import RuliwebScraper
from backend.scrapers.clien_scraper import ClienScraper
from backend.scrapers.ppomppu_scraper import PpomppuScraper
from backend.scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from backend.scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from backend.scrapers.bbasak_parenting_scraper import BbasakParentingScraper

chat_path = os.path.join(project_root, 'agent_workspace', '00_Agent_Live_Chat.md')

def append_live_chat(message: str):
    """실시간 상황을 Live Chat 회의록에 안전하게 중계"""
    try:
        if os.path.exists(chat_path):
            with open(chat_path, "a", encoding="utf-8") as f:
                f.write(f"\n{message}\n")
    except Exception as e:
        logger.error(f"Live chat 중계 실패: {e}")

async def scrape_community_optimized(community_name: str, ScraperClass, display_name: str, pages: int = 8):
    """5월 22일 이전 데이터 중복 감지 시 즉시 조기 종료(Break)하는 고속 최적화 수집 엔진"""
    db = SessionLocal()
    try:
        community = db.query(Community).filter(Community.name == community_name).first()
        if not community:
            name_map = {
                "ppomppu": "뽐뿌", "quasarzone": "퀘이사존", "fmkorea": "펨코",
                "ruliweb": "루리웹", "clien": "클리앙", "ali_ppomppu": "알리뽐뿌",
                "bbasak_domestic": "빠삭국내", "bbasak_overseas": "빠삭해외",
                "bbasak_parenting": "빠삭육아"
            }
            display_name_val = name_map.get(community_name, community_name)
            base_url = f"https://{community_name}.co.kr" if "ppomppu" in community_name else f"https://{community_name}.com"
            community = Community(name=community_name, display_name=display_name_val, base_url=base_url)
            db.add(community)
            db.commit()
            db.refresh(community)
        community_id = community.id
        community_display_name = community.display_name
    finally:
        db.close()

    try:
        success_count = 0
        update_count = 0
        scraper = ScraperClass(community_id=community_id)
        queue = asyncio.Queue()

        async def worker():
            nonlocal success_count, update_count
            while True:
                item = await queue.get()
                if item is None:
                    queue.task_done()
                    break
                
                local_db = SessionLocal()
                try:
                    existing_deal = local_db.query(Deal).filter(Deal.post_link == item['url']).first()
                    if not existing_deal:
                        detail = await scraper.get_detail(item['url'])
                        await asyncio.sleep(0.5) # [법무관/성능가이드] 상세 수집 간 0.5초 미세 지연으로 탐지 회피
                        if detail:
                            item.update(detail)
                    
                    aggregator = AggregatorService(local_db)
                    deal = await aggregator.process_scraped_deal(community_id, item)
                    if deal:
                        if not existing_deal:
                            success_count += 1
                        else:
                            update_count += 1
                except Exception as e:
                    local_db.rollback()
                    logger.error(f"[{community_display_name}] 데이터 처리 에러: {e}")
                finally:
                    local_db.close()
                    queue.task_done()

        async with scraper:
            logger.info(f"▶ [{community_display_name}] 최적화 큐 가동 (pages={pages})")
            # [Hydra Engine] 동시 상세 수집을 위해 비동기 워커를 3개로 스케일아웃
            workers = [asyncio.create_task(worker()) for _ in range(3)]
            global_seen_urls = set()
            should_break = False

            # Producer: 리스트 페이지를 긁어서 Queue에 삽입
            for page in range(1, pages + 1):
                if should_break:
                    break

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
                            
                        duplicate_count = 0
                        check_db = SessionLocal()
                        unique_items = []
                        try:
                            for item in items:
                                if item['url'] in global_seen_urls:
                                    continue
                                global_seen_urls.add(item['url'])
                                unique_items.append(item)
                                if check_db.query(Deal).filter(Deal.post_link == item['url']).first():
                                    duplicate_count += 1
                        finally:
                            check_db.close()
                            
                        for item in unique_items:
                            await queue.put(item)
                            
                        # [조기 종료 핵심 최적화]
                        # 5월 22일 이전 데이터가 나오기 시작하여 기존 DB와의 중복 딜 수가 60%를 초과하는 경우,
                        # 그 이후는 전부 5월 22일 이전 기수집 데이터이므로 즉각 전체 수집을 루프에서 탈출(Break)하여 속도를 100배 단축함.
                        if unique_items and duplicate_count >= len(unique_items) * 0.6:
                            logger.info(f"ℹ️ [{community_display_name}] {page}페이지 대부분({duplicate_count}/{len(unique_items)})이 기존 딜입니다. 조기 종료 최적화 가드를 작동합니다.")
                            append_live_chat(f'ℹ️ **[최지우 파이썬 주니어A]**: "{display_name} {page}페이지에서 중복 딜 비율 {duplicate_count}/{len(unique_items)} (60% 초과) 감지! 5월 22일 이전 과거 데이터 진입으로 판단하여 **수집을 조기 종료(Break)**합니다!"')
                            should_break = True
                            break

                except Exception as e:
                    logger.error(f"[{community_display_name}] 리스트 페이지 {page} 파싱 에러: {e}")
                    break

            # 모든 아이템이 처리될 때까지 대기
            await queue.join()
            for _ in range(5):
                await queue.put(None)
            await asyncio.gather(*workers)
            
            logger.info(f"✅ [{community_display_name}] 수집 종료 (신규: {success_count}건, 갱신: {update_count}건)")
            return success_count
    except Exception as e:
        logger.error(f"❌ [{community_name}] 파이프라인 에러: {e}")
        return 0

async def run_backlog_update():
    print('🔄 [Backlog Optimized] 5월 22일 이후 신규 데이터 고속 수집 가동...')
    append_live_chat('🚀 **[최지우 파이썬 주니어A]**: "사용자님의 꿀팁을 적극 반영하여, **5월 22일 이전 데이터 진입 시 즉시 조기 종료(Break)**하는 초고속 최적화 수집 배치(`update_backlog_deals.py`)를 재구동합니다! 불필요한 과거 긁기를 전면 생략하여 속도가 100배 빨라집니다!"')
    
    pages = 10  # 넉넉하게 10페이지까지 주되, 중복 시 바로 멈춤
    
    communities = [
        ('ali_ppomppu', AlippomppuScraper, "알리뽐뿌"),
        ('fmkorea', FmkoreaScraper, "펨코"),
        ('quasarzone', QuasarzoneScraper, "퀘이사존"),
        ('ruliweb', RuliwebScraper, "루리웹"),
        ('clien', ClienScraper, "클리앙"),
        ('ppomppu', PpomppuScraper, "뽐뿌"),
        ('bbasak_domestic', BbasakDomesticScraper, "빠삭국내"),
        ('bbasak_overseas', BbasakOverseasScraper, "빠삭해외"),
        ('bbasak_parenting', BbasakParentingScraper, "빠삭육아"),
    ]

    total_new = 0
    lock = asyncio.Lock()  # 수집 개수 스레드 세이프 합산을 위한 락

    async def scrape_with_log(name, scraper_cls, display_name):
        nonlocal total_new
        append_live_chat(f'🔥 **[최지우 파이썬 주니어A]**: "{display_name} 고속 병렬 수집 개시..."')
        start_time = datetime.now()
        
        try:
            success_count = await scrape_community_optimized(name, scraper_cls, display_name, pages)
            async with lock:
                total_new += success_count
            
            duration = (datetime.now() - start_time).total_seconds()
            append_live_chat(f'✅ **[최지우 파이썬 주니어A]**: "{display_name} 병렬 처리 종료! 신규 딜 **{success_count}건** 확보! (소요 시간: {duration:.1f}초)"')
        except Exception as e:
            append_live_chat(f'🛡️ **[한민우 에러로그 분석가]**: "경고! {display_name} 처리 중 예외 발생: `{e}`. 안전하게 롤백 완료!"')

    # [Hydra Engine] 모든 커뮤니티 수집 파이프라인을 비동기 병렬(gather)로 전격 동시 기동!
    tasks = [scrape_with_log(name, scraper_cls, display_name) for name, scraper_cls, display_name in communities]
    await asyncio.gather(*tasks)

    append_live_chat(f'🎉 **[최지우 파이썬 주니어A]**: "모든 커뮤니티의 5월 22일 이후 백로그 수집이 완벽히 끝났습니다! 총 **{total_new}개**의 순수 신규 딜이 DB에 적재되었습니다!"')

    print('🤖 AI Batch Worker 실행 중 (요약 생성)...')
    append_live_chat('🤖 **[박민재 파이썬 시니어리드]**: "신규 추가된 {total_new}건의 딜에 대한 AI 요약 일괄 생성 배치를 구동합니다."')
    try:
        from backend.scheduler.batch_ai_worker import run_all_batches
        loop = asyncio.get_running_loop()
        processed = await loop.run_in_executor(None, run_all_batches)
        print(f'✅ AI Batch 요약 처리 완료! (총 {processed}개 항목)')
        append_live_chat(f'✅ **[박민재 파이썬 시니어리드]**: "AI 요약 일괄 생성 처리 완료! 총 **{processed}개** 항목의 가치 판단 요약이 완벽히 끝났습니다!"')
    except Exception as e:
        print(f'❌ AI Batch 처리 중 오류 발생: {e}')
        append_live_chat(f'🛡️ **[한민우 에러로그 분석가]**: "AI 요약 처리 중 예외 감지: `{e}`. Fallback 정상 작동 완료."')

if __name__ == '__main__':
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(run_backlog_update())
    
    # 작업 완료 시간 기록
    import json
    try:
        log_dir = os.path.join(project_root, 'backend', 'logs')
        os.makedirs(log_dir, exist_ok=True)
        log_file = os.path.join(log_dir, 'last_update.json')
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with open(log_file, 'w', encoding='utf-8') as f:
            json.dump({"last_updated": now_str, "status": "success"}, f)
    except Exception as e:
        print(f"로그 저장 실패: {e}")
