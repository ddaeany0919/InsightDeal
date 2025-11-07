from models.product_models import ProductLinkRequest, ProductAnalysisResponse
from services.price_comparison_service import PriceComparisonService
from core.product_analyzer import ProductLinkAnalyzer

class ProductAnalyzerService:
    @staticmethod
    async def analyze_product_link(req: ProductLinkRequest):
        analyzer = ProductLinkAnalyzer()
        info = await analyzer.extract_info(req.url)
        price_service = PriceComparisonService()
        prices = price_service.compare_prices(info['keyword'])
        best = min(prices, key=lambda x: x['price'] if x['price'] is not None else float('inf')) if prices else None
        return ProductAnalysisResponse(
            trace_id="AI_TRACE",
            original_url=req.url,
            extracted_info=info,
            price_comparison=prices,
            lowest_platform=best['platform'] if best else None,
            lowest_total_price=best['price'] if best else None
        )
