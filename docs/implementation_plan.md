# 🎨 [구현 계획서] InsightDeal 3단계 최종 통합 고도화 계획

본 구현 계획서는 **InsightDeal** 앱의 상용화 수준 완성도(토스, 29CM 급)를 달성하기 위해, 사용자가 지정하신 **4대 핵심 프리미엄 피처(수수료 고지 UI, 실시간 FCM 푸시 딥링크, UI/UX 터치 및 네트워크 감쇠 최적화, Paging 3 위시리스트)**를 유기적으로 백엔드와 안드로이드 클라이언트에 완벽 이식하는 설계 및 구현 계획을 명세합니다.

---

## 📌 4대 핵심 고도화 과제 및 아키텍처 개요

### 1. [법적 필수] 제휴 마케팅 수수료 우아한 고지 UI 도입 및 파트너스 도메인 필터링
*   **문제 상황**: 공정거래위원회 지침에 따라 쿠팡 파트너스, 알리 어소시에이트 등 제휴 수수료가 발생할 수 있는 외부 쇼핑몰로 이탈할 때 명확한 고지가 누락되면 법적 제재 대상이 됩니다. 또한, 모든 외부 링크 이탈 시 불필요하게 팝업이 뜨면 사용자 피로도가 급증합니다.
*   **해결 방안**:
    *   유저가 핫딜 상세 화면 또는 카드를 클릭해 외부 브라우저(또는 인앱 브라우저)로 아웃바운드될 때, Toss 감성의 프리미엄 **수수료 안내 BottomSheet**를 띄웁니다.
    *   **도메인 필터링**: 이탈하려는 외부 링크가 쿠팡(`coupang.com`)이나 알리익스프레스(`aliexpress.com`) 등 제휴 마케팅 대상 도메인일 경우에만 수수료 고지 BottomSheet가 안전하게 필터링되어 발동하도록 설계합니다. 일반 다른 커뮤니티나 쇼핑몰 이탈 시에는 팝업 없이 즉각 랜딩됩니다.
    *   **동적 맞춤 안내**: 수수료 안내 BottomSheet UI 상에 이탈하려는 도메인에 맞춰 "쿠팡 파트너스 활동의 일환으로..." 또는 "알리익스프레스 어소시에이트 수수료가..." 등의 안내 문구를 동적으로 출력해 법적 투명성을 완벽하게 충족합니다.
    *   **"오늘 하루 동안 보지 않기"** 체크박스 기능을 탑재하고, 이 상태를 `EncryptedSharedPreferences`에 동적으로 기록 및 대조하여 유저의 중복 노출 피로도를 극도로 낮춥니다.
    *   쿠팡 파트너스 ID나 알리 어소시에이트 ID 등 제휴 링크 포맷은 백엔드의 환경 변수(`.env`) 또는 설정 파일을 통해 동적으로 관리하여 유연성을 확보합니다.

### 2. [서비스 락인] 키워드 실시간 Rich FCM 푸시 알림 및 딥링크(Deep Link)
*   **문제 상황**: 백엔드에서 핫딜이 수집되어도 유저가 이를 즉시 인지하지 못하면 핫딜 플랫폼의 실시간 꿀통 가치가 훼손되고 재방문율(Retention)이 낮아집니다.
*   **해결 방안**:
    *   **딥링크(Deep Link) 탑재**: 알림창을 탭하면 앱의 메인 화면이 아닌, 해당 **핫딜 상품의 인앱 상세 분석 페이지**(`/deals/{deal_id}`)로 곧바로 직행(Redirect)하도록 딥링크 인텐트 필터를 클라이언트에 전격 이식합니다.
    *   **Rich Notification**: 이미지 썸네일 URL을 FCM 페이로드에 포함하여 수신 시 알림창 우측에 핫딜 이미지가 큼직하게 노출되도록 안드로이드 Notification 빌더를 고도화합니다.
    *   **백엔드 푸시 전송 고도화**: `push_worker.py` 및 `aggregator_service.py`에서 사용자가 등록한 관심 키워드(예: '아이폰', '맥북')가 수집된 핫딜와 밀리초 단위로 정확히 일치(Exact/Regex Match)하는지 캐싱 매핑하여 FCM 발송 요청을 처리합니다.

### 3. [UI/UX 디테일] Scroll-Aware 터치 모션 조율 및 네트워크 Flapping 디바운스 이식
*   **문제 상황**:
    1.  **스크롤 모션 충돌**: 카드를 꾹 누른 채로(터치) 리스트를 아래로 스크롤할 때, 카드가 작아진 상태(`0.97f`)로 굳어진 채 끌려다녀 시각적인 뻣뻣함을 유발합니다.
    2.  **네트워크 플래핑(Flapping)**: 1초 이내에 인터넷이 붙었다 끊기기를 무한 반복하는 음영 지역 진입 시, 감지 센서가 수시로 활성화되어 상단 배너가 깜빡거리며 컴포저블을 덜덜 떨게 만듭니다.
