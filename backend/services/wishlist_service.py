from datetime import datetime, timezone
import logging
import re

from sqlalchemy import func, not_, or_
from sqlalchemy.orm import Session, joinedload

try:
    from backend.core.deal_normalizer import (
        clean_canonical_title,
        extract_numeric_price,
        is_collection_dummy_price,
        resolve_canonical_shop_name,
        to_krw_equivalent,
    )
    from backend.core.utils import to_utc_iso
    from backend.database.models import Deal
    from backend.models.wishlist_models import Wishlist
except ImportError:
    from core.deal_normalizer import (
        clean_canonical_title,
        extract_numeric_price,
        is_collection_dummy_price,
        resolve_canonical_shop_name,
        to_krw_equivalent,
    )
    from core.utils import to_utc_iso
    from database.models import Deal
    from models.wishlist_models import Wishlist

logger = logging.getLogger(__name__)


def _escape_like(raw_val: str) -> str:
    """SQL LIKE / ILIKE 와일드카드 문자(\\, %, _)를 리터럴 매칭되도록 이스케이프합니다."""
    if not raw_val:
        return ""
    return raw_val.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


class WishlistService:
    @staticmethod
    async def create_from_url(product_url: str, target_price: int, user_id: str, db: Session):
        """
        URL 기반 위시리스트 등록 (외부 쇼핑몰 크롤링/가격비교 없이 DB 저장만 수행)
        """
        logger.info(f"[WISHLIST_SERVICE] URL 등록 시도: {product_url}")

        keyword = (product_url or "").strip()
        if not keyword:
            raise Exception("상품 URL이 비어 있습니다")

        existing = db.query(Wishlist).filter(
            Wishlist.user_id == user_id,
            Wishlist.keyword == keyword,
        ).first()

        if existing:
            raise Exception(f"이미 '{keyword}' 관심상품이 등록되어 있습니다")

        wishlist = Wishlist(
            keyword=keyword,
            target_price=target_price,
            user_id=user_id,
        )

        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)

        logger.info(f"[WISHLIST_SERVICE] URL로 등록 성공: {keyword}")
        return wishlist

    @staticmethod
    async def create_from_keyword(keyword: str, target_price: int, user_id: str, db: Session):
        """
        키워드로 위시리스트 등록 (외부 쇼핑몰 크롤링/가격비교 없이 DB 저장만 수행)
        """
        logger.info(f"[WISHLIST_SERVICE] 키워드 등록 시도: {keyword}")

        clean_keyword = (keyword or "").strip()
        if not clean_keyword:
            raise Exception("키워드가 비어 있습니다")

        existing = db.query(Wishlist).filter(
            Wishlist.user_id == user_id,
            Wishlist.keyword == clean_keyword,
        ).first()

        if existing:
            raise Exception(f"이미 '{clean_keyword}' 관심상품이 등록되어 있습니다")

        wishlist = Wishlist(
            keyword=clean_keyword,
            target_price=target_price,
            user_id=user_id,
        )

        db.add(wishlist)
        db.commit()
        db.refresh(wishlist)

        logger.info(f"[WISHLIST_SERVICE] 키워드로 등록 성공: {clean_keyword}")
        return wishlist

    @staticmethod
    async def get_user_wishlist(user_id: str, db: Session):
        """사용자의 위시리스트 목록 조회"""
        logger.info(f"[WISHLIST_SERVICE] 위시리스트 조회: user_id={user_id}")
        return db.query(Wishlist).filter(Wishlist.user_id == user_id).all()

    @staticmethod
    async def check_price(wishlist_id: int, user_id: str, db: Session):
        """내부 커뮤니티 핫딜 DB(models.Deal) 기반 위시리스트 최저가 체크"""
        logger.info(f"[WISHLIST_SERVICE] 가격 체크 시도: wishlist_id={wishlist_id}, user_id={user_id}")

        wishlist = db.query(Wishlist).filter(
            Wishlist.id == wishlist_id,
            Wishlist.user_id == user_id,
        ).first()

        if not wishlist:
            raise Exception("위시리스트를 찾을 수 없습니다")

        now_utc = datetime.now(timezone.utc).replace(tzinfo=None)

        def _not_found_response() -> dict:
            wishlist.updated_at = now_utc
            db.commit()
            db.refresh(wishlist)
            return {
                "message": "최저가를 찾을 수 없습니다",
                "keyword": wishlist.keyword,
                "current_price": None,
                "target_price": wishlist.target_price,
                "platform": None,
                "product_url": None,
                "title": None,
                "is_target_reached": False,
                "updated_at": to_utc_iso(wishlist.updated_at),
            }

        raw_keyword = (wishlist.keyword or "").strip()
        logger.info(f"[WISHLIST_SERVICE] 가격 체크: keyword={raw_keyword}")
        if not raw_keyword:
            logger.warning("[WISHLIST_SERVICE] 가격 체크 실패: 빈 키워드")
            return _not_found_response()

        positive_tokens: list[str] = []
        exclude_tokens: list[str] = []
        for tok in raw_keyword.split():
            t = tok.strip()
            if not t:
                continue
            if t.startswith("-") and len(t) > 1:
                exclude_tokens.append(t[1:])
            elif t.startswith((":", "<=", ">=")):
                continue
            else:
                positive_tokens.append(t)

        if not positive_tokens or not any(re.search(r"[가-힣a-zA-Z0-9]", tok) for tok in positive_tokens):
            logger.warning("[WISHLIST_SERVICE] 가격 체크 실패: 유효 검색 토큰 없음")
            return _not_found_response()

        query = (
            db.query(Deal)
            .options(joinedload(Deal.community))
            .filter(
                Deal.is_closed == False,
                Deal.price.isnot(None),
                Deal.price != "",
                Deal.price.notin_(["0", "-"]),
            )
        )

        for tok in positive_tokens:
            escaped_tok = _escape_like(tok)
            like_pattern = f"%{escaped_tok}%"
            token_clauses = [
                Deal.title.ilike(like_pattern, escape="\\"),
                Deal.base_product_name.ilike(like_pattern, escape="\\"),
                Deal.search_keywords.ilike(like_pattern, escape="\\"),
                Deal.options_data.ilike(like_pattern, escape="\\"),
                Deal.brand.ilike(like_pattern, escape="\\"),
                Deal.model_code.ilike(like_pattern, escape="\\"),
            ]
            compact_tok = re.sub(r"\s+", "", tok)
            if len(compact_tok) >= 2 and re.search(r"[가-힣a-zA-Z0-9]", compact_tok):
                compact_like = f"%{_escape_like(compact_tok)}%"
                token_clauses.append(func.replace(Deal.title, " ", "").ilike(compact_like, escape="\\"))
                token_clauses.append(func.replace(Deal.base_product_name, " ", "").ilike(compact_like, escape="\\"))
            query = query.filter(or_(*token_clauses))

        for et in exclude_tokens:
            et_clean = et.strip()
            if not et_clean:
                continue
            et_like = f"%{_escape_like(et_clean)}%"
            query = query.filter(not_(Deal.title.ilike(et_like, escape="\\")))

        # 최신 300건 선컷오프로 구형 최저가 또는 무효 가격 뒤의 유효 딜이 누락되지 않도록 배치 페이징 스캔
        batch_size = 300
        max_scan = 3000
        offset = 0
        ordered_query = query.order_by(Deal.indexed_at.desc(), Deal.id.desc())

        best = None
        while offset < max_scan:
            batch_limit = min(batch_size, max_scan - offset)
            batch_deals = ordered_query.offset(offset).limit(batch_limit).all()
            if not batch_deals:
                break

            for deal in batch_deals:
                raw_price = extract_numeric_price(deal.price)
                if raw_price <= 0:
                    continue
                if is_collection_dummy_price(raw_price, deal.title or "", deal.currency or "KRW"):
                    continue
                krw_price = int(round(to_krw_equivalent(raw_price, deal.currency)))
                if krw_price <= 0:
                    continue

                if deal.indexed_at is None:
                    indexed_ts = 0.0
                elif getattr(deal.indexed_at, "tzinfo", None) is None:
                    indexed_ts = deal.indexed_at.replace(tzinfo=timezone.utc).timestamp()
                else:
                    indexed_ts = deal.indexed_at.astimezone(timezone.utc).timestamp()

                cand_key = (krw_price, -indexed_ts, -int(deal.id or 0))
                if best is None or cand_key < best["key"]:
                    best = {
                        "deal": deal,
                        "krw_price": krw_price,
                        "key": cand_key,
                    }

            if len(batch_deals) < batch_limit:
                break
            offset += len(batch_deals)

        if best is None:
            logger.warning("[WISHLIST_SERVICE] 가격 체크 실패: 조건에 맞는 활성 양수 딜 없음")
            return _not_found_response()

        best_deal = best["deal"]
        best_krw_price = best["krw_price"]

        platform_name = resolve_canonical_shop_name(
            raw_title=best_deal.title or "",
            ecommerce_url=best_deal.ecommerce_link or "",
            current_shop=best_deal.shop_name or "",
            post_url=best_deal.post_link or "",
            default_empty="",
        )
        if not platform_name:
            platform_name = (
                (best_deal.community.display_name if best_deal.community else None)
                or (best_deal.community.name if best_deal.community else None)
                or "커뮤니티 핫딜"
            )

        product_url = (best_deal.ecommerce_link or "").strip() or (best_deal.post_link or "").strip() or None
        clean_title = clean_canonical_title(best_deal.title or "") or (best_deal.title or "").strip()
        is_target_reached = bool(wishlist.target_price is not None and best_krw_price <= wishlist.target_price)

        wishlist.updated_at = now_utc
        db.commit()
        db.refresh(wishlist)

        logger.info(f"[WISHLIST_SERVICE] 가격 체크 성공: {best_krw_price}원 - {platform_name}")

        return {
            "message": "가격 체크 완료",
            "keyword": wishlist.keyword,
            "current_price": best_krw_price,
            "target_price": wishlist.target_price,
            "platform": platform_name,
            "product_url": product_url,
            "title": clean_title,
            "is_target_reached": is_target_reached,
            "updated_at": to_utc_iso(wishlist.updated_at),
        }

    @staticmethod
    async def delete_wishlist(wishlist_id: int, user_id: str, db: Session):
        """위시리스트 삭제"""
        logger.info(f"[WISHLIST_SERVICE] 삭제 시도: wishlist_id={wishlist_id}, user_id={user_id}")

        wishlist = db.query(Wishlist).filter(
            Wishlist.id == wishlist_id,
            Wishlist.user_id == user_id,
        ).first()

        if not wishlist:
            raise Exception("위시리스트를 찾을 수 없습니다")

        db.delete(wishlist)
        db.commit()
        logger.info(f"[WISHLIST_SERVICE] 삭제 성공: ID={wishlist_id}")

    @staticmethod
    async def update_wishlist(wishlist_id: int, user_id: str, updates: dict, db: Session):
        """위시리스트 업데이트"""
        logger.info(f"[WISHLIST_SERVICE] 업데이트 시도: wishlist_id={wishlist_id}, user_id={user_id}")

        wishlist = db.query(Wishlist).filter(
            Wishlist.id == wishlist_id,
            Wishlist.user_id == user_id,
        ).first()

        if not wishlist:
            raise Exception("위시리스트를 찾을 수 없습니다")

        for key, value in updates.items():
            if hasattr(wishlist, key):
                setattr(wishlist, key, value)

        wishlist.updated_at = datetime.now(timezone.utc).replace(tzinfo=None)
        db.commit()
        db.refresh(wishlist)

        logger.info(f"[WISHLIST_SERVICE] 업데이트 성공: ID={wishlist_id}")
        return wishlist
