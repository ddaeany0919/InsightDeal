from datetime import datetime, timezone
from typing import Optional


def to_utc_iso(dt: Optional[datetime]) -> Optional[str]:
    """
    naive(UTC 간주) 또는 timezone-aware datetime을 명시적 UTC ISO-8601('...Z') 문자열로 정규화합니다.
    """
    if dt is None:
        return None
    if getattr(dt, "tzinfo", None) is None:
        dt = dt.replace(tzinfo=timezone.utc)
    else:
        dt = dt.astimezone(timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")
