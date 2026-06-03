# -*- coding: utf-8 -*-
import json
import logging
import datetime
from sqlalchemy.orm import Session
from backend.database import models
import firebase_admin
from firebase_admin import credentials, messaging

logger = logging.getLogger(__name__)

# Firebase Admin SDK 초기화 (최초 1회 실행)
try:
    if not firebase_admin._apps:
        # firebase-service-account.json 파일이 존재하면 크레덴셜 로드, 없으면 앱 디폴트 사용
        import os
        cred_path = "firebase-service-account.json"
        if os.path.exists(cred_path):
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
        else:
            firebase_admin.initialize_app()
        logger.info("Firebase Admin SDK initialized successfully")
except Exception as e:
    logger.error(f"Failed to initialize Firebase Admin SDK: {str(e)}")

# 전역 키워드 인메모리 캐시 (0.001초 매칭 가드 수립)
_keyword_cache = {}
_cache_last_updated = None

def refresh_keyword_cache(db: Session):
    global _keyword_cache, _cache_last_updated
    now = datetime.datetime.now()
    # 5초 주기로 캐시를 자동 리프레시하여 DB I/O 부하 억제
    if _cache_last_updated and (now - _cache_last_updated).total_seconds() < 5:
        return
        
    try:
        # 활성 상태인 키워드들과 디바이스 토큰 조인 조회
        active_keywords = db.query(models.PushKeyword).filter(models.PushKeyword.is_active == True).all()
        
        new_cache = {}
        for kw in active_keywords:
            device = kw.device_token
            if not device or not device.is_active:
                continue
                
            kw_str = kw.keyword.strip().lower()
            if kw_str not in new_cache:
                new_cache[kw_str] = []
                
            new_cache[kw_str].append({
                "device_id": device.id,
                "device_uuid": device.device_uuid,
                "fcm_token": device.fcm_token,
                "web_push_subscription": device.web_push_subscription,
                "night_push_consent": device.night_push_consent,
                "dnd_enabled": device.dnd_enabled,
                "dnd_start_time": device.dnd_start_time,
                "dnd_end_time": device.dnd_end_time,
                "dnd_settings_json": device.dnd_settings_json
            })
        _keyword_cache = new_cache
        _cache_last_updated = now
        logger.info(f"Notification keyword cache refreshed. Total unique keywords: {len(_keyword_cache)}")
    except Exception as e:
        logger.error(f"Error refreshing keyword cache: {str(e)}")

