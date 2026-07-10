# Project: InsightDeal 검증 및 보강

## Architecture
- **Backend**: FastAPI (Python 3.x), Uvicorn 구동 포트 8080
- **Database**: SQLite (`insight_deal.db`), 핫딜 수집 테이블 (`deals`) 및 중복 방지 제약
- **Frontend-Web**: Next.js 16 (Turbopack 회피 설정, 8080 포트 연동)
- **App (Android)**: Kotlin/Compose, 8080 포트 API 호출 설정
- **Notification**: 파이썬 백엔드/안드로이드 FCM 연동 및 키워드 알림 전송 파이프라인

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: 8080 포트 정합성 검증 | `next.config.ts`, `app/build.gradle`, `NetworkConfig.kt`, `NetworkModule.kt` 8080 포트 설정 전수 확인 및 API 서버 8080 검증 | none | DONE |
| 2 | M2: 알림 가드 및 예외 처리 | `NotificationService.process_new_deal` 예외 방어막 보강 및 야간 DND/푸시비동의 홀딩 조건 정합성 검토 | none | DONE |
| 3 | M3: 스크래퍼 통합 테스트 | 뽐뿌, 펨코, 루리웹, 퀘이사존, 클리앙 스크래핑 스크립트 순차 구동 및 DB 적재/중복 방지 검증 | none | DONE |

## Code Layout
- `backend/main.py` - FastAPI 진입점
- `backend/scrapers/` - 사이트별 크롤링 스크립트
- `backend/notification/` 또는 `backend/services/` - 키워드 알림 비교 및 푸시 전송 로직
- `frontend-web/next.config.ts` - 웹 리라이트 타겟 설정
- `app/build.gradle` - 안드로이드 Gradle 빌드 구성
- `app/src/.../NetworkConfig.kt` - 안드로이드 네트워크 설정
- `app/src/.../NetworkModule.kt` - 안드로이드 DI 및 API 모듈

## Interface Contracts
### 1. API Server ↔ Frontend/App
- `GET /api/community`: 8080 포트에서 핫딜 목록 JSON 반환 (HTTP 200)
- API Base URL: `http://localhost:8080` (웹 및 안드로이드 에뮬레이터 로컬 연동)

### 2. Notification Pipeline
- `NotificationService.process_new_deal`: 핫딜 수집 주기 중 예외가 발생해도 수집 루프가 차단되지 않도록 복구 메커니즘을 포함해야 함.
- DND 조건: 21:00 ~ 08:00 (또는 법적 컴플라이언스 기준) 푸시 홀딩 혹은 방해금지 필터링.
