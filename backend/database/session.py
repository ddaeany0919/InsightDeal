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
        """엔진 생성 (앱/웹 공용)"""
        import os
        from dotenv import load_dotenv
        
        # 최상위 .env 로드
        base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        load_dotenv(os.path.join(base_dir, '.env'))
        
        # 무조건 backend/ 폴더 안의 insight_deal.db 파일을 공유하도록 절대경로 지정
        backend_dir = os.path.join(base_dir, "backend")
        default_db_path = os.path.join(backend_dir, "insight_deal.db")
        
        database_url = os.getenv("DATABASE_URL")
        # 호스트 PC에서 도커용 postgres URL로 접속을 시도하면 연결 오류가 나므로 로컬에선 SQLite 강제 처리
        if not database_url or "postgres:5432" in database_url:
            database_url = f"sqlite:///{default_db_path}"
            
        logger.info(f"📊 데이터베이스 연결 시도: {database_url}")
        
        # SQLite 특화 설정 적용
        connect_args = {"check_same_thread": False} if database_url.startswith("sqlite") else {"options": "-c timezone=Asia/Seoul"}
        
        self.engine = create_engine(
            database_url,
            pool_pre_ping=True,
            echo=False,
            connect_args=connect_args
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
        try:
            logger.info("🗄️ 데이터베이스 테이블 생성 시작...")
            
            # 모델 import
            from backend.database.models import Base as CommunityBase, Community
            from backend.models.wishlist_models import Base as WishlistBase
            
            # 1. 커뮤니티/딩/상품 테이블 생성
            logger.info("📄 커뮤니티 관련 테이블 생성 중...")
            CommunityBase.metadata.create_all(self.engine)
            
            # 2. 위시리스트 테이블 생성
            logger.info("💚 위시리스트 테이블 생성 중...")
            WishlistBase.metadata.create_all(self.engine)
            
            # 기본 커뮤니티 데이터 삽입
            session = self.get_session()  # 컨텍스트 매니저 대신 직접 생성/닫기
            try:
                communities = [
                    Community(name="뿐뿐", base_url="https://www.ppomppu.co.kr"),
                    Community(name="루리웹", base_url="https://bbs.ruliweb.com"),
                    Community(name="클리앙", base_url="https://www.clien.net"),
                    Community(name="알리뿐뿐", base_url="https://www.ppomppu.co.kr"),
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
            except Exception as e:
                logger.warning(f"⚠️ 커뮤니티 데이터 초기화 경고: {e}")
                session.rollback()
            finally:
                session.close()
            
            logger.info("✅ 데이터베이스 초기화 완료")
            return True
        except Exception as e:
            logger.error(f"❌ 데이터베이스 초기화 실패: {e}", exc_info=True)
            return False
    
    def test_connection(self) -> bool:
        """데이터베이스 연결 테스트"""
        try:
            with self.engine.connect() as conn:
                conn.execute("SELECT 1")
            logger.info("✅ 데이터베이스 연결 테스트 성공")
            return True
        except Exception as e:
            logger.error(f"❌ 데이터베이스 연결 테스트 실패: {e}")
            return False

# 싱글턴 인스턴스
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
