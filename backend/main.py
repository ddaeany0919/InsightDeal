from fastapi import FastAPI
from routers.wishlist import router as wishlist_router
from routers.product import router as product_router
from routers.health import router as health_router

app = FastAPI(title="InsightDeal API", version="2.0.0")

app.include_router(wishlist_router, prefix="/api/wishlist", tags=["wishlist"])
app.include_router(product_router, prefix="/api/product", tags=["product"])
app.include_router(health_router, prefix="/api", tags=["health"])

# 미들웨어, lifespan, CORS, 기타 공통설정 등은 필요에 따라 추가
