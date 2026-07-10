# 📊 InsightDeal 코드베이스 정밀 분석 보고서 (v1.1)

본 보고서는 **InsightDeal** 프로젝트의 데이터베이스 제약 조건, 스크래퍼 수집 아키텍처, 8000/8080 포트 불일치 잔상, 그리고 푸시 알림 엔진의 예외 방어 및 야간 컴플라이언스(DND) 상태를 정밀 분석하여 구체적인 Target Code Block과 Proposed Changes를 제안하는 결과물입니다.

---

## 1. 데이터베이스 스키마 및 스크래핑 엔진 아키텍처 (기존 분석 유지)

### 1.1 SQLite 데이터베이스 (`insight_deal.db`) 테이블 정의 및 제약 조건
프로젝트 내 데이터베이스는 `backend/database/session.py`를 기점으로 `backend/database/models.py`에 정의된 SQLAlchemy 모델에 따라 SQLite 데이터베이스 파일(`backend/insight_deal.db`)로 단일화되어 있습니다.
중복 적재 및 정합성 유지를 위한 주요 테이블과 스키마 레벨의 유니크 제약 조건(`UniqueConstraint`)은 다음과 같이 설계되어 있습니다.

1. **`deals` 테이블 (핫딜 원본 정보 적재)**
   - `post_link` (String): 원본 커뮤니티 게시글 URL
   - `title` (TEXT): 게시글 제목
   - **중복 방지 제약**: `__table_args__ = (UniqueConstraint('post_link', 'title', name='_post_link_title_uc'),)`
   - **동작**: 수집된 핫딜의 제목과 원본 링크가 동시에 동일한 행은 데이터베이스 수준에서 강제 차단됩니다.
2. **`products` 테이블 (쿠팡 상품 추적 정보)**
   - `user_id` (String), `product_id` (String)
   - **중복 방지 제약**: `__table_args__ = (UniqueConstraint('user_id', 'product_id', name='unique_user_product'),)`
   - **동작**: 동일 사용자가 같은 쿠팡 상품을 중복하여 관심 추적 대상으로 등록하는 것을 물리적으로 제어합니다.
3. **`keyword_wishlist` 테이블 (키워드 관심 상품)**
   - `user_id` (String), `keyword` (String)
   - **중복 방지 제약**: `__table_args__ = (UniqueConstraint('user_id', 'keyword', name='unique_user_keyword'),)`
   - **동작**: 동일 사용자가 동일한 검색 키워드를 중복 등록하는 현상을 예방합니다.
4. **`product_price_history` 테이블 (상품 가격 변화 시계열)**
   - `product_id` (Integer), `tracked_at` (TIMESTAMP)
   - **중복 방지 제약**: `__table_args__ = (UniqueConstraint('product_id', 'tracked_at', name='unique_product_time'),)`
   - **동작**: 동일 시각에 중복된 가격 기록이 적재되는 것을 차단합니다.
5. **SQLite 다중 쓰기 락 및 손상 방지 가드 (`PRAGMA`)**
   - 다중 비동기 크롤러 워커들이 동시에 데이터베이스를 쓸 때 발생하는 DB 락 및 손상(Corruption)을 방지하기 위해 `backend/database/session.py`에서 다음과 같은 SQLite 전용 옵션을 적용하고 있습니다:
     - `connect_args = {"check_same_thread": False, "timeout": 30}`
     - DB 커넥션 획득 시 `@event.listens_for(self.engine, "connect")`를 통해 `PRAGMA busy_timeout=60000` (60초)을 명시적으로 실행하여 락 대기 오버헤드를 제어합니다.
     - `backend/scheduler/main.py`에서는 SQLite 환경에 맞춰 동시 병렬 쓰기를 차단하고 단 1개의 Consumer Worker만 기동하도록 제약하고 있습니다 (`workers = [asyncio.create_task(worker()) for _ in range(1)]`).

