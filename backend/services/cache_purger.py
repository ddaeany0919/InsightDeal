import os
import time
import logging

logger = logging.getLogger(__name__)

def purge_image_cache_lru(
    cache_dir: str, 
    max_age_days: int = 7, 
    max_size_mb: int = 500,
    low_watermark_ratio: float = 0.85
) -> dict:
    """
    고속 LRU 이미지 캐시 자동 정제 엔진
    
    1단계: 최근 max_age_days(기본 7일) 동안 미조회(mtime 경과)된 .cache 파일 선삭제.
    2단계: 삭제 후에도 총 용량이 max_size_mb(기본 500MB)를 초과할 경우,
           mtime 오름차순(가장 오래 미사용된 파일 순)으로 정렬하여
           저수위 마크(기본 85% = 425MB) 이하가 될 때까지 순차 삭제.
    
    *보증*: 삭제된 이미지라도 유저가 나중에 해당 핫딜을 다시 조회하면
           /api/proxy-image 엔드포인트의 On-demand Re-caching 로직에 의해
           자동으로 원본에서 재다운로드 및 재캐싱되므로 엑박이 발생하지 않습니다.
    """
    summary = {
        "purged_by_age": 0,
        "purged_by_lru": 0,
        "freed_bytes": 0,
        "initial_size_mb": 0.0,
        "final_size_mb": 0.0
    }
    
    if not os.path.exists(cache_dir):
        return summary

    now = time.time()
    max_age_seconds = max_age_days * 86400
    max_size_bytes = int(max_size_mb * 1024 * 1024)
    target_size_bytes = int(max_size_bytes * low_watermark_ratio)

    files_info = []
    total_size = 0

    try:
        # os.scandir를 활용한 O(1) 고속 메타데이터 수집
        with os.scandir(cache_dir) as entries:
            for entry in entries:
                if entry.is_file() and entry.name.endswith(".cache"):
                    try:
                        stat = entry.stat()
                        files_info.append({
                            "path": entry.path,
                            "mtime": stat.st_mtime,
                            "size": stat.st_size
                        })
                        total_size += stat.st_size
                    except OSError:
                        continue

        summary["initial_size_mb"] = round(total_size / (1024 * 1024), 2)

        # 1단계: max_age_days 동안 미조회(mtime 경과)된 파일 선삭제
        remaining_files = []
        for item in files_info:
            if (now - item["mtime"]) > max_age_seconds:
                try:
                    os.remove(item["path"])
                    summary["purged_by_age"] += 1
                    summary["freed_bytes"] += item["size"]
                    total_size -= item["size"]
                except OSError:
                    # 삭제 실패 시 남은 파일로 취급
                    remaining_files.append(item)
            else:
                remaining_files.append(item)

        # 2단계: 용량 상한선(max_size_mb) 초과 시 LRU(mtime 오름차순) 순차 삭제
        if total_size > max_size_bytes:
            remaining_files.sort(key=lambda x: x["mtime"])
            for item in remaining_files:
                if total_size <= target_size_bytes:
                    break
                try:
                    os.remove(item["path"])
                    summary["purged_by_lru"] += 1
                    summary["freed_bytes"] += item["size"]
                    total_size -= item["size"]
                except OSError:
                    continue

        summary["final_size_mb"] = round(total_size / (1024 * 1024), 2)
        logger.info(
            f"🧹 [Cache Purger] 정제 완료: {max_age_days}일경과 {summary['purged_by_age']}개, "
            f"LRU {summary['purged_by_lru']}개 삭제 "
            f"(확보: {round(summary['freed_bytes'] / (1024 * 1024), 2)}MB, 최종: {summary['final_size_mb']}MB)"
        )
    except Exception as e:
        logger.error(f"❌ 이미지 캐시 LRU 정제 중 오류: {e}")

    return summary

def purge_old_image_cache(cache_dir: str, max_age_days: int = 7) -> int:
    """
    하위 호환성 래퍼: 기존 호출부 호환을 유지하며 purge_image_cache_lru를 실행
    """
    result = purge_image_cache_lru(cache_dir=cache_dir, max_age_days=max_age_days)
    return result["purged_by_age"] + result["purged_by_lru"]

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    cache_dir = os.path.join(backend_dir, "image_cache")
    purge_image_cache_lru(cache_dir, max_age_days=7, max_size_mb=500)
