import os
import importlib
import sys
from sqlalchemy.orm import Session

import database
from scrapers.base import BaseScraper

def run_all_scrapers():
    """scrapers 폴더 내의 모든 스크래퍼를 동적으로 찾아 실행합니다."""
    
    db: Session = database.SessionLocal()
    try:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        scrapers_dir = os.path.join(script_dir, 'scrapers')
        
        scraper_files = [f for f in os.listdir(scrapers_dir) if f.endswith('_scraper.py')]

        if scrapers_dir not in sys.path:
            sys.path.append(scrapers_dir)

        for file in scraper_files:
            module_name = f"scrapers.{file[:-3]}"
            module = importlib.import_module(module_name)

            for attribute_name in dir(module):
                attribute = getattr(module, attribute_name)
                if (isinstance(attribute, type) and 
                    issubclass(attribute, BaseScraper) and 
                    not attribute.__name__.endswith('BaseScraper')):
                    scraper_instance = attribute(db)
                    scraper_instance.run(limit=4) #스크래퍼 게시물 갯수 
    finally:
        db.close()
        print("모든 스크래핑 작업 완료. DB 세션을 닫습니다.")


if __name__ == "__main__":
    print("데이터베이스 테이블을 생성합니다...")
    database.Base.metadata.create_all(bind=database.engine)
    print("테이블 생성 완료.")
    
    run_all_scrapers()