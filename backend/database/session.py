import os
import logging
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.pool import QueuePool
from typing import Generator

logger = logging.getLogger(__name__)

class DatabaseManager:
    """데이터베이스 연결 및 세션 관리 클래스"""
    
    def __init__(self):
        self.engine = None
        self.SessionLocal = None
        self._create_engine()
    
    def _create_engine(self):
        """최적화된 PostgreSQL 엔진 생성"""
        database_url = os.getenv(
            "DATABASE_URL", 
            "postgresql://insightdeal:password@localhost:5432/insightdeal"
        )
        
        logger.info(f"📊 데이터베이스 연결 시도: {database_url.split('@')[1] if '@' in database_url else 'local'}")
        
        self.engine = create_engine(
            database_url,
            poolclass=QueuePool,
            pool_size=10,
            max_overflow=20,
            pool_pre_ping=True,
            pool_recycle=3600,  # 1시간
            echo=False,  # 프로덕션에서는 False
            connect_args={
                "options": "-c timezone=Asia/Seoul"
            }
        )
        
        self.SessionLocal = sessionmaker(
            autocommit=False, 
            autoflush=False, 
            bind=self.engine
        )
        
        # 연결 이벤트 리스너
        @event.listens_for(self.engine, "connect")
        def set_postgres_settings(dbapi_connection, connection_record):
            logger.info("✅ PostgreSQL 데이터베이스 연결 성공")
        
        @event.listens_for(self.engine, "close")
        def on_disconnect(dbapi_connection, connection_record):
            logger.debug("🔌 데이터베이스 연결 종료")
    
    def get_session(self) -> Session:
        """단일 DB 세션 반환"""
        return self.SessionLocal()
    
    def get_session_context(self) -> Generator[Session, None, None]:
        """컨텍스트 매니저로 사용할 수 있는 DB 세션"""
        session = self.SessionLocal()
        try:
            yield session
        except Exception as e:
            logger.error(f"❌ 데이터베이스 세션 오류: {e}")
            session.rollback()
            raise
        finally:
            session.close()
    
    def close_all_connections(self):
        """모든 연결 종료"""
        if self.engine:
            self.engine.dispose()
            logger.info("🔌 모든 데이터베이스 연결 종료")
    
    def init_database(self):
        """데이터베이스 초기화 (테이블 생성)"""
        from backend.database.models import Base, Community
        
        try:
            logger.info("🗄️ 데이터베이스 테이블 생성 시작...")
            # 모든 테이블 생성
            Base.metadata.create_all(self.engine)
            
            # 기본 커뮤니티 데이터 삽입
            with self.get_session_context() as session:
                communities = [
                    Community(name="뽐뿌", base_url="https://www.ppomppu.co.kr"),
                    Community(name="루리웹", base_url="https://bbs.ruliweb.com"),
                    Community(name="클리앙", base_url="https://www.clien.net"),
                    Community(name="알리뽐뿌", base_url="https://www.ppomppu.co.kr"),
                    Community(name="퀘이사존", base_url="https://quasarzone.com"),
                    Community(name="페던코리아", base_url="https://www.fmkorea.com")
                ]
                
                for community in communities:
                    existing = session.query(Community).filter(
                        Community.name == community.name
                    ).first()
                    
                    if not existing:
                        session.add(community)
                        logger.info(f"✅ 커뮤니티 '{community.name}' 추가")
                
                session.commit()
            
            logger.info("✅ 데이터베이스 초기화 완료")
            return True
            
        except Exception as e:
            logger.error(f"❌ 데이터베이스 초기화 실패: {e}")
            return False
    
    def test_connection(self) -> bool:
        """데이터베이스 연결 테스트"""
        try:
            with self.engine.connect() as conn:  # 변경된 부분
                conn.execute("SELECT 1")
            logger.info("✅ 데이터베이스 연결 테스트 성공")
            return True
        except Exception as e:
            logger.error(f"❌ 데이터베이스 연결 테스트 실패: {e}")
            return False

# 싱글톤 인스턴스
db_manager = DatabaseManager()

# FastAPI 의존성 주입을 위한 함수
def get_db_session() -> Generator[Session, None, None]:
    """
    FastAPI Depends에서 사용할 데이터베이스 세션 제네레이터
    
    Usage:
        from fastapi import Depends
        from backend.database.session import get_db_session
        
        @app.get("/deals")
        def get_deals(db: Session = Depends(get_db_session)):
            return db.query(Deal).all()
    """
    session = db_manager.get_session()
    try:
        yield session
    except Exception as e:
        logger.error(f"❌ API 세션 오류: {e}")
        session.rollback()
        raise
    finally:
        session.close()

# 스케줄러/스크래퍼를 위한 직접 세션 생성
def create_db_session() -> Session:
    """
    스케줄러나 백그라운드 작업에서 사용할 단일 DB 세션
    
    Usage:
        from backend.database.session import create_db_session
        
        def run_scraper():
            with create_db_session() as session:
                # DB 작업
                pass
    """
    return db_manager.get_session()

# 하위 호환성을 위한 alias
SessionLocal = db_manager.SessionLocal
engine = db_manager.engine
get_db = get_db_session  # wishlist.py 등에서 사용하는 get_db import 오류 해결

# 데이터베이스 사전 초기화 (import 시)
if __name__ != "__main__":
    # 서버 시작 시 자동 초기화
    try:
        if not db_manager.test_connection():
            logger.warning("⚠️ 데이터베이스 연결에 실패했지만 계속 진행")
        else:
            db_manager.init_database()
    except Exception as e:
        logger.error(f"❌ 데이터베이스 초기화 중 오류: {e}")
        logger.warning("⚠️ 데이터베이스 오류가 발생했지만 서버는 계속 실행됩니다")

# CLI 실행용
if __name__ == "__main__":
    print("🗄️ 데이터베이스 초기화 시작...")
    
    if db_manager.test_connection():
        print("✅ 데이터베이스 연결 성공")
        
        if db_manager.init_database():
            print("✅ 데이터베이스 초기화 완료")
        else:
            print("❌ 데이터베이스 초기화 실패")
    else:
        print("❌ 데이터베이스 연결 실패")
