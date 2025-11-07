from fastapi import APIRouter, Depends, Request
from models.product_models import ProductLinkRequest, ProductAnalysisResponse
from services.product_analyzer_service import ProductAnalyzerService

router = APIRouter()

@router.post("/analyze-link", response_model=ProductAnalysisResponse)
async def analyze_link(
    req: ProductLinkRequest,
):
    return await ProductAnalyzerService.analyze_product_link(req)
