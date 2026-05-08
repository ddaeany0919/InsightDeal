import os
import logging
from typing import List, Dict, Optional
from datetime import datetime

try:
    import firebase_admin
    from firebase_admin import credentials, messaging
    FIREBASE_AVAILABLE = True
except ImportError:
    FIREBASE_AVAILABLE = False
    logging.warning("Firebase Admin SDK not installed - FCM notifications disabled")

logger = logging.getLogger(__name__)

class FCMNotificationService:
    """Firebase Cloud Messaging 알림 서비스"""
    
    def __init__(self):
        self.app = None
        self.initialized = False
        
        if FIREBASE_AVAILABLE:
            self._initialize_firebase()
    
    def _initialize_firebase(self):
        """Firebase Admin SDK 초기화"""
        try:
            # 환경변수에서 서비스 계정 키 경로 가져오기
            service_account_path = os.getenv(
                "FIREBASE_SERVICE_ACCOUNT_KEY", 
                "firebase-service-account.json"
            )
            
            if os.path.exists(service_account_path):
                cred = credentials.Certificate(service_account_path)
                self.app = firebase_admin.initialize_app(cred)
                self.initialized = True
                logger.info("✅ Firebase FCM initialized successfully")
            else:
                logger.warning(
                    f"⚠️ Firebase service account file not found: {service_account_path}"
                )
                
        except ValueError as e:
            if "already exists" in str(e):
                # 이미 초기화된 경우
                self.app = firebase_admin.get_app()
                self.initialized = True
                logger.info("✅ Firebase FCM already initialized")
            else:
                logger.error(f"❌ Firebase FCM initialization failed: {e}")
        except Exception as e:
            logger.error(f"❌ Firebase FCM initialization failed: {e}")
    
    def is_available(self) -> bool:
        """FCM 서비스 사용 가능 여부"""
        return FIREBASE_AVAILABLE and self.initialized
    
    def send_notification(
        self, 
        tokens: List[str], 
        title: str, 
        body: str, 
        data: Dict[str, str] = None
    ) -> bool:
        """FCM 푸시 알림 전송"""
        if not self.is_available():
            logger.warning("⚠️ FCM service not available")
            return False
            
        if not tokens:
            logger.warning("⚠️ No FCM tokens provided")
            return False
        
        try:
            # 유효한 토큰만 필터링
            valid_tokens = [
                token for token in tokens 
                if token and len(token) > 50  # FCM 토큰은 보통 152자
            ]
            
            if not valid_tokens:
                logger.warning("⚠️ No valid FCM tokens")
                return False
            
            # 메시지 구성
            message = messaging.MulticastMessage(
                tokens=valid_tokens,
                notification=messaging.Notification(
                    title=title,
                    body=body
                ),
                data=data or {},
                android=messaging.AndroidConfig(
                    notification=messaging.AndroidNotification(
                        channel_id="insightdeal_alerts",
                        priority=messaging.Priority.HIGH,
                        default_sound=True,
                        default_vibrate_pattern=True,
                        color="#FF6B35"  # InsightDeal 브랜드 컬러
                    )
                ),
                apns=messaging.APNSConfig(
                    payload=messaging.APNSPayload(
                        aps=messaging.Aps(
                            sound="default",
                            badge=1
                        )
                    )
                )
            )
            
            # 메시지 전송
            response = messaging.send_multicast(message)
            
            success_count = response.success_count
            failure_count = response.failure_count
            
            logger.info(
                f"✅ FCM notification sent: {success_count}/{len(valid_tokens)} successful"
            )
            
            # 실패한 토큰들 로깅
            if failure_count > 0:
                failed_responses = [
                    (i, resp.exception) 
                    for i, resp in enumerate(response.responses) 
                    if not resp.success
                ]
                logger.warning(f"⚠️ FCM failures: {len(failed_responses)}")
                
                for i, exception in failed_responses[:3]:  # 처음 3개만 로깅
                    logger.warning(f"Token {i}: {exception}")
            
            return success_count > 0
            
        except Exception as e:
            logger.error(f"❌ FCM notification send failed: {e}")
            return False
    
    def send_deal_alert(self, deal_data: Dict, user_tokens: List[str]) -> bool:
        """🔥 새 핫딜 발견 알림"""
        title = "🔥 새 핫딜 발견!"
        
        # 제목 길이 제한
        deal_title = deal_data.get('title', '')[:40]
        if len(deal_data.get('title', '')) > 40:
            deal_title += "..."
            
        price_text = f"{deal_data.get('price', '가격미정')}"
        body = f"{deal_title} - {price_text}"
        
        # 커뮤니티별 이모지
        community_emojis = {
            "뽐뿌": "🎯", "루리웹": "🎮", "클리앙": "💻", 
            "퀘이사존": "⚡", "알리뽐뿌": "🛒",
            "펨코": "⚽", "빠삭국내": "📱", "빠삭해외": "✈️"
        }
        community = deal_data.get('community', '')
        emoji = community_emojis.get(community, '🔥')
        body = f"{emoji} {body}"
        
        data = {
            "type": "new_deal",
            "deal_id": str(deal_data.get('id', '')),
            "community": community,
            "click_action": "DEAL_DETAIL"
        }
        
        return self.send_notification(user_tokens, title, body, data)
    
    def send_price_alert(self, product_data: Dict, user_tokens: List[str]) -> bool:
        """💰 목표 가격 도달 알림"""
        current_price = product_data.get('current_price', 0)
        target_price = product_data.get('target_price', 0)
        
        title = "💰 목표 가격 도달!"
        
        product_name = product_data.get('name', '')[:30]
        if len(product_data.get('name', '')) > 30:
            product_name += "..."
            
        body = f"{product_name} - {current_price:,}원 (목표: {target_price:,}원)"
        
        # 할인율 계산
        original_price = product_data.get('original_price', 0)
        if original_price > current_price:
            discount_rate = int(((original_price - current_price) / original_price) * 100)
            title = f"💰 목표 가격 도달! ({discount_rate}% 할인)"
        
        data = {
            "type": "price_alert",
            "product_id": str(product_data.get('id', '')),
            "current_price": str(current_price),
            "target_price": str(target_price),
            "click_action": "PRODUCT_DETAIL"
        }
        
        return self.send_notification(user_tokens, title, body, data)
    
    def send_price_drop_alert(self, product_data: Dict, user_tokens: List[str]) -> bool:
        """📉 가격 하락 알림 (5% 이상)"""
        current_price = product_data.get('current_price', 0)
        previous_price = product_data.get('previous_price', 0)
        
        if previous_price <= 0:
            return False
            
        drop_rate = int(((previous_price - current_price) / previous_price) * 100)
        
        if drop_rate < 5:  # 5% 미만 하락은 알림 안함
            return False
            
        title = f"📉 가격 {drop_rate}% 하락!"
        
        product_name = product_data.get('name', '')[:30]
        if len(product_data.get('name', '')) > 30:
            product_name += "..."
            
        body = f"{product_name} - {current_price:,}원 (이전: {previous_price:,}원)"
        
        data = {
            "type": "price_drop",
            "product_id": str(product_data.get('id', '')),
            "current_price": str(current_price),
            "previous_price": str(previous_price),
            "drop_rate": str(drop_rate),
            "click_action": "PRODUCT_DETAIL"
        }
        
        return self.send_notification(user_tokens, title, body, data)

# 싱글톤 인스턴스
notification_service = FCMNotificationService()