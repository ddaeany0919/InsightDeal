# 📱 InsightDeal Product Specification (초정밀 기능 사양서)

본 문서는 InsightDeal 안드로이드 애플리케이션의 탭별 상세 UI 구성, 데이터 흐름, 컴포넌트 아키텍처, 백엔드 API 명세 및 세부 비즈니스 로직을 코드 레벨 수준으로 초정밀하게 정의한 PRD(Product Requirements Document)입니다.

---

## 1. 🏠 홈 (Home) - `HomeScreen.kt` / `HomeViewModel.kt`
실시간으로 업데이트되는 전체 핫딜 피드를 무한 스크롤(Paging)로 제공하는 메인 화면.

### 1.1 주요 UI 및 상태 관리
*   **상태 관리 (StateFlow)**: `HomeViewModel` 내 `dealsPagingData` (`Flow<PagingData<HotDealDto>>`)를 통해 데이터 스트림을 관리. 화면 회전(Configuration Change) 시에도 `cachedIn(viewModelScope)`를 통해 스크롤 위치 보존.
*   **시간 포매팅 초정밀 최적화 (`StringUtils.kt`)**: 
    *   `GlobalTimeTicker`: 아이템마다 타이머를 생성하여 메모리가 누수되는 것을 방지하기 위해, 싱글톤 코루틴에서 현재 시간 기준 다음 정각(0초)까지의 지연 시간(`millisUntilNextMinute`)을 계산하여 정확하게 1분 단위로 `tick`(`System.currentTimeMillis()`)을 발생시킴.
    *   `rememberRelativeTime`: 이 전역 틱(상태)을 구독하여 "방금 전", "X분 전", "X시간 전" 등의 텍스트를 실제 시간 흐름에 맞춰 오차 없이 리컴포지션함.
*   **플랫폼 명칭 정규화**: `getPlatformDisplayName()`을 통해 `gmarket` -> `G마켓`, `ppomppu` -> `뽐뿌` 등 8가지 이상의 영문 플랫폼 ID를 한글명으로 맵핑하여 UI에 표시.
*   **감성적 핫딜 추천 뱃지 [수정: 오탐지 방지 이중 조건 강화]**: `honeyScore` 기반의 모호한 통계 라벨링 대신 직관적인 감성 문구를 사용. `🔥` 뱃지(`isSuperHot`) 표시 기준을 **이중 조건(AND)**으로 강화 — `aiSummary`에 `"🔥 [커뮤니티 인기]"` 또는 `"🔥 [커뮤니티 인증 핫딜]"` 태그가 포함되어 있으면서 동시에 `honeyScore == 100`인 경우에만 뱃지 표시. `honeyScore >= 95` 단독 조건으로 발생하던 일반 게시글 오탐지 버그 완전 수정. 상단 "오늘의 핫딜" 캐러셀은 별도로 `honeyScore >= 100` 조건만 사용(커뮤니티 인기글 캐러셀 전용).

