from fastapi import APIRouter
from datetime import datetime
import threading
import time

# 스케줄러 전역 상태 및 DB 세션을 로드
from backend.scheduler.main import SCHEDULER_STATE
from backend.database.session import SessionLocal
from backend.database.models import Deal, Product

router = APIRouter()

_HEALTH_COUNTS_CACHE: dict = {}
_HEALTH_COUNTS_LOCK = threading.Lock()
_HEALTH_COUNTS_TTL_SEC = 5.0


@router.get("")
def health_check():
    """
    🏥 시스템 헬스 체크 & 스케줄러 통계 정보
    - 동기 워커 스레드풀 + 5초 TTL 카운트 캐시로 asyncio 이벤트 루프 및 SQLite 블로킹 방지.
    """
    now_ts = time.time()
    total_deals = None
    total_products = None
    db = SessionLocal()
    try:
        bind_key = id(db.get_bind())
        with _HEALTH_COUNTS_LOCK:
            cached = _HEALTH_COUNTS_CACHE.get(bind_key)
            if cached and (now_ts - cached["ts"]) < _HEALTH_COUNTS_TTL_SEC:
                total_deals = cached["deals"]
                total_products = cached["products"]

        if total_deals is None:
            try:
                total_deals = db.query(Deal).count()
                total_products = db.query(Product).count()
                if total_deals > 0:
                    with _HEALTH_COUNTS_LOCK:
                        _HEALTH_COUNTS_CACHE[bind_key] = {
                            "ts": now_ts,
                            "deals": total_deals,
                            "products": total_products,
                        }
            except Exception:
                total_deals = 0
                total_products = 0
    finally:
        db.close()
        
    last_run = SCHEDULER_STATE.get("last_run_time")
    last_run_str = last_run.strftime("%Y-%m-%d %H:%M:%S") if last_run else "Never Run (Idle)"
    
    return {
        "status": "Healthy & Always Flowing",
        "timestamp": datetime.now().isoformat(),
        "database": {
            "total_deals_upserted": total_deals,
            "unique_products_mapped": total_products
        },
        "engine_scheduler": {
            "status": SCHEDULER_STATE.get("last_status"),
            "last_pipeline_run_time": last_run_str,
            "total_cycle_count": SCHEDULER_STATE.get("total_run_count")
        }
    }
