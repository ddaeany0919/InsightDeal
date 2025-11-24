import asyncio
import logging
import os
import sys
from datetime import datetime, timedelta
from typing import List, Dict, Optional
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.interval import IntervalTrigger
from apscheduler.triggers.cron import CronTrigger

# 프로젝트 루트 디렉토리를 Python path에 추가
sys.path.append(os.path.dirname(os.path.dirname(__file__)))

# 호환성 패치 적용 (Python 3.9 importlib.metadata 오류 해결)
try:
    from .metadata_fix import patch_importlib_metadata
    patch_importlib_metadata()
except Exception as e:
    logging.warning(f"⚠️ Metadata patch failed: {e}")

# ✅ 수정된 import 경로 (backend. 제거)
from core.notifications import notification_service
from database.session import get_db_session, create_db_session
from database.models import Deal, Product, PriceHistory, ProductPriceHistory, PriceAlert, FCMToken

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class PriceCollectionScheduler:
    """가격 수집 및 알림 스케줄러"""
    
    def __init__(self):
        self.scheduler = AsyncIOScheduler(
            timezone='Asia/Seoul',
            job_defaults={
                'coalesce': False,
                'max_instances': 3,
                'misfire_grace_time': 300  # 5분
            }
        )
        self.is_running = False
        
    async def start(self):
        """스케줄러 시작"""
        if self.is_running:
            logger.warning("⚠️ Scheduler already running")
            return
            
        try:
            # 작업 등록
            await self._register_jobs()
            
            # 스케줄러 시작
            self.scheduler.start()
            self.is_running = True
            
            logger.info("✅ Price collection scheduler started")
            
            # 즉시 한 번 실행
            await self.collect_active_prices()
            
        except Exception as e:
            logger.error(f"❌ Failed to start scheduler: {e}")
            raise
    
    async def stop(self):
        """스케줄러 정지"""
        if self.scheduler.running:
            self.scheduler.shutdown()
            
        self.is_running = False
        logger.info("🛑 Price collection scheduler stopped")
    
    async def _register_jobs(self):
        """스케줄링 작업 등록"""
        # 1. 활성 상품 가격 수집 - 15분마다
        self.scheduler.add_job(
            self.collect_active_prices,
            trigger=IntervalTrigger(minutes=15),
            id='collect_active_prices',
            name='Active Products Price Collection'
        )
        
        # 2. 전체 상품 가격 수집 - 매시간
        self.scheduler.add_job(
            self.collect_all_prices,
            trigger=CronTrigger(minute=0),  # 매시 정각
            id='collect_all_prices',
            name='All Products Price Collection'
        )
        
        # 3. 가격 변동 알림 체크 - 5분마다
        self.scheduler.add_job(
            self.check_price_alerts,
            trigger=IntervalTrigger(minutes=5),
            id='check_price_alerts',
            name='Price Alert Notifications'
        )
        
        # 4. 새 딜 스크래핑 - 10분마다 (확장된 커뮤니티 커버리지)
        self.scheduler.add_job(
            self.scrape_new_deals,
            trigger=IntervalTrigger(minutes=10),
            id='scrape_new_deals',
            name='New Deal Scraping'
        )
        
        # 5. 데이터 정리 - 매일 새벽 3시
        self.scheduler.add_job(
            self.cleanup_old_data,
            trigger=CronTrigger(hour=3, minute=0),
            id='cleanup_old_data',
            name='Database Cleanup'
        )
        
        logger.info("📋 Registered 5 scheduled jobs")
    
    async def collect_active_prices(self):
        """활성 추적 상품들의 가격 수집"""
        logger.info("🔍 Starting active price collection...")
        
        try:
            # DB에서 활성 추적 상품 목록 가져오기
            active_products = await self._get_active_tracked_products()
            
            if not active_products:
                logger.info("📋 No active products to track")
                return
            
            collected_count = 0
            error_count = 0
            
            for product in active_products:
                try:
                    # 각 쇼핑몰에서 가격 수집
                    new_prices = await self._collect_product_prices(product)
                    
                    if new_prices:
                        # 가격 히스토리에 저장
                        await self._save_price_history(product['id'], new_prices)
                        collected_count += 1
                        
                        # 가격 변동 체크 (5% 이상 변동 시 즉시 알림)
                        await self._check_significant_price_change(product, new_prices)
                        
                except Exception as e:
                    logger.error(f"❌ Error collecting price for product {product['id']}: {e}")
                    error_count += 1
                    
                # API 호출 간격 조절
                await asyncio.sleep(2)
            
            logger.info(
                f"✅ Active price collection completed: {collected_count} successful, {error_count} errors"
            )
            
        except Exception as e:
            logger.error(f"❌ Active price collection failed: {e}")
    
    async def collect_all_prices(self):
        """전체 상품 가격 수집 (시간당)"""
        logger.info("🔍 Starting full price collection...")
        
        try:
            # 전체 상품 목록 가져오기 (페이지네이션 고려)
            all_products = await self._get_all_products(limit=100)
            
            logger.info(f"📊 Found {len(all_products)} products to collect")
            
            collected_count = 0
            for product in all_products:
                try:
                    new_prices = await self._collect_product_prices(product)
                    
                    if new_prices:
                        await self._save_price_history(product['id'], new_prices)
                        collected_count += 1
                        
                except Exception as e:
                    logger.error(f"❌ Error in full collection for product {product['id']}: {e}")
                
                # 부하 분산을 위한 대기
                await asyncio.sleep(3)
            
            logger.info(f"✅ Full price collection completed: {collected_count} products")
            
        except Exception as e:
            logger.error(f"❌ Full price collection failed: {e}")
    
    async def check_price_alerts(self):
        """가격 알림 체크 및 발송"""
        try:
            # 목표가 도달한 상품들 찾기
            target_reached = await self._find_target_price_reached()
            
            for alert_data in target_reached:
                try:
                    user_tokens = await self._get_user_fcm_tokens(alert_data['user_id'])
                    
                    if user_tokens:
                        await notification_service.send_price_alert(
                            alert_data['product'], 
                            user_tokens
                        )
                        
                        # 알림 발송 기록 저장
                        await self._mark_alert_sent(alert_data['alert_id'])
                        
                except Exception as e:
                    logger.error(f"❌ Error sending price alert: {e}")
            
            if target_reached:
                logger.info(f"📢 Sent {len(target_reached)} price alerts")
                
        except Exception as e:
            logger.error(f"❌ Price alert check failed: {e}")
    
    async def scrape_new_deals(self):
        """새로운 딜 스크래핑 - 확장된 커뮤니티 커버리지"""
        logger.info("🔍 Scraping new deals from all communities...")
        
        try:
            # ✅ 수정된 스크래퍼 import 경로 (backend. 제거)
            from scrapers.ppomppu_scraper import PpomppuScraper
            from scrapers.ruliweb_scraper import RuliwebScraper
            from scrapers.clien_scraper import ClienScraper
            from scrapers.quasarzone_scraper import QuasarzoneScraper
            from scrapers.alippomppu_scraper import AlippomppuScraper
            from scrapers.fmkorea_scraper import FmkoreaScraper
            
            # DB 세션 생성
            db_session = create_db_session()
            
            scrapers = [
                PpomppuScraper(db_session),
                RuliwebScraper(db_session),
                ClienScraper(db_session),
                QuasarzoneScraper(db_session),
                AlippomppuScraper(db_session),
                FmkoreaScraper(db_session)
            ]
            
            total_new_deals = 0
            
            for scraper in scrapers:
                try:
                    # 기존 scrape 메서드 활용 (비동기 대신 동기 호출)
                    with scraper:
                        new_deals = scraper.run(limit=20)
                        
                        if new_deals:
                            total_new_deals += 1
                            logger.info(f"✅ {scraper.community_name}: Found new deals")
                            
                except Exception as e:
                    logger.error(f"❌ Scraper {scraper.__class__.__name__} failed: {e}")
            
            # DB 세션 정리
            db_session.close()
            
            if total_new_deals > 0:
                logger.info(f"✅ Scraping completed: {total_new_deals}/6 scrapers successful")
            else:
                logger.info("📋 No new deals found from any community")
                
        except Exception as e:
            logger.error(f"❌ Deal scraping failed: {e}")
    
    async def cleanup_old_data(self):
        """오래된 데이터 정리 (90일 이상)"""
        logger.info("🗑️ Starting database cleanup...")
        
        try:
            cutoff_date = datetime.now() - timedelta(days=90)
            
            # DB 세션을 사용하여 정리 작업
            with create_db_session() as session:
                # 오래된 가격 히스토리 삭제
                deleted_prices = session.query(PriceHistory).filter(
                    PriceHistory.checked_at < cutoff_date
                ).count()
                
                session.query(PriceHistory).filter(
                    PriceHistory.checked_at < cutoff_date
                ).delete(synchronize_session=False)
                
                session.commit()
                
            logger.info(
                f"✅ Cleanup completed: {deleted_prices} price records deleted"
            )
            
        except Exception as e:
            logger.error(f"❌ Database cleanup failed: {e}")
    
    # Helper methods
    
    async def _get_active_tracked_products(self) -> List[Dict]:
        """활성 추적 상품 목록 조회"""
        try:
            with create_db_session() as session:
                products = session.query(Product).filter(
                    Product.is_tracking == True
                ).all()
                
                return [
                    {
                        "id": p.id,
                        "product_id": p.product_id,
                        "url": p.url,
                        "title": p.title,
                        "current_price": p.current_price,
                        "target_price": p.target_price,
                        "user_id": p.user_id
                    }
                    for p in products
                ]
        except Exception as e:
            logger.error(f"❌ Failed to get active products: {e}")
            return []
    
    async def _get_all_products(self, limit: int = 100) -> List[Dict]:
        """전체 상품 목록 조회"""
        try:
            with create_db_session() as session:
                products = session.query(Product).limit(limit).all()
                return [
                    {
                        "id": p.id,
                        "url": p.url,
                        "title": p.title
                    }
                    for p in products
                ]
        except Exception as e:
            logger.error(f"❌ Failed to get all products: {e}")
            return []
    
    async def _collect_product_prices(self, product: Dict) -> Optional[Dict]:
        """특정 상품의 4몰 가격 수집"""
        # TODO: 실제 스크래퍼 연동 (현재는 시뮬레이션 로직)
        # 실제 구현시에는 product['url']을 기반으로 적절한 스크래퍼 선택 필요
        return None
    
    async def _save_price_history(self, product_id: int, prices: Dict):
        """가격 히스토리 저장"""
        try:
            with create_db_session() as session:
                # 1. ProductPriceHistory 추가
                history = ProductPriceHistory(
                    product_id=product_id,
                    price=prices.get('price', 0),
                    is_available=prices.get('is_available', True)
                )
                session.add(history)
                
                # 2. Product 현재가 업데이트
                product = session.query(Product).filter(Product.id == product_id).first()
                if product:
                    product.current_price = prices.get('price', 0)
                    product.last_checked = datetime.now()
                    
                    # 최저가 갱신 로직
                    if not product.lowest_price or product.current_price < product.lowest_price:
                        product.lowest_price = product.current_price
                
                session.commit()
        except Exception as e:
            logger.error(f"❌ Failed to save price history: {e}")
    
    async def _check_significant_price_change(self, product: Dict, new_prices: Dict):
        """유의미한 가격 변동 체크 (5% 이상)"""
        if not product.get('current_price'):
            return
            
        old_price = product['current_price']
        new_price = new_prices.get('price', 0)
        
        if new_price == 0: return
        
        # 5% 이상 하락 시
        if new_price < old_price * 0.95:
            logger.info(f"📉 Price drop detected for {product['title']}: {old_price} -> {new_price}")
            # 알림 발송 로직 호출 가능
            
    async def _find_target_price_reached(self) -> List[Dict]:
        """목표가 도달한 알림 찾기"""
        try:
            with create_db_session() as session:
                # PriceAlert 테이블과 Product 테이블 조인
                alerts = session.query(PriceAlert).join(Product).filter(
                    PriceAlert.is_active == True,
                    Product.current_price <= PriceAlert.target_price
                ).all()
                
                return [
                    {
                        "alert_id": a.id,
                        "user_id": a.user_id,
                        "product": {
                            "title": a.product.title,
                            "price": a.product.current_price,
                            "url": a.product.url
                        }
                    }
                    for a in alerts
                ]
        except Exception as e:
            logger.error(f"❌ Failed to find target price alerts: {e}")
            return []
    
    async def _get_user_fcm_tokens(self, user_id: str) -> List[str]:
        """사용자 FCM 토큰 조회"""
        try:
            with create_db_session() as session:
                tokens = session.query(FCMToken.token).filter(
                    FCMToken.user_id == user_id,
                    FCMToken.is_active == True
                ).all()
                return [t[0] for t in tokens]
        except Exception as e:
            logger.error(f"❌ Failed to get FCM tokens: {e}")
            return []
    
    async def _mark_alert_sent(self, alert_id: int):
        """알림 발송 완료 표시"""
        try:
            with create_db_session() as session:
                alert = session.query(PriceAlert).filter(PriceAlert.id == alert_id).first()
                if alert:
                    alert.last_triggered = datetime.now()
                    alert.trigger_count += 1
                    session.commit()
        except Exception as e:
            logger.error(f"❌ Failed to mark alert sent: {e}")

async def main():
    """스케줄러 메인 실행 함수"""
    scheduler = PriceCollectionScheduler()
    
    try:
        await scheduler.start()
        
        logger.info("⏰ Enhanced scheduler with 6 communities running... Press Ctrl+C to stop")
        
        # 무한 대기 (Ctrl+C로 종료)
        while True:
            await asyncio.sleep(60)
            
    except KeyboardInterrupt:
        logger.info("🛑 Received shutdown signal")
    except Exception as e:
        logger.error(f"❌ Scheduler crashed: {e}")
    finally:
        await scheduler.stop()
        logger.info("👋 Enhanced scheduler shut down gracefully")

if __name__ == "__main__":
    asyncio.run(main())
