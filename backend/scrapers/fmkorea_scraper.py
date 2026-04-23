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

    async def fetch_html(self, url: str):
        # 펨코 430 차단 방지 임시 Mocking Fallback을 제거했습니다.
        # 운영 환경에서 가짜 데이터를 뿌리는 일이 생기지 않도록 차단되면 조용히 스킵합니다.
        res = await super().fetch_html(url)
        if not res or '에펨코리아 보안 시스템' in res:
            logger.warning("[펨코] 430 클라우드플레어 차단 감지! 정상적인 데이터 조회가 불가능합니다.")
            return ""
        return res

    async def parse_list(self, html: str) -> list[dict]:
        """펨코리아 게시판 리스트에서 타겟 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        
        post_rows = soup.select('table.bd_lst tbody tr')
        if not post_rows:
            post_rows = soup.select('li.li:not(.notice), div.list_item:not(.notice)')

        import asyncio

        async def process_row(row):
            row_classes = row.get('class') or []
            if any(cls in row_classes for cls in ['notice', 'notice_pop0', 'notice_pop1']):
                return None
                
            no_tag = row.select_one('td.no')
            if no_tag and no_tag.get_text(strip=True) in ['공지', '인기', 'AD', '광고']:
                return None

            title_element = row.select_one('td.title a, h3.title a, a.title')
            if not title_element:
                if row.name == 'a' and 'title' in (row.get('class') or []):
                    title_element = row
                else:
                    return None

            href = title_element.get('href', '')
            if not href or href.startswith('javascript'): return None
            
            url = urljoin("https://www.fmkorea.com/", href)
            
            # 댓글 카운트 제거
            reply_cnt = title_element.select_one('.replyNum')
            if reply_cnt:
                reply_cnt.decompose()

            full_title = title_element.get_text(strip=True)

            # 🖼️ 기본 썸네일 확인 (아이콘일 가능성이 큼)
            image_url = ""
            img_tag = row.select_one('img.thumb')
            if img_tag and img_tag.has_attr('src'):
                image_url = img_tag['src']
                if image_url.startswith('//'):
                    image_url = "https:" + image_url
                elif not image_url.startswith('http'):
                    image_url = urljoin("https://www.fmkorea.com/", image_url)

            # 상세 페이지 비동기 조회 (진짜 썸네일과 아웃링크 파싱)
            detail_info = await self.get_detail(url)
            
            # 펨코리아 아이콘이 잡힌 경우, 상세 페이지의 이미지로 덮어쓰기
            if 'icons/fmkorea' in image_url or 'transparent.gif' in image_url or not image_url:
                image_url = detail_info.get("image_url", image_url)
                
            # 가격 파싱 (본문 텍스트 내에서 추출된 가격)
            extracted_price = detail_info.get("price", 0)

            # 종료 여부 확인 (취소선이 그어져 있는지 또는 종료 키워드)
            is_closed = False
            style = title_element.get('style', '')
            if 'line-through' in style or title_element.select_one('del, s, strike'):
                is_closed = True
            
            if '종료' in full_title or '마감' in full_title or '품절' in full_title:
                is_closed = True
                
            for span in title_element.select('span'):
                if 'line-through' in span.get('style', ''):
                    is_closed = True
                    break

            return {
                "title": full_title,
                "url": url,
                "price": extracted_price,
                "shop_name": "",
                "image_url": image_url,
                "ecommerce_link": detail_info.get("ecommerce_link", ""),
                "is_closed": is_closed
            }

        tasks = [process_row(row) for row in post_rows]
        results = await asyncio.gather(*tasks)
        return [r for r in results if r]

    async def get_detail(self, url: str) -> dict:
        """펨코리아 상세 페이지 데이터 파싱 로직"""
        html = await self.fetch_html(url)
        if not html: return {}
        soup = BeautifulSoup(html, 'html.parser')
        
        ecommerce_link = ""
        # 펨코 핫딜의 아웃링크는 보통 외부 도메인을 가리키는 a 태그입니다.
        for a in soup.select('a'):
            href = a.get('href', '')
            if href.startswith('http') and 'fmkorea.com' not in href and 'saedu.naver.com' not in href:
                ecommerce_link = href
                break
                
        image_url = ""
        # 펨코 본문 이미지는 보통 files/attach/new 경로에 업로드됩니다.
        for img in soup.select('img'):
            src = img.get('src', '')
            if 'files/attach/new' in src:
                if src.startswith('//'):
                    image_url = "https:" + src
                elif not src.startswith('http'):
                    image_url = urljoin("https://www.fmkorea.com/", src)
                break

        # 혹시 image_url도 못 찾았지만 img 태그가 있다면 (외부 링크 이미지 등)
        if not image_url:
            for img in soup.select('img'):
                src = img.get('src', '')
                if src and 'fmkorealogo' not in src and 'icons/fmkorea' not in src and 'transparent' not in src:
                    if src.startswith('//'):
                        image_url = "https:" + src
                    elif not src.startswith('http'):
                        image_url = urljoin("https://www.fmkorea.com/", src)
                    break

        price_fallback = 0
        import re
        body_text = soup.get_text(separator=' ')
        price_matches = re.findall(r'([0-9]{1,3}(?:,[0-9]{3})+)\s*원', body_text)
        if price_matches:
            try:
                price_fallback = int(price_matches[0].replace(',', ''))
            except:
                pass

        return {
            "ecommerce_link": ecommerce_link,
            "image_url": image_url,
            "price": price_fallback
        }
