from scrapers.naver_shopping import NaverShoppingScraper
from services.ai_product_name_service import AIProductNameService

class PriceComparisonService:
    @staticmethod
    async def search_lowest_price(keyword: str):
        products = NaverShoppingScraper.search_products(keyword, display=30)
        if not products:
            return None
        
        # 가격순 정렬
        products_sorted = sorted(products, key=lambda x: x['price'])
        
        # 상위 10개만 AI에게 전달
        ai_items = [
            {
                'index': idx + 1,
                'price': item['price'],
                'title': item['title'],
                'mall': item['mall']
            }
            for idx, item in enumerate(products_sorted[:10])
        ]
        
        judgment = AIProductNameService.judge_valid_products(keyword, ai_items)
        # AI가 정상이라고 판단한 상품 중 최저가 선택
        valid_indices = [i for i, label in judgment.items() if "정상" in label]
        
        valid_products = [products_sorted[i - 1] for i in valid_indices]
        if not valid_products:
            return None
        lowest = min(valid_products, key=lambda x: x['price'])
        return {
            "product_title": lowest['title'],
            "lowest_price": lowest['price'],
            "mall": lowest['mall'],
            "product_url": lowest['link'],
            "image": lowest['image']
        }
