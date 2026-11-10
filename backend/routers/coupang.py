import os
import time
import hmac
import hashlib
import requests
import logging
from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import RedirectResponse
from pydantic import BaseModel
from sqlalchemy import func
from sqlalchemy.orm import Session
from datetime import datetime, timezone
from typing import Optional
from backend.database.session import get_db_session
from backend.database import models

# 로깅 설정
logger = logging.getLogger("coupang_partners")
logger.setLevel(logging.INFO)

router = APIRouter(
    tags=["Coupang Partners & Affiliate Monetization"]
)


class OutboundClickPayload(BaseModel):
    url: str
    deal_id: Optional[int] = None
    price: Optional[int] = 0
    currency: Optional[str] = "KRW"
    platform: Optional[str] = "web"


# 🔒 금융권 수준의 크레덴셜 격리 보안 서비스
class CoupangPartnersService:
    def __init__(self):
        # 환경 변수로부터 안전하게 자격증명 로드 (하드코딩 유출 0%)
        self.access_key = os.getenv("COUPANG_ACCESS_KEY", "").strip()
        self.secret_key = os.getenv("COUPANG_SECRET_KEY", "").strip()
        self.base_url = "https://api-gateway.coupang.com"
        
    def is_configured(self) -> bool:
        ak = os.getenv("COUPANG_ACCESS_KEY", "").strip() or self.access_key
        sk = os.getenv("COUPANG_SECRET_KEY", "").strip() or self.secret_key
        return bool(ak and sk)

    def _generate_signature(self, method: str, path: str, query_string: str = "") -> tuple[str, str]:
        """
        쿠팡 파트너스 정식 규격 CEA HMAC-SHA256 서명 생성 엔진
        """
        ak = os.getenv("COUPANG_ACCESS_KEY", "").strip() or self.access_key
        sk = os.getenv("COUPANG_SECRET_KEY", "").strip() or self.secret_key
        utc_now = datetime.now(timezone.utc)
        signed_date = utc_now.strftime("%y%m%dT%H%M%SZ")
        
        message = f"{signed_date}{method}{path}"
        if query_string:
            message += f"?{query_string}"
            
        signature = hmac.new(
            sk.encode("utf-8"),
            message.encode("utf-8"),
            hashlib.sha256
        ).hexdigest()
        
        authorization = f"CEA algorithm=HMAC-SHA256, access-key={ak}, signed-date={signed_date}, signature={signature}"
        return authorization, signed_date

    def create_deeplink(self, coupang_url: str, sub_id: str = "insightdeal") -> str:
        """
        쿠팡 일반 상품 URL을 파트너스 공식 단축 수익 링크(https://link.coupang.com/...)로 실시간 변환합니다.
        """
        if not self.is_configured() or not coupang_url:
            return ""
        path = "/v2/providers/affiliate_open_api/apis/openapi/v1/deeplink"
        method = "POST"
        authorization, _ = self._generate_signature(method, path)
        headers = {
            "Content-Type": "application/json;charset=UTF-8",
            "Authorization": authorization,
        }
        payload = {
            "coupangUrls": [coupang_url],
            "subId": sub_id,
        }
        try:
            resp = requests.post(f"{self.base_url}{path}", json=payload, headers=headers, timeout=3.5)
            if resp.status_code == 200:
                data = resp.json().get("data") or []
                if data and isinstance(data, list) and data[0].get("shortenUrl"):
                    return str(data[0]["shortenUrl"]).strip()
        except Exception as e:
            logger.debug(f"Coupang deeplink API error: {e}")
        return ""

    def search_products(self, keyword: str, limit: int = 10) -> dict:
        """
        쿠팡 파트너스 상품 검색 API 프록시 (서버 대행으로 크레덴셜 비공개 보장)
        """
        if not self.is_configured():
            logger.warning("Coupang Partners credentials are not configured in .env")
            return {"status": "inactive", "message": "쿠팡 파트너스 자격증명이 서버 환경변수에 설정되지 않았습니다.", "items": []}
            
        path = "/gateway/sb/v1/partners/products/search"
        method = "POST"
        
        # 서명 및 인증 헤더 생성
        authorization, signed_date = self._generate_signature(method, path)
        
        headers = {
            "Content-Type": "application/json",
            "Authorization": authorization,
            "x-requested-with": "InsightDeal-Server"
        }
        
        payload = {
            "keyword": keyword,
            "limit": limit
        }
        
        try:
            response = requests.post(
                f"{self.base_url}{path}",
                json=payload,
                headers=headers,
                timeout=5
            )
            
            if response.status_code == 200:
                data = response.json()
                return {
                    "status": "success",
                    "items": data.get("data", {}).get("productData", [])
                }
            else:
                logger.error(f"Coupang API error: {response.status_code} - {response.text}")
                raise HTTPException(status_code=response.status_code, detail=f"Coupang API response failure: {response.text}")
                
        except Exception as e:
            logger.error(f"Failed to fetch from Coupang: {e}")
            # 연동 오류 시 서비스 먹통 방지를 위해 우아한 모의 폴백 데이터 서빙 (UX 보존)
            return self._get_mock_fallback_products(keyword)

    def _get_mock_fallback_products(self, keyword: str) -> dict:
        """
        크레덴셜 미등록 혹은 API 에러 시 시뮬레이션용 프리미엄 데이터 빌더
        """
        return {
            "status": "simulation",
            "message": "쿠팡 파트너스 시뮬레이션 활성화 중 (정식 키 입력 시 즉시 연동)",
            "items": [
                {
                    "productId": 7001,
                    "productName": f"[쿠팡] {keyword} 프리미엄 스마트 패키지 역대급 최저가 패키지",
                    "productPrice": 29800,
                    "productImage": "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=500&auto=format&fit=crop&q=60",
                    "productUrl": "https://www.coupang.com",
                    "categoryName": "전자제품",
                    "discountRate": 15
                },
                {
                    "productId": 7002,
                    "productName": f"[쿠팡] {keyword} 1+1 기획 특가 패키지 [단독 무료배송]",
                    "productPrice": 14900,
                    "productImage": "https://images.unsplash.com/photo-1534422298391-e4f8c172dddb?w=500&auto=format&fit=crop&q=60",
                    "productUrl": "https://www.coupang.com",
                    "categoryName": "식품/생활",
                    "discountRate": 20
                }
            ]
        }