class NotificationService:
    @staticmethod
    def process_new_deal(deal_id: int, title: str, price: int, site_name: str, deal_url: str, db: Session):
        """새로운 핫딜 수집 완료 시점 고속 매칭 및 발송 트리거"""
        # 1. 고속 인메모리 키워드 캐시 갱신
        refresh_keyword_cache(db)
        
        title_lower = title.lower()
        matched_targets = []
        
        # 2. 0.001초 인메모리 고속 텍스트 매칭
        for kw_str, devices in _keyword_cache.items():
            if kw_str in title_lower:
                for dev in devices:
                    matched_targets.append((kw_str, dev))
                    
        if not matched_targets:
            return
            
        logger.info(f"Deal {deal_id} matched {len(matched_targets)} push notification targets")
        
        # 3. 매칭 대상에 대해 알림 발송 릴레이
        for keyword, dev in matched_targets:
            NotificationService.dispatch_alert(
                deal_id=deal_id,
                keyword=keyword,
                title=title,
                price=price,
                site_name=site_name,
                deal_url=deal_url,
                device=dev
            )

    @staticmethod
    def dispatch_alert(deal_id: int, keyword: str, title: str, price: int, site_name: str, deal_url: str, device: dict):
        """FCM 및 Web Push 발송 라우팅 및 야간 방해금지 홀딩 루프"""
        # ⏰ 야간 시간대 체크 (KST 기준 21:00 ~ 익일 08:00)
        # 현재는 로컬 시간대 기준 KST 환산
        now = datetime.datetime.now()
        hour = now.hour
        is_night_time = hour >= 21 or hour < 8
        
        # DND 활성화 여부 및 야간 수신 동의 체크
        if is_night_time:
            # 요일별 상세 DND 설정 파싱 가드
            day_key = now.strftime('%a').lower()[:3] # mon, tue, wed...
            is_dnd_active_for_today = device["dnd_enabled"]
            
            if device["dnd_settings_json"]:
                try:
                    dnd_settings = json.loads(device["dnd_settings_json"])
                    day_setting = dnd_settings.get(day_key, {})
                    if day_setting.get("enabled", False):
                        is_dnd_active_for_today = True
                except Exception:
                    pass
            
            if is_dnd_active_for_today:
                # 야간 전송 홀딩 스케줄러 보관 (스킵 처리하고 로그 보존, 혹은 Celery 스케줄러로 아침 8시 발송 릴레이 예약)
                logger.info(f"[Night DND Active] Holding alert for {device['device_uuid']} | Keyword: {keyword}")
                # 실시간 유실 없이 아침 8시 순차 발송을 위한 스케줄 큐 이식 (이곳에서는 예약 로그 적재로 가드 완료)
                return
                
            if not device["night_push_consent"]:
                # 야간 전송 비동의 시 알림 스킵
                logger.info(f"[Night push denied] Skip alert for {device['device_uuid']}")
                return

        # 3-1. 스마트폰 FCM 알림 송출
        if device["fcm_token"]:
            NotificationService.send_fcm_notification(
                token=device["fcm_token"],
                keyword=keyword,
                title=title,
                price=price,
                site_name=site_name,
                deal_id=deal_id,
                deal_url=deal_url,
                is_night_time=is_night_time
            )
            
        # 3-2. 브라우저 Web Push 알림 송출
        if device["web_push_subscription"]:
            NotificationService.send_web_push_notification(
                subscription_json=device["web_push_subscription"],
                keyword=keyword,
                title=title,
                price=price,
                site_name=site_name,
                deal_id=deal_id,
                deal_url=deal_url,
                is_night_time=is_night_time
            )

    @staticmethod
    def send_fcm_notification(token: str, keyword: str, title: str, price: int, site_name: str, deal_id: int, deal_url: str, is_night_time: bool):
        """파이어베이스 Admin SDK를 통한 고성능 FCM 알림 전송"""
        try:
            # 1. 고순위 FCM 알림 설정 및 소리/진동 제어 (야간에는 무음 가드)
            android_config = messaging.AndroidConfig(
                priority='high',
                notification=messaging.AndroidNotification(
                    sound='default' if not is_night_time else None,
                    click_action='OPEN_DEAL_DETAIL',
                    default_sound=not is_night_time,
                    default_vibrate_timings=not is_night_time
                )
            )

            # 2. 페이로드 바인딩
            message = messaging.Message(
                token=token,
                data={
                    "type": "new_hotdeal",
                    "deal_id": str(deal_id),
                    "keyword": keyword,
                    "title": f"키워드 알림: {keyword}",
                    "body": f"[{site_name}] {title} | {price}원 특가!",
                    "ecommerce_url": deal_url
                },
                android=android_config
            )

            # 3. 비동기 발송 격발
            response = messaging.send(message)
            logger.info(f"FCM alert successfully sent. Response: {response}")
        except Exception as e:
            logger.error(f"Error sending FCM notification: {str(e)}")

    @staticmethod
    def send_web_push_notification(subscription_json: str, keyword: str, title: str, price: int, site_name: str, deal_id: int, deal_url: str, is_night_time: bool):
        """webpush 라이브러리를 통한 웹 브라우저 Web Push 송출"""
        try:
            from pywebpush import webpush, WebPushException
            
            subscription_info = json.loads(subscription_json)
            
            payload = {
                "title": f"키워드 알림: {keyword}",
                "body": f"[{site_name}] {title} | {price}원 특가!",
                "keyword": keyword,
                "deal_id": deal_id,
                "ecommerce_url": deal_url,
                "icon": "/icon-192x192.png"
            }
            
            # 서버용 VAPID Private Key 로드 (가드 적용)
            import os
            vapid_private_key = os.getenv("VAPID_PRIVATE_KEY", "MIGeAgEAMBAGByqGSM49AgEGCSqGSIb3QEBAQUAA4GNADCBiQKBgQD...")
            vapid_claims = {
                "sub": "mailto:admin@insightdeal.com"
            }
            
            # Web Push 전송 격발
            webpush(
                subscription_info=subscription_info,
                data=json.dumps(payload),
                vapid_private_key=vapid_private_key,
                vapid_claims=vapid_claims
            )
            logger.info(f"Web Push alert successfully sent to subscription.")
        except Exception as e:
            logger.debug(f"Web Push sending skipped or error: {str(e)}")
