import datetime
import json
import logging
import os
import re
from typing import Any, Optional

from firebase_admin import messaging
from firebase_admin.exceptions import FirebaseError

from backend.database.models import Deal, DeviceToken, NotificationAlert, PushKeyword
from backend.database.session import SessionLocal
from backend.models.wishlist_models import Wishlist

logger = logging.getLogger(__name__)

# Rate Limiting을 위한 전역 In-Memory Cache (실제 프로덕션은 Redis 활용 권장)
_push_rate_limit: dict = {}  # {device_id: {"count": 0, "date": "2023-10-01"}}
_DAILY_PUSH_LIMIT = 3


def reset_rate_limits() -> None:
    """테스트 및 런타임 격리를 위한 푸시 일일 레이트리밋 초기화 훅"""
    _push_rate_limit.clear()


clear_rate_limits = reset_rate_limits


def _get_daily_push_limit() -> int:
    raw = os.getenv("INSIGHTDEAL_DAILY_PUSH_LIMIT", "").strip()
    if raw:
        try:
            val = int(raw)
            if val > 0:
                return val
        except ValueError:
            pass
    return int(_DAILY_PUSH_LIMIT)


def _is_push_rate_limited(device_id: int, current_date_str: str) -> bool:
    rate_info = _push_rate_limit.get(device_id)
    if not rate_info or rate_info.get("date") != current_date_str:
        return False
    return int(rate_info.get("count", 0)) >= _get_daily_push_limit()


def _consume_push_rate_limit(device_id: int, current_date_str: str) -> None:
    rate_info = _push_rate_limit.get(device_id)
    if not rate_info or rate_info.get("date") != current_date_str:
        rate_info = {"count": 0, "date": current_date_str}
    rate_info["count"] = int(rate_info.get("count", 0)) + 1
    _push_rate_limit[device_id] = rate_info


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

        if start_time == end_time:
            return False
        if start_time < end_time:
            return start_time <= now_time < end_time
        else:
            return now_time >= start_time or now_time < end_time
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


def _estimate_krw_price(price_int: int, currency: Optional[str]) -> int:
    """저장 단위(KRW 원, USD/EUR/CNY 센트·전, JPY 엔)를 원화 환산 정수로 변환합니다."""
    if not price_int or price_int <= 0:
        return 0
    curr = (currency or "KRW").strip().upper()
    if curr == "USD":
        return int(round((price_int / 100.0) * 1450))
    if curr == "EUR":
        return int(round((price_int / 100.0) * 1550))
    if curr == "CNY":
        return int(round((price_int / 100.0) * 200))
    if curr == "JPY":
        return int(round(price_int * 9.5))
    return int(price_int)


def _format_push_price(price_int: int, currency: Optional[str]) -> str:
    """알림 본문용 통화 표기 생성 (USD $59.00 -> '$59.00 (약 85,550원)')."""
    if not price_int or price_int <= 0:
        return ""
    curr = (currency or "KRW").strip().upper()
    krw_est = _estimate_krw_price(price_int, curr)
    if curr == "USD":
        return f"${price_int / 100.0:,.2f} (약 {krw_est:,}원)"
    if curr == "EUR":
        return f"€{price_int / 100.0:,.2f} (약 {krw_est:,}원)"
    if curr == "CNY":
        return f"¥{price_int / 100.0:,.2f} (약 {krw_est:,}원)"
    if curr == "JPY":
        return f"{price_int:,}엔 (약 {krw_est:,}원)"
    return f"{price_int:,}원"


def _extract_options_searchable(options_raw: Any) -> str:
    if not options_raw:
        return ""
    try:
        opts = json.loads(options_raw) if isinstance(options_raw, str) else options_raw
        if isinstance(opts, list):
            parts = []
            for o in opts[:30]:
                if isinstance(o, dict):
                    nm = str(o.get("name") or o.get("title") or "").strip()
                    if nm:
                        parts.append(nm)
            return " ".join(parts)
    except Exception:
        pass
    return ""


