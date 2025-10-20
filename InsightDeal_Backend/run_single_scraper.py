import time
from contextlib import contextmanager
from datetime import datetime
from database import db_manager
from logger import logger
from config import config

# 단일 스크래퍼 import
from scrapers.alippomppu_scraper import AlippomppuScraper
from scrapers.ruliweb_scraper import RuliwebScraper
from scrapers.ppomppu_scraper import PpomppuScraper
from scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
from scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from scrapers.clien_scraper import ClienScraper
from scrapers.quasarzone_scraper import QuasarzoneScraper
from scrapers.fmkorea_scraper import FmkoreaScraper

class SingleScraperTester:
    """단일 스크래퍼 테스트 클래스"""
    
    def __init__(self, scraper_class):
        self.scraper_class = scraper_class
        self.scraper_name = scraper_class.__name__
        self.start_time = None
        self.success = False
    
    @contextmanager
    def execution_tracker(self):
        """실행 추적 컨텍스트 매니저"""
        self.start_time = datetime.now()
        logger.info(f"[START] {self.scraper_name} 테스트 시작: {self.start_time}")
        
        try:
            yield
        finally:
            elapsed = datetime.now() - self.start_time
            status = "[SUCCESS]" if self.success else "[FAILED]"
            logger.info(f"{status} {self.scraper_name} 테스트 완료 - 소요시간: {elapsed}")
    
    def run_test(self, limit: int = 5) -> bool:
        """스크래퍼 테스트 실행"""
        try:
            with self.execution_tracker():
                with db_manager.get_session() as db_session:
                    with self.scraper_class(db_session) as scraper:
                        # 스크래퍼 실행
                        self.success = scraper.run(limit=limit)
                        
                        # 결과 상세 정보 출력
                        if self.success:
                            logger.info(f"[SUCCESS] {self.scraper_name} - {limit}개 한도로 실행 완료")
                        else:
                            logger.warning(f"[FAILED] {self.scraper_name} - 실행 실패")
                        
                        return self.success
                        
        except Exception as e:
            logger.error(f"[ERROR] {self.scraper_name} 테스트 중 오류: {e}")
            self.success = False
            return False

def main():
    """메인 실행 함수"""
    print("=" * 60)
    print("🧪 PpomppuOverseasScraper 단독 테스트")
    print("=" * 60)
    
    # 테스트할 스크래퍼 설정
    scraper_to_test = PpomppuOverseasScraper
    limit = 3  # 테스트용으로 5개만
    
    # 테스터 인스턴스 생성 및 실행
    tester = SingleScraperTester(scraper_to_test)
    success = tester.run_test(limit=limit)
    
    # 최종 결과 출력
    print("=" * 60)
    if success:
        print(f"✅ 테스트 성공! {scraper_to_test.__name__}")
        print("🔍 API에서 새로운 딜을 확인해보세요:")
        print("   curl http://localhost:8000/api/deals")
        print("   또는 앱에서 새로고침!")
    else:
        print(f"❌ 테스트 실패! {scraper_to_test.__name__}")
        print("로그를 확인하여 오류를 분석하세요.")
    print("=" * 60)

if __name__ == "__main__":
    main()
