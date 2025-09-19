import re
import time
from urllib.parse import urljoin, urlparse, urlunparse
from bs4 import BeautifulSoup
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from .base import BaseScraper
import models
import ai_parser

class BbasakBaseScraper(BaseScraper):
    def scrape(self):
        deals_data = []
        
        WebDriverWait(self.driver, 15).until(EC.presence_of_element_located((By.CSS_SELECTOR, "form[name='fboardlist']")))
        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_rows = soup.select("form[name='fboardlist'] table tr:has(a.bigSizeLink)")

        if self.limit:
            post_rows = post_rows[:self.limit]

        temp_deals_info = []
        for row in post_rows:
            title_element = row.select_one('td.tit a')
            if not title_element: continue
            
            href = title_element.get('href')
            if not href or href.strip().startswith('javascript:'):
                continue

            # --- ✨ 수정: 데스크톱과 모바일 링크를 분리하여 생성 ---
            # 1. 스크래퍼가 접속할 데스크톱 링크 (href가 이미 완전한 주소)
            desktop_link = href
            
            # 2. DB에 저장할 모바일 링크 (더 안정적인 urlparse 사용)
            parsed_url = urlparse(desktop_link)
            mobile_url_parts = ('https', 'bbasak.com', parsed_url.path, parsed_url.params, parsed_url.query, parsed_url.fragment)
            mobile_link = urlunparse(mobile_url_parts)
            # --- 수정 완료 ---

            full_title_text = title_element.get_text(strip=True)

            image_tag = row.select_one('td:nth-of-type(4) img')
            original_image_url = None
            if image_tag:
                raw_src = image_tag.get('src')
                if raw_src:
                    full_src = urljoin(self.base_url, raw_src)
                    if full_src.endswith('_t.png'):
                        original_image_url = full_src.replace('_t.png', '_b.png')
                    else:
                        original_image_url = full_src
            
            temp_deals_info.append({
                'desktop_link': desktop_link,  # 스크래퍼 접속용
                'post_link': mobile_link,       # DB 저장 및 앱 사용용
                'full_title': full_title_text,
                'original_image_url': original_image_url
            })
            
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

                # --- ✨ 수정: 스크래퍼는 데스크톱 링크로 접속 ---
                self.driver.get(deal_info['desktop_link'])
                time.sleep(0.5)
                # --- 수정 완료 ---
                
                detail_soup = BeautifulSoup(self.driver.page_source, 'html.parser')

                content_element = detail_soup.select_one('#bo_v_atc')
                content_text = content_element.get_text(strip=True, separator='\n') if content_element else ""

                ai_result = ai_parser.parse_content_with_ai(
                    content_text=content_text,
                    post_link=deal_info['post_link'],
                    original_title=deal_info['full_title']
                )

                if not ai_result or not ai_result.get('deals'):
                    print(f"  [Scraper Warning] AI parsing failed for link: {deal_info['post_link']}")
                    continue

                processed_titles_for_this_post = set()
                for idx, deal_item in enumerate(ai_result['deals']):
                    product_title = self._clean_text(deal_item.get('product_title'))
                    if product_title in processed_titles_for_this_post: continue
                    
                    shop_name = self._clean_shop_name(shop_name_code if shop_name_code != '정보 없음' else ai_result.get('shop_name'))
                    shipping_fee = self._clean_shipping_fee(shipping_fee_code if shipping_fee_code != '정보 없음' else deal_item.get('shipping_fee'))
                    price = self._clean_price(price_code if price_code != '정보 없음' else deal_item.get('price'))
                    
                    if self.db.query(models.Deal).filter(
                        models.Deal.post_link == deal_info['post_link'],
                        models.Deal.title == product_title
                    ).first():
                        print(f"  [Scraper Info] Skipping duplicate sub-deal: {product_title}")
                        continue
                    
                    processed_titles_for_this_post.add(product_title)
                    
                    category_ai_result = ai_parser.parse_title_with_ai(deal_info['full_title'])
                    category = self._clean_text(category_ai_result.get('category')) if category_ai_result else "기타"

                    new_deal = models.Deal(
                        source_community_id=self.community_entry.id, title=product_title, 
                        post_link=deal_info['post_link'], # ✨ DB에는 모바일 링크 저장
                        shop_name=shop_name, price=price, shipping_fee=shipping_fee, category=category)
                    deals_data.append({'deal': new_deal, 'original_image_url': deal_info['original_image_url']})
            
            except Exception as e:
                print(f"  [Detail Page Error] Link: {deal_info['post_link']}, Error: {e}")

        return deals_data