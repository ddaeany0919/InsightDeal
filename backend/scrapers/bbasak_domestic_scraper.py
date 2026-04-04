from .bbasak_base_scraper import BbasakBaseScraper

class BbasakDomesticScraper(BbasakBaseScraper):
    def __init__(self, community_id: int):
        super().__init__(
            community_name="빠삭국내",
            community_url="https://bbasak.com/bbs/board.php?bo_table=bbasak1",
            community_id=community_id
        )
