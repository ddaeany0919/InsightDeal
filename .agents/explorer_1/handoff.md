# Handoff Report — explorer_1 (v1.2)

구현 준비를 위한 7개 특정 파일의 포트 8000번 잔상 제거(8080 교체) 및 DND(방해금지) 판단 로직 개선(KST 고정, EncryptedSharedPreferences 동적 로드, FCM 예외 방어 가드) 정밀 분석 Handoff 보고서입니다.

---

## 1. Observation (직접 관찰한 사실)

1. **`frontend-web/next.config.ts`**:
   - Line 11: `const backendUrl = isDocker ? "http://backend:8000" : "http://localhost:8080";`
   - Line 9 (주석): `// 도커 컨테이너 기동 시 브릿지 네트워크 호스트인 backend:8000으로 프록시하고, 로컬 기동 시 localhost:8000을 탑니다.`

2. **`backend/Dockerfile`**:
   - Line 48: `  CMD curl -f http://localhost:8000/api/health || exit 1`
   - Line 51: `CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]`

3. **`backend/docker-compose.yml`**:
   - Line 9: `      - "8000:8000"`

4. **`backend/routers/community.py`**:
   - Line 98: `BASE_URL = os.getenv("BASE_URL", "http://10.0.2.2:8000")`

5. **`backend/fetch_grouped.py` & `backend/fetch_grouped_top.py`**:
   - `backend/fetch_grouped.py` Line 6: `        req = urllib.request.Request('http://localhost:8000/api/community/hot-deals?limit=200')`
   - `backend/fetch_grouped_top.py` Line 7: `        req = urllib.request.Request('http://localhost:8000/api/community/top-hot-deals')`

6. **`backend/services/notification_service.py`**:
   - Line 107-109:
     ```python
     now = datetime.datetime.now()
     hour = now.hour
     is_night_time = hour >= 21 or hour < 8
     ```
   - 서버의 로컬 시스템 시간대 정보가 없는 단순 `now()` 객체를 사용하여 야간 방해금지 시간대를 체크하고 있습니다.

7. **`app/src/main/java/com/ddaeany0919/insightdeal/core/NotificationService.kt`**:
   - Line 261: `val isNightTime = hour >= 21 || hour < 8` 로 DND 판단 구간이 하드코딩되어 있습니다.
   - Line 90~127 (`onMessageReceived`): 데이터 메시지 처리(`handleDataMessage`) 및 알림 처리(`showSmartNotification`) 호출 시 전체적인 예외 처리 캐시(try-catch) 가드가 부재한 상태로, 원격 FCM 메시지 페이로드 파싱 실패(예: `price.toIntOrNull()`) 발생 시 앱 크래시가 유발될 가능성이 관찰됩니다.

---

## 2. Logic Chain (논리 추론 과정)

1. **포트 8080 단일화**:
   - 프로젝트 정합성 규칙에 따라 백엔드 API 포트는 8080 포트로 기동 및 연동되어야 합니다.
   - 웹 프론트엔드가 도커 환경에서 백엔드 API(`backend:8000`)를 Rewrite 프록시하도록 설정되어 있어 도커 가동 시 컨테이너 간 통신이 원천 차단됩니다. (`frontend-web/next.config.ts`)
   - Dockerfile의 헬스체크 및 uvicorn CMD 구동 포트, docker-compose.yml의 포트 포워딩, 백엔드 API fallback 경로(`routers/community.py`), 로컬 테스트 헬퍼 스크립트(`fetch_grouped.py`, `fetch_grouped_top.py`) 등 전체 파일에 걸쳐 8000번 잔상이 남아 있으므로 이를 8080 포트로 변경해야 포트 정합성이 완성됩니다.

2. **백엔드 DND 시간대(KST) 바인딩**:
   - `notification_service.py`가 UTC 등 타임존이 다른 클라우드 서버 환경에서 실행될 경우, 단순 `now.hour`로 DND 구간을 연산하면 9시간의 시차가 발생하여 한국 시간 대낮에 알림이 필터링되거나, 반대로 한국 시간 새벽 시간에 소리와 진동 푸시가 발송되어 중대한 야간 컴플라이언스(정보통신망법) 위반 및 야간 소음 참사를 초래합니다.
   - 이를 극복하기 위해 `timezone(timedelta(hours=9))` KST 오프셋을 명시적으로 주입하여 연산해야 합니다.

