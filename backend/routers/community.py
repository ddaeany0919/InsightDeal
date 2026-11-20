from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, Response
from fastapi.concurrency import run_in_threadpool
from sqlalchemy import func, inspect as sa_inspect, or_
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session, defer, joinedload
from sqlalchemy.orm.attributes import set_committed_value
from backend.database.session import get_db_session
from fastapi.responses import StreamingResponse
import asyncio
import copy
import hashlib
import hmac
import html as _html
import httpx
from backend.database import models
from backend.core.categories import normalize_category_key
from backend.core.deal_normalizer import upgrade_lowres_image_url
from backend.core.device_auth import assert_device_owner, require_device_token
from backend.core.url_utils import is_safe_external_url, is_search_or_community_url
from backend.core.utils import to_utc_iso
from backend.routers.admin import _verify_admin_key
from collections import OrderedDict
import logging
import os
import re
import requests
import threading
import time
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from urllib.parse import unquote as _unquote, urlsplit as _urlsplit, urlunsplit as _urlunsplit

import json
from pydantic import BaseModel
from typing import List, Optional

router = APIRouter()
logger = logging.getLogger(__name__)

_LOOPBACK_HOSTS = {"127.0.0.1", "::1", "localhost", "testclient"}
_KST = timezone(timedelta(hours=9))
_FMKOREA_TRENDING_FILE_OVERRIDE: Optional[str] = None

_COMPOUND_KOR_SEGMENTS = (
    "갤럭시", "아이폰", "아이패드", "맥북", "맥미니", "에어팟", "애플워치",
    "샤오신", "레노버", "로보락", "드리미", "다이슨", "로지텍", "샥즈",
    "오픈런", "오픈핏", "오픈닷", "닌텐도", "플레이스테이션", "듀얼센스",
    "폴드", "플립", "버즈", "워치", "프로", "에어", "미니", "울트라", "플러스", "맥스",
    "패드", "북", "탭", "스위치", "청소기", "노트북", "모니터", "키보드", "마우스", "이어폰", "헤드셋",
    "무선", "유선", "게이밍", "기계식", "블루투스", "로봇", "보조", "배터리", "충전기", "스피커", "외장", "포터블", "제로", "콜라", "물티슈",
)


def _to_utc_naive(dt_val: Optional[datetime]) -> Optional[datetime]:
    """DB의 naive(UTC 기준) 또는 timezone-aware 시각을 비교 안전한 UTC naive datetime으로 정규화합니다."""
    if dt_val is None:
        return None
    if isinstance(dt_val, str):
        dt_val = datetime.fromisoformat(dt_val.strip().replace("Z", "+00:00"))
    if getattr(dt_val, "tzinfo", None) is not None:
        return dt_val.astimezone(timezone.utc).replace(tzinfo=None)
    return dt_val


def _to_kst_aware(dt_val: Optional[datetime]) -> Optional[datetime]:
    """DB의 naive(UTC 기준) 또는 timezone-aware 시각을 KST(+09:00) aware datetime으로 변환합니다."""
    if dt_val is None:
        return None
    if getattr(dt_val, "tzinfo", None) is None:
        return dt_val.replace(tzinfo=timezone.utc).astimezone(_KST)
    return dt_val.astimezone(_KST)


def _escape_like(raw_val: str) -> str:
    """SQL LIKE / ILIKE 와일드카드 문자(\\, %, _)를 리터럴 매칭되도록 이스케이프합니다."""
    if not raw_val:
        return ""
    return raw_val.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def _decompose_compound_query_token(tok: str) -> list[list[str]]:
    """
    붙여쓴 복합어('갤럭시폴드', '갤럭시버즈', '오픈런프로', '맥북프로', '샤오신패드', 'RTX5070')를
    하위 의미 단위 쌍으로 분해하여 중간 모델 문자('갤럭시 Z폴드8')나 공백('RTX 5070')이 있어도 검색되도록 지원합니다.
    """
    t = (tok or "").strip()
    if len(t) < 3:
        return []
    candidates: list[list[str]] = []

    # 1) 영문+숫자+한글 경계 분해 (예: RTX5070 -> ['RTX', '5070'], M4맥북 -> ['M4', '맥북'])
    alnum_kor_parts = re.findall(r"[a-zA-Z]{2,}|\d{2,}|[a-zA-Z]+\d+|\d+[a-zA-Z]+|[가-힣]{2,}", t)
    # 순수 영문+숫자 결합형 우선 분해 (예: RTX5070 -> ['RTX', '5070'])
    pure_alnum = re.findall(r"[a-zA-Z]{2,}|\d{2,}", t)
    if len(pure_alnum) >= 2 and "".join(pure_alnum).lower() == re.sub(r"[^a-zA-Z0-9]", "", t).lower():
        candidates.append(pure_alnum)
    elif len(alnum_kor_parts) >= 2:
        candidates.append(alnum_kor_parts)

    # 2) 한글 브랜드/시리즈 복합어 접두·접미 분해 (예: 갤럭시폴드 -> ['갤럭시', '폴드'])
    t_low = t.lower()
    for seg in _COMPOUND_KOR_SEGMENTS:
        if t_low.startswith(seg) and len(t_low) > len(seg):
            rest = t[len(seg):]
            if len(rest) >= 2 or (len(seg) >= 3 and rest in ("탭", "북")):
                pair = [t[:len(seg)], rest]
                if pair not in candidates:
                    candidates.append(pair)
        elif t_low.endswith(seg) and len(t_low) > len(seg):
            head = t[:-len(seg)]
            if len(head) >= 2:
                pair = [head, t[-len(seg):]]
                if pair not in candidates:
                    candidates.append(pair)
    return candidates


def _safe_loaded_attr(obj, attr_name: str, default=""):
    """defer()로 지연 로딩 설정된 컬럼에 불필요한 N+1 쿼리를 발생시키지 않고 이미 로드된 값만 읽습니다."""
    try:
        from sqlalchemy import inspect as sa_inspect
        insp = sa_inspect(obj)
        if attr_name in insp.unloaded:
            return default
    except Exception as exc:
        logger.debug(f"_safe_loaded_attr inspect skipped for {attr_name}: {exc}")
    val = getattr(obj, attr_name, default)
    return val if val is not None else default

_COMMUNITY_ID_CACHE: dict[int, str] = {}
_COMMUNITY_DISPLAY_MAP: dict[str, str] = {
    "fmkorea": "펨코",
    "ppomppu": "뽐뿌",
    "ppomppu_overseas": "뽐뿌해외",
    "ruliweb": "루리웹",
    "clien": "클리앙",
    "quasarzone": "퀘이사존",
    "ali_ppomppu": "알리뽐뿌",
    "bbasak_domestic": "빠삭국내",
    "bbasak_overseas": "빠삭해외",
    "bbasak_parenting": "빠삭육아",
}

_REPORT_CLOSED_THRESHOLD = 3
_REPORT_CLOSED_HOURLY_LIMIT = 15
_REPORT_CLOSED_WINDOW_SEC = 3600
_REPORT_CLOSED_RATE_LIMIT: dict[str, list[float]] = {}


def reset_rate_limits() -> None:
    """테스트 및 런타임 격리용 커뮤니티 레이트리밋 초기화 훅"""
    _REPORT_CLOSED_RATE_LIMIT.clear()


clear_rate_limits = reset_rate_limits


def _format_cursor_iso(dt: Optional[datetime]) -> Optional[str]:
    """키셋 커서 정밀도(마이크로초 포함) 보존 ISO-8601 UTC 문자열 반환"""
    if dt is None:
        return None
    if getattr(dt, "tzinfo", None) is None:
        dt = dt.replace(tzinfo=timezone.utc)
    else:
        dt = dt.astimezone(timezone.utc)
    if dt.microsecond:
        return dt.strftime("%Y-%m-%dT%H:%M:%S.%fZ")
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def _compute_is_search_fallback(clean_ecom_url: str, resolved_ecom_url: str) -> bool:
    """직접 상품 링크가 없고 쇼핑몰 검색 폴백 링크로 우회된 경우 True 반환"""
    if not clean_ecom_url and bool(resolved_ecom_url):
        return True
    if resolved_ecom_url and is_search_or_community_url(resolved_ecom_url):
        return True
    return False


_DANGEROUS_IMAGE_URL_TOKENS = (
    "javascript:",
    "vbscript:",
    "data:",
    "<script",
    "</script",
    "onerror=",
    "onload=",
    "onclick=",
    "onmouseover=",
    "onfocus=",
    "onblur=",
    "eval(",
    "expression(",
    "<",
    ">",
    '"',
    "'",
    "user:pass@",
)

_PLACEHOLDER_IMAGE_TOKENS = (
    "no_image.svg",
    "thumb_no_image.svg",
    "noimage",
    "dot03.gif",
    "logo_f.svg",
    "49841804f3d132d75a6c11b1510af812.png",
    "https:undefined",
    "/web/upload/",
    "emoticon",
    "sticker",
    "lazyloading",
    "/t.gif",
    "drag_img",
    "spacer.",
    "pixel.",
    "blank.",
    "1x1.",
)


# 알려진 HTML/DOM 이벤트 핸들러 및 속성 탈출 컨텍스트(공백·따옴표 등 뒤 임의 on*=) 차단용 정규식
# (?ver=1&online=1, ?only=1, ?once=1, ?ongoing=1 등 정상 영단어 쿼리 키 오탐 방지)
_KNOWN_DOM_EVENT_HANDLER_NAMES = (
    "abort", "activate", "afterprint", "afterscriptexecute",
    "animationcancel", "animationend", "animationiteration", "animationstart",
    "auxclick", "beforeactivate", "beforecopy", "beforecut", "beforedeactivate",
    "beforeinput", "beforematch", "beforepaste", "beforeprint",
    "beforescriptexecute", "beforetoggle", "beforeunload", "begin", "blur",
    "bounce", "cancel", "canplay", "canplaythrough", "change", "click", "close",
    "contentvisibilityautostatechange", "contextmenu", "copy", "cuechange", "cut",
    "dblclick", "deactivate", "drag", "dragend", "dragenter", "dragexit",
    "dragleave", "dragover", "dragstart", "drop", "durationchange", "emptied",
    "encrypted", "end", "ended", "error", "filterchange", "finish", "focus",
    "focusin", "focusout", "formdata", "fullscreenchange", "fullscreenerror",
    "gotpointercapture", "hashchange", "input", "invalid", "keydown", "keypress",
    "keyup", "languagechange", "load", "loadeddata", "loadedmetadata", "loadend",
    "loadstart", "lostpointercapture", "message", "messageerror", "mousedown",
    "mouseenter", "mouseleave", "mousemove", "mouseout", "mouseover", "mouseup",
    "mousewheel", "offline", "online", "open", "pagehide", "pagereveal",
    "pageshow", "pageswap", "paste", "pause", "play", "playing", "pointercancel",
    "pointerdown", "pointerenter", "pointerleave", "pointermove", "pointerout",
    "pointerover", "pointerup", "popstate", "progress", "ratechange",
    "readystatechange", "rejectionhandled", "repeat", "reset", "resize", "scroll",
    "scrollend", "scrollsnapchange", "scrollsnapchanging", "search",
    "securitypolicyviolation", "seeked", "seeking", "select", "selectionchange",
    "selectstart", "show", "slotchange", "stalled", "start", "storage", "submit",
    "suspend", "timeupdate", "toggle", "touchcancel", "touchend", "touchmove",
    "touchstart", "transitioncancel", "transitionend", "transitionrun",
    "transitionstart", "unhandledrejection", "unload", "visibilitychange",
    "volumechange", "waiting", "waitingforkey", "webkitanimationend",
    "webkitanimationiteration", "webkitanimationstart", "webkittransitionend",
    "wheel",
)
_EVENT_HANDLER_ATTR_RE = re.compile(
    r"(?:(?<![a-z0-9])on(?:" + "|".join(_KNOWN_DOM_EVENT_HANDLER_NAMES) + r")\s*=|(?:^|[\s\"'<>`])on[a-z]{2,24}\s*=)"
)
_SEMICOLON_HTML_ENTITY_RE = re.compile(r"&(?:[a-zA-Z][a-zA-Z0-9]{1,31}|#\d+|#[xX][0-9a-fA-F]+);")
_SECURITY_HTML_ENTITY_RE = re.compile(r"&(?:[a-zA-Z][a-zA-Z0-9]{1,31};|#\d+;?|#[xX][0-9a-fA-F]+;?)")
_IMG_TAG_RE = re.compile(r"<img\b([^>]*)>", flags=re.IGNORECASE)
_IMG_ATTR_RE = re.compile(
    r'(?:^|[\s/])(data-original|data-src|src)\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^\s"\'<>`]+))',
    flags=re.IGNORECASE,
)


def _unescape_semicolon_entities(text: str) -> str:
    """HTML5 속성 값 파싱 규칙에 맞춰 명시적 세미콜론(;) 종결 엔티티만 1회 디코딩"""
    return _SEMICOLON_HTML_ENTITY_RE.sub(lambda m: _html.unescape(m.group(0)), text)


def _unescape_html_for_security(text: str) -> str:
    """보안 검증용 HTML 엔티티 디코딩 (세미콜론 없는 &reg=/&lt= 오변환 방지, 숫자 문자 참조는 세미콜론 선택 허용)"""
    return _SECURITY_HTML_ENTITY_RE.sub(lambda m: _html.unescape(m.group(0)), text)


def _normalize_and_validate_body_image(raw_candidate: str) -> Optional[str]:
    if not raw_candidate:
        return None
    # 반환용 URL 정제는 HTML5 속성 규칙에 따라 명시적 세미콜론 엔티티만 1회 디코딩
    cand = _unescape_semicolon_entities(str(raw_candidate)).strip().rstrip(",)")
    if not cand:
        return None
    if cand.startswith("//"):
        cand = "https:" + cand
    if len(cand) > 2048 or re.search(r"[\x00-\x1f\x7f\s<>\"'`\\]", cand):
        return None
    if not is_safe_external_url(cand):
        return None

    variants: list[str] = [cand]
    seen_variants: set[str] = {cand}
    cur = cand
    # URL 디코딩과 HTML 엔티티 디코딩을 고정점까지 최대 16회 교차 적용하여 다중·혼합 인코딩 XSS 우회 차단
    for _ in range(16):
        unquoted = _unquote(cur)
        unescaped = _unescape_html_for_security(unquoted)
        progressed = False
        for candidate_variant in (unquoted, unescaped):
            if candidate_variant not in seen_variants:
                seen_variants.add(candidate_variant)
                variants.append(candidate_variant)
                progressed = True
        next_cur = unescaped if unescaped != cur else unquoted
        if not progressed and next_cur == cur:
            break
        cur = next_cur

    for variant in variants:
        if re.search(r"[\x00-\x1f\x7f<>\"'`\\]", variant):
            return None
        if any(ch.isspace() and ch != " " for ch in variant):
            return None
        low_var = variant.lower()
        if any(tok in low_var for tok in _DANGEROUS_IMAGE_URL_TOKENS):
            return None
        if _EVENT_HANDLER_ATTR_RE.search(low_var):
            return None
        if any(bad in low_var for bad in _PLACEHOLDER_IMAGE_TOKENS):
            return None
        if variant != cand:
            try:
                parsed_v = _urlsplit(variant)
            except ValueError:
                return None
            if parsed_v.scheme.lower() not in ("http", "https") or not parsed_v.netloc:
                return None
            if any(ch.isspace() for ch in parsed_v.scheme) or any(ch.isspace() for ch in parsed_v.netloc):
                return None
            sanitized_variant = _urlunsplit((
                parsed_v.scheme,
                parsed_v.netloc,
                parsed_v.path.replace(" ", "%20"),
                parsed_v.query.replace(" ", "%20"),
                parsed_v.fragment.replace(" ", "%20"),
            ))
            if not is_safe_external_url(sanitized_variant):
                return None
    return cand


def _extract_body_images(raw_html: str) -> list[str]:
    """본문 정제 전 원본 HTML/텍스트에서 안전한 다중 첨부 이미지를 추출·정규화·중복제거"""
    if not raw_html:
        return []
    candidates: list[str] = []

    for img_match in _IMG_TAG_RE.finditer(raw_html):
        attrs_raw = img_match.group(1) or ""
        attr_map: dict[str, str] = {}
        for attr_match in _IMG_ATTR_RE.finditer(attrs_raw):
            attr_name = (attr_match.group(1) or "").lower()
            attr_val = attr_match.group(2) or attr_match.group(3) or attr_match.group(4) or ""
            if attr_name and attr_name not in attr_map and attr_val.strip():
                attr_map[attr_name] = attr_val
        for preferred in ("data-original", "data-src", "src"):
            if preferred in attr_map:
                candidates.append(attr_map[preferred])
                break

    for m in re.finditer(r"\[상품이미지\s*:\s*([^\]]+)\]", raw_html, flags=re.IGNORECASE):
        candidates.append(m.group(1))

    for m in re.finditer(r"\[첨부된\s*이미지\s*링크들\]\s*:?\s*([^\n\r<]+)", raw_html, flags=re.IGNORECASE):
        for part in re.split(r"[\s,]+", m.group(1)):
            if part:
                candidates.append(part)

    results: list[str] = []
    seen_unquoted: set[str] = set()
    for raw_cand in candidates:
        valid_url = _normalize_and_validate_body_image(raw_cand)
        if not valid_url:
            continue
        dedup_key = _unquote(valid_url)
        if dedup_key in seen_unquoted:
            continue
        seen_unquoted.add(dedup_key)
        results.append(valid_url)
        if len(results) >= 20:
            break
    return results


def _merge_body_images(existing_imgs: list[str], new_imgs: list[str]) -> list[str]:
    merged: list[str] = []
    seen: set[str] = set()
    for u in (existing_imgs or []) + (new_imgs or []):
        k = _unquote(u)
        if k not in seen:
            seen.add(k)
            merged.append(u)
        if len(merged) >= 20:
            break
    return merged


def get_community_display_name_by_id(cid: int, db=None) -> Optional[str]:
    if cid in _COMMUNITY_ID_CACHE:
        return _COMMUNITY_ID_CACHE[cid]
    COMMUNITY_MAP = _COMMUNITY_DISPLAY_MAP
    if db:
        try:
            all_comms = db.query(models.Community).all()
            for c in all_comms:
                _COMMUNITY_ID_CACHE[c.id] = c.display_name or COMMUNITY_MAP.get(c.name, c.name)
            if cid in _COMMUNITY_ID_CACHE:
                return _COMMUNITY_ID_CACHE[cid]
        except Exception as exc:
            logger.debug(f"get_community_display_name_by_id DB lookup fallback (cid={cid}): {exc}")
    fallback_id_map = {
        1: "뽐뿌", 2: "루리웹", 3: "클리앙", 4: "알리뽐뿌",
        5: "퀘이사존", 6: "펨코", 7: "빠삭국내", 8: "빠삭해외", 9: "빠삭육아",
        10: "뽐뿌해외",
    }
    return fallback_id_map.get(cid)

def _build_direct_mall_search_url(shop_name: str, raw_title: str, post_url: str = "", comp_name: str = "") -> str:
    """
    뽐뿌 등 커뮤니티 본문 내 외부 링크가 누락되었거나 모음전으로 생략되었을 때,
    제목의 [쇼핑몰] 태그 및 상품명을 기반으로 해당 쇼핑몰의 다이렉트 상품 페이지로 즉시 연결합니다.
    (사용자가 '쇼핑몰 바로가기'나 카드를 눌렀을 때 다시 커뮤니티 게시글로 튕기는 현상 원천 차단!)
    """
    import urllib.parse
    from backend.services.aggregator_service import infer_shop_name

    inferred_shop = infer_shop_name(raw_title or "", "", shop_name or "")
    if not inferred_shop:
        if "id=ppomppu8" in (post_url or "") or comp_name == "알리뽐뿌" or (raw_title and any(k in raw_title.lower() for k in ("$", "달러", "알리", "코인", "초이스", "광군제"))):
            inferred_shop = "알리익스프레스"
        elif "id=ppomppu4" in (post_url or ""):
            inferred_shop = "아마존"
        elif raw_title:
            m_b = re.match(r"^\s*\[([^\]]+)\]", raw_title)
            if m_b:
                b_txt = m_b.group(1).strip().lower()
                if "지그재그" in b_txt or "zigzag" in b_txt:
                    inferred_shop = "지그재그"
                elif "kream" in b_txt or "크림" in b_txt:
                    inferred_shop = "KREAM"
    if not inferred_shop:
        inferred_shop = "네이버"

    # 상품명에서 대괄호 쇼핑몰명, 가격/배송비 괄호, 노이즈 제거 후 핵심 모델/상품명 추출
    clean_q = re.sub(r"^\s*\[[^\]]+\]\s*", "", raw_title or "").strip()
    clean_q = re.sub(r"^\s*\[[^\]]+\]\s*", "", clean_q).strip()
    clean_q = re.sub(r"\([^)]*(?:원|무료|무배|배송|쿠폰|할인|적립|최대|\$|달러|불|유로|€)[^)]*\)", " ", clean_q)
    clean_q = re.sub(r"(?:\$\s*[0-9,.]+|[0-9,.]+\s*(?:원|만원|만|달러|불|유로|€)|무료배송|무배|택배비|특가|할인|핫딜|끌올|역대급)", " ", clean_q, flags=re.IGNORECASE)
    clean_q = re.sub(r"[/|·~()]+", " ", clean_q)
    clean_q = re.sub(r"\s+", " ", clean_q).strip(" -.,()")
    if len(clean_q) < 2:
        clean_q = (raw_title or "").strip()
    if len(clean_q) < 2:
        return ""
    tokens = clean_q.split()
    query_str = " ".join(tokens[:6])
    q_enc = urllib.parse.quote(query_str)

    mall_search_templates = {
        "G마켓": f"https://www.gmarket.co.kr/n/search?keyword={q_enc}",
        "지마켓": f"https://www.gmarket.co.kr/n/search?keyword={q_enc}",
        "옥션": f"https://browse.auction.co.kr/search?keyword={q_enc}",
        "11번가": f"https://search.11st.co.kr/pc/total-search?kwd={q_enc}",
        "쿠팡": f"https://www.coupang.com/np/search?q={q_enc}",
        "롯데온": f"https://www.lotteon.com/search/search/search.ecn?render=search&platform=pc&q={q_enc}",
        "네이버": f"https://search.shopping.naver.com/search/all?query={q_enc}",
        "SSG": f"https://www.ssg.com/search.ssg?target=all&query={q_enc}",
        "신세계몰": f"https://www.ssg.com/search.ssg?target=all&query={q_enc}",
        "이마트몰": f"https://emart.ssg.com/search.ssg?target=all&query={q_enc}",
        "오늘의집": f"https://ohou.se/productions/feed?query={q_enc}",
        "무신사": f"https://www.musinsa.com/search/goods?keyword={q_enc}",
        "29CM": f"https://search.29cm.co.kr/?keyword={q_enc}",
        "지그재그": f"https://zigzag.kr/search?keyword={q_enc}",
        "KREAM": f"https://kream.co.kr/search?keyword={q_enc}",
        "톡딜": f"https://store.kakao.com/search/result/product?q={q_enc}",
        "카카오": f"https://store.kakao.com/search/result/product?q={q_enc}",
        "알리익스프레스": f"https://ko.aliexpress.com/w/wholesale-{urllib.parse.quote(query_str.replace(' ', '-'))}.html?SearchText={q_enc}",
        "알리": f"https://ko.aliexpress.com/w/wholesale-{urllib.parse.quote(query_str.replace(' ', '-'))}.html?SearchText={q_enc}",
        "우체국쇼핑": f"https://mall.epost.go.kr/fo/search/search.do?searchTerm={q_enc}",
        "하이마트": f"https://www.e-himart.co.kr/app/search/totalSearch?query={q_enc}",
        "CJ온스타일": f"https://display.cjonstyle.com/p/search/searchAllList?k={q_enc}",
        "GS샵": f"https://www.gsshop.com/shop/search/main.gs?tq={q_enc}",
        "현대Hmall": f"https://www.hmall.com/md/pde/search.do?searchTerm={q_enc}",
        "컬리": f"https://www.kurly.com/search?sword={q_enc}",
        "아마존": f"https://www.amazon.com/s?k={q_enc}",
        "큐텐": f"https://www.qoo10.com/s/?keyword={q_enc}",
        "스팀": f"https://store.steampowered.com/search/?term={q_enc}",
        "올리브영": f"https://www.oliveyoung.co.kr/store/search/getSearchMain.do?query={q_enc}",
        "다이소몰": f"https://www.daisomall.co.kr/ds/search?searchTerm={q_enc}",
        "홈플러스": f"https://front.homeplus.co.kr/search?entry=direct&keyword={q_enc}",
        "LF몰": f"https://www.lfmall.co.kr/app/search/letSearch?searchTerm={q_enc}",
        "하프클럽": f"https://www.halfclub.com/search?keyword={q_enc}",
        "보리보리": f"https://www.boribori.co.kr/search?keyword={q_enc}",
        "SSG닷컴": f"https://www.ssg.com/search.ssg?target=all&query={q_enc}",
        "크림": f"https://kream.co.kr/search?keyword={q_enc}",
        "컴퓨존": f"https://www.compuzone.co.kr/search/search.htm?Seargbl=1&SearchProductKey={q_enc}",
        "탁탁몰": "https://www.tagtag.co.kr/",
        "타오바오": f"https://s.taobao.com/search?q={q_enc}",
        "에픽게임즈": f"https://store.epicgames.com/ko/browse?q={q_enc}",
        "커버낫": f"https://covernat.net/product/search.html?keyword={q_enc}",
        "테무": f"https://www.temu.com/search_result.html?search_key={q_enc}",
    }
    from backend.core.url_utils import apply_affiliate_monetization
    target_search_url = mall_search_templates.get(inferred_shop, f"https://search.shopping.naver.com/search/all?query={q_enc}")
    return apply_affiliate_monetization(target_search_url)


