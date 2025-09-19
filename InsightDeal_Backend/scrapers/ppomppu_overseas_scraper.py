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

class PpomppuOverseasScraper(BaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="뽐뿌해외",
            community_url="https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu4"
        )

    def scrape(self):
        deals_data = []
        
        # 1단계: 목록 페이지에서 링크와 제목만 수집
        WebDriverWait(self.driver, 15).until(EC.presence_of_element_located((By.ID, "revolution_main_table")))
        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_rows = soup.select('tr.baseList')

        if self.limit:
            post_rows = post_rows[:self.limit]

        temp_deals_info = []
        for row in post_rows:
            general_tag = row.select_one('small.baseList-small')
            if general_tag and '[일반]' in general_tag.get_text(strip=True): continue

            title_element = row.select_one('a.baseList-title')
            if not title_element: continue
            
            post_link = urljoin(self.community_url, title_element['href'])
            full_title_text = title_element.get_text(strip=True)
            temp_deals_info.append({'post_link': post_link, 'full_title': full_title_text})

        # 2단계: 상세 페이지를 방문하여 정보 추출 및 AI 분석
        for deal_info in temp_deals_info:
            try:
                full_title = deal_info['full_title']
                is_global_free_shipping = full_title.endswith('/무료') or full_title.endswith('/ 무료')
                # 제목에서 쇼핑몰/가격/배송비 우선 추출 (코드-우선 방식)
                shop_name_match = re.search(r'^\[(.*?)\]', full_title)
                shop_name_code = self._clean_shop_name(shop_name_match.group(1).strip() if shop_name_match else '정보 없음')

                price_code = '정보 없음'
                shipping_fee_code = '정보 없음'
                
                # 제목 끝에 "/무료"가 있는지 확인
                if full_title.endswith('/무료') or full_title.endswith('/ 무료'):
                    shipping_fee_code = '무료'

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

                content_element = detail_soup.select_one('td.board-contents')
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
                        src = img_tag.get('src') or img_tag.get('lazy_src') or img_tag.get('data-original')
                        if src and 'emoticon' not in src.lower() and 'icon' not in src.lower():
                            full_url = urljoin(self.base_url, src)
                            valid_images_in_post.append(full_url)

                for idx, deal_item in enumerate(ai_result['deals']):
                    product_title = self._clean_text(deal_item.get('product_title'))
                    
                    # 하이브리드 로직: 코드로 찾은 값이 없으면 AI 결과 사용
                    shop_name = self._clean_shop_name(shop_name_code if shop_name_code != '정보 없음' else ai_result.get('shop_name'))
                    if 'aliexpress.com' in deal_info['post_link']:
                        shop_name = 'AliExpress'
                    if is_global_free_shipping:
                        shipping_fee = '무료'
                    else:
                        shipping_fee = self._clean_shipping_fee(shipping_fee_code if shipping_fee_code != '정보 없음' else deal_item.get('shipping_fee'))
                    price = self._clean_price(price_code if price_code != '정보 없음' else deal_item.get('price'))

                    if self.db.query(models.Deal).filter(
                        models.Deal.post_link == deal_info['post_link'],
                        models.Deal.title == product_title
                    ).first():
                        print(f"  [Scraper Info] Skipping duplicate sub-deal: {product_title}")
                        continue
                    
                    assigned_image_url = None
                    if valid_images_in_post:
                        assigned_image_url = valid_images_in_post[idx] if idx < len(valid_images_in_post) else valid_images_in_post[0]

                    new_deal = models.Deal(
                        source_community_id=self.community_entry.id, title=product_title, post_link=deal_info['post_link'],
                        shop_name=shop_name, price=price, shipping_fee=shipping_fee, category='해외핫딜')
                    deals_data.append({'deal': new_deal, 'original_image_url': assigned_image_url})
            
            except Exception as e:
                print(f"  [Detail Page Error] Link: {deal_info['post_link']}, Error: {e}")

        return deals_data