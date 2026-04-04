from sqlalchemy import Column, Integer, String, Float, DateTime, ForeignKey, Boolean, Text, Index
from sqlalchemy.orm import relationship, declarative_base
from sqlalchemy.sql import func
import datetime

Base = declarative_base()

class TimestampMixin:
    """생성/수정 시간 자동 기록을 위한 Mixin"""
    created_at = Column(DateTime, default=func.now(), nullable=False)
    updated_at = Column(DateTime, default=func.now(), onupdate=func.now(), nullable=False)

class Community(Base, TimestampMixin):
    """수집 대상 커뮤니티 (뽐뿌, 펨코 등)"""
    __tablename__ = "communities"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(50), unique=True, nullable=False) # 예: 'ppomppu'
    display_name = Column(String(100)) # 예: '뽐뿌'
    base_url = Column(String(255))
    is_active = Column(Boolean, default=True)

    deals = relationship("Deal", back_populates="community")

class Product(Base, TimestampMixin):
    """
    정규화된 상품 엔티티 (Aggregation의 핵심)
    여러 커뮤니티의 Deal들이 이 하나의 Product에 묶임
    """
    __tablename__ = "products"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), nullable=False, index=True) # 정규화된 상품명
    brand = Column(String(100), index=True)
    category = Column(String(50), index=True)
    representative_image = Column(String(500)) # 대표 이미지 URL
    
    # 현재 최저가 정보 (캐싱용)
    current_lowest_price = Column(Integer)
    last_detected_at = Column(DateTime, default=func.now())

    deals = relationship("Deal", back_populates="product")
    price_history = relationship("PriceHistory", back_populates="product", cascade="all, delete-orphan")

    __table_args__ = (
        Index('ix_product_name_brand', 'name', 'brand'),
    )

class Deal(Base, TimestampMixin):
    """개별 커뮤니티에 올라온 핫딜 게시글"""
    __tablename__ = "deals"

    id = Column(Integer, primary_key=True, index=True)
    community_id = Column(Integer, ForeignKey("communities.id"), nullable=False)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=True) # Aggregator에 의해 매칭됨
    
    title = Column(String(500), nullable=False) # 게시글 원본 제목
    price = Column(Integer)
    url = Column(String(1000), nullable=False, unique=True) # 중복 수집 방지 키
    
    shop_name = Column(String(100)) # 쇼핑몰 (쿠팡, 11번가 등)
    shipping_fee = Column(String(100))
    
    # 메타 데이터
    like_count = Column(Integer, default=0)
    comment_count = Column(Integer, default=0)
    is_sold_out = Column(Boolean, default=False)

    community = relationship("Community", back_populates="deals")
    product = relationship("Product", back_populates="deals")

class PriceHistory(Base):
    """시계열 가격 변동 이력 (Price History 차트용)"""
    __tablename__ = "price_history"

    id = Column(Integer, primary_key=True, index=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False)
    price = Column(Integer, nullable=False)
    recorded_at = Column(DateTime, default=func.now(), index=True)

    product = relationship("Product", back_populates="price_history")
