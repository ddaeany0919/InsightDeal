from scrapers.naver_shopping_scraper import NaverShoppingScraper
from scrapers.coupang_scraper import CoupangScraper
from scrapers.eleventh_scraper import EleventhScraper
from scrapers.gmarket_scraper import GmarketScraper
from scrapers.auction_scraper import AuctionScraper

class PriceComparisonService:
    def __init__(self):
        self.scrapers = {
            "naver": NaverShoppingScraper(),
            "coupang": CoupangScraper(),
            "eleventh": EleventhScraper(),
            "gmarket": GmarketScraper(),
            "auction": AuctionScraper(),
        }

    def compare_prices(self, keyword: str):
        results = []
        for name, scraper in self.scrapers.items():
            try:
                products = scraper.search_products(keyword)
                if products:
                    best = min(products, key=lambda x: getattr(x, "price", None) or float("inf"))
                    results.append({
                        "platform": name,
                        "price": getattr(best, "price", None),
                        "title": getattr(best, "title", None),
                        "url": getattr(best, "url", None)
                    })
            except Exception:
                continue
        return results