# 싱글톤 서비스 격발
coupang_service = CoupangPartnersService()

@router.get("/search")
def search_coupang_products(
    keyword: str = Query(..., description="검색할 상품 키워드"),
    limit: int = Query(10, description="최대 노출 개수")
):
    try:
        return coupang_service.search_products(keyword, limit)
    except Exception as e:
        logger.error(f"Router error: {e}")
        return {"status": "error", "message": str(e), "items": []}


def _is_network_monetized(network: str) -> bool:
    from backend.core.url_utils import DEFAULT_LINKPRICE_AFFILIATE_ID
    lp_id = (
        os.getenv("AFFILIATE_LINKPRICE_ID", "").strip()
        or os.getenv("LINKPRICE_AFFILIATE_ID", "").strip()
        or DEFAULT_LINKPRICE_AFFILIATE_ID
    )
    if network == "coupang":
        return bool(
            coupang_service.is_configured()
            or os.getenv("AFFILIATE_COUPANG_LPTAG", "").strip()
            or os.getenv("COUPANG_PARTNERS_ID", "").strip()
        )
    if network == "aliexpress":
        return bool(
            os.getenv("AFFILIATE_ALIEXPRESS_ID", "").strip()
            or os.getenv("ALIEXPRESS_TRACKING_ID", "").strip()
            or os.getenv("ALIEXPRESS_APP_KEY", "").strip()
            or lp_id
        )
    if network == "linkprice":
        return bool(lp_id)
    return False


