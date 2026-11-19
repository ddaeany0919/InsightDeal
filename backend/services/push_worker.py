import datetime
import json
import logging
import os
from typing import Any, Optional

from firebase_admin import messaging
from firebase_admin.exceptions import FirebaseError

from backend.database.models import Deal, DeviceToken, NotificationAlert, PushKeyword
from backend.database.session import SessionLocal
from backend.models.wishlist_models import Wishlist

logger = logging.getLogger(__name__)

# Rate Limiting을 위한 전역 In-Memory Cache (실제 프로덕션은 Redis 활용 권장)
_push_rate_limit: dict = {}  # {device_id: {"count": 0, "date": "2023-10-01"}}


def _get_dev_field(device: Any, name: str, default: Any = None) -> Any:
    if isinstance(device, dict):
        return device.get(name, default)
    return getattr(device, name, default)


def is_in_dnd_range(now_time: datetime.time, start_str: str, end_str: str) -> bool:
    """현재 시각이 방해 금지(DND) 설정 시간대에 속하는지 여부를 판단합니다.
    자정을 관통하는 시간대(예: 22:00 ~ 08:00)도 완벽하게 지원합니다.
    """
    try:
        start_hour, start_min = map(int, start_str.split(":"))
        end_hour, end_min = map(int, end_str.split(":"))

        start_time = datetime.time(start_hour, start_min)
        end_time = datetime.time(end_hour, end_min)

        if start_time <= end_time:
            return start_time <= now_time <= end_time
        else:
            return now_time >= start_time or now_time <= end_time
    except Exception as e:
        logger.error(f"DND 시간대 파싱 및 대조 오류: {e} (입력값: start={start_str}, end={end_str})")
        return False


def should_suppress_push(device: Any, now_kst: datetime.datetime) -> tuple[bool, str]:
    """
    공통 DND 및 야간 발송 제한 판별:
    1) dnd_enabled=True이면 주간/야간 불문하고 요일별 dnd_settings_json 또는 dnd_start_time~dnd_end_time 검사
    2) 요일 JSON에 오늘 요일 키가 없거나 파싱 실패 시 dnd_start_time~dnd_end_time 폴백 검사
    3) 야간(21:00~08:00 KST)에 night_push_consent=False이면 차단
    """
    is_night_time = now_kst.hour >= 21 or now_kst.hour < 8
    dnd_enabled = bool(_get_dev_field(device, "dnd_enabled", False))

    if dnd_enabled:
        now_time = now_kst.time()
        default_start = _get_dev_field(device, "dnd_start_time", None) or "21:00"
        default_end = _get_dev_field(device, "dnd_end_time", None) or "08:00"
        dnd_json_raw = _get_dev_field(device, "dnd_settings_json", None)

        handled_by_json = False
        if dnd_json_raw:
            try:
                dnd_map = json.loads(dnd_json_raw) if isinstance(dnd_json_raw, str) else dnd_json_raw
                weekday_keys = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
                today_key = weekday_keys[now_kst.weekday()]
                if isinstance(dnd_map, dict) and today_key in dnd_map and isinstance(dnd_map[today_key], dict):
                    handled_by_json = True
                    day_cfg = dnd_map[today_key]
                    if day_cfg.get("enabled", False):
                        start_str = day_cfg.get("start") or default_start
                        end_str = day_cfg.get("end") or default_end
                        if is_in_dnd_range(now_time, start_str, end_str):
                            return True, "dnd_active"
            except Exception as json_err:
                logger.error(f"요일별 DND JSON 파싱 실패: {json_err}")

        if not handled_by_json:
            if is_in_dnd_range(now_time, default_start, default_end):
                return True, "dnd_active"

    night_consent = bool(_get_dev_field(device, "night_push_consent", False))
    if is_night_time and not night_consent:
        return True, "night_consent_denied"

    return False, "ok"


