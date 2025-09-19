import re
import time
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from .base import BaseScraper
import models
import ai_parser

class ClienScraper(BaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="클리앙",
            community_url="https://www.clien.net/service/board/jirum"
        )

    def scrape(self):
        deals_data = []
        WebDriverWait(self.driver, 15).until(EC.presence_of_element_located((By.CSS_SELECTOR, "div.list_item.symph_row")))
        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_rows = soup.select('div.list_item.symph_row')

        if self.limit:
            post_rows = post_rows[:self.limit]
        
        temp_deals_info = []
        for row in post_rows:
            sold_out_tag = row.select_one('span.icon_info')
            if sold_out_tag and '품절' in sold_out_tag.get_text(strip=True):
                continue

            title_element = row.select_one('a[data-role="list-title-text"]')
            if not title_element: continue
            
            post_link = urljoin(self.base_url, title_element['href'])
            full_title_text = title_element.get_text(strip=True)
            temp_deals_info.append({'post_link': post_link, 'full_title': full_title_text})

        for deal_info in temp_deals_info:
            try:
                full_title = deal_info['full_title']
                
                shop_name_match = re.search(r'^\[(.*?)\]', full_title)
                shop_name_code = self._clean_shop_name(shop_name_match.group(1).strip() if shop_name_match else '정보 없음')

                price_code = '정보 없음'
                shipping_fee_code = '정보 없음'
                paren_match = re.search(r'\(([^)]+)\)$', full_title)
                if paren_match:
                    content = paren_match.group(1).strip()
                    parts = [p.strip() for p in content.split('/')]
                    
                    price_found = False
                    for part in parts:
                        cleaned_price = self._clean_price(part)
                        if cleaned_price != '정보 없음' and not price_found:
                            price_code = cleaned_price
                            price_found = True
                            continue
                        
                        temp_shipping_fee = self._clean_shipping_fee(part)
                        if temp_shipping_fee != '정보 없음':
                            shipping_fee_code = temp_shipping_fee
                
                self.driver.get(deal_info['post_link'])
                time.sleep(0.5)
                detail_soup = BeautifulSoup(self.driver.page_source, 'html.parser')

                content_element = detail_soup.select_one('div.post_content')
                content_text = content_element.get_text(strip=True, separator='\n') if content_element else ""

                ai_result = ai_parser.parse_content_with_ai(
                    content_text=content_text,
                    post_link=deal_info['post_link'],
                    original_title=deal_info['full_title']
                )

                if not ai_result or not ai_result.get('deals'):
                    print(f"  [Scraper Warning] AI parsing failed for link: {deal_info['post_link']}")
                    continue

                post_representative_image_url = None
                if content_element:
                    first_image_tag = content_element.find('img')
                    if first_image_tag:
                        src = first_image_tag.get('src')
                        if src:
                            post_representative_image_url = 'https:' + src if src.startswith('//') else urljoin(self.base_url, src)

                for deal_item in ai_result['deals']:
                    product_title = self._clean_text(deal_item.get('product_title'))
                    
                    shop_name = shop_name_code if shop_name_code != '정보 없음' else self._clean_shop_name(ai_result.get('shop_name'))
                    
                    shipping_fee = shipping_fee_code
                    if shipping_fee == '정보 없음':
                        shipping_fee = self._clean_shipping_fee(deal_item.get('shipping_fee'))
                    if shipping_fee == '정보 없음':
                        shipping_fee = self._clean_shipping_fee(ai_result.get('shipping_fee'))

                    price = price_code if price_code != '정보 없음' else self._clean_price(deal_item.get('price'))

                    if self.db.query(models.Deal).filter(
                        models.Deal.post_link == deal_info['post_link'],
                        models.Deal.title == product_title
                    ).first():
                        print(f"  [Scraper Info] Skipping duplicate sub-deal: {product_title}")
                        continue

                    category_ai_result = ai_parser.parse_title_with_ai(deal_info['full_title'])
                    category = self._clean_text(category_ai_result.get('category')) if category_ai_result else "기타"

                    new_deal = models.Deal(
                        source_community_id=self.community_entry.id, title=product_title, post_link=deal_info['post_link'],
                        shop_name=shop_name, price=price, shipping_fee=shipping_fee, category=category)
                    deals_data.append({'deal': new_deal, 'original_image_url': post_representative_image_url})

            except Exception as e:
                print(f"  [Detail Page Error] Link: {deal_info['post_link']}, Error: {e}")

        return deals_data