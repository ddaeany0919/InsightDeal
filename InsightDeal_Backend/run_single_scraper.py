import database
from sqlalchemy.orm import Session

# --- 여기서 테스트하고 싶은 스크래퍼를 선택하세요 ---
from scrapers.alippomppu_scraper import AlippomppuScraper
from scrapers.ruliweb_scraper import RuliwebScraper
def run_scraper():
    """지정된 단일 스크래퍼를 실행합니다."""
    
    db: Session = database.SessionLocal()
    try:
        # --- 여기서 테스트하고 싶은 스크래퍼 클래스를 지정하세요 ---
        bbasak_domestic_scraper = AlippomppuScraper(db)
        bbasak_domestic_scraper.run(limit=3)
        bbasak_domestic_scraper1 = RuliwebScraper(db)
        bbasak_domestic_scraper1.run(limit=3)
    finally:
        db.close()
        print("단일 스크래핑 작업 완료. DB 세션을 닫습니다.")

if __name__ == "__main__":
    # 데이터베이스 테이블이 없으면 생성합니다.
    print("데이터베이스 테이블을 생성합니다...")
    database.Base.metadata.create_all(bind=database.engine)
    print("테이블 생성 완료.")
    
    # 지정된 스크래퍼를 실행합니다.
    run_scraper()