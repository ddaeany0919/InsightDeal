import logging
import sys
from pathlib import Path
from logging.handlers import RotatingFileHandler
from datetime import datetime
from config import config

class ScraperLogger:
    """스크래퍼 전용 로거 클래스"""
    
    def __init__(self, name: str = "InsightDeal"):
        self.name = name
        self.logger = None
        self._setup_logger()
    
    def _setup_logger(self):
        """로거 설정 초기화"""
        config.ensure_log_dir()
        
        # 로거 생성
        self.logger = logging.getLogger(self.name)
        self.logger.setLevel(getattr(logging, config.LOG_LEVEL))
        
        # 중복 핸들러 방지
        if self.logger.handlers:
            return
        
        # 포맷터 생성
        formatter = logging.Formatter(
            fmt='%(asctime)s | %(levelname)-8s | %(name)s:%(funcName)s:%(lineno)d | %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )
        
        # 파일 핸들러 (회전 로그)
        file_handler = RotatingFileHandler(
            config.LOG_DIR / f"scraper_{datetime.now().strftime('%Y%m%d')}.log",
            maxBytes=10*1024*1024,  # 10MB
            backupCount=5
        )
        file_handler.setFormatter(formatter)
        file_handler.setLevel(logging.DEBUG)
        
        # 콘솔 핸들러
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setFormatter(formatter)
        console_handler.setLevel(logging.INFO)
        
        # 핸들러 추가
        self.logger.addHandler(file_handler)
        self.logger.addHandler(console_handler)
    
    def get_logger(self):
        return self.logger

# 싱글톤 로거 인스턴스
logger = ScraperLogger().get_logger()
