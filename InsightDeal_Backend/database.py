import os
from sqlalchemy import create_engine, event
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import QueuePool
from config import config
from logger import logger

class Database:
    """데이터베이스 연결 관리 클래스"""
    
    def __init__(self):
        self.engine = None
        self.SessionLocal = None
        self._create_engine()
    
    def _create_engine(self):
        """최적화된 엔진 생성"""
        self.engine = create_engine(
            config.DATABASE_URL,
            poolclass=QueuePool,
            pool_size=10,
            max_overflow=20,
            pool_pre_ping=True,
            pool_recycle=3600,
            echo=False  # 프로덕션에서는 False
        )
        
        self.SessionLocal = sessionmaker(
            autocommit=False, 
            autoflush=False, 
            bind=self.engine
        )
        
        # 연결 이벤트 리스너
        @event.listens_for(self.engine, "connect")
        def set_sqlite_pragma(dbapi_connection, connection_record):
            logger.info("Database connection established")
    
    def get_session(self):
        """DB 세션 반환"""
        return self.SessionLocal()
    
    def close_all_connections(self):
        """모든 연결 종료"""
        if self.engine:
            self.engine.dispose()
            logger.info("Database connections closed")

# 싱글톤 데이터베이스 인스턴스
db_manager = Database()
SessionLocal = db_manager.SessionLocal
engine = db_manager.engine
Base = declarative_base()
