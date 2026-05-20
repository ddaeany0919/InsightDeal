# InsightDeal Project Component Inventory

## 📱 Android App Components
### ViewModels
- presentation\DealDetailViewModel.kt
- presentation\KeywordManagerViewModel.kt
- presentation\alerts\AlertsViewModel.kt
- presentation\community\CommunityViewModel.kt
- presentation\home\HomeViewModel.kt
- presentation\price\PriceChartViewModel.kt
- presentation\price\PriceViewModel.kt
- presentation\recommendation\RecommendationViewModel.kt
- presentation\search\AdvancedSearchViewModel.kt
- presentation\tracking\CoupangTrackingViewModel.kt
- presentation\wishlist\WishlistViewModel.kt

### Screens & Activities
- MainActivity.kt
- presentation\DealDetailScreen.kt
- presentation\KeywordManagerScreen.kt
- presentation\alerts\AlertsScreen.kt
- presentation\bookmark\BookmarkScreen.kt
- presentation\category\CategoryScreen.kt
- presentation\community\CommunityScreen.kt
- presentation\home\HomeScreen.kt
- presentation\mypage\MyPageScreen.kt
- presentation\platform\PlatformScreen.kt
- presentation\price\PriceGraphScreen.kt
- presentation\recommendation\RecommendationScreen.kt
- presentation\search\AdvancedSearchScreen.kt
- presentation\settings\SettingsScreen.kt
- presentation\settings\ThemeSettingsScreen.kt
- presentation\settings\ThemeSettingsScreenCollapsible.kt
- presentation\wishlist\WishlistDetailScreen.kt
- presentation\wishlist\WishlistScreen.kt

### Services (Core/Background)
- core\NotificationService.kt
- core\InsightDealFirebaseMessagingService.kt
- core\security\EncryptedPrefsManager.kt
- core\network\NetworkMonitor.kt
- presentation\home\HotDealsRemoteMediator.kt
- data\PriceComparisonService.kt

### Network/API
- data\network\NetworkConfig.kt
- data\network\WishlistApiService.kt
- network\ApiService.kt
- network\DealsApiService.kt
- network\HealthResponse.kt
- network\NaverShoppingApiService.kt
- network\NetworkModule.kt

## ⚙️ Backend Components
### Routers (API Endpoints)
- routers\community.py
- routers\health.py
- routers\product.py
- routers\push.py
- routers\wishlist.py

### Services (Business Logic)
- services\aggregator_service.py
- services\ai_product_name_service.py
- services\coupang_api_client.py
- services\coupang_selenium_scraper.py
- services\coupang_web_scraper.py
- services\price_comparison_service.py
- services\product_analyzer_service.py
- services\product_scraper_factory.py
- services\product_scraper_interface.py
- services\url_product_extractor.py
- services\wishlist_service.py
- services\normalizer\base.py
- services\normalizer\llm_normalizer.py
- services\normalizer\regex_normalizer.py

### Scrapers
- scrapers\alippomppu_scraper.py
- scrapers\base_scraper.py
- scrapers\bbasak_base_scraper.py
- scrapers\bbasak_domestic_scraper.py
- scrapers\bbasak_overseas_scraper.py
- scrapers\bbasak_parenting_scraper.py
- scrapers\clien_scraper.py
- scrapers\fmkorea_scraper.py
- scrapers\http_client.py
- scrapers\naver_shopping_scraper.py
- scrapers\ppomppu_overseas_scraper.py
- scrapers\ppomppu_scraper.py
- scrapers\quasarzone_scraper.py
- scrapers\ruliweb_scraper.py

### Models (Database/Schemas)
- models\models_v2.py
- models\product_models.py
- models\wishlist_models.py

---

## 🛠️ 2단계 고도화 컴파일 트러블슈팅 이력 (Technical Issue Log)

2단계 고도화 진행 과정 중 발생했던 주요 컴파일, 링킹 및 런타임 플랫폼 이슈들과 이에 대한 최종 해결(Action) 내역을 기록한 엔지니어링 분석 로그입니다.

### 1. Room Paging 3 컴파일 의존성 FAILED 에러
*   **원인 (Root Cause)**: Room DB에 캐시된 엔터티를 무한 스크롤 페이징 소스로 반환하기 위해 `PagingSource<Int, DealEntity>` 형태로 DAO 메서드를 정의했으나, 컴파일러가 `To use PagingSource, you must add room-paging artifact from Room as a dependency.` 예외를 발생시키며 중단됨. KSP 컴파일 시 해당 어노테이션 프로세서가 Paging 의존성을 링킹하지 못해 생기는 안드로이드 고유 이슈.
*   **조치 (Resolution)**: `app/build.gradle` 의존성 블록에 `implementation 'androidx.room:room-paging:2.6.1'` 라이브러리를 안전하게 명시적으로 주입하여 KSP 프로세싱 단계를 완전 정상화함.

### 2. Paging 3 MediatorResult Unresolved Reference 컴파일 에러
*   **원인 (Root Cause)**: `HotDealsRemoteMediator.kt`를 설계 및 작성하는 도중, `MediatorResult` 심볼을 참조하려 할 때 `Unresolved reference: MediatorResult` 에러가 빌드 과정에서 발생.
*   **조치 (Resolution)**: Paging 3 아키텍처 상 `MediatorResult`가 독립된 탑레벨 클래스가 아니라 `RemoteMediator` 추상 클래스 내부에 정의된 중첩 실드 클래스(Nested Sealed Class)라는 특이 구조를 파악. 임포트 경로를 `import androidx.paging.RemoteMediator.MediatorResult`로 정밀 정정하여 깔끔하게 에러를 해소하고 컴파일 완료.

### 3. Kotlin/KSP 컴파일러 데몬 내부 캐시 충돌 크래시
*   **원인 (Root Cause)**: 여러 소스 코드 파일(Room Entity, DAO, Mediator 등)이 급격히 신설 및 교체되면서 Gradle 빌드 데몬과 Kotlin 컴파일러 캐시 간의 충돌 상태가 유발됨. 이로 인해 `lateinit property cleanFilenames has not been initialized` 라는 컴파일러 내부 런타임 예외가 발생하여 빌드가 전면 FAILED 됨.
*   **조치 (Resolution)**: Gradle 컴파일러 세션의 데몬 캐시 충돌을 완전히 해소하기 위해, 터미널에서 `.\gradlew clean`을 호출해 전체 컴파일 빌드 캐시 세션을 말끔히 초기화한 뒤 컴파일을 재수행하여 `BUILD SUCCESSFUL`을 달성하고 안정성을 원천 확보함.
