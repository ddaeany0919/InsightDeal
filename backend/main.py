from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from routers.wishlist import router as wishlist_router
from routers.product import router as product_router
from routers.health import router as health_router
from routers.community import router as community_router
import logging

app = FastAPI(title="InsightDeal API", version="2.0.0")

# CORS 미들웨어 (안드로이드/웹 호환)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

logging.basicConfig(level=logging.INFO)

app.include_router(wishlist_router, prefix="/api/wishlist", tags=["wishlist"])
app.include_router(product_router, prefix="/api/product", tags=["product"])
app.include_router(community_router, prefix="/api/community", tags=["community"])
app.include_router(health_router, prefix="/api", tags=["health"])

@app.exception_handler(Exception)
async def generic_exception_handler(request, exc):
    logging.error(f"Unhandled Error: {exc}")
    return JSONResponse(status_code=500, content={"message": "Internal Server Error - Please contact admin."})

# (선택적으로 lifespan, DB/스크래퍼 초기화 등도 여기에 추가 가능)