### 1.2 딜 상세 화면 (`DealDetailScreen.kt`)
홈이나 다른 탭에서 핫딜 카드를 클릭했을 때 진입하는 상세 뷰.
*   **프리미엄 AI 구매 타점 분석 (`AIBuyerGuide`) [수정: 가격 데이터 부족 시 DB 기반 폴백 분석 지원]**: 가격 히스토리 데이터(`PriceHistoryPoint`)를 기반으로 리포트 형태 UI 렌더링. 히스토리가 짧을 경우, DB의 카테고리 평균 가격 정보를 토대로 산출된 `honeyScore`를 활용해 "💡 AI가 분석한 카테고리 내 가치 점수" 기반의 구매 가이드를 제공하여 빈약한 UI를 방지. 역대 최저가 갱신 시 녹색/붉은색 강조, `Insights` 아이콘 적용, 고점 대비 할인 금액 표기 등 시각적 효과를 극대화해 강력한 매수 타이밍 안내.
*   **가격 변동 차트 (`PriceHistoryInteractiveCard`) [수정: 데이터 부족 시 안내 뷰 추가]**: 히스토리 데이터가 3개 이상일 때 활성화. 데이터가 2개 이하일 경우 "데이터 수집 중"이라는 친화적 안내 UI 노출. `PriceChart` 커스텀 컴포넌트를 호출하여 200dp 높이의 라인 차트를 그림. 터치 시 상세 금액 노출.
*   **최저가 비교 테이블 (`MallPriceTable`)**: 여러 웹 판매처의 최저가를 KRW, USD, EUR 등의 통화(`formatPrice`)에 맞춰 변환하여 리스트업하고 '바로가기' 버튼 제공.
*   **목표가 알림 (`PriceAlertRegistrationButton`)**: 현재 가격 기준 -5%(`currentPrice * 0.95`)를 기본값으로, 사용자가 원하는 목표 가격 도달 시 푸시를 받도록 다이얼로그 제공.
*   **UI 컬러링**: 출처별 테마 컬러 강제 적용 (예: 뽐뿌=파란색 `#1565C0`, 퀘이사존=주황색 `#E65100`, 루리웹=남색 `#0D47A1`).

### 1.3 🔄 오프라인 우선(Offline-First) Room + Paging 3 아키텍처
불안정한 네트워크 환경이나 오프라인 상태에서도 끊김 없는 탐색 환경을 보장하기 위해 로컬 캐시를 단일 진실 공급원(Single Source of Truth)으로 삼는 오프라인 캐싱 파이프라인을 구축했습니다.
*   **Room DB 스키마 다변화**:
    *   `DealEntity` (테이블명 `deals`): 크롤러로 수집된 핫딜의 제목, AI 요약, 원가, 할인가, 카테고리, 플랫폼 및 시간 메타데이터를 저장하는 영속성 데이터 모델.
    *   `DealRemoteKeysEntity` (테이블명 `deal_remote_keys`): 무한 스크롤 페이징에서 다음 페이지 오프셋과 키 포인터를 추적하기 위한 페이징 전용 테이블.
    *   **정밀한 메모리 관리 (`Converters.kt`)**: Paging 3 무한 스크롤 시 빈번한 JSON 직렬화/역직렬화 과정에서 쓰레기 객체 생성으로 인한 GC(가비지 컬렉터) 부하 및 렌더링 렉(Jank) 현상을 방지하기 위해, 내부 Gson 인스턴스를 static companion object 싱글톤으로 고정 바인딩하여 런타임 메모리 효율성을 최대화했습니다.
*   **원격 및 로컬 오케스트레이터 (`HotDealsRemoteMediator.kt`)**:
    *   `RemoteMediator<Int, DealEntity>`를 구현하여 네트워크에서 새 핫딜을 페칭하는 즉시 로컬 Room DB에 트랜잭션 단위로 적재(Upsert/Clear)하고, 페이징 컴포넌트(`PagingSource`)는 오직 로컬 DB만 바라보게 설계했습니다.
    *   네트워크 응답 성공 시 페이징 상태에 따라 `MediatorResult.Success(endOfPaginationReached)`를 반환하고, 예외 발생 시 `MediatorResult.Error`로 깔끔하게 래핑하여 예외를 상위로 전파합니다.
*   **HomeViewModel 데이터 흐름 단일화**:
    *   `Pager(config, remoteMediator) { dao.getPagingSource() }.flow.cachedIn(viewModelScope)`를 적용하여 데이터의 Single Source of Truth 아키텍처를 견고히 확립했습니다.

### 1.4 📶 실시간 네트워크 감지 및 슬라이딩 배너 UX (`NetworkMonitor.kt` / `HomeScreen.kt`)
*   **실시간 감지기 (`NetworkMonitor.kt`)**:
    *   `ConnectivityManager`의 상태 콜백 리스너를 코루틴 `callbackFlow`로 래핑하여, 네트워크 유효성 상태(`NetworkCapabilities`)가 변경될 때마다 실시간 `Flow<Boolean>` 스트림으로 전파되도록 구현했습니다.
