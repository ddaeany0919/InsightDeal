## 2026-07-10T10:28:08Z
당신은 InsightDeal 프로젝트의 코드베이스 분석가(explorer_1)입니다.
작업 디렉토리: C:\Users\kth00\StudioProjects\InsightDeal\.agents\explorer_1
귀하의 임무는 프로젝트 내의 포트 설정, 알림 가드, 스크래퍼 코드를 분석하여 실시간 대화 및 기획을 위한 기초 자료를 수집하는 것입니다.

다음 사항들을 꼼꼼히 조사하고 보고서(analysis.md)에 남겨주십시오:
1. `backend/scrapers`와 `backend/main.py`의 구조 및 구현 상태. SQLite 데이터베이스 `insight_deal.db`의 테이블 정의 및 중복 적재 방지 제약 조건.
2. `next.config.ts`, `app/build.gradle`, `NetworkConfig.kt`, `NetworkModule.kt` 및 기타 연동 설정 파일에서 8080 포트와 8000 포트의 설정 여부 및 잔상 확인.
3. 알림/푸시 수신 가드 및 예외 처리 상태:
   - `NotificationService` 및 관련 푸시/알림 엔진의 위치 및 try-catch 예외 방어막 상태.
   - 야간 방해금지(DND) 및 야간 푸시 수신 비동의 체크 로직의 유무와 컴플라이언스 준수성.

분석을 마친 뒤 C:\Users\kth00\StudioProjects\InsightDeal\.agents\explorer_1\analysis.md 에 보고서를 작성하고 parent에게 완료 메시지를 보내십시오.

## 2026-07-10T10:35:14Z
Please explore the following files in the project to prepare for the implementation:
1. `frontend-web/next.config.ts` (Look for 'http://backend:8000' and recommend replacement to 'http://backend:8080')
2. `backend/Dockerfile` (Look for port 8000 health check and uvicorn command, recommend replacement to 8080)
3. `backend/docker-compose.yml` (Look for port forwarding '8000:8000' and recommend replacement to '8080:8080')
4. `backend/routers/community.py` (Look for 'http://10.0.2.2:8000' fallback, recommend replacement to 'http://10.0.2.2:8080')
5. `backend/fetch_grouped.py` and `backend/fetch_grouped_top.py` (Look for localhost/IP port 8000 references, recommend replacement to 8080)
6. `backend/services/notification_service.py` (Analyze DND judgment logic. Recommend KST timezone binding to avoid UTC shift, filtering between 21:00 and 08:00 KST)
7. `app/src/main/java/com/ddaeany0919/insightdeal/core/NotificationService.kt` (Analyze DND logic 'hour >= 21 || hour < 8'. Recommend using EncryptedSharedPreferences to dynamic check dnd_start_time and dnd_end_time, and add try-catch exception guards for FCM message payload parsing to prevent crash)

Provide a detailed analysis report outlining the exact target code blocks and proposed changes. Run read-only file viewing tools. Do not modify files.
