# Project: InsightDeal Cloud Autonomous Scraper Pipeline & Backfill

## Architecture
- **100% Server/Cloud autonomous crawling infrastructure** (GitHub Actions Ubuntu Runner + Vercel Seoul Relay `icn1` + GCP Cloud DB `34.59.208.14`).
- **Complete Windows PC Independence**: User's Windows PC is completely detached and powered off without affecting the 24/7 ingestion.
- **Fast-Failover Routing Matrix**:
  - 빠삭 3대 게시판 (국내, 해외, 육아): Vercel 서울 리전(`icn1`) 릴레이 100% 직행 (0.8초 200 OK).
  - 뽐뿌 3대 게시판 (뽐뿌, 뽐뿌해외, 알리뽐뿌): Vercel 서울 릴레이 경유 RSS 1-RTT 일괄 수집 (0.3초 30건).
  - 퀘이사존: Playwright Chromium Stealth 모드 (`--disable-blink-features=AutomationControlled` 및 `navigator.webdriver` 제거)로 Cloudflare 403 관통 (200 OK).
  - 펨코: Playwright Stealth 모드로 최신 Wasm(`mc.php`) 자동 실행 및 쿠키(`lite_year`) 획득, 100KB+ 정상 목록 수집.
  - 루리웹 & 클리앙: `curl_cffi`(`chrome124`) 직접 수집 (실패 시 서울 릴레이 폴백).
- **동시성 최적화**: 커뮤니티 간 `asyncio.gather(semaphore=4)` 및 상세 페칭 병렬화로 전체 실행 시간 1~2분 수렴.
- **배치 수집 및 전송**: `POST https://34.59.208.14.sslip.io/api/community/ingest-batch`.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Pipeline Timeout & Cancellation Fix | 빠삭 75.5초 지문 드롭 제거, 클라우드 환경 감지 시 5초 타임아웃 및 서울 릴레이 즉시 직행 | M1 | Survey |
| 2 | Ppomppu RSS 1-RTT Fast-Ingest | 뽐뿌 14페이지 순차 크롤링 대신 Vercel 서울 릴레이 RSS 1-RTT로 30건 0.5초 수집 | M1 | Survey |
| 3 | Concurrency & Async Gathering | 커뮤니티 간 `asyncio.gather(semaphore=4)` 병렬 수집 및 상세 페칭 병렬화 | M1 | Survey |
| 4 | GitHub Actions Workflow Optimization | 러너 타임아웃 및 단계 최적화로 워크플로우 1~2분 내 완결 보장 | M1 | Survey |
| 5 | Quasarzone Cloudflare 403 Bypass | Playwright Stealth 모드로 Cloudflare 403 완벽 관통 및 200 OK 수집 | M2 | Survey |
| 6 | Femco HTTP 430 Bypass | Playwright Stealth 모드로 최신 Wasm 자동 실행 및 쿠키 획득/캐싱 | M2 | Survey |
| 7 | Bbasak 3 Boards Vercel Relay | 빠삭 국내/해외/육아 전 게시판 서울 릴레이 고속 라우팅 완결 | M2 | Survey |
| 8 | Windows Task Scheduler Detachment | 로컬 `\InsightDeal_CloudFeeder` 안전 중지 및 비활성화, PC 종료 완벽 대비 | M2 | Survey |
| 9 | Missing Deals Backfill (00:05 KST ~ Now) | 퀘이사존 12건 포함 누락 22건 전수 선별 및 클라우드 DB 원스톱 백필 | M3 | Survey |
| 10 | 3-Way Consistency Verification | Cloud DB vs Live Web vs 실제 사이트 0 불일치 정합성 무결성 검증 | M3 | Survey |
| 11 | Multi-Branch Git Push & Clean | `origin/main` 및 `origin/master` 동시 푸시 및 임시 파일 완전 청소 | M3 | Survey |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Cloud Pipeline Fast-Failover & Concurrency | `base_scraper.py`, `cloud_feeder.py`, `cloud-feeder.yml` | none | DONE |
| 2 | Server-Side Community Bypass & PC Detachment | `base_scraper.py`, `scrapers/*.py`, Windows scheduler | M1 | DONE |
| 3 | Backfill, 3-Way Consistency & Multi-Branch Push | Backfill script, Cloud DB, `origin/main`, `origin/master` | M2 | DONE |

## Interface Contracts
### BaseScraper ↔ Community Scrapers
- `fetch_html(url)`: `IS_CLOUD_ENV` 및 `bbasak.com` 감지 시 즉시 `_fetch_via_seoul_relay(url)` 실행.
- HTTP 403 / 430 감지 시 즉시 서울 릴레이 또는 Playwright stealth 폴백.
- 반환값: HTML 문자열 또는 None.
### Cloud Feeder ↔ Ingestion API
- 엔드포인트: `POST https://34.59.208.14.sslip.io/api/community/ingest-batch`
- 페이로드: `{"deals": [DealDict, ...]}`
- 반환: `{"status": "ok", "inserted": int, "duplicates": int}`

## Code Layout
- `backend/scrapers/base_scraper.py`: 핵심 HTTP 세션, 지문 로테이션, 서울 릴레이 폴백, Playwright stealth 브릿지
- `backend/scrapers/`: 9개 커뮤니티별 스크래퍼 (fmkorea, ppomppu, quasarzone, bbasak, ruliweb, clien 등)
- `backend/scheduler/cloud_feeder.py`: 원샷 및 주기적 클라우드 피더 실행기
- `.github/workflows/cloud-feeder.yml`: GitHub Actions 10분 주기 클라우드 워크플로우
- `frontend-web/src/app/api/scraper-relay/route.ts`: Vercel 서울 리전 (`icn1`) 릴레이 엔드포인트