### 1.2 스크래퍼 및 스케줄러 아키텍처 (`backend/scheduler/main.py`)
수집 엔진은 APScheduler의 `AsyncIOScheduler`를 기반으로 작동하며, 비동기 `Producer-Consumer` 큐 방식을 채택하고 있습니다.
* **비동기 큐 기반 파이프라인**: 
  - `scrape_community` 함수가 각 커뮤니티 게시판(뽐뿌, 퀘이사존, 펨코 등)을 비동기적으로 스크래핑하여 `asyncio.Queue`에 삽입합니다 (Producer).
  - 1개의 싱글 Consumer Worker가 큐에서 핫딜을 꺼내 분석 및 DB 저장을 수행하며, 각 작업 시마다 `SessionLocal()`을 명시적으로 열고 닫아 커넥션 누수를 원천 차단합니다.
* **시간 기반 스마트 델타 스킵 가드 (Time-based Delta Skip Guard)**:
  - 이미 수집된 기존 핫딜일 경우, 불필요한 DB 쓰기 및 외부 I/O 부하를 없애기 위해 아래의 가드를 적용합니다.
    - **가드 1 (노후 핫딜 스킵)**: 최초 수집된 지 12시간이 경과한 노후 핫딜은 핫딜마크 달성 기한이 만료되었으므로 추가 갱신 연산을 스킵합니다.
    - **가드 2 (안정기 핫딜 스킵)**: 수집된 지 3~12시간 사이의 안정기 글은 최근 20분 이내에 이미 리액션이 갱신되었다면 DB Upsert를 건너뜁니다.
* **상세 페이지 재크롤링 방지 (`Lazy-Loading` 최적화)**:
  - 기존 딜 갱신 시, 상세 본문(`content_html`)이 이미 채워져 있는 경우에는 추가 상세 크롤링(`get_detail`)을 생략하고 리스트의 초경량 메타데이터(추천수, 조회수 등)로만 Upsert하여 크롤링 속도를 15배 이상 향상시킵니다.
* **자가 치유 복구 (Self-Healing Backfill) 로직**:
  - `run_pipeline_job` 기동 시 DB에서 가장 최근에 수집된 핫딜의 `indexed_at`을 검사합니다.
  - 최종 수집 완료된 지 2시간을 초과한 공백(서버 장애나 개발 중단 등)이 감지되면 자동으로 **자가 치유 백필 모드**로 진입합니다.
  - 누락 시간 비율에 비례하여 탐색할 과거 페이지 깊이를 최대 25페이지(약 5일 치 분량)까지 동적으로 늘려 누락된 과거 데이터를 복구합니다.
* **과거 딜 품절 검증 및 자가치유 승격 (`validate_closed_deals`)**:
  - 최근 3일 이내에 수집된 미종료(`is_closed == False`) 핫딜 중 최신 150건을 대상으로 20분마다 URL 핑(Ping) 테스트를 수행합니다.
  - 원본 게시글이 404 에러를 반환하거나 삭제 시그니처가 감지되면 종료(`is_closed = True`) 처리합니다.
  - **자가치유 승격 (Self-Healing Healing)**: 대표 딜 링크(`post_link`)가 폭파되었더라도, 병합된 서브 커뮤니티 주소들(`merged_communities`) 중 살아있는 링크가 존재한다면 이를 대표 딜 정보(커뮤니티 ID, URL, 최초 작성 시간)로 자동 교체 승격시켜 데이터 무결성을 지탱합니다.

---

## 2. 포트 설정 검증 및 8000/8080 포트 잔상 진단

프로젝트 위생 정리 규칙(에픽 4 및 가이드라인 4.1)에 의거하여, 로컬 서버 기동 및 연동 포트는 8080 포트로 단일화되어야 합니다. 모바일 안드로이드 클라이언트 영역에서는 이를 철저히 준수하고 있으나, 백엔드 및 웹 프론트엔드 환경 파일들 전반에 8000 포트 잔상이 대거 방치되어 있어 정비가 시급합니다.

