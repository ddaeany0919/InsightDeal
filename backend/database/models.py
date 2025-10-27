from sqlalchemy import Column, Integer, String, Boolean, ForeignKey, TIMESTAMP, TEXT, UniqueConstraint, Float, create_engine
from sqlalchemy.orm import relationship, declarative_base
from sqlalchemy.sql import func
from datetime import datetime
import os

# 데이터베이스 Base 선언
Base = declarative_base()

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
    deal_reactions = relationship("DealReaction", back_populates="deal")

class PriceHistory(Base):
    __tablename__ = "price_history"

    id = Column(Integer, primary_key=True, index=True)
    deal_id = Column(Integer, ForeignKey("deals.id"), nullable=False)
    price = Column(String(100), nullable=False)
    checked_at = Column(TIMESTAMP(timezone=True), server_default=func.now())

    deal = relationship("Deal", back_populates="price_history")

# 새로 추가된 테이블들 (쿠팡 추적 및 FCM 지원)

class Product(Base):
    """쿠팡 상품 추적 테이블 (타이밍/폴센트 기능)"""
    __tablename__ = "products"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(String(50), default='anonymous', nullable=False)
    product_id = Column(String(50))  # 쿠팡 상품 ID
    url = Column(TEXT, nullable=False)
    title = Column(TEXT, nullable=False)
    brand = Column(String(100))
    image_url = Column(TEXT)
    
    # 가격 정보
    current_price = Column(Integer)
    original_price = Column(Integer)  # 정가
    lowest_price = Column(Integer)
    highest_price = Column(Integer)
    average_price = Column(Float)
    target_price = Column(Integer)  # 사용자 설정 목표 가격
    
    # 추적 설정
    is_tracking = Column(Boolean, default=True)
    alert_enabled = Column(Boolean, default=True)
    
    # 메타 정보
    created_at = Column(TIMESTAMP(timezone=True), server_default=func.now())
    updated_at = Column(TIMESTAMP(timezone=True), server_default=func.now(), onupdate=func.now())
    last_checked = Column(TIMESTAMP(timezone=True))
    
    # 관계 설정
    product_price_history = relationship("ProductPriceHistory", back_populates="product")
    price_alerts = relationship("PriceAlert", back_populates="product")
    
    # 유니크 제약 조건
    __table_args__ = (UniqueConstraint('user_id', 'product_id', name='unique_user_product'),)

class ProductPriceHistory(Base):
    """쿠팡 상품 가격 히스토리 테이블"""
    __tablename__ = "product_price_history"
    
    id = Column(Integer, primary_key=True, index=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False)
    price = Column(Integer, nullable=False)
    original_price = Column(Integer)
    discount_rate = Column(Integer)  # 할인율
    is_available = Column(Boolean, default=True)  # 재고 여부
    tracked_at = Column(TIMESTAMP(timezone=True), server_default=func.now())
    
    # 관계 설정
    product = relationship("Product", back_populates="product_price_history")
    
    # 유니크 제약 조건 (같은 시간대 중복 방지)
    __table_args__ = (UniqueConstraint('product_id', 'tracked_at', name='unique_product_time'),)

class FCMToken(Base):
    """FCM 푸시 토큰 관리 테이블"""
    __tablename__ = "fcm_tokens"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(String(50), default='anonymous', nullable=False)
    token = Column(TEXT, unique=True, nullable=False)
    device_info = Column(String(255))  # 기기 정보
    app_version = Column(String(20))   # 앱 버전
    
    # 토큰 상태
    is_active = Column(Boolean, default=True)
    last_used = Column(TIMESTAMP(timezone=True), server_default=func.now())
    created_at = Column(TIMESTAMP(timezone=True), server_default=func.now())
    
    # 관계 설정
    price_alerts = relationship("PriceAlert", back_populates="fcm_token")

