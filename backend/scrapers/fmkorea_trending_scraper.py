import os
import json
import logging
from curl_cffi import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

def update_fmkorea_trending_keywords():
    """펨코 메인 페이지에서 실시간 급상승 검색어를 스크래핑하여 로컬 JSON 파일에 저장합니다."""
    try:
        logger.info("펨코리아 실시간 급상승 검색어 수집 시작...")
        res = requests.get("https://www.fmkorea.com/hotdeal", impersonate="chrome110", timeout=15)
        
        if res.status_code == 200:
            soup = BeautifulSoup(res.text, "html.parser")
            ticker = soup.find("ul", class_="keyword_rank_ticker")
            
            if ticker:
                keywords = []
                for item in ticker.find_all("a"):
                    keyword = item.get("data-keyword")
                    if keyword and keyword not in keywords:
                        keywords.append(keyword)
                        
                if keywords:
                    # backend/data 폴더가 없으면 생성
                    data_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data")
                    os.makedirs(data_dir, exist_ok=True)
                    
                    file_path = os.path.join(data_dir, "fmkorea_trending.json")
                    with open(file_path, "w", encoding="utf-8") as f:
                        json.dump({"keywords": keywords[:10]}, f, ensure_ascii=False)
                    
                    logger.info(f"펨코 실시간 검색어 갱신 완료: {keywords[:10]}")
                    return True
        else:
            logger.error(f"펨코 메인페이지 접근 실패 (상태 코드: {res.status_code})")
    except Exception as e:
        logger.error(f"펨코 실시간 검색어 스크래핑 오류: {e}")
    return False

if __name__ == "__main__":
    update_fmkorea_trending_keywords()
