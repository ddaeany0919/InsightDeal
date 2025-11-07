"""
상품 스크레이퍼 팩토리
환경변수에 따라 자동으로 Selenium/Web 크롤링 또는 API 선택
"""
import os
import logging
from services.product_scraper_interface import ProductScraperInterface
from services.coupang_web_scraper import CoupangWebScraper
from services.coupang_selenium_scraper import CoupangSeleniumScraper
from services.coupang_api_client import CoupangAPIClient

class ProductScraperFactory:
    """상황에 맞는 스크레이퍼 제공"""
    
    @staticmethod
    def get_scraper(url: str, use_selenium: bool = True) -> ProductScraperInterface:
        """
        URL에 맞는 스크레이퍼 반환
        
        우선순위:
        1. COUPANG_API_KEY 있으면 -> CoupangAPIClient
        2. use_selenium=True면 -> CoupangSeleniumScraper (기본값)
        3. use_selenium=False면 -> CoupangWebScraper
        
        Args:
            url: 상품 URL
            use_selenium: Selenium 사용 여부 (기본: True)
            
        Returns:
            ProductScraperInterface 구현체
        """
        
        # 쿠팡 URL 판별
        if 'coupang.com' in url:
            # API 키가 있으면 API 사용
            if os.getenv('COUPANG_API_KEY'):
                logging.info("[FACTORY] 쿠팡 API 클라이언트 사용")
                return CoupangAPIClient()
            
            # Selenium vs Web Scraper 선택
            if use_selenium:
                logging.info("[FACTORY] 쿠팡 Selenium 크롤러 사용")
                return CoupangSeleniumScraper()
            else:
                logging.info("[FACTORY] 쿠팡 웹 크롤러 사용")
                return CoupangWebScraper()
        
        # 기본: Selenium 크롤러 (TODO: 다른 쇼핑몰 지원 추가)
        logging.info("[FACTORY] 기본 Selenium 크롤러 사용")
        return CoupangSeleniumScraper()
    
    @staticmethod
    def get_coupang_scraper(use_selenium: bool = True) -> ProductScraperInterface:
        """쿠팡 전용 스크레이퍼 반환"""
        if os.getenv('COUPANG_API_KEY'):
            return CoupangAPIClient()
        
        if use_selenium:
            return CoupangSeleniumScraper()
        else:
            return CoupangWebScraper()
