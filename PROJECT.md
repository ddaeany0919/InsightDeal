# Project: InsightDeal 5대 핵심 현안 및 잠재 결함 전수 해결, 웹/앱 100% 패리티 & GCP 배포/백필

## Architecture
- **수집 및 피더 파이프라인 (Scraper & Feeder Pipeline)**:
  - Vercel 서울 릴레이(`icn1`) 프록시(`/api/scraper-relay`)를 통한 클라우드 환경(GCP Cloud Run/Scheduler)에서의 뽐뿌/알리뽐뿌 상세 본문(`get_detail()`) 정상 수집 및 100자 요약문 절단 방지.
  - 클리앙(`clien.net`)의 Vercel 서울 릴레이 직행 추가(Cloudflare 403 및 5초 지연 차단) 및 10개 커뮤니티 스크래퍼 회복탄력성 확보.
- **파서 및 데이터 무결성 엔진 (Parser & Data Integrity Engine)**:
  - 슬래시(`/`) 다중 옵션 파서(Pattern C)에 통화 기호(`₩`, `$`, `€`, `원`, `만`, `엔`) 필수 결합 검증을 도입하여 모델명/인치/연도 순수 숫자(12.7, 2025, 65 등) 가격 오인 원천 차단.
  - 작성자의 게시글 재활용/끌올(`is_recycled_post`) 자동 감지 알고리즘 도입: 기존 글의 옵션/본문/링크/이미지/제목을 100% 최신 상태로 Full Force Sync.
- **웹/앱 100% 패리티 및 클린 UX (Frontend Parity & Clean UX)**:
  - 옵션명 접두사(`알리검색 - `, `네이버검색 - ` 등) 다중 중첩 제거를 위한 공통 정규식 헬퍼(`cleanOptionTitle`) 웹/앱 전면 적용.
  - 할인코드 원클릭 자동 복사 및 토스트/스낵바 안내 피드백 웹/앱 100% 패리티 구현.
  - 앱 `DealDetailScreen.kt` 내 잔존 외부 가격비교 컴포넌트(`MallPriceTable`) 전면 삭제 (상세 화면 3대 핵심 뼈대 구성 준수).
- **인프라 무결성 및 배포 (Infra & Production)**:
  - 포트 8080 및 Next.js 16 `turbopack: {}` 설정 유지, 안드로이드 구 Lightsail IP 잔재 청산 및 GCP IP(`34.59.208.14`) 정합.
  - 웹 알림 설정 페이지의 무효 VAPID 키 교체 및 런타임 크래시 방지.
  - GCP 실서버 배포, 서비스 재기동, 100자 절단 뽐뿌 딜 일괄 복구 백필 및 Obsidian Wiki 지식 컴파일 자동 동기화.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Cloud Feeder Detail Scraping Unblock | `cloud_feeder.py`의 `is_ppomppu_cloud` 스킵 제약 해제, Vercel 서울 릴레이를 통한 `get_detail()` 클라우드 정상 수집 | M1 | explorer_survey_1 |
