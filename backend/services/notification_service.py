# -*- coding: utf-8 -*-
import datetime
import json
import logging
import os
from typing import Optional

import firebase_admin
from firebase_admin import credentials, messaging
from sqlalchemy.orm import Session

from backend.database import models
from backend.services.push_worker import should_suppress_push

logger = logging.getLogger(__name__)

# Firebase Admin SDK 초기화 (최초 1회 실행)
try:
    if not firebase_admin._apps:
        cred_path = os.getenv("FIREBASE_SERVICE_ACCOUNT_PATH", "").strip() or "firebase-service-account.json"
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
    kst = datetime.timezone(datetime.timedelta(hours=9))
    now = datetime.datetime.now(kst)
    # 5초 주기로 캐시를 자동 리프레시하여 DB I/O 부하 억제
    if _cache_last_updated and (now - _cache_last_updated).total_seconds() < 5:
        return

    try:
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
                "dnd_settings_json": device.dnd_settings_json,
            })
        _keyword_cache = new_cache
        _cache_last_updated = now
        logger.info(f"Notification keyword cache refreshed. Total unique keywords: {len(_keyword_cache)}")
    except Exception as e:
        logger.error(f"Error refreshing keyword cache: {str(e)}")


_PLACEHOLDER_VAPID_KEYS = {
    "your_vapid_private_key",
    "placeholder",
    "change_me",
}


