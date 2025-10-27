"""
🗃️ InsightDeal Database Schema - Day 5

폴센트 수준을 뛰어넘는 가격 히스토리 시스템:
- 90일까지 지원 (폴센트 30일 vs InsightDeal 90일)
- 4개 플랫폼 동시 추적
- 사용자 맞춤 목표가 알림
- 빠른 조회를 위한 최적화된 인덱스
"""

-- 1. 상품 마스터 테이블
CREATE TABLE IF NOT EXISTS products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200), -- 검색 최적화용
    category VARCHAR(100),
    first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    
    -- 빠른 검색을 위한 인덱스
    UNIQUE(normalized_name)
);

CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_active ON products(is_active) WHERE is_active = true;

-- 2. 가격 히스토리 테이블 (핵심 테이블)
CREATE TABLE IF NOT EXISTS price_history (
    id SERIAL PRIMARY KEY,
    product_id INTEGER REFERENCES products(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL, -- coupang, eleventh, gmarket, auction
    product_url TEXT NOT NULL,
    
    -- 가격 정보
    current_price INTEGER NOT NULL,
    original_price INTEGER DEFAULT 0,
    discount_rate INTEGER DEFAULT 0,
    shipping_fee INTEGER DEFAULT 0,
    
    -- 메타데이터
    seller_name VARCHAR(100),
    rating DECIMAL(2,1) DEFAULT 0,
    review_count INTEGER DEFAULT 0,
    is_available BOOLEAN DEFAULT true,
    
    -- 추적 정보
    captured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    trace_id VARCHAR(50), -- 로그 추적용
    
    -- 성능 최적화 인덱스 (사용자는 최근 90일 데이터를 자주 조회)
    INDEX idx_price_history_product_date (product_id, captured_at DESC),
    INDEX idx_price_history_platform (platform, captured_at DESC),
    INDEX idx_price_history_recent (captured_at DESC) WHERE captured_at > CURRENT_TIMESTAMP - INTERVAL '90 days'
);

-- 3. 사용자 추적 목록
CREATE TABLE IF NOT EXISTS tracks (
    id SERIAL PRIMARY KEY,
    product_id INTEGER REFERENCES products(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL,
    product_url TEXT NOT NULL,
    
    -- 사용자 설정
    target_price INTEGER, -- 목표가 (이 가격 이하로 떨어지면 알림)
    user_device_token VARCHAR(255), -- FCM 토큰 (간소화)
    
    -- 추적 상태
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_checked_at TIMESTAMP,
    last_notified_at TIMESTAMP,
    
    -- 최근 가격 (빠른 비교용)
    last_price INTEGER,
    price_trend VARCHAR(10) DEFAULT 'stable', -- up, down, stable
    
    INDEX idx_tracks_active (is_active, last_checked_at) WHERE is_active = true,
    INDEX idx_tracks_device (user_device_token)
);

-- 4. 알림 로그
CREATE TABLE IF NOT EXISTS price_alerts (
    id SERIAL PRIMARY KEY,
    track_id INTEGER REFERENCES tracks(id) ON DELETE CASCADE,
    product_id INTEGER REFERENCES products(id) ON DELETE CASCADE,
    
    -- 알림 내용
    alert_type VARCHAR(20) NOT NULL, -- target_reached, price_drop, price_rise
    previous_price INTEGER,
    current_price INTEGER,
    price_diff INTEGER,
    
    -- 알림 상태
    notification_sent BOOLEAN DEFAULT false,
    sent_at TIMESTAMP,
    fcm_message_id VARCHAR(100),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_alerts_track (track_id, created_at DESC),
    INDEX idx_alerts_type (alert_type, created_at DESC)
);

-- 5. 스케줄러 작업 상태 (모니터링용)
CREATE TABLE IF NOT EXISTS scheduler_jobs (
    id SERIAL PRIMARY KEY,
    job_name VARCHAR(50) NOT NULL,
    job_type VARCHAR(20) NOT NULL, -- price_collection, alert_check
    
    -- 실행 상태
    status VARCHAR(20) DEFAULT 'pending', -- pending, running, completed, failed
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    -- 결과 정보
    products_processed INTEGER DEFAULT 0,
    successful_updates INTEGER DEFAULT 0,
    failed_updates INTEGER DEFAULT 0,
    error_message TEXT,
    
    -- 성능 추적
    execution_time_ms INTEGER,
    trace_id VARCHAR(50),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_scheduler_jobs_status (status, created_at DESC),
    INDEX idx_scheduler_jobs_type (job_type, created_at DESC)
);

-- 6. 편의 뷰: 최신 가격 정보
CRETE VIEW latest_prices AS
SELECT DISTINCT ON (product_id, platform) 
    product_id,
    platform,
    current_price,
    original_price,
    discount_rate,
    captured_at,
    is_available
FROM price_history
WHERE captured_at > CURRENT_TIMESTAMP - INTERVAL '7 days'
ORDER BY product_id, platform, captured_at DESC;

-- 7. 성능 최적화: 파티셔닝 준비 (추후 데이터 증가 시)
-- price_history 테이블을 월별로 파티셔닝할 수 있도록 준비

-- 8. 초기 데이터 정리 함수
CREATE OR REPLACE FUNCTION cleanup_old_price_data()
RETURNS void AS $$
BEGIN
    -- 90일 이전 데이터 정리 (사용자는 최근 데이터에만 관심)
    DELETE FROM price_history 
    WHERE captured_at < CURRENT_TIMESTAMP - INTERVAL '90 days';
    
    -- 완료된 스케줄러 작업 로그 정리 (30일)
    DELETE FROM scheduler_jobs 
    WHERE status = 'completed' 
      AND created_at < CURRENT_TIMESTAMP - INTERVAL '30 days';
    
    -- 비활성 추적 정리 (30일간 체크되지 않음)
    UPDATE tracks 
    SET is_active = false 
    WHERE last_checked_at < CURRENT_TIMESTAMP - INTERVAL '30 days'
      AND is_active = true;
END;
$$ LANGUAGE plpgsql;

-- 9. 트리거: 자동 타임스탬프 업데이트
CREATE OR REPLACE FUNCTION update_last_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_products_updated
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated_at();