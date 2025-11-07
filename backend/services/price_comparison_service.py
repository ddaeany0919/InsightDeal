from scrapers.naver_shopping import NaverShoppingScraper
from services.ai_product_name_service import AIProductNameService
import logging

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
            {'index': idx + 1, 'price': item['price'], 'title': item['title'], 'mall': item['mall']}
            for idx, item in enumerate(products_sorted[:10])
        ]
        
        judgment = AIProductNameService.judge_valid_products(keyword, ai_items)
        logging.info(f"[AI_RESULT] AI 판단 결과: {judgment}")
        
        # '정상' 만 통과시키도록 완전히 고정
        valid_indices = [int(i) for i, label in judgment.items() if label.strip() == "정상"]
        logging.info(f"[AI_RESULT] 정상 상품 인덱스: {valid_indices}")
        
        valid_products = [products_sorted[i - 1] for i in valid_indices]
        if not valid_products:
            logging.warning("[AI_RESULT] 정상 상품이 없습니다. None 반환")
            return None
        
        lowest = min(valid_products, key=lambda x: x['price'])
        logging.info(f"[AI_RESULT] 최종 최저가 상품: {lowest['price']}원 - {lowest['title']}")
        
        return {
            "product_title": lowest['title'],
            "lowest_price": lowest['price'],
            "mall": lowest['mall'],
            "product_url": lowest['link'],
            "image": lowest['image']
        }
