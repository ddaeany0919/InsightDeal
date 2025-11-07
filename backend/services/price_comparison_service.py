from scrapers.naver_shopping import NaverShoppingScraper

class PriceComparisonService:
    @staticmethod
    async def search_lowest_price(keyword: str):
        products = NaverShoppingScraper.search_products(keyword)
        if not products:
            return None
        lowest = min(products, key=lambda x: x['price'])
        return {
            "product_title": lowest['title'],
            "lowest_price": lowest['price'],
            "mall": lowest['mall'],
            "product_url": lowest['link'],
            "image": lowest['image']
        }
