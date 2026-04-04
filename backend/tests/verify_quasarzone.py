
import logging
from database.session import SessionLocal
from scrapers.quasarzone_scraper import QuasarzoneScraper

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def main():
    db = SessionLocal()
    try:
        scraper = QuasarzoneScraper(db)
        scraper.limit = 5
        deals = scraper.scrape()
        print(f"--- Quasarzone Deals ({len(deals)}) ---")
        for i, deal_entry in enumerate(deals):
            deal = deal_entry['deal']
            title = getattr(deal, 'title', "제목 없음")
            price = getattr(deal, 'price', 'N/A')
            shipping = getattr(deal, 'shipping_fee', 'N/A')
            shop = getattr(deal, 'shop_name', 'N/A')
            print(f"  [{i+1}] {title}")
            print(f"      Price: {price}, Shipping: {shipping}, Shop: {shop}")
    except Exception as e:
        logger.error(f"Error: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    main()