*   **슬라이딩 경고 배너**:
    *   인터넷 연결이 단절되었음을 실시간 감지하면, `HomeScreen` 최상단에 미려한 페이드인/아웃 슬라이딩 애니메이션 효과와 함께 **"⚠️ 현재 오프라인 상태입니다. 로컬 캐시된 핫딜을 표시하고 있습니다."** 라는 브랜드 오렌지 톤(Color(0xFFFF9500))의 경고 배너가 나타납니다.
    *   단순한 토스트 팝업을 넘어서 사용자가 현재 표시된 데이터의 신뢰성 상태를 즉각 직관적으로 인지할 수 있도록 상용 premium 등급의 마이크로 UX 디테일을 구현했습니다.

---

## 2. 🔍 검색 (Search) - `AdvancedSearchScreen.kt`
*   **한글 IME 최적화**: 텍스트필드 상태를 `String`이 아닌 `TextFieldValue`로 유지하여 자음/모음 조합 시(`우` -> `우유`) 깜빡임이나 백스페이스 씹힘 현상 방지.
*   **Debounce 로직**: `snapshotFlow`를 생성 후 `debounce(300)` 적용. 사용자가 입력을 멈추고 0.3초가 지나면 검색 쿼리 실행.
*   **검색 API 연동**: 
    *   Endpoint: `GET /deals/search`
    *   Params: `q`(검색어), `page`, `limit`, `min_price`, `max_price`, `category`, `site`, `sort`(기본값: relevance).

---

## 3. 🤖 AI 개인화 추천 탭 (Recommendation) - `RecommendationViewModel.kt`
InsightDeal의 핵심 지능형 추천 엔진.

### 3.1 추천 알고리즘 종류 (`PersonalizationEngine`)
*   **협업 필터링 (Collaborative Filtering)**: `findSimilarUserRecommendations()`를 통해 유사한 패턴을 가진 다른 사용자가 클릭한 딜 추천.
*   **콘텐츠 기반 (Content-Based)**: `getContentBasedRecommendations()`를 통해 내가 클릭했던 카테고리(IT, 패션 등)나 브랜드(삼성, 애플 등)와 텍스트 유사도가 높은 항목 추천.
*   **하이브리드 추천 (`getHybridRecommendations`)**: 위 두 가지 추천 방식과 유저 피드백 데이터를 합쳐 `distinctBy`로 중복을 제거하고 `shuffled().take(30)`로 최종 30개의 개인화 딜 서빙.

### 3.2 카테고리 & 브랜드 파서 (자연어 추출)
*   `extractCategoryFromTitle()`: 제목 내 "갤럭시, 그래픽카드" -> `IT`, "향수, 의류" -> `해외` 등으로 자체 분류.
*   `extractBrandFromTitle()`: "samsung", "애플", "다이슨" 등의 정규화 키워드로 브랜드 매핑.

### 3.3 사용자 상호작용 추적 (Tracking)
*   `trackInteraction()`: 사용자가 카드를 클릭하거나 체류한 시간(`InteractionType`)을 기록. 누적 횟수 및 프로필 완성도를 `PersonalizationInsights` 모델로 관리.

---

## 4. 🌐 커뮤니티 (Community) - `CommunityScreen.kt` & `CommunityBoardScreen.kt`
다양한 커뮤니티 성향 필터 및 양방향 UGC(User-Generated Content) 소통 공간.

### 4.1 필터링 전략 (스크래핑 핫딜)
*   **"🔥 핫딜만"**: `category != "적립" && category != "이벤트"` (물리적 상품만).
*   **"💰 앱테크만"**: `category == "적립" || category == "이벤트"` (무료 혜택, 포인트 적립 등).
*   **UI/UX**: 전체 핫딜을 로드한 뒤 탭 인덱스(`selectedTabIndex`)에 따라 `state.deals.filter`를 수행하여, 네트워크 재요청 없이 즉각적인 탭 전환 제공.
*   **배송비 처리**: 상품 가격이 `0`인 경우 "이벤트" 카테고리이거나 제목에 "무료/쿠폰"이 있으면 "무료", 그 외는 "확인필요".

