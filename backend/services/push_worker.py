import logging
from backend.database.session import SessionLocal
from backend.database.models import PushKeyword, DeviceToken, Deal
from firebase_admin import messaging
from firebase_admin.exceptions import FirebaseError
import datetime

logger = logging.getLogger(__name__)

# Rate Limiting을 위한 전역 In-Memory Cache (실제 프로덕션은 Redis 활용 권장)
_push_rate_limit = {} # {device_id: {"count": 0, "date": "2023-10-01"}}

def background_trigger_keyword_alarms(deal_id: int):
    """[Epic 3] 등록된 사용자 키워드와 현재 핫딜을 매칭해 푸시 알람 발송 (Log & FCM)
    고성능 Batch Processing (send_each) 적용 및 비동기 워커 패턴"""
    db = SessionLocal()
    try:
        deal = db.query(Deal).filter(Deal.id == deal_id).first()
        if not deal:
            return
            
        # [Epic 1] 정보통신망법 야간 푸시 발송 제한 (21:00 ~ 08:00)
        now = datetime.datetime.now()
        now_hour = now.hour
        is_night_time = now_hour >= 21 or now_hour < 8
        
        # 활성화된 키워드 중 상품 제목이나 카테고리, 혹은 AI 정규화 상품명에 포함된 항목 검색
        keywords_db = db.query(PushKeyword).filter(PushKeyword.is_active == True).all()
        
        current_date_str = now.strftime('%Y-%m-%d')
        
        messages = []
        
        for kw in keywords_db:
            keyword_lower = kw.keyword.lower()
            is_match = (
                keyword_lower in deal.title.lower() or 
                keyword_lower in deal.category.lower() or
                (deal.base_product_name and keyword_lower in deal.base_product_name.lower())
            )
            if is_match:
                device = db.query(DeviceToken).filter(DeviceToken.id == kw.device_token_id).first()
                if device and device.is_active:
                    if is_night_time and not device.night_push_consent:
                        logger.info(f"🌙 [야간 푸시 차단] UID: {device.device_uuid[:8]}... | 키워드: '{kw.keyword}' | 사유: 야간 발송 동의 안함")
                        continue
                        
                    # [Rate Limiting] 하루 최대 3회 제한 로직
                    global _push_rate_limit
                    rate_info = _push_rate_limit.get(device.id, {"count": 0, "date": current_date_str})
                    if rate_info["date"] != current_date_str:
                        rate_info = {"count": 0, "date": current_date_str}
                        
                    if rate_info["count"] >= 3:
                        logger.info(f"🚫 [푸시 초과 차단] UID: {device.device_uuid[:8]}... 일일 최대 발송 횟수(3회) 도달")
                        continue

                    # [Affiliate] 쿠팡/알리 등 특정 쇼핑몰 제휴 링크 변환 우회
                    final_url = deal.ecommerce_link if deal.ecommerce_link else deal.post_link
                    if deal.shop_name and "쿠팡" in deal.shop_name:
                        final_url = f"https://coupa.ng/tracking?url={final_url}"
                        logger.info(f"💸 [수익화] 쿠팡 파트너스 링크 변환 적용: {final_url}")
                    elif deal.shop_name and ("알리" in deal.shop_name or "Ali" in deal.shop_name):
                        final_url = f"https://s.click.aliexpress.com/e/_tracking?url={final_url}"
                        logger.info(f"💸 [수익화] 알리익스프레스 제휴 링크 변환 적용: {final_url}")
                        
                    logger.info(f"🔔 [푸시알림 배치준비] UID: {device.device_uuid[:8]}... | 키워드: '{kw.keyword}' | 상품: {deal.title}")
                    
                    if device.fcm_token:
                        # 횟수 차감을 먼저 진행 (FCM 실패해도 악의적인 연속 요청을 막기 위함)
                        rate_info["count"] += 1
                        _push_rate_limit[device.id] = rate_info
                        
                        try:
                            msg = messaging.Message(
                                notification=messaging.Notification(
                                    title=f"🔔 '{kw.keyword}' 핫딜 임박!",
                                    body=f"{deal.title} - {int(float(deal.price)):,}원" if deal.price != "0" else deal.title,
                                    image=deal.image_url if deal.image_url else None
                                ),
                                data={
                                    "navigate_to": "hotdeal",
                                    "deal_id": str(deal.id),
                                    "url": final_url # 변환된 제휴 링크 탑재
                                },
                                token=device.fcm_token,
                            )
                            messages.append(msg)
                        except Exception as parse_err:
                            logger.error(f"❌ FCM 메시지 생성 실패: {parse_err}")

        # 500개씩 청크로 나누어 고성능 일괄 전송
        if messages:
            chunk_size = 500
            total_sent = 0
            for i in range(0, len(messages), chunk_size):
                chunk = messages[i:i + chunk_size]
                try:
                    response = messaging.send_each(chunk)
                    total_sent += response.success_count
                    logger.info(f"🚀 FCM Batch 발송: {response.success_count} 성공, {response.failure_count} 실패")
                    
                    # 실패한 토큰에 대한 에러 처리 로직
                    if response.failure_count > 0:
                        for idx, resp in enumerate(response.responses):
                            if not resp.success:
                                logger.error(f"❌ FCM 토큰 발송 실패: {resp.exception}")
                                
                except FirebaseError as fcm_err:
                    logger.error(f"❌ FCM Batch 발송 치명적 오류: {fcm_err}")
                    
            logger.info(f"✅ 총 {total_sent}개의 푸시알림 발송 완료")
                            
    except Exception as e:
        logger.error(f"❌ 푸시알림 매치 에러: {e}")
    finally:
        db.close()