| 2 | Ppomppu 100-Char Cut Deals Backfill Script | 100자 이하로 잘려 저장된 뽐뿌/알리뽐뿌 딜을 식별하여 Vercel 릴레이로 상세 본문/옵션/링크를 일괄 재수집·복구하는 백필 스크립트 구축 | M1 | explorer_survey_1 |
| 3 | Slash Option Parser Currency Guard | `aggregator_service.py` `parse_price_token`에서 통화 기호 없는 순수 숫자(12.7, 2025 등) 가격 오인 차단 및 Pattern C 통화 결합 필수화 | M2 | explorer_survey_2 |
| 4 | Recycled Post Full Force Sync | 동일 URL 재수집 시 글 재활용(`is_recycled_post`)을 감지하여 제목/본문/링크/이미지/`options_data`를 최신 데이터로 강제 갱신 | M2 | explorer_survey_2 |
| 5 | Community Scrapers Coverage & Resilience | `clien.net` Vercel 릴레이 직행 등록, 뽐뿌해외/알리뽐뿌 캐시 키 및 중복 URL 분리, 전 10개 커뮤니티 안정성 복원 | M3 | explorer_survey_1 |
| 6 | Option Title Prefix ('알리검색 - ') Cleaning | 웹(`utils.ts`, `DealDetailModal.tsx`) 및 앱(`DealDetailScreen.kt`)에서 다중 분할에도 안전한 정규식 기반 옵션명 정제 적용 | M4 | explorer_survey_3 |
| 7 | Promo Code Auto-Copy & Toast Feedback Parity | 웹 옵션 구매 버튼 토스트 연동 및 앱 구성상품/바텀시트 구매 버튼 클릭 시 클립보드 자동 복사 + 토스트 알림 완성 | M4 | explorer_survey_3 |
| 8 | Detail Screen 3-Pillar Compliance | 앱 `DealDetailScreen.kt` 내 외부 가격비교 컴포넌트(`MallPriceTable`) 전면 삭제 및 3대 핵심 뼈대 집중 렌더링 준수 | M4 | explorer_survey_3 |
| 9 | Web Notification Crash Fix & IP Cleanup | `frontend-web/.../notifications/page.tsx` 유효 VAPID 키 교체 및 앱 `NetworkConfig.kt`/`network_security_config.xml` 구 IP 청산 | M5 | explorer_survey_3 |
| 10 | Transaction Atomicity & Cache Concurrency | `PriceHistory` 트랜잭션 분리 방지 및 `_SEEN_DETAIL_CACHE` 원자적 파일 교체(Atomic Write)로 동시성 결함 방어 | M5 | explorer_survey_1,2 |
| 11 | Comprehensive E2E Test Suite | 4-Tier(기능, 경계값, 페어와이즈, 실워크로드) 및 Tier 5(적대적 커버리지) E2E 테스트 스위트 구축 및 `TEST_READY.md` 발행 | M6 | E2E Testing Track |
| 12 | Dual-Platform Build & GCP Deploy & Backfill | 웹 Next.js 빌드, 안드로이드 Kotlin 컴파일, GCP 실서버 배포, 백필 스크립트 실행 및 실서버 엔드포인트 검증 | M6 | Orchestrator & Workers |
| 13 | Obsidian Wiki Knowledge Auto-Sync | 아키텍처 결정 사항 및 트레이드오프를 `projects/insightdeal.md`에 반영하고 `obsidian-wiki memory sync` 자동 동기화 | M6 | Orchestrator |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Cloud Feeder & Detail Scraping Backfill (현안 1) | `backend/scheduler/cloud_feeder.py`, `backend/scripts/cloud_feeder.py`, `backend/scripts/backfill_ppomppu_100_deals.py` | none | DONE (worker: 34c44d03, 6/6 unit tests passed, live backfill dry-run verified) |
| M2 | Aggregator Parser Hardening & Recycled Post Sync (현안 2, 3) | `backend/services/aggregator_service.py`, `backend/db/` 관련 로직 | none | DONE (worker: ab2d4c9b, 14/14 unit tests passed) |
| M3 | Community Scrapers Coverage & Resilience (현안 5) | `backend/scrapers/base_scraper.py`, `backend/scrapers/ppomppu_overseas_scraper.py`, `backend/scrapers/bbasak_overseas_scraper.py`, `backend/scheduler/cloud_feeder.py` | none | DONE (worker: 97157b67, 12/12 unit tests passed) |
| M4 | UI/UX Parity & Promo Code Auto-Copy (현안 4) | `frontend-web/src/app/lib/utils.ts`, `frontend-web/src/app/components/DealDetailModal.tsx`, `app/.../DealDetailScreen.kt` | none | DONE (worker: 00f72ba1, web build & kotlin compile passed) |
| M5 | Codebase & Infra Latent Defects Hardening (현안 6) | `frontend-web/src/app/notifications/page.tsx`, `app/.../NetworkConfig.kt`, `app/src/main/res/xml/network_security_config.xml`, `backend/services/aggregator_service.py` | none | DONE (worker: 88286c4d) |
| M6 | E2E Test Suite Pass, GCP Deployment, Backfill & Wiki Sync | Dual-Platform E2E 검증, GCP 실서버 배포, 실서버 백필 실행, Obsidian Wiki 동기화 | M1, M2, M3, M4, M5 | DONE (worker: 14f31b68, 48/48 tests passed, deploy & backfill verified, wiki synced) |

## Interface Contracts
### Web/App ↔ Backend Deal Detail & Options Schema
- `options_data` (JSON List):
  - `name`: string (정제된 상품명, 접두사 미포함)
  - `price`: number (KRW: 원, USD: 센트)
  - `currency`: string (`KRW` | `USD`)
  - `url`: string (구매처 링크)
  - `image_url`: string (옵션 썸네일)
  - `code`: string (할인 프로모션 코드)
- `content_html` / `contentHtml`: 원본 상세 본문 (최소 100자 이상 정상 본문 보장)
- `url`: 원본 커뮤니티 게시글 바로가기 링크
- `ecommerce_link` / `ecommerceLink`: 구매처 다이렉트 아웃링크

### Scraper Relay Contract
- 엔드포인트: `https://insightdeal.vercel.app/api/scraper-relay` (서울 `icn1` 리전)
- 쿼리 파라미터: `target_url`, `board_type`
- 바이패스 도메인: `ppomppu.co.kr`, `clien.net`, `ruliweb.com`

## Code Layout
- `backend/scheduler/cloud_feeder.py`: 클라우드 피더 본문 수집 연동 및 스케줄러
- `backend/scripts/cloud_feeder.py`: 단독 실행 피더 스크립트 동기화
- `backend/scripts/backfill_ppomppu_100_deals.py`: 100자 절단 딜 복구 백필 스크립트
- `backend/services/aggregator_service.py`: 슬래시 옵션 파서(Pattern C), 끌올 감지 및 Full Force Sync
- `backend/scrapers/base_scraper.py`: Vercel 서울 릴레이 직행 도메인 관리
- `backend/scrapers/ppomppu_overseas_scraper.py`: 뽐뿌해외/알리뽐뿌 분리
- `frontend-web/src/app/lib/utils.ts`: 옵션명 정규식 정제(`cleanOptionTitle`)
- `frontend-web/src/app/components/DealDetailModal.tsx`: 웹 상세 모달 옵션 구매 토스트 연동
- `frontend-web/src/app/notifications/page.tsx`: 웹 푸시 VAPID 키 교체 및 크래시 방어
- `app/src/main/java/com/ddaeany0919/insightdeal/presentation/DealDetailScreen.kt`: 앱 상세 화면 옵션명 정제, 할인코드 원클릭 자동 복사, 가격비교 컴포넌트 제거
- `app/src/main/java/com/ddaeany0919/insightdeal/data/network/NetworkConfig.kt`: IP 및 포트 8080 일관성