### 4.2 사용자 참여형 커뮤니티 게시판 (UGC)
*   **핫딜 역경매 (REQUEST)**: 유저가 원하는 상품의 예산과 조건을 올리면(예: "10만원 이하 모니터"), 다른 고인물 유저들이 링크를 찾아주고 `bounty_points`(꿀점수)를 받아가는 지식인 스타일 게시판. 채택 시 `is_resolved=True` 전환.
*   **오프라인 핫딜 (OFFLINE)**: 온라인 스크래퍼가 긁어오지 못하는 동네 오프라인 마감세일 정보를 제보하는 발품 전용 탭 (`location` 필드 활용).
*   **API & 데이터 구조**: `CommunityPost`, `CommunityPostComment` DB를 통해 사용자 `nickname` 및 타임스탬프(`created_at`)가 뷰모델(`CommunityBoardViewModel`)을 거쳐 실시간 렌더링.

---

## 5. 🔐 회원가입 및 세션 인프라 (Auth)
*   **백엔드 인증 구조**: FastAPI `/api/auth/signup`, `/login` 제공. 비밀번호는 `hashlib.sha256`로 암호화 후 `User` 테이블에 적재. 유저의 `honey_points`(꿀점수) 및 `nickname` 고유성 보장.
*   **모바일 로컬 세션**: 구글 공식 권장 `DataStore(Preferences)` 기반의 `AuthManager.kt`를 통해 로그인한 유저 세션 정보를 영구 보존. 커뮤니티 글 작성 시 자동 바인딩.

### 5.2 🛡️ 하드웨어 수준의 로컬 보안 암호화 인프라 (`EncryptedPrefsManager.kt`)
개인 정보 보호 및 안전한 앱 보안을 위해 로컬에 저장되는 민감 정보를 하드웨어 수준에서 암호화 관리하도록 설계했습니다.
*   **Keystore 기반 AES-256-GCM 암호화**:
    *   `androidx.security:security-crypto:1.1.0-alpha06` 라이브러리를 도입하여, Android Keystore 시스템에 대칭키 방식의 마스터 키(`MasterKey.Builder`)를 생성하고 파일명과 콘텐츠를 AES-256 규격으로 무중단 실시간 암호화 처리합니다.
*   **백그라운드 런타임 Fallback 예외 대응 아키텍처**:
    *   기기가 완전히 잠겨있거나 백그라운드 서비스 및 수신기(`MyFirebaseMessagingService` 등)에서 Keystore 마스터 키에 접근할 수 없는 특수한 안드로이드 플랫폼 런타임 예외(`KeyStoreException`, `GeneralSecurityException`) 발생 시 크래시가 유발되는 문제를 해결하기 위해 **안전한 Fallback 아키텍처**를 구현했습니다.
    *   Keystore 로드 예외 감지 시 즉각 일반 SharedPreferences 영역으로 대체하여 데이터를 안전하게 저장/로드하도록 유연한 싱글톤 빌더 구조를 구축했습니다.
*   **보안 저장소로의 전면적인 마이그레이션**:
    *   앱 잠금 여부 및 비밀번호 값(`app_lock_enabled`, `app_lock_pin`), 야간 DND 알림 수신 동의 여부(`night_push_consent`) 등의 핵심 제어 설정 값을 읽고 쓰는 주체를 기존 일반 SharedPreferences에서 `EncryptedPrefsManager.getEncryptedPrefs(context)`로 일괄 완전 전환했습니다.

---

## 6. 👤 내정보 (My Info) - `MyPageScreen.kt`
개인화된 활동 내역과 푸시 알림 인프라 제어.

