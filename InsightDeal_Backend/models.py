from sqlalchemy import Column, Integer, String, Boolean, ForeignKey, TIMESTAMP, TEXT, UniqueConstraint
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

try:
    # 서버로 실행될 때를 위한 상대 경로 임포트
    from .database import Base
except ImportError:
    # 직접 스크립트를 실행할 때를 위한 절대 경로 임포트
    from database import Base

class Community(Base):
    __tablename__ = "communities"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(50), unique=True, nullable=False)
    base_url = Column(String(255), nullable=False)

    deals = relationship("Deal", back_populates="community")

class Deal(Base):
    __tablename__ = "deals"

    id = Column(Integer, primary_key=True, index=True)
    source_community_id = Column(Integer, ForeignKey("communities.id"), nullable=False)
    title = Column(TEXT, nullable=False)
    post_link = Column(String(2048), nullable=False)
    ecommerce_link = Column(String(2048), nullable=True)
    shop_name = Column(String(100))
    price = Column(String(100))
    shipping_fee = Column(String(100))
    image_url = Column(String(2048))
    category = Column(String(100), default="기타")
    indexed_at = Column(TIMESTAMP(timezone=True), server_default=func.now())
    is_closed = Column(Boolean, default=False, nullable=False)
    deal_type = Column(String(50), default='일반', nullable=False)
    content_html = Column(TEXT, nullable=True)
    group_id = Column(String(32), index=True, nullable=True)
    has_options = Column(Boolean, default=False, nullable=False)
    options_data = Column(TEXT, nullable=True)
    base_product_name = Column(String(500), nullable=True)

    __table_args__ = (UniqueConstraint('post_link', 'title', name='_post_link_title_uc'),)

    community = relationship("Community", back_populates="deals")
    price_history = relationship("PriceHistory", back_populates="deal")

class PriceHistory(Base):
    __tablename__ = "price_history"

    id = Column(Integer, primary_key=True, index=True)
    deal_id = Column(Integer, ForeignKey("deals.id"), nullable=False)
    price = Column(String(100), nullable=False)
    checked_at = Column(TIMESTAMP(timezone=True), server_default=func.now())

    deal = relationship("Deal", back_populates="price_history")