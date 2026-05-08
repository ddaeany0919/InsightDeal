from .bbasak_base_scraper import BbasakBaseScraper

class BbasakParentingScraper(BbasakBaseScraper):
    def __init__(self, community_id: int):
        super().__init__(
            community_name="빠삭육아",
            community_url="https://bbasak.com/bbs/board.php?bo_table=bbasak3",
            community_id=community_id
        )
