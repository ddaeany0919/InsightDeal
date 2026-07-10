# Handoff Report — reviewer_2

## 1. Observation
품질 검증 과정에서 직접 확인한 사항 및 실행 도구의 출력 결과는 다음과 같습니다.

- **8080 포트 연동 정합성**:
  - `frontend-web/next.config.ts` Line 11: `const backendUrl = isDocker ? "http://backend:8080" : "http://localhost:8080";`
  - `backend/Dockerfile` Line 48: `CMD curl -f http://localhost:8080/api/health || exit 1`
  - `backend/Dockerfile` Line 51: `CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080", "--workers", "2"]`
  - `backend/docker-compose.yml` Line 9: `ports: - "8080:8080"`
  - `backend/routers/community.py` Line 98: `BASE_URL = os.getenv("BASE_URL", "http://10.0.2.2:8080")`
  - `backend/fetch_grouped.py` Line 6: `req = urllib.request.Request('http://localhost:8080/api/community/hot-deals?limit=200')`
  - `backend/fetch_grouped_top.py` Line 7: `req = urllib.request.Request('http://localhost:8080/api/community/top-hot-deals')`
  - `frontend-web/src/app/api/push-register/route.ts` Line 3: `const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080';`
  - `app/build.gradle` Line 34: `buildConfigField "String", "BASE_URL", '"http://192.168.0.4:8080/"'`
  - `app/build.gradle` Line 36: `buildConfigField "String", "EMULATOR_BASE_URL", '"http://10.0.2.2:8080/"'`
  - `app/src/main/java/com/ddaeany0919/insightdeal/data/network/NetworkConfig.kt` Line 17: `private const val SERVER_PORT = 8080`
  - `app/src/main/java/com/ddaeany0919/insightdeal/data/PriceHistoryRepository.kt` Line 93: `private const val BASE_URL = "http://192.168.0.36:8080/"`
  - `app/src/main/java/com/ddaeany0919/insightdeal/network/NetworkModule.kt` Line 21: `private const val BASE_URL = "http://192.168.0.36:8080/"`
  - `grep_search` 결과 프로젝트 전체에서 포트 8000의 잔상은 모두 주석 및 미사용 스크립트 로그에만 위치하고, 실제 런타임 코드상에서는 완전히 제거됨을 확인했습니다.

- **KST 타임존 가드**:
  - `backend/services/notification_service.py` Line 108:
    ```python
    kst = datetime.timezone(datetime.timedelta(hours=9))
    now = datetime.datetime.now(kst)
    hour = now.hour
    is_night_time = hour >= 21 or hour < 8
    ```
    명시적인 UTC+9 타임존 지정 및 DND 조건 분기가 이루어지고 있습니다.

- **안드로이드 DND 및 FCM 예외 가드**:
  - `app/src/main/java/com/ddaeany0919/insightdeal/core/NotificationService.kt` Line 295: `val prefs = EncryptedPrefsManager.getEncryptedPrefs(applicationContext)`
  - `NotificationService.kt` Line 304: `val isNightTime = checkTimeInDndRange(hour, minute, dndStartTime, dndEndTime)`
  - `EncryptedPrefsManager.kt` Line 42-49: Keystore 실패 시 `insight_deal_prefs_fallback`이라는 일반 SharedPreferences를 인출하도록 Fallback 처리 설계 완료.
  - `NotificationService.kt` `onMessageReceived` 메서드 전체가 다중 try-catch로 감싸져 수신 중 Crash 발생 가능성을 예방하고 있습니다.

- **SQLite DB 및 통합 테스트 실행 결과**:
  - `backend/insight_deal.db` 테이블 레코드 수:
    - `deals`: 9,187 records
    - `price_history`: 13,212 records
    - `communities`: 18 records
  - 스크래퍼 통합 테스트(`run_scraper_test.py`) 실행 시 다음과 같은 에러 로그 획득:
    ```
    Traceback (most recent call last):
      File "C:\Users\kth00\StudioProjects\InsightDeal\backend\tests\run_scraper_test.py", line 60, in run_test
        scraper.run(limit=5)
    TypeError: AsyncBaseScraper.run() got an unexpected keyword argument 'limit'
    ```

