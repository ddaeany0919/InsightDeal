# [PLAN] 백엔드 중복 폴더 통합 및 레거시 코드 아카이브

## 1. 개요
현재 `InsightDeal` 프로젝트 내부에 `backend`와 `InsightDeal_Backend`라는 두 개의 중복된 백엔드 폴더가 존재하며, 메인 엔진인 `backend/` 루트에도 다수의 임시 테스트/디버그 스크립트와 데이터 파일이 흩어져 있어 관리 효율성이 떨어집니다. 이를 정리하여 아키텍처를 명확히 하고 유지보수성을 높입니다.

## 2. 목표
- 중복된 `InsightDeal_Backend` 폴더를 아카이브로 이동하여 혼선 방지
- `backend/` 폴더 내부의 엔트리포인트(`main.py`, `Dockerfile` 등)와 설정 파일(`requirements.txt`)을 제외한 임시 파일들을 용도별로 분류 및 이동
- 프로젝트 루트의 레거시 문서들을 `docs/` 폴더 내로 정리

## 3. 상세 계획

### 3.1. 디렉토리 구조 생성
- `archive/`: 프로젝트 루트에 레거시 코드 및 문서 보관용 폴더 생성
- `backend/tests/`: 유닛 및 통합 테스트 코드 보관용
- `backend/scripts/`: 일회성 디버깅, 데이터 추출, 실행 스크립트 보관용
- `backend/data/fixtures/`: 스크래퍼 테스트를 위한 HTML 소스 등 샘플 데이터 보관용
- `docs/archive/`: 완료된 계획 및 기타 문서 아카이브용

### 3.2. 파일 이동 및 정리

#### A. `InsightDeal_Backend` 정리
- `InsightDeal_Backend/` → `archive/InsightDeal_Backend/` 로 이동

#### B. `backend/` 루트 파일 분류
- **테스트 및 검증**: `test_*.py`, `verify_*.py` → `backend/tests/`
- **스크립트 및 유틸리티**: `debug_*.py`, `check_*.py`, `analyze_*.py`, `run_*.py`, `extract_*.py`, `register_*.py`, `save_*.py` → `backend/scripts/`
- **샘플 데이터**: `*.html` (스크래핑 소스) → `backend/data/fixtures/`

#### C. 프로젝트 루트 레거시 문서 정리
- `RESUME_FINAL.md`, `RESUME_FINAL_V2.md`, `DAY5-7_PLAN.md`, `MIGRATION_CHECKLIST.md` → `docs/archive/`
- `build_log.txt` → `logs/` (또는 삭제)

## 4. 기대 효과
- **프로젝트 명확성**: 실제 작동하는 코드와 참고용 레거시 코드가 엄격히 분리됨
- **개발 환경 개선**: `backend/` 폴더가 깔끔해져 주요 로직 파악이 용이해짐
- **테스트 체계화**: `tests/` 폴더 도입을 통해 향후 QA 및 CI/CD 연동 기반 마련

## 5. 리스크 및 주의사항
- 스크립트 이동 후 상대 경로(`import`, `open()`) 수정 필요 여부 확인
- `docker-compose.yml` 및 `Dockerfile`의 경로 설정에 영향이 없는지 최종 점검 (현재 `backend/` 루트에서 실행되므로 큰 영향은 없을 것으로 판단됨)
