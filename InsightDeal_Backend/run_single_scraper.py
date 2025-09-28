import database
from sqlalchemy.orm import Session

# --- 테스트할 모든 스크래퍼를 가져옵니다 ---
from scrapers.alippomppu_scraper import AlippomppuScraper
from scrapers.ruliweb_scraper import RuliwebScraper
from scrapers.ppomppu_scraper import PpomppuScraper
from scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
from scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from scrapers.clien_scraper import ClienScraper
from scrapers.quasarzone_scraper import QuasarzoneScraper
from scrapers.fmkorea_scraper import FmkoreaScraper

def run_scrapers():
    """지정된 모든 스크래퍼를 순차적으로 실행합니다."""
    
    # 실행할 스크래퍼 클래스 목록
    scraper_classes = [
        AlippomppuScraper,
        #RuliwebScraper,
        PpomppuScraper,
        PpomppuOverseasScraper,
        #BbasakDomesticScraper,
        #BbasakOverseasScraper,
        #ClienScraper,
        #QuasarzoneScraper,
        #FmkoreaScraper,
    ]
    
    db: Session = database.SessionLocal()
    try:
        # 목록에 있는 모든 스크래퍼를 순서대로 실행
        for scraper_class in scraper_classes:
            scraper_name = scraper_class.__name__
            print(f"\n{'='*20}\n[START] {scraper_name} 스크래핑 시작\n{'='*20}")
            
            scraper = scraper_class(db)
            scraper.run(limit=3)  # 테스트를 위해 3개씩만 수집
            
            print(f"\n{'='*20}\n[END] {scraper_name} 스크래핑 완료\n{'='*20}")

    finally:
        db.close()
        print("\n모든 스크래핑 작업 완료. DB 세션을 닫습니다.")

if __name__ == "__main__":
    # 데이터베이스 테이블이 없으면 생성합니다.
    print("데이터베이스 테이블을 생성합니다...")
    database.Base.metadata.create_all(bind=database.engine)
    print("테이블 생성 완료.")
    
    # 지정된 스크래퍼들을 실행합니다.
    run_scrapers()