아래는 정비 대상 파일들과 구체적인 Target Code Block 및 Proposed Changes입니다.

### 2.1 `frontend-web/next.config.ts`
- **분석**: 도커 환경에서 동작하는 웹 프론트엔드가 백엔드 API 서버를 프록시할 때 `http://backend:8000`을 향하게 되어 있어, 도커 컨테이너 간 통신이 전면 실패하게 만드는 결함입니다.

* **Target Code Block (Line 9-11)**:
```typescript
// 도커 컨테이너 기동 시 브릿지 네트워크 호스트인 backend:8000으로 프록시하고, 로컬 기동 시 localhost:8000을 탑니다.
const isDocker = process.env.DOCKER_ENV === "true";
const backendUrl = isDocker ? "http://backend:8000" : "http://localhost:8080";
```

* **Proposed Changes**:
```typescript
// 도커 컨테이너 기동 시 브릿지 네트워크 호스트인 backend:8080으로 프록시하고, 로컬 기동 시 localhost:8080을 탑니다.
const isDocker = process.env.DOCKER_ENV === "true";
const backendUrl = isDocker ? "http://backend:8080" : "http://localhost:8080";
```

---

### 2.2 `backend/Dockerfile`
- **분석**: Dockerfile 내의 헬스체크 및 uvicorn 기본 구동 포트가 여전히 8000번으로 고정되어 있습니다. 개발자가 docker-compose 환경이 아닌 단독 컨테이너 빌드로 서비스를 올릴 때 포트 8000 바인딩을 시도하므로, 8080 단일화 정책에 전면 위배됩니다.

* **Target Code Block (Line 46-51)**:
```dockerfile
# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8000/api/health || exit 1

# Default command (overridden by docker-compose for dev)
CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
```

* **Proposed Changes**:
```dockerfile
# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

# Default command (overridden by docker-compose for dev)
CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080", "--workers", "2"]
```

---

### 2.3 `backend/docker-compose.yml`
- **분석**: 백엔드 전용 도커 컴포즈 파일에서 FastAPI API 서비스(`insightdeal_api`)를 외부 호스트의 8000 포트로 포워딩하고 있습니다. 이 역시 WSL2 환경에서 포트 바인딩 락 에러를 유발하는 위험 지점입니다.

* **Target Code Block (Line 8-9)**:
```yaml
    ports:
      - "8000:8000"
```

* **Proposed Changes**:
```yaml
    ports:
      - "8080:8080"
```

---

### 2.4 `backend/routers/community.py`
- **분석**: API fallback 주소가 `10.0.2.2:8000`으로 잡혀있어 모바일과의 프록시 주소 바인딩 정합성이 어긋납니다.

* **Target Code Block (Line 98)**:
```python
BASE_URL = os.getenv("BASE_URL", "http://10.0.2.2:8000")
```

* **Proposed Changes**:
```python
BASE_URL = os.getenv("BASE_URL", "http://10.0.2.2:8080")
```

---

### 2.5 `backend/fetch_grouped.py` & `backend/fetch_grouped_top.py`
- **분석**: 로컬 환경에서 통계를 취합하거나 테스트하기 위해 사용하는 유틸리티 스크립트들이 `localhost:8000`을 바라보고 있어, 기동 시 포트 연결 거부 에러가 떨어지게 됩니다.

#### `backend/fetch_grouped.py`
* **Target Code Block (Line 6)**:
```python
        req = urllib.request.Request('http://localhost:8000/api/community/hot-deals?limit=200')
```

* **Proposed Changes**:
```python
        req = urllib.request.Request('http://localhost:8080/api/community/hot-deals?limit=200')
```

#### `backend/fetch_grouped_top.py`
* **Target Code Block (Line 7)**:
```python
        req = urllib.request.Request('http://localhost:8000/api/community/top-hot-deals')
```

* **Proposed Changes**:
```python
        req = urllib.request.Request('http://localhost:8080/api/community/top-hot-deals')
```

