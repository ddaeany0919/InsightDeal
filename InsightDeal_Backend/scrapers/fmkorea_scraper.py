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

class FmkoreaScraper(BaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="펨코",
            community_url="https://www.fmkorea.com/hotdeal"
        )

    def scrape(self):
        deals_data = []
        
        # 1단계: 목록 페이지에서 기본 정보(링크, 제목) 수집
        for i in range(3):
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
            if not title_tag: continue

            post_link = urljoin(self.community_url, title_tag['href'])
            full_title_text = title_tag.get_text(strip=True)
            
            shipping_fee_match = re.search(r'배송: <a.*?>(.*?)</a>', hotdeal_info_html)
            shipping_fee = self._clean_shipping_fee(shipping_fee_match.group(1).strip() if shipping_fee_match else '정보 없음')
            
            shop_name_match = re.search(r'쇼핑몰: <a.*?>(.*?)</a>', hotdeal_info_html)
            shop_name = self._clean_text(shop_name_match.group(1).strip() if shop_name_match else '정보 없음')

            price_match = re.search(r'가격: <a.*?>(.*?)</a>', hotdeal_info_html)
            price = self._clean_price(price_match.group(1).strip() if price_match else '정보 없음')
            # --- 수정 완료 ---
            temp_deals_info.append({
                'post_link': post_link, 
                'full_title': full_title_text,
                'shipping_fee': shipping_fee,
                'shop_name': shop_name,
                'price': price
            })

        # 2단계: 상세 페이지를 방문하며 AI에게 종합 분석 요청
        for deal_info in temp_deals_info:
            try:
                self.driver.get(deal_info['post_link'])
                
                WebDriverWait(self.driver, 10).until(
                    EC.presence_of_element_located((By.CSS_SELECTOR, "div.rd_body"))
                )
                
                detail_soup = BeautifulSoup(self.driver.page_source, 'html.parser')

                # --- 수정된 부분: 핫딜 정보 박스와 본문 내용을 모두 수집 ---
                # 1. 핫딜 정보가 담긴 박스의 텍스트를 가져옴
                info_box_element = detail_soup.select_one('div.rhymix_content')
                info_box_text = info_box_element.get_text(strip=True, separator='\n') if info_box_element else ""

                # 2. 실제 게시물 본문 텍스트를 가져옴
                main_content_element = detail_soup.select_one('div.rd_body article')
                main_content_text = main_content_element.get_text(strip=True, separator='\n') if main_content_element else ""
                
                # 3. 두 텍스트를 합쳐서 AI에게 전달할 전체 본문을 구성
                content_text = f"{info_box_text}\n\n{main_content_text}"
                # --- 수정 완료 ---
                
                print(f"  [AI Input Text] Submitting following text to AI:\n---\n{content_text[:500]}...\n---")
                
                ai_result = ai_parser.parse_content_with_ai(
                    content_text=content_text,
                    post_link=deal_info['post_link'],
                    original_title=deal_info['full_title']
                )

                if not ai_result or not ai_result.get('deals'):
                    print(f"  [Scraper Warning] AI parsing failed for link: {deal_info['post_link']}")
                    continue

                shop_name = deal_info['shop_name']
                if shop_name == '정보 없음':
                    shop_name = self._clean_text(ai_result.get('shop_name'))

                shipping_fee = deal_info['shipping_fee']
                if shipping_fee == '정보 없음':
                    shipping_fee = self._clean_shipping_fee(ai_result.get('shipping_fee'))

                valid_images_in_post = []
                image_container = detail_soup.select_one('div.rd_body')
                if image_container:
                    all_images = image_container.select('img')
                    for img_tag in all_images:
                        src = img_tag.get('src') or img_tag.get('data-src')
                        if src:
                            full_url = 'https:' + src if src.startswith('//') else urljoin(self.base_url, src)
                            if 'small_' in full_url: full_url = full_url.replace('small_', '')
                            valid_images_in_post.append(full_url)
                
                for idx, deal_item in enumerate(ai_result['deals']):
                    product_title = self._clean_text(deal_item.get('product_title'))
                    price = deal_info['price']
                    if price == '정보 없음':
                        ai_price = self._clean_price(deal_item.get('price'))
                        print(f"  [Price Info] Price not in list view. Using AI result: '{ai_price}'")
                        price = ai_price
                    else:
                        print(f"  [Price Info] Using price from list view: '{price}'")

                    if self.db.query(models.Deal).filter(
                        models.Deal.post_link == deal_info['post_link'],
                        models.Deal.title == product_title
                    ).first():
                        print(f"  [Scraper Info] Skipping duplicate sub-deal: {product_title}")
                        continue
                    
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