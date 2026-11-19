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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ddaeany0919.insightdeal.models.CategoryCatalog
import com.ddaeany0919.insightdeal.presentation.theme.LocalInsightDealColors
import com.ddaeany0919.insightdeal.presentation.theme.LocalIsDarkTheme

@Immutable
private data class CategorySpec(
    val routeName: String,
    val displayTitle: String,
    val icon: ImageVector,
    val tintColor: Color
)

private fun iconAndToneForCategory(key: String, isDark: Boolean): Pair<ImageVector, Color> {
    val toneHotOrange = if (isDark) Color(0xFFFB923C) else Color(0xFFC2410C)
    val toneBrandBlue = if (isDark) Color(0xFF60A5FA) else Color(0xFF1B64DA)
    val toneEmerald = if (isDark) Color(0xFF34D399) else Color(0xFF006B47)
    val toneWarmAmber = if (isDark) Color(0xFFFBBF24) else Color(0xFF92400E)
    val toneIndigo = if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA)
    val toneRose = if (isDark) Color(0xFFFB7185) else Color(0xFFBE123C)
    val toneSlate = if (isDark) Color(0xFFA8B3C2) else Color(0xFF525D6B)

    return when (key) {
        "핫딜모음" -> Icons.Outlined.LocalFireDepartment to toneHotOrange
        "음식" -> Icons.Outlined.Restaurant to toneWarmAmber
        "생활용품" -> Icons.Outlined.Inventory2 to toneEmerald
        "PC용품" -> Icons.Outlined.Computer to toneBrandBlue
        "가전제품" -> Icons.Outlined.Tv to toneIndigo
        "SW/게임" -> Icons.Outlined.SportsEsports to toneIndigo
        "패션" -> Icons.Outlined.Checkroom to toneRose
        "뷰티" -> Icons.Outlined.AutoAwesome to toneRose
        "모바일/기프티콘" -> Icons.Outlined.Smartphone to toneBrandBlue
        "상품권" -> Icons.Outlined.ConfirmationNumber to toneWarmAmber
        "육아/유아용품" -> Icons.Outlined.ChildCare to toneEmerald
        "해외직구" -> Icons.Outlined.FlightTakeoff to toneBrandBlue
        "적립" -> Icons.Outlined.Savings to toneEmerald
        "이벤트" -> Icons.Outlined.Redeem to toneHotOrange
        else -> Icons.Outlined.MoreHoriz to toneSlate
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(navController: NavController? = null) {
    val isDark = LocalIsDarkTheme.current
    val semantic = LocalInsightDealColors.current
    val fontScale = LocalDensity.current.fontScale
    val catalogCategories by CategoryCatalog.categoriesState.collectAsState()
    val categoriesToShow = remember(catalogCategories, isDark) {
        val orderedKeys = listOf(
            "핫딜모음", "음식", "생활용품", "PC용품", "가전제품",
            "SW/게임", "패션", "뷰티", "모바일/기프티콘", "상품권",
            "육아/유아용품", "해외직구", "적립", "이벤트", "기타"
        )
        val byKey = catalogCategories.associateBy { it.key }
        orderedKeys.map { key ->
            val std = byKey[key]
            val (icon, tone) = iconAndToneForCategory(key, isDark)
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val columnCount = if (maxWidth < 340.dp || fontScale >= 1.3f) 2 else 3
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = categoriesToShow,
                    key = { it.routeName },
                    contentType = { "category_grid_item" }
                ) { spec ->
                    CategoryCard(
                        spec = spec,
                        cardBorderColor = semantic.cardBorder,
                        onClick = {
                            val encodedName = java.net.URLEncoder.encode(spec.routeName, "UTF-8")
                            navController?.navigate("category_detail/$encodedName")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    spec: CategorySpec,
    cardBorderColor: Color,
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
            .heightIn(min = 92.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "${spec.displayTitle} 카테고리"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = cardBorderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(spec.tintColor.copy(alpha = if (isDark) 0.16f else 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
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
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}
