"""
상품 스크레이퍼 팩토리
환경변수에 따라 자동으로 크롤링 또는 API 선택
"""
import os
import logging
from services.product_scraper_interface import ProductScraperInterface
from services.coupang_web_scraper import CoupangWebScraper
from services.coupang_api_client import CoupangAPIClient

class ProductScraperFactory:
    """상황에 맞는 스크레이퍼 제공"""
    
    @staticmethod
    def get_scraper(url: str) -> ProductScraperInterface:
        """
        URL에 맞는 스크레이퍼 반환
        
        우선순위:
        1. COUPANG_API_KEY 있으면 -> CoupangAPIClient
        2. 없으면 -> CoupangWebScraper
        
        Args:
            url: 상품 URL
            
        Returns:
            ProductScraperInterface 구현체
        """
        
        # 쿠팡 URL 판별
        if 'coupang.com' in url:
            # API 키가 있으면 API 사용
            if os.getenv('COUPANG_API_KEY'):
                logging.info("[FACTORY] 쿠팡 API 클라이언트 사용")
                return CoupangAPIClient()
            else:
                logging.info("[FACTORY] 쿠팁 웹 크롤러 사용")
                return CoupangWebScraper()
        
        # 기본: 크롤링 (TODO: 다른 쇼핑몰 지원 추가)
        logging.info("[FACTORY] 기본 크롤러 사용")
        return CoupangWebScraper()
    
    @staticmethod
    def get_coupang_scraper() -> ProductScraperInterface:
        """쿠팡 전용 스크레이퍼 반환"""
        if os.getenv('COUPANG_API_KEY'):
            return CoupangAPIClient()
        return CoupangWebScraper()
