import os
import re
import sqlite3
import logging
from datetime import datetime, timedelta, timezone
from sqlalchemy import create_engine, event, text
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.pool import QueuePool, StaticPool
from typing import Generator

logger = logging.getLogger(__name__)

class DatabaseManager:
    """데이터베이스 연결, 인덱스 최적화 및 안전 백업 관리 클래스"""
    
    def __init__(self):
        self.engine = None
        self.SessionLocal = None
        self.db_file_path = None
        self._create_engine()
    
    def _create_engine(self):
        """엔진 생성 (앱/웹 공용)"""
        from dotenv import load_dotenv
        
        backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        base_dir = os.path.dirname(backend_dir)
        load_dotenv(os.path.join(base_dir, '.env'))
        
        database_url = os.getenv("DATABASE_URL")
        if not database_url:
            default_db_path = os.path.join(backend_dir, "insight_deal.db")
            self.db_file_path = default_db_path
            database_url = f"sqlite:///{default_db_path}"
        else:
            if database_url.startswith("postgres://"):
                database_url = "postgresql://" + database_url[len("postgres://"):]
            if database_url.startswith("sqlite:///") and ":memory:" not in database_url:
                self.db_file_path = database_url.replace("sqlite:///", "", 1)

        masked_url = re.sub(r":([^:@/]+)@", ":***@", database_url)
        logger.info(f"데이터베이스 연결 시도: {masked_url}")

        is_sqlite = database_url.startswith("sqlite")
        is_memory_sqlite = is_sqlite and (
            database_url in ("sqlite://", "sqlite:///:memory:") or ":memory:" in database_url
        )
        connect_args = (
            {"check_same_thread": False, "timeout": 30}
            if is_sqlite
            else {"options": "-c timezone=Asia/Seoul"}
        )

        engine_kwargs = {
            "echo": False,
            "connect_args": connect_args,
        }
        if is_memory_sqlite:
            engine_kwargs["poolclass"] = StaticPool
        else:
            engine_kwargs.update({
                "poolclass": QueuePool,
                "pool_size": 10,
                "max_overflow": 20,
                "pool_pre_ping": True,
            })

        self.engine = create_engine(database_url, **engine_kwargs)
        
        if database_url.startswith("sqlite"):
            @event.listens_for(self.engine, "connect")
            def set_sqlite_pragma(dbapi_connection, connection_record):
                cursor = dbapi_connection.cursor()
                cursor.execute("PRAGMA journal_mode=WAL")
                cursor.execute("PRAGMA synchronous=NORMAL")
                cursor.execute("PRAGMA busy_timeout=60000")
                cursor.execute("PRAGMA cache_size=-32000")  # 32MB in-memory page cache
                cursor.execute("PRAGMA temp_store=MEMORY")
                cursor.close()
        
        self.SessionLocal = sessionmaker(
            autocommit=False, 
            autoflush=False, 
            expire_on_commit=False,
            bind=self.engine
        )
        
        if not database_url.startswith("sqlite"):
            @event.listens_for(self.engine, "connect")
            def set_postgres_settings(dbapi_connection, connection_record):
                logger.info("PostgreSQL 데이터베이스 연결 성공")
        
        @event.listens_for(self.engine, "close")
        def on_disconnect(dbapi_connection, connection_record):
            logger.debug("데이터베이스 연결 종료")
    
    def get_session(self) -> Session:
        """단일 DB 세션 반환"""
        return self.SessionLocal()
    
    def get_session_context(self) -> Generator[Session, None, None]:
        """컨텍스트 매니저로 사용할 수 있는 DB 세션"""
        session = self.SessionLocal()
        try:
            yield session
        except Exception as e:
            logger.error(f"데이터베이스 세션 오류: {e}")
            session.rollback()
            raise
        finally:
            session.close()
    
    def close_all_connections(self):
        """모든 연결 종료"""
        if self.engine:
            self.engine.dispose()
            logger.info("모든 데이터베이스 연결 종료")

    def ensure_schema_migrations(self):
        """구형 SQLite/PostgreSQL 스키마에 누락된 컬럼을 안전하게 추가 (무중단 자동 마이그레이션)"""
        if self.engine is None:
            return
        from sqlalchemy import inspect
        inspector = inspect(self.engine)
        existing_tables = set(inspector.get_table_names())
        is_sqlite = (self.engine.dialect.name == "sqlite")

        migrations: dict[str, list[tuple[str, str, str]]] = {
            "notification_alerts": [
                ("is_read", "BOOLEAN NOT NULL DEFAULT 0", "BOOLEAN NOT NULL DEFAULT FALSE"),
                ("dnd_suppressed", "BOOLEAN NOT NULL DEFAULT 0", "BOOLEAN NOT NULL DEFAULT FALSE"),
                ("digest_sent_at", "TIMESTAMP NULL", "TIMESTAMPTZ NULL"),
            ],
            "users": [
                ("token_version", "INTEGER NOT NULL DEFAULT 0", "INTEGER NOT NULL DEFAULT 0"),
            ],
            "deals": [
                ("brand", "VARCHAR(100)", "VARCHAR(100)"),
                ("model_code", "VARCHAR(100)", "VARCHAR(100)"),
            ],
            "device_tokens": [
                ("dnd_enabled", "BOOLEAN NOT NULL DEFAULT 0", "BOOLEAN NOT NULL DEFAULT FALSE"),
                ("dnd_start_time", "VARCHAR(5) NOT NULL DEFAULT '21:00'", "VARCHAR(5) NOT NULL DEFAULT '21:00'"),
                ("dnd_end_time", "VARCHAR(5) NOT NULL DEFAULT '08:00'", "VARCHAR(5) NOT NULL DEFAULT '08:00'"),
                ("dnd_settings_json", "TEXT", "TEXT"),
                ("web_push_subscription", "TEXT", "TEXT"),
            ],
        }

        for table_name, cols in migrations.items():
            if table_name not in existing_tables:
                continue
            existing_cols = {c["name"] for c in inspector.get_columns(table_name)}
            for col_name, sqlite_def, pg_def in cols:
                if col_name in existing_cols:
                    continue
                col_sql = sqlite_def if is_sqlite else pg_def
                stmt = (
                    f"ALTER TABLE {table_name} ADD COLUMN {col_name} {col_sql}"
                    if is_sqlite
                    else f"ALTER TABLE {table_name} ADD COLUMN IF NOT EXISTS {col_name} {col_sql}"
                )
                try:
                    with self.engine.begin() as conn:
                        conn.execute(text(stmt))
                    logger.info(f"자동 마이그레이션 컬럼 추가 완료: {table_name}.{col_name}")
                except Exception as e:
                    logger.warning(f"자동 마이그레이션 컬럼 추가 스킵 ({table_name}.{col_name}): {e}")

    def ensure_performance_indexes(self):
        """6개월 시세 차트, DSU 클러스터링, 피드 필터링 고속화를 위한 복합 인덱스 자동 보장"""
        index_statements = [
            "CREATE INDEX IF NOT EXISTS idx_deals_closed_indexed ON deals (is_closed, indexed_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_deals_community_closed_indexed ON deals (source_community_id, is_closed, indexed_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_deals_base_product_indexed ON deals (base_product_name, indexed_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_deals_category_closed ON deals (category, is_closed, indexed_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_price_history_deal_checked ON price_history (deal_id, checked_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_wishlist_user_active ON wishlist (user_id, is_active, alert_enabled)",
            "CREATE INDEX IF NOT EXISTS idx_notif_alerts_dnd_digest ON notification_alerts (user_id, dnd_suppressed, digest_sent_at)",
        ]
        created_count = 0
        for stmt in index_statements:
            try:
                with self.engine.begin() as conn:
                    conn.execute(text(stmt))
                created_count += 1
            except Exception as e:
                logger.warning(f"복합 인덱스 생성 스킵 ({stmt}): {e}")
        logger.info(f"고속 조회용 복합 인덱스({created_count}/{len(index_statements)}종) 점검 및 적용 완료")

    def perform_maintenance_and_backup(
        self, 
        max_backups: int = 3, 
        slim_days: int = 180,
        clean_image_cache: bool = True,
        cache_max_age_days: int = 7,
        cache_max_mb: int = 500
    ) -> dict:
        """
        1) 180일(6개월) 경과 딜의 무거운 HTML 본문(content_html) 슬림화 (가격/시세/제목은 영구 보존)
        2) SQLite WAL 체크포인트 병합
        3) 온라인 무중단 백업 (backend/backups/ 폴더에 최근 3일치 유지)
        4) 이미지 캐시 7일 만료 및 500MB 상한선 LRU 자동 정제 (GCP 디스크 용량 영구 방어)
        """
        summary = {"slimmed_deals": 0, "backup_path": None, "cache_summary": None}
        session = self.get_session()
        try:
            cutoff_date = datetime.now(timezone.utc) - timedelta(days=slim_days)
            if self.engine is not None and self.engine.dialect.name == "sqlite":
                cutoff_date = cutoff_date.replace(tzinfo=None)
            res = session.execute(
                text("UPDATE deals SET content_html = NULL WHERE indexed_at < :cutoff AND content_html IS NOT NULL"),
                {"cutoff": cutoff_date}
            )
            summary["slimmed_deals"] = res.rowcount or 0
            session.commit()
        except Exception as e:
            session.rollback()
            logger.warning(f"노후 본문 슬림화 중 경고: {e}")
        finally:
            session.close()

        if self.db_file_path and os.path.exists(self.db_file_path):
            try:
                backend_dir = os.path.dirname(self.db_file_path)
                backup_dir = os.path.join(backend_dir, "backups")
                os.makedirs(backup_dir, exist_ok=True)
                today_str = datetime.now().strftime("%Y%m%d")
                backup_file = os.path.join(backup_dir, f"insight_deal_{today_str}.db")

                src_conn = sqlite3.connect(self.db_file_path, timeout=30)
                try:
                    src_conn.execute("PRAGMA wal_checkpoint(PASSIVE)")
                    dst_conn = sqlite3.connect(backup_file)
                    try:
                        src_conn.backup(dst_conn, pages=200, sleep=0.05)
                    finally:
                        dst_conn.close()
                finally:
                    src_conn.close()

                summary["backup_path"] = backup_file

                # 오래된 백업 정리 (최근 max_backups개만 유지)
                backups = sorted(
                    [
                        os.path.join(backup_dir, f)
                        for f in os.listdir(backup_dir)
                        if f.startswith("insight_deal_") and f.endswith(".db")
                    ]
                )
                while len(backups) > max_backups:
                    old_file = backups.pop(0)
                    try:
                        os.remove(old_file)
                    except OSError:
                        pass

                logger.info(f"DB 안전 백업 및 최적화 완료: {backup_file} (슬림화: {summary['slimmed_deals']}건)")
            except Exception as e:
                logger.warning(f"SQLite 온라인 백업 중 경고: {e}")

        # 4) 이미지 캐시 자동 청소 및 LRU 용량 관리
        if clean_image_cache:
            try:
                from backend.services.cache_purger import purge_image_cache_lru
                backend_dir = os.path.dirname(self.db_file_path) if self.db_file_path else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
                cache_dir = os.path.join(backend_dir, "image_cache")
                summary["cache_summary"] = purge_image_cache_lru(
                    cache_dir=cache_dir,
                    max_age_days=cache_max_age_days,
                    max_size_mb=cache_max_mb
                )
            except Exception as e:
                logger.warning(f"이미지 캐시 자동 유지보수 연동 중 경고: {e}")

        return summary
    
    def init_database(self):
        """데이터베이스 초기화 (테이블 및 복합 인덱스 생성)"""
        try:
            logger.info("데이터베이스 테이블 생성 시작...")
            
            from backend.database.models import Base as CommunityBase, Community
            from backend.models.wishlist_models import Base as WishlistBase
            
            CommunityBase.metadata.create_all(self.engine)
            WishlistBase.metadata.create_all(self.engine)
            self.ensure_schema_migrations()
            self.ensure_performance_indexes()
            
            session = self.get_session()
            try:
                communities = [
                    Community(name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr"),
                    Community(name="ppomppu_overseas", display_name="뽐뿌해외", base_url="https://www.ppomppu.co.kr"),
                    Community(name="ruliweb", display_name="루리웹", base_url="https://bbs.ruliweb.com"),
                    Community(name="clien", display_name="클리앙", base_url="https://www.clien.net"),
                    Community(name="ali_ppomppu", display_name="알리뽐뿌", base_url="https://www.ppomppu.co.kr"),
                    Community(name="quasarzone", display_name="퀘이사존", base_url="https://quasarzone.com"),
                    Community(name="fmkorea", display_name="펨코", base_url="https://www.fmkorea.com"),
                    Community(name="bbasak_domestic", display_name="빠삭국내", base_url="https://bbasak.com"),
                    Community(name="bbasak_overseas", display_name="빠삭해외", base_url="https://bbasak.com"),
                    Community(name="bbasak_parenting", display_name="빠삭육아", base_url="https://bbasak.com")
                ]
                for community in communities:
                    existing = session.query(Community).filter(
                        Community.name == community.name
                    ).first()
                    if not existing:
                        session.add(community)
                session.commit()
            except Exception as e:
                logger.warning(f"커뮤니티 데이터 초기화 경고: {e}")
                session.rollback()
            finally:
                session.close()
            
            logger.info("데이터베이스 초기화 완료")
            return True
        except Exception as e:
            logger.error(f"데이터베이스 초기화 실패: {e}", exc_info=True)
            return False
    
    def test_connection(self) -> bool:
        """데이터베이스 연결 테스트"""
        try:
            with self.engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            return True
        except Exception as e:
            logger.error(f"데이터베이스 연결 테스트 실패: {e}")
            return False

# 싱글턴 인스턴스
db_manager = DatabaseManager()

def get_db_session() -> Generator[Session, None, None]:
    session = db_manager.get_session()
    try:
        yield session
    except Exception as e:
        logger.error(f"API 세션 오류: {e}")
        session.rollback()
        raise
    finally:
        session.close()

def create_db_session() -> Session:
    return db_manager.get_session()

SessionLocal = db_manager.SessionLocal
engine = db_manager.engine
get_db = get_db_session

if __name__ == "__main__":
    if db_manager.test_connection():
        db_manager.init_database()
