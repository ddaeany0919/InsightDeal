import re
import time
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from .base import BaseScraper

class FmkoreaScraper(BaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="펨코",
            community_url="https://www.fmkorea.com/hotdeal"
        )

    def scrape(self):
        """
        펨코 목록 페이지에서 딜 정보를 수집하고,
        상세 분석은 BaseScraper의 공통 처리 함수에 위임합니다.
        """
        # 1. 목록 페이지 로딩 및 기본 정보 수집
        for i in range(3): # 페이지 스크롤 다운
            self.driver.execute_script(f"window.scrollBy(0, {1000 * (i + 1)});")
            time.sleep(1)

        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_items = soup.select('div.fm_best_widget._bd_pc > ul > li')

        if self.limit:
            post_items = post_items[:self.limit]

        temp_deals_info = []
        for li in post_items:
            title_tag = li.select_one('h3.title a')
            hotdeal_info_html = str(li.select_one('div.hotdeal_info'))
            if not title_tag: 
                continue

            post_link = urljoin(self.community_url, title_tag['href'])
            full_title_text = title_tag.get_text(strip=True)
            
            shipping_fee_match = re.search(r'배송: <a.*?>(.*?)</a>', hotdeal_info_html)
            shipping_fee = self._clean_shipping_fee(shipping_fee_match.group(1).strip() if shipping_fee_match else '정보 없음')
            
            shop_name_match = re.search(r'쇼핑몰: <a.*?>(.*?)</a>', hotdeal_info_html)
            shop_name = self._clean_text(shop_name_match.group(1).strip() if shop_name_match else '정보 없음')

            price_match = re.search(r'가격: <a.*?>(.*?)</a>', hotdeal_info_html)
            price = self._clean_price(price_match.group(1).strip() if price_match else '정보 없음')

            temp_deals_info.append({
                'post_link': post_link, 
                'full_title': full_title_text,
                'list_shipping_fee': shipping_fee,
                'list_shop_name': shop_name,
                'list_price': price
            })

        # 2. 수집된 기본 정보를 바탕으로 공통 상세 페이지 처리 함수 호출
        return self._process_detail_pages(temp_deals_info)