### 6.1 FCM 푸시 & 알림 인프라 (`ApiService` 연동)
*   **기기 등록 (`POST /api/push/device`)**: 익명 가입 시 `device_uuid`와 `fcm_token`을 백엔드로 전송하여 알림 구독 테이블 생성.
*   **키워드 알림 (`POST /api/push/keywords`)**: "아이패드" 등을 등록 시 DB에 매핑되어 크롤러가 해당 텍스트 감지 시 백그라운드 푸시 전송.
*   **위시리스트 (찜목록)**: `POST /api/wishlist/from-keyword`, `GET /api/wishlist/{wishlist_id}/history` 등 전용 Wishlist API를 통해 관심 상품의 지속적인 가격 추적 및 변동 알림(Alarm State) 토글 기능 제공.

### 6.2 🔔 스마트 FCM 푸시 파이프라인 및 야간 DND 무음 처리 (`NotificationService.kt`)
사용자의 수면 및 일상 피로도를 보호하면서도 놓치기 아쉬운 핫딜 정보를 안전하게 배달하기 위한 고도화된 알림 수신 파이프라인입니다.
*   **실시간 수신 및 무중단 알림 보관함 적재**:
    *   `InsightDealFirebaseMessagingService` 백그라운드 서비스를 가동하여 앱이 종료된 상태에서도 실시간으로 푸시 토큰 및 페이로드를 안정적으로 수집합니다.
    *   푸시가 도착하면 기기의 네트워크나 활성 상태와 상관없이, 하드웨어 보안 저장소 기반으로 구동되는 **로컬 알림 보관함(`NotificationHistoryManager.kt`)**에 즉시 암호화 적재를 수행합니다.
*   **야간 방해금지 (DND, 21:00 ~ 08:00) 스마트 필터링**:
    *   정보통신망법 규정을 철저히 준수하고 사용자 경험을 배려하기 위해, 푸시 알림 수신 시 디바이스 로컬 시간을 기준으로 야간(21:00 ~ 익일 08:00) 시간대를 정밀 판별합니다.
    *   사용자의 야간 알림 수신 동의 상태(`night_push_consent`)에 따라 동작을 스마트 분기합니다:
        1.  **DND 활성화 상태 (수신 거부)**: 시스템 헤드업 알림 및 소리/진동을 원천적으로 발생시키지 않고, 조용히 로컬 알림 보관함(`NotificationHistoryManager`)에만 적재합니다.
        2.  **DND 비활성화 상태 (수신 동의)**: 소리와 진동을 원천 배제한 **무음 전용 알림 채널 (`silent_hotdeal_notifications`)**로 즉시 전환 바인딩하여 백그라운드 노티피케이션을 노출시킵니다.
    *   이를 통해 푸시 알림 피로도를 획기적으로 경감시켜 사용자가 앱을 이탈하거나 알림 자체를 전면 차단하는 심각한 보안 및 운영 악순환을 사전에 방지했습니다.

---

## 🛠️ 6. 프론트엔드 네트워크 통신 아키텍처 - `NetworkModule.kt`

### 6.1 타임아웃 및 인터셉터 설정
*   **타임아웃 분배**: 커넥션, 리드, 라이트 타임아웃을 각각 **120초**로 길게 설정. 이는 백엔드(Python)에서 AI 파싱 로직(`Gemini`)이 가동될 때 응답 시간이 길어지는 상황을 고려한 예외 처리.
*   **로깅 & 헤더**: `HttpLoggingInterceptor` 적용 및 모든 요청에 `User-Agent: InsightDeal-Android/1.0` 주입.

---

## 🕸️ 7. 인앱 브라우저 (WebView) 심화 로직 - `DealWebViewScreen.kt`

### 7.1 봇 차단 방어막(WAF) 완벽 우회 (User-Agent 변조)
*   네이버 스마트스토어, 클라우드플레어 등 봇/스크래퍼를 튕겨내는 쇼핑몰을 무사히 열기 위해 **정상 모바일 크롬 브라우저 UA**(`Mozilla/5.0 (Linux; Android 14; SM-S928N)...`)를 강제로 주입.
*   `bbasak.com` 같은 특정 폐쇄몰의 경우 **PC 버전 브라우저 UA**(`Windows NT 10.0; Win64; x64...`)로 런타임에 분기 처리.
*   **Referer 변조**: 네이버 쇼핑몰 접근 시 `Referer` 헤더를 `https://search.naver.com`으로 조작하여 자연스러운 검색 유입으로 위장.

