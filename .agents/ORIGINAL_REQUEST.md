# Original User Request

## Initial Request — 2026-07-09T20:03:18+09:00

# Teamwork Project Prompt — Draft

웹 프론트엔드(Next.js) 대시보드 내에 모바일 앱 수준의 개인화 설정 기능(북마크, 알림 키워드 및 주기 설정)과 핫딜 카드 우측 상세 보기 패널 내 4대 쇼핑몰 실시간 최저가 가격 비교 컴포넌트를 병렬로 완벽 이식합니다.

Working directory: C:\Users\kth00\StudioProjects\InsightDeal\frontend-web
Integrity mode: development

## Requirements

### R1. 웹 개인화 대시보드 확장 (북마크/키워드/알람 설정)
- Next.js 내에 북마크 리스트 조회, 관심 알림 키워드 등록/삭제, 알람 수신 주기(DND 및 스케줄러 간격) 설정을 조작할 수 있는 설정 페이지 혹은 서브 패널 UI를 신설합니다.
- 백엔드 인증 엔드포인트(`/api/auth` 및 `/api/wishlist`)와 연동하여 사용자의 개인 설정 데이터가 DB 및 UI에 양방향 실시간 보존 및 반영되도록 설계합니다. JWT 토큰 보관 및 세션 상태 관리는 로컬 환경에 적합한 방식(LocalStorage 또는 Cookie)을 에이전트가 자율 결정합니다.

### R2. 실시간 4대 쇼핑몰 최저가 비교 컴포넌트 추가
- 우측 핫딜 상세 보기 패널 내에 가격 추이 그래프와 더불어, 4대 대표 쇼핑몰(쿠팡, G마켓, 11번가 등)의 실시간 최저가 매칭 및 비교 상태를 렌더링하는 `PriceComparison` 섹션을 구현합니다.
- 백엔드 가격 비교 API(`/api/product`)와 유기적으로 통신하며, 정보가 없거나 지연될 시 뼈대 렌더링(Skeleton) 또는 안전한 예외 폴백 레이아웃을 제공합니다. 스타일링은 기존 globals.css와 CSS Modules(Vanilla CSS) 구조를 철저히 고수합니다.

## Acceptance Criteria

### UI/UX 정합성 (UI Consistency)
- [ ] 신설된 개인화 UI 및 최저가 비교 컴포넌트는 기존 대시보드 테마(`globals.css` 및 CSS Modules)와 어우러지는 일관된 비주얼 룩을 유지해야 합니다. (임의의 Ad-hoc 스타일 지양)
- [ ] 최저가 비교 가격 클릭 시 해당 쇼핑몰의 직접 구매 페이지로 새 탭 링크 아웃이 성공적으로 격발되어야 합니다.

### 데이터 및 예외 처리 (Data Integrity)
- [ ] JWT 인증 토큰 만료 또는 비로그인 상태 시 UI 단에서 로그인 연동 유도 혹은 모달 알림이 정상 제공되어야 합니다.
- [ ] 가격 비교 데이터가 존재하지 않는 핫딜의 경우, 레이아웃 붕괴 없이 "금액 확인 필요" 상태로 폴백 처리되어야 합니다.

### Technical Validation
- [ ] TypeScript 컴파일러 `npx tsc --noEmit` 검사 및 Next.js 프로덕션 빌드 `npm run build` 실행 시 경고 및 에러가 단 한 건도 발생하지 않아야 합니다.

## Follow-up — 2026-07-10T10:27:03Z

InsightDeal의 성공적인 출시를 위해 백엔드 스크래퍼들을 실제로 작동 및 테스트하고, 코드 베이스 전반의 버그나 비정상 동작을 꼼꼼하게 검증하고 디버깅하여 프로덕션 릴리즈가 가능한 무결성 상태로 보증한다.

Working directory: C:/Users/kth00/StudioProjects/InsightDeal
Integrity mode: development

## Requirements

### R1. 모든 활성 스크래퍼 통합 테스트 수행 및 디버깅
- `backend/scrapers` 및 `backend/main.py` 크롤러 파이프라인 상의 모든 스크래핑 스크립트(뽐뿌, 펨코, 루리웹, 퀘이사존, 클리앙 등)를 순차적으로 구동하여 최신 핫딜 페이지를 수집하는지 검증한다.
- 수집된 데이터가 로컬 SQLite 데이터베이스(`insight_deal.db`)에 누락 없이 무결하게 적재되는지, 중복 적재 방지 제약이 정상적으로 동작하는지 교차 검토하고 에러 발생 시 디버깅을 수행한다.

