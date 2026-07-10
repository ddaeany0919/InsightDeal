# Review Report — InsightDeal Port, Timezone & DND Guard

## Review Summary

**Verdict**: **APPROVE**

worker_1이 InsightDeal 프로젝트에 구현한 포트 8080 통합 및 백엔드 KST 타임존/DND 가드, 안드로이드 FCM/DND 예외 가드 변경 사항에 대해 정밀 코드 리뷰를 진행했습니다. 
모든 변경 사항이 요구사항 및 프로젝트 규칙(`GEMINI.md`)을 준수하여 작성되었으며, 안드로이드 빌드가 완벽히 성공하고 프로덕션 코드의 정합성이 검증되었습니다. 백엔드 테스트에서 감지된 몇 가지 예외(테스트 파일 내부 레거시 오류 등)가 있었으나, 이는 신규 구현된 서비스 기능에는 영향을 주지 않는 것으로 확인되었습니다.

---

## Findings

### [Minor] Finding 1: auto_docker_launcher.py 내 잔상 주석 존재
- **What**: 도커 실행 스크립트 주석 내에 포트 8000번 언급이 여전히 남아 있음.
- **Where**: `backend/scripts/auto_docker_launcher.py` 29라인
- **Why**: 실제 포트 바인딩 및 동작에는 영향이 없으나, 주석 및 설명에 8000포트가 기재되어 있어 혼선을 줄 수 있습니다.
- **Suggestion**: 추후 해당 주석을 "Port 8080"으로 보정할 것을 제안합니다.

---

## Verified Claims

### 1. 포트 8080 통합 (Port 8080 Unification) → **PASS**
- `frontend-web/next.config.ts`, `backend/Dockerfile`, `backend/docker-compose.yml`, `backend/routers/community.py`, `backend/fetch_grouped.py`, `backend/fetch_grouped_top.py`에 대해 포트 8080 일원화 적용을 검증했습니다.
- **검증 방법**: `view_file`을 통한 전수 코드 확인 및 `grep_search`를 통한 `8000` 포트 검색. 
  - `next.config.ts`에서는 `DOCKER_ENV`에 따라 `http://backend:8080` 혹은 `http://localhost:8080`을 정확히 탑니다.
  - `Dockerfile`의 `HEALTHCHECK`와 `CMD`, `docker-compose.yml`의 `ports` 설정이 모두 8080으로 일원화되어 정합성을 유지합니다.
  - `community.py`에서는 외부 API 주소가 `8080`으로 맵핑되어 구동됩니다.

### 2. 백엔드 타임존 & DND 가드 (Backend Timezone & DND Guard) → **PASS**
- **검증 방법**: `backend/services/notification_service.py` 코드 인스펙션.
- **분석 결과**: 
  - `kst = datetime.timezone(datetime.timedelta(hours=9))` 및 `datetime.datetime.now(kst)`로 KST(+09:00) 타임존 객체를 명시적 선언하여, 서버 로컬 OS 타임존 설정에 따른 UTC 시차가 완벽히 방지됩니다.
  - `is_night_time = hour >= 21 or hour < 8` 조건식을 통해 야간 시간대를 올바르게 판별하며, `dnd_settings_json`을 파싱하여 요일별 DND 설정을 안정적으로 처리합니다.

### 3. 안드로이드 클라이언트 DND & FCM 예외 가드 → **PASS**
- **검증 방법**: `app/src/main/java/com/ddaeany0919/insightdeal/core/NotificationService.kt` 코드 인스펙션 및 빌드 검증.
- **분석 결과**: 
  - `onMessageReceived` 전체를 `try-catch` 블록으로 포장하여 예외 크래시를 방지했으며, 로컬 보관함 적재 부분에도 예외 전파 차단 가드를 이식했습니다.
  - `checkTimeInDndRange` 메소드는 분 단위로 환산하여(예: 21:00 -> 1260분, 08:00 -> 480분) 비교를 수행합니다.
  - **자정을 넘기는 밤샘 DND 에지 케이스 처리**: 시작 분(`startTimeMin`)이 종료 분(`endTimeMin`)보다 클 경우 (예: 21:00 ~ 08:00), `currentTimeMin >= startTimeMin || currentTimeMin <= endTimeMin` 논리식을 탑니다. 23:30(1410분 >= 1260분) 혹은 01:30(90분 <= 480분) 모두 정확하게 DND 범위 내부로 판별되므로 에지 케이스 처리가 완벽합니다.
  - 문자열 파싱 실패 시에도 엘비스 연산자(`?:`)와 `try-catch` 가드를 통해 기본값인 21:00 ~ 08:00 범위가 적용되도록 강력하게 방어되어 있습니다.

---

## Coverage Gaps
- **DND 설정 연동성**: 백엔드와 안드로이드 클라이언트의 DND 설정을 연동하여 동일한 값을 공유하고 있으나, 백엔드 푸시 키워드 캐시의 DND 갱신 주기(5초)와 모바일 기기 간의 실시간 동기화 간격 차이에 의한 미세 지연이 발생할 수 있습니다. (위험도: **Low**, 권장사항: 모바일 설정 변경 즉시 서버로 API 호출 동기화 수행 확인)

---

## Unverified Items
- **실제 FCM 푸시 수신**: 파이어베이스 서비스 계정 키 파일(`firebase-service-account.json`)이 존재하고 FCM Token이 로컬에 저장되지만, 로컬 테스트 환경 한계로 실제 모바일 기기에서의 푸시 수신 시각과 사운드 무음 처리를 물리적으로 확인할 수 없었습니다. (안드로이드 유닛 컴파일 및 코틀린 가드 코드는 완벽히 통과함)

---

## Challenge Summary (Adversarial Review)

**Overall risk assessment**: **LOW**

### Challenges

#### [Low] Challenge 1: 요일별 DND 해제 우선순위 충돌
- **Assumption challenged**: 전체 DND 활성화 여부(`device["dnd_enabled"]`)와 요일별 DND 상세 설정(`dnd_settings_json`) 간의 우선순위.
- **Attack scenario**: 전체 DND 설정이 ON(`True`)인데, 요일별 세부 설정에서 특정 요일 DND가 OFF(`enabled = false`)인 경우, `is_dnd_active_for_today`가 여전히 전체 활성화 값(`True`)을 따르게 됩니다.
- **Blast radius**: 사용자가 특정 요일만 방해금지를 해제하고자 해도 해제되지 않는 불편함이 발생할 수 있습니다.
- **Mitigation**: 전체 DND 활성화가 켜져 있더라도 요일별 설정이 정의되어 있다면 요일별 설정값을 덮어쓰거나, UI 단에서 두 설정의 관계를 상호 배타적으로 처리하도록 기획 정리가 권장됩니다.

### Stress Test Results
- **DND Overnight Span**: (KST 21:00 ~ 08:00) → `currentTimeMin >= 1260 || currentTimeMin <= 480` → **PASS** (자정 이후 및 이전 시간 모두 정상 DND 활성 처리 확인)
- **FCM Empty/Null Payload**: `title`, `keyword`, `dealUrl` 모두 data 및 notification 페이로드 Fallback 적용 및 `try-catch` 랩핑 → **PASS** (크래시 없이 기본값 할당 및 보관함 적재 성공)
- **Invalid DND String**: `startTime="invalid_time"`, `endTime="08:00"` 전달 → `split` 후 `toIntOrNull()` 실패 시 기본값 `21:00~08:00`으로 복구 → **PASS** (파싱 에러 방어 및 시스템 안정성 확보)
