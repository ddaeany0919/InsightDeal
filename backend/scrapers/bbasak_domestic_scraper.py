from .bbasak_base_scraper import BbasakBaseScraper

class BbasakDomesticScraper(BbasakBaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="빠삭국내",
            community_url="https://bbs.bbasak.com/domestic"
        )