3. **안드로이드 클라이언트 DND 로직 고도화**:
   - 사용자가 기기 단에서 직접 맞춤 설정한 DND 시작/종료 시간(`dnd_start_time`, `dnd_end_time`)이 로컬 스토리지에 있음에도 불구하고, 클라이언트 이중 가드에서는 `hour >= 21 || hour < 8`로 고정되어 있어 괴리가 생깁니다.
   - `EncryptedSharedPreferences`에서 `dnd_start_time` 및 `dnd_end_time`을 로드하여 현재 시간과 동적으로 대조하는 범위 계산 헬퍼 메서드가 적용되어야 합니다.

4. **FCM 수신 예외 방어막**:
   - `onMessageReceived` 최외각에 전체 try-catch 가드가 없고, 하위 처리 메서드에도 안전 가드가 부족하여 서버에서 보낸 비정상 FCM 페이로드(파싱 실패 등)에 의해 앱이 예기치 않게 강제 종료될 수 있습니다.
   - 파싱 및 호출 단계마다 예외를 안전하게 격리하고 최외각 예외 방어 가드를 적용해야 프로덕션 환경의 안정성이 확보됩니다.

---

## 3. Caveats (주의 및 한계 사항)

* 실제 도커 컨테이너 기동 및 FCM 푸시 전송 실측 테스트를 수행한 것은 아니며, 코드베이스 검증과 23인 스웜 타운홀 토론 설계를 통한 정적 분석 및 제안서 작성 단계입니다.
* 사용자의 AI 토큰 비용 방어 규칙에 따라 AI 관련 요약/추천 기능은 배제하고 순수 텍스트/시간 필터링 알림 로직에 집중하여 설계하였습니다.

---

## 4. Conclusion (최종 판단 및 조치 사항)

* **포트 잔상 제거**: `frontend-web/next.config.ts`, `backend/Dockerfile`, `backend/docker-compose.yml`, `backend/routers/community.py`, `backend/fetch_grouped.py`, `backend/fetch_grouped_top.py` 파일들의 8000번 포트 설정을 일괄적으로 **8080 포트**로 교체 보정하는 것이 시급하며 타당합니다.
* **시간대 및 동적 DND**: 백엔드 `notification_service.py`에서는 명시적 KST 타임존 바인딩 가드를 치고, 안드로이드 클라이언트 `NotificationService.kt`에서는 `EncryptedSharedPreferences`에서 `dnd_start_time`/`dnd_end_time`을 로드해 동적 범위 연산을 태우도록 구조를 변경해야 합니다.
* **FCM 안정성**: `NotificationService.kt`의 `onMessageReceived` 함수 전체와 하위 파싱 호출 단계에 개별 try-catch 예외 방어막을 장착하여 페이로드 오류 시의 앱 크래시 리스크를 소거해야 합니다.

---

## 5. Verification Method (독립 검증 방법)

1. **포트 8080 정합성 검증**:
   - 교체 후 `frontend-web/next.config.ts` Line 11의 `backendUrl` 포트가 `8080`인지 확인.
   - `backend/Dockerfile` Line 48 헬스체크 주소와 Line 51 uvicorn 포트 매개변수가 `--port 8080`인지 확인.
   - `backend/docker-compose.yml` Line 9의 ports 포워딩이 `"8080:8080"`인지 확인.
   - `backend/routers/community.py` Line 98의 default BASE_URL이 `10.0.2.2:8080`인지 확인.
   - `fetch_grouped.py` 및 `fetch_grouped_top.py` 내 API 포트 호출부가 `8080`으로 변경되었는지 확인.
2. **타임존 및 DND 검증**:
   - `backend/services/notification_service.py`에서 `datetime.datetime.now(timezone(timedelta(hours=9)))` KST 바인딩이 구현되어 로컬/클라우드 서버 시간대에 상관없이 DND 판단이 동일하게 동작하는지 단위 테스트 수행.
   - 안드로이드 클라이언트의 `NotificationService.kt`에서 `EncryptedSharedPreferences`로부터 동적 값을 로드하여 DND 시간 대조를 수행하는 `checkTimeInDndRange` 헬퍼 메서드의 유효성 검증.
3. **FCM 예외 가드 검증**:
   - `onMessageReceived` 최외각 예외 처리 추가 후, 비정상 FCM 페이로드(예: price 값에 문자열 삽입하여 NumberFormatException 유도)를 임의 수신시키는 시뮬레이션을 돌려 앱이 크래시 없이 정상 구동을 유지하는지 확인.