def _record_outbound_click(
    db: Session,
    url: str,
    deal_id: Optional[int] = None,
    price: int = 0,
    currency: str = "KRW",
    platform: str = "web",
) -> dict:
    from backend.core.url_utils import classify_outbound_mall
    mall_name, domain, network, comm_rate = classify_outbound_mall(url)
    is_mon = _is_network_monetized(network)

    price_krw = price
    if currency == "USD" and price > 0:
        price_krw = int((price / 100.0) * 1400)
    elif price_krw <= 0:
        price_krw = 25000  # 가격 미기재 딜의 평균 객단가(25,000원) 가정

    # 클릭당 기대 수익(EPC) = 객단가 × 수수료율(2~6%) × 평균 구매 전환율(5%)
    est_comm = round(price_krw * comm_rate * 0.05, 1) if comm_rate > 0 else 0.0

    try:
        models.OutboundClick.__table__.create(bind=db.get_bind(), checkfirst=True)
        click_row = models.OutboundClick(
            deal_id=deal_id,
            mall_name=mall_name,
            domain=domain or "unknown",
            affiliate_network=network,
            is_monetized=is_mon,
            price_krw=price_krw,
            estimated_commission_krw=est_comm,
            platform=(platform or "web")[:30],
            target_url=url[:2000],
        )
        db.add(click_row)
        db.commit()
    except Exception as e:
        db.rollback()
        logger.debug(f"Failed to record outbound click: {e}")

    return {
        "mall_name": mall_name,
        "affiliate_network": network,
        "is_monetized": is_mon,
        "estimated_commission_krw": est_comm,
    }


@router.post("/track-click")
def track_outbound_click(
    payload: OutboundClickPayload,
    db: Session = Depends(get_db_session),
):
    """
    웹 / 앱인토스 / 안드로이드 앱에서 유저가 쇼핑몰 구매 링크를 클릭할 때
    페이지 이동 지연(0ms) 없이 비동기로 클릭 및 예상 제휴 수익을 기록합니다.
    """
    if not payload.url or not payload.url.startswith("http"):
        return {"status": "ignored"}
    info = _record_outbound_click(
        db=db,
        url=payload.url,
        deal_id=payload.deal_id,
        price=int(payload.price or 0),
        currency=payload.currency or "KRW",
        platform=payload.platform or "web",
    )
    return {"status": "ok", **info}


@router.get("/redirect")
def redirect_with_affiliate(
    url: str = Query(..., description="이동할 쇼핑몰 원본 URL"),
    deal_id: Optional[int] = Query(None),
    platform: str = Query("web"),
    db: Session = Depends(get_db_session),
):
    """
    외부 공유 링크나 푸시 알림에서 호출 시 타인 제휴코드를 세탁하고
    인사이트딜 제휴 링크로 즉시 302 리다이렉트합니다.
    """
    from backend.core.url_utils import unwrap_third_party_affiliate_url, apply_affiliate_monetization
    if not url or not url.startswith(("http://", "https://")):
        raise HTTPException(status_code=400, detail="유효하지 않은 URL입니다.")
    clean_u = unwrap_third_party_affiliate_url(url, allow_network=True)
    monetized_u = apply_affiliate_monetization(clean_u)
    _record_outbound_click(db=db, url=monetized_u, deal_id=deal_id, platform=platform)
    return RedirectResponse(url=monetized_u, status_code=302)