---

## 3. 알림/푸시 수신 가드 및 컴플라이언스 준수성 분석

정보통신망법 등의 광고성 정보 전송 제한 규제(야간 전송 제한)를 완벽히 준수하고 앱 실행 안전성을 담보하기 위해 알림 가드 구조가 이중으로 설계되어 있습니다.

### 3.1 `backend/services/notification_service.py` (KST 타임존 지정 가드)
- **분석**: `datetime.datetime.now()`는 타임존 정보가 배제된 서버 시스템 로컬 시각을 기준삼아 동작합니다. 만약 백엔드가 UTC(세계협정시) 환경의 도커 컨테이너나 클라우드 서버(AWS EC2 등)에서 구동된다면 한국 시간(KST)보다 9시간 늦게 체크가 발생합니다. 한국 표준시(KST) 오프셋을 명시적으로 바인딩하여 시차 오류를 전격 교정해야 합니다.

* **Target Code Block (Line 105-109)**:
```python
        # ⏰ 야간 시간대 체크 (KST 기준 21:00 ~ 익일 08:00)
        # 현재는 로컬 시간대 기준 KST 환산
        now = datetime.datetime.now()
        hour = now.hour
        is_night_time = hour >= 21 or hour < 8
```

* **Proposed Changes**:
```python
        # ⏰ 야간 시간대 체크 (KST 기준 21:00 ~ 익일 08:00)
        # 명시적으로 KST (UTC+9) 타임존 오프셋을 바인딩하여 UTC 서버 환경 시차 오류(UTC Shift) 소거
        from datetime import timezone, timedelta
        kst = timezone(timedelta(hours=9))
        now = datetime.datetime.now(kst)
        hour = now.hour
        is_night_time = hour >= 21 or hour < 8
```

---

### 3.2 `app/src/main/java/com/ddaeany0919/insightdeal/core/NotificationService.kt` (Android DND 동적 로드 및 FCM 파싱 예외 가드)
- **분석 1 (맞춤 DND 동적 파싱)**: 기존 코드에서는 방해금지 시간 범위(21시 ~ 08시)가 앱 내에 하드코딩되어 있습니다. `EncryptedSharedPreferences`에서 `dnd_start_time` 및 `dnd_end_time`을 동적으로 읽고 범위 체크 헬퍼 함수를 통해 밤 시간대를 동적으로 판단하도록 리팩토링합니다.
- **분석 2 (FCM 파싱 예외 가드)**: `onMessageReceived` 메시지 수신부에서 `handleDataMessage`와 `showSmartNotification` 호출 시에 전체를 감싸는 try-catch를 추가하여 crash를 예방하고 메시지 파싱 안정성을 확보합니다.

* **Target Code Block 1 (Line 258-273)**:
```kotlin
        // ⏰ 야간 시간대 체크 (21:00 ~ 08:00)
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val isNightTime = hour >= 21 || hour < 8

        // 🔒 보안 스토리지를 통해 야간 수신 동의 여부 로드 (백그라운드 스레드 Keystore 예외 방지 Fallback 적용)
        val prefs = EncryptedPrefsManager.getEncryptedPrefs(applicationContext)
        val nightPushConsent = prefs.getBoolean("night_push_consent", false)

        if (isNightTime) {
            if (!nightPushConsent) {
                // 야간 수신 동의가 비활성화 상태이면 시스템 알림은 노출하지 않고 취소 (로컬 보관함에만 적재)
                Log.d(TAG, "🚫 야간 방해금지(21:00~08:00) 및 수신 비동의 상태로 시스템 알림 노출 스킵: $title")
                return
            }
        }
```