def get_deal_sources(
    deal,
    comp_name,
    parsed_price_int,
    db=None,
    content_html_override: Optional[str] = None,
    currency_override: Optional[str] = None,
):
    deal_sources = []
    currency = currency_override or getattr(deal, 'currency', 'KRW') or 'KRW'
    # defer(content_html) 상태인 ORM 객체에서 SQLAlchemy N+1 lazy load가 발동하지 않도록 적재된 값만 읽음
    content_html = (
        content_html_override
        if content_html_override is not None
        else _safe_loaded_attr(deal, "content_html", "")
    )
    deal_id = getattr(deal, 'id', None)
    
    # 모음전/다중 딜 여부 판별
    is_multi = False
    ecommerce_link = getattr(deal, 'ecommerce_link', None)
    post_link = getattr(deal, 'post_link', None)
    title = getattr(deal, 'title', '') or ''
    ai_summary = getattr(deal, 'ai_summary', '') or ''
    shop_name = getattr(deal, 'shop_name', '') or ''
    
    # 핫딜 복원 실패 시의 동일 주소 매칭 조건은 모음전 판단에서 배제
    if (title and ("," in title or "모음" in title or "선택" in title or "외/" in title)) or ("[모음전]" in ai_summary):
        is_multi = True
        
    cleaned_ecom_url = get_clean_ecommerce_url(deal.ecommerce_link or deal.post_link or "", content_html, deal_id, db, is_multi_item=is_multi)
    if not cleaned_ecom_url:
        cleaned_ecom_url = _build_direct_mall_search_url(shop_name, title, post_link or "", comp_name)

    if hasattr(deal, 'options_data') and deal.options_data:
        try:
            options = json.loads(deal.options_data)
            for opt in options:
                opt_url = opt.get("url")
                opt_name = opt.get("name", "옵션")
                if opt_url:
                    opt_ecom_url = get_clean_ecommerce_url(opt_url, "", deal_id, None, is_multi_item=False)
                else:
                    opt_ecom_url = ""
                if not opt_ecom_url:
                    opt_ecom_url = _build_direct_mall_search_url(shop_name, f"[{shop_name}] {opt_name}" if shop_name else opt_name, post_link or "", comp_name) or cleaned_ecom_url
                raw_subs = opt.get("sub_urls") or opt.get("urls") or []
                clean_subs = [get_clean_ecommerce_url(su, "", deal_id, None, is_multi_item=False) or su for su in raw_subs if su]
                deal_sources.append({
                    "site_name": f"{comp_name} - {opt_name}",
                    "post_url": deal.post_link or "",
                    "ecommerce_url": opt_ecom_url,
                    "price": int(opt.get("price", parsed_price_int)),
                    "currency": opt.get("currency", currency),
                    "code": opt.get("code") or "",
                    "image_url": opt.get("image_url") or "",
                    "sub_urls": clean_subs,
                })
        except Exception:
            deal_sources.append({
                "site_name": comp_name, 
                "post_url": deal.post_link or "",
                "ecommerce_url": cleaned_ecom_url,
                "price": parsed_price_int,
                "currency": currency,
                "code": "",
                "image_url": getattr(deal, 'image_url', '') or "",
                "sub_urls": [],
            })
    else:
        deal_sources.append({
            "site_name": comp_name, 
            "post_url": deal.post_link or "",
            "ecommerce_url": cleaned_ecom_url,
            "price": parsed_price_int,
            "currency": currency,
            "code": "",
            "image_url": getattr(deal, 'image_url', '') or "",
            "sub_urls": [],
        })
        
    # [백그라운드 병합 딜(merged_communities) 소스 복원 로직 추가 - 인메모리 캐시로 N+1 DB 쿼리 완전 제거]
    if hasattr(deal, 'merged_communities') and deal.merged_communities:
        for merge_item in deal.merged_communities.split(','):
            merge_item = merge_item.strip()
            if not merge_item: continue
            
            parts = merge_item.split("::")
            try:
                cid = int(parts[0])
                merge_post_url = parts[1] if len(parts) > 1 else deal.post_link
            except (ValueError, TypeError) as exc:
                logger.debug(f"Invalid merged_communities entry {merge_item!r}: {exc}")
                continue
            
            m_name = get_community_display_name_by_id(cid, db)
            if m_name:
                existing_src = next((s for s in deal_sources if s.get("site_name") == m_name), None)
                if existing_src is not None:
                    if merge_post_url:
                        existing_src["post_url"] = merge_post_url
                else:
                    deal_sources.append({
                        "site_name": m_name,
                        "post_url": merge_post_url or "",
                        "ecommerce_url": cleaned_ecom_url,
                        "price": parsed_price_int,
                        "currency": currency,
                        "code": "",
                        "image_url": getattr(deal, 'image_url', '') or "",
                        "sub_urls": [],
                    })
    
    return deal_sources

BASE_URL = os.getenv("BASE_URL", "http://10.0.2.2:8080")

@router.get("/scraper-stats")
def get_scraper_stats():
    import os
    stats_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scraper_stats.json")
    if os.path.exists(stats_file):
        try:
            with open(stats_file, 'r', encoding='utf-8') as f:
                return json.load(f)
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning(f"Failed to load scraper_stats.json: {exc}")
            return {}
    return {}

@router.get("/proxy-image")
async def proxy_image(
    url: str,
    w: Optional[int] = None,
    width: Optional[int] = None,
    v: Optional[str] = None,
):
    from backend.main import proxy_image as main_proxy_image
    return await main_proxy_image(url=url, w=w, width=width, v=v)

_CLEAN_ECOM_URL_CACHE: OrderedDict = OrderedDict()
_CLEAN_ECOM_URL_CACHE_MAX = 4096
_CLEAN_ECOM_URL_LOCK = threading.Lock()


def _ecom_url_cache_env_sig() -> tuple:
    return (
        os.getenv("AFFILIATE_COUPANG_LPTAG", ""),
        os.getenv("COUPANG_PARTNERS_ID", ""),
        os.getenv("COUPANG_SUB_ID", ""),
        os.getenv("COUPANG_ACCESS_KEY", ""),
        os.getenv("AFFILIATE_ALIEXPRESS_ID", ""),
        os.getenv("ALIEXPRESS_TRACKING_ID", ""),
        os.getenv("AFFILIATE_LINKPRICE_ID", ""),
        os.getenv("LINKPRICE_AFFILIATE_ID", ""),
    )


def get_clean_ecommerce_url(url: str, content_html: str = "", deal_id: int = None, db = None, is_multi_item: bool = False) -> str:
    if not url:
        return ""
    cache_key = None
    if not content_html:
        cache_key = (str(url), bool(is_multi_item), _ecom_url_cache_env_sig())
        with _CLEAN_ECOM_URL_LOCK:
            cached = _CLEAN_ECOM_URL_CACHE.get(cache_key)
            if cached is not None:
                _CLEAN_ECOM_URL_CACHE.move_to_end(cache_key)
                return cached
    res = _compute_clean_ecommerce_url(url, content_html=content_html, deal_id=deal_id, db=db, is_multi_item=is_multi_item)
    if cache_key is not None:
        with _CLEAN_ECOM_URL_LOCK:
            _CLEAN_ECOM_URL_CACHE[cache_key] = res
            if len(_CLEAN_ECOM_URL_CACHE) > _CLEAN_ECOM_URL_CACHE_MAX:
                _CLEAN_ECOM_URL_CACHE.popitem(last=False)
    return res


