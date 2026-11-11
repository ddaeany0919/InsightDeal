import os
import json
import logging
from datetime import datetime, timedelta, timezone
from curl_cffi import requests
from bs4 import BeautifulSoup

from backend.core.deal_normalizer import (
    is_valid_popular_keyword,
    unescape_and_strip_html_entities,
)

logger = logging.getLogger(__name__)
_KST = timezone(timedelta(hours=9))


def update_fmkorea_trending_keywords():
    """펨코 메인 페이지에서 실시간 급상승 검색어를 스크래핑하여 노이즈 정제 후 로컬 JSON 파일에 저장합니다."""
    try:
        logger.info("펨코리아 실시간 급상승 검색어 수집 시작...")
        res = requests.get("https://www.fmkorea.com/hotdeal", impersonate="chrome110", timeout=15)

        if res.status_code == 200:
            soup = BeautifulSoup(res.text, "html.parser")
            ticker = soup.find("ul", class_="keyword_rank_ticker")

            if ticker:
                keywords = []
                seen_low = set()
                for item in ticker.find_all("a"):
                    raw_kw = item.get("data-keyword") or item.get_text(strip=True) or ""
                    if not is_valid_popular_keyword(raw_kw):
                        continue
                    cleaned_kw = unescape_and_strip_html_entities(raw_kw).strip()
                    if cleaned_kw.lower() not in seen_low:
                        seen_low.add(cleaned_kw.lower())
                        keywords.append(cleaned_kw)

                if keywords:
                    data_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data")
                    os.makedirs(data_dir, exist_ok=True)

                    file_path = os.path.join(data_dir, "fmkorea_trending.json")
                    payload = {
                        "updated_at": datetime.now(_KST).isoformat(timespec="seconds"),
                        "keywords": keywords[:15],
                    }
                    with open(file_path, "w", encoding="utf-8") as f:
                        json.dump(payload, f, ensure_ascii=False)

                    logger.info(f"펨코 실시간 검색어 갱신 완료: {keywords[:10]}")
                    return True
        else:
            logger.error(f"펨코 메인페이지 접근 실패 (상태 코드: {res.status_code})")
    except Exception as e:
        logger.error(f"펨코 실시간 검색어 스크래핑 오류: {e}")
    return False


if __name__ == "__main__":
    update_fmkorea_trending_keywords()