* **Proposed Changes 1**:
```kotlin
        // ⏰ 야간 시간대 체크
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // 🔒 보안 스토리지를 통해 야간 수신 동의 여부 로드 (백그라운드 스레드 Keystore 예외 방지 Fallback 적용)
        val prefs = EncryptedPrefsManager.getEncryptedPrefs(applicationContext)
        val nightPushConsent = prefs.getBoolean("night_push_consent", false)

        // ⚙️ EncryptedSharedPreferences를 통해 동적 DND 체크 적용 (기본값 21:00 ~ 08:00)
        val dndStartTime = prefs.getString("dnd_start_time", "21:00") ?: "21:00"
        val dndEndTime = prefs.getString("dnd_end_time", "08:00") ?: "08:00"
        val isNightTime = checkTimeInDndRange(hour, minute, dndStartTime, dndEndTime)

        if (isNightTime) {
            if (!nightPushConsent) {
                // 야간 수신 동의가 비활성화 상태이면 시스템 알림은 노출하지 않고 취소 (로컬 보관함에만 적재)
                Log.d(TAG, "🚫 야간 방해금지(동적: $dndStartTime ~ $dndEndTime) 및 수신 비동의 상태로 시스템 알림 노출 스킵: $title")
                return
            }
        }
```

* **Target Code Block 2 (Line 90-127)**:
```kotlin
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "📨 FCM 메시지 수신: ${remoteMessage.from}")

        // 📥 데이터 수신 즉시 로컬 보관함에 적재 연동
        val title = remoteMessage.notification?.title 
            ?: remoteMessage.data["title"] 
            ?: "InsightDeal 알림"
        val keyword = remoteMessage.data["keyword"] 
            ?: remoteMessage.data["category"] 
            ?: "핫딜"
        val dealUrl = remoteMessage.data["ecommerce_url"] 
            ?: remoteMessage.data["post_url"] 
            ?: "https://insightdeal.com"

        try {
            NotificationHistoryManager.init(applicationContext)
            NotificationHistoryManager.addAlert(applicationContext, title, keyword, dealUrl)
            Log.d(TAG, "✅ FCM 수신 알림 로컬 보관함 적재 성공")
        } catch (e: Exception) {
            Log.e(TAG, "❌ FCM 수신 알림 로컬 보관함 적재 중 에러", e)
        }

        // 데이터 페이로드 처리
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "📊 데이터 페이로드: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // 알림 페이로드 처리 (앱이 포그라운드일 때도 표시)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "🔔 알림 페이로드: ${notification.title}")
            showSmartNotification(
                title = notification.title ?: "InsightDeal",
                body = notification.body ?: "새로운 알림이 있습니다",
                data = remoteMessage.data
            )
        }
    }
```

