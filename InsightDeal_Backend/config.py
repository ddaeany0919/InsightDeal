import os
from pathlib import Path
from typing import Optional

class Config:
    """애플리케이션 설정 관리 클래스"""
    
    # 데이터베이스 설정
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL", 
        "postgresql://insightdeal:password@localhost:5432/insightdeal"
    )
    
    # 스크래핑 설정
    SELENIUM_TIMEOUT: int = int(os.getenv("SELENIUM_TIMEOUT", "15"))
    SCRAPER_DELAY: int = int(os.getenv("SCRAPER_DELAY", "1"))
    MAX_RETRY_COUNT: int = int(os.getenv("MAX_RETRY_COUNT", "3"))
    
    # 로깅 설정
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")
    LOG_DIR: Path = Path(__file__).parent / "logs"

    PARALLEL_EXECUTION: bool = os.getenv("PARALLEL_EXECUTION", "False").lower() == "true"
    # API 키
    GEMINI_API_KEY: Optional[str] = os.getenv("GEMINI_API_KEY")
    
    # User-Agent 로테이션
    USER_AGENTS = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    ]
    
    @classmethod
    def ensure_log_dir(cls):
        """로그 디렉토리 생성"""
        cls.LOG_DIR.mkdir(exist_ok=True)
        return cls.LOG_DIR

config = Config()
