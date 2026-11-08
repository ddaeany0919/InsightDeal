# 🧪 InsightDeal E2E Test Infrastructure Specification

## 1. 개요 및 테스트 철학 (Philosophy)

본 테스트 인프라는 **InsightDeal** 서비스의 5대 핵심 현안 및 잠재 결함을 전수 검증하기 위해 구축된 **요구사항 기반 Opaque-Box(Black-Box) 종합 E2E 테스트 스위트**입니다.

### 1.1 Opaque-Box 테스트 철학
- **내부 구현 종속 탈피**: 특정 클래스나 내부 변수의 구현 방식에 결합되지 않고, `PROJECT.md` 및 `ORIGINAL_REQUEST.md`에 명시된 비즈니스 요구사항과 인터페이스 계약(입출력 스키마)을 기준으로 검증합니다.
- **예상 출력의 엄격한 유도 (Expected Output Derivation)**: 모든 테스트 케이스는 비즈니스 규칙 및 사양서로부터 도출된 결정론적 기대값을 기준으로 단언(Assert)합니다.
- **테스트 무결성 (Integrity First)**: 절대 무조건 통과하는 가짜 테스트(Facade Test)를 작성하지 않으며, 실제 로직 파이프라인(수집 릴레이 -> 파싱/정규화 -> DB Upsert -> API 응답)을 온전히 통과시킵니다.
- **독립성 및 격리성 (Isolation & Cleanliness)**: 모든 테스트는 테스트 전용 격리 SQLite DB(in-memory 또는 임시 파일)와 목(Mock)/통합 픽스처를 사용하여, 테스트 순서에 영향을 받지 않고 실운영 DB(`insight_deal.db`)에 영구적 부작용을 남기지 않습니다.

---

## 2. 4-Tier 테스트 아키텍처

```
tests/e2e/
├── conftest.py                # 격리 DB, TestClient, Mock 픽스처, 팩토리 함수
├── test_tier1_functional.py   # Tier 1: 기능 커버리지 (현안 1~5 및 REST API)
├── test_tier2_boundary.py     # Tier 2: 경계값 및 코너 케이스 (가격 오인 방어, 재활용 역오염 방어 등)
├── test_tier3_pairwise.py     # Tier 3: 복합 인터랙션 및 페어와이즈 (5개 차원 2-way 조합 검증)
└── test_tier4_workload.py     # Tier 4: 실환경 워크로드 파이프라인 (수집->정규화->Upsert->API 서빙)
```

### Tier 1: 기능 커버리지 (Functional Coverage)
5대 핵심 현안 및 백엔드 REST API의 핵심 계약 준수 여부를 검증합니다.
- **현안 1 (클라우드 피더 상세 본문 수집)**: `is_ppomppu_cloud` 스킵 제약 해제 검증 및 Vercel 서울 릴레이(`/api/scraper-relay`)를 통한 100자 요약문 절단 방지 검증.
- **현안 2 (슬래시 옵션 파서 가격 오인 방어)**: 통화 기호(`₩`, `$`, `€`, `원`, `만`, `달러`) 필수 결합 정규식 및 파싱 무결성.
- **현안 3 (재활용/끌올 게시글 Full Force Sync)**: 작성자가 기존 글 번호의 상품을 변경(`is_recycled_post`)했을 때 제목, 본문, 링크, 이미지, `options_data`가 최신 상태로 강제 갱신되는지 검증.
- **현안 4 (옵션명 접두사 정제 & 할인코드 연동)**: `'알리검색 - '`, `'네이버검색 - '` 접두사 제거 및 할인 프로모션 코드 인터페이스 보존.
- **현안 5 (10대 커뮤니티 수집 회복탄력성)**: 클리앙 서울 릴레이 직행, 뽐뿌해외/알리뽐뿌 분리 등 전 10개 커뮤니티 스크래퍼 회복탄력성 검증.
- **REST API 엔드포인트**: `/api/community/deals`, `/api/community/deals/{id}`, `/api/community/deals/search`, `/api/health` 등 핵심 엔드포인트의 200 OK 및 계약 준수 검증.

### Tier 2: 경계값 및 코너 케이스 (Boundary & Corner Cases)
실환경에서 시스템을 오작동시킬 수 있는 극한의 엣지 케이스를 방어합니다.
- **통화 기호 없는 숫자 가격 오인 차단**: `12.7`(인치), `2025`(연도), `65`(인치), `144`(Hz), `32`(GB) 등 모델명/규격 숫자가 가격(KRW/USD)으로 둔갑하지 않도록 차단.
- **재활용 글 옵션 역오염 차단**: 이전 게시글에 5개 옵션이 있고 신규 글에 2개 옵션이 있을 때, 이전 옵션이 누출되거나 덮어쓰기 누락 없이 깨끗하게 신규 2개로만 대체되는지 검증.
- **텍스트 길이 극단값**: 1,000자 초과 초장문 제목, 수만 자의 거대 HTML 본문, 0자 빈 문자열 안전 처리.
- **특수문자 및 태그 인젝션 탈출**: `<script>`, `<BALPUM01>` 같은 꺾쇠 괄호 할인코드가 HTML 파서에 의해 삭제되지 않고 정상 보존되는지 검증, `\xa0` 비구분 공백 정규화.
- **복합 할인/조건문 경계**: 음수 할인액(`-$10`, `-₩37,229`), 구매 조건문(`$150 이상 구매 시 -$22`), 화살표 정가 표기(`15만 -> 12만`)에서 실제 최종 체감가만 정확히 추출하는지 검증.