### 7.2 앱 스키마 (intent://) 핸들링
*   `shouldOverrideUrlLoading`을 오버라이딩하여, 쇼핑몰 결제창이나 앱카드 구동 시 발생하는 `intent://` 프로토콜을 `Intent.parseUri`로 변환하여 로컬 기기에 설치된 앱(예: 카드사 앱, 네이버페이 앱)이 정상적으로 뜨도록 분기.

---

## 🧠 8. 하이브리드 어그리게이터 (Backend Aggregation) - `aggregator_service.py`
InsightDeal의 심장부로, 크롤링된 가공되지 않은 데이터를 완벽한 핫딜 정보로 탈바꿈시키는 계층.

### 8.1 스팸/어뷰징 게시판 뻘글 필터링
*   `spam_keywords` 리스트("공지", "질문", "몰테일", "바이럴", "활동내역" 등)를 사용하여 핫딜 게시판에 올라오는 질문 글이나 운영자 공지를 원천 차단하여 DB 오염을 막음.

### 8.2 AI 기반 다중 핫딜 분할 엔진 (Gemini 2.5 Flash)
*   **분할 트리거 (Token Saving)**: 제목의 온점/쉼표 갯수(`count(".")`)와 본문의 금액 정규식 패턴(`[0-9]+원`) 매칭 수를 비교. 1개의 글에 여러 상품이 올려진 "모음전" 형태이거나 가격 추출에 완전히 실패한 경우에만 AI 작동.
*   **외화 자동 변환**: 프롬프트 규칙을 통해 `$` 또는 `€` 표시의 해외 직구 가격을 원화(KRW)로 자동 환산하여 파싱.
*   **할인가 계산기**: "40,000원 정가 75% 할인"과 같은 자연어에서 LLM이 직접 수식을 계산하여 최종 가격 `10,000`을 추출하도록 강제.

### 8.3 메타태그 스나이핑 로직
*   본문 정규식이나 AI로도 가격 파싱에 실패했을 때 (`final_price == 0` 이고 `ecommerce_link`가 존재하는 경우), 
    해당 쇼핑몰 링크로 직접 침투(`urllib.request`)하여 `og:price:amount` 또는 `product:price:amount` 메타 태그를 읽어와 숨겨진 최종 결제 금액을 탈취함 (단, 쿠팡 등 방어가 강한 몰은 제외).

### 8.4 꿀딜(Honey Score) 동적 점수 알고리즘 [수정: 카테고리 DB 평균가 비교 로직 도입]
*   **가격 메리트 분석**: DB 내 동일 카테고리 평균 가격 데이터를 실시간으로 조회해, 현재 핫딜가(`final_price`)와의 할인율 격차를 점수(`honey_score`)에 동적으로 반영. 기존 휴리스틱 난수 의존 방식을 탈피하여 데이터 기반의 정밀한 "AI 가격 메리트" 지표 완성.
*   `조회수 / 100 + (좋아요 * 10) + (댓글수 * 5)` 공식을 사용하여 `honey_score` 기본 계산.
*   특정 커뮤니티의 기준을 넘은 글(`is_super_hotdeal == True`)은 알고리즘 점수와 무관하게 꿀딜 점수를 100점으로 강제 세팅.
*   가격이 0원 초과인데 점수가 너무 낮게 측정된 경우 과거 난수(`random.randint(50, 70)`)를 부여했으나, 현재는 카테고리 평균가 대비 할인율 로직으로 고도화되어 보다 유의미한 점수 도출 보장.

### 8.5 커뮤니티별 `is_super_hotdeal` 판별 기준 [신규 명세]
각 스크래퍼가 독립적으로 커뮤니티 자체 기준으로 `is_super_hotdeal` 플래그를 설정하며, `aggregator_service.py`에서 이 플래그가 `True`일 때 `honey_score=100`과 `🔥 [커뮤니티 인기]` ai_summary 태그를 자동 부여.

