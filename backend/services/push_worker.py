import logging
from backend.database.session import SessionLocal
from backend.database.models import PushKeyword, DeviceToken, Deal, NotificationAlert
from backend.models.wishlist_models import Wishlist
from firebase_admin import messaging
from firebase_admin.exceptions import FirebaseError
import datetime

logger = logging.getLogger(__name__)

# Rate Limiting을 위한 전역 In-Memory Cache (실제 프로덕션은 Redis 활용 권장)
_push_rate_limit = {} # {device_id: {"count": 0, "date": "2023-10-01"}}

def is_in_dnd_range(now_time: datetime.time, start_str: str, end_str: str) -> bool:
    """현재 시각이 방해 금지(DND) 설정 시간대에 속하는지 여부를 판단합니다.
    자정을 관통하는 시간대(예: 22:00 ~ 08:00)도 완벽하게 지원합니다.
    """
    try:
        start_hour, start_min = map(int, start_str.split(':'))
        end_hour, end_min = map(int, end_str.split(':'))
        
        start_time = datetime.time(start_hour, start_min)
        end_time = datetime.time(end_hour, end_min)
        
        if start_time <= end_time:
            return start_time <= now_time <= end_time
        else:
            return now_time >= start_time or now_time <= end_time
    except Exception as e:
        logger.error(f"DND 시간대 파싱 및 대조 오류: {e} (입력값: start={start_str}, end={end_str})")
        return False

def _build_affiliate_url(deal: Deal) -> str:
    import os
    final_url = (deal.ecommerce_link if deal.ecommerce_link else deal.post_link) or ""
    coupang_prefix = os.getenv("COUPANG_AFFILIATE_PREFIX", "").strip()
    ali_prefix = os.getenv("ALI_AFFILIATE_PREFIX", "").strip()
    if coupang_prefix and deal.shop_name and "쿠팡" in deal.shop_name:
        if "coupa.ng" not in final_url:
            final_url = f"{coupang_prefix}{final_url}"
    elif ali_prefix and deal.shop_name and ("알리" in deal.shop_name or "Ali" in deal.shop_name):
        if "s.click.aliexpress.com" not in final_url:
            final_url = f"{ali_prefix}{final_url}"
    return final_url


def _match_smart_keyword(raw_keyword: str, deal: Deal, deal_price_int: int) -> tuple[bool, str]:
    """키워드 조건 문법 파싱 및 매칭
    지원 문법:
      - 기본 단어: 공백 구분 AND 매칭 (예: '맥북 에어')
      - 제외 단어: '-단어' (예: '아이폰 -케이스 -필름')
      - 상한 금액: ':금액' (예: '제로콜라 :15000')
    """
    if not raw_keyword or not raw_keyword.strip():
        return False, ""

    tokens = raw_keyword.strip().split()
    positive_terms: list[str] = []
    exclude_terms: list[str] = []
    max_price: int | None = None

    for token in tokens:
        if token.startswith(":") and len(token) > 1:
            digits = "".join(ch for ch in token[1:] if ch.isdigit())
            if digits:
                try:
                    parsed_price = int(digits)
                    if parsed_price > 0:
                        max_price = parsed_price
                except ValueError:
                    pass
        elif token.startswith("-") and len(token) > 1:
            exclude_terms.append(token[1:].lower())
        else:
            positive_terms.append(token.lower())

    if not positive_terms:
        return False, raw_keyword

    searchable = " ".join([
        (deal.title or ""),
        (deal.category or ""),
        (deal.base_product_name or ""),
    ]).lower()

    if not all(term in searchable for term in positive_terms):
        return False, raw_keyword

    if any(ex_term in searchable for ex_term in exclude_terms):
        return False, raw_keyword

    if max_price is not None:
        if deal_price_int <= 0 or deal_price_int > max_price:
            return False, raw_keyword

    display_name = " ".join(positive_terms)
    if max_price is not None:
        display_name = f"{display_name} ({max_price:,}원 이하)"
    return True, display_name


