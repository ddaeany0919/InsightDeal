# Project: InsightDeal Web & App 100% Parity Multi-Option & Realtime UX Overhaul

## Architecture
- **Unified Cross-Platform Core (Web & Android Parity)**:
  - Both Web (Next.js 16 + React 19) and Android (Jetpack Compose M3 + Coroutines/Flow MVVM) maintain 100% identical interaction patterns for multi-option deals.
  - 5-Element Dynamic Switching: (1) Card active selection (`selectedOptionIndex`), (2) Header price/currency ($ ↔ 원), (3) Option-specific price history chart, (4) Header image/option thumbnails, (5) Bottom purchase link & option-specific promo code.
- **Native Minimal Design Standard (Anti-AI Emojis)**:
  - No AI emojis (🔥, ✨, 🚀, 📦 등) or verbose prompts.
  - Clean Toss/Kream/M3 style: Minimal `[↻ 새 핫딜]` floating capsule with smooth scroll to top, and native circular spinner for Pull-to-Refresh.
- **Backend High-Precision Scraper & Chart APIs**:
  - Slash (`/`) multi-option parser with parenthesis masking in `aggregator_service.py`.
  - Option thumbnail auto-matching via shopping search CDN.
  - PriceHistory duplicate insert prevention & 10-min cooldown.
  - Option-specific `/deals/{deal_id}/history` API with 50+ generic category stopwords.
- **GCP Cloud Deployment & Autonomous DB Backfill**:
  - Automated deployment to GCP (`34.59.208.14`).
  - DB cleanup of 2,657 duplicate `price_history` rows and one-stop backfill for unparsed slash deals and missing thumbnails.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Slash Multi-Option Parser (Pattern C) | 괄호 없는 최상위 슬래시(`/`) 복수 상품 파싱 및 `options_data` 분리 | M1 | explorer_backend_1 |
| 2 | Option Thumbnails Auto-Matching | 옵션별 `image_url` 누락 시 네이버/다음 쇼핑 CDN 기반 썸네일 자동 페칭 및 1번 옵션 대표이미지 백필 | M1 | explorer_backend_1 |
| 3 | PriceHistory Duplicate Insertion Fix | 재수집 시 동일 가격 중복 인서트 차단 및 10분 쿨다운 가드 적용 | M1 | explorer_backend_1 |
| 4 | Option-Specific History API & Stopwords | `/deals/{deal_id}/history`에 `option_name`/`currency` 지원 및 50+ 범용 명사 불용어 필터로 603건 노이즈 제거 | M1 | explorer_backend_1 |
| 5 | Web Multi-Option 5-Element Dynamic Parity | `DealDetailModal.tsx` & `PriceTrendChart.tsx` 옵션 선택 시 5대 요소 실시간 동적 전환 | M2 | explorer_web_1 |
| 6 | Web Realtime Refresh & Floating Capsule | `page.tsx` 모바일 터치 Pull-to-Refresh 및 45~60초 주기 `[↻ 새 핫딜]` 플로팅 캡슐 배너 (AI 이모지 전면 배제) | M2 | explorer_web_1 |
| 7 | Android Multi-Option 5-Element Dynamic Parity | `DealDetailScreen.kt`, `DealDetailViewModel.kt`, `DealItem.kt`, `ApiService.kt` 5대 요소 동적 전환 (M3 규격) | M3 | explorer_android_1 |
| 8 | Android Realtime Refresh & Floating Capsule | `HomeScreen.kt` & `HomeViewModel.kt` 불꽃 오버레이 제거, M3 `PullToRefreshContainer` 복원 및 `[↻ 새 핫딜]` 플로팅 캡슐 | M3 | explorer_android_1 |
| 9 | Dual-Platform Build & Integrity Gate | `npm --prefix frontend-web run build` 및 `./gradlew compileDebugKotlin` 100% 통과 및 코드 리뷰 | M4 | Review & Verification |
| 10 | GCP Deployment & SQLite DB Backfill & Clean | GCP 실서버 코드 배포, 서비스 재시작, 2,657건 중복 정리, 17170/17147 백필 및 로컬 임시파일 완전 청소 | M5 | Deployment & Cleanup |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Backend Parser, Thumbnails & PriceHistory Bugfix | `aggregator_service.py`, `cloud_feeder.py`, `community.py` | none | DONE |
| 2 | Web Frontend 100% Dynamic Parity & Realtime UX | `DealDetailModal.tsx`, `PriceTrendChart.tsx`, `page.tsx`, `types.ts` | M1 interface | DONE |
| 3 | Android App Jetpack Compose 100% Parity & Realtime UX | `DealDetailScreen.kt`, `DealDetailViewModel.kt`, `HomeScreen.kt`, `HomeViewModel.kt`, `DealItem.kt`, `ApiService.kt` | M1 interface | DONE |
| 4 | Dual-Platform Review & Verification Gate | Code Review, Turbopack Build, Kotlin Gradle Compile | M2, M3 | DONE |
| 5 | GCP Server Deploy, DB Backfill & Workspace Cleanup | Git push/pull, service restart, DB backfill script, local cleanup | M4 | IN_PROGRESS |