def _compute_clean_ecommerce_url(url: str, content_html: str = "", deal_id: int = None, db = None, is_multi_item: bool = False) -> str:
    if not url:
        return ""
    url = re.split(r'[가-힣\s"\'<>]|(?:\$\d)', str(url).strip())[0].strip().rstrip(".,;)>]")
    if not url:
        return ""
        
    # [보증 가드]: 이미 진짜 쇼핑몰 아웃링크로 온전하게 변환/복원 완료된 주소라면 커뮤니티 경유 디코딩을 생략함
    if url.startswith("http") and not any(x in url.lower() for x in ["ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "quasarzone.com", "ruliweb.com", "ruliweb.daum.net", "bbasak.com", "clien.net"]):
        from backend.core.url_utils import apply_affiliate_monetization
        return apply_affiliate_monetization(url)
    
    if is_multi_item:
        # 모음전이거나 여러 개가 묶인 상품이라도 이미 외부 쇼핑몰 주소라면 그대로 반환하고, 커뮤니티 글 주소라면 본문에서 첫 번째 쇼핑몰 링크를 추출 시도!
        if any(x in url.lower() for x in ["ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "quasarzone.com", "ruliweb.com", "ruliweb.daum.net", "bbasak.com", "clien.net"]) and not any(k in url.lower() for k in ["s.ppomppu.co.kr", "out_link.php", "view_gourl_show.php", "link.php", "popup/outlink"]):
            if not content_html:
                return ""
        else:
            return url
    
    import urllib.parse
    import base64
    url_str = str(url).strip()
    
    # 0. 즉시 Base64 디코딩 시도 (만약 이미 s.ppomppu.co.kr 이거나 target 파라미터가 들어있는 리디렉션 주소인 경우)
    if "s.ppomppu.co.kr" in url_str:
        match = re.search(r'target=([^&]+)', url_str)
        if match:
            try:
                base64_str = match.group(1)
                missing_padding = len(base64_str) % 4
                if missing_padding:
                    base64_str += '=' * (4 - missing_padding)
                decoded = base64.b64decode(base64_str).decode('utf-8')
                if decoded.startswith("http"):
                    url_str = decoded
            except Exception as e:
                logger.error(f"Failed to decode base64 target in url_str: {e}")

    # [백필 방어막]: 만약 들어온 URL이 뽐뿌 글 상세주소이거나 펨코 글 상세주소라면 진짜 아웃링크를 복원한다.
    is_fmkorea_post = "fmkorea.com/" in url_str or "fmkorea.org/" in url_str
    is_ppomppu_post = "ppomppu.co.kr/" in url_str
    
    if (is_fmkorea_post and "link.php" not in url_str) or (is_ppomppu_post and "out_link.php" not in url_str and "view_gourl_show.php" not in url_str and "s.ppomppu.co.kr" not in url_str):
        # 뽐뿌/펨코 상세 글 주소인 경우 진짜 쇼핑몰 링크 복원 시도
        real_url = None
        
        # 1차: content_html 내에서 쇼핑몰 도메인 및 s.ppomppu.co.kr 찾기
        if content_html:
            patterns = [
                r'href="([^"]*?s\.ppomppu\.co\.kr[^"]*?)"',
                r'https?://[^\s"\'<>]*?(?:gmarket|auction|11st|coupang|coupa\.ng|naver|ssg|lotteon|tmon|wemakeprice|aliexpress|taobao)[^\s"\'<>]*'
            ]
            for pat in patterns:
                matches = re.findall(pat, content_html, re.IGNORECASE)
                if matches:
                    for m in matches:
                        if "s.ppomppu.co.kr" in m:
                            t_match = re.search(r'target=([^&]+)', m)
                            if t_match:
                                try:
                                    b64 = t_match.group(1)
                                    missing_padding = len(b64) % 4
                                    if missing_padding:
                                        b64 += '=' * (4 - missing_padding)
                                    decoded = base64.b64decode(b64).decode('utf-8')
                                    if decoded.startswith("http"):
                                        real_url = decoded
                                        break
                                except (ValueError, UnicodeDecodeError) as exc:
                                    logger.debug(f"s.ppomppu base64 decode skipped: {exc}")
                        elif "fmkorea" not in m and "ppomppu" not in m:
                            if "naver.com" in m and any(x in m for x in ["saedu", "log", "adbiz", "ad.naver", "click.link"]):
                                continue
                            real_url = m
                            break
                if real_url:
                    break
                    
        # 2차 실시간 파싱(requests.get) 및 GET 중 DB UPDATE/commit은 SQLite 쓰기 락 경합(검색/피드 지연)을 유발하므로
        # 복원된 URL만 메모리에서 즉시 반환합니다. (영구 백필은 수집기 AggregatorService에서 수행)
        if real_url:
            url_str = real_url

    # 1. 펨코 (link.fmkorea.org/link.php?url=...)
    if "fmkorea.org/link.php" in url_str or "fmkorea.com/link.php" in url_str:
        match = re.search(r'url=([^&]+)', url_str)
        if match:
            try:
                decoded = urllib.parse.unquote(match.group(1))
                if decoded.startswith("http"):
                    url_str = decoded
            except Exception as exc:
                logger.debug(f"fmkorea link unquote skipped: {exc}")
                
    # 2. 뽐뿌 (m.ppomppu.co.kr/new/out_link.php?link=...)
    elif "out_link.php" in url_str:
        match = re.search(r'link=([^&]+)', url_str)
        if match:
            try:
                decoded = urllib.parse.unquote(match.group(1))
                if decoded.startswith("http"):
                    url_str = decoded
            except Exception as exc:
                logger.debug(f"ppomppu out_link unquote skipped: {exc}")
                
    # 3. 뽐뿌 PC (view_gourl_show.php?gourl=...)
    elif "view_gourl_show.php" in url_str:
        match = re.search(r'gourl=([^&]+)', url_str)
        if match:
            try:
                decoded = urllib.parse.unquote(match.group(1))
                if decoded.startswith("http"):
                    url_str = decoded
            except Exception as exc:
                logger.debug(f"ppomppu view_gourl unquote skipped: {exc}")
                
    # 4. 루리웹 (ruliweb.com/link.php?ol=...)
    elif "ruliweb.com/link.php" in url_str or "ruliweb.daum.net/link.php" in url_str:
        match = re.search(r'ol=([^&]+)', url_str)
        if match:
            try:
                decoded = urllib.parse.unquote(match.group(1))
                if decoded.startswith("http"):
                    url_str = decoded
            except Exception as exc:
                logger.debug(f"ruliweb link unquote skipped: {exc}")
                
    # 5. 클리앙 (clien.net/service/popup/outlink?url=...)
    elif "popup/outlink" in url_str:
        match = re.search(r'url=([^&]+)', url_str)
        if match:
            try:
                decoded = urllib.parse.unquote(match.group(1))
                if decoded.startswith("http"):
                    url_str = decoded
            except Exception as exc:
                logger.debug(f"clien outlink unquote skipped: {exc}")
                
    # 모바일 도메인을 표준 PC/표준 주소 도메인으로 규격 다듬기
    url_str = url_str.replace("m.gmarket.co.kr", "item.gmarket.co.kr")
    url_str = url_str.replace("m.auction.co.kr", "itempage3.auction.co.kr")
    url_str = url_str.replace("m.11st.co.kr", "www.11st.co.kr")
    
    # [대표님이 명시한 특수 규격 보정]: G마켓 모바일 혹은 간소화 주소가 넘어올 때 Item?goodscode 로 규격 강제 일원화!
    # 예: http://item.gmarket.co.kr/Item?goodscode=4701840459
    if "gmarket.co.kr" in url_str:
        match = re.search(r'goodscode=([0-9]+)', url_str, re.IGNORECASE)
        if match:
            url_str = f"https://item.gmarket.co.kr/Item?goodscode={match.group(1)}"
            
    if "auction.co.kr" in url_str:
        match = re.search(r'itemno=([a-zA-Z0-9]+)', url_str, re.IGNORECASE)
        if match:
            url_str = f"https://itempage3.auction.co.kr/DetailView.aspx?itemno={match.group(1)}"
            
    # 모든 /./ 오염 주소를 원천 격파
    url_str = url_str.replace("/./", "/")

    if url_str.startswith("http://"):
        url_str = "https://" + url_str[7:]
    elif url_str.startswith("//"):
        url_str = "https:" + url_str
        
    # [아웃링크 오염 원천 봉쇄 가드]: 복원에 최종 실패하여 여전히 커뮤니티 글 주소라면 빈 문자열 반환
    if any(x in url_str for x in ["ppomppu.co.kr", "fmkorea.com", "fmkorea.org", "quasarzone.com", "ruliweb.com", "bbasak.com", "clien.net"]):
        return ""

    from backend.core.url_utils import apply_affiliate_monetization
    return apply_affiliate_monetization(url_str)


def extract_price(price_str):
    if not price_str: return 0
    price_str_str = str(price_str)
    # 제휴 링크나 URL 내의 상품번호 숫자가 가격으로 오역되는 현상 영구 차단
    if "http://" in price_str_str or "https://" in price_str_str or "products/" in price_str_str:
        return 0
    nums = re.findall(r'\d+', price_str_str)
    if nums:
        parsed_val = int(''.join(nums))
        # 천오백만 원(15,000,000원) 이상의 비현실적 가격은 크롤링 오류이므로 0(정보 없음 / 본문 참조)으로 강제 정화
        if parsed_val > 15000000:
            return 0
        return parsed_val
    return 0


def _to_krw_equiv(price, currency: Optional[str] = "KRW") -> float:
    from backend.core.deal_normalizer import to_krw_equivalent
    return to_krw_equivalent(price, currency)


def _normalize_shipping_fee(raw_fee: Optional[str], title_hint: str = "") -> str:
    from backend.core.deal_normalizer import normalize_shipping_fee
    return normalize_shipping_fee(raw_fee, title_hint=title_hint)


def _resolve_canonical_shop_name(deal, ecom_url: str = "") -> str:
    from backend.core.deal_normalizer import resolve_canonical_shop_name
    return resolve_canonical_shop_name(
        raw_title=getattr(deal, "title", "") or "",
        ecommerce_url=ecom_url or getattr(deal, "ecommerce_link", "") or "",
        current_shop=getattr(deal, "shop_name", "") or "",
        post_url=getattr(deal, "post_link", "") or "",
        default_empty="",
    )


def _serialize_options_data(raw_options, deal_id: Optional[int] = None) -> list[dict]:
    if not raw_options:
        return []
    try:
        parsed = json.loads(raw_options) if isinstance(raw_options, str) else raw_options
    except Exception:
        return []
    if not isinstance(parsed, list):
        return []
    serialized: list[dict] = []
    for opt in parsed:
        if not isinstance(opt, dict):
            continue
        item = dict(opt)
        raw_u = (item.get("url") or "").strip()
        if raw_u:
            clean_u = get_clean_ecommerce_url(raw_u, "", deal_id, None, is_multi_item=False)
            item["url"] = clean_u or raw_u
        else:
            item["url"] = ""
        raw_subs = item.get("sub_urls") or item.get("urls") or []
        if isinstance(raw_subs, list):
            item["sub_urls"] = [
                get_clean_ecommerce_url(su, "", deal_id, None, is_multi_item=False) or su
                for su in raw_subs
                if su
            ]
        else:
            item["sub_urls"] = []
        serialized.append(item)
    return serialized


def _compute_total_price(price_int: int, currency: Optional[str], shipping_fee_str: str) -> int:
    if not price_int or price_int <= 0:
        return 0
    curr = (currency or "KRW").strip().upper()
    sf = str(shipping_fee_str or "").strip()
    if curr == "KRW" and not any(sym in sf for sym in ("$", "€", "¥", "￥", "달러", "위안", "엔", "유로")):
        return price_int + extract_shipping_fee(sf)
    if curr == "USD" and ("$" in sf or "달러" in sf):
        return price_int + extract_shipping_fee(sf)
    return price_int


def resolve_deal_price_and_currency(deal) -> tuple[int, str]:
    from backend.core.deal_normalizer import (
        extract_body_fallback_price,
        infer_currency_from_deal_context,
        is_collection_dummy_price,
        is_micro_point_deal,
        parse_title_price_and_shipping,
    )

    parsed_price = extract_price(getattr(deal, 'price', 0))
    currency = (getattr(deal, 'currency', 'KRW') or 'KRW').strip().upper()
    title = getattr(deal, 'title', '') or ''
    body_html = _safe_loaded_attr(deal, 'content_html', '')
    ecom_url = getattr(deal, 'ecommerce_link', '') or ''

    if parsed_price > 0 and is_collection_dummy_price(parsed_price, title, currency):
        return 0, "KRW"

    tp, tc, _, is_free = parse_title_price_and_shipping(title) if title else (0, "KRW", "", False)
    if is_free:
        return 0, "KRW"

    if parsed_price > 0:
        if currency == "KRW" and 0 < parsed_price < 100 and not is_micro_point_deal(parsed_price, title):
            if tp > 0:
                return tp, tc
            if body_html:
                fb_p, fb_c = extract_body_fallback_price(body_html, title)
                if (fb_c == "KRW" and fb_p >= 100) or (fb_c != "KRW" and fb_p > 0):
                    return fb_p, fb_c
            return 0, "KRW"

        if currency == "KRW" and 1 <= parsed_price < 5000 and body_html:
            m_won_dec = re.search(
                r"[₩￦]\s*([0-9]{1,4})\.([0-9]{2})(?=\s*(?:\(KRW\)|\(|$|\s|<))",
                body_html,
                re.IGNORECASE,
            )
            overseas_ctx = (title + " " + ecom_url).lower()
            is_overseas_hint = any(
                tok in overseas_ctx
                for tok in ("[알리]", "[아마존]", "aliexpress.", "amazon.", "ebay.", "temu.", "qoo10.")
            )
            if m_won_dec and is_overseas_hint and abs(int(m_won_dec.group(1)) - parsed_price) <= 1:
                return int(m_won_dec.group(1)) * 100 + int(m_won_dec.group(2)), "USD"

        if currency == "KRW" and tp == 0 and body_html:
            currency = infer_currency_from_deal_context(
                raw_title=title,
                raw_price_int=parsed_price,
                current_currency=currency,
                content_html=body_html,
                ecommerce_url=ecom_url,
            )
        return parsed_price, currency

    if tp > 0:
        return tp, tc
    return 0, currency


def extend_unique_sources(existing_sources: list, new_sources: list):
    for ns in new_sources:
        ns_site = ns.get("site_name") or ""
        if " - " in ns_site:
            opt_name = re.sub(r'\s+', ' ', ns_site.split(" - ", 1)[1].strip()).lower()
            if any(
                " - " in (es.get("site_name") or "")
                and re.sub(r'\s+', ' ', (es.get("site_name") or "").split(" - ", 1)[1].strip()).lower() == opt_name
                for es in existing_sources
            ):
                continue
        existing_sources.append(ns)

def extract_shipping_fee(fee_str):
    if not fee_str:
        return 0
    fee_str_str = str(fee_str).strip()
    if len(fee_str_str) > 25 or any(x in fee_str_str for x in ['무료', '무배', '무료배송', '포함', '정보 없음', '확인 필요']):
        return 0
    if '$' in fee_str_str or '달러' in fee_str_str:
        m_usd = re.search(r'([0-9]+(?:\.[0-9]+)?)', fee_str_str)
        return round(float(m_usd.group(1)) * 100) if m_usd else 0
    m_won = re.search(r'([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,6})', fee_str_str)
    if m_won:
        val = int(m_won.group(1).replace(',', ''))
        return val if val <= 100000 else 0
    return 0


_BENEFIT_TAG_RULES = [
    (["삼성카드", "삼카"], "삼성카드"),
    (["신한카드", "신카"], "신한카드"),
    (["국민카드", "kb카드", "국민kb"], "국민카드"),
    (["현대카드", "현카"], "현대카드"),
    (["농협카드", "nh카드"], "농협카드"),
    (["롯데카드", "롯카"], "롯데카드"),
    (["하나카드"], "하나카드"),
    (["우리카드"], "우리카드"),
    (["비씨카드", "bc카드"], "BC카드"),
    (["카드할인", "카드결제", "청구할인"], "카드할인"),
    (["네이버페이", "네페"], "네이버페이"),
    (["카카오페이", "카페"], "카카오페이"),
    (["토스페이", "토스결제"], "토스페이"),
    (["페이코", "payco"], "페이코"),
    (["와우회원", "와우할인", "로켓와우"], "와우할인"),
    (["우주패스"], "우주패스"),
    (["네멤", "네이버멤버십", "플러스멤버십"], "네이버멤버십"),
    (["쿠폰", "중복쿠폰", "할인코드", "프로모션코드"], "쿠폰적용"),
    (["사은품", "증정", "+증정"], "사은품"),
    (["선착순", "타임딜", "한정수량"], "타임특가"),
    (["관부가세", "직구", "초이스데이"], "해외직구"),
]

_BENEFIT_TAGS_MEMO: dict[str, list[str]] = {}
_NORM_NAME_MEMO: dict[str, Optional[str]] = {}
_NORM_URL_MEMO: dict[str, Optional[str]] = {}

def extract_deal_benefit_tags(title: str, content_html: str = "") -> list[str]:
    """제목 및 본문에서 결제 수단/멤버십/쿠폰 혜택을 자동 추출하여 최대 3개 태그로 반환"""
    cache_k = f"{title or ''}|{(content_html or '')[:120]}"
    if cache_k in _BENEFIT_TAGS_MEMO:
        return _BENEFIT_TAGS_MEMO[cache_k]
    text = f"{title or ''} {(content_html or '')[:600]}".lower()
    tags = []
    for keywords, badge in _BENEFIT_TAG_RULES:
        if any(kw in text for kw in keywords):
            if badge not in tags:
                tags.append(badge)
            if len(tags) >= 3:
                break
    if len(_BENEFIT_TAGS_MEMO) > 4000:
        _BENEFIT_TAGS_MEMO.clear()
    _BENEFIT_TAGS_MEMO[cache_k] = tags
    return tags



def normalize_units(text: str) -> str:
    t = text.lower()
    t = re.sub(r'(\d+)\s*\+\s*(\d+)', r'\1 \2', t)
    t = re.sub(r'(\d+)\s*기가바이트', r'\1gb', t)
    t = re.sub(r'(\d+)\s*기가', r'\1gb', t)
    t = re.sub(r'(\d+)\s*tb', r'\1000gb', t)
    t = re.sub(r'(\d+)\s*gb', r'\1gb', t)
    t = re.sub(r'(\d+\.\d+)\s*kg', lambda m: str(int(float(m.group(1)) * 1000)) + 'g', t)
    t = re.sub(r'(\d+\.\d+)\s*l', lambda m: str(int(float(m.group(1)) * 1000)) + 'ml', t)
    t = re.sub(r'(?<!\d)(\d+)\s*kg', lambda m: str(int(m.group(1)) * 1000) + 'g', t)
    t = re.sub(r'(?<!\d)(\d+)\s*l', lambda m: str(int(m.group(1)) * 1000) + 'ml', t)
    t = re.sub(r'(\d+)\s*킬로그램', r'\1kg', t)
    return t
def get_normalized_base_name(deal):
    raw_title = getattr(deal, 'title', None)
    if not raw_title:
        return None
    if raw_title in _NORM_NAME_MEMO:
        return _NORM_NAME_MEMO[raw_title]
    clean = raw_title
    clean = re.sub(r'\([^)]*(?:달러|배송|무배|무료|체감|할인|쿠폰|최저가|특가)[^)]*\)', '', clean).strip()
    clean = re.sub(r'\s*\([\d,\s/]+\)\s*$', '', clean).strip()
    clean = re.sub(r'\[[^\]]*\]', '', clean).strip()
    clean = re.sub(r'\d+(?:,\d+)*\s*원', '', clean)
    clean = normalize_units(clean)
    clean = re.sub(r'아이(?:스)?\s*아메리카노', '아이스아메리카노', clean)
    base_str = re.sub(r'[^가-힣a-zA-Z0-9]', ' ', clean)
    malls = r'(쿠팡|g마켓|지마켓|11번가|십일번가|옥션|위메프|티몬|ssg|쓱|이마트|홈플러스|롯데마트|하이마트|오늘의집|집꾸미기|알리|알리익스프레스|테무|큐텐|아마존|무신사|올리브영|에이블리|지그재그|아이디어스)'
    modifiers = r'(역대급|대박|미친|오늘만|단하루|품절|임박|로켓|스마일|새벽|무료|배송|무배|체감|추가|할인|반값|특가|쿠폰|카드|핫딜|최저가|이벤트|세일|오픈|론칭|한정|수량|마감|긴급|선착순|단독|예약|할인가|공식|인증|행사|사은품|증정|패키지|초특가|투데이|반짝|게릴라|종결|우주패스|빅스마일데이|광군제|블프|블랙프라이데이|크리스마스|명절|설날|추석|옵션|행상\d+종|행상\d+가지|재입고|출시|추천|종료)'
    clean = re.sub(malls, '', base_str, flags=re.IGNORECASE)
    clean = re.sub(modifiers, '', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\s+', ' ', clean).strip()
    res = clean if len(clean) > 1 else base_str.strip()
    if len(_NORM_NAME_MEMO) > 4000:
        _NORM_NAME_MEMO.clear()
    _NORM_NAME_MEMO[raw_title] = res
    return res
def get_normalized_url(deal):
    raw_url = getattr(deal, 'ecommerce_link', None) or ""
    if not raw_url:
        return None
    if raw_url in _NORM_URL_MEMO:
        return _NORM_URL_MEMO[raw_url]
    from backend.core.url_utils import extract_canonical_product_key, normalize_url
    canonical_key = extract_canonical_product_key(raw_url)
    if canonical_key:
        res = canonical_key
    elif "link.php" in raw_url or "out.php" in raw_url:
        res = None
    else:
        res = normalize_url(raw_url)
    if len(_NORM_URL_MEMO) > 4000:
        _NORM_URL_MEMO.clear()
    _NORM_URL_MEMO[raw_url] = res
    return res

class DSU:
    def __init__(self):
        self.parent = {}
    def find(self, i):
        if self.parent.setdefault(i, i) != i:
            self.parent[i] = self.find(self.parent[i])
        return self.parent[i]
    def union(self, i, j):
        root_i = self.find(i)
        root_j = self.find(j)
        if root_i != root_j:
            self.parent[root_i] = root_j

def has_quantity_conflict(words_a, words_b):
    """
    용량(g/kg, ml/l), 수량(개/입/캔/병/팩/봉/포/매/박스), 전력/용량(w, gb/tb), 화면크기(인치/호)가
    양쪽에 모두 존재하는데 수치가 다르면 절대 병합하지 않도록 방어합니다.
    """
    def get_quants(words):
        q = {}
        joined = words.lower() if isinstance(words, str) else " ".join(words).lower()
        # 복합 수량(예: 500ml x 24캔, 200g*10개)까지 모두 개별 단위로 정밀 추출
        matches = re.findall(
            r'(\d+(?:\.\d+)?)\s*(kg|g|ml|l|gb|tb|w|인치|inch|개입|개|입|캔|병|팩|봉|포|매|판|박스|세트|롤|정|캡슐)(?![a-zA-Z])',
            joined
        )
        for val_str, unit in matches:
            val = float(val_str)
            if unit in ['개', '개입', '입', '판', '팩', '봉', '포', '세트', '매', '캔', '병', '박스', '롤', '정', '캡슐']:
                u_key = 'count'
            elif unit == 'kg':
                val *= 1000
                u_key = 'weight'
            elif unit == 'g':
                u_key = 'weight'
            elif unit == 'l':
                val *= 1000
                u_key = 'volume'
            elif unit == 'ml':
                u_key = 'volume'
            elif unit == 'tb':
                val *= 1000
                u_key = 'storage'
            elif unit == 'gb':
                u_key = 'storage'
            elif unit == 'w':
                u_key = 'watt'
            elif unit in ['인치', 'inch']:
                u_key = 'inch'
            else:
                u_key = unit
            q.setdefault(u_key, set()).add(int(round(val)))
        return q
        
    qa = get_quants(words_a)
    qb = get_quants(words_b)
    for unit, vals_a in qa.items():
        if unit in qb:
            if not vals_a.intersection(qb[unit]):
                return True
    return False

def extract_true_model_codes(words) -> set[str]:
    if isinstance(words, str):
        words = set(re.split(r'[\s,\[\]\(\)/]+', words))
    codes = set()
    for w in words:
        w_low = w.lower().strip()
        if (len(w_low) >= 4
                and re.match(r'^[a-zA-Z0-9\-]+$', w_low)
                and any(c.isdigit() for c in w_low)
                and any(c.isascii() and c.isalpha() for c in w_low)
                and not re.match(r'^\d+(gb|tb|mb|kg|g|ml|l|w|v|a|hz|fps|pcs|ea|mah|ah|gsm|in1|p|구|미|쪽|봉|팩|개|캔)$', w_low)
                and not re.match(r'^\d+x\d+$', w_low)):
            codes.add(w_low.replace('-', ''))
    return codes

def has_model_conflict(words_a, words_b):
    if isinstance(words_a, str):
        words_a = set(re.split(r'[\s,\[\]\(\)/]+', words_a))
    if isinstance(words_b, str):
        words_b = set(re.split(r'[\s,\[\]\(\)/]+', words_b))

    codes_a = extract_true_model_codes(words_a)
    codes_b = extract_true_model_codes(words_b)

    if codes_a and codes_b:
        if not codes_a.intersection(codes_b):
            return True
        else:
            # 고유 제조사 모델코드(예: WIZ040, LS32FM701 등)가 명확히 일치하면 동일 제품으로 인정
            return False

    tier_tokens = {"pro", "max", "plus", "ultra", "mini", "se", "fe", "lite", "air", "ti", "super", "oled", "qled", "프로", "맥스", "플러스", "울트라", "미니", "라이트", "에어"}
    tiers_a = {w.lower() for w in words_a if w.lower() in tier_tokens}
    tiers_b = {w.lower() for w in words_b if w.lower() in tier_tokens}
    if tiers_a != tiers_b:
        return True

    return False

def has_brand_or_keyword_conflict(words_a, words_b):
    if isinstance(words_a, str):
        words_a = set(re.split(r'[\s,\[\]\(\)/]+', words_a))
    if isinstance(words_b, str):
        words_b = set(re.split(r'[\s,\[\]\(\)/]+', words_b))
    brands = {
        "펩시", "코카콜라", "코카", "칠성", "스프라이트", "가래떡", "만두", "피자", "치킨", "라면", "신라면", 
        "불닭", "햇반", "오뚜기밥", "아디다스", "나이키", "뉴발란스", "푸마", "울트라기어", "아이맥", "imac", 
        "맥북", "그램", "갤럭시", "아이폰", "아이패드", "에어팟", "버즈", "닌텐도", "플레이스테이션", "ps5", 
        "xbox", "엑스박스", "조청", "떡", "갑오징어", "오징어", "삼다수", "아이시스", "백산수", "스파클",
        "비비고", "하림", "마니커", "종가집", "동원", "오뚜기", "농심", "삼양", "청정원", "풀무원",
        "로지텍", "레이저", "레노버", "아수스", "asus", "msi", "기가바이트", "한성", "주연테크"
    }
    
    words_a_lower = {w.lower() for w in words_a}
    words_b_lower = {w.lower() for w in words_b}
    
    brands_a = {w for w in words_a_lower if w in brands or any(b in w for b in brands)}
    brands_b = {w for w in words_b_lower if w in brands or any(b in w for b in brands)}
    
    if brands_a and brands_b:
        has_overlap = False
        for ba in brands_a:
            for bb in brands_b:
                if ba in bb or bb in ba:
                    has_overlap = True
                    break
            if has_overlap:
                break
        if not has_overlap:
            return True
    return False

def has_price_conflict(
    price_a: int,
    price_b: int,
    curr_a: str = "KRW",
    curr_b: str = "KRW",
) -> bool:
    """
    통화 단위(KRW/USD/EUR/CNY/JPY)를 원화로 환산한 뒤 45% 이상 차이 나면 병합을 차단합니다.
    """
    from backend.core.deal_normalizer import has_currency_aware_price_conflict
    return has_currency_aware_price_conflict(price_a, price_b, curr_a, curr_b, threshold=0.45)

def build_dsu(deals, time_window_days=1):
    dsu = DSU()
    url_to_deal = {}
    model_to_deal = {}
    name_clusters = []
    token_to_cluster_idxs: dict[str, list[int]] = {}

    unit_or_generic_re = re.compile(
        r'^(?:\d+(?:\.\d+)?(?:kg|g|ml|l|gb|tb|mb|w|v|a|hz|mah|ah|gsm|in1|인치|inch|개입|개|입|캔|병|팩|봉|포|매|판|박스|세트|롤|정|캡슐|구|미|쪽|호|p|ea|pcs|년|개월|일|시간|원|만원|천원)?|국내산|국산|해외|정품|무료|무배|특가|할인|증정|사은품|단품|혼합|선택|외|모음|한정|출시|신제품|신모델|리뉴얼|끌올)$',
        re.IGNORECASE
    )

    def get_core_words(words_set: set[str]) -> set[str]:
        return {w.lower() for w in words_set if len(w.strip()) >= 2 and not unit_or_generic_re.match(w.strip())}

    for deal in deals:
        dsu.find(deal.id)
        n_url = get_normalized_url(deal)
        n_name = get_normalized_base_name(deal)
        deal_time = _to_utc_naive(deal.indexed_at)
        curr_price, curr_currency = resolve_deal_price_and_currency(deal)
        curr_model = (getattr(deal, 'model_code', None) or "").strip().lower()

        curr_words_set = set(n_name.split(' ')) if n_name else set()
        curr_core_set = get_core_words(curr_words_set)
        curr_sc = "".join(sorted(curr_core_set)) if curr_core_set else (n_name.replace(' ', '') if n_name else "")

        # 1단계: 쇼핑몰 고유 상품 키(Canonical Product Key) 또는 정규화 URL 기반 매칭
        if n_url and not any(x in n_url for x in ["ppomppu", "fmkorea", "ruliweb", "quasarzone", "clien", "bbasak"]):
            n_url_key = n_url
            if n_url_key in url_to_deal:
                for past_id, past_words_set, past_core_set, past_price, past_currency in url_to_deal[n_url_key]:
                    if curr_words_set and past_words_set:
                        intersection = len(curr_words_set.intersection(past_words_set))
                        union = len(curr_words_set.union(past_words_set))
                        core_inter = len(curr_core_set.intersection(past_core_set))

                        is_exact_id = any(
                            n_url_key.startswith(prefix)
                            for prefix in [
                                "gmarket_", "auction_", "11st_", "coupang_", "aliexpress_",
                                "naver_", "lotteon_", "ssg_", "musinsa_", "ohouse_", "kakao_", "himart_"
                            ]
                        )

                        if is_exact_id:
                            if (
                                not has_quantity_conflict(curr_words_set, past_words_set)
                                and not has_model_conflict(curr_words_set, past_words_set)
                                and not has_brand_or_keyword_conflict(curr_words_set, past_words_set)
                                and not has_price_conflict(curr_price, past_price, curr_currency, past_currency)
                            ):
                                if core_inter >= 1 and (union == 0 or (intersection / union) >= 0.25):
                                    dsu.union(deal.id, past_id)
                                    break
                        else:
                            if union > 0 and (intersection / union) >= 0.8 and core_inter >= 2:
                                if (
                                    not has_quantity_conflict(curr_words_set, past_words_set)
                                    and not has_model_conflict(curr_words_set, past_words_set)
                                    and not has_brand_or_keyword_conflict(curr_words_set, past_words_set)
                                    and not has_price_conflict(curr_price, past_price, curr_currency, past_currency)
                                ):
                                    dsu.union(deal.id, past_id)
                                    break
            url_to_deal.setdefault(n_url_key, []).append((deal.id, curr_words_set, curr_core_set, curr_price, curr_currency))

        # 2단계: 고유 제조사 모델코드 일치 시 교차 병합
        true_codes = extract_true_model_codes(curr_words_set)
        if curr_model and len(curr_model) >= 5:
            true_codes.update(extract_true_model_codes({curr_model}))
        for code_key in true_codes:
            if code_key in model_to_deal:
                for past_id, past_words_set, past_core_set, past_price, past_currency in model_to_deal[code_key]:
                    if (
                        len(curr_core_set.intersection(past_core_set)) >= 2
                        and not has_quantity_conflict(curr_words_set, past_words_set)
                        and not has_model_conflict(curr_words_set, past_words_set)
                        and not has_brand_or_keyword_conflict(curr_words_set, past_words_set)
                        and not has_price_conflict(curr_price, past_price, curr_currency, past_currency)
                    ):
                        dsu.union(deal.id, past_id)
                        break
            model_to_deal.setdefault(code_key, []).append((deal.id, curr_words_set, curr_core_set, curr_price, curr_currency))

        # 3단계: 정규화 상품명 및 핵심 토큰 유사도 기반 매칭 (역인덱스로 공통 핵심 토큰 보유 클러스터만 O(1) 후보 추출)
        if n_name and curr_core_set:
            words_set = curr_words_set
            matched = False
            cand_idxs = sorted({
                idx
                for cw in curr_core_set
                for idx in token_to_cluster_idxs.get(cw, ())
            })
            for idx in cand_idxs:
                cluster = name_clusters[idx]
                if curr_model and cluster.get('model_code') and curr_model != cluster['model_code']:
                    continue
                time_diff_ok = False
                if deal_time:
                    for c_time in cluster.get('times', []):
                        if c_time and abs((deal_time - c_time).total_seconds()) <= time_window_days * 24 * 3600:
                            time_diff_ok = True
                            break
                    if not cluster.get('times'):
                        time_diff_ok = True
                else:
                    time_diff_ok = True

                if not time_diff_ok:
                    continue

                core_inter = len(curr_core_set.intersection(cluster['core_words']))
                core_union = len(curr_core_set.union(cluster['core_words']))
                core_jaccard = core_inter / core_union if core_union > 0 else 0

                core_min_len = min(len(curr_core_set), len(cluster['core_words']))
                core_subset_ratio = core_inter / core_min_len if core_min_len > 0 else 0

                sc_match = bool(curr_sc and cluster.get('sc') and curr_sc == cluster['sc'] and core_inter >= 2)

                if (core_min_len == 1 and core_jaccard == 1.0) or (
                    core_inter >= 2 and (core_jaccard >= 0.68 or sc_match or (core_subset_ratio >= 0.85 and core_inter >= 3))
                ):
                    if (
                        not has_quantity_conflict(words_set, cluster['words'])
                        and not has_model_conflict(words_set, cluster['words'])
                        and not has_brand_or_keyword_conflict(words_set, cluster['words'])
                        and not has_price_conflict(
                            curr_price,
                            cluster.get('price', 0),
                            curr_currency,
                            cluster.get('currency', 'KRW'),
                        )
                    ):
                        dsu.union(deal.id, cluster['id'])
                        if curr_model and not cluster.get('model_code'):
                            cluster['model_code'] = curr_model
                        if deal_time:
                            cluster.setdefault('times', []).append(deal_time)
                        matched = True
                        break

            if not matched:
                new_idx = len(name_clusters)
                name_clusters.append({
                    'id': deal.id,
                    'words': words_set,
                    'core_words': curr_core_set,
                    'sc': curr_sc,
                    'model_code': curr_model,
                    'price': curr_price,
                    'currency': curr_currency,
                    'times': [deal_time] if deal_time else []
                })
                for cw in curr_core_set:
                    token_to_cluster_idxs.setdefault(cw, []).append(new_idx)

    return dsu

import time as _feed_time

# 자체 프리파싱(Pre-parsed) 인메모리 JSON API 캐시 (페이지 이동·탭 전환 시 재파싱 없이 0.5ms 내 즉시 응답)
_TOP_DEALS_API_CACHE = {"ts": 0.0, "max_id": 0, "bind_id": 0, "data": None}
_HOT_DEALS_API_CACHE = {}  # key: (bind_id, category, keyword, platform, sort, only_hot, include_closed) -> {"ts": float, "max_id": int, "grouped": list, ...}
_HOT_DEALS_CURSOR_CACHE: dict[tuple, dict] = {}
_HOT_DEALS_CACHE_LOCK = threading.Lock()
_FEED_CACHE_TTL_SEC = 45.0

_DEAL_HISTORY_CACHE: OrderedDict = OrderedDict()
_DEAL_HISTORY_CACHE_MAX_SIZE = 512
_DEAL_HISTORY_CACHE_TTL_SEC = 60.0
_DEAL_HISTORY_CACHE_LOCK = threading.Lock()

_POPULAR_KEYWORDS_CACHE: dict = {}
_POPULAR_KEYWORDS_LOCK = threading.Lock()
_POPULAR_KEYWORDS_TTL_SEC = 60.0

_DEAL_DETAIL_CACHE: OrderedDict = OrderedDict()
_DEAL_DETAIL_CACHE_MAX_SIZE = 256
_DEAL_DETAIL_CACHE_TTL_SEC = 20.0
_DEAL_DETAIL_CACHE_LOCK = threading.Lock()


def _evict_deal_detail_cache(deal_id: int) -> None:
    with _DEAL_DETAIL_CACHE_LOCK:
        keys = [k for k in _DEAL_DETAIL_CACHE if len(k) >= 2 and k[1] == deal_id]
        for k in keys:
            _DEAL_DETAIL_CACHE.pop(k, None)


def invalidate_feed_api_cache(clear_ecom_url_cache: bool = True):
    import sys as _sys
    _TOP_DEALS_API_CACHE["ts"] = 0.0
    _TOP_DEALS_API_CACHE["data"] = None
    with _HOT_DEALS_CACHE_LOCK:
        _HOT_DEALS_API_CACHE.clear()
        _HOT_DEALS_CURSOR_CACHE.clear()
    with _DEAL_HISTORY_CACHE_LOCK:
        _DEAL_HISTORY_CACHE.clear()
    with _POPULAR_KEYWORDS_LOCK:
        _POPULAR_KEYWORDS_CACHE.clear()
    with _DEAL_DETAIL_CACHE_LOCK:
        _DEAL_DETAIL_CACHE.clear()
    if clear_ecom_url_cache:
        with _CLEAN_ECOM_URL_LOCK:
            _CLEAN_ECOM_URL_CACHE.clear()
    for _alias in ("routers.community", "backend.routers.community"):
        _mod = _sys.modules.get(_alias)
        if _mod is not None and _mod is not _sys.modules.get(__name__):
            if hasattr(_mod, "_TOP_DEALS_API_CACHE"):
                _mod._TOP_DEALS_API_CACHE["ts"] = 0.0
                _mod._TOP_DEALS_API_CACHE["data"] = None
            if hasattr(_mod, "_HOT_DEALS_API_CACHE"):
                _mod._HOT_DEALS_API_CACHE.clear()
            if hasattr(_mod, "_HOT_DEALS_CURSOR_CACHE"):
                _mod._HOT_DEALS_CURSOR_CACHE.clear()
            if hasattr(_mod, "_DEAL_HISTORY_CACHE"):
                _mod._DEAL_HISTORY_CACHE.clear()
            if hasattr(_mod, "_POPULAR_KEYWORDS_CACHE"):
                _mod._POPULAR_KEYWORDS_CACHE.clear()
            if hasattr(_mod, "_DEAL_DETAIL_CACHE"):
                _mod._DEAL_DETAIL_CACHE.clear()
            if clear_ecom_url_cache and hasattr(_mod, "_CLEAN_ECOM_URL_CACHE"):
                _mod._CLEAN_ECOM_URL_CACHE.clear()


invalidate_deals_cache = invalidate_feed_api_cache


def _hydrate_content_html_batch(db: Session, deals_list: list) -> set[int]:
    """지연 로딩(defer)된 Deal 목록 중 미로드 content_html만 배치 IN 쿼리로 적재하여 N+1 차단"""
    if not deals_list:
        return set()
    need_map: dict[int, list] = {}
    for d in deals_list:
        if d is None or getattr(d, "id", None) is None:
            continue
        try:
            if "content_html" not in sa_inspect(d).unloaded:
                continue
        except Exception:
            continue
        need_map.setdefault(int(d.id), []).append(d)
    if not need_map:
        return set()

    html_by_id: dict[int, Optional[str]] = {}
    all_ids = list(need_map.keys())
    for start in range(0, len(all_ids), 400):
        batch_ids = all_ids[start:start + 400]
        rows = (
            db.query(models.Deal.id, models.Deal.content_html)
            .filter(models.Deal.id.in_(batch_ids))
            .all()
        )
        for did, html_val in rows:
            html_by_id[int(did)] = html_val

    hydrated_ids: set[int] = set()
    for did, objs in need_map.items():
        html_val = html_by_id.get(did)
        for obj in objs:
            try:
                set_committed_value(obj, "content_html", html_val)
            except Exception:
                pass
        hydrated_ids.add(did)
    return hydrated_ids


def _hydrate_suspect_krw_cents_content_html(db: Session, deals_list: list) -> None:
    """KRW·소액(0 < price < 100000) 중 제목에서 가격/무료를 확정할 수 없는 행만 선택 IN 하이드레이션"""
    from backend.core.deal_normalizer import parse_title_price_and_shipping

    suspects = []
    for d in deals_list:
        if d is None:
            continue
        curr = (getattr(d, "currency", None) or "KRW").strip().upper()
        if curr != "KRW":
            continue
        p_val = extract_price(getattr(d, "price", 0))
        if 0 < p_val < 100000:
            if p_val >= 100:
                title_val = getattr(d, "title", "") or ""
                tp, _, _, is_free = parse_title_price_and_shipping(title_val) if title_val else (0, "KRW", "", False)
                if is_free or tp > 0:
                    continue
            suspects.append(d)
    if suspects:
        _hydrate_content_html_batch(db, suspects)


def _apply_html_fields_to_deal_dict(
    g: dict,
    primary_html: str,
    primary_title: str,
    primary_clean_title: str,
    cluster_htmls: list[str],
    ecom_obj,
    ecom_html: str,
    ecom_is_multi: bool,
    ecom_comp_name: str,
    db: Session,
) -> None:
    """2단계 지연 로드된 content_html을 기반으로 본문/태그/이미지/구매처 폴백 필드를 보강"""
    g["content_html"] = _clean_content_text(primary_html, primary_clean_title)
    g["tags"] = extract_deal_benefit_tags(primary_title, primary_html)

    merged_imgs: list[str] = []
    for ch in cluster_htmls:
        if ch:
            merged_imgs = _merge_body_images(merged_imgs, _extract_body_images(ch))
    g["body_images"] = merged_imgs
    g["bodyImages"] = merged_imgs

    if not g.get("image_url") and primary_html:
        fallback_img = merged_imgs[0] if merged_imgs else None
        if not fallback_img:
            m = re.search(r'\[첨부된 이미지 링크들\]\s*:?\s*(https?://[^\s,\)\]]+)', primary_html)
            if m and "no_image" not in m.group(1).lower():
                fallback_img = m.group(1).strip().rstrip(",)")
        if not fallback_img:
            m2 = re.search(r'(https?://[^\s"\'<>\)\]]+?\.(?:jpg|jpeg|png|webp))', primary_html, re.IGNORECASE)
            if m2 and "dot03.gif" not in m2.group(1).lower():
                fallback_img = m2.group(1).strip().rstrip(",)")
        if fallback_img:
            g["image_url"] = fallback_img

    if ecom_html:
        clean_ecom_url = get_clean_ecommerce_url(
            (getattr(ecom_obj, "ecommerce_link", "") or "") or (getattr(ecom_obj, "post_link", "") or ""),
            ecom_html,
            getattr(ecom_obj, "id", None),
            db,
            is_multi_item=ecom_is_multi,
        )
        resolved_ecom_url = clean_ecom_url
        if not resolved_ecom_url:
            resolved_ecom_url = _build_direct_mall_search_url(
                getattr(ecom_obj, "shop_name", "") or "",
                getattr(ecom_obj, "title", "") or "",
                getattr(ecom_obj, "post_link", "") or "",
                ecom_comp_name,
            )
        is_fb = _compute_is_search_fallback(clean_ecom_url, resolved_ecom_url)
        g["ecommerce_url"] = resolved_ecom_url
        g["is_search_fallback"] = is_fb
        g["isSearchFallback"] = is_fb
        new_shop = _resolve_canonical_shop_name(ecom_obj, resolved_ecom_url)
        if new_shop or not g.get("shop_name"):
            g["shop_name"] = new_shop
        ecom_post = getattr(ecom_obj, "post_link", "") or ""
        for src in g.get("sources") or ():
            if isinstance(src, dict) and " - " not in (src.get("site_name") or ""):
                if not ecom_post or src.get("post_url") == ecom_post:
                    src["ecommerce_url"] = resolved_ecom_url

def _clean_content_text(raw_text: str, title: str = "") -> str:
    if not raw_text:
        return ""
    text = raw_text
    # 스크래퍼 아티팩트 제거: CDATA/인라인 JS, 댓글 영역 누수, (링크: ...), [첨부된 이미지 링크들]: ..., 비디오 태그 폴백 문구 등
    text = re.sub(r'//<!\[CDATA\[[\s\S]*?//\]\]>', '', text, flags=re.IGNORECASE)
    text = re.sub(r'댓글\s*작성\s*하려면\s*로그인이\s*필요합니다[\s\S]*$', '', text, flags=re.IGNORECASE)
    text = re.sub(r'추천수\s*/\s*비추천수[\s\S]*$', '', text, flags=re.IGNORECASE)
    text = re.sub(r'\[상품이미지\s*:\s*https?://[^\]]+\]', '', text, flags=re.IGNORECASE)
    text = re.sub(r'\[상품이미지\s*:[^\]]*\]', '', text, flags=re.IGNORECASE)
    text = re.sub(r'\(\s*링크\s*:\s*https?://[^\)]+\)', '', text, flags=re.IGNORECASE)
    text = re.sub(r'\[첨부된 이미지 링크들\]\s*:?[\s\S]*$', '', text, flags=re.IGNORECASE)
    text = re.sub(r'Your browser does not support the video tag\.?', '', text, flags=re.IGNORECASE)
    text = re.sub(
        r'</?(?:p|div|span|br|a|img|table|thead|tbody|tr|td|th|ul|ol|li|dl|dt|dd|strong|b|i|u|s|strike|del|em|font|center|h[1-6]|blockquote|hr|section|article|header|footer|figure|figcaption|iframe|script|style|input|button|form|label|select|option|textarea|meta|link|video|source)\b[^>]*>',
        ' ',
        text,
        flags=re.IGNORECASE,
    )
    text = (
        text.replace('&nbsp;', ' ')
        .replace('&amp;', '&')
        .replace('&lt;', '<')
        .replace('&gt;', '>')
        .replace('&quot;', '"')
        .replace('&#039;', "'")
        .replace('&#39;', "'")
    )
    # 단독 URL 라인 및 본문 내 날것의 URL 제거
    text = re.sub(r'https?://[^\s\)\]]+', '', text)
    # 각 줄 양끝 공백 정리 및 빈 줄 압축
    lines = [ln.strip() for ln in text.splitlines()]
    cleaned_lines = []
    for ln in lines:
        if not ln:
            if cleaned_lines and cleaned_lines[-1] != "":
                cleaned_lines.append("")
            continue
        # 의미 없는 구분선/기호만 있는 줄 제거
        if re.match(r'^[\-\=\*\_\~\.\,\:\;\(\)\[\]\/\\]+$', ln):
            continue
        cleaned_lines.append(ln)
    result = "\n".join(cleaned_lines).strip()
    if len(result) < 4:
        return ""
    if title and result.replace(" ", "") == title.replace(" ", ""):
        return ""
    return result

def _clean_ai_summary(raw_summary: str) -> str:
    if not raw_summary:
        return ""
    if any(bad in raw_summary for bad in (
        "여러 상품이 포함된 핫딜입니다",
        "AI가 자동 분리해낸 핫딜입니다",
        "묻지도 따지지도 않고 쟁여두면",
        "카드 할인/쿠폰 최대한 끌어모으면",
        "가격이 0원이거나 역동적 할인이 적용되어",
    )):
        return "[커뮤니티 인증 핫딜]" if ("인기" in raw_summary or "인증" in raw_summary) else ""
    cleaned = re.sub(r'[🔥✨🤖💡⚡🎉📦✅⚠️]+', '', raw_summary).strip()
    cleaned = re.sub(r'^\s*\[커뮤니티 인기\]\s*', '[커뮤니티 인증 핫딜] ', cleaned).strip()
    return re.sub(r'\s{2,}', ' ', cleaned)


def clean_deal_title(raw_title: str) -> str:
    """
    단일 진실 공급원(deal_normalizer.clean_canonical_title)을 통해 표시용 제목을 정제합니다.
    """
    from backend.core.deal_normalizer import clean_canonical_title
    return clean_canonical_title(raw_title)


def _is_search_fallback_thumbnail_url(url: Optional[str]) -> bool:
    """검색 폴백/아이콘/아바타 썸네일 여부 판별 (실상품 원본 이미지 보호용)."""
    if not url:
        return True
    low = str(url).lower()
    return (
        "ui-avatars.com" in low
        or "kakaocdn.net/argon" in low
        or "search.pstatic.net" in low
        or "cdn-icons-png.flaticon.com" in low
    )


def _resolve_best_image_url(deal_obj, base_url: str) -> Optional[str]:
    img = (getattr(deal_obj, "image_url", None) or "").replace("&amp;", "&").strip().rstrip(",)")
    if img.startswith("//"):
        img = "https:" + img
    if img:
        lower_img = img.lower()
        is_invalid_img = any(
            bad in lower_img
            for bad in (
                "no_image.svg",
                "thumb_no_image.svg",
                "noimage",
                "dot03.gif",
                "logo_f.svg",
                "49841804f3d132d75a6c11b1510af812.png",
                "https:undefined",
                "<a href=",
                "/web/upload/",
                "coupang.com/vp/products/",
                "item.gmarket.co.kr/item",
                "mobile.auction.co.kr/ego.aspx",
            )
        )
        if not is_invalid_img:
            if img.startswith("/images/"):
                return f"{base_url}{img}"
            if not img.startswith("http"):
                return f"{base_url}/images/{img}"
            return upgrade_lowres_image_url(img)
    content = _safe_loaded_attr(deal_obj, "content_html", "")
    if content:
        m = re.search(r'\[첨부된 이미지 링크들\]\s*:?\s*(https?://[^\s,\)\]]+)', content)
        if m:
            cand = m.group(1).strip().rstrip(",)")
            if cand and "no_image" not in cand.lower():
                return upgrade_lowres_image_url(cand)
        m2 = re.search(r'(https?://[^\s"\'<>\)\]]+?\.(?:jpg|jpeg|png|webp))', content, re.IGNORECASE)
        if m2:
            cand2 = m2.group(1).strip().rstrip(",)")
            if cand2 and "dot03.gif" not in cand2.lower():
                return upgrade_lowres_image_url(cand2)
    title = getattr(deal_obj, "title", None) or ""
    lower_title = title.lower()

    if any(kw in lower_title for kw in ["롯데시네마", "롯시"]):
        return "https://play-lh.googleusercontent.com/WUVm0Q6FQnqTZ33julTaMJx7oJCqYANWArhv92Sin8Bix4dBvgWxPn1YkCogC6BSihWS2dDz2evjq-FSIhPFIA=s300"
    if "메가박스" in lower_title:
        return "https://play-lh.googleusercontent.com/Np5x0-5pstR3-j9DsuIzeMLbCcvvdVAIqwfLlJY2XdJkjQWXUfdJCBF0nTvJr9iBhnSPPxuSJeHK2gJFP68vkac=s300"
    if any(kw in lower_title for kw in ["cgv", "씨지브이"]):
        return "https://play-lh.googleusercontent.com/3S9LNVDqVQCm-QRwvpECpJJBKmRhQ9LNuvPAc14occs2d69IM49_OCOyp8i0cTVmH3DUZvXGSYqvm7R7Pbxa7fY=s300"
    if any(kw in lower_title for kw in ["스타벅스", "스벅"]):
        return "https://play-lh.googleusercontent.com/vD7K2him82DhJFyfPjw9WbPIXTqLoS8_LoC4GPpfBKKsc2apjowqvcCw2l9zNRrI_YbortUyLhfKW14ePgL0pA=s300"
    if any(kw in lower_title for kw in ["네이버페이", "네이버하이", "네이버적립", "일일적립"]):
        return "https://campaign-cdn.pstatic.net/0/campaign2/2025/03/click-point/img/npay_og_260x260.png"
    if any(kw in lower_title for kw in [
        "기프트카드", "기프트코드", "문화상품권", "해피머니", "컬쳐랜드", 
        "도서문화상품권", "모바일상품권", "백화점상품권", "금액권", "교환권"
    ]):
        return "https://cdn-icons-png.flaticon.com/512/8633/8633496.png"
    return None

@router.get("/top-hot-deals")
def get_top_hot_deals(db: Session = Depends(get_db_session)):
    try:
        from sqlalchemy import and_, or_, not_
        from sqlalchemy.orm import joinedload
        now_ts = _feed_time.time()
        db_bind_id = id(db.get_bind())
        if (
            _TOP_DEALS_API_CACHE["data"] is not None
            and _TOP_DEALS_API_CACHE.get("bind_id") == db_bind_id
            and (now_ts - _TOP_DEALS_API_CACHE["ts"]) < _FEED_CACHE_TTL_SEC
        ):
            return {"deals": _TOP_DEALS_API_CACHE["data"]}

        query = db.query(models.Deal).join(models.Community).options(
            joinedload(models.Deal.community),
            joinedload(models.Deal.deal_reactions)
        )
        # ⏰ 24시간 제한을 48시간으로 완화하여 어제 올라온 고품질 핫딜도 함께 구제
        time_limit = datetime.utcnow() - timedelta(hours=48)
        
        deals = query.filter(
            and_(
                # 🎯 대표님의 정책 결정에 따라 오늘의 핫딜 신뢰성 보존을 위해 100점 컷오프 유지!
                models.Deal.honey_score >= 100,
                models.Deal.indexed_at >= time_limit,
                models.Deal.is_closed == False,
                # 🧹 적립/이벤트 카테고리 제외
                models.Deal.category != "적립",
                models.Deal.category != "이벤트",
                models.Deal.category != "적립/이벤트",
                not_(models.Deal.title.like("%블라인드%"))
            )
        ).order_by(models.Deal.indexed_at.desc()).limit(600).all()

        # 48시간 내 신규 수집이 멈춘 상태라면 최신 고득점 딜로 폴백하여 빈 화면 방지
        use_time_cutoff = True
        if not deals:
            use_time_cutoff = False
            deals = query.filter(
                and_(
                    models.Deal.honey_score >= 100,
                    models.Deal.is_closed == False,
                    models.Deal.category != "적립",
                    models.Deal.category != "이벤트",
                    models.Deal.category != "적립/이벤트",
                    not_(models.Deal.title.like("%블라인드%"))
                )
            ).order_by(models.Deal.indexed_at.desc()).limit(200).all()

        COMMUNITY_MAP = _COMMUNITY_DISPLAY_MAP

        cluster_map = {}
        grouped_result = []
        
        dsu = build_dsu(deals)

        for deal in deals:
            comp_name = getattr(deal.community, 'display_name', None) or COMMUNITY_MAP.get(deal.community.name, deal.community.name)
            parsed_price_int, resolved_currency = resolve_deal_price_and_currency(deal)
            norm_shipping_fee = _normalize_shipping_fee(deal.shipping_fee, title_hint=deal.title or "")
            total_price = _compute_total_price(parsed_price_int, resolved_currency, norm_shipping_fee)
            
            # 모음전/다중 딜 여부 판별
            is_multi = False
            ecommerce_link = getattr(deal, 'ecommerce_link', None)
            post_link = getattr(deal, 'post_link', None)
            title = getattr(deal, 'title', '') or ''
            ai_summary = getattr(deal, 'ai_summary', '') or ''
            if (ecommerce_link == post_link) or (title and ("," in title or "모음" in title or "선택" in title or "외/" in title)) or ("[모음전]" in ai_summary):
                is_multi = True
            
            cluster_key = dsu.find(deal.id)
            image_url = _resolve_best_image_url(deal, BASE_URL)
            serialized_opts = _serialize_options_data(getattr(deal, "options_data", None), deal.id)
            deal_body_imgs = _extract_body_images(getattr(deal, "content_html", "") or "")

            if cluster_key in cluster_map:
                existing = cluster_map[cluster_key]
                merged_imgs = _merge_body_images(existing.get("body_images", []), deal_body_imgs)
                existing["body_images"] = merged_imgs
                existing["bodyImages"] = merged_imgs
                if total_price > 0:
                    existing_krw = _to_krw_equiv(existing.get("total_price", 0), existing.get("currency", "KRW"))
                    cand_krw = _to_krw_equiv(total_price, resolved_currency)
                    if existing_krw <= 0 or cand_krw < existing_krw:
                        # 더 저렴한 딜이 발견되면 대표 가격, 이미지, 제목 갱신
                        existing["price"] = parsed_price_int
                        existing["shipping_fee"] = norm_shipping_fee
                        existing["total_price"] = total_price
                        existing["currency"] = resolved_currency
                        clean_title = clean_deal_title(deal.title)
                        existing["title"] = clean_title
                        
                        # 더 저렴한 최저가에 유효한 이미지가 있거나 기존 딜에 이미지가 없는 경우에만 덮어씀
                        if image_url or not existing.get("image_url") or "ui-avatars.com" in (existing.get("image_url", "") or ""):
                            existing["image_url"] = image_url or existing.get("image_url")
                            
                        clean_top_ecom = get_clean_ecommerce_url(deal.ecommerce_link or deal.post_link or "", deal.content_html, deal.id, db, is_multi_item=is_multi)
                        new_ecom_url = clean_top_ecom
                        if not new_ecom_url:
                            new_ecom_url = _build_direct_mall_search_url(getattr(deal, 'shop_name', '') or '', deal.title or '', deal.post_link or '', comp_name)
                        if new_ecom_url or not existing.get("ecommerce_url"):
                            existing["ecommerce_url"] = new_ecom_url
                            top_fb = _compute_is_search_fallback(clean_top_ecom, new_ecom_url)
                            existing["is_search_fallback"] = top_fb
                            existing["isSearchFallback"] = top_fb
                        new_shop = _resolve_canonical_shop_name(deal, new_ecom_url)
                        if new_shop or not existing.get("shop_name"):
                            existing["shop_name"] = new_shop
                        if serialized_opts:
                            existing["options_data"] = serialized_opts
                        existing["post_url"] = deal.post_link or ""
                    else:
                        if image_url and (not existing.get("image_url") or "ui-avatars.com" in (existing.get("image_url", "") or "")):
                            existing["image_url"] = image_url
                        if serialized_opts and not existing.get("options_data"):
                            existing["options_data"] = serialized_opts
                    
                    # 출처 추가 (post_url 기준 중복 방지)
                    if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                        if comp_name not in existing.setdefault("site_names", []):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                        extend_unique_sources(existing["sources"], get_deal_sources(deal, comp_name, parsed_price_int, db, currency_override=resolved_currency))
                else:
                    if existing.get("total_price", 0) == 0:
                        if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                            if comp_name not in existing.setdefault("site_names", []):
                                existing["site_names"].append(comp_name)
                                existing["site_name"] = ", ".join(existing["site_names"])
                            extend_unique_sources(existing["sources"], get_deal_sources(deal, comp_name, parsed_price_int, db, currency_override=resolved_currency))
                
                # 하나라도 살아있다면 전체 딜을 진행중인 것으로 처리
                existing["is_closed"] = existing.get("is_closed", True) and getattr(deal, 'is_closed', False)
                existing["has_options"] = existing.get("has_options", False) or bool(getattr(deal, "has_options", False)) or bool(serialized_opts)
                    
                # 다수의 중복 딜 중 하나라도 핫딜이면 핫딜 상태 병합
                if getattr(deal, 'honey_score', 0) >= 100:
                    existing["honey_score"] = max(existing.get("honey_score", 0), 100)
                if deal.ai_summary and ("🔥" in deal.ai_summary or "인증 핫딜" in deal.ai_summary):
                    if not existing.get("ai_summary"):
                        existing["ai_summary"] = "[커뮤니티 인증 핫딜]"
                    elif "인증 핫딜" not in existing["ai_summary"]:
                        existing["ai_summary"] = "[커뮤니티 인증 핫딜] " + existing["ai_summary"]
            else:
                ai_sum = _clean_ai_summary(deal.ai_summary)
                # Clean title for display
                clean_title = clean_deal_title(deal.title)
                
                buy_count = deal.like_count or 0
                save_count = 0
                if hasattr(deal, 'deal_reactions') and deal.deal_reactions:
                    reaction = deal.deal_reactions[0]
                    buy_count += reaction.positive_reactions
                    save_count = reaction.negative_reactions

                clean_top_ecom = get_clean_ecommerce_url(deal.ecommerce_link or deal.post_link or "", deal.content_html, deal.id, db, is_multi_item=is_multi)
                resolved_top_ecom = clean_top_ecom or _build_direct_mall_search_url(getattr(deal, 'shop_name', '') or '', deal.title or '', deal.post_link or '', comp_name)
                top_fallback = _compute_is_search_fallback(clean_top_ecom, resolved_top_ecom)
                deal_dict = {
                    "id": deal.id,
                    "title": clean_title,
                    "price": parsed_price_int,
                    "shipping_fee": norm_shipping_fee,
                    "total_price": total_price,
                    "currency": resolved_currency,
                    "original_price": None,
                    "discount_rate": 0,
                    "image_url": image_url,
                    "ecommerce_url": resolved_top_ecom,
                    "shop_name": _resolve_canonical_shop_name(deal, resolved_top_ecom),
                    "post_url": deal.post_link or "",
                    "site_name": comp_name,
                    "site_names": [comp_name],
                    "sources": get_deal_sources(deal, comp_name, parsed_price_int, db, currency_override=resolved_currency),
                    "options_data": serialized_opts,
                    "category": normalize_category_key(deal.category),
                    "created_at": to_utc_iso(deal.indexed_at),
                    "view_count": deal.view_count or 0,
                    "like_count": buy_count,
                    "comment_count": deal.comment_count or 0,
                    "dislike_count": save_count,
                    "tags": extract_deal_benefit_tags(deal.title, deal.content_html),
                    "is_closed": deal.is_closed,
                    "honey_score": deal.honey_score or 0,
                    "ai_summary": ai_sum,
                    "content_html": _clean_content_text(deal.content_html or "", clean_title),
                    "has_options": bool(getattr(deal, "has_options", False) or serialized_opts),
                    "body_images": deal_body_imgs,
                    "bodyImages": deal_body_imgs,
                    "is_search_fallback": top_fallback,
                    "isSearchFallback": top_fallback,
                }
                cluster_map[cluster_key] = deal_dict
                grouped_result.append(deal_dict)
        if use_time_cutoff:
            # 48시간 이내에 올라온 딜을 포함한 클러스터링 필터링
            cutoff = datetime.utcnow() - timedelta(hours=48)
            filtered_result = []
            for deal_dict in grouped_result:
                if deal_dict.get("created_at"):
                    dt = datetime.fromisoformat(deal_dict["created_at"].replace("Z", "+00:00"))
                    if dt.tzinfo:
                        dt = dt.replace(tzinfo=None)
                    if dt >= cutoff:
                        filtered_result.append(deal_dict)
                else:
                    filtered_result.append(deal_dict)
        else:
            filtered_result = grouped_result
                
        # 필터링된 결과 중 앞에서 200개만 반환 (시간순 정렬 유지)
        final_top_result = filtered_result[:200]
        _TOP_DEALS_API_CACHE["ts"] = now_ts
        _TOP_DEALS_API_CACHE["bind_id"] = db_bind_id
        _TOP_DEALS_API_CACHE["data"] = final_top_result
        
        return {"deals": final_top_result}
    except HTTPException:
        raise
    except SQLAlchemyError as e:
        logger.error(f"Database error fetching top hot deals: {e}", exc_info=True)
        raise HTTPException(status_code=503, detail="Database unavailable")
    except Exception as e:
        logger.error(f"Error fetching top hot deals: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal Server Error")

def _build_single_category_clause(models, single_cat: str, or_, and_, not_):
    """단일 카테고리 토큰을 DB 필터 SQLAlchemy 조건절로 변환합니다."""
    cat_token = single_cat.strip()
    cat_lower = cat_token.lower()
    target_keywords: list[str] = []
    if cat_token in ("음식", "식품/건강"):
        canonical_cat = "음식"
        target_keywords = ["음식", "식품", "먹거리", "간식", "음료수", "건강"]
    elif cat_lower in ("sw/게임",):
        canonical_cat = "SW/게임"
        target_keywords = ["게임", "sw", "소프트웨어", "콘솔"]
    elif cat_lower in ("pc용품", "pc/하드웨어"):
        canonical_cat = "PC용품"
        target_keywords = ["pc", "하드웨어", "노트북", "컴퓨터", "데스크탑", "모니터"]
    elif cat_lower in ("가전제품", "가전/tv", "가전", "가전/디지털"):
        canonical_cat = "가전제품"
        target_keywords = ["가전제품", "가전", "냉장고", "tv", "음향", "가전/tv", "디지털"]
    elif cat_token == "생활용품":
        canonical_cat = "생활용품"
        target_keywords = ["생활", "생필품", "주방", "가구", "인테리어"]
    elif cat_token in ("패션", "의류/패션"):
        canonical_cat = "패션"
        target_keywords = ["패션", "의류", "신발", "잡화", "가방"]
    elif cat_token == "뷰티":
        canonical_cat = "뷰티"
        target_keywords = ["뷰티", "미용", "화장품"]
    elif cat_token == "모바일/기프티콘":
        canonical_cat = "모바일/기프티콘"
        target_keywords = ["모바일", "스마트폰", "태블릿", "기프티콘", "쿠폰", "e쿠폰", "e-쿠폰"]
    elif cat_token == "상품권":
        canonical_cat = "상품권"
        target_keywords = ["상품권", "이용권", "패키지", "기프티콘", "커피", "이커머스"]
    elif cat_token in ("적립", "적립/포인트"):
        canonical_cat = "적립"
        target_keywords = ["적립", "사인업", "하이패스", "리퀘스트"]
    elif cat_token == "이벤트":
        canonical_cat = "이벤트"
        target_keywords = ["이벤트", "추석", "명절", "무료증정", "리뷰", "체험단", "선착순", "무료배포"]
    elif cat_token == "육아/유아용품":
        canonical_cat = "육아/유아용품"
        target_keywords = ["유아용품", "육아"]
    elif cat_token in ("해외직구", "여행.해외핫딜", "해외핫딜", "여행/해외핫딜", "여행/해외"):
        canonical_cat = "해외직구"
        target_keywords = ["해외직구", "해외핫딜", "여행.해외핫딜", "여행", "해외", "호텔", "프리미엄", "항공", "직구"]
    elif cat_token == "핫딜모음":
        canonical_cat = "핫딜모음"
        target_keywords = []  # 핫딜모음은 아래에서 별도로 필터링 (honey_score >= 100)
    else:
        canonical_cat = cat_token
        target_keywords = [cat_token]

    if canonical_cat == "핫딜모음":
        return models.Deal.honey_score >= 100
    if canonical_cat == "육아/유아용품":
        parenting_title_kws = ["기저귀", "분유", "아기", "유아", "키즈", "하기스", "팸퍼스", "젖병", "카시트", "유모차", "베베", "아동"]
        return or_(
            models.Community.name == "bbasak_parenting",
            models.Deal.category.ilike("%육아%"),
            models.Deal.category.ilike("%유아%"),
            *[models.Deal.title.ilike(f"%{kw}%") for kw in parenting_title_kws],
        )
    if canonical_cat == "적립":
        point_title_kws = ["네이버페이", "적립", "네이버포인트", "페이포인트", "라방", "클릭", "출석"]
        return or_(
            *[models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords],
            *[models.Deal.title.ilike(f"%{kw}%") for kw in point_title_kws],
        )
    if canonical_cat == "이벤트":
        event_title_kws = ["이벤트", "선착순", "무료배포", "일시무료", "프로모션", "체험단", "응모"]
        return or_(
            *[models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords],
            *[models.Deal.title.ilike(f"%{kw}%") for kw in event_title_kws],
        )
    if canonical_cat == "뷰티":
        beauty_title_kws = ["선크림", "바디워시", "샴푸", "트리트먼트", "클렌징", "토너패드", "스킨토너", "세럼", "로션", "올리브영", "이니스프리", "토니모리", "닥터지", "달바"]
        return and_(
            or_(
                *[models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords],
                *[models.Deal.title.ilike(f"%{kw}%") for kw in beauty_title_kws],
            ),
            not_(models.Deal.title.ilike("%프린터%")),
            not_(models.Deal.title.ilike("%복합기%")),
            not_(models.Deal.title.ilike("%카트리지%")),
        )
    if canonical_cat == "가전제품":
        appliance_title_kws = [
            "선풍기", "에어컨", "청소기", "로봇청소기", "냉장고", "세탁기", "건조기", "공기청정기",
            "제습기", "가습기", "전자레인지", "오븐", "에어프라이어", "밥솥", "식기세척기", "블렌더",
            "믹서기", "다리미", "안마", "드라이기", "면도기", "전동칫솔", "이어폰", "헤드폰", "스피커",
            "사운드바", "버즈", "에어팟", "빔프로젝터", "TV", "티비",
        ]
        return and_(
            or_(
                *[models.Deal.category.ilike(f"%{kw}%") for kw in target_keywords],
                *[models.Deal.title.ilike(f"%{kw}%") for kw in appliance_title_kws],
            ),
            not_(models.Deal.title.ilike("%오븐구이%")),
        )
    base_clause = or_(*[models.Deal.category.ilike(f"%{_escape_like(kw)}%", escape="\\") for kw in target_keywords])
    if canonical_cat == "PC용품":
        return and_(base_clause, not_(models.Deal.title.ilike("%워치페이스%")))
    return base_clause


@router.get("/hot-deals")
def get_hot_deals(
    limit: int = 20,
    offset: int = 0,
    category: str = Query(None, description="분류별 카테고리명(ex: 가전제품, 패션 등)"),
    keyword: str = Query(None, description="검색어 (공백 AND, -제외단어, :상한가격 지원)"),
    platform: str = Query(None, description="커뮤니티/사이트 필터"),
    source: Optional[str] = Query(None, description="커뮤니티/사이트 필터 (alias for platform)"),
    sort: str = Query("latest", description="정렬 기준: latest | popular | price_asc"),
    popular_only: bool = Query(False, description="인기 딜(honey_score >= 100)만 필터링"),
    cursor_indexed_at: Optional[str] = Query(None, description="커서 기준 indexed_at (ISO-8601, latest 전용)"),
    cursor_id: Optional[int] = Query(None, description="커서 기준 id (latest 전용)"),
    include_closed: bool = Query(False, description="종료된 딜 포함 여부"),
    only_hot: bool = Query(False, description="인기/슈퍼핫딜 필터"),
    hide_closed: bool = Query(False, description="종료된 딜 강제 숨김 (include_closed보다 우선)"),
    db: Session = Depends(get_db_session)
):
    try:
        from sqlalchemy import and_, func, not_, or_

        if limit < 1 or limit > 1000:
            raise HTTPException(status_code=400, detail="Invalid limit")
        if offset < 0:
            raise HTTPException(status_code=400, detail="Invalid offset")

        if (cursor_indexed_at is None) != (cursor_id is None):
            raise HTTPException(status_code=400, detail="Both cursor_indexed_at and cursor_id are required")
        if cursor_id is not None and cursor_id <= 0:
            raise HTTPException(status_code=400, detail="Invalid cursor_id")

        cursor_dt: Optional[datetime] = None
        if cursor_indexed_at is not None:
            try:
                parsed_cursor = datetime.fromisoformat(cursor_indexed_at.strip().replace("Z", "+00:00"))
                cursor_dt = _to_utc_naive(parsed_cursor)
            except Exception:
                raise HTTPException(status_code=400, detail="Invalid cursor_indexed_at format")

        effective_include_closed = bool(include_closed) and not bool(hide_closed)
        effective_only_hot = bool(only_hot) or bool(popular_only)

        target_platform = platform or source
        now_ts = _feed_time.time()
        db_bind_id = id(db.get_bind())
        sort_mode = (sort or "latest").strip().lower()
        cat_raw = (category or "").strip()
        normalized_cat_key = ",".join(
            sorted({c.strip() for c in cat_raw.split(",") if c.strip() and c.strip() != "전체"})
        )
        normalized_platform_key = ",".join(
            sorted({p.strip() for p in (target_platform or "").split(",") if p.strip() and p.strip() != "전체"})
        )
        cache_key = (
            db_bind_id,
            normalized_cat_key,
            (keyword or "").strip(),
            normalized_platform_key,
            sort_mode,
            effective_only_hot,
            effective_include_closed,
        )
        def _hydrate_cached_slice_rows(cached_entry_obj: dict, target_slice: list) -> None:
            need_meta = []
            need_cluster_ids = set()
            with _HOT_DEALS_CACHE_LOCK:
                hydrated_gids = cached_entry_obj.setdefault("hydrated_gids", set())
                meta_map = cached_entry_obj.get("hydrator_meta") or {}
                for g in target_slice:
                    if g["id"] not in hydrated_gids and g["id"] in meta_map:
                        m = meta_map[g["id"]]
                        need_meta.append((g, m))
                        need_cluster_ids.update(m["cluster_ids"])
            if need_cluster_ids:
                html_by_id: dict[int, str] = {}
                cids_list = list(need_cluster_ids)
                for start in range(0, len(cids_list), 400):
                    rows = (
                        db.query(models.Deal.id, models.Deal.content_html)
                        .filter(models.Deal.id.in_(cids_list[start:start + 400]))
                        .all()
                    )
                    for did, h_val in rows:
                        html_by_id[int(did)] = h_val or ""
                with _HOT_DEALS_CACHE_LOCK:
                    hydrated_gids = cached_entry_obj.setdefault("hydrated_gids", set())
                    for g, m in need_meta:
                        if g["id"] in hydrated_gids:
                            continue
                        ecom_obj = SimpleNamespace(
                            id=m["ecom_id"],
                            ecommerce_link=m["ecom_link"],
                            post_link=m["ecom_post_link"],
                            title=m["ecom_title"],
                            shop_name=m["ecom_shop_name"],
                        )
                        _apply_html_fields_to_deal_dict(
                            g=g,
                            primary_html=html_by_id.get(m["primary_id"], ""),
                            primary_title=m["primary_title"],
                            primary_clean_title=m["primary_clean_title"],
                            cluster_htmls=[html_by_id.get(cid, "") for cid in m["cluster_ids"]],
                            ecom_obj=ecom_obj,
                            ecom_html=html_by_id.get(m["ecom_id"], ""),
                            ecom_is_multi=m["ecom_is_multi"],
                            ecom_comp_name=m["ecom_comp_name"],
                            db=db,
                        )
                        hydrated_gids.add(g["id"])

        current_max_id = db.query(func.max(models.Deal.id)).scalar() or 0
        with _HOT_DEALS_CACHE_LOCK:
            cached_entry = _HOT_DEALS_API_CACHE.get(cache_key)
        is_main_cache_valid = (
            cached_entry is not None
            and cached_entry.get("max_id") == current_max_id
            and (now_ts - cached_entry["ts"]) < _FEED_CACHE_TTL_SEC
        )
        if cursor_dt is None:
            if is_main_cache_valid:
                cached_grouped = cached_entry["grouped"]
                is_exhausted = bool(cached_entry.get("exhausted", True))
                if is_exhausted or (offset + limit < len(cached_grouped)):
                    cached_slice = cached_grouped[offset:offset + limit]
                    _hydrate_cached_slice_rows(cached_entry, cached_slice)
                    cached_has_more = (offset + len(cached_slice)) < len(cached_grouped) or (not is_exhausted)
                    cached_next_cursor = None
                    if sort_mode == "latest" and cached_has_more and cached_slice:
                        cursors_map = cached_entry.get("cursors", {})
                        cached_next_cursor = cursors_map.get(offset + len(cached_slice) - 1)
                    return {
                        "deals": [dict(item) for item in cached_slice],
                        "has_more": cached_has_more,
                        "next_cursor": cached_next_cursor,
                        "nextCursor": cached_next_cursor,
                    }
        elif sort_mode == "latest" and cursor_indexed_at is not None and cursor_id is not None:
            cursor_iso_norm = cursor_indexed_at.strip()
            cursor_id_int = int(cursor_id)
            if is_main_cache_valid:
                cursors_map = cached_entry.get("cursors", {})
                matched_c_idx = None
                for idx_k, nc_val in cursors_map.items():
                    if nc_val.get("id") == cursor_id_int and nc_val.get("indexed_at") == cursor_iso_norm:
                        matched_c_idx = int(idx_k)
                        break
                if matched_c_idx is not None:
                    cached_grouped = cached_entry["grouped"]
                    is_exhausted = bool(cached_entry.get("exhausted", True))
                    if is_exhausted or (matched_c_idx + 1 + limit < len(cached_grouped)):
                        cached_slice = cached_grouped[matched_c_idx + 1:matched_c_idx + 1 + limit]
                        _hydrate_cached_slice_rows(cached_entry, cached_slice)
                        end_idx = matched_c_idx + 1 + len(cached_slice)
                        cached_has_more = end_idx < len(cached_grouped) or (not is_exhausted)
                        cached_next_cursor = (
                            cursors_map.get(matched_c_idx + len(cached_slice))
                            if (cached_has_more and cached_slice)
                            else None
                        )
                        return {
                            "deals": [dict(item) for item in cached_slice],
                            "has_more": cached_has_more,
                            "next_cursor": cached_next_cursor,
                            "nextCursor": cached_next_cursor,
                        }
            cursor_cache_key = (cache_key, cursor_iso_norm, cursor_id_int, int(limit))
            with _HOT_DEALS_CACHE_LOCK:
                cursor_entry = _HOT_DEALS_CURSOR_CACHE.get(cursor_cache_key)
            if (
                cursor_entry is not None
                and cursor_entry.get("max_id") == current_max_id
                and (now_ts - cursor_entry["ts"]) < _FEED_CACHE_TTL_SEC
            ):
                return {
                    "deals": [dict(item) for item in cursor_entry["deals"]],
                    "has_more": bool(cursor_entry["has_more"]),
                    "next_cursor": cursor_entry.get("next_cursor"),
                    "nextCursor": cursor_entry.get("next_cursor"),
                }

        query = db.query(models.Deal).join(models.Community).options(
            joinedload(models.Deal.community),
            joinedload(models.Deal.deal_reactions),
            defer(models.Deal.content_html),
            defer(models.Deal.search_keywords),
        )

        # 블라인드 처리된 게시글 제외 + include_closed/hide_closed 반영
        query = query.filter(not_(models.Deal.title.like("%블라인드%")))
        if not effective_include_closed:
            query = query.filter(models.Deal.is_closed == False)
        if effective_only_hot:
            query = query.filter(
                or_(
                    models.Deal.honey_score >= 100,
                    models.Deal.is_super_hotdeal == True,
                    models.Deal.like_count >= 8,
                )
            )
        
        cat_tokens = list(
            dict.fromkeys(
                c.strip() for c in cat_raw.split(",") if c.strip() and c.strip() != "전체"
            )
        )
        if cat_tokens:
            cat_clauses = [
                _build_single_category_clause(models, tok, or_, and_, not_)
                for tok in cat_tokens
            ]
            query = query.filter(or_(*cat_clauses))
        else:
            # 전체 탭일 경우 순수 포인트 클릭형 '적립' 및 공지성 '정보 안내'만 제외하고, [슈퍼적립]/[즉시적립]/무료배포 핫딜은 모두 정상 노출
            query = query.filter(
                and_(
                    models.Deal.category != "적립",
                    models.Deal.category != "적립/이벤트",
                    not_(models.Deal.title.like("%일일적립%")),
                    not_(models.Deal.title.like("%정보 안내%")),
                    not_(models.Deal.title.like("%불가%"))
                )
            )

        search_max_price: Optional[int] = None
        has_search_or_platform_filter = bool(
            (keyword and keyword.strip())
            or normalized_platform_key
        )
        if keyword and keyword.strip():
            from sqlalchemy import and_, func, not_, or_
            from backend.core.deal_normalizer import KOR_ENG_SEARCH_SYNONYMS

            positive_tokens = []
            exclude_tokens = []
            for raw_tok in keyword.strip().split():
                if raw_tok.startswith(":") and len(raw_tok) > 1:
                    digits = "".join(ch for ch in raw_tok[1:] if ch.isdigit())
                    if digits:
                        try:
                            val = int(digits)
                            if val > 0:
                                search_max_price = val
                        except ValueError:
                            pass
                elif raw_tok.startswith("-") and len(raw_tok) > 1:
                    et_val = raw_tok[1:].strip()
                    if et_val and re.search(r"[가-힣a-zA-Z0-9]", et_val):
                        exclude_tokens.append(et_val)
                else:
                    tok = raw_tok.strip()
                    # 와일드카드/특수기호만 있는 입력('%', '_', '[]' 등) 차단
                    if not re.search(r"[가-힣a-zA-Z0-9]", tok):
                        return {"deals": [], "has_more": False}
                    # 단일 영문 알파벳(예: 'f'), 단일 숫자, 단일 한글 자모(예: 'ㄱ')는 노이즈 매칭을 유발하므로 차단
                    if len(tok) == 1 and (tok.isascii() or re.match(r'^[ㄱ-ㅎㅏ-ㅣ]$', tok)):
                        return {"deals": [], "has_more": False}
                    positive_tokens.append(tok)

            def _build_positive_keyword_clause(pt: str):
                pt_escaped = _escape_like(pt)
                pt_like = f"%{pt_escaped}%"
                if len(pt) == 1:
                    return or_(
                        models.Deal.title.ilike(pt_like, escape="\\"),
                        models.Deal.base_product_name.ilike(pt_like, escape="\\"),
                        models.Deal.options_data.ilike(pt_like, escape="\\"),
                    )

                clauses = [
                    models.Deal.title.ilike(pt_like, escape="\\"),
                    models.Deal.search_keywords.ilike(pt_like, escape="\\"),
                    models.Deal.base_product_name.ilike(pt_like, escape="\\"),
                    models.Deal.options_data.ilike(pt_like, escape="\\"),
                ]

                # 대괄호 감싸진 검색어('[특가]' 등) 입력 시 대괄호 제거 본문도 함께 매칭
                inner_bracket = pt[1:-1].strip() if (pt.startswith("[") and pt.endswith("]") and len(pt) > 2) else ""
                if inner_bracket and re.search(r"[가-힣a-zA-Z0-9]", inner_bracket):
                    ib_like = f"%{_escape_like(inner_bracket)}%"
                    clauses.append(models.Deal.title.ilike(ib_like, escape="\\"))

                # 순수 한글/영숫자 토큰에 한해 공백·하이픈 무시 비교, 복합어 분해, 한/영 동의어 확장 적용
                if re.fullmatch(r"[가-힣a-zA-Z0-9\-]+", pt):
                    pt_compact = re.sub(r"[\s\-]+", "", pt)
                    if len(pt_compact) >= 2:
                        compact_like = f"%{_escape_like(pt_compact)}%"
                        clauses.append(
                            func.replace(func.replace(models.Deal.title, " ", ""), "-", "").ilike(compact_like, escape="\\")
                        )
                        clauses.append(
                            func.replace(func.replace(models.Deal.base_product_name, " ", ""), "-", "").ilike(compact_like, escape="\\")
                        )

                    for sub_parts in _decompose_compound_query_token(pt_compact):
                        if len(sub_parts) >= 2:
                            clauses.append(
                                and_(
                                    *[
                                        or_(
                                            models.Deal.title.ilike(f"%{_escape_like(sp)}%", escape="\\"),
                                            models.Deal.base_product_name.ilike(f"%{_escape_like(sp)}%", escape="\\"),
                                            models.Deal.search_keywords.ilike(f"%{_escape_like(sp)}%", escape="\\"),
                                        )
                                        for sp in sub_parts
                                    ]
                                )
                            )

                    syns = KOR_ENG_SEARCH_SYNONYMS.get(pt_compact.lower(), ())
                    for syn in syns:
                        syn_like = f"%{_escape_like(syn)}%"
                        clauses.append(models.Deal.title.ilike(syn_like, escape="\\"))
                        clauses.append(models.Deal.base_product_name.ilike(syn_like, escape="\\"))
                        clauses.append(models.Deal.search_keywords.ilike(syn_like, escape="\\"))

                    m_knum = re.fullmatch(r"([가-힣]{2,})([0-9]+[a-zA-Z]*)", pt_compact.lower())
                    if m_knum:
                        k_pref, num_suf = m_knum.group(1), m_knum.group(2)
                        for p_syn in KOR_ENG_SEARCH_SYNONYMS.get(k_pref, ()):
                            if re.fullmatch(r"[a-z0-9]+", p_syn):
                                comb_tok = f"{p_syn}{num_suf}"
                                comb_like = f"%{_escape_like(comb_tok)}%"
                                clauses.append(models.Deal.title.ilike(comb_like, escape="\\"))
                                clauses.append(models.Deal.base_product_name.ilike(comb_like, escape="\\"))
                                clauses.append(models.Deal.search_keywords.ilike(comb_like, escape="\\"))
                            pref_like = f"%{_escape_like(p_syn)}%"
                            num_like = f"%{_escape_like(num_suf)}%"
                            clauses.append(
                                and_(
                                    or_(
                                        models.Deal.title.ilike(pref_like, escape="\\"),
                                        models.Deal.base_product_name.ilike(pref_like, escape="\\"),
                                        models.Deal.search_keywords.ilike(pref_like, escape="\\"),
                                    ),
                                    or_(
                                        models.Deal.title.ilike(num_like, escape="\\"),
                                        models.Deal.base_product_name.ilike(num_like, escape="\\"),
                                        models.Deal.search_keywords.ilike(num_like, escape="\\"),
                                    ),
                                )
                            )

                return or_(*clauses)

            for pt in positive_tokens:
                query = query.filter(_build_positive_keyword_clause(pt))
            for et in exclude_tokens:
                et_like = f"%{_escape_like(et)}%"
                query = query.filter(not_(models.Deal.title.ilike(et_like, escape="\\")))
            
        platforms = list(
            dict.fromkeys(
                p.strip()
                for p in (target_platform or "").split(",")
                if p.strip() and p.strip() != "전체"
            )
        )
        if platforms:
            # DB의 communities.name / display_name 정확 매칭 + 다중 출처 병합(merged_communities) 포함
            REVERSE_COMMUNITY_MAP = {
                "펨코": "fmkorea", "에펨": "fmkorea", "뽐뿌": "ppomppu", "루리웹": "ruliweb",
                "클리앙": "clien", "퀘이사존": "quasarzone", "알리뽐뿌": "ali_ppomppu",
                "빠삭국내": "bbasak_domestic", "빠삭해외": "bbasak_overseas",
                "빠삭육아": "bbasak_parenting", "뽐뿌해외": "ppomppu_overseas"
            }
            from sqlalchemy import or_
            platform_conditions = []
            target_names = set()
            for p in platforms:
                target_names.add(p)
                eng_name = REVERSE_COMMUNITY_MAP.get(p)
                if eng_name:
                    target_names.add(eng_name)
                    platform_conditions.append(models.Community.name == eng_name)
                    platform_conditions.append(models.Community.display_name == p)
                else:
                    platform_conditions.append(models.Community.name == p)
                    platform_conditions.append(models.Community.display_name == p)
            matched_comms = db.query(models.Community.id).filter(
                or_(
                    models.Community.name.in_(list(target_names)),
                    models.Community.display_name.in_(list(target_names))
                )
            ).all()
            for (cid,) in matched_comms:
                platform_conditions.append(models.Deal.merged_communities.like(f"{cid}::%"))
                platform_conditions.append(models.Deal.merged_communities.like(f"%,{cid}::%"))
                platform_conditions.append(models.Deal.merged_communities == str(cid))
            if platform_conditions:
                query = query.filter(or_(*platform_conditions))
            
        # [300건 상한 해제 + latest 키셋 커서/popular 전수 페이징]
        def _build_grouped_from_raw(raw_seed_list: list):
            deals_all = list(raw_seed_list)
            seed_ids = {d.id for d in raw_seed_list}

            _GENERIC_BROKEN_LINKS = {
                "https://item.gmarket.co.kr/item",
                "https://itempage3.auction.co.kr/detailview.aspx",
                "https://bbs.ruliweb.com/link.php",
                "https://www.11st.co.kr/product/sellerproductdetail.tmall",
                "https://item.gmarket.co.kr/detailview/item.asp",
                "https://www.hyundaihmall.com/front/shitemdetailr.do",
                "https://click.linkprice.com/click.php",
                "https://www.lotteimall.com/goods/viewgoodsdetail.lotte",
            }
            if deals_all and has_search_or_platform_filter:
                from urllib.parse import urlparse as _urlparse
                from backend.core.url_utils import extract_canonical_product_key

                ecom_links = []
                for d in deals_all:
                    if d.ecommerce_link and len(d.ecommerce_link) > 15:
                        link = d.ecommerce_link.strip()
                        if link.lower() in _GENERIC_BROKEN_LINKS:
                            continue
                        if any(c in link.lower() for c in ["fmkorea.", "ppomppu.", "ruliweb.", "quasarzone.", "clien.", "bbasak."]):
                            continue
                        parsed_u = _urlparse(link)
                        has_concrete_target = bool(
                            extract_canonical_product_key(link)
                            or (parsed_u.path and parsed_u.path.strip("/") != "")
                        )
                        if not has_concrete_target:
                            continue
                        ecom_links.append(link)
                        if link.endswith('/'):
                            ecom_links.append(link[:-1])
                        else:
                            ecom_links.append(link + '/')
                if ecom_links:
                    existing_ids = {d.id for d in deals_all}
                    backfill_q = db.query(models.Deal).join(models.Community).options(
                        joinedload(models.Deal.community),
                        joinedload(models.Deal.deal_reactions),
                        defer(models.Deal.content_html),
                        defer(models.Deal.search_keywords),
                    ).filter(
                        models.Deal.ecommerce_link.in_(ecom_links),
                        ~models.Deal.id.in_(existing_ids),
                    )
                    if not effective_include_closed:
                        backfill_q = backfill_q.filter(models.Deal.is_closed == False)
                    additional_deals = backfill_q.limit(100).all()
                    if additional_deals:
                        deals_all.extend(additional_deals)

            # 1단계 선택 하이드레이션: KRW·소액(0 < price < 100000) 외화 센트 오표기 의심 행만 본문 보충
            _hydrate_suspect_krw_cents_content_html(db, deals_all)

            COMMUNITY_MAP = _COMMUNITY_DISPLAY_MAP
            c_map = {}
            g_list = []
            cluster_meta_by_gid = {}
            dsu_obj = build_dsu(deals_all)
            valid_c_keys = {dsu_obj.find(sid) for sid in seed_ids} if has_search_or_platform_filter else None

            for deal in deals_all:
                c_key = dsu_obj.find(deal.id)
                if valid_c_keys is not None and c_key not in valid_c_keys:
                    continue

                is_multi = False
                ecommerce_link = getattr(deal, 'ecommerce_link', None)
                post_link = getattr(deal, 'post_link', None)
                title = getattr(deal, 'title', '') or ''
                ai_summary = getattr(deal, 'ai_summary', '') or ''
                if (ecommerce_link == post_link) or (title and ("," in title or "모음" in title or "선택" in title or "외/" in title)) or ("[모음전]" in ai_summary):
                    is_multi = True

                comp_name = getattr(deal.community, 'display_name', None) or COMMUNITY_MAP.get(deal.community.name, deal.community.name)
                image_url = _resolve_best_image_url(deal, BASE_URL)
                clean_title = clean_deal_title(deal.title)
                parsed_price_int, resolved_currency = resolve_deal_price_and_currency(deal)
                norm_shipping_fee = _normalize_shipping_fee(deal.shipping_fee, title_hint=deal.title or "")
                total_price = _compute_total_price(parsed_price_int, resolved_currency, norm_shipping_fee)
                serialized_opts = _serialize_options_data(getattr(deal, "options_data", None), deal.id)
                loaded_html = _safe_loaded_attr(deal, "content_html", "")
                deal_body_imgs = _extract_body_images(loaded_html)

                if c_key in c_map:
                    existing = c_map[c_key]
                    meta = cluster_meta_by_gid[existing["id"]]
                    meta["cluster_deals"].append(deal)
                    merged_imgs = _merge_body_images(existing.get("body_images", []), deal_body_imgs)
                    existing["body_images"] = merged_imgs
                    existing["bodyImages"] = merged_imgs

                    if total_price > 0:
                        existing_krw = _to_krw_equiv(existing.get("total_price", 0), existing.get("currency", "KRW"))
                        cand_krw = _to_krw_equiv(total_price, resolved_currency)
                        if existing_krw <= 0 or cand_krw < existing_krw:
                            existing["price"] = parsed_price_int
                            existing["shipping_fee"] = norm_shipping_fee
                            existing["total_price"] = total_price
                            existing["currency"] = resolved_currency
                            ex_img = existing.get("image_url") or ""
                            if image_url and (not ex_img or _is_search_fallback_thumbnail_url(ex_img) or not _is_search_fallback_thumbnail_url(image_url)):
                                existing["image_url"] = image_url
                            existing["title"] = clean_title
                            clean_new_ecom = get_clean_ecommerce_url(deal.ecommerce_link or deal.post_link or "", loaded_html, deal.id, db, is_multi_item=is_multi)
                            new_ecom_url = clean_new_ecom
                            if not new_ecom_url:
                                new_ecom_url = _build_direct_mall_search_url(getattr(deal, 'shop_name', '') or '', deal.title or '', deal.post_link or '', comp_name)
                            if new_ecom_url or not existing.get("ecommerce_url"):
                                existing["ecommerce_url"] = new_ecom_url
                                cand_fb = _compute_is_search_fallback(clean_new_ecom, new_ecom_url)
                                existing["is_search_fallback"] = cand_fb
                                existing["isSearchFallback"] = cand_fb
                            meta["ecom_deal"] = deal
                            meta["ecom_is_multi"] = is_multi
                            meta["ecom_comp_name"] = comp_name
                            new_shop = _resolve_canonical_shop_name(deal, new_ecom_url)
                            if new_shop or not existing.get("shop_name"):
                                existing["shop_name"] = new_shop
                            if serialized_opts:
                                existing["options_data"] = serialized_opts
                            if comp_name in existing.setdefault("site_names", []):
                                existing["site_names"].remove(comp_name)
                            existing["site_names"].insert(0, comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                        else:
                            ex_img = existing.get("image_url") or ""
                            if image_url and (not ex_img or (_is_search_fallback_thumbnail_url(ex_img) and not _is_search_fallback_thumbnail_url(image_url))):
                                existing["image_url"] = image_url
                            if serialized_opts and not existing.get("options_data"):
                                existing["options_data"] = serialized_opts
                        if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                            if comp_name not in existing.setdefault("site_names", []):
                                existing["site_names"].append(comp_name)
                                existing["site_name"] = ", ".join(existing["site_names"])
                            extend_unique_sources(existing["sources"], get_deal_sources(deal, comp_name, parsed_price_int, db, currency_override=resolved_currency))
                    else:
                        if existing.get("total_price", 0) == 0:
                            if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                                if comp_name not in existing.setdefault("site_names", []):
                                    existing["site_names"].append(comp_name)
                                    existing["site_name"] = ", ".join(existing["site_names"])
                                extend_unique_sources(existing["sources"], get_deal_sources(deal, comp_name, parsed_price_int, db, currency_override=resolved_currency))

                    existing["is_closed"] = existing.get("is_closed", True) and getattr(deal, 'is_closed', False)
                    existing["has_options"] = existing.get("has_options", False) or bool(getattr(deal, "has_options", False)) or bool(serialized_opts)
                    if getattr(deal, 'honey_score', 0) >= 100:
                        existing["honey_score"] = max(existing.get("honey_score", 0), 100)
                    if getattr(deal, 'is_super_hotdeal', False):
                        existing["is_super_hotdeal"] = True
                    if deal.ai_summary and ("🔥" in deal.ai_summary or "인증 핫딜" in deal.ai_summary):
                        if not existing.get("ai_summary"):
                            existing["ai_summary"] = "[커뮤니티 인증 핫딜]"
                        elif "인증 핫딜" not in existing["ai_summary"]:
                            existing["ai_summary"] = "[커뮤니티 인증 핫딜] " + existing["ai_summary"]
                else:
                    buy_count = deal.like_count or 0
                    save_count = 0
                    if hasattr(deal, 'deal_reactions') and deal.deal_reactions:
                        reaction = deal.deal_reactions[0]
                        buy_count += reaction.positive_reactions
                        save_count = reaction.negative_reactions

                    site_names_list = [comp_name]
                    if getattr(deal, 'merged_communities', None):
                        for cid_str in deal.merged_communities.split(','):
                            try:
                                cid = int(cid_str.split('::')[0].strip())
                                m_name = get_community_display_name_by_id(cid, db)
                                if m_name and m_name not in site_names_list:
                                    site_names_list.append(m_name)
                            except (ValueError, TypeError) as exc:
                                logger.debug(f"Invalid merged_communities cid token {cid_str!r}: {exc}")

                    clean_ecom_url = get_clean_ecommerce_url(deal.ecommerce_link or deal.post_link or "", loaded_html, deal.id, db, is_multi_item=is_multi)
                    resolved_ecom_url = clean_ecom_url
                    if not resolved_ecom_url:
                        resolved_ecom_url = _build_direct_mall_search_url(getattr(deal, 'shop_name', '') or '', deal.title or '', deal.post_link or '', comp_name)
                    is_fb = _compute_is_search_fallback(clean_ecom_url, resolved_ecom_url)
                    is_hot = bool(getattr(deal, 'is_super_hotdeal', False) or (deal.honey_score or 0) >= 100 or (buy_count >= 8))
                    final_honey_score = 100 if is_hot else (deal.honey_score or 0)

                    deal_dict = {
                        "id": deal.id,
                        "title": clean_title,
                        "price": parsed_price_int,
                        "shipping_fee": norm_shipping_fee,
                        "total_price": total_price,
                        "currency": resolved_currency,
                        "image_url": image_url,
                        "ecommerce_url": resolved_ecom_url,
                        "shop_name": _resolve_canonical_shop_name(deal, resolved_ecom_url),
                        "post_url": deal.post_link or "",
                        "site_name": ", ".join(site_names_list),
                        "site_names": site_names_list,
                        "sources": get_deal_sources(deal, comp_name, parsed_price_int, db, currency_override=resolved_currency),
                        "options_data": serialized_opts,
                        "category": normalize_category_key(deal.category),
                        "honey_score": final_honey_score,
                        "is_super_hotdeal": is_hot,
                        "ai_summary": _clean_ai_summary(deal.ai_summary or ""),
                        "content_html": _clean_content_text(loaded_html, clean_title),
                        "is_closed": getattr(deal, 'is_closed', False),
                        "created_at": to_utc_iso(deal.indexed_at),
                        "view_count": deal.view_count or 0,
                        "comment_count": deal.comment_count or 0,
                        "like_count": buy_count,
                        "dislike_count": save_count,
                        "tags": extract_deal_benefit_tags(deal.title, loaded_html),
                        "has_options": bool(getattr(deal, "has_options", False) or serialized_opts),
                        "body_images": deal_body_imgs,
                        "bodyImages": deal_body_imgs,
                        "is_search_fallback": is_fb,
                        "isSearchFallback": is_fb,
                    }
                    c_map[c_key] = deal_dict
                    g_list.append(deal_dict)
                    cluster_meta_by_gid[deal.id] = {
                        "primary_deal": deal,
                        "primary_title": deal.title or "",
                        "primary_clean_title": clean_title,
                        "ecom_deal": deal,
                        "ecom_is_multi": is_multi,
                        "ecom_comp_name": comp_name,
                        "cluster_deals": [deal],
                    }

            if search_max_price is not None and search_max_price > 0:
                g_list = [
                    g for g in g_list
                    if 0 < _to_krw_equiv(g.get("price"), g.get("currency")) <= search_max_price
                ]

            if effective_only_hot:
                g_list = [
                    g for g in g_list
                    if int(g.get("honey_score") or 0) >= 100 or bool(g.get("is_super_hotdeal"))
                ]

            return g_list, dsu_obj, cluster_meta_by_gid

        raw_seed_deals = []
        grouped_result = []
        cluster_meta_by_gid = {}
        dsu = None
        db_exhausted = True

        is_sqlite_dialect = getattr(getattr(db.get_bind(), "dialect", None), "name", None) == "sqlite"
        order_ts_expr = models.Deal.indexed_at

        if sort_mode == "latest":
            effective_offset = 0 if cursor_dt is not None else max(0, offset)
            target_needed = effective_offset + limit + 1
            chunk_size = max(target_needed * 3, 150)
            chunk_cursor_dt = cursor_dt
            chunk_cursor_id = cursor_id
            db_exhausted = False

            lookback_rows: list = []
            if cursor_dt is not None and cursor_id is not None:
                lookback_limit = 180
                if is_sqlite_dialect:
                    init_cursor_val = cursor_dt.strftime("%Y-%m-%d %H:%M:%S.%f")
                    lb_q = query.filter(
                        or_(
                            order_ts_expr > init_cursor_val,
                            and_(
                                order_ts_expr == init_cursor_val,
                                models.Deal.id >= cursor_id,
                            ),
                        )
                    )
                else:
                    lb_q = query.filter(
                        or_(
                            models.Deal.indexed_at > cursor_dt,
                            and_(
                                models.Deal.indexed_at == cursor_dt,
                                models.Deal.id >= cursor_id,
                            ),
                        )
                    )
                lookback_chunk = lb_q.order_by(
                    order_ts_expr.asc(),
                    models.Deal.id.asc(),
                ).limit(lookback_limit).all()
                lookback_rows = list(reversed(lookback_chunk))

            for _ in range(20):
                chunk_q = query
                if chunk_cursor_dt is not None and chunk_cursor_id is not None:
                    if is_sqlite_dialect:
                        chunk_cursor_val = _to_utc_naive(chunk_cursor_dt).strftime("%Y-%m-%d %H:%M:%S.%f")
                        chunk_q = chunk_q.filter(
                            or_(
                                order_ts_expr < chunk_cursor_val,
                                and_(
                                    order_ts_expr == chunk_cursor_val,
                                    models.Deal.id < chunk_cursor_id,
                                ),
                            )
                        )
                    else:
                        chunk_q = chunk_q.filter(
                            or_(
                                models.Deal.indexed_at < chunk_cursor_dt,
                                and_(
                                    models.Deal.indexed_at == chunk_cursor_dt,
                                    models.Deal.id < chunk_cursor_id,
                                ),
                            )
                        )
                chunk = chunk_q.order_by(
                    order_ts_expr.desc(),
                    models.Deal.id.desc(),
                ).limit(chunk_size).all()
                raw_seed_deals.extend(chunk)
                if len(chunk) < chunk_size:
                    db_exhausted = True
                else:
                    chunk_cursor_dt = chunk[-1].indexed_at
                    chunk_cursor_id = chunk[-1].id

                if lookback_rows:
                    combined_raw = lookback_rows + raw_seed_deals
                    grouped_all, dsu, cluster_meta_by_gid = _build_grouped_from_raw(combined_raw)
                    lookback_cluster_keys = {dsu.find(d.id) for d in lookback_rows}
                    grouped_result = [
                        g for g in grouped_all
                        if dsu.find(g["id"]) not in lookback_cluster_keys
                    ]
                else:
                    grouped_result, dsu, cluster_meta_by_gid = _build_grouped_from_raw(raw_seed_deals)
                if db_exhausted or len(grouped_result) >= target_needed:
                    break

            final_result = grouped_result[effective_offset:effective_offset + limit]
            has_more = (effective_offset + len(final_result)) < len(grouped_result)
            if not has_more and not db_exhausted:
                has_more = True
        else:
            if sort_mode == "popular":
                raw_limit = min(2000, max(260, offset + limit * 5 + 150))
                raw_seed_deals = query.order_by(
                    models.Deal.honey_score.desc(),
                    models.Deal.like_count.desc(),
                    order_ts_expr.desc(),
                    models.Deal.id.desc(),
                ).limit(raw_limit).all()
            else:
                raw_limit = min(2000, max(1000, offset + limit + 400))
                raw_seed_deals = query.order_by(
                    order_ts_expr.desc(),
                    models.Deal.id.desc(),
                ).limit(raw_limit).all()
            db_exhausted = len(raw_seed_deals) < raw_limit
            grouped_result, dsu, cluster_meta_by_gid = _build_grouped_from_raw(raw_seed_deals)

            if sort_mode == "popular":
                grouped_result.sort(
                    key=lambda x: (
                        int(x.get("honey_score") or 0),
                        int(x.get("like_count") or 0),
                        int(x.get("comment_count") or 0)
                    ),
                    reverse=True
                )
            elif sort_mode == "price_asc":
                grouped_result.sort(
                    key=lambda x: (
                        0 if int(x.get("price") or 0) > 0 else 1,
                        _to_krw_equiv(x.get("total_price") or x.get("price"), x.get("currency")),
                        _to_krw_equiv(x.get("price"), x.get("currency")),
                    )
                )

            final_result = grouped_result[offset:offset + limit]
            has_more = (offset + len(final_result)) < len(grouped_result)

        # 2단계 배치 하이드레이션: 최종 노출 slice(최대 limit건)에 대해 아직 미로드된 content_html만 1회 IN 조회
        if final_result and cluster_meta_by_gid:
            slice_deals = []
            for g in final_result:
                m = cluster_meta_by_gid.get(g["id"])
                if m:
                    slice_deals.extend(m["cluster_deals"])
            newly_hydrated_ids = _hydrate_content_html_batch(db, slice_deals)
            if newly_hydrated_ids:
                for g in final_result:
                    m = cluster_meta_by_gid.get(g["id"])
                    if not m or not any(cd.id in newly_hydrated_ids for cd in m["cluster_deals"]):
                        continue
                    p_deal = m["primary_deal"]
                    e_deal = m["ecom_deal"]
                    _apply_html_fields_to_deal_dict(
                        g=g,
                        primary_html=_safe_loaded_attr(p_deal, "content_html", ""),
                        primary_title=m["primary_title"],
                        primary_clean_title=m["primary_clean_title"],
                        cluster_htmls=[_safe_loaded_attr(cd, "content_html", "") for cd in m["cluster_deals"]],
                        ecom_obj=e_deal,
                        ecom_html=_safe_loaded_attr(e_deal, "content_html", "") if e_deal.id in newly_hydrated_ids else "",
                        ecom_is_multi=m["ecom_is_multi"],
                        ecom_comp_name=m["ecom_comp_name"],
                        db=db,
                    )

        def _compute_next_cursor_for_slice(slice_end_idx: int) -> Optional[dict]:
            if sort_mode != "latest" or dsu is None or not raw_seed_deals:
                return None
            if slice_end_idx < 0 or slice_end_idx >= len(grouped_result):
                return None
            last_emitted_id = grouped_result[slice_end_idx]["id"]
            seed_pos_map = {d.id: idx for idx, d in enumerate(raw_seed_deals)}
            rep_idx = seed_pos_map.get(last_emitted_id)
            if rep_idx is None:
                return None
            remaining_cluster_keys = {dsu.find(d["id"]) for d in grouped_result[slice_end_idx + 1:]}
            cursor_raw = raw_seed_deals[rep_idx]
            k = rep_idx + 1
            while k < len(raw_seed_deals):
                if dsu.find(raw_seed_deals[k].id) in remaining_cluster_keys:
                    break
                cursor_raw = raw_seed_deals[k]
                k += 1
            return {
                "indexed_at": _format_cursor_iso(cursor_raw.indexed_at),
                "id": int(cursor_raw.id),
            }

        next_cursor_obj = None
        if sort_mode == "latest" and has_more and final_result:
            slice_base = 0 if cursor_dt is not None else max(0, offset)
            next_cursor_obj = _compute_next_cursor_for_slice(slice_base + len(final_result) - 1)

        if cursor_dt is None:
            cursors_map: dict[int, dict] = {}
            if sort_mode == "latest":
                for idx in range(len(grouped_result) - 1):
                    nc = _compute_next_cursor_for_slice(idx)
                    if nc:
                        cursors_map[idx] = nc
            primitive_meta = {
                gid: {
                    "primary_id": m["primary_deal"].id,
                    "primary_title": m["primary_title"],
                    "primary_clean_title": m["primary_clean_title"],
                    "ecom_id": m["ecom_deal"].id,
                    "ecom_link": m["ecom_deal"].ecommerce_link or "",
                    "ecom_post_link": m["ecom_deal"].post_link or "",
                    "ecom_title": m["ecom_deal"].title or "",
                    "ecom_shop_name": m["ecom_deal"].shop_name or "",
                    "ecom_is_multi": m["ecom_is_multi"],
                    "ecom_comp_name": m["ecom_comp_name"],
                    "cluster_ids": [cd.id for cd in m["cluster_deals"]],
                }
                for gid, m in cluster_meta_by_gid.items()
            }
            with _HOT_DEALS_CACHE_LOCK:
                if len(_HOT_DEALS_API_CACHE) > 50:
                    _HOT_DEALS_API_CACHE.clear()
                _HOT_DEALS_API_CACHE[cache_key] = {
                    "ts": now_ts,
                    "max_id": current_max_id,
                    "grouped": grouped_result,
                    "exhausted": db_exhausted,
                    "cursors": cursors_map,
                    "hydrated_gids": {g["id"] for g in final_result},
                    "hydrator_meta": primitive_meta,
                }
        elif sort_mode == "latest" and cursor_indexed_at is not None and cursor_id is not None:
            cursor_cache_key = (cache_key, cursor_indexed_at.strip(), int(cursor_id), int(limit))
            with _HOT_DEALS_CACHE_LOCK:
                if len(_HOT_DEALS_CURSOR_CACHE) > 50:
                    _HOT_DEALS_CURSOR_CACHE.clear()
                _HOT_DEALS_CURSOR_CACHE[cursor_cache_key] = {
                    "ts": now_ts,
                    "max_id": current_max_id,
                    "deals": [dict(item) for item in final_result],
                    "has_more": has_more,
                    "next_cursor": next_cursor_obj,
                }

        return {
            "deals": [dict(item) for item in final_result],
            "has_more": has_more,
            "next_cursor": next_cursor_obj,
            "nextCursor": next_cursor_obj,
        }

    except HTTPException:
        raise
    except SQLAlchemyError as e:
        logger.error(f"Database error fetching hot deals: {e}", exc_info=True)
        raise HTTPException(status_code=503, detail="Database unavailable")
    except Exception as e:
        logger.error(f"Error fetching hot deals: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal Server Error")

from fastapi import BackgroundTasks
import time as _time

_LAST_SCRAPE_TRIGGER_TS: float = 0.0
_SCRAPE_RUNNING_LOCK: bool = False

def run_scraper_script():
    global _SCRAPE_RUNNING_LOCK
    if _SCRAPE_RUNNING_LOCK:
        return
    _SCRAPE_RUNNING_LOCK = True
    try:
        import subprocess
        import sys
        root_dir = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
        fetch_script = os.path.join(root_dir, 'backend', 'scheduler', 'main.py')
        subprocess.run([sys.executable, fetch_script, "--one-shot"], cwd=root_dir, timeout=240)
        invalidate_feed_api_cache()
    except Exception as e:
        logger.warning(f"Manual scrape background task error: {e}")
    finally:
        _SCRAPE_RUNNING_LOCK = False

@router.post("/force-scrape", dependencies=[Depends(_verify_admin_key)])
def force_scrape_deals(background_tasks: BackgroundTasks):
    global _LAST_SCRAPE_TRIGGER_TS
    try:
        invalidate_feed_api_cache()
        now = _time.time()
        if _SCRAPE_RUNNING_LOCK or (now - _LAST_SCRAPE_TRIGGER_TS < 60):
            return {"status": "cooldown", "message": "이미 실시간 스크래핑이 진행 중이거나 최근 동기화되었습니다."}
        _LAST_SCRAPE_TRIGGER_TS = now
        background_tasks.add_task(run_scraper_script)
        return {"status": "success", "message": "Manual scraping is now running in the background."}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@router.get("/popular-keywords")
def get_popular_keywords(
    limit: int = Query(10, ge=1, le=50, description="반환할 인기 검색어 개수"),
    db: Session = Depends(get_db_session),
):
    try:
        from sqlalchemy import func
        from backend.core.deal_normalizer import (
            is_valid_popular_keyword,
            unescape_and_strip_html_entities,
        )

        data_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data")
        file_path = _FMKOREA_TRENDING_FILE_OVERRIDE or os.path.join(data_dir, "fmkorea_trending.json")
        file_mtime = round(os.path.getmtime(file_path), 3) if os.path.exists(file_path) else 0.0
        current_max_id = int(db.query(func.max(models.Deal.id)).scalar() or 0)
        kw_cache_key = (
            id(db.get_bind()),
            int(limit),
            current_max_id,
            str(_FMKOREA_TRENDING_FILE_OVERRIDE or ""),
            file_mtime,
        )
        now_kw_ts = _feed_time.time()
        with _POPULAR_KEYWORDS_LOCK:
            kw_cached = _POPULAR_KEYWORDS_CACHE.get(kw_cache_key)
            if kw_cached is not None and (now_kw_ts - kw_cached["ts"]) < _POPULAR_KEYWORDS_TTL_SEC:
                cd = kw_cached["data"]
                return {
                    "keywords": list(cd["keywords"]),
                    "rankings": [dict(r) for r in cd["rankings"]],
                    "updated_at": cd["updated_at"],
                }

        extra_noise = {
            "pro", "max", "plus", "mini", "ultra", "usb", "pc", "tv", "led", "유튜버", "링크",
            "자켓", "게이밍", "다운", "헤비", "브랜드", "일시", "일시품절", "스토어", "기본가",
            "무선", "유선", "골라담기", "미개봉", "결제시", "무료증정", "빠른배송", "겨울", "여름",
            "구분", "대체", "제품별", "가정용", "업소용", "소용량", "제주", "슈퍼", "스페셜",
            "슬림", "와이드", "멀티", "고속", "초고속", "스토어쿠폰", "결제할인", "공구코드",
            "슈퍼적립", "브랜드쿠폰", "전원증정", "오늘마켓", "총정리", "넾다세일", "기타",
        }

        def _is_clean_kw(candidate: str) -> bool:
            parts = [p.strip() for p in candidate.split() if p.strip()]
            if not parts:
                return False
            for part in parts:
                if not is_valid_popular_keyword(part):
                    return False
                if part.lower() in extra_noise:
                    return False
                if re.match(r"^[a-zA-Z가-힣]\d+$", part):
                    return False
            return True

        deals_rows = db.query(models.Deal.title, models.Deal.indexed_at).filter(
            models.Deal.is_closed == False
        ).order_by(models.Deal.indexed_at.desc()).limit(1000).all()

        now_utc = datetime.now(timezone.utc).replace(tzinfo=None)
        recent_cutoff = now_utc - timedelta(hours=12)
        prepared_deals = [
            (unescape_and_strip_html_entities(t_val), _to_utc_naive(idx_val))
            for t_val, idx_val in deals_rows
            if t_val
        ]

        def count_matching_deals(kw_str: str) -> tuple[int, int]:
            tokens = [t.lower() for t in kw_str.split() if t]
            if not tokens:
                return 0, 0
            d_cnt = 0
            r_cnt = 0
            for t_val, idx_val in prepared_deals:
                t_low = t_val.lower()
                if all(tok in t_low for tok in tokens):
                    d_cnt += 1
                    if idx_val and idx_val >= recent_cutoff:
                        r_cnt += 1
            return d_cnt, r_cnt

        filtered_keywords: list[str] = []
        seen_kw_low: set[str] = set()

        # 1. 펨코 실시간 급상승 검색어 JSON 확인 (불용어·엔티티·숫자단위 검증 + 실제 DB 매칭 1건 이상 필수)
        if os.path.exists(file_path):
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                for raw_kw in data.get("keywords", []):
                    if not isinstance(raw_kw, str) or "&" in raw_kw or "+" in raw_kw or "%" in raw_kw:
                        continue
                    kw_clean = unescape_and_strip_html_entities(raw_kw).strip()
                    if kw_clean == "제로":
                        kw_clean = "제로콜라"
                    elif kw_clean.upper() == "메가MGC커피":
                        kw_clean = "메가커피"
                    elif kw_clean == "폴드":
                        kw_clean = "갤럭시 폴드"
                    if _is_clean_kw(kw_clean) and kw_clean.lower() not in seen_kw_low:
                        d_cnt, _ = count_matching_deals(kw_clean)
                        if d_cnt > 0:
                            seen_kw_low.add(kw_clean.lower())
                            filtered_keywords.append(kw_clean)

        # 2. 최근 핫딜 제목 분석 (HTML 엔티티 해제 + 미완성 가격괄호 제거 후 순수 명사 집계)
        words_count: dict[str, int] = {}
        recent_count: dict[str, int] = {}

        for unescaped_title, idx_utc in prepared_deals:
            stripped_title = clean_deal_title(unescaped_title)
            stripped_title = re.sub(r"\[[^\]]*\]|\([^\)]*\)?", " ", stripped_title)
            stripped_title = re.sub(r"\b[A-Za-z]{2,}(?:\s+[A-Za-z]{2,}){2,}\b", " ", stripped_title)
            words = re.findall(r"[가-힣a-zA-Z0-9]{2,}", stripped_title)
            is_recent = bool(idx_utc and idx_utc >= recent_cutoff)
            for w in words:
                w_norm = "제로콜라" if w == "제로" else ("메가커피" if w.upper() == "메가MGC커피" else w)
                if not _is_clean_kw(w_norm):
                    continue
                words_count[w_norm] = words_count.get(w_norm, 0) + 1
                if is_recent:
                    recent_count[w_norm] = recent_count.get(w_norm, 0) + 1

        sorted_words = sorted(words_count.items(), key=lambda x: (recent_count.get(x[0], 0) * 2 + x[1]), reverse=True)
        top_keywords = list(filtered_keywords)
        for w_norm, _ in sorted_words:
            if len(top_keywords) >= limit:
                break
            if w_norm.lower() in seen_kw_low:
                continue
            d_cnt, _ = count_matching_deals(w_norm)
            if d_cnt > 0:
                seen_kw_low.add(w_norm.lower())
                top_keywords.append(w_norm)

        base_keywords = ["아이폰", "갤럭시", "로봇청소기", "다이슨", "노트북", "에어팟", "갈비탕", "제로콜라", "모니터", "생수"]
        if len(top_keywords) < limit:
            for bk in base_keywords:
                if bk.lower() not in seen_kw_low and _is_clean_kw(bk):
                    d_cnt, _ = count_matching_deals(bk)
                    if d_cnt > 0:
                        seen_kw_low.add(bk.lower())
                        top_keywords.append(bk)
                if len(top_keywords) >= limit:
                    break

        final_keywords = top_keywords[:limit]
        rankings = []
        for idx, kw in enumerate(final_keywords):
            deal_cnt, recent_cnt = count_matching_deals(kw)
            if deal_cnt == 0:
                deal_cnt = words_count.get(kw, 1)

            if idx == 0 or (idx < 3 and recent_cnt >= 3):
                status = "HOT"
            elif idx < 5 or recent_cnt >= 2:
                status = "UP"
            elif idx in (6, 8) and recent_cnt >= 1:
                status = "NEW"
            else:
                status = "SAME"

            rankings.append({
                "rank": idx + 1,
                "keyword": kw,
                "deal_count": max(1, deal_cnt),
                "recent_count": recent_cnt,
                "status": status
            })

        result_payload = {
            "keywords": final_keywords,
            "rankings": rankings,
            "updated_at": datetime.now(_KST).strftime("%H:%M 기준")
        }
        with _POPULAR_KEYWORDS_LOCK:
            if len(_POPULAR_KEYWORDS_CACHE) > 20:
                _POPULAR_KEYWORDS_CACHE.clear()
            _POPULAR_KEYWORDS_CACHE[kw_cache_key] = {"ts": now_kw_ts, "data": result_payload}
        return result_payload
    except Exception as e:
        logger.error(f"Error fetching keywords: {e}", exc_info=True)
        fallback = ["아이폰", "갤럭시", "다이슨", "제로콜라", "노트북", "로봇청소기", "모니터", "에어팟", "스탠리", "보조배터리"][:limit]
        return {
            "keywords": fallback,
            "rankings": [
                {"rank": i + 1, "keyword": k, "deal_count": 5, "recent_count": 1, "status": "HOT" if i == 0 else ("UP" if i < 3 else "SAME")}
                for i, k in enumerate(fallback)
            ],
            "updated_at": "방금 전"
        }

GENERIC_CATEGORY_STOPWORDS = {
    # 가전 / 디지털
    "청소기", "로봇청소기", "로청", "무선청소기", "물걸레", "스틱청소기", "창문로봇청소기",
    "모니터", "포터블모니터", "휴대용모니터", "tv", "티비", "텔레비전", "oled", "qled",
    "노트북", "랩탑", "컴퓨터", "데스크탑", "본체", "완본체", "태블릿", "패드", "아이패드", "갤럭시탭",
    "스마트폰", "핸드폰", "휴대폰", "자급제", "이어폰", "헤드폰", "헤드셋", "스피커", "사운드바",
    "마우스", "키보드", "기계식키보드", "충전기", "고속충전기", "보조배터리", "보배", "케이블", "거치대",
    # 생활 / 계절
    "선풍기", "서큘레이터", "가습기", "제습기", "에어컨", "냉장고", "김치냉장고", "세탁기", "건조기",
    "공기청정기", "공청기", "밥솥", "에어프라이어", "전자레인지", "식기세척기", "식세기", "인덕션",
    "의자", "게이밍의자", "책상", "모션데스크", "침대", "매트리스", "토퍼", "전기장판", "온수매트", "카본매트",
    # 식품 / 패션 / 뷰티
    "영양제", "비타민", "유산균", "오메가3", "밀크씨슬", "루테인", "단백질", "프로틴",
    "생수", "음료", "탄산수", "사이다", "콜라", "커피", "원두", "라면", "햇반", "쌀",
    "신발", "운동화", "슬리퍼", "티셔츠", "바지", "패딩", "코트", "자켓"
}

_CORE_UNIT_OR_GENERIC_RE = re.compile(
    r'^(?:\d+(?:\.\d+)?(?:kg|g|ml|l|gb|tb|mb|w|v|a|hz|mah|ah|gsm|in1|인치|inch|개입|개|입|캔|병|팩|봉|포|매|판|박스|세트|롤|정|캡슐|구|미|쪽|호|p|ea|pcs|년|개월|일|시간|원|만원|천원)?|국내산|국산|해외|정품|무료|무배|특가|할인|증정|사은품|단품|혼합|선택|외|모음|한정|출시|신제품|신모델|리뉴얼|끌올)$',
    re.IGNORECASE
)


def get_core_product_tokens(d_obj) -> set[str]:
    if isinstance(d_obj, str):
        norm_str = d_obj
    else:
        norm_str = get_normalized_base_name(d_obj) or ""
    tokens = set()
    for w in norm_str.split():
        w_low = w.strip().lower()
        if len(w_low) < 2 or _CORE_UNIT_OR_GENERIC_RE.match(w_low) or w_low in GENERIC_CATEGORY_STOPWORDS:
            continue
        tokens.add(w_low)
    return tokens


@router.get("/deals/{deal_id}")
def get_deal(
    deal_id: int,
    user_id: Optional[str] = Query(default=None),
    db: Session = Depends(get_db_session),
):
    bind_obj = db.get_bind() if hasattr(db, "get_bind") else None
    detail_cache_key = (id(bind_obj), deal_id) if bind_obj is not None else None
    now_detail_ts = _feed_time.time()
    if detail_cache_key is not None:
        with _DEAL_DETAIL_CACHE_LOCK:
            detail_entry = _DEAL_DETAIL_CACHE.get(detail_cache_key)
            if detail_entry is not None and (now_detail_ts - detail_entry["ts"]) >= _DEAL_DETAIL_CACHE_TTL_SEC:
                _DEAL_DETAIL_CACHE.pop(detail_cache_key, None)
                detail_entry = None
        if detail_entry is not None:
            stamp_row = (
                db.query(
                    models.Deal.price,
                    models.Deal.is_closed,
                    models.Deal.comment_count,
                    models.Deal.honey_score,
                    func.length(models.Deal.content_html),
                    models.Deal.title,
                )
                .filter(models.Deal.id == deal_id)
                .first()
            )
            curr_stamp = (
                (
                    stamp_row[0],
                    bool(stamp_row[1]),
                    int(stamp_row[2] or 0),
                    int(stamp_row[3] or 0),
                    int(stamp_row[4] or 0),
                    stamp_row[5] or "",
                )
                if stamp_row is not None
                else None
            )
            if curr_stamp is not None and curr_stamp == detail_entry.get("stamp"):
                with _DEAL_DETAIL_CACHE_LOCK:
                    if detail_cache_key in _DEAL_DETAIL_CACHE:
                        _DEAL_DETAIL_CACHE.move_to_end(detail_cache_key)
                cached_res = copy.deepcopy(detail_entry["data"])
                if user_id:
                    uv_row = (
                        db.query(models.DealVote.vote_type)
                        .filter(
                            models.DealVote.deal_id == deal_id,
                            models.DealVote.user_id == user_id,
                        )
                        .first()
                    )
                    uv_val = uv_row[0] if uv_row else None
                    cached_res["user_vote"] = uv_val
                    cached_res["userVote"] = uv_val
                return cached_res
            with _DEAL_DETAIL_CACHE_LOCK:
                _DEAL_DETAIL_CACHE.pop(detail_cache_key, None)

    from sqlalchemy.orm import joinedload
    deal = db.query(models.Deal).options(
        joinedload(models.Deal.community),
        joinedload(models.Deal.deal_reactions)
    ).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")
        
    COMMUNITY_MAP = _COMMUNITY_DISPLAY_MAP
    
    # 현재 딜과 cluster_key가 동일한 딜들을 모아서 출처(sources) 병합 (최신 250건 범위로 제한하여 응답속도 최적화)
    deal_idx_utc = _to_utc_naive(deal.indexed_at)
    if deal_idx_utc:
        end_time = deal_idx_utc + timedelta(hours=12)
        time_limit = deal_idx_utc - timedelta(days=3)
    else:
        end_time = datetime.now(timezone.utc).replace(tzinfo=None)
        time_limit = end_time - timedelta(days=3)

    from sqlalchemy import or_
    from sqlalchemy.orm import defer

    search_clauses = [models.Deal.id == deal.id]
    n_url = get_normalized_url(deal)
    if deal.ecommerce_link and len(deal.ecommerce_link) > 12:
        search_clauses.append(models.Deal.ecommerce_link == deal.ecommerce_link)
    if n_url:
        m_id_num = re.search(r"^(?:gmarket|auction|11st|coupang|aliexpress|naver|lotteon|ssg|musinsa|ohouse|kakao|himart)_([A-Za-z0-9]+)", n_url)
        if m_id_num and len(m_id_num.group(1)) >= 4:
            esc_id = _escape_like(m_id_num.group(1))
            search_clauses.append(models.Deal.ecommerce_link.ilike(f"%{esc_id}%", escape="\\"))

    curr_model = (getattr(deal, "model_code", None) or "").strip()
    if curr_model and len(curr_model) >= 3:
        esc_model = _escape_like(curr_model)
        search_clauses.append(models.Deal.model_code.ilike(f"%{esc_model}%", escape="\\"))
        search_clauses.append(models.Deal.title.ilike(f"%{esc_model}%", escape="\\"))

    curr_norm_name = get_normalized_base_name(deal) or ""
    curr_all_words = set(curr_norm_name.split()) if curr_norm_name else set()
    for code_tok in list(extract_true_model_codes(curr_all_words))[:3]:
        if len(code_tok) >= 4:
            esc_code = _escape_like(code_tok)
            search_clauses.append(models.Deal.title.ilike(f"%{esc_code}%", escape="\\"))

    core_tokens = sorted(get_core_product_tokens(deal), key=lambda x: len(x), reverse=True)
    for cw in core_tokens[:6]:
        if len(cw) >= 2:
            esc_cw = _escape_like(cw)
            search_clauses.append(models.Deal.title.ilike(f"%{esc_cw}%", escape="\\"))
            search_clauses.append(models.Deal.base_product_name.ilike(f"%{esc_cw}%", escape="\\"))

    base_sim_q = db.query(models.Deal).join(models.Community).options(
        joinedload(models.Deal.community),
        joinedload(models.Deal.deal_reactions),
        defer(models.Deal.content_html),
        defer(models.Deal.search_keywords),
    ).filter(
        models.Deal.indexed_at >= time_limit,
        models.Deal.indexed_at <= end_time,
    )

    if len(search_clauses) > 1:
        similar_deals = base_sim_q.filter(or_(*search_clauses)).order_by(models.Deal.indexed_at.desc()).limit(150).all()
    else:
        similar_deals = [deal]
    
    if not any(d.id == deal.id for d in similar_deals):
        similar_deals.append(deal)
        
    dsu = build_dsu(similar_deals, time_window_days=3)
    cluster_key = dsu.find(deal.id)
    
    site_names = []
    sources = []
    
    best_deal = deal
    min_total_price = float('inf')
    
    for d in similar_deals:
        if dsu.find(d.id) == cluster_key:
            # 현재 딜이 진행 중(is_closed=False)일 때, 이미 종료된 과거 딜(is_closed=True)은
            # 시세 차트(히스토리)에만 남기고 실시간 판매처 비교(sources) 및 현재가 계산에서는 제외
            if d.id != deal.id and getattr(d, 'is_closed', False) and not getattr(deal, 'is_closed', False):
                continue

            c_name = d.community.display_name if d.community and hasattr(d.community, 'display_name') and d.community.display_name else (COMMUNITY_MAP.get(d.community.name, d.community.name) if d.community else "Unknown")
            
            p_val, d_curr = resolve_deal_price_and_currency(d)
            d_norm_ship = _normalize_shipping_fee(d.shipping_fee, title_hint=d.title or "")
            tot_val = _compute_total_price(p_val, d_curr, d_norm_ship)
            tot_krw = _to_krw_equiv(tot_val, d_curr)
            
            if tot_krw > 0 and tot_krw < min_total_price:
                min_total_price = tot_krw
                best_deal = d
            elif tot_krw == 0 and min_total_price == float('inf'):
                best_deal = d
            
            if not any(s.get('post_url') == (d.post_link or "") for s in sources):
                if c_name not in site_names:
                    site_names.append(c_name)
                for new_src in get_deal_sources(d, c_name, p_val, db, currency_override=d_curr):
                    if not any(
                        s.get("site_name") == new_src.get("site_name") and s.get("price") == new_src.get("price")
                        for s in sources
                    ):
                        sources.append(new_src)
                
    if not sources:
        c_name = deal.community.display_name if deal.community and hasattr(deal.community, 'display_name') and deal.community.display_name else (COMMUNITY_MAP.get(deal.community.name, deal.community.name) if deal.community else "Unknown")
        site_names = [c_name]
        p_val, orig_c = resolve_deal_price_and_currency(deal)
        sources = get_deal_sources(deal, c_name, p_val, db, currency_override=orig_c)
        best_deal = deal

    community_name = ", ".join(site_names)
    
    # 클러스터 전체가 종료되었는지 확인 (하나라도 열려있으면 False)
    is_closed = True
    for d in similar_deals:
        if dsu.find(d.id) == cluster_key:
            if not getattr(d, 'is_closed', False):
                is_closed = False
                break
    
    # 최저가 계산 (이종 통화 혼선 방지를 위해 대표 통화와 동일한 옵션 가격만 비교)
    _, curr = resolve_deal_price_and_currency(best_deal)
    same_curr_prices = [s["price"] for s in sources if s.get("price", 0) > 0 and (s.get("currency") or curr) == curr]
    min_price = min(same_curr_prices, default=0)
    original_price, orig_curr = resolve_deal_price_and_currency(deal)
    if orig_curr != curr:
        original_price, _ = resolve_deal_price_and_currency(best_deal)
    final_price_int = min_price if min_price > 0 and (original_price == 0 or min_price < original_price) else original_price
                     
    clean_detail_ecom_url = get_clean_ecommerce_url(
        best_deal.ecommerce_link or best_deal.post_link, 
        getattr(best_deal, "content_html", "") or "", 
        best_deal.id, 
        db, 
        is_multi_item=(
            (getattr(best_deal, 'ecommerce_link', None) == getattr(best_deal, 'post_link', None)) or 
            (getattr(best_deal, 'title', '') and ("," in best_deal.title or "모음" in best_deal.title or "선택" in best_deal.title or "외/" in best_deal.title)) or 
            ("[모음전]" in (getattr(best_deal, 'ai_summary', '') or ''))
        )
    )
    detail_ecom_url = clean_detail_ecom_url
    if not detail_ecom_url:
        detail_ecom_url = _build_direct_mall_search_url(getattr(best_deal, "shop_name", "") or "", getattr(best_deal, "title", "") or deal.title or "", getattr(best_deal, "post_link", "") or "", community_name)
    is_search_fallback = _compute_is_search_fallback(clean_detail_ecom_url, detail_ecom_url)

    raw_detail_content = getattr(best_deal, "content_html", "") or getattr(deal, "content_html", "") or ""
    body_images = _extract_body_images(raw_detail_content)
    report_count = db.query(models.DealClosedReport).filter(
        models.DealClosedReport.deal_id == deal_id
    ).count()

    from backend.core.deal_normalizer import parse_title_price_and_shipping
    title_for_free = getattr(best_deal, "title", None) or getattr(deal, "title", "") or ""
    _, _, _, is_explicit_free = parse_title_price_and_shipping(title_for_free)
    if not is_explicit_free and getattr(deal, "title", None) and deal.title != title_for_free:
        _, _, _, is_explicit_free = parse_title_price_and_shipping(deal.title)
    is_free = bool(is_explicit_free and final_price_int == 0)

    deal_votes = db.query(models.DealVote).filter(models.DealVote.deal_id == deal_id).all()
    buy_count = sum(1 for v in deal_votes if v.vote_type == "buy")
    pass_count = sum(1 for v in deal_votes if v.vote_type in ("save", "pass"))
    user_vote = None
    if user_id:
        uv = next((v for v in deal_votes if v.user_id == user_id), None)
        if uv:
            user_vote = uv.vote_type

    active_comment_count = db.query(models.DealComment).filter(
        models.DealComment.deal_id == deal_id,
        models.DealComment.is_deleted == False,
    ).count()

    initial_history: list = []
    initial_history_period: str = "7d"
    try:
        hist_target_id = best_deal.id if (original_price == 0 and min_price > 0) else deal_id
        hist_points = _compute_deal_history(hist_target_id, "7d", db, target_currency=curr)
        if isinstance(hist_points, list):
            initial_history = hist_points
        if len(initial_history) <= 1:
            hist_3m = _compute_deal_history(hist_target_id, "3m", db, target_currency=curr)
            if isinstance(hist_3m, list) and len(hist_3m) >= 2:
                initial_history = hist_3m
                initial_history_period = "3m"
        history_count = len(initial_history)
    except Exception as hist_exc:
        logger.debug(f"[get_deal] history_count fallback (deal_id={deal_id}): {hist_exc}")
        initial_history = []
        initial_history_period = "7d"
        history_count = 0

    raw_detail_opts = getattr(best_deal, "options_data", None) or getattr(deal, "options_data", None)
    serialized_detail_opts = _serialize_options_data(raw_detail_opts, best_deal.id)
    detail_shipping_fee = _normalize_shipping_fee(
        getattr(best_deal, "shipping_fee", None) or getattr(deal, "shipping_fee", None),
        title_hint=getattr(best_deal, "title", "") or deal.title or "",
    )
    detail_shop_name = _resolve_canonical_shop_name(best_deal, detail_ecom_url) or _resolve_canonical_shop_name(deal, detail_ecom_url)

    detail_payload = {
        "id": deal.id,
        "title": clean_deal_title(deal.title or ""),
        "price": final_price_int,
        "shipping_fee": detail_shipping_fee,
        "shop_name": detail_shop_name,
        "post_url": best_deal.post_link,
        "ecommerce_url": detail_ecom_url,
        "image_url": _resolve_best_image_url(best_deal, BASE_URL) or _resolve_best_image_url(deal, BASE_URL),
        "site_name": community_name,
        "site_names": site_names,
        "currency": curr,
        "sources": sources,
        "options_data": serialized_detail_opts,
        "category": normalize_category_key(getattr(best_deal, "category", None) or getattr(deal, "category", None)),
        "author": getattr(best_deal, "author", "익명") or "익명",
        "views": getattr(best_deal, "view_count", 0) or 0,
        "recommendations": getattr(best_deal, "like_count", 0) or 0,
        "comments": getattr(best_deal, "comment_count", 0) or 0,
        "indexed_at": to_utc_iso(deal.indexed_at),
        "is_closed": is_closed,
        "score": getattr(best_deal, "honey_score", 0) or getattr(deal, "honey_score", 0),
        "ai_summary": _clean_ai_summary(getattr(best_deal, "ai_summary", "") or getattr(deal, "ai_summary", "") or ""),
        "content_html": _clean_content_text(raw_detail_content, deal.title or ""),
        "has_options": bool(getattr(best_deal, "has_options", False) or getattr(deal, "has_options", False) or serialized_detail_opts),
        "buy_count": buy_count,
        "buyCount": buy_count,
        "pass_count": pass_count,
        "saveCount": pass_count,
        "passCount": pass_count,
        "user_vote": None,
        "userVote": None,
        "comment_count": active_comment_count,
        "commentCount": active_comment_count,
        "history_count": history_count,
        "historyCount": history_count,
        "initial_history": initial_history,
        "initialHistory": initial_history,
        "initial_history_period": initial_history_period,
        "initialHistoryPeriod": initial_history_period,
        "is_search_fallback": is_search_fallback,
        "isSearchFallback": is_search_fallback,
        "is_free": is_free,
        "body_images": body_images,
        "bodyImages": body_images,
        "report_count": report_count,
        "reportCount": report_count,
    }
    if detail_cache_key is not None:
        fresh_stamp = (
            deal.price,
            bool(deal.is_closed),
            int(deal.comment_count or 0),
            int(deal.honey_score or 0),
            len(deal.content_html or ""),
            deal.title or "",
        )
        with _DEAL_DETAIL_CACHE_LOCK:
            _DEAL_DETAIL_CACHE[detail_cache_key] = {
                "ts": _feed_time.time(),
                "stamp": fresh_stamp,
                "data": copy.deepcopy(detail_payload),
            }
            _DEAL_DETAIL_CACHE.move_to_end(detail_cache_key)
            while len(_DEAL_DETAIL_CACHE) > _DEAL_DETAIL_CACHE_MAX_SIZE:
                _DEAL_DETAIL_CACHE.popitem(last=False)

    detail_payload["user_vote"] = user_vote
    detail_payload["userVote"] = user_vote
    return detail_payload


@router.post("/deals/{deal_id}/report-closed")
def report_deal_closed(
    deal_id: int,
    request: Request,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    """사용자 원클릭 품절/종료 제보 (X-Device-Token 필수, 3명 누적 시 자동 종료 전환)"""
    now_ts = _time.time()
    recent = [
        ts for ts in _REPORT_CLOSED_RATE_LIMIT.get(auth_uuid, [])
        if (now_ts - ts) < _REPORT_CLOSED_WINDOW_SEC
    ]
    if len(recent) >= _REPORT_CLOSED_HOURLY_LIMIT:
        _REPORT_CLOSED_RATE_LIMIT[auth_uuid] = recent
        raise HTTPException(status_code=429, detail="Too many closed reports; please try again later")

    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")

    existing = db.query(models.DealClosedReport).filter(
        models.DealClosedReport.deal_id == deal_id,
        models.DealClosedReport.device_uuid == auth_uuid,
    ).first()
    if existing is not None:
        raise HTTPException(status_code=409, detail="Already reported by this device")

    recent.append(now_ts)
    _REPORT_CLOSED_RATE_LIMIT[auth_uuid] = recent

    try:
        db.add(models.DealClosedReport(deal_id=deal_id, device_uuid=auth_uuid))
        db.flush()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="Already reported by this device")

    report_count = db.query(models.DealClosedReport).filter(
        models.DealClosedReport.deal_id == deal_id
    ).count()

    if report_count >= _REPORT_CLOSED_THRESHOLD and not deal.is_closed:
        deal.is_closed = True

    db.commit()
    _evict_deal_detail_cache(deal_id)
    invalidate_feed_api_cache()

    is_closed_val = bool(deal.is_closed)
    return {
        "status": "ok",
        "deal_id": deal_id,
        "dealId": deal_id,
        "report_count": report_count,
        "reportCount": report_count,
        "is_closed": is_closed_val,
        "isClosed": is_closed_val,
        "threshold": _REPORT_CLOSED_THRESHOLD,
    }

@router.get("/deals/{deal_id}/history")
def get_deal_history(
    deal_id: int, 
    period: str = "7d", 
    option_name: Optional[str] = Query(None, description="선택된 멀티옵션 상품명"),
    query: Optional[str] = Query(None, description="선택된 옵션/검색어 쿼리 (안드로이드 호환)"),
    currency: Optional[str] = Query(None, description="선택된 통화 (KRW, USD)"),
    db: Session = Depends(get_db_session)
):
    opt_query = option_name or query
    period_norm = (period or "7d").strip().lower()
    auto_fallback = (period_norm == "7d_auto")
    effective_period = "7d" if auto_fallback else period_norm
    res = _compute_deal_history(deal_id, effective_period, db, option_name=opt_query, target_currency=currency)
    if auto_fallback and len(res) <= 1:
        res_3m = _compute_deal_history(deal_id, "3m", db, option_name=opt_query, target_currency=currency)
        if len(res_3m) >= 2:
            return res_3m
        res_6m = _compute_deal_history(deal_id, "6m", db, option_name=opt_query, target_currency=currency)
        if len(res_6m) >= 2:
            return res_6m
    return res

def _compute_deal_history(
    deal_id: int, 
    period: str = "7d", 
    db: Session = None,
    option_name: Optional[str] = None,
    target_currency: Optional[str] = None
):
    from backend.core.deal_normalizer import is_suspicious_price_history_value

    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")

    resolved_deal_price, resolved_deal_curr = resolve_deal_price_and_currency(deal)
    target_opt_name = (option_name or "").strip()
    target_opt_price = 0
    effective_currency = (target_currency or resolved_deal_curr or "KRW").strip().upper()

    if target_opt_name and deal.options_data:
        try:
            opts = json.loads(deal.options_data) if isinstance(deal.options_data, str) else deal.options_data
            if isinstance(opts, list):
                for opt in opts:
                    o_name = (opt.get("name") or "").strip()
                    if o_name and (o_name == target_opt_name or target_opt_name in o_name or o_name in target_opt_name):
                        target_opt_price = int(opt.get("price", 0) or 0)
                        if not target_currency and opt.get("currency"):
                            effective_currency = str(opt.get("currency")).strip().upper()
                        break
        except Exception as e:
            logger.debug(f"Error parsing options_data in history: {e}")

    if target_opt_price > 0:
        target_price_val = target_opt_price
    elif resolved_deal_curr == effective_currency:
        target_price_val = resolved_deal_price
    else:
        target_price_val = 0

    # 0원('가격 확인') 단일 딜은 과거 더미 잔재(777원, 10원)나 타 상품 가격이 섞이지 않도록 즉시 빈 배열 반환
    if not target_opt_name and resolved_deal_curr == effective_currency and target_price_val <= 0:
        return []

    period_norm = (period or "7d").strip().lower()
    if period_norm == "7d_auto":
        period_norm = "7d"

    hist_cache_key = (id(db.get_bind()), deal_id, period_norm, target_opt_name, effective_currency)
    now_hist_ts = _feed_time.time()
    with _DEAL_HISTORY_CACHE_LOCK:
        hist_entry = _DEAL_HISTORY_CACHE.get(hist_cache_key)
        if hist_entry is not None:
            if (now_hist_ts - hist_entry["ts"]) < _DEAL_HISTORY_CACHE_TTL_SEC:
                _DEAL_HISTORY_CACHE.move_to_end(hist_cache_key)
                return [dict(pt) for pt in hist_entry["data"]]
            _DEAL_HISTORY_CACHE.pop(hist_cache_key, None)

    now_utc = datetime.now(timezone.utc).replace(tzinfo=None)
    ref_base_utc = _to_utc_naive(deal.indexed_at) if deal.indexed_at else now_utc
    end_time_utc = max(now_utc, ref_base_utc) + timedelta(minutes=5)

    is_all_period = (period_norm == "all")
    days_map = {
        "7d": 7,
        "1m": 30,
        "3m": 90,
        "6m": 180,
        "1y": 365,
        "all": 3650,
    }
    days = days_map.get(period_norm, 7)
    time_limit_utc = None if is_all_period else (ref_base_utc - timedelta(days=days))

    if target_opt_name:
        curr_norm_name = target_opt_name
        curr_all_words = set(re.findall(r'[가-힣a-zA-Z0-9]+', target_opt_name))
        curr_true_codes = extract_true_model_codes(curr_all_words)
        curr_model = ""
        core_tokens = get_core_product_tokens(target_opt_name)
    else:
        curr_model = (getattr(deal, "model_code", None) or "").strip()
        curr_norm_name = get_normalized_base_name(deal) or ""
        curr_all_words = set(curr_norm_name.split()) if curr_norm_name else set()
        curr_true_codes = extract_true_model_codes(curr_all_words)
        if curr_model and len(curr_model) >= 4:
            curr_true_codes.add(curr_model.lower().replace("-", ""))
        core_tokens = get_core_product_tokens(deal)

    n_url = get_normalized_url(deal)

    from sqlalchemy import or_
    search_clauses = []
    if curr_model and len(curr_model) >= 3:
        esc_model = _escape_like(curr_model)
        search_clauses.append(models.Deal.model_code.ilike(f"%{esc_model}%", escape="\\"))
        search_clauses.append(models.Deal.title.ilike(f"%{esc_model}%", escape="\\"))
    for code_tok in list(curr_true_codes)[:2]:
        esc_code = _escape_like(code_tok)
        search_clauses.append(models.Deal.title.ilike(f"%{esc_code}%", escape="\\"))

    sorted_core = sorted(core_tokens, key=lambda x: len(x), reverse=True)
    for cw in sorted_core[:3]:
        if len(cw) >= 2 and cw not in GENERIC_CATEGORY_STOPWORDS:
            esc_cw = _escape_like(cw)
            search_clauses.append(models.Deal.title.ilike(f"%{esc_cw}%", escape="\\"))

    query = (
        db.query(models.Deal)
        .options(defer(models.Deal.content_html), defer(models.Deal.search_keywords))
        .filter(models.Deal.indexed_at <= end_time_utc)
    )
    if time_limit_utc is not None:
        query = query.filter(models.Deal.indexed_at >= time_limit_utc)

    # 옵션명이 지정되었으나 모델코드나 식별 토큰이 부족하면 타 딜 교차 매칭을 제한하여 노이즈 방어
    if target_opt_name and not curr_true_codes and len(core_tokens) <= 1:
        candidate_deals = [deal]
    elif search_clauses:
        candidate_deals = query.filter(or_(*search_clauses)).order_by(models.Deal.indexed_at.asc()).limit(450).all()
    else:
        candidate_deals = [deal]

    # 본문 가격 자가치유가 필요한 소수 행(KRW 소액)만 선택 배치 로드하여 N+1 차단
    _hydrate_suspect_krw_cents_content_html(db, candidate_deals)

    target_is_collection = bool(re.search(r'(?:외\s*\d+종|모음전|골라담기)', deal.title or ""))

    matched_deals = []
    for d in candidate_deals:
        if d.id == deal.id:
            if target_opt_name or resolved_deal_curr == effective_currency:
                matched_deals.append(d)
            continue

        d_price, d_curr = resolve_deal_price_and_currency(d)
        if effective_currency and d_curr != effective_currency:
            continue

        if d_price <= 0:
            continue

        if not target_is_collection and re.search(r'(?:외\s*\d+종|모음전|골라담기)', d.title or ""):
            continue

        if target_price_val > 0:
            if d_price < target_price_val * 0.40 or d_price > target_price_val * 2.20:
                continue

        d_norm_name = get_normalized_base_name(d) or ""
        d_all_words = set(d_norm_name.split()) if d_norm_name else set()
        d_core_tokens = get_core_product_tokens(d)

        if has_quantity_conflict(curr_all_words, d_all_words):
            continue
        if has_model_conflict(curr_all_words, d_all_words):
            continue
        if has_brand_or_keyword_conflict(curr_all_words, d_all_words):
            continue

        # 옵션 검색 시 고유 식별자(모델코드 또는 2개 이상의 핵심 토큰) 일치 강제
        if target_opt_name:
            d_true_codes = extract_true_model_codes(d_all_words)
            d_model = (getattr(d, "model_code", None) or "").strip()
            if d_model and len(d_model) >= 4:
                d_true_codes.add(d_model.lower().replace("-", ""))
            has_model_match = bool(curr_true_codes and d_true_codes and curr_true_codes.intersection(d_true_codes))
            has_core_match = len(core_tokens.intersection(d_core_tokens)) >= 2
            if has_model_match or has_core_match:
                matched_deals.append(d)
            continue

        # 1) 쇼핑몰 고유 상품 키(Canonical URL Key) 일치
        d_url = get_normalized_url(d)
        if n_url and d_url and n_url == d_url and not any(x in n_url for x in ["ppomppu", "fmkorea", "ruliweb", "quasarzone", "clien", "bbasak"]):
            if len(core_tokens.intersection(d_core_tokens)) >= 1:
                matched_deals.append(d)
                continue

        # 2) 고유 제조사 모델코드(예: WIZ040, LS32FM701 등) 일치
        d_true_codes = extract_true_model_codes(d_all_words)
        d_model = (getattr(d, "model_code", None) or "").strip()
        if d_model and len(d_model) >= 4:
            d_true_codes.add(d_model.lower().replace("-", ""))
        if curr_true_codes and d_true_codes and curr_true_codes.intersection(d_true_codes):
            if len(core_tokens.intersection(d_core_tokens)) >= 2:
                matched_deals.append(d)
                continue

        # 3) 순수 상품명 핵심 토큰(단위/수량 제외) 유사도 검사
        if not core_tokens or not d_core_tokens:
            continue

        core_inter = core_tokens.intersection(d_core_tokens)
        core_union = core_tokens.union(d_core_tokens)
        core_jaccard = len(core_inter) / len(core_union) if core_union else 0
        core_min_len = min(len(core_tokens), len(d_core_tokens))
        core_subset = len(core_inter) / core_min_len if core_min_len else 0

        if (core_min_len == 1 and core_jaccard == 1.0) or (
            len(core_inter) >= 2 and (core_jaccard >= 0.55 or (core_subset >= 0.80 and len(core_inter) >= 2))
        ):
            matched_deals.append(d)

    matched_deal_ids = [d.id for d in matched_deals]
    if deal.id not in matched_deal_ids and (target_opt_name or resolved_deal_curr == effective_currency):
        matched_deals.append(deal)
        matched_deal_ids.append(deal.id)

    if not matched_deals:
        return []

    fallback_kst_now = datetime.now(_KST)

    def normalize_kst_dt(dt_val) -> datetime:
        converted = _to_kst_aware(dt_val)
        return converted if converted is not None else fallback_kst_now

    deal_obj_map = {d.id: d for d in matched_deals}

    def resolve_deal_effective_price(d_obj) -> int:
        if target_opt_name and getattr(d_obj, "options_data", None):
            try:
                opts = json.loads(d_obj.options_data) if isinstance(d_obj.options_data, str) else d_obj.options_data
                if isinstance(opts, list):
                    for opt in opts:
                        o_name = (opt.get("name") or "").strip()
                        if not o_name:
                            continue
                        o_curr = (opt.get("currency") or getattr(d_obj, "currency", "KRW") or "KRW").strip().upper()
                        if effective_currency and o_curr != effective_currency:
                            continue
                        o_words = set(re.findall(r'[가-힣a-zA-Z0-9]+', o_name))
                        o_codes = extract_true_model_codes(o_words)
                        o_core = get_core_product_tokens(o_name)
                        if (
                            o_name == target_opt_name
                            or target_opt_name in o_name
                            or o_name in target_opt_name
                            or (curr_true_codes and o_codes and curr_true_codes == o_codes)
                            or (len(core_tokens) >= 2 and len(core_tokens.intersection(o_core)) >= 2 and not has_model_conflict(curr_all_words, o_words))
                        ):
                            p = int(opt.get("price", 0) or 0)
                            if p > 0:
                                return p
            except Exception as exc:
                logger.debug(f"resolve_deal_effective_price options_data parse skipped: {exc}")
        d_p, d_c = resolve_deal_price_and_currency(d_obj)
        if effective_currency and d_c != effective_currency:
            return 0
        return d_p

    deal_price_map = {d.id: resolve_deal_effective_price(d) for d in matched_deals}

    # 2. 커뮤니티 핫딜 가격 히스토리(PriceHistory) 조회
    ph_query = db.query(models.PriceHistory).filter(
        models.PriceHistory.deal_id.in_(matched_deal_ids),
        models.PriceHistory.checked_at <= end_time_utc,
    )
    if time_limit_utc is not None:
        ph_query = ph_query.filter(models.PriceHistory.checked_at >= time_limit_utc)
    history = ph_query.order_by(models.PriceHistory.checked_at.asc(), models.PriceHistory.id.asc()).all()

    time_format = "%m.%d %H:%M" if days <= 7 else "%y.%m.%d"

    community_points = []
    raw_comm_events: list[dict] = []
    ph_by_deal: dict[int, list[tuple[datetime, int]]] = {}

    # 2-1. PriceHistory 데이터 수집 (반등 곡선은 보존하되 과거 오파싱 잔재·통화 불일치·옵션 스윙 노이즈 차단)
    for h in history:
        try:
            h_deal = deal_obj_map.get(h.deal_id)
            if target_opt_name:
                continue
            if h_deal and (getattr(h_deal, "has_options", False) or getattr(h_deal, "options_data", None)):
                continue
            if h_deal is not None:
                _, h_curr = resolve_deal_price_and_currency(h_deal)
                if effective_currency and h_curr != effective_currency:
                    continue
            p_val = int(re.sub(r'[^\d]', '', str(h.price)))
            if is_suspicious_price_history_value(
                p_val,
                effective_currency,
                getattr(h_deal, "title", "") if h_deal else "",
            ):
                continue
            ref_p_for_h = deal_price_map.get(h.deal_id) or target_price_val
            if ref_p_for_h <= 0:
                continue
            if p_val < ref_p_for_h * 0.40 or p_val > ref_p_for_h * 2.20:
                continue
            if target_price_val > 0 and (p_val < target_price_val * 0.40 or p_val > target_price_val * 2.20):
                continue
            dt = normalize_kst_dt(h.checked_at)
            ph_by_deal.setdefault(h.deal_id, []).append((dt, p_val))
        except Exception as exc:
            logger.debug(f"_compute_deal_history price_history row skipped: {exc}")

    # 2-2. 각 매칭 Deal별 시계열 이벤트 구성
    seq_counter = 0
    for d in matched_deals:
        try:
            is_current = (d.id == deal.id)
            p_val = target_opt_price if (is_current and target_opt_price > 0) else deal_price_map.get(d.id, 0)
            if not is_current and p_val > 0 and target_price_val > 0:
                if p_val < target_price_val * 0.40 or p_val > target_price_val * 2.20:
                    continue

            ph_rows = sorted(ph_by_deal.get(d.id, []), key=lambda item: item[0])
            if ph_rows:
                for ph_dt, ph_price in ph_rows:
                    raw_comm_events.append({
                        "deal_id": d.id,
                        "dt": ph_dt,
                        "price": ph_price,
                        "seq": seq_counter,
                    })
                    seq_counter += 1
                if p_val > 0 and ph_rows[-1][1] != p_val:
                    synth_dt = max(normalize_kst_dt(d.indexed_at), ph_rows[-1][0] + timedelta(seconds=1))
                    raw_comm_events.append({
                        "deal_id": d.id,
                        "dt": synth_dt,
                        "price": p_val,
                        "seq": seq_counter,
                    })
                    seq_counter += 1
            elif p_val > 0:
                raw_comm_events.append({
                    "deal_id": d.id,
                    "dt": normalize_kst_dt(d.indexed_at),
                    "price": p_val,
                    "seq": seq_counter,
                })
                seq_counter += 1
        except Exception as exc:
            logger.debug(f"_compute_deal_history deal row skipped: {exc}")

    raw_comm_events.sort(key=lambda ev: (ev["dt"], ev["seq"]))

    for ev in raw_comm_events:
        dt = ev["dt"]
        p_val = ev["price"]
        base_label = dt.strftime(time_format).replace(" 00:00", "")

        if community_points:
            prev = community_points[-1]
            # 동일 딜에서 연속된 동일 가격 스냅샷이거나 동일 라벨·동일 가격인 중복 포인트만 병합 (20,000 -> 25,000 -> 20,000 반등 곡선은 유지)
            if prev["price"] == p_val and (prev["deal_id"] == ev["deal_id"] or prev["recordedAt"] == base_label):
                continue

        community_points.append({
            "price": p_val,
            "originalPrice": None,
            "discountRate": None,
            "recordedAt": base_label,
            "source": "community",
            "dt": dt,
            "deal_id": ev["deal_id"],
        })

    # 3. 네이버 쇼핑 최저가 히스토리(NaverPriceHistory) 조회 및 병합 (원화 단일 상품 전용)
    naver_points = []
    if not target_opt_name and effective_currency == "KRW" and target_price_val > 0:
        target_model_code = deal.model_code if deal.model_code else (deal.base_product_name if deal.base_product_name else None)
        if deal.brand and target_model_code:
            nh_query = db.query(models.NaverPriceHistory).filter(
                models.NaverPriceHistory.brand == deal.brand,
                models.NaverPriceHistory.model_code == target_model_code,
                models.NaverPriceHistory.checked_at <= end_time_utc,
            )
            if time_limit_utc is not None:
                nh_query = nh_query.filter(models.NaverPriceHistory.checked_at >= time_limit_utc)
            naver_history = nh_query.order_by(models.NaverPriceHistory.checked_at.asc()).all()

            naver_map = {}
            for nh in naver_history:
                try:
                    p_val = int(nh.price)
                    if p_val <= 0 or p_val < target_price_val * 0.40 or p_val > target_price_val * 2.20:
                        continue
                    dt = normalize_kst_dt(nh.checked_at)
                    time_key = dt.strftime(time_format)
                    if time_key not in naver_map or p_val < naver_map[time_key]["price"]:
                        naver_map[time_key] = {"price": p_val, "dt": dt, "source": "naver"}
                except Exception as exc:
                    logger.debug(f"_compute_deal_history naver_history row skipped: {exc}")

            for k, v in naver_map.items():
                naver_points.append({
                    "price": v["price"],
                    "originalPrice": None,
                    "discountRate": None,
                    "recordedAt": k.replace(" 00:00", ""),
                    "source": "naver",
                    "dt": v["dt"]
                })

    # 4. 두 최저가 스트림을 통합하여 날짜(dt) 순으로 오름차순 정렬
    all_points = community_points + naver_points
    all_points.sort(key=lambda x: x["dt"])

    # 5. 최종 반환 형태 정제 (currency 및 UTC Z ISO-8601 타임스탬프 + KST 차트 라벨 recordedAt 유지)
    result = []
    for p in all_points:
        iso_utc = to_utc_iso(p["dt"])
        result.append({
            "price": p["price"],
            "currency": effective_currency,
            "originalPrice": p["originalPrice"],
            "discountRate": p["discountRate"],
            "recordedAt": p["recordedAt"],
            "timestamp": iso_utc,
            "recorded_at": iso_utc,
            "source": p["source"],
            "platform": p["source"],
        })

    with _DEAL_HISTORY_CACHE_LOCK:
        while len(_DEAL_HISTORY_CACHE) >= _DEAL_HISTORY_CACHE_MAX_SIZE:
            _DEAL_HISTORY_CACHE.popitem(last=False)
        _DEAL_HISTORY_CACHE[hist_cache_key] = {
            "ts": _feed_time.time(),
            "data": [dict(pt) for pt in result],
        }

    return result

# --- 소비 참견 거지방 API ---

class CommentCreate(BaseModel):
    user_id: Optional[str] = None
    nickname: str = "익명의찰떡이"
    content: str

class VoteCreate(BaseModel):
    user_id: Optional[str] = None
    vote_type: str # "buy" or "save" (or "pass")


def _serialize_deal_comment(c: models.DealComment) -> dict:
    created_utc = to_utc_iso(c.created_at)
    return {
        "id": c.id,
        "deal_id": c.deal_id,
        "dealId": c.deal_id,
        "user_id": c.user_id,
        "userId": c.user_id,
        "nickname": c.nickname,
        "content": c.content,
        "created_at": created_utc,
        "createdAt": created_utc,
    }


def _serialize_deal_votes(buy_count: int, pass_count: int, user_vote: Optional[str]) -> dict:
    return {
        "buy_count": buy_count,
        "buyCount": buy_count,
        "pass_count": pass_count,
        "saveCount": pass_count,
        "passCount": pass_count,
        "user_vote": user_vote,
        "userVote": user_vote,
    }


@router.get("/deals/{deal_id}/comments")
def get_deal_comments(deal_id: int, db: Session = Depends(get_db_session)):
    comments = db.query(models.DealComment).filter(
        models.DealComment.deal_id == deal_id,
        models.DealComment.is_deleted == False
    ).order_by(models.DealComment.created_at.desc()).all()

    return [_serialize_deal_comment(c) for c in comments]


@router.post("/deals/{deal_id}/comments")
def add_deal_comment(
    deal_id: int,
    comment: CommentCreate,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    owner_uuid = assert_device_owner(auth_uuid, comment.user_id)
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")

    new_comment = models.DealComment(
        deal_id=deal_id,
        user_id=owner_uuid,
        nickname=comment.nickname,
        content=comment.content
    )
    db.add(new_comment)

    # 딜 반응 업데이트
    reaction = db.query(models.DealReaction).filter(models.DealReaction.deal_id == deal_id).first()
    if not reaction:
        reaction = models.DealReaction(deal_id=deal_id, comment_count=1)
        db.add(reaction)
    else:
        reaction.comment_count = (reaction.comment_count or 0) + 1

    deal.comment_count = (deal.comment_count or 0) + 1

    db.commit()
    _evict_deal_detail_cache(deal_id)
    db.refresh(new_comment)

    return _serialize_deal_comment(new_comment)


@router.delete("/deals/{deal_id}/comments/{comment_id}")
def delete_deal_comment(
    deal_id: int,
    comment_id: int,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    comment = db.query(models.DealComment).filter(
        models.DealComment.id == comment_id,
        models.DealComment.deal_id == deal_id,
        models.DealComment.is_deleted == False,
    ).first()
    if not comment:
        raise HTTPException(status_code=404, detail="Comment not found")
    if comment.user_id != auth_uuid:
        raise HTTPException(status_code=403, detail="Only the comment creator can delete")

    comment.is_deleted = True

    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if deal:
        deal.comment_count = max(0, (deal.comment_count or 0) - 1)

    reaction = db.query(models.DealReaction).filter(models.DealReaction.deal_id == deal_id).first()
    if reaction:
        reaction.comment_count = max(0, (reaction.comment_count or 0) - 1)

    db.commit()
    _evict_deal_detail_cache(deal_id)
    return {
        "status": "ok",
        "message": "Comment deleted successfully",
        "id": comment.id,
        "deal_id": deal_id,
        "dealId": deal_id,
    }


@router.get("/deals/{deal_id}/votes")
def get_deal_votes(deal_id: int, user_id: Optional[str] = None, db: Session = Depends(get_db_session)):
    votes = db.query(models.DealVote).filter(models.DealVote.deal_id == deal_id).all()

    buy_count = sum(1 for v in votes if v.vote_type == "buy")
    pass_count = sum(1 for v in votes if v.vote_type in ("save", "pass"))

    user_vote = None
    if user_id:
        uv = next((v for v in votes if v.user_id == user_id), None)
        if uv:
            user_vote = uv.vote_type

    return _serialize_deal_votes(buy_count, pass_count, user_vote)


@router.post("/deals/{deal_id}/votes")
def add_deal_vote(
    deal_id: int,
    vote: VoteCreate,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    owner_uuid = assert_device_owner(auth_uuid, vote.user_id)
    deal = db.query(models.Deal).filter(models.Deal.id == deal_id).first()
    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")

    if vote.vote_type not in ("buy", "save", "pass"):
        raise HTTPException(status_code=400, detail="Invalid vote_type. Must be 'buy' or 'save'")
    normalized_vote = "save" if vote.vote_type == "pass" else vote.vote_type

    existing_vote = db.query(models.DealVote).filter(
        models.DealVote.deal_id == deal_id,
        models.DealVote.user_id == owner_uuid
    ).first()

    if existing_vote:
        # 기존 투표 변경 또는 취소
        if existing_vote.vote_type == normalized_vote or (
            existing_vote.vote_type in ("save", "pass") and normalized_vote == "save"
        ):
            # 같은 투표 한 번 더 누르면 취소
            db.delete(existing_vote)
        else:
            # 다른 투표로 변경
            existing_vote.vote_type = normalized_vote
    else:
        # 새로 투표
        new_vote = models.DealVote(
            deal_id=deal_id,
            user_id=owner_uuid,
            vote_type=normalized_vote
        )
        db.add(new_vote)

    db.commit()

    # 갱신된 투표 수 계산
    all_votes = db.query(models.DealVote).filter(models.DealVote.deal_id == deal_id).all()
    buy_count = sum(1 for v in all_votes if v.vote_type == "buy")
    pass_count = sum(1 for v in all_votes if v.vote_type in ("save", "pass"))

    # 딜 반응 업데이트
    reaction = db.query(models.DealReaction).filter(models.DealReaction.deal_id == deal_id).first()
    if not reaction:
        reaction = models.DealReaction(
            deal_id=deal_id,
            positive_reactions=buy_count,
            negative_reactions=pass_count
        )
        db.add(reaction)
    else:
        reaction.positive_reactions = buy_count
        reaction.negative_reactions = pass_count

    db.commit()
    _evict_deal_detail_cache(deal_id)

    uv = db.query(models.DealVote).filter(
        models.DealVote.deal_id == deal_id,
        models.DealVote.user_id == owner_uuid
    ).first()

    return _serialize_deal_votes(buy_count, pass_count, uv.vote_type if uv else None)

# --- UGC Community Features (역경매 & 오프라인 핫딜) ---

class CommunityPostCreate(BaseModel):
    post_type: str
    user_id: Optional[str] = None
    nickname: str
    title: str
    content: str
    target_price: Optional[int] = None
    bounty_points: Optional[int] = 0
    like_count: Optional[int] = 0
    location: Optional[str] = None

class CommunityPostCommentCreate(BaseModel):
    user_id: Optional[str] = None
    nickname: str
    content: str
    deal_url: Optional[str] = None
    parent_id: Optional[int] = None


def _serialize_community_post(p: models.CommunityPost) -> dict:
    created_utc = to_utc_iso(p.created_at)
    return {
        "id": p.id,
        "post_type": p.post_type,
        "user_id": p.user_id,
        "userId": p.user_id,
        "nickname": p.nickname,
        "title": p.title,
        "content": p.content,
        "target_price": p.target_price,
        "bounty_points": p.bounty_points,
        "is_resolved": p.is_resolved,
        "location": p.location,
        "view_count": p.view_count,
        "like_count": p.like_count,
        "created_at": created_utc,
        "createdAt": created_utc,
    }


def _serialize_community_post_comment(c: models.CommunityPostComment) -> dict:
    created_utc = to_utc_iso(c.created_at)
    return {
        "id": c.id,
        "user_id": c.user_id,
        "userId": c.user_id,
        "nickname": c.nickname,
        "content": c.content,
        "deal_url": c.deal_url,
        "is_accepted": c.is_accepted,
        "parent_id": c.parent_id,
        "created_at": created_utc,
        "createdAt": created_utc,
    }


@router.post("/posts")
def create_community_post(
    post: CommunityPostCreate,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session),
):
    owner_uuid = assert_device_owner(auth_uuid, post.user_id)
    new_post = models.CommunityPost(
        post_type=post.post_type,
        user_id=owner_uuid,
        nickname=post.nickname,
        title=post.title,
        content=post.content,
        target_price=post.target_price,
        bounty_points=post.bounty_points,
        like_count=post.like_count if post.like_count is not None else 0,
        location=post.location
    )
    db.add(new_post)
    db.commit()
    db.refresh(new_post)
    return _serialize_community_post(new_post)


@router.put("/posts/{post_id}")
def update_community_post(
    post_id: int,
    post_data: CommunityPostCreate,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session)
):
    owner_uuid = assert_device_owner(auth_uuid, post_data.user_id)
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")

    if post.user_id != owner_uuid:
        raise HTTPException(status_code=403, detail="Only the post creator can edit")

    post.title = post_data.title
    post.content = post_data.content
    post.target_price = post_data.target_price
    post.bounty_points = post_data.bounty_points
    if post_data.like_count is not None:
        post.like_count = post_data.like_count
    post.location = post_data.location
    post.post_type = post_data.post_type

    db.commit()
    db.refresh(post)
    return _serialize_community_post(post)


@router.get("/posts")
def get_community_posts(
    post_type: Optional[str] = None,
    skip: int = 0,
    limit: int = 20,
    db: Session = Depends(get_db_session)
):
    query = db.query(models.CommunityPost)
    if post_type:
        query = query.filter(models.CommunityPost.post_type == post_type)

    posts = query.order_by(models.CommunityPost.created_at.desc()).offset(skip).limit(limit).all()

    result = []
    for p in posts:
        item = _serialize_community_post(p)
        item["comment_count"] = len(p.comments)
        result.append(item)
    return result


@router.get("/posts/{post_id}")
def get_community_post(post_id: int, db: Session = Depends(get_db_session)):
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")

    # 조회수 증가
    post.view_count += 1
    db.commit()
    db.refresh(post)

    # 댓글 정렬: 먼저 COALESCE(parent_id, id) 로 그룹화하고,
    # parent_id가 없는(부모) 댓글이 먼저 오고 그 다음 자식 댓글들이 작성순으로 오도록 정렬
    sorted_comments = sorted(
        post.comments,
        key=lambda c: (c.parent_id if c.parent_id is not None else c.id, c.parent_id is not None, c.created_at)
    )

    comments = [_serialize_community_post_comment(c) for c in sorted_comments]

    res = _serialize_community_post(post)
    res["comments"] = comments
    return res


@router.post("/posts/{post_id}/comments")
def create_community_post_comment(
    post_id: int,
    comment: CommunityPostCommentCreate,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session)
):
    owner_uuid = assert_device_owner(auth_uuid, comment.user_id)
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")

    new_comment = models.CommunityPostComment(
        post_id=post_id,
        user_id=owner_uuid,
        nickname=comment.nickname,
        content=comment.content,
        deal_url=comment.deal_url,
        parent_id=comment.parent_id
    )
    db.add(new_comment)
    db.commit()
    db.refresh(new_comment)

    return _serialize_community_post_comment(new_comment)


@router.put("/posts/{post_id}/comments/{comment_id}")
def update_community_post_comment(
    post_id: int,
    comment_id: int,
    comment_data: CommunityPostCommentCreate,
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session)
):
    owner_uuid = assert_device_owner(auth_uuid, comment_data.user_id)
    comment = db.query(models.CommunityPostComment).filter(
        models.CommunityPostComment.id == comment_id,
        models.CommunityPostComment.post_id == post_id
    ).first()

    if not comment:
        raise HTTPException(status_code=404, detail="Comment not found")

    if comment.user_id != owner_uuid:
        raise HTTPException(status_code=403, detail="Only the comment creator can edit")

    comment.content = comment_data.content
    if comment_data.deal_url is not None:
        comment.deal_url = comment_data.deal_url

    db.commit()
    db.refresh(comment)

    return _serialize_community_post_comment(comment)


@router.delete("/posts/{post_id}/comments/{comment_id}")
def delete_community_post_comment(
    post_id: int,
    comment_id: int,
    user_id: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session)
):
    owner_uuid = assert_device_owner(auth_uuid, user_id)
    comment = db.query(models.CommunityPostComment).filter(
        models.CommunityPostComment.id == comment_id,
        models.CommunityPostComment.post_id == post_id
    ).first()

    if not comment:
        raise HTTPException(status_code=404, detail="Comment not found")

    if comment.user_id != owner_uuid:
        raise HTTPException(status_code=403, detail="Only the comment creator can delete")

    # 만약 자식 댓글이 있으면 삭제 상태로만 마킹 (또는 내용만 변경)
    # 여기서는 간단히 삭제 처리
    db.delete(comment)
    db.commit()

    return {"message": "Comment deleted successfully"}


@router.post("/posts/{post_id}/comments/{comment_id}/accept")
def accept_community_post_comment(
    post_id: int,
    comment_id: int,
    user_id: Optional[str] = Query(default=None),
    auth_uuid: str = Depends(require_device_token),
    db: Session = Depends(get_db_session)
):
    owner_uuid = assert_device_owner(auth_uuid, user_id)
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")

    if post.user_id != owner_uuid:
        raise HTTPException(status_code=403, detail="Only the post creator can accept comments")

    if post.is_resolved:
        raise HTTPException(status_code=400, detail="Post is already resolved")

    comment = db.query(models.CommunityPostComment).filter(
        models.CommunityPostComment.id == comment_id,
        models.CommunityPostComment.post_id == post_id
    ).first()

    if not comment:
        raise HTTPException(status_code=404, detail="Comment not found")

    comment.is_accepted = True
    post.is_resolved = True

    db.commit()

    return {"message": "Comment accepted successfully", "bounty_points_awarded": post.bounty_points}


@router.get("/users/{user_id}/comments")
def get_user_comments(
    user_id: str,
    skip: int = 0,
    limit: int = 20,
    db: Session = Depends(get_db_session)
):
    comments = db.query(models.CommunityPostComment)\
        .filter(models.CommunityPostComment.user_id == user_id)\
        .order_by(models.CommunityPostComment.created_at.desc())\
        .offset(skip).limit(limit).all()

    result = []
    for c in comments:
        # Get parent post for context
        post = db.query(models.CommunityPost).filter(models.CommunityPost.id == c.post_id).first()
        created_utc = to_utc_iso(c.created_at)

        result.append({
            "id": c.id,
            "post_id": c.post_id,
            "post_title": post.title if post else "삭제된 게시글",
            "user_id": c.user_id,
            "userId": c.user_id,
            "nickname": c.nickname,
            "content": c.content,
            "deal_url": c.deal_url,
            "is_accepted": c.is_accepted,
            "parent_id": c.parent_id,
            "created_at": created_utc,
            "createdAt": created_utc,
        })

    return result


class RelayTokenVerifyPayload(BaseModel):
    target_url: str


_RELAY_ALLOWED_HOSTS = (
    "ppomppu.co.kr",
    "quasarzone.com",
    "fmkorea.com",
    "ruliweb.com",
    "clien.net",
    "bbasak.com",
    "auction.co.kr",
    "gmarket.co.kr",
    "11st.co.kr",
    "bnkrmall.co.kr",
)


def _is_allowed_relay_host(hostname: str) -> bool:
    norm = (hostname or "").lower()
    return any(norm == h or norm.endswith(f".{h}") for h in _RELAY_ALLOWED_HOSTS)


@router.post("/verify-relay-token")
def verify_relay_token(
    payload: RelayTokenVerifyPayload,
    x_feeder_token: Optional[str] = Header(default=None, alias="X-Feeder-Token"),
):
    """Vercel 서울 릴레이(/api/scraper-relay)가 자체 시크릿 미설정 시 HMAC 토큰 검증을 위임하는 엔드포인트."""
    try:
        parsed = _urlsplit((payload.target_url or "").strip())
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid url")
    if parsed.scheme.lower() not in ("http", "https") or not parsed.hostname:
        raise HTTPException(status_code=400, detail="Invalid url")
    if not _is_allowed_relay_host(parsed.hostname):
        raise HTTPException(status_code=403, detail="Host not allowed")

    secret = (os.getenv("FEEDER_API_TOKEN") or os.getenv("SCRAPER_RELAY_SECRET") or "").strip()
    if not secret:
        raise HTTPException(status_code=503, detail="Relay authentication secret is not configured")

    provided = (x_feeder_token or "").strip()
    m = re.fullmatch(r"v1\.(0|[1-9]\d*)\.([0-9a-fA-F]{64})", provided)
    if not m:
        raise HTTPException(status_code=401, detail="Invalid or missing feeder token")

    ts = int(m.group(1))
    sig_hex = m.group(2).lower()
    if abs(int(time.time()) - ts) > 300:
        raise HTTPException(status_code=401, detail="Invalid or expired feeder token")

    expected_hex = hmac.new(
        secret.encode("utf-8"),
        f"{ts}:{payload.target_url}".encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    if not hmac.compare_digest(sig_hex, expected_hex):
        raise HTTPException(status_code=401, detail="Invalid or expired feeder token")

    return {"valid": True}


class IngestBatchPayload(BaseModel):
    community_name: str
    display_name: str
    base_url: str
    deals: List[dict]


@router.post("/ingest-batch")
async def ingest_scraped_deals_batch(
    payload: IngestBatchPayload,
    request: Request,
    x_feeder_token: Optional[str] = Header(default=None, alias="X-Feeder-Token"),
    db: Session = Depends(get_db_session),
):
    """한국 리전 릴레이/피더로부터 수집된 핫딜 배치를 서버 DB에 병합하고 통계를 갱신합니다."""
    expected_token = os.getenv("FEEDER_API_TOKEN", "").strip()
    if expected_token:
        if not hmac.compare_digest(x_feeder_token or "", expected_token):
            raise HTTPException(status_code=401, detail="Invalid or missing feeder token")
    else:
        forwarded_for = (request.headers.get("x-forwarded-for") or "").split(",")[0].strip().lower() if request else ""
        real_ip = (request.headers.get("x-real-ip") or "").strip().lower() if request else ""
        client_host = ((request.client.host if request and request.client else "") or "").strip().lower()
        is_loopback = (
            client_host in _LOOPBACK_HOSTS
            and (not forwarded_for or forwarded_for in _LOOPBACK_HOSTS)
            and (not real_ip or real_ip in _LOOPBACK_HOSTS)
        )
        if not is_loopback:
            raise HTTPException(status_code=503, detail="FEEDER_API_TOKEN is not configured")

    from backend.services.aggregator_service import AggregatorService

    community = db.query(models.Community).filter(models.Community.name == payload.community_name).first()
    if not community:
        community = models.Community(
            name=payload.community_name,
            display_name=payload.display_name,
            base_url=payload.base_url,
        )
        db.add(community)
        db.commit()
        db.refresh(community)

    def _fetch_ingest_snapshot() -> tuple[int, dict[int, tuple]]:
        prev_max = int(db.query(func.max(models.Deal.id)).scalar() or 0)
        cand_post_urls: set[str] = set()
        cand_ecom_urls: set[str] = set()
        for raw_item in payload.deals:
            if not isinstance(raw_item, dict):
                continue
            for key_name, bucket in (("url", cand_post_urls), ("ecommerce_link", cand_ecom_urls)):
                val = str(raw_item.get(key_name) or "").strip()
                if not val or len(val) > 512:
                    continue
                bucket.add(val)
                if val.startswith("http://"):
                    bucket.add("https://" + val[len("http://"):])
                elif val.startswith("https://"):
                    bucket.add("http://" + val[len("https://"):])
        clauses = []
        if cand_post_urls:
            clauses.append(models.Deal.post_link.in_(list(cand_post_urls)[:200]))
        if cand_ecom_urls:
            clauses.append(models.Deal.ecommerce_link.in_(list(cand_ecom_urls)[:200]))
        snap: dict[int, tuple] = {}
        if clauses:
            rows = (
                db.query(
                    models.Deal.id,
                    models.Deal.price,
                    models.Deal.is_closed,
                    models.Deal.is_super_hotdeal,
                )
                .filter(or_(*clauses))
                .all()
            )
            for r_id, r_price, r_closed, r_hot in rows:
                snap[int(r_id)] = (r_price, bool(r_closed), bool(r_hot))
        return prev_max, snap

    prev_max_id, prev_snap_by_id = await run_in_threadpool(_fetch_ingest_snapshot)

    aggregator = AggregatorService(db)
    inserted = 0
    has_feed_change = False
    new_inserted_images: list[str] = []
    for item in payload.deals:
        await asyncio.sleep(0.01)
        try:
            deal = await aggregator.process_scraped_deal(community.id, item)
            if deal:
                inserted += 1
                img_u = getattr(deal, "image_url", None)
                d_id = getattr(deal, "id", None)
                if d_id is not None and d_id > prev_max_id:
                    has_feed_change = True
                    if img_u:
                        new_inserted_images.append(img_u)
                elif d_id is not None:
                    curr_snap = (
                        getattr(deal, "price", None),
                        bool(getattr(deal, "is_closed", False)),
                        bool(getattr(deal, "is_super_hotdeal", False)),
                    )
                    prev_snap = prev_snap_by_id.get(d_id)
                    if prev_snap is None or prev_snap != curr_snap:
                        has_feed_change = True
                    _evict_deal_detail_cache(d_id)
        except Exception as e:
            db.rollback()
            logger.warning(f"[ingest-batch] item error ({payload.community_name}): {e}")

    # Update scraper_stats.json (실제 수집 아이템이 있을 때만 통계 파일 갱신)
    if payload.deals:
        stats_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scraper_stats.json")
        try:
            stats = {}
            if os.path.exists(stats_file):
                with open(stats_file, "r", encoding="utf-8") as f:
                    stats = json.load(f)
            kst_tz = timezone(timedelta(hours=9))
            stats[payload.display_name] = {
                "last_count": inserted,
                "fetched_count": len(payload.deals),
                "updated_count": inserted,
                "last_run": datetime.now(kst_tz).strftime("%Y-%m-%d %H:%M:%S"),
                "status": "Success",
            }
            with open(stats_file, "w", encoding="utf-8") as f:
                json.dump(stats, f, ensure_ascii=False, indent=2)
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning(f"[ingest-batch] failed to update scraper_stats.json: {exc}")

    if has_feed_change:
        invalidate_feed_api_cache(clear_ecom_url_cache=False)
    if new_inserted_images:
        try:
            from backend.main import schedule_thumbnail_prewarm
            schedule_thumbnail_prewarm(new_inserted_images)
        except Exception as exc:
            logger.debug("Thumbnail prewarm scheduling skipped: %s", exc)
    return {"status": "ok", "community": payload.display_name, "processed": inserted}

