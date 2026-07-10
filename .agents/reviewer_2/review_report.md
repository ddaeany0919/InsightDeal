# Review Report — 2026-07-10T20:00:00+09:00

## Review Summary

**Verdict**: REQUEST_CHANGES

InsightDeal 프로젝트 최종 품질 검증 결과, 실 서비스 구동에 사용되는 프로덕션 코드(백엔드 포트 8080 변경, DND/FCM 예외 가드, KST 타임존 가드)는 설계대로 정교하고 무결하게 구현되어 있음을 확인하였습니다. 그러나 통합 스크래퍼 검증용 테스트 스크립트(`backend/tests/run_scraper_test.py`)에서 `AsyncBaseScraper.run()` 호출 시 맞지 않는 매개변수(`limit`)를 전달하여 실행 오류가 발생하는 결함이 발견되어 `REQUEST_CHANGES` 판정을 내립니다.

---

## Findings

### [Major] Finding 1: 스크래퍼 통합 테스트 실행 오류 (`limit` 매개변수 불일치)

- **What**: `run_scraper_test.py`에서 각 스크래퍼의 `run()` 메서드를 호출할 때 `limit=5` 인자를 전달하고 있으나, 기본 클래스인 `AsyncBaseScraper.run()`은 `url: str` 매개변수만 전달받도록 설계되어 있어 `TypeError`가 격발됩니다.
- **Where**: `backend/tests/run_scraper_test.py` Line 60 (`scraper.run(limit=5)`) 및 `backend/scrapers/base_scraper.py` Line 148 (`async def run(self, url: str) -> List[dict]`)
- **Why**: 통합 테스트 코드가 스크래퍼 기본 클래스의 비동기 서명(Signature) 변경에 맞게 업데이트되지 않아, 스크래퍼 통합 테스트 전체가 정상 동작하지 못하는 결함이 발생합니다.
- **Suggestion**: `run_scraper_test.py`에서 `limit` 전달 방식을 수정하거나, 각 개별 스크래퍼 구현체 또는 기본 클래스인 `AsyncBaseScraper`에서 `limit` 매개변수를 허용하도록 수정해야 합니다.

---

## Verified Claims

1. **백엔드 8080 포트 연동 정합성** → verified via `view_file` & `grep_search` → **PASS**
   - `frontend-web/next.config.ts`, `backend/Dockerfile`, `backend/docker-compose.yml`, `backend/routers/community.py`, `backend/fetch_grouped.py`, `backend/fetch_grouped_top.py`, `frontend-web/src/app/api/push-register/route.ts` 등에서 포트 8000의 흔적이 완전히 배제되고 8080 포트가 정합성 있게 연동됨을 직접 코드를 검사하여 확인했습니다.
   - 안드로이드 클라이언트의 `app/build.gradle` (BASE_URL, EMULATOR_BASE_URL), `NetworkConfig.kt`, `PriceHistoryRepository.kt`, `NetworkModule.kt`에서도 동일하게 백엔드 8080 포트를 안전하게 바라보고 있음을 확인했습니다.

2. **KST 타임존 가드 및 정보통신망법 컴플라이언스** → verified via `view_file` (`backend/services/notification_service.py`) → **PASS**
   - 야간 광고성 정보 전송 제한 규정(21:00 ~ 익일 08:00)을 만족하기 위해 UTC 시차 영향을 완벽 방어하도록 `datetime.timezone(datetime.timedelta(hours=9))` (KST 오프셋)을 명시적으로 수립 및 적용하고 있음을 확인했습니다.
   - 야간 판단 여부에 따라 DND 활성 기기의 전송 홀딩/스킵 로직이 올바르게 설계되어 있습니다.

3. **안드로이드 DND 및 FCM 예외 가드** → verified via `view_file` (`NotificationService.kt`, `EncryptedPrefsManager.kt`) → **PASS**
   - `EncryptedSharedPreferences`를 활용하여 커스텀 방해금지 시간대(`dnd_start_time`, `dnd_end_time`)가 로컬 보안 스토리지에서 안전하게 인출 및 비교(자정을 걸치는 예외 케이스 분 계산 가드 포함)됨을 확인했습니다.
   - FCM 수신 시 발생할 수 있는 안드로이드 Keystore 백그라운드 스레드 로드 실패 예외 등에 대해 일반 SharedPreferences로 복구(Fallback)되도록 `EncryptedPrefsManager`에 예외 처리 로직이 견고히 적용되어 있고, FCM 수신 메서드 전체가 다중 예외 처리 가드로 방어 중임을 확인했습니다.

4. **SQLite DB 테이블 레코드 현황** → verified via `run_command` (DB 검사 임시 스크립트 실행) → **PASS**
   - `insight_deal.db` 데이터베이스가 단일화 및 실시간 동기화 상태로 원활히 동작 중입니다.
   - 테이블 레코드 현황:
     - `deals`: 9,187개 레코드 적재
     - `price_history`: 13,212개 레코드 적재
     - `communities`: 18개 레코드 적재
     - `push_keywords`: 3개 레코드 적재
     - `notification_alerts`: 4개 레코드 적재

---

## Coverage Gaps

- **unexplored area**: 스크래퍼 통합 테스트 실행 단계에서 `TypeError` 결함이 격발됨에 따라 개별 스크래퍼(`PpomppuScraper`, `FmkoreaScraper` 등) 내부 파싱 모듈의 실시간 크롤링 무결성 검증은 정상 진행되지 못했습니다.
- **risk level**: Medium
- **recommendation**: `run_scraper_test.py`의 결함 수정 이후 전체 스크래퍼 통합 테스트를 재가동하여 실시간 사이트 구조 변화에 따른 크롤링 에러 유무를 전수 조사해야 합니다.

---

## Unverified Items

- **item**: 각 개별 사이트별 실시간 크롤러 파싱 성공 여부
- **reason not verified**: `run_scraper_test.py`의 매개변수 오류(TypeError)로 인해 테스트가 정상 완주하지 못했기 때문입니다.
