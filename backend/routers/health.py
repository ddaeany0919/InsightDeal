from fastapi import APIRouter
from datetime import datetime

# 스케줄러 전역 상태 및 DB 세션을 로드
# 추후 파일 구조가 커지면 의존성 주입(DI)으로 빼는 것이 좋습니다.
from backend.scheduler.main import SCHEDULER_STATE, SessionLocal
from backend.models.models_v2 import Deal, Product

router = APIRouter()

@router.get("")
async def health_check():
    """
    🏥 시스템 헬스 체크 & 스케줄러 통계 정보
    - 엔진이 살아서 돌고 있는지(생존 체커) 파악.
    - 데이터가 멈추지 않고 계속 축적(Price History)되고 있는지 볼 수 있는 관리자 렌즈입니다.
    """
    db = SessionLocal()
    try:
        total_deals = db.query(Deal).count()
        total_products = db.query(Product).count()
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
