# Original User Request

## 2026-07-10T19:35:00Z

당신은 InsightDeal 프로젝트의 개발자(worker_5)입니다.
작업 디렉토리: C:\Users\kth00\StudioProjects\InsightDeal\.agents\worker_5
귀하의 임무는 백엔드 8080 포트 정합성 검증 및 API 정상 구동을 위해 8000번 포트 잔상을 전수 제거하고, DND 타임존 및 FCM 알림 가드 예외 처리를 보강하는 것입니다.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

작업 지침:
1. 포트 8080 통일 및 8000 잔상 전수 교정:
   - `frontend-web/next.config.ts`: `http://backend:8000`을 `http://backend:8080`으로 변경.
   - `backend/Dockerfile`: 헬스체크 포트 `8000` -> `8080` 변경, uvicorn 포트 `8000` -> `8080` 변경.
   - `backend/docker-compose.yml`: 포트 포워딩 `"8000:8000"` -> `"8080:8080"` 변경.
   - `backend/routers/community.py`: default fallback `http://10.0.2.2:8000` -> `http://10.0.2.2:8080` 변경.
   - `backend/fetch_grouped.py` & `backend/fetch_grouped_top.py`: 로컬 테스트 URL의 포트를 `8000`에서 `8080`으로 일괄 교체.

2. 백엔드 타임존 지정 및 DND 가드 강화:
   - `backend/services/notification_service.py` 내부 `datetime.datetime.now()`를 사용하는 DND 판단 로직에서 타임존 정보를 명시하지 않아 발생하는 UTC 서버 시차 오류를 수정하십시오.
   - KST 시간대를 사용하도록 `timezone` 객체 (예: `datetime.timezone(datetime.timedelta(hours=9))` 또는 `pytz.timezone('Asia/Seoul')`)를 명시적으로 바인딩하여 한국 시간 기준 21:00 ~ 08:00에 DND 필터링이 정상 동작하도록 수정하십시오.

3. 안드로이드 클라이언트 맞춤 DND 동적 파싱 및 FCM 예외 가드 강화:
   - `app/src/main/java/com/ddaeany0919/insightdeal/core/NotificationService.kt` 내부의 DND 검사 로직(`hour >= 21 || hour < 8`)을 수정하여, 기기 로컬의 `EncryptedSharedPreferences` 등에서 커스텀 DND 시각(`dnd_start_time`, `dnd_end_time`)을 로드하여 동적으로 매칭 적용하도록 변경하십시오.
   - `onMessageReceived` 또는 관련 FCM 푸시 데이터 수신/파싱부 전체에 try-catch 예외 가드를 보강하여, 잘못된 페이로드 파싱으로 인한 앱의 비정상 크래시를 원천 방지하십시오.

주의 사항:
- 한글이 포함된 Kotlin/Python 소스 파일 수정 시, PowerShell의 Set-Content 대신 `replace_file_content` 툴을 사용하거나, Node.js 스크립트를 작성하여 안전하게 수정해 주십시오. (인코딩 안전 규칙 엄수)
- 작업 완료 후 빌드 및 테스트를 실행하여 정상 통과 여부를 확인하십시오.
- 최종 결과를 C:\Users\kth00\StudioProjects\InsightDeal\.agents\worker_5\handoff.md 에 기록하고 parent에게 완료 메시지를 보내십시오.