*   **해결 방안**:
    *   **Scroll-Aware bounceClick Modifier**: `pointerInput` 제스처 터치 인지 체인 내에 드래그 변위 감지기를 내장합니다. 유저가 카드를 터치한 후 이동(Drag) 거리가 스크롤 임계점을 넘는 즉시 바운스 스케일을 즉각 원래대로(`1.0f`) 복구시키고 클릭 동작을 부드럽게 Cancel 처리합니다.
    *   **Network Debounce 스트림**: `NetworkMonitor`의 StateFlow 변화 주기에 `debounce(1500)` 필터를 걸어 1.5초 이내의 단기적인 네트워크 껌뻑임 현상을 무시함으로써 레이아웃의 무한 리포지셔닝 떨림 현상을 원천 방어합니다.

### 4. [위시리스트 고도화] Room DB와 Paging 3를 결합한 초고성능 오프라인 우선 위시리스트
*   **문제 상황**: 사용자가 찜한 목록(위시리스트)이 수백 개 이상으로 폭증할 때 일괄 쿼리로 데이터를 로딩하면 앱 메모리가 한계에 도달하고 스크롤 렉이 극심해집니다.
*   **해결 방안**:
    *   **Wishlist Database**: `WishlistEntity` [NEW] 테이블과 `WishlistDao` [NEW]를 설계하여 유저의 로컬 찜 데이터를 영구 보관합니다.
    *   **Paging 3 결합**: 위시리스트 로딩 데이터 파이프라인을 `PagingSource`로 추상화하여, Room DB로부터 무한 스크롤 환경에서 20개 단위로 데이터를 안전하고 매끄럽게 페이징 로딩(Paging 3)함으로써 60fps/120fps Jank-Free 오프라인 UX를 이식합니다.

---

## 🛠️ Proposed Changes (제안된 변경 내역)

### 💻 안드로이드 클라이언트 컴포넌트

#### [NEW] [WishlistDao.kt](file:///C:/Users/kth00/StudioProjects/InsightDeal/app/src/main/java/com/ddaeany0919/insightdeal/local/db/WishlistDao.kt)
*   위시리스트 추가, 제거, 페이징 조회를 위한 `PagingSource<Int, DealEntity>`를 반환하는 Room DAO 구현.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/kth00/StudioProjects/InsightDeal/app/src/main/java/com/ddaeany0919/insightdeal/presentation/home/HomeScreen.kt)
*   핫딜 클릭 시 브라우저 랜딩 직전 **제휴 마케팅 수수료 안내 BottomSheet** 디자인 및 24시간 쿠키 스킵 제어 바인딩.
*   `bounceClick` Modifier 내에 드래그 제스처 캔슬러를 심어 스크롤 드래그 시 스케일을 1.0f로 자동 복원하는 로직 주입.

#### [MODIFY] [NotificationService.kt](file:///C:/Users/kth00/StudioProjects/InsightDeal/app/src/main/java/com/ddaeany0919/insightdeal/core/NotificationService.kt)
*   FCM 알림 페이로드에서 `image_url`을 추출해 Rich Notification(BigPictureStyle) 빌드.
*   알림 탭 시 `deal_id`를 담아 상세 뷰로 랜딩시키는 인텐트 딥링크 세팅.

#### [MODIFY] [NetworkMonitor.kt](file:///C:/Users/kth00/StudioProjects/InsightDeal/app/src/main/java/com/ddaeany0919/insightdeal/core/network/NetworkMonitor.kt)
*   `callbackFlow` 스트림 하단에 `debounce` 스트림 튜너를 장착하여 네트워크 플래핑을 방지한 감쇠 필터링 구현.

---

## 🧪 Verification Plan (검증 계획)

### 1. 컴파일 빌드 검증
*   `.\gradlew compileDebugKotlin`를 실행하여 딥링크 인텐트, Room 페이징, 바운스 클릭 제스처가 경고나 컴파일 빌드 실패 없이 무결한지 검증합니다.

### 2. 수동 및 감각적 검증
*   **수수료 BottomSheet**: 핫딜 카드를 누르면 Toss 느낌의 수수료 버텀시트가 미려하게 뜨고, "오늘 하루 안 보기" 체크 시 24시간 동안 완벽하게 생략되는지 검증합니다.
*   **Scroll-Aware Touch**: 카드를 꾹 누른 상태에서 스크롤을 시도할 때, 카드가 즉각 `1.0f`로 부드럽게 펴지며 스크롤이 이뤄지는지 시각적으로 검증합니다.
*   **Paging 3 위시리스트**: 찜 목록에 100개 이상의 아이템을 임의 등록한 뒤 위시리스트 화면을 끊김 없이 부드럽게 무한 스크롤할 수 있는지 감각적으로 검증합니다.