---

## 2. Logic Chain
1. **포트 8080 정합성**: 백엔드, 프론트엔드, 안드로이드의 주요 네트워크 연결 설정을 `view_file`로 열어서 포트 번호를 대조한 결과, 모두 8080 포트를 성공적으로 가리키고 있음을 관찰하여 "백엔드 8080 포트 연동 정합성"이 완벽하게 준수되었음을 확인했습니다.
2. **KST 타임존 가드**: `notification_service.py` 내의 시간 판단 로직에 KST(UTC+9) 오프셋이 적용되어 있는 것을 `view_file`로 관찰했으며, 야간 기준 시간(21시~08시)이 정상적으로 필터링되므로 정보통신망법 컴플라이언스를 충족합니다.
3. **FCM & DND 예외 가드**: `NotificationService.kt`와 `EncryptedPrefsManager.kt`에서 암호화 보안 스토리지 초기화 실패 예외에 대해 Fallback을 수립하고, DND 시간 대조 함수(`checkTimeInDndRange`)에서 자정 분기 시간대를 올바르게 연산하고 있음을 `view_file`로 검증했습니다.
4. **통합 테스트 결함**: `run_scraper_test.py`를 실행했을 때 `TypeError`가 격발되는 현상을 확인했습니다. `base_scraper.py`에 구현된 `AsyncBaseScraper.run()`은 `limit` 인자를 정의하고 있지 않은데, `run_scraper_test.py`에서 `limit=5`를 명시적으로 인자로 전달하여 발생한 예외입니다. 따라서 테스트 코드가 불합치한 상태로 방치되어 있는 결함을 도출했습니다.

---

## 3. Caveats
- `run_scraper_test.py` 실행 시 발생한 `TypeError` 결함으로 인해, 실제 8개 각 커뮤니티별 스크래퍼 파싱 모듈의 실시간 크롤링 무결성 검증은 완수되지 못했습니다. 따라서 개별 스크래퍼의 크롤링 로직 자체의 정상 유무는 추가 검증이 필요합니다.

---

## 4. Conclusion
1. ** verdicts(CLEAN 여부)**:
   - 프로덕션 시스템(FCM/DND 예외 가드, 8080 포트, KST 타임존 가드)은 **CLEAN**합니다.
   - 단, 검증용 테스트 코드인 `run_scraper_test.py`는 `AsyncBaseScraper.run` 시그니처와 매치되지 않는 버그가 존재하여 **REQUEST_CHANGES** 판정을 내립니다.
2. **조치 요구사항**:
   - `backend/tests/run_scraper_test.py`의 Line 60을 수정하여, `limit` 인자 없이 수집을 실행하거나 기본 클래스(`AsyncBaseScraper`)의 `run` 함수 정의를 업데이트해야 합니다.

---

## 5. Verification Method
- **독립 검증 명령**:
  - `PYTHONPATH` 설정을 추가하여 스크래퍼 통합 테스트를 다시 격발해 봅니다.
    ```powershell
    $env:PYTHONPATH="C:\Users\kth00\StudioProjects\InsightDeal;C:\Users\kth00\StudioProjects\InsightDeal\backend"
    C:\Users\kth00\StudioProjects\InsightDeal\backend\venv\Scripts\python.exe C:\Users\kth00\StudioProjects\InsightDeal\backend\tests\run_scraper_test.py
    ```
  - **오류 발생 조건**: `AsyncBaseScraper.run()`에 `limit` 매개변수가 추가되지 않았거나, `run_scraper_test.py`가 수정되지 않은 상태에서 실행하면 동일한 `TypeError`가 격발됩니다.
