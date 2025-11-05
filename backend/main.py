# ... (상단 기존 코드 동일)
from sqlalchemy import desc, and_
from fastapi import FastAPI, HTTPException, Query, Request, BackgroundTasks, Depends
# (중략)

# ======= 관심상품 API 엔드포인트들 =======
@app.post("/api/wishlist", response_model=WishlistResponse)
async def create_wishlist(
    wishlist: WishlistCreate,
    db: Session = Depends(get_db_session)
):
    # (기존 내용 그대로)
    # ...
    return WishlistResponse(
        id=db_wishlist.id,
        keyword=db_wishlist.keyword,
        target_price=db_wishlist.target_price,
        current_lowest_price=db_wishlist.current_lowest_price,
        current_lowest_platform=db_wishlist.current_lowest_platform,
        current_lowest_product_title=db_wishlist.current_lowest_product_title,
        price_drop_percentage=price_drop_percentage,
        is_target_reached=(
            db_wishlist.current_lowest_price is not None and 
            db_wishlist.current_lowest_price <= db_wishlist.target_price
        ),
        is_active=db_wishlist.is_active,
        alert_enabled=db_wishlist.alert_enabled,
        created_at=db_wishlist.created_at,
        updated_at=db_wishlist.updated_at,
        last_checked=db_wishlist.last_checked
    )

@app.get("/api/wishlist", response_model=List[WishlistResponse])
async def get_wishlist(
    user_id: str = Query(default="default", description="사용자 ID"),
    active_only: bool = Query(default=True, description="활성상태만 조회"),
    db: Session = Depends(get_db_session)
):
    # (기존 내용 그대로)
    # ...
    return response_list

@app.delete("/api/wishlist/{wishlist_id}")
async def delete_wishlist(
    wishlist_id: int,
    user_id: str = Query(default="default", description="사용자 ID"),
    db: Session = Depends(get_db_session)
):
    """
    🗑️ 관심상품 삭제
    - Path: /api/wishlist/{wishlist_id}
    - Query: user_id (소유자 검증)
    반환: { "message": "삭제되었습니다" }
    """
    wishlist = db.query(KeywordWishlist).filter(
        and_(
            KeywordWishlist.id == wishlist_id,
            KeywordWishlist.user_id == user_id
        )
    ).first()

    if not wishlist:
        raise HTTPException(status_code=404, detail="관심상품을 찾을 수 없습니다")

    # 소프트 삭제가 필요하면 is_active=False로만 업데이트
    # 현재는 하드 삭제 수행
    db.delete(wishlist)
    db.commit()

    # 메트릭 갱신(가능하면 실제 상태에 맞게 조정)
    metrics["wishlist_stats"]["total_items"] = max(0, metrics["wishlist_stats"]["total_items"] - 1)

    return {"message": "삭제되었습니다"}

@app.post("/api/wishlist/{wishlist_id}/check-price")
async def manual_price_check(
    wishlist_id: int,
    user_id: str = Query(default="default", description="사용자 ID"),
    db: Session = Depends(get_db_session)
):
    # (기존 내용 그대로)
    # ...
    return {
        "message": f"'{wishlist.keyword}' 가격 체크를 완료했습니다",
        "keyword": wishlist.keyword,
        "current_price": wishlist.current_lowest_price,
        "target_price": wishlist.target_price,
        "updated_at": datetime.utcnow().isoformat()
    }

# ... (하단 uvicorn 실행부 동일)
