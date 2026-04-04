import logging
from sqlalchemy.orm import Session
from datetime import datetime
from backend.models.models_v2 import Product, Deal, PriceHistory
from backend.services.normalizer.llm_normalizer import LlmNormalizer

logger = logging.getLogger(__name__)

class AggregatorService:
    """
    🔗 스크래핑 결과(Deal)를 정규화하여 DB의 Product와 매칭 및 병합하는 연동 레이어
    지능형 업서트(Upsert)와 가격 역사(Price History) 추적 로직이 포함됩니다.
    """
    
    def __init__(self, db_session: Session):
        self.db = db_session
        self.normalizer = LlmNormalizer()


    async def process_scraped_deal(self, community_id: int, scraped_data: dict) -> Deal:
        raw_title = scraped_data.get("title", "")
        price = scraped_data.get("price", 0)
        url = scraped_data.get("url", "")
        shop_name = scraped_data.get("shop_name", "")
        
        # 1. 원본 텍스트를 RegexNormalizer에 통과
        normalized = await self.normalizer.normalize(raw_title)

        # 2. Product 룩업(Upsert 로직의 핵심)
        product = self.db.query(Product).filter(
            Product.name == normalized.name,
            Product.brand == normalized.brand
        ).first()

        if not product:
             # 상품이 아예 없다면 신규 생성
             product = Product(
                 name=normalized.name,
                 brand=normalized.brand,
                 category=normalized.category,
                 current_lowest_price=price if price > 0 else None
             )
             self.db.add(product)
             self.db.commit()
             self.db.refresh(product)
             logger.debug(f"✨ 신규 제품 DB 등록: [{product.brand}] {product.name}")
             
             # 초기가격 히스토리(Price History) 삽입
             if price > 0:
                 self._insert_price_history(product.id, price)
        else:
             # 기존 상품 존재 시: 최저가가 더 저렴해졌다면 즉시 반영하고 골든타임(Price History) 기록
             if price > 0:
                 if not product.current_lowest_price or price < product.current_lowest_price:
                     product.current_lowest_price = price
                     self._insert_price_history(product.id, price)
             self.db.commit()

        # 3. 개별 스크랩 Deal 생성 또는 업데이트(Upsert)
        existing_deal = self.db.query(Deal).filter(Deal.url == url).first()
        if existing_deal:
             # 이미 수집했던 글이라면 가격 등 메타정보 갱신 (Upsert)
             if existing_deal.price != price and price > 0:
                 existing_deal.price = price
             logger.debug(f"🔄 기존 Deal 가격/상태 업데이트 (Upsert): {url}")
             self.db.commit()
             return existing_deal

        # 신규 등록
        new_deal = Deal(
            community_id=community_id,
            product_id=product.id,
            title=raw_title,
            price=price,
            url=url,
            shop_name=shop_name,
        )
        self.db.add(new_deal)
        self.db.commit()
        self.db.refresh(new_deal)
        
        logger.debug(f"🔗 딜 분석 및 DB 병합 완료: Product ID [{product.id}]")
        return new_deal

    def _insert_price_history(self, product_id: int, price: int):
        """특정 Product의 가격 변동(골든타임)을 추적합니다."""
        history = PriceHistory(
            product_id=product_id,
            price=price,
            recorded_at=datetime.now()
        )
        self.db.add(history)
        logger.info(f"📈 [Price History] 최저가 역사 기록! Product ID {product_id} -> {price:,}원")
