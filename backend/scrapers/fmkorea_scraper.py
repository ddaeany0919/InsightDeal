import re
import time
import random
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from .base import BaseScraper
from datetime import datetime
import logging

# 로거 설정
logger = logging.getLogger(__name__)

class FmkoreaScraper(BaseScraper):
    def __init__(self, db_session):
        super().__init__(
            db_session,
            community_name="펨코",
            community_url="https://www.fmkorea.com/"
        )

    def scrape(self):
        """
        펨코리아 목록 페이지에서 딜 정보를 수집합니다.
        Cloudflare Turnstile 우회를 위해 신뢰 구축 단계를 거칩니다.
        """
        if not self.driver:
            self._create_selenium_driver()
            
        try:
            # 0단계: 쿠키 로드 시도
            cookies_loaded = self._load_cookies()
            if cookies_loaded:
                logger.info(f"[{self.community_name}] 저장된 쿠키를 불러왔습니다. 유효성 검사 중...")
                self.driver.get("https://www.fmkorea.com/hotdeal?listStyle=list")
                time.sleep(3)
                if "에펨코리아 보안 시스템" not in self.driver.title:
                    soup = BeautifulSoup(self.driver.page_source, 'html.parser')
                    if soup.select('tbody tr'):
                        logger.info(f"[{self.community_name}] ✅ 기존 쿠키로 보안 챌린지 건너뛰기 성공!")
                        return self._extracted_scraped_items() # 별도 메서드로 추출 (아래 수정)
            
            # 1단계: 메인 페이지 접속 (쿠키 생성)
            logger.info(f"[{self.community_name}] 신뢰 구축 1단계: 메인 페이지 접속")
            self.driver.get("https://www.fmkorea.com/")
            time.sleep(random.uniform(4, 6))
            
            # 2단계: 중립적인 서브 페이지 접속 (자유게시판 등)
            logger.info(f"[{self.community_name}] 신뢰 구축 2단계: 서브 페이지 접속")
            self.driver.get("https://www.fmkorea.com/free")
            time.sleep(random.uniform(3, 5))
            
            # 3단계: 핫딜 페이지 접속 (listStyle=list 강제)
            logger.info(f"[{self.community_name}] 최종 목적지: 핫딜 페이지 이동")
            target_url = "https://www.fmkorea.com/hotdeal?listStyle=list"
            self.driver.get(target_url)
            
            # 페이지 로딩 및 챌린지 대기 (루프 기반 재시도)
            total_wait = 90
            start_time = time.time()
            success = False
            while time.time() - start_time < total_wait:
                if "에펨코리아 보안 시스템" not in self.driver.title:
                    soup = BeautifulSoup(self.driver.page_source, 'html.parser')
                    if soup.select('tbody tr'):
                        logger.info(f"[{self.community_name}] ✅ 보안 챌린지 우회 성공!")
                        # 성공 시 쿠키 저장
                        self._save_cookies()
                        success = True
                        break
                
                logger.info(f"[{self.community_name}] 보안 챌린지 대기 중... (Title: {self.driver.title})")
                time.sleep(5)
                # 인간미 넘치는 스크롤 추가
                self.driver.execute_script("window.scrollTo(0, document.body.scrollHeight/4);")
            
            if not success:
                raise Exception("보안 챌린지 우회 실패 (타임아웃)")
            
        except Exception as e:
            logger.warning(f"[{self.community_name}] 보안 챌린지 우회 실패: {e}")
            if self.driver:
                try:
                    with open("/app/fmkorea_fail_source.html", "w", encoding="utf-8") as f:
                        f.write(self.driver.page_source)
                except: pass
            return []

        return self._extracted_scraped_items()

    def _extracted_scraped_items(self):
        """실제 목록 데이터 추출 로직 (scrape 메서드에서 분리)"""
        soup = BeautifulSoup(self.driver.page_source, 'html.parser')
        
        # 목록 항목 수집 (테이블 형식과 웹진/리스트 형식 모두 고려)
        post_rows = []
        
        # 1. 일반 게시글 테이블 탐색
        all_tables = soup.select('table.bd_lst')
        for table in all_tables:
            if 'common_notice' in (table.get('class') or []):
                continue
            post_rows.extend(table.select('tbody tr'))
            
        # 2. 리스트 형식 탐색
        if not post_rows:
            post_rows = soup.select('li.li:not(.notice), div.list_item:not(.notice)')
            
        logger.info(f"[{self.community_name}] Found {len(post_rows)} potential items.")

        temp_deals_info = []
        for row in post_rows:
            # 공지 제외 로직
            row_classes = row.get('class') or []
            if any(cls in row_classes for cls in ['notice', 'notice_pop0', 'notice_pop1', 'notice_pop2']):
                continue
                
            no_tag = row.select_one('td.no')
            if no_tag and no_tag.get_text(strip=True) in ['공지', '인기', 'AD', '광고']:
                continue

            # 제목 및 링크 추출
            title_element = row.select_one('td.title a, h3.title a, a.title')
            if not title_element:
                if row.name == 'a' and 'title' in (row.get('class') or []):
                    title_element = row
                else:
                    continue

            href = title_element.get('href', '')
            if not href or href.startswith('javascript'): continue
            
            post_link = urljoin(self.base_url, href)
            full_title_text = title_element.get_text(strip=True)
            
            logger.info(f"[{self.community_name}] 게시글 발견: {full_title_text}")

            temp_deals_info.append({
                'post_link': post_link,
                'full_title': full_title_text
            })

            if self.limit and len(temp_deals_info) >= self.limit:
                break

        return self._process_detail_pages(temp_deals_info)

    def get_post_details(self, post_url):
        """펨코리아 전용 게시글 상세 정보 추출"""
        logger.info(f"[{self.community_name}] 게시글 상세 추출 시작: {post_url[:50]}...")
        
        try:
            if not self.driver:
                self._create_selenium_driver()
                
            self.driver.get(post_url)
            self.wait.until(EC.presence_of_element_located((By.TAG_NAME, 'body')))
            
            soup = BeautifulSoup(self.driver.page_source, 'html.parser')
            soup['data-url'] = post_url
            
            # 펨코리아 전용 최적화 설정
            site_config = {
                "content_selectors": ['.post-content', '.article-content', '.xe_content'],
                "time_selectors": ['.date', '.time', '.post-time'],
                "time_patterns": [
                    r'(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2})',
                    r'(\d{4}\.\d{2}\.\d{2}\s+\d{2}:\d{2})'
                ],
                "exclude_image_keywords": ['icon_', 'btn_', 'logo_', 'fm_'],
                "text_selectors": ['p', 'div']
            }
            
            # base.py의 공통 메서드 활용
            result = self.extract_post_content_and_images(
                soup, 
                site_config["content_selectors"],
                self.base_url,
                site_config
            )
            
            logger.info(f"[{self.community_name}] 추출 완료! 이미지: {len(result['images'])}개, 텍스트: {len(result['content'])}자")
            return result
            
        except Exception as e:
            logger.error(f"[{self.community_name}] 추출 실패: {e}")
            return {
                "images": [],
                "content": "게시글 정보를 불러올 수 없습니다.",
                "posted_time": None,
                "error": str(e),
                "crawled_at": datetime.now().isoformat()
            }
