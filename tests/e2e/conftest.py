import os
import sys
import pytest
from datetime import datetime, timezone
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from fastapi.testclient import TestClient

# 프로젝트 루트를 sys.path에 추가
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from backend.database.models import Base, Deal, PriceHistory, Community
from backend.database.session import get_db
from backend.main import app


@pytest.fixture(scope="session")
def test_engine():
    """테스트 세션 전체에서 공유할 격리 SQLite 엔진 (In-Memory)"""
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        echo=False
    )
    Base.metadata.create_all(bind=engine)
    return engine


@pytest.fixture(scope="function")
def db_session(test_engine):
    """각 테스트 케이스마다 독립적으로 롤백/클린업되는 트랜잭션 격리 세션"""
    connection = test_engine.connect()
    transaction = connection.begin()
    SessionLocal = sessionmaker(bind=connection, autocommit=False, autoflush=False, expire_on_commit=False)
    session = SessionLocal()

    # 10대 기본 커뮤니티 시드 주입 (존재하지 않을 경우에만)
    default_communities = [
        Community(id=1, name="ppomppu", display_name="뽐뿌", base_url="https://www.ppomppu.co.kr"),
        Community(id=2, name="ppomppu_overseas", display_name="뽐뿌해외", base_url="https://www.ppomppu.co.kr"),
        Community(id=3, name="ruliweb", display_name="루리웹", base_url="https://bbs.ruliweb.com"),
        Community(id=4, name="clien", display_name="클리앙", base_url="https://www.clien.net"),
        Community(id=5, name="ali_ppomppu", display_name="알리뽐뿌", base_url="https://www.ppomppu.co.kr"),
        Community(id=6, name="quasarzone", display_name="퀘이사존", base_url="https://quasarzone.com"),
        Community(id=7, name="fmkorea", display_name="펨코", base_url="https://www.fmkorea.com"),
        Community(id=8, name="bbasak_domestic", display_name="빠삭국내", base_url="https://bbasak.com"),
        Community(id=9, name="bbasak_overseas", display_name="빠삭해외", base_url="https://bbasak.com"),
        Community(id=10, name="bbasak_parenting", display_name="빠삭육아", base_url="https://bbasak.com"),
    ]
    for comm in default_communities:
        existing = session.query(Community).filter(Community.id == comm.id).first()
        if not existing:
            session.add(comm)
    session.flush()

    try:
        yield session
    finally:
        session.close()
        transaction.rollback()
        connection.close()


@pytest.fixture(scope="function")
def client(db_session):
    """테스트용 DB 세션이 주입된 FastAPI TestClient 픽스처"""
    def override_get_db():
        try:
            yield db_session
        finally:
            pass

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


@pytest.fixture
def deal_factory(db_session):
    """다양한 조건의 Deal 인스턴스를 즉시 생성하는 테스트 데이터 팩토리"""
    def _create_deal(
        title: str = "테스트 핫딜 상품",
        price: str = "15000",
        currency: str = "KRW",
        url: str = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=99999",
        ecommerce_link: str = "https://www.coupang.com/vp/products/12345",
        source_community_id: int = 1,
        content_html: str = "<p>테스트 상세 본문입니다.</p>",
        options_data: str = None,
        is_closed: bool = False,
        shop_name: str = "쿠팡",
        category: str = "기타",
        like_count: int = 10,
        view_count: int = 100,
        comment_count: int = 5,
        **kwargs
    ) -> Deal:
        deal = Deal(
            title=title,
            price=str(price),
            currency=currency,
            post_link=url,
            ecommerce_link=ecommerce_link,
            source_community_id=source_community_id,
            content_html=content_html,
            options_data=options_data,
            has_options=bool(options_data),
            is_closed=is_closed,
            shop_name=shop_name,
            category=category,
            like_count=like_count,
            view_count=view_count,
            comment_count=comment_count,
            indexed_at=datetime.now(timezone.utc),
            **kwargs
        )
        db_session.add(deal)
        db_session.commit()
        db_session.refresh(deal)
        return deal

    return _create_deal


def clean_option_title_spec(title: str) -> str:
    """
    M4 및 PROJECT.md § Option Title Prefix Cleaning 요구사항에 따른 공통 정규화 헬퍼.
    '알리검색 - ', '네이버검색 - ', '지마켓검색 - ' 등 다중 중첩 접두사를 제거합니다.
    """
    import re
    if not title:
        return ""
    # 중첩된 검색 접두사 재귀/반복 제거
    cleaned = re.sub(
        r'^(?:\s*(?:알리|네이버|지마켓|옥션|쿠팡|11번가|티몬|위메프)?\s*검색\s*[-:–—·]\s*)+',
        '',
        title.strip(),
        flags=re.IGNORECASE
    )
    return cleaned.strip()
