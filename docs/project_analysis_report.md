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
