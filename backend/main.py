# ... (생략: 기존 import 및 설정 코드)

# ===== 앱 생성 및 기존 라우트 모두 이곳 아래에 =====
app = FastAPI(
    title="InsightDeal API",
    description="🛒 국내 최초 4몰 통합 가격비교 API + 네이버 쇼핑 API + 관심상품 시스템 + 🤖 AI 상품 분석",
    version="2.1.0",
    lifespan=lifespan
)

# ... (생략: 미들웨어, 모든 기존 엔드포인트 정의)

# ===== manual_price_check는 이 곳에 위치 =====
@app.post("/api/wishlist/{wishlist_id}/check-price")
async def manual_price_check(wishlist_id: int, user_id: str = Query(default="default"), db: Session = Depends(get_db_session)):
    """🔄 수동 가격 체크: 네이버 쇼핑 API로 최신 가격 업데이트"""
    w = db.query(KeywordWishlist).filter(and_(KeywordWishlist.id==wishlist_id, KeywordWishlist.user_id==user_id, KeywordWishlist.is_active==True)).first()
    if not w:
        raise HTTPException(status_code=404, detail="활성상태의 관심상품을 찾을 수 없습니다")
    # 네이버 쇼핑 검색으로 최신 가격 업데이트
    if naver_scraper:
        try:
            results = naver_scraper.search_products(w.keyword)
            if results:
                best_result = min(results, key=lambda x: x.get('price', float('inf')))
                w.current_lowest_price = best_result.get('price')
                w.current_lowest_platform = "네이버쇼핑"
                w.current_lowest_product_title = best_result.get('title', '')[:200]
                w.last_checked = datetime.utcnow()
                db.commit()
                return {
                    "message": f"'{w.keyword}' 가격 체크를 완료했습니다",
                    "keyword": w.keyword,
                    "current_price": w.current_lowest_price,
                    "target_price": w.target_price,
                    "platform": w.current_lowest_platform,
                    "is_target_reached": w.current_lowest_price <= w.target_price if w.current_lowest_price else False,
                    "updated_at": w.last_checked.isoformat()
                }
        except Exception as e:
            logger.error(f"가격 체크 실패: {e}")
            raise HTTPException(status_code=500, detail="가격 체크에 실패했습니다")
    else:
        raise HTTPException(status_code=503, detail="네이버 쇼핑 서비스를 사용할 수 없습니다")

# ... (생략: if __name__ == "__main__" 블록)
