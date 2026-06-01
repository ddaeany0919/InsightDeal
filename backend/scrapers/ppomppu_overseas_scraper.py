import logging
from backend.scrapers.ppomppu_scraper import PpomppuScraper

logger = logging.getLogger(__name__)

class PpomppuOverseasScraper(PpomppuScraper):
    def __init__(self, community_id: int):
        super().__init__(community_id)
        self.platform_name = "뽐뿌해외"
        # 뽐뿌 해외게시판 및 해외직구 게시판 둘 다 관리
        self.list_urls = [
            "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4",
            "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu8"
        ]
        self.list_url = self.list_urls[0]

    async def run(self, url: str = None) -> list[dict]:
        """두 해외 게시판(ppomppu4, ppomppu8) 모두를 긁어오도록 run 재정의"""
        all_deals = []
        for target_url in self.list_urls:
            try:
                logger.info(f"🔍 Scraping overseas board via super().run: {target_url}")
                parsed = await super().run(target_url)
                if parsed:
                    all_deals.extend(parsed)
            except Exception as e:
                logger.error(f"❌ Error scraping {target_url} via super().run: {e}")
        return all_deals

    async def parse_list(self, html: str) -> list[dict]:
        """뽐뿌 해외 특화 로직 (없으면 부모 클래스 사용)"""
        deals = await super().parse_list(html)
        
        # '질문'/'문의' 등 필터링은 PpomppuScraper와 AggregatorService에서 처리됨
        # 추가로 해외뽐뿌에서 질문을 걸러냄
        filtered_deals = []
        for d in deals:
            title = d.get('title', '')
            if '질문' in title or '문의' in title:
                continue
            filtered_deals.append(d)
            
        return filtered_deals

