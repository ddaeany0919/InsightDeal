# BRIEFING — 2026-07-10T19:59:00+09:00

## Mission
InsightDeal 최종 품질 검증 (포트 8080 연동 정합성, KST 타임존 가드, FCM & DND 예외 가드, 스크래퍼 통합 테스트 정합성 검증)

## 🔒 My Identity
- Archetype: reviewer_2
- Roles: reviewer, critic
- Working directory: C:\Users\kth00\StudioProjects\InsightDeal\.agents\reviewer_2
- Original parent: e2e87b2c-c1ce-45d2-a361-b4f4322eef0a
- Milestone: Final Verification
- Instance: 1 of 1

## 🔒 Key Constraints
- Review-only — do NOT modify implementation code
- 한국어(Korean) 우선순위 준수
- Ultra 레벨 지능의 정밀한 교차 검증 및 무결성 판정
- 8080 포트 바인딩 및 정합성 고수
- DND DND & FCM 예외 가드 로직 무결성 확인
- SQLite DB(insight_deal.db) 및 스크래퍼 순차 실행 테스트 정합성 검증
- 검증 결과 및 증적을 review_report.md에 기록하고 parent에게 보고

## Current Parent
- Conversation ID: e2e87b2c-c1ce-45d2-a361-b4f4322eef0a
- Updated: not yet

## Review Scope
- **Files to review**:
  - `frontend-web/next.config.ts`
  - `backend/Dockerfile`
  - `backend/docker-compose.yml`
  - `backend/routers/community.py`
  - `backend/fetch_grouped.py`
  - `backend/fetch_grouped_top.py`
  - `backend/services/notification_service.py`
  - `app/src/main/java/com/ddaeany0919/insightdeal/core/NotificationService.kt`
- **Interface contracts**: `PROJECT.md` 및 `GEMINI.md`
- **Review criteria**: correctness, completeness, conformance to rules

## Key Decisions Made
- [TBD]

## Artifact Index
- [TBD]