| 커뮤니티 | 판별 기준 |
|---|---|
| 뽐뿌 | 게시글 행에 `hot_icon2.jpg` 이미지 존재 여부 (공식 HOT 마크) |
| 루리웹 | 게시글 `<tr>` 클래스에 `best` 포함 |
| 퀘이사존 | `.label`에 "인기" 텍스트 포함 OR 좋아요 수 ≥ 20 |
| 에펨코리아 | 포텐 게시글(`is_poten=True`) |
| 클리앙 | 공감수(`symph`) ≥ 10 |
| 빠삭 | 조회수 ≥ 2,500 |
| 뽐뿌해외 | 뽐뿌 기준 상속 (hot_icon2.jpg) |

**⚠️ 뽐뿌 `pmarket` 게시판 오염 방지**: 뽐뿌 핫딜 목록(`ppomppu`, `ppomppu4`, `ppomppu8`) 외 다른 게시판(예: `pmarket` 자유/쇼핑 게시판)의 게시글은 `board_id` 화이트리스트 필터로 수집 단계에서 원천 차단. 과거 유입된 데이터는 `🔥` 태그 및 100점 소급 정정 완료.

---

## 9. 데이터 파이프라인 및 개발 환경 관리 (Operations & Scraper)

### 9.1 스크래퍼 정교화 및 고품질 데이터 확보
*   **FmkoreaScraper (펨코) [수정: 다중 본문 블록 통합 파싱]**: 일반 게시판 스크래핑 외에도, 핫딜 페이지(`https://www.fmkorea.com/hotdeal`)를 타겟팅. 펨코의 다중 `.xe_content` 구조(복수 본문 블록 존재) 특성상 첫 번째 블록만 파싱하던 버그를 수정하여, **모든 본문 블록을 병합**하고 `<a>` 태그의 링크 정보를 보존하도록 로직 고도화. AI 하위 분할(split) 시 누락 상품 방지 효과. `&sort_index=pop&order_type=desc` 파라미터를 활용한 '인기/포텐' 필터링 URL 지원.
*   **PpomppuScraper (뽐뿌) [수정: 게시판 화이트리스트 필터 강화]**: `hot_icon2.jpg` 기반 핫딜 마크 감지 유지. 수집 URL의 `board_id`를 `ppomppu`, `ppomppu4`, `ppomppu8`로만 제한하는 화이트리스트 필터 운영. `pmarket`(뽐뿌 자유게시판) 등 비핫딜 게시판 게시글이 추천글 형태로 핫딜 리스트에 혼입되는 현상 원천 차단.
*   **모음전(다중상품) Fallback 처리 [수정]**: AI 분할 엔진이 작동하지 않을 때 기존 `🔥 [모음전]` fallback 요약 텍스트가 핫딜 뱃지를 오발동하던 문제 수정. fallback 접두사를 `📦 [모음전]`으로 변경하여 🔥 이모지 기반 오탐지 완전 차단.
*   **엄격한 필터링**: 메타데이터 상 `is_super_hotdeal` 정보가 없거나 점수가 낮은 무의미한 게시물은 파이프라인 단에서 즉각 `None` 처리하여 DB 적재를 원천 차단.

### 9.2 프로젝트 폴더 아키텍처 및 IDE 환경 세팅
*   **루트 디렉토리 청결화**: 크롤링 과정에서 발생한 HTML 덤프나 테스트 임시 파일(`*.html`, `*.txt` 등)은 `agent_workspace/` 하위로 격리 보관.
*   **IDE 안전 숨김 처리**: 안드로이드 스튜디오 환경을 고려하여, `.gemini`, `.bkit`, `.idea` 등 시스템 및 에이전트 구동에 필수적인 설정 폴더들은 물리적 이동을 금지하고, IDE 내부의 `Ignored Files and Folders` 세팅으로 시각적으로만 가려 빌드 및 에이전트 안정성을 보장.