def _record_notification_alert(db, user_id: str, title: str, keyword: str, deal_url: str):
    if not user_id or not title:
        return
    try:
        exists = db.query(NotificationAlert).filter(
            NotificationAlert.user_id == user_id,
            NotificationAlert.title == title
        ).first()
        if not exists:
            db.add(
                NotificationAlert(
                    user_id=user_id,
                    title=title,
                    keyword=keyword,
                    deal_url=deal_url or ""
                )
            )
            db.commit()
    except Exception as e:
        db.rollback()
        logger.error(f"NotificationAlert 저장 실패: {e}")


def match_existing_deals_for_keyword(db, user_id: str, raw_keyword: str) -> int:
    """키워드 등록 즉시 최근 활성 핫딜 중 조건에 부합하는 실제 딜을 찾아 인앱 알림함에 기록"""
    if not user_id or not raw_keyword or not raw_keyword.strip():
        return 0
    try:
        recent_deals = (
            db.query(Deal)
            .filter(Deal.is_closed == False)
            .order_by(Deal.indexed_at.desc())
            .limit(80)
            .all()
        )
        matched_count = 0
        for d in recent_deals:
            try:
                p_int = int(float(d.price)) if d.price else 0
            except Exception:
                p_int = 0
            ok, disp_kw = _match_smart_keyword(raw_keyword, d, p_int)
            if ok:
                alert_title = f"{d.title} · {p_int:,}원" if p_int > 0 else d.title
                _record_notification_alert(
                    db=db,
                    user_id=user_id,
                    title=alert_title,
                    keyword=disp_kw,
                    deal_url=_build_affiliate_url(d),
                )
                matched_count += 1
                if matched_count >= 2:
                    break
        return matched_count
    except Exception as e:
        logger.error(f"즉시 키워드 매칭 스캔 오류: {e}")
        return 0