## Interface Contracts
### Web/App ↔ Backend History API
- 엔드포인트: `GET /api/community/deals/{deal_id}/history`
- 쿼리 파라미터:
  - `period`: `7d` (default), `30d`, `all`
  - `option_name` (or `query` in Android): 선택된 옵션 상품명 (예: `UWANT V600`, `뷰몬트 15.6 모니터`)
  - `currency`: 선택된 옵션의 통화 (`USD` vs `KRW`)
- 반환값:
  - `has_data`: boolean
  - `points`: `[{"date": "11.07 01:26", "price": 126.0, "source": "알리뽐뿌"}]`
  - `stats`: `{"min_price": ..., "avg_price": ..., "max_price": ..., "current_percentile": ...}`

### DealItem / DealSource Schema
- `DealSource` (Web: `OptionSource`, Android: `DealSource`):
  - `name`: string
  - `price`: number (cents for USD, won for KRW)
  - `currency`: string (`USD` / `KRW`)
  - `url`: string (ecommerce outlink)
  - `image_url`: string (optional option thumbnail CDN URL)
  - `code`: string (optional promo code for this option)

## Code Layout
- `backend/services/aggregator_service.py`: 슬래시 멀티옵션 파서, 옵션 썸네일 수집기, 중복 인서트 방어
- `backend/scheduler/cloud_feeder.py`: 슬래시 멀티 딜 감지 및 피더 연동
- `backend/routers/community.py`: 옵션 전용 시세 API 및 범용 불용어 필터
- `frontend-web/src/app/components/DealDetailModal.tsx`: 웹 상세 모달 5대 동적 전환
- `frontend-web/src/app/components/PriceTrendChart.tsx`: 웹 시세 차트 옵션 전용 격리
- `frontend-web/src/app/page.tsx`: 웹 Pull-to-Refresh & `[↻ 새 핫딜]` 플로팅 캡슐
- `app/src/main/java/com/ddaeany0919/insightdeal/presentation/DealDetailScreen.kt`: 앱 상세 화면 5대 동적 전환
- `app/src/main/java/com/ddaeany0919/insightdeal/presentation/DealDetailViewModel.kt`: 앱 상세 화면 옵션 상태/시세 관리
- `app/src/main/java/com/ddaeany0919/insightdeal/presentation/home/HomeScreen.kt`: 앱 M3 스피너 및 `[↻ 새 핫딜]` 플로팅 캡슐
- `app/src/main/java/com/ddaeany0919/insightdeal/presentation/home/HomeViewModel.kt`: 앱 50초 주기 새 딜 감지
- `app/src/main/java/com/ddaeany0919/insightdeal/data/model/DealItem.kt`: DealSource 필드 확장
- `app/src/main/java/com/ddaeany0919/insightdeal/data/network/ApiService.kt`: 히스토리 쿼리 파라미터 확장
