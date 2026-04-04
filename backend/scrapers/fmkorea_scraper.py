import logging
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class FmkoreaScraper(AsyncBaseScraper):
    def __init__(self, community_id: int):
        # 펨코는 Cloudflare 방어가 강하므로 딜레이를 늘리고 동시성을 낮춥니다.
        super().__init__("펨코", max_concurrent_requests=2)
        self.community_id = community_id
        self.list_url = "https://www.fmkorea.com/hotdeal?listStyle=list"

    async def parse_list(self, html: str) -> list[dict]:
        """펨코리아 게시판 리스트에서 타겟 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        
        post_rows = soup.select('table.bd_lst tbody tr')
        if not post_rows:
            post_rows = soup.select('li.li:not(.notice), div.list_item:not(.notice)')

        deals = []
        for row in post_rows:
            row_classes = row.get('class') or []
            if any(cls in row_classes for cls in ['notice', 'notice_pop0', 'notice_pop1']):
                continue
                
            no_tag = row.select_one('td.no')
            if no_tag and no_tag.get_text(strip=True) in ['공지', '인기', 'AD', '광고']:
                continue

            title_element = row.select_one('td.title a, h3.title a, a.title')
            if not title_element:
                if row.name == 'a' and 'title' in (row.get('class') or []):
                    title_element = row
                else:
                    continue

            href = title_element.get('href', '')
            if not href or href.startswith('javascript'): continue
            
            url = urljoin("https://www.fmkorea.com/", href)
            
            # 댓글 카운트 제거
            reply_cnt = title_element.select_one('.replyNum')
            if reply_cnt:
                reply_cnt.decompose()

            full_title = title_element.get_text(strip=True)

            deals.append({
                "title": full_title,
                "url": url,
                "price": 0,
                "shop_name": ""
            })
            
        return deals

    async def get_detail(self, url: str) -> dict:
        """펨코리아 상세 페이지 데이터 파싱 로직 (추후 고도화)"""
        pass