### Tier 3: 복합 인터랙션 및 페어와이즈 (Combinatorial & Pairwise)
단일 기능에서는 드러나지 않는 다차원 조합 상호작용 결함을 페어와이즈(Pairwise) 기법으로 전수 커버합니다.
- **인자 1 (커뮤니티 소스)**: 뽐뿌, 퀘이사존, 펨코, 클리앙, 알리뽐뿌
- **인자 2 (통화 및 단위)**: 원화(원), 만원(만), 달러($), 유로(€)
- **인자 3 (옵션 구조)**: 단일 상품, 괄호 안 슬래시, 괄호 밖 슬래시(Pattern C), 본문 번호형 블록
- **인자 4 (배송비 조건)**: 무료배송, 유료(3,000원), 조건부 무료(5만원 이상 무배)
- **인자 5 (프로모션 및 접두사)**: `알리검색 - ` 접두사, 인라인 쿠폰코드, 단독 할인코드 줄, 일반 상품명
- 모든 2-Way 상호작용 조합에 대해 스크래퍼 -> 어그리게이터 -> 옵션 정제 -> API 서빙의 데이터 보존 무결성을 검증합니다.

### Tier 4: 실환경 워크로드 시나리오 (End-to-End Workload Pipelines)
실제 운영 환경과 동일한 생명주기(Lifecycle) 파이프라인 전체를 모의합니다.
- **시나리오 1 (100자 절단 복구 파이프라인)**: RSS 요약본(100자)으로 먼저 수집된 뽐뿌 딜이 서울 릴레이를 거쳐 500자 이상의 풍부한 본문 및 옵션으로 승격되는 복구 파이프라인 완결성.
- **시나리오 2 (가격 변동 및 재활용 라이프사이클)**: 기존 딜 수집 -> 시세 변동 기록(`PriceHistory` 누적) -> 작성자의 딜 상품 완전 교체(재활용) -> 제목/본문/옵션 Full Force Sync 및 시세 히스토리 정합성 유지.
- **시나리오 3 (다중 옵션 핫딜 E2E 소비 파이프라인)**: 복합 모음전 수집 -> 옵션 파서 정제 -> DB 저장 -> 웹/앱 공통 API(`/api/community/deals/{id}`)를 통한 정제된 옵션 및 프로모션 코드 제공 검증.

---

## 3. 테스트 환경 설정 및 격리 전략

1. **격리 DB (In-Memory / Temp SQLite)**:
   - 프로덕션 DB(`backend/insight_deal.db`)를 절대 건드리지 않도록, 테스트 실행 시 `sqlite:///:memory:` 또는 임시 SQLite 파일을 생성하여 스키마를 초기화합니다.
2. **FastAPI TestClient 연동**:
   - `from fastapi.testclient import TestClient`를 통해 백엔드 라우터를 실제 HTTP 요청과 동일하게 테스트합니다.
   - DB 의존성(`get_db`)을 테스트용 세션으로 오버라이드하여 데이터 격리를 완성합니다.
3. **네트워크 격리 및 서울 릴레이 Mocking**:
   - 외부 커뮤니티(뽐뿌, 클리앙 등)와 Vercel 릴레이에 대한 네트워크 호출은 `unittest.mock` 및 가상 응답 팩토리를 통해 결정론적(Deterministic)으로 검증합니다.

---

## 4. 테스트 실행 방법 (Execution Guide)

### 4.1 전체 테스트 스위트 실행
```powershell
pytest tests/e2e/ -v --tb=short
```

### 4.2 티어별 개별 실행
```powershell
# Tier 1: 기능 커버리지 (현안 1~5 및 백엔드 API)
pytest tests/e2e/test_tier1_functional.py -v

# Tier 2: 경계값 및 코너 케이스 (12.7 가격 오인, 재활용 역오염 방어 등)
pytest tests/e2e/test_tier2_boundary.py -v

# Tier 3: 복합 인터랙션 및 페어와이즈 (다차원 조합 검증)
pytest tests/e2e/test_tier3_pairwise.py -v

# Tier 4: 실환경 워크로드 파이프라인 (수집->파싱->DB->API E2E)
pytest tests/e2e/test_tier4_workload.py -v
```

### 4.3 특정 테스트 또는 키워드 필터링
```powershell
# 가격 오인 방어 테스트만 실행
pytest tests/e2e/ -k "test_currency_guard or test_price_token" -v

# 재활용/끌올 동기화 테스트만 실행
pytest tests/e2e/ -k "recycled" -v
```