class PriceAlert(Base):
    """사용자 가격 알림 설정 테이블"""
    __tablename__ = "price_alerts"
    
    id = Column(Integer, primary_key=True, index=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False)
    user_id = Column(String(50), nullable=False)
    fcm_token_id = Column(Integer, ForeignKey("fcm_tokens.id"))
    
    # 알림 조건
    target_price = Column(Integer, nullable=False)
    alert_type = Column(String(20), default='below')  # 'below', 'above', 'change'
    percentage_threshold = Column(Float)  # 가격 변동 % 임계값
    
    # 알림 상태
    is_active = Column(Boolean, default=True)
    last_triggered = Column(TIMESTAMP(timezone=True))
    trigger_count = Column(Integer, default=0)
    
    # 메타 정보
    created_at = Column(TIMESTAMP(timezone=True), server_default=func.now())
    updated_at = Column(TIMESTAMP(timezone=True), server_default=func.now(), onupdate=func.now())
    
    # 관계 설정
    product = relationship("Product", back_populates="price_alerts")
    fcm_token = relationship("FCMToken", back_populates="price_alerts")

class UserSettings(Base):
    """사용자 개인 설정 테이블"""
    __tablename__ = "user_settings"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(String(50), unique=True, nullable=False)
    
    # 알림 설정
    push_enabled = Column(Boolean, default=True)
    deal_alerts = Column(Boolean, default=True)
    price_alerts = Column(Boolean, default=True)
    
    # 관심 카테고리 (JSON 형태)
    favorite_categories = Column(TEXT)  # ["전자기기", "의류", "식품"]
    favorite_shops = Column(TEXT)       # ["쿠팡", "G마켓", "11번가"]
    
    # 가격대 필터
    min_price = Column(Integer, default=0)
    max_price = Column(Integer, default=1000000)
    
    # 메타 정보
    created_at = Column(TIMESTAMP(timezone=True), server_default=func.now())
    updated_at = Column(TIMESTAMP(timezone=True), server_default=func.now(), onupdate=func.now())

class UserActivity(Base):
    """사용자 활동 로그 테이블 (AI 추천 알고리즘용)"""
    __tablename__ = "user_activities"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(String(50), nullable=False)
    
    # 활동 정보
    activity_type = Column(String(20), nullable=False)  # 'view', 'click', 'share', 'favorite'
    target_type = Column(String(20), nullable=False)    # 'deal', 'product', 'category'
    target_id = Column(Integer, nullable=False)
    
    # 활동 상세
    category = Column(String(50))
    shop_name = Column(String(100))
    price_range = Column(String(20))  # '10만원대', '1만원대'
    
    # 메타 정보
    created_at = Column(TIMESTAMP(timezone=True), server_default=func.now())
    ip_address = Column(String(45))    # IPv6 지원
    user_agent = Column(TEXT)

class DealReaction(Base):
    """딜 커뮤니티 반응 점수 테이블 (꿀딜 지수)"""
    __tablename__ = "deal_reactions"
    
    id = Column(Integer, primary_key=True, index=True)
    deal_id = Column(Integer, ForeignKey("deals.id"), nullable=False)
    
    # 반응 데이터
    view_count = Column(Integer, default=0)
    comment_count = Column(Integer, default=0)
    positive_reactions = Column(Integer, default=0)  # 대박, 굿딜, 감사
    negative_reactions = Column(Integer, default=0)  # 비싸, 별로, 재고없음
    
    # 꿀딜 지수 (0-100)
    honey_score = Column(Float, default=0.0)
    confidence_level = Column(String(10), default='low')  # low, medium, high
    
    # 메타 정보
    last_updated = Column(TIMESTAMP(timezone=True), server_default=func.now())
    analyzed_at = Column(TIMESTAMP(timezone=True), server_default=func.now())
    
    # 관계 설정
    deal = relationship("Deal", back_populates="deal_reactions")

# 데이터베이스 세션 생성 함수
def get_db_engine():
    """데이터베이스 엔진 생성"""
    database_url = os.getenv(
        "DATABASE_URL", 
        "postgresql://insightdeal:password@localhost:5432/insightdeal"
    )
    
    engine = create_engine(
        database_url,
        echo=False,  # SQL 로그는 제한적으로
        pool_size=10,
        max_overflow=20,
        pool_pre_ping=True,
        pool_recycle=3600  # 1시간
    )
    
    return engine

def create_all_tables():
    """모든 테이블 생성"""
    engine = get_db_engine()
    Base.metadata.create_all(engine)
    print("✅ 모든 데이터베이스 테이블이 생성되었습니다.")
    return engine