def background_trigger_keyword_alarms(deal_id: int):
    """등록된 사용자 키워드 및 목표가 알림(Wishlist)과 현재 핫딜을 매칭해 푸시 알람 발송"""
    db = SessionLocal()
    try:
        deal = db.query(Deal).filter(Deal.id == deal_id).first()
        if not deal:
            return
            
        kst = datetime.timezone(datetime.timedelta(hours=9))
        now = datetime.datetime.now(kst)
        now_hour = now.hour
        
        # 정보통신망법 야간 푸시 발송 제한 (21:00 ~ 08:00)
        is_night_time = now_hour >= 21 or now_hour < 8
        current_date_str = now.strftime('%Y-%m-%d')
        final_url = _build_affiliate_url(deal)
        
        try:
            deal_price_int = int(float(deal.price)) if deal.price else 0
        except Exception:
            deal_price_int = 0

        messages = []
        
        # 1. 키워드 알림 매칭 (:상한가격 및 -제외단어 스마트 조건 필터 지원)
        keywords_db = db.query(PushKeyword).filter(PushKeyword.is_active == True).all()
        for kw in keywords_db:
            is_match, display_kw = _match_smart_keyword(kw.keyword, deal, deal_price_int)
            if is_match:
                device = db.query(DeviceToken).filter(DeviceToken.id == kw.device_token_id).first()
                if device and device.is_active:
                    alert_body = f"{deal.title} · {deal_price_int:,}원" if deal_price_int > 0 else deal.title
                    _record_notification_alert(
                        db=db,
                        user_id=device.device_uuid,
                        title=alert_body,
                        keyword=display_kw,
                        deal_url=final_url,
                    )
                    if is_night_time and not device.night_push_consent:
                        continue
                        
                    if device.dnd_enabled:
                        now_time = now.time()
                        dnd_blocked = False
                        if device.dnd_settings_json:
                            try:
                                import json
                                dnd_map = json.loads(device.dnd_settings_json)
                                weekday_keys = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
                                today_key = weekday_keys[now.weekday()]
                                if today_key in dnd_map:
                                    day_config = dnd_map[today_key]
                                    if day_config.get("enabled", False):
                                        start_str = day_config.get("start", "21:00")
                                        end_str = day_config.get("end", "08:00")
                                        if is_in_dnd_range(now_time, start_str, end_str):
                                            dnd_blocked = True
                            except Exception as json_err:
                                logger.error(f"요일별 DND JSON 파싱 실패: {json_err}")
                        
                        if not dnd_blocked and not device.dnd_settings_json:
                            if is_in_dnd_range(now_time, device.dnd_start_time, device.dnd_end_time):
                                dnd_blocked = True
                        
                        if dnd_blocked:
                            continue

                    global _push_rate_limit
                    rate_info = _push_rate_limit.get(device.id, {"count": 0, "date": current_date_str})
                    if rate_info["date"] != current_date_str:
                        rate_info = {"count": 0, "date": current_date_str}
                        
                    if rate_info["count"] >= 3:
                        continue

                    if device.fcm_token:
                        rate_info["count"] += 1
                        _push_rate_limit[device.id] = rate_info
                        try:
                            msg = messaging.Message(
                                notification=messaging.Notification(
                                    title=f"'{display_kw}' 새 핫딜이 올라왔어요",
                                    body=f"{deal.title} · {deal_price_int:,}원" if deal_price_int > 0 else deal.title,
                                    image=deal.image_url if deal.image_url else None
                                ),
                                data={
                                    "navigate_to": "hotdeal",
                                    "deal_id": str(deal.id),
                                    "url": final_url
                                },
                                token=device.fcm_token,
                            )
                            messages.append(msg)
                        except Exception as parse_err:
                            logger.error(f"FCM 메시지 생성 실패: {parse_err}")

        # 2. 목표가 알림 (Wishlist) 매칭
        if deal_price_int > 0:
            wishlists_db = db.query(Wishlist).filter(
                Wishlist.is_active == True,
                Wishlist.alert_enabled == True,
                Wishlist.target_price >= deal_price_int
            ).all()
            for wl in wishlists_db:
                wl_key = (wl.keyword or "").lower().strip()
                if not wl_key:
                    continue
                is_wl_match = (
                    wl_key in deal.title.lower() or
                    (deal.base_product_name and wl_key in deal.base_product_name.lower()) or
                    (deal.ecommerce_link and wl_key == deal.ecommerce_link.lower()) or
                    (deal.post_link and wl_key == deal.post_link.lower())
                )
                if is_wl_match:
                    _record_notification_alert(
                        db=db,
                        user_id=wl.user_id,
                        title=f"[목표가 {wl.target_price:,}원 이하] {deal.title} · 현재 {deal_price_int:,}원",
                        keyword=wl.keyword,
                        deal_url=final_url,
                    )
                    device = db.query(DeviceToken).filter(
                        DeviceToken.device_uuid == wl.user_id,
                        DeviceToken.is_active == True
                    ).first()
                    if device and device.fcm_token:
                        if is_night_time and not device.night_push_consent:
                            continue
                        try:
                            msg = messaging.Message(
                                notification=messaging.Notification(
                                    title=f"설정한 목표가({wl.target_price:,}원) 이하로 내려갔어요",
                                    body=f"{deal.title} · 현재 {deal_price_int:,}원",
                                    image=deal.image_url if deal.image_url else None
                                ),
                                data={
                                    "navigate_to": "hotdeal",
                                    "deal_id": str(deal.id),
                                    "url": final_url
                                },
                                token=device.fcm_token,
                            )
                            messages.append(msg)
                        except Exception as wl_err:
                            logger.error(f"목표가 FCM 메시지 생성 실패: {wl_err}")

        # 500개씩 청크로 나누어 일괄 전송
        if messages:
            chunk_size = 500
            total_sent = 0
            for i in range(0, len(messages), chunk_size):
                chunk = messages[i:i + chunk_size]
                try:
                    response = messaging.send_each(chunk)
                    total_sent += response.success_count
                    logger.info(f"FCM Batch 발송: {response.success_count} 성공, {response.failure_count} 실패")
                except FirebaseError as fcm_err:
                    logger.error(f"FCM Batch 발송 오류: {fcm_err}")
                    
            logger.info(f"총 {total_sent}개의 푸시알림 발송 완료")
                            
    except Exception as e:
        logger.error(f"푸시알림 매치 에러: {e}")
    finally:
        db.close()
