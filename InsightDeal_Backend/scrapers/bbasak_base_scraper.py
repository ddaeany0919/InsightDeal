import re
from urllib.parse import urljoin, urlparse, urlunparse
from bs4 import BeautifulSoup
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from .base import BaseScraper

class BbasakBaseScraper(BaseScraper):
    """
    빠삭 커뮤니티 전용 스크래퍼
    BaseScraper의 공통 처리 함수를 최대한 활용합니다.
    """

    def scrape(self):
        """
        빠삭 목록 페이지에서 딜 정보를 수집하고,
        상세 분석은 BaseScraper의 공통 처리 함수에 위임합니다.
        """
        # 1. 목록 페이지 로딩 및 기본 정보 수집
        WebDriverWait(self.driver, 15).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "form[name='fboardlist']"))
        )

        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        post_rows = soup.select("form[name='fboardlist'] table tr:has(a.bigSizeLink)")
        
        if self.limit:
            post_rows = post_rows[:self.limit]

        temp_deals_info = []
        for row in post_rows:
            title_element = row.select_one('td.tit a')
            if not title_element:
                continue
                
            href = title_element.get('href')
            if not href or href.strip().startswith('javascript:'):
                continue

            # 링크 처리: href가 이미 전체 URL인지 확인
            if href.startswith('http'):
                desktop_link = href
            else:
                desktop_link = urljoin(self.base_url, href)
                
            # 빠삭은 모바일 페이지(bbasak.com)가 크롤링에 더 안정적이므로 모바일 링크를 사용
            parsed_url = urlparse(desktop_link)
            mobile_url_parts = (
                'https', 
                'bbasak.com', 
                parsed_url.path,
                parsed_url.params, 
                parsed_url.query, 
                parsed_url.fragment
            )
            mobile_link = urlunparse(mobile_url_parts)
            
            full_title = title_element.get_text(strip=True)

            # 이미지 URL 추출 및 원본 변환
            image_tag = row.select_one('td:nth-of-type(4) img')
            original_image_url = None
            if image_tag:
                raw_src = image_tag.get('src')
                if raw_src:
                    full_src = urljoin(self.base_url, raw_src)
                    # 썸네일을 원본 이미지로 변환
                    original_image_url = full_src.replace('_t.png', '_b.png') if full_src.endswith('_t.png') else full_src

            # 제목에서 메타데이터 추출
            shop_name_match = re.search(r'^\[(.*?)\]', full_title)
            list_shop_name = self._clean_shop_name(
                shop_name_match.group(1).strip() if shop_name_match else '정보 없음'
            )

            # 괄호 안의 가격/배송비 정보 추출
            list_price = self._extract_price_from_title(full_title)
            list_shipping_fee = self._extract_shipping_from_title(full_title)

            temp_deals_info.append({
                'post_link': mobile_link,  # 모바일 링크를 메인으로 사용
                'full_title': full_title,
                'original_image_url': original_image_url, # 목록에서 가져온 고품질 이미지
                'list_shop_name': list_shop_name,         # 목록에서 가져온 쇼핑몰 이름
                'list_price': list_price,                 # 목록에서 가져온 가격
                'list_shipping_fee': list_shipping_fee    # 목록에서 가져온 배송비
            })

        # 2. 수집된 기본 정보를 바탕으로 ★공통 상세 페이지 처리 함수 호출★
        return self._process_detail_pages(temp_deals_info)