### R2. 백엔드 8080 포트 연동 정합성 및 API 정상 응답 검증
- 프로젝트 내 연동 설정 파일들(`next.config.ts`, `build.gradle`, `NetworkConfig.kt`, `NetworkModule.kt` 등)이 반드시 8080 포트를 바라보고 있는지 확인하고, 포트 정합성 오설정 잔상을 전수 조사하여 완전히 제거한다.
- 백엔드 FastAPI 서버를 기동하여 핫딜 목록 API(`GET /api/community`) 등이 8080 포트 상에서 정상 응답하는지 검증하고, 웹/앱 연동 무결성을 체크한다.

### R3. 알림/푸시 수신 가드 및 예외 처리 예방책 점검
- 신규 핫딜 수집 및 DB 적재 시 실행되는 실시간 키워드 알림 비교 엔진(`NotificationService.process_new_deal`)에서 예외가 발생하더라도 전체 수집 주기 루프가 중단되지 않도록 단단한 try-catch 예외 방어막을 보강한다.
- 야간 방해금지(DND) 처리 및 야간 푸시 수신 비동의 체크 등의 홀딩 조건이 법적 컴플라이언스 및 설계 규칙대로 정상 작동하는지 논리적 무결성을 검토한다.

## Acceptance Criteria

### 1. 스크래퍼 및 데이터 수집 무결성
- [ ] 각 사이트별 스크래퍼 구동 시 파싱 에러나 네트워크 예외로 인한 비정상 크래시 없이 정상 수집 로그가 확인되어야 한다.
- [ ] 스크래퍼 실행 후 `insight_deal.db`의 `deals` 테이블에 신규 수집된 핫딜 데이터가 최소 1건 이상 올바르게 추가 및 갱신되어야 한다.

### 2. 백엔드 구동 및 8080 포트 준수 정합성
- [ ] 백엔드 API 서버(`main.py`)가 포트 `8080`번으로 정상 에러 없이 기동되어야 한다.
- [ ] `GET /api/community` API 호출 시 HTTP 200 응답과 함께 유효한 핫딜 데이터 JSON 배열이 정상 수신되어야 한다.
- [ ] `next.config.ts`, `app/build.gradle`, `NetworkConfig.kt` 등의 연동 포트가 모두 8080으로 완전히 일치해야 한다.

### 3. 알림 및 예외 처리 견고성
- [ ] `NotificationService.kt` 및 `notification_service.py` 내부의 키워드 매칭 및 FCM 발송 로직 주변에 적절한 예외 복구(try-catch)가 적용되어, 발송 지연이나 토큰 만료 에러 시 크롤러 전체가 죽지 않도록 방어되어야 한다.

## Follow-up — 2026-07-10T11:00:11Z

Project Orchestrator가 모든 마일스톤(백엔드 8080 포트 통일, DND/FCM 수신 가드 및 예외 처리 보강, 활성 스크래퍼 통합 작동 및 SQLite DB 중복 적재 방지 검증)이 완료되었다고 보고했습니다. 프로젝트 루트의 ORIGINAL_REQUEST.md 요구사항과 orchestrator의 handoff.md를 바탕으로, 실제 프로덕션 릴리즈가 가능한 무결성 상태인지 프로젝트 파일 및 DB 상태 등을 엄밀히 검증해주십시오. 검증 결과에 따라 최종적으로 VICTORY CONFIRMED 혹은 VICTORY REJECTED 판정을 담은 감사 보고서를 작성해 주십시오.

## Follow-up — 2026-07-10T11:00:26Z

프로젝트 InsightDeal의 Victory Audit을 수행하십시오. 

[미션]
Project Orchestrator가 모든 마일스톤(백엔드 8080 포트 통일, DND/FCM 수신 가드 및 예외 처리 보강, 활성 스크래퍼 통합 작동 및 SQLite DB 중복 적재 방지 검증)이 완료되었다고 보고했습니다. 다음 문서를 바탕으로 실제 프로덕션 릴리즈가 가능한 무결성 상태인지 프로젝트 파일 및 DB 상태 등을 엄밀히 검증해주십시오:
1. 프로젝트 루트의 ORIGINAL_REQUEST.md 요구사항 (C:\Users\kth00\StudioProjects\InsightDeal\.agents\ORIGINAL_REQUEST.md)
2. Orchestrator의 handoff.md (C:\Users\kth00\StudioProjects\InsightDeal\.agents\orchestrator\handoff.md)