def _build_affiliate_url(deal: Deal) -> str:
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
      - 제외 단어: '-단어' 또는 '!단어' (예: '아이폰 -케이스 !필름')
      - 상한 금액: ':금액' 또는 '<=금액' (예: '제로콜라 :15,000', '제로콜라 <=15000')
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
        elif token.startswith("<=") and len(token) > 2:
            digits = "".join(ch for ch in token[2:] if ch.isdigit())
            if digits:
                try:
                    parsed_price = int(digits)
                    if parsed_price > 0:
                        max_price = parsed_price
                except ValueError:
                    pass
        elif (token.startswith("-") or token.startswith("!")) and len(token) > 1:
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
            NotificationAlert.title == title,
        ).first()
        if not exists:
            db.add(
                NotificationAlert(
                    user_id=user_id,
                    title=title,
                    keyword=keyword,
                    deal_url=deal_url or "",
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


def background_trigger_keyword_alarms(
    deal_id: int,
    session_factory=None,
    now_kst: Optional[datetime.datetime] = None,
) -> dict:
    """등록된 사용자 키워드 및 목표가 알림(Wishlist)과 현재 핫딜을 매칭해 푸시 알람 발송"""
    factory = session_factory or SessionLocal
    db = factory()
    stats = {
        "matched_keywords": 0,
        "matched_wishlists": 0,
        "dropped_dnd": 0,
        "fcm_queued": 0,
        "fcm_sent": 0,
        "web_push_sent": 0,
    }
    try:
        deal = db.query(Deal).filter(Deal.id == deal_id).first()
        if not deal:
            return stats

        kst = datetime.timezone(datetime.timedelta(hours=9))
        now = now_kst if now_kst is not None else datetime.datetime.now(kst)
        is_night_time = now.hour >= 21 or now.hour < 8
        current_date_str = now.strftime("%Y-%m-%d")
        final_url = _build_affiliate_url(deal)

        try:
            deal_price_int = int(float(deal.price)) if deal.price else 0
        except Exception:
            deal_price_int = 0

        from backend.services.notification_service import NotificationService

        messages = []

        # 1. 키워드 알림 매칭 (:상한가격, <=상한가격, -제외단어, !제외단어 스마트 조건 필터 지원)
        keywords_db = db.query(PushKeyword).filter(PushKeyword.is_active == True).all()
        for kw in keywords_db:
            is_match, display_kw = _match_smart_keyword(kw.keyword, deal, deal_price_int)
            if not is_match:
                continue

            device = db.query(DeviceToken).filter(DeviceToken.id == kw.device_token_id).first()
            if not device or not device.is_active:
                continue

            stats["matched_keywords"] += 1
            alert_body = f"{deal.title} · {deal_price_int:,}원" if deal_price_int > 0 else deal.title
            # ① DND 판별 전 인앱 알림함 선적재 (drop_with_history)
            _record_notification_alert(
                db=db,
                user_id=device.device_uuid,
                title=alert_body,
                keyword=display_kw,
                deal_url=final_url,
            )

            # ② 공통 DND/야간 차단 판별
            suppressed, reason = should_suppress_push(device, now)
            if suppressed:
                stats["dropped_dnd"] += 1
                logger.info(f"[{reason} drop_with_history] device={device.device_uuid}, keyword={display_kw}")
                continue

            global _push_rate_limit
            rate_info = _push_rate_limit.get(device.id, {"count": 0, "date": current_date_str})
            if rate_info["date"] != current_date_str:
                rate_info = {"count": 0, "date": current_date_str}

            if rate_info["count"] >= 3:
                continue

            dispatched_channel = False
            if device.fcm_token:
                try:
                    msg = messaging.Message(
                        notification=messaging.Notification(
                            title=f"'{display_kw}' 새 핫딜이 올라왔어요",
                            body=alert_body,
                            image=deal.image_url if deal.image_url else None,
                        ),
                        data={
                            "navigate_to": "hotdeal",
                            "deal_id": str(deal.id),
                            "url": final_url,
                        },
                        token=device.fcm_token,
                    )
                    messages.append(msg)
                    stats["fcm_queued"] += 1
                    dispatched_channel = True
                except Exception as parse_err:
                    logger.error(f"FCM 메시지 생성 실패: {parse_err}")

            if device.web_push_subscription:
                sent_web = NotificationService.send_web_push_notification(
                    subscription_json=device.web_push_subscription,
                    keyword=display_kw,
                    title=deal.title or "",
                    price=deal_price_int,
                    site_name=deal.shop_name or "HotDeal",
                    deal_id=deal.id,
                    deal_url=final_url,
                    is_night_time=is_night_time,
                    db=db,
                    device=device,
                    custom_title=f"'{display_kw}' 새 핫딜이 올라왔어요",
                    custom_body=alert_body,
                )
                if sent_web:
                    stats["web_push_sent"] += 1
                dispatched_channel = True

            if dispatched_channel:
                rate_info["count"] += 1
                _push_rate_limit[device.id] = rate_info

        # 2. 목표가 알림 (Wishlist) 매칭
        if deal_price_int > 0:
            wishlists_db = []
            try:
                wishlists_db = db.query(Wishlist).filter(
                    Wishlist.is_active == True,
                    Wishlist.alert_enabled == True,
                    Wishlist.target_price >= deal_price_int,
                ).all()
            except Exception as wl_query_err:
                logger.debug(f"Wishlist 조회 건너뜀 (테이블 미존재 또는 조회 오류): {wl_query_err}")

            for wl in wishlists_db:
                wl_key = (wl.keyword or "").lower().strip()
                if not wl_key:
                    continue
                is_wl_match = (
                    wl_key in (deal.title or "").lower()
                    or (deal.base_product_name and wl_key in deal.base_product_name.lower())
                    or (deal.ecommerce_link and wl_key == deal.ecommerce_link.lower())
                    or (deal.post_link and wl_key == deal.post_link.lower())
                )
                if not is_wl_match:
                    continue

                stats["matched_wishlists"] += 1
                wl_alert_title = f"[목표가 {wl.target_price:,}원 이하] {deal.title} · 현재 {deal_price_int:,}원"
                # ① DND 판별 전 인앱 알림함 선적재 (drop_with_history)
                _record_notification_alert(
                    db=db,
                    user_id=wl.user_id,
                    title=wl_alert_title,
                    keyword=wl.keyword,
                    deal_url=final_url,
                )

                device = db.query(DeviceToken).filter(
                    DeviceToken.device_uuid == wl.user_id,
                    DeviceToken.is_active == True,
                ).first()
                if not device:
                    continue

                # ② 공통 DND/야간 차단 판별
                suppressed, reason = should_suppress_push(device, now)
                if suppressed:
                    stats["dropped_dnd"] += 1
                    logger.info(f"[Wishlist {reason} drop_with_history] device={device.device_uuid}, keyword={wl.keyword}")
                    continue

                if device.fcm_token:
                    try:
                        msg = messaging.Message(
                            notification=messaging.Notification(
                                title=f"설정한 목표가({wl.target_price:,}원) 이하로 내려갔어요",
                                body=f"{deal.title} · 현재 {deal_price_int:,}원",
                                image=deal.image_url if deal.image_url else None,
                            ),
                            data={
                                "navigate_to": "hotdeal",
                                "deal_id": str(deal.id),
                                "url": final_url,
                            },
                            token=device.fcm_token,
                        )
                        messages.append(msg)
                        stats["fcm_queued"] += 1
                    except Exception as wl_err:
                        logger.error(f"목표가 FCM 메시지 생성 실패: {wl_err}")

                if device.web_push_subscription:
                    sent_web = NotificationService.send_web_push_notification(
                        subscription_json=device.web_push_subscription,
                        keyword=wl.keyword,
                        title=deal.title or "",
                        price=deal_price_int,
                        site_name=deal.shop_name or "HotDeal",
                        deal_id=deal.id,
                        deal_url=final_url,
                        is_night_time=is_night_time,
                        db=db,
                        device=device,
                        custom_title=f"설정한 목표가({wl.target_price:,}원) 이하로 내려갔어요",
                        custom_body=f"{deal.title} · 현재 {deal_price_int:,}원",
                    )
                    if sent_web:
                        stats["web_push_sent"] += 1

        # 3. FCM 500개씩 청크로 나누어 일괄 전송
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
                except Exception as fcm_unexpected_err:
                    logger.warning(f"FCM Batch 발송 건너뜀: {fcm_unexpected_err}")
            stats["fcm_sent"] = total_sent
            logger.info(f"총 {total_sent}개의 푸시알림 발송 완료")

    except Exception as e:
        logger.error(f"푸시알림 매치 에러: {e}")
    finally:
        db.close()

    return stats