def _match_smart_keyword(raw_keyword: str, deal: Deal, deal_price_int: int) -> tuple[bool, str]:
    """키워드 조건 문법 파싱 및 매칭
    지원 문법:
      - 기본 단어: 공백 구분 AND 매칭 (예: '맥북 에어', 한영 동의어 및 공백 무시 지원)
      - 제외 단어: '-단어' 또는 '!단어' (예: '아이폰 -케이스 !필름')
      - 상한 금액: ':금액' 또는 '<=금액' (예: '제로콜라 :15,000', '제로콜라 <=15000', 외화는 원화 환산 기준 비교)
      - 쇼핑몰/카테고리 한정: '@쇼핑몰' / '#카테고리' (예: '물티슈 @쿠팡', 'SSD #PC용품')
    """
    if not raw_keyword or not raw_keyword.strip():
        return False, ""

    from backend.core.deal_normalizer import KOR_ENG_SEARCH_SYNONYMS

    tokens = raw_keyword.strip().split()
    positive_terms: list[str] = []
    exclude_terms: list[str] = []
    shop_filters: list[str] = []
    cat_filters: list[str] = []
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
        elif token.startswith("@") and len(token) > 1:
            shop_filters.append(token[1:].strip().lower())
        elif token.startswith("#") and len(token) > 1:
            cat_filters.append(token[1:].strip().lower())
        else:
            positive_terms.append(token.lower())

    if not positive_terms:
        return False, raw_keyword

    deal_shop_low = re.sub(r"\s+", "", (getattr(deal, "shop_name", None) or "").lower())
    deal_title_low = (getattr(deal, "title", None) or "").lower()
    deal_cat_low = re.sub(r"\s+", "", (getattr(deal, "category", None) or "").lower())
    if shop_filters:
        if not any(
            re.sub(r"\s+", "", sf) in deal_shop_low or sf in deal_title_low
            for sf in shop_filters
        ):
            return False, raw_keyword
    if cat_filters:
        if not any(re.sub(r"\s+", "", cf) in deal_cat_low for cf in cat_filters):
            return False, raw_keyword

    searchable = " ".join([
        (getattr(deal, "title", None) or ""),
        (getattr(deal, "category", None) or ""),
        (getattr(deal, "base_product_name", None) or ""),
        (getattr(deal, "search_keywords", None) or ""),
        _extract_options_searchable(getattr(deal, "options_data", None)),
    ]).lower()
    searchable_compact = re.sub(r"\s+", "", searchable)

    def _term_matches(term: str) -> bool:
        t_compact = re.sub(r"\s+", "", term)
        if term in searchable or (t_compact and t_compact in searchable_compact):
            return True
        syns = KOR_ENG_SEARCH_SYNONYMS.get(term) or KOR_ENG_SEARCH_SYNONYMS.get(t_compact) or ()
        for syn in syns:
            syn_low = syn.lower()
            syn_compact = re.sub(r"\s+", "", syn_low)
            if syn_low in searchable or (syn_compact and syn_compact in searchable_compact):
                return True
        return False

    if not all(_term_matches(term) for term in positive_terms):
        return False, raw_keyword

    if any(ex_term in searchable for ex_term in exclude_terms):
        return False, raw_keyword

    deal_currency = (getattr(deal, "currency", None) or "KRW").strip().upper()
    effective_krw_price = _estimate_krw_price(deal_price_int, deal_currency)
    if max_price is not None:
        if effective_krw_price <= 0 or effective_krw_price > max_price:
            return False, raw_keyword

    display_name = " ".join(positive_terms)
    if max_price is not None:
        display_name = f"{display_name} ({max_price:,}원 이하)"
    return True, display_name


