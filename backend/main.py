from fastapi import FastAPI, HTTPException, Query, Request, BackgroundTasks, Depends
import asyncio
import json
import time
import uuid
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
from contextlib import asynccontextmanager
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import uvicorn
import logging
import structlog
from pydantic import BaseModel, validator
from sqlalchemy.orm import Session
from sqlalchemy import desc, and_
from scrapers.base_scraper import PriceComparisonEngine, ProductInfo
from scrapers.coupang_scraper import CoupangScraper
from scrapers.eleventh_scraper import EleventhScraper
from scrapers.gmarket_scraper import GmarketScraper
from scrapers.auction_scraper import AuctionScraper
from scrapers.naver_shopping_scraper import NaverShoppingScraper
from database.models import KeywordWishlist, KeywordPriceHistory, KeywordAlert, Base, get_db_engine
from database.session import get_db_session
from core.product_analyzer import ProductLinkAnalyzer
from models.product_models import ProductLinkRequest, ProductAnalysisResponse, LinkBasedWishlistCreate, ExtractedProductInfo, PlatformPriceInfo

# ===== lifespan 함수 반드시 app=FastAPI 위에 =====
@asynccontextmanager
async def lifespan(app: FastAPI):
    global naver_scraper, product_analyzer
    start_time = time.time()
    try:
        try:
            engine = get_db_engine()
            Base.metadata.create_all(engine)
        except Exception as e:
            pass
        yield
    finally:
        pass

# ===== FastAPI 인스턴스 정의 및 라우트 =====
app = FastAPI(
    title="InsightDeal API",
    description="🛒 국내 최초 4몰 통합 가격비교 API + 네이버 쇼핑 API + 관심상품 시스템 + 🤖 AI 상품 분석",
    version="2.1.0",
    lifespan=lifespan
)

# ... (기존 라우트/핸들러 모두 app = FastAPI 밑에 위치!) ...

# 마지막 if __name__ == "__main__": ...