@router.get("/stats")
def get_monetization_stats(db: Session = Depends(get_db_session)):
    """
    📊 실시간 제휴 수익화(Affiliate) 연동 상태, DB 내 쇼핑몰 점유율 및 클릭/예상수익 대시보드 API
    """
    from backend.core.url_utils import classify_outbound_mall
    try:
        models.OutboundClick.__table__.create(bind=db.get_bind(), checkfirst=True)
    except Exception:
        pass

    networks = {
        "linkprice": {
            "name": "링크프라이스 (G마켓·11번가·롯데온·옥션·SSG·오늘의집 등 15개 몰)",
            "configured": _is_network_monetized("linkprice"),
            "env_key": "AFFILIATE_LINKPRICE_ID",
            "commission_rate": "1.5% ~ 4.0%",
            "approval_hurdle": "없음 (가입 즉시 어필리에이트 ID 발급)",
        },
        "aliexpress": {
            "name": "알리익스프레스 Portals (알리뽐뿌·해외직구 전체)",
            "configured": _is_network_monetized("aliexpress"),
            "env_key": "AFFILIATE_ALIEXPRESS_ID",
            "commission_rate": "4.0% ~ 9.0%",
            "approval_hurdle": "없음 (승인 즉시 트래킹 ID 발급)",
        },
        "coupang": {
            "name": "쿠팡 파트너스 (24시간 장바구니 전체 합산 3%)",
            "configured": _is_network_monetized("coupang"),
            "api_mode": coupang_service.is_configured(),
            "env_key": "AFFILIATE_COUPANG_LPTAG / COUPANG_ACCESS_KEY",
            "commission_rate": "3.0%",
            "approval_hurdle": "파트너스 ID(AF...)는 즉시 발급 / Open API는 누적 판매 15만 원 달성 시 발급",
        },
    }

    # 최근 500개 딜의 쇼핑몰 및 제휴 네트워크 커버리지 분석
    recent_deals = (
        db.query(models.Deal.ecommerce_link, models.Deal.shop_name)
        .order_by(models.Deal.id.desc())
        .limit(500)
        .all()
    )
    network_counts = {"linkprice": 0, "aliexpress": 0, "coupang": 0, "direct": 0}
    mall_counts: dict[str, int] = {}
    for ecom_link, s_name in recent_deals:
        mall_ko, _dom, net, _rate = classify_outbound_mall(ecom_link or "")
        network_counts[net] = network_counts.get(net, 0) + 1
        label = mall_ko if mall_ko != "기타" else (s_name or "기타")
        mall_counts[label] = mall_counts.get(label, 0) + 1

    total_deals_sampled = max(1, len(recent_deals))
    monetizable_count = network_counts["linkprice"] + network_counts["aliexpress"] + network_counts["coupang"]

    # 누적 클릭 및 예상 수익 집계
    total_clicks = db.query(func.count(models.OutboundClick.id)).scalar() or 0
    total_est_comm = db.query(func.sum(models.OutboundClick.estimated_commission_krw)).scalar() or 0.0
    by_mall_rows = (
        db.query(
            models.OutboundClick.mall_name,
            models.OutboundClick.affiliate_network,
            func.count(models.OutboundClick.id),
            func.sum(models.OutboundClick.estimated_commission_krw),
        )
        .group_by(models.OutboundClick.mall_name, models.OutboundClick.affiliate_network)
        .order_by(func.count(models.OutboundClick.id).desc())
        .limit(15)
        .all()
    )

    return {
        "networks": networks,
        "db_coverage": {
            "sampled_deals": len(recent_deals),
            "monetizable_deals": monetizable_count,
            "monetizable_ratio_pct": round(monetizable_count * 100.0 / total_deals_sampled, 1),
            "by_network": network_counts,
            "top_malls_in_db": sorted(mall_counts.items(), key=lambda x: x[1], reverse=True)[:10],
        },
        "click_stats": {
            "total_clicks": total_clicks,
            "estimated_commission_krw": round(float(total_est_comm), 1),
            "by_mall": [
                {
                    "mall_name": m,
                    "network": net,
                    "clicks": c,
                    "estimated_commission_krw": round(float(est or 0.0), 1),
                }
                for m, net, c, est in by_mall_rows
            ],
        },
    }