class NotificationService:
    @staticmethod
    def _matches_keyword_rule(rule_str: str, title_lower: str, price: int) -> bool:
        """
        고급 키워드 문법 지원:
        - 기본 포함 단어: 공백 구분 AND 매칭 (예: '갤럭시 버즈')
        - 제외 단어: '-단어' 또는 '!단어' (예: '갤럭시 -케이스 -필름', '에어팟 !케이스')
        - 목표가 상한 조건: ':가격' 또는 '<=가격' (예: '제로콜라 :15000' -> 15,000원 이하일 때만 매칭)
        """
        tokens = [t.strip() for t in rule_str.split() if t.strip()]
        if not tokens:
            return False

        include_words = []
        exclude_words = []
        max_price = None

        for tok in tokens:
            if (tok.startswith("-") or tok.startswith("!")) and len(tok) > 1:
                exclude_words.append(tok[1:].lower())
            elif tok.startswith(":") and tok[1:].replace(",", "").isdigit():
                max_price = int(tok[1:].replace(",", ""))
            elif tok.startswith("<=") and tok[2:].replace(",", "").isdigit():
                max_price = int(tok[2:].replace(",", ""))
            else:
                include_words.append(tok.lower())

        if not include_words:
            return False

        if not all(inc in title_lower for inc in include_words):
            return False

        if any(exc in title_lower for exc in exclude_words):
            return False

        if max_price is not None and max_price > 0:
            if price <= 0 or price > max_price:
                return False

        return True

    @staticmethod
    def process_new_deal(deal_id: int, title: str, price: int, site_name: str, deal_url: str, db: Session):
        """새로운 핫딜 수집 완료 시점 고속 매칭 및 발송 트리거"""
        refresh_keyword_cache(db)

        title_lower = title.lower()
        matched_targets = []

        for kw_str, devices in _keyword_cache.items():
            if NotificationService._matches_keyword_rule(kw_str, title_lower, price):
                for dev in devices:
                    matched_targets.append((kw_str, dev))

        if not matched_targets:
            return

        logger.info(f"Deal {deal_id} matched {len(matched_targets)} push notification targets")

        for keyword, dev in matched_targets:
            try:
                alert_record = models.NotificationAlert(
                    user_id=dev.get("device_uuid") or dev.get("fcm_token") or "default",
                    title=f"[{site_name}] {title} ({price:,}원)" if price > 0 else f"[{site_name}] {title}",
                    keyword=keyword,
                    deal_url=deal_url,
                )
                db.add(alert_record)
                db.commit()
            except Exception as db_err:
                db.rollback()
                logger.warning(f"Failed to save NotificationAlert history: {db_err}")

            NotificationService.dispatch_alert(
                deal_id=deal_id,
                keyword=keyword,
                title=title,
                price=price,
                site_name=site_name,
                deal_url=deal_url,
                device=dev,
                db=db,
            )

    @staticmethod
    def dispatch_alert(
        deal_id: int,
        keyword: str,
        title: str,
        price: int,
        site_name: str,
        deal_url: str,
        device: dict,
        db: Optional[Session] = None,
        now_kst: Optional[datetime.datetime] = None,
    ):
        """FCM 및 Web Push 발송 라우팅 및 공통 DND/야간 방해금지 차단"""
        kst = datetime.timezone(datetime.timedelta(hours=9))
        now = now_kst if now_kst is not None else datetime.datetime.now(kst)
        is_night_time = now.hour >= 21 or now.hour < 8

        suppressed, reason = should_suppress_push(device, now)
        if suppressed:
            logger.info(f"[{reason} drop_with_history] Holding alert for {device.get('device_uuid')} | Keyword: {keyword}")
            return

        if device.get("fcm_token"):
            NotificationService.send_fcm_notification(
                token=device["fcm_token"],
                keyword=keyword,
                title=title,
                price=price,
                site_name=site_name,
                deal_id=deal_id,
                deal_url=deal_url,
                is_night_time=is_night_time,
            )

        if device.get("web_push_subscription"):
            NotificationService.send_web_push_notification(
                subscription_json=device["web_push_subscription"],
                keyword=keyword,
                title=title,
                price=price,
                site_name=site_name,
                deal_id=deal_id,
                deal_url=deal_url,
                is_night_time=is_night_time,
                db=db,
            )

    @staticmethod
    def send_fcm_notification(
        token: str,
        keyword: str,
        title: str,
        price: int,
        site_name: str,
        deal_id: int,
        deal_url: str,
        is_night_time: bool,
    ):
        """파이어베이스 Admin SDK를 통한 고성능 FCM 알림 전송"""
        try:
            android_config = messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    sound="default" if not is_night_time else None,
                    click_action="OPEN_DEAL_DETAIL",
                    default_sound=not is_night_time,
                    default_vibrate_timings=not is_night_time,
                ),
            )

            message = messaging.Message(
                token=token,
                data={
                    "type": "new_hotdeal",
                    "deal_id": str(deal_id),
                    "keyword": keyword,
                    "title": f"키워드 알림: {keyword}",
                    "body": f"[{site_name}] {title} | {price}원 특가!",
                    "ecommerce_url": deal_url,
                },
                android=android_config,
            )

            response = messaging.send(message)
            logger.info(f"FCM alert successfully sent. Response: {response}")
        except Exception as e:
            logger.error(f"Error sending FCM notification: {str(e)}")

    @staticmethod
    def send_web_push_notification(
        subscription_json: str,
        keyword: str,
        title: str,
        price: int,
        site_name: str,
        deal_id: int,
        deal_url: str,
        is_night_time: bool = False,
        *,
        db: Optional[Session] = None,
        device: Optional[models.DeviceToken] = None,
        custom_title: Optional[str] = None,
        custom_body: Optional[str] = None,
    ) -> bool:
        """pywebpush 라이브러리를 통한 웹 브라우저 Web Push 송출 (VAPID_PRIVATE_KEY 미설정 시 즉시 차단)"""
        vapid_private_key = (os.getenv("VAPID_PRIVATE_KEY") or "").strip()
        lowered_key = vapid_private_key.lower()
        if (
            not vapid_private_key
            or lowered_key in _PLACEHOLDER_VAPID_KEYS
            or lowered_key.startswith(("your_", "placeholder", "change_me"))
            or "..." in vapid_private_key
        ):
            logger.warning("VAPID_PRIVATE_KEY가 설정되지 않아 Web Push 발송을 건너뜁니다.")
            return False

        try:
            from pywebpush import WebPushException, webpush
        except ImportError as imp_err:
            logger.warning(f"pywebpush 패키지를 로드할 수 없어 Web Push를 건너뜁니다: {imp_err}")
            return False

        try:
            subscription_info = json.loads(subscription_json) if isinstance(subscription_json, str) else subscription_json
            if not isinstance(subscription_info, dict) or not subscription_info.get("endpoint") or not subscription_info.get("keys"):
                logger.warning("유효하지 않은 Web Push 구독 정보 형식입니다.")
                return False

            payload = {
                "title": custom_title or f"키워드 알림: {keyword}",
                "body": custom_body or (f"[{site_name}] {title} | {price:,}원 특가!" if price > 0 else f"[{site_name}] {title}"),
                "keyword": keyword,
                "deal_id": deal_id,
                "ecommerce_url": deal_url,
                "post_url": deal_url,
                "icon": "/icon-192x192.png",
                "silent": bool(is_night_time),
            }

            claim_sub = (os.getenv("VAPID_CLAIM_EMAIL") or "mailto:admin@insightdeal.com").strip()
            if claim_sub and not claim_sub.startswith("mailto:"):
                claim_sub = f"mailto:{claim_sub}"
            vapid_claims = {"sub": claim_sub or "mailto:admin@insightdeal.com"}

            webpush(
                subscription_info=subscription_info,
                data=json.dumps(payload, ensure_ascii=False),
                vapid_private_key=vapid_private_key,
                vapid_claims=vapid_claims,
            )
            logger.info("Web Push alert successfully sent to subscription.")
            return True
        except WebPushException as wp_err:
            status_code = getattr(getattr(wp_err, "response", None), "status_code", None)
            logger.warning(f"Web Push 전송 실패 (status={status_code}): {wp_err}")
            if status_code in (404, 410) and device is not None and db is not None:
                try:
                    device.web_push_subscription = None
                    db.commit()
                    logger.info(f"만료된 Web Push 구독 정리 완료 (device_uuid={device.device_uuid}, status={status_code})")
                except Exception as cleanup_err:
                    db.rollback()
                    logger.error(f"만료된 Web Push 구독 정리 실패: {cleanup_err}")
            return False
        except Exception as e:
            logger.error(f"Web Push 전송 중 예외 발생: {e}")
            return False
