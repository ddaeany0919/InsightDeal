import logging
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from backend.scrapers.base_scraper import AsyncBaseScraper

logger = logging.getLogger(__name__)

class BbasakBaseScraper(AsyncBaseScraper):
    def __init__(self, community_name: str, community_url: str, community_id: int = 0):
        super().__init__(community_name, max_concurrent_requests=5)
        self.community_id = community_id
        self.list_url = community_url

    async def parse_list(self, html: str) -> list[dict]:
        """빠삭 게시판 데이터 추출 (비동기 처리)"""
        soup = BeautifulSoup(html, 'html.parser')
        
        tables = soup.select('table.t1')
        post_rows = []
        if len(tables) >= 2:
            post_rows = tables[1].select('tbody tr')
        elif len(tables) == 1:
             post_rows = tables[0].select('tbody tr')

        deals = []
        import asyncio
        
        async def process_row(row):
            title_element = row.select_one('td.tit a')
            if not title_element: return None

            href = title_element.get('href')
            if not href: return None

            url = urljoin("https://bbasak.com", href)
            if 'device=pc' not in url:
                url += "&device=pc" if "?" in url else "?device=pc"
                
            full_title = title_element.get_text(strip=True)

            detail_info = await self.get_detail(url)
            
            price = 0
            import re
            match = re.search(r'([\d,]+)\s*원', full_title)
            if match:
                try:
                    price = int(match.group(1).replace(',', ''))
                except: pass
                
            is_closed = False
            if '종료' in full_title or '마감' in full_title or '품절' in full_title:
                is_closed = True
            style = title_element.get('style', '')
            if 'line-through' in style or title_element.select_one('del, s, strike'):
                is_closed = True
                
            return {
                "title": full_title,
                "url": url,
                "price": price,
                "shop_name": "",
                "image_url": detail_info.get("image_url", ""),
                "ecommerce_link": detail_info.get("ecommerce_link", ""),
                "is_closed": is_closed
            }

        tasks = [process_row(row) for row in post_rows]
        results = await asyncio.gather(*tasks)
        for r in results:
            if r: deals.append(r)
            
        return deals

    async def get_detail(self, url: str) -> dict:
        """상세 페이지 데이터 파싱 로직 (추후 고도화)"""
        html = await self.fetch_html(url)
        if not html: return {}
        soup = BeautifulSoup(html, 'html.parser')
        
        import re
        # 이미지 파싱 (본문 영역 내에서만 추출)
        valid_images = [urljoin("https://bbasak.com", img.get('src') or '') for img in soup.select('#board_view img') if img.get('src')]
        valid_images = [img for img in valid_images if not re.search(r'icon|emoticon|expand|beautifulLine|util/|skin/|share/|btn_|footer|banner|logo|layout|bg\.|mypage|member|board', img, re.IGNORECASE)]
        
        # 실제 상품 이미지(ex: 업로드 본문이미지)가 우선되도록 정렬 (카톡/스크린샷 캡처 증빙 이미지는 후순위로 밀림)
        def image_score(url):
            url_lower = url.lower()
            if 'kakao' in url_lower or 'screenshot' in url_lower or 'capture' in url_lower:
                return 10  # 아주 낮음
            if 'ckeditor' in url_lower:
                return 1   # 본문 삽입 이미지 (보통 상품 이미지 확률이 가장 높음)
            return 5       # 일반 첨부파일
            
        valid_images.sort(key=image_score)
        image_url = valid_images[0] if valid_images else ""
        
        # 외부 쇼핑몰 링크 추출 (본문 영역 내에서만 추출)
        ecommerce_link = ""
        for a in soup.select('#board_view a'):
            href = a.get('href')
            if href:
                href = href.replace('\xa0', '').replace('\u00A0', '').strip()
                if href.startswith('http') and 'sns' not in href:
                    if 'bbasak.com' not in href:
                        ecommerce_link = href
                        break
                    elif 'link.php' in href:
                        # link.php는 모바일 브라우저에서 막히므로, 파이썬 백엔드에서 미리 최종 리다이렉트 URL을 추적합니다.
                        try:
                            import httpx
                            # 모바일 우회 및 최종 URL 확보를 위해 PC User-Agent 사용
                            client = httpx.AsyncClient(verify=False, follow_redirects=True, timeout=10.0)
                            headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0'}
                            res = await client.head(href, headers=headers)
                            if str(res.url) != href and "bbasak.com" not in str(res.url):
                                ecommerce_link = str(res.url)
                            else:
                                # HEAD 방식이 막히면 GET 테스트
                                res = await client.get(href, headers=headers)
                                ecommerce_link = str(res.url)
                            await client.aclose()
                            break
                        except Exception as e:
                            logger.error(f"[빠삭] 리다이렉트 실패: {e}")
                            ecommerce_link = href + ("&device=pc" if "?" in href else "?device=pc")
                            break
        
        return {"image_url": image_url, "ecommerce_link": ecommerce_link}
