import os
import time
import logging
from glob import glob

logger = logging.getLogger(__name__)

def purge_old_image_cache(cache_dir: str, max_age_days: int = 14) -> int:
    """
    디스크 용량 한도 방수를 위해 max_age_days(기본 14일) 이상 경과한 캐시 파일 선택 정리.
    *보증*: 삭제된 이미지라도 유저가 나중에 해당 핫딜을 다시 조회하면
    /api/proxy-image 엔드포인트의 On-demand Re-caching 로직에 의해
    자동으로 원본에서 재다운로드 및 재캐싱되므로 엑박이 발생하지 않습니다.
    """
    if not os.path.exists(cache_dir):
        return 0
        
    now = time.time()
    max_age_seconds = max_age_days * 86400
    purged_count = 0
    
    try:
        cache_files = glob(os.path.join(cache_dir, "*.cache"))
        for file_path in cache_files:
            try:
                # 마지막 수정/접근 시간 검사
                file_age = now - os.path.getmtime(file_path)
                if file_age > max_age_seconds:
                    os.remove(file_path)
                    purged_count += 1
            except Exception as e:
                logger.debug(f"Cache file purge error ({file_path}): {e}")
                
        logger.info(f"🧹 이미지 캐시 자동 정제 완료: {purged_count}개 파일 삭제됨 (On-demand 재생성 보장됨)")
    except Exception as e:
        logger.error(f"❌ 이미지 캐시 정제 데몬 예외: {e}")
        
    return purged_count

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    cache_dir = os.path.join(backend_dir, "image_cache")
    purge_old_image_cache(cache_dir, max_age_days=14)
