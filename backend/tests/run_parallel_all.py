import time
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from database.session import SessionLocal
from scrapers.clien_scraper import ClienScraper
from scrapers.ppomppu_scraper import PpomppuScraper
from scrapers.ruliweb_scraper import RuliwebScraper
from scrapers.fmkorea_scraper import FmkoreaScraper
from scrapers.quasarzone_scraper import QuasarzoneScraper
from scrapers.alippomppu_scraper import AlippomppuScraper
from scrapers.ppomppu_overseas_scraper import PpomppuOverseasScraper
from scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from scrapers.bbasak_overseas_scraper import BbasakOverseasScraper
from scrapers.bbasak_domestic_scraper import BbasakDomesticScraper
from scrapers.bbasak_overseas_scraper import BbasakOverseasScraper

# 로깅 설정
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(name)s: %(message)s')
logger = logging.getLogger("ParallelScraper")

def run_scraper(scraper_class, name):
    db = SessionLocal()
    start_time = time.time()
    try:
        logger.info(f"🚀 Starting {name} scraper...")
        scraper = scraper_class(db)
        # scraper.limit = 10 # 테스트를 위해 제한 (주석 처리)
        results = scraper.scrape()
        duration = time.time() - start_time
        logger.info(f"✅ {name} finished in {duration:.2f}s (Found {len(results)} items)")
        return name, len(results), duration
    except Exception as e:
        logger.error(f"❌ {name} failed: {e}", exc_info=True)
        return name, 0, time.time() - start_time
    finally:
        db.close()

def main():
    scrapers_to_run = [
        (FmkoreaScraper, "FMKorea"),      # 가장 오래 걸리는 것부터 시작
        (QuasarzoneScraper, "Quasarzone"),
        (ClienScraper, "Clien"),
        (PpomppuScraper, "Ppomppu"),
        (RuliwebScraper, "Ruliweb"),
        (AlippomppuScraper, "AliPpomppu"),
        (BbasakDomesticScraper, "BbasakDomestic"),
        (BbasakOverseasScraper, "BbasakOverseas")
    ]
    
    overall_start = time.time()
    
    # max_workers=4 정도로 제한하여 메모리 밸런스 조정 (필요시 늘림)
    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = {executor.submit(run_scraper, scol, name): name for scol, name in scrapers_to_run}
        
        for future in as_completed(futures):
            name = futures[future]
            try:
                name, count, duration = future.result()
            except Exception as e:
                logger.error(f"Future for {name} generated an exception: {e}")

    total_duration = time.time() - overall_start
    logger.info(f"==== All scrapers finished in {total_duration:.2f}s ====")

if __name__ == "__main__":
    main()
