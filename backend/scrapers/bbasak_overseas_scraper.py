from .bbasak_base_scraper import BbasakBaseScraper

class BbasakOverseasScraper(BbasakBaseScraper):
    def __init__(self, community_id: int):
        super().__init__(
            community_name="빠삭해외",
            community_url="https://bbasak.com/bbs/board.php?bo_table=bbasak2",
            community_id=community_id
        )
