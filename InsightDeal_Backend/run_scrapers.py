import asyncio
import concurrent.futures
import time
from contextlib import contextmanager
from datetime import datetime
from typing import List, Type

from database import db_manager
from logger import logger
from config import config

# 모든 스크래퍼 import
from scrapers.alippomppu_scraper import AlippomppuScraper
from scrapers.ruliweb_scraper import RuliwebScraper
from scrapers.ppomppu_scraper import PpomppuScraper
from scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
from scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from scrapers.clien_scraper import ClienScraper
from scrapers.quasarzone_scraper import QuasarzoneScraper
from scrapers.fmkorea_scraper import FmkoreaScraper
# ... 다른 스크래퍼들

class ScraperManager:
    """스크래퍼 실행 관리 클래스"""
    
    SCRAPERS = [
        AlippomppuScraper,
        RuliwebScraper,
        PpomppuScraper,
        PpomppuOverseasScraper,
        BbasakDomesticScraper,
        BbasakOverseasScraper,
        ClienScraper,
        QuasarzoneScraper,
        FmkoreaScraper,
    ]
    
    def __init__(self):
        self.results = {}
        self.start_time = None
    
    @contextmanager
    def execution_tracker(self):
        """실행 추적 컨텍스트 매니저"""
        self.start_time = datetime.now()
        logger.info(f"🚀 스크래퍼 실행 시작: {self.start_time}")
        
        try:
            yield
        finally:
            elapsed = datetime.now() - self.start_time
            success_count = sum(1 for result in self.results.values() if result)
            total_count = len(self.results)
            
            logger.info(
                f"✅ 스크래퍼 실행 완료 - "
                f"성공: {success_count}/{total_count}, "
                f"소요시간: {elapsed}"
            )
    
    def run_scraper(self, scraper_class: Type) -> bool:
        """개별 스크래퍼 실행"""
        scraper_name = scraper_class.__name__
        
        try:
            with db_manager.get_session() as db_session:
                with scraper_class(db_session) as scraper:
                    success = scraper.run(limit=5)
                    self.results[scraper_name] = success
                    return success
                    
        except Exception as e:
            logger.error(f"[ERROR] {scraper_name} 실행 중 오류: {e}")
            self.results[scraper_name] = False
            return False
    
    def run_parallel(self, max_workers: int = 3) -> dict:
        """병렬 실행 (제한된 동시 실행)"""
        with self.execution_tracker():
            with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
                future_to_scraper = {
                    executor.submit(self.run_scraper, scraper_class): scraper_class.__name__
                    for scraper_class in self.SCRAPERS
                }
                
                for future in concurrent.futures.as_completed(future_to_scraper):
                    scraper_name = future_to_scraper[future]
                    try:
                        success = future.result()
                        status = "[SUCCESS]" if success else "[FAILED]"
                        logger.info(f"{status} {scraper_name} 완료")
                    except Exception as e:
                        logger.error(f"[ERROR] {scraper_name} 실행 중 예외: {e}")
        
        return self.results
    
    def run_sequential(self) -> dict:
        """순차 실행"""
        with self.execution_tracker():
            for scraper_class in self.SCRAPERS:
                self.run_scraper(scraper_class)
                
                # 각 스크래퍼 간 딜레이
                time.sleep(config.SCRAPER_DELAY)
        
        return self.results

if __name__ == "__main__":
    manager = ScraperManager()
    
    # 환경에 따라 실행 방식 선택
    if hasattr(config, 'PARALLEL_EXECUTION') and config.PARALLEL_EXECUTION:
        results = manager.run_parallel(max_workers=3)
    else:
        results = manager.run_sequential()
    
    # 결과 보고
    failed_scrapers = [name for name, success in results.items() if not success]
    if failed_scrapers:
        logger.warning(f"실패한 스크래퍼: {failed_scrapers}")