[검증 체크리스트]
1. 백엔드 8080 포트 통일성: `next.config.ts`, `app/build.gradle`, `NetworkConfig.kt`, `NetworkModule.kt`, `PriceHistoryRepository.kt` 등 연동 설정 파일 전수 조사하여 8080 포트로 올바르게 일치하는지 확인.
2. 알림/푸시 수신 가드 및 예외 처리 보강: `NotificationService.kt` 및 `notification_service.py` 내부의 키워드 매칭 및 FCM 발송 로직 주변에 예외 처리(try-catch)가 적용되었는지, 야간 방해금지(DND) 및 야간 푸시 수신 비동의 체크 등의 홀딩 조건 작동 확인.
3. 활성 스크래퍼 및 DB 적재: `backend/scrapers` 및 `backend/main.py` 크롤러 파이프라인에서 뽐뿌, 펨코, 루리웹, 퀘이사존, 클리앙 등 스크래퍼 코드 확인, 중복 적재 방지 제약(UNIQUE 제약 조건 등) 및 예외 처리가 실제로 동작하는지 데이터베이스 상태(insight_deal.db) 및 코드 확인.
4. AI 스웜 타운홀 토론 기록 및 클린업: `agent_workspace/00_Agent_Live_Chat.md` 파일에 핑퐁 대본이 남았는지, 임시 파일들이 청소되었는지 확인.
5. TypeScript 컴파일러 및 빌드 무결성 확인.

검증 결과에 따라 최종적으로 VICTORY CONFIRMED 혹은 VICTORY REJECTED 판정을 담은 감사 보고서(Audit Report)를 작성하여 handoff.md 형식 혹은 메시지로 전달해주십시오. 모든 보고서와 대화는 한국어로 작성해야 합니다.

## Follow-up — 2026-07-10T20:00:52+09:00

당신은 Victory Auditor입니다. 
프로젝트 InsightDeal에 대하여 Victory Audit을 수행해주십시오.
Orchestrator가 완료되었다고 보고한 마일스톤이 실제 프로덕션 릴리즈가 가능한 무결성 상태인지 프로젝트 파일 및 DB 상태 등을 엄밀히 검증해주십시오.

[검증 대상 문서]
1. 프로젝트 루트의 ORIGINAL_REQUEST.md 요구사항 (C:\Users\kth00\StudioProjects\InsightDeal\.agents\ORIGINAL_REQUEST.md)
2. Orchestrator의 handoff.md (C:\Users\kth00\StudioProjects\InsightDeal\.agents\orchestrator\handoff.md)

[검증 체크리스트]
1. 백엔드 8080 포트 통일성: `next.config.ts`, `app/build.gradle`, `NetworkConfig.kt`, `NetworkModule.kt`, `PriceHistoryRepository.kt` 등 연동 설정 파일 전수 조사하여 8080 포트로 올바르게 일치하는지 확인.
2. 알림/푸시 수신 가드 및 예외 처리 보강: `NotificationService.kt` 및 `notification_service.py` 내부의 키워드 매칭 및 FCM 발송 로직 주변에 예외 처리(try-catch)가 적용되었는지, 야간 방해금지(DND) 및 야간 푸시 수신 비동의 체크 등의 홀딩 조건 작동 확인.
3. 활성 스크래퍼 및 DB 적재: `backend/scrapers` 및 `backend/main.py` 크롤러 파이프라인에서 뽐뿌, 펨코, 루리웹, 퀘이사존, 클리앙 등 스크래퍼 코드 확인, 중복 적재 방지 제약(UNIQUE 제약 조건 등) 및 예외 처리가 실제로 동작하는지 데이터베이스 상태(insight_deal.db) 및 코드 확인.
4. AI 스웜 타운홀 토론 기록 및 클린업: `agent_workspace/00_Agent_Live_Chat.md` 파일에 핑퐁 대본이 남았는지, 임시 파일들이 청소되었는지 확인.
5. TypeScript 컴파일러 및 빌드 무결성 확인.

[결과 보고 형식]
검증 결과에 따라 최종적으로 VICTORY CONFIRMED 혹은 VICTORY REJECTED 판정을 담은 상세한 감사 보고서(Audit Report)를 작성하여 주십시오. 
보고서는 한국어로 작성해야 합니다. 완료 후 handoff.md 및 메세지를 통해 결과를 보고해주십시오.

