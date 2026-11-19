package com.ddaeany0919.insightdeal.presentation.category

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.presentation.theme.LocalIsDarkTheme

@Immutable
private data class CategorySpec(
    val routeName: String,
    val displayTitle: String,
    val icon: ImageVector,
    val tintColor: Color
)

// [ui-tokens] 15개 알록달록 무지개 색상 대신 5대 시맨틱 톤(핫딜 오렌지 / 브랜드 블루 / 에메랄드 그린 / 웜 앰버 / 슬레이트)으로 정제하여 시각적 통일성 강화
private val ToneHotOrange = Color(0xFFFF5A26)
private val ToneBrandBlue = Color(0xFF3182F6)
private val ToneEmerald = Color(0xFF0D9488)
private val ToneWarmAmber = Color(0xFFD97706)
private val ToneIndigo = Color(0xFF4F46E5)
private val ToneRose = Color(0xFFE11D48)
private val ToneSlate = Color(0xFF4E5968)

private fun iconAndToneForCategory(key: String): Pair<ImageVector, Color> = when (key) {
    "핫딜모음" -> Icons.Outlined.LocalFireDepartment to ToneHotOrange
    "음식" -> Icons.Outlined.Restaurant to ToneWarmAmber
    "생활용품" -> Icons.Outlined.Inventory2 to ToneEmerald
    "PC용품" -> Icons.Outlined.Computer to ToneBrandBlue
    "가전제품" -> Icons.Outlined.Tv to ToneIndigo
    "SW/게임" -> Icons.Outlined.SportsEsports to ToneIndigo
    "패션" -> Icons.Outlined.Checkroom to ToneRose
    "뷰티" -> Icons.Outlined.AutoAwesome to ToneRose
    "모바일/기프티콘" -> Icons.Outlined.Smartphone to ToneBrandBlue
    "상품권" -> Icons.Outlined.ConfirmationNumber to ToneWarmAmber
    "육아/유아용품" -> Icons.Outlined.ChildCare to ToneEmerald
    "해외직구" -> Icons.Outlined.FlightTakeoff to ToneBrandBlue
    "적립" -> Icons.Outlined.Savings to ToneEmerald
    "이벤트" -> Icons.Outlined.Redeem to ToneHotOrange
    else -> Icons.Outlined.MoreHoriz to ToneSlate
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(navController: NavController? = null) {
    val catalogCategories by CategoryCatalog.categoriesState.collectAsState()
    val categoriesToShow = remember(catalogCategories) {
        val orderedKeys = listOf(
            "핫딜모음", "음식", "생활용품", "PC용품", "가전제품",
            "SW/게임", "패션", "뷰티", "모바일/기프티콘", "상품권",
            "육아/유아용품", "해외직구", "적립", "이벤트", "기타"
        )
        val byKey = catalogCategories.associateBy { it.key }
        orderedKeys.map { key ->
            val std = byKey[key]
            val (icon, tone) = iconAndToneForCategory(key)
            CategorySpec(
                routeName = key,
                displayTitle = std?.label ?: CategoryCatalog.getDisplayLabel(key),
                icon = icon,
                tintColor = tone
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "카테고리",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        letterSpacing = (-0.4).sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(
                items = categoriesToShow,
                key = { it.routeName },
                contentType = { "category_grid_item" }
            ) { spec ->
                CategoryCard(
                    spec = spec,
                    onClick = {
                        val encodedName = java.net.URLEncoder.encode(spec.routeName, "UTF-8")
                        navController?.navigate("category_detail/$encodedName")
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    spec: CategorySpec,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "categoryCardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(spec.tintColor.copy(alpha = if (isDark) 0.16f else 0.09f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = spec.displayTitle,
                    tint = spec.tintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = spec.displayTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
