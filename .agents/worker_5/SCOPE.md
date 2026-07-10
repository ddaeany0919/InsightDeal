# Scope: Port Unification & DND / FCM Guard Enhancement

## Architecture
- 웹 프론트엔드: Next.js (`frontend-web/next.config.ts` 및 API 포트 설정)
- 백엔드 서버: FastAPI (`backend/Dockerfile`, `backend/docker-compose.yml`, `backend/routers/community.py`, `backend/fetch_grouped.py`, `backend/fetch_grouped_top.py`)
- 안드로이드 클라이언트: Kotlin App (`app/src/main/java/com/ddaeany0919/insightdeal/core/NotificationService.kt`)
- 알림 전송: Firebase Cloud Messaging (`backend/services/notification_service.py` -> FCM 전송 시 DND 가드 확인)

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | Port 8080 Unification | next.config.ts, Dockerfile, docker-compose.yml, community.py, fetch_grouped.py, fetch_grouped_top.py 포트 8080 변경 | None | DONE |
| 2 | Backend Timezone & DND Guard | notification_service.py 내 DND 판별 시 Asia/Seoul 타임존 사용하도록 교정 | M1 | DONE |
| 3 | Android DND & FCM Exception Guard | NotificationService.kt DND 설정 동적 바인딩 및 파싱 에러 가드 보강 | M2 | DONE |
| 4 | Verification | 로컬 빌드 테스트 및 API 구동 검증 | M3 | DONE |

## Interface Contracts
### Backend ↔ Android FCM Payload
- Notification payload parsing must be wrapped in strong try-catch.
- Dynamic DND Settings key: `dnd_start_time` (String, e.g. "21:00"), `dnd_end_time` (String, e.g. "08:00") stored via EncryptedSharedPreferences.
