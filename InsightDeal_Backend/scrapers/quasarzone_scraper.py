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

class QuasarzoneScraper(BaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="퀘이사존",
            community_url="https://quasarzone.com/bbs/qb_saleinfo"
        )
    
    def scrape(self):
        deals_data = []
        
        # 1단계: 목록 페이지에서 링크와 제목만 수집
        WebDriverWait(self.driver, 15).until(EC.presence_of_element_located((By.CSS_SELECTOR, "div.market-info-list")))
        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_rows = soup.select('div.market-info-list')

        if self.limit:
            post_rows = post_rows[:self.limit]

        temp_deals_info = []
        for row in post_rows:
            if 'notice' in row.find_parent('tr').get('class', []): continue

            tit_element = row.select_one('p.tit a.subject-link')
            if not tit_element: continue

            post_link = urljoin(self.base_url, tit_element['href'])
            
            raw_title = tit_element.get_text(strip=True)
            full_title_text = re.sub(r'\s*댓글\s*\[\d+\]$', '', raw_title).strip()

            price_span = row.select_one('span.text-orange')
            price = self._clean_price(price_span.text.strip() if price_span else '정보 없음')
            
            shipping_fee_span = row.find('span', string=re.compile(r'배송비'))
            shipping_fee = self._clean_shipping_fee(shipping_fee_span.text.replace('배송비', '').strip() if shipping_fee_span else '정보 없음')
            
            shop_name_span = row.select_one('span.brand')
            shop_name = self._clean_shop_name(shop_name_span.get_text(strip=True) if shop_name_span else '정보 없음')
            temp_deals_info.append({
                'post_link': post_link, 
                'full_title': full_title_text,
                'price': price,
                'shipping_fee': shipping_fee,
                'shop_name': shop_name,
            })
        
        # 2단계: 상세 페이지를 방문하여 정보 추출 및 AI 분석
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

                content_element = detail_soup.select_one('div.view-content')
                content_text = content_element.get_text(strip=True, separator='\n') if content_element else ""

                ai_result = ai_parser.parse_content_with_ai(
                    content_text=content_text,
                    post_link=deal_info['post_link'],
                    original_title=deal_info['full_title']
                )

                if not ai_result or not ai_result.get('deals'):
                    print(f"  [Scraper Warning] AI parsing failed for link: {deal_info['post_link']}")
                    continue

                valid_images_in_post = []
                if content_element:
                    all_images = content_element.select('img')
                    for img_tag in all_images:
                        src = img_tag.get('src')
                        if src and not src.startswith('data:image'):
                            full_url = urljoin(self.base_url, src)
                            valid_images_in_post.append(full_url)
                
                processed_titles_for_this_post = set()
                for idx, deal_item in enumerate(ai_result['deals']):
                    product_title = self._clean_text(deal_item.get('product_title'))
                    if product_title in processed_titles_for_this_post: continue
                    
                    shop_name = self._clean_shop_name(deal_info['shop_name'] if deal_info['shop_name'] != '정보 없음' else ai_result.get('shop_name'))
                    shipping_fee = self._clean_shipping_fee(deal_info['shipping_fee'] if deal_info['shipping_fee'] != '정보 없음' else deal_item.get('shipping_fee'))
                    price = self._clean_price(deal_info['price'] if deal_info['price'] != '정보 없음' else deal_item.get('price'))

                    if self.db.query(models.Deal).filter(
                        models.Deal.post_link == deal_info['post_link'],
                        models.Deal.title == product_title
                    ).first():
                        print(f"  [Scraper Info] Skipping duplicate sub-deal: {product_title}")
                        continue
                    
                    processed_titles_for_this_post.add(product_title)
                    
                    assigned_image_url = None
                    if valid_images_in_post:
                        assigned_image_url = valid_images_in_post[idx] if idx < len(valid_images_in_post) else valid_images_in_post[0]

                    category_ai_result = ai_parser.parse_title_with_ai(deal_info['full_title'])
                    category = self._clean_text(category_ai_result.get('category')) if category_ai_result else "기타"

                    new_deal = models.Deal(
                        source_community_id=self.community_entry.id, title=product_title, post_link=deal_info['post_link'],
                        shop_name=shop_name, price=price, shipping_fee=shipping_fee, category=category)
                    deals_data.append({'deal': new_deal, 'original_image_url': assigned_image_url})
            
            except Exception as e:
                print(f"  [Detail Page Error] Link: {deal_info['post_link']}, Error: {e}")

        return deals_data