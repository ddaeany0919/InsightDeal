import logging
from typing import Optional, List
from .bbasak_base_scraper import BbasakBaseScraper

logger = logging.getLogger(__name__)

class BbasakOverseasScraper(BbasakBaseScraper):
    def __init__(self, community_id: int):
        super().__init__(
            community_name="빠삭해외",
            community_url="https://bbasak.com/bbs/board.php?bo_table=bbasak2",
            community_id=community_id,
            default_category="해외핫딜"
        )

    async def parse_list(self, html: str) -> list[dict]:
        """빠삭 해외 게시판 데이터 추출 (저빈도 게시판 특화 graceful 예외 처리)"""
        if not html or not html.strip():
            logger.info(f"ℹ️ [{self.platform_name}] 빈 HTML 응답 수신 - 0건 수집으로 정상 처리")
            return []

        try:
            deals = await super().parse_list(html)
            if not deals:
                logger.info(f"ℹ️ [{self.platform_name}] 저빈도 게시판 특성: 수집된 딜 0건 (정상 처리)")
                return []
            return deals
        except Exception as e:
            logger.warning(f"⚠️ [{self.platform_name}] 저빈도 게시판 파싱 중 예외 발생 ({e}) - 0건으로 graceful 처리")
            return []

    async def run(self, url: Optional[str] = None) -> List[dict]:
        """저빈도 게시판 예외 방어 래퍼 (0건 수집 시 에러 미발생 및 정상 완료)"""
        target_url = url or self.list_url
        try:
            deals = await super().run(target_url)
            if not deals:
                logger.info(f"ℹ️ [{self.platform_name}] 수집 결과 0건 (저빈도 게시판 정상 완료): {target_url}")
                return []
            return deals
        except Exception as e:
            logger.warning(f"⚠️ [{self.platform_name}] 수집 중 예외 발생 ({e}) - graceful하게 빈 목록 반환: {target_url}")
            return []

    async def get_detail(self, url: str, full_title: str = "") -> dict:
        """상세 페이지 파싱 graceful 예외 방어"""
        try:
            return await super().get_detail(url, full_title)
        except Exception as e:
            logger.warning(f"⚠️ [{self.platform_name}] 상세 페이지 파싱 중 예외 ({e}) - 빈 딕셔너리 반환: {url}")
            return {}
