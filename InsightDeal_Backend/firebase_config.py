import os
import firebase_admin
from firebase_admin import credentials, messaging
import logging
from datetime import datetime
from typing import List, Dict, Optional

logger = logging.getLogger(__name__)

class FCMManager:
    """Firebase Cloud Messaging 관리 클래스"""
    _instance = None
    _app = None
    
    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(FCMManager, cls).__new__(cls)
        return cls._instance
    
    def __init__(self):
        if FCMManager._app is None:
            self.initialize_firebase()
    
    def initialize_firebase(self):
        """Firebase Admin SDK 초기화"""
        try:
            # Firebase 서비스 계정 키 파일 경로
            service_account_path = os.getenv("FIREBASE_SERVICE_ACCOUNT_KEY", "firebase-service-account.json")
            
            if os.path.exists(service_account_path):
                cred = credentials.Certificate(service_account_path)
                FCMManager._app = firebase_admin.initialize_app(cred, name='insightdeal-fcm')
                logger.info("✅ Firebase Admin SDK initialized successfully")
            else:
                logger.warning("⚠️ Firebase service account file not found, FCM disabled")
                
        except ValueError as e:
            if "already exists" in str(e):
                logger.info("✅ Firebase Admin SDK already initialized")
            else:
                logger.error(f"❌ Firebase initialization failed: {e}")
        except Exception as e:
            logger.error(f"❌ Firebase initialization failed: {e}")
    
    def send_push_notification(self, tokens: List[str], title: str, body: str, data: Dict[str, str] = None) -> bool:
        """FCM 푸시 알림 전송"""
        try:
            if not tokens or not FCMManager._app:
                logger.warning("⚠️ No FCM tokens provided or Firebase not initialized")
                return False
            
            # 유효하지 않은 토큰 제거
            valid_tokens = [token for token in tokens if token and len(token) > 50]
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
                        channel_id="hotdeal_channel",
                        priority=messaging.Priority.HIGH,
                        default_sound=True,
                        default_vibrate_pattern=True,
                        color="#FF6B35"  # InsightDeal 브랜드 컬러
                    )
                )
            )
            
            # 메시지 전송
            response = messaging.send_multicast(message)
            logger.info(f"✅ FCM sent: {response.success_count}/{len(valid_tokens)} devices")
            
            # 실패한 토큰 로깅
            if response.failure_count > 0:
                failed_indices = [i for i, resp in enumerate(response.responses) if not resp.success]
                logger.warning(f"⚠️ Failed to send to {len(failed_indices)} devices")
            
            return response.success_count > 0
            
        except Exception as e:
            logger.error(f"❌ FCM send failed: {e}")
            return False
    
    def send_new_deal_notification(self, deal_data: Dict) -> bool:
        """🔥 새 핫딜 발견 시 알림"""
        title = "🔥 새 핫딜 발견!"
        body = f"{deal_data['title'][:50]} - {deal_data['price']}"
        
        # 커뮤니티별 이모지 추가
        community_emojis = {
            "뽐뿌": "🎯", "루리웹": "🎮", "클리앙": "💻", 
            "퀘이사존": "⚡", "펨코": "🔥", "빠삭": "💎"
        }
        community_emoji = community_emojis.get(deal_data.get('community', ''), '🔥')
        body = f"{community_emoji} {body}"
        
        data = {
            "type": "new_deal",
            "deal_id": str(deal_data['id']),
            "community": deal_data.get('community', ''),
            "click_action": "DEAL_DETAIL"
        }
        
        # 모든 활성 사용자에게 전송
        tokens = self.get_all_active_tokens()
        return self.send_push_notification(tokens, title, body, data)
    
    def send_price_alert_notification(self, product_data: Dict, user_tokens: List[str]) -> bool:
        """💰 목표 가격 도달 시 알림"""
        current = product_data.get('current_price', 0)
        target = product_data.get('target_price', 0)
        
        # 할인율 계산
        if product_data.get('original_price', 0) > 0:
            discount = int(((product_data['original_price'] - current) / product_data['original_price']) * 100)
            title = f"💰 목표 가격 도달! ({discount}% 할인)"
        else:
            title = "💰 목표 가격 도달!"
            
        body = f"{product_data['name'][:40]} - {current:,}원 (목표: {target:,}원)"
        
        data = {
            "type": "price_alert",
            "product_id": str(product_data['id']),
            "current_price": str(current),
            "target_price": str(target),
            "click_action": "PRODUCT_DETAIL"
        }
        
        return self.send_push_notification(user_tokens, title, body, data)
    
    def send_lowest_price_notification(self, product_data: Dict, user_tokens: List[str]) -> bool:
        """📉 역대 최저가 달성 시 알림"""
        title = "📉 역대 최저가 갱신!"
        body = f"{product_data['name'][:40]} - {product_data['current_price']:,}원 (이전 최저: {product_data['previous_lowest']:,}원)"
        
        data = {
            "type": "lowest_price",
            "product_id": str(product_data['id']),
            "current_price": str(product_data['current_price']),
            "previous_lowest": str(product_data['previous_lowest']),
            "click_action": "PRODUCT_DETAIL"
        }
        
        return self.send_push_notification(user_tokens, title, body, data)
    
    def get_all_active_tokens(self) -> List[str]:
        """활성 FCM 토큰 조회 (DB에서) - 추후 구현"""
        # 실제로는 DB에서 조회해야 함
        # 임시로 빈 리스트 반환
        return []

# 싱글톤 인스턴스
fcm_manager = FCMManager()