* **Proposed Changes 2**:
```kotlin
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // 최외각 try-catch 예외 방어 가드로 파싱 실패나 예기치 못한 에러에 의한 앱 크래시 방어
        try {
            Log.d(TAG, "📨 FCM 메시지 수신: ${remoteMessage.from}")

            // 📥 데이터 수신 즉시 로컬 보관함에 적재 연동
            val title = remoteMessage.notification?.title 
                ?: remoteMessage.data["title"] 
                ?: "InsightDeal 알림"
            val keyword = remoteMessage.data["keyword"] 
                ?: remoteMessage.data["category"] 
                ?: "핫딜"
            val dealUrl = remoteMessage.data["ecommerce_url"] 
                ?: remoteMessage.data["post_url"] 
                ?: "https://insightdeal.com"

            try {
                NotificationHistoryManager.init(applicationContext)
                NotificationHistoryManager.addAlert(applicationContext, title, keyword, dealUrl)
                Log.d(TAG, "✅ FCM 수신 알림 로컬 보관함 적재 성공")
            } catch (e: Exception) {
                Log.e(TAG, "❌ FCM 수신 알림 로컬 보관함 적재 중 에러", e)
            }

            // 데이터 페이로드 처리
            if (remoteMessage.data.isNotEmpty()) {
                Log.d(TAG, "📊 데이터 페이로드: ${remoteMessage.data}")
                try {
                    handleDataMessage(remoteMessage.data)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ FCM 데이터 메시지 파싱 중 오류 발생 (크래시 가드): ${e.message}", e)
                }
            }

            // 알림 페이로드 처리 (앱이 포그라운드일 때도 표시)
            remoteMessage.notification?.let { notification ->
                Log.d(TAG, "🔔 알림 페이로드: ${notification.title}")
                try {
                    showSmartNotification(
                        title = notification.title ?: "InsightDeal",
                        body = notification.body ?: "새로운 알림이 있습니다",
                        data = remoteMessage.data
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ FCM 알림 노출 중 오류 발생 (크래시 가드): ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ FCM 수신 메시지 최외각 처리 실패 (크래시 예방 가드 작동): ${e.message}", e)
        }
    }

    // 동적 DND 체크를 위한 범위 매칭 헬퍼 메서드 추가
    private fun checkTimeInDndRange(currentHour: Int, currentMinute: Int, startTime: String, endTime: String): Boolean {
        return try {
            val startParts = startTime.split(":")
            val endParts = endTime.split(":")
            if (startParts.size != 2 || endParts.size != 2) return false
            
            val startH = startParts[0].toIntOrNull() ?: 21
            val startM = startParts[1].toIntOrNull() ?: 0
            val endH = endParts[0].toIntOrNull() ?: 8
            val endM = endParts[1].toIntOrNull() ?: 0
            
            val currentMinutes = currentHour * 60 + currentMinute
            val startMinutes = startH * 60 + startM
            val endMinutes = endH * 60 + endM
            
            if (startMinutes <= endMinutes) {
                currentMinutes in startMinutes..endMinutes
            } else {
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ DND 범위 파싱 오류: ${e.message}")
            // Fallback: 기존 정적 범위 21:00 ~ 08:00
            currentHour >= 21 || currentHour < 8
        }
    }
```

---

## 4. 종합 결론 및 권고사항

### 4.1 포트 8080 통일화를 위한 소스 교정 리스트
- [ ] `frontend-web/next.config.ts`: Line 11의 `backend:8000`을 `backend:8080`으로 변경.
- [ ] `backend/Dockerfile`: Line 48의 헬스체크 포트 `8000`과 Line 51의 uvicorn `--port 8000`을 모두 `8080`으로 변경.
- [ ] `backend/docker-compose.yml`: Line 9의 포트 포워딩 `"8000:8000"`을 `"8080:8080"`으로 변경.
- [ ] `backend/routers/community.py`: Line 98의 default `10.0.2.2:8000`을 `10.0.2.2:8080`으로 변경.
- [ ] `backend/fetch_grouped.py` & `backend/fetch_grouped_top.py`: 로컬 테스트 URL의 포트를 `8000`에서 `8080`으로 일괄 교체.

### 4.2 야간 컴플라이언스(DND) 및 예외 가드 보강 액션 플랜
- [ ] **백엔드 타임존 지정 가드**: `notification_service.py`에서 타임존 미지정 `datetime.datetime.now()`를 사용하는 대신, `pytz` 또는 `datetime.timezone(datetime.timedelta(hours=9))`을 적용한 KST 시간으로 명시적으로 시간대를 바인딩하여 시차 오류를 소거해야 합니다.
- [ ] **안드로이드 맞춤 DND 동적 파싱 구현**: `NotificationService.kt`에서 DND 판단 로직 구현 시 하드코딩된 `hour >= 21 || hour < 8`에만 기대지 말고, 기기에 보안 저장된 커스텀 방해금지 시간대(`dnd_start_time`, `dnd_end_time`)를 파싱하여 적용하도록 클라이언트 단의 시간대 로직을 동적으로 업데이트해야 합니다.
- [ ] **FCM 메시지 파싱 예외 처리 보강**: `onMessageReceived` 최외각에 예외 처리를 감싸 FCM 페이로드 구성 유효성 오류나 null 값 처리에 따른 앱의 예기치 못한 크래시를 원천 방어합니다.
