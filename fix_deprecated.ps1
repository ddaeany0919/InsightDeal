# Warning 수정 스크립트
# deprecated 아이콘들을 일괄 변경

# 1. ArrowBack
(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\bookmark\BookmarkScreen.kt") -replace 'import androidx.compose.material.icons.filled.ArrowBack', 'import androidx.compose.material.icons.automirrored.filled.ArrowBack' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\bookmark\BookmarkScreen.kt"

(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\price\PriceGraphScreen.kt") -replace 'import androidx.compose.material.icons.filled.ArrowBack', 'import androidx.compose.material.icons.automirrored.filled.ArrowBack' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\price\PriceGraphScreen.kt"

(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\recommendation\RecommendationScreen.kt") -replace 'import androidx.compose.material.icons.filled.ArrowBack', 'import androidx.compose.material.icons.automirrored.filled.ArrowBack' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\recommendation\RecommendationScreen.kt"

(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\search\AdvancedSearchScreen.kt") -replace 'import androidx.compose.material.icons.filled.ArrowBack', 'import androidx.compose.material.icons.automirrored.filled.ArrowBack' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\search\AdvancedSearchScreen.kt"

(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\settings\ThemeSettingsScreen.kt") -replace 'import androidx.compose.material.icons.filled.ArrowBack', 'import androidx.compose.material.icons.automirrored.filled.ArrowBack' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\settings\ThemeSettingsScreen.kt"

(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\ui\DealDetailScreen.kt") -replace 'import androidx.compose.material.icons.filled.ArrowBack', 'import androidx.compose.material.icons.automirrored.filled.ArrowBack' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\ui\DealDetailScreen.kt"

# 2. TrendingUp/Down
(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\components\FourMallComparisonSection.kt") -replace 'import androidx.compose.material.icons.filled.TrendingDown', 'import androidx.compose.material.icons.automirrored.filled.TrendingDown' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\components\FourMallComparisonSection.kt"

(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\price\PriceGraphScreen.kt") -replace 'import androidx.compose.material.icons.filled.TrendingUp', 'import androidx.compose.material.icons.automirrored.filled.TrendingUp' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\price\PriceGraphScreen.kt"

(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\price\PriceGraphScreen.kt") -replace 'import androidx.compose.material.icons.filled.TrendingDown', 'import androidx.compose.material.icons.automirrored.filled.TrendingDown' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\price\PriceGraphScreen.kt"

(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\search\AdvancedSearchScreen.kt") -replace 'import androidx.compose.material.icons.filled.TrendingUp', 'import androidx.compose.material.icons.automirrored.filled.TrendingUp' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\presentation\search\AdvancedSearchScreen.kt"

(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\ui\components\EnhancedPriceCard.kt") -replace 'import androidx.compose.material.icons.filled.TrendingDown', 'import androidx.compose.material.icons.automirrored.filled.TrendingDown' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\ui\components\EnhancedPriceCard.kt"

# 3. Divider -> HorizontalDivider  
(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\ui\DealDetailScreen.kt") -replace 'Divider\(\)', 'HorizontalDivider()' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\ui\DealDetailScreen.kt"

(Get-Content "app\src\main\java\com\ddaeany0919\insightdeal\ui\HomeScreenScaffold.kt") -replace 'Divider\(\)', 'HorizontalDivider()' | Set-Content "app\src\main\java\com\ddaeany0919\insightdeal\ui\HomeScreenScaffold.kt"

Write-Host "✅ Deprecated 아이콘 및 컴포넌트 일괄 수정 완료!"