def _record_notification_alert(
    db,
    user_id: str,
    title: str,
    keyword: str,
    deal_url: str,
    dnd_suppressed: bool = False,
):
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
                    dnd_suppressed=bool(dnd_suppressed),
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
                price_txt = _format_push_price(p_int, getattr(d, "currency", "KRW"))
                alert_title = f"{d.title} · {price_txt}" if price_txt else d.title
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
        "toss_push_sent": 0,
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
        deal_currency = (getattr(deal, "currency", None) or "KRW").strip().upper()
        formatted_deal_price = _format_push_price(deal_price_int, deal_currency)
        deal_krw_est = _estimate_krw_price(deal_price_int, deal_currency)

        from backend.services.notification_service import NotificationService
        from backend.services.toss_push_service import (
            sanitize_toss_keyword,
            send_toss_push_message,
        )

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
            alert_body = f"{deal.title} · {formatted_deal_price}" if formatted_deal_price else deal.title

            # ① 공통 DND/야간 차단 판별 (DND 억제 여부를 NotificationAlert에 반영)
            suppressed, reason = should_suppress_push(device, now)
            is_dnd_suppressed = bool(suppressed and reason == "dnd_active")

            # ② 인앱 알림함 선적재 (drop_with_history)
            _record_notification_alert(
                db=db,
                user_id=device.device_uuid,
                title=alert_body,
                keyword=display_kw,
                deal_url=final_url,
                dnd_suppressed=is_dnd_suppressed,
            )

            if suppressed:
                stats["dropped_dnd"] += 1
                logger.info(f"[{reason} drop_with_history] device={device.device_uuid}, keyword={display_kw}")
                continue

            if _is_push_rate_limited(device.id, current_date_str):
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

            toss_anon_key = getattr(device, "toss_anon_key", None)
            if toss_anon_key:
                try:
                    short_kw = sanitize_toss_keyword(display_kw.split(" (")[0] or kw.keyword)
                    toss_res = send_toss_push_message(
                        toss_anon_key,
                        context={"keyword": short_kw, "dealId": str(deal.id)},
                    )
                    if toss_res.get("sent"):
                        stats["toss_push_sent"] += 1
                        dispatched_channel = True
                except Exception as toss_err:
                    logger.warning(f"[TossPush] 키워드 발송 격리 예외: device={device.device_uuid}, err={type(toss_err).__name__}")

            if dispatched_channel:
                _consume_push_rate_limit(device.id, current_date_str)

        # 2. 목표가 알림 (Wishlist) 매칭 (외화는 원화 환산가로 비교)
        if deal_krw_est > 0:
            wishlists_db = []
            try:
                wishlists_db = db.query(Wishlist).filter(
                    Wishlist.is_active == True,
                    Wishlist.alert_enabled == True,
                    Wishlist.target_price >= deal_krw_est,
                ).all()
            except Exception as wl_query_err:
                logger.debug(f"Wishlist 조회 건너뜀 (테이블 미존재 또는 조회 오류): {wl_query_err}")

            for wl in wishlists_db:
                wl_key = (wl.keyword or "").lower().strip()
                if not wl_key:
                    continue
                wl_searchable = " ".join([
                    (deal.title or ""),
                    (deal.base_product_name or ""),
                    (deal.search_keywords or ""),
                ]).lower()
                is_wl_match = (
                    wl_key in wl_searchable
                    or (wl_key.replace(" ", "") in re.sub(r"\s+", "", wl_searchable))
                    or (deal.ecommerce_link and wl_key == deal.ecommerce_link.lower())
                    or (deal.post_link and wl_key == deal.post_link.lower())
                )
                if not is_wl_match:
                    continue

                stats["matched_wishlists"] += 1
                wl_curr_price_txt = formatted_deal_price or f"{deal_price_int:,}원"
                wl_alert_title = f"[목표가 {wl.target_price:,}원 이하] {deal.title} · 현재 {wl_curr_price_txt}"

                device = db.query(DeviceToken).filter(
                    DeviceToken.device_uuid == wl.user_id,
                    DeviceToken.is_active == True,
                ).first()

                suppressed, reason = should_suppress_push(device, now) if device else (False, "ok")
                is_dnd_suppressed = bool(device and suppressed and reason == "dnd_active")

                # ① DND 판별 반영 후 인앱 알림함 선적재 (레이트리밋 시에도 인앱 기록 보존)
                _record_notification_alert(
                    db=db,
                    user_id=wl.user_id,
                    title=wl_alert_title,
                    keyword=wl.keyword,
                    deal_url=final_url,
                    dnd_suppressed=is_dnd_suppressed,
                )

                if not device:
                    continue

                # ② 공통 DND/야간 차단 판별 (DND 억제 시 일일 한도 미소모)
                if suppressed:
                    stats["dropped_dnd"] += 1
                    logger.info(f"[Wishlist {reason} drop_with_history] device={device.device_uuid}, keyword={wl.keyword}")
                    continue

                # ③ 키워드/위시리스트 공통 일일 푸시 한도(3건) 검사
                if _is_push_rate_limited(device.id, current_date_str):
                    continue

                dispatched_wl_channel = False
                if device.fcm_token:
                    try:
                        msg = messaging.Message(
                            notification=messaging.Notification(
                                title=f"설정한 목표가({wl.target_price:,}원) 이하로 내려갔어요",
                                body=f"{deal.title} · 현재 {wl_curr_price_txt}",
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
                        dispatched_wl_channel = True
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
                        custom_body=f"{deal.title} · 현재 {wl_curr_price_txt}",
                    )
                    if sent_web:
                        stats["web_push_sent"] += 1
                        dispatched_wl_channel = True

                toss_anon_key = getattr(device, "toss_anon_key", None)
                if toss_anon_key:
                    try:
                        short_kw = sanitize_toss_keyword(wl.keyword)
                        toss_res = send_toss_push_message(
                            toss_anon_key,
                            context={"keyword": short_kw, "dealId": str(deal.id)},
                        )
                        if toss_res.get("sent"):
                            stats["toss_push_sent"] += 1
                            dispatched_wl_channel = True
                    except Exception as toss_err:
                        logger.warning(f"[TossPush] 목표가 발송 격리 예외: device={device.device_uuid}, err={type(toss_err).__name__}")

                if dispatched_wl_channel:
                    _consume_push_rate_limit(device.id, current_date_str)

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


def dnd_morning_digest_job(
    session_factory=None,
    now_kst: Optional[datetime.datetime] = None,
) -> dict:
    """
    [P1-04] DND 해제 시각 도래 시 밤사이 억제 적재된 알림을 기기당 1건 요약 푸시로 일괄 발송합니다.
    - digest_sent_at 기록으로 재실행 멱등성 보장 (중복 발송 0건)
    - 동일 FCM 토큰 중복 발송 방지
    """
    factory = session_factory or SessionLocal
    db = factory()
    stats = {
        "pending_alerts": 0,
        "digests_sent": 0,
        "fcm_sent": 0,
        "web_push_sent": 0,
        "toss_push_sent": 0,
    }
    try:
        kst = datetime.timezone(datetime.timedelta(hours=9))
        if now_kst is None:
            now = datetime.datetime.now(kst)
        elif now_kst.tzinfo is None:
            now = now_kst.replace(tzinfo=kst)
        else:
            now = now_kst.astimezone(kst)

        pending_alerts = (
            db.query(NotificationAlert)
            .filter(
                NotificationAlert.dnd_suppressed == True,
                NotificationAlert.digest_sent_at.is_(None),
            )
            .all()
        )
        stats["pending_alerts"] = len(pending_alerts)
        if not pending_alerts:
            return stats

        alerts_by_user: dict[str, list[NotificationAlert]] = {}
        for al in pending_alerts:
            if al.user_id:
                alerts_by_user.setdefault(al.user_id, []).append(al)

        from backend.services.notification_service import NotificationService

        fcm_entries: list[tuple[Any, list[tuple[str, list[NotificationAlert]]]]] = []
        fcm_token_groups: dict[str, list[tuple[str, list[NotificationAlert]]]] = {}
        counted_users: set[str] = set()
        alerts_to_mark: list[NotificationAlert] = []
        marked_alert_ids: set[int] = set()

        for user_id, user_alerts in alerts_by_user.items():
            device = db.query(DeviceToken).filter(
                DeviceToken.device_uuid == user_id,
                DeviceToken.is_active == True,
            ).first()
            if not device or not device.dnd_enabled:
                continue

            suppressed, _ = should_suppress_push(device, now)
            if suppressed:
                continue

            count_n = len(user_alerts)
            digest_title = f"밤사이 놓친 핫딜 {count_n}건"
            digest_body = f"방해금지 시간 동안 도착한 핫딜 {count_n}건을 지금 확인해 보세요."

            if device.fcm_token:
                if device.fcm_token not in fcm_token_groups:
                    try:
                        msg = messaging.Message(
                            notification=messaging.Notification(
                                title=digest_title,
                                body=digest_body,
                            ),
                            data={
                                "navigate_to": "notifications",
                                "count": str(count_n),
                            },
                            token=device.fcm_token,
                        )
                        groups_list: list[tuple[str, list[NotificationAlert]]] = [(user_id, user_alerts)]
                        fcm_token_groups[device.fcm_token] = groups_list
                        fcm_entries.append((msg, groups_list))
                    except Exception as fcm_err:
                        logger.error(f"아침 요약 FCM 메시지 생성 실패: {fcm_err}")
                else:
                    fcm_token_groups[device.fcm_token].append((user_id, user_alerts))

            if device.web_push_subscription:
                sent_web = NotificationService.send_web_push_notification(
                    subscription_json=device.web_push_subscription,
                    keyword="밤사이 요약",
                    title=digest_title,
                    price=0,
                    site_name="얼만데",
                    deal_id=0,
                    deal_url="",
                    is_night_time=False,
                    db=db,
                    device=device,
                    custom_title=digest_title,
                    custom_body=digest_body,
                )
                if sent_web:
                    stats["web_push_sent"] += 1
                    if user_id not in counted_users:
                        counted_users.add(user_id)
                        stats["digests_sent"] += 1
                    for al in user_alerts:
                        if id(al) not in marked_alert_ids:
                            marked_alert_ids.add(id(al))
                            alerts_to_mark.append(al)

            toss_anon_key = getattr(device, "toss_anon_key", None)
            if toss_anon_key and not device.fcm_token and not device.web_push_subscription:
                try:
                    latest_al = user_alerts[-1]
                    m_did = re.search(r"dealId=(\d+)|/deals/(\d+)", str(getattr(latest_al, "deal_url", "") or ""))
                    latest_did = (m_did.group(1) or m_did.group(2)) if m_did else ""
                    toss_res = send_toss_push_message(
                        toss_anon_key,
                        context={"keyword": sanitize_toss_keyword(f"밤사이 핫딜 {count_n}건"), "dealId": str(latest_did)},
                    )
                    if toss_res.get("sent"):
                        stats["toss_push_sent"] += 1
                        if user_id not in counted_users:
                            counted_users.add(user_id)
                            stats["digests_sent"] += 1
                        for al in user_alerts:
                            if id(al) not in marked_alert_ids:
                                marked_alert_ids.add(id(al))
                                alerts_to_mark.append(al)
                    elif toss_res.get("reason") == "not_configured":
                        for al in user_alerts:
                            if id(al) not in marked_alert_ids:
                                marked_alert_ids.add(id(al))
                                alerts_to_mark.append(al)
                except Exception as toss_err:
                    logger.warning(f"[TossPush] 아침 요약 격리 예외: device={device.device_uuid}, err={type(toss_err).__name__}")

        if fcm_entries:
            chunk_size = 500
            total_fcm = 0
            for i in range(0, len(fcm_entries), chunk_size):
                chunk_entries = fcm_entries[i:i + chunk_size]
                chunk = [entry[0] for entry in chunk_entries]
                try:
                    response = messaging.send_each(chunk)
                    succ_cnt = int(getattr(response, "success_count", 0) or 0)
                    total_fcm += succ_cnt
                    per_responses = getattr(response, "responses", None)
                    if isinstance(per_responses, (list, tuple)) and len(per_responses) == len(chunk_entries):
                        for (_, tok_user_groups), resp_item in zip(chunk_entries, per_responses):
                            if getattr(resp_item, "success", False):
                                for uid, u_alerts in tok_user_groups:
                                    if uid not in counted_users:
                                        counted_users.add(uid)
                                        stats["digests_sent"] += 1
                                    for al in u_alerts:
                                        if id(al) not in marked_alert_ids:
                                            marked_alert_ids.add(id(al))
                                            alerts_to_mark.append(al)
                    elif succ_cnt > 0:
                        for _, tok_user_groups in chunk_entries:
                            for uid, u_alerts in tok_user_groups:
                                if uid not in counted_users:
                                    counted_users.add(uid)
                                    stats["digests_sent"] += 1
                                for al in u_alerts:
                                    if id(al) not in marked_alert_ids:
                                        marked_alert_ids.add(id(al))
                                        alerts_to_mark.append(al)
                except FirebaseError as fcm_err:
                    logger.error(f"아침 요약 FCM Batch 발송 오류: {fcm_err}")
                except Exception as fcm_unexpected_err:
                    logger.warning(f"아침 요약 FCM Batch 발송 건너뜀: {fcm_unexpected_err}")
            stats["fcm_sent"] = total_fcm

        if alerts_to_mark:
            sent_utc = datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None)
            for al in alerts_to_mark:
                al.digest_sent_at = sent_utc
            db.commit()

    except Exception as e:
        db.rollback()
        logger.error(f"DND 아침 요약 배치 오류: {e}")
    finally:
        db.close()

    return stats
