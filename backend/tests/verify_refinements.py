
import logging
from database.session import SessionLocal
from scrapers.clien_scraper import ClienScraper
from scrapers.ppomppu_scraper import PpomppuScraper
from scrapers.ruliweb_scraper import RuliwebScraper
from scrapers.fmkorea_scraper import FmkoreaScraper
from scrapers.quasarzone_scraper import QuasarzoneScraper

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def verify_scraper(scraper_class, name):
    db = SessionLocal()
    try:
        logger.info(f"--- Verifying {name} ---")
        scraper = scraper_class(db)
        scraper.limit = 5
        deals_data = scraper.scrape()
        
        logger.info(f"Found {len(deals_data)} items for {name}")
        for i, item in enumerate(deals_data):
            deal = item['deal']
            logger.info(f"  [{i+1}] {deal.title[:50]}... ({deal.post_link})")
            logger.info(f"      Price: {deal.price}, Shipping: {deal.shipping_fee}, Shop: {deal.shop_name}")
            
        if not deals_data:
            logger.warning(f"No deals found for {name}!")
    except Exception as e:
        logger.error(f"Error verifying {name}: {e}")
    finally:
        db.close()

def main():
    verify_scraper(ClienScraper, "Clien")
    verify_scraper(PpomppuScraper, "Pomppu")
    verify_scraper(RuliwebScraper, "Ruliweb")
    verify_scraper(FmkoreaScraper, "FMKorea")
    verify_scraper(QuasarzoneScraper, "Quasarzone")

if __name__ == "__main__":
    main()
