from models.product_models import ProductLinkRequest, ProductAnalysisResponse
from services.price_comparison_service import PriceComparisonService
from core.product_analyzer import ProductLinkAnalyzer
import logging

logger = logging.getLogger(__name__)

class ProductAnalyzerService:
    @staticmethod
    async def analyze_product_link(req: ProductLinkRequest):
        """
        상품 링크 분석
        1. URL에서 상품 정보 추출
        2. 키워드로 최저가 검색
        3. 결과 반환
        """
        logger.info(f"[PRODUCT_ANALYZER] 링크 분석 시도: {req.url}")
        
        try:
            # 1. URL에서 상품 정보 추출
            analyzer = ProductLinkAnalyzer()
            info = await analyzer.extract_info(req.url)
            logger.info(f"[PRODUCT_ANALYZER] 추출된 정보: {info}")
            
            # 2. 키워드로 최저가 검색 (AI 필터링 적용)
            keyword = info.get('keyword', '')
            if not keyword:
                logger.warning("[PRODUCT_ANALYZER] 키워드를 추출할 수 없음")
                return ProductAnalysisResponse(
                    trace_id="AI_TRACE",
                    original_url=req.url,
                    extracted_info=info,
                    price_comparison=[],
                    lowest_platform=None,
                    lowest_total_price=None
                )
            
            result = await PriceComparisonService.search_lowest_price(keyword)
            logger.info(f"[PRODUCT_ANALYZER] 가격 검색 결과: {result}")
            
            # 3. 결과 반환
            if result:
                price_comparison = [{
                    'platform': result['mall'],
                    'price': result['lowest_price'],
                    'product_url': result['product_url'],
                    'title': result['product_title']
                }]
                
                return ProductAnalysisResponse(
                    trace_id="AI_TRACE",
                    original_url=req.url,
                    extracted_info=info,
                    price_comparison=price_comparison,
                    lowest_platform=result['mall'],
                    lowest_total_price=result['lowest_price']
                )
            else:
                logger.warning("[PRODUCT_ANALYZER] 최저가를 찾을 수 없음")
                return ProductAnalysisResponse(
                    trace_id="AI_TRACE",
                    original_url=req.url,
                    extracted_info=info,
                    price_comparison=[],
                    lowest_platform=None,
                    lowest_total_price=None
                )
                
        except Exception as e:
            logger.error(f"[PRODUCT_ANALYZER] 오류 발생: {str(e)}", exc_info=True)
            raise Exception(f"링크 분석 실패: {str(e)}")
